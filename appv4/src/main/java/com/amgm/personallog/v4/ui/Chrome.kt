package com.amgm.personallog.v4.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.screens.create.openCreate
import com.amgm.personallog.v4.ui.components.overdueCount
import kotlinx.coroutines.delay

/*
 * 下部ナビ・シート・トースト(app.css の .nav / .sheet / .toast)。
 */

/** 下部ナビ: 浮いた角丸22のカード。[タブ][タブ][＋][タブ][タブ]。帯が上下にすべって2枚を切り替える(＋とつまみは動かない) */
@Composable
fun BottomNav(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val c = V4.c
    val data = LocalData.current
    val today = app.today
    val badge: (String) -> Int = { k -> if (k == Tabs.TODO) data.overdueCount(today) else 0 }
    val pg = app.navPage
    val anim by animateFloatAsState(pg.toFloat(), tween(260), label = "navpg")
    val otherDot = Tabs.PAGES[if (pg == 0) 1 else 0].any { it != null && it.first != "blank" && badge(it.first) > 0 }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    Box(
        modifier
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .height(V4Dim.navH),
    ) {
        // 切替のつまみ(カードの上に浮く 52×30。押せる範囲 48×48)
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-39).dp)
                .size(52.dp, 30.dp)
                .shadow(10.dp, RoundedCornerShape(50), ambientColor = Color(0x47284896), spotColor = Color(0x47284896))
                .clip(RoundedCornerShape(50)).background(c.surface)
                .semantics { contentDescription = "ナビを切り替え(${pg + 1}/2)" + if (otherDot) "(もう一方の枚目にお知らせ)" else "" }
                .clickable { app.navPage = 1 - pg },
            contentAlignment = Alignment.Center,
        ) {
            // 2つの点: いまの枚目 14×6 の pill、もう一方 6×6 で淡い
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                val w0 = 6f + 8f * (1 - anim); val w1 = 6f + 8f * anim
                Box(Modifier.size(w0.dp, 6.dp).clip(RoundedCornerShape(50)).background(c.tx2.copy(alpha = 0.35f + 0.65f * (1 - anim))))
                Box(Modifier.size(w1.dp, 6.dp).clip(RoundedCornerShape(50)).background(c.tx2.copy(alpha = 0.35f + 0.65f * anim)))
            }
            if (otherDot) Box(Modifier.align(Alignment.TopEnd).offset(2.dp, (-2).dp).size(12.dp).clip(CircleShape).background(c.surface).padding(2.dp).clip(CircleShape).background(c.lateFill))
        }
        Box(
            Modifier.fillMaxSize()
                .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x47284896), spotColor = Color(0x47284896))
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface.copy(alpha = 0.97f))
                .pointerInput(pg) {
                    detectHorizontalDragGestures(onDragStart = { dragX = 0f }, onDragEnd = { if (kotlin.math.abs(dragX) > 30 * density) app.navPage = 1 - app.navPage }) { _, d -> dragX += d }
                }
                .pointerInput(pg) {
                    detectVerticalDragGestures(onDragStart = { dragY = 0f }, onDragEnd = {
                        if (dragY < -24 * density && app.navPage == 0) app.navPage = 1
                        else if (dragY > 24 * density && app.navPage == 1) app.navPage = 0
                    }) { _, d -> dragY += d }
                },
        ) {
            Row(Modifier.fillMaxSize()) {
                for (slot in 0 until 5) {
                    if (slot == 2) { Box(Modifier.width(64.dp).fillMaxHeight()); continue }
                    NavCell(slot, anim, badge, Modifier.weight(1f))
                }
            }
        }
        // ＋(中央。2枚とも同じ位置、動かない)
        Box(Modifier.align(Alignment.Center).offset(y = (-14).dp)) {
            Box(
                Modifier.size(56.dp)
                    .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = c.meFill, spotColor = c.meFill)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.meFill).background(lightBrush())
                    .semantics { contentDescription = "作成" }
                    .clickable { openCreate(app) },
                contentAlignment = Alignment.Center,
            ) { Icon(V4Icons.plus, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        }
    }
}

/** 窓(overflow:hidden)の中に1枚目と2枚目のタブを縦に積んだ帯。anim=0 で1枚目、1 で2枚目 */
@Composable
private fun NavCell(slot: Int, anim: Float, badge: (String) -> Int, modifier: Modifier) {
    val app = LocalApp.current
    BoxWithConstraints(modifier.fillMaxHeight().clip(VerticalClip)) {
        val h = maxHeight
        Column(Modifier.fillMaxWidth().height(h * 2).offset(y = -h * anim)) {
            for (p in 0..1) {
                val t = Tabs.PAGES[p][slot]
                Box(Modifier.fillMaxWidth().height(h)) {
                    if (t != null && t.first != "blank") NavTab(t.first, t.second, app.lastTab == t.first, badge(t.first), enabled = app.navPage == p)
                }
            }
        }
    }
}

