package com.amgm.personallog.v4.screens.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Category
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.Expense
import com.amgm.personallog.v4.data.XItem
import com.amgm.personallog.v4.data.catAnc
import com.amgm.personallog.v4.data.catColor
import com.amgm.personallog.v4.data.catFx
import com.amgm.personallog.v4.data.catName
import com.amgm.personallog.v4.data.catPath
import com.amgm.personallog.v4.data.catRoot
import com.amgm.personallog.v4.data.catSp
import com.amgm.personallog.v4.data.nextOn
import com.amgm.personallog.v4.data.payLabel
import com.amgm.personallog.v4.data.roots
import com.amgm.personallog.v4.data.shopOf
import com.amgm.personallog.v4.data.uid
import com.amgm.personallog.v4.data.xDate
import com.amgm.personallog.v4.data.xFx
import com.amgm.personallog.v4.data.xSp
import com.amgm.personallog.v4.data.xTitle
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.Route
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.colorOf
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.components.AppBar
import com.amgm.personallog.v4.ui.components.BarAction
import com.amgm.personallog.v4.ui.components.Banner
import com.amgm.personallog.v4.ui.components.CatTag
import com.amgm.personallog.v4.ui.components.Divider
import com.amgm.personallog.v4.ui.components.Empty
import com.amgm.personallog.v4.ui.components.Hint
import com.amgm.personallog.v4.ui.components.Ico
import com.amgm.personallog.v4.ui.components.LargeTitle
import com.amgm.personallog.v4.ui.components.ListCard
import com.amgm.personallog.v4.ui.components.Pill
import com.amgm.personallog.v4.ui.components.press
import com.amgm.personallog.v4.ui.components.Screen
import com.amgm.personallog.v4.ui.components.Seg
import com.amgm.personallog.v4.ui.components.SectionHead
import com.amgm.personallog.v4.ui.components.Tag
import com.amgm.personallog.v4.ui.components.TagKind
import com.amgm.personallog.v4.ui.components.TitleAction
import com.amgm.personallog.v4.ui.components.dashedBorder
import com.amgm.personallog.v4.ui.components.num
import com.amgm.personallog.v4.ui.components.PayShell
import com.amgm.personallog.v4.ui.components.PsMode
import com.amgm.personallog.v4.ui.components.press
import com.amgm.personallog.v4.ui.components.tlShadow

/*
 * 家計簿(#/ledger)・自分の支出の詳細(#/x/{id})・カテゴリの木(#/cats)。
 * 仕様: /home/claude/specs/ledger.md。今月の数字・一覧・内訳は、選んだ月の支出だけで集計する(§9-1)。
 * 「今月」は app.today の月(§9-2)。推移の強調は選んだ月(§9-4)。
 */

/* ---------------- 集計(ledger.js の計算) ---------------- */

/** 1か月分。byRoot = 大分類ごとの合計 */
internal class LMonth(val ym: String, val byRoot: Map<String, Int>) {
    val total: Int get() = byRoot.values.sum()
    val label: String get() = "${ym.substring(5, 7).toInt()}月"
}

/** 6か月(古い→新しい)。最後が app.today の月。過去の月は TREND の値、今月は支出から */
internal fun AppData.ledgerMonths(today: String): List<LMonth> {
    val cur = java.time.YearMonth.from(Dates.parse(today))
    return (5 downTo 0).map { back ->
        val ym = cur.minusMonths(back.toLong()).toString()
        val trendRow = if (back == 0) null else trend.firstOrNull { it.label == "${ym.substring(5, 7).toInt()}月" }
        LMonth(ym, trendRow?.byRoot ?: monthRoots(ym))
    }
}

/** その月の支出を大分類ごとに合計する(xDate の先頭7文字 = 月) */
internal fun AppData.monthRoots(ym: String): Map<String, Int> =
    expenses.values.filter { xDate(it).take(7) == ym }
        .groupBy { catRoot(it.cat) }
        .mapValues { (_, xs) -> xs.sumOf { it.amt } }

/** 絞りに当たるか(ldMatch) */
internal fun AppData.ledgerMatch(x: Expense, f: LFilter?): Boolean =
    if (f == null) true else when (f.ax) {
        "cat" -> catAnc(x.cat).contains(f.k)
        "fx" -> (xFx(x) ?: "未指定") == f.k
        "sp" -> (xSp(x) ?: "未指定") == f.k
        else -> true
    }

/** 直下の子カテゴリ(CATS の登録順) */
internal fun AppData.childrenOf(k: String): List<String> = cats.filterValues { it.p == k }.keys.toList()

