package com.amgm.personallog.ui.receipt

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
import androidx.compose.foundation.shape.RoundedCornerShape

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
 * 支払い一覧(ReceiptRoutes.HOME)。取り込みの進捗、一覧(すべて/レシート/手入力で絞り込み)を出す。
 * 支払いを作る操作(撮る・選ぶ・手入力)は家計簿の「＋ 記録」にあり、ここには無い。
 * 取り込みの進捗は家計簿と同じ状態を見せる(読み取り専用)。確認(重複・向きと範囲)が残っていれば、ここでも出す。
 * onBack があれば見出しに「戻る」を出す。
 */
@Composable
fun ReceiptScreen(
    modifier: Modifier = Modifier,
    onOpenReceipt: (Long) -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: ReceiptListViewModel = hiltViewModel(),
) {
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val duplicates by viewModel.possibleDuplicates.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 一覧の絞り込み(FILTER_* の値)
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }

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

            if (state.imports.isNotEmpty() || state.error != null) {
                item(key = "imports") {
                    ImportPanel(
                        imports = state.imports,
                        error = state.error,
                        onDismissError = viewModel::dismissError,
                    )
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
                        "まだ支払いがありません。家計簿の「＋ 記録」から追加してください。",
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

    ReceiptImportDialogs(
        state = state,
        onToggleAddAnyway = viewModel::toggleAddAnyway,
        onResolveDuplicates = viewModel::resolveDuplicates,
        onSetAdjust = viewModel::setAdjust,
        onAnalyze = viewModel::analyzeAdjusted,
        onAnalyzeWithout = viewModel::analyzeWithoutAdjust,
        onCancelAdjust = viewModel::cancelAdjust,
    )
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
