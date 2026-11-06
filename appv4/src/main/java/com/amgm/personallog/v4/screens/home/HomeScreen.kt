package com.amgm.personallog.v4.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.*
import com.amgm.personallog.v4.screens.create.openDiarySheet
import com.amgm.personallog.v4.screens.plan.recordEvent
import com.amgm.personallog.v4.screens.plan.toggleTask
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.Route
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.lightBrush
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.components.CheckBox
import com.amgm.personallog.v4.ui.components.Knots
import com.amgm.personallog.v4.ui.components.Chips
import com.amgm.personallog.v4.ui.components.Screen
import com.amgm.personallog.v4.ui.components.Tag
import com.amgm.personallog.v4.ui.components.e1
import com.amgm.personallog.v4.ui.components.num
import com.amgm.personallog.v4.ui.components.press

/*
 * ホーム(#/home) — home.js の再現。担当: ホーム。
 * 他画面の Axis 部品は使わず、時間軸などはこのファイルの中で描く。
 * 画面をまたぐ操作は Create.kt / PlanActions.kt の関数を呼ぶ。
 */

@Composable
fun HomeScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val today = app.today
    val now = app.now
    Screen {
        HmHead(app, d, today)
        HmMain(app, d, today, now)
        HmPending(app, d, today, now)
        HmGrid(app, d, today, now)
    }
}

/* ---------------- 共通の補助(home.js の hm* 関数) ---------------- */

/** 記録の時刻(無ければ空文字) */
private fun Activity.at(): String = time ?: ""

/** 今日の日記の子の記録のうち時刻のあるもの(時刻順) */
private fun AppData.todayRecs(today: String): List<Pair<String, Activity>> {
    val day = dayKeyByDate(today) ?: return emptyList()
    return (acts[day]?.kids ?: emptyList())
        .mapNotNull { k -> acts[k]?.let { k to it } }
        .filter { it.second.time != null }
        .sortedBy { it.second.at() }
}

/** 今日の予定(時刻が無くても、終日でも含む。記録済みは除く) */
private fun AppData.todayPlans(today: String): List<Pair<String, Plan>> =
    evOn(today).filter { (_, p) -> !p.hol && !(p.rec != null && acts.containsKey(p.rec)) }

/** 「あと 〇分」「あと 〇時間〇分」 */
private fun leftText(s: String, now: String): String {
    val m = Dates.minutes(s) - Dates.minutes(now)
    return if (m < 60) "あと ${m}分" else {
        val h = m / 60; val r = m % 60
        "あと ${h}時間" + if (r > 0) "${r}分" else ""
    }
}

/** 今日/昨日/明日/明後日、それ以外は「10月29日(木)」 */
private fun homeRelDay(due: String, today: String): String = when (Dates.daysBetween(today, due)) {
    0L -> "今日"; -1L -> "昨日"; 1L -> "明日"; 2L -> "明後日"
    else -> Dates.fmtMDW(due)
}

/** 日ごとの支出(固定費を除く。支払いの日付で数える) */
private fun AppData.spendOn(day: String): Int =
    expenses.values.filter { x -> xFx(x) != "固定" && xDate(x) == day }.sumOf { it.amt }

/** 「+¥480」「−¥1,680」「±0」 */
private fun signedYen(n: Int): String = when {
    n == 0 -> "±0"
    n > 0 -> "+" + yen(n)
    else -> "−" + yen(-n)
}

/** 「+1%」「−3%」。前が0なら null */
private fun pctText(cur: Int, prev: Int): String? {
    if (prev == 0) return null
    val p = Math.round((cur - prev) * 100.0 / prev).toInt()
    return if (p >= 0) "+$p%" else "−${-p}%"
}

/** 「2026-10-31 08:12」→「10/31 08:12」 */
private fun memoAt(at: String): String = runCatching {
    val (y, m, dd) = at.substring(0, 10).split("-").map { it.toInt() }
    "$m/$dd " + at.substring(11, 16)
}.getOrDefault(at)

