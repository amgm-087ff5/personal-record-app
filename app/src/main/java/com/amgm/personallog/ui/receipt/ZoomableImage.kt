package com.amgm.personallog.ui.receipt

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amgm.personallog.receipt.ReceiptImageCodec
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.common.ZoomMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** ビューア用に読み込む長辺の上限(px)。OutOfMemoryError のときは下の値で再試行する。 */
private val VIEWER_MAX_SIDES = listOf(2048, 1024)

private sealed interface ViewerLoad {
    data object Loading : ViewerLoad
    class Ready(val bitmap: ImageBitmap) : ViewerLoad
    data object Failed : ViewerLoad
}

/**
 * レシート原本を全画面で表示するビューア(Dialog)。
 * 戻るボタン・「閉じる」で閉じる。拡大・移動は [ZoomableImage] 参照。
 */
@Composable
fun ReceiptImageViewer(relativePath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val load by produceState<ViewerLoad>(initialValue = ViewerLoad.Loading, relativePath) {
        value = loadViewerImage(File(context.filesDir, relativePath))
            ?.let { ViewerLoad.Ready(it) }
            ?: ViewerLoad.Failed
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            when (val s = load) {
                ViewerLoad.Loading -> Label(
                    "読み込み中…",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                ViewerLoad.Failed -> Text(
                    "画像を表示できません",
                    style = AppTheme.type.body,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                is ViewerLoad.Ready -> ZoomableImage(
                    bitmap = s.bitmap,
                    contentDescription = "レシート画像(拡大表示)",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            TextAction(
                "閉じる",
                onClick = onDismiss,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Space.s),
            )
            Text(
                "二本指で拡大・縮小 / ダブルタップで拡大",
                color = Color.White,
                style = AppTheme.type.caption,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.m),
            )
        }
    }
}

/**
 * 画像を容器に収めて表示し、二本指ピンチ・一本指ドラッグ・ダブルタップで拡大・移動する。
 * 拡大率は 1〜5倍(ZoomMath)。移動量は画像の端が容器の内側に入らない範囲に制限する。
 */
@Composable
fun ZoomableImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var transform by remember { mutableStateOf(ZoomMath.Transform()) }
    var container by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { container = it }
            .pointerInput(bitmap) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        val cw = container.width.toFloat()
                        val ch = container.height.toFloat()
                        if (cw > 0f && ch > 0f) {
                            val (fw, fh) = ZoomMath.fitSize(
                                bitmap.width.toFloat(), bitmap.height.toFloat(), cw, ch,
                            )
                            transform = ZoomMath.doubleTap(
                                transform, pos.x - cw / 2f, pos.y - ch / 2f, fw, fh, cw, ch,
                            )
                        }
                    },
                )
            }
            .pointerInput(bitmap) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val cw = container.width.toFloat()
                    val ch = container.height.toFloat()
                    if (cw > 0f && ch > 0f) {
                        val (fw, fh) = ZoomMath.fitSize(
                            bitmap.width.toFloat(), bitmap.height.toFloat(), cw, ch,
                        )
                        transform = ZoomMath.applyGesture(
                            current = transform,
                            zoom = zoom,
                            panX = pan.x,
                            panY = pan.y,
                            centroidX = centroid.x - cw / 2f,
                            centroidY = centroid.y - ch / 2f,
                            fitW = fw,
                            fitH = fh,
                            containerW = cw,
                            containerH = ch,
                        )
                    }
                }
            },
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.offsetX
                    translationY = transform.offsetY
                },
        )
    }
}

/**
 * 原本を読み込む(IOスレッド)。長辺 2048px を上限に、EXIFの向きを補正して読む。
 * メモリ不足(OutOfMemoryError)なら低い解像度で再試行する。失敗時 null。
 */
private suspend fun loadViewerImage(file: File): ImageBitmap? = withContext(Dispatchers.IO) {
    for (side in VIEWER_MAX_SIDES) {
        try {
            val bmp: Bitmap = ReceiptImageCodec.decodeScaled(file, side) ?: return@withContext null
            return@withContext bmp.asImageBitmap()
        } catch (e: OutOfMemoryError) {
            // 次の低い解像度で再試行する
        } catch (e: Exception) {
            return@withContext null
        }
    }
    null
}