/** 自分と子孫のカテゴリ(親の候補から外す・削除の判定) */
internal fun AppData.catDesc(k: String): Set<String> {
    val out = linkedSetOf(k)
    fun walk(c: String) { for (ch in childrenOf(c)) if (out.add(ch)) walk(ch) }
    walk(k)
    return out
}

/* ---------------- 家計簿 #/ledger ---------------- */

private class LBreak(val ax: String, val k: String, val name: String, val color: Color, val v: Int, val kid: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedgerScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val months = remember(d, app.today) { d.ledgerMonths(app.today) }
    var mi by rememberSaveable { mutableIntStateOf(months.lastIndex) }
    var axis by rememberSaveable { mutableStateOf("cat") }
    var filter by remember { mutableStateOf<LFilter?>(null) }

    val isNow = mi == months.lastIndex
    val cur = months[mi]
    val tot = cur.total
    val prevTot = if (mi > 0) months[mi - 1].total else 0
    val diff: Int? = if (prevTot != 0) Math.round((tot - prevTot).toDouble() / prevTot * 100).toInt() else null
    val byRoot = cur.byRoot
    val ranked = d.roots().filter { (byRoot[it] ?: 0) > 0 }.sortedByDescending { byRoot[it] ?: 0 }

    // 選んだ月の支出だけ(§9-1)
    val mx = d.expenses.filterValues { d.xDate(it).take(7) == cur.ym }
    val fixed = mx.values.filter { d.xFx(it) == "固定" }.sumOf { it.amt }
    val varia = mx.values.filter { d.xFx(it) == "変動" }.sumOf { it.amt }
    val daily = mx.values.filter { d.xSp(it) == "日常" }.sumOf { it.amt }
    val nonDaily = mx.values.filter { d.xSp(it) == "非日常" }.sumOf { it.amt }
    val fl = mx.entries.filter { (_, x) -> d.ledgerMatch(x, filter) }
        .sortedWith(compareBy<Map.Entry<String, Expense>>({ d.xDate(it.value) }, { d.acts[it.value.pay]?.time ?: "" }))

    Screen(topBar = null) {
        LargeTitle("家計簿", listOf(TitleAction(text = "カテゴリ", icon = V4Icons.cats, onClick = { app.go("cats") })))

        Row(Modifier.padding(start = V4Dim.gutter, bottom = 12.dp, top = 2.dp)) {
            LPeriodNav(
                label = cur.label, sub = cur.ym.take(4),
                prevOn = mi > 0, nextOn = !isNow,
                onPrev = { if (mi > 0) { mi -= 1; filter = null } },
                onNext = { if (!isNow) { mi += 1; filter = null } },
            )
        }
        if (!isNow) Banner(warn = false, text = "この月の明細は10月だけ表示しています。")

        // まとめ
        ListCard {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("自分の支出", style = V4.t.label, color = c.tx2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(yen(tot), style = plex(34, 700, 1.1).num(), color = c.tx, maxLines = 1, softWrap = false)
                    if (diff != null) Tag("前月比 " + (if (diff > 0) "+" else "") + "$diff%")
                }
                // 大分類の割合の帯
                Row(
                    Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.line)
                        .semantics { contentDescription = "大分類の割合" },
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ranked.forEach { rt ->
                        Box(Modifier.weight((byRoot[rt] ?: 0).toFloat()).fillMaxHeight().background(colorOf(d.catColor(rt))))
                    }
                }
            }
            if (isNow) {
                // 固定費などの押せる数字(.ld-fig)
                Divider(0.dp)
                val cells = buildList {
                    add(LCell("固定費", fixed, LFilter("fx", "固定"), strong = false))
                    add(LCell("変動費", varia, LFilter("fx", "変動"), strong = false))
                    val un = tot - fixed - varia
                    if (un > 0) add(LCell("固定・変動 未指定", un, LFilter("fx", "未指定"), strong = false))
                    add(LCell("非日常", nonDaily, LFilter("sp", "非日常"), strong = true))
                }
                cells.chunked(3).forEachIndexed { ci, chunk ->
                    Row(Modifier.fillMaxWidth()) {
                        chunk.forEachIndexed { j, cell ->
                            val sel = filter == cell.f
                            val overall = ci * 3 + j
                            Column(
                                Modifier.weight(1f).heightIn(min = 56.dp)
                                    .background(if (sel) c.brandSoft else Color.Transparent)
                                    .let {
                                        if (overall == 0) it
                                        else it.drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx()) }
                                    }
                                    .press(onClick = { filter = if (sel) null else cell.f })
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (cell.strong) Icon(V4Icons.sparkle, null, tint = c.tx, modifier = Modifier.size(13.dp).padding(end = 3.dp))
                                    Text(cell.label, style = V4.t.cap, color = if (cell.strong) c.tx else c.tx3)
                                }
                                Text(yen(cell.v), style = plex(15, 600, 1.3).num(), color = c.tx)
                            }
                        }
                        repeat(3 - chunk.size) { Box(Modifier.weight(1f)) }
                    }
                }
                Divider(0.dp)
                // 下段の操作(.sc-foot)
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LActButton("レシートで支払い", V4Icons.camera, primary = true, modifier = Modifier.weight(1f)) { com.amgm.personallog.v4.screens.create.openReceiptSheet(app) }
                    LActButton("金額だけ", V4Icons.plus, primary = false, modifier = Modifier.weight(1f)) { com.amgm.personallog.v4.screens.create.openPaymentSheet(app) }
                }
            }
        }

        // 推移(6か月)
        SectionHead("推移", "6か月")
        ListCard {
            val maxV = months.maxOf { it.total }.coerceAtLeast(1)
            val past = months.dropLast(1)
            val avg = if (past.isEmpty()) 0 else Math.round(past.sumOf { it.total }.toDouble() / past.size).toInt()
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                months.forEachIndexed { i, m ->
                    val on = i == mi
                    val barH = if (on) 14.dp else 12.dp
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(m.label, style = plex(12, if (on) 700 else 500, 1.4), color = if (on) c.tx else c.tx2, modifier = Modifier.width(32.dp))
                        Box(
                            Modifier.weight(1f).height(barH).drawWithContent {
                                drawContent()
                                // 5か月平均の点線(.ld-avg)
                                val x = size.width * avg / maxV
                                drawLine(
                                    c.tx3.copy(alpha = 0.6f), Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()),
                                    strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                                )
                            },
                        ) {
                            Row(Modifier.fillMaxWidth((m.total.toFloat() / maxV).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp))) {
                                m.byRoot.filter { it.value > 0 }.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                                    Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(colorOf(d.catColor(k))))
                                }
                            }
                        }
                        Text(
                            yen(m.total), style = plex(13, if (on) 700 else 500, 1.0).num(), color = if (on) c.tx else c.tx2,
                            textAlign = TextAlign.End, modifier = Modifier.width(72.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(40.dp))
                    Box(Modifier.weight(1f)) {
                        Text(
                            "${past.size}か月平均 ${yen(avg)} ┆", style = V4.t.cap, color = c.tx3, maxLines = 1, softWrap = false,
                            modifier = Modifier.fillMaxWidth().layout { measurable, constraints ->
                                // 平均の位置の右端に合わせる(translateX(-100% + 4px))
                                val p = measurable.measure(Constraints())
                                val w = constraints.maxWidth
                                val x = (w * avg.toFloat() / maxV).toInt()
                                layout(w, p.height) { p.place(x - p.width + 4.dp.roundToPx(), 0) }
                            },
                        )
                    }
                }
            }
        }

        if (isNow) {
            // 内訳
            SectionHead("内訳")
            Seg(listOf("cat" to "カテゴリ", "fx" to "固定・変動", "sp" to "特別感"), axis, onPick = { axis = it; filter = null })
            val rows = buildList {
                when (axis) {
                    "cat" -> ranked.forEach { rt ->
                        add(LBreak("cat", rt, d.catName(rt), colorOf(d.catColor(rt)), byRoot[rt] ?: 0, false))
                        val f = filter
                        if (f != null && f.ax == "cat" && d.catRoot(f.k) == rt) {
                            d.childrenOf(rt)
                                .map { ch -> ch to mx.values.filter { d.catAnc(it.cat).contains(ch) }.sumOf { it.amt } }
                                .filter { it.second > 0 }.sortedByDescending { it.second }
                                .forEach { (ch, v) -> add(LBreak("cat", ch, d.catName(ch), colorOf(d.catColor(ch)), v, true)) }
                        }
                    }
                    "fx" -> {
                        add(LBreak("fx", "固定", "固定", c.tx2, fixed, false))
                        add(LBreak("fx", "変動", "変動", c.axis, varia, false))
                        if (tot - fixed - varia > 0) add(LBreak("fx", "未指定", "未指定", c.lineStrong, tot - fixed - varia, false))
                    }
                    else -> {
                        add(LBreak("sp", "日常", "日常", c.axis, daily, false))
                        add(LBreak("sp", "非日常", "非日常", c.ev, nonDaily, false))
                        if (tot - daily - nonDaily > 0) add(LBreak("sp", "未指定", "未指定", c.lineStrong, tot - daily - nonDaily, false))
                    }
                }
            }
            if (rows.isNotEmpty()) ListCard {
                rows.forEachIndexed { i, b ->
                    val sel = filter?.ax == b.ax && filter?.k == b.k
                    val frac = if (tot > 0) b.v.toFloat() / tot else 0f
                    val pct = Math.round(frac * 100)
                    LRow(
                        t = b.name, minH = 52.dp, padV = 8.dp,
                        lead = { LSquare(b.color) },
                        sub = {
                            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.line)) {
                                Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(b.color))
                            }
                        },
                        v = { LAmount(b.v, pct) },
                        indent = if (b.kid) 22.dp else 0.dp,
                        guideX = if (b.kid) 19.dp else null,
                        selected = sel,
                        divider = i > 0,
                        onClick = { filter = if (sel) null else LFilter(b.ax, b.k) },
                    )
                }
            }

            // 支出(選んだ月の一覧)
            SectionHead("支出", "${fl.size}件")
            val f = filter
            if (f != null) {
                val axName = when (f.ax) { "cat" -> "カテゴリ"; "fx" -> "固定・変動"; else -> "特別感" }
                val name = if (f.ax == "cat") d.catName(f.k) else f.k
                Row(Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, bottom = 10.dp)) {
                    Pill("$axName: $name ×", selected = true) { filter = null }
                }
            }
            if (fl.isEmpty()) Empty(V4Icons.expense, "該当の支出 なし")
            else Column(Modifier.padding(horizontal = V4Dim.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fl.forEach { (k, x) -> ExpenseTicket(app, d, k, x) }
            }

            // これからの固定費(繰り返す支払い予定)
            val ups = d.plans.filterValues { it.pay != null && it.rep != null }.entries
                .sortedBy { nextOn(it.value, app.today) ?: "" }
            if (ups.isNotEmpty()) {
                SectionHead("これからの固定費", "${ups.size}件")
                Column(Modifier.padding(horizontal = V4Dim.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ups.forEach { (k, e) ->
                        val pay = e.pay ?: return@forEach
                        val nd = nextOn(e, app.today)
                        Row(
                            Modifier.fillMaxWidth().dashedBorder(c.plan, radius = 14.dp)
                                .press(onClick = { app.go("ev", k) })
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(nd?.let { Dates.md(it) } ?: "", style = plex(13, 500, 1.3).num(), color = c.tx2, modifier = Modifier.width(44.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.t, style = plex(15, 500, 1.4), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(d.catPath(pay.cat), style = V4.t.cap, color = c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(yen(pay.amt), style = plex(15, 600, 1.4).num(), color = c.tx)
                        }
                    }
                }
            }
        }
    }
}

/** 固定・変動などの押せる数字 */
private class LCell(val label: String, val v: Int, val f: LFilter, val strong: Boolean)

/** 家計簿の1件(半券)。押すと自分の支出の詳細 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseTicket(app: AppState, d: AppData, key: String, x: Expense) {
    val c = V4.c
    val p = d.acts[x.pay]
    val payAmt = p?.amt ?: x.amt
    val rest = payAmt - x.amt
    val owed = p?.adv?.state == "未精算"
    val date = d.xDate(x)
    val title = d.xTitle(x)
    val shop = d.shopOf(x)
    LTicket(
        t = title,
        sp = d.xSp(x),
        onClick = { app.go("x", key) },
        s = { sc ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val head = (if (date.isEmpty()) "" else Dates.fmtMDW(date)) + (if (title != shop && shop.isNotEmpty()) " · $shop" else "")
                Text(head, style = plex(13, 400, 1.35), color = sc)
                CatTag(x.cat)
                if (rest > 0) LPayBar(x.amt, rest, owed)
            }
        },
        amt = {
            Column(horizontalAlignment = Alignment.End) {
                Text(yen(x.amt), style = plex(17, 700, 1.0).num(), color = c.tx)
                if (x.amt != payAmt) Text("支払い ${yen(payAmt)}", style = plex(12, 400, 1.2).num(), color = c.tx3, modifier = Modifier.padding(top = 3.dp))
            }
        },
    )
}

/* ---------------- 自分の支出の詳細 #/x/{id} ---------------- */

/** 品目を「同じ名前・同じ金額」でまとめた組(表示だけ。データは1行=1個)。モックの itemGroups(金額行は支出の品目に無い) */
private class LItemGroup(val first: XItem, val idx: MutableList<Int>)

private fun lGroupItems(items: List<XItem>): List<LItemGroup> {
    val out = ArrayList<LItemGroup>()
    val at = HashMap<String, Int>()
    items.forEachIndexed { i, p ->
        val key = "${p.n}|${p.price}"
        val g = at[key]
        if (g == null) {
            at[key] = out.size
            out.add(LItemGroup(p, mutableListOf(i)))
        } else {
            out[g].idx.add(i)
        }
    }
    return out
}

/** 同じ品目を開いているか(画面のメモリ上。モックの openGroups と同じ) */
private val lOpenGroups = mutableStateMapOf<String, Boolean>()

@Composable
fun ExpenseDetailScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val key = r.id
    val x = key?.let { d.expenses[it] }
    Screen(topBar = {
        AppBar(
            title = "",
            actions = if (key != null && x != null) listOf(BarAction(V4Icons.more, "その他の操作") { openExpenseMenu(app, key) }) else emptyList(),
        )
    }) {
        if (key == null || x == null) {
            Empty(V4Icons.expense, "支出 なし")
            return@Screen
        }
        Column(Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 6.dp)) { PayShell(x.pay, PsMode.X) }

        SectionHead("分類")
        ListCard {
            LProp("カテゴリ", chev = true, onClick = { openCatPick(app, key) }) { CatTag(x.cat) }
            LProp(
                "固定・変動", divider = true,
                meta = {
                    if (x.fx != null) Tag("この支出だけ", kind = TagKind.BRAND)
                    if (d.catFx(x.cat) == null) Tag("都度指定", kind = TagKind.WARN)
                },
            ) {
                LMini(listOf("固定" to "固定", "変動" to "変動"), d.xFx(x) ?: "") { v -> setExpenseFx(app, key, v) }
            }
            LProp(
                "特別感", divider = true,
                meta = {
                    if (x.sp != null) Tag("この支出だけ", kind = TagKind.BRAND)
                    if (d.catSp(x.cat) == null) Tag("都度指定", kind = TagKind.WARN)
                },
            ) {
                LMini(listOf("日常" to "日常", "非日常" to "非日常"), d.xSp(x) ?: "") { v -> setExpenseSp(app, key, v) }
            }
            LProp("メモ", divider = true) {
                LTextField(value = x.memo, onValue = { v -> setExpenseMemo(app, key, v) }, placeholder = "メモを書く", modifier = Modifier.fillMaxWidth())
            }
        }

        // 品目(同じものは「×N」にまとめ、開くと1個目・2個目が字下げ+案内線で並ぶ)
        val groups = lGroupItems(x.items)
        SectionHead("品目", "${groups.size}", actLabel = "編集", actIcon = V4Icons.edit, onAct = { app.go("a", x.pay) }  /* 品目は支払いが持つ → 支払いの詳細(品目の編集はそこ) */)
        ListCard {
            groups.forEachIndexed { gi, g -> LItemGroupRow(key, x.pay, x.items, g, gi, divider = gi > 0) }
            val total = x.items.sumOf { it.price }
            LRow(
                t = "品目の合計 = 自分の支出", tBold = true, divider = groups.isNotEmpty(),
                v = { Text(yen(total), style = plex(15, 600, 1.4).num(), color = c.me) },
            )
        }
    }
}

