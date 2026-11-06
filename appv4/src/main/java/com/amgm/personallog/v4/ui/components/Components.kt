package com.amgm.personallog.v4.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amgm.personallog.v4.data.Activity
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Expense
import com.amgm.personallog.v4.data.catColor
import com.amgm.personallog.v4.data.catPath
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.PrimaryButton
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.colorOf
import com.amgm.personallog.v4.ui.lightBrush
import com.amgm.personallog.v4.ui.plex

/*
 * 共通部品 — v4/js/ui.js の部品(HTML を返す関数)を Composable にしたもの。名前は ui.js に合わせた。
 * 寸法・色は /home/claude/specs/components.md(app.css・ticket.css・summary.css の最終値)。
 * 画面はこの部品を組み合わせて作る。部品の見た目を画面側で上書きしない(同じ意味=同じ見た目)。
 */

/* ---------------- 押した時の手応え(縮小 .985 + 押下中の面) ---------------- */

/** 押せる要素。押下中は 98.5% に縮む(DESIGN v4「押した時に一瞬沈む」) */
fun Modifier.press(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.985f else 1f, label = "press")
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(src, null, enabled = enabled, role = role, onClick = onClick)
}

/** 角丸の破線の枠(予定の点線・半券の切り取り線など)。dash/gap は dp、cap は丸 */
fun Modifier.dashedBorder(color: Color, width: Dp = 1.25.dp, radius: Dp = 0.dp, dash: Dp = 3.dp, gap: Dp = 6.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx() + w))),
    )
}

/** 塗りの面(--light + 色)。どの塗りもこれ(光=左上の丸いグラデのみ) */
fun Modifier.fill(color: Color, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(0.dp)): Modifier =
    this.clip(shape).background(color).background(lightBrush())

/** 影(--e1)。小さく柔らかい */
fun Modifier.e1(shape: androidx.compose.ui.graphics.Shape): Modifier = this.shadow(2.dp, shape, ambientColor = Color(0x14283A5A), spotColor = Color(0x14283A5A))
/** 影(--tl-sh)。タイムラインの白いカード */
fun Modifier.tlShadow(shape: androidx.compose.ui.graphics.Shape): Modifier = this.shadow(6.dp, shape, ambientColor = Color(0x293C64B4), spotColor = Color(0x293C64B4))

/* ---------------- 画面の枠 ---------------- */

/**
 * 1画面の縦スクロールの枠。上はステータスバーの下から、下はナビに隠れない余白(--page-end)。
 * 中身は Column。topBar は固定の上部(appbar など)を置く所(スクロールしない)。
 */
@Composable
fun Screen(
    scroll: ScrollState = rememberScrollState(),
    topBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        topBar?.invoke()
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            content()
            Spacer(Modifier.height(V4Dim.pageEnd))
        }
    }
}

/** appbar: 戻る+題名+右の操作(詳細・サブ画面の上部)。高さ56、背景は --bg 70% */
class BarAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
fun AppBar(title: String, back: Boolean = true, actions: List<BarAction> = emptyList()) {
    val app = LocalApp.current
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(c.bg.copy(alpha = 0.7f)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (back) IconBtn(V4Icons.back, "戻る") { app.goBack() }
        Text(title, style = V4.t.head, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        actions.forEach { IconBtn(it.icon, it.label, onClick = it.onClick) }
    }
}

/** 44×44 の丸いアイコンボタン(.iconbtn)。アイコン22、色 --tx-2 */
@Composable
fun IconBtn(icon: ImageVector, label: String, tint: Color = V4.c.tx2, size: Dp = 22.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size)) }
}

/** largeTitle の右の操作。text があれば小ボタン(primary=塗り / それ以外=ghost)、無ければアイコンだけ */
class TitleAction(val text: String? = null, val icon: ImageVector? = null, val label: String = text ?: "", val primary: Boolean = false, val onClick: () -> Unit)

