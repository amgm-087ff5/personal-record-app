package com.amgm.personallog.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** シートの止まる位置。 */
internal enum class SheetSnap { Expanded, Minimized, Hidden }

/** シートの止まる位置の計算(純Kotlin)。offset は「開いた位置からどれだけ下に下がっているか」(px)。 */
internal object SheetSnapLogic {
    /** 下スワイプとみなす速さ(px/秒)。これ以上の速さで離したら、1段下(上向きなら1段上)へ動かす。 */
    const val FLING_VELOCITY = 1800f

    /** 速さが足りないときに、次の段へ進める距離の割合(段の間の何割を超えたら進むか)。小さな動きでは動かさない。 */
    const val DISTANCE_RATIO = 0.35f

    fun offsetOf(snap: SheetSnap, sheetHeight: Float, peekHeight: Float): Float = when (snap) {
        SheetSnap.Expanded -> 0f
        SheetSnap.Minimized -> (sheetHeight - peekHeight).coerceAtLeast(0f)
        SheetSnap.Hidden -> sheetHeight
    }

    /**
     * 指を離した位置と速さから止まる位置を決める。
     * @param from 動かし始めた時の位置
     * @param minimizable true なら下スワイプはまず最小化に止める(入力を保持)。false なら閉じる
     */
    fun settle(
        from: SheetSnap,
        offset: Float,
        velocity: Float,
        sheetHeight: Float,
        peekHeight: Float,
        minimizable: Boolean,
    ): SheetSnap {
        val order = if (minimizable) listOf(SheetSnap.Expanded, SheetSnap.Minimized, SheetSnap.Hidden) else listOf(SheetSnap.Expanded, SheetSnap.Hidden)
        val start = order.indexOf(from).coerceAtLeast(0)
        val startOffset = offsetOf(order[start], sheetHeight, peekHeight)
        val down = order.getOrNull(start + 1)
        val up = order.getOrNull(start - 1)
        return when {
            velocity >= FLING_VELOCITY && down != null -> down
            velocity <= -FLING_VELOCITY && up != null -> up
            offset > startOffset && down != null -> {
                val gap = offsetOf(down, sheetHeight, peekHeight) - startOffset
                if (gap > 0 && offset - startOffset > gap * DISTANCE_RATIO) down else order[start]
            }
            offset < startOffset && up != null -> {
                val gap = startOffset - offsetOf(up, sheetHeight, peekHeight)
                if (gap > 0 && startOffset - offset > gap * DISTANCE_RATIO) up else order[start]
            }
            else -> order[start]
        }
    }
}

/**
 * 下から出る編集シート(ドロワー)。ダイアログやインラインの入力欄の代わりに使う。
 *
 * - 画面の最上部までは広げない。上に余白を残して後ろの画面を見せ、シートだと分かるようにする。
 * - 動かせるのは上端のつまみ・見出しの部分だけ。中身のスクロールではシートは動かない(誤って閉じない)。
 * - 右上の × で閉じる。戻るキーも同じ。どちらも [onDismiss] を呼ぶ(未保存の確認は呼ぶ側で出す)。
 * - [swipeToDismiss] = true(既定・確認や選択の小さなシート): 明確な下スワイプで閉じる。後ろをタップしても閉じる。
 * - [swipeToDismiss] = false(入力のシート): 下スワイプでは閉じず、画面下部に最小化して入力を保持する。
 *   最小化から更に下スワイプすると [onDismiss](完全に閉じる)。最小化の見出しをタップすると開き直す。後ろのタップでは閉じない。
 *
 * @param title 見出し。最小化したときにも出す。null なら出さない(中身の側に見出しがあるシート)。
 * @param closable false なら × を出さず、スワイプ・後ろのタップ・戻るでも閉じない(処理中の表示など)。
 */
