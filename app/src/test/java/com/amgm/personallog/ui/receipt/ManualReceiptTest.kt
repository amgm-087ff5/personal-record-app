package com.amgm.personallog.ui.receipt

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.model.TotalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 手入力の支払い(画像なし)の作り方と、詳細画面の「手入力」判定。 */
class ManualReceiptTest {

    private fun ok(result: ManualReceipt.Result): Receipt =
        (result as? ManualReceipt.Result.Ok)?.receipt ?: throw AssertionError("Ok を期待したが $result")

    private fun invalid(result: ManualReceipt.Result): String =
        (result as? ManualReceipt.Result.Invalid)?.message ?: throw AssertionError("Invalid を期待したが $result")

    @Test
    fun dateOnly_makesDraftWithoutImageAndManualTotalSource() {
        val r = ok(ManualReceipt.build("2026-10-10", "", "  コンビニ  ", "", now = 42L))
        assertEquals("2026-10-10", r.purchasedAt)
        assertEquals("コンビニ", r.storeName)
        assertNull(r.totalAmount)
        assertEquals("", r.imagePath)
        assertEquals("", r.rawJson)
        assertEquals(ReceiptStatus.DRAFT, r.status)
        assertEquals(TotalSource.MANUAL, r.totalSource)
        assertEquals(42L, r.createdAt)
        assertNull(r.imageHash)
        assertFalse(r.isAdvance)
    }

    @Test
    fun withTime_usesSameFormatAsReceipts() {
        val r = ok(ManualReceipt.build("2026-10-10", " 14:05 ", "", "1280", now = 0L))
        assertEquals("2026-10-10 14:05", r.purchasedAt)
        assertEquals(1280, r.totalAmount)
        assertEquals(TotalSource.MANUAL, r.totalSource)
    }

    @Test
    fun zeroTotal_isAllowed() {
        assertEquals(0, ok(ManualReceipt.build("2026-01-01", "", "", "0", now = 0L)).totalAmount)
    }

    @Test
    fun invalidDate_isRejected() {
        assertTrue(invalid(ManualReceipt.build("", "", "", "", now = 0L)).contains("日付"))
        assertTrue(invalid(ManualReceipt.build("2026/10/10", "", "", "", now = 0L)).contains("日付"))
        assertTrue(invalid(ManualReceipt.build("2026-13-01", "", "", "", now = 0L)).contains("日付"))
    }

    @Test
    fun invalidTime_isRejected() {
        assertTrue(invalid(ManualReceipt.build("2026-10-10", "25:00", "", "", now = 0L)).contains("時刻"))
        assertTrue(invalid(ManualReceipt.build("2026-10-10", "9:00", "", "", now = 0L)).contains("時刻"))
    }

    @Test
    fun invalidTotal_isRejected() {
        assertTrue(invalid(ManualReceipt.build("2026-10-10", "", "", "-1", now = 0L)).contains("合計"))
        assertTrue(invalid(ManualReceipt.build("2026-10-10", "", "", "12a", now = 0L)).contains("合計"))
    }

    @Test
    fun manualEntry_isTrueOnlyForLoadedReceiptWithoutImage() {
        assertTrue(DetailUiState(loading = false, imagePath = "").manualEntry)
        assertFalse(DetailUiState(loading = false, imagePath = "receipts/a.jpg").manualEntry)
        // 読み込み中・見つからない場合は「手入力」を判定しない
        assertFalse(DetailUiState(loading = true, imagePath = "").manualEntry)
        assertFalse(DetailUiState(loading = false, notFound = true, imagePath = "").manualEntry)
    }
}
