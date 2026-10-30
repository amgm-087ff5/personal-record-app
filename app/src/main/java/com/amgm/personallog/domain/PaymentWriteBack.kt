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

/** 書き戻しの対象。receiptId は紐づく支払いの id。 */
sealed interface WriteBackTarget {
    val receiptId: Long?

    /**
     * 支出の品目。receiptItemId があれば支払いの品目へ書き戻す。無ければ品目の無い支払いの1品目(支出だけに持つ)。
     * onlyItem: 支出の品目がこれ1つだけか。1つだけなら金額は支出の金額と同じなので、品目の金額の変更は
     * 支出(まとまり)の金額の変更と同じに扱う(支払いの合計・立て替えの自分の分と、支払いの品目1つの金額へ書き戻す)。
     */
    data class Item(val expense: Expense, val onlyItem: Boolean = false) : WriteBackTarget {
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
     * @param expense 対象が品目のときの、更新後の行(書き戻した欄の編集済みは下ろしてある)
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
 * 家計簿(支出)の画面で直した欄を、支払いと品目へ書き戻す判定(純Kotlin・DBに触れない)。
 *
 * 書き戻し先:
 *  - 日時 → 支払いの purchasedAt(品目・支出の日時は支払いに従う)
 *  - 品目の品名・カテゴリ・メモ → その品目(receipt_items)。金額 → その品目を税込にして金額を入れる
 *  - 支出のまとまりの金額 → 支払いの合計(totalAmount。totalSource は MANUAL。印字のある支払いも同じで、画像・読取原文はそのまま。
 *    品目の合計との差は差額の表示(ExpenseItems.mismatch)に任せる)。立て替えの支払いは、合計ではなく自分の分(ownAmount)へ書く
 *  - 品目が1つだけの支出の品目の金額 → 支出のまとまりの金額と同じ(支払いの合計・自分の分と、対応する支払いの品目1つの金額)
 *  - 支払い先 → 支払いの storeName(別の操作。withStoreName)
 * 支払いに対応する欄が無いもの(支出のまとまりのカテゴリ・メモ、品目の無い支払いの1品目の品名・カテゴリ・メモ)は、
 * 支出だけに持つ(編集済みで保護)。品目の無い支払いの1品目の金額は支出の金額に従うので、品目の側では直せない(支出の金額で直す)。
 *
 * 金額は、割り勘の参加者が1人のときだけ書き戻す(people が 2 以上なら拒否)。
 *
 * 規則の例外(設計判断待ち): 「支出を変えたら支払いにも反映される」の例外が、この外に残っている。実装はしていない。
 *  - (d) 割り勘で参加者2人以上の金額変更は拒否する(MESSAGE_SPLIT_AMOUNT)。自分の分は割り勘の計算結果で、支払いの欄に対応が無い。
 *  - (e) 支出の品目の削除・復元は支払いの品目へ反映しない(ExpenseRepository.delete / restore。deletedByUser を立てるだけ)。
 *  - (f) 支出のまとまりのカテゴリ・メモは支払いに対応する欄が無く、支出だけが持つ(編集済みで保護)。
 *  - (g) 割り勘で自分の分を変えると支出の金額だけが変わる(ReceiptRepository.saveSplit)。
 */
object PaymentWriteBack {
    const val MESSAGE_SPLIT_AMOUNT = "負担割りがあるため、金額は負担割り画面で直してください。"
    const val MESSAGE_ITEMS_AMOUNT = "品目のある支払いの金額は、品目の金額で直してください。"
    const val MESSAGE_BLANK_ITEM_AMOUNT = "品目の無い支払いの品目の金額は、支出の金額で直してください。"
    const val MESSAGE_ONLY_ITEM_ZERO = "品目が1つのときの金額は、1円以上にしてください。"

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
        is WriteBackTarget.Item -> when {
            target.expense.receiptItemId == null -> planBlankItem(target.expense, values, receipt)
            target.onlyItem && values.amount != target.expense.amount -> planOnlyItem(target.expense, values, receipt, items, people)
            else -> planItem(target.expense, values, receipt, items, people)
        }
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
        val item = items.firstOrNull { it.id == itemId } ?: return WriteBackPlan.Reject("支払いの品目が見つかりません。")
        // 品目の日時は支払い(グループ)に従う。ここでは日時を書き戻さない
        val name = v.name.trim()
        val memo = v.memo.trim()
        val nameChanged = name != e.name
        val categoryChanged = v.category != e.category
        val memoChanged = memo != e.memo
        val amountChanged = v.amount != e.amount
        // 規則の例外(設計判断待ち): 割り勘で参加者2人以上の金額変更は書き戻さない。自分の分は割り勘の計算結果で、品目の金額とは別
        if (amountChanged && people >= 2) return WriteBackPlan.Reject(MESSAGE_SPLIT_AMOUNT)

        var newItem = item
        if (nameChanged) newItem = newItem.copy(name = name)
        if (categoryChanged) newItem = newItem.copy(category = v.category)
        if (memoChanged) newItem = newItem.copy(memo = memo)
        // 金額を入れたら、その品目は金額不明ではなくなる
        if (amountChanged) newItem = newItem.copy(amount = v.amount, priceIncludesTax = true, unknownAmount = false)
        val newItems = items.map { if (it.id == itemId) newItem else it }

