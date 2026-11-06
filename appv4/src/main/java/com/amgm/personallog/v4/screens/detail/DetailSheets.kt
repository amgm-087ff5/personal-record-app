package com.amgm.personallog.v4.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.Activity
import com.amgm.personallog.v4.data.AmtLine
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Entity
import com.amgm.personallog.v4.data.Plan
import com.amgm.personallog.v4.data.PayItem
import com.amgm.personallog.v4.data.XItem
import com.amgm.personallog.v4.data.amount
import com.amgm.personallog.v4.data.descOf
import com.amgm.personallog.v4.data.isSpan
import com.amgm.personallog.v4.data.linesTotal
import com.amgm.personallog.v4.data.pctYen
import com.amgm.personallog.v4.data.todos
import com.amgm.personallog.v4.data.uid
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.components.Field
import com.amgm.personallog.v4.ui.components.GhostButton
import com.amgm.personallog.v4.ui.components.Ico
import com.amgm.personallog.v4.ui.components.IconBtn
import com.amgm.personallog.v4.ui.components.ListCard
import com.amgm.personallog.v4.ui.components.Pill
import com.amgm.personallog.v4.ui.components.Prop
import com.amgm.personallog.v4.ui.components.SectionHead
import com.amgm.personallog.v4.ui.components.Seg
import com.amgm.personallog.v4.ui.components.V4Row
import com.amgm.personallog.v4.ui.components.Divider
import com.amgm.personallog.v4.ui.components.num
import com.amgm.personallog.v4.ui.components.press
import kotlin.math.abs

/*
 * 出来事・品目の操作とシート(詳細の担当)。
 * 操作は Store.update 1回にする(取り消しは Store.undo で全部戻る)。負担の計算は data の規則(detail.md §8)どおり。
 * 負担の設定(全体で割る・割り方・自分の金額)は Activity.ext の "split" "how" "me" に入れる(Model に項目が無いため)。
 */

/* ---------------- 計算(データの規則。画面の数字はこの結果) ---------------- */

internal fun isWhole(a: Activity): Boolean = a.ext["split"] == "whole"

/** 相手の姓(相手の縁の名前の最後の語)。無ければ null */
internal fun partnerOf(d: AppData, a: Activity): String? =
    a.adv?.let { adv -> d.entities[adv.who]?.n?.trim()?.split(Regex("\\s+"))?.lastOrNull() }

/** 品目ごとの自分の分(全体で割る時は按分) */
internal fun mineOf(a: Activity, pt: String?): List<Int> {
    val items = a.items
    if (isWhole(a)) {
        val total = items.sumOf { it.amount() }
        if (total == 0) return items.map { 0 }
        val me = if (a.ext["how"] == "amount") (a.ext["me"]?.toIntOrNull() ?: 0).coerceIn(0, total) else total / 2
        var rest = me
        return items.mapIndexed { i, p ->
            if (i == items.lastIndex) rest else {
                val s = minOf(rest, p.amount() * me / total)
                rest -= s
                s
            }
        }
    }
    return items.map { p -> if (p.b == "半分") p.amount() / 2 else if (p.b == pt) 0 else p.amount() }
}

/**
 * 支払いの変更を反映する: 金額 = 品目の合計、自分の支出(X)の品目と金額、立替の相手の分。
 * 自分の分が 0 の品目は支出から外す。
 */
internal fun AppData.withPayment(id: String, a0: Activity, pt: String?): AppData {
    val a1 = if (a0.items.isEmpty()) a0 else a0.copy(amt = a0.items.sumOf { it.amount() })
    val xid = a1.x
    val x = xid?.let { expenses[it] }
    if (xid == null || x == null) return copy(acts = acts + (id to a1))
    val mine = mineOf(a1, pt)
    val newX = if (a1.items.isEmpty()) x else x.copy(
        amt = mine.sum(),
        items = a1.items.mapIndexedNotNull { i, p ->
            val m = mine.getOrElse(i) { 0 }
            if (m <= 0) null
            else XItem(p.n, m, if (m == p.amount()) "全額" else if (m * 2 == p.amount()) "1/2" else "按分", p.memo)
        },
    )
    val adv = a1.adv
    val a2 = if (adv != null) a1.copy(adv = adv.copy(amt = (a1.amt ?: 0) - newX.amt)) else a1
    return copy(acts = acts + (id to a2), expenses = expenses + (xid to newX))
}

/** 同じ名前・同じ金額・同じ金額行の品目を1つにまとめる(表示だけ。データは1行=1個) */
internal class ItemGroup(val first: PayItem, val idx: MutableList<Int>)

