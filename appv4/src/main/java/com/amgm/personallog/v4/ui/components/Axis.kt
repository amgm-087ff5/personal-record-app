package com.amgm.personallog.v4.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.catPath
import com.amgm.personallog.v4.data.dateOf
import com.amgm.personallog.v4.data.isInstant
import com.amgm.personallog.v4.data.kidActs
import com.amgm.personallog.v4.data.payLabel
import com.amgm.personallog.v4.data.payeeOf
import com.amgm.personallog.v4.data.placeOf
import com.amgm.personallog.v4.data.roleName
import com.amgm.personallog.v4.data.xSp
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.plex

/*
 * 時間軸の部品 — ui.js の axRow / momentRow / kidList / payShell / planRowAx / dayHead / monthHeadAx / nowLine / axEdge。
 * 見た目は /home/claude/specs/activities.md §5・§6(axis.css の最終値)。上=過去・下=未来。
 * 使い方: AxColumn { DayHead(...); MomentRow("l1"); NowLine(app.now); PlanRowAx("e1") }
 * 署名(関数名・引数)は全画面が使うので変えない。中身(見た目)は本体が仕様に合わせて直す。
 */

/** 軸の節の種類 */
enum class AxNode { DOT, INSTANT, FACT, PLAN, LATE, NONE }

/** 軸の列(.ax)。中の行の左に時刻の列(幅52)と縦の線 */
@Composable
fun AxColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter), content = content)
}

/** 汎用の軸の1行。time=左の時刻、node=軸の節、title/meta=本文。content を渡すと本文の代わりに置く */
@Composable
fun AxRow(
    time: String?,
    title: String = "",
    meta: String? = null,
    node: AxNode = AxNode.DOT,
    future: Boolean = false,
    timeEnd: String? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = V4.c
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).let { if (onClick != null) it.press(onClick = onClick) else it }) {
        Column(Modifier.width(44.dp).padding(top = 14.dp)) {
            Text(time ?: "", style = plex(13, 500).num(), color = c.tx2)
            if (timeEnd != null) Text("–$timeEnd", style = plex(12, 400).num(), color = c.tx3)
        }
        Box(Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(1.5.dp).fillMaxHeight().background(if (future) c.planLn else c.axisPast))
            AxNodeMark(node, Modifier.padding(top = 18.dp))
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
            if (content != null) content() else {
                Text(title, style = plex(15, 500, 1.4), color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!meta.isNullOrEmpty()) Text(meta, style = V4.t.cap, color = c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun AxNodeMark(node: AxNode, modifier: Modifier = Modifier) {
    val c = V4.c
    when (node) {
        AxNode.DOT -> Box(modifier.size(10.dp).clip(CircleShape).background(c.ev))
        AxNode.INSTANT -> Box(modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c.ev))
        AxNode.FACT -> Box(modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).border(1.5.dp, c.kin, RoundedCornerShape(2.dp)))
        AxNode.PLAN -> Box(modifier.size(10.dp).clip(CircleShape).border(1.5.dp, c.plan, CircleShape).background(c.bg))
        AxNode.LATE -> Box(modifier.size(10.dp).clip(CircleShape).border(1.5.dp, c.late, CircleShape).background(c.bg))
        AxNode.NONE -> Spacer(modifier.size(10.dp))
    }
}

/**
 * 日の札(dayHead)。d=日付、diary=日記の本文(1行)、sum=その日の支払いの合計(2件以上の時だけ渡す)、
 * empty=予定・記録の無い日の短い文言(祝日名など)、future=これからの日。押すと日のページ(既定)。
 */
@Composable
fun DayHead(d: String, diary: String? = null, sum: Int? = null, empty: String? = null, future: Boolean = false, onClick: (() -> Unit)? = null) {
    val app = LocalApp.current
    val c = V4.c
    val today = d == app.today
    Row(
        Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 10.dp).clip(RoundedCornerShape(14.dp))
            .background(if (today) c.meFill else c.dcardBg).clickable { (onClick ?: { app.go("day", d) })() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (today) Color.White.copy(alpha = 0.18f) else c.dateSoft), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(Dates.parse(d).dayOfMonth.toString(), style = plex(18, 700, 1.0).num(), color = if (today) Color.White else c.dateInk)
            Text(Dates.wd(d), style = plex(12, 500, 1.0), color = if (today) Color.White else c.dateInk)
        }
        Column(Modifier.weight(1f)) {
            Text(Dates.fmtMD(d) + if (today) " 今日" else "", style = plex(15, 600), color = if (today) Color.White else c.tx)
            val sub = diary?.takeIf { it.isNotEmpty() } ?: empty
            if (sub != null) Text(sub, style = V4.t.cap, color = if (today) Color.White.copy(alpha = 0.85f) else c.tx3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sum != null) Text(yen(sum), style = plex(13, 600).num(), color = if (today) Color.White else c.tx2)
    }
}

