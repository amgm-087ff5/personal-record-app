package com.amgm.personallog.ui.ledger

import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup

/** 手入力の品目つき支払いの、入力途中の1品目。数値は文字列のまま。 */
data class DraftItem(
    val name: String = "",
    val amount: String = "",
    val category: String = Categories.UNCATEGORIZED,
    val memo: String = "",
) {
    /** 何も入力していない行(保存時に捨てる)。カテゴリは見ない。 */
    val isBlank: Boolean get() = name.isBlank() && amount.isBlank() && memo.isBlank()
}

/** 手入力の品目つき支払いの検証結果。エラーが無ければ group と items が入る。 */
data class DraftPaymentResult(
    val errors: List<String>,
    val group: ExpenseGroup? = null,
    val items: List<Expense> = emptyList(),
) {
    val isValid: Boolean get() = errors.isEmpty() && group != null
}

object DraftPayment {
    /** 空欄でない品目の金額(円)の合計。数字として読めない行は 0 として数える。 */
    fun itemsSum(items: List<DraftItem>): Long =
        items.filterNot { it.isBlank }.sumOf { it.amount.trim().toLongOrNull() ?: 0L }

    /** 合計欄に入れる値の文字列。品目の合計が1円以上のときだけ。 */
    fun sumText(items: List<DraftItem>): String? = itemsSum(items).takeIf { it > 0 }?.toString()

    /**
     * 入力を検証し、グループと品目の支出を作る。groupId は未設定(Repository が入れる)。
     * 品目は空欄の行を捨てて1件以上、金額は1円以上の整数。合計は1円以上。
     * グループのカテゴリは、金額が最も高い品目のカテゴリ。
     */
    fun build(
        dateText: String,
        timeText: String,
        totalText: String,
        memo: String,
        drafts: List<DraftItem>,
        now: Long,
    ): DraftPaymentResult {
        val errors = ArrayList<String>()
        val base = LedgerLogic.validate(dateText, timeText, totalText, "-")
        base.dateError?.let { errors += it }
        base.timeError?.let { errors += it }
        base.amountError?.let { errors += "合計: $it" }
        val rows = drafts.filterNot { it.isBlank }
        if (rows.isEmpty()) errors += "品目を1つ以上入力してください"
        val amounts = rows.mapIndexed { i, r ->
            val t = r.amount.trim()
            val n = t.takeIf { s -> s.isNotEmpty() && s.all { c -> c in '0'..'9' } }?.toIntOrNull()
            if (n == null || n <= 0) {
                errors += "品目${i + 1}の金額は1円以上の整数で入力してください"
                0
            } else {
                n
            }
        }
        if (errors.isNotEmpty()) return DraftPaymentResult(errors)
        val date = base.date!!
        val time = base.time!!
        val top = rows.indices.maxByOrNull { amounts[it] }
        val group = ExpenseGroup(
            date = date,
            time = time,
            amount = base.amount!!,
            category = top?.let { rows[it].category } ?: Categories.UNCATEGORIZED,
            memo = memo.trim(),
            createdAt = now,
        )
        val items = rows.mapIndexed { i, r ->
            Expense(
                date = date,
                time = time,
                amount = amounts[i],
                category = r.category,
                name = r.name.trim(),
                memo = r.memo.trim(),
                createdAt = now,
            )
        }
        return DraftPaymentResult(emptyList(), group, items)
    }
}
