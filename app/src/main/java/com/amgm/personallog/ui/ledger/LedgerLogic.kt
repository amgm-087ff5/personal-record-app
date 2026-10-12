package com.amgm.personallog.ui.ledger

import com.amgm.personallog.data.model.CategoryTotal
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * 家計簿の一覧に並ぶ1行。単独の支出か、支払いグループ(品目をまとめたもの)。
 * amount / category はこの行が家計簿の合計・カテゴリ集計に入る値(グループは支払い金額)。
 */
sealed interface LedgerEntry {
    val date: String
    val amount: Long
    val category: String
    val createdAt: Long

    data class Single(val expense: Expense) : LedgerEntry {
        override val date: String get() = expense.date
        override val amount: Long get() = expense.amount.toLong()
        override val category: String get() = expense.category
        override val createdAt: Long get() = expense.createdAt
    }

    /** items は品目(支出)。amount は支払い金額(正)。 */
    data class Group(val group: ExpenseGroup, val items: List<Expense>) : LedgerEntry {
        override val date: String get() = group.date
        override val amount: Long get() = group.amount.toLong()
        override val category: String get() = group.category
        override val createdAt: Long get() = group.createdAt

        /** 品目の合計。 */
        val itemSum: Long get() = items.sumOf { it.amount.toLong() }

        /** 支払い金額 - 品目の合計。0 なら一致。 */
        val difference: Long get() = amount - itemSum
    }
}

/** 日付ごとにまとめた行。 */
data class DayGroup(val date: String, val total: Long, val items: List<LedgerEntry>)

data class CategoryShare(val category: String, val total: Long, val percent: Int)

data class MonthTotal(val yearMonth: String, val total: Long)

/** 入力フォームの検証結果。エラーが無ければ date / amount / category が入る。 */
data class ExpenseValidation(
    val dateError: String? = null,
    val amountError: String? = null,
    val categoryError: String? = null,
    val date: String? = null,
    val amount: Int? = null,
    val category: String? = null,
) {
    val isValid: Boolean get() = dateError == null && amountError == null && categoryError == null
}

/** 家計簿画面用の純粋ロジック(JVMでテスト可能)。domain/LedgerCalc は変更せず、足りない分をここに置く。 */
object LedgerLogic {
    /** "2026-10" に delta か月を足す。 */
    fun shiftMonth(yearMonth: String, delta: Int): String =
        YearMonth.parse(yearMonth).plusMonths(delta.toLong()).toString()

    fun monthLabel(yearMonth: String): String {
        val ym = YearMonth.parse(yearMonth)
        return "${ym.year}年${ym.monthValue}月"
    }

    /** 末尾が yearMonth の直近 count か月(古い順)。 */
    fun recentMonths(yearMonth: String, count: Int = 6): List<String> =
        (count - 1 downTo 0).map { shiftMonth(yearMonth, -it) }

    /**
     * 支出と支払いグループを一覧用の行にする。グループの品目は group の行に入り、単独の行には入らない
     * (合計が二重にならない)。並びは日付の降順、同じ日は作成の新しい順。
     */
    fun buildEntries(expenses: List<Expense>, groups: List<ExpenseGroup>): List<LedgerEntry> {
        val groupIds = groups.map { it.id }.toSet()
        val itemsByGroup = expenses
            .filter { e -> e.groupId != null && e.groupId in groupIds }
            .groupBy { it.groupId!! }
        val singles = expenses
            .filter { e -> e.groupId == null || e.groupId !in groupIds }
            .map { LedgerEntry.Single(it) }
        val grouped = groups.map { LedgerEntry.Group(it, itemsByGroup[it.id].orEmpty().sortedBy { x -> x.id }) }
        return (singles + grouped).sortedWith(
            compareByDescending<LedgerEntry> { it.date }.thenByDescending { it.createdAt },
        )
    }

    /** 日付降順にグループ化。グループ内は entries の順序を保つ。 */
    fun groupByDate(entries: List<LedgerEntry>): List<DayGroup> =
        entries.groupBy { it.date }
            .map { (date, list) -> DayGroup(date, list.sumOf { it.amount }, list) }
            .sortedByDescending { it.date }

    /** 行のカテゴリ別合計(金額の多い順)。 */
    fun totalsByCategory(entries: List<LedgerEntry>): List<CategoryTotal> =
        entries.groupBy { it.category }
            .map { (cat, list) -> CategoryTotal(cat, list.sumOf { it.amount }) }
            .sortedByDescending { it.total }

    /** カテゴリ別の割合(合計100%に丸める必要はなく、四捨五入)。合計0なら空。 */
    fun categoryShares(totals: List<CategoryTotal>): List<CategoryShare> {
        val sum = totals.sumOf { it.total }
        if (sum <= 0L) return emptyList()
        return totals.sortedByDescending { it.total }
            .map { CategoryShare(it.category, it.total, Math.round(it.total * 100.0 / sum).toInt()) }
    }

    /** months の各月の合計(date の先頭7文字で判定)。支出が無い月は0。 */
    fun monthlyTotals(entries: List<LedgerEntry>, months: List<String>): List<MonthTotal> {
        val byMonth = entries.groupBy { it.date.take(7) }
        return months.map { m -> MonthTotal(m, byMonth[m].orEmpty().sumOf { it.amount }) }
    }

    fun filterMonth(expenses: List<Expense>, yearMonth: String): List<Expense> =
        expenses.filter { it.date.startsWith(yearMonth) }

    /** "2026-10-05" -> "10月5日(月)" */
    fun dayLabel(date: String): String = try {
        val d = LocalDate.parse(date)
        val dow = "月火水木金土日"[d.dayOfWeek.value - 1]
        "${d.monthValue}月${d.dayOfMonth}日($dow)"
    } catch (e: DateTimeParseException) {
        date
    }

    fun formatYen(amount: Long): String = "¥" + String.format(Locale.US, "%,d", amount)

    /** グラフ下の短い表記。1万円以上は "1.2万"。 */
    fun compactYen(amount: Long): String =
        if (amount >= 10_000L) {
            val v = amount / 1000 / 10.0 // 小数1桁(切り捨て)
            (if (v == Math.floor(v)) v.toLong().toString() else v.toString()) + "万"
        } else {
            amount.toString()
        }

    /** 新規入力の初期日付。表示月が今月なら今日、そうでなければ表示月の1日。 */
    fun defaultDate(yearMonth: String, today: LocalDate): String =
        if (YearMonth.from(today).toString() == yearMonth) today.toString() else "$yearMonth-01"

    fun validate(dateText: String, amountText: String, category: String?): ExpenseValidation {
        var dateError: String? = null
        var amountError: String? = null
        var categoryError: String? = null
        var date: String? = null
        var amount: Int? = null

        try {
            date = LocalDate.parse(dateText.trim()).toString()
        } catch (e: DateTimeParseException) {
            dateError = "日付は yyyy-MM-dd の形式で入力してください"
        }

        val t = amountText.trim()
        if (t.isEmpty()) {
            amountError = "金額を入力してください"
        } else if (!t.all { it in '0'..'9' }) {
            amountError = "金額は正の整数で入力してください"
        } else {
            val n = t.toIntOrNull()
            if (n == null) amountError = "金額が大きすぎます"
            else if (n <= 0) amountError = "金額は1円以上にしてください"
            else amount = n
        }

        if (category.isNullOrBlank()) categoryError = "カテゴリを選択してください"

        return ExpenseValidation(dateError, amountError, categoryError, date, amount, category)
    }
}