internal fun groupItems(items: List<PayItem>): List<ItemGroup> {
    val out = ArrayList<ItemGroup>()
    val at = HashMap<String, Int>()
    items.forEachIndexed { i, p ->
        val key = "${p.n}|${p.amount()}|" + p.lines.joinToString(",") { "${it.k}${it.v}${it.n}" }
        val g = at[key]
        if (g == null) {
            at[key] = out.size
            out.add(ItemGroup(p, mutableListOf(i)))
        } else {
            out[g].idx.add(i)
        }
    }
    return out
}

/** 金額行の名前。1行目は本体 */
internal fun lnName(l: AmtLine, i: Int): String =
    if (i == 0) "本体" else l.n.ifEmpty { if (l.v < 0) "割引" else "オプション" }

/** 金額行の表示の円(割合の行は直前までの小計にかける) */
internal fun lineValue(p: PayItem, i: Int): Int =
    if (i == 0) p.lines[0].v else if (p.lines[i].k == "pct") pctYen(p.lines, i) else p.lines[i].v

/** 品目の補足: 「1個 ¥800、」+ 金額行の内訳 or 税 */
internal fun itemSub(p: PayItem, n: Int): String {
    val head = if (n > 1) "1個 ${yen(p.amount())}、" else ""
    if (p.lines.size < 2) return head + "税${p.tax}%"
    val parts = p.lines.indices.map { i ->
        if (i == 0) "本体 ${yen(lineValue(p, 0))}"
        else {
            val v = lineValue(p, i)
            "${if (v < 0) "−" else "＋"}${lnName(p.lines[i], i)} ${yen(abs(v))}"
        }
    }
    return head + parts.joinToString("  ") + "  税${p.tax}%"
}

internal fun burdenText(a: Activity): String =
    if (isWhole(a)) (if (a.ext["how"] == "amount") "全体で割る(自分の金額を決めた)" else "全体で割る(半分ずつ)")
    else "食べた分(品目ごと)"

/** 買い物リストの ToDo で、この支払いの品目と名前が一致する未完了のもの */
internal fun shopTodos(d: AppData, a: Activity): List<Pair<String, Plan>> {
    val payee = a.roles.firstOrNull { it.role == "支払先" }?.ent
    val names = a.items.map { it.n }.toSet()
    return d.todos.entries.filter { (_, t) ->
        val lst = t.list?.let { d.lists[it] }
        t.done == null && t.t in names && lst != null && (lst.shop == null || lst.shop == payee)
    }.map { it.key to it.value }
}

/* ---------------- 操作(その他の操作・削除・時刻・精算・店) ---------------- */

internal fun openActMenu(app: AppState, id: String) {
    val a = app.data.acts[id] ?: return
    val instant = !a.isSpan() && a.type != "支払い"
    app.openSheet(title = "操作") {
        ListCard {
            V4Row(
                t = if (instant) "時刻を編集" else "編集",
                lead = { Ico(V4Icons.edit) },
                onClick = {
                    app.closeSheet()
                    if (instant) openTimeSheet(app, id) else app.toast("編集はモックでは未対応です")
                },
            )
            V4Row(t = "削除", lead = { Ico(V4Icons.trash) }, divider = true, onClick = { deleteActivity(app, id) })
        }
    }
}

internal fun openTimeSheet(app: AppState, id: String) {
    val a = app.data.acts[id] ?: return
    val draft = mutableStateOf(a.time ?: "")
    app.openSheet(
        title = "時刻を編集", mainLabel = "保存",
        onMain = {
            val t = draft.value
            if (t.isBlank()) app.toast("時刻を入れてください")
            else {
                app.store.update { d ->
                    val x = d.acts[id] ?: return@update d
                    d.copy(acts = d.acts + (id to x.copy(time = t)))
                }
                app.closeSheet()
                app.toast("時刻を保存しました", undo = true)
            }
        },
    ) {
        ListCard {
            Field(label = "時刻", value = draft.value, onValue = { draft.value = it }, placeholder = "HH:MM")
        }
    }
}

/** この記録と、その中の全ての子(孫まで)を消す。支払いの支出も消える。取り消しは Store.undo */
internal fun deleteActivity(app: AppState, id: String) {
    val a0 = app.data.acts[id] ?: return
    val ids = listOf(id) + app.data.descOf(id)
    val count = ids.size - 1
    app.store.update { d ->
        val xs = ids.mapNotNull { d.acts[it]?.x }
        var acts = d.acts.filterKeys { it !in ids }
        val pk = a0.parent
        if (pk != null && acts[pk] != null) acts = acts + (pk to acts[pk]!!.copy(kids = acts[pk]!!.kids.filter { it != id }))
        d.copy(acts = acts, expenses = d.expenses.filterKeys { it !in xs })
    }
    app.closeSheet()
    app.goBack()
    app.toast(if (count > 0) "記録と中の $count 件を削除しました" else "記録を削除しました", undo = true)
}

