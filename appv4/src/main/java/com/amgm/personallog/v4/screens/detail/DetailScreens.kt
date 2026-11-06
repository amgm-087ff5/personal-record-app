package com.amgm.personallog.v4.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.Activity
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.PayItem
import com.amgm.personallog.v4.data.Role
import com.amgm.personallog.v4.data.amount
import com.amgm.personallog.v4.data.ancIds
import com.amgm.personallog.v4.data.dateOf
import com.amgm.personallog.v4.data.isInstant
import com.amgm.personallog.v4.data.isSpan
import com.amgm.personallog.v4.data.kidActs
import com.amgm.personallog.v4.data.payLabel
import com.amgm.personallog.v4.data.payeeOf
import com.amgm.personallog.v4.data.roleName
import com.amgm.personallog.v4.data.timeText
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.screens.create.openAddKidSheet
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.Route
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.lightBrush
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.components.AppBar
import com.amgm.personallog.v4.ui.components.BarAction
import com.amgm.personallog.v4.ui.components.DayHead
import com.amgm.personallog.v4.ui.components.ERef
import com.amgm.personallog.v4.ui.components.Empty
import com.amgm.personallog.v4.ui.components.Divider
import com.amgm.personallog.v4.ui.components.ListCard
import com.amgm.personallog.v4.ui.components.ObjCard
import com.amgm.personallog.v4.ui.components.Objs
import com.amgm.personallog.v4.ui.components.Prop
import com.amgm.personallog.v4.ui.components.PsTicket
import com.amgm.personallog.v4.ui.components.Screen
import com.amgm.personallog.v4.ui.components.SectionHead
import com.amgm.personallog.v4.ui.components.SplitBar
import com.amgm.personallog.v4.ui.components.SplitSeg
import com.amgm.personallog.v4.ui.components.Suggest
import com.amgm.personallog.v4.ui.components.Tag
import com.amgm.personallog.v4.ui.components.TicketSize
import com.amgm.personallog.v4.ui.components.V4Row
import com.amgm.personallog.v4.ui.components.VLine
import com.amgm.personallog.v4.ui.components.dashedBorder
import com.amgm.personallog.v4.ui.components.e1
import com.amgm.personallog.v4.ui.components.hatch
import com.amgm.personallog.v4.ui.components.num
import com.amgm.personallog.v4.ui.components.press
import com.amgm.personallog.v4.ui.components.tlShadow
import com.amgm.personallog.v4.ui.components.SmallButton
import com.amgm.personallog.v4.ui.components.GhostButton
import com.amgm.personallog.v4.ui.components.Ico

/*
 * 出来事の詳細(#/a/{id})と品目の詳細(#/i/{支払い}/{番号})— activities.js の route('a') / route('i')。
 * 寸法・文言は /home/claude/specs/detail.md。操作とシートは DetailSheets.kt。
 */

/** 開いている品目の内訳(画面を離れても残る。キーは 支払いの id:グループ番号) */
private val openGroups = mutableStateMapOf<String, Boolean>()

/* ---------------- 出来事の詳細 ---------------- */

@Composable
fun ActivityDetailScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val id = r.id.orEmpty()
    val a = d.acts[id]
    if (a == null) {
        Screen(topBar = { AppBar("") }) { Empty(V4Icons.activity, "記録 なし") }
        return
    }
    // 日記は日のページへ置き換える(モックの location.replace と同じ)
    if (a.type == "日記") {
        LaunchedEffect(id) { app.replace(Route("day", listOf(a.date.orEmpty()))) }
        return
    }
    val day = d.dateOf(id)
    val ancs = d.ancIds(id)
    val menu = BarAction(V4Icons.more, "その他の操作") { openActMenu(app, id) }
    Screen(topBar = { AppBar("", actions = listOf(menu)) }) {
        when {
            a.type == "支払い" -> PayBody(id, a, day, ancs)
            a.isSpan() -> SpanBody(id, a, day, ancs)
            else -> InstantBody(id, a, day, ancs)
        }
    }
}

@Composable
private fun SpanBody(id: String, a: Activity, day: String, ancs: List<String>) {
    val d = LocalData.current
    PinFrame(day, instant = false, ancs = ancs) { SpanCap(id, a) }
    val roles = if (a.type == "移動") a.roles.filter { it.role !in setOf("出発", "到着") } else a.roles
    RolesSection(roles)
    BodyText(a)
    OrigPlan(id)
}

