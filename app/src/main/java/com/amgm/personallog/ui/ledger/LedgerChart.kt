package com.amgm.personallog.ui.ledger

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * 積み上げ棒グラフの階層。上の階層の棒をタップすると1段下の階層の棒に分解される。
 * 年: 直近6か月(表示の終わりの月まで、古い順) / 月: その月の週(月曜始まり・週内に1日でも含まれれば表示、棒の集計は月内の日だけ) /
 * 週: その週の7日 / 日: その日の24時間(支払った時刻で振り分け)。
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

    /** 直近の月の数(年の階層で表示する月数)。終わりの月を含めて6か月。 */
    private const val YEAR_WINDOW_MONTHS = 6

    /**
     * focus を含む階層の期間(両端含む)。
     * 年: focus の月から5か月前の1日 〜 focus の月の末日(直近6か月)。
     */
    fun containerRange(level: ChartLevel, focus: LocalDate): Pair<LocalDate, LocalDate> = when (level) {
        ChartLevel.YEAR -> {
            val last = YearMonth.from(focus)
            last.minusMonths((YEAR_WINDOW_MONTHS - 1).toLong()).atDay(1) to last.atEndOfMonth()
        }
        ChartLevel.MONTH -> YearMonth.from(focus).atDay(1) to YearMonth.from(focus).atEndOfMonth()
        ChartLevel.WEEK -> {
            val mon = focus.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            mon to mon.plusDays(6)
        }
        ChartLevel.DAY -> focus to focus
    }

    /** 階層ごとの前後移動。◀▶ ボタン用。年は6か月ずつ動く。 */
    fun shift(level: ChartLevel, focus: LocalDate, delta: Long): LocalDate = when (level) {
        ChartLevel.YEAR -> focus.plusMonths(YEAR_WINDOW_MONTHS * delta)
        ChartLevel.MONTH -> focus.plusMonths(delta)
        ChartLevel.WEEK -> focus.plusWeeks(delta)
        ChartLevel.DAY -> focus.plusDays(delta)
    }

    /**
     * 表示中の期間の見出し。
     * 年: 終わりの月のみ("〜2026年10月")。月: 週の範囲("9/28週〜11/1週"、週は月曜〜日曜の全体)。
     */
    fun containerLabel(level: ChartLevel, focus: LocalDate): String {
        val (s, e) = containerRange(level, focus)
        return when (level) {
            ChartLevel.YEAR -> "〜${focus.year}年${focus.monthValue}月"
            ChartLevel.MONTH -> {
                val firstFullMon = s.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val lastFullSun = e.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(6)
                "${md(firstFullMon)}週〜${md(lastFullSun)}週"
            }
            ChartLevel.WEEK -> "${md(s)}〜${md(e)}"
            ChartLevel.DAY -> LedgerLogic.dayLabel(focus.toString())
        }
    }

    /**
     * 階層 level を focus の期間で表示したときの棒の並び。
     * 年: 直近6か月を古い順に(終わりの月が最後)。月: 月内の週(週の範囲ラベル、期間は月内に切り詰め)。
     * 週: 月曜から日曜の7日。日: 0時〜23時の24本(分解不可)。
     */
    fun bars(level: ChartLevel, focus: LocalDate, entries: List<LedgerEntry>): List<ChartBar> {
        val (cs, ce) = containerRange(level, focus)
        return when (level) {
            ChartLevel.YEAR -> {
                val last = YearMonth.from(focus)
                (YEAR_WINDOW_MONTHS - 1 downTo 0).map { k ->
                    val month = last.minusMonths(k.toLong())
                    bar("${month.monthValue}月", month.atDay(1), month.atEndOfMonth(), entries, drillable = true)
                }
            }
            ChartLevel.MONTH -> weeksOf(cs, ce).map { (s, e) ->
                val fullMon = s.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val fullSun = fullMon.plusDays(6)
                bar("${md(fullMon)}〜${md(fullSun)}", s, e, entries, drillable = true)
            }
            ChartLevel.WEEK -> (0..6).map { i ->
                val d = cs.plusDays(i.toLong())
                bar("${md(d)}(${WEEKDAY_CHARS[i]})", d, d, entries, drillable = true)
            }
            ChartLevel.DAY -> (0..23).map { h ->
                val segs = hourSegments(entries, cs, h)
                ChartBar("${h}:", cs, cs, segs, segs.sumOf { it.amount }, drillable = false)
            }
        }
    }

    /** その日の hour 時台(hour:00〜hour:59)の支出をカテゴリごとに合計し、金額の多い順にする。 */
    fun hourSegments(entries: List<LedgerEntry>, day: LocalDate, hour: Int): List<ChartSegment> {
        val d = day.toString()
        return entries
            .filter { it.date == d && hourOf(it) == hour }
            .groupBy { it.category }
            .map { (c, list) -> ChartSegment(c, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }
    }

    /**
     * 行の時刻 "HH:mm" の時(0〜23)。
     * 時刻が空(レシートに時刻が無かった)は 1日の始まりとして 0 時に入れる。値は補完しない。
     * 形式が不正な値は -1(どの時台にも入らない)。
     */
    fun hourOf(entry: LedgerEntry): Int {
        if (entry.time.isEmpty()) return 0
        return entry.time.take(2).toIntOrNull()?.takeIf { it in 0..23 } ?: -1
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

    /**
     * 月内の週(月曜始まり)。週内に月の日が1日でも含まれれば対象。
     * 返す期間は月の範囲に切り詰めたもの(集計用)。ラベル用の全体の週は bars 側で求める。
     */
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
