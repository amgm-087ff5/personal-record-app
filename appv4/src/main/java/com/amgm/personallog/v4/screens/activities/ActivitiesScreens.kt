package com.amgm.personallog.v4.screens.activities

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.dateOf
import com.amgm.personallog.v4.data.evOn
import com.amgm.personallog.v4.data.events
import com.amgm.personallog.v4.data.todos
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.screens.create.openActivitySheet
import com.amgm.personallog.v4.screens.create.openDiarySheet
import com.amgm.personallog.v4.screens.create.openPlanSheet
import com.amgm.personallog.v4.screens.create.openReceiptSheet
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.Route
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.lightBrush
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.components.AppBar
import com.amgm.personallog.v4.ui.components.AxColumn
import com.amgm.personallog.v4.ui.components.AxEdge
import com.amgm.personallog.v4.ui.components.BarAction
import com.amgm.personallog.v4.ui.components.DayHead
import com.amgm.personallog.v4.ui.components.ERef
import com.amgm.personallog.v4.ui.components.Empty
import com.amgm.personallog.v4.ui.components.IconBtn
import com.amgm.personallog.v4.ui.components.PlanRowAx
import com.amgm.personallog.v4.ui.components.Screen
import com.amgm.personallog.v4.ui.components.SectionHead
import com.amgm.personallog.v4.ui.components.dashedBorder
import com.amgm.personallog.v4.ui.components.num
import com.amgm.personallog.v4.ui.components.tlShadow
import java.time.YearMonth

/*
 * アクティビティ(タブ)と日のページ。route('activities')(view = list|day|month)と route('day')。
 * 仕様: /home/claude/specs/activities.md。日・月の本体は ActDayMonth.kt、状態と計算は ActNav.kt。
 */

/** 表示の切替の行(.act-views)。選んだ表示は --surface の塗りと影 */
@Composable
private fun ActViewSeg(cur: String, onPick: (String) -> Unit) {
    val c = V4.c
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = V4Dim.gutter), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp)).background(c.sunk).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf("list" to "リスト", "day" to "日", "month" to "月").forEach { (v, label) ->
                val on = v == cur
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .let { if (on) it.shadow(2.dp, shape, ambientColor = Color(0x14283A5A), spotColor = Color(0x14283A5A)).clip(shape).background(c.surface) else it.clip(shape) }
                        .clickable { onPick(v) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = plex(13, if (on) 600 else 500), color = if (on) c.tx else c.tx2, maxLines = 1)
                }
            }
        }
    }
}

/** 見出しの行(.act-lt): 題名と検索 */
@Composable
private fun ActTitleRow(app: AppState) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = V4Dim.gutter, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "アクティビティ", style = plex(28, 700, 1.2).copy(letterSpacing = 0.01.em), color = V4.c.tx,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        IconBtn(V4Icons.search, "検索") { app.toast("検索はモックでは未対応です") }
    }
}

/** 絞り込み(.act-chips): 高さ44のピル。選んだものは --me-soft と --me */
@Composable
private fun ActChips(cur: String, onPick: (String) -> Unit) {
    val c = V4.c
    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = V4Dim.gutter),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ACT_FILTERS.forEach { (v, label) ->
                val on = v == cur
                Row(
                    Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(if (on) c.meSoft else c.sunk)
                        .clickable { onPick(v) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = plex(13, if (on) 600 else 500), color = if (on) c.me else c.tx2, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** まとめの札(.act-sc): 数字3つ(押せるものは予定・ToDoへ)と、作る2つのボタン */
@Composable
private fun ActSummary(app: AppState, ym: String, monthRec: Int, todayEv: Int, todoCount: Int, onView: (String) -> Unit) {
    val c = V4.c
    val month = ym.substring(5, 7).toInt()
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 4.dp).fillMaxWidth().height(130.dp)
            .tlShadow(shape).clip(shape).background(c.tlCard).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp)) {
            ActMetric("${monthRec}件", "${month}月の記録", first = true, href = null, modifier = Modifier.weight(1f)) {}
            ActMetric("${todayEv}件", "今日の予定", first = false, href = "day", modifier = Modifier.weight(1f)) { onView("day") }
            ActMetric("${todoCount}件", "今日のToDo", first = false, href = "todo", modifier = Modifier.weight(1f)) { app.go("todo") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActBtn("レシートで支払い", V4Icons.camera, primary = true, modifier = Modifier.weight(1f)) { openReceiptSheet(app) }
            ActBtn("出来事", V4Icons.plus, primary = false, modifier = Modifier.weight(1f)) { openActivitySheet(app) }
        }
    }
}

