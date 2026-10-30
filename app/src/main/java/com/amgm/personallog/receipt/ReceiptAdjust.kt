package com.amgm.personallog.receipt

import com.amgm.personallog.data.model.Receipt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * レシート画像の補正値(回転とトリミング)。Android に依存しない純Kotlin。
 * - rotationDegrees: 時計回りの度。基準は EXIF の向きを適用した後の画像。0 以上 360 未満に正規化して持つ。
 * - crop*: 回転後の外接矩形に対する 0〜1 の比率。
 */
data class ReceiptAdjust(
    val rotationDegrees: Float = 0f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
) {
    /** 補正なし(回転 0 度で、枠が全体)。 */
    val isNone: Boolean
        get() = ReceiptGeometry.isNoRotation(rotationDegrees) &&
            ReceiptGeometry.isFullCrop(cropLeft, cropTop, cropRight, cropBottom)

    /** 枠が全体か。 */
    val isFullCrop: Boolean get() = ReceiptGeometry.isFullCrop(cropLeft, cropTop, cropRight, cropBottom)

    /** 角度を変える。角度が変わったら枠は全体に戻す。 */
    fun withRotation(degrees: Float): ReceiptAdjust {
        val n = ReceiptGeometry.normalizeDegrees(degrees)
        return if (ReceiptGeometry.sameAngle(n, rotationDegrees)) this else ReceiptAdjust(rotationDegrees = n)
    }

    /** 枠を変える。最小サイズを守り、0〜1に収める。 */
    fun withCrop(left: Float, top: Float, right: Float, bottom: Float): ReceiptAdjust {
        val c = ReceiptGeometry.clampCrop(left, top, right, bottom)
        return copy(cropLeft = c[0], cropTop = c[1], cropRight = c[2], cropBottom = c[3])
    }

    /** 保存してある値を安全な値にそろえる(壊れた値でも落とさない)。 */
    fun sanitized(): ReceiptAdjust {
        val c = ReceiptGeometry.clampCrop(cropLeft, cropTop, cropRight, cropBottom)
        return ReceiptAdjust(ReceiptGeometry.normalizeDegrees(rotationDegrees), c[0], c[1], c[2], c[3])
    }

    companion object {
        val NONE = ReceiptAdjust()
    }
}

/** 回転とトリミングの座標計算。 */
object ReceiptGeometry {
    /** 枠の最小サイズ(全体に対する比率)。 */
    const val MIN_CROP = 0.1f

    /** 角度の比較許容(度)。 */
    private const val ANGLE_EPS = 0.01f

    /** 比率の比較許容。 */
    private const val RATIO_EPS = 0.001f

    /** 角度を 0 以上 360 未満にする。NaN・無限大は 0。360 にほぼ等しい値は 0。 */
    fun normalizeDegrees(degrees: Float): Float {
        if (degrees.isNaN() || degrees.isInfinite()) return 0f
        var r = degrees % 360f
        if (r < 0f) r += 360f
        if (r >= 360f - ANGLE_EPS) r = 0f
        return r
    }

    /** 2つの角度が同じか(360度の周回を考慮)。 */
    fun sameAngle(a: Float, b: Float): Boolean {
        val d = abs(normalizeDegrees(a) - normalizeDegrees(b))
        return min(d, 360f - d) < ANGLE_EPS
    }

    fun isNoRotation(degrees: Float): Boolean = sameAngle(degrees, 0f)

    fun isFullCrop(left: Float, top: Float, right: Float, bottom: Float): Boolean =
        left <= RATIO_EPS && top <= RATIO_EPS && right >= 1f - RATIO_EPS && bottom >= 1f - RATIO_EPS

    /**
     * 合計の角度を (90度の回数 0〜3, 微調整 -45〜+44 の整数度) に分ける。
     * 境界の 45 度は常に「次の90度 -45」側に寄せる(45 -> 1回 -45、135 -> 2回 -45)。
     * 画面の状態は [RotationParts] が別々に持つので、これは読み込み時の分解にだけ使う。
     */
    fun splitDegrees(degrees: Float): Pair<Int, Int> {
        val r = normalizeDegrees(degrees).roundToInt()
        val q = Math.floorDiv(r + 45, 90)
        return (q % 4) to (r - q * 90)
    }

