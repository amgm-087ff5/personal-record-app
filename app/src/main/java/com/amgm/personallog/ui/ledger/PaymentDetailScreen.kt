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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.domain.PaymentField
import com.amgm.personallog.domain.PaymentWriteBack
import com.amgm.personallog.domain.split.IndividualExpense
import com.amgm.personallog.receipt.ReceiptDedup
import com.amgm.personallog.receipt.ReceiptImageCodec
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.LeaderRow
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.StampTone
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable
import com.amgm.personallog.ui.split.unsetNoteText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 支出の詳細画面。家計簿の支出(まとまり、または単独の支出)のうち、自分の分だけを見て直す。
 * 支払い由来なら、日時・金額・品目は支払いへ書き戻す。支払い先・レシート・割り勘は「支払いを開く」から支払い詳細で見る。
 */
@Composable
fun PaymentDetailScreen(
    onBack: () -> Unit,
    onOpenReceipt: (Long) -> Unit,
    /** 割り勘シートを開く(支払いの id)。支払いに紐づく支出のときだけボタンを出す。 */
    onOpenSplit: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PaymentDetailViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    // 編集中の状態は、画面の回転やプロセスの再生成でも失われないよう rememberSaveable で持つ
    // (Expense は保存できないので id で持ち、表示のたびに最新の品目から引く)
    var editingGroup by rememberSaveable { mutableStateOf(false) }
    var editingItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addingItem by rememberSaveable { mutableStateOf(false) }
    var deletingGroup by rememberSaveable { mutableStateOf(false) }
    var editingStore by rememberSaveable { mutableStateOf(false) }

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
            val scanned = PaymentMarks.isScannedReceipt(s.receipt)
            s.duplicateOf?.let { original ->
                item(key = "duplicate") {
                    DuplicateNotice(original, onOpenPayment = { s.receipt?.id?.let(onOpenReceipt) })
                }
            }
            if (group != null) {
                item(key = "head") {
                    GroupHeader(group, scanned, onEdit = { editingGroup = true }, onRevert = { viewModel.revertGroupField(group, it) })
                }
                if (s.splitPeople >= 2) {
                    item(key = "individual") { IndividualBlock(s.individual) }
                }
                item(key = "payment") { OpenPaymentRow(s.receipt?.id, onOpenReceipt) }
                item(key = "items-title") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Space.edge).padding(top = Space.l)) {
                        Label("品目")
                        Spacer(Modifier.height(Space.xs))
                        Hairline(color = c.ink)
                    }
                }
                items(s.items.size, key = { "item-" + s.items[it].id }) { i ->
                    ItemRow(s.items[i], scanned, onClick = { editingItemId = s.items[i].id }, onRevert = { viewModel.revertItemField(s.items[i], it) })
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
                                "差額 ${LedgerLogic.formatYen(s.difference)}(支出金額 ${LedgerLogic.formatYen(group.amount.toLong())} − 品目の合計)。" +
                                    "カテゴリ別の集計では、差額は支出のカテゴリ「${group.category}」に入ります。",
                                style = AppTheme.type.caption,
                                color = c.danger,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                            // 印字のあるレシートや割り勘の金額は、ここから合計に入れられない
                            val canTakeSum = s.receipt?.let { !PaymentWriteBack.isPrinted(it) && s.splitPeople < 2 } ?: true
                            if (s.itemSum > 0 && canTakeSum) {
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
                if (s.deletedItems.isNotEmpty()) {
                    item(key = "deleted-title") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = Space.edge).padding(top = Space.l)) {
                            Label("消した品目 ${s.deletedItems.size}件")
                            Spacer(Modifier.height(Space.xs))
                            Hairline(color = c.ink)
                            Text(DELETED_ITEMS_NOTE, style = AppTheme.type.caption, color = c.inkFaint, modifier = Modifier.padding(top = Space.xs))
                        }
                    }
                    items(s.deletedItems.size, key = { "deleted-" + s.deletedItems[it].id }) { i ->
                        DeletedItemRow(s.deletedItems[i], onRestore = { viewModel.restoreItem(s.deletedItems[i]) })
                    }
                }
                item(key = "delete") {
                    TextAction(
                        "この支出を削除",
                        onClick = { deletingGroup = true },
                        color = c.danger,
                        modifier = Modifier.padding(horizontal = Space.s, vertical = Space.l),
                    )
                }
            } else if (s.selfExcluded) {
                // 割り勘で自分が外れた支払い。家計簿には自分の行が無く、支払いと割り勘だけを見せる
                s.receipt?.let { r ->
                    item(key = "excluded-note") {
                        Text(
                            "この支払いに自分は含まれていません",
                            style = AppTheme.type.body,
                            color = c.ink,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.edge).padding(top = Space.l),
                        )
                    }
                    item(key = "store") { StoreRow(r.storeName, onEdit = { editingStore = true }) }
                    item(key = "split") { SplitRow(s.splitPeople, r.id, onOpenSplit) }
                    item(key = "receipt") {
                        ReceiptBlock(r.imagePath, r.id, onOpenReceipt)
                    }
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
            fromScannedReceipt = PaymentMarks.isScannedReceipt(s.receipt),
            onDismiss = { editingGroup = false },
            onSave = { date, time, amount, category, _, memo, done ->
                viewModel.saveGroup(group, date, time, amount, category, memo, done)
            },
            onDelete = null,
            title = "支出を編集",
            showName = false,
            // 割り勘が2人以上なら金額は読み取り専用のまま(注記の文言は出さない。直し方は支払い詳細で案内する)
            lockedAmountNote = if (s.splitPeople >= 2) "" else null,
        )
    }
    // 品目の編集(横スワイプで前後の品目へ移れるドロワー)。追加のときは末尾に空の品目を足して開く
    if (group != null && (editingItemId != null || addingItem)) {
        ItemEditorSheet(
            group = group,
            items = s.items,
            startItemId = editingItemId,
            adding = addingItem,
            splitPeople = s.splitPeople,
            onlyOneItem = s.onlyOneItem,
            onDismiss = {
                editingItemId = null
                addingItem = false
            },
            onSaveItem = { original, amount, category, name, memo, done ->
                viewModel.saveItem(group, original, group.date, group.time, amount, category, name, memo, done)
            },
            onSetUnknown = { expense, unknown, done -> viewModel.setItemUnknown(expense, unknown, done) },
            onDeleteItem = { expense -> viewModel.deleteItem(expense) },
        )
    }
    if (deletingGroup && group != null) {
        ConfirmSheet(
            title = "支出を削除",
            message = "「${group.memo.ifBlank { group.category }}」の支出と品目をすべて削除します。支払いは残ります。",
            confirmText = "削除",
            onDismiss = { deletingGroup = false },
            onConfirm = {
                deletingGroup = false
                viewModel.deleteGroup()
            },
        )
    }
    val receipt = s.receipt
    if (editingStore && receipt != null) {
        StoreSheet(
            current = receipt.storeName,
            onDismiss = { editingStore = false },
            onSave = { name, done -> viewModel.saveStore(receipt.id, name, done) },
        )
    }
}