/** タブの一番上の見出し(.lt): padding 12 20 4、h1 = 700 28px/1.2 */
@Composable
fun LargeTitle(title: String, actions: List<TitleAction> = emptyList()) {
    val c = V4.c
    Row(Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = V4.t.display, color = c.tx, modifier = Modifier.weight(1f))
        actions.forEach { a ->
            when {
                a.text != null && a.primary -> SmallButton(a.text, a.icon, onClick = a.onClick)
                a.text != null -> GhostButton(a.text, a.icon, onClick = a.onClick)
                a.icon != null -> Box(Modifier.offset(x = 10.dp)) { IconBtn(a.icon, a.label, onClick = a.onClick) }
            }
        }
    }
}

/** 節の見出し(.sh): padding 32 20 12、700 20px、件数 500 15 --tx-3、右の操作 --t-label --brand */
@Composable
fun SectionHead(title: String, n: String? = null, actLabel: String? = null, actIcon: ImageVector? = null, topPad: Dp = 32.dp, onAct: (() -> Unit)? = null) {
    val c = V4.c
    Row(Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = topPad, bottom = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = plex(20, 700, 1.3), color = c.tx)
        if (!n.isNullOrEmpty()) Text(n, style = plex(15, 500), color = c.tx3, modifier = Modifier.padding(bottom = 2.dp))
        Spacer(Modifier.weight(1f))
        if (actLabel != null && onAct != null) Row(
            Modifier.heightIn(min = 32.dp).clickable(onClick = onAct).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            actIcon?.let { Icon(it, null, tint = c.brand, modifier = Modifier.size(16.dp)) }
            Text(actLabel, style = V4.t.label, color = c.brand)
        }
    }
}

/* ---------------- list / row / prop / objs ---------------- */

/** 枠線の面(.list): 左右20、背景 --surface、枠 1px --line、角丸14。中の行は区切り線で分ける */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = V4.c
    Column(
        modifier.padding(horizontal = V4Dim.gutter).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(14.dp)),
        content = content,
    )
}

/** 区切り線(row の上)。left = 左からの開始(row=54、prop/flat=14) */
@Composable
fun Divider(left: Dp = 54.dp) { Box(Modifier.padding(start = left).fillMaxWidth().height(1.dp).background(V4.c.line)) }

/**
 * 1行(.row): 高さ56以上、padding 10 14、gap 12。time=左の時刻(幅40)、lead=左の部品、t=題名、s=補足、v=右の値(amt=太字)、chev=›。
 * 2行目以降で divider=true にすると上に区切り線(左54から)。
 */
