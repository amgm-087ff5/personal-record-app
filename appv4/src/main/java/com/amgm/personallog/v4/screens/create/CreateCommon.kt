package com.amgm.personallog.v4.screens.create

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.Activity
import com.amgm.personallog.v4.data.Advance
import com.amgm.personallog.v4.data.AmtLine
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.Entity
import com.amgm.personallog.v4.data.Expense
import com.amgm.personallog.v4.data.PayItem
import com.amgm.personallog.v4.data.Role
import com.amgm.personallog.v4.data.XItem
import com.amgm.personallog.v4.data.actsOn
import com.amgm.personallog.v4.data.amount
import com.amgm.personallog.v4.data.dateOf
import com.amgm.personallog.v4.data.dayKeyByDate
import com.amgm.personallog.v4.data.isUnder
import com.amgm.personallog.v4.data.timeText
import com.amgm.personallog.v4.data.uid
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.components.Divider
import com.amgm.personallog.v4.ui.components.Field
import com.amgm.personallog.v4.ui.components.Ico
import com.amgm.personallog.v4.ui.components.press

/*
 * 作成シートの共通部分(create.js の cr* の補助)。
 * 日時・活動の親の解決・支払いの保存(crPayment)・シート内の入力の部品をここに置く。
 * 状態はシートごとの holder(mutableStateOf)に持ち、onMain の中からも読めるようにする。
 */

/* ---------------- 時刻・日付 ---------------- */

/** "HH:MM" に分を足す(23:59 で止める) */
internal fun plusMin(t: String, n: Int): String = Dates.hhmm(minOf(Dates.minutes(t) + n, 23 * 60 + 59))

/** 名前の最後の語(田中 花子 → 花子)。品目の負担の名前に使う */
internal fun surname(name: String): String = name.trim().split(Regex("\\s+")).last()

