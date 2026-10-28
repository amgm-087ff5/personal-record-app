package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem

/**
 * レシートの確定(再確定)で、家計簿の行をどう変えるかの計画。DBに触れない純Kotlinで作り、Repository が実行する。
 * 自分の支出は必ずまとまり(expense_groups)1つと品目1行以上(v14〜。単独の支出は無い)。
 */
sealed interface ConfirmPlan {
    /**
     * 既存の行を全部消して作り直す。group が null なら家計簿に行を作らない(割り勘で自分の分が0円)。
     * children はまとまりの品目(groupId は実行時に付ける)。
     */
    data class Rebuild(
        val group: ExpenseGroup?,
        val children: List<Expense>,
        /**
         * 家計簿で消した品目(deletedByUser)の行。作り直しでも消したまま残す(children には含めない)。
         * group が null の間は、まとまりの無い非表示の行(groupId なし・deletedByUser)として置いておき、まとまりができたら付け替える。
         * 一覧・集計には出ないので「単独の支出」ではない。
         */
        val keepDeleted: List<Expense> = emptyList(),
    ) : ConfirmPlan

    /** 同じ品目の行を更新する。編集済みの欄は残す。group は更新後の値(id は既存)。 */
    data class UpdateGroup(
        val group: ExpenseGroup,
        val inserts: List<Expense>,
        val updates: List<Expense>,
        val deleteIds: List<Long>,
    ) : ConfirmPlan
}

object ConfirmPlanner {
    /**
     * @param items レシートの品目(DBの id を持つ)
     * @param existingGroups このレシートに紐づく既存の支払いグループ
     * @param existingExpenses このレシートに紐づく既存の支出(receiptId が一致するもの。手入力で足した品目は含まない)
     * @param share 割り勘の自分の分。null なら従来どおり(全額が自分の分)
     */
    fun plan(
        receipt: Receipt,
        items: List<ReceiptItem>,
        existingGroups: List<ExpenseGroup>,
        existingExpenses: List<Expense>,
        now: Long,
        share: SelfShare? = null,
    ): ConfirmPlan {
        val fresh = LedgerCalc.groupFromReceipt(receipt, items, now, share)
        // 家計簿で消した品目は、作り直しても復活させない。非表示のまま残す
        val itemIds = items.map { it.id }.toSet()
        val hidden = existingExpenses.filter { it.deletedByUser && it.receiptItemId != null && it.receiptItemId in itemIds }
        val hiddenItemIds = hidden.mapTo(HashSet()) { it.receiptItemId }
        // 自分の分が0円(割り勘で自分を外した): 家計簿に行を作らない。消した品目は非表示のまま置いておく
        if (fresh == null) return ConfirmPlan.Rebuild(null, emptyList(), keepDeleted = hidden)

        val group = existingGroups.singleOrNull()
        // 品目に対応しない行(receiptItemId なし)は、品目の無い支払いの1品目(品名が空)が1つだけなら更新できる。
        // それ以外(品名のある対応の無い行・複数)は v9 より前の古い形式なので、作り直す
        val unlinked = existingExpenses.filter { it.receiptItemId == null }
        val canUpdate = group != null &&
            existingExpenses.all { it.groupId == group.id } &&
            (unlinked.isEmpty() || (unlinked.size == 1 && unlinked[0].name.isBlank() && !unlinked[0].deletedByUser))
        if (!canUpdate || group == null) {
            val children = fresh.items.filter { it.receiptItemId == null || it.receiptItemId !in hiddenItemIds }
                // 品目は必ず1つ以上(消した品目しか無いとき)。品名が空の1品目を足す
                .ifEmpty { listOf(LedgerCalc.blankItem(fresh.group)) }
            val (kept, deleted) = normalizeSplit(fresh.group, children, hidden)
            return ConfirmPlan.Rebuild(fresh.group, kept, keepDeleted = deleted)
        }

        val updatedGroup = mergeGroup(group, fresh.group)
        // 品目の日時はグループに従う(グループの日時を支払い側で直していても、品目はそれに合わせる)
        fun Expense.followGroup() = copy(date = updatedGroup.date, time = updatedGroup.time)
        val wantedByItem = fresh.items.filter { it.receiptItemId != null }.associateBy { it.receiptItemId }
        val existingByItem = existingExpenses.filter { it.receiptItemId != null }.groupBy { it.receiptItemId }
        // 結果の行(レシートの品目の順)。id が 0 の行は追加、それ以外は更新
        val result = ArrayList<Expense>()
        val deleteIds = ArrayList<Long>()
        // レシートの品目の順に処理する(挿入の順を安定させる)
        for (item in items) {
            val want = wantedByItem[item.id]
            val have = existingByItem[item.id].orEmpty()
            if (want == null) {
                // 0円の品目: 作らない。あれば消す。ただし家計簿で金額を直した行(amountEdited)は消さずに残す
                // (家計簿で消した行は、直した内容を残す意味がないので消す)
                val (keep, drop) = have.partition { it.amountEdited && !it.deletedByUser }
                deleteIds += drop.map { it.id }
                result += keep
            } else if (have.isEmpty()) {
                result += want.followGroup()
            } else {
                result += mergeItem(have[0], want).followGroup()
                deleteIds += have.drop(1).map { it.id }
            }
        }
        // レシートから消えた品目の支出
        for ((itemId, have) in existingByItem) if (itemId !in itemIds) deleteIds += have.map { it.id }
        // 品名が空の1品目(receiptItemId なし): 品目の無い支払い、または品目を全部消した・0円しか無い支出の品目。
        // 他に消していない品目が無ければ使う(あれば更新、無ければ作る)。他に品目ができたら消す
        val freshBlank = fresh.items.firstOrNull { it.receiptItemId == null } ?: LedgerCalc.blankItem(updatedGroup).copy(createdAt = now)
        val haveBlank = existingExpenses.firstOrNull { it.receiptItemId == null }
        if (result.none { !it.deletedByUser }) {
            result += (if (haveBlank != null) mergeItem(haveBlank, freshBlank) else freshBlank).followGroup()
        } else if (haveBlank != null) {
            deleteIds += haveBlank.id
        }
        // 1品目なら金額をまとめに合わせ、不明の品目の金額を求め直す
        val normalized = ExpenseItems.normalize(updatedGroup, result)
        return ConfirmPlan.UpdateGroup(
            group = updatedGroup,
            inserts = normalized.filter { it.id == 0L },
            updates = normalized.filter { it.id != 0L },
            deleteIds = deleteIds,
        )
    }