@Composable
fun V4Row(
    t: String,
    s: String? = null,
    v: String? = null,
    time: String? = null,
    amt: Boolean = false,
    chev: Boolean = false,
    wrap: Boolean = false,
    divider: Boolean = false,
    dividerLeft: Dp = 54.dp,
    tColor: Color = V4.c.tx,
    lead: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    sContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = V4.c
    Column {
        if (divider) Divider(dividerLeft)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).let { if (onClick != null) it.press(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (time != null) Text(time, style = plex(13, 500).copy(fontFeatureSettings = "tnum"), color = c.tx2, modifier = Modifier.width(40.dp))
            lead?.invoke()
            Column(Modifier.weight(1f)) {
                Text(t, style = plex(15, 500, 1.4), color = tColor, maxLines = if (wrap) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                if (sContent != null) Box(Modifier.padding(top = 2.dp)) { sContent() }
                else if (!s.isNullOrEmpty()) Text(s, style = V4.t.cap, color = c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            if (v != null) Text(v, style = plex(15, if (amt) 600 else 500, 1.4).copy(fontFeatureSettings = "tnum"), color = c.tx, textAlign = TextAlign.End)
            trailing?.invoke()
            if (chev) Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
    }
}

/** 属性の行(.prop): k(幅84・--t-label --tx-3) + 値(--t-body) + meta。2つ目以降は divider=true */
@Composable
fun Prop(k: String, v: String? = null, meta: String? = null, divider: Boolean = false, onClick: (() -> Unit)? = null, value: (@Composable RowScope.() -> Unit)? = null) {
    val c = V4.c
    Column {
        if (divider) Divider(14.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).let { if (onClick != null) it.press(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(k, style = V4.t.label, color = c.tx3, modifier = Modifier.width(84.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (value != null) value() else Text(v ?: "", style = V4.t.body, color = c.tx)
            }
            if (meta != null) Text(meta, style = V4.t.cap, color = c.tx3)
        }
    }
}

/** 独立した面の並び(.objs): 1件=1つの白い面(角丸14・影 e1)。gap 8、左右20 */
@Composable
fun Objs(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = V4Dim.gutter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** objs の中の1つの面 */
@Composable
fun ObjCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().e1(shape).clip(shape).background(V4.c.surface).let { if (onClick != null) it.press(onClick = onClick) else it },
        content = content,
    )
}

/* ---------------- tag / catTag ---------------- */

enum class TagKind { NONE, WARN, LATE, DANGER, OK, BRAND, SPECIAL }

/** データに付いた分類の名前(.tag): 高さ24・角丸6・--sunk・500 12px。color があれば左に 14×6 の色の帯 */
@Composable
fun Tag(label: String, color: Color? = null, kind: TagKind = TagKind.NONE, onClick: (() -> Unit)? = null) {
    val c = V4.c
    val (bg, fg) = when (kind) {
        TagKind.WARN -> c.oweSoft to c.oweText
        TagKind.LATE -> c.lateSoft to c.late
        TagKind.DANGER -> c.dangerSoft to c.danger
        TagKind.OK -> c.okSoft to c.ok
        TagKind.BRAND -> c.brandSoft to c.brand
        TagKind.SPECIAL -> c.sunk to c.tx
        TagKind.NONE -> c.sunk to c.tx2
    }
    Row(
        Modifier.height(24.dp).clip(RoundedCornerShape(6.dp)).background(bg).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (kind == TagKind.WARN) Box(Modifier.size(14.dp, 6.dp).clip(RoundedCornerShape(3.dp)).hatch(c.owe))
        if (color != null) Box(Modifier.size(14.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = plex(12, 500), color = fg, maxLines = 1, softWrap = false)
    }
}

/** カテゴリのタグ(色の帯+パス「食費 › 外食」)。path=false なら名前だけ */
@Composable
fun CatTag(k: String, path: Boolean = true) {
    val d = LocalData.current
    Tag(if (path) d.catPath(k) else (d.cats[k]?.n ?: k), color = colorOf(d.catColor(k)))
}

/** 琥珀の斜線(未精算)。線だけ琥珀、間は透明。hw=線の太さ */
fun Modifier.hatch(color: Color, hw: Dp = 2.dp): Modifier = drawBehind {
    val w = hw.toPx(); val step = w * 2 * 1.4142f
    clipRect {
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = w)
            x += step
        }
    }
}

/* ---------------- chips / seg ---------------- */

/** 絞り込みのピル(.chips): 横スクロール1段、高さ32、選択中 --me-soft+--me 600。items = (値, 文字, 件数) */
@Composable
fun Chips(items: List<Triple<String, String, Int?>>, cur: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = V4Dim.gutter, end = V4Dim.gutter, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { items.forEach { (v, l, n) -> Pill(l, v == cur, n) { onPick(v) } } }
}

/** 切替(.seg): chips と同じ見た目のピル(左右20、下12)。items = (値, 文字) */
@Composable
fun Seg(items: List<Pair<String, String>>, cur: String, modifier: Modifier = Modifier, padH: Dp = V4Dim.gutter, onPick: (String) -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = padH, end = padH, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { items.forEach { (v, l) -> Pill(l, v == cur) { onPick(v) } } }
}

/** 1つのピル(操作)。高さ32・左右14 */
@Composable
fun Pill(label: String, selected: Boolean, n: Int? = null, onClick: () -> Unit) {
    val c = V4.c
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(if (selected) c.meSoft else c.sunk).press(onClick = onClick).padding(horizontal = 14.dp)
            .semantics { contentDescription = label + if (selected) "(選択中)" else "" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = plex(13, if (selected) 600 else 500), color = if (selected) c.me else c.tx2, maxLines = 1, softWrap = false)
        if (n != null) Text("$n", style = plex(13, 400), color = (if (selected) c.me else c.tx2).copy(alpha = 0.7f))
    }
}

/* ---------------- toggle / checkbox ---------------- */

/** スイッチ(.toggle): 48×28、オンで --me */
@Composable
fun Toggle(on: Boolean, onClick: () -> Unit) {
    val c = V4.c
    val x by animateFloatAsState(if (on) 20f else 0f, label = "tg")
    Box(Modifier.size(48.dp, 28.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.me else c.lineStrong).clickable(role = Role.Switch, onClick = onClick)) {
        Box(Modifier.offset(x = (3 + x).dp, y = 3.dp).size(22.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

/** チェック欄(.check): 24×24 角丸7、枠1.5 --tx-3。完了=出来事の青灰の塗り+白いチェック。lg=30×30 角丸9 */
@Composable
fun CheckBox(on: Boolean, lg: Boolean = false, onClick: () -> Unit) {
    val c = V4.c
    val sz = if (lg) 30.dp else 24.dp; val r = if (lg) 9.dp else 7.dp
    Box(Modifier.size(44.dp).clickable(role = Role.Checkbox, onClick = onClick).semantics { contentDescription = if (on) "完了" else "未完了" }, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(sz).clip(RoundedCornerShape(r))
                .let { if (on) it.background(c.ev).background(lightBrush()) else it.background(c.surface).border(1.5.dp, c.tx3, RoundedCornerShape(r)) },
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(V4Icons.check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

/* ---------------- empty / ico / banner / suggest ---------------- */

/** 小さいアイコンの台(.ico): 32×32 角丸10、色の13%の面にアイコン18 */
@Composable
fun Ico(icon: ImageVector, color: Color = V4.c.tx2, onFill: Boolean = false) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (onFill) Color.White.copy(alpha = 0.18f) else color.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (onFill) Color.White else color, modifier = Modifier.size(18.dp)) }
}

/** 空の表示(.empty): 「○○ なし」の短いラベル。padding 36 32 */
@Composable
fun Empty(icon: ImageVector, text: String, btnLabel: String? = null, onBtn: (() -> Unit)? = null) {
    val c = V4.c
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Ico(icon)
        Text(text, style = V4.t.label, color = c.tx2, textAlign = TextAlign.Center)
        if (btnLabel != null && onBtn != null) TonalButton(btnLabel, small = true, onClick = onBtn)
    }
}

/** お知らせ(.banner): warn=未精算の面+琥珀のアイコン / info=--brand-soft */
@Composable
fun Banner(warn: Boolean, text: String) {
    val c = V4.c
    Row(
        Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (warn) c.oweSoft else c.brandSoft).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (warn) V4Icons.warn else V4Icons.info, null, tint = if (warn) c.owe else c.brand, modifier = Modifier.size(20.dp))
        Text(text, style = V4.t.label, color = c.tx, modifier = Modifier.weight(1f))
    }
}

/** サジェスト+ボタン1つ(.suggest) */
@Composable
fun Suggest(text: String, btn: String, onClick: () -> Unit) {
    val c = V4.c
    Row(
        Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.brandSoft).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = V4.t.label, color = c.tx, modifier = Modifier.weight(1f))
        SmallButton(btn, onClick = onClick)
    }
}

/* ---------------- ボタン ---------------- */

/** 小さい主ボタン(.btn.sm): 高さ36・左右14・14px */
@Composable
fun SmallButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = V4.c
    Row(
        Modifier.height(36.dp).graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .shadow(4.dp, RoundedCornerShape(10.dp), ambientColor = c.meFill, spotColor = c.meFill)
            .fill(c.meFill, RoundedCornerShape(10.dp)).press(enabled, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        Text(text, style = plex(14, 600), color = Color.White, maxLines = 1)
    }
}

/** 淡い塗りのボタン(.btn.tonal): --me-soft+--me */
@Composable
fun TonalButton(text: String, icon: ImageVector? = null, small: Boolean = false, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = V4.c
    Row(
        modifier.height(if (small) 36.dp else 48.dp).graphicsLayer { alpha = if (enabled) 1f else 0.45f }.clip(RoundedCornerShape(10.dp)).background(c.meSoft)
            .press(enabled, onClick = onClick).padding(horizontal = if (small) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let { Icon(it, null, tint = c.me, modifier = Modifier.size(18.dp)) }
        Text(text, style = plex(if (small) 14 else 15, 600), color = c.me, maxLines = 1)
    }
}

/** 枠なしの文字ボタン(.btn.ghost): 高さ40・--me */
@Composable
fun GhostButton(text: String, icon: ImageVector? = null, color: Color = V4.c.me, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).press(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(18.dp)) }
        Text(text, style = plex(15, 600), color = color, maxLines = 1)
    }
}

