@file:OptIn(ExperimentalMaterial3Api::class)

package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amgm.personallog.backup.ImportMode
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.LeaderRow
import com.amgm.personallog.ui.design.Pills
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.StampTone
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset


internal fun entryKey(entry: LedgerEntry): String = when (entry) {
    is LedgerEntry.Single -> "exp-" + entry.expense.id
    is LedgerEntry.Group -> "grp-" + entry.group.id
}

/** カテゴリの色。定義済みカテゴリは順番で固定し、それ以外は名前から決まる色にする。未分類は inkFaint。 */
internal fun categoryColor(category: String, series: List<Color>, uncategorized: Color): Color {
    if (category == Categories.UNCATEGORIZED) return uncategorized
    val fixed = Categories.DEFAULTS.indexOf(category)
    val index = if (fixed >= 0) fixed else (category.hashCode() and Int.MAX_VALUE)
    return series[index % series.size]
}

private const val WEEKDAYS = "月火水木金土日"

// ------------------------------------------------------------ 内訳

/** ●色 名前 ······ 金額  割合 を縦に並べた凡例。 */
@Composable
internal fun CategoryLegend(shares: List<CategoryShare>, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        shares.forEach { share ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(categoryColor(share.category, c.series, c.inkFaint)),
                )
                Spacer(Modifier.width(Space.s))
                LeaderRow(
                    label = share.category,
                    value = LedgerLogic.formatYen(share.total),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${share.percent}%",
                    style = AppTheme.type.caption,
                    color = c.inkSoft,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(Space.xl),
                )
            }
        }
    }
}


// ------------------------------------------------------------ グラフ

