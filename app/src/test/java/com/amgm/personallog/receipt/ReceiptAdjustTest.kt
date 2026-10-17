package com.amgm.personallog.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptAdjustTest {
    private val limit = 3_500_000L

    @Test
    fun normalizeDegrees() {
        assertEquals(0f, ReceiptGeometry.normalizeDegrees(0f), 0f)
        assertEquals(350f, ReceiptGeometry.normalizeDegrees(-10f), 0.001f)
        assertEquals(10f, ReceiptGeometry.normalizeDegrees(370f), 0.001f)
        assertEquals(0f, ReceiptGeometry.normalizeDegrees(360f), 0f)
        assertEquals(0f, ReceiptGeometry.normalizeDegrees(-360f), 0f)
        assertEquals(0f, ReceiptGeometry.normalizeDegrees(Float.NaN), 0f)
        assertEquals(0f, ReceiptGeometry.normalizeDegrees(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, ReceiptGeometry.normalizeDegrees(359.999f), 0f)
    }

    @Test
    fun splitAndCombineDegrees() {
        assertEquals(0 to 0, ReceiptGeometry.splitDegrees(0f))
        assertEquals(1 to 3, ReceiptGeometry.splitDegrees(93f))
        assertEquals(0 to -5, ReceiptGeometry.splitDegrees(355f))
        assertEquals(3 to 0, ReceiptGeometry.splitDegrees(270f))
        assertEquals(2 to -45, ReceiptGeometry.splitDegrees(135f))
        // 境界の 45 度は常に「次の90度の -45」側
        assertEquals(1 to -45, ReceiptGeometry.splitDegrees(45f))
        assertEquals(3 to -45, ReceiptGeometry.splitDegrees(225f))
        assertEquals(0 to 44, ReceiptGeometry.splitDegrees(44f))
        assertEquals(1 to -45, ReceiptGeometry.splitDegrees(44.6f))
        assertEquals(0 to 0, ReceiptGeometry.splitDegrees(359.7f))
        for (r in 0 until 360) {
            val (q, f) = ReceiptGeometry.splitDegrees(r.toFloat())
            assertTrue("fine=$f at $r", f in -45..44)
            assertTrue(q in 0..3)
            assertEquals(r.toFloat(), ReceiptGeometry.combineDegrees(q, f), 0f)
        }
        assertEquals(93f, ReceiptGeometry.combineDegrees(1, 3), 0f)
        assertEquals(355f, ReceiptGeometry.combineDegrees(0, -5), 0f)
        assertEquals(0f, ReceiptGeometry.combineDegrees(4, 0), 0f)
        for (q in 0..3) for (f in -45..45) {
            val (q2, f2) = ReceiptGeometry.splitDegrees(ReceiptGeometry.combineDegrees(q, f))
            assertEquals(ReceiptGeometry.combineDegrees(q, f), ReceiptGeometry.combineDegrees(q2, f2), 0f)
        }
    }

    @Test
    fun boundingSize() {
        assertEquals(200 to 100, ReceiptGeometry.boundingSize(200, 100, 0f))
        assertEquals(100 to 200, ReceiptGeometry.boundingSize(200, 100, 90f))
        assertEquals(200 to 100, ReceiptGeometry.boundingSize(200, 100, 180f))
        assertEquals(100 to 200, ReceiptGeometry.boundingSize(200, 100, 270f))
        assertEquals(100 to 200, ReceiptGeometry.boundingSize(200, 100, -90f))
        // 45度: (200+100)/sqrt2 = 212.13 -> 213
        assertEquals(213 to 213, ReceiptGeometry.boundingSize(200, 100, 45f))
        assertEquals(213 to 213, ReceiptGeometry.boundingSize(200, 100, 315f))
        assertEquals(0 to 0, ReceiptGeometry.boundingSize(0, 100, 10f))
    }

    @Test
    fun clampCropKeepsMinSizeAndRange() {
        val m = ReceiptGeometry.MIN_CROP
        val c = ReceiptGeometry.clampCrop(-0.5f, -1f, 2f, 3f)
        assertEquals(listOf(0f, 0f, 1f, 1f), c.toList())
        val small = ReceiptGeometry.clampCrop(0.5f, 0.5f, 0.5f, 0.5f)
        assertEquals(m, small[2] - small[0], 0.0001f)
        assertEquals(m, small[3] - small[1], 0.0001f)
        // 端での最小サイズは範囲内にずらす
        val edge = ReceiptGeometry.clampCrop(0.98f, 0f, 1f, 1f)
        assertEquals(1f, edge[2], 0f)
        assertEquals(1f - m, edge[0], 0.0001f)
        val edge0 = ReceiptGeometry.clampCrop(0f, 0f, 0.01f, 1f)
        assertEquals(0f, edge0[0], 0f)
        assertEquals(m, edge0[2], 0.0001f)
        // 逆順は入れ替え
        val swapped = ReceiptGeometry.clampCrop(0.8f, 0.9f, 0.2f, 0.1f)
        assertEquals(listOf(0.2f, 0.1f, 0.8f, 0.9f), swapped.toList())
        val nan = ReceiptGeometry.clampCrop(Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        assertEquals(listOf(0f, 0f, 1f, 1f), nan.toList())
    }

    @Test
    fun cropToPixels() {
        assertEquals(listOf(0, 0, 200, 100), ReceiptGeometry.cropToPixels(200, 100, 0f, 0f, 1f, 1f).toList())
        assertEquals(listOf(20, 10, 180, 90), ReceiptGeometry.cropToPixels(200, 100, 0.1f, 0.1f, 0.9f, 0.9f).toList())
        // 範囲外は収める
        assertEquals(listOf(0, 0, 200, 100), ReceiptGeometry.cropToPixels(200, 100, -1f, -1f, 5f, 5f).toList())
        // 最小サイズの枠は最低1画素以上
        val r = ReceiptGeometry.cropToPixels(5, 5, 0.5f, 0.5f, 0.5f, 0.5f)
        assertTrue(r[2] - r[0] >= 1)
        assertTrue(r[3] - r[1] >= 1)
        assertTrue(r[0] >= 0 && r[1] >= 0 && r[2] <= 5 && r[3] <= 5)
        // 切り捨て/切り上げで枠を欠かさない
        val q = ReceiptGeometry.cropToPixels(101, 101, 0.25f, 0.25f, 0.75f, 0.75f)
        assertEquals(listOf(25, 25, 76, 76), q.toList())
    }

    @Test
    fun changingAngleResetsCrop() {
        val a = ReceiptAdjust.NONE.withCrop(0.1f, 0.2f, 0.8f, 0.9f)
        assertEquals(0.1f, a.cropLeft, 0f)
        assertFalse(a.isFullCrop)
        val rotated = a.withRotation(90f)
        assertEquals(90f, rotated.rotationDegrees, 0f)
        assertTrue(rotated.isFullCrop)
        // 同じ角度なら枠を保つ(360度の周回も同じ)
        assertSame(a, a.withRotation(0f))
        assertSame(a, a.withRotation(360f))
        val keep = rotated.withCrop(0.2f, 0.2f, 0.7f, 0.7f)
        assertEquals(0.2f, keep.withRotation(90f).cropLeft, 0f)
        assertTrue(keep.withRotation(91f).isFullCrop)
        assertEquals(350f, ReceiptAdjust.NONE.withRotation(-10f).rotationDegrees, 0.001f)
    }

    @Test
    fun withCropClamps() {
        val a = ReceiptAdjust.NONE.withCrop(-1f, 0.5f, 0.52f, 2f)
        assertEquals(0f, a.cropLeft, 0f)
        assertEquals(1f, a.cropBottom, 0f)
        assertTrue(a.cropRight - a.cropLeft >= ReceiptGeometry.MIN_CROP - 0.0001f)
        assertTrue(a.cropBottom - a.cropTop >= ReceiptGeometry.MIN_CROP - 0.0001f)
    }

    @Test
    fun isNone() {
        assertTrue(ReceiptAdjust.NONE.isNone)
        assertTrue(ReceiptAdjust(rotationDegrees = 360f).isNone)
        assertTrue(ReceiptAdjust(rotationDegrees = 0.001f).isNone)
        assertFalse(ReceiptAdjust(rotationDegrees = 1f).isNone)
        assertFalse(ReceiptAdjust(rotationDegrees = 90f).isNone)
        assertFalse(ReceiptAdjust(cropLeft = 0.05f).isNone)
        assertFalse(ReceiptAdjust(cropBottom = 0.9f).isNone)
        assertTrue(ReceiptAdjust(cropLeft = 0.0005f, cropRight = 0.9995f).isNone)
    }

    @Test
    fun sanitizedFixesBrokenValues() {
        val s = ReceiptAdjust(-90f, 0.9f, 0.9f, 0.1f, 0.1f).sanitized()
        assertEquals(270f, s.rotationDegrees, 0.001f)
        assertTrue(s.cropLeft < s.cropRight && s.cropTop < s.cropBottom)
    }

    @Test
    fun shouldSendOriginalOnlyWhenAllThreeHold() {
        val none = ReceiptAdjust.NONE
        assertTrue(ReceiptGeometry.shouldSendOriginal(true, none, 1_000L, limit))
        assertTrue(ReceiptGeometry.shouldSendOriginal(true, none, limit, limit))
        // EXIF が通常でなければ、小さくても必ず変換する
        assertFalse(ReceiptGeometry.shouldSendOriginal(false, none, 1_000L, limit))
        // 補正あり
        assertFalse(ReceiptGeometry.shouldSendOriginal(true, ReceiptAdjust(rotationDegrees = 90f), 1_000L, limit))
        assertFalse(ReceiptGeometry.shouldSendOriginal(true, none.withCrop(0.1f, 0f, 1f, 1f), 1_000L, limit))
        // サイズ超過
        assertFalse(ReceiptGeometry.shouldSendOriginal(true, none, limit + 1, limit))
        // 組み合わせ
        assertFalse(ReceiptGeometry.shouldSendOriginal(false, ReceiptAdjust(rotationDegrees = 5f), limit + 1, limit))
    }
}
