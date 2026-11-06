package com.amgm.personallog.v4.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.amgm.personallog.v4.R

/*
 * トークン — v4/css/tokens.css を1対1で移したもの。名前は CSS 変数の -- を外してキャメルケースにした
 * (--surface-2 → surface2, --ev-hi → evHi)。1つの値はここで1回だけ定義する。
 * 使い方: val c = V4.c; Modifier.background(c.surface)
 */
@Immutable
data class V4Colors(
    val dark: Boolean,
    val bg: Color, val surface: Color, val surface2: Color, val sunk: Color, val line: Color, val lineStrong: Color,
    val tx: Color, val tx2: Color, val tx3: Color, val txInv: Color,
    val brand: Color, val brandInk: Color, val brandSoft: Color, val brandPress: Color,
    val cActivity: Color, val cActivitySoft: Color, val cSchedule: Color, val cScheduleSoft: Color,
    val cTodo: Color, val cTodoSoft: Color, val cEntity: Color, val cEntitySoft: Color,
    val cNote: Color, val cNoteSoft: Color, val cType: Color, val cTypeSoft: Color, val cExpense: Color, val cExpenseSoft: Color,
    val axis: Color, val axisPast: Color, val now: Color, val plan: Color,
    val warn: Color, val warnSoft: Color, val danger: Color, val dangerSoft: Color, val ok: Color, val okSoft: Color,
    val special: Color, val specialSoft: Color,
    val fillAct: Color, val fillPay: Color, val fillBrand: Color, val fillPlan: Color,
    val me: Color, val meSoft: Color, val meOnEv: Color,
    val kin: Color, val kinSoft: Color,
    val late: Color, val lateSoft: Color,
    val owe: Color, val oweText: Color, val oweSoft: Color,
    val ev: Color, val evHi: Color, val evLo: Color, val evSoft: Color,
    val meFill: Color, val kinFill: Color, val lateFill: Color,
    val dcardBg: Color, val glow: Color, val glowHi: Color, val tlCard: Color, val tlBg1: Color, val tlBg2: Color,
    val dateSoft: Color, val dateInk: Color,
    /** 予定の点線の色 = plan を 45%(ダーク 55%) */
    val planLn: Color,
    /** --bg-grad。ダークは無し(bg の単色) */
    val bgGrad: Brush,
) {
    val onMe: Color get() = Color.White
    val onKin: Color get() = Color.White
    val onLate: Color get() = Color.White
    val onCap: Color get() = Color.White
}

private fun hex(s: String): Color = Color(android.graphics.Color.parseColor(s))
fun colorOf(hexStr: String): Color = hex(hexStr)

val LightColors = run {
    val me = hex("#2D59B8"); val kin = hex("#13838A"); val late = hex("#D3393B"); val plan = hex("#7A8496"); val ev = hex("#5A6C8D")
    V4Colors(
        dark = false,
        bg = hex("#F1F5FC"), surface = hex("#FAFBFD"), surface2 = hex("#F4F6FA"), sunk = hex("#E5E9F0"), line = hex("#E4E8EF"), lineStrong = hex("#DDE2EA"),
        tx = hex("#34405A"), tx2 = hex("#4F5C74"), tx3 = hex("#6A768C"), txInv = Color.White,
        brand = me, brandInk = Color.White, brandSoft = hex("#E5ECFA"), brandPress = hex("#244A9A"),
        cActivity = hex("#23879A"), cActivitySoft = hex("#E2F2F4"), cSchedule = hex("#3C7BD0"), cScheduleSoft = hex("#E5EEFA"),
        cTodo = hex("#C03C6B"), cTodoSoft = hex("#FAE6EE"), cEntity = hex("#2D59B8"), cEntitySoft = hex("#E7EDF9"),
        cNote = hex("#3A8A5B"), cNoteSoft = hex("#E3F2E9"), cType = hex("#56627A"), cTypeSoft = hex("#ECEFF4"), cExpense = hex("#3D4F8F"), cExpenseSoft = hex("#E7EAF4"),
        axis = hex("#D8DEE8"), axisPast = hex("#CCD4DF"), now = late, plan = plan,
        warn = hex("#C76A12"), warnSoft = hex("#FFF5D6"), danger = late, dangerSoft = hex("#FBE7E7"), ok = hex("#2E8A57"), okSoft = hex("#E2F3E9"),
        special = hex("#7457B3"), specialSoft = hex("#EFEAF8"),
        fillAct = hex("#1F7F91"), fillPay = hex("#33437A"), fillBrand = hex("#2D59B8"), fillPlan = hex("#3C7BD0"),
        me = me, meSoft = hex("#E5ECFA"), meOnEv = hex("#7FA3F0"),
        kin = kin, kinSoft = hex("#DDF0F0"),
        late = late, lateSoft = hex("#FBE6E6"),
        owe = hex("#B86A0E"), oweText = hex("#34405A"), oweSoft = hex("#F4F6FA"),
        ev = ev, evHi = hex("#5F77A2"), evLo = hex("#45577D"), evSoft = hex("#E6EAF1"),
        meFill = hex("#3560BC"), kinFill = kin, lateFill = late,
        dcardBg = Color.White, glow = hex("#9CC4FF"), glowHi = hex("#CFE2FF"), tlCard = Color.White, tlBg1 = hex("#F4F7FD"), tlBg2 = hex("#EAF1FD"),
        dateSoft = hex("#E3EEFF"), dateInk = hex("#2D59B8"),
        planLn = plan.copy(alpha = 0.45f),
        bgGrad = Brush.verticalGradient(0f to hex("#F4F7FD"), 0.55f to hex("#EAF1FD"), 1f to hex("#F4F7FD")),
    )
}

