package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.TotalSource

/** 支出の画面で直した値(書き戻しの判定の入力)。 */
data class PaymentValues(
    val date: String,
    val time: String,
    val amount: Int,
    val category: String,
    val name: String,
    val memo: String,
)

/** 書き戻しの対象。receiptId は紐づく支払い(レシート)の id。 */
sealed interface WriteBackTarget {
    val receiptId: Long?

    /** 品目の支出(receiptItemId あり)。 */
    data class Item(val expense: Expense) : WriteBackTarget {
        override val receiptId: Long? get() = expense.receiptId
    }

    /** 単独の支出(品目なし)。 */
    data class Single(val expense: Expense) : WriteBackTarget {
        override val receiptId: Long? get() = expense.receiptId
    }

    /** 支出のまとまり(支払い全体)。 */
    data class Group(val group: ExpenseGroup) : WriteBackTarget {
        override val receiptId: Long? get() = group.receiptId
    }
}

/** 書き戻しの判定の結果。 */
sealed interface WriteBackPlan {
    /**
     * 支払い・品目を変え、対象の支出の行を更新する。その後の家計簿の作り直しは呼び出し側が行う。
     * @param receipt 更新後の支払い(変わらなければ元のまま)
     * @param changedItems 変えた品目だけ
     * @param expense 対象が品目・単独の支出のときの、更新後の行(書き戻した欄の編集済みは下ろしてある)
     * @param group 対象が支出のまとまりのときの、更新後の行
     * @param expectedAmount 金額を直したときの、家計簿に入るはずの金額(作り直し後に確かめる。直していなければ null)
     */
    data class Apply(
        val receipt: Receipt,
        val changedItems: List<ReceiptItem>,
        val expense: Expense?,
        val group: ExpenseGroup?,
        val expectedAmount: Int?,
    ) : WriteBackPlan

    /** 書き戻せない・許されない変更。何も書かず、理由を出す。 */
    data class Reject(val message: String) : WriteBackPlan
}

/**
 * 家計簿(支出)の画面で直した欄を、支払い(レシート)と品目へ書き戻す判定(純Kotlin・DBに触れない)。
 *
 * 書き戻し先:
 *  - 日時 → 支払いの purchasedAt(品目・支出の日時は支払いに従う)
 *  - 品目の品名・カテゴリ・メモ → その品目(receipt_items)。金額 → その品目を税込にして金額を入れる
 *  - 単独の支出・支出のまとまりの金額 → 支払いの合計(手入力の支払いだけ。印字のある支払いは変えない)
 *  - 支払い先 → 支払いの storeName(別の操作。withStoreName)
 * 支払いに対応する欄が無いもの(支出のまとまりのカテゴリ・メモ、単独の支出のカテゴリ・メモ・品名)は、支出だけに持つ(編集済みで保護)。
 *
 * 金額は、割り勘の参加者が1人のときだけ書き戻す(people が 2 以上なら拒否)。
 */
object PaymentWriteBack {
    const val MESSAGE_SPLIT_AMOUNT = "割り勘があるため、金額は割り勘画面で直してください。"
    const val MESSAGE_PRINTED_TOTAL = "レシートに印字された合計は変えられません。"
    const val MESSAGE_ITEMS_AMOUNT = "品目のある支払いの金額は、品目の金額で直してください。"
    const val MESSAGE_ADVANCE_AMOUNT = "立て替えの支払いの金額は、ここでは直せません。"

    /** 日時から支払いの purchasedAt を作る("yyyy-MM-dd HH:mm"。時刻が空なら日付だけ)。 */
    fun purchasedAtOf(date: String, time: String): String = if (time.isBlank()) date else "$date $time"

    /** 支払い先(店名)を入れ替える。前後の空白は除く。 */
    fun withStoreName(receipt: Receipt, storeName: String): Receipt = receipt.copy(storeName = storeName.trim())

    /** 印字のあるレシート(画像あり)か。画像が無い支払いは手入力。 */
    fun isPrinted(receipt: Receipt): Boolean = receipt.imagePath.isNotBlank()

    /**
     * @param items 支払いの品目(今の内容)
     * @param people 割り勘の参加者の数(割り勘シートが無ければ1)
     */
    fun plan(
        target: WriteBackTarget,
        values: PaymentValues,
        receipt: Receipt,
        items: List<ReceiptItem>,
        people: Int,
    ): WriteBackPlan = when (target) {
        is WriteBackTarget.Item -> planItem(target.expense, values, receipt, items, people)
        is WriteBackTarget.Single -> planSingle(target.expense, values, receipt, items, people)
        is WriteBackTarget.Group -> planGroup(target.group, values, receipt, people)
    }

