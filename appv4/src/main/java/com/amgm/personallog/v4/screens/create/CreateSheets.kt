package com.amgm.personallog.v4.screens.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.Activity
import com.amgm.personallog.v4.data.catAnc
import com.amgm.personallog.v4.data.catFxRaw
import com.amgm.personallog.v4.data.catName
import com.amgm.personallog.v4.data.catRoot
import com.amgm.personallog.v4.data.roots
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.Entity
import com.amgm.personallog.v4.data.Fact
import com.amgm.personallog.v4.data.Memo
import com.amgm.personallog.v4.data.Plan
import com.amgm.personallog.v4.data.PlaceRef
import com.amgm.personallog.v4.data.Role
import com.amgm.personallog.v4.data.dateOf
import com.amgm.personallog.v4.data.dayKeyByDate
import com.amgm.personallog.v4.data.propsOf
import com.amgm.personallog.v4.data.typeAnc
import com.amgm.personallog.v4.data.uid
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.components.Field
import com.amgm.personallog.v4.ui.components.ListCard
import com.amgm.personallog.v4.ui.components.Knot
import com.amgm.personallog.v4.ui.components.Seg
import com.amgm.personallog.v4.ui.components.Toggle
import com.amgm.personallog.v4.ui.components.V4Row
import com.amgm.personallog.v4.ui.plex

/*
 * 作成シートの本体(金額だけ・出来事・予定/ToDo・メモ・エンティティ・日記・活動への追加)。
 * 入口は Create.kt の公開関数から呼ぶ。状態は holder(mutableStateOf)に持つ。
 */

/* ================= 金額だけで支払い ================= */

internal class QuickHolder(date: String, time: String, act: String?) {
    var amt by mutableStateOf("")
    var shop by mutableStateOf("")
    var date by mutableStateOf(date)
    var time by mutableStateOf(time)
    /** null=既定(時刻で決める) / ""=なし / キー */
    var act by mutableStateOf(act)
    var cat by mutableStateOf("eatout")
    var fx by mutableStateOf<String?>(null)
    var sp by mutableStateOf<String?>(null)
}

internal fun openQuickPayment(app: AppState, parent: String?, date: String?, time: String?) {
    val d0 = app.data
    val parentDate = parent?.let { d0.dateOf(it) }?.takeIf { it.isNotEmpty() }
    val h = QuickHolder(date = date ?: parentDate ?: app.today, time = time ?: app.now, act = parent)
    app.openSheet(title = "金額だけで支払い", mainLabel = "支払いを保存", onMain = { saveQuick(app, h) }) {
        QuickBody(app, h)
    }
}

@Composable
private fun QuickBody(app: AppState, h: QuickHolder) {
    val d = LocalData.current
    val ctx = LocalContext.current
    val c = V4.c
    val root = d.catRoot(h.cat)
    val children = d.cats.filter { it.value.p == root }.keys.toList()
    val askFx = d.catFxRaw(h.cat) == "ask"
    val askSp = d.catAnc(h.cat).reversed().firstNotNullOfOrNull { d.cats[it]?.sp } == "ask"
    val actResolved = d.actResolve(h.date, h.time, h.act)
    val actOpts = d.actOptions(h.date, actResolved)
    Column {
        ListCard {
            AmountField(h.amt, { h.amt = it })
            PickRow("日付", h.date, divider = true) { showDatePicker(ctx, h.date) { h.date = it } }
            PickRow("時刻", h.time, divider = true) { showTimePicker(ctx, h.time) { h.time = it } }
            Field("店", h.shop, { h.shop = it }, placeholder = "店名を入力", divider = true)
            SuggestChips(d.placeHits(h.shop), padH = 14.dp) { h.shop = it }
            SelectRow("活動の中", actOpts.firstOrNull { it.first == (actResolved ?: "") }?.second ?: "なし(日の直下)", actOpts, divider = true) { h.act = it }
        }
        SheetHead("カテゴリ")
        ChipRow {
            d.roots().forEach { r ->
                SChip(d.catName(r), r == root) { h.cat = d.cats.filter { it.value.p == r }.keys.firstOrNull() ?: r }
            }
        }
        if (children.isNotEmpty()) {
            ListCard {
                SelectRow("小分類", d.catName(h.cat), children.map { it to d.catName(it) }) { h.cat = it }
            }
        }
        if (askFx || askSp) {
            SheetHead("このカテゴリは都度指定")
            ListCard {
                if (askFx) LdMiniRow("固定・変動", listOf("固定", "変動"), h.fx) { h.fx = it }
                if (askSp) LdMiniRow("特別感", listOf("日常", "非日常"), h.sp, divider = askFx) { h.sp = it }
            }
        }
    }
}

