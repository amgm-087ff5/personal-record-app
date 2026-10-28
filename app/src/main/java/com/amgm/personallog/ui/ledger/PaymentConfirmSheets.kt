package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.StampTone
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.receipt.RebuildWarningSheet
import com.amgm.personallog.ui.receipt.SheetTitle

/**
 * 下書きの確定の確認(支出の画面)。支払いの画面と同じ中身: 重複の候補・合計が未入力・家計簿の作り直し。
 * 判定は PaymentConfirmer が行い、ここはその結果の表示だけを持つ。
 */
@Composable
internal fun DraftConfirmSheets(
    state: PaymentUiState,
    onConfirmAnyway: () -> Unit,
    onNotDuplicate: () -> Unit,
    onUseItemsSum: () -> Unit,
    onInputTotal: (Int) -> Unit,
    onRebuildConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors

    if (state.confirmDuplicates.isNotEmpty()) {
        InkSheet(onDismiss = onDismiss) {
            SheetTitle("重複の確認")
            Stamp("重複の可能性", tone = StampTone.Danger)
            Text(
                "既に同じ支払いがあります。確定しますか?\n\n" + state.confirmDuplicates.joinToString("\n") { it.summary },
                style = AppTheme.type.body,
                color = c.inkSoft,
                modifier = Modifier.padding(vertical = Space.m),
            )
            TextAction("確定する", onClick = onConfirmAnyway)
            TextAction("重複ではないとして確定", onClick = onNotDuplicate)
            TextAction("やめる", onClick = onDismiss, color = c.inkSoft)
        }
    }

    state.confirmMissingTotal?.let { itemsSum ->
        InkSheet(onDismiss = onDismiss) {
            SheetTitle("合計が未入力です")
            Text(
                "確定するには合計が必要です。合計を入力するか、品目の合計を入れてください。",
                style = AppTheme.type.body,
                color = c.inkSoft,
                modifier = Modifier.padding(bottom = Space.m),
            )
            var input by remember { mutableStateOf("") }
            BareField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(9) },
                placeholder = "合計(円)",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            val typed = input.toIntOrNull()
            if (typed != null && typed > 0) {
                TextAction("入力した合計 ${LedgerLogic.formatYen(typed.toLong())} で確定", onClick = { onInputTotal(typed) })
            }
            if (itemsSum > 0L) {
                TextAction("品目の合計 ${LedgerLogic.formatYen(itemsSum)} を入れて確定", onClick = onUseItemsSum)
            }
            TextAction("やめる", onClick = onDismiss, color = c.inkSoft)
        }
    }

    state.confirmRebuild?.let { impact ->
        RebuildWarningSheet(
            rebuilds = impact.rebuild,
            lostEdited = impact.lostEdited,
            keptDeleted = impact.keptDeleted,
            onConfirm = onRebuildConfirm,
            onDismiss = onDismiss,
        )
    }
}
