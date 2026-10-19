package com.amgm.personallog.ledger

import com.amgm.personallog.ui.ledger.DraftItem
import com.amgm.personallog.ui.ledger.DraftPayment
import com.amgm.personallog.ui.ledger.LedgerLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 家計簿の手入力の「人数(任意)」。空欄は null、整数の1以上だけ受け付ける。 */
class LedgerHeadcountTest {
    private val drafts = listOf(DraftItem(name = "ランチ", amount = "500"))

    @Test
    fun parseHeadcount_blankIsNull_noError() {
        val p = LedgerLogic.parseHeadcount("  ")
        assertNull(p.value)
        assertNull(p.error)
    }

    @Test
    fun parseHeadcount_positiveInteger_isKept() {
        assertEquals(3, LedgerLogic.parseHeadcount("3").value)
        assertEquals(12, LedgerLogic.parseHeadcount(" 12 ").value)
    }

    @Test
    fun parseHeadcount_zeroNegativeOrNotInteger_isError() {
        for (text in listOf("0", "-1", "2.5", "4人", "abc")) {
            val p = LedgerLogic.parseHeadcount(text)
            assertNull("value for $text", p.value)
            assertTrue("error for $text", p.error != null)
        }
    }

    @Test
    fun draftBuild_keepsHeadcount() {
        val r = DraftPayment.build("2026-10-05", "12:00", "", "店", drafts, now = 1L, headcountText = "3")
        assertTrue(r.isValid)
        assertEquals(3, r.headcount)
    }

    @Test
    fun draftBuild_blankHeadcount_isNull() {
        val r = DraftPayment.build("2026-10-05", "12:00", "", "店", drafts, now = 1L)
        assertTrue(r.isValid)
        assertNull(r.headcount)
    }

    @Test
    fun draftBuild_invalidHeadcount_blocksSave() {
        val r = DraftPayment.build("2026-10-05", "12:00", "", "店", drafts, now = 1L, headcountText = "0")
        assertFalse(r.isValid)
        assertTrue(r.errors.any { it.startsWith("人数") })
    }
}