/** 品目の1組(.row.grp)。2個以上は「名前 ×N ⌄」と「1個 ¥N」、押すと開く。1個だけは補足に全額などを出し、押すと品目の詳細 */
@Composable
private fun LItemGroupRow(key: String, pay: String, items: List<XItem>, g: LItemGroup, gi: Int, divider: Boolean) {
    val app = LocalApp.current
    val c = V4.c
    val gk = "$key:$gi"
    val n = g.idx.size
    val open = lOpenGroups[gk] == true
    val unit = g.first.price
    Column {
        if (divider) Divider(14.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).press {
                if (n > 1) lOpenGroups[gk] = !open else app.go("i", pay, g.idx[0].toString())
            }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.first.n, style = plex(15, 500, 1.4), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (n > 1) {
                        Text(" ×$n", style = plex(15, 400, 1.4), color = c.tx3)
                        Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.padding(start = 4.dp).size(16.dp).rotate(if (open) -90f else 90f))
                    }
                }
                Text(if (n > 1) "1個 ${yen(unit)}" else g.first.b, style = V4.t.cap, color = c.tx3, modifier = Modifier.padding(top = 2.dp))
            }
            Text(yen(unit * n), style = plex(15, 600, 1.4).num(), color = c.tx)
            if (n == 1) Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
        // 開いた子(.grp-kids): 一覧の左から34dp字下げ。案内線は一覧の左から16dp、親の下から中ほどまで
        if (n > 1 && open) Column(
            Modifier.fillMaxWidth().padding(start = 34.dp, bottom = 6.dp).drawBehind {
                val x = -18.dp.toPx()
                drawLine(c.line, Offset(x, -4.dp.toPx()), Offset(x, size.height / 2), strokeWidth = 1.5.dp.toPx())
            },
        ) {
            g.idx.forEachIndexed { k, idx ->
                if (k > 0) Divider(12.dp)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).press { app.go("i", pay, idx.toString()) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LKidNo(k + 1)
                    Text(
                        items[idx].memo ?: "メモなし", style = V4.t.label, color = c.tx2,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Tag(items[idx].b)
                    Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** 子の番号(.kid-n): 22dp の丸、背景 --sunk */
@Composable
private fun LKidNo(n: Int) {
    val c = V4.c
    Box(Modifier.size(22.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) {
        Text("$n", style = plex(12, 600), color = c.tx2)
    }
}

private fun setExpenseFx(app: AppState, key: String, v: String) {
    app.store.update(undoable = false) { d ->
        val x = d.expenses[key] ?: return@update d
        // 今と同じ値・カテゴリの既定と同じ値を押すと、個別の上書きを外す(ldSetFx)
        val nx = x.copy(fx = if (x.fx == v || v == d.catFx(x.cat)) null else v)
        d.copy(expenses = d.expenses + (key to nx))
    }
}

private fun setExpenseSp(app: AppState, key: String, v: String) {
    app.store.update(undoable = false) { d ->
        val x = d.expenses[key] ?: return@update d
        val nx = x.copy(sp = if (x.sp == v || v == d.catSp(x.cat)) null else v)
        d.copy(expenses = d.expenses + (key to nx))
    }
}

private fun setExpenseMemo(app: AppState, key: String, v: String) {
    app.store.update(undoable = false) { d ->
        val x = d.expenses[key] ?: return@update d
        d.copy(expenses = d.expenses + (key to x.copy(memo = v)))
    }
}

private fun setExpenseCat(app: AppState, key: String, k: String) {
    app.store.update(undoable = false) { d ->
        val x = d.expenses[key] ?: return@update d
        d.copy(expenses = d.expenses + (key to x.copy(cat = k)))
    }
    app.closeSheet()
    app.toast("カテゴリを変えました")
}

/** 操作シート(ldMenu) */
private fun openExpenseMenu(app: AppState, key: String) {
    app.openSheet(title = "操作") {
        ListCard {
            LRow(
                t = "編集", minH = 56.dp, padV = 10.dp,
                lead = { Ico(V4Icons.edit) },
                onClick = { app.closeSheet(); app.toast("編集はモックでは未対応です") },
            )
            LRow(
                t = "支払いを開く", minH = 56.dp, padV = 10.dp, divider = true,
                lead = { Ico(V4Icons.pay) },
                onClick = { app.closeSheet(); app.go("a", app.data.expenses[key]?.pay ?: "") },
            )
        }
    }
}

/** カテゴリを選ぶシート(ldCatPick): 大分類と直下の子(2段まで) */
private fun openCatPick(app: AppState, key: String) {
    app.openSheet(title = "カテゴリを選ぶ") {
        val d = LocalData.current
        val cur = d.expenses[key]?.cat
        ListCard { CatPickRows(d, cur, depth = 0, parent = null) { k -> setExpenseCat(app, key, k) } }
    }
}

@Composable
private fun CatPickRows(d: AppData, cur: String?, depth: Int, parent: String?, onPick: (String) -> Unit) {
    val list = if (parent == null) d.roots() else d.childrenOf(parent)
    list.forEachIndexed { i, k ->
        LRow(
            t = d.catName(k), minH = 52.dp, padV = 10.dp,
            lead = { LSquare(colorOf(d.catColor(k))) },
            v = if (cur == k) ({ CheckMark() }) else null,
            indent = if (depth > 0) 22.dp else 0.dp,
            guideX = if (depth > 0) 19.dp else null,
            divider = i > 0 || depth > 0,
            onClick = { onPick(k) },
        )
        if (depth == 0) CatPickRows(d, cur, depth = 1, parent = k, onPick = onPick)
    }
}

@Composable
private fun CheckMark() {
    Icon(V4Icons.check, null, tint = V4.c.brand, modifier = Modifier.size(18.dp))
}

/* ---------------- カテゴリの木 #/cats ---------------- */

@Composable
fun CatsScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    Screen(topBar = {
        AppBar(
            title = "カテゴリ",
            actions = listOf(BarAction(V4Icons.plus, "カテゴリを作成") { openCatSheet(app, null) }),
        )
    }) {
        SectionHead("すべて", "${d.cats.size}")
        ListCard {
            d.roots().forEachIndexed { i, rt -> CatNode(app, d, rt, divider = i > 0) }
        }
    }
}

/** 木の1行(大分類・子)。子は字下げ21の縦の線の中 */
@Composable
private fun CatNode(app: AppState, d: AppData, k: String, divider: Boolean) {
    val c = V4.c
    val kids = d.childrenOf(k)
    val sum = d.expenses.values.filter { d.catAnc(it.cat).contains(k) }.sumOf { it.amt }
    Column {
        LRow(
            t = d.catName(k), minH = 56.dp, padV = 10.dp,
            lead = { LSquare(colorOf(d.catColor(k))) },
            sub = { Text(catDefs(d, k, c.tx2, c.tx3), style = V4.t.cap, color = c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) },
            v = { Text(yen(sum), style = plex(15, 600, 1.4).num(), color = c.tx) },
            chev = true, divider = divider,
            onClick = { openCatSheet(app, k) },
        )
        if (kids.isNotEmpty()) {
            Column(Modifier.padding(start = 21.dp).drawBehind {
                drawLine(c.lineStrong, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx())
            }) {
                kids.forEachIndexed { i, ch -> CatNode(app, d, ch, divider = i > 0) }
            }
        }
    }
}

/** 補足(defs): 固定・変動と非日常。親から引き継いだものは薄く */
private fun catDefs(d: AppData, k: String, strong: Color, faint: Color): AnnotatedString {
    val parts = mutableListOf<Pair<String, Boolean>>()
    val fx = d.catFx(k)
    if (fx != null) parts += fx to (d.cats[k]?.fx == null)
    else parts += "固定・変動は都度指定" to true
    if (d.catSp(k) == "非日常") parts += "非日常" to (d.cats[k]?.sp == null)
    return buildAnnotatedString {
        parts.forEachIndexed { i, (t, isFaint) ->
            if (i > 0) append("、")
            withStyle(SpanStyle(color = if (isFaint) faint else strong)) { append(t) }
        }
    }
}

/* ---------------- カテゴリの編集・作成シート ---------------- */

/** 下書き(ldDraft)。編集中に変わるので state にする */
internal class LCatDraft(n: String, p: String, c: String, fx: String, sp: String) {
    var n by mutableStateOf(n)
    var p by mutableStateOf(p)
    var c by mutableStateOf(c)
    var fx by mutableStateOf(fx)
    var sp by mutableStateOf(sp)
}

private fun openCatSheet(app: AppState, key: String?) {
    val d = app.data
    val cat: Category? = key?.let { d.cats[it] }
    val draft = LCatDraft(
        n = cat?.n ?: "",
        p = cat?.p ?: "",
        c = cat?.c ?: d.colors.firstOrNull() ?: "#8B94A7",
        fx = (cat?.fx ?: "inh").let { if (it == "none") "ask" else it },
        sp = (cat?.sp ?: "inh").let { if (it == "none") "ask" else it },
    )
    app.openSheet(
        title = if (key == null) "カテゴリを作成" else "カテゴリを編集",
        mainLabel = if (key == null) "作成" else "保存",
        onMain = { saveCat(app, draft, key) },
        leftLabel = "閉じる",
        onLeft = { app.closeSheet() },
    ) {
        CatEditBody(app, draft, key)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatEditBody(app: AppState, dr: LCatDraft, key: String?) {
    val d = LocalData.current
    val c = V4.c
    val isChild = dr.p.isNotEmpty()
    val ban: Set<String> = if (key != null) d.catDesc(key) else emptySet()
    var parentOpen by remember { mutableStateOf(false) }

    val fxOpts = if (isChild) listOf("inh" to "親から", "固定" to "固定", "変動" to "変動", "ask" to "都度指定")
    else listOf("固定" to "固定", "変動" to "変動", "ask" to "都度指定")
    val spOpts = if (isChild) listOf("inh" to "親から", "日常" to "日常", "非日常" to "非日常", "ask" to "都度指定")
    else listOf("日常" to "日常", "非日常" to "非日常", "ask" to "都度指定")
    // 大分類の「親から」は保存されない(都度指定と同じに見える)
    val fxCur = if (!isChild && dr.fx == "inh") "ask" else dr.fx
    val spCur = if (!isChild && dr.sp == "inh") "ask" else dr.sp

    ListCard {
        LProp("名前") { LTextField(value = dr.n, onValue = { dr.n = it }, placeholder = "例: 外食", modifier = Modifier.fillMaxWidth()) }
        LProp("親", divider = true, chev = true, onClick = { parentOpen = true }) {
            Box {
                Text(if (dr.p.isEmpty()) "いちばん上" else d.catPath(dr.p), style = V4.t.body, color = c.tx)
                DropdownMenu(expanded = parentOpen, onDismissRequest = { parentOpen = false }) {
                    DropdownMenuItem(text = { Text("いちばん上") }, onClick = { dr.p = ""; parentOpen = false })
                    d.cats.keys.filter { it !in ban }.forEach { k ->
                        DropdownMenuItem(text = { Text(d.catPath(k)) }, onClick = { dr.p = k; parentOpen = false })
                    }
                }
            }
        }
        if (!isChild) LProp("色", divider = true) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                d.colors.forEach { hex ->
                    CatSwatch(hex, sel = dr.c == hex) { dr.c = hex }
                }
            }
        }
        LProp("固定・変動", divider = true) { LMini(fxOpts, fxCur) { dr.fx = it } }
        LProp("特別感", divider = true) { LMini(spOpts, spCur) { dr.sp = it } }
    }
    Hint("都度指定: このカテゴリは既定値を持たず、支出を入れる時に毎回選びます。")

    if (key != null) {
        val hasKids = d.childrenOf(key).isNotEmpty()
        val used = d.expenses.values.any { it.cat in ban }
        val blocked = hasKids || used
        Row(Modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(c.sunk)
                    .graphicsLayer { alpha = if (blocked) 0.45f else 1f }
                    .let { if (blocked) it else it.press(onClick = { deleteCat(app, key) }) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(V4Icons.trash, null, tint = c.tx2, modifier = Modifier.size(18.dp))
                Text("カテゴリを削除", style = plex(15, 600), color = c.tx2)
            }
        }
        if (hasKids) Hint("子カテゴリがあるので削除できません。")
        else if (used) Hint("使われているので削除できません。")
    }
}

/** 色の丸(.sw): 44の中に32の丸。選んだ色には青の輪 */
@Composable
private fun CatSwatch(hex: String, sel: Boolean, onClick: () -> Unit) {
    val c = V4.c
    Box(
        Modifier.size(44.dp).press(onClick = onClick).semantics { contentDescription = "色 $hex" },
        contentAlignment = Alignment.Center,
    ) {
        if (sel) Box(Modifier.size(40.dp).clip(CircleShape).border(2.dp, c.brand, CircleShape))
        Box(Modifier.size(32.dp).clip(CircleShape).background(colorOf(hex)).border(1.dp, c.lineStrong, CircleShape))
    }
}

private fun saveCat(app: AppState, dr: LCatDraft, key: String?) {
    val n = dr.n.trim()
    if (n.isEmpty()) { app.toast("名前を入れてください"); return }
    val root = dr.p.isEmpty()
    val k = key ?: uid("cat")
    // 大分類の「親から」は都度指定にする。子の「親から」は保存しない(引き継ぎ)
    val fx = if (dr.fx == "inh" && root) "ask" else dr.fx
    val sp = if (dr.sp == "inh" && root) "ask" else dr.sp
    val cat = Category(
        n = n,
        p = dr.p.ifEmpty { null },
        c = if (root) dr.c else null,
        fx = fx.takeIf { it != "inh" },
        sp = sp.takeIf { it != "inh" },
    )
    app.store.update(undoable = false) { d -> d.copy(cats = d.cats + (k to cat)) }
    app.closeSheet()
    app.toast("「$n」を${if (key == null) "作成" else "保存"}しました")
}

private fun deleteCat(app: AppState, key: String) {
    val d = app.data
    val name = d.cats[key]?.n ?: return
    val blocked = d.childrenOf(key).isNotEmpty() || d.expenses.values.any { it.cat in d.catDesc(key) }
    if (blocked) { app.toast("使われているので削除できません"); return }
    app.store.update { it.copy(cats = it.cats - key) }
    app.closeSheet()
    app.toast("「$name」を削除しました", undo = true)
}
