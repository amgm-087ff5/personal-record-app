package com.amgm.personallog.ui.receipt

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.time.LocalDate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Pill
import com.amgm.personallog.ui.design.ScreenHeader
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.StampTone
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.sheet
import com.amgm.personallog.ui.design.tappable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape

/** 取り込みブロックの高さ。 */
private val CAPTURE_BLOCK_HEIGHT = 112.dp

/** 月見出し付きの一覧の1行分。 */
private class SlipEntry(val month: String?, val receipt: Receipt, val lastOfMonth: Boolean)

private fun monthOf(r: Receipt): String {
    val head = r.purchasedAt.take(7)
    val y = head.take(4)
    val m = head.drop(5)
    return if (head.length == 7 && head[4] == '-' && y.all { it.isDigit() } && m.all { it.isDigit() }) {
        "${y}年${m.trimStart('0')}月"
    } else {
        "日付不明"
    }
}

private fun buildEntries(receipts: List<Receipt>): List<SlipEntry> =
    receipts.mapIndexed { i, r ->
        val month = monthOf(r)
        val prev = receipts.getOrNull(i - 1)?.let { monthOf(it) }
        val next = receipts.getOrNull(i + 1)?.let { monthOf(it) }
        SlipEntry(month = if (month != prev) month else null, receipt = r, lastOfMonth = month != next)
    }

/** 絞り込み(一覧の表示対象)。判定は画像の有無: imagePath が空なら手入力。 */
private const val FILTER_ALL = 0
private const val FILTER_RECEIPT = 1
private const val FILTER_MANUAL = 2

private fun Receipt.matches(filter: Int): Boolean = when (filter) {
    FILTER_RECEIPT -> imagePath.isNotBlank()
    FILTER_MANUAL -> imagePath.isBlank()
    else -> true
}

/** 戻るボタン(家計簿から開いたとき用)。 */
private fun headerBack(onBack: () -> Unit): @Composable () -> Unit = {
    TextAction("戻る", onClick = onBack, color = AppTheme.colors.inkSoft)
}

/**
 * 支払い画面(ReceiptRoutes.HOME)。取り込み状況、一覧(すべて/レシート/手入力で絞り込み)、「撮る」「選ぶ(複数可)」。
 * autoCapture が true のときは、表示直後に一度だけ「撮る」と同じカメラを開く(家計簿の「レシートから登録」)。
 * onBack があれば見出しに「戻る」を出す。
 */
