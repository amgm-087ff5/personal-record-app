package com.amgm.personallog.v4.screens.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.payLabel
import com.amgm.personallog.v4.data.catColor
import com.amgm.personallog.v4.data.evOn
import com.amgm.personallog.v4.data.actsOn
import com.amgm.personallog.v4.data.todosDueOn
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.screens.plan.TodoRow
import com.amgm.personallog.v4.screens.plan.toggleTask
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.colorOf
import com.amgm.personallog.v4.ui.lightBrush
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.components.AxColumn
import com.amgm.personallog.v4.ui.components.CheckBox
import com.amgm.personallog.v4.ui.components.DayHead
import com.amgm.personallog.v4.ui.components.Divider
import com.amgm.personallog.v4.ui.components.ListCard
import com.amgm.personallog.v4.ui.components.MomentRow
import com.amgm.personallog.v4.ui.components.Objs
import com.amgm.personallog.v4.ui.components.rail
import com.amgm.personallog.v4.ui.components.PlanRowAx
import com.amgm.personallog.v4.ui.components.SectionHead
import com.amgm.personallog.v4.ui.components.dashedBorder
import com.amgm.personallog.v4.ui.components.num
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.ui.components.Empty
import java.time.YearMonth

/*
 * 日・月の表示の中身と、リスト・日のページで共通の「日の行」。plan.js の plDayView / plMonthView / plDayAx / plTodoBlock に当たる。
 * 寸法は s-plan.css と plan.md §4〜§7 の値。時間グリッドは 6:00〜24:00、1時間=48dp。
 */

/** 期間の送り(periodNav)。白いピルの中に 前 / 名前 / 小さい文字 / 次 */
@Composable
fun ActPeriod(label: String, sub: String, prevLabel: String, nextLabel: String, modifier: Modifier = Modifier, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = V4.c
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxHeight().clickable(onClick = onPrev).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(prevLabel, style = V4.t.label, color = c.brand, maxLines = 1)
        }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = plex(16, 700).num(), color = c.tx, maxLines = 1)
            if (sub.isNotEmpty()) Text(sub, style = plex(13, 500), color = c.tx3, maxLines = 1)
        }
        Box(Modifier.fillMaxHeight().clickable(onClick = onNext).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(nextLabel, style = V4.t.label, color = c.brand, maxLines = 1)
        }
    }
}

/** 一覧と日のページで使う、その日の行(ax の中)。今日は「いま」の線で過去と未来を分ける。
 *  late=過ぎて記録していない予定(赤)。future=これからの予定(点線の軸)。 */
@Composable
fun ColumnScope.ActAxRows(d: String, filter: String, today: String, now: String, nowMod: Modifier = Modifier) {
    val data = LocalData.current
    val items = data.dayItemsOf(d, filter)
    fun isFut(x: DayItem) = !x.rec && data.plans[x.k]?.let { e -> data.isFuturePlanOf(e, today, now) } == true
    // 遅れ: 過去の日の予定は全部。今日は、これからの予定以外。未来の日は遅れない
    fun late(x: DayItem) = !x.rec && (d < today || (d == today && !isFut(x)))
    if (d != today) {
        items.forEach { ActItemRow(it = it, future = false, late = late(it)) }
        return
    }
    items.filter { !isFut(it) }.forEach { ActItemRow(it = it, future = false, late = late(it)) }
    Box(nowMod.fillMaxWidth()) { DayNowLine(now) }
    items.filter { isFut(it) }.forEach { ActItemRow(it = it, future = true, late = false) }
}

@Composable
private fun ActItemRow(it: DayItem, future: Boolean, late: Boolean) {
    if (it.rec) MomentRow(it.k) else PlanRowAx(it.k, future = future, late = late)
}

/* ---------- 日の表示(view = day) ---------- */

/** 日の表示の本体: 日の札(余白なし)・終日・時間グリッド・その日の ToDo */
@Composable
fun ActDayPanel(sel: String) {
    val app = LocalApp.current
    val data = LocalData.current
    val hol = data.evOn(sel).firstOrNull { it.second.hol }?.second?.t
    val items = data.plItemsOf(sel)
    Column(Modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter)) {
        PanelDayCard(sel, data.diaryBodyOf(sel), data.daySpentOf(sel).takeIf { it > 0 }, hol)
        val allday = items.filter { it.all }
        if (allday.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allday.forEach { ActAllDayBar(it) }
            }
        }
        Spacer(Modifier.height(10.dp))
        ActTimeGrid(items.filter { !it.all }, isToday = sel == app.today, now = app.now)
        ActTodoBlock(sel)
    }
}

