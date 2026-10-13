package com.amgm.personallog.ui.receipt

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.domain.TaxCalc
import com.amgm.personallog.receipt.ReceiptImageCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 税率の即選択ボタン。よく使う 8%(軽減)と 10%(標準)。それ以外は下の入力欄に直接打つ。 */
private val PRESET_TAX_RATES = listOf("8", "10")

/**
 * レシートの確認・修正画面。
 * 長いフォームを LazyColumn にして、画面外の品目は描画しない(スクロールを軽くするため)。
 */
@Composable
fun ReceiptDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceiptDetailViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showRescan by rememberSaveable { mutableStateOf(false) }
    var bigImage by rememberSaveable { mutableStateOf(false) }
    var viewerOpen by rememberSaveable { mutableStateOf(false) }
    val groups = remember(s.items) { groupItemForms(s.items) }

    LaunchedEffect(s.finished) { if (s.finished) onBack() }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("戻る") }
                    Text("レシートの確認", style = MaterialTheme.typography.titleLarge)
                }
            }

            if (s.loading) {
                item(key = "loading") { CircularProgressIndicator() }
            } else if (s.notFound) {
                item(key = "notFound") { Text("レシートが見つかりません。") }
            } else {
                if (s.imagePath.isNotBlank()) {
                    item(key = "image") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReceiptImage(imagePath = s.imagePath, big = bigImage, onClick = { viewerOpen = true })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { bigImage = !bigImage }) { Text(if (bigImage) "画像を小さく" else "画像を大きく") }
                                OutlinedButton(enabled = !s.busy, onClick = { showRescan = true }) { Text("読み取り直す") }
                            }
                        }
                    }
                }

                if (s.scanning) {
                    item(key = "scanning") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("読み取り中です…(最大1〜2分)")
                        }
                    }
                }

                s.error?.let { msg ->
                    item(key = "error") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(onClick = viewModel::dismissError) { Text("閉じる") }
                            }
                        }
                    }
                }
                s.info?.let { msg ->
                    item(key = "info") {
                        Card {
                            Column(Modifier.padding(12.dp)) {
                                Text(msg)
                                TextButton(onClick = viewModel::dismissInfo) { Text("閉じる") }
                            }
                        }
                    }
                }

                s.possibleDuplicate?.let { original ->
                    item(key = "duplicate") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "重複の可能性: 同じ内容(日付・店名・合計)のレシートが既にあります。\n$original",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }

                item(key = "status") {
                    if (s.confirmed) {
                        Text("確定済み(編集して「再確定」すると家計簿も更新されます)", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("未確定(下書き)", color = MaterialTheme.colorScheme.tertiary)
                    }
                }

                // 店の情報: 日付・時刻・店名・立て替え
                item(key = "shop") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = s.date,
                                onValueChange = viewModel::setDate,
                                label = { Text("日付 yyyy-MM-dd") },
                                singleLine = true,
                                modifier = Modifier.weight(1.6f),
                            )
                            OutlinedTextField(
                                value = s.time,
                                onValueChange = viewModel::setTime,
                                label = { Text("時刻 HH:mm") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = s.store,
                            onValueChange = viewModel::setStore,
                            label = { Text("店名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = s.isAdvance, onCheckedChange = viewModel::setAdvance)
                            Text("自分が立て替えた支払い(他の人の分を含む)")
                        }
                    }
                }

                // 品目: 1個ずつカード。同じ品目はまとめて1枚にし、その中に1個ずつのカードを並べる。
                item(key = "itemsTitle") {
                    Text("品目", style = MaterialTheme.typography.titleMedium)
                }
                items(groups, key = { it.head.key }) { group ->
                    ItemGroupCard(group = group, isAdvance = s.isAdvance, viewModel = viewModel)
                }
                item(key = "addItem") {
                    OutlinedButton(onClick = viewModel::addItem) { Text("品目を追加") }
                }

                // 合計と割り勘(レシート全体)
                item(key = "total") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = s.total,
                            onValueChange = viewModel::setTotal,
                            label = { Text("合計(円・レシートの印字)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Card {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("税込の支払合計: ${formatYen(s.itemsSum)}", style = MaterialTheme.typography.bodyMedium)
                                if (s.isAdvance) {
                                    OutlinedTextField(
                                        value = s.ownAmount,
                                        onValueChange = viewModel::setOwnAmount,
                                        label = { Text("自分が払う分(円・税込)") },
                                        placeholder = { Text("空欄は全額 ${formatYen(s.itemsSum)}") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        "品目の「自分の分に含める」にチェックすると、その税込合計がここに入ります。手で直接入力も可。",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text("自分が払う分: ${formatYen(s.ownTotal)}", style = MaterialTheme.typography.bodyMedium)
                                    Text("貰う分(他の人の分): ${formatYen(s.theirTotal)}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text("小数点以下は合計で切り捨てています。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                s.mismatch?.let { diff ->
                    item(key = "mismatch") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "合計欄と税込の支払合計が一致しません(合計欄 - 支払合計 = ${formatYen(diff)})。読み取りミスか、端数の差の可能性があります。",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                TextButton(onClick = viewModel::useItemsSumAsTotal) { Text("合計欄を支払合計に合わせる") }
                            }
                        }
                    }
                }

                if (s.rawJson.isNotBlank() && s.items.isEmpty()) {
                    item(key = "raw") {
                        Column {
                            Text("Claudeの応答原文(解釈できなかった場合の参考)", style = MaterialTheme.typography.labelMedium)
                            SelectionContainer {
                                Text(s.rawJson, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!s.confirmed) {
                                OutlinedButton(enabled = !s.busy, onClick = viewModel::saveDraft) { Text("下書き保存") }
                            }
                            Button(enabled = !s.busy, onClick = viewModel::confirm) { Text(if (s.confirmed) "再確定" else "確定") }
                        }
                        TextButton(enabled = !s.busy, onClick = { showDelete = true }) {
                            Text("このレシートを削除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (viewerOpen) {
        ReceiptImageViewer(relativePath = s.imagePath, onDismiss = { viewerOpen = false })
    }

    s.duplicateWarning?.let { existing ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateWarning,
            title = { Text("重複の確認") },
            text = { Text("既に同じレシートがあります。追加しますか?\n\n$existing") },
            confirmButton = { TextButton(onClick = viewModel::confirmAnyway) { Text("追加する") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDuplicateWarning) { Text("キャンセル") } },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("レシートを削除") },
            text = { Text("レシート画像も端末から削除されます。このレシートから作った家計簿の行はどうしますか?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { showDelete = false; viewModel.delete(alsoExpenses = true) }) { Text("家計簿の行も消す") }
                    TextButton(onClick = { showDelete = false; viewModel.delete(alsoExpenses = false) }) { Text("家計簿の行は残す") }
                    TextButton(onClick = { showDelete = false }) { Text("キャンセル") }
                }
            },
        )
    }
    if (showRescan) {
        AlertDialog(
            onDismissRequest = { showRescan = false },
            title = { Text("読み取り直す") },
            text = { Text("現在の入力内容は、新しい読み取り結果で置き換わります。画像をClaudeに再送信します。") },
            confirmButton = {
                TextButton(onClick = { showRescan = false; viewModel.rescan() }) { Text("読み取り直す") }
            },
            dismissButton = { TextButton(onClick = { showRescan = false }) { Text("キャンセル") } },
        )
    }
}

/**
 * 同じ品目のまとまり1つ分のカード。
 * 常に見える: 品名・金額(+税込/税抜)・割り勘の補助チェック。税抜のときは税込価格も出す。
 * 詳細: 税率・カテゴリ・メモ。個数が2以上のときは、1個ずつのカード(金額・メモ・割り勘・削除)を並べる。
 */
@Composable
private fun ItemGroupCard(group: ItemGroup, isAdvance: Boolean, viewModel: ReceiptDetailViewModel) {
    val head = group.head
    val keys = group.keys
    val count = group.units.size
    var expanded by rememberSaveable(head.key) { mutableStateOf(false) }
    val headModel = head.toItemModel()
    val allPicked = group.units.all { it.picked }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = head.name,
                    onValueChange = { viewModel.setGroupName(keys, it) },
                    label = { Text("品名") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (count > 1) {
                    Text("×$count", style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = head.amount,
                    onValueChange = { viewModel.setGroupAmount(keys, it) },
                    label = { Text(if (count > 1) "金額(1個・円)" else "金額(円)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.toggleGroupTaxIncluded(keys) }) {
                    Text(if (head.priceIncludesTax) "税込 ${head.taxRate}%" else "税抜 ${head.taxRate}%")
                }
            }
            if (!head.priceIncludesTax) {
                Text(
                    "→ 税込 ${TaxCalc.paidYenText(headModel)}円",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (isAdvance) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allPicked, onCheckedChange = { viewModel.togglePicked(keys) })
                    Text(if (count > 1) "自分の分に含める(全部)" else "自分の分に含める")
                }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "詳細を閉じる" else "詳細(税率・カテゴリ・メモ)")
            }
            if (expanded) {
                TaxRateRow(current = head.taxRate, onPick = { viewModel.setGroupTaxRate(keys, it) })
                CategoryPicker(value = head.category, onChange = { viewModel.setGroupCategory(keys, it) })
                if (count == 1) {
                    OutlinedTextField(
                        value = head.memo,
                        onValueChange = { viewModel.setUnitMemo(head.key, it) },
                        label = { Text("メモ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { viewModel.removeGroup(keys) }) {
                        Text("この品目を削除", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    group.units.forEachIndexed { index, unit ->
                        UnitCard(
                            number = index + 1,
                            unit = unit,
                            isAdvance = isAdvance,
                            viewModel = viewModel,
                        )
                    }
                    OutlinedButton(onClick = { viewModel.addSameItem(head.key) }) { Text("同じ品目をもう1個追加") }
                }
            }
        }
    }
}

/** 2個以上の品目の、1個ぶんのカード。金額・メモ・割り勘の補助・削除を持つ。 */
@Composable
private fun UnitCard(number: Int, unit: ItemForm, isAdvance: Boolean, viewModel: ReceiptDetailViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${number}個目", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = unit.amount,
                onValueChange = { viewModel.setUnitAmount(unit.key, it) },
                label = { Text("この1個の金額(円)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = unit.memo,
                onValueChange = { viewModel.setUnitMemo(unit.key, it) },
                label = { Text("メモ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isAdvance) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = unit.picked, onCheckedChange = { viewModel.togglePicked(setOf(unit.key)) })
                    Text("この1個を自分の分に含める")
                }
            }
            TextButton(onClick = { viewModel.removeUnit(unit.key) }) {
                Text("この1個を削除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 税率: 8%・10%の即選択ボタンと、任意の値を入れる欄。 */
@Composable
private fun TaxRateRow(current: String, onPick: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PRESET_TAX_RATES.forEach { rate ->
            if (current == rate) {
                Button(onClick = { onPick(rate) }) { Text("$rate%") }
            } else {
                OutlinedButton(onClick = { onPick(rate) }) { Text("$rate%") }
            }
        }
        OutlinedTextField(
            value = current,
            onValueChange = onPick,
            label = { Text("税率(%)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CategoryPicker(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("カテゴリ: $value") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val options = if (value in Categories.DEFAULTS) Categories.DEFAULTS else Categories.DEFAULTS + value
            options.forEach { c ->
                DropdownMenuItem(text = { Text(c) }, onClick = { onChange(c); open = false })
            }
        }
    }
}

@Composable
private fun ReceiptImage(imagePath: String, big: Boolean, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 一覧用の表示は 1024px まで。拡大表示(ReceiptImageViewer)は別に高解像度で読む。
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) {
            try {
                ReceiptImageCodec.decodeScaled(File(context.filesDir, imagePath), 1024)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val bmp = bitmap
    if (bmp == null) {
        Text("(画像を表示できません)", style = MaterialTheme.typography.bodySmall)
    } else {
        Image(
            bitmap = bmp,
            contentDescription = "レシート画像(タップで拡大表示)",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (big) 640.dp else 220.dp)
                .clickable(onClick = onClick),
        )
    }
}