@Composable
internal fun StackedChart(
    level: ChartLevel,
    label: String,
    bars: List<ChartBar>,
    legend: List<CategoryShare>,
    onLevel: (ChartLevel) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBarTap: (ChartBar) -> Unit,
) {
    val c = AppTheme.colors
    val series = c.series
    val faint = c.inkFaint
    val baseline = c.line
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextAction("‹", onClick = onPrev, color = c.ink)
            Text(
                label,
                style = AppTheme.type.title,
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextAction("›", onClick = onNext, color = c.ink)
        }

        val max = bars.maxOfOrNull { it.drawnTotal }?.coerceAtLeast(1L) ?: 1L
        if (bars.isNotEmpty()) {
            Spacer(Modifier.height(Space.s))
            val listState = rememberLazyListState()
            LaunchedEffect(level, label) { listState.scrollToItem(0) }
            // 1本ずつ横スクロールし、1本単位でスナップして止まる。時間別(24本)は細く、金額は省く。
            val dense = level == ChartLevel.DAY
            val itemWidth = if (dense) 20.dp else 64.dp
            val tiny = AppTheme.type.label.copy(letterSpacing = 0.sp)
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(bars) { i, bar ->
                    Column(
                        Modifier
                            .width(itemWidth)
                            .then(if (bar.drillable) Modifier.tappable { onBarTap(bar) } else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!dense) {
                            Text(
                                LedgerLogic.compactYen(bar.total),
                                style = tiny,
                                color = c.inkSoft,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                            val barWidth = size.width * 0.6f
                            val x = (size.width - barWidth) / 2f
                            var bottom = size.height
                            bar.segments.forEach { seg ->
                                val h = size.height * (seg.amount.toFloat() / max)
                                if (h > 0f) {
                                    drawRect(
                                        color = categoryColor(seg.category, series, faint),
                                        topLeft = Offset(x, bottom - h),
                                        size = Size(barWidth, h),
                                    )
                                    bottom -= h
                                }
                            }
                            drawLine(
                                color = baseline,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1f,
                            )
                        }
                        // 「9/28〜10/4」は「〜」で2行に分ける。時間別は3時間おきだけ表示する。
                        val twoLines = bar.label.contains("〜")
                        val text = when {
                            dense && i % 3 != 0 -> ""
                            twoLines -> bar.label.replace("〜", "〜\n")
                            else -> bar.label
                        }
                        Text(
                            text,
                            style = tiny,
                            color = c.inkFaint,
                            textAlign = TextAlign.Center,
                            maxLines = if (twoLines) 2 else 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
                if (dense) {
                    // 時間別の終わりを示す 24: (タップ不可)
                    item {
                        Column(Modifier.width(itemWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(160.dp))
                            Text(
                                "24:",
                                style = tiny,
                                color = c.inkFaint,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                "表示するデータがありません",
                style = AppTheme.type.body,
                color = c.inkSoft,
                modifier = Modifier.padding(vertical = Space.s),
            )
        }

        if (legend.isNotEmpty()) {
            Spacer(Modifier.height(Space.m))
            CategoryLegend(legend)
            Spacer(Modifier.height(Space.s))
            Text(ALLOCATION_NOTE, style = AppTheme.type.caption, color = c.inkFaint)
        }

        Spacer(Modifier.height(Space.m))
        LevelTabs(level = level, onLevel = onLevel)
    }
}

/** 年/月/週/日の文字タブ。選択中は ink + 朱の下線。 */
@Composable
private fun LevelTabs(level: ChartLevel, onLevel: (ChartLevel) -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        ChartLevel.values().forEach { lv ->
            val selected = lv == level
            Column(
                Modifier
                    .tappable { onLevel(lv) }
                    .padding(horizontal = Space.m, vertical = Space.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(lv.label, style = AppTheme.type.title, color = if (selected) c.ink else c.inkFaint)
                Spacer(Modifier.height(Space.xs))
                Box(
                    Modifier
                        .width(Space.l)
                        .height(2.dp)
                        .background(if (selected) c.accent else Color.Transparent),
                )
            }
        }
    }
}

// ------------------------------------------------------------ 日ごとの帳面

@Composable
internal fun DayBlock(
    day: DayGroup,
    expandedGroups: Map<Long, Boolean>,
    /** 下書きのレシートの id(行に「下書き」を付ける)。 */
    draftReceiptIds: Set<Long>,
    /** 割り勘に警告があるレシートの id(行に「要確認」を付ける)。 */
    warnReceiptIds: Set<Long>,
    /** 画像のあるレシート(レシート由来の支払い)の id。行の「レシート由来」は、ここに含まれる支払いの行だけに付ける。 */
    imageReceiptIds: Set<Long>,
    onToggleGroup: (Long) -> Unit,
    /** 行をタップ: 支出の詳細画面を開く。 */
    onOpen: (LedgerEntry) -> Unit,
    onDeleteGroup: (LedgerEntry.Group) -> Unit,
) {
    val c = AppTheme.colors
    val date = try {
        LocalDate.parse(day.date)
    } catch (e: Exception) {
        null
    }
    Column(Modifier.fillMaxWidth()) {
        Hairline(color = c.ink)
        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier.width(Space.xl + Space.m).padding(top = Space.s, end = Space.s),
            ) {
                Text(date?.dayOfMonth?.toString() ?: day.date, style = AppTheme.type.numeralL, color = c.ink)
                if (date != null) {
                    val dow = date.dayOfWeek.value
                    Label(
                        WEEKDAYS[dow - 1] + "曜",
                        color = if (dow == 7) c.accent else c.inkFaint,
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    LedgerLogic.formatYen(day.total),
                    style = AppTheme.type.caption,
                    color = c.inkSoft,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Column(Modifier.weight(1f)) {
                day.items.forEachIndexed { index, entry ->
                    if (index > 0) Hairline()
                    when (entry) {
                        is LedgerEntry.Single -> ExpenseLine(
                            entry.expense,
                            onClick = { onOpen(entry) },
                            showBadges = true,
                            draftReceiptIds = draftReceiptIds,
                            warnReceiptIds = warnReceiptIds,
                            imageReceiptIds = imageReceiptIds,
                        )
                        is LedgerEntry.Group -> GroupLine(
                            entry = entry,
                            draftReceiptIds = draftReceiptIds,
                            warnReceiptIds = warnReceiptIds,
                            imageReceiptIds = imageReceiptIds,
                            expanded = expandedGroups[entry.group.id] == true,
                            onToggle = { onToggleGroup(entry.group.id) },
                            onOpen = { onOpen(entry) },
                            onDelete = { onDeleteGroup(entry) },
                        )
                    }
                }
            }
        }
    }
}

/** 行の印: レシートが下書きなら「下書き」、割り勘に警告があれば「要確認」。手入力の行には付けない。 */
@Composable
private fun RowBadges(receiptId: Long?, draftReceiptIds: Set<Long>, warnReceiptIds: Set<Long>) {
    if (receiptId == null) return
    if (receiptId in draftReceiptIds) Stamp("下書き", modifier = Modifier.padding(start = Space.xs))
    if (receiptId in warnReceiptIds) Stamp("要確認", modifier = Modifier.padding(start = Space.xs), tone = StampTone.Danger)
}

@Composable
private fun ExpenseLine(
    expense: Expense,
    onClick: () -> Unit,
    /** 行の印(下書き・要確認)を出すか。グループの中の品目には出さない。 */
    showBadges: Boolean = false,
    draftReceiptIds: Set<Long> = emptySet(),
    warnReceiptIds: Set<Long> = emptySet(),
    /** 画像のあるレシートの id。「レシート由来」の印は、ここに含まれる支払いの行にだけ付ける(手入力の支払いには付けない)。 */
    imageReceiptIds: Set<Long> = emptySet(),
) {
    val c = AppTheme.colors
    val title = listOf(expense.name, expense.memo).filter { it.isNotBlank() }.joinToString(" ").ifBlank { expense.category }
    val sub = listOfNotNull(
        expense.time.ifEmpty { null },
        if (expense.name.isNotBlank() || expense.memo.isNotBlank()) expense.category else null,
        if (expense.receiptId != null && expense.receiptId in imageReceiptIds) "レシート由来" else null,
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().tappable(onClick).padding(vertical = Space.s + Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = AppTheme.type.title,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showBadges) RowBadges(expense.receiptId, draftReceiptIds, warnReceiptIds)
            }
            if (sub.isNotEmpty()) {
                Text(sub, style = AppTheme.type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Space.s))
        Text(LedgerLogic.formatYen(expense.amount.toLong()), style = AppTheme.type.numeral, color = c.ink)
    }
}

@Composable
private fun GroupLine(
    entry: LedgerEntry.Group,
    draftReceiptIds: Set<Long>,
    warnReceiptIds: Set<Long>,
    imageReceiptIds: Set<Long>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = AppTheme.colors
    val sub = listOfNotNull(
        entry.time.ifEmpty { null },
        if (entry.group.memo.isNotBlank()) entry.category else null,
        "${entry.items.size}品",
    ).joinToString(" · ")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().tappable(onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 開閉は矢印だけ。行のほかの場所をタップすると支出の詳細画面を開く
            Text(
                if (expanded) "▾" else "▸",
                style = AppTheme.type.title,
                color = c.accent,
                modifier = Modifier.tappable(onToggle).padding(end = Space.s, top = Space.s + Space.xs, bottom = Space.s + Space.xs),
            )
            Column(Modifier.weight(1f).padding(vertical = Space.s + Space.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.group.memo.ifBlank { entry.category },
                        style = AppTheme.type.title,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    RowBadges(entry.receiptId, draftReceiptIds, warnReceiptIds)
                }
                Text(sub, style = AppTheme.type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.s))
            Text(LedgerLogic.formatYen(entry.amount), style = AppTheme.type.numeral, color = c.ink)
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = Space.m, bottom = Space.s)) {
                entry.items.forEach { item ->
                    Hairline()
                    ExpenseLine(item, onClick = onOpen, imageReceiptIds = imageReceiptIds)
                }
                Hairline(dashed = true)
                Spacer(Modifier.height(Space.s))
                LeaderRow(
                    label = "品目の合計",
                    value = LedgerLogic.formatYen(entry.itemSum),
                    labelStyle = AppTheme.type.caption,
                    valueStyle = AppTheme.type.caption,
                    labelColor = c.inkSoft,
                    valueColor = c.inkSoft,
                )
                if (entry.difference != 0L) {
                    Text(
                        "差額 ${LedgerLogic.formatYen(entry.difference)}(品目の合計と支出金額が一致しません)",
                        style = AppTheme.type.caption,
                        color = c.danger,
                    )
                }
                TextAction("この支出を削除", onClick = onDelete, color = c.danger)
            }
        }
    }
}

// ------------------------------------------------------------ シート

/** 確認用の小さなシート(AlertDialog の代わり)。 */
@Composable
internal fun ConfirmSheet(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "キャンセル",
    danger: Boolean = true,
) {
    val c = AppTheme.colors
    InkSheet(onDismiss = onDismiss) {
        Text(title, style = AppTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Space.s))
        Text(message, style = AppTheme.type.body, color = c.inkSoft)
        Spacer(Modifier.height(Space.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextAction(dismissText, onClick = onDismiss, color = c.inkSoft)
            TextAction(confirmText, onClick = onConfirm, color = if (danger) c.danger else c.accent)
        }
    }
}

/** 支出の追加・編集シート。 */
@Composable
internal fun ExpenseEditorSheet(
    original: Expense?,
    initialDate: String,
    initialTime: String,
    /** 紐づくレシートの日時 "yyyy-MM-dd HH:mm"。紐づかなければ null。 */
    linkedReceiptAt: String?,
    onDismiss: () -> Unit,
    onSave: (date: String, time: String, amount: Int, category: String, name: String, memo: String, done: (String?) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
    /** 見出し。null なら「支出を追加/編集」。 */
    title: String? = null,
    /** 品名の欄を出すか(支払い=グループの編集では出さない)。 */
    showName: Boolean = true,
    /** 日時の欄を出すか。支払いグループの品目の日時はグループに従うので、品目の編集では出さない。 */
    showDateTime: Boolean = true,
) {
    val c = AppTheme.colors
    var dateText by remember { mutableStateOf(initialDate) }
    var timeText by remember { mutableStateOf(initialTime) }
    var pendingSave by remember { mutableStateOf<ExpenseValidation?>(null) }
    var amountText by remember { mutableStateOf(original?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(original?.category) }
    var memo by remember { mutableStateOf(original?.memo ?: "") }
    var name by remember { mutableStateOf(original?.name ?: "") }
    var validation by remember { mutableStateOf<ExpenseValidation?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val categories = remember(original) {
        val base = Categories.DEFAULTS.filter { it != Categories.UNCATEGORIZED }
        val extra = original?.category
        if (extra != null && extra !in base) base + extra else base
    }

    fun doSave(v: ExpenseValidation) {
        saving = true
        onSave(v.date!!, v.time!!, v.amount!!, v.category!!, name, memo) { err ->
            saving = false
            if (err == null) onDismiss() else saveError = err
        }
    }

    InkSheet(onDismiss = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Label(title ?: if (original == null) "支出を追加" else "支出を編集")
            if (original?.receiptId != null) {
                Text(
                    "レシート由来です" + (linkedReceiptAt?.let { "(レシート日時 $it)" } ?: ""),
                    style = AppTheme.type.caption,
                    color = c.accent,
                )
            }

            // 金額: 大きく中央
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Label("金額(円)")
                BareField(
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it in '0'..'9' }.take(9) },
                    textStyle = AppTheme.type.numeralXL.copy(textAlign = TextAlign.Center),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Hairline(color = c.ink)
            }

            // カテゴリ
            Label("カテゴリ")
            Pills(
                options = categories,
                selected = category,
                onSelect = { category = it },
                label = { it },
            )

            // 日時: タップできる文字
            Label("日時")
            if (showDateTime) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextAction(dateChip(dateText), onClick = { showDatePicker = true }, color = c.ink)
                    TextAction(timeText.ifBlank { "時刻を選ぶ" }, onClick = { showTimePicker = true }, color = c.ink)
                    Text("タップで変更", style = AppTheme.type.caption, color = c.inkFaint)
                }
            } else {
                Text(
                    "${dateChip(dateText)} ${timeText}(支出の日時に従います。直すときは支出の日時を直してください)",
                    style = AppTheme.type.caption,
                    color = c.inkFaint,
                )
            }

            if (showName) {
                Label("品名")
                BareField(
                    value = name,
                    onValueChange = { name = it },
                    underline = true,
                    placeholder = "品名",
                )
            }

            // メモ
            Label("メモ")
            BareField(
                value = memo,
                onValueChange = { memo = it },
                underline = true,
                placeholder = "メモ",
            )

            val errors = listOfNotNull(
                validation?.dateError,
                validation?.timeError,
                validation?.amountError,
                validation?.categoryError,
                saveError,
            )
            errors.forEach { Text(it, style = AppTheme.type.caption, color = c.danger) }

            InkButton(
                text = "保存",
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val v = LedgerLogic.validate(dateText, timeText, amountText, category)
                    validation = v
                    saveError = null
                    if (v.isValid) {
                        // 紐づくレシートと日時が違うまま日時を変えて保存しようとしたら、確認を挟む(レシートは変更しない)
                        val dateTimeChanged = original != null && (v.date != original.date || v.time != original.time)
                        val mismatch = linkedReceiptAt != null && "${v.date} ${v.time}" != linkedReceiptAt
                        if (dateTimeChanged && mismatch) pendingSave = v else doSave(v)
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (onDelete != null) {
                    TextAction("削除", enabled = !saving, onClick = { confirmDelete = true }, color = c.danger)
                } else {
                    Spacer(Modifier.width(Space.xs))
                }
                TextAction("キャンセル", enabled = !saving, onClick = onDismiss, color = c.inkSoft)
            }
        }
    }

    pendingSave?.let { v ->
        ConfirmSheet(
            title = "レシートの日時と異なります",
            message = "この支出はレシート(${linkedReceiptAt ?: ""})に紐づいています。" +
                "入力した日時(${v.date} ${v.time})はレシートと異なります。" +
                "レシートの日時は変更されません。このまま保存しますか？",
            confirmText = "このまま保存",
            dismissText = "戻る",
            danger = false,
            onDismiss = { pendingSave = null },
            onConfirm = {
                pendingSave = null
                doSave(v)
            },
        )
    }

    if (showDatePicker) {
        val initialMillis = try {
            LocalDate.parse(dateText.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextAction("決定", onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        dateText = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                })
            },
            dismissButton = { TextAction("キャンセル", onClick = { showDatePicker = false }, color = c.inkSoft) },
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val parts = timeText.split(":")
        val h0 = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12
        val m0 = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timeState = rememberTimePickerState(initialHour = h0, initialMinute = m0, is24Hour = true)
        InkSheet(onDismiss = { showTimePicker = false }) {
            Label("時刻")
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = timeState)
            }
            InkButton(
                text = "決定",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    timeText = timeState.hour.toString().padStart(2, '0') + ":" +
                        timeState.minute.toString().padStart(2, '0')
                    showTimePicker = false
                },
            )
        }
    }

    if (confirmDelete && onDelete != null) {
        ConfirmSheet(
            title = "削除しますか?",
            message = "この支出を削除します。元に戻せません。",
            confirmText = "削除",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

private fun dateChip(dateText: String): String = try {
    val d = LocalDate.parse(dateText.trim())
    "${d.monthValue}月${d.dayOfMonth}日"
} catch (e: Exception) {
    dateText.ifBlank { "日付を選ぶ" }
}

// ------------------------------------------------------------ バックアップ

@Composable
internal fun BackupSheets(
    state: BackupUiState,
    onPickMode: (ImportMode) -> Unit,
    onCancelImport: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val c = AppTheme.colors
    if (state.pendingImport != null) {
        var mode by remember { mutableStateOf(ImportMode.MERGE) }
        InkSheet(onDismiss = onCancelImport) {
            Text("バックアップを取り込む", style = AppTheme.type.title, color = c.ink)
            Spacer(Modifier.height(Space.s))
            Text("既存のデータの扱いを選んでください。", style = AppTheme.type.body, color = c.inkSoft)
            Spacer(Modifier.height(Space.s))
            ModeChoice("追加(重複はスキップ)", "今あるデータは残し、無いものだけ追加します", mode == ImportMode.MERGE) { mode = ImportMode.MERGE }
            Hairline()
            ModeChoice("すべて置き換え", "今ある日記・支払い・家計簿を全て削除してから取り込みます", mode == ImportMode.REPLACE) { mode = ImportMode.REPLACE }
            Spacer(Modifier.height(Space.m))
            InkButton("取り込む", onClick = { onPickMode(mode) }, modifier = Modifier.fillMaxWidth())
            TextAction("キャンセル", onClick = onCancelImport, color = c.inkSoft, modifier = Modifier.fillMaxWidth())
        }
    }
    state.progress?.let { p ->
        InkSheet(onDismiss = {}) {
            Label("処理中")
            Spacer(Modifier.height(Space.s))
            Text(
                p.message + if (p.total > 0) "(${p.current}/${p.total})" else if (p.current > 0) "(${p.current})" else "",
                style = AppTheme.type.body,
                color = c.ink,
            )
            Spacer(Modifier.height(Space.m))
            if (p.total > 0) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(c.line)) {
                    Box(
                        Modifier
                            .fillMaxWidth((p.current.toFloat() / p.total).coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(c.accent),
                    )
                }
            } else {
                Hairline(dashed = true, color = c.accent)
            }
        }
    }
    if (state.resultTitle != null) {
        InkSheet(onDismiss = onDismissResult) {
            Text(state.resultTitle, style = AppTheme.type.title, color = c.ink)
            Spacer(Modifier.height(Space.s))
            Text(state.resultMessage ?: "", style = AppTheme.type.body, color = c.inkSoft)
            Spacer(Modifier.height(Space.m))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("OK", onClick = onDismissResult)
            }
        }
    }
}

@Composable
private fun ModeChoice(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().tappable(onClick).padding(vertical = Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Text(if (selected) "◉" else "○", style = AppTheme.type.title, color = if (selected) c.accent else c.inkFaint)
        Spacer(Modifier.width(Space.s))
        Column {
            Text(title, style = AppTheme.type.body, color = c.ink)
            Text(sub, style = AppTheme.type.caption, color = c.inkSoft)
        }
    }
}