/* ---------------- 1. 見出し ---------------- */

@Composable
private fun HmHead(app: AppState, d: AppData, today: String) {
    val c = V4.c
    val ld = Dates.parse(today)
    val hasDiary = d.dayKeyByDate(today)?.let { d.acts[it]?.body?.isNotEmpty() } == true
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier,
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${ld.monthValue}月${ld.dayOfMonth}日", style = plex(34, 800, 1.1).num(), color = c.tx, maxLines = 1, softWrap = false)
            Text("${Dates.wd(today)}曜", style = plex(15, 600, 1.2), color = c.tx2, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.weight(1f))
        // 押し面 44・見た目 36(home.js の .hm-pill)
        Box(
            Modifier.height(44.dp).press { if (hasDiary) app.go("day", today) else openDiarySheet(app, today) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(999.dp)).background(c.meSoft).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (hasDiary) "日記を開く" else "日記を書く", style = plex(14, 600, 1.0), color = c.me, maxLines = 1, softWrap = false)
            }
        }
    }
}

/* ---------------- 2. 主役の面: 今日の流れ ---------------- */

/** 時間軸の節の種類(点の形と位置) */
private enum class Node { PAST, FUT, NOW, NEXT }

@Composable
private fun HmMain(app: AppState, d: AppData, today: String, now: String) {
    val c = V4.c
    val shape = RoundedCornerShape(28.dp)
    val recs = d.todayRecs(today)
    val past = recs.filter { it.second.at() <= now }
    val future = recs.filter { it.second.at() > now }
    val plans = d.todayPlans(today).filter { (_, p) -> !p.all && p.s != null && (p.e ?: "") > now }
    val next = plans.firstOrNull { (_, p) -> (p.s ?: "") > now }?.first

    Column(
        Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .shadow(18.dp, shape, ambientColor = c.meFill.copy(alpha = 0.45f), spotColor = c.meFill.copy(alpha = 0.45f))
            .clip(shape)
            .background(c.meFill)
            .background(lightBrush())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("今日の流れ", style = plex(15, 700, 1.3), color = c.txInv, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Box(Modifier.height(44.dp).press { app.go("activities") }, contentAlignment = Alignment.CenterEnd) {
                Text("アクティビティ ›", style = plex(14, 600, 1.0), color = c.txInv.copy(alpha = 0.92f), maxLines = 1, softWrap = false)
            }
        }
        if (past.isEmpty() && future.isEmpty() && plans.isEmpty()) {
            Text("今日の予定 なし", style = plex(15, 500, 1.5), color = c.txInv, modifier = Modifier.padding(vertical = 12.dp))
            return@Column
        }
        Column(Modifier.padding(top = 4.dp)) {
            if (past.size > 3) {
                TlRow(
                    time = {}, node = Node.PAST, fut = false, minH = 44.dp,
                    onClick = { app.go("day", today) },
                ) {
                    Text("これより前 ${past.size - 3}件", style = plex(13, 600, 1.0), color = c.txInv.copy(alpha = 0.92f))
                }
            }
            past.takeLast(3).forEach { (k, a) -> RecRow(app, d, k, a, past = true) }
            // いまの線
            TlRow(
                time = {
                    Text("いま", style = plex(12, 700, 1.3), color = c.txInv, maxLines = 1)
                    Text(now, style = plex(13, 500, 1.3).num(), color = c.txInv.copy(alpha = 0.8f), maxLines = 1)
                },
                node = Node.NOW, fut = false, topPad = 6.dp, minH = 36.dp,
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.txInv.copy(alpha = 0.7f)))
            }
            future.forEach { (k, a) -> RecRow(app, d, k, a, past = false) }
            plans.forEach { (k, p) ->
                if (k == next) NextRow(app, d, k, p, now) else PlanRow(app, d, k, p, now)
            }
        }
    }
}

