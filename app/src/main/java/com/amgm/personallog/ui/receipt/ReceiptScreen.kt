package com.amgm.personallog.ui.receipt

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptStatus

/** レシートタブのトップ画面: 取り込み状況、一覧、「撮影」「写真から選ぶ(複数可)」。 */
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

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("レシート", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    try {
                        val path = viewModel.newCameraPath()
                        pendingPath = path
                        takePicture.launch(viewModel.cameraUri(path))
                    } catch (e: Exception) {
                        pendingPath = null
                        viewModel.showError("カメラを起動できませんでした。「写真から選ぶ」をお使いください。")
                    }
                },
            ) { Text("撮影") }
            Button(
                onClick = {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) { Text("写真から選ぶ(最大${RECEIPT_PICK_MAX}枚)") }
        }

        if (state.imports.isNotEmpty()) {
            ImportPanel(
                imports = state.imports,
                onRetry = viewModel::retry,
                onOpen = onOpenReceipt,
                onClear = viewModel::clearFinished,
            )
        }

        state.error?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = viewModel::dismissError) { Text("閉じる") }
                }
            }
        }

        if (receipts.isEmpty()) {
            Text("まだレシートがありません。「撮影」または「写真から選ぶ」から追加してください。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(receipts, key = { it.id }) { r ->
                    ReceiptRow(r, original = duplicates[r.id], onClick = { onOpenReceipt(r.id) })
                }
            }
        }
    }

    if (pending.isNotEmpty()) {
        DuplicateDialog(
            pending = pending,
            all = state.imports,
            onToggle = viewModel::toggleAddAnyway,
            onResolve = viewModel::resolveDuplicates,
        )
    }
}

/** 取り込み中・完了・失敗の項目一覧と全体の進捗。 */
@Composable
private fun ImportPanel(
    imports: List<ImportItem>,
    onRetry: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val total = imports.size
    val done = imports.count { it.phase == ImportPhase.DONE || it.phase == ImportPhase.DUPLICATE_SKIPPED }
    val failed = imports.count { it.phase == ImportPhase.FAILED }
    val anyActive = imports.any { it.phase.active }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val failedText = if (failed > 0) "(失敗${failed}件)" else ""
            Text("${total}件中${done}件完了$failedText", style = MaterialTheme.typography.titleSmall)
            Column(
                modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                imports.forEachIndexed { index, item ->
                    val onTap: (() -> Unit)? = when {
                        item.phase == ImportPhase.FAILED -> ({ onRetry(item.key) })
                        item.phase == ImportPhase.DONE -> item.receiptId?.let { id -> ({ onOpen(id) }) }
                        else -> null
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("写真 ${index + 1}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = statusText(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.phase == ImportPhase.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            if (!anyActive) {
                TextButton(onClick = onClear) { Text("この取り込みの表示を閉じる") }
            }
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
 * チェックした写真だけ「それでも追加」として通常どおり解析する。
 */
@Composable
private fun DuplicateDialog(
    pending: List<ImportItem>,
    all: List<ImportItem>,
    onToggle: (Long) -> Unit,
    onResolve: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("重複の可能性がある写真") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("次の写真は、既に取り込まれたレシートと同じ画像の可能性があります。チェックした写真だけ読み取ります。チェックしない写真は起票しません(既定)。")
                pending.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.addAnyway, onCheckedChange = { onToggle(item.key) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                "写真 ${all.indexOfFirst { it.key == item.key } + 1}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(item.duplicateNote.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onResolve(true) }) { Text("チェックした分を読み取る") }
        },
        dismissButton = {
            TextButton(onClick = { onResolve(false) }) { Text("すべてスキップ(既定)") }
        },
    )
}

@Composable
private fun ReceiptRow(r: Receipt, original: Receipt?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.purchasedAt, style = MaterialTheme.typography.bodySmall)
                Text(r.storeName.ifBlank { "(店名未入力)" }, style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatYen(r.totalAmount.toLong()), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (original != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                "重複の可能性",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (r.status == ReceiptStatus.DRAFT) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                "未確定",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun formatYen(v: Long): String = String.format(java.util.Locale.JAPAN, "¥%,d", v)