@Composable
fun ReceiptScreen(
    modifier: Modifier = Modifier,
    onOpenReceipt: (Long) -> Unit = {},
    onBack: (() -> Unit)? = null,
    autoCapture: Boolean = false,
    viewModel: ReceiptListViewModel = hiltViewModel(),
) {
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val duplicates by viewModel.possibleDuplicates.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 撮影中の保存先(画面回転・プロセス再生成後も結果を受け取れるよう保存する)
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    // 手入力の支払い(レシート画像なし)のシート
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    // 一覧の絞り込み(FILTER_* の値)
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPath
        pendingPath = null
        if (path != null) {
            if (ok) viewModel.onCaptured(path) else viewModel.discardCapture(path)
        }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(RECEIPT_PICK_MAX)) { uris ->
        if (uris.isNotEmpty()) viewModel.onPicked(uris)
    }

    // 「撮る」と同じ処理(カメラを開く。起動できなければ案内を出す)
    val startCamera: () -> Unit = {
        try {
            val path = viewModel.newCameraPath()
            pendingPath = path
            takePicture.launch(viewModel.cameraUri(path))
        } catch (e: Exception) {
            pendingPath = null
            viewModel.showError("カメラを起動できませんでした。「選ぶ」をお使いください。")
        }
    }
    // 家計簿の「レシートから登録」から来たときは、表示直後に一度だけ開く(回転などで開き直さない)
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoCapture) {
        if (autoCapture && !autoStarted) {
            autoStarted = true
            startCamera()
        }
    }

    val pendingOpen by viewModel.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen) {
        pendingOpen?.let { id ->
            viewModel.consumeOpen(id)
            onOpenReceipt(id)
        }
    }

    val pending = state.imports.filter { it.phase == ImportPhase.DUPLICATE_PENDING }
    val visible = remember(receipts, filter) { receipts.filter { it.matches(filter) } }
    val entries = remember(visible) { buildEntries(visible) }

    PaperScreen(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                ScreenHeader(
                    kicker = "領収の記録",
                    title = "支払い",
                    trailing = if (onBack == null) null else headerBack(onBack),
                )
            }

            item(key = "capture") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CaptureBlocks(
                        onCamera = startCamera,
                        onGallery = {
                            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    TextAction(
                        "手入力で追加(レシート画像なし)",
                        onClick = { manualOpen = true },
                        modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
                    )
                }
            }

            if (state.imports.isNotEmpty()) {
                item(key = "imports") {
                    ImportPanel(
                        imports = state.imports,
                        onRetry = viewModel::retry,
                        onOpen = onOpenReceipt,
                        onClear = viewModel::clearFinished,
                    )
                }
            }

            state.error?.let { msg ->
                item(key = "error") {
                    Column(modifier = Modifier.padding(horizontal = Space.edge).padding(top = Space.m)) {
                        Text(msg, style = AppTheme.type.body, color = AppTheme.colors.danger)
                        TextAction("閉じる", onClick = viewModel::dismissError, color = AppTheme.colors.danger)
                    }
                }
            }

            if (receipts.isNotEmpty()) {
                item(key = "filter") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.edge)
                            .padding(top = Space.m),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        Pill(text = "すべて", selected = filter == FILTER_ALL, onClick = { filter = FILTER_ALL })
                        Pill(text = "レシート", selected = filter == FILTER_RECEIPT, onClick = { filter = FILTER_RECEIPT })
                        Pill(text = "手入力", selected = filter == FILTER_MANUAL, onClick = { filter = FILTER_MANUAL })
                    }
                }
            }

            if (receipts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "まだ支払いがありません。「撮る」「選ぶ」、または「手入力で追加」から追加してください。",
                        style = AppTheme.type.body,
                        color = AppTheme.colors.inkSoft,
                        modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.l),
                    )
                }
            } else if (visible.isEmpty()) {
                item(key = "filter-empty") {
                    Text(
                        "この絞り込みに当てはまる支払いはありません。",
                        style = AppTheme.type.body,
                        color = AppTheme.colors.inkSoft,
                        modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.l),
                    )
                }
            } else {
                items(entries, key = { it.receipt.id }) { e ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (e.month != null) {
                            Label(
                                e.month,
                                modifier = Modifier.padding(start = Space.edge, end = Space.edge, top = Space.l, bottom = Space.s),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.edge, vertical = Space.xs)
                                .sheet(RoundedCornerShape(16.dp), depth = 10.dp),
                        ) {
                            ReceiptSlip(
                                r = e.receipt,
                                isDuplicate = duplicates[e.receipt.id] != null,
                                onClick = { onOpenReceipt(e.receipt.id) },
                            )
                        }
                    }
                }
            }

            item(key = "bottomSpace") { Box(modifier = Modifier.height(Space.xl)) }
        }
    }

    // 重複の確認が済み、コピー中の項目も無くなったら、向き・範囲の確認画面(毎回出す)
    if (pending.isEmpty() && state.adjustReady) {
        ReceiptAdjustImportDialog(
            items = state.adjustItems,
            onChange = viewModel::setAdjust,
            onAnalyze = viewModel::analyzeAdjusted,
            onAnalyzeWithout = viewModel::analyzeWithoutAdjust,
            onCancel = viewModel::cancelAdjust,
        )
    }

    if (pending.isNotEmpty()) {
        DuplicateSheet(
            pending = pending,
            all = state.imports,
            onToggle = viewModel::toggleAddAnyway,
            onResolve = viewModel::resolveDuplicates,
        )
    }

    if (manualOpen) {
        ManualPaymentSheet(
            onSubmit = { date, time, store, total, headcount ->
                val error = viewModel.addManual(date, time, store, total, headcount)
                // 保存を始めたらシートを閉じる。詳細画面へは pendingOpen で移る
                if (error == null) manualOpen = false
                error
            },
            onDismiss = { manualOpen = false },
        )
    }
}