@Composable
fun InkSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    swipeToDismiss: Boolean = true,
    title: String? = null,
    closable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val minimizable = !swipeToDismiss
    val host = LocalSheetHost.current
    if (host == null) {
        // 重ね表示の置き場が無いとき(プレビュー・テストなど)は、その場に全面で描く
        Box(Modifier.fillMaxSize()) {
            InkSheetBody(onDismiss, modifier, minimizable, title, closable, content)
        }
        return
    }
    // 呼び出し側の最新の内容で中身を差し替える。呼び出しが画面から外れたら重ね表示から取り除く
    val entry = remember { SheetEntry(nextSheetId()) }
    SideEffect {
        entry.content = { InkSheetBody(onDismiss, modifier, minimizable, title, closable, content) }
    }
    DisposableEffect(host) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** 重ねて出すシートの一覧。[SheetHost] が持つ。 */
internal class SheetHostState {
    val entries = mutableStateListOf<SheetEntry>()
}

/** 重ねて出す1枚のシート。中身は [InkSheet] 側の最新の内容で差し替える。 */
internal class SheetEntry(val id: Long) {
    var content: @Composable () -> Unit by mutableStateOf<@Composable () -> Unit>({})
}

/** 重ね表示の置き場。[SheetHost] の中でだけ値が入る。無いときは [InkSheet] がその場に描く。 */
internal val LocalSheetHost = staticCompositionLocalOf<SheetHostState?> { null }

/**
 * 画面全体を包み、その最前面に [InkSheet] を重ねて描く。AppRoot で Scaffold の外側に置く。
 * 後から出したシートほど上に重なる。別ウィンドウ(Dialog)を使わないので、高さ・キーボードが端末で変わらない。
 */
@Composable
fun SheetHost(content: @Composable () -> Unit) {
    val state = remember { SheetHostState() }
    CompositionLocalProvider(LocalSheetHost provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            state.entries.forEach { entry -> key(entry.id) { entry.content() } }
        }
    }
}

/** シートの番号。同じ番号を二度使わないよう増やし続ける(UI スレッドからだけ呼ぶ)。 */
private var sheetSeq = 0L

private fun nextSheetId(): Long = ++sheetSeq