/** 固定・変動 / 特別感 の小さい2択(押した同じものは外す) */
@Composable
internal fun LdMiniRow(label: String, opts: List<String>, cur: String?, divider: Boolean = false, onPick: (String?) -> Unit) {
    Column {
        if (divider) com.amgm.personallog.v4.ui.components.Divider(14.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, style = V4.t.label, color = V4.c.tx3, modifier = Modifier.width(84.dp))
            ChipRow(padH = 0.dp) { opts.forEach { o -> SChip(o, cur == o) { onPick(if (cur == o) null else o) } } }
        }
    }
}

private fun saveQuick(app: AppState, h: QuickHolder) {
    val amt = digitsOf(h.amt)
    if (amt == 0) { app.toast("金額を入れてください"); return }
    val d0 = app.data
    val askFx = d0.catFxRaw(h.cat) == "ask"
    val askSp = d0.catAnc(h.cat).reversed().firstNotNullOfOrNull { d0.cats[it]?.sp } == "ask"
    var savedId: String? = null
    app.store.update { d ->
        val (nd, id) = d.addPayment(
            PayIn(
                date = h.date, time = h.time, act = h.act, pay = "現金",
                shop = h.shop.trim(), register = false,
                items = emptyList(), total = amt,
                cat = h.cat, fx = if (askFx) h.fx else null, sp = if (askSp) h.sp else null,
                with = null, todo = emptyList(),
            ),
            app.today,
        )
        savedId = id
        nd
    }
    app.closeSheet()
    savedId?.let { app.go("a", it) }
    app.toast("支払いを保存しました(自分の支出=全額)", undo = true)
}

/* ================= 出来事(期間・瞬間) ================= */

private val SPAN_TYPES = listOf("食事", "会話", "作業", "移動", "買い物", "その他")
private val INST_TYPES = listOf("起床", "就寝", "出発", "到着", "その他")

internal class HappenHolder(val date: String, span0: Boolean, val parent: String?, t0: String, end0: String) {
    var span by mutableStateOf(span0)
    var type by mutableStateOf(if (span0) "食事" else "起床")
    var title by mutableStateOf("")
    var start by mutableStateOf(t0)
    var end by mutableStateOf(end0)
    var from by mutableStateOf("")
    var to by mutableStateOf("")
    var place by mutableStateOf("")
    val who = mutableStateListOf<String>()
    var body by mutableStateOf("")
    /** null=既定(時刻で決める) / ""=なし / キー */
    var act by mutableStateOf<String?>(parent)
}

/** 出来事を記録(期間 or 瞬間)。親・日付・時刻は活動から開いた時に引き継ぐ */
internal fun openHappening(app: AppState, instant: Boolean, parent: String?, date: String?, time: String?) {
    val d = app.data
    val pa = parent?.let { d.acts[it] }
    val day = date ?: parent?.let { d.dateOf(it) }?.takeIf { it.isNotEmpty() } ?: app.today
    val t0 = time ?: pa?.time ?: app.now
    val end0 = pa?.end ?: plusMin(t0, 60)
    val h = HappenHolder(day, !instant, parent, t0, end0)
    app.openSheet(title = "出来事を記録", mainLabel = "出来事を保存", onMain = { saveHappening(app, h) }) {
        HappeningBody(app, h)
    }
}

