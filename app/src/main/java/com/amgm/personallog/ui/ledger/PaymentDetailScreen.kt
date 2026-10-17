package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.receipt.ReceiptImageCodec
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.LeaderRow
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.StampTone
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 支払いの詳細画面。家計簿の正である支払い(グループまたは単独の支出)を、合計・日時・メモ・品目まで見て直す。
 * レシート由来なら、サムネイルと「レシートを開く」でレシートの詳細へ移れる。
 */
@Composable
fun PaymentDetailScreen(
    onBack: () -> Unit,
    onOpenReceipt: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentDetailViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var editingGroup by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Expense?>(null) }
    var addingItem by remember { mutableStateOf(false) }
    var deletingGroup by remember { mutableStateOf(false) }

    // 削除などで対象が無くなった(または開いた時点で無かった)ら戻る
    LaunchedEffect(s.finished, s.notFound) { if (s.finished || s.notFound) onBack() }

    PaperScreen(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "nav") {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.s)) {
                    TextAction("戻る", onClick = onBack, color = c.inkSoft)
                }
            }
            s.error?.let { err ->
                item(key = "error") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.edge, vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(err, style = AppTheme.type.caption, color = c.danger, modifier = Modifier.weight(1f))
                        TextAction("閉じる", onClick = viewModel::dismissError, color = c.danger)
                    }
                }
            }
            val group = s.group
            val single = s.single
            if (group != null) {
                item(key = "head") {
                    GroupHeader(group, onEdit = { editingGroup = true })
                }
                item(key = "receipt") {
                    ReceiptBlock(s.receipt?.imagePath, s.receipt?.id, onOpenReceipt)
                }
                item(key = "items-title") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Space.edge).padding(top = Space.l)) {
                        Label("品目")
                        Spacer(Modifier.height(Space.xs))
                        Hairline(color = c.ink)
                    }
                }
                items(s.items.size, key = { "item-" + s.items[it].id }) { i ->
                    ItemRow(s.items[i], onClick = { editingItem = s.items[i] })
                }
                item(key = "sum") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Space.edge).padding(top = Space.s)) {
                        Hairline(dashed = true)
                        Spacer(Modifier.height(Space.s))
                        LeaderRow(
                            label = "品目の合計",
                            value = LedgerLogic.formatYen(s.itemSum),
                            labelStyle = AppTheme.type.caption,
                            valueStyle = AppTheme.type.caption,
                            labelColor = c.inkSoft,
                            valueColor = c.inkSoft,
                        )
                        if (s.difference != 0L) {
                            Text(
                                "差額 ${LedgerLogic.formatYen(s.difference)}(支払い金額 ${LedgerLogic.formatYen(group.amount.toLong())} − 品目の合計)。" +
                                    "カテゴリ別の集計では、差額は支払いのカテゴリ「${group.category}」に入ります。",
                                style = AppTheme.type.caption,
                                color = c.danger,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                            if (s.itemSum > 0) {
                                TextAction(
                                    "${LedgerLogic.formatYen(s.itemSum)} を合計に入れる",
                                    onClick = viewModel::useItemsSumAsTotal,
                                )
                            }
                        }
                        TextAction("＋ 品目を追加", onClick = { addingItem = true })
                        if (group.receiptId != null) {
                            Text(ALLOCATION_NOTE, style = AppTheme.type.caption, color = c.inkFaint)
                        }
                    }
                }
                item(key = "delete") {
                    TextAction(
                        "この支払いを削除",
                        onClick = { deletingGroup = true },
                        color = c.danger,
                        modifier = Modifier.padding(horizontal = Space.s, vertical = Space.l),
                    )
                }
            } else if (single != null) {
                item(key = "single") {
                    SingleBody(single, onEdit = { editingItem = single })
                }
                item(key = "receipt") {
                    ReceiptBlock(s.receipt?.imagePath, s.receipt?.id, onOpenReceipt)
                }
                item(key = "pad") { Spacer(Modifier.height(Space.xl)) }
            } else if (s.loading) {
                item(key = "loading") {
                    Text("読み込み中…", style = AppTheme.type.body, color = c.inkSoft, modifier = Modifier.padding(Space.edge))
                }
            }
        }
    }

    val group = s.group
    if (editingGroup && group != null) {
        // グループ自体の編集には既存のシートを使う(品名の欄は出さない)
        ExpenseEditorSheet(
            original = Expense(
                date = group.date, time = group.time, amount = group.amount, category = group.category,
                memo = group.memo, receiptId = group.receiptId, createdAt = group.createdAt,
            ),
            initialDate = group.date,
            initialTime = group.time,
            linkedReceiptAt = s.receiptAt,
            onDismiss = { editingGroup = false },
            onSave = { date, time, amount, category, _, memo, done ->
                viewModel.saveGroup(group, date, time, amount, category, memo, done)
            },
            onDelete = null,
            title = "支払いを編集",
            showName = false,
        )
    }
    editingItem?.let { target ->
        ExpenseEditorSheet(
            original = target,
            initialDate = target.date,
            initialTime = target.time,
            linkedReceiptAt = s.receiptAt?.takeIf { target.receiptId != null },
            onDismiss = { editingItem = null },
            onSave = { date, time, amount, category, name, memo, done ->
                viewModel.saveItem(group, target, date, time, amount, category, name, memo, done)
            },
            onDelete = {
                viewModel.deleteItem(target)
                editingItem = null
            },
            title = if (group != null) "品目を編集" else null,
        )
    }
    if (addingItem && group != null) {
        ExpenseEditorSheet(
            original = null,
            initialDate = group.date,
            initialTime = group.time,
            linkedReceiptAt = null,
            onDismiss = { addingItem = false },
            onSave = { date, time, amount, category, name, memo, done ->
                viewModel.saveItem(group, null, date, time, amount, category, name, memo, done)
            },
            onDelete = null,
            title = "品目を追加",
        )
    }
    if (deletingGroup && group != null) {
        ConfirmSheet(
            title = "支払いを削除",
            message = "「${group.memo.ifBlank { group.category }}」の支払いと品目をすべて削除します。レシートは残ります。",
            confirmText = "削除",
            onDismiss = { deletingGroup = false },
            onConfirm = {
                deletingGroup = false
                viewModel.deleteGroup()
            },
        )
    }
}