@Composable
private fun InstantBody(id: String, a: Activity, day: String, ancs: List<String>) {
    val c = V4.c
    val shape = RoundedCornerShape(18.dp)
    PinFrame(day, instant = true, ancs = ancs) {
        Column(
            Modifier.fillMaxWidth().tlShadow(shape).clip(shape).background(c.tlCard)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(a.time ?: "", style = plex(32, 700, 1.0).num(), color = c.tx)
            Text(a.title, style = plex(15, 600, 1.3), color = c.tx2)
        }
    }
    OrigPlan(id)
}

@Composable
private fun PayBody(id: String, a: Activity, day: String, ancs: List<String>) {
    val app = LocalApp.current
    val d = LocalData.current
    val pt = partnerOf(d, a)
    PinFrame(day, instant = false, ancs = ancs) { PayCap(id, a) }
    val adv = a.adv
    if (adv != null && adv.state == "未精算") OweBand(id, adv.amt, pt)
    ShopSuggest(id, a)
    val payee = a.roles.firstOrNull { it.role == "支払先" }
    val shopAct = payee != null && payee.ent == null && payee.name != null
    RolesSection(payRoles(d, a), onShop = if (shopAct) ({ registerShop(app, id) }) else null)
    val groups = groupItems(a.items)
    SectionHead("品目", n = groups.size.toString(), actLabel = "追加", actIcon = V4Icons.plus, onAct = { openItemSheet(app, id, null) })
    if (groups.isNotEmpty()) ListCard {
        groups.forEachIndexed { gi, g -> ItemGroupRow(id, a, g, gi, divider = gi > 0) }
    }
    if (pt != null && a.items.isNotEmpty()) {
        SectionHead("負担")
        BurdenRow(id, a, pt)
    }
    OrigPlan(id)
}

/* ---------------- 共通の骨格(軸・祖先の枠・日の札) ---------------- */

/** 日の札 → 軸と節 → 祖先の枠 → 主役。.pin の寸法 */
@Composable
private fun PinFrame(day: String, instant: Boolean, ancs: List<String>, cap: @Composable () -> Unit) {
    val c = V4.c
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = V4Dim.gutter)) { DayHead(day) }
        Row(Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, bottom = 22.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(36.dp).fillMaxHeight()) {
                VLine(c.axisPast, modifier = Modifier.offset(x = 16.25.dp))
                // 節(中心は軸の上 x=17dp)。瞬間は小さい四角、期間は角丸の薬の形
                if (instant) {
                    Box(Modifier.offset(x = 6.dp, y = 23.dp).size(22.dp).clip(RoundedCornerShape(6.dp)).background(c.bg))
                    Box(Modifier.offset(x = 10.dp, y = 27.dp).size(14.dp).clip(RoundedCornerShape(4.dp)).background(c.surface).border(1.5.dp, c.ev, RoundedCornerShape(4.dp)))
                } else {
                    Box(Modifier.offset(x = -2.dp, y = 14.dp).size(38.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(c.bg))
                    Box(Modifier.offset(x = 2.dp, y = 18.dp).size(30.dp, 18.dp).clip(RoundedCornerShape(9.dp)).background(c.ev).background(lightBrush()))
                    Box(Modifier.offset(x = 32.dp, y = 26.dp).size(6.dp, 2.dp).background(c.ev))
                }
            }
            Column(Modifier.weight(1f)) { AncFrames(ancs, 0, cap) }
        }
    }
}

/** 祖先を外側から順に枠で包む(子の詳細で親が白い枠で包む。入れ子) */
@Composable
private fun AncFrames(ancs: List<String>, i: Int, cap: @Composable () -> Unit) {
    if (i >= ancs.size) {
        cap()
        return
    }
    AncFrame(ancs[i], outer = i == 0) { AncFrames(ancs, i + 1, cap) }
}

@Composable
private fun AncFrame(key: String, outer: Boolean, body: @Composable () -> Unit) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val a = d.acts[key] ?: return
    val shape = RoundedCornerShape(20.dp)
    val m = if (outer) Modifier.fillMaxWidth().tlShadow(shape).clip(shape).background(c.tlCard)
    else Modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.line, shape)
    val pad = if (outer) PaddingValues(10.dp) else PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp)
    Column(m.padding(pad)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { app.go("a", key) }.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(c.sunk), contentAlignment = Alignment.Center) {
                Icon(V4Icons.actIcon(a.type), null, tint = c.tx2, modifier = Modifier.size(15.dp))
            }
            Text(a.timeText(), style = plex(13, 600).num(), color = c.tx2)
            Text(a.title, style = plex(14, 600, 1.3), color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) { body() }
    }
}