/** 時間軸の1行: 時刻列 / 軸(線と点) / 内容 */
@Composable
private fun TlRow(
    time: @Composable () -> Unit,
    node: Node,
    fut: Boolean,
    topPad: Dp = 11.dp,
    minH: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = minH).height(IntrinsicSize.Min)
            .let { if (onClick != null) it.press(onClick = onClick) else it },
    ) {
        Column(Modifier.width(48.dp).padding(top = topPad)) { time() }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.width(18.dp).fillMaxHeight().drawBehind {
                val cx = size.width / 2f
                val lineW = 1.5.dp.toPx()
                if (fut) {
                    drawLine(
                        c.txInv.copy(alpha = 0.75f), Offset(cx, 0f), Offset(cx, size.height), lineW,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
                    )
                } else {
                    drawLine(c.txInv.copy(alpha = 0.35f), Offset(cx, 0f), Offset(cx, size.height), lineW)
                }
                when (node) {
                    Node.NOW -> {
                        val y = size.height / 2f
                        drawCircle(c.meFill.copy(alpha = 0.55f), 10.dp.toPx(), Offset(cx, y))
                        drawCircle(Color.White, 6.dp.toPx(), Offset(cx, y))
                    }
                    Node.PAST -> drawCircle(Color.White, 4.5.dp.toPx(), Offset(cx, 21.dp.toPx()))
                    Node.FUT -> {
                        val y = 21.dp.toPx()
                        drawCircle(c.meFill, 5.dp.toPx(), Offset(cx, y))
                        drawCircle(Color.White, 4.25.dp.toPx(), Offset(cx, y), style = Stroke(1.5.dp.toPx()))
                    }
                    Node.NEXT -> {
                        val y = 34.dp.toPx()
                        drawCircle(c.meFill, 5.dp.toPx(), Offset(cx, y))
                        drawCircle(Color.White, 4.25.dp.toPx(), Offset(cx, y), style = Stroke(1.5.dp.toPx()))
                    }
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(top = 11.dp, bottom = 10.dp), content = body)
    }
}

/** 記録の行(過去・これから) */
@Composable
private fun RecRow(app: AppState, d: AppData, key: String, a: Activity, past: Boolean) {
    val c = V4.c
    val title = if (a.type == "支払い") d.payLabel(a) else a.title.ifEmpty { "記録" }
    TlRow(
        time = { Text(a.at(), style = plex(13, 500, 1.3).num(), color = c.txInv.copy(alpha = 0.8f), maxLines = 1, softWrap = false) },
        node = if (past) Node.PAST else Node.FUT,
        fut = !past,
        onClick = { app.go("a", key) },
    ) {
        Text(
            title, style = plex(15, 600, 1.35),
            color = if (past) c.txInv.copy(alpha = 0.9f) else c.txInv,
        )
        if (a.end != null) {
            Text(a.timeText(), style = plex(13, 500, 1.3).num(), color = c.txInv.copy(alpha = 0.8f), maxLines = 1, softWrap = false)
        }
    }
}

/** 予定の行(小さい行) */
@Composable
private fun PlanRow(app: AppState, d: AppData, key: String, p: Plan, now: String) {
    val c = V4.c
    val ing = (p.s ?: "") <= now
    TlRow(
        time = { Text(p.s ?: "", style = plex(13, 500, 1.3).num(), color = c.txInv.copy(alpha = 0.8f), maxLines = 1, softWrap = false) },
        node = Node.FUT, fut = true,
        onClick = { app.go("ev", key) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.t, style = plex(15, 500, 1.35), color = c.txInv.copy(alpha = 0.82f), modifier = Modifier.weight(1f, fill = false))
            if (ing) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(c.txInv.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("進行中", style = plex(12, 600, 1.4), color = c.txInv, maxLines = 1, softWrap = false)
                }
            }
        }
        Text("${p.s}–${p.e}", style = plex(13, 500, 1.3).num(), color = c.txInv.copy(alpha = 0.82f), maxLines = 1, softWrap = false)
    }
}

