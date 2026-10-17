package com.amgm.personallog.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptRegionTest {
    // ---- 90度の回数と微調整(スライダーの端で値が跳ばない) ----

    /** 画面と同じ流れ: 状態を更新し、合計の角度を親が持ち、次の描画で分け直し(reconciled)を通す。 */
    private fun settle(p: RotationParts): RotationParts = p.reconciled(p.degrees)

    @Test
    fun sliderEndsDoNotJump() {
        var p = RotationParts()
        p = settle(p.withFine(45))
        assertEquals(RotationParts(0, 45), p)
        assertEquals(45f, p.degrees, 0f)
        // 指を置いたまま次の通知が来ても、つまみは右端のまま
        p = settle(p.withFine(45))
        assertEquals(RotationParts(0, 45), p)
        // 右端から左端へ、左端から右端へ(+45 と -45 の往復)
        p = settle(p.withFine(-45))
        assertEquals(RotationParts(0, -45), p)
        assertEquals(315f, p.degrees, 0f)
        p = settle(p.withFine(45))
        assertEquals(RotationParts(0, 45), p)
        for (i in 0 until 5) {
            p = settle(p.withFine(-45)); assertEquals(0, p.quarters); assertEquals(-45, p.fine)
            p = settle(p.withFine(45)); assertEquals(0, p.quarters); assertEquals(45, p.fine)
        }
    }

    @Test
    fun sliderEndsAfterQuarterTurns() {
        var p = RotationParts().turned(1)
        p = settle(p.withFine(45))
        assertEquals(RotationParts(1, 45), p)
        assertEquals(135f, p.degrees, 0f)
        p = settle(p.withFine(-45))
        assertEquals(RotationParts(1, -45), p)
        assertEquals(45f, p.degrees, 0f)
        // 合計が同じ角度なら、分け直さずに今の状態を保つ(45度を (0,45) でも (1,-45) でも)
        assertEquals(RotationParts(0, 45), RotationParts(0, 45).reconciled(45f))
        assertEquals(RotationParts(1, -45), RotationParts(1, -45).reconciled(45f))
    }

    @Test
    fun fineValueIsClampedAndQuartersWrap() {
        assertEquals(45, RotationParts().withFine(99).fine)
        assertEquals(-45, RotationParts().withFine(-99).fine)
        assertEquals(3, RotationParts().turned(-1).quarters)
        assertEquals(0, RotationParts(3, 0).turned(1).quarters)
        assertEquals(RotationParts(1, 10), RotationParts(1, 10).turned(4))
    }

    @Test
    fun reconcileSplitsAgainOnlyWhenTotalChanged() {
        assertEquals(RotationParts(0, 0), RotationParts(1, 10).reconciled(0f))
        assertEquals(RotationParts(2, 3), RotationParts(1, 10).reconciled(183f))
        assertEquals(RotationParts(1, 10), RotationParts(1, 10).reconciled(100f))
    }

    // ---- 範囲の計画 ----

    private fun adjust(rot: Float = 0f, l: Float = 0f, t: Float = 0f, r: Float = 1f, b: Float = 1f) =
        ReceiptAdjust(rot, l, t, r, b)

    @Test
    fun hugePhotoFullFrameIsReadAtReducedSize() {
        // 1億画素(12000x9000)を全体で回転: 縮小して読む。全体を等倍で読まない
        val p = ReceiptRegion.plan(12000, 9000, 1, adjust(rot = 10f), 2048)!!
        assertTrue(p.sampleSize >= 4)
        val reg = p.region!!
        val px = (reg[2] - reg[0]).toLong() / p.sampleSize * ((reg[3] - reg[1]).toLong() / p.sampleSize)
        assertTrue("読む画素数 $px", px <= 16_000_000L)
        assertEquals(2048, maxOf(p.outWidth, p.outHeight))
    }

    @Test
    fun smallFrameIsReadAtNearFullResolution() {
        // 中央 20% の枠: 元画像の解像度が許す限り、長辺 2048 で送れる
        val p = ReceiptRegion.plan(12000, 9000, 1, adjust(l = 0.4f, t = 0.4f, r = 0.6f, b = 0.6f), 2048)!!
        assertEquals(1, p.sampleSize)
        assertEquals(2048, maxOf(p.outWidth, p.outHeight))
        val reg = p.region!!
        // 枠は 2400x1800。余白を含めても少し大きい程度
        assertTrue(reg[2] - reg[0] in 2400..2420)
        assertTrue(reg[3] - reg[1] in 1800..1820)
    }

    @Test
    fun frameSmallerThanOutputIsNotUpscaled() {
        // 枠が 1200x900 しかないなら出力もそれ以上にしない(等倍で読む)
        val p = ReceiptRegion.plan(12000, 9000, 1, adjust(l = 0.45f, t = 0.45f, r = 0.55f, b = 0.55f), 2048)!!
        assertEquals(1, p.sampleSize)
        // 画素への丸め(切り上げ・切り下げ)で数画素の差が出る
        assertTrue("outWidth=${p.outWidth}", p.outWidth in 1199..1204)
        assertTrue("outHeight=${p.outHeight}", p.outHeight in 899..904)
    }

    @Test
    fun sampleSizeIsLargestPowerOfTwoNotBelowTarget() {
        // 4000x3000 を 1000 に縮める: scale 0.25 -> sample 4
        val p = ReceiptRegion.plan(4000, 3000, 1, adjust(l = 0f, t = 0f, r = 0.9999f, b = 1f), 1000)!!
        assertEquals(4, p.sampleSize)
        // scale 0.3 -> 1/scale = 3.3 -> sample 2
        val q = ReceiptRegion.plan(4000, 3000, 1, adjust(rot = 90f), 1200)!!
        assertEquals(2, q.sampleSize)
    }

    @Test
    fun regionPixelCapRaisesSampleSize() {
        val p = ReceiptRegion.plan(8000, 6000, 1, adjust(l = 0f, t = 0f, r = 0.9f, b = 1f), 8000, maxRegionPixels = 1_000_000L)!!
        val reg = p.region!!
        assertTrue(p.sampleSize >= 8)
        assertTrue((reg[2] - reg[0]).toLong() / p.sampleSize * ((reg[3] - reg[1]).toLong() / p.sampleSize) <= 1_000_000L)
    }

    @Test
    fun invalidInputGivesNull() {
        assertNull(ReceiptRegion.plan(0, 100, 1, adjust(rot = 10f), 2048))
        assertNull(ReceiptRegion.plan(100, 100, 1, adjust(rot = 10f), 0))
    }

    @Test
    fun frameOutsideTheRotatedImageHasNoRegion() {
        // 45度に回した細長い画像(左上から右下へ斜めに伸びる)の、右上の隅(余白だけ)を枠にする
        val p = ReceiptRegion.plan(1000, 100, 1, adjust(rot = 45f, l = 0.9f, t = 0f, r = 1f, b = 0.1f), 2048)!!
        assertNull(p.region)
        assertTrue(p.outWidth >= 1 && p.outHeight >= 1)
    }

    /** 範囲が、枠に当たる原本のすべての画素を含む(回転・EXIF の逆算が正しい)。 */
    @Test
    fun regionCoversEveryRawPixelInsideTheFrame() {
        val rawW = 120
        val rawH = 80
        val adjusts = listOf(
            adjust(rot = 0f, l = 0.2f, t = 0.1f, r = 0.7f, b = 0.9f),
            adjust(rot = 30f),
            adjust(rot = 30f, l = 0.3f, t = 0.2f, r = 0.8f, b = 0.6f),
            adjust(rot = 90f, l = 0.1f, t = 0.1f, r = 0.5f, b = 0.5f),
            adjust(rot = 137f, l = 0.25f, t = 0.25f, r = 0.95f, b = 0.75f),
            adjust(rot = 315f, l = 0f, t = 0.4f, r = 0.6f, b = 1f),
            adjust(rot = 270f),
        )
        for (o in 1..8) for (a in adjusts) {
            val p = ReceiptRegion.plan(rawW, rawH, o, a, 2048)!!
            val reg = p.region
            var inside = 0
            for (y in 0 until rawH) for (x in 0 until rawW) {
                val b = ReceiptRegion.rawToBound(o, rawW, rawH, a, x + 0.5, y + 0.5)
                val inFrame = b[0] >= p.crop[0] && b[0] <= p.crop[2] && b[1] >= p.crop[1] && b[1] <= p.crop[3]
                if (!inFrame) continue
                inside++
                assertNotNull("o=$o a=$a: 枠内の画素があるのに範囲が無い", reg)
                assertTrue("o=$o a=$a x=$x y=$y", x >= reg!![0] && x < reg[2] && y >= reg[1] && y < reg[3])
            }
            if (reg == null) assertEquals(0, inside)
        }
    }

    @Test
    fun exifSizeSwapsForRotatedOrientations() {
        for (o in 1..8) {
            val p = ReceiptRegion.plan(200, 100, o, adjust(rot = 90f), 4096)!!
            if (o in 5..8) {
                assertEquals(100, p.exifWidth); assertEquals(200, p.exifHeight)
                assertEquals(200, p.boundWidth); assertEquals(100, p.boundHeight)
            } else {
                assertEquals(200, p.exifWidth); assertEquals(100, p.exifHeight)
                assertEquals(100, p.boundWidth); assertEquals(200, p.boundHeight)
            }
        }
    }

    // ---- 取り込みの残骸の選別 ----

    private val hour = ReceiptOrphans.MIN_AGE_MS

    @Test
    fun orphansSelectOnlyOldUnreferencedUnusedFiles() {
        val now = 10 * hour
        val files = listOf(
            ReceiptOrphans.Candidate("receipts/ref.jpg", 0L + 1),
            ReceiptOrphans.Candidate("receipts/old.jpg", now - 2 * hour),
            ReceiptOrphans.Candidate("receipts/exact.jpg", now - hour),
            ReceiptOrphans.Candidate("receipts/new.jpg", now - hour + 1),
            ReceiptOrphans.Candidate("receipts/busy.jpg", now - 5 * hour),
            ReceiptOrphans.Candidate("receipts/unknown.jpg", 0L),
        )
        val sel = ReceiptOrphans.select(files, referenced = listOf("receipts/ref.jpg"), inUse = listOf("receipts/busy.jpg"), now = now)
        assertEquals(listOf("receipts/old.jpg", "receipts/exact.jpg"), sel)
    }

    @Test
    fun orphansComparePathsIgnoringSeparatorStyle() {
        val now = 10 * hour
        val files = listOf(ReceiptOrphans.Candidate("receipts/a.jpg", 1L), ReceiptOrphans.Candidate("receipts/b.jpg", 1L))
        val sel = ReceiptOrphans.select(files, referenced = listOf("receipts\\a.jpg"), inUse = listOf("/receipts/b.jpg"), now = now)
        assertTrue(sel.isEmpty())
    }
}