@Composable
private fun HappeningBody(app: AppState, h: HappenHolder) {
    val d = LocalData.current
    val ctx = LocalContext.current
    val types = if (h.span) SPAN_TYPES else INST_TYPES
    val resolved = d.actResolve(h.date, h.start, h.act)
    val opts = d.actOptions(h.date, resolved)
    Column {
        Seg(listOf("span" to "期間", "inst" to "瞬間"), cur = if (h.span) "span" else "inst") { v ->
            h.span = v == "span"
            h.type = if (h.span) "食事" else "起床"
        }
        SheetHead("種類")
        ChipRow { types.forEach { t -> SChip(t, h.type == t) { h.type = t } } }
        ListCard {
            Field("タイトル", h.title, { h.title = it }, placeholder = "例: 進路の相談")
            if (h.span) {
                PickRow("開始", h.start, divider = true) { showTimePicker(ctx, h.start) { h.start = it } }
                PickRow("終了", h.end, divider = true) { showTimePicker(ctx, h.end) { h.end = it } }
            } else {
                PickRow("時刻", h.start, divider = true) { showTimePicker(ctx, h.start) { h.start = it } }
            }
            SelectRow("活動の中", opts.firstOrNull { it.first == (resolved ?: "") }?.second ?: "なし(日の直下)", opts, divider = true) { h.act = it }
            if (h.span) {
                if (h.type == "移動") {
                    Field("出発地", h.from, { h.from = it }, placeholder = "出発地を入力", divider = true)
                    SuggestChips(d.placeHits(h.from), padH = 14.dp) { h.from = it }
                    Field("到着地", h.to, { h.to = it }, placeholder = "到着地を入力", divider = true)
                    SuggestChips(d.placeHits(h.to), padH = 14.dp) { h.to = it }
                } else {
                    Field("場所", h.place, { h.place = it }, placeholder = "場所を入力", divider = true)
                    SuggestChips(d.placeHits(h.place), padH = 14.dp) { h.place = it }
                }
            }
        }
        if (h.span) {
            if (h.type != "移動") {
                SheetHead("参加者")
                ChipRow {
                    d.peopleList().forEach { (k, n) ->
                        SChip(n, k in h.who) { if (k in h.who) h.who.remove(k) else h.who.add(k) }
                    }
                }
            }
            SheetHead("内容")
            ListCard { TextArea(h.body, { h.body = it }, minH = 90.dp, placeholder = "何があったか") }
        }
    }
}

private fun saveHappening(app: AppState, h: HappenHolder) {
    if (!h.span) {
        if (h.start.isBlank()) { app.toast("時刻を入れてください"); return }
    } else {
        if (h.start.isBlank() || h.end.isBlank()) { app.toast("開始と終了を入れてください"); return }
        if (h.end <= h.start) { app.toast("終了は開始より後にしてください"); return }
    }
    var savedId: String? = null
    app.store.update { d ->
        val (nd, day) = d.withDay(h.date)
        val par = d.actResolve(h.date, h.start, h.act) ?: day
        val id = uid("a")
        val a = if (h.span) {
            val type = if (h.type == "その他") "出来事" else h.type
            val roles = buildList {
                h.who.forEach { add(Role("参加", it)) }
                if (h.type == "移動") {
                    if (h.from.isNotBlank()) add(placeRole("出発", h.from, d))
                    if (h.to.isNotBlank()) add(placeRole("到着", h.to, d))
                } else if (h.place.isNotBlank()) {
                    add(placeRole("場所", h.place, d))
                }
            }
            Activity(
                type = type, parent = par, time = h.start, end = h.end,
                title = h.title.trim().ifEmpty { type }, roles = roles, body = h.body,
            )
        } else {
            val isOther = h.type == "その他"
            Activity(
                type = if (isOther) "出来事(瞬間)" else h.type, parent = par, time = h.start,
                title = if (isOther) "出来事" else h.type,
            )
        }
        var acts = nd.acts + (id to a)
        val pa = acts[par]
        if (pa != null) acts = acts + (par to pa.copy(kids = pa.kids + id))
        savedId = id
        nd.copy(acts = acts)
    }
    app.closeSheet()
    savedId?.let { app.go("a", it) }
    app.toast("出来事を保存しました", undo = true)
}

/** 場所の役割。登録済みの名前なら参照、無ければ文字だけ */
internal fun placeRole(role: String, text: String, d: com.amgm.personallog.v4.data.AppData): Role {
    val key = d.entByName(text)
    return Role(role, key, if (key == null) text.trim() else null)
}

/* ================= 活動に子を追加(＋ アクティビティを追加) ================= */

internal fun openKidSheet(app: AppState, parent: String) {
    app.openSheet(title = "アクティビティを追加") {
        val d = LocalData.current
        val date = d.dateOf(parent)
        val time = d.acts[parent]?.time ?: app.now
        Column {
            KidRow("支払い", V4Icons.pay, first = true) {
                app.closeSheet()
                openQuickPayment(app, parent, date, time)
            }
            KidRow("期間のアクティビティ", V4Icons.moment) {
                app.closeSheet()
                openHappening(app, false, parent, date, time)
            }
            KidRow("瞬間", V4Icons.spark) {
                app.closeSheet()
                openHappening(app, true, parent, date, time)
            }
        }
    }
}

@Composable
private fun KidRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, first: Boolean = false, onClick: () -> Unit) {
    ListCard {
        V4Row(
            t = label, divider = !first, onClick = onClick,
            lead = { com.amgm.personallog.v4.ui.components.Ico(icon, V4.c.tx2) },
        )
    }
}

/* ================= 予定・ToDo ================= */