/** 削除などの操作(赤は使わない: 文字の色+ごみ箱の印。DESIGN「赤を使わない所」) */
@Composable
fun DeleteButton(text: String = "削除", modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = V4.c
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(10.dp)).background(c.sunk).press(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(V4Icons.trash, null, tint = c.tx2, modifier = Modifier.size(18.dp))
        Text(text, style = plex(15, 600), color = c.tx2)
    }
}

/** 主ボタン(塗り)。Chrome.kt の PrimaryButton と同じ */
@Composable
fun MainButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) = PrimaryButton(text, enabled, modifier, onClick = onClick)

/** ボタンの並び(.btnrow): padding 12 20、gap 8、各ボタンは等分 */
@Composable
fun BtnRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/* ---------------- 入力 ---------------- */

/**
 * 入力の行(.field): ラベル(幅84・--t-label --tx-3)+入力(--t-body)。高さ52以上。list の中で使う。
 * amount=true で 700 22px 右揃え・数字キーボード。multiline で textarea(80以上)。
 */
@Composable
fun Field(
    label: String?,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String = "",
    divider: Boolean = false,
    amount: Boolean = false,
    multiline: Boolean = false,
    number: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = V4.c
    Column {
        if (divider) Divider(14.dp)
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (label != null) Text(label, style = V4.t.label, color = c.tx3, modifier = Modifier.width(84.dp).padding(top = if (multiline) 10.dp else 0.dp))
            val style: TextStyle = if (amount) plex(22, 700).copy(textAlign = TextAlign.End, fontFeatureSettings = "tnum", color = c.tx) else V4.t.body.copy(color = c.tx)
            Box(Modifier.weight(1f).heightIn(min = if (multiline) 80.dp else 40.dp), contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.tx3), modifier = Modifier.fillMaxWidth().padding(vertical = if (multiline) 8.dp else 0.dp))
                BasicTextField(
                    value, onValue, textStyle = style, singleLine = !multiline,
                    cursorBrush = SolidColor(c.brand),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (amount || number) KeyboardType.Number else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().padding(vertical = if (multiline) 8.dp else 0.dp),
                )
            }
            trailing?.invoke()
        }
    }
}

