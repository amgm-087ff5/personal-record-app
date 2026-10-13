package com.amgm.personallog.ui.ledger

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * 積み上げ棒グラフの階層。上の階層の棒をタップすると1段下の階層の棒に分解される。
 * 年: 1年の中の12か月 / 月: その月の週(月曜始まり・週内に1日でも含まれれば表示、月内の日だけで集計) /
 * 週: その週の7日 / 日: その日の合計(時刻データが無いため時間別には分けられない)。
 */
enum class ChartLevel(val label: String) {
    YEAR("年"),
    MONTH("月"),
    WEEK("週"),
    DAY("日"),
    ;

    /** タップで移動する1段下の階層。DAY は下がない。 */
    val child: ChartLevel?
        get() = when (this) {
            YEAR -> MONTH
            MONTH -> WEEK
            WEEK -> DAY
            DAY -> null
        }
}

/** 棒の中の1区切り(カテゴリ1つぶん)。 */
data class ChartSegment(val category: String, val amount: Long)

/** 棒1本。start..end(両端含む)の支出を category ごとに積み上げたもの。 */
data class ChartBar(
    val label: String,
    val start: LocalDate,
    val end: LocalDate,
    val segments: List<ChartSegment>,
    val total: Long,
    val drillable: Boolean,
)

/** 積み上げ棒グラフ用の純粋ロジック(JVMでテスト可能)。 */
object LedgerChart {
    private const val WEEKDAY_CHARS = "月火水木金土日"

    /** focus を含む階層の期間(両端含む)。 */
    fun containerRange(level: ChartLevel, focus: LocalDate): Pair<LocalDate, LocalDate> = when (level) {
        ChartLevel.YEAR -> LocalDate.of(focus.year, 1, 1) to LocalDate.of(focus.year, 12, 31)
        ChartLevel.MONTH -> YearMonth.from(focus).atDay(1) to YearMonth.from(focus).atEndOfMonth()
        ChartLevel.WEEK -> {
            val mon = focus.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            mon to mon.plusDays(6)
        }
        ChartLevel.DAY -> focus to focus
    }

    /** 階層ごとの前後移動。◀▶ ボタン用。 */
    fun shift(level: ChartLevel, focus: LocalDate, delta: Long): LocalDate = when (level) {
        ChartLevel.YEAR -> focus.plusYears(delta)
        ChartLevel.MONTH -> focus.plusMonths(delta)
        ChartLevel.WEEK -> focus.plusWeeks(delta)
        ChartLevel.DAY -> focus.plusDays(delta)
    }

    /** 表示中の期間の見出し。 */
    fun containerLabel(level: ChartLevel, focus: LocalDate): String {
        val (s, e) = containerRange(level, focus)
        return when (level) {
            ChartLevel.YEAR -> "${s.year}年"
            ChartLevel.MONTH -> "${s.year}年${s.monthValue}月"
            ChartLevel.WEEK -> "${md(s)}〜${md(e)}"
            ChartLevel.DAY -> LedgerLogic.dayLabel(focus.toString())
        }
    }

    /** 階層 level を focus の期間で表示したときの棒の並び。 */
    fun bars(level: ChartLevel, focus: LocalDate, entries: List<LedgerEntry>): List<ChartBar> {
        val (cs, ce) = containerRange(level, focus)
        return when (level) {
            ChartLevel.YEAR -> (1..12).map { m ->
                val s = LocalDate.of(cs.year, m, 1)
                bar("${m}月", s, YearMonth.of(cs.year, m).atEndOfMonth(), entries, drillable = true)
            }
            ChartLevel.MONTH -> weeksOf(cs, ce).map { (s, e) ->
                bar(md(s), s, e, entries, drillable = true)
            }
            ChartLevel.WEEK -> (0..6).map { i ->
                val d = cs.plusDays(i.toLong())
                bar("${d.dayOfMonth}(${WEEKDAY_CHARS[i]})", d, d, entries, drillable = true)
            }
            ChartLevel.DAY -> listOf(bar(md(cs), cs, ce, entries, drillable = false))
        }
    }

    /** 表示中の期間全体のカテゴリ別内訳(円と割合)。凡例に使う。 */
    fun legend(entries: List<LedgerEntry>, level: ChartLevel, focus: LocalDate): List<CategoryShare> {
        val (s, e) = containerRange(level, focus)
        return LedgerLogic.categoryShares(LedgerLogic.totalsByCategory(inRange(entries, s, e)))
    }

    /** start..end(両端含む)の支出をカテゴリごとに合計し、金額の多い順にする。 */
    fun segments(entries: List<LedgerEntry>, start: LocalDate, end: LocalDate): List<ChartSegment> =
        inRange(entries, start, end)
            .groupBy { it.category }
            .map { (c, list) -> ChartSegment(c, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

    /** date(yyyy-MM-dd)が start..end に入る行。ISO 形式なので文字列比較で足りる。 */
    fun inRange(entries: List<LedgerEntry>, start: LocalDate, end: LocalDate): List<LedgerEntry> {
        val s = start.toString()
        val e = end.toString()
        return entries.filter { it.date >= s && it.date <= e }
    }

    private fun bar(label: String, s: LocalDate, e: LocalDate, entries: List<LedgerEntry>, drillable: Boolean): ChartBar {
        val segs = segments(entries, s, e)
        return ChartBar(label, s, e, segs, segs.sumOf { it.amount }, drillable)
    }

    /** 月内の週(月曜始まり)。週内に月の日が1日でも含まれれば対象。期間は月の範囲に切り詰める。 */
    private fun weeksOf(monthStart: LocalDate, monthEnd: LocalDate): List<Pair<LocalDate, LocalDate>> {
        val out = mutableListOf<Pair<LocalDate, LocalDate>>()
        var mon = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        while (!mon.isAfter(monthEnd)) {
            val sun = mon.plusDays(6)
            val s = if (mon.isBefore(monthStart)) monthStart else mon
            val e = if (sun.isAfter(monthEnd)) monthEnd else sun
            out += s to e
            mon = mon.plusWeeks(1)
        }
        return out
    }

    private fun md(d: LocalDate): String = "${d.monthValue}/${d.dayOfMonth}"
}
