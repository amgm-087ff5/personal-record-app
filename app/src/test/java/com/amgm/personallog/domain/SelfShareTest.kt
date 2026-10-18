package com.amgm.personallog.domain

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
}
