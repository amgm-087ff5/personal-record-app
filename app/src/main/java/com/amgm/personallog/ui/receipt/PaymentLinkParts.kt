package com.amgm.personallog.ui.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.amgm.personallog.domain.PaymentLink
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable

/** 「支払いを開く(家計簿 ¥N)」。確定済みで、家計簿に紐づく支払いがあるときだけ出す。 */
internal fun paymentLinkLabel(link: PaymentLink): String = "支払いを開く(家計簿 ${formatYen(link.amount)})"

@Composable
internal fun PaymentLinkRow(link: PaymentLink, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick)
            .padding(horizontal = Space.edge, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Label("家計簿")
        Text(paymentLinkLabel(link) + "  ›", style = AppTheme.type.title, color = c.accent)
    }
    Hairline()
}

/** 再確定で家計簿の行が作り直されるときの確認。 */
@Composable
internal fun RebuildWarningSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    InkSheet(onDismiss = onDismiss) {
        SheetTitle("家計簿の行を作り直します")
        Text(
            "立て替えと通常を切り替えたとき、または古い形式で確定した記録のときは、このレシートの家計簿の行を作り直します。" +
                "家計簿で直した金額・カテゴリ・メモ・日時は消えます。",
            style = AppTheme.type.body,
            color = c.inkSoft,
            modifier = Modifier.padding(bottom = Space.m),
        )
        TextAction("作り直して確定", onClick = onConfirm, color = c.danger)
        TextAction("キャンセル", onClick = onDismiss, color = c.inkSoft)
    }
}
