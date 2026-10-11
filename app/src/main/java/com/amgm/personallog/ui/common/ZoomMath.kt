package com.amgm.personallog.ui.common

import kotlin.math.max
import kotlin.math.min

/**
 * 画像の拡大・移動の純粋な計算(Androidに依存しない。JVMユニットテスト可)。
 *
 * 座標はすべて「容器の中心を原点」とした px。描画側は graphicsLayer で
 * scale と translation を適用し、画像は容器内に ContentScale.Fit で中央表示する前提。
 * 拡大の基準点は容器の中心(transformOrigin = Center)。
 */
object ZoomMath {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 5f
    const val DOUBLE_TAP_SCALE = 2.5f

    private const val EPS = 0.001f

    /** 拡大率と移動量。offsetX/offsetY は中心基準の平行移動(px)。 */
    data class Transform(
        val scale: Float = MIN_SCALE,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
    )

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** ContentScale.Fit で容器に収めたときの画像の表示サイズ(px)。 */
    fun fitSize(imageW: Float, imageH: Float, containerW: Float, containerH: Float): Pair<Float, Float> {
        if (imageW <= 0f || imageH <= 0f || containerW <= 0f || containerH <= 0f) return 0f to 0f
        val k = min(containerW / imageW, containerH / imageH)
        return imageW * k to imageH * k
    }

    /**
     * 片側の移動上限(px)。拡大後の表示幅・高さが容器を超えた分の半分。
     * 容器より小さければ 0(中央に固定し、画像の端が画面の内側に入らない)。
     */
    fun maxOffset(
        fitW: Float,
        fitH: Float,
        containerW: Float,
        containerH: Float,
        scale: Float,
    ): Pair<Float, Float> =
        max(0f, (fitW * scale - containerW) / 2f) to max(0f, (fitH * scale - containerH) / 2f)

    fun clampOffset(x: Float, y: Float, maxX: Float, maxY: Float): Pair<Float, Float> =
        x.coerceIn(-maxX, maxX) to y.coerceIn(-maxY, maxY)

    /** 移動量を上限内に収めた状態を返す。 */
    fun clamped(
        t: Transform,
        fitW: Float,
        fitH: Float,
        containerW: Float,
        containerH: Float,
    ): Transform {
        val (mx, my) = maxOffset(fitW, fitH, containerW, containerH, t.scale)
        val (x, y) = clampOffset(t.offsetX, t.offsetY, mx, my)
        return t.copy(offsetX = x, offsetY = y)
    }

    /**
     * ピンチ・ドラッグを1フレーム分反映する。
     *
     * @param zoom このフレームの倍率変化(detectTransformGestures の zoom)
     * @param panX/panY このフレームの中心の移動量(pan)
     * @param centroidX/centroidY 現在の二本指の中心(容器中心基準)
     *
     * 直前の中心の下にあった画像上の点が、新しい中心の下に来るよう平行移動を求める。
     */
    fun applyGesture(
        current: Transform,
        zoom: Float,
        panX: Float,
        panY: Float,
        centroidX: Float,
        centroidY: Float,
        fitW: Float,
        fitH: Float,
        containerW: Float,
        containerH: Float,
    ): Transform {
        val newScale = clampScale(current.scale * zoom)
        val z = newScale / current.scale
        val oldCx = centroidX - panX
        val oldCy = centroidY - panY
        val tx = centroidX - z * (oldCx - current.offsetX)
        val ty = centroidY - z * (oldCy - current.offsetY)
        return clamped(Transform(newScale, tx, ty), fitW, fitH, containerW, containerH)
    }

    /**
     * ダブルタップ。拡大中(1倍より大きい)なら 1倍に戻す。1倍なら [DOUBLE_TAP_SCALE] に拡大し、
     * タップ位置の画像上の点が動かないように移動する。
     */
    fun doubleTap(
        current: Transform,
        tapX: Float,
        tapY: Float,
        fitW: Float,
        fitH: Float,
        containerW: Float,
        containerH: Float,
    ): Transform {
        if (current.scale > MIN_SCALE + EPS) return Transform()
        val z = DOUBLE_TAP_SCALE / current.scale
        val tx = tapX - z * (tapX - current.offsetX)
        val ty = tapY - z * (tapY - current.offsetY)
        return clamped(Transform(DOUBLE_TAP_SCALE, tx, ty), fitW, fitH, containerW, containerH)
    }
}
