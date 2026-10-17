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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.amgm.personallog.ui.design.tappable
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

/** レシートタブのトップ画面: 取り込み状況、一覧、「撮る」「選ぶ(複数可)」。 */
@Composable
fun ReceiptScreen(
    modifier: Modifier = Modifier,
    onOpenReceipt: (Long) -> Unit = {},
    viewModel: ReceiptListViewModel = hiltViewModel(),
) {
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val duplicates by viewModel.possibleDuplicates.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 撮影中の保存先(画面回転・プロセス再生成後も結果を受け取れるよう保存する)
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

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

    LaunchedEffect(Unit) {
        viewModel.openEvents.collect { onOpenReceipt(it) }
    }

    val pending = state.imports.filter { it.phase == ImportPhase.DUPLICATE_PENDING }
    val entries = remember(receipts) { buildEntries(receipts) }

    PaperScreen(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header") { ScreenHeader(kicker = "領収の記録", title = "レシート") }

            item(key = "capture") {
                CaptureBlocks(
                    onCamera = {
                        try {
                            val path = viewModel.newCameraPath()
                            pendingPath = path
                            takePicture.launch(viewModel.cameraUri(path))
                        } catch (e: Exception) {
                            pendingPath = null
                            viewModel.showError("カメラを起動できませんでした。「選ぶ」をお使いください。")
                        }
                    },
                    onGallery = {
                        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
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

            if (receipts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "まだレシートがありません。「撮る」または「選ぶ」から追加してください。",
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
                        Column(modifier = Modifier.fillMaxWidth().background(AppTheme.colors.paperRaised)) {
                            Hairline(dashed = true)
                            ReceiptSlip(
                                r = e.receipt,
                                isDuplicate = duplicates[e.receipt.id] != null,
                                onClick = { onOpenReceipt(e.receipt.id) },
                            )
                            if (e.lastOfMonth) Hairline(dashed = true)
                        }
                    }
                }
            }

            item(key = "bottomSpace") { Box(modifier = Modifier.height(Space.xl)) }
        }
    }

    if (pending.isNotEmpty()) {
        DuplicateSheet(
            pending = pending,
            all = state.imports,
            onToggle = viewModel::toggleAddAnyway,
            onResolve = viewModel::resolveDuplicates,
        )
    }
}

/** 横2分割の取り込みブロック。左は朱ベタ「撮る」、右は罫線ふち「選ぶ」。 */
@Composable
private fun CaptureBlocks(onCamera: () -> Unit, onGallery: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.edge),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(CAPTURE_BLOCK_HEIGHT)
                .background(c.accent, shape)
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
                .border(1.dp, c.ink, shape)
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
    val done = imports.count { it.phase == ImportPhase.DONE || it.phase == ImportPhase.DUPLICATE_SKIPPED }
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
    InkSheet(onDismiss = {}) {
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
            Text(formatYen(r.totalAmount.toLong()), style = AppTheme.type.numeralL, color = AppTheme.colors.ink)
            if (isDuplicate) Stamp("重複の可能性", tone = StampTone.Danger)
            if (r.status == ReceiptStatus.DRAFT) {
                Stamp("未確定", tone = StampTone.Accent)
            } else {
                Stamp("確定", tone = StampTone.Moss)
            }
        }
    }
}

internal fun formatYen(v: Long): String = String.format(java.util.Locale.JAPAN, "¥%,d", v)
