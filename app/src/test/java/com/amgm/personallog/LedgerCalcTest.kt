package com.amgm.personallog

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.domain.LedgerCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerCalcTest {
    @Test
    fun monthRange_handlesMonthEnds() {
        assertEquals("2026-10-01" to "2026-10-31", LedgerCalc.monthRange("2026-10"))
        assertEquals("2024-02-01" to "2024-02-29", LedgerCalc.monthRange("2024-02"))
    }

    @Test
    fun groupFromReceipt_totalIsReceiptAmountAndItemsAreChildren() {
        val receipt = Receipt(
            id = 9, purchasedAt = "2026-10-05 12:30", storeName = "スーパー", totalAmount = 700,
            imagePath = "", rawJson = "{}", createdAt = 0,
        )
        val items = listOf(
            ReceiptItem(receiptId = 9, name = "牛乳", amount = 200, category = "食費"),
            ReceiptItem(receiptId = 9, name = "洗剤", amount = 500, category = "日用品"),
        )
        val g = LedgerCalc.groupFromReceipt(receipt, items, now = 3)!!
        assertEquals(700, g.group.amount)
        assertEquals("日用品", g.group.category)
        assertEquals("スーパー", g.group.memo)
        assertEquals(9L, g.group.receiptId)
        assertEquals(listOf(200, 500), g.items.map { it.amount })
        assertTrue(g.items.all { it.receiptId == 9L && it.groupId == null })
    }

    @Test
    fun groupFromReceipt_advanceOrNoItemsIsNotGrouped() {
        val receipt = Receipt(
            id = 9, purchasedAt = "2026-10-05", storeName = "店", totalAmount = 700,
            imagePath = "", rawJson = "{}", createdAt = 0, isAdvance = true,
        )
        val items = listOf(ReceiptItem(receiptId = 9, name = "牛乳", amount = 200))
        assertTrue(LedgerCalc.groupFromReceipt(receipt, items, now = 3) == null)
        assertTrue(LedgerCalc.groupFromReceipt(receipt.copy(isAdvance = false), emptyList(), now = 3) == null)
    }

    @Test
    fun totalsByCategory_sumsAndSortsDescending() {
        val e = listOf(
            Expense(date = "2026-10-01", amount = 300, category = "食費", createdAt = 0),
            Expense(date = "2026-10-02", amount = 1000, category = "交通費", createdAt = 0),
            Expense(date = "2026-10-03", amount = 500, category = "食費", createdAt = 0),
        )
        val r = LedgerCalc.totalsByCategory(e)
        assertEquals(listOf("交通費", "食費"), r.map { it.category })
        assertEquals(listOf(1000L, 800L), r.map { it.total })
    }

    @Test
    fun expensesFromReceipt_perItemAndTotal() {
        val receipt = Receipt(
            id = 7, purchasedAt = "2026-10-05 12:30", storeName = "スーパー", totalAmount = 700,
            imagePath = "", rawJson = "{}", createdAt = 0,
        )
        val items = listOf(
            ReceiptItem(receiptId = 7, name = "牛乳", amount = 200, category = "食費"),
            ReceiptItem(receiptId = 7, name = "洗剤", amount = 500, category = "日用品"),
        )
        val per = LedgerCalc.expensesFromReceipt(receipt, items, perItem = true, now = 1)
        assertEquals(2, per.size)
        assertTrue(per.all { it.date == "2026-10-05" && it.receiptId == 7L })

        val total = LedgerCalc.expensesFromReceipt(receipt, items, perItem = false, now = 1)
        assertEquals(1, total.size)
        assertEquals(700, total[0].amount)
        assertEquals("日用品", total[0].category)
    }

    @Test
    fun versionCodeFormula_isMonotonicAndFitsInt() {
        fun code(ms: Long) = (ms / 60000L).toInt()
        val a = code(1_790_000_000_000L)
        val b = code(1_790_000_060_000L)
        assertEquals(a + 1, b)
        assertTrue(a > 0)
    }

    @Test
    fun splitPurchasedAt_dateOnlyHasNoTime() {
        assertEquals("2026-10-05" to "12:30", LedgerCalc.splitPurchasedAt("2026-10-05 12:30"))
        assertEquals("2026-10-05" to "", LedgerCalc.splitPurchasedAt("2026-10-05"))
    }

    @Test
    fun timeOfMillis_usesGivenZone() {
        // 2026-10-14T01:05:00Z は UTC なら 01:05、東京(+9)なら 10:05
        val millis = java.time.Instant.parse("2026-10-14T01:05:00Z").toEpochMilli()
        assertEquals("01:05", LedgerCalc.timeOfMillis(millis, java.time.ZoneOffset.UTC))
        assertEquals("10:05", LedgerCalc.timeOfMillis(millis, java.time.ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun expensesFromReceipt_carriesReceiptTime() {
        val receipt = Receipt(
            id = 5, purchasedAt = "2026-10-05 12:30", storeName = "店", totalAmount = 300,
            imagePath = "", rawJson = "{}", createdAt = 0,
        )
        val items = listOf(ReceiptItem(receiptId = 5, name = "牛乳", amount = 300, category = "食費"))
        val rows = LedgerCalc.expensesFromReceipt(receipt, items, perItem = true, now = 1)
        assertTrue(rows.isNotEmpty())
        assertEquals(setOf("12:30"), rows.map { it.time }.toSet())
    }
}
