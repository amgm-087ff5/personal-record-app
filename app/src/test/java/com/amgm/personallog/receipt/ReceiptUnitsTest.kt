package com.amgm.personallog.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptUnitsTest {
    @Test
    fun unitCount_onlyWholeNumbersFrom2To99Expand() {
        assertEquals(1, ReceiptUnits.unitCount(1.0))
        assertEquals(3, ReceiptUnits.unitCount(3.0))
        assertEquals(1, ReceiptUnits.unitCount(1.5))
        assertEquals(1, ReceiptUnits.unitCount(0.0))
        assertEquals(1, ReceiptUnits.unitCount(150.0))
    }

    @Test
    fun splitAmount_remainderGoesToLast() {
        assertEquals(listOf(100, 100, 101), ReceiptUnits.splitAmount(301, 3))
        assertEquals(listOf(-25, -25), ReceiptUnits.splitAmount(-50, 2))
        assertEquals(listOf(7), ReceiptUnits.splitAmount(7, 1))
    }

    @Test
    fun fromParsed_expandsQuantityIntoUnitRows() {
        val p = ParsedItem(name = "洗剤", quantity = 2.0, unitPrice = 300, amount = 600, category = "日用品", taxRate = 8, priceIncludesTax = false)
        val rows = ReceiptUnits.fromParsed(p)
        assertEquals(listOf(300, 300), rows.map { it.amount })
        assertEquals(listOf(8, 8), rows.map { it.taxRate })
        assertEquals(listOf(false, false), rows.map { it.priceIncludesTax })
        assertNull(rows[0].ownAmount)
    }

    @Test
    fun fromLegacy_convertsOwnPercentToYen() {
        // 税抜100円(10%)を1個、負担50% -> 税込110円の半分で55円
        val rows = ReceiptUnits.fromLegacy(
            LegacyRow(name = "A", quantity = 1.0, amount = 100, category = "食費", taxRate = 10, priceIncludesTax = false, ownPercent = 50),
        )
        assertEquals(55, rows.single().ownAmount)
        // 100%は null(全額自分)
        val full = ReceiptUnits.fromLegacy(
            LegacyRow(name = "B", quantity = 1.0, amount = 100, category = "食費", taxRate = 10, priceIncludesTax = true, ownPercent = 100),
        )
        assertNull(full.single().ownAmount)
    }

    @Test
    fun fromLegacy_expandsQuantityAndKeepsTotal() {
        val rows = ReceiptUnits.fromLegacy(
            LegacyRow(name = "牛乳", quantity = 3.0, amount = 301, category = "食費", taxRate = 8, priceIncludesTax = true, ownPercent = 100),
        )
        assertEquals(3, rows.size)
        assertEquals(301, rows.sumOf { it.amount })
    }

    @Test
    fun fromParsed_carriesSharedSuggestionToEveryUnit() {
        val shared = ParsedItem(name = "レジ袋", quantity = 2.0, unitPrice = 3, amount = 6, category = "その他", shared = true)
        val rows = ReceiptUnits.fromParsed(shared)
        assertEquals(listOf(true, true), rows.map { it.shared })
        // 指定しなければ false(既定値)
        val normal = ParsedItem(name = "牛乳", quantity = 1.0, unitPrice = 198, amount = 198, category = "食費")
        assertEquals(false, ReceiptUnits.fromParsed(normal).single().shared)
        assertEquals(false, UnitRow(name = "x", amount = 1, category = "食費", taxRate = 10, priceIncludesTax = true).shared)
    }
}