/** 支払いを開く行(支払い詳細へ)。支払い先・レシート画像・割り勘は支払い詳細で見る。支払いが無ければ出さない。 */
@Composable
private fun OpenPaymentRow(receiptId: Long?, onOpenPayment: (Long) -> Unit) {
    if (receiptId == null) return
    val c = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .tappable { onOpenPayment(receiptId) }
            .padding(horizontal = Space.edge, vertical = Space.m),
    ) {
        Label("支払い")
        Text("支払いを開く  ›", style = AppTheme.type.title, color = c.accent)
    }
    Hairline()
}

/**
 * 支払いの重複の候補があるときの注意。支払い一覧の「重複?」と同じ判定(後から作られた側にだけ出す)。
 * 確認・まとめる・「重複ではない」は支払い詳細で行う。
 */
@Composable
private fun DuplicateNotice(original: Receipt, onOpenPayment: () -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .tappable(onOpenPayment)
            .padding(horizontal = Space.edge, vertical = Space.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stamp("重複?", tone = StampTone.Danger)
            Text(
                "  元の支払いが重複している可能性があります",
                style = AppTheme.type.caption,
                color = c.danger,
            )
        }
        Text(
            "相手: ${ReceiptDedup.summary(original)}",
            style = AppTheme.type.caption,
            color = c.inkSoft,
            modifier = Modifier.padding(top = Space.xs),
        )
        Text("支払いを開いて確認する  ›", style = AppTheme.type.caption, color = c.accent)
    }
    Hairline()
}

