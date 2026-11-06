package com.amgm.personallog.v4.screens.plan

import com.amgm.personallog.v4.data.Activity
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.Entity
import com.amgm.personallog.v4.data.Expense
import com.amgm.personallog.v4.data.PayItem
import com.amgm.personallog.v4.data.PlaceRef
import com.amgm.personallog.v4.data.Plan
import com.amgm.personallog.v4.data.Role
import com.amgm.personallog.v4.data.XItem
import com.amgm.personallog.v4.data.dayKeyByDate
import com.amgm.personallog.v4.data.uid
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.Route

/*
 * 予定・ToDo の操作 — plan.js の recordEvent / toggleTask / todoToEvent / eventToTodo など。担当: ToDo・予定の担当。
 * ほかの画面(ホーム・エンティティ・アクティビティ)はここを呼ぶ(署名は変えない)。
 * 書くときは app.store.update だけを使う。取り消せる操作は toast(undo = true)。
 */

/** 予定を記録にする(支払い予定なら支払い+自分の支出)。記録の詳細へ移る */
fun recordEvent(app: AppState, evKey: String) {
    val e = app.data.plans[evKey] ?: return
    if (e.rec != null || e.isTodo) return
    val date = e.d ?: app.today
    val dayKey = uid("d"); val actKey = uid("a"); val xKey = uid("x")
    app.store.update { d -> recordIn(d, evKey, date, dayKey, actKey, xKey) }
    app.go("a", actKey)
    app.toast("記録にしました")
}

/** 記録を作って予定の rec に入れる(純粋な変換。store の中で呼ぶ) */
private fun recordIn(d: AppData, evKey: String, date: String, dayKey: String, actKey: String, xKey: String): AppData {
    val e = d.plans[evKey] ?: return d
    if (e.rec != null || e.isTodo) return d
    val existing = d.dayKeyByDate(date)
    val day = existing ?: dayKey
    val placeEnt = e.place?.ent
    val placeName = e.place?.name ?: e.t
    val pay = e.pay
    val act: Activity
    var expense: Expense? = null
    if (pay != null) {
        val roles = listOf(
            Role("支払先", placeEnt, if (placeEnt == null) placeName else null),
            Role("支払者", "me"),
        ) + e.who.map { Role("同席", it) }
        act = Activity(
            type = "支払い", parent = day, time = e.s ?: "09:00", title = "支払い", pay = "カード ••1234", amt = pay.amt,
            roles = roles, items = listOf(PayItem(n = e.t, price = pay.amt, b = "自分", tax = 10)), x = xKey,
        )
        expense = Expense(pay = actKey, amt = pay.amt, cat = pay.cat, items = listOf(XItem(e.t, pay.amt)))
    } else {
        val ranged = !e.all && e.s != null && e.e != null
        val roles = e.who.map { Role("参加", it) } +
            listOfNotNull(e.place?.let { Role("場所", it.ent, if (it.ent == null) it.name else null) })
        act = Activity(
            type = "出来事", parent = day,
            time = if (ranged) e.s else if (e.all) "09:00" else (e.s ?: "09:00"),
            end = if (ranged) e.e else null, title = e.t, roles = roles,
        )
    }
    var acts = d.acts + (actKey to act)
    acts = if (existing == null) {
        acts + (dayKey to Activity(type = "日記", date = date, dd = Dates.parse(date).dayOfMonth.toString(), dw = Dates.wd(date), kids = listOf(actKey)))
    } else {
        acts + (existing to d.acts.getValue(existing).let { it.copy(kids = it.kids + actKey) })
    }
    val expenses = if (expense != null) d.expenses + (xKey to expense) else d.expenses
    return d.copy(acts = acts, expenses = expenses, plans = d.plans + (evKey to e.copy(rec = actKey)))
}

