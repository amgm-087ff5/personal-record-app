package com.amgm.personallog.ui.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.amgm.personallog.receipt.ReceiptAdjust
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.Pill
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable

/**
 * 取り込みの進捗(家計簿と支払い一覧で共有する表示)。
 * 項目ごとの状態と全体の進み具合を出す。onRetry・onOpen・onClear を渡したときだけ、その操作を出す(読み取り専用なら null)。
 * [error] があれば、取り込みの失敗を出す。
 */
@Composable
fun ImportPanel(
    imports: List<ImportItem>,
    error: String? = null,
    onDismissError: (() -> Unit)? = null,
    onRetry: ((Long) -> Unit)? = null,
    /** 読み取りが済んだ項目の「開く ›」。引数は取り込みの下書きのレシートID。 */
    onOpen: ((Long) -> Unit)? = null,
    /** まだ確定していない支払いの id。その項目の「開く ›」は「確認する ›」と出す。 */
    draftPaymentIds: Set<Long> = emptySet(),
    onClear: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    val total = imports.size
    val done = imports.count { it.phase == ImportPhase.DONE || it.phase == ImportPhase.DUPLICATE_SKIPPED || it.phase == ImportPhase.CANCELLED }
    val failed = imports.count { it.phase == ImportPhase.FAILED }
    val anyActive = imports.any { it.phase.active }
    val fraction = if (total == 0) 0f else (done + failed).toFloat() / total

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.edge).padding(top = Space.m)) {
        error?.let { msg ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Text(msg, style = AppTheme.type.caption, color = c.danger, modifier = Modifier.weight(1f))
                if (onDismissError != null) TextAction("閉じる", onClick = onDismissError, color = c.danger)
            }
            Hairline(color = c.danger)
        }
        if (total > 0) {
            val failedText = if (failed > 0) "(失敗${failed}件)" else ""
            Label("${total}件中${done}件完了$failedText", color = c.inkSoft)
            Box(modifier = Modifier.padding(top = Space.s).fillMaxWidth().height(Space.hair * 2).background(c.line)) {
                Box(modifier = Modifier.fillMaxWidth(fraction).height(Space.hair * 2).background(c.accent))
            }
        }
        imports.forEachIndexed { index, item ->
            val onTap: (() -> Unit)? = if (item.phase == ImportPhase.FAILED && onRetry != null) {
                { onRetry(item.key) }
            } else null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onTap != null) Modifier.tappable(onClick = onTap) else Modifier)
                    .padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Label("写真 ${index + 1}", color = c.ink)
                Text(
                    text = statusText(item),
                    style = AppTheme.type.caption,
                    color = if (item.phase == ImportPhase.FAILED) c.danger else c.inkSoft,
                    modifier = Modifier.weight(1f),
                )
                val paymentId = item.paymentId
                if (item.phase == ImportPhase.DONE && onOpen != null && paymentId != null) {
                    val label = if (paymentId in draftPaymentIds) "確認する ›" else "開く ›"
                    TextAction(label, onClick = { onOpen(paymentId) }, color = c.ink)
                }
            }
        }
        if (!anyActive && onClear != null) {
            TextAction("この取り込みの表示を閉じる", onClick = onClear)
        }
    }
}

private fun statusText(item: ImportItem): String = when (item.phase) {
    ImportPhase.WAITING -> "待機中"
    ImportPhase.PREPARING -> "取り込み中"
    ImportPhase.SCANNING -> "解析中"
    ImportPhase.DONE -> "完了(下書き)"
    ImportPhase.FAILED -> "失敗: ${item.message.orEmpty()}(タップで再試行)"
    ImportPhase.DUPLICATE_PENDING -> "重複の可能性(確認待ち)"
    ImportPhase.DUPLICATE_SKIPPED -> "重複スキップ"
    ImportPhase.ADJUST_PENDING -> "向き・範囲の確認待ち"
    ImportPhase.CANCELLED -> "取り消し"
}

/**
 * 取り込みの確認ダイアログ(重複の確認、向き・範囲の補正)。家計簿と支払い一覧の両方で、同じ状態を出す。
 * 重複の確認が先、その後に補正画面を出す。
 */
@Composable
fun ReceiptImportDialogs(
    state: ReceiptListUiState,
    onToggleAddAnyway: (Long) -> Unit,
    onResolveDuplicates: (Boolean) -> Unit,
    onSetAdjust: (Long, ReceiptAdjust) -> Unit,
    onAnalyze: () -> Unit,
    onAnalyzeWithout: () -> Unit,
    onCancelAdjust: () -> Unit,
) {
    val pending = state.imports.filter { it.phase == ImportPhase.DUPLICATE_PENDING }

    if (pending.isNotEmpty()) {
        DuplicateSheet(
            pending = pending,
            all = state.imports,
            onToggle = onToggleAddAnyway,
            onResolve = onResolveDuplicates,
        )
    }

    // 重複の確認が済み、コピー中の項目も無くなったら、向き・範囲の確認画面(毎回出す)
    if (pending.isEmpty() && state.adjustReady) {
        ReceiptAdjustImportDialog(
            items = state.adjustItems,
            onChange = onSetAdjust,
            onAnalyze = onAnalyze,
            onAnalyzeWithout = onAnalyzeWithout,
            onCancel = onCancelAdjust,
        )
    }
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