    private fun planItem(
        e: Expense,
        v: PaymentValues,
        receipt: Receipt,
        items: List<ReceiptItem>,
        people: Int,
    ): WriteBackPlan {
        val itemId = e.receiptItemId ?: return WriteBackPlan.Reject("この支出は品目に紐づいていません。")
        val item = items.firstOrNull { it.id == itemId } ?: return WriteBackPlan.Reject("レシートの品目が見つかりません。")
        // 品目の日時は支払い(グループ)に従う。ここでは日時を書き戻さない
        val name = v.name.trim()
        val memo = v.memo.trim()
        val nameChanged = name != e.name
        val categoryChanged = v.category != e.category
        val memoChanged = memo != e.memo
        val amountChanged = v.amount != e.amount
        if (amountChanged && people >= 2) return WriteBackPlan.Reject(MESSAGE_SPLIT_AMOUNT)

        var newItem = item
        if (nameChanged) newItem = newItem.copy(name = name)
        if (categoryChanged) newItem = newItem.copy(category = v.category)
        if (memoChanged) newItem = newItem.copy(memo = memo)
        if (amountChanged) newItem = newItem.copy(amount = v.amount, priceIncludesTax = true)
        val newItems = items.map { if (it.id == itemId) newItem else it }

        var newReceipt = receipt
        if (amountChanged) {
            // 手入力の支払いで、直す前の合計が品目の合計と一致していたときだけ、合計も新しい品目の合計に合わせる
            val oldSum = TaxCalc.paidYen(items)
            if (!isPrinted(receipt) && receipt.totalAmount?.toLong() == oldSum) {
                newReceipt = receipt.copy(totalAmount = TaxCalc.paidYen(newItems).toInt(), totalSource = TotalSource.ITEMS)
            }
        }

        val expense = e.copy(
            name = if (nameChanged) name else e.name,
            category = if (categoryChanged) v.category else e.category,
            memo = if (memoChanged) memo else e.memo,
            amount = if (amountChanged) v.amount else e.amount,
            nameEdited = if (nameChanged) false else e.nameEdited,
            categoryEdited = if (categoryChanged) false else e.categoryEdited,
            memoEdited = if (memoChanged) false else e.memoEdited,
            amountEdited = if (amountChanged) false else e.amountEdited,
        )
        return WriteBackPlan.Apply(
            receipt = newReceipt,
            changedItems = if (newItem != item) listOf(newItem) else emptyList(),
            expense = expense,
            group = null,
            expectedAmount = if (amountChanged) v.amount else null,
        )
    }

    private fun planSingle(
        e: Expense,
        v: PaymentValues,
        receipt: Receipt,
        items: List<ReceiptItem>,
        people: Int,
    ): WriteBackPlan {
        val dateChanged = v.date != e.date || v.time != e.time
        val amountChanged = v.amount != e.amount
        if (amountChanged) {
            if (people >= 2) return WriteBackPlan.Reject(MESSAGE_SPLIT_AMOUNT)
            if (receipt.isAdvance) return WriteBackPlan.Reject(MESSAGE_ADVANCE_AMOUNT)
            if (items.isNotEmpty()) return WriteBackPlan.Reject(MESSAGE_ITEMS_AMOUNT)
            if (isPrinted(receipt)) return WriteBackPlan.Reject(MESSAGE_PRINTED_TOTAL)
        }

        var newReceipt = receipt
        if (dateChanged) newReceipt = newReceipt.copy(purchasedAt = purchasedAtOf(v.date, v.time))
        if (amountChanged) newReceipt = newReceipt.copy(totalAmount = v.amount, totalSource = TotalSource.MANUAL)

        // 品名・カテゴリ・メモは支出だけに持つ(PaymentEdit と同じ規則で編集済みを立てる)
        val expense = PaymentEdit.editExpense(e, v.date, v.time, v.amount, v.category, v.name, v.memo).copy(
            amountEdited = if (amountChanged) false else e.amountEdited,
            timeEdited = if (dateChanged) false else e.timeEdited,
        )
        return WriteBackPlan.Apply(
            receipt = newReceipt,
            changedItems = emptyList(),
            expense = expense,
            group = null,
            expectedAmount = if (amountChanged) v.amount else null,
        )
    }

    private fun planGroup(
        g: ExpenseGroup,
        v: PaymentValues,
        receipt: Receipt,
        people: Int,
    ): WriteBackPlan {
        val dateChanged = v.date != g.date || v.time != g.time
        val amountChanged = v.amount != g.amount
        if (amountChanged) {
            if (people >= 2) return WriteBackPlan.Reject(MESSAGE_SPLIT_AMOUNT)
            if (isPrinted(receipt)) return WriteBackPlan.Reject(MESSAGE_PRINTED_TOTAL)
        }

        var newReceipt = receipt
        if (dateChanged) newReceipt = newReceipt.copy(purchasedAt = purchasedAtOf(v.date, v.time))
        if (amountChanged) newReceipt = newReceipt.copy(totalAmount = v.amount, totalSource = TotalSource.MANUAL)

        // カテゴリ・メモは支出のまとまりだけに持つ(PaymentEdit と同じ規則)
        val group = PaymentEdit.editGroup(g, v.date, v.time, v.amount, v.category, v.memo).copy(
            amountEdited = if (amountChanged) false else g.amountEdited,
            timeEdited = if (dateChanged) false else g.timeEdited,
        )
        return WriteBackPlan.Apply(
            receipt = newReceipt,
            changedItems = emptyList(),
            expense = null,
            group = group,
            expectedAmount = if (amountChanged) v.amount else null,
        )
    }
}
