package com.amgm.personallog.v4.data

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** 日付の文字列(YYYY-MM-DD)の補助 */
object Dates {
    private const val WD = "日月火水木金土"
    fun parse(d: String): LocalDate = LocalDate.parse(d.substring(0, 10))
    /** 曜日1文字(日〜土) */
    fun wd(d: String): String = WD[parse(d).dayOfWeek.value % 7].toString()
    fun isSunday(d: String) = parse(d).dayOfWeek.value == 7
    fun addDays(d: String, n: Int): String = parse(d).plusDays(n.toLong()).toString()
    /** 10/30 */
    fun md(d: String): String { val x = parse(d); return "${x.monthValue}/${x.dayOfMonth}" }
    /** 10/30(金) */
    fun mdw(d: String): String = "${md(d)}(${wd(d)})"
    /** 10月30日 */
    fun fmtMD(d: String): String { val x = parse(d); return "${x.monthValue}月${x.dayOfMonth}日" }
    /** 10月30日(金) */
    fun fmtMDW(d: String): String = "${fmtMD(d)}(${wd(d)})"
    fun ym(d: String): String = d.substring(0, 7)
    fun daysBetween(a: String, b: String): Long = java.time.temporal.ChronoUnit.DAYS.between(parse(a), parse(b))
    /** 今日/昨日/明日/n日前 */
    fun relDay(d: String, today: String): String = when (val n = daysBetween(today, d)) {
        0L -> "今日"; -1L -> "昨日"; 1L -> "明日"; -2L -> "おととい"
        else -> if (n < 0) "${-n}日前" else "${n}日後"
    }
    fun minutes(t: String): Int { val (h, m) = t.split(":"); return h.toInt() * 60 + m.toInt() }
    fun hhmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
}

/** いま(画面のテスト・見本の再現では固定できる) */
interface AppClock {
    fun today(): String
    fun now(): String
}

object SystemClock : AppClock {
    override fun today(): String = LocalDate.now().toString()
    override fun now(): String = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
}

/** モックと同じ「いま」= 2026-10-31 13:20(スクリーンショットの照合用) */
class FixedClock(private val d: String = Seed.MOCK_TODAY, private val t: String = Seed.MOCK_NOW) : AppClock {
    override fun today() = d
    override fun now() = t
}

/** ¥4,580 */
fun yen(n: Int): String = "¥" + NumberFormat.getIntegerInstance(Locale.JAPAN).format(n)

/**
 * data.js の純粋なヘルパーを AppData の拡張として移したもの。名前は data.js と同じにしてある。
 */
val INSTANT_TYPES = setOf("起床", "就寝", "出発", "到着", "支払い")

fun AppData.typeAnc(t: String?): List<String> { val r = ArrayList<String>(); var k = t; while (k != null) { r.add(0, k); k = types[k]?.p }; return r }
fun AppData.isUnder(t: String, root: String) = typeAnc(t).contains(root)

/** その縁が関わった記録 [キー, 記録, 役割]。親の活動も関わっていれば子は除く(同じことを2回出さない) */
fun AppData.actsOf(id: String): List<Triple<String, Activity, String>> {
    val hit = acts.entries.filter { (_, a) -> a.roles.any { it.ent == id } }
    val keys = hit.map { it.key }.toSet()
    return hit.filter { (_, a) -> !(a.parent != null && a.parent in keys) }
        .map { (k, a) -> Triple(k, a, a.roles.first { it.ent == id }.role) }
}

/** 関係 [種類, 相手, 期間, お互いか]。相手側から見た向きに直す(勤務先 → 従業員) */
fun AppData.relsOf(id: String): List<Rel> {
    val out = ArrayList<Rel>()
    for ((k, e) in entities) for (r in e.rels) {
        if (k == id) out.add(r)
        else if (r.to == id) out.add(Rel(if (r.mutual) r.kind else (mapOf("勤務先" to "従業員")[r.kind] ?: r.kind), k, r.period, r.mutual))
    }
    return out
}

