package com.amgm.personallog.ui.receipt

import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportDraftIdsTest {
    private fun payment(id: Long, status: String) = Payment(
        id = id, purchasedAt = "2026-10-28", storeName = "店", totalAmount = 100,
        imagePath = "", rawJson = "", status = status, createdAt = 0L,
    )

    @Test
    fun onlyDraftsAmongImportedPaymentsAreReturned() {
        val payments = listOf(
            payment(1L, PaymentStatus.DRAFT),
            payment(2L, PaymentStatus.CONFIRMED),
            payment(3L, PaymentStatus.DRAFT),
        )
        // 取り込み中(まだ支払いが無い項目 = null)と、確定済みの項目は含めない
        assertEquals(setOf(1L, 3L), draftPaymentIds(listOf(1L, null, 2L, 3L), payments))
    }

    @Test
    fun emptyWhenNothingImported() {
        assertEquals(emptySet<Long>(), draftPaymentIds(emptyList(), listOf(payment(1L, PaymentStatus.DRAFT))))
    }
}
