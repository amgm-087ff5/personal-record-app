package com.amgm.personallog.v4.screens.entities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.Entity
import com.amgm.personallog.v4.data.Note
import com.amgm.personallog.v4.data.Plan
import com.amgm.personallog.v4.data.TypeDef
import com.amgm.personallog.v4.data.actsOf
import com.amgm.personallog.v4.data.dateOf
import com.amgm.personallog.v4.data.entName
import com.amgm.personallog.v4.data.events
import com.amgm.personallog.v4.data.isUnder
import com.amgm.personallog.v4.data.propsOf
import com.amgm.personallog.v4.data.relsOf
import com.amgm.personallog.v4.data.todos
import com.amgm.personallog.v4.data.typeAnc
import com.amgm.personallog.v4.data.uid
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.screens.create.openEntitySheet
import com.amgm.personallog.v4.screens.plan.toggleTask
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.Route
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.components.AxColumn
import com.amgm.personallog.v4.ui.components.AxEdge
import com.amgm.personallog.v4.ui.components.AxNode
import com.amgm.personallog.v4.ui.components.AxRow
import com.amgm.personallog.v4.ui.components.AppBar
import com.amgm.personallog.v4.ui.components.BarAction
import com.amgm.personallog.v4.ui.components.BtnRow
import com.amgm.personallog.v4.ui.components.CheckBox
import com.amgm.personallog.v4.ui.components.Chips
import com.amgm.personallog.v4.ui.components.DeleteButton
import com.amgm.personallog.v4.ui.components.Divider
import com.amgm.personallog.v4.ui.components.Empty
import com.amgm.personallog.v4.ui.components.ERef
import com.amgm.personallog.v4.ui.components.Field
import com.amgm.personallog.v4.ui.components.Hint
import com.amgm.personallog.v4.ui.components.Knot
import com.amgm.personallog.v4.ui.components.LargeTitle
import com.amgm.personallog.v4.ui.components.ListCard
import com.amgm.personallog.v4.ui.components.MomentRow
import com.amgm.personallog.v4.ui.components.MonthHead
import com.amgm.personallog.v4.ui.components.NowLine
import com.amgm.personallog.v4.ui.components.ObjCard
import com.amgm.personallog.v4.ui.components.Objs
import com.amgm.personallog.v4.ui.components.PlanRowAx
import com.amgm.personallog.v4.ui.components.Screen
import com.amgm.personallog.v4.ui.components.SectionHead
import com.amgm.personallog.v4.ui.components.TitleAction
import com.amgm.personallog.v4.ui.components.TonalButton
import com.amgm.personallog.v4.ui.components.V4Row
import com.amgm.personallog.v4.ui.components.press
import com.amgm.personallog.v4.ui.components.dashedBorder

/*
 * エンティティの3画面 — entities.js(一覧 #/entities・詳細 #/e/{id}・種類の木 #/types)。
 * 仕様: /home/claude/specs/entities.md。時間軸の行は ui/components/Axis.kt の部品を使う。
 * 補助の計算(data.js 相当)は、このファイルの private 拡張にまとめる。
 */

/* ---------------- 補助(data.js / ui.js の計算) ---------------- */

private val YMRE = Regex("\\d{4}-\\d{2}")
private val YM_HEAD = Regex("^(\\d{4})-(\\d{2})")
private val MARK_RE = Regex("\\{[ea]:[^}]*\\}")
private val collator: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.JAPAN)

/** 「メモ」「YYYY-MM〜」→「2020年4月から」。形が合わなければ null */
private fun sinceOf(s: String?): String? = s?.let { YM_HEAD.find(it) }?.let { "${it.groupValues[1]}年${it.groupValues[2].toInt()}月から" }

/** 「最後に交わった」の文言(agoLabel) */
private fun agoLabel(date: String?, today: String): String {
    if (date.isNullOrEmpty()) return "まだなし"
    val n = Dates.daysBetween(date, today)
    return when {
        n == 0L -> "今日"
        n == 1L -> "昨日"
        n < 7 -> "${n}日前"
        n < 35 -> "${Math.round(n / 7.0)}週間前"
        else -> "${Math.round(n / 30.0)}か月前"
    }
}