/** 親をたどって日(日記)のキーを返す */
fun AppData.dayKeyOf(id: String?): String? {
    var k = id?.let { acts[it]?.parent }
    while (k != null) { val p = acts[k] ?: return null; if (p.type == "日記") return k; k = p.parent }
    return null
}
fun AppData.dayOf(id: String?): Activity? = dayKeyOf(id)?.let { acts[it] }
fun AppData.dateOf(id: String): String { val a = acts[id]; return dayOf(id)?.date ?: a?.date ?: "" }
fun Activity.isSpan() = end != null
fun Activity.timeText() = if (end != null) "$time–$end" else (time ?: "")
/** 瞬間(時刻1つ)。日記以外で終了が無いもの */
fun Activity.isInstant() = type != "日記" && end == null
fun AppData.parentAct(id: String): String? { val p = acts[id]?.parent ?: return null; return if (acts[p]?.type != "日記") p else null }
fun AppData.kidActs(id: String): List<String> = acts[id]?.kids?.filter { acts.containsKey(it) } ?: emptyList()
/** 祖先の活動のキー(日記を除く)。外側が先 */
fun AppData.ancIds(id: String): List<String> {
    val r = ArrayList<String>(); var k = acts[id]?.parent
    while (k != null && acts[k] != null && acts[k]!!.type != "日記") { r.add(0, k); k = acts[k]!!.parent }
    return r
}
fun AppData.descOf(id: String): List<String> {
    val out = ArrayList<String>()
    fun walk(k: String) { for (c in acts[k]?.kids ?: emptyList()) if (acts.containsKey(c)) { out.add(c); walk(c) } }
    walk(id); return out
}
/** 日記のキーを日付で */
fun AppData.dayKeyByDate(d: String): String? = acts.entries.firstOrNull { it.value.type == "日記" && it.value.date == d }?.key
/** その日のアクティビティ(日記以外) */
fun AppData.actsOn(d: String): List<Pair<String, Activity>> =
    acts.entries.filter { (k, a) -> a.type != "日記" && a.parent != null && dateOf(k) == d }.map { it.key to it.value }

fun AppData.catAnc(k: String?): List<String> { val r = ArrayList<String>(); var c = k; while (c != null && cats[c] != null) { r.add(0, c); c = cats[c]!!.p }; return r }
fun AppData.catName(k: String) = cats[k]?.n ?: k
fun AppData.catPath(k: String) = catAnc(k).joinToString(" › ") { cats[it]!!.n }
/** カテゴリの色(#RRGGBB)。大分類から継承 */
fun AppData.catColor(k: String?): String { for (c in catAnc(k).reversed()) cats[c]?.c?.let { return it }; return "#8B94A7" }
fun AppData.catRoot(k: String): String = catAnc(k).firstOrNull() ?: k
fun AppData.roots(): List<String> = cats.keys.filter { cats[it]!!.p == null }
private fun setv(v: String?): String? = if (v == null || v == "ask" || v == "none") null else v
/** 固定・変動(最も近い祖先の設定。"ask"=都度指定なら null) */
fun AppData.catFx(k: String): String? { for (c in catAnc(k).reversed()) cats[c]?.fx?.let { return setv(it) }; return null }
fun AppData.catFxRaw(k: String): String? { for (c in catAnc(k).reversed()) cats[c]?.fx?.let { return it }; return null }
fun AppData.xFx(x: Expense): String? = if (x.fx != null) setv(x.fx) else catFx(x.cat)
/** 特別感 日常/非日常(null=未設定) */
fun AppData.catSp(k: String): String? { for (c in catAnc(k).reversed()) cats[c]?.sp?.let { return setv(it) }; return null }
fun AppData.xSp(x: Expense): String? = if (x.sp != null) setv(x.sp) else catSp(x.cat)

fun AppData.entName(id: String?): String = id?.let { entities[it]?.n } ?: ""
fun AppData.roleName(r: Role): String = r.ent?.let { entities[it]?.n } ?: (r.name ?: "")
fun AppData.payeeOf(a: Activity?): String = a?.roles?.firstOrNull { it.role == "支払先" }?.let { roleName(it) } ?: ""
/** 「支払い · らーめん青葉」 */
fun AppData.payLabel(a: Activity?): String { val p = payeeOf(a); return if (p.isNotEmpty()) "支払い · $p" else "支払い" }
/** 家計簿の支出の題名: 親の活動の題名、無ければ支払先 */
fun AppData.xTitle(x: Expense): String { val pa = parentAct(x.pay); return pa?.let { acts[it]!!.title } ?: payeeOf(acts[x.pay]).ifEmpty { "支払い" } }
fun AppData.shopOf(x: Expense): String = acts[x.pay]?.roles?.firstOrNull()?.let { roleName(it) } ?: ""
fun AppData.whoOf(x: Expense): List<String> {
    val a = acts[x.pay] ?: return listOf("ひとり"); val pa = parentAct(x.pay)?.let { acts[it] }
    val w = (a.roles + (pa?.roles ?: emptyList())).filter { it.ent != null && it.role !in setOf("支払先", "支払者", "場所", "出発", "到着") }
        .map { entName(it.ent) }.distinct()
    return w.ifEmpty { listOf("ひとり") }
}
fun AppData.xDate(x: Expense): String = dateOf(x.pay)
fun AppData.propsOf(t: String): List<Pair<String, String>> = typeAnc(t).flatMap { tt -> (typeProps[tt] ?: emptyList()).map { it to tt } }
fun AppData.placeOf(p: Plan): String = p.place?.let { it.ent?.let { e -> entName(e) } ?: it.name } ?: ""

