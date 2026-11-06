package com.amgm.personallog.v4.screens.plan

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.ui.components.overdueCount
import com.amgm.personallog.v4.data.Plan
import com.amgm.personallog.v4.data.catFx
import com.amgm.personallog.v4.data.nextOn
import com.amgm.personallog.v4.data.todos
import com.amgm.personallog.v4.data.todosOf
import com.amgm.personallog.v4.data.payLabel
import com.amgm.personallog.v4.data.placeOf
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.Route
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.components.AppBar
import com.amgm.personallog.v4.ui.components.AxColumn
import com.amgm.personallog.v4.ui.components.BarAction
import com.amgm.personallog.v4.ui.components.CardAction
import com.amgm.personallog.v4.ui.components.CatTag
import com.amgm.personallog.v4.ui.components.CheckBox
import com.amgm.personallog.v4.ui.components.Chips
import com.amgm.personallog.v4.ui.components.DayHead
import com.amgm.personallog.v4.ui.components.Divider
import com.amgm.personallog.v4.ui.components.Empty as EmptyState
import com.amgm.personallog.v4.ui.components.ERef
import com.amgm.personallog.v4.ui.components.GhostButton
import com.amgm.personallog.v4.ui.components.Knots
import com.amgm.personallog.v4.ui.components.ListCard
import com.amgm.personallog.v4.ui.components.LargeTitle
import com.amgm.personallog.v4.ui.components.MainButton
import com.amgm.personallog.v4.ui.components.Metric
import com.amgm.personallog.v4.ui.components.MomentRow
import com.amgm.personallog.v4.ui.components.ObjCard
import com.amgm.personallog.v4.ui.components.Objs
import com.amgm.personallog.v4.ui.lightBrush
import com.amgm.personallog.v4.ui.components.Prop
import com.amgm.personallog.v4.ui.components.Screen
import com.amgm.personallog.v4.ui.components.SummaryCard
import com.amgm.personallog.v4.ui.components.Toggle
import com.amgm.personallog.v4.ui.components.V4Row
import com.amgm.personallog.v4.ui.components.dashedBorder
import com.amgm.personallog.v4.ui.components.e1
import com.amgm.personallog.v4.ui.components.num
import com.amgm.personallog.v4.ui.plex

/*
 * 予定・ToDo の画面 — plan.js の ToDo タブ・予定の詳細・ToDo の詳細・旧入口。担当: ToDo・予定の担当。
 * 仕様: /home/claude/specs/plan.md。操作の中身は PlanActions.kt、補助は PlanParts.kt。
 */

/** 旧入口 #/plan: すぐ別の場所へ置き換える(ToDo か アクティビティの日・月) */
@Composable
fun PlanScreen(r: Route) {
    val app = LocalApp.current
    LaunchedEffect(Unit) {
        if (r.q["view"] == "todo") app.go("todo")
        else app.go("activities", q = mapOf("view" to if (r.q["view"] == "day") "day" else "month"))
    }
    Box(Modifier.fillMaxSize())
}

/* ---------------- ToDo タブ ---------------- */

@Composable
fun TodoScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val today = app.today
    val view = PlanMem.todoView
    var qa by rememberSaveable { mutableStateOf("") }
    val fr = remember { FocusRequester() }

    val undone = d.todos.filterValues { it.done == null }
    val todayN = undone.values.count { it.due == today }
    val chips = listOf(
        Triple("today", "今日(期限切れ含む)", undone.values.count { it.due != null && it.due <= today }),
        Triple("linked", "予定あり", undone.values.count { it.ev != null }),
        Triple("all", "すべて", undone.size),
    ) + d.lists.map { (k, l) -> Triple("list:$k", l.n, undone.values.count { it.list == k }) }

    val shown = d.todos.filterValues { inView(it, view, today) }
    val open = shown.filterValues { it.done == null }
    val done = shown.filterValues { it.done != null }
    val late = open.filterValues { it.due != null && it.due < today }
    val dueToday = open.filterValues { it.due == today }
    val next = open.filterValues { it.due != null && it.due > today }
    val noDue = open.filterValues { it.due == null }

    fun add() {
        val q = parseQuick(qa, if (view.startsWith("list:")) view.removePrefix("list:") else "inbox", d.lists, today)
        if (q.title.isEmpty()) { app.toast("ToDoの名前を入力してください"); return }
        addTodo(app, q.title, q.list, q.due)
        qa = ""
    }

    Screen {
        LargeTitle("ToDo")
        SummaryCard(
            metrics = listOf(
                Metric("${d.overdueCount(today)}件", "期限切れ") { PlanMem.todoView = "today" },
                Metric("${todayN}件", "今日") { PlanMem.todoView = "today" },
                Metric("${undone.size}件", "全部の未完了") { PlanMem.todoView = "all" },
            ),
            actions = listOf(CardAction("ToDoを追加", V4Icons.plus) { fr.requestFocus() }),
        )
        QuickInput(qa, { qa = it }, fr, onAdd = { add() })
        Chips(chips, view) { PlanMem.todoView = it }

        if (open.isEmpty() && done.isEmpty()) EmptyState(V4Icons.todo, "ToDo なし")
        TodoGroup("期限切れ", late, late = true)
        TodoGroup("今日", dueToday)
        TodoGroup("これから", next)
        TodoGroup("期限なし", noDue)
        if (done.isNotEmpty()) {
            PlSec("完了", done.size.toString(), actLabel = if (PlanMem.showDone) "閉じる" else "表示",
                onAct = { PlanMem.showDone = !PlanMem.showDone })
            if (PlanMem.showDone) Objs { done.forEach { (k, p) -> TodoRow(k, p, true) } }
        }
    }
}