/** エンティティの一覧の対象(me 以外) */
private fun AppData.enAll(): List<Pair<String, Entity>> = entities.filterKeys { it != "me" }.map { it.key to it.value }
/** 種類 t の下(t 自身を含む)にある縁の数 */
private fun AppData.enCount(t: String): Int = enAll().count { (_, e) -> e.t.any { isUnder(it, t) } }
/** 根の種類(p を持たないもの)を宣言順に */
private fun AppData.enRoots(): List<String> = types.filterValues { it.p == null }.keys.toList()
/** 種類の祖先の名前を ` › ` でつなぐ */
private fun AppData.typePath(t: String?): String = if (t == null) "" else typeAnc(t).joinToString(" › ") { types[it]?.n ?: it }
/** 最後に交わった日(記録の日付の最大) */
private fun AppData.lastMet(id: String): String? = actsOf(id).map { dateOf(it.first) }.filter { it.isNotEmpty() }.maxOrNull()
/** 12週の出会い(右が今週)。各週の記録の数 */
private fun AppData.encCounts(id: String, today: String): IntArray {
    val c = IntArray(12)
    for ((k, _, _) in actsOf(id)) {
        val date = dateOf(k)
        if (date.isEmpty()) continue
        val w = Math.floorDiv(Dates.daysBetween(date, today), 7L).toInt()
        if (w in 0..11) c[11 - w]++
    }
    return c
}
/** 一覧の束の見出し(すべて → 根、絞り込み中 → 選んだ種類の下の段) */
private fun AppData.groupOf(e: Entity, kind: String): String {
    if (kind == "all") return typeAnc(e.t.firstOrNull()).firstOrNull() ?: ""
    val t = e.t.firstOrNull { isUnder(it, kind) } ?: return kind
    return typeAnc(t).firstOrNull { types[it]?.p == kind } ?: t
}

/* ---------------- 共通の小部品 ---------------- */

/** 値札(vtag): 左が三角の札。点の色と札の色は呼び出し側が決める */
private object VTagShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val n = with(density) { 12.dp.toPx() }
        val p = Path().apply {
            moveTo(n, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
            lineTo(n, size.height); lineTo(0f, size.height / 2f); close()
        }
        return Outline.Generic(p)
    }
}