/* ---- 予定・ToDo(plans.js) ---- */
val AppData.events: Map<String, Plan> get() = plans.filterValues { !it.isTodo }
val AppData.todos: Map<String, Plan> get() = plans.filterValues { it.isTodo }
fun repOn(e: Plan, d: String): Boolean = e.rep != null && e.d != null && d > e.d && d.substring(8).toInt() == e.d.substring(8).toInt() && e.rep.startsWith("毎月")
/** その日の予定(終日が先、時刻順) */
fun AppData.evOn(d: String): List<Pair<String, Plan>> = events.entries
    .filter { (_, e) -> e.d == d || (e.d2 != null && e.d!! <= d && d <= e.d2) || repOn(e, d) }
    .sortedBy { if (it.value.all) "0" else (it.value.s ?: "0") }.map { it.key to it.value }
/** 繰り返しの次回 */
fun nextOn(e: Plan, today: String): String? {
    if (e.rep == null || e.d == null) return e.d
    val dom = e.d.substring(8).toInt(); var y = today.substring(0, 4).toInt(); var m = today.substring(5, 7).toInt()
    var d = "%04d-%02d-%02d".format(y, m, dom)
    if (d <= today) { m++; if (m > 12) { m = 1; y++ }; d = "%04d-%02d-%02d".format(y, m, dom) }
    return d
}
fun AppData.todosOf(ev: String): List<String> = todos.filterValues { it.ev == ev }.keys.toList()
fun AppData.todosDueOn(d: String): List<Pair<String, Plan>> = todos.filterValues { it.done == null && it.due == d }.map { it.key to it.value }

/** kind と予定の todo 配列を揃える(plSettle)。Store.update が毎回呼ぶ */
fun AppData.settled(): AppData {
    var ps = plans.mapValuesTo(LinkedHashMap()) { (_, p) ->
        val kind = when {
            p.kind == Plan.KIND_TODO -> Plan.KIND_TODO
            p.pay != null -> Plan.KIND_PAY
            else -> Plan.KIND_EVENT
        }
        if (kind != p.kind) p.copy(kind = kind) else p
    }
    ps = ps.mapValuesTo(LinkedHashMap()) { (_, p) ->
        if (p.isTodo && p.ev != null && (ps[p.ev] == null || ps[p.ev]!!.isTodo)) p.copy(ev = null) else p
    }
    ps = ps.mapValuesTo(LinkedHashMap()) { (k, p) ->
        if (p.isTodo) p else { val ks = ps.filterValues { it.isTodo && it.ev == k }.keys.toList(); if (ks != p.todo) p.copy(todo = ks) else p }
    }
    return if (ps == plans) this else copy(plans = ps)
}

/** 新しいキー(接頭辞+時刻の36進) */
fun uid(prefix: String): String = prefix + java.lang.Long.toString(System.currentTimeMillis(), 36) + (0..999).random().toString(36)

/* ---- 品目の金額行(ui.js lnV 系) ---- */
/** 品目の金額 = 行の合計(%の行は直前までの小計にかけて四捨五入)。行が無ければ price */
fun PayItem.amount(): Int = linesTotal(lines) ?: price
fun linesTotal(lines: List<AmtLine>): Int? {
    if (lines.isEmpty()) return null
    var sub = 0
    for ((i, l) in lines.withIndex()) {
        sub += if (l.k == "pct" && i > 0) Math.round(sub * l.v / 100.0).toInt() else l.v
    }
    return sub
}
/** %の行の円の結果 */
fun pctYen(lines: List<AmtLine>, idx: Int): Int { var sub = 0; for (i in 0 until idx) { val l = lines[i]; sub += if (l.k == "pct" && i > 0) Math.round(sub * l.v / 100.0).toInt() else l.v }; return Math.round(sub * lines[idx].v / 100.0).toInt() }
