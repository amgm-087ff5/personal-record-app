package com.amgm.personallog.ledger

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.ui.ledger.ChartBar
import com.amgm.personallog.ui.ledger.ChartLevel
import com.amgm.personallog.ui.ledger.ChartSegment
import com.amgm.personallog.ui.ledger.LedgerChart
import com.amgm.personallog.ui.ledger.LedgerLogic
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** グループの日時を別の月へ動かしたとき、月・日の振り分けがグループの日付に従い、二重に数えないこと。 */
class MonthBoundaryTest {
    private fun child(id: Long, date: String, amount: Int, cat: String) =
        Expense(id = id, date = date, amount = amount, category = cat, groupId = 7, createdAt = 1)

    private fun monthTotals(expenses: List<Expense>, groups: List<ExpenseGroup>): Map<String, Long> {
        val entries = LedgerLogic.buildEntries(expenses, groups)
        return LedgerLogic.monthlyTotals(entries, listOf("2026-10", "2026-11")).associate { it.yearMonth to it.total }
    }

    @Test
    fun groupMovedToNextMonth_countsOnlyInTheGroupsMonth_evenIfItemDatesAreStale() {
        val items = listOf(child(1, "2026-10-31", 400, "食費"), child(2, "2026-10-31", 300, "日用品"))
        val single = Expense(id = 3, date = "2026-10-20", amount = 50, category = "その他", createdAt = 1)
        val movedGroup = ExpenseGroup(id = 7, date = "2026-11-01", amount = 1000, category = "食費", createdAt = 1)
        // 品目の日付が古いまま(10月)でも、10月に単独の行として残らない
        val t = monthTotals(items + single, listOf(movedGroup))
        assertEquals(50L, t["2026-10"])
        assertEquals(1000L, t["2026-11"])
        assertEquals(1050L, t.values.sum())
    }

    @Test
    fun staleChildrenWhoseGroupIsOutOfRange_areNotSingles() {
        // 10月の範囲を読んだとき、品目は(日付が10月でも)グループが範囲外なら読まれない。読まれても数えない
        val items = listOf(child(1, "2026-10-31", 400, "食費"))
        assertEquals(0L, monthTotals(items, emptyList())["2026-10"])
    }

    @Test
    fun beforeAndAfterMove_bothMonthsAreCorrect() {
        val items = listOf(child(1, "2026-10-31", 400, "食費"))
        val before = ExpenseGroup(id = 7, date = "2026-10-31", amount = 500, category = "食費", createdAt = 1)
        assertEquals(500L, monthTotals(items, listOf(before))["2026-10"])
        assertEquals(0L, monthTotals(items, listOf(before))["2026-11"])
        val after = before.copy(date = "2026-11-02")
        val moved = items.map { it.copy(date = "2026-11-02") }
        assertEquals(0L, monthTotals(moved, listOf(after))["2026-10"])
        assertEquals(500L, monthTotals(moved, listOf(after))["2026-11"])
    }

    @Test
    fun barHeight_usesPositiveSegmentsOnly() {
        val bar = ChartBar(
            "x", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
            listOf(ChartSegment("食費", 1000), ChartSegment("日用品", -300)), 700, drillable = true,
        )
        assertEquals(700L, bar.total) // 合計の数字は今のまま
        assertEquals(1000L, bar.drawnTotal) // 描く高さは正の区切りの和
    }

    @Test
    fun bars_withNegativeDifference_haveDrawnTotalAtLeastTotal() {
        // 品目の合計が支払い金額より大きい(差額が負)グループ
        val g = ExpenseGroup(id = 7, date = "2026-10-14", amount = 500, category = "日用品", createdAt = 1)
        val items = listOf(child(1, "2026-10-14", 400, "食費"), child(2, "2026-10-14", 300, "食費"))
        val entries = LedgerLogic.buildEntries(items, listOf(g))
        val bars = LedgerChart.bars(ChartLevel.YEAR, LocalDate.of(2026, 10, 14), entries)
        val oct = bars.last() // 年は直近6か月。終わりの月(10月)が最後
        assertEquals(500L, oct.total)
        assertEquals(700L, oct.drawnTotal)
    }

    // ---- 家計簿で消した品目 ----

    @Test
    fun deletedItems_areLeftOutOfListAndSums_andBecomeDifference() {
        val g = ExpenseGroup(id = 7, date = "2026-10-14", amount = 700, category = "日用品", createdAt = 1)
        val kept = child(1, "2026-10-14", 200, "食費")
        val gone = child(2, "2026-10-14", 500, "食費").copy(deletedByUser = true)
        val entry = LedgerLogic.buildEntries(listOf(kept, gone), listOf(g)).single() as com.amgm.personallog.ui.ledger.LedgerEntry.Group
        assertEquals(listOf(1L), entry.items.map { it.id })
        assertEquals(500L, entry.difference)
        // 消した品目の金額は、差額としてグループのカテゴリ(日用品)に入る。合計は支払い金額のまま
        assertEquals(
            mapOf("食費" to 200L, "日用品" to 500L),
            entry.categoryParts.associate { it.category to it.total },
        )
        assertEquals(700L, entry.categoryParts.sumOf { it.total })
    }

    @Test
    fun deletedItem_isNeverCountedAsSingle() {
        val gone = child(2, "2026-10-14", 500, "食費").copy(deletedByUser = true, groupId = null)
        assertEquals(0, LedgerLogic.buildEntries(listOf(gone), emptyList()).size)
    }
}