internal fun settleAct(app: AppState, id: String) {
    app.store.update { d ->
        val x = d.acts[id] ?: return@update d
        val adv = x.adv ?: return@update d
        d.copy(acts = d.acts + (id to x.copy(adv = adv.copy(state = "精算済み"))))
    }
    app.toast("精算済みにしました", undo = true)
}

internal fun registerShop(app: AppState, id: String) {
    val a = app.data.acts[id] ?: return
    val nm = a.roles.firstOrNull { it.role == "支払先" && it.ent == null && it.name != null }?.name ?: return
    val key = uid("e")
    app.store.update { d ->
        val x = d.acts[id] ?: return@update d
        d.copy(
            entities = d.entities + (key to Entity(n = nm, i = nm.take(1), t = listOf("shop"))),
            acts = d.acts + (id to x.copy(roles = x.roles.map { if (it.role == "支払先" && it.ent == null) it.copy(ent = key, name = null) else it })),
        )
    }
    app.toast("「$nm」を店として登録しました", undo = true)
}

internal fun doneByPay(app: AppState, keys: List<String>, id: String) {
    val day = app.today
    app.store.update { d -> d.copy(plans = d.plans.mapValues { (k, p) -> if (k in keys) p.copy(done = day, payRef = id) else p }) }
    app.toast("支払いの品目から完了にしました", undo = true)
}

/* ---------------- 品目の負担(詳細の一覧・品目の詳細) ---------------- */

internal fun setItemBurden(app: AppState, id: String, ix: Int, v: String) {
    app.store.update { d ->
        val a = d.acts[id] ?: return@update d
        val items = a.items.mapIndexed { i, p -> if (i == ix) p.copy(b = v) else p }
        d.withPayment(id, a.copy(items = items), partnerOf(d, a))
    }
    app.toast("負担を変更しました", undo = true)
}

@Composable
internal fun BurdenChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = V4.c
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.meSoft else c.sunk).press(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = plex(15, if (selected) 600 else 500), color = if (selected) c.me else c.tx2, maxLines = 1)
    }
}

/* ---------------- 負担シート ---------------- */

internal class BurdenDraft(a: Activity) {
    var whole by mutableStateOf(isWhole(a))
    var how by mutableStateOf(a.ext["how"] ?: "half")
    var me by mutableStateOf(a.ext["me"] ?: "")
    val bs = mutableStateListOf<String>().also { l -> a.items.forEach { l.add(it.b) } }
}

internal fun openBurdenSheet(app: AppState, id: String) {
    val a = app.data.acts[id] ?: return
    val pt = partnerOf(app.data, a) ?: return
    val draft = BurdenDraft(a)
    app.openSheet(
        title = "負担", mainLabel = "保存", onMain = { saveBurden(app, id, draft, pt) },
        leftLabel = "閉じる", onLeft = { app.closeSheet() }, full = true,
    ) {
        BurdenBody(draft, a, pt)
    }
}

private fun saveBurden(app: AppState, id: String, draft: BurdenDraft, pt: String) {
    app.store.update { d ->
        val a = d.acts[id] ?: return@update d
        val items = a.items.mapIndexed { i, p -> p.copy(b = draft.bs.getOrElse(i) { p.b }) }
        val ext = if (draft.whole) a.ext + mapOf("split" to "whole", "how" to draft.how, "me" to draft.me)
        else a.ext - setOf("split", "how", "me")
        d.withPayment(id, a.copy(items = items, ext = ext), pt)
    }
    app.closeSheet()
    app.toast("負担を保存しました", undo = true)
}

