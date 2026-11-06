package com.amgm.personallog.v4.screens.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.lightBrush
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.components.Divider
import com.amgm.personallog.v4.ui.components.SpStub
import com.amgm.personallog.v4.ui.components.TicketShape
import com.amgm.personallog.v4.ui.components.TicketSize
import com.amgm.personallog.v4.ui.components.fill
import com.amgm.personallog.v4.ui.components.hatch
import com.amgm.personallog.v4.ui.components.num
import com.amgm.personallog.v4.ui.components.press
import com.amgm.personallog.v4.ui.components.e1
import com.amgm.personallog.v4.data.yen

/*
 * 家計簿の部品(この画面だけで使う)。ui.js の ticket / prop / ld-row / ld-mini / pnav / sc-acts に当たる。
 * 共通部品(ui/components)は変えない。ここには、共通に無い形だけを置く。
 */

/** 絞り込み {ax: cat / fx / sp, k: 値}。null は絞りなし */
internal data class LFilter(val ax: String, val k: String)

/** 小さい塗りの帯(.ld-sq): 14×6、角丸3 */
@Composable
internal fun LSquare(color: Color, w: Dp = 14.dp, h: Dp = 6.dp) {
    Box(Modifier.size(w, h).clip(RoundedCornerShape(3.dp)).background(color))
}

/** 割り勘・立替の棒(.ld-pbar): 幅32・高さ6。自分=青 / 他人=--axis / 未精算=琥珀の斜線 */
@Composable
internal fun LPayBar(mine: Int, rest: Int, owed: Boolean) {
    val c = V4.c
    Row(Modifier.size(32.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(c.sunk)) {
        if (mine > 0) Box(Modifier.weight(mine.toFloat()).fillMaxHeight().background(c.me).background(lightBrush()))
        if (rest > 0) Box(Modifier.weight(rest.toFloat()).fillMaxHeight().let { if (owed) it.hatch(c.owe) else it.background(c.axis) })
    }
}

/**
 * 半券(.tkt-w)。lg=詳細の最上部(stub 68・角丸18・題名36)/ 既定=一覧(stub 56・角丸14・題名15)。
 * fill=自分の青い塗り。s は補足の並び(文字色 sc を受け取る)。amt は右の金額。
 */
@Composable
internal fun LTicket(
    t: String,
    sp: String?,
    lg: Boolean = false,
    fill: Boolean = false,
    label: String? = null,
    onClick: (() -> Unit)? = null,
    s: (@Composable (Color) -> Unit)? = null,
    amt: (@Composable () -> Unit)? = null,
) {
    val c = V4.c
    val stubW = if (lg) 68.dp else 56.dp
    val notch = if (lg) 10.dp else 8.dp
    val radius = if (lg) 18.dp else 14.dp
    val minH = if (lg) 112.dp else 64.dp
    val shape = TicketShape(radius, stubW, notch)
    val onC = if (fill) Color.White else c.tx
    val sc = if (fill) Color.White.copy(alpha = 0.82f) else c.tx3
    val shadowColor = if (fill) Color(0x4D2D59B8) else Color(0x2E2D59B8)
    Box(
        Modifier.fillMaxWidth()
            .shadow(if (fill) 10.dp else 5.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(shape)
            .let { if (fill) it.background(c.meFill).background(lightBrush()) else it.background(c.tlCard) }
            .let { if (onClick != null) it.press(onClick = onClick) else it }
            .semantics { if (label != null) contentDescription = label },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = minH).height(IntrinsicSize.Min)) {
            Box(
                Modifier.width(stubW).fillMaxHeight()
                    .let { if (fill) it.background(Color.White.copy(alpha = 0.12f)) else it.background(c.meFill).background(lightBrush()) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { SpStub(sp, if (lg) TicketSize.LG else TicketSize.NORMAL) }
            // 切り取り線(縦の破線)
            Box(Modifier.width(1.5.dp).fillMaxHeight().drawBehind {
                drawLine(
                    if (fill) Color.White.copy(alpha = 0.42f) else c.line,
                    Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = size.width,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            })
            Row(
                Modifier.weight(1f).padding(horizontal = if (lg) 18.dp else 14.dp, vertical = if (lg) 16.dp else 10.dp),
                verticalAlignment = if (lg) Alignment.Top else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (lg) 6.dp else 3.dp)) {
                    Text(
                        t, color = onC, maxLines = 1,
                        overflow = if (lg) TextOverflow.Visible else TextOverflow.Ellipsis,
                        style = if (lg) plex(36, 700, 1.1).num() else plex(15, 600, 1.35),
                    )
                    s?.invoke(sc)
                }
                if (amt != null) amt()
            }
        }
    }
}

/**
 * 行(.ld-row / .row): lead の後に題名・補足、右に値。
 * indent=字下げ、guideX=子の縦の線の位置(字下げの左)、selected=選択中の面。
 */
@Composable
internal fun LRow(
    t: String,
    tColor: Color = V4.c.tx,
    tBold: Boolean = false,
    lead: (@Composable () -> Unit)? = null,
    sub: (@Composable () -> Unit)? = null,
    v: (@Composable () -> Unit)? = null,
    indent: Dp = 0.dp,
    guideX: Dp? = null,
    selected: Boolean = false,
    chev: Boolean = false,
    divider: Boolean = false,
    minH: Dp = 52.dp,
    padV: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
) {
    val c = V4.c
    Column {
        if (divider) Divider(14.dp)
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = minH)
                .background(if (selected) c.brandSoft else Color.Transparent)
                .let {
                    if (guideX == null) it
                    else it.drawBehind { drawLine(c.lineStrong, Offset(guideX.toPx(), 0f), Offset(guideX.toPx(), size.height), strokeWidth = 1.dp.toPx()) }
                }
                .let { if (onClick != null) it.press(onClick = onClick) else it }
                .padding(start = 14.dp + indent, end = 14.dp, top = padV, bottom = padV),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            lead?.invoke()
            Column(Modifier.weight(1f)) {
                Text(t, color = tColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = plex(15, if (tBold) 600 else 500, 1.4))
                sub?.invoke()
            }
            v?.invoke()
            if (chev) Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
    }
}