/** 節の中の未完了 ToDo の並び。空なら何も出さない */
@Composable
private fun TodoGroup(title: String, items: Map<String, Plan>, late: Boolean = false) {
    if (items.isEmpty()) return
    PlSec(title, items.size.toString(), late = late)
    Objs { items.forEach { (k, p) -> TodoRow(k, p, false) } }
}

/* ---------------- 予定の詳細 ---------------- */

@Composable
fun EventDetailScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val id = r.id
    val e = id?.let { d.plans[it] }?.takeIf { !it.isTodo }
    if (id == null || e == null) {
        Screen(topBar = { AppBar("予定") }) { EmptyState(V4Icons.schedule, "予定 なし") }
        return
    }
    val today = app.today
    val now = app.now
    val od = nextOn(e, today) ?: e.d ?: today
    val dd = e.d ?: today
    val pay = e.pay
    val pl = e.place
    val late = e.rec == null && (dd < today || (dd == today && !e.all && e.s != null && e.s < now))
    val recKey = e.rec?.takeIf { d.acts.containsKey(it) }
    val kids = d.todosOf(id)
    val rowCount = (if (pl != null) 1 else 0) + (if (e.who.isNotEmpty()) 1 else 0)

    fun openOps() {
        app.openSheet(title = "操作") {
            Column {
                if (pay == null) V4Row(t = "ToDoにする", onClick = { app.closeSheet(); eventToTodo(app, id) })
                V4Row(t = "削除", divider = pay == null, onClick = { app.closeSheet(); app.goBack(); deletePlan(app, id) })
            }
        }
    }

    Screen(topBar = { AppBar("", back = true, actions = listOf(BarAction(V4Icons.more, "操作") { openOps() })) }) {
        // 上に日の札、点線の軸に掛かった予定のカプセル
        Column(Modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter)) {
            DayHead(od, future = od > today)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // 軸(中心 17dp)・予定の節(幅30・高さ18の破線の印)・つなぎ線。カードは左 36dp から(モックの .pin の余白)。
                // 節は軸の上(後に描く)で、カードには掛からない
                val cardColor = if (late) c.late else c.plan
                Box(Modifier.width(36.dp).fillMaxHeight()) {
                    Box(Modifier.offset(x = 16.25.dp).width(1.5.dp).fillMaxHeight().background(c.planLn))
                    Box(Modifier.offset(x = 2.dp, y = 30.dp).size(30.dp, 18.dp).clip(RoundedCornerShape(9.dp))
                        .background(c.bg).dashedBorder(cardColor, width = 2.dp, radius = 9.dp, dash = 3.dp, gap = 3.dp))
                    Box(Modifier.offset(x = 32.dp, y = 38.dp).size(4.dp, 2.dp).background(cardColor))
                }
                Column(
                    Modifier.weight(1f).padding(top = 8.dp, bottom = 12.dp).clip(RoundedCornerShape(24.dp))
                        .background(c.surface).dashedBorder(cardColor, radius = 24.dp).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(V4Icons.schedule, null, tint = c.tx3, modifier = Modifier.size(16.dp))
                        Text(if (pay != null) "予定・支払い" else "予定", style = V4.t.label, color = c.tx2,
                            modifier = Modifier.padding(start = 6.dp).weight(1f))
                        Text(eventTimeLabel(e), style = plex(13, 600).num(), color = c.tx2)
                    }
                    Text(e.t, style = plex(22, 700, 1.3), color = c.tx, modifier = Modifier.padding(top = 6.dp))
                    if (pay != null) Text(yen(pay.amt), style = plex(18, 500).num(), color = c.tx2, modifier = Modifier.padding(top = 2.dp))
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (pl != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(V4Icons.place, null, tint = c.tx3, modifier = Modifier.size(14.dp))
                            Text(d.placeOf(e), style = V4.t.label, color = c.tx2)
                        }
                        if (e.who.isNotEmpty()) Knots(e.who, color = c.tx2, style = V4.t.label)
                        // 「27日後」と「次回 ○日」は1行(モックの .meta: 間隔 14)
                        val remain = remainText(od, e, today, now)
                        if (remain != null || e.rep != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            remain?.let { Text(it, style = plex(15, 600), color = c.tx2) }
                            if (e.rep != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(V4Icons.repeat, null, tint = c.tx3, modifier = Modifier.size(15.dp))
                                Text("次回 ${Dates.fmtMDW(od)}", style = V4.t.label, color = c.tx2)
                            }
                        }
                    }
                }
            }
        }

        if (rowCount > 0) {
            PlSec("関わるエンティティ", rowCount.toString(),
                actLabel = if (pl != null && pl.ent == null) "場所として登録" else null,
                onAct = { registerPlace(app, id) })
            ListCard {
                if (pl != null) Prop(k = "場所", value = { ERef(pl.ent, pl.name) })
                if (e.who.isNotEmpty()) Prop(k = "参加者", divider = pl != null, value = { Knots(e.who, color = c.tx) })
            }
        }

        Spacer(Modifier.height(12.dp))
        ListCard {
            Prop(k = "繰り返し", v = e.rep ?: "なし")
            if (pay != null) Prop(
                k = "支払い", divider = true, meta = "家計簿 ›", onClick = { app.go("ledger") },
                value = {
                    CatTag(pay.cat)
                    Text((d.catFx(pay.cat) ?: "") + "費", style = V4.t.body, color = c.tx)
                },
            )
            Prop(k = "通知", v = e.alarm ?: "なし", divider = true)
            e.memo?.takeIf { it.isNotEmpty() }?.let { Prop(k = "メモ", v = it, divider = true) }
        }

        if (kids.isNotEmpty()) {
            PlSec("この予定のToDo", kids.size.toString())
            Objs { kids.forEach { k -> d.plans[k]?.let { EvTaskRow(k, it) } } }
        }

        if (recKey != null) {
            PlSec(if (e.rep != null) "前回の記録" else "記録", null)
            AxColumn { MomentRow(recKey) }
        } else {
            PlSec("記録", null)
            RecordButton(if (pay != null) "支払いとして記録する" else "記録にする") { recordEvent(app, id) }
        }
    }
}

