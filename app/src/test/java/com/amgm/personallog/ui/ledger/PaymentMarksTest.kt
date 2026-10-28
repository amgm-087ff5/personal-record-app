package com.amgm.personallog.ui.ledger

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentMarksTest {
    private fun receipt(imagePath: String = "", rawJson: String = "") = Receipt(
        purchasedAt = "2026-10-28", storeName = "店", totalAmount = null,
        imagePath = imagePath, rawJson = rawJson, createdAt = 0L,
    )

    @Test
    fun draftBannerAsksToCheckReadResultForScannedReceipt() {
        assertEquals("読み取り結果を確認して確定してください", PaymentMarks.draftBannerText(receipt(imagePath = "a.jpg")))
        assertEquals("読み取り結果を確認して確定してください", PaymentMarks.draftBannerText(receipt(rawJson = "{}")))
    }

    @Test
    fun draftBannerAsksForTotalForManualDraft() {
        assertEquals("合計を入れて確定してください", PaymentMarks.draftBannerText(receipt()))
    }

    @Test
    fun taxLabelsShowRateAndTaxExcludedFlag() {
        assertEquals(listOf("8%"), PaymentMarks.taxLabels(ReceiptItem(receiptId = 1L, name = "x", taxRate = 8, priceIncludesTax = true)))
        assertEquals(listOf("10%", "税抜"), PaymentMarks.taxLabels(ReceiptItem(receiptId = 1L, name = "x", taxRate = 10, priceIncludesTax = false)))
    }
}
