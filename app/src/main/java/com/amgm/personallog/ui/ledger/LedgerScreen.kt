@file:OptIn(ExperimentalMaterial3Api::class)

package com.amgm.personallog.ui.ledger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.backup.ImportMode
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** 家計簿タブのトップ画面。 */
@Composable
fun LedgerScreen(modifier: Modifier = Modifier, viewModel: LedgerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Expense?>(null) }
    var creating by remember { mutableStateOf(false) }
    val expandedGroups = remember { mutableStateMapOf<Long, Boolean>() }
    var deletingGroup by remember { mutableStateOf<ExpenseGroup?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImportPicked(uri)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = viewModel::previousMonth) { Text("◀") }
                    Text(
                        LedgerLogic.monthLabel(state.month),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = viewModel::nextMonth) { Text("▶") }
                    Spacer(Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("バックアップ ▾") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("エクスポート(ZIPに保存)") },
                                onClick = {
                                    menuOpen = false
                                    exportLauncher.launch("personal-log-${LocalDate.now()}.zip")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("インポート(ZIPから復元)") },
                                onClick = {
                                    menuOpen = false
                                    importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                                },
                            )
                        }
                    }
                }
            }
            state.error?.let { err ->
                item(key = "error") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::dismissError) { Text("閉じる") }
                        }
                    }
                }
            }
            item(key = "total") {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("支出合計", style = MaterialTheme.typography.labelLarge)
                    Text(
                        LedgerLogic.formatYen(state.total),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item(key = "cat-title") { SectionTitle("カテゴリ別") }
            if (state.shares.isEmpty()) {
                item(key = "cat-empty") {
                    Text(
                        if (state.loading) "読み込み中…" else "この月の支出はありません",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                val max = state.shares.maxOf { it.total }.coerceAtLeast(1L)
                items(state.shares, key = { "cat-" + it.category }) { share ->
                    CategoryBar(share, fraction = share.total.toFloat() / max)
                }
            }
            item(key = "chart-title") { SectionTitle("内訳(カテゴリ別の積み上げ)") }
            item(key = "chart") {
                StackedChart(
                    level = state.chartLevel,
                    label = state.chartLabel,
                    bars = state.chartBars,
                    legend = state.chartLegend,
                    onLevel = viewModel::selectChartLevel,
                    onPrev = viewModel::chartPrevious,
                    onNext = viewModel::chartNext,
                    onBarTap = viewModel::onChartBarTapped,
                )
            }
            item(key = "list-title") { SectionTitle("支出一覧") }
            if (state.days.isEmpty()) {
                item(key = "list-empty") {
                    Text("右下の + から支出を追加できます", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            state.days.forEach { group ->
                item(key = "day-" + group.date) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LedgerLogic.dayLabel(group.date), style = MaterialTheme.typography.titleSmall)
                        Text(LedgerLogic.formatYen(group.total), style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                }
                items(group.items, key = { entryKey(it) }) { entry ->
                    when (entry) {
                        is LedgerEntry.Single -> ExpenseRow(entry.expense, onClick = { editing = entry.expense })
                        is LedgerEntry.Group -> GroupRow(
                            entry = entry,
                            expanded = expandedGroups[entry.group.id] == true,
                            onToggle = { expandedGroups[entry.group.id] = expandedGroups[entry.group.id] != true },
                            onItemClick = { editing = it },
                            onDelete = { deletingGroup = entry.group },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "支出を追加") }
    }

    if (creating) {
        val initialDate = LedgerLogic.defaultDate(state.month, LocalDate.now())
        ExpenseEditorDialog(
            original = null,
            initialDate = initialDate,
            initialTime = LedgerLogic.defaultTime(initialDate, LocalDate.now(), LocalTime.now()),
            linkedReceiptAt = null,
            onDismiss = { creating = false },
            onSave = { date, time, amount, category, memo, done ->
                viewModel.saveExpense(null, date, time, amount, category, memo, done)
            },
            onDelete = null,
        )
    }
    editing?.let { target ->
        // レシートに紐づく支出なら、そのレシートの日時を取っておき、日時を変えたときの警告に使う
        var linkedAt by remember(target) { mutableStateOf<String?>(null) }
        LaunchedEffect(target) { linkedAt = viewModel.linkedReceiptDateTime(target) }
        ExpenseEditorDialog(
            original = target,
            initialDate = target.date,
            initialTime = target.time,
            linkedReceiptAt = linkedAt,
            onDismiss = { editing = null },
            onSave = { date, time, amount, category, memo, done ->
                viewModel.saveExpense(target, date, time, amount, category, memo, done)
            },
            onDelete = {
                viewModel.deleteExpense(target)
                editing = null
            },
        )
    }

    deletingGroup?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("グループを削除") },
            text = { Text("「${target.memo.ifBlank { target.category }}」の支払いと品目をすべて削除します。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(target)
                    deletingGroup = null
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deletingGroup = null }) { Text("キャンセル") } },
        )
    }

    BackupDialogs(
        state = state.backup,
        onPickMode = viewModel::confirmImport,
        onCancelImport = viewModel::cancelImport,
        onDismissResult = viewModel::dismissBackupResult,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun CategoryBar(share: CategoryShare, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(share.category, style = MaterialTheme.typography.bodyMedium)
            Text("${LedgerLogic.formatYen(share.total)}(${share.percent}%)", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp)),
        ) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(10.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)),
            )
        }
    }
}