        var newReceipt = receipt
        if (amountChanged) {
            // 手入力の支払いで、直す前の合計が品目の合計と一致していたときだけ、合計も新しい品目の合計に合わせる
            // (金額不明の品目があるときは、差は不明の品目に入るので合計は変えない)
            val oldSum = TaxCalc.paidYen(items)
            if (!isPrinted(receipt) && items.none { it.unknownAmount } && receipt.totalAmount?.toLong() == oldSum) {
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
            unknownAmount = if (amountChanged) false else e.unknownAmount,
        )
        return WriteBackPlan.Apply(
            receipt = newReceipt,
            changedItems = if (newItem != item) listOf(newItem) else emptyList(),
            expense = expense,
            group = null,
            expectedAmount = if (amountChanged) v.amount else null,
        )
    }

    /**
     * 品目が1つだけの支出の品目(receiptItemId あり)の金額変更。品目の金額は支出の金額と同じなので、支出(まとまり)の金額の変更と同じに扱う:
     *  - 立て替え: 支払いの自分の分(ownAmount)。合計と品目は変えない(支出の金額は自分の分で、品目は支出の金額に追従する)。
     *  - それ以外: 支払いの合計(MANUAL)と、対応する支払いの品目の金額(税込・金額不明でなくす)。
     * 品名・カテゴリ・メモの変更は planItem と同じ。割り勘で参加者2人以上のときは書き戻さない(planGroup と同じ)。
     */
    private fun planOnlyItem(
        e: Expense,
        v: PaymentValues,
        receipt: Receipt,
        items: List<ReceiptItem>,
        people: Int,
    ): WriteBackPlan {
        if (people >= 2) return WriteBackPlan.Reject(MESSAGE_SPLIT_AMOUNT)
        // 0円の品目は支出の品目として作られない(金額は1円以上)
        if (v.amount <= 0) return WriteBackPlan.Reject(MESSAGE_ONLY_ITEM_ZERO)
        // 品名・カテゴリ・メモは金額を変えない planItem で求め、金額だけをここで足す
        val base = planItem(e, v.copy(amount = e.amount), receipt, items, people)
        if (base !is WriteBackPlan.Apply) return base
        val original = items.firstOrNull { it.id == e.receiptItemId } ?: return WriteBackPlan.Reject("レシートの品目が見つかりません。")
        val current = base.changedItems.firstOrNull() ?: original
        val newItem = if (receipt.isAdvance) {
            current
        } else {
            current.copy(amount = v.amount, priceIncludesTax = true, unknownAmount = false)
        }
        return WriteBackPlan.Apply(
            receipt = withPaidAmount(base.receipt, v.amount),
            changedItems = if (newItem != original) listOf(newItem) else emptyList(),
            expense = base.expense?.copy(amount = v.amount, amountEdited = false, unknownAmount = false),
            group = null,
            expectedAmount = v.amount,
        )
    }

    /**
     * 支出の金額(自分の負担)を支払いへ入れる。立て替えは自分の分(ownAmount。合計は変えない)、それ以外は合計(MANUAL。
     * 印字のあるレシートも同じで、画像・読取原文は変えない)。
     */
    private fun withPaidAmount(receipt: Receipt, amount: Int): Receipt =
        if (receipt.isAdvance) {
            receipt.copy(ownAmount = amount)
        } else {
            receipt.copy(totalAmount = amount, totalSource = TotalSource.MANUAL)
        }

    /**
     * 品目の無い支払いの1品目(receiptItemId なし)。品名・カテゴリ・メモは支出の品目だけに持つ(編集済みを立てる)。
     * 金額は支出の金額に従うので、ここでは直せない。日時は支出(まとまり)に従うので、書き戻さない。
     */
    private fun planBlankItem(e: Expense, v: PaymentValues, receipt: Receipt): WriteBackPlan {
        if (v.amount != e.amount) return WriteBackPlan.Reject(MESSAGE_BLANK_ITEM_AMOUNT)
        val expense = PaymentEdit.editExpense(e, e.date, e.time, e.amount, v.category, v.name, v.memo)
        return WriteBackPlan.Apply(
            receipt = receipt,
            changedItems = emptyList(),
            expense = expense,
            group = null,
            expectedAmount = null,
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
        // 規則の例外(設計判断待ち): 割り勘で参加者2人以上の金額変更は書き戻さない。自分の分は割り勘の計算結果で、支払いの合計ではない
        if (amountChanged && people >= 2) return WriteBackPlan.Reject(MESSAGE_SPLIT_AMOUNT)

        var newReceipt = receipt
        if (dateChanged) newReceipt = newReceipt.copy(purchasedAt = purchasedAtOf(v.date, v.time))
        if (amountChanged) newReceipt = withPaidAmount(newReceipt, v.amount)

        // 規則の例外(設計判断待ち): カテゴリ・メモは支払いに対応する欄が無く、支出のまとまりだけに持つ(PaymentEdit と同じ規則)
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
