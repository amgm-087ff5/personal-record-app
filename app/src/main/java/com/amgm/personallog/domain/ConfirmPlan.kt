package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem

/**
 * レシートの確定(再確定)で、家計簿の行をどう変えるかの計画。DBに触れない純Kotlinで作り、Repository が実行する。
 */
sealed interface ConfirmPlan {
    /** 既存の行を全部消して作り直す。group が null なら children は単独の支出。 */
    data class Rebuild(val group: ExpenseGroup?, val children: List<Expense>) : ConfirmPlan

    /** 同じ品目の行を更新する。編集済みの欄は残す。group は更新後の値(id は既存)。 */
    data class UpdateGroup(
        val group: ExpenseGroup,
        val inserts: List<Expense>,
        val updates: List<Expense>,
        val deleteIds: List<Long>,
    ) : ConfirmPlan

    /** 単独の支出1行を更新する(立て替え、または品目をまとめない確定)。編集済みの欄は残す。 */
    data class UpdateSingle(val expense: Expense) : ConfirmPlan
}

object ConfirmPlanner {
    /**
     * @param items レシートの品目(DBの id を持つ)
     * @param existingGroups このレシートに紐づく既存の支払いグループ
     * @param existingExpenses このレシートに紐づく既存の支出(receiptId が一致するもの。手入力で足した品目は含まない)
     */
    fun plan(
        receipt: Receipt,
        items: List<ReceiptItem>,
        existingGroups: List<ExpenseGroup>,
        existingExpenses: List<Expense>,
        perItem: Boolean,
        now: Long,
    ): ConfirmPlan {
        val fresh = if (perItem) LedgerCalc.groupFromReceipt(receipt, items, now) else null
        if (fresh == null) {
            val wanted = LedgerCalc.expensesFromReceipt(receipt, items, perItem, now)
            val current = existingExpenses.singleOrNull()
            // 単独1行のまま(立て替えなど)は、その行を更新して編集済みの欄を残す
            if (wanted.size == 1 && existingGroups.isEmpty() && current != null &&
                current.groupId == null && current.receiptItemId == null
            ) {
                return ConfirmPlan.UpdateSingle(mergeSingle(current, wanted[0]))
            }
            return ConfirmPlan.Rebuild(null, wanted)
        }

        val group = existingGroups.singleOrNull()
        val canUpdate = group != null && existingExpenses.all { it.groupId == group.id && it.receiptItemId != null }
        if (!canUpdate || group == null) return ConfirmPlan.Rebuild(fresh.group, fresh.items)

        val updatedGroup = mergeGroup(group, fresh.group)
        val wantedByItem = fresh.items.associateBy { it.receiptItemId }
        val existingByItem = existingExpenses.groupBy { it.receiptItemId }
        val inserts = ArrayList<Expense>()
        val updates = ArrayList<Expense>()
        val deleteIds = ArrayList<Long>()
        // レシートの品目の順に処理する(挿入の順を安定させる)
        for (item in items) {
            val want = wantedByItem[item.id]
            val have = existingByItem[item.id].orEmpty()
            if (want == null) {
                deleteIds += have.map { it.id } // 0円の品目: 作らない。あれば消す
            } else if (have.isEmpty()) {
                inserts += want
            } else {
                updates += mergeItem(have[0], want)
                deleteIds += have.drop(1).map { it.id }
            }
        }
        // レシートから消えた品目の支出
        val itemIds = items.map { it.id }.toSet()
        for ((itemId, have) in existingByItem) if (itemId !in itemIds) deleteIds += have.map { it.id }
        return ConfirmPlan.UpdateGroup(updatedGroup, inserts, updates, deleteIds)
    }

    /** 実行すると、家計簿に既にある行が作り直される(編集が消える)計画か。 */
    fun discardsExisting(plan: ConfirmPlan, existingGroups: List<ExpenseGroup>, existingExpenses: List<Expense>): Boolean =
        plan is ConfirmPlan.Rebuild && (existingGroups.isNotEmpty() || existingExpenses.isNotEmpty())

    /** 品目の支出: 編集済みでない欄だけレシート(want)の値にする。 */
    internal fun mergeItem(have: Expense, want: Expense): Expense = have.copy(
        amount = if (have.amountEdited) have.amount else want.amount,
        category = if (have.categoryEdited) have.category else want.category,
        name = if (have.nameEdited) have.name else want.name,
        memo = if (have.memoEdited) have.memo else want.memo,
        date = if (have.timeEdited) have.date else want.date,
        time = if (have.timeEdited) have.time else want.time,
    )

    /** グループ: 金額(=合計)・日時・メモ(=店名)・カテゴリを、編集済みでない欄だけ更新する。 */
    internal fun mergeGroup(have: ExpenseGroup, want: ExpenseGroup): ExpenseGroup = have.copy(
        amount = if (have.amountEdited) have.amount else want.amount,
        category = if (have.categoryEdited) have.category else want.category,
        memo = if (have.memoEdited) have.memo else want.memo,
        date = if (have.timeEdited) have.date else want.date,
        time = if (have.timeEdited) have.time else want.time,
    )

    /** 単独の支出(メモは店名)。品名は無いので name は触らない。 */
    internal fun mergeSingle(have: Expense, want: Expense): Expense = have.copy(
        amount = if (have.amountEdited) have.amount else want.amount,
        category = if (have.categoryEdited) have.category else want.category,
        memo = if (have.memoEdited) have.memo else want.memo,
        date = if (have.timeEdited) have.date else want.date,
        time = if (have.timeEdited) have.time else want.time,
    )
}

/** レシートの品目の保存を差分にするための計画。id が保たれる。 */
data class ItemSavePlan(
    val inserts: List<ReceiptItem>,
    val updates: List<ReceiptItem>,
    val deleteIds: List<Long>,
)

object ItemSaver {
    /**
     * @param existing DBにある品目
     * @param incoming 画面の品目。id が existing にあれば更新、0(新規)や不明な id は追加。existing にあって incoming に無いものは削除。
     */
    fun plan(receiptId: Long, existing: List<ReceiptItem>, incoming: List<ReceiptItem>): ItemSavePlan {
        val existingIds = existing.map { it.id }.toSet()
        val kept = HashSet<Long>()
        val inserts = ArrayList<ReceiptItem>()
        val updates = ArrayList<ReceiptItem>()
        for (item in incoming) {
            if (item.id != 0L && item.id in existingIds && kept.add(item.id)) {
                updates += item.copy(receiptId = receiptId)
            } else {
                inserts += item.copy(id = 0, receiptId = receiptId)
            }
        }
        return ItemSavePlan(inserts, updates, existingIds.filter { it !in kept })
    }
}
