package com.amgm.personallog.v4.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.catPath
import com.amgm.personallog.v4.data.dateOf
import com.amgm.personallog.v4.data.kidActs
import com.amgm.personallog.v4.data.nextOn
import com.amgm.personallog.v4.data.payLabel
import com.amgm.personallog.v4.data.payeeOf
import com.amgm.personallog.v4.data.placeOf
import com.amgm.personallog.v4.data.roleName
import com.amgm.personallog.v4.data.timeText
import com.amgm.personallog.v4.data.xDate
import com.amgm.personallog.v4.data.xSp
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.lightBrush
import com.amgm.personallog.v4.ui.plex

/*
 * 時間軸の部品 — ui.js の axRow / momentRow / kidList / kidItem / payShell / planRowAx / dayHead / monthHeadAx / nowLine / axEdge。
 * 見た目は axis.css の最終値(/home/claude/specs/activities.md §5・§6)。上=過去・下=未来。
 * 行は3列: 時刻(52) | 軸のレール(26。中央に 1.5 の線) | 中身。AxColumn は左右の余白を持たない(カードが右 20 の余白を持つ)。
 * 使い方: AxColumn { DayHead(d); MomentRow("l1"); NowLine(app.now); PlanRowAx("e1") }
 */

private val TCOL = 52.dp
private val RAIL = 26.dp
private val INSTANT = setOf("起床", "就寝", "出発", "到着", "出来事(瞬間)")
/** 行の題名(600 15.5px/1.35) */
private val T155: TextStyle get() = plex(15, 600, 1.35).copy(fontSize = 15.5.sp)

/** 種類 → 節のアイコン(AX_NODE) */
fun nodeIcon(type: String): ImageVector = when (type) {
    "支払い" -> V4Icons.pay; "会話" -> V4Icons.talk; "移動" -> V4Icons.move; "出来事" -> V4Icons.spark; "日記" -> V4Icons.diary
    "食事", "買い物", "作業" -> V4Icons.moment; else -> V4Icons.activity
}

/** 軸の節の種類 */
enum class AxNode { DOT, INSTANT, FACT, PLAN, LATE, NONE }

/** 軸の列(.ax)。下に 24 の余白 */
@Composable
fun AxColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = 24.dp), content = content)
}

/** レールの線を背景に描く(過去=実線 --axis-past 80%、未来=破線 4/5 の --plan-ln)。from/to は高さの割合 */
fun Modifier.rail(future: Boolean, color: Color, planLn: Color, from: Float = 0f, to: Float = 1f): Modifier = drawBehind {
    val x = (TCOL + RAIL / 2).toPx()
    val w = 1.5.dp.toPx()
    val y0 = size.height * from; val y1 = size.height * to
    if (future) drawLine(planLn, Offset(x, y0), Offset(x, y1), w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())))
    else drawLine(color.copy(alpha = color.alpha * 0.8f), Offset(x, y0), Offset(x, y1), w)
}

/** 時刻の列(.ax-time): 600 12px --tx-3 右揃え、終わりは「–13:10」400 12px。day があれば「30日」+時刻 */
@Composable
private fun AxTime(time: String?, end: String?, top: Dp, day: Int? = null) {
    val c = V4.c
    Column(Modifier.width(TCOL).padding(start = 8.dp, end = 4.dp, top = top), horizontalAlignment = Alignment.End) {
        if (day != null) Text("${day}日", style = plex(12, 600, 1.0).num(), color = c.tx3, maxLines = 1, softWrap = false)
        if (!time.isNullOrEmpty()) Text(time, style = (if (day != null) plex(12, 400, 1.0) else plex(12, 600, 1.0)).num(), color = c.tx3, maxLines = 1, softWrap = false,
            modifier = Modifier.padding(top = if (day != null) 5.dp else 0.dp))
        if (end != null) Text("–$end", style = plex(12, 400, 1.0).num(), color = c.tx3, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 5.dp))
    }
}