/** 次の予定の大札 */
@Composable
private fun NextRow(app: AppState, d: AppData, key: String, p: Plan, now: String) {
    val c = V4.c
    val shape = RoundedCornerShape(18.dp)
    TlRow(
        time = { Text(p.s ?: "", style = plex(13, 500, 1.3).num(), color = c.txInv.copy(alpha = 0.8f), maxLines = 1, softWrap = false) },
        node = Node.NEXT, fut = true,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(c.txInv.copy(alpha = 0.14f), shape)
                .border(1.dp, c.txInv.copy(alpha = 0.22f), shape)
                .press { app.go("ev", key) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("次の予定", style = plex(12, 600, 1.4), color = c.txInv.copy(alpha = 0.84f), maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(leftText(p.s ?: "", now), style = plex(15, 700, 1.3).num(), color = c.txInv, maxLines = 1, softWrap = false)
            }
            Text(p.t, style = plex(20, 700, 1.3), color = c.txInv, modifier = Modifier.padding(top = 8.dp))
            Text("${p.s}–${p.e}", style = plex(14, 600, 1.4), color = c.txInv.copy(alpha = 0.92f), modifier = Modifier.padding(top = 4.dp))
            val who = p.who.filter { d.entities.containsKey(it) }
            if (p.place != null || who.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (p.place != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(V4Icons.place, null, tint = c.txInv.copy(alpha = 0.92f), modifier = Modifier.size(16.dp))
                            Text(d.placeOf(p), style = plex(13, 500, 1.4), color = c.txInv.copy(alpha = 0.92f), maxLines = 1)
                        }
                    }
                    who.forEach { id ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(c.txInv.copy(alpha = 0.8f)))
                            Text(d.entities[id]?.n ?: "", style = plex(13, 500, 1.4), color = c.txInv.copy(alpha = 0.92f), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- 3. 記録待ち ---------------- */

@Composable
private fun HmPending(app: AppState, d: AppData, today: String, now: String) {
    val c = V4.c
    val pend = d.todayPlans(today).filter { (_, p) -> !p.all && p.e?.let { it <= now } == true }
    if (pend.isEmpty()) return
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp)
            .e1(shape)
            .background(c.surface, shape)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Text("記録待ち", style = plex(13, 600, 1.4), color = c.tx2, modifier = Modifier.padding(bottom = 4.dp))
        pend.forEachIndexed { i, (k, p) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .let { if (i > 0) it.drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else it },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 赤い破線の小カプセル(.cap.plan.late)
                Canvas(Modifier.size(30.dp, 18.dp)) {
                    val sw = 2.5.dp.toPx()
                    drawRoundRect(
                        color = c.late,
                        topLeft = Offset(sw / 2, sw / 2),
                        size = Size(size.width - sw, size.height - sw),
                        cornerRadius = CornerRadius(size.height / 2),
                        style = Stroke(
                            sw,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 6.dp.toPx())),
                            cap = StrokeCap.Round,
                        ),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(p.t, style = plex(15, 600, 1.35), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.s}–${p.e}", style = plex(13, 500, 1.3).num(), color = c.tx2, maxLines = 1, softWrap = false)
                }
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(999.dp))
                        .background(c.meFill).background(lightBrush())
                        .press { recordEvent(app, k) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("記録にする", style = plex(14, 700, 1.0), color = c.onMe, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/* ---------------- 4. タイルの格子 ---------------- */

@Composable
private fun HmGrid(app: AppState, d: AppData, today: String, now: String) {
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HmMoneyTile(app, d, today)
        HmTodoTile(app, d, today)
        HmOweTile(app, d)
        HmMemoTile(app, d, today, now)
    }
}

/** タイルの面(.hm-tile): 角丸20・面・影 e1 */
@Composable
private fun TileBox(modifier: Modifier = Modifier, gap: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = V4.c
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().e1(shape).background(c.surface, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/* ---- 4-1 支出 ---- */

/** 支出の棒 1本 = 8日分(左が古い) */
@Composable
private fun HmMoneyTile(app: AppState, d: AppData, today: String) {
    val c = V4.c
    val days = (-7..0).map { Dates.addDays(today, it) }
    val vals = days.map { d.spendOn(it) }
    val maxV = vals.maxOrNull() ?: 0
    val lastWeek = vals.first()
    val todayAmt = vals.last()
    val curWeek = (-6..0).sumOf { d.spendOn(Dates.addDays(today, it)) }
    val prevWeek = (-13..-7).sumOf { d.spendOn(Dates.addDays(today, it)) }
    val pct = pctText(curWeek, prevWeek)

    TileBox(gap = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("支出", style = plex(18, 700, 1.3), color = c.tx, maxLines = 1)
            Text(
                "固定費(家賃・通信・サブスク)を除く",
                style = plex(12, 600, 1.3), color = c.me, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(999.dp)).background(c.meSoft).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(0.dp))
            Box(Modifier.height(44.dp).press { app.go("ledger") }, contentAlignment = Alignment.CenterEnd) {
                Text("家計簿 ›", style = plex(14, 600, 1.0), color = c.me, maxLines = 1, softWrap = false)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("今日", style = plex(13, 600, 1.4), color = c.tx2)
                Text(yen(todayAmt), style = plex(28, 800, 1.15).num(), color = c.tx, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "先週の${Dates.wd(days.first())}曜 ${yen(lastWeek)}",
                    style = plex(13, 500, 1.4).num(), color = c.tx2, maxLines = 1, softWrap = false,
                )
                Tag(signedYen(todayAmt - lastWeek))
            }
        }
        // 棒グラフ 8本(高さ 118)
        Row(
            Modifier.fillMaxWidth().height(118.dp).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            days.forEachIndexed { i, day ->
                val v = vals[i]
                val isToday = day == today
                val h: Dp = if (v == 0 || maxV == 0) 2.dp else Math.round(v * 88.0 / maxV).toInt().coerceAtLeast(2).dp
                val barColor = if (isToday) c.meFill else lerpColor(c.surface, c.meFill, 0.3f)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().widthIn(max = 26.dp).height(h)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(barColor).background(com.amgm.personallog.v4.ui.lightBrush()),
                    )
                    Text(
                        if (isToday) "今日" else Dates.wd(day),
                        style = plex(12, if (isToday) 700 else 500, 1.0),
                        color = if (isToday) c.me else c.tx2,
                        maxLines = 1, softWrap = false,
                    )
                }
            }
        }
        // この7日間
        Row(
            Modifier.fillMaxWidth().drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text("この7日間 ${Dates.md(Dates.addDays(today, -6))}〜${Dates.md(today)}", style = plex(13, 600, 1.4), color = c.tx2, maxLines = 1, softWrap = false)
                Text(yen(curWeek), style = plex(20, 700, 1.25).num(), color = c.tx, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("前の7日間 ${yen(prevWeek)}", style = plex(13, 500, 1.4).num(), color = c.tx2, maxLines = 1, softWrap = false)
                if (pct != null) Tag(pct)
            }
        }
        QuickExpenseRow(app)
    }
}

/** 「その場で記録」の入力行(金額を入れて記録 → 金額だけのシート。ホームに留まる) */
@Composable
private fun QuickExpenseRow(app: AppState) {
    val c = V4.c
    var amt by remember { mutableStateOf("") }
    val submit: () -> Unit = {
        val v = amt.toIntOrNull() ?: 0
        if (v == 0) app.toast("金額を入れてください") else openQuickExpense(app, amt)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = amt,
            onValueChange = { amt = it.filter(Char::isDigit).take(9) },
            singleLine = true,
            textStyle = plex(16, 600, 1.0).num().copy(color = c.tx),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            cursorBrush = SolidColor(c.me),
            modifier = Modifier.weight(1f).height(44.dp)
                .clip(RoundedCornerShape(12.dp)).background(c.surface, RoundedCornerShape(12.dp))
                .border(1.dp, c.line, RoundedCornerShape(12.dp)),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    if (amt.isEmpty()) Text("¥ 金額", style = plex(16, 500, 1.0), color = c.tx3, maxLines = 1)
                    inner()
                }
            },
        )
        Box(
            Modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
                .background(c.meFill).background(lightBrush())
                .press { submit() }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { Text("記録", style = plex(15, 700, 1.0), color = c.onMe, maxLines = 1) }
        Box(Modifier.height(44.dp).press { openQuickExpense(app, "") }, contentAlignment = Alignment.Center) {
            Text("詳しく", style = plex(14, 600, 1.0), color = c.me, maxLines = 1, softWrap = false)
        }
    }
}