internal class PlanHolder(date0: String, val ev: String?) {
    var isTodo by mutableStateOf(false)
    var title by mutableStateOf("")
    var all by mutableStateOf(false)
    var date by mutableStateOf(date0)
    var start by mutableStateOf("10:00")
    var end by mutableStateOf("11:00")
    var place by mutableStateOf("")
    val who = mutableStateListOf<String>()
    var rep by mutableStateOf("なし")
    var alarm by mutableStateOf("なし")
    var due by mutableStateOf("")
    var list by mutableStateOf("inbox")
    var pri by mutableStateOf(false)
}

private val REPS = listOf("なし", "毎日", "毎週", "毎月")
private val ALARMS = listOf("なし", "10分前", "30分前", "1時間前", "前日")

/** 予定(todo=false)か ToDo(todo=true)を作る。date=日付、ev=親の予定(ToDo) */
internal fun openPlan(app: AppState, date: String?, todo: Boolean, ev: String?) {
    val h = PlanHolder(date ?: app.today, ev)
    h.isTodo = todo
    showPlan(app, h)
}

/** シートを開き直す(主ボタンの文言が予定 / ToDo で変わるため) */
internal fun showPlan(app: AppState, h: PlanHolder) {
    app.openSheet(title = "予定", mainLabel = if (h.isTodo) "ToDoを追加" else "予定を追加", onMain = { savePlan(app, h) }) {
        PlanBody(app, h)
    }
}

@Composable
private fun PlanBody(app: AppState, h: PlanHolder) {
    val d = LocalData.current
    val ctx = LocalContext.current
    Column {
        Seg(listOf("event" to "予定", "todo" to "ToDo"), cur = if (h.isTodo) "todo" else "event") { v ->
            h.isTodo = v == "todo"
            showPlan(app, h)
        }
        ListCard {
            Field("タイトル", h.title, { h.title = it }, placeholder = if (h.isTodo) "例: 牛乳を買う" else "例: 花子さんとカフェ")
            if (!h.isTodo) {
                RowField("終日", divider = true) {
                    Spacer(Modifier.weight(1f))
                    Toggle(on = h.all, onClick = { h.all = !h.all })
                }
                PickRow("日付", h.date, divider = true) { showDatePicker(ctx, h.date) { h.date = it } }
                if (!h.all) {
                    PickRow("開始", h.start, divider = true) { showTimePicker(ctx, h.start) { h.start = it } }
                    PickRow("終了", h.end, divider = true) { showTimePicker(ctx, h.end) { h.end = it } }
                }
                Field("場所", h.place, { h.place = it }, placeholder = "場所を入力", divider = true)
                SuggestChips(d.placeHits(h.place), padH = 14.dp) { h.place = it }
                SelectRow("繰り返し", h.rep, REPS.map { it to it }, divider = true) { h.rep = it }
                SelectRow("通知", h.alarm, ALARMS.map { it to it }, divider = true) { h.alarm = it }
            } else {
                PickRow("期限", h.due) { showDatePicker(ctx, h.due.ifEmpty { h.date }) { h.due = it } }
                val lists = d.lists.map { it.key to it.value.n }
                SelectRow("リスト", d.lists[h.list]?.n ?: "", lists, divider = true) { h.list = it }
                RowField("優先", divider = true) {
                    Spacer(Modifier.weight(1f))
                    Toggle(on = h.pri, onClick = { h.pri = !h.pri })
                }
            }
        }
        SheetHead(if (h.isTodo) "関係する人" else "参加者")
        ChipRow {
            d.peopleList().forEach { (k, n) ->
                SChip(n, k in h.who) { if (k in h.who) h.who.remove(k) else h.who.add(k) }
            }
        }
    }
}

private fun savePlan(app: AppState, h: PlanHolder) {
    val title = h.title.trim()
    if (title.isEmpty()) { app.toast("タイトルを入れてください"); return }
    val d = app.data
    if (h.isTodo) {
        val id = uid("t")
        val todo = Plan(
            kind = Plan.KIND_TODO, t = title, list = h.list, due = h.due.ifEmpty { null },
            pri = h.pri, ent = h.who.toList(), ev = h.ev,
        )
        app.store.update { x -> x.copy(plans = x.plans + (id to todo)) }
        app.closeSheet()
        app.rerender()
        app.toast("ToDoを追加しました", undo = true)
    } else {
        val id = uid("ev")
        val rep = when (h.rep) {
            "毎日" -> "毎日"
            "毎週" -> "毎週${Dates.wd(h.date)}曜"
            "毎月" -> "毎月${Dates.parse(h.date).dayOfMonth}日"
            else -> null
        }
        val pl = h.place.trim()
        val ev = Plan(
            kind = Plan.KIND_EVENT, t = title, d = h.date.ifEmpty { null }, all = h.all,
            s = if (h.all) null else h.start, e = if (h.all) null else h.end,
            place = if (pl.isEmpty()) null else PlaceRef(d.entByName(pl), pl),
            who = h.who.toList(), rep = rep, alarm = h.alarm,
        )
        app.store.update { x -> x.copy(plans = x.plans + (id to ev)) }
        app.closeSheet()
        app.go("ev", id)
        app.toast("予定を追加しました", undo = true)
    }
}

