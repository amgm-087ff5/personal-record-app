package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.Pills
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable

/** 「＋ 記録」を押したときの選択: 単独の支出か、品目つきの支払いか。 */
@Composable
internal fun AddKindSheet(onDismiss: () -> Unit, onSingle: () -> Unit, onWithItems: () -> Unit) {
    val c = AppTheme.colors
    InkSheet(onDismiss = onDismiss) {
        Label("記録する")
        Text(
            "単独の支出",
            style = AppTheme.type.title,
            color = c.ink,
            modifier = Modifier.fillMaxWidth().tappable(onSingle).padding(vertical = Space.m),
        )
        Text("金額・カテゴリ・メモを1行で記録します", style = AppTheme.type.caption, color = c.inkSoft)
        Spacer(Modifier.height(Space.s))
        Hairline()
        Text(
            "品目つきの支出",
            style = AppTheme.type.title,
            color = c.ink,
            modifier = Modifier.fillMaxWidth().tappable(onWithItems).padding(vertical = Space.m),
        )
        Text(
            "合計・店名と、品目(品名・金額・カテゴリ)をまとめて記録します",
            style = AppTheme.type.caption,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Space.m))
    }
}

/**
 * 品目つきの支払いの手入力。合計・日時・メモ(店名など)・品目を入れて、グループと品目の支出を作る。
 * 品目の金額の合計を合計欄の下に出し、1タップで合計に入れられる。
 */
@Composable
internal fun ManualPaymentSheet(
    initialDate: String,
    initialTime: String,
    onDismiss: () -> Unit,
    onSave: (group: ExpenseGroup, items: List<Expense>, done: (String?) -> Unit) -> Unit,
) {
    val c = AppTheme.colors
    var dateText by remember { mutableStateOf(initialDate) }
    var timeText by remember { mutableStateOf(initialTime) }
    var totalText by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf(DraftItem(), DraftItem()) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }

    val itemsSum = DraftPayment.itemsSum(drafts)
    val sumText = DraftPayment.sumText(drafts)

    InkSheet(onDismiss = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Label("品目つきの支出を追加")

            // 合計: 大きく中央。下に品目の合計と「合計に入れる」
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Label("合計(円・店への支払い総額)")
                BareField(
                    value = totalText,
                    onValueChange = { v -> totalText = v.filter { it in '0'..'9' }.take(9) },
                    textStyle = AppTheme.type.numeralXL.copy(textAlign = TextAlign.Center),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Hairline(color = c.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "品目の合計 ${LedgerLogic.formatYen(itemsSum)}",
                        style = AppTheme.type.caption,
                        color = c.inkSoft,
                    )
                    if (sumText != null && sumText != totalText) {
                        TextAction("${LedgerLogic.formatYen(itemsSum)} を合計に入れる", onClick = { totalText = sumText })
                    }
                }
                val total = totalText.toLongOrNull()
                if (total != null && total != itemsSum && itemsSum > 0) {
                    Text(
                        "差額 ${LedgerLogic.formatYen(total - itemsSum)}(カテゴリ別の集計では、合計のカテゴリに入ります)",
                        style = AppTheme.type.caption,
                        color = c.danger,
                    )
                }
            }

            Label("日時")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
                BareField(
                    value = dateText,
                    onValueChange = { dateText = it.take(10) },
                    underline = true,
                    singleLine = true,
                    placeholder = "yyyy-MM-dd",
                    modifier = Modifier.weight(1f),
                )
                BareField(
                    value = timeText,
                    onValueChange = { timeText = it.take(5) },
                    underline = true,
                    singleLine = true,
                    placeholder = "HH:mm",
                    modifier = Modifier.width(88.dp),
                )
            }

            Label("メモ(店名など)")
            BareField(value = memo, onValueChange = { memo = it }, underline = true, placeholder = "店名など")

            Label("品目")
            drafts.forEachIndexed { index, item ->
                DraftItemRow(
                    item = item,
                    onChange = { drafts[index] = it },
                    onRemove = { if (drafts.size > 1) drafts.removeAt(index) else drafts[index] = DraftItem() },
                )
                Hairline(dashed = true)
            }
            TextAction("＋ 品目を追加", onClick = { drafts.add(DraftItem()) })

            errors.forEach { Text(it, style = AppTheme.type.caption, color = c.danger) }

            InkButton(
                text = "保存",
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val r = DraftPayment.build(dateText, timeText, totalText, memo, drafts.toList(), System.currentTimeMillis())
                    errors = r.errors
                    if (r.isValid) {
                        saving = true
                        onSave(r.group!!, r.items) { err ->
                            saving = false
                            if (err == null) onDismiss() else errors = listOf(err)
                        }
                    }
                },
            )
            TextAction("キャンセル", enabled = !saving, onClick = onDismiss, color = c.inkSoft, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DraftItemRow(item: DraftItem, onChange: (DraftItem) -> Unit, onRemove: () -> Unit) {
    val c = AppTheme.colors
    var pickCategory by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
            BareField(
                value = item.name,
                onValueChange = { onChange(item.copy(name = it)) },
                underline = true,
                singleLine = true,
                placeholder = "品名",
                modifier = Modifier.weight(1f),
            )
            BareField(
                value = item.amount,
                onValueChange = { v -> onChange(item.copy(amount = v.filter { it in '0'..'9' }.take(9))) },
                underline = true,
                singleLine = true,
                placeholder = "金額",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = AppTheme.type.numeral.copy(textAlign = TextAlign.End),
                modifier = Modifier.width(96.dp),
            )
            TextAction("削除", onClick = onRemove, color = c.danger)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction(
                "カテゴリ: ${item.category} ${if (pickCategory) "▴" else "▾"}",
                onClick = { pickCategory = !pickCategory },
                color = c.ink,
            )
            BareField(
                value = item.memo,
                onValueChange = { onChange(item.copy(memo = it)) },
                underline = true,
                singleLine = true,
                placeholder = "メモ",
                modifier = Modifier.weight(1f),
            )
        }
        if (pickCategory) {
            Pills(
                options = Categories.DEFAULTS,
                selected = item.category,
                onSelect = {
                    onChange(item.copy(category = it))
                    pickCategory = false
                },
                label = { it },
            )
        }
    }
}
