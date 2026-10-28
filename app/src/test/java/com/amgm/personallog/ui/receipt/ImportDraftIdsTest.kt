package com.amgm.personallog.ui.receipt

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportDraftIdsTest {
    private fun receipt(id: Long, status: String) = Receipt(
        id = id, purchasedAt = "2026-10-28", storeName = "店", totalAmount = 100,
        imagePath = "", rawJson = "", status = status, createdAt = 0L,
    )

    @Test
    fun onlyDraftsAmongImportedReceiptsAreReturned() {
        val receipts = listOf(
            receipt(1L, ReceiptStatus.DRAFT),
            receipt(2L, ReceiptStatus.CONFIRMED),
            receipt(3L, ReceiptStatus.DRAFT),
        )
        // 取り込み中(まだ支払いが無い項目 = null)と、確定済みの項目は含めない
        assertEquals(setOf(1L, 3L), draftReceiptIds(listOf(1L, null, 2L, 3L), receipts))
    }

    @Test
    fun emptyWhenNothingImported() {
        assertEquals(emptySet<Long>(), draftReceiptIds(emptyList(), listOf(receipt(1L, ReceiptStatus.DRAFT))))
    }
}