/** 「レシートと違う」の小さな印。 */
@Composable
private fun EditedMark(modifier: Modifier = Modifier) {
    Stamp("レシートと違う", tone = StampTone.Accent, modifier = modifier)
}

@Composable
private fun GroupHeader(group: ExpenseGroup, onEdit: () -> Unit) {
    val c = AppTheme.colors
    val marks = PaymentMarks.ofGroup(group)
    Column(
        Modifier.fillMaxWidth().tappable(onEdit).padding(horizontal = Space.edge, vertical = Space.m),
    ) {
        Label("支払い")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                LedgerLogic.dayLabel(group.date) + if (group.time.isNotEmpty()) " ${group.time}" else "",
                style = AppTheme.type.body,
                color = c.inkSoft,
            )
            if (PaymentField.TIME in marks) EditedMark(Modifier.padding(start = Space.s))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(LedgerLogic.formatYen(group.amount.toLong()), style = AppTheme.type.numeralXL, color = c.ink)
            if (PaymentField.AMOUNT in marks) EditedMark(Modifier.padding(start = Space.s, bottom = Space.s))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                group.memo.ifBlank { "(メモなし)" },
                style = AppTheme.type.title,
                color = if (group.memo.isBlank()) c.inkFaint else c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (PaymentField.MEMO in marks) EditedMark(Modifier.padding(start = Space.s))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("カテゴリ ${group.category}", style = AppTheme.type.caption, color = c.inkSoft)
            if (PaymentField.CATEGORY in marks) EditedMark(Modifier.padding(start = Space.s))
        }
        Text("タップで直す", style = AppTheme.type.caption, color = c.inkFaint)
    }
    Hairline()
}

