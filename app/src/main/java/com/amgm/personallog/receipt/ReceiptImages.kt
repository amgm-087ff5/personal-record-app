package com.amgm.personallog.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** レシート原本画像の保存場所(filesDir/receipts)。DBには filesDir からの相対パスを入れる。 */
@Singleton
class ReceiptImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.filesDir, DIR).also { it.mkdirs() }

    fun file(relativePath: String): File = File(context.filesDir, relativePath)

    /** 新しい保存先の相対パスを決める(ファイルはまだ作らない)。 */
    fun newRelativePath(): String {
        dir
        return "$DIR/${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
    }

    /** カメラ撮影の出力先 Uri(FileProvider)。 */
    fun uriFor(relativePath: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file(relativePath))

    /** 選択した画像を原本として保存。JPEGならバイト列そのまま、他形式はJPEG(品質95)に変換。相対パスを返す。 */
    suspend fun importFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        val rel = newRelativePath()
        val target = file(rel)
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw java.io.IOException("画像を開けませんでした")
            val isJpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
            if (isJpeg) {
                target.writeBytes(bytes)
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > ReceiptImageCodec.MAX_SIDE * 2) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    ?: throw java.io.IOException("この画像形式は読み込めませんでした")
                target.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                bmp.recycle()
            }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        rel
    }

    /** filesDir/receipts にある画像ファイルの一覧(相対パスと更新時刻)。掃除の候補選びに使う。 */
    fun listStored(): List<ReceiptOrphans.Candidate> =
        File(context.filesDir, DIR).listFiles()?.filter { it.isFile }
            ?.map { ReceiptOrphans.Candidate("$DIR/${it.name}", it.lastModified()) }
            .orEmpty()

    fun delete(relativePath: String) {
        if (relativePath.isBlank()) return
        runCatching { file(relativePath).delete() }
    }

    companion object {
        const val DIR = "receipts"
    }
}

object ReceiptImageCodec {
    const val RESIZE_THRESHOLD_BYTES = 3_500_000L
    const val MAX_SIDE = 2048
    const val JPEG_QUALITY = 92

    /**
     * 送信用バイト列。次の3つをすべて満たすときだけ原本のバイト列をそのまま送る:
     * EXIFの向きが通常 / 補正なし / 3.5MB以下。
     * それ以外は「EXIF → 回転 → トリミング → 長辺 MAX_SIDE 以下」の順に変換してJPEG(品質92)にする(メモリ上のみ)。
     */
    fun prepareForUpload(file: File, adjust: ReceiptAdjust = ReceiptAdjust.NONE): ByteArray {
        if (ReceiptGeometry.shouldSendOriginal(isExifNormal(file), adjust, file.length(), RESIZE_THRESHOLD_BYTES)) {
            return file.readBytes()
        }
        val bmp = decodeAdjusted(file, adjust, MAX_SIDE) ?: return file.readBytes()
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bmp.recycle()
        return out.toByteArray()
    }

    /** EXIF の向きが通常(または記録なし)か。読めないときも通常として扱う。 */
    fun isExifNormal(file: File): Boolean {
        val o = exifOrientation(file)
        return o == ExifInterface.ORIENTATION_NORMAL || o == ExifInterface.ORIENTATION_UNDEFINED
    }

