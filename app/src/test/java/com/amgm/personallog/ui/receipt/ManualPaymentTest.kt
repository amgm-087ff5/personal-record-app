package com.amgm.personallog.ui.receipt

import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentStatus
import com.amgm.personallog.data.model.TotalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 手入力の支払い(画像なし)の作り方と、詳細画面の「手入力」判定。 */
class ManualPaymentTest {

    private fun ok(result: ManualPayment.Result): Payment =
        (result as? ManualPayment.Result.Ok)?.payment ?: throw AssertionError("Ok を期待したが $result")

    private fun invalid(result: ManualPayment.Result): String =
        (result as? ManualPayment.Result.Invalid)?.message ?: throw AssertionError("Invalid を期待したが $result")

    @Test
    fun dateOnly_makesDraftWithoutImageAndManualTotalSource() {
        val r = ok(ManualPayment.build("2026-10-10", "", "  コンビニ  ", "", now = 42L))
        assertEquals("2026-10-10", r.purchasedAt)
        assertEquals("コンビニ", r.storeName)
        assertNull(r.totalAmount)
        assertEquals("", r.imagePath)
        assertEquals("", r.rawJson)
        assertEquals(PaymentStatus.DRAFT, r.status)
        assertEquals(TotalSource.MANUAL, r.totalSource)
        assertEquals(42L, r.createdAt)
        assertNull(r.imageHash)
        assertFalse(r.isAdvance)
    }

    @Test
    fun withTime_usesSameFormatAsPayments() {
        val r = ok(ManualPayment.build("2026-10-10", " 14:05 ", "", "1280", now = 0L))
        assertEquals("2026-10-10 14:05", r.purchasedAt)
        assertEquals(1280, r.totalAmount)
        assertEquals(TotalSource.MANUAL, r.totalSource)
        // 合計が入っているので確定
        assertEquals(PaymentStatus.CONFIRMED, r.status)
    }

    @Test
    fun zeroTotal_isAllowed_andConfirmed() {
        val r = ok(ManualPayment.build("2026-01-01", "", "", "0", now = 0L))
        assertEquals(0, r.totalAmount)
        assertEquals(PaymentStatus.CONFIRMED, r.status)
    }

    @Test
    fun noTotal_staysDraft() {
        assertEquals(PaymentStatus.DRAFT, ok(ManualPayment.build("2026-10-10", "", "", "", now = 0L)).status)
    }

    @Test
    fun invalidDate_isRejected() {
        assertTrue(invalid(ManualPayment.build("", "", "", "", now = 0L)).contains("日付"))
        assertTrue(invalid(ManualPayment.build("2026/10/10", "", "", "", now = 0L)).contains("日付"))
        assertTrue(invalid(ManualPayment.build("2026-13-01", "", "", "", now = 0L)).contains("日付"))
    }

    @Test
    fun invalidTime_isRejected() {
        assertTrue(invalid(ManualPayment.build("2026-10-10", "25:00", "", "", now = 0L)).contains("時刻"))
        assertTrue(invalid(ManualPayment.build("2026-10-10", "9:00", "", "", now = 0L)).contains("時刻"))
    }

    @Test
    fun invalidTotal_isRejected() {
        assertTrue(invalid(ManualPayment.build("2026-10-10", "", "", "-1", now = 0L)).contains("合計"))
        assertTrue(invalid(ManualPayment.build("2026-10-10", "", "", "12a", now = 0L)).contains("合計"))
    }

    @Test
    fun manualEntry_isTrueOnlyForLoadedPaymentWithoutImage() {
        assertTrue(DetailUiState(loading = false, imagePath = "").manualEntry)
        assertFalse(DetailUiState(loading = false, imagePath = "receipts/a.jpg").manualEntry)
        // 読み込み中・見つからない場合は「手入力」を判定しない
        assertFalse(DetailUiState(loading = true, imagePath = "").manualEntry)
        assertFalse(DetailUiState(loading = false, notFound = true, imagePath = "").manualEntry)
    }
}
