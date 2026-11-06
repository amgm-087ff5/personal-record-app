package com.amgm.personallog.v4.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.Plan
import com.amgm.personallog.v4.data.TodoList
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.components.CheckBox
import com.amgm.personallog.v4.ui.components.IconBtn
import com.amgm.personallog.v4.ui.components.ObjCard
import com.amgm.personallog.v4.ui.components.Tag
import com.amgm.personallog.v4.ui.components.TagKind
import com.amgm.personallog.v4.ui.plex

/*
 * 予定・ToDo の補助 — 2つの画面(ToDo の一覧 / 予定・ToDo の詳細)で共有するもの。担当: ToDo・予定の担当。
 */

/** 画面をまたいで残る状態(plTodoView / plShowDone) */
internal object PlanMem {
    /** ToDo の絞り込み。today / linked / all / list:xxx */
    var todoView by mutableStateOf("today")
    /** 完了の節を開いているか */
    var showDone by mutableStateOf(false)
}

/** 絞り込みに入るか(完了も含めて判定する。件数は未完了だけで数える) */
internal fun inView(p: Plan, view: String, today: String): Boolean = when {
    view == "today" -> p.due != null && p.due <= today
    view == "linked" -> p.ev != null
    view.startsWith("list:") -> p.list == view.removePrefix("list:")
    else -> true
}

/** 今日・明日・昨日・明後日、それ以外は 10月29日(木) */
internal fun relDayPl(d: String, today: String): String = when (Dates.daysBetween(today, d)) {
    0L -> "今日"
    1L -> "明日"
    -1L -> "昨日"
    2L -> "明後日"
    else -> Dates.fmtMDW(d)
}

/** 予定の残り時間(今日の時間指定で開始が過ぎていなければ あと○分 / 未来の日は 明日・○日後)。出さないなら null */
internal fun remainText(day: String, e: Plan, today: String, now: String): String? {
    if (day == today) {
        val s = e.s
        if (e.all || s == null || s <= now) return null
        val m = Dates.minutes(s) - Dates.minutes(now)
        if (m < 60) return "あと${m}分"
        val h = m / 60; val r = m % 60
        return if (r == 0) "あと${h}時間" else "あと${h}時間${r}分"
    }
    if (day < today) return null
    val n = Dates.daysBetween(today, day)
    return if (n == 1L) "明日" else "${n}日後"
}

/** 予定の時刻の文字(終日 / 〜日付 / 開始–終了) */
internal fun eventTimeLabel(e: Plan): String = when {
    e.all -> if (e.d2 != null) "終日〜${Dates.fmtMD(e.d2)}" else "終日"
    e.e != null -> "${e.s ?: ""}–${e.e}"
    else -> e.s ?: ""
}

/** クイック追加の読み取り結果 */
internal data class QuickAdd(val title: String, val list: String, val due: String?)

/** 「明日 牛乳 #買い物」を読む。日付の語は最初の1つ、リストは #名前 が一致した時だけ */
internal fun parseQuick(raw: String, defList: String, lists: Map<String, TodoList>, today: String): QuickAdd {
    var s = raw
    var due: String? = null
    for ((w, n) in listOf("明後日" to 2, "明日" to 1, "今日" to 0)) {
        if (s.contains(w)) { due = Dates.addDays(today, n); s = s.replaceFirst(w, " "); break }
    }
    var list = defList
    val m = Regex("#(\\S+)").find(s)
    if (m != null) {
        val key = lists.entries.firstOrNull { it.value.n == m.groupValues[1] }?.key
        if (key != null) { list = key; s = s.replaceRange(m.range, " ") }
    }
    val title = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    return QuickAdd(title, list, due)
}

/** 節の見出し(plSec)。late=期限切れの赤。n は件数の文字 */
@Composable
internal fun PlSec(title: String, n: String?, late: Boolean = false, actLabel: String? = null, onAct: (() -> Unit)? = null) {
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = plex(20, 700, 1.3), color = if (late) c.late else c.tx)
        if (!n.isNullOrEmpty()) Text(n, style = plex(15, 500), color = c.tx3, modifier = Modifier.padding(bottom = 2.dp))
        Spacer(Modifier.weight(1f))
        if (actLabel != null && onAct != null) Text(
            actLabel, style = V4.t.label, color = c.brand,
            modifier = Modifier.clickable(onClick = onAct).padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

/** ToDo の1行(面。押すと詳細)。done=完了の行は取り消し線 */
@Composable
internal fun TodoRow(k: String, p: Plan, done: Boolean) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val today = app.today
    ObjCard(onClick = { app.go("task", k) }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBox(on = done, onClick = { toggleTask(app, k) })
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.t, style = plex(15, 500, 1.4), color = if (done) c.tx3 else c.tx, maxLines = 2,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (p.pri && !done) Text("!", style = plex(15, 700), color = c.me, modifier = Modifier.padding(start = 4.dp))
                }
                if (!done) {
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.due?.let { due ->
                            if (due < today) Tag(relDayPl(due, today), kind = TagKind.LATE)
                            else Text(relDayPl(due, today), style = V4.t.cap, color = c.tx3)
                        }
                        p.list?.let { l -> d.lists[l]?.n }?.let { Text(it, style = V4.t.cap, color = c.tx3) }
                        p.ev?.let { d.plans[it]?.t }?.let { Text(it, style = V4.t.cap, color = c.tx3, maxLines = 1) }
                    }
                }
            }
            Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
    }
}

/** 戻る+題名+操作の上部(ToDo の詳細。戻り先は必ず ToDo の一覧) */
@Composable
internal fun TaskBar(onBack: () -> Unit, onMore: () -> Unit) {
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(c.bg.copy(alpha = 0.7f)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconBtn(V4Icons.back, "戻る") { onBack() }
        Text("ToDo", style = V4.t.head, color = c.tx, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        IconBtn(V4Icons.more, "操作") { onMore() }
    }
}

/** 下の入力欄(クイック追加): 白い面の中に入力と「追加」 */
@Composable
internal fun QuickInput(value: String, onValue: (String) -> Unit, focus: androidx.compose.ui.focus.FocusRequester, onAdd: () -> Unit) {
    val c = V4.c
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 4.dp, bottom = 12.dp).fillMaxWidth()
            .clip(shape).background(c.surface).border(1.dp, c.line, shape)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("例: 明日 牛乳 #買い物", style = V4.t.body, color = c.tx3)
            androidx.compose.foundation.text.BasicTextField(
                value = value, onValueChange = onValue, singleLine = true,
                textStyle = V4.t.body.copy(color = c.tx),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.brand),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onAdd() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        com.amgm.personallog.v4.ui.components.SmallButton("追加", onClick = onAdd)
    }
}