/** 月の見出し(エンティティの時間軸)。「2026年10月」 */
@Composable
fun MonthHead(ym: String, future: Boolean = false) {
    val c = V4.c
    Text("${ym.substring(0, 4)}年${ym.substring(5, 7).toInt()}月", style = plex(15, 700), color = if (future) c.tx2 else c.tx,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
}

/** いまの線(赤い横線+赤い時刻) */
@Composable
fun NowLine(now: String) {
    val c = V4.c
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(now, style = plex(12, 600).num(), color = c.now, modifier = Modifier.width(44.dp))
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(8.dp).clip(CircleShape).background(c.now)) }
        Box(Modifier.weight(1f).height(1.5.dp).background(c.now))
    }
}

/** 軸の端の文字(「これより前を見る ›」「これより先の予定 ›」「記録 なし」) */
@Composable
fun AxEdge(text: String, past: Boolean = true, onClick: (() -> Unit)? = null) {
    val c = V4.c
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(64.dp))
        Text(text, style = V4.t.label, color = if (onClick != null) c.brand else c.tx3)
    }
}

/**
 * 記録の1行(momentRow)。種類で出し分ける: 期間の活動=カード(中に子の入れ子)、瞬間=細い行、支払い=殻。
 * exclude=このエンティティの名前は出さない、showDate=時刻の上に日付、role=役割(エンティティの時間軸の行頭)。
 */
