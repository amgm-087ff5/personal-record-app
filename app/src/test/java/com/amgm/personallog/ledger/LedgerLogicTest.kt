package com.amgm.personallog.ledger

import com.amgm.personallog.data.model.CategoryTotal
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.ui.ledger.LedgerEntry
import com.amgm.personallog.ui.ledger.LedgerLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerLogicTest {
    private fun e(id: Long, date: String, amount: Int, cat: String = "食費") =
        Expense(id = id, date = date, amount = amount, category = cat, createdAt = 0)

    @Test
    fun shiftMonth_crossesYear() {
        assertEquals("2026-09", LedgerLogic.shiftMonth("2026-10", -1))
        assertEquals("2027-01", LedgerLogic.shiftMonth("2026-12", 1))
        assertEquals("2025-12", LedgerLogic.shiftMonth("2026-01", -1))
    }

    @Test
    fun recentMonths_isOldestFirst() {
        assertEquals(
            listOf("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10"),
            LedgerLogic.recentMonths("2026-10"),
        )
        assertEquals("2025-11", LedgerLogic.recentMonths("2026-04").first())
    }

    @Test
    fun monthLabel_formats() {
        assertEquals("2026年10月", LedgerLogic.monthLabel("2026-10"))
        assertEquals("2026年1月", LedgerLogic.monthLabel("2026-01"))
    }

    @Test
    fun groupByDate_sortsDescendingAndSums() {
        val groups = LedgerLogic.groupByDate(
            listOf(e(1, "2026-10-01", 100), e(2, "2026-10-03", 300), e(3, "2026-10-01", 50)).map { LedgerEntry.Single(it) },
        )
        assertEquals(listOf("2026-10-03", "2026-10-01"), groups.map { it.date })
        assertEquals(150L, groups[1].total)
        assertEquals(2, groups[1].items.size)
    }

    @Test
    fun categoryShares_percentages() {
        val shares = LedgerLogic.categoryShares(listOf(CategoryTotal("外食", 1000), CategoryTotal("食費", 3000)))
        assertEquals("食費", shares[0].category)
        assertEquals(75, shares[0].percent)
        assertEquals(25, shares[1].percent)
        assertTrue(LedgerLogic.categoryShares(emptyList()).isEmpty())
    }

    @Test
    fun monthlyTotals_includesEmptyMonths() {
        val totals = LedgerLogic.monthlyTotals(
            listOf(e(1, "2026-10-01", 100), e(2, "2026-08-15", 200), e(3, "2026-10-31", 5)).map { LedgerEntry.Single(it) },
            listOf("2026-08", "2026-09", "2026-10"),
        )
        assertEquals(listOf(200L, 0L, 105L), totals.map { it.total })
    }

    @Test
    fun buildEntries_groupCountsOnceAndItemsStayInside() {
        val group = ExpenseGroup(id = 7, date = "2026-10-03", amount = 1000, category = "食費", memo = "スーパー", createdAt = 5)
        val items = listOf(
            Expense(id = 11, date = "2026-10-03", amount = 400, category = "食費", groupId = 7, createdAt = 5),
            Expense(id = 12, date = "2026-10-03", amount = 300, category = "日用品", groupId = 7, createdAt = 5),
        )
        val single = e(13, "2026-10-03", 50, "その他")
        val entries = LedgerLogic.buildEntries(items + single, listOf(group))

        // 単独1行 + グループ1行。品目は行の中に入り、二重計上しない。
        assertEquals(2, entries.size)
        assertEquals(1050L, LedgerLogic.monthlyTotals(entries, listOf("2026-10"))[0].total)
        val g = entries.filterIsInstance<LedgerEntry.Group>().single()
        assertEquals(listOf(11L, 12L), g.items.map { it.id })
        assertEquals(700L, g.itemSum)
        assertEquals(300L, g.difference)
        assertEquals(
            listOf("その他" to 50L, "食費" to 1000L),
            LedgerLogic.totalsByCategory(entries).map { it.category to it.total }.sortedBy { it.second },
        )
    }

    @Test
    fun buildEntries_orphanChildBecomesSingle() {
        val orphan = Expense(id = 1, date = "2026-10-01", amount = 10, groupId = 99, createdAt = 1)
        val entries = LedgerLogic.buildEntries(listOf(orphan), emptyList())
        assertTrue(entries.single() is LedgerEntry.Single)
    }

    @Test
    fun filterMonth_matchesPrefix() {
        val list = listOf(e(1, "2026-10-01", 1), e(2, "2026-11-01", 1))
        assertEquals(1, LedgerLogic.filterMonth(list, "2026-10").size)
    }

    @Test
    fun validate_acceptsGoodInput() {
        val v = LedgerLogic.validate("2026-10-05", "1200", "食費")
        assertTrue(v.isValid)
        assertEquals(1200, v.amount)
        assertEquals("2026-10-05", v.date)
    }

    @Test
    fun validate_rejectsBadAmounts() {
        for (bad in listOf("", "0", "-5", "+5", "12.5", "abc", "99999999999", "1,000")) {
            assertNotNull("amount '$bad' should be rejected", LedgerLogic.validate("2026-10-05", bad, "食費").amountError)
        }
    }

    @Test
    fun validate_requiresCategoryAndValidDate() {
        assertNotNull(LedgerLogic.validate("2026-10-05", "100", null).categoryError)
        assertNotNull(LedgerLogic.validate("2026-10-05", "100", " ").categoryError)
        assertNotNull(LedgerLogic.validate("2026-02-30", "100", "食費").dateError)
        assertNotNull(LedgerLogic.validate("10/05", "100", "食費").dateError)
        assertNull(LedgerLogic.validate("2026-10-05", "100", "食費").dateError)
        assertFalse(LedgerLogic.validate("", "", null).isValid)
    }

    @Test
    fun formatting() {
        assertEquals("¥1,234,567", LedgerLogic.formatYen(1234567))
        assertEquals("¥0", LedgerLogic.formatYen(0))
        assertEquals("9999", LedgerLogic.compactYen(9999))
        assertEquals("1万", LedgerLogic.compactYen(10000))
        assertEquals("1.2万", LedgerLogic.compactYen(12345))
        assertEquals("10月5日(月)", LedgerLogic.dayLabel("2026-10-05"))
    }

    @Test
    fun defaultDate_usesTodayOnlyInCurrentMonth() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals("2026-10-10", LedgerLogic.defaultDate("2026-10", today))
        assertEquals("2026-09-01", LedgerLogic.defaultDate("2026-09", today))
    }
}
