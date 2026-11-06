package com.amgm.personallog.v4.screens.activities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.amgm.personallog.v4.data.Activity
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Plan
import com.amgm.personallog.v4.data.actsOn
import com.amgm.personallog.v4.data.dateOf
import com.amgm.personallog.v4.data.dayKeyByDate
import com.amgm.personallog.v4.data.descOf
import com.amgm.personallog.v4.data.evOn
import com.amgm.personallog.v4.data.events
import com.amgm.personallog.v4.data.kidActs
import com.amgm.personallog.v4.data.todosDueOn

/*
 * アクティビティの画面の状態と計算。activities.js の actFilter / actMonth / dayItems / recordDates / daySpent、
 * plan.js の plSel / plItemsOn / plRecordedOn に当たる。描き方は ActivitiesScreens.kt と ActDayMonth.kt。
 */

/** 画面をまたいで残す状態(メモリ内。アプリを閉じると戻る) */
object ActNav {
    /** 絞り込み(all / 予定 / 支払い / 会話 / 移動)。日のページも同じ値を使う */
    var filter by mutableStateOf("all")
    /** リストの月(yyyy-MM)。null は今月 */
    var ym by mutableStateOf<String?>(null)
    /** 日・月の表示で選んだ日(yyyy-MM-dd)。null は今日 */
    var sel by mutableStateOf<String?>(null)
}

/** 絞り込みの並び(値 to 文字)。文字はモックのまま */
val ACT_FILTERS = listOf("all" to "すべて", "予定" to "予定", "支払い" to "支払い", "会話" to "会話", "移動" to "移動")

/** 一覧・日のページの1行。rec=true は記録、false は未記録の予定 */
data class DayItem(val time: String, val rec: Boolean, val k: String)

/** 日・月の表示の1行(plItemsOn)。end は記録の終わり、cat は支払いの分類(印のため) */
data class PlItem(val k: String, val rec: Boolean, val title: String, val time: String, val end: String?, val all: Boolean, val cat: String)

/** 絞り込みを通るか(all は全部。予定の絞り込みでは記録は出ない) */
fun passFilter(filter: String, a: Activity) = filter == "all" || a.type == filter

/** 予定を出すか(all か 予定) */
fun planVisible(filter: String) = filter == "all" || filter == "予定"

/** その日付の日記(無ければ null) */
fun AppData.diaryOf(date: String): Activity? = dayKeyByDate(date)?.let { acts[it] }

/** その日の全部の記録(活動の中の支払いも含む) */
fun AppData.allActsOfDay(date: String): List<String> = dayKeyByDate(date)?.let { descOf(it) } ?: emptyList()

/** その日の直下の記録(日記の子) */
fun AppData.kidsOfDay(date: String): List<String> = dayKeyByDate(date)?.let { kidActs(it) } ?: emptyList()

/** 日記の本文(空なら null) */
fun AppData.diaryBodyOf(date: String): String? = diaryOf(date)?.body?.takeIf { it.isNotEmpty() }

/** 日の自分の支出の合計。支払いが2件以上で、自分の支出がある時だけ(1件だけなら 0) */
fun AppData.daySpentOf(date: String): Int {
    val ps = allActsOfDay(date).mapNotNull { acts[it]?.x?.let { xid -> expenses[xid] } }
    return if (ps.size >= 2) ps.sumOf { it.amt } else 0
}

/** 予定が記録済みか(記録の日付がその日) */
fun AppData.planRecordedOn(e: Plan, date: String): Boolean = e.rec?.let { dateOf(it) == date } ?: false

/** 今日の時刻指定の予定で、開始が今より後(これからの予定) */
fun AppData.isFuturePlanOf(e: Plan, today: String, now: String): Boolean = !e.all && e.d == today && (e.s ?: "") > now

/** 一覧・日のページの項目: 絞り込みを通る記録と、未記録の予定を時刻の古い順に */
fun AppData.dayItemsOf(date: String, filter: String): List<DayItem> {
    val items = ArrayList<DayItem>()
    if (filter != "予定") {
        val keys = if (filter == "支払い") allActsOfDay(date) else kidsOfDay(date)
        keys.forEach { k ->
            val a = acts[k] ?: return@forEach
            if (passFilter(filter, a)) items.add(DayItem(a.time ?: "", true, k))
        }
    }
    if (planVisible(filter)) {
        evOn(date).filter { (_, e) -> e.rec == null && !e.hol }
            .forEach { (k, e) -> items.add(DayItem(if (e.all) "00:00" else e.s ?: "", false, k)) }
    }
    return items.sortedBy { it.time }
}

/** 記録のある日(今日より前・古い順)。予定を出すときは、今月の1日から今日より前の未記録の予定の日も */
fun AppData.recordDatesOf(filter: String, today: String): List<String> {
    val ds = HashSet<String>()
    acts.forEach { (k, a) ->
        val date = a.date
        if (a.type == "日記" && date != null && date < today &&
            descOf(k).any { id -> acts[id]?.let { passFilter(filter, it) } == true }
        ) ds.add(date)
    }
    if (planVisible(filter)) {
        val monthStart = today.take(8) + "01"
        events.values.forEach { e ->
            val d = e.d
            if (e.rec == null && !e.hol && d != null && d < today && d >= monthStart) ds.add(d)
        }
    }
    return ds.sorted()
}

/** 日・月の表示の1日の項目(記録と予定を時刻順)。記録済みの予定は出さない */
fun AppData.plItemsOf(date: String): List<PlItem> {
    val recs = actsOn(date).map { (k, a) ->
        PlItem(k, true, a.title, a.time ?: "00:00", a.end, false, a.x?.let { expenses[it]?.cat } ?: "")
    }
    val plans = evOn(date).filter { (_, e) -> !e.hol && !planRecordedOn(e, date) }.map { (k, e) ->
        PlItem(k, false, e.t, if (e.all) "00:00" else e.s ?: "00:00", if (e.all) null else e.e, e.all, e.pay?.cat ?: "")
    }
    return (recs + plans).sortedBy { it.time }
}

/** 日・月の表示で、その日が期限の未完了の ToDo の数 */
fun AppData.todoCountOn(date: String): Int = todosDueOn(date).size
