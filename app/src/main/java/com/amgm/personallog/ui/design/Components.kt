package com.amgm.personallog.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/*
 * 「紙と墨」の部品集。画面は Material の Card / OutlinedTextField / Button / FAB を直接並べず、
 * ここの部品と、画面ごとに固有のレイアウトを組み合わせる。
 */

/** 画面全体の地。各画面のルートに置く。 */
@Composable
fun PaperScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(AppTheme.colors.paper)) { content() }
}

/** 罫線。dashed=true でミシン目(レシートの切り取り線)。 */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = AppTheme.colors.line, dashed: Boolean = false) {
    if (dashed) {
        Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            )
        }
    } else {
        Box(modifier = modifier.fillMaxWidth().height(Space.hair).background(color))
    }
}

/** 小さな見出し(字間広め)。節の頭やプロパティ名に使う。 */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = AppTheme.colors.inkFaint) {
    Text(text = text, style = AppTheme.type.label, color = color, modifier = modifier)
}

/** 画面見出し。上に小さな kicker、下に大きな題。ステータスバー分の余白を内包する。 */
@Composable
fun ScreenHeader(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Space.edge, end = Space.edge, top = Space.l, bottom = Space.m),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Label(kicker)
            Text(title, style = AppTheme.type.display, color = AppTheme.colors.ink)
        }
        if (trailing != null) trailing()
    }
}

enum class StampTone { Neutral, Accent, Moss, Danger }

/** 判子風の小さなラベル(状態表示)。 */
@Composable
fun Stamp(text: String, modifier: Modifier = Modifier, tone: StampTone = StampTone.Neutral, filled: Boolean = false) {
    val c = AppTheme.colors
    val tint = when (tone) {
        StampTone.Neutral -> c.inkSoft
        StampTone.Accent -> c.accent
        StampTone.Moss -> c.moss
        StampTone.Danger -> c.danger
    }
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .then(if (filled) Modifier.background(tint) else Modifier.border(1.dp, tint, shape))
            .padding(horizontal = Space.s, vertical = 2.dp),
    ) {
        Text(text, style = AppTheme.type.label, color = if (filled) c.paper else tint)
    }
}

enum class InkButtonKind { Solid, Outline }

/** 角の小さい大きなボタン。主要操作は Solid(朱)、副次は Outline。 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: InkButtonKind = InkButtonKind.Solid,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(4.dp)
    val bg = if (kind == InkButtonKind.Solid) (if (enabled) c.accent else c.line) else Color.Transparent
    val fg = if (kind == InkButtonKind.Solid) (if (enabled) c.onAccent else c.inkFaint) else (if (enabled) c.ink else c.inkFaint)
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(bg)
            .then(if (kind == InkButtonKind.Outline) Modifier.border(1.dp, if (enabled) c.ink else c.line, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.title.copy(fontSize = AppTheme.type.body.fontSize), color = fg)
    }
}

/** 枠のない文字だけの操作。タップ領域は48dp以上。 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.accent,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = AppTheme.type.title.copy(fontSize = AppTheme.type.body.fontSize),
            color = if (enabled) color else AppTheme.colors.inkFaint,
        )
    }
}

/**
 * 枠なしの入力。罫線も箱もなく、文字がそのまま紙に書かれたように見せる。
 * underline=true のときだけ下に細い罫線を引く(設定のAPIキーなど、欄として明示したい所のみ)。
 */
@Composable
fun BareField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = AppTheme.type.body,
    placeholder: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    color: Color = AppTheme.colors.ink,
    underline: Boolean = false,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle.copy(color = color),
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(AppTheme.colors.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = if (underline) Space.s else 0.dp)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = textStyle, color = AppTheme.colors.inkFaint)
                    }
                    inner()
                }
            },
        )
        if (underline) Hairline(color = AppTheme.colors.ink)
    }
}

/** 選択肢の丸薬。入力欄の代わりに「選ぶ」操作へ置き換えるのに使う。 */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) c.ink else Color.Transparent)
            .border(1.dp, if (selected) c.ink else c.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.body, color = if (selected) c.paper else c.ink)
    }
}

/** 丸薬を折り返して並べる。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Pills(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        options.forEach { option ->
            Pill(text = label(option), selected = option == selected, onClick = { onSelect(option) })
        }
    }
}

/** 「名前 ········ 値」の点線つき一行。レシートの品目・設定の情報に使う。 */
@Composable
fun LeaderRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = AppTheme.type.body,
    valueStyle: TextStyle = AppTheme.type.numeral,
    labelColor: Color = AppTheme.colors.ink,
    valueColor: Color = AppTheme.colors.ink,
) {
    val leaderColor = AppTheme.colors.inkFaint.copy(alpha = 0.6f)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(label, style = labelStyle, color = labelColor, modifier = Modifier.weight(1f, fill = false))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.s)
                .padding(bottom = 6.dp)
                .height(1.dp),
        ) {
            drawLine(
                color = leaderColor,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
            )
        }
        Text(value, style = valueStyle, color = valueColor)
    }
}


/** 割合を一本の帯で見せる(カテゴリ内訳など)。parts は (割合, 色)。 */
@Composable
fun ProportionBand(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    Row(
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(2.dp)),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        parts.forEach { (share, color) ->
            Box(modifier = Modifier.weight(max(share, 0.0001f)).fillMaxSize().background(color))
        }
    }
}

/** 下から出る編集シート。ダイアログやインラインの入力欄の代わりに使う。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.paperRaised,
        contentColor = c.ink,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = Space.m)
                    .width(36.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.line),
            )
        },
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.edge)
                .padding(bottom = Space.l),
            content = content,
        )
    }
}

/** 見た目のない押せる面(行全体のタップ領域)。リップルなし。 */
@Composable
fun Modifier.tappable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = source, indication = null, onClick = onClick)
}