@Composable
private fun ActMetric(num: String, label: String, first: Boolean, href: String?, modifier: Modifier, onClick: () -> Unit) {
    val c = V4.c
    Row(modifier.fillMaxHeight()) {
        // 2つ目以降の左の区切り線(1dp)
        if (!first) Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .let { if (href != null) it.clickable(onClick = onClick) else it }
                .padding(start = if (first) 4.dp else 10.dp, end = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(num, style = plex(20, 700, 1.2).num(), color = c.tx, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = plex(12, 500, 1.3), color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (href != null) Icon(V4Icons.chev, null, tint = c.tx2, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** 作るボタン(.sc-acts .btn): 高さ48・文字14。primary は塗り、そうでなければ淡い塗り */
@Composable
private fun ActBtn(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = V4.c
    val shape = RoundedCornerShape(10.dp)
    val fg = if (primary) Color.White else c.me
    Row(
        modifier.height(48.dp)
            .let {
                if (primary) it.shadow(6.dp, shape, ambientColor = c.meFill, spotColor = c.meFill).clip(shape).background(c.meFill).background(lightBrush())
                else it.clip(shape).background(c.meSoft)
            }
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = plex(14, 600), color = fg, maxLines = 1, softWrap = false)
    }
}

/** 月の切替(.act-mn): 丸い枠の前後と「YYYY年M月」。範囲の外は薄く押せない */
@Composable
private fun ActMonthNav(ym: String, canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = V4.c
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = V4Dim.gutter, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        ActRoundNav(false, "前の月", canPrev, onPrev)
        Spacer(Modifier.width(18.dp))
        Text(
            "${ym.substring(0, 4)}年${ym.substring(5, 7).toInt()}月", style = plex(16, 700, 1.0),
            color = c.tx, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 110.dp),
        )
        Spacer(Modifier.width(18.dp))
        ActRoundNav(true, "次の月", canNext, onNext)
    }
}

@Composable
private fun ActRoundNav(next: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = V4.c
    Box(
        Modifier.size(44.dp).graphicsLayer { alpha = if (enabled) 1f else 0.3f }.clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.lineStrong, CircleShape).background(c.surface))
        Icon(V4Icons.back, label, tint = c.tx2, modifier = Modifier.size(22.dp).let { if (next) it.rotate(180f) else it })
    }
}

/** 期間の行(日・月の表示の上): 送り + 丸い「予定を追加」 */
@Composable
private fun ActPeriodRow(app: AppState, view: String, sel: String, today: String, data: AppData, onPick: (String) -> Unit) {
    val c = V4.c
    val hol = data.evOn(sel).firstOrNull { it.second.hol }?.second?.t
    val month = sel.take(7)
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = V4Dim.gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (view == "day") {
            ActPeriod(
                label = Dates.fmtMDW(sel), sub = if (sel == today) "今日" else (hol ?: ""),
                prevLabel = "前の日", nextLabel = "次の日", modifier = Modifier.weight(1f),
                onPrev = { ActNav.sel = Dates.addDays(sel, -1) }, onNext = { ActNav.sel = Dates.addDays(sel, 1) },
            )
        } else {
            ActPeriod(
                label = "${month.substring(5, 7).toInt()}月", sub = month.substring(0, 4),
                prevLabel = "前の月", nextLabel = "次の月", modifier = Modifier.weight(1f),
                onPrev = { ActNav.sel = pickMonthSel(YearMonth.parse(month).minusMonths(1).toString(), today) },
                onNext = { ActNav.sel = pickMonthSel(YearMonth.parse(month).plusMonths(1).toString(), today) },
            )
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.meSoft).clickable { openPlanSheet(app, date = sel) }, contentAlignment = Alignment.Center) {
            Icon(V4Icons.plus, "予定を追加", tint = c.me, modifier = Modifier.size(20.dp))
        }
    }
}

