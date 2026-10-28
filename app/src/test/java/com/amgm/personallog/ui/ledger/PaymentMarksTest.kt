package com.amgm.personallog.ui.ledger

import com.amgm.personallog.data.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentMarksTest {
    @Test
    fun taxLabelsShowRateAndTaxExcludedFlag() {
        assertEquals(listOf("8%"), PaymentMarks.taxLabels(ReceiptItem(receiptId = 1L, name = "x", taxRate = 8, priceIncludesTax = true)))
        assertEquals(listOf("10%", "税抜"), PaymentMarks.taxLabels(ReceiptItem(receiptId = 1L, name = "x", taxRate = 10, priceIncludesTax = false)))
    }
}
