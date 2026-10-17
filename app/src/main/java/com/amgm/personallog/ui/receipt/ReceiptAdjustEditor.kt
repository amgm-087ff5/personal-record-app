package com.amgm.personallog.ui.receipt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.amgm.personallog.receipt.ReceiptAdjust
import com.amgm.personallog.receipt.ReceiptGeometry
import com.amgm.personallog.receipt.ReceiptImageCodec
import com.amgm.personallog.receipt.RotationParts
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.Pill
import com.amgm.personallog.ui.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** 編集用に読み込む長辺(px)。 */
private const val EDIT_MAX_SIDE = 1600

/** 四隅をつかめる距離(dp)。 */
private val CORNER_GRAB = 40.dp

/**
 * レシート画像1枚の編集部品: 画像+トリミング枠(上)、回転とスライダー(下)。
 * 補正値 [adjust] は呼び出し側が持ち、変更は [onChange] で受け取る。
 */
@Composable
fun ReceiptAdjustEditor(
    imagePath: String,
    adjust: ReceiptAdjust,
    onChange: (ReceiptAdjust) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ReceiptAdjustCanvas(imagePath, adjust, onChange, Modifier.weight(1f).fillMaxWidth())
        ReceiptAdjustControls(adjust, onChange)
    }
}

/** EXIF適用後の画像を読み込む(IOスレッド)。失敗・メモリ不足は null。 */
@Composable
private fun rememberEditBitmap(imagePath: String): ImageBitmap? {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) {
            try {
                ReceiptImageCodec.decodeScaled(File(context.filesDir, imagePath), EDIT_MAX_SIDE)?.asImageBitmap()
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }
    return bmp
}

/** 描画と指の操作に使う、表示中の画像矩形(回転後の外接矩形)の位置と大きさ。 */
private class Geo(
    val originX: Float,
    val originY: Float,
    val w: Float,
    val h: Float,
    val adjust: ReceiptAdjust,
    val onChange: (ReceiptAdjust) -> Unit,
)

/**
 * 画像を回転して外接矩形(余白は白)に描き、その上にトリミング枠を重ねる。
 * 枠は四隅をドラッグして動かす。角度が変わると枠は全体に戻る(ReceiptAdjust.withRotation)。
 */
@Composable
fun ReceiptAdjustCanvas(
    imagePath: String,
    adjust: ReceiptAdjust,
    onChange: (ReceiptAdjust) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val bitmap = rememberEditBitmap(imagePath)
    val grabPx = with(LocalDensity.current) { CORNER_GRAB.toPx() }
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }
    val handlePx = with(LocalDensity.current) { 16.dp.toPx() }

    BoxWithConstraints(modifier = modifier.background(c.line).padding(Space.s)) {
        val bmp = bitmap
        if (bmp == null) {
            Label("読み込み中…", modifier = Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val (rw, rh) = ReceiptGeometry.boundingSize(bmp.width, bmp.height, adjust.rotationDegrees)
        val f = min(cw / rw, ch / rh)
        val dispW = rw * f
        val dispH = rh * f
        val ox = (cw - dispW) / 2f
        val oy = (ch - dispH) / 2f
        val geo = rememberUpdatedState(Geo(ox, oy, dispW, dispH, adjust, onChange))

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val g0 = geo.value
                        val idx = nearestCorner(down.position, g0, grabPx)
                        // 四隅以外は何もしない(ページの横スワイプに譲る)
                        if (idx < 0) return@awaitEachGesture
                        down.consume()
                        val a0 = g0.adjust
                        var l = a0.cropLeft
                        var t = a0.cropTop
                        var r = a0.cropRight
                        var b = a0.cropBottom
                        drag(down.id) { change ->
                            val g = geo.value
                            val d = change.positionChange()
                            val dx = d.x / g.w
                            val dy = d.y / g.h
                            val mc = ReceiptGeometry.MIN_CROP
                            // 左上=0 右上=1 右下=2 左下=3。対辺を越えない・0〜1に収める
                            if (idx == 0 || idx == 3) l = (l + dx).coerceIn(0f, r - mc)
                            if (idx == 1 || idx == 2) r = (r + dx).coerceIn(l + mc, 1f)
                            if (idx == 0 || idx == 1) t = (t + dy).coerceIn(0f, b - mc)
                            if (idx == 2 || idx == 3) b = (b + dy).coerceIn(t + mc, 1f)
                            g.onChange(g.adjust.withCrop(l, t, r, b))
                            change.consume()
                        }
                    }
                },
        ) {
            val center = Offset(ox + dispW / 2f, oy + dispH / 2f)
            clipRect(ox, oy, ox + dispW, oy + dispH) {
                drawRect(Color.White, topLeft = Offset(ox, oy), size = Size(dispW, dispH))
                rotate(adjust.rotationDegrees, pivot = center) {
                    val iw = (bmp.width * f).roundToInt()
                    val ih = (bmp.height * f).roundToInt()
                    drawImage(
                        bmp,
                        dstOffset = IntOffset((center.x - iw / 2f).roundToInt(), (center.y - ih / 2f).roundToInt()),
                        dstSize = IntSize(iw, ih),
                    )
                }
            }
            val cl = ox + adjust.cropLeft * dispW
            val ct = oy + adjust.cropTop * dispH
            val cr = ox + adjust.cropRight * dispW
            val cb = oy + adjust.cropBottom * dispH
            val dim = Color.Black.copy(alpha = 0.45f)
            if (!adjust.isFullCrop) {
                drawRect(dim, Offset(ox, oy), Size(dispW, ct - oy))
                drawRect(dim, Offset(ox, cb), Size(dispW, oy + dispH - cb))
                drawRect(dim, Offset(ox, ct), Size(cl - ox, cb - ct))
                drawRect(dim, Offset(cr, ct), Size(ox + dispW - cr, cb - ct))
            }
            drawRect(c.accent, Offset(cl, ct), Size(cr - cl, cb - ct), style = Stroke(strokePx))
            for (p in listOf(Offset(cl, ct), Offset(cr, ct), Offset(cr, cb), Offset(cl, cb))) {
                drawRect(c.accent, Offset(p.x - handlePx / 2f, p.y - handlePx / 2f), Size(handlePx, handlePx))
                drawRect(Color.White, Offset(p.x - handlePx / 2f, p.y - handlePx / 2f), Size(handlePx, handlePx), style = Stroke(strokePx / 2f))
            }
        }
    }
}

