package com.amgm.personallog.ui.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportBookkeepingTest {
    @Test
    fun opensImmediatelyWhenNoAdjustScreen() {
        val d = DeferredOpen()
        assertEquals(5L, d.offer(5L, adjustActive = false))
        assertNull(d.release(adjustActive = false))
    }

    @Test
    fun defersWhileAdjustScreenIsShownAndOpensLatestAfterwards() {
        val d = DeferredOpen()
        assertNull(d.offer(5L, adjustActive = true))
        assertNull(d.offer(6L, adjustActive = true))
        // まだ補正画面が出ている間は開かない
        assertNull(d.release(adjustActive = true))
        // 閉じたら最後の1件だけ(溜めない)
        assertEquals(6L, d.release(adjustActive = false))
        assertNull(d.release(adjustActive = false))
    }

    @Test
    fun clearDropsDeferred() {
        val d = DeferredOpen()
        d.offer(5L, adjustActive = true)
        d.clear()
        assertNull(d.release(adjustActive = false))
    }

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