@Composable
fun MomentRow(id: String, exclude: String? = null, showDate: Boolean = false, role: String? = null) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val a = d.acts[id] ?: return
    val time = (if (showDate) Dates.md(d.dateOf(id)) + " " else "") + (a.time ?: "")
    when {
        a.type == "支払い" -> AxRow(time, node = AxNode.INSTANT) { PayShell(id, PsMode.ROW) }
        a.isInstant() -> AxRow(time, (role?.let { "$it · " } ?: "") + a.title, node = AxNode.INSTANT, onClick = { app.go("a", id) })
        else -> AxRow(time, node = AxNode.DOT, timeEnd = a.end, onClick = { app.go("a", id) }) {
            Column(Modifier.fillMaxWidth().tlShadow(RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(c.tlCard).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(V4Icons.actIcon(a.type), null, tint = c.tx3, modifier = Modifier.size(14.dp))
                    Text((role?.let { "$it · " } ?: "") + a.title, style = plex(15, 600, 1.35), color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val who = a.roles.filter { it.ent != null && it.ent != exclude && it.ent != "me" && it.role !in setOf("支払先", "支払者") }.map { it.ent!! }
                if (who.isNotEmpty()) Box(Modifier.padding(top = 4.dp)) { Knots(who) }
                KidList(id, depth = 0)
            }
        }
    }
}

/** 活動の子の入れ子(kidList)。支払い=殻(kid)、ほかの活動=小さい行。孫は案内線の下に字下げ */
@Composable
fun KidList(id: String, depth: Int = 0) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val kids = d.kidActs(id).sortedBy { d.acts[it]?.time ?: "" }
    if (kids.isEmpty()) return
    Column(Modifier.padding(start = (depth * 12).dp)) {
        kids.forEach { k ->
            val a = d.acts[k]!!
            if (a.type == "支払い") PayShell(k, PsMode.KID)
            else {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { app.go("a", k) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(V4Icons.actIcon(a.type), null, tint = c.tx3, modifier = Modifier.size(14.dp))
                    Text(a.time ?: "", style = plex(12, 400).num(), color = c.tx3)
                    Text(a.title, style = plex(14, 500), color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                if (depth < 1) KidList(k, depth + 1)
            }
        }
    }
}

enum class PsMode { ROW, KID, PAY, X }

/** 支払いの殻(payShell)。ROW=タイムラインの行 / KID=活動の中 / PAY=支払い詳細の下端 / X=支出詳細の最上部 */
@Composable
fun PayShell(pid: String, mode: PsMode = PsMode.ROW) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val p = d.acts[pid] ?: return
    val x = p.x?.let { d.expenses[it] }
    Column(Modifier.fillMaxWidth().let { if (mode == PsMode.KID) it.padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(c.meSoft.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 8.dp) else it }) {
        if (mode != PsMode.PAY) Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).clickable { app.go("a", pid) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(c.ev), contentAlignment = Alignment.Center) { Icon(V4Icons.pay, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
            Text(if (mode == PsMode.KID) d.payeeOf(p).ifEmpty { "支払い" } else d.payLabel(p), style = plex(15, 600), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("合計 " + yen(p.amt ?: 0), style = plex(13, 500).num(), color = c.tx2)
        }
        if (x != null) Box(Modifier.padding(top = 8.dp)) { PsTicket(pid, size = if (mode == PsMode.X) TicketSize.LG else if (mode == PsMode.PAY) TicketSize.NORMAL else TicketSize.SM) }
    }
}

/** 支払いの中の自分の支出の半券(psTk)。押すと自分の支出 */
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
            Text(d.catPath(x.cat))
            if (x.amt != (p.amt ?: x.amt)) PayBar(p, x, width = if (size == TicketSize.SM) 56.dp else 64.dp, height = if (size == TicketSize.SM) 5.dp else 6.dp)
        },
        onClick = { app.go("x", xid) },
    )
}

/** 予定の行(planRowAx)。点線・中抜き。late=過ぎて未記録(赤) */
@Composable
fun PlanRowAx(id: String, future: Boolean = true, late: Boolean = false, showDate: Boolean = false, exclude: String? = null) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val e = d.plans[id] ?: return
    val time = (if (showDate && e.d != null) Dates.md(e.d) + " " else "") + (if (e.all) "終日" else (e.s ?: ""))
    AxRow(time, node = if (late) AxNode.LATE else AxNode.PLAN, future = future, timeEnd = if (e.all) null else e.e, onClick = { app.go(if (e.isTodo) "task" else "ev", id) }) {
        Column(Modifier.fillMaxWidth().dashedBorder(if (late) c.late else c.plan, radius = 14.dp).padding(12.dp)) {
            Text(e.t, style = plex(15, 500), color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val pl = d.placeOf(e)
            if (pl.isNotEmpty()) Text(pl, style = V4.t.cap, color = c.tx3, maxLines = 1)
            val who = e.who.filter { it != exclude }
            if (who.isNotEmpty()) Box(Modifier.padding(top = 4.dp)) { Knots(who) }
            if (late) Box(Modifier.padding(top = 6.dp)) { Tag("記録にする?", kind = TagKind.LATE) }
        }
    }
}

@Suppress("unused") private fun unusedRole(d: com.amgm.personallog.v4.data.AppData, r: com.amgm.personallog.v4.data.Role) = d.roleName(r)
