package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.domain.split.YenAllocator

/**
 * 自分の支出(expense_groups)とその品目(expenses)の規則(純Kotlin)。
 *
 * 規則(利用者の決定 2026-10-28):
 *  - 支出には品目が必ず1つ以上ある。品目の無い支払いの支出は、品名が空の1品目を持つ。
 *  - 品目が1つだけのとき、その品目の金額は支出の金額と同じ(支出の金額を直すと追従する)。
 *    「1つだけ」は家計簿で消した品目(deletedByUser)も含めた行の数で数える
 *    (品目を消して1つ残ったときに、残った品目の金額を書き換えない。消した品目の金額は差額として支出のカテゴリに入る)。
 *    ただし品目を全部消して足された品名が空の1品目(receiptItemId なし)は、消した品目があっても支出に従う。
 *  - 支払いの品目に対応しない1品目(receiptItemId なし = 品目の無い支払いの品目)は、金額に加えて日時も支出に従い、
 *    カテゴリ・メモも品目の側で直していなければ(categoryEdited・memoEdited が false)支出に従う
 *    (カテゴリ別の集計は品目のカテゴリで数えるため、支出のカテゴリを直したら品目も合わせる)。品名は品目だけが持つ。
 *  - 金額が不明の品目は、支出の金額 − 分かっている品目(消していないもの)の合計 を均等に分けた金額にする(UnknownAmounts)。
 */
object ExpenseItems {
    /** 品目の金額を規則に合わせる。並びは変えない。変える必要が無ければ同じ内容を返す。 */
    fun normalize(group: ExpenseGroup, items: List<Expense>): List<Expense> {
        val visible = items.indices.filter { !items[it].deletedByUser }
        // 1品目: 行が1つだけ、または消していない行が品目の無い支払いの1品目(receiptItemId なし)だけ
        val only = visible.singleOrNull()?.takeIf { items.size == 1 || items[it].receiptItemId == null }
        if (only != null) {
            val e = items[only]
            val fixed = if (e.receiptItemId == null) {
                e.copy(
                    amount = group.amount,
                    category = if (e.categoryEdited) e.category else group.category,
                    memo = if (e.memoEdited) e.memo else group.memo,
                    date = group.date,
                    time = group.time,
                    unknownAmount = false,
                )
            } else {
                e.copy(amount = group.amount)
            }
            return items.toMutableList().also { it[only] = fixed }
        }
        val unknownIdx = visible.filter { items[it].unknownAmount }
        if (unknownIdx.isEmpty()) return items
        val shares = UnknownAmounts.splitEqually(group.amount.toLong() - knownSum(items), unknownIdx.size)
        val out = items.toMutableList()
        unknownIdx.forEachIndexed { n, i -> out[i] = out[i].copy(amount = shares[n].toInt()) }
        return out
    }

    /** 分かっている品目(不明でなく、消していない)の合計。 */
    fun knownSum(items: List<Expense>): Long = items.filter { !it.unknownAmount && !it.deletedByUser }.sumOf { it.amount.toLong() }

    /**
     * 支出の金額と品目の不一致(支出の金額 − 分かっている品目の合計)。エラーでなければ null(UnknownAmounts.mismatch と同じ規則)。
     * 品目が1つだけなら常に一致(金額は支出に追従する)。
     */
    fun mismatch(group: ExpenseGroup, items: List<Expense>): Long? {
        if (items.size <= 1) return null
        val visible = items.filterNot { it.deletedByUser }
        return UnknownAmounts.mismatch(group.amount.toLong(), knownSum(items), visible.any { it.unknownAmount })
    }

    /**
     * 支出の画面での「未入力」の品目: 金額が0円で、不明でも消してもいないもの
     * (支出の品目の金額は整数で持つので、空欄は0円として保存される)。
     */
    fun isBlank(e: Expense): Boolean = e.amount == 0 && !e.unknownAmount && !e.deletedByUser

    /**
     * amount を重み(weights。品目ごとの税込円)の比で配る(最大剰余。合計は必ず amount)。並びは weights と同じ。
     * 負の重み(値引き)は0として扱う。重みが全部0なら null(配れない)。
     * 端数の順番は割り勘の YenAllocator と同じ(余りの大きい順 → 前の品目)。
     */
    fun allocateByRatio(amount: Long, weights: List<Long>): List<Long>? {
        val w = weights.map { it.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() }
        if (w.none { it > 0 }) return null
        val keyed = w.mapIndexed { i, x -> i.toLong() to x }
        val order = w.indices.associate { it.toLong() to it }
        val res = YenAllocator.allocate(amount.toInt(), keyed, emptySet(), order)
        return w.indices.map { (res[it.toLong()] ?: 0).toLong() }
    }
}