val DarkColors = run {
    val bg = hex("#121A2A"); val surface = hex("#1A2335"); val plan = hex("#8D97A8")
    V4Colors(
        dark = true,
        bg = bg, surface = surface, surface2 = hex("#1E283B"), sunk = hex("#0E1522"), line = hex("#28324A"), lineStrong = hex("#323C55"),
        tx = hex("#E4E9F1"), tx2 = hex("#B8C1CF"), tx3 = hex("#8E99AB"), txInv = Color.White,
        brand = hex("#7FA2EC"), brandInk = Color.White, brandSoft = hex("#1E2A44"), brandPress = hex("#9BB7F2"),
        cActivity = hex("#5BC0D0"), cActivitySoft = hex("#14313A"), cSchedule = hex("#7FAEEB"), cScheduleSoft = hex("#18283F"),
        cTodo = hex("#EB7AA2"), cTodoSoft = hex("#3A1C28"), cEntity = hex("#7FA2EC"), cEntitySoft = hex("#1E2A44"),
        cNote = hex("#6CC290"), cNoteSoft = hex("#16301F"), cType = hex("#9CA8BE"), cTypeSoft = hex("#232A36"), cExpense = hex("#97A6DA"), cExpenseSoft = hex("#1F2640"),
        axis = hex("#2E3747"), axisPast = hex("#3D475A"), now = hex("#FF6B60"), plan = plan,
        warn = hex("#EFA35A"), warnSoft = hex("#1E283B"), danger = hex("#F07A7A"), dangerSoft = hex("#3B1A1A"), ok = hex("#6CC290"), okSoft = hex("#16301F"),
        special = hex("#B39CEB"), specialSoft = hex("#2A2340"),
        fillAct = hex("#1D6B7A"), fillPay = hex("#3A4A85"), fillBrand = hex("#3760C0"), fillPlan = hex("#3C74C4"),
        me = hex("#7FA3F0"), meSoft = hex("#1E2A44"), meOnEv = hex("#7FA3F0"),
        kin = hex("#4FC1C6"), kinSoft = hex("#123236"),
        late = hex("#FF6B66"), lateSoft = hex("#3B1A1A"),
        owe = hex("#E9A24E"), oweText = hex("#E4E9F1"), oweSoft = hex("#1E283B"),
        ev = hex("#5B6E94"), evHi = hex("#566C97"), evLo = hex("#3B4B6C"), evSoft = hex("#232B3A"),
        meFill = hex("#3D66C8"), kinFill = hex("#1E8389"), lateFill = hex("#C9403C"),
        dcardBg = surface, glow = hex("#A9CCFF"), glowHi = hex("#DCEAFF"), tlCard = surface, tlBg1 = bg, tlBg2 = bg,
        dateSoft = hex("#1E2A44"), dateInk = hex("#A9CCFF"),
        planLn = plan.copy(alpha = 0.55f),
        bgGrad = Brush.verticalGradient(listOf(bg, bg)),
    )
}

/** 文字 — IBM Plex Sans JP(400/500/600/700。800 は 700 で代用) */
val Plex = FontFamily(
    Font(R.font.plex_400, FontWeight.Normal),
    Font(R.font.plex_500, FontWeight.Medium),
    Font(R.font.plex_600, FontWeight.SemiBold),
    Font(R.font.plex_700, FontWeight.Bold),
    Font(R.font.plex_700, FontWeight.ExtraBold),
)

/** tokens.css の文字(--t-display など)。px は sp に1対1 */
@Immutable
data class V4Type(
    val display: TextStyle = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 1.2.em),
    val title: TextStyle = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 1.3.em),
    val head: TextStyle = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 1.4.em),
    val body: TextStyle = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 1.55.em),
    val label: TextStyle = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 1.4.em),
    val cap: TextStyle = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 1.4.em),
)

/** 形と寸法(tokens.css) */
object V4Dim {
    val rXs = 6.dp; val rSm = 10.dp; val rMd = 14.dp; val rLg = 20.dp
    val gutter = 20.dp; val row = 56.dp; val tap = 44.dp; val navH = 64.dp
    /** 画面の一番下の余白(ナビ64+96) */
    val pageEnd = 160.dp
}

/** 任意の大きさ・太さの Plex(fontSize は px=sp) */
fun plex(size: Int, weight: Int = 400, lineHeight: Double? = null): TextStyle = TextStyle(
    fontFamily = Plex,
    fontWeight = FontWeight(weight.coerceAtMost(700)),
    fontSize = size.sp,
    lineHeight = lineHeight?.em ?: androidx.compose.ui.unit.TextUnit.Unspecified,
)

val LocalV4Colors = staticCompositionLocalOf { LightColors }
val LocalV4Type = staticCompositionLocalOf { V4Type() }

object V4 {
    val c: V4Colors @Composable get() = LocalV4Colors.current
    val t: V4Type @Composable get() = LocalV4Type.current
}

/** theme: system / light / dark */
@Composable
fun V4Theme(theme: String = "system", content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    CompositionLocalProvider(LocalV4Colors provides if (dark) DarkColors else LightColors, LocalV4Type provides V4Type()) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme(),
        ) { content() }
    }
}