/** 支払い先(支払いの店名)。タップで直す。支払いへ書き戻す。 */
@Composable
private fun StoreRow(store: String, onEdit: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().tappable(onEdit).padding(horizontal = Space.edge, vertical = Space.s)) {
        Label("支払い先")
        Text(
            store.ifBlank { "(未入力)" },
            style = AppTheme.type.body,
            color = if (store.isBlank()) c.inkFaint else c.ink,
        )
    }
    Hairline()
}

/**
 * 割り勘の入口。参加者が2人以上なら、金額はこの画面では直せないので、割り勘画面へ案内する。
 * 支払いが無い(支払いに紐づかない)支出では出さない。
 */
@Composable
private fun SplitRow(people: Int, receiptId: Long, onOpenSplit: (Long) -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.edge, vertical = Space.xs)) {
        if (people >= 2) {
            Text(
                "負担割りがあるため、金額はここでは直せません。金額は全体支出で直してください。",
                style = AppTheme.type.caption,
                color = c.inkSoft,
            )
        }
        TextAction(
            if (people >= 2) "全体支出を開く" else "負担割り",
            onClick = { onOpenSplit(receiptId) },
        )
    }
}

/** 割り勘があるときの、自分の個別支出。まとまりごとの割合・食べた分・負担と、差(多めに払ったか・払ってもらったか)。 */
@Composable
private fun IndividualBlock(individual: IndividualExpense?) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.edge, vertical = Space.s)) {
        Label("自分の個別支出")
        if (individual == null) {
            Text("計算できません", style = AppTheme.type.caption, color = c.danger, modifier = Modifier.padding(top = Space.xs))
            return@Column
        }
        if (individual.lines.isEmpty()) {
            Text("この支払いでは自分の負担はありません。", style = AppTheme.type.caption, color = c.inkSoft, modifier = Modifier.padding(top = Space.xs))
        }
        individual.lines.forEach { line ->
            Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(line.name, style = AppTheme.type.body, color = c.ink)
                    val shareText = if (line.groupId == null) "残差・調整(参加者で均等)" else "割合 ${line.share}"
                    Text(shareText, style = AppTheme.type.caption, color = c.inkSoft)
                    if (line.unset) {
                        Text(unsetNoteText(line.isShared), style = AppTheme.type.caption, color = c.inkFaint)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(LedgerLogic.formatYen(line.eaten.toLong()), style = AppTheme.type.numeral, color = c.ink)
                    if (line.burden != line.eaten) {
                        Text("負担 ${LedgerLogic.formatYen(line.burden.toLong())}", style = AppTheme.type.caption, color = c.inkSoft)
                    }
                }
            }
        }
        Hairline(dashed = true, modifier = Modifier.padding(top = Space.s))
        LeaderRow(
            label = "食べた分",
            value = LedgerLogic.formatYen(individual.eatenTotal.toLong()),
            labelStyle = AppTheme.type.caption,
            valueStyle = AppTheme.type.caption,
            labelColor = c.inkSoft,
            valueColor = c.inkSoft,
            modifier = Modifier.padding(top = Space.xs),
        )
        LeaderRow(
            label = "負担",
            value = LedgerLogic.formatYen(individual.burdenTotal.toLong()),
            modifier = Modifier.padding(top = Space.xs),
        )
        Text(
            individualDifferenceText(individual.difference),
            style = AppTheme.type.caption,
            color = c.ink,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/** 差の文言。差 = 負担 − 食べた分。マイナスは「多めに払ってもらった」、プラスは「多めに払った」。 */
private fun individualDifferenceText(difference: Int): String = when {
    difference < 0 -> "多めに払ってもらった ${LedgerLogic.formatYen(-difference.toLong())}"
    difference > 0 -> "多めに払った ${LedgerLogic.formatYen(difference.toLong())}"
    else -> "食べた分どおり(差はありません)"
}

/** 支払い先を直すシート。空にはできない(ViewModel が拒否する)。 */
@Composable
private fun StoreSheet(current: String, onDismiss: () -> Unit, onSave: (String, (String?) -> Unit) -> Unit) {
    val c = AppTheme.colors
    var text by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    InkSheet(onDismiss = { if (!saving) onDismiss() }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Label("支払い先")
            BareField(
                value = text,
                onValueChange = { text = it },
                underline = true,
                singleLine = true,
                placeholder = "支払い先",
            )
            error?.let { Text(it, style = AppTheme.type.caption, color = c.danger) }
            InkButton(
                text = "保存",
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    saving = true
                    onSave(text) { err ->
                        saving = false
                        if (err == null) onDismiss() else error = err
                    }
                },
            )
        }
    }
}

