package com.amgm.personallog.receipt

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptDedupTest {
    private fun r(
        id: Long,
        at: String = "2026-10-09 18:32",
        store: String = "スーパーA",
        total: Int = 748,
        status: String = ReceiptStatus.CONFIRMED,
    ) = Receipt(
        id = id,
        purchasedAt = at,
        storeName = store,
        totalAmount = total,
        imagePath = "receipts/$id.jpg",
        rawJson = "{}",
        status = status,
        createdAt = id,
    )

    @Test
    fun normalizeStore_ignoresWidthSpacesAndCase() {
        assertEquals(
            ReceiptDedup.normalizeStore("ABC スーパー ｱ"),
            ReceiptDedup.normalizeStore("ａｂｃ　スーパー　ア"),
        )
        assertEquals("abc", ReceiptDedup.normalizeStore(" A B C "))
    }

    @Test
    fun keyOf_sameDayDifferentTime_isSameKey() {
        val a = ReceiptDedup.keyOf("2026-10-09 18:32", "スーパーA", 748)
        val b = ReceiptDedup.keyOf("2026-10-09", "スーパーＡ", 748)
        assertEquals(a, b)
        assertTrue(a != null)
    }

    @Test
    fun keyOf_differentTotalOrDate_isDifferentKey() {
        assertTrue(ReceiptDedup.keyOf("2026-10-09", "店", 100) != ReceiptDedup.keyOf("2026-10-09", "店", 101))
        assertTrue(ReceiptDedup.keyOf("2026-10-09", "店", 100) != ReceiptDedup.keyOf("2026-10-10", "店", 100))
    }

    @Test
    fun keyOf_nullWhenStoreBlankOrTotalNotPositiveOrBadDate() {
        assertNull(ReceiptDedup.keyOf("2026-10-09", "   ", 100))
        assertNull(ReceiptDedup.keyOf("2026-10-09", "店", 0))
        assertNull(ReceiptDedup.keyOf("2026-10-09", "店", -5))
        assertNull(ReceiptDedup.keyOf("", "店", 100))
    }

    @Test
    fun laterDuplicates_flagsLaterOnly() {
        val list = listOf(
            r(1),
            r(2, store = "スーパーＡ"),
            r(3, total = 900),
            r(4, store = " ", status = ReceiptStatus.DRAFT),
            r(5, store = " ", status = ReceiptStatus.DRAFT),
        )
        val dups = ReceiptDedup.laterDuplicates(list)
        assertEquals(setOf(2L), dups.keys)
        assertEquals(1L, dups.getValue(2L).id)
    }

    @Test
    fun laterDuplicates_chainPointsToTheEarliest() {
        val list = listOf(r(7), r(3), r(9))
        val dups = ReceiptDedup.laterDuplicates(list)
        assertEquals(setOf(7L, 9L), dups.keys)
        assertEquals(3L, dups.getValue(7L).id)
        assertEquals(3L, dups.getValue(9L).id)
    }

    @Test
    fun sameContent_excludesSelf() {
        val list = listOf(r(1), r(2))
        val key = ReceiptDedup.keyOf(list[0])!!
        val same = ReceiptDedup.sameContent(key, list, excludeId = 1L)
        assertEquals(listOf(2L), same.map { it.id })
    }

    @Test
    fun summary_containsDateStoreAndTotal() {
        val s = ReceiptDedup.summary(r(1, store = "", total = 1234))
        assertTrue(s.contains("2026-10-09 18:32"))
        assertTrue(s.contains("(店名なし)"))
        assertTrue(s.contains("1,234"))
    }
}