/* ---------------- 主役(塗りのカプセル) ---------------- */

private fun durText(a: Activity): String {
    val e = a.end ?: return ""
    val t = a.time ?: return ""
    var m = Dates.minutes(e) - Dates.minutes(t)
    if (m < 0) m += 24 * 60
    val h = m / 60
    val mm = m % 60
    return when {
        h > 0 && mm > 0 -> "${h}時間${mm}分"
        h > 0 -> "${h}時間"
        else -> "${mm}分"
    }
}

/** 主役の塗り(--ev の斜め + 左上の光) */
private fun capModifier(c: com.amgm.personallog.v4.ui.V4Colors): Modifier {
    val shape = RoundedCornerShape(30.dp)
    return Modifier.fillMaxWidth().shadow(16.dp, shape, ambientColor = c.evLo.copy(alpha = 0.5f), spotColor = c.evLo.copy(alpha = 0.5f))
        .clip(shape).background(Brush.linearGradient(listOf(c.evHi, c.evLo))).background(lightBrush())
}

@Composable
private fun SpanCap(id: String, a: Activity) {
    val c = V4.c
    val isMove = a.type == "移動"
    Column(capModifier(c).padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 22.dp)) {
        KindRow(
            icon = V4Icons.actIcon(a.type), label = a.type,
            right = if (isMove) durText(a) else a.timeText(),
            small = if (isMove) null else durText(a).ifEmpty { null },
        )
        Text(a.title, style = plex(24, 600, 1.25), color = c.onCap, modifier = Modifier.padding(top = 12.dp))
        if (isMove) MoveRoute(a)
        CapKids(id)
    }
}

@Composable
private fun PayCap(id: String, a: Activity) {
    val d = LocalData.current
    val c = V4.c
    val x = a.x?.let { d.expenses[it] }
    val total = a.amt ?: 0
    val mine = x?.amt ?: 0
    val rest = total - mine
    val pt = partnerOf(d, a)
    val adv = a.adv
    val segs = buildList {
        if (x != null) add(SplitSeg(mine, "me", "自分の支出", noAmt = true))
        if (rest > 0) {
            if (adv != null) add(SplitSeg(rest, if (adv.state == "未精算") "owed" else "other", "${pt ?: "相手"}さんの分・${adv.state}"))
            else add(SplitSeg(rest, "other", "ほかの人の分"))
        }
    }
    Column(capModifier(c).padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 22.dp)) {
        KindRow(icon = V4Icons.pay, label = "支払い", right = a.time ?: "")
        Text(d.payeeOf(a).ifEmpty { a.title }, style = plex(24, 600, 1.25), color = c.onCap, modifier = Modifier.padding(top = 12.dp))
        if (a.amt != null) Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("合計", style = plex(15, 500, 1.3), color = c.onCap)
            Text(yen(a.amt), style = plex(17, 700, 1.2).num(), color = c.onCap)
        }
        if (a.pay != null) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(V4Icons.pay, null, tint = c.onCap, modifier = Modifier.size(15.dp))
            Text(a.pay, style = V4.t.label, color = c.onCap)
        }
        if (segs.isNotEmpty()) SplitBar(segs, onFill = true)
        if (x != null) {
            val shape = RoundedCornerShape(20.dp)
            Box(Modifier.padding(top = 16.dp).fillMaxWidth().e1(shape).clip(shape).background(c.surface).padding(6.dp)) {
                PsTicket(id, TicketSize.NORMAL, chev = true)
            }
        }
    }
}

/** 種類の札(.lbl)と時刻(.tm)の行 */
@Composable
private fun KindRow(icon: ImageVector, label: String, right: String, small: String? = null) {
    val c = V4.c
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.height(28.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = c.onCap, modifier = Modifier.size(16.dp))
            Text(label, style = plex(13, 600), color = c.onCap)
        }
        Spacer(Modifier.weight(1f))
        Text(right, style = plex(14, 600).num(), color = c.onCap)
        if (small != null) Text(small, style = plex(12, 400).num(), color = c.onCap.copy(alpha = 0.9f), modifier = Modifier.padding(start = 6.dp))
    }
}