/**
 * 手入力の支払いを1件作るシート。日付は必須、時刻・店名・合計は任意。
 * 品目は作ったあとの詳細画面で足す。合計は空でもよい(下書きとして作る)。
 */
@Composable
private fun ManualPaymentSheet(
    onSubmit: (date: String, time: String, store: String, total: String, headcount: String) -> String?,
    onDismiss: () -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var time by rememberSaveable { mutableStateOf("") }
    var store by rememberSaveable { mutableStateOf("") }
    var total by rememberSaveable { mutableStateOf("") }
    var headcount by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    InkSheet(onDismiss = onDismiss, modifier = Modifier.imePadding().verticalScroll(rememberScrollState())) {
        SheetTitle("手入力で支払いを追加")
        Text(
            "レシートの画像なしで、支払いを1件作ります。品目は次の画面で追加できます。",
            style = AppTheme.type.body,
            color = AppTheme.colors.inkSoft,
            modifier = Modifier.padding(bottom = Space.m),
        )
        SheetField("日付 yyyy-MM-dd", date, { date = it })
        SheetField("時刻 HH:mm(任意)", time, { time = it })
        SheetField("店名(任意)", store, { store = it })
        SheetField("合計(円・任意)", total, { total = it.filter { c -> c.isDigit() } }, numeric = true)
        SheetField("人数(任意)", headcount, { headcount = it.filter { c -> c.isDigit() }.take(3) }, numeric = true)
        error?.let {
            Text(it, style = AppTheme.type.caption, color = AppTheme.colors.danger, modifier = Modifier.padding(bottom = Space.s))
        }
        TextAction("追加して開く", onClick = { error = onSubmit(date, time, store, total, headcount) })
        TextAction("キャンセル", onClick = onDismiss, color = AppTheme.colors.inkSoft)
    }
}

/** 横2分割の取り込みブロック。左は琥珀に灯る「撮る」、右は白い面の「選ぶ」。 */
@Composable
private fun CaptureBlocks(onCamera: () -> Unit, onGallery: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.edge),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(CAPTURE_BLOCK_HEIGHT)
                .shadow(
                    elevation = 14.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = c.glow.copy(alpha = 0.25f),
                    spotColor = c.glow.copy(alpha = 0.60f),
                )
                .background(Brush.verticalGradient(listOf(c.accentTop, c.accent)), shape)
                .tappable(onClick = onCamera)
                .padding(Space.m),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column {
                Text("撮る", style = AppTheme.type.headline, color = c.onAccent)
                Label("カメラ", color = c.onAccent)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(CAPTURE_BLOCK_HEIGHT)
                .sheet(shape, depth = 10.dp)
                .tappable(onClick = onGallery)
                .padding(Space.m),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column {
                Text("選ぶ", style = AppTheme.type.headline, color = c.ink)
                Label("ギャラリー(最大${RECEIPT_PICK_MAX}枚)")
            }
        }
    }
}

