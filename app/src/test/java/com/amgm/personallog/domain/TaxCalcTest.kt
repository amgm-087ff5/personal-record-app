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
        own: Int? = null,
    ) = ReceiptItem(
        receiptId = 1, name = "x", amount = amount, taxRate = taxRate,
        priceIncludesTax = included, ownAmount = own,
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
    fun taxRate_acceptsAnyIntegerInRange_elseDefault() {
        assertEquals(7, TaxCalc.normalizeTaxRate(7))
        assertEquals(0, TaxCalc.normalizeTaxRate(0))
        assertEquals(100, TaxCalc.normalizeTaxRate(100))
        assertEquals(10, TaxCalc.normalizeTaxRate(101))
        assertEquals(10, TaxCalc.normalizeTaxRate(-1))
        assertEquals(10, TaxCalc.normalizeTaxRate(null))
        // 7%の税抜1000円は1070円
        assertEquals("1070", TaxCalc.paidYenText(item(1000, taxRate = 7, included = false)))
    }

    @Test
    fun ownAmount_isYenAndDefaultsToWholePaid() {
        val paidWhole = item(1000, taxRate = 10, included = false)
        assertEquals("1100", TaxCalc.ownYenText(paidWhole))
        // 自分が払う分を金額で指定(税込1100円のうち400円)
        val share = item(1000, taxRate = 10, included = false, own = 400)
        assertEquals(400L, TaxCalc.ownYen(listOf(share)))
        assertEquals(700L, TaxCalc.paidYen(listOf(share)) - TaxCalc.ownYen(listOf(share)))
    }

    @Test
    fun negativeDiscount_isKept() {
        assertEquals(-50L, TaxCalc.paidYen(listOf(item(-50))))
    }

    @Test
    fun perItemOwnShares_sumToFlooredOwnTotal() {
        val items = listOf(
            item(120, taxRate = 8, included = false),          // 税込 129.6(全額自分)
            item(123, taxRate = 8, included = false, own = 100), // 税込 132.84 のうち 100円
            item(500, included = true),                         // 500
        )
        // 支払合計 762.44 -> 762 / 自分の分 729.6 -> 729
        assertEquals(762L, TaxCalc.paidYen(items))
        assertEquals(729L, TaxCalc.ownYen(items))
        val per = TaxCalc.ownYenPerItem(items)
        assertEquals(TaxCalc.ownYen(items), per.sum())
    }

    @Test
    fun expensesFromReceipt_advanceUsesOwnAmount_nonAdvanceIgnoresIt() {
        val items = listOf(
            item(120, taxRate = 8, included = false),
            item(500, included = true, own = 200),
        )
        val advance = Receipt(
            id = 3, purchasedAt = "2026-10-06", storeName = "店", totalAmount = 762,
            imagePath = "", rawJson = "{}", createdAt = 0, isAdvance = true,
        )
        assertEquals(listOf(129L, 200L), LedgerCalc.expensesFromReceipt(advance, items, perItem = true, now = 1).map { it.amount.toLong() })

        val normal = advance.copy(isAdvance = false)
        assertEquals(listOf(129L, 500L), LedgerCalc.expensesFromReceipt(normal, items, perItem = true, now = 1).map { it.amount.toLong() })
    }
}