/** 補足の小さい字(.hint): --t-cap --tx-3、padding 6 20 0 */
@Composable
fun Hint(text: String) { Text(text, style = V4.t.cap, color = V4.c.tx3, modifier = Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 6.dp)) }

/* ---------------- エンティティの印・参照 ---------------- */

/** エンティティの印 = 緑のドット(10、sm=8、lg=14)。未登録=中抜き */
@Composable
fun Knot(size: Dp = 10.dp, unreg: Boolean = false, color: Color = V4.c.kin) {
    Box(Modifier.size(size).clip(CircleShape).let { if (unreg) it.border(1.5.dp, color, CircleShape) else it.background(color) })
}

/** 関わった人の並び: ドット(8)+名前。gap 10 */
@Composable
fun Knots(ids: List<String>, color: Color = V4.c.tx2, style: TextStyle = V4.t.cap) {
    val d = LocalData.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ids.forEach { id ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Knot(8.dp); Text(d.entities[id]?.n ?: "", style = style, color = color, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** 名前つきの参照(eref): 登録済み=ドット+名前(押すとエンティティへ)、未登録=中抜きのドット+名前(押せない) */
@Composable
fun ERef(id: String?, name: String? = null) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val e = id?.let { d.entities[it] }
    Row(
        Modifier.heightIn(min = 32.dp).let { if (e != null) it.clickable { app.go("e", id!!) } else it },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Knot(unreg = e == null)
        Text(e?.n ?: (name ?: "未登録"), style = plex(15, 500, 1.3), color = if (e != null) c.tx else c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ---------------- 半券チケット(自分の支出) ---------------- */

enum class TicketSize { NORMAL, SM, LG }

/** 半券の中身 = 特別感(日常 / ✦非日常 / 未設定)。DESIGN v4.5 */
@Composable
fun SpStub(sp: String?, size: TicketSize = TicketSize.NORMAL) {
    val big = when (size) { TicketSize.SM -> 12; else -> 13 }
    when (sp) {
        "非日常" -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(V4Icons.sparkle, null, tint = Color.White, modifier = Modifier.size(when (size) { TicketSize.SM -> 13.dp; TicketSize.LG -> 24.dp; else -> 16.dp }))
            Text("非日常", style = plex(big, 700, 1.2), color = Color.White)
        }
        "日常" -> Text("日常", style = plex(big, 700, 1.2), color = Color.White)
        else -> Text("未設定", style = plex(12, 500, 1.2), color = Color.White.copy(alpha = 0.78f))
    }
}

/**
 * 半券チケット(.tkt-w): 左に半券(--me-fill の塗り・幅56)、上下に切れ込み、点線の切り取り線、本体に題名・補足・金額。
 * fill=true で全体が青の塗り(詳細の最上部)。size: SM(行・活動の中)/ LG(詳細の最上部)。
 */
@Composable
fun Ticket(
    t: String,
    stub: @Composable () -> Unit,
    s: (@Composable RowScope.() -> Unit)? = null,
    amt: String? = null,
    size: TicketSize = TicketSize.NORMAL,
    fill: Boolean = false,
    chev: Boolean = false,
    label: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = V4.c
    val stubW = when (size) { TicketSize.SM -> 50.dp; TicketSize.LG -> 68.dp; else -> 56.dp }
    val notch = when (size) { TicketSize.SM -> 6.dp; TicketSize.LG -> 10.dp; else -> 8.dp }
    val radius = when (size) { TicketSize.SM -> 12.dp; TicketSize.LG -> 18.dp; else -> 14.dp }
    val minH = when (size) { TicketSize.SM -> 52.dp; TicketSize.LG -> 112.dp; else -> 64.dp }
    val shape = TicketShape(radius, stubW, notch)
    val onC = if (fill) Color.White else c.tx
    Box(
        modifier.fillMaxWidth()
            .shadow(if (fill) 10.dp else if (size == TicketSize.SM) 3.dp else 5.dp, shape, ambientColor = Color(0x2E2D59B8), spotColor = Color(0x2E2D59B8))
            .clip(shape)
            .let { if (fill) it.background(c.meFill).background(lightBrush()) else it.background(c.tlCard) }
            .let { if (onClick != null) it.press(onClick = onClick) else it }
            .semantics { if (label != null) contentDescription = label },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = minH).height(IntrinsicSize.Min)) {
            Box(
                Modifier.width(stubW).fillMaxHeight()
                    .let { if (fill) it.background(Color.White.copy(alpha = 0.12f)) else it.background(c.meFill).background(lightBrush()) }
                    .padding(horizontal = if (size == TicketSize.SM) 2.dp else 4.dp, vertical = if (size == TicketSize.SM) 6.dp else 8.dp),
                contentAlignment = Alignment.Center,
            ) { stub() }
            // 切り取り線(縦の破線 1.5)
            Box(Modifier.width(1.5.dp).fillMaxHeight().drawBehind {
                drawLine(if (fill) Color.White.copy(alpha = 0.42f) else c.line, Offset(this.size.width / 2, 0f), Offset(this.size.width / 2, this.size.height), strokeWidth = this.size.width,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            })
            Row(
                Modifier.weight(1f).padding(
                    horizontal = when (size) { TicketSize.SM -> 10.dp; TicketSize.LG -> 18.dp; else -> 14.dp },
                    vertical = when (size) { TicketSize.SM -> 6.dp; TicketSize.LG -> 16.dp; else -> 10.dp },
                ),
                verticalAlignment = if (size == TicketSize.LG) Alignment.Top else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (size == TicketSize.SM) 8.dp else 10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(when (size) { TicketSize.SM -> 2.dp; TicketSize.LG -> 6.dp; else -> 3.dp })) {
                    Text(
                        t, color = onC, maxLines = 1, overflow = if (size == TicketSize.LG) TextOverflow.Visible else TextOverflow.Ellipsis,
                        style = when (size) { TicketSize.SM -> plex(14, 600, 1.3); TicketSize.LG -> plex(36, 700, 1.1).copy(fontFeatureSettings = "tnum"); else -> plex(15, 600, 1.35) },
                    )
                    if (s != null) {
                        val sc = if (fill) Color.White.copy(alpha = 0.82f) else c.tx3
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalContentColor provides sc,
                            androidx.compose.material3.LocalTextStyle provides plex(when (size) { TicketSize.SM -> 12; TicketSize.LG -> 14; else -> 13 }, 400, 1.35).copy(color = sc),
                        ) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { s() } }
                    }
                }
                if (amt != null) Text(amt, style = plex(if (size == TicketSize.SM) 15 else 17, 700).copy(fontFeatureSettings = "tnum"), color = onC, textAlign = TextAlign.End)
                if (chev) Icon(V4Icons.chev, null, tint = if (fill) Color.White.copy(alpha = 0.82f) else c.tx3, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** 半券の形: 角丸+半券の線(x=stub)の上下に半円の切れ込み */
class TicketShape(private val r: Dp, private val stub: Dp, private val notch: Dp) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        with(density) {
            val rr = r.toPx(); val x = stub.toPx(); val n = notch.toPx()
            val base = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(rr))) }
            val cut = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(x - n, -n, x + n, n))
                addOval(androidx.compose.ui.geometry.Rect(x - n, size.height - n, x + n, size.height + n))
            }
            return androidx.compose.ui.graphics.Outline.Generic(androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, base, cut))
        }
    }
}