/** 節(.ax-node): 30×20 のカプセル(青灰のグラデ+白いアイコン13)。周りに --bg の 3px の輪 */
@Composable
fun AxNodeCapsule(icon: ImageVector?) {
    val c = V4.c
    Box(
        Modifier.size(36.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(c.bg).padding(3.dp)
            .clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(c.evHi, c.evLo))).background(lightBrush()),
        contentAlignment = Alignment.Center,
    ) { if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
}

@Composable
fun AxNodeMark(node: AxNode, icon: ImageVector? = null) {
    val c = V4.c
    when (node) {
        AxNode.DOT -> AxNodeCapsule(icon)
        // 瞬間: 12×12 角丸3、--surface に 1.5 の --ev の枠、周りに --bg の輪
        AxNode.INSTANT -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(c.bg).padding(3.dp).clip(RoundedCornerShape(3.dp)).background(c.surface).border(1.5.dp, c.ev, RoundedCornerShape(3.dp)))
        // 事実の変化・関係の始まり: ひし形(12、角丸3、45度、--kin)
        AxNode.FACT -> Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).rotate(45f).clip(RoundedCornerShape(3.dp)).background(c.kinSoft).border(1.5.dp, c.kin, RoundedCornerShape(3.dp)))
        }
        // 予定: 30×20 の --bg に 2px の破線の枠(遅れは赤)
        AxNode.PLAN, AxNode.LATE -> Box(Modifier.size(36.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(c.bg).padding(3.dp).dashedBorder(if (node == AxNode.LATE) c.late else c.plan, 2.dp, 10.dp, 3.dp, 2.dp))
        AxNode.NONE -> Spacer(Modifier.size(20.dp))
    }
}

/**
 * 汎用の軸の1行(axRow)。time=時刻、timeEnd=終わり、day=日(エンティティの時間軸)、node=節、icon=節のアイコン。
 * 中身: 既定は白いカード(.ax-c: 角丸16・--tl-card・影・margin 6 20 6 4・padding 12 14)。
 * 予定(PLAN/LATE)は透明+1.5 の破線 --line-strong、瞬間・事実(INSTANT/FACT)はカードなし・細い。
 * title/meta を渡すと既定の中身。content を渡すとそれを置く。
 */
@Composable
fun AxRow(
    time: String?,
    title: String = "",
    meta: String? = null,
    node: AxNode = AxNode.DOT,
    icon: ImageVector? = null,
    future: Boolean = false,
    timeEnd: String? = null,
    day: Int? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = V4.c
    val instant = node == AxNode.INSTANT || node == AxNode.FACT
    val plan = node == AxNode.PLAN || node == AxNode.LATE
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (instant) 44.dp else 0.dp).rail(future, c.axisPast, c.planLn)
            .let { if (onClick != null && instant) it.press(onClick = onClick) else it },
    ) {
        AxTime(time, timeEnd, if (instant) 13.dp else 19.dp, day)
        Box(Modifier.width(RAIL).padding(top = if (instant) 10.dp else 11.dp), contentAlignment = Alignment.TopCenter) { AxNodeMark(node, icon) }
        val shape = RoundedCornerShape(16.dp)
        val body: @Composable ColumnScope.() -> Unit = content ?: {
            Text(title, style = if (instant) plex(14, 500, 1.4) else T155, color = if (instant || plan) c.tx2 else c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!meta.isNullOrEmpty()) Text(meta, style = plex(12, 400, 1.4), color = c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Column(
            Modifier.weight(1f).padding(start = 4.dp, end = 20.dp, top = 6.dp, bottom = 6.dp).let {
                when {
                    instant -> it.padding(start = 14.dp, end = 14.dp, top = 5.dp, bottom = 2.dp)
                    plan -> it.clip(shape).dashedBorder(c.lineStrong, 1.5.dp, 16.dp, 4.dp, 3.dp).let { m -> if (onClick != null) m.press(onClick = onClick) else m }.padding(horizontal = 14.dp, vertical = 12.dp)
                    else -> it.tlShadow(shape).clip(shape).background(c.tlCard).let { m -> if (onClick != null) m.press(onClick = onClick) else m }.padding(horizontal = 14.dp, vertical = 12.dp)
                }
            },
            content = body,
        )
    }
}

/** 補足の行(.m): 役割・場所・関わった人(ドット+名前)・遅れの札 */
@Composable
private fun MetaLine(role: String?, place: String?, people: List<String>, late: Boolean = false) {
    val c = V4.c
    if (role == null && place.isNullOrEmpty() && people.isEmpty() && !late) return
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (role != null) Text(role, style = plex(12, 600, 1.4), color = c.tx2, maxLines = 1, softWrap = false)
        if (!place.isNullOrEmpty()) Text(place, style = plex(12, 400, 1.4), color = c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (people.isNotEmpty()) Knots(people, color = c.tx3, style = plex(12, 400, 1.4))
        if (late) Tag("記録にする?", kind = TagKind.LATE)
    }
}

/** 札の面(日の札・月の札で共通)。今日=青の塗り、それ以外=--surface+1px --line */
@Composable
private fun CardFrame(today: Boolean, future: Boolean, first: Boolean, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val c = V4.c
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().rail(future, c.axisPast, c.planLn).padding(start = 8.dp, end = 8.dp, top = if (first) 20.dp else 30.dp, bottom = 10.dp)) {
        Box(
            Modifier.fillMaxWidth()
                .let { if (today) it.shadow(10.dp, shape, ambientColor = c.meFill, spotColor = c.meFill).clip(shape).background(c.meFill).background(lightBrush()) else it.clip(shape).background(c.surface).border(1.dp, c.line, shape) }
                .let { if (onClick != null) it.press(onClick = onClick) else it },
        ) { content() }
    }
}

