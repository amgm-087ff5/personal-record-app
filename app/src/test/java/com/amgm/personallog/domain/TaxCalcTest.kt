package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaxCalcTest {
    private fun item(
        amount: Int,
        taxRate: Int = 10,
        included: Boolean = true,
    ) = ReceiptItem(
        receiptId = 1, name = "x", amount = amount, taxRate = taxRate, priceIncludesTax = included,
    )

    private fun receipt(advance: Boolean, own: Int? = null) = Receipt(
        id = 3, purchasedAt = "2026-10-06", storeName = "店", totalAmount = 762,
        imagePath = "", rawJson = "{}", createdAt = 0, isAdvance = advance, ownAmount = own,
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
    fun negativeDiscount_isKept() {
        assertEquals(-50L, TaxCalc.paidYen(listOf(item(-50))))
    }

    @Test
    fun perItemPaid_sumToFlooredTotal() {
        val items = listOf(
            item(120, taxRate = 8, included = false), // 129.6
            item(123, taxRate = 8, included = false), // 132.84
            item(500, included = true),               // 500
        )
        // 支払合計 762.44 -> 762
        assertEquals(762L, TaxCalc.paidYen(items))
        assertEquals(762L, TaxCalc.paidYenPerItem(items).sum())
    }

    @Test
    fun ownShareAtReceiptLevel_fromPickedItems_floored() {
        val items = listOf(
            item(120, taxRate = 8, included = false), // 129.6 (選ぶ)
            item(123, taxRate = 8, included = false), // 132.84 (選ばない)
            item(500, included = true),               // 500 (選ぶ)
        )
        // 選んだ品目の税込合計 629.6 -> 629
        assertEquals(629L, TaxCalc.ownYenOfPicked(items, listOf(true, false, true)))
    }

    @Test
    fun legacyOwnShares_sumToReceiptOwn() {
        // 旧形式: 品目ごとの自分の分(円・税込)を足して、レシート全体の値にする。
        val lines = listOf(
            PricedLine(amount = 120, taxRate = 8, priceIncludesTax = false),           // 全額自分 129.6
            PricedLine(amount = 123, taxRate = 8, priceIncludesTax = false, ownAmount = 100),
            PricedLine(amount = 500, taxRate = 10, priceIncludesTax = true),           // 全額自分 500
        )
        // 129.6 + 100 + 500 = 729.6 -> 729
        assertEquals(729, legacyReceiptOwnYen(lines))
        assertNull(legacyReceiptOwnYen(lines.map { it.copy(ownAmount = null) }))
    }

    @Test
    fun expensesFromReceipt_advanceUsesReceiptOwn_nonAdvanceIgnoresIt() {
        val items = listOf(
            item(120, taxRate = 8, included = false),
            item(500, included = true),
        )
        // 立て替え: レシート全体の自分の分(200円)を1行
        val advance = receipt(advance = true, own = 200)
        assertEquals(listOf(200L), LedgerCalc.expensesFromReceipt(advance, items, perItem = true, now = 1).map { it.amount.toLong() })

        // 立て替えで未入力: 支払総額の全額(629.6 -> 629)を1行
        val advanceWhole = receipt(advance = true, own = null)
        assertEquals(listOf(629L), LedgerCalc.expensesFromReceipt(advanceWhole, items, perItem = false, now = 1).map { it.amount.toLong() })

        // 立て替えでない: own は無視、品目ごとに税込の行
        val normal = receipt(advance = false, own = 200)
        assertEquals(listOf(129L, 500L), LedgerCalc.expensesFromReceipt(normal, items, perItem = true, now = 1).map { it.amount.toLong() })
    }
}