/** 金額だけのシートの中身(ホームの「その場で記録」)。保存後はホームに留まる */
private class QeDraft {
    var amt by mutableStateOf("")
    var cat by mutableStateOf<String?>(null)
}

private fun openQuickExpense(app: AppState, amt: String) {
    val draft = QeDraft().also { it.amt = amt }
    app.openSheet(
        title = "金額だけで支払い",
        mainLabel = "支払いを保存",
        onMain = { saveQuickExpense(app, draft) },
    ) { QeBody(draft) }
}

@Composable
private fun QeBody(draft: QeDraft) {
    val c = V4.c
    val d = LocalData.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicTextField(
            value = draft.amt,
            onValueChange = { draft.amt = it.filter(Char::isDigit).take(9) },
            singleLine = true,
            textStyle = plex(28, 700, 1.2).num().copy(color = c.tx),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(c.me),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (draft.amt.isEmpty()) Text("¥0", style = plex(28, 700, 1.2), color = c.tx3, maxLines = 1)
                    inner()
                }
            },
        )
        Chips(
            items = d.roots().map { Triple<String, String, Int?>(it, d.catName(it), null) },
            cur = draft.cat ?: "",
            onPick = { draft.cat = it },
        )
    }
}

/** 金額だけで支払いを作る(今日の日記の子に入れ、家計簿の支出も作る) */
private fun saveQuickExpense(app: AppState, draft: QeDraft) {
    val amt = draft.amt.toIntOrNull() ?: 0
    if (amt == 0) { app.toast("金額を入れてください"); return }
    val today = app.today
    val now = app.now
    val cat = draft.cat ?: ""
    app.store.update { dd ->
        val existing = dd.dayKeyByDate(today)
        val dk = existing ?: uid("d")
        var acts = dd.acts
        if (existing == null) acts = acts + (dk to Activity(type = "日記", date = today, dd = Dates.md(today), dw = Dates.wd(today)))
        val pk = uid("a")
        val day = acts.getValue(dk)
        acts = acts + (dk to day.copy(kids = day.kids + pk)) + (pk to Activity(type = "支払い", parent = dk, time = now, amt = amt))
        dd.copy(acts = acts, expenses = dd.expenses + (uid("x") to Expense(pay = pk, amt = amt, cat = cat)))
    }
    app.closeSheet()
    app.toast("支払いを保存しました")
}

