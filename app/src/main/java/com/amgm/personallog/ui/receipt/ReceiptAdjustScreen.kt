package com.amgm.personallog.ui.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amgm.personallog.receipt.ReceiptAdjust
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkButtonKind
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import kotlinx.coroutines.launch

/**
 * 取り込み後の確認画面(全画面)。補正待ちの画像を順に表示し、回転・トリミングを決める。
 * 毎回出す。戻る操作では閉じず、下のボタンで進めるか取り消す。
 */
@Composable
fun ReceiptAdjustImportDialog(
    items: List<ImportItem>,
    onChange: (key: Long, adjust: ReceiptAdjust) -> Unit,
    onAnalyze: () -> Unit,
    onAnalyzeWithout: () -> Unit,
    onCancel: () -> Unit,
) {
    if (items.isEmpty()) return
    val c = AppTheme.colors
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage.coerceIn(0, items.lastIndex)
    val current = items[page]

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.paper)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(
                    "前へ",
                    onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                    enabled = page > 0,
                    color = c.inkSoft,
                )
                Label("向き・範囲の確認 ${page + 1} / ${items.size}", color = c.ink, modifier = Modifier.padding(horizontal = Space.s))
                TextAction(
                    "次へ",
                    onClick = { scope.launch { pager.animateScrollToPage(page + 1) } },
                    enabled = page < items.lastIndex,
                    color = c.inkSoft,
                )
                Box(modifier = Modifier.weight(1f))
                TextAction("取り消し", onClick = onCancel, color = c.danger)
            }
            HorizontalPager(
                state = pager,
                key = { items.getOrNull(it)?.key ?: it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { i ->
                val item = items.getOrNull(i) ?: return@HorizontalPager
                ReceiptAdjustCanvas(
                    imagePath = item.imagePath.orEmpty(),
                    adjust = item.adjust,
                    onChange = { onChange(item.key, it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ReceiptAdjustControls(
                adjust = current.adjust,
                onChange = { onChange(current.key, it) },
                stateKey = current.key,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.edge).padding(bottom = Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                InkButton("この補正で解析", onClick = onAnalyze, modifier = Modifier.fillMaxWidth())
                InkButton(
                    "補正なしで全部解析",
                    onClick = onAnalyzeWithout,
                    kind = InkButtonKind.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 詳細画面から開く、画像1枚の編集(全画面)。
 * 「保存」は補正値だけ保存、「保存して読み取り直す」は保存してから読み取り直す(確認は呼び出し側)。
 */
@Composable
fun ReceiptAdjustSingleDialog(
    imagePath: String,
    initial: ReceiptAdjust,
    busy: Boolean,
    onSave: (ReceiptAdjust) -> Unit,
    onSaveAndRescan: (ReceiptAdjust) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    // 画面回転でも編集中の値が消えないよう、5つの数値で保存する
    var draft by rememberSaveable(stateSaver = ReceiptAdjustSaver) { mutableStateOf(initial) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.paper)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("向き・範囲を調整", color = c.ink, modifier = Modifier.padding(start = Space.s))
                Box(modifier = Modifier.weight(1f))
                TextAction("キャンセル", onClick = onDismiss, color = c.inkSoft)
            }
            ReceiptAdjustEditor(
                imagePath = imagePath,
                adjust = draft,
                onChange = { draft = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.edge).padding(bottom = Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                InkButton("保存", onClick = { onSave(draft) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                InkButton(
                    "保存して読み取り直す",
                    onClick = { onSaveAndRescan(draft) },
                    enabled = !busy,
                    kind = InkButtonKind.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
