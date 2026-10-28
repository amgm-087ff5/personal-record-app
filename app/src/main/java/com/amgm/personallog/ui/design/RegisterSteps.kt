package com.amgm.personallog.ui.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

/**
 * 登録の手順の表示(「① 支払い › ② 自分の支出」)。current は今の手順(1 か 2)。
 * 今の手順は ink の semibold で、もう一方は inkFaint。今の手順は selected の意味を持つ。
 */
@Composable
fun RegisterSteps(current: Int, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.edge, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RegisterStep("① 支払い", selected = current == 1)
        Text(" › ", style = AppTheme.type.body, color = c.inkFaint)
        RegisterStep("② 自分の支出", selected = current == 2)
    }
}

@Composable
private fun RegisterStep(text: String, selected: Boolean) {
    val c = AppTheme.colors
    Text(
        text,
        style = AppTheme.type.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
        color = if (selected) c.ink else c.inkFaint,
        modifier = Modifier.semantics { this.selected = selected },
    )
}
