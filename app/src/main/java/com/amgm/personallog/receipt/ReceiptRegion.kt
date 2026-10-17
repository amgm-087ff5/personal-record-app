package com.amgm.personallog.receipt

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 回転・トリミングした結果に必要な範囲だけを、元画像から読むための計算(Android に依存しない純Kotlin)。
 * 座標は3つの枠で考える:
 * - 原本: ファイルの画素(EXIF の向きを適用する前)
 * - EXIF 後: 向きを適用した画像
 * - 外接矩形: EXIF 後の画像を回転して、すべてを含む矩形に置いたもの(トリミング枠はこの上の比率)
 */
object ReceiptRegion {
    /** フィルタ用に、必要な範囲の外へ余分に読む画素数(原本の解像度で)。 */
    const val MARGIN = 2

    /** 1回に読む範囲の画素数の上限(RGB_565 で約16MB)。超えるときは inSampleSize を上げる。 */
    const val MAX_REGION_PIXELS = 8_000_000L

    /** EXIF の向きが縦横を入れ替えるか(5〜8)。 */
    fun swapsAxes(orientation: Int): Boolean = orientation in 5..8

    /**
     * 原本の座標 (x, y) から EXIF 後の座標への変換 x' = a x + b y + c, y' = d x + e y + f。
     * [a, b, c, d, e, f] を返す。w, h は原本の幅・高さ。向きは ExifInterface の値(1〜8。他は 1 と同じ)。
     */
    fun exifAffine(orientation: Int, w: Double, h: Double): DoubleArray = when (orientation) {
        2 -> doubleArrayOf(-1.0, 0.0, w, 0.0, 1.0, 0.0)
        3 -> doubleArrayOf(-1.0, 0.0, w, 0.0, -1.0, h)
        4 -> doubleArrayOf(1.0, 0.0, 0.0, 0.0, -1.0, h)
        5 -> doubleArrayOf(0.0, 1.0, 0.0, 1.0, 0.0, 0.0)
        6 -> doubleArrayOf(0.0, -1.0, h, 1.0, 0.0, 0.0)
        7 -> doubleArrayOf(0.0, -1.0, h, -1.0, 0.0, w)
        8 -> doubleArrayOf(0.0, 1.0, 0.0, -1.0, 0.0, w)
        else -> doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    }

    /** 読む範囲の計画。 */
    class Plan(
        /** 原本の読む範囲 [left, top, right, bottom](右・下は含まない)。枠が画像に全くかからないときは null(全面が余白)。 */
        val region: IntArray?,
        /** BitmapRegionDecoder に渡す inSampleSize(2の累乗)。 */
        val sampleSize: Int,
        /** EXIF 後の画像の大きさ(原本の解像度) */
        val exifWidth: Int,
        val exifHeight: Int,
        /** 外接矩形の大きさ(原本の解像度) */
        val boundWidth: Int,
        val boundHeight: Int,
        /** トリミング枠(外接矩形上の画素 [left, top, right, bottom]) */
        val crop: IntArray,
        /** 出力の大きさ(長辺 maxSide 以下) */
        val outWidth: Int,
        val outHeight: Int,
    ) {
        val cropWidth: Int get() = crop[2] - crop[0]
        val cropHeight: Int get() = crop[3] - crop[1]
    }

    /**
     * 原本 rawW×rawH、EXIF の向き、補正から、読む範囲と出力の大きさを決める。
     * 範囲は、枠の四隅を回転と EXIF の逆順にたどって原本の座標にした外接矩形(余白付き)。
     * inSampleSize は、出力の解像度を下回らない範囲で最も大きい2の累乗。
     */
    fun plan(
        rawW: Int,
        rawH: Int,
        orientation: Int,
        adjust: ReceiptAdjust,
        maxSide: Int,
        maxRegionPixels: Long = MAX_REGION_PIXELS,
    ): Plan? {
        if (rawW <= 0 || rawH <= 0 || maxSide <= 0) return null
        val a = adjust.sanitized()
        val swap = swapsAxes(orientation)
        val ew = if (swap) rawH else rawW
        val eh = if (swap) rawW else rawH
        val (bw, bh) = ReceiptGeometry.boundingSize(ew, eh, a.rotationDegrees)
        val crop = ReceiptGeometry.cropToPixels(bw, bh, a.cropLeft, a.cropTop, a.cropRight, a.cropBottom)
        val cw = crop[2] - crop[0]
        val ch = crop[3] - crop[1]
        val scale = min(1.0, maxSide.toDouble() / max(cw, ch))
        val outW = max(1, (cw * scale).roundToInt())
        val outH = max(1, (ch * scale).roundToInt())

        // 枠の四隅 -> EXIF 後の座標(回転の逆)-> 外接矩形
        val rad = Math.toRadians(a.rotationDegrees.toDouble())
        val cs = cos(rad)
        val sn = sin(rad)
        val cxB = bw / 2.0
        val cyB = bh / 2.0
        val cxE = ew / 2.0
        val cyE = eh / 2.0
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (i in 0..3) {
            val px = (if (i == 0 || i == 3) crop[0] else crop[2]) - cxB
            val py = (if (i < 2) crop[1] else crop[3]) - cyB
            val ex = cs * px + sn * py + cxE
            val ey = -sn * px + cs * py + cyE
            minX = min(minX, ex); maxX = max(maxX, ex)
            minY = min(minY, ey); maxY = max(maxY, ey)
        }
        val el = max(0, floor(minX).toInt() - MARGIN)
        val et = max(0, floor(minY).toInt() - MARGIN)
        val er = min(ew, ceil(maxX).toInt() + MARGIN)
        val eb = min(eh, ceil(maxY).toInt() + MARGIN)

        var region: IntArray? = null
        var sample = 1
        if (er > el && eb > et) {
            // EXIF 後 -> 原本。向きの変換は軸に沿った等長変換なので、対角の2点を戻せば矩形になる
            val m = exifAffine(orientation, rawW.toDouble(), rawH.toDouble())
            fun back(x: Double, y: Double): DoubleArray {
                val dx = x - m[2]
                val dy = y - m[5]
                return doubleArrayOf(m[0] * dx + m[3] * dy, m[1] * dx + m[4] * dy)
            }
            val p = back(el.toDouble(), et.toDouble())
            val q = back(er.toDouble(), eb.toDouble())
            val l = min(p[0], q[0]).roundToInt().coerceIn(0, rawW)
            val t = min(p[1], q[1]).roundToInt().coerceIn(0, rawH)
            val r = max(p[0], q[0]).roundToInt().coerceIn(0, rawW)
            val b = max(p[1], q[1]).roundToInt().coerceIn(0, rawH)
            if (r > l && b > t) {
                region = intArrayOf(l, t, r, b)
                // 出力より粗くならない最大の2の累乗(1/sample >= scale)
                while (sample * 2 * scale <= 1.0 + 1e-9) sample *= 2
                // 読む範囲が大きすぎるときはさらに粗くする
                val rw = (r - l).toLong()
                val rh = (b - t).toLong()
                while ((rw / sample + 1) * (rh / sample + 1) > maxRegionPixels) sample *= 2
            }
        }
        return Plan(region, sample, ew, eh, bw, bh, crop, outW, outH)
    }