    internal fun exifOrientation(file: File): Int = try {
        ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /**
     * EXIFの向きを反映したうえで、回転・トリミングを適用したビットマップ(長辺 maxSide 以下)。
     * 回転の基準は EXIF 適用後の画像で、二重にはかけない。90度単位以外の角度は外接矩形に描き、余白は白。
     * 補正なしなら [decodeScaled] と同じ。失敗時 null。
     * 補正ありのときは、元画像のうち枠に当たる範囲だけを BitmapRegionDecoder で必要な縮小率で読む(全体は読まない)。
     */
    fun decodeAdjusted(file: File, adjust: ReceiptAdjust, maxSide: Int): Bitmap? {
        val a = adjust.sanitized()
        if (a.isNone) return decodeScaled(file, maxSide)
        return decodeAdjustedRegion(file, a, maxSide) ?: decodeAdjustedWhole(file, a, maxSide)
    }

    /**
     * 範囲読み版。範囲の計算は [ReceiptRegion.plan]。デコーダを作れないときなどは null。
     * 中間のビットマップは使い終わったら recycle する。メモリ不足は呼び出し側へ投げる。
     */
    @Suppress("DEPRECATION")
    fun decodeAdjustedRegion(file: File, adjust: ReceiptAdjust, maxSide: Int): Bitmap? {
        val decoder = try {
            BitmapRegionDecoder.newInstance(file.path, false)
        } catch (e: Exception) {
            null
        } ?: return null
        try {
            val orientation = exifOrientation(file)
            val plan = ReceiptRegion.plan(decoder.width, decoder.height, orientation, adjust, maxSide)
                ?: return null
            val out = Bitmap.createBitmap(plan.outWidth, plan.outHeight, Bitmap.Config.ARGB_8888)
            var ok = false
            try {
                val canvas = Canvas(out)
                canvas.drawColor(Color.WHITE)
                val reg = plan.region
                if (reg != null) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize }
                    val part = decoder.decodeRegion(Rect(reg[0], reg[1], reg[2], reg[3]), opts) ?: return null
                    try {
                        // 読んだ画素 -> 原本 -> EXIF 後 -> 外接矩形(回転) -> 枠の左上を原点 -> 出力の大きさ
                        val m = Matrix()
                        m.postScale((reg[2] - reg[0]).toFloat() / part.width, (reg[3] - reg[1]).toFloat() / part.height)
                        m.postTranslate(reg[0].toFloat(), reg[1].toFloat())
                        val e = ReceiptRegion.exifAffine(orientation, decoder.width.toDouble(), decoder.height.toDouble())
                        val ex = Matrix()
                        ex.setValues(
                            floatArrayOf(
                                e[0].toFloat(), e[1].toFloat(), e[2].toFloat(),
                                e[3].toFloat(), e[4].toFloat(), e[5].toFloat(),
                                0f, 0f, 1f,
                            ),
                        )
                        m.postConcat(ex)
                        m.postTranslate(-plan.exifWidth / 2f, -plan.exifHeight / 2f)
                        m.postRotate(adjust.rotationDegrees)
                        m.postTranslate(plan.boundWidth / 2f, plan.boundHeight / 2f)
                        m.postTranslate(-plan.crop[0].toFloat(), -plan.crop[1].toFloat())
                        m.postScale(plan.outWidth.toFloat() / plan.cropWidth, plan.outHeight.toFloat() / plan.cropHeight)
                        canvas.drawBitmap(part, m, Paint(Paint.FILTER_BITMAP_FLAG))
                    } finally {
                        part.recycle()
                    }
                }
                ok = true
                return out
            } finally {
                if (!ok) out.recycle()
            }
        } finally {
            decoder.recycle()
        }
    }

    /** 範囲読みが使えないときの代わり: 長辺を抑えて全体を読み、回転・トリミングする。 */
    internal fun decodeAdjustedWhole(file: File, adjust: ReceiptAdjust, maxSide: Int): Bitmap? {
        val a = adjust
        val srcSide = if (a.isFullCrop) maxSide else minOf(maxSide * 2, 3072)
        val src = decodeScaled(file, srcSide) ?: return null
        try {
            val (bw, bh) = ReceiptGeometry.boundingSize(src.width, src.height, a.rotationDegrees)
            val r = ReceiptGeometry.cropToPixels(bw, bh, a.cropLeft, a.cropTop, a.cropRight, a.cropBottom)
            val cw = r[2] - r[0]
            val ch = r[3] - r[1]
            val scale = minOf(1f, maxSide.toFloat() / maxOf(cw, ch))
            val outW = maxOf(1, Math.round(cw * scale))
            val outH = maxOf(1, Math.round(ch * scale))
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.WHITE)
            val m = Matrix()
            m.postTranslate(-src.width / 2f, -src.height / 2f)
            m.postRotate(a.rotationDegrees)
            m.postTranslate(bw / 2f, bh / 2f)
            m.postTranslate(-r[0].toFloat(), -r[1].toFloat())
            m.postScale(outW.toFloat() / cw, outH.toFloat() / ch)
            canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        } finally {
            src.recycle()
        }
    }

    /** EXIFの向きを反映し、長辺が maxSide を超えないように縮小したビットマップ。 */
    fun decodeScaled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        val longSide = maxOf(w, h)
        var sample = 1
        while (longSide / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null

        val matrix = Matrix()
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        when (exifOrientation(file)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        if (!matrix.isIdentity) {
            val t = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (t !== bmp) bmp.recycle()
            bmp = t
        }
        return bmp
    }
}