@Composable
private fun VTag(label: String, bg: Color = V4.c.cTypeSoft, fg: Color = V4.c.cType, dot: Color = V4.c.bg, modifier: Modifier = Modifier) {
    Box(
        modifier.height(28.dp).clip(VTagShape).background(bg).padding(start = 24.dp, end = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = (-16).dp).size(6.dp).clip(CircleShape).background(dot))
        Text(label, style = plex(14, 600, 2.0), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

/** 12週の棒(enc)。段: 3件以上=高さ18・不透明1 / 2件=12・0.8 / 1件=8・0.55 / 0件=4(地の色) */
@Composable
private fun EncStrip(counts: IntArray) {
    val c = V4.c
    Row(Modifier.height(18.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        counts.forEach { n ->
            val h = when { n >= 3 -> 18; n == 2 -> 12; n == 1 -> 8; else -> 4 }
            val col = if (n == 0) c.axis else c.kin.copy(alpha = when { n >= 3 -> 1f; n == 2 -> 0.8f; else -> 0.55f })
            Box(Modifier.size(6.dp, h.dp).clip(RoundedCornerShape(2.dp)).background(col))
        }
    }
}

/** 事実・関係の1行(dt = 項目名、dd = 値、右端に since) */
@Composable
private fun FactLine(k: String, divider: Boolean, since: String? = null, value: @Composable () -> Unit) {
    val c = V4.c
    Column {
        if (divider) Divider(0.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(k, style = V4.t.label, color = c.tx3, maxLines = 1, softWrap = false, modifier = Modifier.width(84.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { value() }
                if (since != null) Text(since, style = V4.t.cap, color = c.tx3, maxLines = 1, softWrap = false)
            }
        }
    }
}

/* ---------------- 一覧 #/entities ---------------- */

@Composable
fun EntitiesScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val today = app.today
    var kind by remember { mutableStateOf("all") }
    // チップを変えると検索の文字は消える(モックの rerender と同じ)
    var q by remember(kind) { mutableStateOf("") }
    val qq = q.trim()

    val items: List<Triple<String, String, Int?>> =
        listOf(Triple("all", "すべて", d.enAll().size)) +
            d.enRoots().filter { d.enCount(it) > 0 }.map { Triple(it, d.types[it]?.n ?: it, d.enCount(it)) }

    val rows = d.enAll()
        .filter { (_, e) -> kind == "all" || e.t.any { d.isUnder(it, kind) } }
        .sortedWith(Comparator<Pair<String, Entity>> { a, b ->
            // 最後に交わった日の降順(無いものは最後)、同じなら名前の照合順
            val c1 = (d.lastMet(b.first) ?: "").compareTo(d.lastMet(a.first) ?: "")
            if (c1 != 0) c1 else collator.compare(a.second.n, b.second.n)
        })
    val groups = rows.groupBy { d.groupOf(it.second, kind) }
    val order = d.types.keys.filter { it in groups }

    Screen(
        topBar = null,
    ) {
        LargeTitle(
            "エンティティ",
            listOf(
                TitleAction(text = "追加", icon = V4Icons.plus, primary = true) { openEntitySheet(app) },
                TitleAction(icon = V4Icons.lines, label = "エンティティのメモ") { app.go("notes") },
                TitleAction(icon = V4Icons.type, label = "種類") { app.go("types") },
            ),
        )

        // 検索欄(名前の部分一致)
        Row(
            Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, bottom = 12.dp).fillMaxWidth().height(48.dp)
                .clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(V4Icons.search, null, tint = c.tx3, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("名前で探す", style = V4.t.body, color = c.tx3)
                BasicTextField(
                    q, { q = it }, singleLine = true, textStyle = V4.t.body.copy(color = c.tx),
                    cursorBrush = SolidColor(c.brand), modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Chips(items, kind) { kind = it }

        if (groups.isEmpty()) {
            Empty(V4Icons.people, "エンティティ なし")
        } else {
            val shown = order.filter { k -> groups[k].orEmpty().any { (_, e) -> qq.isEmpty() || e.n.contains(qq, ignoreCase = true) } }
            shown.forEachIndexed { i, k ->
                val list = groups[k].orEmpty().filter { (_, e) -> qq.isEmpty() || e.n.contains(qq, ignoreCase = true) }
                Row(
                    Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = if (i == 0) 18.dp else 32.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VTag(d.types[k]?.n ?: k)
                    Text("${groups[k].orEmpty().size}件", style = V4.t.cap, color = c.tx3)
                }
                Objs { list.forEach { (id, e) -> EntityRow(id, e) } }
            }
        }
    }
}

/** 一覧の1行: 緑の印+名前+種類の道 / 右に最後に交わった日と12週の棒 */
@Composable
private fun EntityRow(id: String, e: Entity) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val today = app.today
    ObjCard(onClick = { app.go("e", id) }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.padding(top = 5.dp)) { Knot() }
            Column(Modifier.weight(1f)) {
                Text(e.n, style = plex(15, 500, 1.33), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val path = d.typePath(e.t.firstOrNull())
                if (path.isNotEmpty()) Text(path, style = V4.t.cap, color = c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(agoLabel(d.lastMet(id), today), style = V4.t.label, color = c.tx2, maxLines = 1, softWrap = false)
                EncStrip(d.encCounts(id, today))
            }
        }
    }
}

/* ---------------- 詳細 #/e/{id} ---------------- */

@Composable
fun EntityDetailScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val id = r.id.orEmpty()
    val e = d.entities[id]
    Screen(
        topBar = {
            AppBar("", back = true, actions = listOf(BarAction(V4Icons.more, "その他の操作") { app.toast("編集はモックでは未対応です") }))
        },
    ) {
        if (e == null) Empty(V4Icons.people, "エンティティ なし")
        else EntityBody(id, e)
    }
}

/** 時間軸の1項目(月ごとの面の中に並ぶ) */
private class TLItem(val key: String, val ym: String, val content: @Composable () -> Unit)

@Composable
private fun EntityBody(id: String, e: Entity) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val today = app.today
    val cur = today.take(7)

    // 記録(日付のあるもの)を時刻順に
    val acts = d.actsOf(id).mapNotNull { (k, a, role) -> d.dateOf(k).takeIf { it.isNotEmpty() }?.let { Triple(k, a, role) } }
    val yearN = d.actsOf(id).count { d.dateOf(it.first).startsWith(today.take(4)) }
    val money = acts.filter { it.second.type == "支払い" }.sumOf { a -> a.second.x?.let { d.expenses[it]?.amt } ?: 0 }
    val owed = d.acts.values.sumOf { a -> a.adv?.let { if (it.who == id && it.state == "未精算") it.amt else 0 } ?: 0 }
    val lastDate = d.lastMet(id)
    val rootKey = d.typeAnc(e.t.firstOrNull()).firstOrNull() ?: ""

    val plans: List<Pair<String, Plan>> = d.events.filterValues { p ->
        p.rec == null && !p.hol && (p.d ?: "") >= today &&
            (id in p.who || p.place?.ent == id || id in p.ent)
    }.entries.sortedBy { (it.value.d ?: "") + " " + (it.value.s ?: "") }.map { it.key to it.value }

    val todos = d.todos.filterValues { t -> t.done == null && id in t.ent }.entries
        .sortedBy { it.value.due ?: "9999" }

    val facts = e.facts.filter { !it.rare }
    val rare = e.facts.filter { it.rare }
    val rels = d.relsOf(id)

    // 詳細の上: フォルダのタブ + 本体(名前と種類)
    val path = d.typeAnc(e.t.firstOrNull())
    val fillHi = lerp(c.kinFill, Color.White, 0.18f)
    Column(Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 12.dp)) {
        Row(
            Modifier.height(32.dp).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(c.kinFill).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            Spacer(Modifier.width(8.dp))
            path.forEachIndexed { i, k ->
                if (i > 0) Text("›", style = plex(13, 700), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 6.dp))
                Text(
                    d.types[k]?.n ?: k, style = plex(13, 700), color = Color.White,
                    modifier = Modifier.clickable { app.go("types") },
                )
            }
        }
        EntityHero(e, fillHi)
    }

    // 統計: 最後に交わった / 今年 / 支出 / 立替
    Row(
        Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StatItem(agoLabel(lastDate, today), "最後に交わった")
        StatItem("${yearN}回", "今年")
        if (money > 0) StatItem(yen(money), if (rootKey == "person") "一緒の時の自分の支出" else "ここでの自分の支出")
        if (owed > 0) StatItem(yen(owed), "立替・未精算", valueColor = c.oweText)
    }

    // 事実と関係(表の面)
    if (facts.isNotEmpty() || rels.isNotEmpty()) {
        ListCard(Modifier.padding(top = 12.dp)) {
            var idx = 0
            facts.forEach { f ->
                FactLine(f.k, divider = idx > 0, since = sinceOf(f.ver)) {
                    Text(f.v, style = V4.t.body, color = c.tx)
                }
                idx++
            }
            rels.forEach { rel ->
                FactLine(rel.kind, divider = idx > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (rel.mutual) "⟷" else "→", style = plex(14, 500), color = c.tx3)
                        ERef(rel.to)
                    }
                }
                idx++
            }
        }
    }

    // ほかの情報(あまり使わない項目の折りたたみ)
    if (rare.isNotEmpty()) RareFacts(rare)

    // エンティティのメモの要約(角を折った紙)
    MemoPaper(id)

    // ToDo
    if (todos.isNotEmpty()) {
        SectionHead("ToDo", n = todos.size.toString())
        ListCard {
            todos.forEachIndexed { i, (k, t) ->
                V4Row(
                    t = if (t.pri) "${t.t} !" else t.t,
                    s = t.due?.let { Dates.relDay(it, today) },
                    lead = { CheckBox(false) { toggleTask(app, k) } },
                    chev = true, wrap = true, divider = i > 0,
                    onClick = { app.go("task", k) },
                )
            }
        }
    }

    // この縁の時間軸
    SectionHead("このエンティティの時間軸")
    Timeline(id, e, acts, plans, rels, cur, today, c)
}