/** 取り込み中・完了・失敗の項目と全体の進捗。細い線+小さな文字。 */
@Composable
private fun ImportPanel(
    imports: List<ImportItem>,
    onRetry: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val c = AppTheme.colors
    val total = imports.size
    val done = imports.count { it.phase == ImportPhase.DONE || it.phase == ImportPhase.DUPLICATE_SKIPPED || it.phase == ImportPhase.CANCELLED }
    val failed = imports.count { it.phase == ImportPhase.FAILED }
    val anyActive = imports.any { it.phase.active }
    val fraction = if (total == 0) 0f else (done + failed).toFloat() / total

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.edge).padding(top = Space.m)) {
        val failedText = if (failed > 0) "(失敗${failed}件)" else ""
        Label("${total}件中${done}件完了$failedText", color = c.inkSoft)
        Box(modifier = Modifier.padding(top = Space.s).fillMaxWidth().height(Space.hair * 2).background(c.line)) {
            Box(modifier = Modifier.fillMaxWidth(fraction).height(Space.hair * 2).background(c.accent))
        }
        imports.forEachIndexed { index, item ->
            val onTap: (() -> Unit)? = when {
                item.phase == ImportPhase.FAILED -> ({ onRetry(item.key) })
                item.phase == ImportPhase.DONE -> item.receiptId?.let { id -> ({ onOpen(id) }) }
                else -> null
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onTap != null) Modifier.tappable(onClick = onTap) else Modifier)
                    .padding(vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Label("写真 ${index + 1}", color = c.ink)
                Text(
                    text = statusText(item),
                    style = AppTheme.type.caption,
                    color = if (item.phase == ImportPhase.FAILED) c.danger else c.inkSoft,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (!anyActive) {
            TextAction("この取り込みの表示を閉じる", onClick = onClear)
        }
    }
}

private fun statusText(item: ImportItem): String = when (item.phase) {
    ImportPhase.WAITING -> "待機中"
    ImportPhase.PREPARING -> "取り込み中"
    ImportPhase.SCANNING -> "解析中"
    ImportPhase.DONE -> "完了(下書き)・タップで開く"
    ImportPhase.FAILED -> "失敗: ${item.message.orEmpty()}(タップで再試行)"
    ImportPhase.DUPLICATE_PENDING -> "重複の可能性(確認待ち)"
    ImportPhase.DUPLICATE_SKIPPED -> "重複スキップ"
    ImportPhase.ADJUST_PENDING -> "向き・範囲の確認待ち"
    ImportPhase.CANCELLED -> "取り消し"
}

/**
 * 重複の可能性がある写真の確認。既定はすべてスキップ(起票しない)。
 * 「追加する」を選んだ写真だけ通常どおり解析する。閉じるには下のどちらかを押す。
 */
@Composable
private fun DuplicateSheet(
    pending: List<ImportItem>,
    all: List<ImportItem>,
    onToggle: (Long) -> Unit,
    onResolve: (Boolean) -> Unit,
) {
    InkSheet(onDismiss = {}, closable = false) {
        SheetTitle("重複の可能性がある写真")
        Text(
            "次の写真は、既に取り込まれたレシートと同じ画像の可能性があります。「追加する」を選んだ写真だけ読み取ります。選ばない写真は起票しません(既定)。",
            style = AppTheme.type.body,
            color = AppTheme.colors.inkSoft,
            modifier = Modifier.padding(bottom = Space.m),
        )
        pending.forEach { item ->
            Hairline()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Label("写真 ${all.indexOfFirst { it.key == item.key } + 1}")
                    Text(item.duplicateNote.orEmpty(), style = AppTheme.type.caption, color = AppTheme.colors.inkSoft)
                }
                Pill(
                    text = if (item.addAnyway) "追加する" else "スキップ",
                    selected = item.addAnyway,
                    onClick = { onToggle(item.key) },
                )
            }
        }
        Hairline()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Space.m),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextAction("すべてスキップ(既定)", onClick = { onResolve(false) }, color = AppTheme.colors.inkSoft)
            TextAction("選んだ分を読み取る", onClick = { onResolve(true) })
        }
    }
}

/** 一覧の1枚: 左に日付と店名、右に金額と状態の判子。 */
@Composable
private fun ReceiptSlip(r: Receipt, isDuplicate: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick = onClick)
            .padding(horizontal = Space.edge, vertical = Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Space.m)) {
            Label(r.purchasedAt)
            Text(
                r.storeName.ifBlank { "(店名未入力)" },
                style = AppTheme.type.title,
                color = AppTheme.colors.ink,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            val total = r.totalAmount
            if (total == null) {
                // 合計が空(下書き)。0円と区別して警告色で出す
                Text("合計 未入力", style = AppTheme.type.numeral, color = AppTheme.colors.danger)
            } else {
                Text(formatYen(total.toLong()), style = AppTheme.type.numeralL, color = AppTheme.colors.ink)
            }
            if (isDuplicate) Stamp("重複の可能性", tone = StampTone.Danger)
            // 画像の無い支払いは手入力
            if (r.imagePath.isBlank()) Stamp("手入力", tone = StampTone.Neutral)
            if (r.status == ReceiptStatus.DRAFT) {
                Stamp("未確定", tone = StampTone.Accent)
            } else {
                Stamp("確定", tone = StampTone.Moss)
            }
        }
    }
}

internal fun formatYen(v: Long): String = String.format(java.util.Locale.JAPAN, "¥%,d", v)