/* ---------------- 支払いの棒・割合の棒 ---------------- */

/** 1件の支払いの棒(.pbar): 幅64・高さ6。自分=青 / ほかの人=--axis / 未精算=琥珀の斜線 */
@Composable
fun PayBar(a: Activity, x: Expense, width: Dp = 64.dp, height: Dp = 6.dp) {
    val c = V4.c
    val total = a.amt ?: x.amt
    val rest = total - x.amt
    val owed = a.adv != null && a.adv.state == "未精算"
    Row(
        Modifier.size(width, height).clip(RoundedCornerShape(3.dp)).background(c.sunk)
            .semantics { contentDescription = "支払い ${yen(total)} のうち自分 ${yen(x.amt)}" },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (x.amt > 0) Box(Modifier.weight(x.amt.toFloat()).fillMaxHeight().background(c.me).background(lightBrush()))
        if (rest > 0) Box(Modifier.weight(rest.toFloat()).fillMaxHeight().let { if (owed) it.hatch(c.owe) else it.background(c.axis) })
    }
}

class SplitSeg(val v: Int, val cls: String, val label: String, val onClick: (() -> Unit)? = null, val noAmt: Boolean = false)

/** 割合の棒+凡例(.split)。塗りの面(cap)の上で使う: 棒 高さ10・角丸5、自分=白、ほか=白45%、未精算=琥珀の斜線 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitBar(segs: List<SplitSeg>, onFill: Boolean = true) {
    val c = V4.c
    val fg = if (onFill) Color.White else c.tx
    Column(Modifier.padding(top = 16.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(fg.copy(alpha = 0.22f)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            segs.filter { it.v > 0 }.forEach { s ->
                Box(Modifier.weight(s.v.toFloat()).fillMaxHeight().let {
                    when (s.cls) { "me" -> it.background(fg); "owed" -> it.hatch(c.owe, 3.dp); else -> it.background(fg.copy(alpha = 0.45f)) }
                })
            }
        }
        FlowRow(Modifier.padding(top = 10.dp).graphicsLayer { alpha = 0.92f }, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            segs.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.let { m -> s.onClick?.let { m.clickable(onClick = it) } ?: m }) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).let {
                        when (s.cls) { "me" -> it.background(fg); "owed" -> it.background(c.owe); else -> it.background(fg.copy(alpha = 0.45f)) }
                    })
                    Text(s.label, style = V4.t.cap, color = fg)
                    if (!s.noAmt) Text(yen(s.v), style = plex(13, 600), color = fg)
                }
            }
        }
    }
}

/* ---------------- まとめカード ---------------- */