    /** children(追加する品目)と kept(消したまま残す品目)を一緒に規則に合わせ、元の2つに分けて返す。 */
    private fun normalizeSplit(group: ExpenseGroup, children: List<Expense>, deleted: List<Expense>): Pair<List<Expense>, List<Expense>> {
        val all = ExpenseItems.normalize(group, children + deleted)
        return all.take(children.size) to all.drop(children.size)
    }

    /** 実行すると、家計簿に既にある行が作り直される(編集が消える)計画か。 */
    fun discardsExisting(plan: ConfirmPlan, existingGroups: List<ExpenseGroup>, existingExpenses: List<Expense>): Boolean =
        plan is ConfirmPlan.Rebuild && (existingGroups.isNotEmpty() || existingExpenses.isNotEmpty())

    /** 家計簿で直した欄がある品目の支出か(消した行は別扱い: 作り直しでも消したまま残す)。再確定で作り直されると失われる内容。 */
    fun hasUserChanges(e: Expense): Boolean =
        e.amountEdited || e.categoryEdited || e.nameEdited || e.memoEdited

    /** 家計簿で直した欄があるグループか。 */
    fun hasUserChanges(g: ExpenseGroup): Boolean = g.amountEdited || g.categoryEdited || g.memoEdited || g.timeEdited

    /**
     * 実行すると失われる「家計簿で直した」件数。
     * @param remaining 保存のあとも残る既存の支出(作り直す計画なら、これらが全部消える)
     * @param removed 保存で品目が消えるために先に消える既存の支出
     */
    fun lostEditedCount(
        plan: ConfirmPlan,
        existingGroups: List<ExpenseGroup>,
        remaining: List<Expense>,
        removed: List<Expense>,
    ): Int {
        val lostByRemoval = removed.count { hasUserChanges(it) }
        return when (plan) {
            is ConfirmPlan.Rebuild ->
                existingGroups.count { hasUserChanges(it) } + remaining.count { hasUserChanges(it) } + lostByRemoval
            // 更新でも消える行(品名が空の1品目が品目の行に置き換わるときなど)に直した欄があれば数える
            is ConfirmPlan.UpdateGroup -> {
                val deleted = plan.deleteIds.toHashSet()
                remaining.count { it.id in deleted && hasUserChanges(it) } + lostByRemoval
            }
        }
    }

    /** 品目の支出: 編集済みでない欄だけレシート(want)の値にする。金額不明の印はレシートに従う。 */
    internal fun mergeItem(have: Expense, want: Expense): Expense = have.copy(
        amount = if (have.amountEdited) have.amount else want.amount,
        category = if (have.categoryEdited) have.category else want.category,
        name = if (have.nameEdited) have.name else want.name,
        memo = if (have.memoEdited) have.memo else want.memo,
        date = if (have.timeEdited) have.date else want.date,
        time = if (have.timeEdited) have.time else want.time,
        unknownAmount = want.unknownAmount,
    )

    /** グループ: 金額(=合計)・日時・メモ・カテゴリを、編集済みでない欄だけ更新する(メモの既定は空。店名は支払いに持つ)。 */
    internal fun mergeGroup(have: ExpenseGroup, want: ExpenseGroup): ExpenseGroup = have.copy(
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

    /**
     * plan を保存したあとの品目 id を、incoming(画面の並び)と同じ順に返す。plan と同じ規則で、更新の行は id をそのまま使う。
     * 新規の行は plan.inserts の順に、保存後に増えた id を小さい順に当てる(Room は挿入を順に採番するため)。
     * savedIds は保存後にDBにある、このレシートの品目の id 全部。
     */
    fun savedIdsInOrder(incoming: List<ReceiptItem>, plan: ItemSavePlan, savedIds: List<Long>): List<Long> {
        val updateIds = plan.updates.mapTo(HashSet()) { it.id }
        val kept = HashSet<Long>()
        val newIds = savedIds.filter { it !in updateIds }.sorted().iterator()
        return incoming.map { item ->
            if (item.id != 0L && item.id in updateIds && kept.add(item.id)) item.id else newIds.next()
        }
    }
}