/** カテゴリの色。定義済みカテゴリは順番で固定し、それ以外は名前から決まる色にする。未分類は灰色。 */
private val CHART_PALETTE = listOf(
    Color(0xFF4E79A7), Color(0xFFF28E2B), Color(0xFFE15759), Color(0xFF76B7B2), Color(0xFF59A14F),
    Color(0xFFEDC948), Color(0xFFB07AA1), Color(0xFFFF9DA7), Color(0xFF9C755F), Color(0xFF86BCB6),
)
private val UNCATEGORIZED_COLOR = Color(0xFF9E9E9E)

private fun categoryColor(category: String): Color {
    if (category == Categories.UNCATEGORIZED) return UNCATEGORIZED_COLOR
    val fixed = Categories.DEFAULTS.indexOf(category)
    val index = if (fixed >= 0) fixed else (category.hashCode() and Int.MAX_VALUE)
    return CHART_PALETTE[index % CHART_PALETTE.size]
}

/**
 * 積み上げ棒グラフ。棒をタップすると1段下の階層へ、階層ボタンで表示単位を切り替え、◀▶で前後へ移動する。
 * 凡例は表示期間全体のカテゴリ別「金額 (割合)」。
 */
@Composable
private fun StackedChart(
    level: ChartLevel,
    label: String,
    bars: List<ChartBar>,
    legend: List<CategoryShare>,
    onLevel: (ChartLevel) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBarTap: (ChartBar) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrev) { Text("◀") }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNext) { Text("▶") }
        }

        val max = bars.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
        if (bars.isNotEmpty()) {
            Canvas(
                Modifier.fillMaxWidth().height(160.dp).pointerInput(bars) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / bars.size
                        val i = (offset.x / slot).toInt().coerceIn(0, bars.lastIndex)
                        onBarTap(bars[i])
                    }
                },
            ) {
                val slot = size.width / bars.size
                val barWidth = slot * 0.6f
                bars.forEachIndexed { i, bar ->
                    val x = slot * i + (slot - barWidth) / 2f
                    var bottom = size.height
                    bar.segments.forEach { seg ->
                        val h = size.height * (seg.amount.toFloat() / max)
                        if (h > 0f) {
                            drawRect(
                                color = categoryColor(seg.category),
                                topLeft = Offset(x, bottom - h),
                                size = Size(barWidth, h),
                            )
                            bottom -= h
                        }
                    }
                }
            }
            // 棒が多い(時間別の24本)ときは、金額は省き、ラベルは3本おきに出す
            val dense = bars.size > 12
            Row(Modifier.fillMaxWidth()) {
                bars.forEachIndexed { i, bar ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(enabled = bar.drillable) { onBarTap(bar) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!dense) {
                            Text(
                                LedgerLogic.compactYen(bar.total),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                        Text(
                            if (!dense || i % 3 == 0) bar.label else "",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        } else {
            Text("表示するデータがありません", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }

        if (legend.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            legend.forEach { share ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(categoryColor(share.category), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(share.category, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${LedgerLogic.formatYen(share.total)} (${share.percent}%)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // 階層の切替(下部)。時間別は時刻データが無いため含めない。
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ChartLevel.values().forEach { lv ->
                if (lv == level) {
                    Button(onClick = { onLevel(lv) }) { Text(lv.label) }
                } else {
                    OutlinedButton(onClick = { onLevel(lv) }) { Text(lv.label) }
                }
            }
        }
    }
}

private fun entryKey(entry: LedgerEntry): String = when (entry) {
    is LedgerEntry.Single -> "exp-" + entry.expense.id
    is LedgerEntry.Group -> "grp-" + entry.group.id
}

/** 支払いグループの1行。タップで品目を開く。展開時は品目・品目の合計・差額・削除を出す。 */
@Composable
private fun GroupRow(
    entry: LedgerEntry.Group,
    expanded: Boolean,
    onToggle: () -> Unit,
    onItemClick: (Expense) -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.group.memo.ifBlank { entry.category },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${entry.items.size}品",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (expanded) "タップで閉じる" else "タップで品目を表示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(LedgerLogic.formatYen(entry.amount), style = MaterialTheme.typography.bodyLarge)
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp)) {
                entry.items.forEach { item -> ExpenseRow(item, onClick = { onItemClick(item) }) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("品目の合計", style = MaterialTheme.typography.bodySmall)
                    Text(LedgerLogic.formatYen(entry.itemSum), style = MaterialTheme.typography.bodySmall)
                }
                if (entry.difference != 0L) {
                    Text(
                        "差額 ${LedgerLogic.formatYen(entry.difference)}(品目の合計と支払い金額が一致しません)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDelete) { Text("このグループを削除") }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(expense.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(expense.category, style = MaterialTheme.typography.bodyLarge)
                if (expense.receiptId != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "レシート由来",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (expense.memo.isNotBlank()) {
                Text(
                    expense.memo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(LedgerLogic.formatYen(expense.amount.toLong()), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ExpenseEditorDialog(
    original: Expense?,
    initialDate: String,
    initialTime: String,
    /** 紐づくレシートの日時 "yyyy-MM-dd HH:mm"。紐づかなければ null。 */
    linkedReceiptAt: String?,
    onDismiss: () -> Unit,
    onSave: (date: String, time: String, amount: Int, category: String, memo: String, done: (String?) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var dateText by remember { mutableStateOf(initialDate) }
    var timeText by remember { mutableStateOf(initialTime) }
    /** 日時がレシートと違うので確認中の入力。null なら確認ダイアログは出ていない。 */
    var pendingSave by remember { mutableStateOf<ExpenseValidation?>(null) }
    var amountText by remember { mutableStateOf(original?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(original?.category) }
    var memo by remember { mutableStateOf(original?.memo ?: "") }
    var validation by remember { mutableStateOf<ExpenseValidation?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val categories = remember(original) {
        val base = Categories.DEFAULTS.filter { it != Categories.UNCATEGORIZED }
        val extra = original?.category
        if (extra != null && extra !in base) base + extra else base
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (original == null) "支出を追加" else "支出を編集") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (original?.receiptId != null) {
                    Text(
                        "レシート由来の支出です" + (linkedReceiptAt?.let { "(レシート日時 $it)" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("日付 (yyyy-MM-dd)") },
                        singleLine = true,
                        isError = validation?.dateError != null,
                        supportingText = if (validation?.dateError != null) { { Text(validation?.dateError ?: "") } } else null,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showDatePicker = true }) { Text("選択") }
                }
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("時刻 (HH:mm)") },
                    singleLine = true,
                    isError = validation?.timeError != null,
                    supportingText = if (validation?.timeError != null) { { Text(validation?.timeError ?: "") } } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金額(円)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = validation?.amountError != null,
                    supportingText = if (validation?.amountError != null) { { Text(validation?.amountError ?: "") } } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    Box {
                        OutlinedButton(onClick = { showCategoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("カテゴリ: " + (category ?: "選択してください") + " ▾")
                        }
                        DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                            categories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = {
                                        category = c
                                        showCategoryMenu = false
                                    },
                                )
                            }
                        }
                    }
                    validation?.categoryError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("メモ") },
                    modifier = Modifier.fillMaxWidth(),
                )
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val v = LedgerLogic.validate(dateText, timeText, amountText, category)
                    validation = v
                    saveError = null
                    if (v.isValid) {
                        // 紐づくレシートと日時が違うまま日時を変えて保存しようとしたら、確認を挟む(レシートは変更しない)
                        val dateTimeChanged = original != null && (v.date != original.date || v.time != original.time)
                        val mismatch = linkedReceiptAt != null && "${v.date} ${v.time}" != linkedReceiptAt
                        if (dateTimeChanged && mismatch) {
                            pendingSave = v
                        } else {
                            saving = true
                            onSave(v.date!!, v.time!!, v.amount!!, v.category!!, memo) { err ->
                                saving = false
                                if (err == null) onDismiss() else saveError = err
                            }
                        }
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(enabled = !saving, onClick = { confirmDelete = true }) {
                        Text("削除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(enabled = !saving, onClick = onDismiss) { Text("キャンセル") }
            }
        },
    )

    pendingSave?.let { v ->
        AlertDialog(
            onDismissRequest = { pendingSave = null },
            title = { Text("レシートの日時と異なります") },
            text = {
                Text(
                    "この支出はレシート(${linkedReceiptAt ?: ""})に紐づいています。" +
                        "入力した日時(${v.date} ${v.time})はレシートと異なります。" +
                        "レシートの日時は変更されません。このまま保存しますか？",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSave = null
                    saving = true
                    onSave(v.date!!, v.time!!, v.amount!!, v.category!!, memo) { err ->
                        saving = false
                        if (err == null) onDismiss() else saveError = err
                    }
                }) { Text("このまま保存") }
            },
            dismissButton = { TextButton(onClick = { pendingSave = null }) { Text("戻る") } },
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
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        dateText = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } },
        ) { DatePicker(state = pickerState) }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("削除しますか?") },
            text = { Text("この支出を削除します。元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun BackupDialogs(
    state: BackupUiState,
    onPickMode: (ImportMode) -> Unit,
    onCancelImport: () -> Unit,
    onDismissResult: () -> Unit,
) {
    if (state.pendingImport != null) {
        var mode by remember { mutableStateOf(ImportMode.MERGE) }
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text("バックアップを取り込む") },
            text = {
                Column {
                    Text("既存のデータの扱いを選んでください。")
                    Spacer(Modifier.height(8.dp))
                    ModeRow("追加(重複はスキップ)", "今あるデータは残し、無いものだけ追加します", mode == ImportMode.MERGE) { mode = ImportMode.MERGE }
                    ModeRow("すべて置き換え", "今ある日記・レシート・家計簿を全て削除してから取り込みます", mode == ImportMode.REPLACE) { mode = ImportMode.REPLACE }
                }
            },
            confirmButton = { TextButton(onClick = { onPickMode(mode) }) { Text("取り込む") } },
            dismissButton = { TextButton(onClick = onCancelImport) { Text("キャンセル") } },
        )
    }
    state.progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("処理中") },
            text = {
                Column {
                    Text(p.message + if (p.total > 0) "(${p.current}/${p.total})" else if (p.current > 0) "(${p.current})" else "")
                    Spacer(Modifier.height(12.dp))
                    if (p.total > 0) {
                        LinearProgressIndicator(progress = { p.current.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
        )
    }
    if (state.resultTitle != null) {
        AlertDialog(
            onDismissRequest = onDismissResult,
            title = { Text(state.resultTitle) },
            text = { Text(state.resultMessage ?: "") },
            confirmButton = { TextButton(onClick = onDismissResult) { Text("OK") } },
        )
    }
}

@Composable
private fun ModeRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