/** 日の札(dpanel の中。.pl-dpanel .dh2 は余白 0)。押すと日のページ */
@Composable
private fun PanelDayCard(d: String, diary: String?, sum: Int?, empty: String?) {
    val app = LocalApp.current
    val c = V4.c
    val today = d == app.today
    val shape = RoundedCornerShape(16.dp)
    val dt = Dates.parse(d)
    val fg = if (today) Color.White else c.tx
    Row(
        Modifier.fillMaxWidth()
            .let {
                if (today) it.shadow(10.dp, shape, ambientColor = c.meFill, spotColor = c.meFill).clip(shape).background(c.meFill).background(lightBrush())
                else it.clip(shape).border(1.dp, c.line, shape).background(c.surface)
            }
            .clickable { app.go("day", d) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.size(52.dp, 56.dp).clip(RoundedCornerShape(14.dp)).background(if (today) Color.White.copy(alpha = 0.18f) else c.dateSoft),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(dt.dayOfMonth.toString(), style = plex(24, 700, 1.0).num(), color = if (today) Color.White else c.dateInk)
            Text(Dates.wd(d), style = plex(12, 600, 1.0), color = if (today) Color.White else c.dateInk)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${Dates.fmtMD(d)} ${Dates.wd(d)}曜", style = plex(15, 700, 1.3), color = fg, maxLines = 1)
                if (today) Text("今日", style = plex(13, 600, 1.0), color = fg)
            }
            val sub = diary?.takeIf { it.isNotEmpty() } ?: empty
            if (sub != null) Text(sub, style = plex(13, 500, 1.4), color = if (today) Color.White.copy(alpha = 0.82f) else c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sum != null) Text("自分 ${yen(sum)}", style = plex(13, 500, 1.0).num(), color = if (today) Color.White.copy(alpha = 0.82f) else c.tx3)
    }
}

/** 終日の予定の帯(.pl-bar.plan)。押すと予定の詳細 */
@Composable
private fun ActAllDayBar(x: PlItem) {
    val app = LocalApp.current
    val data = LocalData.current
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.surface)
            .dashedBorder(c.plan, radius = 22.dp)
            .clickable { app.go(if (x.rec) "a" else "ev", x.k) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (x.cat.isNotEmpty()) {
            Box(Modifier.size(8.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(colorOf(data.catColor(x.cat))))
            Spacer(Modifier.width(6.dp))
        }
        Text(x.title, style = plex(14, 500), color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 時間グリッド(6:00〜24:00)。1時間=48dp。記録は塗りの帯、予定は点線の帯。重なりは列に分ける */
@Composable
private fun ActTimeGrid(items: List<PlItem>, isToday: Boolean, now: String) {
    val app = LocalApp.current
    val data = LocalData.current
    val c = V4.c
    val h0 = 6 * 60
    val h1 = 24 * 60
    val perHour = 48f

    class Blk(val x: PlItem, val s: Int, val e: Int) { var lane = 0 }
    val blocks = items.map { x ->
        val s = maxOf(Dates.minutes(x.time), h0)
        val raw = x.end?.let { Dates.minutes(it) } ?: (s + 36)
        Blk(x, s, minOf(maxOf(raw, s + 36), h1))
    }.sortedBy { it.s }
    val lanesEnd = ArrayList<Int>()
    blocks.forEach { b ->
        var i = lanesEnd.indexOfFirst { it <= b.s }
        if (i < 0) { i = lanesEnd.size; lanesEnd.add(0) }
        lanesEnd[i] = b.e
        b.lane = i
    }
    val lanes = maxOf(1, lanesEnd.size)

    BoxWithConstraints(Modifier.fillMaxWidth().height((18 * perHour + 12).dp)) {
        val gridW: Dp = maxWidth - 52.dp
        // 時刻と区切り線
        for (h in 6..24) {
            val y = ((h - 6) * perHour).dp
            Text(
                "$h:00", style = plex(12, 500).num(), color = c.tx3, textAlign = TextAlign.End,
                modifier = Modifier.offset(y = y - 8.dp).width(48.dp).padding(end = 8.dp),
            )
            Box(Modifier.offset(x = 52.dp, y = y).width(gridW).height(1.dp).background(c.line))
        }
        // 記録と予定の帯
        blocks.forEach { b ->
            val x = b.x
            val inst = x.end == null && x.rec // 終わりの無い記録は、その時刻の細いカプセル
            val top = if (inst) (b.s - h0) / 60f * perHour - 14f else (b.s - h0) / 60f * perHour
            val height = if (inst) 28f else maxOf((b.e - b.s) / 60f * perHour, 44f)
            val left = 52.dp + gridW * (b.lane.toFloat() / lanes)
            val width = gridW / lanes - 4.dp
            val a = if (x.rec) data.acts[x.k] else null
            val title = if (a?.type == "支払い") "${data.payLabel(a)} ${yen(a.amt ?: 0)}" else x.title
            val fg = if (x.rec) c.onCap else c.tx2
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier.offset(x = left, y = top.dp).width(width).height(height.dp).clip(shape)
                    .let { if (x.rec) it.background(c.ev).background(lightBrush()) else it.background(c.surface).dashedBorder(c.plan, radius = 12.dp) }
                    .clickable { app.go(if (x.rec) "a" else "ev", x.k) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                if (inst) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(x.time, style = plex(12, 500).num(), color = fg.copy(alpha = 0.85f), maxLines = 1)
                        Text(title, style = plex(12, 600), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Column {
                        Text(title, style = plex(13, 600, 1.3), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (height >= 48f) {
                            val tm = x.end?.let { "${x.time}–$it" } ?: x.time
                            Text(tm, style = plex(12, 500, 1.3).num(), color = fg.copy(alpha = 0.85f), maxLines = 1)
                        }
                    }
                }
            }
        }
        // いまの線(今日のとき)
        if (isToday) {
            val ny = ((minutesOf(now) - h0) / 60f * perHour).dp
            Row(Modifier.offset(y = ny - 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    now, style = plex(12, 700).num(), color = c.late, textAlign = TextAlign.End,
                    modifier = Modifier.width(52.dp).padding(end = 6.dp),
                )
                Box(Modifier.width(gridW).height(2.dp).clip(RoundedCornerShape(1.dp)).background(c.late))
            }
        }
    }
}

private fun minutesOf(t: String): Int = Dates.minutes(t)

/* ---------- 月の表示(view = month) ---------- */

/** 月の本体: 7列のカレンダー・凡例、続けて選んだ日の面(plDayAx)と ToDo */
@Composable
fun ActMonthPanel(sel: String) {
    val app = LocalApp.current
    val data = LocalData.current
    val c = V4.c
    val ymStr = sel.take(7)
    val ym = YearMonth.parse(ymStr)
    val first = ym.atDay(1).dayOfWeek.value % 7 // 日曜始まり
    val cells: List<String?> = List(first) { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it).toString() }
    val weeks = cells.chunked(7).map { w -> w + List(7 - w.size) { null } }
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "月", "火", "水", "木", "金", "土").forEachIndexed { i, w ->
                Text(w, style = plex(12, 500, 2.0), color = if (i == 0) c.tx2 else c.tx3, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    if (d == null) Spacer(Modifier.weight(1f)) else ActMonthCell(d, Modifier.weight(1f))
                }
            }
        }
        ActLegend()
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter)) {
        ActDayAxBody(sel)
        ActTodoBlock(sel)
    }
}

