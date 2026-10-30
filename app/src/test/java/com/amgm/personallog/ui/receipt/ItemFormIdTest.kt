package com.amgm.personallog.ui.receipt

import org.junit.Assert.assertEquals
import org.junit.Test

/** 画面の品目フォームが PaymentItem の id を持ち回る(保存の差分で id を保つため)。 */
class ItemFormIdTest {
    private fun form(id: Long) = ItemForm(
        key = 1, name = "牛乳", memo = "", amount = "200", category = "食費",
        taxRate = "10", priceIncludesTax = true, id = id,
    )

    @Test
    fun toItemModel_carriesId() {
        assertEquals(5L, form(5).toItemModel(paymentId = 3).id)
        assertEquals(3L, form(5).toItemModel(paymentId = 3).paymentId)
    }

    @Test
    fun newRowHasIdZero() {
        assertEquals(0L, form(0).toItemModel().id)
    }
}
