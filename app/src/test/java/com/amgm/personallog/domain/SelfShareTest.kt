package com.amgm.personallog.domain

import com.amgm.personallog.domain.split.ItemYen
import com.amgm.personallog.domain.split.SplitLine
import com.amgm.personallog.domain.split.SplitResult
import com.amgm.personallog.domain.split.SplitWarning
import org.junit.Assert.assertEquals
import org.junit.Test

class SelfShareTest {
    @Test
    fun warningCodes_oneEachKind_inOrder() {
        val codes = SplitWarningCodes.of(
            listOf(
                SplitWarning.ItemsTotalMismatch(residual = 3),
                SplitWarning.GroupAmountMismatch(groupId = 1, expected = 100, allocated = 90),
                SplitWarning.ItemsTotalMismatch(residual = -2),
                SplitWarning.InvalidShares(groupId = null, reason = "存在しない人"),
            ),
        )
        assertEquals("W1,W3,W5", codes)
    }

    @Test
    fun warningCodes_emptyWhenNoWarnings() {
        assertEquals("", SplitWarningCodes.of(emptyList()))
    }

    private val self = 1L
    private val friend = 2L

    @Test
    fun selfOf_takesOnlySelfLines_andTotalIsTheirSum() {
        val items = listOf(ItemYen(1L, 10L, 1000, "食品"), ItemYen(2L, 11L, 500, "食品"))
        val result = SplitResult(
            lines = listOf(SplitLine(self, 10L, 600), SplitLine(friend, 10L, 400)),
            warnings = emptyList(),
        )
        val share = SelfShare.of(result, self, items)
        // 品目2は自分の行が無いので0円。0円の品目は perItem に入れない
        assertEquals(mapOf(1L to 600L), share.perItem)
        assertEquals(600L, share.total)
    }

    @Test
    fun selfOf_noSelfLines_isEmpty() {
        val items = listOf(ItemYen(1L, 10L, 1000, "食品"))
        val result = SplitResult(lines = listOf(SplitLine(friend, 10L, 1000)), warnings = emptyList())
        val share = SelfShare.of(result, self, items)
        assertEquals(emptyMap<Long, Long>(), share.perItem)
        assertEquals(0L, share.total)
    }

    @Test
    fun selfOf_sameGroup_splitsByItemYen() {
        // 同じまとまりの自分の行 300 円を、品目の税込円の比(1000 : 500)で配る
        val items = listOf(ItemYen(1L, 10L, 1000, "食品"), ItemYen(2L, 10L, 500, "食品"))
        val result = SplitResult(lines = listOf(SplitLine(self, 10L, 300)), warnings = emptyList())
        val share = SelfShare.of(result, self, items)
        assertEquals(mapOf(1L to 200L, 2L to 100L), share.perItem)
        assertEquals(300L, share.total)
        assertEquals(share.total, share.perItem.values.sum())
    }
}