@Composable
private fun NavTab(key: String, label: String, current: Boolean, badge: Int, enabled: Boolean) {
    val app = LocalApp.current
    val c = V4.c
    Column(
        Modifier.fillMaxSize().clickable(enabled = enabled) { app.go(key) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(56.dp, 30.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(if (current) c.brandSoft else Color.Transparent), contentAlignment = Alignment.Center) {
                Icon(V4NavIcons.byKey(key), null, tint = if (current) c.brand else c.tx3, modifier = Modifier.size(24.dp))
            }
            // 期限切れの件数(赤い丸)。ピルの外にはみ出してよい
            if (badge > 0) Box(
                Modifier.offset(x = 36.dp, y = (-2).dp).heightIn(min = 16.dp).widthIn(min = 16.dp)
                    .clip(RoundedCornerShape(8.dp)).background(c.lateFill).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Text("$badge", style = plex(12, 600, 1.0), color = Color.White) }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = plex(12, 500, 1.0), color = if (current) c.brand else c.tx3, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true))
    }
}

/** 縦だけを切る(窓の上下で帯を隠す。横はラベルがはみ出してよい) */
private val VerticalClip = androidx.compose.foundation.shape.GenericShape { size, _ -> addRect(androidx.compose.ui.geometry.Rect(-40f, 0f, size.width + 40f, size.height)) }

/** 下から出るシート(.sheet)。つまみ・見出し(左=✕か文字の操作/題名/右は空き)・本文・下の主ボタン */
@Composable
fun SheetHost() {
    val app = LocalApp.current
    val spec = app.sheet
    val c = V4.c
    AnimatedVisibility(spec != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Color(0x6B0A0E16)).clickable(remember { MutableInteractionSource() }, null) { app.closeSheet() })
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        var last by remember { mutableStateOf(spec) }
        if (spec != null) last = spec
        AnimatedVisibility(spec != null, enter = slideInVertically(tween(260)) { it }, exit = slideOutVertically(tween(260)) { it }) {
            val s = last ?: return@AnimatedVisibility
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val maxH = maxHeight * 0.92f
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth()
                        .heightIn(max = maxH).let { if (s.full) it.height(maxHeight * 0.96f) else it }
                        .shadow(24.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(c.bg)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .imePadding(),
                ) {
                    Box(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.lineStrong))
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (s.leftLabel != null) {
                            Box(Modifier.heightIn(min = 44.dp).widthIn(min = 64.dp).clickable { s.onLeft?.invoke() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(s.leftLabel, style = plex(15, 500), color = c.me)
                            }
                        } else {
                            Box(Modifier.size(44.dp).clip(CircleShape).clickable { app.closeSheet() }.semantics { contentDescription = "閉じる" }, contentAlignment = Alignment.Center) {
                                Icon(V4Icons.close, null, tint = c.tx2, modifier = Modifier.size(22.dp))
                            }
                        }
                        Text(s.title, style = V4.t.head, color = c.tx, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(44.dp))
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { s.content() }
                    if (s.mainLabel != null) {
                        Box(Modifier.fillMaxWidth().background(c.line).padding(top = 1.dp).background(c.surface).padding(horizontal = V4Dim.gutter, vertical = 12.dp).navigationBarsPadding()) {
                            PrimaryButton(s.mainLabel, enabled = s.mainEnabled(), modifier = Modifier.fillMaxWidth()) { s.onMain?.invoke() }
                        }
                    }
                }
            }
        }
    }
}

/** トースト(白い浮いた面+「取り消す」) */
@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val t = app.toast
    val c = V4.c
    LaunchedEffect(t?.serial) { if (t != null) { delay(if (t.undo) 5000 else 2600); app.dismissToast(t.serial) } }
    var last by remember { mutableStateOf(t) }
    if (t != null) last = t
    AnimatedVisibility(
        t != null, modifier.navigationBarsPadding().padding(bottom = V4Dim.navH + 34.dp, start = 16.dp, end = 16.dp),
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }, exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 3 },
    ) {
        val s = last ?: return@AnimatedVisibility
        Row(
            Modifier.heightIn(min = 48.dp)
                .shadow(16.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                .background(c.surface).background(Color.Transparent)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(s.msg, style = V4.t.label, color = c.tx, modifier = Modifier.padding(vertical = 12.dp))
            if (s.undo) Box(Modifier.height(40.dp).clickable { app.undo() }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Text("取り消す", style = plex(13, 600), color = c.me)
            }
        }
    }
}

/** 塗りの面の光(--light): 左上の丸いグラデーション */
fun lightBrush() = androidx.compose.ui.graphics.Brush.radialGradient(
    0f to Color.White.copy(alpha = 0.15f), 0.55f to Color.White.copy(alpha = 0f),
    center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 600f,
)

/** 主ボタン(.btn): 高さ48・角丸10・塗り=光+--me-fill・白文字 600 15 */
@Composable
fun PrimaryButton(label: String, enabled: Boolean = true, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    val c = V4.c
    Box(
        modifier.height(if (small) 36.dp else 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .shadow(6.dp, RoundedCornerShape(10.dp), ambientColor = c.meFill, spotColor = c.meFill)
            .clip(RoundedCornerShape(10.dp)).background(c.meFill).background(lightBrush())
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = plex(if (small) 14 else 15, 600), color = Color.White, maxLines = 1) }
}

