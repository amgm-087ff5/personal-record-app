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
 * テーマ「紙と墨」。手帳・帳簿の紙面を意識した配色と、明朝(Serif)×ゴシック(Sans)の組み合わせ。
 * Material の標準色・動的カラーは使わない。画面は AppTheme.colors / AppTheme.type / Space から値を取る。
 */
@Immutable
data class AppColors(
    val paper: Color,        // 画面の地
    val paperRaised: Color,  // 一段浮いた面(シート、スリップ)
    val ink: Color,          // 主文字
    val inkSoft: Color,      // 補足文字
    val inkFaint: Color,     // 注記・プレースホルダ
    val line: Color,         // 罫線
    val accent: Color,       // 朱。主要操作・強調
    val onAccent: Color,
    val accentWash: Color,   // 朱の薄い面
    val moss: Color,         // 肯定(受取・一致)
    val danger: Color,       // 警告・削除
    val series: List<Color>, // グラフ・カテゴリ用の抑えた色(8色)
    val isDark: Boolean,
)

val LightAppColors = AppColors(
    paper = Color(0xFFF5F0E6),
    paperRaised = Color(0xFFFBF8F1),
    ink = Color(0xFF1F1B16),
    inkSoft = Color(0xFF5E574B),
    inkFaint = Color(0xFF9A917F),
    line = Color(0xFFD9CFBB),
    accent = Color(0xFFB8432B),
    onAccent = Color(0xFFFFF8EE),
    accentWash = Color(0xFFF0D9CF),
    moss = Color(0xFF4F6B4A),
    danger = Color(0xFF9E2A2B),
    series = listOf(
        Color(0xFFB8432B), Color(0xFF2F4A6B), Color(0xFF6B7F3A), Color(0xFFC08A2E),
        Color(0xFF7A4E7E), Color(0xFF2F7F7A), Color(0xFF8A6A4A), Color(0xFF6F6A60),
    ),
    isDark = false,
)

val DarkAppColors = AppColors(
    paper = Color(0xFF15130F),
    paperRaised = Color(0xFF1E1B16),
    ink = Color(0xFFECE4D3),
    inkSoft = Color(0xFFB5AC98),
    inkFaint = Color(0xFF7C7566),
    line = Color(0xFF33302A),
    accent = Color(0xFFE06A4E),
    onAccent = Color(0xFF1A0F0B),
    accentWash = Color(0xFF3A2420),
    moss = Color(0xFF8FB387),
    danger = Color(0xFFE57373),
    series = listOf(
        Color(0xFFE06A4E), Color(0xFF7FA3D1), Color(0xFFA9C26B), Color(0xFFE0B35A),
        Color(0xFFC59AC9), Color(0xFF6FC4BE), Color(0xFFC9A57F), Color(0xFFA8A296),
    ),
    isDark = true,
)

/** 文字スタイル。見出し・数字は明朝、本文・注記はゴシック。数字は等幅(tnum)。 */
@Immutable
data class AppType(
    val display: TextStyle,     // 画面の巨大見出し・月・日の数字
    val headline: TextStyle,    // 画面見出し
    val title: TextStyle,       // 物の名前(店名・日付・品名)
    val body: TextStyle,        // 本文(ゴシック)
    val prose: TextStyle,       // 日記の書き味(明朝・ゆったり)
    val label: TextStyle,       // 小さな見出し・注記(字間広め)
    val caption: TextStyle,     // 補足
    val numeralXL: TextStyle,   // 主役の金額
    val numeralL: TextStyle,    // 大きめの金額
    val numeral: TextStyle,     // 行内の金額
)

private const val tnum = "tnum"

val DefaultAppType = AppType(
    display = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
    headline = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    title = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 27.sp),
    body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    prose = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 31.sp),
    label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
    caption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    numeralXL = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp, fontFeatureSettings = tnum),
    numeralL = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = tnum),
    numeral = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, fontFeatureSettings = tnum),
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