/** 月を動かした後の選んだ日(今月なら今日、それ以外はその月の1日) */
private fun pickMonthSel(ym: String, today: String): String = if (ym == today.take(7)) today else "$ym-01"

/** 1行の見出し(.act-lt)+表示の切替。リスト・日・月で共通 */
@Composable
fun ActivitiesScreen(r: Route) {
    val app = LocalApp.current
    val data = LocalData.current
    val c = V4.c
    val today = app.today
    val now = app.now
    val todayYm = today.take(7)
    val view = r.q["view"].takeIf { it == "day" || it == "month" } ?: "list"
    val filter = ActNav.filter
    val ym = ActNav.ym ?: todayYm
    val sel = ActNav.sel ?: today
    val isNow = ym == todayYm

    val density = LocalDensity.current
    var headPx by remember { mutableIntStateOf(0) }
    val headDp = with(density) { headPx.toDp() }
    val listState = rememberLazyListState()

    val recDates = data.recordDatesOf(filter, today)
    val minYm = (recDates + today).min().take(7)
    val dates = recDates.filter { it.startsWith(ym) }
    val monthRec = data.acts.entries.count { (k, a) -> a.type != "日記" && data.dateOf(k).startsWith(ym) }
    val todayEv = data.evOn(today).count { !it.second.hol }
    val todoCount = data.todos.values.count { it.done == null && it.due != null && it.due <= today }
    val later = data.events.values.count { e -> !e.hol && e.rec == null && (e.d ?: "") > Dates.addDays(today, 14) }

    fun setView(v: String) {
        if (v == view) return
        if (v == "list") ActNav.ym = sel.take(7)
        else if (view == "list") ActNav.sel = pickMonthSel(ym, today)
        app.replace(Route("activities", q = mapOf("view" to v)))
    }

    // 開いた時と月の切替の位置。今月の一覧は今日の札を上部のすぐ下(8dp)へ、前の月は一番下へ。日・月は一番上へ
    LaunchedEffect(view, ym, headPx) {
        if (headPx == 0) return@LaunchedEffect
        if (view != "list") {
            listState.scrollToItem(0)
        } else if (isNow) {
            val idx = 1 + (if (ym <= minYm) 1 else 0) + dates.size
            listState.scrollToItem(idx, -(headPx + with(density) { 8.dp.toPx() }).toInt())
        } else {
            listState.scrollToItem(0)
            listState.scroll { scrollBy(100_000f) }
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "head-space") { Spacer(Modifier.height(headDp)) }
            if (view == "list") {
                listBody(
                    app = app, data = data, filter = filter, today = today, now = now, ym = ym, dates = dates,
                    minYm = minYm, isNow = isNow, planOn = planVisibleOf(filter), later = later,
                )
            } else {
                item(key = "pl-$view") {
                    if (view == "day") ActDayPanel(sel) else ActMonthPanel(sel)
                }
            }
            item(key = "end") { Spacer(Modifier.height(V4Dim.pageEnd)) }
        }

        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(c.bg).onSizeChanged { headPx = it.height },
        ) {
            ActTitleRow(app)
            ActViewSeg(view, ::setView)
            if (view == "list") {
                val monthRecVal = monthRec
                ActSummary(app, ym, monthRecVal, todayEv, todoCount) { v -> setView(v) }
                ActMonthNav(
                    ym = ym, canPrev = ym > minYm, canNext = ym < todayYm,
                    onPrev = { ActNav.ym = java.time.YearMonth.parse(ym).minusMonths(1).toString() },
                    onNext = { ActNav.ym = java.time.YearMonth.parse(ym).plusMonths(1).toString() },
                )
                ActChips(filter) { ActNav.filter = it }
            } else {
                ActPeriodRow(app, view, sel, today, data, ::setView)
            }
        }
    }
}