/** 金額(太字・数字の幅をそろえる)。pct があれば小さく % を続ける(.ld-pct) */
@Composable
internal fun LAmount(v: Int, pct: Int? = null) {
    val c = V4.c
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(yen(v), style = plex(15, 600, 1.4).num(), color = c.tx)
        if (pct != null) Text("$pct%", style = plex(12, 400), color = c.tx3, modifier = Modifier.padding(start = 6.dp))
    }
}

/** 属性の行(.prop): 左に幅84の名前、右に値、meta と ›。divider=true で上に線(左14から) */
@Composable
internal fun LProp(
    k: String,
    divider: Boolean = false,
    chev: Boolean = false,
    onClick: (() -> Unit)? = null,
    meta: (@Composable RowScope.() -> Unit)? = null,
    value: @Composable RowScope.() -> Unit,
) {
    val c = V4.c
    Column {
        if (divider) Divider(14.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .let { if (onClick != null) it.press(onClick = onClick) else it }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(k, style = V4.t.label, color = c.tx3, modifier = Modifier.width(84.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = value)
            meta?.invoke(this)
            if (chev) Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
    }
}

/** 区分のピル(.ld-mini): 高さ36・角丸pill。選択中は --me-soft / --me / 600。items = (値, 文字) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LMini(items: List<Pair<String, String>>, cur: String, onPick: (String) -> Unit) {
    val c = V4.c
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (v, l) ->
            val sel = v == cur
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(50))
                    .background(if (sel) c.meSoft else c.surface2)
                    .let { if (sel) it else it.border(1.dp, c.lineStrong, RoundedCornerShape(50)) }
                    .press(onClick = { onPick(v) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(l, style = plex(13, if (sel) 600 else 500), color = if (sel) c.me else c.tx2, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** 期間の移動(.pnav): 白いピル「前の月 / 10月 2026 / 次の月」。押せない時は .5 の薄さ。‹ › は使わない */
@Composable
internal fun LPeriodNav(label: String, sub: String, prevOn: Boolean, nextOn: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = V4.c
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(36.dp).e1(shape).clip(shape).background(c.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight().let { if (prevOn) it.press(onClick = onPrev) else it }.padding(horizontal = 10.dp)
                .graphicsLayer { alpha = if (prevOn) 1f else 0.5f },
            contentAlignment = Alignment.Center,
        ) { Text("前の月", style = plex(13, 500), color = c.brand) }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = plex(15, 700).num(), color = c.tx, maxLines = 1, softWrap = false)
            Text(sub, style = plex(12, 500), color = c.tx3, maxLines = 1, softWrap = false)
        }
        Box(
            Modifier.fillMaxHeight().let { if (nextOn) it.press(onClick = onNext) else it }.padding(horizontal = 10.dp)
                .graphicsLayer { alpha = if (nextOn) 1f else 0.5f },
            contentAlignment = Alignment.Center,
        ) { Text("次の月", style = plex(13, 500), color = c.brand) }
    }
}

/** 下段の操作(.sc-acts の .btn): 高さ48。primary=塗り / それ以外=淡い塗り */
@Composable
internal fun LActButton(text: String, icon: ImageVector, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = V4.c
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.height(48.dp)
            .let { if (primary) it.shadow(6.dp, shape, ambientColor = c.meFill, spotColor = c.meFill).fill(c.meFill, shape) else it.clip(shape).background(c.meSoft) }
            .press(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = if (primary) Color.White else c.me, modifier = Modifier.size(18.dp))
        Text(text, style = plex(14, 600), color = if (primary) Color.White else c.me, maxLines = 1, softWrap = false)
    }
}

/** 未精算の印(半券の中の tag。白の18%) */
@Composable
internal fun LFillTag(label: String) {
    Box(
        Modifier.height(24.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = plex(12, 500, 1.0), color = Color.White, maxLines = 1, softWrap = false) }
}

/** 入力の文字(BasicTextField)。空の時は placeholder を薄く出す */
@Composable
internal fun LTextField(value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = V4.c
    BasicTextField(
        value = value,
        onValueChange = onValue,
        textStyle = V4.t.body.copy(color = c.tx),
        singleLine = true,
        cursorBrush = SolidColor(c.brand),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = V4.t.body, color = c.tx3)
                inner()
            }
        },
    )
}