/* ---- 4-2 ToDo ---- */

@Composable
private fun HmTodoTile(app: AppState, d: AppData, today: String) {
    val c = V4.c
    val keys = d.plans.entries
        .filter { (_, p) -> p.isTodo && p.done == null && p.due != null && p.due <= today }
        .sortedBy { it.value.due }
        .map { it.key to it.value }
    TileBox {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ToDo", style = plex(18, 700, 1.3), color = c.tx, maxLines = 1)
            Text("${keys.size}件", style = plex(13, 500, 1.3).num(), color = c.tx3, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            Box(Modifier.height(44.dp).press { app.go("todo") }, contentAlignment = Alignment.CenterEnd) {
                Text("すべて ›", style = plex(14, 600, 1.0), color = c.me, maxLines = 1, softWrap = false)
            }
        }
        if (keys.isEmpty()) {
            Text("今日までのToDo なし", style = plex(14, 500, 1.5), color = c.tx3, modifier = Modifier.padding(vertical = 4.dp))
        }
        keys.take(3).forEachIndexed { i, (k, t) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .let { if (i > 0) it.drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else it },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CheckBox(on = false) { toggleTask(app, k) }
                Column(Modifier.weight(1f).press { app.go("task", k) }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.t, style = plex(15, 500, 1.35), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (t.pri) Text("!", style = plex(15, 700, 1.35), color = c.me, modifier = Modifier.padding(start = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val due = t.due ?: today
                        if (due < today) {
                            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(c.lateSoft).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("期限切れ", style = plex(12, 600, 1.4), color = c.late, maxLines = 1, softWrap = false)
                            }
                        }
                        Text(homeRelDay(due, today), style = plex(13, 500, 1.3), color = c.tx2, maxLines = 1, softWrap = false)
                        t.ev?.let { evKey ->
                            d.plans[evKey]?.let { ev ->
                                Text(ev.t, style = plex(13, 500, 1.3), color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---- 4-3 未精算 ---- */

@Composable
private fun HmOweTile(app: AppState, d: AppData) {
    val c = V4.c
    val shape = RoundedCornerShape(20.dp)
    val owe = d.acts.values.filter { it.adv?.state == "未精算" }
    val sum = owe.sumOf { it.adv?.amt ?: 0 }
    val who = owe.mapNotNull { it.adv?.who }.distinct().filter { d.entities.containsKey(it) }
    Row(
        Modifier.fillMaxWidth().e1(shape).background(c.surface, shape)
            .press { app.go("ledger") }
            .heightIn(min = 56.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("未精算", style = plex(13, 600, 1.4), color = c.tx2, maxLines = 1)
        Text(if (owe.isEmpty()) "なし" else yen(sum), style = plex(22, 700, 1.2).num(), color = c.tx, maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        if (who.isNotEmpty()) Knots(who, color = c.tx2, style = plex(13, 500, 1.4))
    }
}

/* ---- 4-4 すぐメモ ---- */

@Composable
private fun HmMemoTile(app: AppState, d: AppData, today: String, now: String) {
    val c = V4.c
    var text by remember { mutableStateOf("") }
    val save: () -> Unit = {
        val body = text.trim()
        if (body.isEmpty()) {
            app.toast("メモを入れてください")
        } else {
            app.store.update { dd -> dd.copy(memos = dd.memos + (uid("m") to Memo(body = body, at = "$today $now"))) }
            text = ""
            app.toast("メモを保存しました")
        }
    }
    val last = d.memos.entries.maxByOrNull { it.value.at }
    TileBox {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("すぐメモ", style = plex(18, 700, 1.3), color = c.tx, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Box(Modifier.height(44.dp).press { app.go("memo") }, contentAlignment = Alignment.CenterEnd) {
                Text("すべて ›", style = plex(14, 600, 1.0), color = c.me, maxLines = 1, softWrap = false)
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clip(RoundedCornerShape(14.dp)).background(c.sunk, RoundedCornerShape(14.dp))
                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = plex(15, 400, 1.55).copy(color = c.tx),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                cursorBrush = SolidColor(c.me),
                modifier = Modifier.weight(1f).height(44.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) Text("思いついたことを書く", style = plex(15, 400, 1.55), color = c.tx3, maxLines = 1)
                        inner()
                    }
                },
            )
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(c.meFill).background(lightBrush())
                    .press { save() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("保存", style = plex(15, 700, 1.0), color = c.onMe, maxLines = 1) }
        }
        if (last != null) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    .drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    .press { app.go("memo", last.key) }
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(last.value.body, style = plex(15, 500, 1.5), color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(memoAt(last.value.at), style = plex(13, 500, 1.3).num(), color = c.tx3, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** 2色の中間(color-mix の代わり) */
private fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t)