@Composable
private fun StatItem(value: String, label: String, valueColor: Color = V4.c.tx) {
    Column {
        Text(value, style = plex(17, 600, 1.2).copy(fontFeatureSettings = "tnum"), color = valueColor)
        Text(label, style = V4.t.cap, color = V4.c.tx3)
    }
}

/** 本体: 大きな輪郭の面(名前+種類の値札) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntityHero(e: Entity, fillHi: Color) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val shape = RoundedCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(fillHi, c.kinFill)))
            .padding(18.dp),
    ) {
        Text(e.n, style = plex(24, 700, 1.3), color = Color.White, modifier = Modifier.fillMaxWidth())
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            e.t.forEach { t ->
                VTag(d.types[t]?.n ?: t, bg = c.kinFill, fg = Color.White, dot = Color.White)
            }
            Box(
                Modifier.height(28.dp).dashedBorder(Color.White.copy(alpha = 0.7f), width = 1.5.dp, radius = 6.dp)
                    .clickable { app.toast("種類の追加はモックでは未対応です") }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("＋種類", style = plex(14, 600), color = Color.White) }
        }
    }
}

/** ほかの情報(折りたたみ)。開くと rare の項目 */
@Composable
private fun RareFacts(rare: List<com.amgm.personallog.v4.data.Fact>) {
    val c = V4.c
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) { Text("ほかの情報 ${rare.size}件", style = V4.t.label, color = c.brand) }
        if (open) rare.forEachIndexed { i, f ->
            FactLine(f.k, divider = true) { Text(f.v, style = V4.t.body, color = c.tx) }
        }
    }
}