@Composable
private fun InkSheetBody(
    onDismiss: () -> Unit,
    modifier: Modifier,
    minimizable: Boolean,
    title: String?,
    closable: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var sheetHeight by remember { mutableIntStateOf(0) }
    var snap by remember { mutableStateOf(SheetSnap.Expanded) }
    // 開いた位置からの下がり幅(px)。最初は画面外から出す
    val offset = remember { Animatable(Float.MAX_VALUE) }
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    // 最小化したときに見える高さ: つまみ+見出しの行+ナビゲーションバー
    val peekPx = with(density) { PEEK_HEIGHT.toPx() } + navBottom

    fun target(s: SheetSnap): Float = SheetSnapLogic.offsetOf(s, sheetHeight.toFloat(), peekPx)

    fun animateTo(s: SheetSnap) {
        snap = s
        scope.launch { offset.animateTo(target(s), tween(SNAP_MILLIS)) }
    }

    /**
     * 完全に閉じる。シートを下げてから onDismiss を呼ぶ。呼ぶ側が未保存の確認を出してシートを残したときは、
     * 元の位置(returnTo)へ戻す(シートが消えればこのコルーチンも止まる)。
     */
    fun close(returnTo: SheetSnap) {
        scope.launch {
            offset.animateTo(target(SheetSnap.Hidden), tween(SNAP_MILLIS))
            onDismiss()
            snap = returnTo
            offset.animateTo(target(returnTo), tween(SNAP_MILLIS))
        }
    }

    // 高さが決まったら(または中身の高さが変わったら)、今の段の位置へ合わせる
    LaunchedEffect(sheetHeight) {
        if (sheetHeight <= 0) return@LaunchedEffect
        if (offset.value == Float.MAX_VALUE) offset.snapTo(sheetHeight.toFloat())
        offset.animateTo(target(snap), tween(SNAP_MILLIS))
    }

    var dragFrom by remember { mutableStateOf(SheetSnap.Expanded) }
    val dragState = rememberDraggableState { delta ->
        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, sheetHeight.toFloat())) }
    }
    val dragHandle = Modifier.draggable(
        state = dragState,
        enabled = closable,
        orientation = Orientation.Vertical,
        onDragStarted = { dragFrom = snap },
        onDragStopped = { velocity ->
            val next = SheetSnapLogic.settle(dragFrom, offset.value, velocity, sheetHeight.toFloat(), peekPx, minimizable)
            if (next == SheetSnap.Hidden) close(returnTo = dragFrom) else animateTo(next)
        },
    )

    // 戻るキーは × と同じ。閉じられないシート(処理中など)では何もしない。ダイアログを使わないので、ここで受ける
    BackHandler(enabled = true) { if (closable) close(returnTo = snap) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        // 上端に余白を残す(ステータスバー+TOP_GAP)。シートだと分かるように後ろの画面を見せる
        val maxSheet = (maxHeight - statusTop - TOP_GAP).coerceAtLeast(PEEK_HEIGHT * 2)
        // 後ろの面。開いている間だけ暗くする。最小化中は後ろを見せる(タップでは閉じない)
        val scrimAlpha = if (sheetHeight > 0) {
            val open = 1f - (offset.value / sheetHeight.toFloat()).coerceIn(0f, 1f)
            if (snap == SheetSnap.Minimized) 0.08f else 0.32f * open
        } else {
            0f
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    when {
                        !closable || snap == SheetSnap.Minimized -> Unit
                        minimizable -> animateTo(SheetSnap.Minimized)
                        else -> close(returnTo = SheetSnap.Expanded)
                    }
                },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // キーボードの高さ分だけシート全体を持ち上げる(中身の imePadding は 0 になる)
                .imePadding()
                .heightIn(max = maxSheet)
                .offset { IntOffset(0, if (offset.value == Float.MAX_VALUE) maxSheet.roundToPx() else offset.value.roundToInt()) }
                .onSizeChanged { sheetHeight = it.height }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(c.paperRaised)
                // シートの面でタップを受け止める。押せない所(説明文・余白)のタップが後ろの面へ抜けて閉じないようにする。
                // 子の clickable は先に受け取るので、ボタンなどの動きには影響しない
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // つまみ・見出し・×。ここだけがスワイプでシートを動かせる
            Column(
                dragHandle
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (snap == SheetSnap.Minimized) animateTo(SheetSnap.Expanded)
                    },
            ) {
                Box(Modifier.fillMaxWidth().padding(top = Space.s), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c.line),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().height(HEADER_ROW).padding(start = Space.edge, end = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title ?: if (snap == SheetSnap.Minimized) "入力中(タップで開く)" else "",
                        style = AppTheme.type.title,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (snap == SheetSnap.Minimized && title != null) {
                        Text("タップで開く", style = AppTheme.type.caption, color = c.inkFaint, modifier = Modifier.padding(end = Space.s))
                    }
                    if (closable) Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { close(returnTo = snap) }
                            .semantics { contentDescription = "閉じる" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", style = AppTheme.type.title, color = c.inkSoft)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(modifier)
                    .fillMaxWidth()
                    .padding(horizontal = Space.edge)
                    .padding(bottom = Space.l),
                content = content,
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** シートの上に残す余白(ステータスバーの下からさらに)。後ろの画面が見えてシートだと分かる高さ。 */
private val TOP_GAP = 72.dp

/** 見出しの行の高さ(× のタップ領域を含む)。 */
private val HEADER_ROW = 48.dp

/** 最小化したときに見える高さ(つまみ+見出しの行)。ナビゲーションバーの分は別に足す。 */
private val PEEK_HEIGHT = 4.dp + 8.dp + 48.dp

private const val SNAP_MILLIS = 220
