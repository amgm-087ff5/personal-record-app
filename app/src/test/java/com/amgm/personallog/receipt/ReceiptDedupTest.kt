package com.amgm.personallog.receipt

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptNotDuplicate
import com.amgm.personallog.data.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReceiptDedupTest {
    private fun r(
        id: Long,
        at: String = "2026-10-09 18:32",
        store: String = "スーパーA",
        total: Int? = 748,
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

    private fun notDup(a: Long, b: Long) = ReceiptNotDuplicate.of(a, b, 0)!!

    @Test
    fun exactlyFiveMinutes_isCandidate_sixMinutes_isNot() {
        assertTrue(ReceiptDedup.isCandidate(r(1, "2026-10-09 18:30"), r(2, "2026-10-09 18:35")))
        assertTrue(ReceiptDedup.isCandidate(r(1, "2026-10-09 18:35"), r(2, "2026-10-09 18:30")))
        assertFalse(ReceiptDedup.isCandidate(r(1, "2026-10-09 18:30"), r(2, "2026-10-09 18:36")))
        assertTrue(ReceiptDedup.isCandidate(r(1, "2026-10-09 18:30"), r(2, "2026-10-09 18:30")))
    }

    @Test
    fun fiveMinutesOneSecond_isOutsideTheWindow() {
        val base = LocalDateTime.of(2026, 10, 9, 18, 30, 0)
        assertTrue(ReceiptDedup.withinWindow(base, base.plusMinutes(5)))
        assertFalse(ReceiptDedup.withinWindow(base, base.plusMinutes(5).plusSeconds(1)))
        assertTrue(ReceiptDedup.withinWindow(base.plusMinutes(5), base))
        assertFalse(ReceiptDedup.withinWindow(base.plusMinutes(5).plusSeconds(1), base))
    }

    @Test
    fun windowCrossesMidnight() {
        assertTrue(ReceiptDedup.isCandidate(r(1, "2026-10-09 23:58"), r(2, "2026-10-10 00:02")))
        assertFalse(ReceiptDedup.isCandidate(r(1, "2026-10-09 23:50"), r(2, "2026-10-10 00:02")))
        // 月またぎ・年またぎ
        assertTrue(ReceiptDedup.isCandidate(r(1, "2026-12-31 23:59"), r(2, "2027-01-01 00:03")))
    }

    @Test
    fun differentTotal_isNotCandidate() {
        assertFalse(ReceiptDedup.isCandidate(r(1, total = 748), r(2, total = 749)))
    }

    @Test
    fun storeNameIsNotUsed() {
        assertTrue(ReceiptDedup.isCandidate(r(1, store = "スーパーA"), r(2, store = "ｽｰﾊﾟｰ ａ 駅前店")))
        assertTrue(ReceiptDedup.isCandidate(r(1, store = ""), r(2, store = "")))
    }

    @Test
    fun withoutTime_sameDateAndTotalIsCandidate() {
        // どちらかに時刻が無い: 同じ日付+同じ合計
        assertTrue(ReceiptDedup.isCandidate(r(1, "2026-10-09"), r(2, "2026-10-09 18:32")))
        assertTrue(ReceiptDedup.isCandidate(r(1, "2026-10-09 03:00"), r(2, "2026-10-09")))
        assertTrue(ReceiptDedup.isCandidate(r(1, "2026-10-09"), r(2, "2026-10-09")))
        assertFalse(ReceiptDedup.isCandidate(r(1, "2026-10-09"), r(2, "2026-10-10 00:01")))
        assertFalse(ReceiptDedup.isCandidate(r(1, "2026-10-09", total = 1), r(2, "2026-10-09", total = 2)))
        assertFalse(ReceiptDedup.isCandidate(r(1, ""), r(2, "")))
    }

    @Test
    fun nullTotal_isNeverJudged() {
        assertFalse(ReceiptDedup.isCandidate(r(1, total = null), r(2, total = null)))
        assertFalse(ReceiptDedup.isCandidate(r(1, total = null), r(2, total = 748)))
        assertFalse(ReceiptDedup.isCandidate(r(1, total = 748), r(2, total = null)))
        assertTrue(ReceiptDedup.laterDuplicates(listOf(r(1, total = null), r(2, total = null))).isEmpty())
    }

    @Test
    fun laterDuplicates_flagsLaterOnly_pointingToTheEarliest() {
        val list = listOf(
            r(7, "2026-10-09 18:32"),
            r(3, "2026-10-09 18:33"),
            r(9, "2026-10-09 18:34"),
            r(4, "2026-10-09 18:33", total = 900),
        )
        val dups = ReceiptDedup.laterDuplicates(list)
        assertEquals(setOf(7L, 9L), dups.keys)
        assertEquals(3L, dups.getValue(7L).id)
        assertEquals(3L, dups.getValue(9L).id)
    }

    @Test
    fun laterDuplicates_excludesNotDuplicatePairs() {
        val list = listOf(r(1), r(2), r(3))
        assertEquals(setOf(2L, 3L), ReceiptDedup.laterDuplicates(list).keys)
        // 1-2 を「重複ではない」にすると 2 の警告は消えるが、3 は 1 とも 2 とも候補のまま
        val set12 = ReceiptDedup.notDuplicateSet(listOf(notDup(2, 1)))
        val afterOne = ReceiptDedup.laterDuplicates(list, set12)
        assertEquals(setOf(3L), afterOne.keys)
        // 3 の相手は、保存済みでない最も早い 1
        assertEquals(1L, afterOne.getValue(3L).id)
        // 全ての組を「重複ではない」にすれば警告は出ない
        val all = ReceiptDedup.notDuplicateSet(listOf(notDup(1, 2), notDup(1, 3), notDup(2, 3)))
        assertTrue(ReceiptDedup.laterDuplicates(list, all).isEmpty())
    }

    @Test
    fun earlierDuplicates_listsEachPartnerAndSkipsSavedPairs() {
        val list = listOf(r(1), r(2), r(3), r(4, total = 5))
        assertEquals(listOf(1L, 2L), ReceiptDedup.earlierDuplicates(list[2], list).map { it.id })
        val set = ReceiptDedup.notDuplicateSet(listOf(notDup(1, 3)))
        assertEquals(listOf(2L), ReceiptDedup.earlierDuplicates(list[2], list, set).map { it.id })
        // 原本側(id が小さい方)には警告しない
        assertTrue(ReceiptDedup.earlierDuplicates(list[0], list).isEmpty())
    }

    @Test
    fun sameContent_excludesSelfAndSavedPairs() {
        val confirmed = listOf(r(1), r(2), r(3, total = 1))
        val target = r(2, status = ReceiptStatus.DRAFT)
        assertEquals(listOf(1L), ReceiptDedup.sameContent(target, confirmed).map { it.id })
        val set = ReceiptDedup.notDuplicateSet(listOf(notDup(2, 1)))
        assertTrue(ReceiptDedup.sameContent(target, confirmed, set).isEmpty())
        // 確定済みの相手は id が大きくてもよい
        assertEquals(listOf(5L), ReceiptDedup.sameContent(r(2), listOf(r(5))).map { it.id })
    }

    @Test
    fun pairIsNormalizedLowHigh() {
        assertEquals(2L to 9L, ReceiptDedup.pairOf(9, 2))
        val p = ReceiptNotDuplicate.of(9, 2, 100)!!
        assertEquals(2L, p.receiptIdLow)
        assertEquals(9L, p.receiptIdHigh)
        assertEquals(null, ReceiptNotDuplicate.of(4, 4, 0))
    }

    @Test
    fun summary_containsDateStoreAndTotal_andNeverNull() {
        val s = ReceiptDedup.summary(r(1, store = "", total = 1234))
        assertTrue(s.contains("2026-10-09 18:32"))
        assertTrue(s.contains("(店名なし)"))
        assertTrue(s.contains("1,234"))
        val n = ReceiptDedup.summary(r(1, total = null))
        assertTrue(n.contains("合計 未入力"))
        assertFalse(n.contains("null"))
    }
}