/** 端末の日付ダイアログ(input type=date 相当) */
internal fun showDatePicker(ctx: Context, value: String, onPick: (String) -> Unit) {
    val d = Dates.parse(value)
    DatePickerDialog(ctx, { _, y, m, dd -> onPick("%04d-%02d-%02d".format(y, m + 1, dd)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

/** 端末の時刻ダイアログ(input type=time 相当) */
internal fun showTimePicker(ctx: Context, value: String, onPick: (String) -> Unit) {
    val m = Dates.minutes(value)
    TimePickerDialog(ctx, { _, h, mm -> onPick("%02d:%02d".format(h, mm)) }, m / 60, m % 60, true).show()
}

/* ---------------- 日記・活動の親 ---------------- */

/** その日の日記を取る。無ければ作る(crDay)。戻りは (データ, 日記のキー) */
internal fun AppData.withDay(date: String): Pair<AppData, String> {
    dayKeyByDate(date)?.let { return this to it }
    val key = "d" + date.replace("-", "")
    val a = Activity("日記", date = date, dd = Dates.parse(date).dayOfMonth.toString(), dw = Dates.wd(date), body = "", kids = emptyList())
    return copy(acts = acts + (key to a)) to key
}

/** 親の候補(その日の期間つきの活動。支払いは除く。開始の早い順) */
internal fun AppData.actCands(date: String): List<Pair<String, Activity>> =
    actsOn(date).filter { (_, a) -> a.end != null && a.type != "支払い" }.sortedBy { it.second.time ?: "" }

/** 既定の親: 時刻を含む候補のうち一番内側(開始が一番遅いもの)。無ければ null(日の直下) */
internal fun AppData.actDefault(date: String, time: String): String? =
    actCands(date).filter { (_, a) -> (a.time ?: "") <= time && time <= (a.end ?: "") }.maxByOrNull { it.second.time ?: "" }?.first

/** 選ばれている親。act=null は既定(時刻で決める)、""=なし、キー=その活動(日が合う時だけ) */
internal fun AppData.actResolve(date: String, time: String, act: String?): String? = when {
    act == null -> actDefault(date, time)
    act.isEmpty() -> null
    acts[act] != null && dateOf(act) == date -> act
    else -> null
}

/** 「活動の中」の選択肢(先頭は「なし(日の直下)」)。cur が候補に無い時(瞬間の親など)はその 1 件を先頭に足す */
internal fun AppData.actOptions(date: String, cur: String?): List<Pair<String, String>> {
    val cands = actCands(date).toMutableList()
    if (cur != null && acts[cur] != null && cands.none { it.first == cur } && dateOf(cur) == date) cands.add(0, cur to acts[cur]!!)
    return listOf("" to "なし(日の直下)") + cands.map { (k, a) -> k to (a.timeText() + " " + a.title.ifEmpty { a.type }) }
}

/* ---------------- 人・場所・種類 ---------------- */

/** 人(me 以外で、人物の下にあるエンティティ) */
internal fun AppData.peopleList(): List<Pair<String, String>> =
    entities.entries.filter { (k, e) -> k != "me" && e.t.any { isUnder(it, "person") } }.map { (k, e) -> k to e.n }

/** 場所の候補(登録済み・名前が入力を含む・最大8件) */
internal fun AppData.placeHits(q: String): List<Pair<String, String>> =
    entities.entries.filter { (_, e) -> e.t.any { isUnder(it, "place") } && (q.isBlank() || e.n.contains(q.trim())) }
        .take(8).map { (k, e) -> k to e.n }

/** 名前で登録済みのエンティティを探す(無ければ null) */
internal fun AppData.entByName(name: String): String? {
    val n = name.trim()
    if (n.isEmpty()) return null
    return entities.entries.firstOrNull { it.value.n == n }?.key
}

/** 種類の選択肢(根の順。子は深さの分だけ全角空白を前に付ける) */
internal fun AppData.typeOptions(): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    fun walk(k: String, depth: Int) {
        out.add(k to "　".repeat(depth) + types[k]!!.n)
        types.filter { it.value.p == k }.keys.forEach { walk(it, depth + 1) }
    }
    types.filter { it.value.p == null }.keys.forEach { walk(it, 0) }
    return out
}

/** 店から決まる既定のカテゴリ(crGuessCat)。カフェ → 食費>カフェ、スーパー → 自炊、ドラッグストア → 日用品、他の飲食 → 外食 */
internal fun AppData.guessCat(eid: String?): String {
    val t = eid?.let { entities[it]?.t }.orEmpty()
    return when {
        t.any { isUnder(it, "cafe") } -> "cafe"
        t.any { isUnder(it, "super") } -> "cook"
        t.any { isUnder(it, "drug") } -> "daily"
        else -> "eatout"
    }
}

/* ---------------- 支払い(crPayment) ---------------- */

/** 支払い1件の入力。items は保存する品目(price は amount() 済み)。total は全員分 */
internal class PayIn(
    val date: String, val time: String, val act: String?, val pay: String,
    val shop: String, val register: Boolean,
    val items: List<PayItem>, val total: Int,
    val cat: String, val fx: String?, val sp: String?,
    val with: String?, val todo: List<String>,
    val photo: String? = null, val hash: String? = null,
)

/** 自分の分と、自分の支出の品目(相手の分は入れない)。品目が無い時は全額 */
internal fun splitMine(items: List<PayItem>, total: Int, withSur: String?): Pair<Int, List<XItem>> {
    if (items.isEmpty()) return total to emptyList()
    var mine = 0
    val xs = ArrayList<XItem>()
    for (it in items) {
        val amt = it.amount()
        when {
            withSur != null && it.b == withSur -> {}
            withSur != null && it.b == "半分" -> { val h = amt / 2; mine += h; xs.add(XItem(it.n, h, "1/2")) }
            else -> { mine += amt; xs.add(XItem(it.n, amt, "全額")) }
        }
    }
    return mine to xs
}

/** 支払いを1件作る(A・X・E・ToDo・写真)。戻りは (新しいデータ, 支払いのキー) */
internal fun AppData.addPayment(p: PayIn, today: String): Pair<AppData, String> {
    val (d, day) = withDay(p.date)
    val par = actResolve(p.date, p.time, p.act) ?: day
    val aid = uid("p")
    val withSur = p.with?.let { d.entities[it]?.n?.let(::surname) }
    val (mine, xs) = splitMine(p.items, p.total, withSur)
    val xid = if (mine > 0) uid("x") else null

    var ents = d.entities
    var shopKey = d.entByName(p.shop)
    if (shopKey == null && p.register && p.shop.isNotBlank()) {
        val eid = uid("e")
        val n = p.shop.trim()
        ents = ents + (eid to Entity(n = n, i = n.take(1), t = listOf("shop")))
        shopKey = eid
    }
    val roles = buildList {
        add(Role("支払先", shopKey, if (shopKey == null) p.shop.ifEmpty { "(店なし)" } else null))
        add(Role("支払者", "me"))
        if (p.with != null) add(Role("同席", p.with))
    }
    val adv = if (p.with != null && p.total - mine > 0) Advance(p.with, p.total - mine) else null
    val ext = buildMap {
        p.photo?.let { put("photo", it) }
        p.hash?.let { put("hash", it) }
    }
    val a = Activity(
        type = "支払い", parent = par, time = p.time, title = "支払い", roles = roles, pay = p.pay, amt = p.total,
        items = p.items.map { it.copy(price = it.amount()) }, x = xid, adv = adv, ext = ext,
    )
    var acts = d.acts + (aid to a)
    val pa = acts[par]
    if (pa != null) acts = acts + (par to pa.copy(kids = pa.kids + aid))

    var expenses = d.expenses
    if (xid != null) expenses = expenses + (xid to Expense(pay = aid, amt = mine, cat = p.cat, fx = p.fx, sp = p.sp, memo = "", items = xs))

    var plans = d.plans
    for (k in p.todo) {
        val pl = plans[k] ?: continue
        plans = plans + (k to pl.copy(done = today, payRef = aid))
    }
    return d.copy(acts = acts, expenses = expenses, entities = ents, plans = plans) to aid
}

/* ---------------- 入力の部品 ---------------- */

/** 選択の小さいピル(押せる高さ44・見た目32)。チップ行の中で使う */
@Composable
internal fun SChip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = V4.c
    Row(Modifier.height(44.dp).press(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(if (on) c.meSoft else c.sunk).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = plex(13, if (on) 600 else 500), color = if (on) c.me else c.tx2, maxLines = 1, softWrap = false) }
    }
}

/** 横に並ぶチップの行(横スクロール) */
@Composable
internal fun ChipRow(padH: Dp = V4Dim.gutter, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = padH),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/** 候補の行(.cr-sug): 登録済みの名前をチップで出す。空なら何も出さない */
@Composable
internal fun SuggestChips(items: List<Pair<String, String>>, padH: Dp = V4Dim.gutter, onPick: (String) -> Unit) {
    if (items.isEmpty()) return
    ChipRow(padH) { items.forEach { (_, n) -> SChip(n, false) { onPick(n) } } }
}

/** 入力の行(ラベル84 + 中身)。select・日付・時刻の行で使う */
@Composable
internal fun RowField(label: String, divider: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Column {
        if (divider) Divider(14.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).let { if (onClick != null) it.press(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, style = V4.t.label, color = V4.c.tx3, modifier = Modifier.width(84.dp))
            content()
        }
    }
}

/** 選択の行(select 相当): 今の値を出し、押すと一覧を開く */
@Composable
internal fun SelectRow(label: String, value: String, options: List<Pair<String, String>>, divider: Boolean = false, onPick: (String) -> Unit) {
    val c = V4.c
    var open by remember { mutableStateOf(false) }
    RowField(label, divider, onClick = { open = true }) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = V4.t.body, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(V4Icons.down, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l, style = V4.t.body, color = c.tx) }, onClick = { open = false; onPick(v) })
            }
        }
    }
}

