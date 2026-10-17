package com.amgm.personallog.receipt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * 合成画像(左上=赤 R、右上=緑 G、左下=黄 Y、右下=青 B)に EXIF の向きと補正をかけ、
 * decodeAdjusted の結果の大きさと四隅の色で向きが正しいことを確かめる。
 * 色は '角の並び(左上・右上・左下・右下)' の4文字で表す。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class ReceiptImageRobolectricTest {
    private val rawW = 128
    private val rawH = 96
    private lateinit var dir: File

    /** EXIF の向き -> EXIF 適用後の四隅(左上・右上・左下・右下)。 */
    private val expectedByOrientation = mapOf(
        1 to "RGYB",
        2 to "GRBY",
        3 to "BYGR",
        4 to "YBRG",
        5 to "RYGB",
        6 to "YRBG",
        7 to "BGYR",
        8 to "GBRY",
    )

    @Before
    fun setUp() {
        dir = File(RuntimeEnvironment.getApplication().cacheDir, "receipt-img-test")
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun makeJpeg(orientation: Int): File {
        val bmp = Bitmap.createBitmap(rawW, rawH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        fun fill(color: Int, l: Int, t: Int, r: Int, b: Int) {
            p.color = color
            c.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), p)
        }
        val hw = rawW / 2
        val hh = rawH / 2
        fill(Color.rgb(255, 0, 0), 0, 0, hw, hh)
        fill(Color.rgb(0, 255, 0), hw, 0, rawW, hh)
        fill(Color.rgb(255, 255, 0), 0, hh, hw, rawH)
        fill(Color.rgb(0, 0, 255), hw, hh, rawW, rawH)
        val f = File(dir, "o$orientation.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bmp.recycle()
        val exif = ExifInterface(f.path)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        exif.saveAttributes()
        return f
    }

    private fun classify(p: Int): Char {
        val palette = mapOf(
            'R' to intArrayOf(255, 0, 0),
            'G' to intArrayOf(0, 255, 0),
            'Y' to intArrayOf(255, 255, 0),
            'B' to intArrayOf(0, 0, 255),
            'W' to intArrayOf(255, 255, 255),
        )
        val r = Color.red(p)
        val g = Color.green(p)
        val b = Color.blue(p)
        return palette.minByOrNull { (_, v) -> (v[0] - r) * (v[0] - r) + (v[1] - g) * (v[1] - g) + (v[2] - b) * (v[2] - b) }!!.key
    }

    /** 四隅の「色の領域」: 各象限の中心付近の色。 */
    private fun corners(bmp: Bitmap): String {
        val xs = intArrayOf(bmp.width / 4, bmp.width * 3 / 4)
        val ys = intArrayOf(bmp.height / 4, bmp.height * 3 / 4)
        val sb = StringBuilder()
        for (y in ys) for (x in xs) sb.append(classify(bmp.getPixel(x, y)))
        return sb.toString()
    }

    /** 失敗時の手がかり: 画像を粗い格子(最大 16x12)の色の文字にして出す。 */
    private fun dump(bmp: Bitmap): String {
        val gw = minOf(16, bmp.width)
        val gh = minOf(12, bmp.height)
        val sb = StringBuilder("\n${bmp.width}x${bmp.height}\n")
        for (j in 0 until gh) {
            for (i in 0 until gw) sb.append(classify(bmp.getPixel((i + 0.5f).times(bmp.width) .div(gw).toInt(), (j + 0.5f).times(bmp.height).div(gh).toInt())))
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun assertCorners(msg: String, expected: String, bmp: Bitmap) {
        assertEquals(msg + dump(bmp), expected, corners(bmp))
    }

    /** 時計回りに90度回した四隅。 */
    private fun rot90(s: String): String = "${s[2]}${s[0]}${s[3]}${s[1]}"

    private fun rot(s: String, times: Int): String {
        var r = s
        repeat(times) { r = rot90(r) }
        return r
    }

    private fun exifSize(o: Int): Pair<Int, Int> = if (o in 5..8) rawH to rawW else rawW to rawH

    @Test
    fun exifOrientationAloneIsAppliedByDecodeScaled() {
        for (o in 1..8) {
            val bmp = ReceiptImageCodec.decodeScaled(makeJpeg(o), 4096)
            assertNotNull("o=$o", bmp)
            val (w, h) = exifSize(o)
            assertEquals("o=$o w", w, bmp!!.width)
            assertEquals("o=$o h", h, bmp.height)
            assertEquals("o=$o corners", expectedByOrientation[o], corners(bmp))
            bmp.recycle()
        }
    }

    @Test
    fun rotationIsBasedOnExifAppliedImage() {
        for (o in 1..8) {
            val f = makeJpeg(o)
            for (times in 1..3) {
                val bmp = ReceiptImageCodec.decodeAdjusted(f, ReceiptAdjust(rotationDegrees = 90f * times), 4096)
                assertNotNull("o=$o t=$times", bmp)
                val (ew, eh) = exifSize(o)
                val (w, h) = if (times % 2 == 1) eh to ew else ew to eh
                assertEquals("o=$o t=$times w", w, bmp!!.width)
                assertEquals("o=$o t=$times h", h, bmp.height)
                assertCorners("o=$o t=$times corners", rot(expectedByOrientation[o]!!, times), bmp)
                bmp.recycle()
            }
        }
    }

    @Test
    fun cropIsAppliedAfterExifAndRotation() {
        for (o in 1..8) {
            val f = makeJpeg(o)
            val e = expectedByOrientation[o]!!
            val (ew, eh) = exifSize(o)
            // 右半分(回転なし): 右の色が左右に並ぶ
            val right = ReceiptImageCodec.decodeAdjusted(f, ReceiptAdjust(0f, 0.5f, 0f, 1f, 1f), 4096)!!
            assertEquals("o=$o right w", ew / 2, right.width)
            assertEquals("o=$o right h", eh, right.height)
            assertCorners("o=$o right", "${e[1]}${e[1]}${e[3]}${e[3]}", right)
            right.recycle()
            // 上半分
            val top = ReceiptImageCodec.decodeAdjusted(f, ReceiptAdjust(0f, 0f, 0f, 1f, 0.5f), 4096)!!
            assertEquals("o=$o top w", ew, top.width)
            assertEquals("o=$o top h", eh / 2, top.height)
            assertCorners("o=$o top", "${e[0]}${e[1]}${e[0]}${e[1]}", top)
            top.recycle()
            // 右90度回転してから左半分: 回転後の左列(元の下の行)が残る
            val rotated = rot90(e)
            val rl = ReceiptImageCodec.decodeAdjusted(f, ReceiptAdjust(90f, 0f, 0f, 0.5f, 1f), 4096)!!
            assertEquals("o=$o rl w", eh / 2, rl.width)
            assertEquals("o=$o rl h", ew, rl.height)
            assertCorners("o=$o rl", "${rotated[0]}${rotated[0]}${rotated[2]}${rotated[2]}", rl)
            rl.recycle()
        }
    }

    @Test
    fun smallTiltKeepsOrientationAndAddsWhiteMargins() {
        for (o in intArrayOf(1, 6, 3, 8)) {
            val f = makeJpeg(o)
            val bmp = ReceiptImageCodec.decodeAdjusted(f, ReceiptAdjust(rotationDegrees = 10f), 4096)!!
            val (ew, eh) = exifSize(o)
            val (bw, bh) = ReceiptGeometry.boundingSize(ew, eh, 10f)
            assertEquals("o=$o w", bw, bmp.width)
            assertEquals("o=$o h", bh, bmp.height)
            assertCorners("o=$o corners", expectedByOrientation[o]!!, bmp)
            // 外接矩形の四隅の画素は、画像の外なので白
            assertEquals('W', classify(bmp.getPixel(1, 1)))
            assertEquals('W', classify(bmp.getPixel(bmp.width - 2, bmp.height - 2)))
            bmp.recycle()
        }
    }

    @Test
    fun outputIsReducedToMaxSide() {
        val f = makeJpeg(6)
        val bmp = ReceiptImageCodec.decodeAdjusted(f, ReceiptAdjust(rotationDegrees = 90f), 64)!!
        assertEquals(64, maxOf(bmp.width, bmp.height))
        // 縮小しても向きは同じ
        assertCorners("maxSide64", rot90(expectedByOrientation[6]!!), bmp)
        bmp.recycle()
    }

    /** 範囲読み(BitmapRegionDecoder)が、全体を読んでから回転・トリミングした結果と許容誤差内で一致する。 */
    @Test
    fun regionReadMatchesWholeRead() {
        val adjusts = listOf(
            ReceiptAdjust(rotationDegrees = 10f),
            ReceiptAdjust(0f, 0.2f, 0.1f, 0.7f, 0.9f),
            ReceiptAdjust(90f, 0.1f, 0.2f, 0.9f, 0.8f),
            ReceiptAdjust(37f, 0.15f, 0.1f, 0.85f, 0.9f),
            ReceiptAdjust(312f),
        )
        for (maxSide in intArrayOf(4096, 64)) for (o in intArrayOf(1, 3, 5, 6, 8)) {
            val f = makeJpeg(o)
            for (a in adjusts) {
                val region = ReceiptImageCodec.decodeAdjustedRegion(f, a.sanitized(), maxSide)
                assertNotNull("範囲読みが使えない o=$o a=$a", region)
                val whole = ReceiptImageCodec.decodeAdjustedWhole(f, a.sanitized(), maxSide)!!
                val msg = "maxSide=$maxSide o=$o a=$a"
                // 画素への丸めで数画素の差は出る
                assertTrue("$msg size ${region!!.width}x${region.height} vs ${whole.width}x${whole.height}",
                    abs(region.width - whole.width) <= 3 && abs(region.height - whole.height) <= 3)
                var same = 0
                var total = 0
                for (iy in 1..18) for (ix in 1..18) {
                    val u = ix / 19f
                    val v = iy / 19f
                    val pr = region.getPixel((u * region.width).toInt(), (v * region.height).toInt())
                    val pw = whole.getPixel((u * whole.width).toInt(), (v * whole.height).toInt())
                    total++
                    if (classify(pr) == classify(pw)) same++
                }
                // 色の境目では縮小・回転の丸めで食い違うので、9割以上の一致を求める
                assertTrue("$msg 一致 $same / $total 範囲読み:${dump(region)} 全体読み:${dump(whole)}", same >= total * 0.9)
                region.recycle()
                whole.recycle()
            }
        }
    }

    @Test
    fun diagRegionDecoder() {
        val f = makeJpeg(1)
        @Suppress("DEPRECATION")
        val d = android.graphics.BitmapRegionDecoder.newInstance(f.path, false)!!
        val full = d.decodeRegion(android.graphics.Rect(0, 0, rawW, rawH), null)
        val part = d.decodeRegion(android.graphics.Rect(64, 0, 128, 96), android.graphics.BitmapFactory.Options().apply { inSampleSize = 1 })
        val info = "dec=${d.width}x${d.height} full=${full?.width}x${full?.height}${full?.let { dump(it) }} part=${part?.width}x${part?.height}${part?.let { dump(it) }} px=${part?.getPixel(5, 5)?.toString(16)}"
        assertEquals(info, "x", "y")
    }
}