/** 月のマス(.pl-cell): 日の数字・件数の印(最大4つ)・ToDo の件数 */
@Composable
private fun ActMonthCell(d: String, modifier: Modifier) {
    val app = LocalApp.current
    val data = LocalData.current
    val c = V4.c
    val its = data.plItemsOf(d)
    val tds = data.todoCountOn(d)
    val hol = data.evOn(d).any { it.second.hol }
    val sun = hol || Dates.isSunday(d)
    val isSel = d == ActNav.sel ?: app.today
    val isToday = d == app.today
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.padding(horizontal = 1.dp, vertical = 1.5.dp).heightIn(min = 64.dp).clip(shape)
            .let {
                when {
                    isToday -> it.border(1.dp, c.glow, shape).background(c.meSoft)
                    sun -> it.background(c.sunk.copy(alpha = 0.55f))
                    else -> it
                }
            }
            .clickable { ActNav.sel = d }
            .padding(top = 4.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).let { if (isSel) it.background(c.me).background(lightBrush()) else it }, contentAlignment = Alignment.Center) {
            Text(
                Dates.parse(d).dayOfMonth.toString(),
                style = plex(15, if (isToday || sun) 700 else 500).num(),
                color = when { isSel -> c.onMe; isToday -> c.me; else -> c.tx },
            )
        }
        Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            its.take(4).forEach { p ->
                val s = if (p.cat.isNotEmpty()) RoundedCornerShape(2.dp) else RoundedCornerShape(999.dp)
                Box(Modifier.size(10.dp, 6.dp).clip(s).let { if (p.rec) it.background(c.ev) else it.dashedBorder(c.plan, radius = 2.dp) })
            }
        }
        Row(Modifier.height(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tds > 0) {
                Icon(V4Icons.check, null, tint = c.tx2, modifier = Modifier.size(12.dp))
                Text("$tds", style = plex(12, 600, 1.0), color = c.tx2)
            }
        }
    }
}