/** 予定の詳細の「この予定のToDo」の行(モックの plEvTaskRow): 題名と期限だけ。「!」・リスト・予定名は出さない */
@Composable
private fun EvTaskRow(k: String, p: Plan) {
    val app = LocalApp.current
    val c = V4.c
    val done = p.done != null
    ObjCard(onClick = { app.go("task", k) }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBox(on = done, onClick = { toggleTask(app, k) })
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    p.t, style = plex(15, 500, 1.4), color = if (done) c.tx3 else c.tx, maxLines = 2,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                p.due?.let { Text(relDayPl(it, app.today), style = V4.t.cap, color = c.tx3, modifier = Modifier.padding(top = 2.dp)) }
            }
            Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
    }
}

/** 「記録にする」(モックの .btn.block + ✓): 幅いっぱい・高さ48。塗りは主ボタンと同じ、前に ✓ の印 */
@Composable
private fun RecordButton(label: String, onClick: () -> Unit) {
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter, vertical = 12.dp).height(48.dp)
            .shadow(6.dp, RoundedCornerShape(10.dp), ambientColor = c.meFill, spotColor = c.meFill)
            .clip(RoundedCornerShape(10.dp)).background(c.meFill).background(lightBrush())
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(V4Icons.check, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = plex(15, 600), color = Color.White, maxLines = 1)
    }
}

/* ---------------- ToDo の詳細 ---------------- */

