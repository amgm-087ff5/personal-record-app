package com.amgm.personallog.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    // 容器は縦長 1000x2000、画像は横長 4000x3000 → Fit 表示は 1000x750
    private val cw = 1000f
    private val ch = 2000f
    private fun fit(): Pair<Float, Float> = ZoomMath.fitSize(4000f, 3000f, cw, ch)

    @Test
    fun clampScale_limitsTo1to5() {
        assertEquals(1f, ZoomMath.clampScale(0.5f), 1e-4f)
        assertEquals(3f, ZoomMath.clampScale(3f), 1e-4f)
        assertEquals(5f, ZoomMath.clampScale(9f), 1e-4f)
    }

    @Test
    fun fitSize_fitsInsideContainer() {
        val (w, h) = fit()
        assertEquals(1000f, w, 1e-3f)
        assertEquals(750f, h, 1e-3f)
        val (pw, ph) = ZoomMath.fitSize(1000f, 2000f, cw, ch)
        assertEquals(1000f, pw, 1e-3f)
        assertEquals(2000f, ph, 1e-3f)
        val (zw, zh) = ZoomMath.fitSize(0f, 0f, cw, ch)
        assertEquals(0f, zw, 0f)
        assertEquals(0f, zh, 0f)
    }

    @Test
    fun maxOffset_isZeroAtOneTimes() {
        val (fw, fh) = fit()
        val (mx, my) = ZoomMath.maxOffset(fw, fh, cw, ch, 1f)
        assertEquals(0f, mx, 0f)
        assertEquals(0f, my, 0f)
    }

    @Test
    fun maxOffset_usesOverflowHalf() {
        val (fw, fh) = fit()
        // 2倍: 横 2000 - 1000 = 1000 の半分 = 500、縦 1500 < 2000 なので 0
        val (mx, my) = ZoomMath.maxOffset(fw, fh, cw, ch, 2f)
        assertEquals(500f, mx, 1e-3f)
        assertEquals(0f, my, 0f)
        // 2.5倍: 横 2500 - 1000 の半分 = 750
        val (mx25, _) = ZoomMath.maxOffset(fw, fh, cw, ch, 2.5f)
        assertEquals(750f, mx25, 1e-3f)
    }

    @Test
    fun clampOffset_limitsToBounds() {
        val (x, y) = ZoomMath.clampOffset(900f, -900f, 500f, 100f)
        assertEquals(500f, x, 0f)
        assertEquals(-100f, y, 0f)
        val (x2, y2) = ZoomMath.clampOffset(-10f, 20f, 500f, 100f)
        assertEquals(-10f, x2, 0f)
        assertEquals(20f, y2, 0f)
    }

    @Test
    fun drag_atOneTimes_doesNotMove() {
        val (fw, fh) = fit()
        val t = ZoomMath.applyGesture(
            current = ZoomMath.Transform(),
            zoom = 1f, panX = 30f, panY = 40f,
            centroidX = 30f, centroidY = 40f,
            fitW = fw, fitH = fh, containerW = cw, containerH = ch,
        )
        assertEquals(1f, t.scale, 1e-4f)
        assertEquals(0f, t.offsetX, 0f)
        assertEquals(0f, t.offsetY, 0f)
    }

    @Test
    fun drag_whileZoomed_movesAndStopsAtEdge() {
        val (fw, fh) = fit()
        val zoomed = ZoomMath.Transform(scale = 2f)
        val moved = ZoomMath.applyGesture(
            current = zoomed, zoom = 1f, panX = -100f, panY = 0f,
            centroidX = -100f, centroidY = 0f,
            fitW = fw, fitH = fh, containerW = cw, containerH = ch,
        )
        assertEquals(-100f, moved.offsetX, 1e-3f)
        // 大きく動かしても画像の端が画面の内側に入らない(上限 500)
        val far = ZoomMath.applyGesture(
            current = zoomed, zoom = 1f, panX = 5000f, panY = 0f,
            centroidX = 5000f, centroidY = 0f,
            fitW = fw, fitH = fh, containerW = cw, containerH = ch,
        )
        assertEquals(500f, far.offsetX, 1e-3f)
    }

    @Test
    fun pinch_keepsPointUnderCentroid() {
        val (fw, fh) = fit()
        val before = ZoomMath.Transform(scale = 1f)
        val cx = 200f
        val cy = 0f
        val after = ZoomMath.applyGesture(
            current = before, zoom = 2f, panX = 0f, panY = 0f,
            centroidX = cx, centroidY = cy,
            fitW = fw, fitH = fh, containerW = cw, containerH = ch,
        )
        assertEquals(2f, after.scale, 1e-4f)
        // ピンチ前にcentroidの下にあった画像上の点 p = (cx - 0) / 1 = 200。
        // 拡大後も同じ点が centroid に来る: (cx - offsetX) / scale = 200
        val pointUnderCentroid = (cx - after.offsetX) / after.scale
        assertEquals(200f, pointUnderCentroid, 1e-3f)
    }

    @Test
    fun pinch_clampsAtMaxScale() {
        val (fw, fh) = fit()
        val t = ZoomMath.applyGesture(
            current = ZoomMath.Transform(scale = 5f), zoom = 2f, panX = 0f, panY = 0f,
            centroidX = 0f, centroidY = 0f,
            fitW = fw, fitH = fh, containerW = cw, containerH = ch,
        )
        assertEquals(5f, t.scale, 1e-4f)
    }

    @Test
    fun pinch_clampsAtMinScale() {
        val (fw, fh) = fit()
        val t = ZoomMath.applyGesture(
            current = ZoomMath.Transform(), zoom = 0.5f, panX = 0f, panY = 0f,
            centroidX = 100f, centroidY = 100f,
            fitW = fw, fitH = fh, containerW = cw, containerH = ch,
        )
        assertEquals(1f, t.scale, 1e-4f)
        assertEquals(0f, t.offsetX, 0f)
        assertEquals(0f, t.offsetY, 0f)
    }

    @Test
    fun doubleTap_fromOneTimes_zoomsToTwoPointFiveAroundTap() {
        val (fw, fh) = fit()
        val t = ZoomMath.doubleTap(
            current = ZoomMath.Transform(), tapX = -200f, tapY = 0f,
            fitW = fw, fitH = fh, containerW = cw, containerH = ch,
        )
        assertEquals(2.5f, t.scale, 1e-4f)
        // タップ位置の画像上の点(-200)が動かない: (-200 - offsetX) / 2.5 = -200
        assertEquals(-200f, (-200f - t.offsetX) / t.scale, 1e-3f)
        assertEquals(300f, t.offsetX, 1e-3f)
        assertEquals(0f, t.offsetY, 0f)
    }

    @Test
    fun doubleTap_atCenter_keepsCentered() {
        val (fw, fh) = fit()
        val t = ZoomMath.doubleTap(
            current = ZoomMath.Transform(), tapX = 0f, tapY = 0f,
            fitW = fw, fitH = fh, containerW = cw, containerH = ch,
        )
        assertEquals(2.5f, t.scale, 1e-4f)
        assertEquals(0f, t.offsetX, 1e-3f)
        assertEquals(0f, t.offsetY, 1e-3f)
    }

    @Test
    fun doubleTap_whenZoomed_resetsToOneTimes() {
        val (fw, fh) = fit()
        val t = ZoomMath.doubleTap(
            current = ZoomMath.Transform(scale = 2.5f, offsetX = 300f, offsetY = 0f),
            tapX = 10f, tapY = 10f,
            fitW = fw, fitH = fh, containerW = cw, containerH = ch,
        )
        assertEquals(ZoomMath.Transform(), t)
    }

    @Test
    fun doubleTap_whenPinchedToOtherScale_resetsToOneTimes() {
        val (fw, fh) = fit()
        val t = ZoomMath.doubleTap(
            current = ZoomMath.Transform(scale = 4f, offsetX = 100f, offsetY = 50f),
            tapX = 0f, tapY = 0f,
            fitW = fw, fitH = fh, containerW = cw, containerH = ch,
        )
        assertEquals(ZoomMath.Transform(), t)
    }
}
