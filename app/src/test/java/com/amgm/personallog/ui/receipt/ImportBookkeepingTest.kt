package com.amgm.personallog.ui.receipt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportBookkeepingTest {
    @Test
    fun claimAnywayReservesHashForOthers() {
        val c = HashClaims()
        // 重複と分かっていても追加する項目も予約する
        c.claimAnyway("h", 1L)
        assertTrue(c.isClaimed("h"))
        assertFalse(c.claimIfAbsent("h", 2L))
        c.release(1L)
        assertFalse(c.isClaimed("h"))
        assertTrue(c.claimIfAbsent("h", 2L))
    }

    @Test
    fun claimAnywayDoesNotStealExistingClaim() {
        val c = HashClaims()
        assertTrue(c.claimIfAbsent("h", 1L))
        c.claimAnyway("h", 2L)
        c.release(2L)
        assertTrue(c.isClaimed("h"))
        c.release(1L)
        assertFalse(c.isClaimed("h"))
    }
}
