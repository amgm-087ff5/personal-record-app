package com.amgm.personallog.ui.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 計算合計・「合計に入れる」ボタンの表示条件・mismatch。 */
class DetailUiStateTest {
    private fun item(amount: Int, rate: Int = 10, included: Boolean = true) = ItemForm(
        key = 1, name = "x", memo = "", amount = amount.toString(), category = "食費",
        taxRate = rate.toString(), priceIncludesTax = included,
    )

    private fun state(total: String, vararg items: ItemForm) = DetailUiState(loading = false, total = total, items = items.toList())

    @Test
    fun oneYenDifference_isMismatch_andOffersButtonWithConfirm() {
        val s = state("301", item(100), item(200))
        assertEquals(300L, s.itemsSum)
        assertEquals(1L, s.mismatch)
        assertTrue(s.canUseItemsSum)
        assertTrue(s.useItemsSumNeedsConfirm)
        assertEquals(-1L, state("299", item(100), item(200)).mismatch)
    }

    @Test
    fun match_hasNoMismatchAndNoButton() {
        val s = state("300", item(100), item(200))
        assertNull(s.mismatch)
        assertFalse(s.canUseItemsSum)
    }

    @Test
    fun emptyTotal_withItems_offersButtonWithoutConfirm() {
        val s = state("", item(100), item(200))
        assertNull(s.mismatch)
        assertTrue(s.totalMissing)
        assertTrue(s.canUseItemsSum)
        assertFalse(s.useItemsSumNeedsConfirm)
        // 数字として読めない入力も未入力として扱う
        assertTrue(state("-", item(100)).totalMissing)
    }

    @Test
    fun emptyTotal_withoutItems_hasNoButton() {
        val s = state("")
        assertEquals(0L, s.itemsSum)
        assertNull(s.mismatch)
        assertFalse(s.canUseItemsSum)
    }

    @Test
    fun taxExcludedItem_usesTaxIncludedPayment() {
        // 税抜100円・10% -> 支払い110円
        val s = state("110", item(100, included = false))
        assertEquals(110L, s.itemsSum)
        assertNull(s.mismatch)
        assertEquals(-10L, state("100", item(100, included = false)).mismatch)
    }

    @Test
    fun fractionalTaxIsFloored() {
        // 120円(税抜)・8% = 129.6円 -> 合計は切り捨てて129円
        val s = state("129", item(120, rate = 8, included = false))
        assertEquals(129L, s.itemsSum)
        assertNull(s.mismatch)
        assertEquals(1L, state("130", item(120, rate = 8, included = false)).mismatch)
    }

    @Test
    fun discountLine_isSubtracted() {
        val s = state("400", item(500), item(-100))
        assertEquals(400L, s.itemsSum)
        assertNull(s.mismatch)
        assertEquals(100L, state("500", item(500), item(-100)).mismatch)
    }

    @Test
    fun zeroYen() {
        // 0円の品目だけ: 合計0円は一致、空ならボタン(¥0)、0円と違う合計は不一致
        assertNull(state("0", item(0)).mismatch)
        assertTrue(state("", item(0)).canUseItemsSum)
        assertEquals(5L, state("5", item(0)).mismatch)
        // 合計が0円で品目が500円
        assertEquals(-500L, state("0", item(500)).mismatch)
        assertFalse(state("0", item(0)).canUseItemsSum)
    }

    @Test
    fun ownTotalAndBreakdownFollowItems() {
        val s = state("", item(108, rate = 8), item(110))
        assertEquals(218L, s.itemsSum)
        assertEquals(200L, s.taxBreakdown.netYen)
        assertEquals(18L, s.taxBreakdown.taxYen)
        assertEquals(218L, s.ownTotal)
    }
}