@Composable
fun TaskDetailScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val ctx = LocalContext.current
    val today = app.today
    val id = r.id
    val t = id?.let { d.plans[it] }?.takeIf { it.isTodo }
    var listOpen by remember { mutableStateOf(false) }
    if (id == null || t == null) {
        Screen(topBar = { AppBar("ToDo") }) { EmptyState(V4Icons.todo, "ToDo なし") }
        return
    }
    val done = t.done != null

    fun openOps() {
        app.openSheet(title = "操作") {
            Column {
                V4Row(t = "予定にする", onClick = { app.closeSheet(); todoToEvent(app, id) })
                V4Row(t = "削除", divider = true, onClick = { app.closeSheet(); app.go("todo"); deletePlan(app, id) })
            }
        }
    }

    fun pickDue() {
        val p = Dates.parse(t.due ?: today)
        DatePickerDialog(ctx, { _, y, m, dd -> setTaskDue(app, id, "%04d-%02d-%02d".format(y, m + 1, dd)) },
            p.year, p.monthValue - 1, p.dayOfMonth).show()
    }

    val evKey = t.ev
    val ev = evKey?.let { d.plans[it] }?.takeIf { !it.isTodo }
    val ents = t.ent.filter { d.entities.containsKey(it) }
    val payAct = t.payRef?.let { d.acts[it] }

    Screen(topBar = { TaskBar(onBack = { app.go("todo") }, onMore = { openOps() }) }) {
        // 大きい札: 大きなチェック欄・題名・期限と完了
        Column(
            Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 4.dp)
                .e1(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(c.surface).padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CheckBox(on = done, lg = true) { toggleTask(app, id) }
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(t.t, style = plex(20, 700, 1.35), color = if (done) c.tx3 else c.tx,
                        textDecoration = if (done) TextDecoration.LineThrough else null)
                    Text(t.due?.let { "期限 ${Dates.fmtMDW(it)}" } ?: "期限なし", style = V4.t.label, color = c.tx2,
                        modifier = Modifier.padding(top = 12.dp))
                    t.done?.let { Text("完了 ${Dates.fmtMD(it)}", style = V4.t.label, color = c.tx2, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ListCard {
            Prop(k = "期限", value = {
                Text(t.due?.let { Dates.fmtMDW(it) } ?: "なし", style = V4.t.body, color = c.tx,
                    modifier = Modifier.weight(1f).clickable { pickDue() })
                if (t.due != null) GhostButton("消す") { setTaskDue(app, id, null) }
            })
            Prop(k = "リスト", divider = true, value = {
                Box(Modifier.weight(1f)) {
                    Row(Modifier.clickable { listOpen = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text(t.list?.let { d.lists[it]?.n } ?: "なし", style = V4.t.body, color = c.tx)
                        Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = listOpen, onDismissRequest = { listOpen = false }) {
                        d.lists.forEach { (k, l) ->
                            DropdownMenuItem(text = { Text(l.n) }, onClick = { listOpen = false; setTaskList(app, id, k) })
                        }
                    }
                }
            })
            Prop(k = "繰り返し", v = t.rep ?: "なし", divider = true)
            Prop(k = "優先", divider = true, value = { Toggle(t.pri) { togglePri(app, id) } })
        }

        if (ev != null || ents.isNotEmpty() || payAct != null) {
            PlSec("結び付き", null)
            ListCard {
                if (ev != null && evKey != null) V4Row(t = ev.t, s = Dates.fmtMDW(ev.d ?: today), chev = true, onClick = { app.go("ev", evKey) })
                ents.forEachIndexed { i, eid ->
                    if (i > 0 || ev != null) Divider(14.dp)
                    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) { ERef(eid) }
                }
                if (payAct != null && t.payRef != null) V4Row(
                    t = d.payLabel(payAct), v = yen(payAct.amt ?: 0), chev = true, divider = ev != null || ents.isNotEmpty(),
                    onClick = { app.go("a", t.payRef) },
                )
            }
        }

        if (t.sub.isNotEmpty()) {
            PlSec("サブタスク", "${t.sub.count { it.done }}/${t.sub.size}")
            ListCard {
                t.sub.forEachIndexed { i, s ->
                    if (i > 0) Divider(14.dp)
                    Row(Modifier.fillMaxWidth().padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CheckBox(on = s.done) { toggleSub(app, id, i) }
                        Text(s.t, style = plex(15, 500, 1.4), color = if (s.done) c.tx3 else c.tx,
                            textDecoration = if (s.done) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        t.memo?.takeIf { it.isNotEmpty() }?.let { m ->
            PlSec("メモ", null)
            ListCard { Text(m, style = V4.t.body, color = c.tx, modifier = Modifier.padding(14.dp)) }
        }
    }
}
