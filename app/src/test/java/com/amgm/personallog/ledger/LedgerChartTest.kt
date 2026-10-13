package com.amgm.personallog.ledger

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.ui.ledger.ChartLevel
import com.amgm.personallog.ui.ledger.LedgerChart
import com.amgm.personallog.ui.ledger.LedgerLogic
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LedgerChartTest {
    private fun e(id: Long, date: String, amount: Int, cat: String) =
        Expense(id = id, date = date, amount = amount, category = cat, createdAt = 0)

    private fun entries(vararg list: Expense) = LedgerLogic.buildEntries(list.toList(), emptyList())

    private val oct14 = LocalDate.of(2026, 10, 14)

    @Test
    fun containerRange_week_isMondayToSunday() {
        // 2026-10-14 は水曜。週は 10/12(月)〜10/18(日)
        assertEquals(LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 18), LedgerChart.containerRange(ChartLevel.WEEK, oct14))
    }

    @Test
    fun monthBars_weeksIntersectingMonth_clippedToMonth() {
        // 2026-10 は 10/1(木)〜10/31(土)。月曜始まりの週は 5 つ。先頭は 10/1〜10/4、末尾は 10/26〜10/31 に切り詰め
        val bars = LedgerChart.bars(ChartLevel.MONTH, oct14, emptyList())
        assertEquals(5, bars.size)
        assertEquals(LocalDate.of(2026, 10, 1), bars.first().start)
        assertEquals(LocalDate.of(2026, 10, 4), bars.first().end)
        assertEquals(LocalDate.of(2026, 10, 26), bars.last().start)
        assertEquals(LocalDate.of(2026, 10, 31), bars.last().end)
        assertEquals(true, bars.all { it.drillable })
    }

    @Test
    fun yearBars_twelveMonths_drillToMonth() {
        val bars = LedgerChart.bars(ChartLevel.YEAR, oct14, emptyList())
        assertEquals(12, bars.size)
        assertEquals("10月", bars[9].label)
        assertEquals(LocalDate.of(2026, 10, 1), bars[9].start)
        assertEquals(LocalDate.of(2026, 10, 31), bars[9].end)
    }

    @Test
    fun segments_sumByCategory_andSortByAmountDesc() {
        val list = entries(
            e(1, "2026-10-03", 1000, "食費"),
            e(2, "2026-10-20", 500, "娯楽"),
            e(3, "2026-10-05", 2000, "食費"),
            e(4, "2026-09-30", 9999, "食費"), // 範囲外
        )
        val segs = LedgerChart.segments(list, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(listOf("食費", "娯楽"), segs.map { it.category })
        assertEquals(listOf(3000L, 500L), segs.map { it.amount })
    }

    @Test
    fun weekBars_sevenDays_monday_first_drillToDay() {
        val bars = LedgerChart.bars(ChartLevel.WEEK, oct14, emptyList())
        assertEquals(7, bars.size)
        assertEquals(LocalDate.of(2026, 10, 12), bars.first().start)
        assertEquals("12(月)", bars.first().label)
        assertEquals(true, bars.all { it.drillable })
    }

    @Test
    fun legend_percentAndYen_forContainer() {
        val list = entries(e(1, "2026-10-01", 3000, "食費"), e(2, "2026-10-02", 1000, "娯楽"))
        val legend = LedgerChart.legend(list, ChartLevel.MONTH, oct14)
        assertEquals(listOf("食費", "娯楽"), legend.map { it.category })
        assertEquals(listOf(75, 25), legend.map { it.percent })
    }

    @Test
    fun shift_movesByLevelUnit() {
        assertEquals(LocalDate.of(2027, 10, 14), LedgerChart.shift(ChartLevel.YEAR, oct14, 1))
        assertEquals(LocalDate.of(2026, 11, 14), LedgerChart.shift(ChartLevel.MONTH, oct14, 1))
        assertEquals(LocalDate.of(2026, 10, 21), LedgerChart.shift(ChartLevel.WEEK, oct14, 1))
        assertEquals(LocalDate.of(2026, 10, 13), LedgerChart.shift(ChartLevel.DAY, oct14, -1))
    }

    @Test
    fun childLevel_chain() {
        assertEquals(ChartLevel.MONTH, ChartLevel.YEAR.child)
        assertEquals(ChartLevel.WEEK, ChartLevel.MONTH.child)
        assertEquals(ChartLevel.DAY, ChartLevel.WEEK.child)
        assertEquals(null, ChartLevel.DAY.child)
    }

    private fun t(id: Long, date: String, time: String, amount: Int, cat: String) =
        Expense(id = id, date = date, time = time, amount = amount, category = cat, createdAt = 0)

    @Test
    fun monthBars_canHaveSixWeeks() {
        // 2026-08 は 8/1(土)始まり。月曜始まりで 7/27〜8/2 から 8/31 まで 6 週
        val bars = LedgerChart.bars(ChartLevel.MONTH, LocalDate.of(2026, 8, 1), emptyList())
        assertEquals(6, bars.size)
        assertEquals(LocalDate.of(2026, 8, 1), bars.first().start)
        assertEquals(LocalDate.of(2026, 8, 2), bars.first().end)
        assertEquals(LocalDate.of(2026, 8, 31), bars.last().end)
    }

    @Test
    fun dayBars_are24Hours_splitByPurchaseTime() {
        val list = entries(
            t(1, "2026-10-14", "09:05", 300, "食費"),
            t(2, "2026-10-14", "09:59", 200, "外食"),
            t(3, "2026-10-14", "18:00", 500, "食費"),
            t(4, "2026-10-13", "09:30", 9999, "食費"), // 別の日
        )
        val bars = LedgerChart.bars(ChartLevel.DAY, oct14, list)
        assertEquals(24, bars.size)
        assertEquals(false, bars.all { it.drillable })
        assertEquals(500L, bars[9].total)
        assertEquals(listOf("食費", "外食"), bars[9].segments.map { it.category })
        assertEquals(500L, bars[18].total)
        assertEquals(0L, bars[0].total)
    }

    @Test
    fun hourOf_blankTime_isStartOfDay_butNotFilled() {
        // 時刻が無い行は 0 時の棒に入るが、行の time は空のまま(補完しない)
        val entry = LedgerLogic.buildEntries(listOf(t(1, "2026-10-14", "", 700, "食費")), emptyList()).first()
        assertEquals(0, LedgerChart.hourOf(entry))
        assertEquals("", entry.time)
        val bars = LedgerChart.bars(ChartLevel.DAY, oct14, listOf(entry))
        assertEquals(700L, bars[0].total)
    }

    @Test
    fun hourOf_invalidTime_isMinusOne() {
        assertEquals(-1, LedgerChart.hourOf(LedgerLogic.buildEntries(listOf(t(1, "2026-10-14", "--:--", 1, "食費")), emptyList()).first()))
    }
}