class Metric(val v: String, val l: String, val onClick: (() -> Unit)? = null)
class CardAction(val text: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/** まとめカード(.sc): 数字3つ(押せる数字は該当画面へ)+作成のボタン2つ(1つ目=塗り、2つ目=淡い塗り) */
@Composable
fun SummaryCard(metrics: List<Metric>, actions: List<CardAction>) {
    val c = V4.c
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 4.dp, bottom = 12.dp).fillMaxWidth().tlShadow(shape).clip(shape).background(c.tlCard)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
    ) {
        if (metrics.isNotEmpty()) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            metrics.forEachIndexed { i, m ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                Column(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).let { if (m.onClick != null) it.press(onClick = m.onClick) else it }.padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Text(m.v, style = plex(20, 600, 1.2).copy(fontFeatureSettings = "tnum"), color = c.tx, maxLines = 1, softWrap = false)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.l, style = plex(12, 500, 1.3), color = c.tx2, maxLines = 1, softWrap = false)
                        if (m.onClick != null) Icon(V4Icons.chev, null, tint = c.tx2, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        if (actions.isNotEmpty()) Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { i, a ->
                if (i == 0) Row(
                    Modifier.weight(1f).height(48.dp).shadow(6.dp, RoundedCornerShape(10.dp), ambientColor = c.meFill, spotColor = c.meFill).fill(c.meFill, RoundedCornerShape(10.dp)).press(onClick = a.onClick).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) { a.icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(18.dp)) }; Text(a.text, style = plex(14, 600), color = Color.White, maxLines = 1, softWrap = false) }
                else TonalButton(a.text, a.icon, modifier = Modifier.weight(1f), onClick = a.onClick)
            }
        }
    }
}