/** ToDo の完了/未完了を切り替える(完了は取り消せる。未完了に戻す操作は取り消さない) */
fun toggleTask(app: AppState, key: String) {
    val wasDone = app.data.plans[key]?.done != null
    app.store.update { d -> val t = d.plans[key] ?: return@update d; d.copy(plans = d.plans + (key to t.copy(done = if (t.done == null) app.today else null))) }
    if (wasDone) app.toast("未完了に戻しました") else app.toast("完了にしました", undo = true)
}

/** 1件の予定 / ToDo を書き換える */
fun updatePlan(app: AppState, key: String, f: (Plan) -> Plan) {
    app.store.update { d -> val p = d.plans[key] ?: return@update d; d.copy(plans = d.plans + (key to f(p))) }
}

/** ToDo を予定にする(同じキー)。日付は期限、時刻は無し(終日)。予定の詳細へ移る */
fun todoToEvent(app: AppState, key: String) {
    val t = app.data.plans[key]?.takeIf { it.isTodo } ?: return
    val from = t.ev?.takeIf { app.data.plans[it]?.isTodo == false }
    updatePlan(app, key) { p ->
        p.copy(
            kind = Plan.KIND_EVENT, d = p.due ?: app.today, all = true, s = null, e = null,
            done = null, list = null, pri = false, due = null, ev = null, payRef = null, from = from,
        )
    }
    app.replace(Route("ev", listOf(key)))
}

/** 予定を ToDo にする(同じキー)。残すのは題名・繰り返し・メモ・結び付くエンティティ。支払い予定は不可 */
fun eventToTodo(app: AppState, key: String): Boolean {
    val e = app.data.plans[key]?.takeIf { !it.isTodo } ?: return false
    if (e.pay != null) { app.toast("支払い予定はToDoにできません"); return false }
    app.store.update { d ->
        val p = d.plans[key] ?: return@update d
        val moved = d.plans.mapValues { (k, x) -> if (k != key && x.isTodo && x.ev == key) x.copy(ev = null) else x }
        d.copy(plans = moved + (key to Plan(kind = Plan.KIND_TODO, t = p.t, due = p.d, list = "inbox", rep = p.rep, memo = p.memo, ent = p.ent)))
    }
    app.replace(Route("task", listOf(key)))
    return true
}

/** 予定または ToDo を消す(取り消せる)。記録は消さない。呼ぶ側で画面を戻す */
fun deletePlan(app: AppState, key: String) {
    app.store.update { d -> d.copy(plans = d.plans - key) }
    app.toast("削除しました", undo = true)
}

/** 予定の場所を未登録から登録済みへ(場所のエンティティを作る)。取り消せる */
fun registerPlace(app: AppState, key: String) {
    val name = app.data.plans[key]?.place?.name ?: return
    val ek = uid("en")
    app.store.update { d ->
        val q = d.plans[key] ?: return@update d
        d.copy(
            entities = d.entities + (ek to Entity(n = name, t = listOf("place"))),
            plans = d.plans + (key to q.copy(place = PlaceRef(ek, null))),
        )
    }
    app.toast("「$name」を場所として登録しました", undo = true)
}

/** ToDo を追加する(クイック追加・作成) */
fun addTodo(app: AppState, title: String, list: String, due: String?) {
    app.store.update { d -> d.copy(plans = d.plans + (uid("t") to Plan(kind = Plan.KIND_TODO, t = title, list = list, due = due))) }
    app.toast("ToDoを追加しました")
}

/** ToDo の期限を変える(null で消す) */
fun setTaskDue(app: AppState, key: String, due: String?) = updatePlan(app, key) { it.copy(due = due) }

/** ToDo のリストを変える */
fun setTaskList(app: AppState, key: String, list: String) = updatePlan(app, key) { it.copy(list = list) }

/** ToDo の優先を付け外しする */
fun togglePri(app: AppState, key: String) = updatePlan(app, key) { it.copy(pri = !it.pri) }

/** サブタスクの完了を切り替える(親の完了には影響しない) */
fun toggleSub(app: AppState, key: String, index: Int) = updatePlan(app, key) { p ->
    p.copy(sub = p.sub.mapIndexed { j, s -> if (j == index) s.copy(done = !s.done) else s })
}