/**
 * 日の札(dayHead)。d=日付、diary=日記の本文、sum=「自分 ¥…」の合計(支払い2件以上の日だけ渡す)、
 * empty=日記が無い時の文字(今日=「今日の日記を書く」・祝日名)、future=これからの日、first=列の最初の札。押すと日のページ(既定)。
 */
@Composable
fun DayHead(d: String, diary: String? = null, sum: Int? = null, empty: String? = null, future: Boolean = false, first: Boolean = false, onClick: (() -> Unit)? = null) {
    val app = LocalApp.current
    val c = V4.c
    val today = d == app.today
    val on = Color.White
    CardFrame(today, future, first, onClick ?: { app.go("day", d) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.size(52.dp, 56.dp).clip(RoundedCornerShape(14.dp)).background(if (today) on.copy(alpha = 0.18f) else c.dateSoft),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Text(Dates.parse(d).dayOfMonth.toString(), style = plex(24, 700, 1.0).num(), color = if (today) on else c.dateInk)
                Text(Dates.wd(d), style = plex(12, 600, 1.0), color = if (today) on else c.dateInk)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${Dates.fmtMD(d)} ${Dates.wd(d)}曜", style = plex(15, 700, 1.3).num(), color = if (today) on else c.tx, maxLines = 1, softWrap = false)
                    if (today) Text("今日", style = plex(13, 600, 1.0), color = on)
                    Spacer(Modifier.weight(1f))
                    if (sum != null && sum > 0) Text("自分 ${yen(sum)}", style = plex(13, 500, 1.0).num(), color = if (today) on.copy(alpha = 0.82f) else c.tx3, maxLines = 1, softWrap = false)
                }
                val txt = diary?.takeIf { it.isNotEmpty() } ?: empty
                if (!txt.isNullOrEmpty()) Text(txt, style = plex(13, 500, 1.4), color = if (today) on.copy(alpha = 0.82f) else c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** 月の見出し(monthHeadAx、エンティティの時間軸)。日の札と同じ札に「2026年10月」 */
@Composable
fun MonthHead(ym: String, future: Boolean = false, first: Boolean = false) {
    CardFrame(false, future, first, null) {
        Text("${ym.substring(0, 4)}年${ym.substring(5, 7).toInt()}月", style = plex(15, 700, 1.3), color = V4.c.tx, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

/** いまの線(.now): 高さ28。赤い時刻(600 12)・短い赤の印 12×2・赤い 2px の線(右 20)。上半分の軸=実線、下半分=破線 */
@Composable
fun NowLine(now: String) {
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().height(28.dp).rail(false, c.axisPast, c.planLn, 0f, 0.5f).rail(true, c.axisPast, c.planLn, 0.5f, 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(now, style = plex(12, 600, 1.0).num(), color = c.late, textAlign = TextAlign.End, modifier = Modifier.width(TCOL).padding(end = 6.dp))
        Box(Modifier.width(RAIL), contentAlignment = Alignment.Center) { Box(Modifier.size(12.dp, 2.dp).clip(RoundedCornerShape(2.dp)).background(c.late)) }
        Box(Modifier.weight(1f).padding(end = 20.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(c.late))
    }
}

/** 端の文字(.ax-edge): 高さ48以上、文字 500 13 --tx-2(past は --tx-3)。軸は端へ向かって消える */
@Composable
fun AxEdge(text: String, past: Boolean = true, chev: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).drawBehind {
            val x = (TCOL + RAIL / 2).toPx(); val w = 1.5.dp.toPx()
            val col = if (past) c.axisPast else c.planLn
            val br = if (past) Brush.verticalGradient(listOf(Color.Transparent, col)) else Brush.verticalGradient(listOf(col, Color.Transparent))
            drawLine(br, Offset(x, 0f), Offset(x, size.height), w, pathEffect = if (past) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())))
        }.let { if (onClick != null) it.press(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(TCOL + RAIL))
        Row(Modifier.padding(start = 6.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, style = plex(13, 500, 1.4), color = if (past) c.tx3 else c.tx2)
            if (chev) Icon(V4Icons.chev, null, tint = if (past) c.tx3 else c.tx2, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * 記録の1行(momentRow)。瞬間=細い行、支払い=殻(カードの中に半券)、活動=カード(補足と子の入れ子)。
 * exclude=このエンティティは補足に出さない、showDate=時刻の上に「30日」、role=行頭の役割、flat=子を出さない。
 */
@Composable
fun MomentRow(id: String, exclude: String? = null, showDate: Boolean = false, role: String? = null, future: Boolean = false, flat: Boolean = false) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val a = d.acts[id] ?: return
    val day = if (showDate) d.dateOf(id).takeIf { it.isNotEmpty() }?.let { Dates.parse(it).dayOfMonth } else null
    when {
        a.type in INSTANT -> AxRow(a.time, (role?.let { "$it · " } ?: "") + a.title, node = AxNode.INSTANT, day = day, future = future, onClick = { app.go("a", id) })
        // 支払いは殻だけ(ui.js momentRow: 支払いの行に役割は出さない)
        a.type == "支払い" -> AxRow(a.time, node = AxNode.DOT, icon = V4Icons.pay, day = day, future = future) { PayShell(id, PsMode.ROW) }
        else -> {
            val pl = a.roles.firstOrNull { it.role in setOf("支払先", "場所", "到着") }?.takeIf { it.ent == null || it.ent != exclude }?.let { d.roleName(it) }
            val ppl = a.roles.filter { it.ent != null && d.entities[it.ent] != null && it.ent != "me" && it.ent != exclude && it.role !in setOf("支払先", "場所", "到着", "出発") }.map { it.ent!! }
            val x = a.x?.let { d.expenses[it] }
            AxRow(a.time, node = AxNode.DOT, icon = nodeIcon(a.type), timeEnd = a.end, day = day, future = future, onClick = { app.go("a", id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = T155, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MetaLine(role, pl, ppl)
                    }
                    if (a.amt != null) Column(horizontalAlignment = Alignment.End) {
                        Text(yen(a.amt), style = plex(17, 700, 1.2).num(), color = c.tx)
                        if (x != null && x.amt != a.amt) Box(Modifier.padding(top = 5.dp)) { PayBar(a, x, 56.dp, 5.dp) }
                    }
                }
                if (!flat && d.kidActs(id).isNotEmpty()) Column(Modifier.padding(top = 8.dp).fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    KidList(id)
                }
            }
        }
    }
}

/** 活動の子の入れ子(kidList)。時刻の昇順(時刻なしは先頭)。支払い(自分の支出あり)=殻(kid)、それ以外=淡い面の小さい行。孫は案内線の下 */
@Composable
fun KidList(id: String, depth: Int = 0, onFill: Boolean = false) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val kids = d.kidActs(id).sortedBy { d.acts[it]?.time ?: "" }
    kids.forEach { k ->
        val p = d.acts[k]!!
        if (p.type == "支払い") {
            if (p.x != null && d.expenses[p.x] != null) PayShell(k, PsMode.KID, onFill)
            else Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).press { app.go("a", k) }.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(22.dp, 16.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(c.evHi, c.evLo))), contentAlignment = Alignment.Center) {
                    Icon(V4Icons.pay, null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
                Text(d.payLabel(p), style = plex(14, 500, 1.3), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(p.time ?: "", style = plex(12, 400, 1.0), color = c.tx3)
                Spacer(Modifier.weight(1f))
                Text(yen(p.amt ?: 0), style = plex(15, 600, 1.2).num(), color = c.tx)
            }
        } else Column(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(mix(c.me, c.surface, 0.05f)).press { app.go("a", k) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(c.sunk), contentAlignment = Alignment.Center) { Icon(nodeIcon(p.type), null, tint = c.tx2, modifier = Modifier.size(12.dp)) }
                Text(p.title.ifEmpty { p.type }, style = plex(13, 600, 1.35), color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(p.timeText(), style = plex(12, 400, 1.0).num(), color = c.tx3, maxLines = 1, softWrap = false)
            }
            if (d.kidActs(k).isNotEmpty()) Column(
                Modifier.padding(start = 14.dp).fillMaxWidth().drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(0f, size.height), 1.5.dp.toPx()) }.padding(start = 10.dp),
            ) { KidList(k, depth + 1, onFill) }
        }
    }
}

/** 色を混ぜる(color-mix(in srgb, a p, b)) */
fun mix(a: Color, b: Color, p: Float): Color = Color(a.red * p + b.red * (1 - p), a.green * p + b.green * (1 - p), a.blue * p + b.blue * (1 - p), 1f)

enum class PsMode { ROW, KID, PAY, X }

/** 支払いの見出しの行(.ps-h): 支払いの印(24 角丸8 のグラデ)+支払先+時刻+「合計 ¥」 */
@Composable
private fun PsHead(label: String, time: String?, total: Int, small: Boolean, chev: Boolean = false, wrap: Boolean = false, onClick: () -> Unit) {
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (small) 28.dp else 36.dp).press(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(c.evHi, c.evLo))).background(lightBrush()), contentAlignment = Alignment.Center) {
            Icon(V4Icons.pay, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Text(label, style = plex(if (small) 13 else 15, 600, 1.35), color = c.tx, maxLines = if (wrap) 3 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (time != null) Text(time, style = plex(12, 400, 1.0).num(), color = c.tx3, maxLines = 1, softWrap = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("合計 ", style = plex(13, 500, 1.2), color = c.tx2, maxLines = 1, softWrap = false)
            Text(yen(total), style = plex(13, 600, 1.2).num(), color = c.tx2, maxLines = 1, softWrap = false)
        }
        if (chev) Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
    }
}

/**
 * 支払いの殻(payShell)。ROW=タイムラインの行(白いカードの中) / KID=活動の中(淡い青の面) / PAY=支払い詳細の下端(半券だけ・›) /
 * X=支出詳細の最上部(白い面: 支払いの1行+大きな青い半券)。onFill=true は塗りの主役の上(白い殻で包む)。
 */
@Composable
fun PayShell(pid: String, mode: PsMode = PsMode.ROW, onFill: Boolean = false) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val p = d.acts[pid] ?: return
    val x = p.x?.let { d.expenses[it] }
    val total = p.amt ?: 0
    when (mode) {
        PsMode.ROW -> Column(Modifier.fillMaxWidth()) {
            PsHead(d.payLabel(p), null, total, small = false) { app.go("a", pid) }
            if (x != null) Box(Modifier.padding(top = 8.dp)) { PsTicket(pid, TicketSize.SM) }
        }
        PsMode.KID -> Column(
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(if (onFill) 18.dp else 14.dp))
                .background(if (onFill) c.surface else mix(c.me, c.surface, 0.05f))
                .padding(horizontal = if (onFill) 8.dp else 10.dp, vertical = if (onFill) 6.dp else 8.dp),
        ) {
            PsHead(d.payeeOf(p).ifEmpty { "支払い" }, p.time, total, small = true) { app.go("a", pid) }
            if (x != null) Box(Modifier.padding(top = 6.dp)) { PsTicket(pid, TicketSize.SM) }
        }
        PsMode.PAY -> if (x != null) Box(
            Modifier.padding(top = 16.dp).fillMaxWidth().let { if (onFill) it.e1(RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(c.surface).padding(6.dp) else it },
        ) { PsTicket(pid, TicketSize.NORMAL, chev = true) }
        PsMode.X -> if (x != null) {
            val pct = if (total > 0) Math.round(x.amt * 100.0 / total).toInt() else 100
            val owed = p.adv?.state == "未精算"
            val shape = RoundedCornerShape(20.dp)
            Column(Modifier.fillMaxWidth().tlShadow(shape).clip(shape).background(c.tlCard).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)) {
                PsHead("${d.payLabel(p)} · ${Dates.fmtMDW(d.xDate(x))}${p.time?.let { " $it" } ?: ""}", null, total, small = true, chev = true, wrap = true) { app.go("a", pid) }
                Box(Modifier.padding(top = 10.dp)) {
                    Ticket(
                        t = yen(x.amt), stub = { SpStub(d.xSp(x), TicketSize.LG) }, size = TicketSize.LG, fill = true, label = "自分の支出 ${yen(x.amt)}",
                        s = {
                            Text("支払い ${yen(total)} の $pct%")
                            if (owed) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("未精算", style = plex(12, 500), color = Color.White)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 支払いの中の自分の支出の半券(psTk): 題名「自分の支出」、補足=カテゴリの道+割り勘の棒、金額。押すと自分の支出 */
@Composable
fun PsTicket(pid: String, size: TicketSize = TicketSize.SM, chev: Boolean = false) {
    val app = LocalApp.current
    val d = LocalData.current
    val p = d.acts[pid] ?: return
    val xid = p.x ?: return
    val x = d.expenses[xid] ?: return
    Ticket(
        t = "自分の支出", stub = { SpStub(d.xSp(x), size) }, amt = yen(x.amt), size = size, chev = chev,
        s = {
            // カテゴリは切らずに折り返す(.ps-cat)。割り勘の棒はその右
            Text(d.catPath(x.cat))
            if (x.amt != (p.amt ?: x.amt)) PayBar(p, x, width = if (size == TicketSize.SM) 56.dp else 64.dp, height = if (size == TicketSize.SM) 5.dp else 6.dp)
        },
        onClick = { app.go("x", xid) },
    )
}

/** 予定の行(planRowAx)。破線の中抜き。late=過ぎて未記録(節が赤・「記録にする?」)。支払い予定は金額+「支払い予定」 */
@Composable
fun PlanRowAx(id: String, future: Boolean = true, late: Boolean = false, showDate: Boolean = false, exclude: String? = null) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val e = d.plans[id] ?: return
    val od = (if (e.rep != null) nextOn(e, app.today) else e.d) ?: e.d
    val day = if (showDate && od != null) Dates.parse(od).dayOfMonth else null
    AxRow(
        if (e.all) "終日" else e.s, node = if (late) AxNode.LATE else AxNode.PLAN, future = future, day = day,
        onClick = { app.go(if (e.isTodo) "task" else "ev", id) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(e.t, style = T155, color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val pl = d.placeOf(e).takeIf { e.place?.ent == null || e.place.ent != exclude }
                MetaLine(null, pl, e.who.filter { d.entities[it] != null && it != exclude }, late)
            }
            if (e.pay != null) Column(horizontalAlignment = Alignment.End) {
                Text(yen(e.pay.amt), style = plex(17, 700, 1.2).num(), color = c.tx)
                Text("支払い予定", style = plex(12, 400, 1.4), color = c.tx3, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