/** 移動の出発・到着 */
@Composable
private fun MoveRoute(a: Activity) {
    val d = LocalData.current
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RouteStop(d, "出発", a.roles.firstOrNull { it.role == "出発" }, a.time)
        RouteStop(d, "到着", a.roles.firstOrNull { it.role == "到着" }, a.end)
    }
}

@Composable
private fun RouteStop(d: AppData, label: String, r: Role?, time: String?) {
    val app = LocalApp.current
    val c = V4.c
    if (r == null) return
    val ent = r.ent
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = plex(12, 500), color = c.onCap.copy(alpha = 0.85f))
        Text(
            d.roleName(r), style = plex(16, 600, 1.4).copy(textDecoration = TextDecoration.Underline), color = c.onCap,
            modifier = if (ent != null) Modifier.clickable { app.go("e", ent) } else Modifier,
        )
        Text(time ?: "", style = plex(16, 700).num(), color = c.onCap)
    }
}

/* ---------------- 子の並び(主役の中) ---------------- */

@Composable
private fun CapKids(id: String) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val kids = d.kidActs(id).sortedBy { d.acts[it]?.time ?: "99:99" }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        kids.forEach { CkKid(it, 1) }
        Box(
            Modifier.fillMaxWidth().padding(top = 2.dp).heightIn(min = 44.dp)
                .dashedBorder(c.onCap.copy(alpha = 0.6f), width = 1.dp, radius = 14.dp)
                .clickable { openAddKidSheet(app, id) },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(V4Icons.plus, null, tint = c.onCap, modifier = Modifier.size(16.dp))
                Text("アクティビティを追加", style = plex(14, 600), color = c.onCap)
            }
        }
        // 支払いが2件以上で、自分の支出があれば合計
        val pays = kids.mapNotNull { d.acts[it] }.filter { it.type == "支払い" }
        val sum = pays.sumOf { p -> p.x?.let { d.expenses[it]?.amt } ?: 0 }
        if (pays.size >= 2 && sum > 0) Row(Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("自分の支出 合計", style = plex(14, 500, 1.3), color = c.onCap)
            Text(yen(sum), style = plex(14, 600, 1.3).num(), color = c.onCap)
        }
    }
}

/** 子1つ。depth は 1 から。深さ3は孫を出さず、件数だけ出す */
@Composable
private fun CkKid(k: String, depth: Int) {
    val d = LocalData.current
    val a = d.acts[k] ?: return
    val kids = d.kidActs(k).sortedBy { d.acts[it]?.time ?: "99:99" }
    if (a.type == "支払い") KidPay(k) else CkRow(k, showN = depth >= 3 && kids.isNotEmpty())
    if (depth < 3 && kids.isNotEmpty() && a.type != "支払い") {
        Row(Modifier.padding(start = 22.dp, top = 6.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.padding(start = 4.dp).width(1.5.dp).fillMaxHeight().background(V4.c.onCap.copy(alpha = 0.5f)))
            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                kids.forEach { CkKid(it, depth + 1) }
            }
        }
    }
}

@Composable
private fun CkRow(k: String, showN: Boolean) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val a = d.acts[k] ?: return
    val inst = a.isInstant()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).e1(RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(c.surface)
            .clickable { app.go("a", k) }
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(c.sunk), contentAlignment = Alignment.Center) {
            Icon(if (inst) V4Icons.spark else V4Icons.actIcon(a.type), null, tint = c.tx2, modifier = Modifier.size(15.dp))
        }
        Text(a.timeText(), style = plex(13, 600).num(), color = c.tx2)
        Text(a.title, style = plex(15, 500, 1.3), color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (showN) Text("${d.kidActs(k).size}件", style = plex(12, 600), color = c.tx2,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.sunk).padding(horizontal = 8.dp, vertical = 4.dp))
        if (!inst) Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
    }
}

/** 支払いの子(活動の中の殻)。押すと支払いの詳細、半券は支出の詳細 */
@Composable
private fun KidPay(k: String) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val p = d.acts[k] ?: return
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().e1(shape).clip(shape).background(c.surface).padding(6.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 28.dp).clickable { app.go("a", k) }.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(c.ev), contentAlignment = Alignment.Center) {
                Icon(V4Icons.pay, null, tint = c.onCap, modifier = Modifier.size(12.dp))
            }
            Text(d.payeeOf(p).ifEmpty { "支払い" }, style = plex(15, 600), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (p.time != null) Text(p.time, style = plex(12, 400).num(), color = c.tx3)
            Text("合計 " + yen(p.amt ?: 0), style = plex(13, 500).num(), color = c.tx2)
        }
        Box(Modifier.padding(top = 6.dp)) { PsTicket(k, TicketSize.SM) }
    }
}

