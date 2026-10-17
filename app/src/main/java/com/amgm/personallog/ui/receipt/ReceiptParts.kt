package com.amgm.personallog.ui.receipt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.Space

/** 金額の文字列を「¥1,234」にする。数字として読めないときは「¥—」。 */
internal fun yenText(amount: String): String = amount.toLongOrNull()?.let { formatYen(it) } ?: "¥—"

/** シート内の見出し(店名や品目の名前)。 */
@Composable
internal fun SheetTitle(text: String) {
    Text(
        text,
        style = AppTheme.type.title,
        color = AppTheme.colors.ink,
        modifier = Modifier.padding(bottom = Space.m),
    )
}

/** シート内の入力: 上に小さな見出し、下に罫線つきの枠なし入力。 */
@Composable
internal fun SheetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
) {
    Column(modifier = modifier.padding(bottom = Space.m)) {
        Label(label)
        BareField(
            value = value,
            onValueChange = onValueChange,
            underline = true,
            singleLine = true,
            placeholder = placeholder,
            textStyle = AppTheme.type.title,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        )
    }
}

/** シート内の選択欄: 見出し+その下に丸薬の並び(中身は呼び出し側)。 */
@Composable
internal fun SheetChoice(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.padding(bottom = Space.m)) {
        Label(label, modifier = Modifier.padding(bottom = Space.s))
        content()
    }
}