@Composable
private fun ItemRow(item: Expense, onClick: () -> Unit) {
    val c = AppTheme.colors
    val marks = PaymentMarks.ofExpense(item)
    val sub = listOfNotNull(
        item.category,
        item.memo.ifBlank { null },
        item.time.ifEmpty { null }.takeIf { PaymentField.TIME in marks },
    ).joinToString(" · ")
    Column(Modifier.fillMaxWidth().tappable(onClick).padding(horizontal = Space.edge)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s + Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name.ifBlank { "(品名なし)" },
                    style = AppTheme.type.title,
                    color = if (item.name.isBlank()) c.inkFaint else c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(sub, style = AppTheme.type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (marks.isNotEmpty()) EditedMark(Modifier.padding(top = Space.xs))
            }
            Spacer(Modifier.width(Space.s))
            Text(LedgerLogic.formatYen(item.amount.toLong()), style = AppTheme.type.numeral, color = c.ink)
        }
        Hairline()
    }
}

@Composable
private fun SingleBody(e: Expense, onEdit: () -> Unit) {
    val c = AppTheme.colors
    val marks = PaymentMarks.ofExpense(e)
    Column(Modifier.fillMaxWidth().tappable(onEdit).padding(horizontal = Space.edge, vertical = Space.m)) {
        Label("支出")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                LedgerLogic.dayLabel(e.date) + if (e.time.isNotEmpty()) " ${e.time}" else "",
                style = AppTheme.type.body,
                color = c.inkSoft,
            )
            if (PaymentField.TIME in marks) EditedMark(Modifier.padding(start = Space.s))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(LedgerLogic.formatYen(e.amount.toLong()), style = AppTheme.type.numeralXL, color = c.ink)
            if (PaymentField.AMOUNT in marks) EditedMark(Modifier.padding(start = Space.s, bottom = Space.s))
        }
        if (e.name.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, style = AppTheme.type.title, color = c.ink)
                if (PaymentField.NAME in marks) EditedMark(Modifier.padding(start = Space.s))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                e.memo.ifBlank { "(メモなし)" },
                style = AppTheme.type.body,
                color = if (e.memo.isBlank()) c.inkFaint else c.ink,
            )
            if (PaymentField.MEMO in marks) EditedMark(Modifier.padding(start = Space.s))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("カテゴリ ${e.category}", style = AppTheme.type.caption, color = c.inkSoft)
            if (PaymentField.CATEGORY in marks) EditedMark(Modifier.padding(start = Space.s))
        }
        Text("タップで直す", style = AppTheme.type.caption, color = c.inkFaint)
    }
    Hairline()
}

/** レシート由来のとき: サムネイルと「レシートを開く」。 */
@Composable
private fun ReceiptBlock(imagePath: String?, receiptId: Long?, onOpenReceipt: (Long) -> Unit) {
    if (receiptId == null) return
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .tappable { onOpenReceipt(receiptId) }
            .padding(horizontal = Space.edge, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ReceiptThumb(imagePath)
        Column(Modifier.weight(1f)) {
            Label("レシート")
            Text("レシートを開く  ›", style = AppTheme.type.title, color = c.accent)
        }
    }
    Hairline()
}

@Composable
private fun ReceiptThumb(imagePath: String?) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imagePath) {
        value = if (imagePath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    ReceiptImageCodec.decodeScaled(File(context.filesDir, imagePath), 256)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    val bmp = bitmap
    val shape = Modifier.size(width = 64.dp, height = 84.dp)
    if (bmp == null) {
        Box(shape.background(AppTheme.colors.paperRaised), contentAlignment = Alignment.Center) { Label("画像なし") }
    } else {
        Image(
            bitmap = bmp,
            contentDescription = "レシート画像のサムネイル",
            contentScale = ContentScale.Crop,
            modifier = shape,
        )
    }
}
