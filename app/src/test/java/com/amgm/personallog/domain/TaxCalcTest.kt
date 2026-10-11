package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TaxCalcTest {
    private fun item(
        amount: Int,
        taxRate: Int = 10,
        included: Boolean = true,
        own: Int = 100,
    ) = ReceiptItem(
        receiptId = 1, name = "x", amount = amount, taxRate = taxRate,
        priceIncludesTax = included, ownPercent = own,
    )

    @Test
    fun taxIncludedItem_paysAmountAsPrinted() {
        assertEquals(120L, TaxCalc.paidYen(listOf(item(120))))
        assertEquals("120", TaxCalc.paidYenText(item(120)))
    }

    @Test
    fun taxExcludedItem_addsTaxWithDecimals() {
        val i = item(120, taxRate = 8, included = false)
        assertEquals("129.6", TaxCalc.paidYenText(i))
        // 支払総額は切り捨て
        assertEquals(129L, TaxCalc.paidYen(listOf(i)))
    }

    @Test
    fun unknownRate_fallsBackToTenPercent() {
        assertEquals(10, TaxCalc.normalizeTaxRate(9))
        assertEquals(10, TaxCalc.normalizeTaxRate(null))
        assertEquals(8, TaxCalc.normalizeTaxRate(8))
    }

    @Test
    fun ownPercent_isClampedToZeroToHundred() {
        assertEquals(100, TaxCalc.normalizeOwnPercent(150))
        assertEquals(0, TaxCalc.normalizeOwnPercent(-5))
        // 税抜1000円(10%)=税込1100円の半分
        val half = item(1000, taxRate = 10, included = false, own = 50)
        assertEquals("550", TaxCalc.ownYenText(half))
        assertEquals(550L, TaxCalc.ownYen(listOf(half)))
    }

    @Test
    fun negativeDiscount_isKept() {
        assertEquals(-50L, TaxCalc.paidYen(listOf(item(-50))))
    }

    @Test
    fun perItemOwnShares_sumToFlooredOwnTotal() {
        val items = listOf(
            item(120, taxRate = 8, included = false),  // 129.6
            item(123, taxRate = 8, included = false),  // 132.84
            item(500, included = true, own = 50),       // 250
        )
        // 支払合計 762.44 -> 762、自分の分 512.44 -> 512
        assertEquals(762L, TaxCalc.paidYen(items))
        assertEquals(512L, TaxCalc.ownYen(items))
        val per = TaxCalc.ownYenPerItem(items)
        assertEquals(listOf(129L, 133L, 250L), per)
        assertEquals(TaxCalc.ownYen(items), per.sum())
        // 立て替えの貰う分 = 支払合計 - 自分の分
        assertEquals(250L, TaxCalc.paidYen(items) - TaxCalc.ownYen(items))
    }

    @Test
    fun expensesFromReceipt_usesOwnShare() {
        val receipt = Receipt(
            id = 3, purchasedAt = "2026-10-06", storeName = "店", totalAmount = 762,
            imagePath = "", rawJson = "{}", createdAt = 0, isAdvance = true,
        )
        val items = listOf(
            item(120, taxRate = 8, included = false),
            item(500, included = true, own = 50),
        )
        val rows = LedgerCalc.expensesFromReceipt(receipt, items, perItem = true, now = 1)
        assertEquals(listOf(129, 250), rows.map { it.amount })
        assertEquals(379, rows.sumOf { it.amount })
    }
}
