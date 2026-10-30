package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.Pills
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction

/**
 * 手入力の支払いの入力。合計・日時・店名・品目を入れて、支払い(receipts の表の下書き)と品目を保存する。
 * 家計簿の支出は、保存のなかで支払いから作られる。品目の金額の合計を合計欄の下に出し、1タップで合計に入れられる。
 * onSave には作った支払いと品目を渡す。done には保存の結果(エラーメッセージ、成功なら null)を渡す。
 */
@Composable
internal fun ManualPaymentSheet(
    initialDate: String,
    initialTime: String,
    onDismiss: () -> Unit,
    onSave: (receipt: Receipt, items: List<ReceiptItem>, headcount: Int?, done: (String?) -> Unit) -> Unit,
) {
    val c = AppTheme.colors
    var dateText by remember { mutableStateOf(initialDate) }
    var timeText by remember { mutableStateOf(initialTime) }
    var totalText by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var headcountText by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf(DraftItem(), DraftItem()) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val itemsSum = DraftPayment.itemsSum(drafts)
    val sumText = DraftPayment.sumText(drafts)

    // 日時の初期値(今日など)は入力とみなさない。それ以外の欄に何か入っていれば未保存。
    val dirty = totalText.isNotBlank() || memo.isNotBlank() || headcountText.isNotBlank() ||
        dateText != initialDate || timeText != initialTime ||
        drafts.any { it.name.isNotBlank() || it.amount.isNotBlank() || it.memo.isNotBlank() }
    // 閉じる・戻る・シート外タップの共通の入口。未保存があれば確認を出す。保存中は閉じない。
    val requestClose: () -> Unit = {
        if (!saving) {
            if (dirty) confirmDiscard = true else onDismiss()
        }
    }

    // 下スワイプでは閉じず最小化(入力を保持)。× ・戻る・最小化からの下スワイプで requestClose(未保存なら確認)
    InkSheet(onDismiss = requestClose, swipeToDismiss = false, title = "手動で登録") {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {

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

            Label("店名(任意)")
            BareField(value = memo, onValueChange = { memo = it }, underline = true, placeholder = "店名など")

            Label("人数(任意)")
            BareField(
                value = headcountText,
                onValueChange = { v -> headcountText = v.filter { it in '0'..'9' }.take(3) },
                underline = true,
                singleLine = true,
                placeholder = "人数",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

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
                    val r = DraftPayment.build(
                        dateText, timeText, totalText, memo, drafts.toList(), System.currentTimeMillis(), headcountText,
                    )
                    errors = r.errors
                    if (r.isValid) {
                        saving = true
                        onSave(r.receipt!!, r.items, r.headcount) { err ->
                            saving = false
                            if (err == null) onDismiss() else errors = listOf(err)
                        }
                    }
                },
            )
            TextAction("キャンセル", enabled = !saving, onClick = requestClose, color = c.inkSoft, modifier = Modifier.fillMaxWidth())
        }
    }

    if (confirmDiscard) {
        ConfirmSheet(
            title = "未保存の内容が残っています",
            message = "破棄しますか？ 入力した内容は保存されません。",
            confirmText = "破棄",
            dismissText = "入力に戻る",
            onConfirm = {
                confirmDiscard = false
                onDismiss()
            },
            onDismiss = { confirmDiscard = false },
        )
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