@Composable
private fun BurdenBody(draft: BurdenDraft, a: Activity, pt: String) {
    val c = V4.c
    val preview = a.copy(
        items = a.items.mapIndexed { i, p -> p.copy(b = draft.bs.getOrElse(i) { p.b }) },
        ext = if (draft.whole) a.ext + mapOf("split" to "whole", "how" to draft.how, "me" to draft.me) else a.ext - setOf("split", "how", "me"),
    )
    val mine = mineOf(preview, pt).sum()
    val total = a.items.sumOf { it.amount() }
    Column(Modifier.fillMaxWidth()) {
        Seg(items = listOf("items" to "食べた分", "whole" to "全体で割る"), cur = if (draft.whole) "whole" else "items", onPick = { draft.whole = it == "whole" })
        if (!draft.whole) {
            SectionHead("品目ごとの負担", n = a.items.size.toString(), topPad = 12.dp)
            ListCard {
                a.items.forEachIndexed { i, p ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.n, style = plex(15, 500, 1.4), color = c.tx)
                            Text(yen(p.amount()), style = plex(15, 600).num(), color = c.tx)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("自分" to "自分", "半分" to "半分", pt to pt).forEach { (v, label) ->
                                BurdenChip(label, selected = draft.bs.getOrNull(i) == v, modifier = Modifier.weight(1f)) {
                                    if (i < draft.bs.size) draft.bs[i] = v
                                }
                            }
                        }
                    }
                    if (i > 0) Divider(14.dp)
                }
            }
        } else {
            SectionHead("全体の割り方", topPad = 12.dp)
            Seg(items = listOf("half" to "半分ずつ", "amount" to "自分の金額を決める"), cur = draft.how, onPick = { draft.how = it })
            if (draft.how == "amount") ListCard {
                Field(
                    label = "自分の負担", value = draft.me, onValue = { draft.me = it.filter { ch -> ch.isDigit() } },
                    placeholder = "¥0", amount = true, number = true,
                )
            }
        }
        ListCard(Modifier.padding(top = 12.dp)) {
            Prop(k = "合計", value = { Text(yen(total), style = plex(15, 600).num(), color = c.tx) })
            Prop(k = "自分", divider = true, value = { Text(yen(mine), style = plex(15, 600).num(), color = c.me) })
            Prop(k = "${pt}さん", divider = true, value = { Text(yen(total - mine), style = plex(15, 500).num(), color = c.tx) })
        }
    }
}

/* ---------------- 品目の編集シート(1品目) ---------------- */

internal class LineDraft(code: String, name: String, v: String) {
    /** a+ = ＋円 / a- = −円 / p+ = ＋% / p- = −% */
    var code by mutableStateOf(code)
    var name by mutableStateOf(name)
    var v by mutableStateOf(v)
}

internal class ItemDraft(base: PayItem?) {
    var name by mutableStateOf(base?.n ?: "")
    var amount by mutableStateOf(if (base == null || base.lines.size >= 2) "" else base.price.toString())
    var tax by mutableIntStateOf(base?.tax ?: 10)
    var memo by mutableStateOf(base?.memo ?: "")
    val lines = mutableStateListOf<LineDraft>()

    init {
        if (base != null && base.lines.size >= 2) base.lines.forEach { l ->
            val neg = l.v < 0
            lines.add(LineDraft(if (l.k == "pct") (if (neg) "p-" else "p+") else (if (neg) "a-" else "a+"), l.n, abs(l.v).toString()))
        }
    }
}

private fun digitsOf(s: String): Int = s.filter { it.isDigit() }.toIntOrNull() ?: 0

/** 編集の内容を品目にする(金額行が2行以上の時だけ金額行を残す) */
internal fun ItemDraft.toItem(base: PayItem?): PayItem {
    val ls = if (lines.size >= 2) lines.mapIndexed { i, l ->
        val n = digitsOf(l.v)
        AmtLine(if (i == 0) "" else l.name.trim(), if (l.code.startsWith("p")) "pct" else "amt", if (l.code.endsWith("-")) -n else n)
    } else emptyList()
    val price = when {
        ls.isNotEmpty() -> linesTotal(ls) ?: 0
        lines.size == 1 -> digitsOf(lines[0].v)
        else -> digitsOf(amount)
    }
    return PayItem(n = name.trim(), price = price, lines = ls, b = base?.b ?: "自分", tax = tax, memo = memo.trim().ifEmpty { null })
}

fun openItemSheet(app: AppState, id: String, ix: Int?) {
    val a = app.data.acts[id] ?: return
    val base = ix?.let { a.items.getOrNull(it) }
    val draft = ItemDraft(base)
    val del: (() -> Unit)? = if (ix == null) null else ({ deleteItem(app, id, ix) })
    app.openSheet(
        title = if (ix == null) "品目を追加" else "品目を編集",
        mainLabel = if (ix == null) "追加" else "保存",
        onMain = {
            val item = draft.toItem(base)
            if (draft.name.isBlank() || item.amount() == 0) app.toast("品名と金額を入れてください")
            else {
                saveItem(app, id, ix, item)
                app.closeSheet()
                app.toast("保存しました", undo = true)
            }
        },
        leftLabel = "閉じる", onLeft = { app.closeSheet() }, full = true,
    ) {
        ItemEditorBody(draft, del)
    }
}