/** 触れた位置に最も近い四隅(0=左上 1=右上 2=右下 3=左下)。遠ければ -1。 */
private fun nearestCorner(pos: Offset, g: Geo, grab: Float): Int {
    val a = g.adjust
    val xs = floatArrayOf(a.cropLeft, a.cropRight, a.cropRight, a.cropLeft)
    val ys = floatArrayOf(a.cropTop, a.cropTop, a.cropBottom, a.cropBottom)
    var best = -1
    var bestD = grab
    for (i in 0..3) {
        val d = hypot(pos.x - (g.originX + xs[i] * g.w), pos.y - (g.originY + ys[i] * g.h))
        if (d <= bestD) { best = i; bestD = d }
    }
    return best
}

/** 補正値を5つの数値の配列で保存する(画面回転・プロセス再生成でも編集中の値を残す)。 */
internal val ReceiptAdjustSaver: Saver<ReceiptAdjust, Any> = listSaver<ReceiptAdjust, Float>(
    save = { listOf(it.rotationDegrees, it.cropLeft, it.cropTop, it.cropRight, it.cropBottom) },
    restore = { ReceiptAdjust(it[0], it[1], it[2], it[3], it[4]).sanitized() },
)

/** 補正値が無い状態(null)も保存できる版。 */
internal val ReceiptAdjustOrNullSaver: Saver<ReceiptAdjust?, Any> = listSaver<ReceiptAdjust?, Float>(
    save = { a -> if (a == null) emptyList() else listOf(a.rotationDegrees, a.cropLeft, a.cropTop, a.cropRight, a.cropBottom) },
    restore = { if (it.size == 5) ReceiptAdjust(it[0], it[1], it[2], it[3], it[4]).sanitized() else null },
)

/**
 * 左90°・右90°・元に戻す、と微調整スライダー(-45〜+45度、1度単位)。
 * 90度の回数と微調整は別々の状態で持ち、合計の角度から毎回求め直さない(±45度でつまみが跳ばない)。
 * [stateKey] が変わると(別の画像など)状態を作り直す。
 */
@Composable
fun ReceiptAdjustControls(
    adjust: ReceiptAdjust,
    onChange: (ReceiptAdjust) -> Unit,
    modifier: Modifier = Modifier,
    stateKey: Any = Unit,
) {
    val c = AppTheme.colors
    var quarters by rememberSaveable(stateKey) { mutableStateOf(RotationParts.of(adjust.rotationDegrees).quarters) }
    var finePart by rememberSaveable(stateKey) { mutableStateOf(RotationParts.of(adjust.rotationDegrees).fine) }
    // 外から合計の角度が変わったとき(元に戻す等)だけ分け直す
    val parts = RotationParts(quarters, finePart).reconciled(adjust.rotationDegrees)
    val fine = parts.fine
    fun update(next: RotationParts) {
        quarters = next.quarters
        finePart = next.fine
        onChange(adjust.withRotation(next.degrees))
    }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Space.edge, vertical = Space.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
            Pill("左90°", selected = false, onClick = { update(parts.turned(-1)) })
            Pill("右90°", selected = false, onClick = { update(parts.turned(1)) })
            Pill("元に戻す", selected = false, onClick = { onChange(ReceiptAdjust.NONE) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("微調整 ${if (fine > 0) "+" else ""}${fine}°", color = c.inkSoft, modifier = Modifier.padding(end = Space.s))
            Slider(
                value = fine.toFloat(),
                onValueChange = { v ->
                    val n = v.roundToInt().coerceIn(-45, 45)
                    if (n != fine) update(parts.withFine(n))
                },
                valueRange = -45f..45f,
                steps = 89,
                colors = SliderDefaults.colors(
                    thumbColor = c.accent,
                    activeTrackColor = c.accent,
                    inactiveTrackColor = c.line,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