    /** (90度の回数, 微調整) から合計の角度(0 以上 360 未満)。 */
    fun combineDegrees(quarters: Int, fine: Int): Float =
        normalizeDegrees(quarters * 90f + fine.coerceIn(-45, 45))

    /** 回転後の外接矩形の大きさ(画素)。90度単位ではちょうどの値にし、それ以外は切り上げる。 */
    fun boundingSize(width: Int, height: Int, degrees: Float): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val rad = Math.toRadians(normalizeDegrees(degrees).toDouble())
        val c = abs(cos(rad))
        val s = abs(sin(rad))
        // 浮動小数の誤差(1e-9 程度)で 1 画素増えないよう、少し丸めてから切り上げる
        val w = ceil(round6(width * c + height * s)).toInt()
        val h = ceil(round6(width * s + height * c)).toInt()
        return max(1, w) to max(1, h)
    }

    private fun round6(v: Double): Double = Math.round(v * 1_000_000.0) / 1_000_000.0

    /** 枠を最小サイズ以上・0〜1の範囲に収める。[left, top, right, bottom] を返す。 */
    fun clampCrop(left: Float, top: Float, right: Float, bottom: Float): FloatArray {
        fun fix(a: Float, b: Float): Pair<Float, Float> {
            var lo = if (a.isNaN()) 0f else a
            var hi = if (b.isNaN()) 1f else b
            if (lo > hi) { val t = lo; lo = hi; hi = t }
            lo = lo.coerceIn(0f, 1f)
            hi = hi.coerceIn(0f, 1f)
            if (hi - lo < MIN_CROP) {
                // 小さすぎる枠は、中心を保って最小サイズに広げてから範囲内へずらす
                val mid = (lo + hi) / 2f
                lo = mid - MIN_CROP / 2f
                hi = mid + MIN_CROP / 2f
                if (lo < 0f) { hi -= lo; lo = 0f }
                if (hi > 1f) { lo -= (hi - 1f); hi = 1f }
                lo = max(0f, lo)
            }
            return lo to hi
        }
        val (l, r) = fix(left, right)
        val (t, b) = fix(top, bottom)
        return floatArrayOf(l, t, r, b)
    }

    /**
     * 回転後の画像(幅 w、高さ h の画素)に対する枠の比率を、画素の矩形 [left, top, right, bottom] にする。
     * 範囲内に収め、幅・高さは最低 1 画素。right/bottom は排他的(幅 = right - left)。
     */
    fun cropToPixels(w: Int, h: Int, left: Float, top: Float, right: Float, bottom: Float): IntArray {
        if (w <= 0 || h <= 0) return intArrayOf(0, 0, max(w, 0), max(h, 0))
        val c = clampCrop(left, top, right, bottom)
        var l = floor(c[0] * w).toInt().coerceIn(0, w - 1)
        var t = floor(c[1] * h).toInt().coerceIn(0, h - 1)
        var r = ceil(c[2] * w).toInt().coerceIn(l + 1, w)
        var b = ceil(c[3] * h).toInt().coerceIn(t + 1, h)
        if (r <= l) { l = max(0, r - 1); r = l + 1 }
        if (b <= t) { t = max(0, b - 1); b = t + 1 }
        return intArrayOf(l, t, r, b)
    }

    /**
     * 原本のバイト列をそのまま送ってよいか。次の3つをすべて満たすときだけ true。
     * - EXIF の向きが通常 / 補正なし / サイズが閾値以下
     */
    fun shouldSendOriginal(exifNormal: Boolean, adjust: ReceiptAdjust, fileBytes: Long, thresholdBytes: Long): Boolean =
        exifNormal && adjust.isNone && fileBytes <= thresholdBytes
}

/** 支払いに保存してある補正値。 */
fun Receipt.adjust(): ReceiptAdjust =
    ReceiptAdjust(rotationDegrees, cropLeft, cropTop, cropRight, cropBottom).sanitized()