/* ---------------- 期間の移動 ---------------- */

/**
 * 期間の移動(periodNav): 白いピルの中に「前の月 / 10月 2026 / 次の月」。‹ › は使わない(戻る・開くの意味)。
 * prevLabel/nextLabel は「前の月」「次の月」または「前の日」「次の日」。
 */
@Composable
fun PeriodNav(label: String, prevLabel: String = "前の月", nextLabel: String = "次の月", modifier: Modifier = Modifier, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = V4.c
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxHeight().clickable(onClick = onPrev).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text(prevLabel, style = V4.t.label, color = c.brand) }
        Text(label, style = plex(15, 600).copy(fontFeatureSettings = "tnum"), color = c.tx, modifier = Modifier.padding(horizontal = 6.dp))
        Box(Modifier.fillMaxHeight().clickable(onClick = onNext).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text(nextLabel, style = V4.t.label, color = c.brand) }
    }
}

/** 2026年10月 → 「10月 2026」 */
fun monthLabel(ym: String): String = "${ym.substring(5, 7).toInt()}月 ${ym.substring(0, 4)}"

/* ---------------- 補助 ---------------- */

/** 等幅数字(tnum) */
fun TextStyle.num(): TextStyle = copy(fontFeatureSettings = "tnum")

/** 線(Rule)。縦の時間軸などで使う 1.5dp の線 */
@Composable
fun VLine(color: Color, width: Dp = 1.5.dp, modifier: Modifier = Modifier) { Box(modifier.width(width).fillMaxHeight().background(color)) }

/** AppData を読む(LocalData.current の短縮) */
val data: AppData @Composable get() = LocalData.current

@Suppress("unused") private val unusedWeight = FontWeight.Normal
@Suppress("unused") private val unusedBrush: Brush? = null
@Suppress("unused") private val unusedSp = 1.sp
@Suppress("unused") private fun Modifier.unusedWidthIn() = widthIn(0.dp)
@Suppress("unused") private fun Modifier.unusedDraw() = drawWithContent { drawContent() }