private fun planVisibleOf(filter: String) = planVisible(filter)

/** 一覧の本体(リスト)。日の札と行を日ごと。今月は今日の札と、明日からの2週間の予定 */
private fun LazyListScope.listBody(
    app: AppState, data: AppData, filter: String, today: String, now: String, ym: String, dates: List<String>,
    minYm: String, isNow: Boolean, planOn: Boolean, later: Int,
) {
    if (ym <= minYm) {
        item(key = "edge-past") { AxColumn { AxEdge("これより前の記録 なし", past = true) } }
    }
    dates.forEach { d ->
        item(key = "d-$d") {
            AxColumn {
                DayHead(d, diary = data.diaryBodyOf(d), sum = data.daySpentOf(d).takeIf { it > 0 })
                ActAxRows(d, filter, today, now)
            }
        }
    }
    if (isNow) {
        item(key = "today") {
            AxColumn {
                DayHead(today, diary = data.diaryBodyOf(today), sum = data.daySpentOf(today).takeIf { it > 0 }, empty = "今日の日記を書く")
                ActAxRows(today, filter, today, now)
            }
        }
    } else if (dates.isEmpty()) {
        item(key = "none") {
            Text("この月の記録はありません", style = plex(13, 400, 1.4), color = V4.c.tx3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
        }
    }
    if (isNow && planOn) {
        for (i in 1..14) {
            val day = Dates.addDays(today, i)
            val evs = data.evOn(day).filter { it.second.rec == null }
            if (evs.isEmpty()) continue
            val hol = evs.firstOrNull { it.second.hol }
            val rows = evs.filter { !it.second.hol }
            item(key = "f-$day") {
                AxColumn {
                    DayHead(day, future = true, empty = hol?.second?.t)
                    rows.forEach { PlanRowAx(it.first, future = true, late = false) }
                }
            }
        }
        item(key = "later") {
            AxColumn {
                AxEdge(if (later > 0) "これより先の予定 ${later}件" else "予定 › 月で見る", past = false, chev = true) {
                    app.replace(Route("activities", q = mapOf("view" to "month")))
                }
            }
        }
    }
    if (isNow) item(key = "room") { Spacer(Modifier.height(LocalConfiguration.current.screenHeightDp.dp)) }
}

/** 日のページ(route('day')): 日付・日記・数字・この日に交わった縁・この日の時間軸 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayScreen(r: Route) {
    val app = LocalApp.current
    val data = LocalData.current
    val c = V4.c
    val today = app.today
    val d = r.id ?: today
    val filter = ActNav.filter
    val diary = data.diaryOf(d)
    val items = data.dayItemsOf(d, filter)
    val sp = data.daySpentOf(d)
    val people = items.filter { it.rec }.flatMap { data.acts[it.k]?.roles.orEmpty() }.mapNotNull { it.ent }.filter { it != "me" }.distinct()
    val dl = (data.acts.values.filter { it.type == "日記" }.mapNotNull { it.date } + today).distinct().sorted()
    val prev = dl.lastOrNull { it < d }
    val next = dl.firstOrNull { it > d }

    // 日のページは上端から開く(モックの正解画像どおり。今日の「いま」の位置合わせはしない)
    val scroll = rememberScrollState()

    Screen(
        scroll = scroll,
        topBar = {
            AppBar("", back = true, actions = buildList<BarAction> {
                if (next != null) add(BarAction(V4Icons.up, "次の日(未来へ)") { app.go("day", next) })
                if (prev != null) add(BarAction(V4Icons.down, "前の日(過去へ)") { app.go("day", prev) })
                add(BarAction(V4Icons.edit, "日記を書く") { openDiarySheet(app, d) })
            })
        },
    ) {
        // 日付の見出し
        val dt = Dates.parse(d)
        Row(
            Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 4.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(dt.dayOfMonth.toString(), style = plex(76, 600, 0.9).num().copy(letterSpacing = (-0.04).em), color = c.tx)
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("${Dates.wd(d)}曜日", style = plex(17, 600, 1.3), color = c.tx)
                Text(
                    "${dt.year}年${dt.monthValue}月" + if (d == today) "・今日" else "",
                    style = V4.t.cap, color = c.tx3, modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // 日記の本文(無ければ書くための文字)
        val body = diary?.body?.takeIf { it.isNotEmpty() }
        if (body != null) {
            Text(body, style = plex(17, 400, 1.95), color = c.tx, modifier = Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 18.dp))
        } else {
            Text(
                "この日のことを書く…", style = V4.t.body, color = c.tx3,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { openDiarySheet(app, d) }.padding(start = V4Dim.gutter, top = 18.dp),
            )
        }
        val photos = diary?.photos ?: 0
        if (photos > 0) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = V4Dim.gutter, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(photos) { DayPhoto(d, it) }
            }
        }

        // 数字の段
        Row(Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 14.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatCell("${items.count { it.rec }}件", "出来事")
            if (sp > 0) StatCell(yen(sp), "自分の支出")
        }

        if (people.isNotEmpty()) {
            SectionHead("この日に交わった縁", n = "${people.size}")
            FlowRow(
                Modifier.padding(horizontal = V4Dim.gutter),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { people.forEach { ERef(it) } }
        }

        SectionHead("この日の時間軸")
        if (items.isNotEmpty()) {
            AxColumn {
                ActAxRows(d, filter, today, app.now)
            }
            if (d == today) Spacer(Modifier.height(LocalConfiguration.current.screenHeightDp.dp))
        } else {
            Empty(V4Icons.activity, "この日の出来事はまだありません", "出来事を追加") { openActivitySheet(app, date = d) }
        }
    }
}

/**
 * 日の写真の1枚(モックの photoThumb): 72 角丸12。空・太陽・草原の絵。色と丘の形は日と番号で決まる(同じ日なら同じ絵)
 * 絵は 88 の座標で描いて 72dp に縮める
 */
/** CSS の hsl(色相, 彩度, 明度) を Color にする(色相は度、彩度と明度は 0〜1) */
private fun hslColor(hue: Float, sat: Float, light: Float): Color {
    val c = (1f - kotlin.math.abs(2f * light - 1f)) * sat
    val hp = (hue % 360f) / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = light - c / 2f
    return Color(r + m, g + m, b + m)
}

@Composable
private fun DayPhoto(d: String, i: Int) {
    val day = d.takeLast(2).toInt()
    val h = (day * 37 + i * 71) % 40
    val sky = hslColor(195f + h / 2f, 0.5f, 0.74f)
    val land = hslColor(85f + h, 0.28f, 0.42f)
    val sun = hslColor(45f + h / 4f, 0.85f, 0.86f)
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / 88f
            drawRect(sky)
            drawCircle(sun, radius = 9f * s, center = Offset((20f + i * 18f) * s, 26f * s))
            // 手前の丘: M0 64 Q22 (46+6i) 44 60 T88 56 V88 H0Z (T は前の制御点を鏡映し)
            val hill = Path().apply {
                moveTo(0f * s, 64f * s)
                quadraticBezierTo(22f * s, (46f + i * 6f) * s, 44f * s, 60f * s)
                quadraticBezierTo(66f * s, (74f - i * 6f) * s, 88f * s, 56f * s)
                lineTo(88f * s, 88f * s); lineTo(0f, 88f * s); close()
            }
            drawPath(hill, land)
            // 奥の草原(70%): M0 74 Q30 66 60 76 T88 72 V88 H0Z
            val far = Path().apply {
                moveTo(0f, 74f * s)
                quadraticBezierTo(30f * s, 66f * s, 60f * s, 76f * s)
                quadraticBezierTo(90f * s, 86f * s, 88f * s, 72f * s)
                lineTo(88f * s, 88f * s); lineTo(0f, 88f * s); close()
            }
            drawPath(far, land, alpha = 0.7f)
        }
    }
}

@Composable
private fun StatCell(num: String, label: String) {
    val c = V4.c
    Column {
        Text(num, style = plex(17, 600, 1.2).num(), color = c.tx)
        Text(label, style = V4.t.cap, color = c.tx3)
    }
}