/** 「レシートと違う」の小さな印。onRevert があれば、その欄を「支払いの値に戻す」操作を添える。 */
@Composable
private fun EditedMark(modifier: Modifier = Modifier, text: String = "レシートと違う", onRevert: (() -> Unit)? = null) {
    if (onRevert == null) {
        Stamp(text, tone = StampTone.Accent, modifier = modifier)
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Stamp(text, tone = StampTone.Accent)
            TextAction("支払いの値に戻す", onClick = onRevert)
        }
    }
}

private fun fieldLabel(f: PaymentField): String = when (f) {
    PaymentField.AMOUNT -> "金額"
    PaymentField.CATEGORY -> "カテゴリ"
    PaymentField.NAME -> "品名"
    PaymentField.MEMO -> "メモ"
    PaymentField.TIME -> "日時"
}

@Composable
private fun GroupHeader(group: ExpenseGroup, scanned: Boolean, onEdit: () -> Unit, onRevert: (PaymentField) -> Unit) {
    val c = AppTheme.colors
    val marks = PaymentMarks.ofGroup(group, scanned)
    Column(
        Modifier.fillMaxWidth().tappable(onEdit).padding(horizontal = Space.edge, vertical = Space.m),
    ) {
        Label("支出")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                LedgerLogic.dayLabel(group.date) + if (group.time.isNotEmpty()) " ${group.time}" else "",
                style = AppTheme.type.body,
                color = c.inkSoft,
            )
            if (PaymentField.TIME in marks) EditedMark(Modifier.padding(start = Space.s), onRevert = { onRevert(PaymentField.TIME) })
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(LedgerLogic.formatYen(group.amount.toLong()), style = AppTheme.type.numeralXL, color = c.ink)
            if (PaymentField.AMOUNT in marks) EditedMark(Modifier.padding(start = Space.s, bottom = Space.s), onRevert = { onRevert(PaymentField.AMOUNT) })
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
            if (PaymentField.MEMO in marks) EditedMark(Modifier.padding(start = Space.s), onRevert = { onRevert(PaymentField.MEMO) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("カテゴリ ${group.category}", style = AppTheme.type.caption, color = c.inkSoft)
            if (PaymentField.CATEGORY in marks) EditedMark(Modifier.padding(start = Space.s), onRevert = { onRevert(PaymentField.CATEGORY) })
        }
        Text("タップで直す", style = AppTheme.type.caption, color = c.inkFaint)
    }
    Hairline()
}

@Composable
private fun ItemRow(item: Expense, scanned: Boolean, onClick: () -> Unit, onRevert: (PaymentField) -> Unit) {
    val c = AppTheme.colors
    val marks = PaymentMarks.ofExpense(item, scanned)
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
                // 日時はグループに従うので、品目の行には出さない(グループの欄で戻す)
                marks.filter { it != PaymentField.TIME }.forEach { f ->
                    EditedMark(Modifier.padding(top = Space.xs), text = "${fieldLabel(f)}がレシートと違う", onRevert = { onRevert(f) })
                }
            }
            Spacer(Modifier.width(Space.s))
            Text(LedgerLogic.formatYen(item.amount.toLong()), style = AppTheme.type.numeral, color = c.ink)
        }
        Hairline()
    }
}

/** 家計簿で消した品目の行。「元に戻す」で品目に戻る。 */
@Composable
private fun DeletedItemRow(item: Expense, onRestore: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.edge)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.name.ifBlank { "(品名なし)" },
                style = AppTheme.type.body,
                color = c.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.s))
            Text(LedgerLogic.formatYen(item.amount.toLong()), style = AppTheme.type.numeral, color = c.inkSoft)
            TextAction("元に戻す", onClick = onRestore)
        }
        Hairline()
    }
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