    /** 原本の点 (x, y) を、外接矩形上の座標にする(テスト・検算用)。 */
    fun rawToBound(orientation: Int, rawW: Int, rawH: Int, adjust: ReceiptAdjust, x: Double, y: Double): DoubleArray {
        val m = exifAffine(orientation, rawW.toDouble(), rawH.toDouble())
        val ex = m[0] * x + m[1] * y + m[2]
        val ey = m[3] * x + m[4] * y + m[5]
        val swap = swapsAxes(orientation)
        val ew = if (swap) rawH else rawW
        val eh = if (swap) rawW else rawH
        val a = adjust.sanitized()
        val (bw, bh) = ReceiptGeometry.boundingSize(ew, eh, a.rotationDegrees)
        val rad = Math.toRadians(a.rotationDegrees.toDouble())
        val dx = ex - ew / 2.0
        val dy = ey - eh / 2.0
        return doubleArrayOf(
            cos(rad) * dx - sin(rad) * dy + bw / 2.0,
            sin(rad) * dx + cos(rad) * dy + bh / 2.0,
        )
    }
}

/**
 * 角度を「90度の回数」と「微調整(-45〜+45)」の2つの状態で持つ。
 * 合計の角度から毎回求め直さない(境界の ±45 で、つまみが端へ跳ばないようにするため)。
 * 外から合計の角度が変わったとき(元に戻す・別の画像)だけ [reconciled] で分け直す。
 */
data class RotationParts(val quarters: Int = 0, val fine: Int = 0) {
    /** 合計の角度(0 以上 360 未満)。 */
    val degrees: Float get() = ReceiptGeometry.combineDegrees(quarters, fine)

    /** 90度単位で回す(正が時計回り)。微調整はそのまま。 */
    fun turned(deltaQuarters: Int): RotationParts = copy(quarters = Math.floorMod(quarters + deltaQuarters, 4))

    fun withFine(value: Int): RotationParts = copy(fine = value.coerceIn(-45, 45))

    /** 合計の角度が今の状態と同じなら今の状態を保ち、違うときだけ分け直す。 */
    fun reconciled(totalDegrees: Float): RotationParts =
        if (ReceiptGeometry.sameAngle(degrees, totalDegrees)) this else of(totalDegrees)

    companion object {
        fun of(totalDegrees: Float): RotationParts {
            val (q, f) = ReceiptGeometry.splitDegrees(totalDegrees)
            return RotationParts(q, f)
        }
    }
}

/**
 * 保存したままのレシート画像のうち、どのレシートからも参照されていない古いものを選ぶ(取り込み途中で終了した残骸の掃除)。
 * 純Kotlinの選別。ファイルの削除は呼び出し側。
 */
object ReceiptOrphans {
    /** 作られてから経過させる時間(ミリ秒)。 */
    const val MIN_AGE_MS = 60L * 60L * 1000L

    class Candidate(val relativePath: String, val lastModified: Long)

    /**
     * 消してよい相対パスを返す。参照されている・取り込み中・1時間未満のものは残す。
     * パスは区切りを / にそろえて比べる。
     */
    fun select(
        files: List<Candidate>,
        referenced: Collection<String>,
        inUse: Collection<String>,
        now: Long,
        minAgeMs: Long = MIN_AGE_MS,
    ): List<String> {
        val keep = HashSet<String>()
        referenced.forEach { keep.add(norm(it)) }
        inUse.forEach { keep.add(norm(it)) }
        return files.filter { f ->
            norm(f.relativePath) !in keep && f.lastModified > 0L && now - f.lastModified >= minAgeMs
        }.map { it.relativePath }
    }

    private fun norm(p: String): String = p.replace('\\', '/').trimStart('/')
}