/* ================= メモ ================= */

internal fun openMemo(app: AppState, link: String?) {
    val h = MemoHolder()
    app.openSheet(title = "メモ", mainLabel = "メモを保存", onMain = {
        val body = h.body.trim()
        if (body.isEmpty()) {
            app.toast("本文を入れてください")
        } else {
            val id = uid("q")
            app.store.update { d ->
                d.copy(memos = d.memos + (id to Memo(body = body, at = "${app.today} ${app.now}", links = listOfNotNull(link))))
            }
            app.closeSheet()
            app.rerender()
            app.toast("メモを保存しました", undo = true)
        }
    }) {
        ListCard { TextArea(h.body, { h.body = it }, minH = 140.dp, placeholder = "思いついたこと") }
    }
}

internal class MemoHolder {
    var body by mutableStateOf("")
}

/* ================= エンティティ ================= */

internal class EntHolder {
    var type by mutableStateOf("person")
    var name by mutableStateOf("")
    val vals = mutableStateMapOf<String, String>()
}

internal fun openEntity(app: AppState) {
    val h = EntHolder()
    app.openSheet(title = "エンティティを追加", mainLabel = "追加", onMain = { saveEntity(app, h) }) {
        EntityBody(h)
    }
}

@Composable
private fun EntityBody(h: EntHolder) {
    val d = LocalData.current
    val c = V4.c
    val root = d.typeAnc(h.type).firstOrNull() ?: h.type
    Column {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Knot(size = 14.dp, unreg = true, color = c.kin)
            Text(d.types[root]?.n ?: "", style = V4.t.cap, color = c.tx3)
        }
        ListCard {
            Field(
                "名前", h.name, { h.name = it },
                placeholder = when (root) {
                    "person" -> "例: 鈴木 一郎"
                    "project" -> "例: 引っ越し準備"
                    else -> "例: 本屋 ひだまり"
                },
            )
            SelectRow("種類", d.types[h.type]?.n ?: "", d.typeOptions(), divider = true) { h.type = it }
            d.propsOf(h.type).forEach { (k, _) ->
                Field(k, h.vals[k] ?: "", { h.vals[k] = it }, placeholder = "任意", divider = true)
            }
        }
    }
}

private fun saveEntity(app: AppState, h: EntHolder) {
    val name = h.name.trim()
    if (name.isEmpty()) { app.toast("名前を入れてください"); return }
    val d = app.data
    val facts = d.propsOf(h.type).mapNotNull { (k, _) ->
        h.vals[k]?.trim()?.takeIf { it.isNotEmpty() }?.let { Fact(k, it) }
    }
    val id = uid("e")
    val ent = Entity(n = name, i = name.take(1), t = listOf(h.type), facts = facts)
    app.store.update { x -> x.copy(entities = x.entities + (id to ent)) }
    app.closeSheet()
    app.go("e", id)
    app.toast("「$name」を追加しました", undo = true)
}

/* ================= 日記 ================= */

internal class DiaryHolder(initial: String) {
    var body by mutableStateOf(initial)
}

internal fun openDiary(app: AppState, date: String) {
    val d = app.data
    val h = DiaryHolder(d.dayKeyByDate(date)?.let { d.acts[it]?.body }.orEmpty())
    val title = if (date == app.today) "今日の日記" else "日記"
    app.openSheet(title = title, mainLabel = "日記を保存", onMain = {
        app.store.update { x ->
            val (nd, k) = x.withDay(date)
            val a = nd.acts[k]!!
            nd.copy(acts = nd.acts + (k to a.copy(body = h.body)))
        }
        app.closeSheet()
        app.rerender()
        app.toast("日記を保存しました", undo = true)
    }) {
        Column {
            Text(
                Dates.fmtMDW(date), style = V4.t.label, color = V4.c.tx2,
                modifier = Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 4.dp),
            )
            ListCard { TextArea(h.body, { h.body = it }, minH = 240.dp, placeholder = "今日はどんな日だった?") }
        }
    }
}
