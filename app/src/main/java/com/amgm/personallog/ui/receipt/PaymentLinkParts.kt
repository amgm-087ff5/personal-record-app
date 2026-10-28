package com.amgm.personallog.ui.receipt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.amgm.personallog.domain.PaymentLink
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.nestedLine
import com.amgm.personallog.ui.design.tappable

/**
 * 自分の支出(この支払いの子)の行。支払いの最下部に、支払いの下に字下げして入れる(左に罫線)。
 * 確定済みで、家計簿に紐づく支出があるときだけ出す。タップで自分の支出を開く。
 */
@Composable
internal fun PaymentLinkRow(link: PaymentLink, onClick: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().nestedLine(c.line)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tappable(onClick)
                .padding(horizontal = Space.edge, vertical = Space.s),
        ) {
            Label("自分の支出")
            Text("${formatYen(link.amount)}  ›", style = AppTheme.type.title, color = c.accent)
        }
        Hairline()
    }
}

/** 再確定で家計簿の行が作り直されるときの確認。 */
@Composable
internal fun RebuildWarningSheet(rebuilds: Boolean, lostEdited: Int, keptDeleted: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    InkSheet(onDismiss = onDismiss) {
        SheetTitle(if (rebuilds) "家計簿の行を作り直します" else "家計簿で直した内容が消えます")
        val lostNote = (if (lostEdited > 0) "家計簿で直した${lostEdited}件が消えます。" else "") +
            (if (keptDeleted > 0) "家計簿で消した品目${keptDeleted}件は、消したまま残ります(支出の詳細から元に戻せます)。" else "")
        Text(
            if (rebuilds) {
                "古い形式で確定した記録のときは、この支払いの家計簿の行を作り直します。" +
                    "家計簿で直した金額・カテゴリ・メモ・日時は消えます。$lostNote"
            } else {
                "支払いから消した品目に対応する家計簿の行は、品目といっしょに消えます。$lostNote"
            },
            style = AppTheme.type.body,
            color = c.inkSoft,
            modifier = Modifier.padding(bottom = Space.m),
        )
        TextAction(if (rebuilds) "作り直して確定" else "消して確定", onClick = onConfirm, color = c.danger)
        TextAction("キャンセル", onClick = onDismiss, color = c.inkSoft)
    }
}