/* ---------------- 本文・関わり・元の予定 ---------------- */

private fun payRoles(d: AppData, a: Activity): List<Role> {
    val parent = a.parent?.let { d.acts[it] }
    val extra = (parent?.roles ?: emptyList()).filter { r ->
        r.role !in setOf("場所", "出発", "到着", "支払先", "支払者") && r.ent != "me" &&
            a.roles.none { o -> o.ent != null && o.ent == r.ent }
    }
    return a.roles + extra
}

@Composable
private fun RolesSection(roles: List<Role>, onShop: (() -> Unit)? = null) {
    if (roles.isEmpty()) return
    SectionHead("関わったエンティティ", n = roles.size.toString(), actLabel = if (onShop != null) "支払先を店として登録" else null, onAct = onShop)
    ListCard {
        roles.forEachIndexed { i, r -> Prop(k = r.role, divider = i > 0, value = { ERef(r.ent, r.name) }) }
    }
}

@Composable
private fun BodyText(a: Activity) {
    val c = V4.c
    val body = a.body
    if (body.isNullOrEmpty()) return
    SectionHead("内容")
    ListCard {
        Text(body, style = plex(15, 400, 1.85), color = c.tx, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

@Composable
private fun OrigPlan(id: String) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val (key, p) = d.plans.entries.firstOrNull { it.value.rec == id }?.let { it.key to it.value } ?: return
    SectionHead("元の予定")
    Objs {
        ObjCard(onClick = { app.go("ev", key) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(22.dp, 16.dp).dashedBorder(c.plan, radius = 6.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.t, style = plex(15, 500, 1.4), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.d?.let { Dates.fmtMDW(it) } ?: ""} ${p.s ?: ""}".trim(), style = V4.t.cap, color = c.tx3)
                }
                Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/* ---------------- 支払いの補助(未精算・買い物リスト) ---------------- */

@Composable
private fun OweBand(id: String, amt: Int, pt: String?) {
    val app = LocalApp.current
    val c = V4.c
    Row(
        Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(c.oweSoft).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp, 10.dp).clip(RoundedCornerShape(3.dp)).hatch(c.owe, 3.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${pt ?: "相手"}さんの分 ", style = plex(15, 500), color = c.tx)
                Text(yen(amt), style = plex(15, 700).num(), color = c.tx)
            }
            Text("未精算", style = V4.t.cap, color = c.tx2, modifier = Modifier.padding(top = 2.dp))
        }
        SmallButton("精算済みにする") { settleAct(app, id) }
    }
}

@Composable
private fun ShopSuggest(id: String, a: Activity) {
    val app = LocalApp.current
    val d = LocalData.current
    val hits = shopTodos(d, a)
    if (hits.isEmpty()) return
    val text = "買い物リストの " + hits.joinToString("") { "「${it.second.t}」" } + " がこの支払いにあります"
    Suggest(text, "完了にする") { doneByPay(app, hits.map { it.first }, id) }
}

/* ---------------- 品目の一覧・負担の行 ---------------- */

@Composable
private fun ItemGroupRow(id: String, a: Activity, g: ItemGroup, gi: Int, divider: Boolean) {
    val app = LocalApp.current
    val c = V4.c
    val key = "$id:$gi"
    val n = g.idx.size
    val open = openGroups[key] == true
    val p = g.first
    Column {
        if (divider) Divider(14.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).press {
                if (n > 1) { openGroups[key] = !open } else { app.go("i", id, g.idx[0].toString()) }
            }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.n, style = plex(15, 500, 1.4), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (n > 1) {
                        Text(" ×$n", style = plex(15, 400, 1.4), color = c.tx3)
                        Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.padding(start = 4.dp).size(16.dp).rotate(if (open) -90f else 90f))
                    }
                }
                Text(itemSub(p, n), style = V4.t.cap, color = c.tx3, modifier = Modifier.padding(top = 2.dp))
            }
            Text(yen(p.amount() * n), style = plex(15, 600, 1.4).num(), color = c.tx)
            if (n == 1) Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
        if (n > 1 && open) Row(Modifier.padding(start = 34.dp, bottom = 6.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.padding(start = 16.dp).width(1.5.dp).fillMaxHeight().background(c.line))
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                g.idx.forEachIndexed { k, idx ->
                    val item = a.items[idx]
                    V4Row(
                        t = item.memo ?: "メモなし", tColor = c.tx2, divider = k > 0, dividerLeft = 14.dp, chev = true,
                        lead = { KidNo(k + 1) }, trailing = { Tag(item.b) },
                        onClick = { app.go("i", id, idx.toString()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KidNo(n: Int) {
    val c = V4.c
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(c.sunk), contentAlignment = Alignment.Center) {
        Text("$n", style = plex(12, 600), color = c.tx2)
    }
}

@Composable
private fun BurdenRow(id: String, a: Activity, pt: String) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val mine = a.x?.let { d.expenses[it]?.amt } ?: 0
    val theirs = a.adv?.amt ?: 0
    ListCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).press { openBurdenSheet(app, id) }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(burdenText(a), style = plex(15, 500, 1.4), color = c.tx)
                Text("自分 ${yen(mine)}、${pt}さん ${yen(theirs)}", style = V4.t.cap, color = c.tx3, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
    }
}

/* ---------------- 品目の詳細 ---------------- */

@Composable
fun ItemDetailScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val id = r.arg(0).orEmpty()
    val ix = r.arg(1)?.toIntOrNull() ?: -1
    val a = d.acts[id]
    val item = a?.items?.getOrNull(ix)
    if (a == null || item == null) {
        Screen(topBar = { AppBar("") }) { Empty(V4Icons.activity, "品目 なし") }
        return
    }
    val pt = partnerOf(d, a)
    val mine = mineOf(a, pt).getOrElse(ix) { 0 }
    val edit = BarAction(V4Icons.edit, "編集") { openItemSheet(app, id, ix) }
    val shape = RoundedCornerShape(20.dp)
    Screen(topBar = { AppBar("品目", actions = listOf(edit)) }) {
        Column(
            Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 8.dp).fillMaxWidth()
                .tlShadow(shape).clip(shape).background(c.tlCard).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(item.n, style = plex(18, 600, 1.4), color = c.tx, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(yen(item.amount()), style = plex(44, 600, 1.1).num(), color = c.tx, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
            Text(
                "${d.payLabel(a)} ・ 税${item.tax}%" + (item.memo?.let { " ・ $it" } ?: ""),
                style = V4.t.cap, color = c.tx3,
            )
        }
        if (item.lines.size >= 2) {
            SectionHead("内訳")
            ListCard {
                item.lines.forEachIndexed { i, l ->
                    val v = lineValue(item, i)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(lnName(l, i), style = V4.t.body, color = c.tx)
                            if (l.k == "pct" && i > 0) Text(" ${kotlin.math.abs(l.v)}%", style = V4.t.cap, color = c.tx3)
                        }
                        if (i == 0) Text(yen(v), style = plex(15, 500).num(), color = c.tx)
                        else Text(
                            (if (v < 0) "−" else "＋") + yen(kotlin.math.abs(v)),
                            style = plex(15, 500).num(), color = if (v < 0) c.late else c.tx,
                        )
                    }
                    Divider(14.dp)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("合計", style = V4.t.body, color = c.tx, modifier = Modifier.weight(1f))
                    Text(yen(item.amount()), style = plex(15, 700).num(), color = c.tx)
                }
            }
        }
        if (pt != null) {
            SectionHead("負担")
            if (isWhole(a)) {
                Text("全体で割る設定です。この品目の自分の分は ${yen(mine)}（負担画面で変更）", style = V4.t.cap, color = c.tx3,
                    modifier = Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 10.dp))
            } else {
                Row(Modifier.padding(horizontal = V4Dim.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("自分" to "自分", "半分" to "半分", pt to pt).forEach { (v, label) ->
                        BurdenChip(label, selected = item.b == v, modifier = Modifier.weight(1f)) { setItemBurden(app, id, ix, v) }
                    }
                }
                Text("自分の分 ${yen(mine)}", style = V4.t.cap, color = c.tx3,
                    modifier = Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 10.dp))
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
            GhostButton("この品目を編集") { openItemSheet(app, id, ix) }
        }
    }
}