/** 凡例(.pl-legend): 予定 / 記録 / 支払い予定 / ToDo */
@Composable
private fun ActLegend() {
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(10.dp, 6.dp).legendDash(c.plan, 3.dp))
            Text("予定", style = V4.t.cap, color = c.tx3)
        }
        Spacer(Modifier.width(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(10.dp, 6.dp).clip(RoundedCornerShape(999.dp)).background(c.ev))
            Text("記録", style = V4.t.cap, color = c.tx3)
        }
        Spacer(Modifier.width(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(10.dp, 6.dp).legendDash(c.plan, 2.dp))
            Text("支払い予定", style = V4.t.cap, color = c.tx3)
        }
        Spacer(Modifier.width(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(V4Icons.check, null, tint = c.tx3, modifier = Modifier.size(12.dp))
            Text("ToDo", style = V4.t.cap, color = c.tx3)
        }
    }
}

/** 選んだ日の面(plDayAx): 日の札と、その日の記録・予定を時刻順に。今日は「いま」の線で分ける */
@Composable
private fun ActDayAxBody(d: String) {
    val app = LocalApp.current
    val data = LocalData.current
    val c = V4.c
    val today = app.today
    val now = app.now
    val future = d > today
    val hol = data.evOn(d).firstOrNull { it.second.hol }?.second?.t
    val entries = ArrayList<AxEntry>()
    data.actsOn(d).forEach { (k, a) -> entries.add(AxEntry(a.time ?: "", k, rec = true, fut = false, late = false)) }
    data.evOn(d).filter { (_, e) -> !e.hol && !data.planRecordedOn(e, d) }.forEach { (k, e) ->
        val fut = future || (d == today && !e.all && (e.s ?: "") > now)
        val late = !fut && !(d == today && e.all)
        entries.add(AxEntry(if (e.all) "00:00" else e.s ?: "", k, rec = false, fut = fut, late = late))
    }
    val sorted = entries.sortedBy { it.time }
    AxColumn {
        DayHead(d, diary = data.diaryBodyOf(d), sum = data.daySpentOf(d).takeIf { it > 0 }, empty = hol, future = future)
        if (sorted.isEmpty()) {
            Text("予定・記録 なし", style = plex(13, 400, 1.4), color = c.tx3, modifier = Modifier.padding(top = 12.dp, bottom = 14.dp))
        } else if (d == today) {
            sorted.filter { !it.fut }.forEach { AxEntryRow(it) }
            Box(Modifier.fillMaxWidth()) { DayNowLine(now) }
            sorted.filter { it.fut }.forEach { AxEntryRow(it) }
        } else {
            sorted.forEach { AxEntryRow(it) }
        }
    }
}

private class AxEntry(val time: String, val k: String, val rec: Boolean, val fut: Boolean, val late: Boolean)

@Composable
private fun AxEntryRow(e: AxEntry) {
    if (e.rec) MomentRow(e.k) else PlanRowAx(e.k, future = e.fut, late = e.late)
}

/** 凡例の点線の印(.pl-dot.plan): 1.5 の破線の輪郭。10×6 の小さい印に合わせて破線を細かく */
private fun Modifier.legendDash(color: Color, radius: Dp): Modifier = drawBehind {
    val w = 1.5.dp.toPx()
    drawRoundRect(
        color = color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.dp.toPx()))),
    )
}

/** いまの線の列の幅(Axis.kt の時間の列・軸の列と同じ値) */
private val NOW_TCOL = 52.dp
private val NOW_RAIL = 26.dp

/** いまの線(日の時間グリッド): 時刻を白い地の箱に載せる(モックの .pl-now span)。軸と線の描き方は NowLine と同じ */
@Composable
private fun DayNowLine(now: String) {
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().height(28.dp).rail(false, c.axisPast, c.planLn, 0f, 0.5f).rail(true, c.axisPast, c.planLn, 0.5f, 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(NOW_TCOL).fillMaxHeight().background(c.tlCard), contentAlignment = Alignment.CenterEnd) {
            Text(now, style = plex(12, 700, 1.0).num(), color = c.late, modifier = Modifier.padding(end = 6.dp))
        }
        Box(Modifier.width(NOW_RAIL), contentAlignment = Alignment.Center) { Box(Modifier.size(12.dp, 2.dp).clip(RoundedCornerShape(2.dp)).background(c.late)) }
        Box(Modifier.weight(1f).padding(end = 20.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(c.late))
    }
}

/* ---------- その日の ToDo(plTodoBlock) ---------- */

/** その日が期限の未完了の ToDo。0件なら何も出さない。チェック=完了、行=ToDo の詳細 */
@Composable
fun ActTodoBlock(d: String) {
    val data = LocalData.current
    val ks = data.todosDueOn(d)
    if (ks.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionHead("ToDo", "${ks.size}件")
        // 行は ToDo の一覧と同じ部品(題名・「!」・期限・リスト・予定名)
        Objs { ks.forEach { (k, t) -> TodoRow(k, t, false) } }
    }
}