private fun saveItem(app: AppState, id: String, ix: Int?, item: PayItem) {
    app.store.update { d ->
        val a = d.acts[id] ?: return@update d
        val items = if (ix == null) a.items + item else a.items.mapIndexed { i, p -> if (i == ix) item else p }
        d.withPayment(id, a.copy(items = items), partnerOf(d, a))
    }
}

/** 品目を1つ消す。最後の1品目は消せない。消したら品目の詳細は要らないので一つ戻る */
private fun deleteItem(app: AppState, id: String, ix: Int) {
    val n = app.data.acts[id]?.items?.size ?: 0
    if (n <= 1) {
        app.toast("最後の1品目は削除できません")
        return
    }
    app.store.update { d ->
        val a = d.acts[id] ?: return@update d
        val items = a.items.filterIndexed { i, _ -> i != ix }
        d.withPayment(id, a.copy(items = items), partnerOf(d, a))
    }
    app.closeSheet()
    app.goBack()
    app.toast("削除しました", undo = true)
}

@Composable
internal fun ItemEditorBody(draft: ItemDraft, onDelete: (() -> Unit)?) {
    val c = V4.c
    Column(Modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("品名", style = V4.t.cap, color = c.tx3)
        PlainField(draft.name, { draft.name = it }, "品名", false, plex(17, 600, 1.4))
        if (draft.lines.isEmpty()) {
            Text("金額", style = V4.t.cap, color = c.tx3)
            PlainField(draft.amount, { draft.amount = it.filter { ch -> ch.isDigit() } }, "¥0", true, plex(20, 600).num())
            GhostButton("オプション・割引を追加", V4Icons.plus) {
                draft.lines.add(LineDraft("a+", "", draft.amount))
                draft.lines.add(LineDraft("a+", "", ""))
                draft.amount = ""
            }
        } else {
            draft.lines.forEachIndexed { i, l -> LineRow(draft, i, l) }
            val total = draft.toItem(null).amount()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("本体 ${yen(digitsOf(draft.lines[0].v))}", style = V4.t.cap, color = c.tx3)
                Text("= " + yen(total), style = plex(15, 700).num(), color = c.tx)
            }
            GhostButton("オプション・割引を追加", V4Icons.plus) { draft.lines.add(LineDraft("a+", "", "")) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(8, 10).forEach { t -> Pill("税${t}%", draft.tax == t) { draft.tax = t } }
            Spacer(Modifier.weight(1f))
            if (onDelete != null) GhostButton("削除", V4Icons.trash, color = c.tx2, onClick = onDelete)
        }
        Column {
            Text("メモ", style = V4.t.cap, color = c.tx3)
            PlainField(draft.memo, { draft.memo = it }, "任意", false, V4.t.body)
        }
    }
}

@Composable
private fun LineRow(draft: ItemDraft, i: Int, l: LineDraft) {
    val c = V4.c
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (i == 0) {
            Text("本体", style = plex(13, 500), color = c.tx2, modifier = Modifier.width(76.dp).padding(start = 4.dp))
            Spacer(Modifier.weight(1f))
        } else {
            val label = when (l.code) { "a+" -> "＋円"; "a-" -> "−円"; "p+" -> "＋%"; else -> "−%" }
            Box(
                Modifier.width(64.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp)).background(c.sunk)
                    .clickable { l.code = when (l.code) { "a+" -> "a-"; "a-" -> "p+"; "p+" -> "p-"; else -> "a+" } },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = plex(13, 600), color = c.tx) }
            PlainField(l.name, { l.name = it }, if (l.code.endsWith("-")) "割引" else "オプション", false, V4.t.body, Modifier.weight(1f))
        }
        PlainField(l.v, { l.v = it.filter { ch -> ch.isDigit() } }, if (l.code.startsWith("p")) "0%" else "¥0", true, plex(15, 600).num(), Modifier.width(92.dp))
        if (i > 0) IconBtn(V4Icons.close, "この行を削除", tint = c.tx3, size = 18.dp) { draft.lines.removeAt(i) }
        else Spacer(Modifier.width(44.dp))
    }
}

/** 入力の小さい箱(背景=面、左右10)。数字だけのものは numeric */
@Composable
internal fun PlainField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    numeric: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val c = V4.c
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(c.surface).padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.tx3))
        BasicTextField(
            value = value, onValueChange = onValue, textStyle = style.copy(color = c.tx), singleLine = true,
            cursorBrush = SolidColor(c.brand),
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
