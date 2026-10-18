package com.amgm.personallog.domain.split

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * 端数の配り方(YenAllocator)の期待値テスト。
 * 人は id 1〜5(1=髙木が支払者、order は id−1)。重みは並びの順に id 1, 2, ... へ対応する。
 */
class YenAllocatorTest {
    private val order = mapOf(1L to 0, 2L to 1, 3L to 2, 4L to 3, 5L to 4)
    private val payers = setOf(1L)

    /** 重みを並び順に id 1, 2, ... へ割り当てて配る。 */
    private fun alloc(amount: Int, vararg weights: Int): Map<Long, Int> {
        val list = weights.mapIndexed { i, w -> (i + 1L) to w }
        return YenAllocator.allocate(amount, list, payers, order)
    }

    private fun assertSumIs(amount: Int, result: Map<Long, Int>) {
        assertEquals(amount, result.values.sum())
    }

    @Test
    fun equal400Among3_paysRemainderToNonPayerFirst() {
        // 400/3 = 133 余り1。非支払者の中で order の小さい山田(2)へ。
        val r = alloc(400, 1, 1, 1)
        assertEquals(133, r[1L])
        assertEquals(134, r[2L])
        assertEquals(133, r[3L])
        assertSumIs(400, r)
    }

    @Test
    fun equal500Among3() {
        // 500/3 = 166 余り2。山田・佐藤に1円ずつ。
        val r = alloc(500, 1, 1, 1)
        assertEquals(166, r[1L])
        assertEquals(167, r[2L])
        assertEquals(167, r[3L])
        assertSumIs(500, r)
    }

    @Test
    fun equal1000Among3() {
        val r = alloc(1000, 1, 1, 1)
        assertEquals(333, r[1L])
        assertEquals(334, r[2L])
        assertEquals(333, r[3L])
        assertSumIs(1000, r)
    }

    @Test
    fun ratio800With3_3_2_isExact() {
        val r = alloc(800, 3, 3, 2)
        assertEquals(300, r[1L])
        assertEquals(300, r[2L])
        assertEquals(200, r[3L])
        assertSumIs(800, r)
    }

    @Test
    fun ratio900With3_3_2_remainderToSecond() {
        // 900 * 3/8 = 337 余り4、同じく山田も余り4、佐藤は225余り0。残り1円は rem の大きい山田へ。
        val r = alloc(900, 3, 3, 2)
        assertEquals(337, r[1L])
        assertEquals(338, r[2L])
        assertEquals(225, r[3L])
        assertSumIs(900, r)
    }

    @Test
    fun ratio400With2_2_1_1_1() {
        // 400/7 系の端数。残り1円は山田(余り2が最大の非支払者)へ。
        val r = alloc(400, 2, 2, 1, 1, 1)
        assertEquals(114, r[1L])
        assertEquals(115, r[2L])
        assertEquals(57, r[3L])
        assertEquals(57, r[4L])
        assertEquals(57, r[5L])
        assertSumIs(400, r)
    }

    @Test
    fun ratio500With2_2_1_1_1() {
        // 残り3円は 山田(rem6)→佐藤→D の順。E は端数なし。
        val r = alloc(500, 2, 2, 1, 1, 1)
        assertEquals(142, r[1L])
        assertEquals(143, r[2L])
        assertEquals(72, r[3L])
        assertEquals(72, r[4L])
        assertEquals(71, r[5L])
        assertSumIs(500, r)
    }

    @Test
    fun ratio700With2_5_isExact() {
        val r = alloc(700, 2, 5)
        assertEquals(200, r[1L])
        assertEquals(500, r[2L])
        assertSumIs(700, r)
    }

    @Test
    fun ratio500With2_5_remainderToNonPayer() {
        val r = alloc(500, 2, 5)
        assertEquals(142, r[1L])
        assertEquals(358, r[2L])
        assertSumIs(500, r)
    }

    @Test
    fun negativeAmount_payerGetsRemainderFirst() {
        // 値引き -50 を 1:2 に。支払者(髙木)が先に端数を受けるので -17 / -33。
        val r = alloc(-50, 1, 2)
        assertEquals(-17, r[1L])
        assertEquals(-33, r[2L])
        assertSumIs(-50, r)
    }

    @Test
    fun equal5Among3_remainderToNonPayersInOrder() {
        // 5/3 = 1 余り2。支払者でない山田・佐藤が1円ずつ。
        val r = alloc(5, 1, 1, 1)
        assertEquals(1, r[1L])
        assertEquals(2, r[2L])
        assertEquals(2, r[3L])
        assertSumIs(5, r)
    }

    @Test
    fun zeroWeightPerson_getsZero() {
        val r = alloc(100, 1, 0, 1)
        assertEquals(0, r[2L])
        assertEquals(100, r.values.sum())
    }

    @Test
    fun allZeroWeights_throwsIllegalArgument() {
        // 重みの合計が0なら配れないので例外。
        try {
            alloc(100, 0, 0)
            fail("IllegalArgumentException が出るはず")
        } catch (e: IllegalArgumentException) {
            // 期待どおり
        }
    }
}