/** エンティティのメモの要約: 角を折った紙。有れば #/n/{id}、無ければ書く */
@Composable
private fun MemoPaper(id: String) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val note = d.notes[id]
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 0.dp, bottomEnd = 14.dp, bottomStart = 14.dp)
    Box(
        Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 12.dp).fillMaxWidth()
            .clip(shape).background(c.surface)
            .clickable {
                if (note != null) app.go("n", id)
                else {
                    val upd = "${Dates.md(app.today)} ${app.now}"
                    app.store.update { dd -> dd.copy(notes = dd.notes + (id to Note(upd = upd, ver = 1, body = emptyList(), subs = emptyList()))) }
                    app.go("n", id)
                }
            }
            .padding(start = 20.dp, end = 34.dp, top = 14.dp, bottom = 14.dp)
            // 左の綴じ目(縁の色)。文字の後ろに描く
            .drawBehind {
                drawRoundRect(
                    c.kin,
                    topLeft = Offset(0f, 14.dp.toPx()),
                    size = Size(3.dp.toPx(), size.height - 28.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            },
    ) {
        // 折り返し(右上の三角)
        Box(
            Modifier.align(Alignment.TopEnd).size(18.dp).drawBehind {
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, c.line)
            },
        )
        Column {
            if (note == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(V4Icons.lines, null, tint = c.tx2, modifier = Modifier.size(16.dp))
                    Text("エンティティのメモを書く", style = V4.t.label, color = c.tx2)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(V4Icons.lines, null, tint = c.tx2, modifier = Modifier.size(16.dp))
                    Text("エンティティのメモ", style = V4.t.label, color = c.tx2)
                    Spacer(Modifier.weight(1f))
                    Text("更新 ${note.upd}", style = V4.t.cap, color = c.tx3)
                }
                val body = note.body.firstOrNull()?.getOrNull(1).orEmpty()
                Text(
                    MARK_RE.replace(body, ""), style = V4.t.body, color = c.tx, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** 時間軸の本体: 過去(今月と前の月。残りは「これより前を見る」) → いまの線 → 予定 */
@Composable
private fun Timeline(
    id: String, e: Entity, acts: List<Triple<String, com.amgm.personallog.v4.data.Activity, String>>,
    plans: List<Pair<String, Plan>>, rels: List<com.amgm.personallog.v4.data.Rel>, cur: String, today: String,
    c: com.amgm.personallog.v4.ui.V4Colors,
) {
    val app = LocalApp.current
    val d = LocalData.current
    var allPast by remember(id) { mutableStateOf(false) }
    val from = Dates.parse(today).minusMonths(1).toString().take(7)

    // 過去の項目(古い → 新しい)
    val past = ArrayList<TLItem>()
    for ((k, a, role) in acts) {
        val date = d.dateOf(k)
        past.add(TLItem(date + " " + (a.time ?: ""), date.take(7)) {
            MomentRow(k, exclude = id, showDate = true, role = role)
        })
    }
    // 事実の変化(履歴の段の期間)
    e.facts.forEach { f ->
        f.hist.forEach { h ->
            val ym = h.getOrNull(1).orEmpty().take(7)
            if (YMRE.matches(ym)) {
                val meta = listOfNotNull(
                    h.getOrNull(0)?.let { "$it → ${f.v}" },
                    h.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { "($it)" },
                ).joinToString(" ")
                past.add(TLItem("$ym-00", ym) {
                    AxRow(time = "", title = "${f.k}が変わった", meta = meta, node = AxNode.FACT)
                })
            }
        }
    }
    // 関係の始まり
    rels.forEach { r ->
        val ym = r.period.take(7)
        if (YMRE.matches(ym)) {
            past.add(TLItem("$ym-00", ym) {
                AxRow(time = "", node = AxNode.FACT, content = {
                    Column {
                        Text("${r.kind}: ${d.entName(r.to)}", style = plex(15, 500, 1.4), color = c.tx)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Knot(8.dp)
                            Text("関係が始まった", style = V4.t.cap, color = c.tx3)
                        }
                    }
                })
            })
        }
    }
    past.sortBy { it.key }

    // これからの予定
    val future = plans.map { (pid, p) ->
        TLItem((p.d ?: "") + " " + (p.s ?: ""), (p.d ?: "").take(7)) {
            PlanRowAx(pid, future = true, showDate = true, exclude = id)
        }
    }

    val shownPast = if (allPast) past else past.filter { it.ym >= from }
    val older = past.size - shownPast.size
    val months = (shownPast.map { it.ym } + future.map { it.ym } +
        (if (shownPast.isNotEmpty() || future.isNotEmpty()) listOf(cur) else emptyList()))
        .distinct().sorted()

    AxColumn {
        when {
            past.isEmpty() -> AxEdge("記録 なし", past = true)
            older > 0 -> AxEdge("これより前を見る ›", past = true, onClick = { allPast = true })
            else -> AxEdge("これより前の記録 なし", past = true)
        }
        months.forEach { ym ->
            MonthHead(ym, future = ym > cur)
            shownPast.filter { it.ym == ym }.forEach { it.content() }
            if (ym == cur) NowLine(app.now)
            future.filter { it.ym == ym }.forEach { it.content() }
        }
    }
}

/* ---------------- 種類の木 #/types ---------------- */

private data class TreeRow(val k: String, val depth: Int, val hasKids: Boolean, val open: Boolean)

@Composable
fun TypesScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    // 手で開いた/閉じた種類(再起動で初期に戻る)。深さ0と1は初期で開く
    var flipped by remember { mutableStateOf(setOf<String>()) }

    val flat = ArrayList<TreeRow>()
    fun walk(k: String, depth: Int) {
        val kids = d.types.filterValues { it.p == k }.keys.toList()
        val open = (depth < 2) xor (k in flipped)
        flat.add(TreeRow(k, depth, kids.isNotEmpty(), open))
        if (open) kids.forEach { walk(it, depth + 1) }
    }
    d.enRoots().forEach { walk(it, 0) }

    Screen(
        topBar = {
            AppBar("種類", back = true, actions = listOf(BarAction(V4Icons.plus, "種類を作成") { openTypeSheet(app, "") }))
        },
    ) {
        Spacer(Modifier.height(12.dp))
        Objs {
            flat.forEach { row ->
                val k = row.k
                Box(Modifier.fillMaxWidth().padding(start = (row.depth * 20).dp)) {
                    ObjCard(onClick = { openTypeEdit(app, k) }) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 14.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // 開閉の印(子がある種類だけ。押しても行の編集は開かない)
                            Box(
                                Modifier.size(44.dp).let {
                                    if (row.hasKids) it.clickable { flipped = if (k in flipped) flipped - k else flipped + k } else it
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (row.hasKids) Icon(if (row.open) V4Icons.down else V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
                            }
                            if (row.depth == 0) Knot(8.dp) else Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                VTag(d.types[k]?.n ?: k)
                                val props = d.propsOf(k).map { it.first }
                                Text(
                                    "項目 " + (if (props.isEmpty()) "なし" else props.joinToString("・")),
                                    style = V4.t.cap, color = c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            Text("${d.enCount(k)}", style = V4.t.cap, color = c.tx3)
                        }
                    }
                }
            }
        }
    }
}

/** 種類の編集・作成のフォームの中身(名前・親・項目) */
private class TypeForm(val k: String?, name: String, parent: String?) {
    val nameS: MutableState<String> = mutableStateOf(name)
    val parent: MutableState<String?> = mutableStateOf(parent)
}

private fun openTypeEdit(app: AppState, k: String) {
    val t = app.data.types[k]
    val form = TypeForm(k, t?.n.orEmpty(), t?.p)
    app.openSheet(title = "種類を編集", mainLabel = "保存", onMain = { saveType(app, form) }) {
        TypeFormBody(form)
    }
}

/** 種類を作成。parent=親(空なら いちばん上) */
private fun openTypeSheet(app: AppState, parent: String) {
    val form = TypeForm(null, "", parent.ifEmpty { null })
    app.openSheet(title = "種類を作成", mainLabel = "作成", onMain = { createType(app, form) }) {
        TypeFormBody(form)
    }
}

private fun saveType(app: AppState, form: TypeForm) {
    val nm = form.nameS.value.trim()
    if (nm.isEmpty()) { app.toast("名前を入れてください"); return }
    val k = form.k ?: return
    app.store.update { dd -> dd.copy(types = dd.types + (k to TypeDef(nm, form.parent.value))) }
    app.closeSheet()
    app.toast("「$nm」を保存しました")
}

private fun createType(app: AppState, form: TypeForm) {
    val nm = form.nameS.value.trim()
    if (nm.isEmpty()) { app.toast("名前を入れてください"); return }
    app.store.update { dd -> dd.copy(types = dd.types + (uid("t") to TypeDef(nm, form.parent.value))) }
    app.closeSheet()
    app.toast("「$nm」を作成しました")
}

private fun deleteType(app: AppState, k: String) {
    val d = app.data
    val t = d.types[k] ?: return
    val hasKids = d.types.values.any { it.p == k }
    if (hasKids || d.enCount(k) > 0) { app.toast("使用中・子ありのため削除できません"); return }
    app.store.update { dd -> dd.copy(types = dd.types - k, typeProps = dd.typeProps - k) }
    app.closeSheet()
    app.toast("「${t.n}」を削除しました", undo = true)
}

@Composable
private fun TypeFormBody(form: TypeForm) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val k = form.k
    var menu by remember { mutableStateOf(false) }
    val parent = form.parent.value

    ListCard {
        Field("名前", form.nameS.value, { form.nameS.value = it }, placeholder = "例: 居酒屋")
        Divider(14.dp)
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { menu = true }.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("親", style = V4.t.label, color = c.tx3, modifier = Modifier.width(84.dp))
                Text(if (parent == null) "いちばん上(輪郭を決める)" else d.typePath(parent), style = V4.t.body, color = c.tx)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("いちばん上(輪郭を決める)") }, onClick = { form.parent.value = null; menu = false })
                d.types.keys.filter { t -> k == null || !d.isUnder(t, k) }.forEach { t ->
                    DropdownMenuItem(text = { Text(d.typePath(t)) }, onClick = { form.parent.value = t; menu = false })
                }
            }
        }
    }

    if (k != null) {
        // 項目(この種類で定義 / 親から継承)
        val own = d.typeProps[k].orEmpty()
        val inherited = d.propsOf(k).filter { it.second != k }
        SectionHead("項目", n = (own.size + inherited.size).toString())
        ListCard {
            if (own.isEmpty() && inherited.isEmpty()) {
                PropLine("項目 なし", "", faint = true, divider = false)
            }
            own.forEachIndexed { i, name -> PropLine(name, "この種類", faint = false, divider = i > 0) }
            inherited.forEachIndexed { i, (name, owner) ->
                PropLine(name, "${d.types[owner]?.n ?: owner}から", faint = true, divider = own.isNotEmpty() || i > 0)
            }
        }

        val kids = d.types.values.any { it.p == k }
        val used = d.enCount(k)
        val blocked = when {
            kids -> "子の種類があるため削除できません"
            used > 0 -> "${used}件で使われているため削除できません"
            else -> null
        }
        if (blocked != null) Hint(blocked)

        BtnRow {
            TonalButton("子の種類を追加", modifier = Modifier.weight(1f)) { openTypeSheet(app, k) }
            DeleteButton(modifier = Modifier.weight(1f)) { deleteType(app, k) }
        }
    }
}

@Composable
private fun PropLine(name: String, right: String, faint: Boolean, divider: Boolean) {
    val c = V4.c
    Column {
        if (divider) Divider(14.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = V4.t.body, color = if (faint) c.tx3 else c.tx, modifier = Modifier.weight(1f))
            if (right.isNotEmpty()) Text(right, style = V4.t.cap, color = c.tx3)
        }
    }
}