/** 日付・時刻の行。押すと端末のダイアログを開く */
@Composable
internal fun PickRow(label: String, value: String, divider: Boolean = false, onClick: () -> Unit) {
    val c = V4.c
    RowField(label, divider, onClick = onClick) {
        Text(value.ifEmpty { "未設定" }, style = V4.t.body, color = if (value.isEmpty()) c.tx3 else c.tx, modifier = Modifier.weight(1f))
    }
}

/** 複数行の入力(textarea)。minH は最低の高さ */
@Composable
internal fun TextArea(value: String, onValue: (String) -> Unit, minH: Dp, placeholder: String) {
    val c = V4.c
    Box(Modifier.fillMaxWidth().heightIn(min = minH).padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(placeholder, style = V4.t.body, color = c.tx3)
        BasicTextField(
            value, onValue, textStyle = V4.t.body.copy(color = c.tx), cursorBrush = SolidColor(c.brand),
            modifier = Modifier.fillMaxWidth().heightIn(min = minH),
        )
    }
}

/** 見出し(sh) */
@Composable
internal fun SheetHead(title: String, n: String? = null) = com.amgm.personallog.v4.ui.components.SectionHead(title, n)

/** 金額の欄(数字だけ) */
@Composable
internal fun AmountField(value: String, onValue: (String) -> Unit, divider: Boolean = false) {
    Field("金額", value, { onValue(it.filter { ch -> ch.isDigit() }) }, placeholder = "¥0", divider = divider, amount = true, number = true)
}

/** 「の品」用の小さな文字(補足) */
@Composable
internal fun CapText(text: String, color: androidx.compose.ui.graphics.Color = V4.c.tx3) {
    Text(text, style = V4.t.cap, color = color, modifier = Modifier.padding(horizontal = V4Dim.gutter))
}

/** 下のボタンに渡す小さなアイコン付きの丸(活動の追加などの行で使う) */
@Composable
internal fun RowIco(icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color) = Ico(icon, color)

/** 数字だけを残す */
internal fun digitsOf(s: String): Int = s.filter { it.isDigit() }.toIntOrNull() ?: 0
