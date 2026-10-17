package com.amgm.personallog.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * テーマ「窓辺」。窓から差す自然光と、やわらかい影のデザイン。
 *  - 昼: 日が差す壁(左上が暖かく、右下が青い陰)に、白い面がふわりと浮く。
 *  - 夜: 夜景の色の壁に、窓の明かり(琥珀)が灯る。
 * ガラスの板や縁の線光源は使わない。光は「背景の差し込み」「主操作の灯り」「面の影」だけで表す。
 * 画面は AppTheme.colors / AppTheme.type / Space から値を取る(フィールド名 paper / paperRaised / ink は
 * 旧名のまま。意味は 壁 / 面 / 文字)。
 */
@Immutable
data class AppColors(
    val paper: Color,        // 壁(単色が必要な所。ウィンドウ背景など)
    val paperRaised: Color,  // 面(一段浮いたシート・スリップ・ボタンの地)
    val ink: Color,          // 主文字
    val inkSoft: Color,      // 補足文字
    val inkFaint: Color,     // 注記・プレースホルダ
    val line: Color,         // 罫線
    val accent: Color,       // 琥珀。主要操作・強調(文字色としても使える濃さ)
    val accentTop: Color,    // 主操作ボタンのグラデーション上端(accent より明るい)
    val onAccent: Color,
    val accentWash: Color,   // 琥珀の薄い面(選択中の丸薬など)
    val glow: Color,         // 灯り・日差しの色
    val shadow: Color,       // 影の色(黒ではなく少し青みを含める)
    val edge: Color,         // 面の上端のハイライト
    val washTop: Color,      // 壁のグラデーション 左上
    val washBottom: Color,   // 壁のグラデーション 右下
    val moss: Color,         // 肯定(受取・一致)
    val danger: Color,       // 警告・削除
    val series: List<Color>, // グラフ・カテゴリ用の色(8色)
    val isDark: Boolean,
)

val LightAppColors = AppColors(
    paper = Color(0xFFF3F1EC),
    paperRaised = Color(0xFFFFFDFA),
    ink = Color(0xFF1F2329),
    inkSoft = Color(0xFF525C6B),
    inkFaint = Color(0xFF8B94A1),
    line = Color(0xFFE2DFD8),
    accent = Color(0xFFB85C00),
    accentTop = Color(0xFFD9780F),
    onAccent = Color(0xFFFFF8EE),
    accentWash = Color(0xFFFBE7CC),
    glow = Color(0xFFFFB84D),
    shadow = Color(0xFF22304A),
    edge = Color(0xFFFFFFFF),
    washTop = Color(0xFFFBF3E2),
    washBottom = Color(0xFFE8EDF2),
    moss = Color(0xFF2F7D57),
    danger = Color(0xFFB3261E),
    series = listOf(
        Color(0xFFE0892B), Color(0xFF3B6FB6), Color(0xFF3E9B7A), Color(0xFFC75B7A),
        Color(0xFF7A63C9), Color(0xFF2E9CB8), Color(0xFF8FA33A), Color(0xFF7B8794),
    ),
    isDark = false,
)

val DarkAppColors = AppColors(
    paper = Color(0xFF0E1422),
    paperRaised = Color(0xFF182033),
    ink = Color(0xFFEAEFF7),
    inkSoft = Color(0xFFA7B1C3),
    inkFaint = Color(0xFF707B8E),
    line = Color(0xFF263046),
    accent = Color(0xFFFFB84D),
    accentTop = Color(0xFFFFCB75),
    onAccent = Color(0xFF231500),
    accentWash = Color(0xFF3A2D17),
    glow = Color(0xFFFFB84D),
    shadow = Color(0xFF000000),
    edge = Color(0xFFFFFFFF),
    washTop = Color(0xFF182139),
    washBottom = Color(0xFF0A0F1B),
    moss = Color(0xFF7FD1A0),
    danger = Color(0xFFFF8A80),
    series = listOf(
        Color(0xFFFFB84D), Color(0xFF7FA8E8), Color(0xFF6FD1AE), Color(0xFFF08FAA),
        Color(0xFFB4A2F0), Color(0xFF6FCBE0), Color(0xFFC2D470), Color(0xFFA8B2C0),
    ),
    isDark = true,
)

/** 文字スタイル。すべてゴシック(Sans)。大きな数字は細く軽く、名前は太めにして強弱をつける。 */
@Immutable
data class AppType(
    val display: TextStyle,     // 画面の巨大見出し・月・日の数字
    val headline: TextStyle,    // 画面見出し
    val title: TextStyle,       // 物の名前(店名・日付・品名)
    val body: TextStyle,        // 本文
    val prose: TextStyle,       // 日記の書き味(ゆったり)
    val label: TextStyle,       // 小さな見出し・注記
    val caption: TextStyle,     // 補足
    val numeralXL: TextStyle,   // 主役の金額
    val numeralL: TextStyle,    // 大きめの金額
    val numeral: TextStyle,     // 行内の金額
)

private const val tnum = "tnum"

val DefaultAppType = AppType(
    display = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 60.sp, lineHeight = 64.sp, letterSpacing = (-1.5).sp),
    headline = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp),
    title = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    prose = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 30.sp),
    label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
    caption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    numeralXL = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1).sp, fontFeatureSettings = tnum),
    numeralL = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = tnum),
    numeral = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp, fontFeatureSettings = tnum),
)

/** 余白は5段階+画面端。直値のdpは使わない。 */
object Space {
    val hair: Dp = 1.dp
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 16.dp
    val l: Dp = 28.dp
    val xl: Dp = 48.dp
    val edge: Dp = 24.dp  // 画面の左右余白
}

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
val LocalAppType = staticCompositionLocalOf { DefaultAppType }

object AppTheme {
    val colors: AppColors @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppType @Composable @ReadOnlyComposable get() = LocalAppType.current
}

@Composable
fun appColorsForSystem(): AppColors = if (isSystemInDarkTheme()) DarkAppColors else LightAppColors
