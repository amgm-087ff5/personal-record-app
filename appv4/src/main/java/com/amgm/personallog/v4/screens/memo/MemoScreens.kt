package com.amgm.personallog.v4.screens.memo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.Memo
import com.amgm.personallog.v4.data.Note
import com.amgm.personallog.v4.data.payLabel
import com.amgm.personallog.v4.data.uid
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.Route
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.components.AppBar
import com.amgm.personallog.v4.ui.components.BarAction
import com.amgm.personallog.v4.ui.components.DeleteButton
import com.amgm.personallog.v4.ui.components.Empty
import com.amgm.personallog.v4.ui.components.Ico
import com.amgm.personallog.v4.ui.components.Knot
import com.amgm.personallog.v4.ui.components.LargeTitle
import com.amgm.personallog.v4.ui.components.ListCard
import com.amgm.personallog.v4.ui.components.MainButton
import com.amgm.personallog.v4.ui.components.ObjCard
import com.amgm.personallog.v4.ui.components.Objs
import com.amgm.personallog.v4.ui.components.Screen
import com.amgm.personallog.v4.ui.components.SectionHead
import com.amgm.personallog.v4.ui.components.TitleAction
import com.amgm.personallog.v4.ui.components.Toggle
import com.amgm.personallog.v4.ui.components.TonalButton
import com.amgm.personallog.v4.ui.components.V4Row
import com.amgm.personallog.v4.ui.components.e1
import com.amgm.personallog.v4.ui.components.press

/*
 * メモ(#/memo)・エンティティのメモ(#/n/{id}・#/notes)。仕様は specs/memo.md。
 * 「角を折った紙」(.en-paper)は MemoCard として public。ほかの画面からも使える。
 */

/* ---------------- 書く欄の下書き・編集シートの状態 ---------------- */

/** 書く欄の下書き(モックの memoDraft。画面を移っても残る) */
object MemoDraft {
    var body by mutableStateOf("")
    val links = mutableStateListOf<String>()
    var pick by mutableStateOf(false)
}

/** 編集シートの中身(モックの memoSheet) */
class MemoEdit(val id: String, body: String, links: List<String>, pin: Boolean) {
    var body by mutableStateOf(body)
    val links = mutableStateListOf<String>().also { it.addAll(links) }
    var pin by mutableStateOf(pin)
}

/* ---------------- 紙の形(.en-paper) ---------------- */

/** 角を折った紙の外形。右上は13px切る(折り返しの角)。tl/br/bl は各角の丸み */
private class PaperShape(private val tl: Dp, private val br: Dp, private val bl: Dp, private val cut: Dp = 13.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val c = with(density) { cut.toPx() }
        val a = with(density) { tl.toPx() }; val b = with(density) { br.toPx() }; val d = with(density) { bl.toPx() }
        val p = Path()
        p.moveTo(a, 0f)
        p.lineTo(w - c, 0f)
        p.lineTo(w, c)
        p.lineTo(w, h - b)
        if (b > 0f) p.arcTo(Rect(w - 2 * b, h - 2 * b, w, h), 0f, 90f, false) else p.lineTo(w, h)
        p.lineTo(d, h)
        if (d > 0f) p.arcTo(Rect(0f, h - 2 * d, 2 * d, h), 90f, 90f, false)
        p.lineTo(0f, a)
        if (a > 0f) p.arcTo(Rect(0f, 0f, 2 * a, 2 * a), 180f, 90f, false)
        p.close()
        return Outline.Generic(p)
    }
}

private val PAPER_SHADOW = Color(40, 55, 90, 20)

/** 紙: 白い面+右上の折り返し+左の綴じ目の線(常に --kin) */
private fun Modifier.paper(tl: Dp = 4.dp, br: Dp = 14.dp, bl: Dp = 14.dp): Modifier = composed {
    val c = V4.c
    val shape = PaperShape(tl, br, bl)
    this.shadow(3.dp, shape, ambientColor = PAPER_SHADOW, spotColor = PAPER_SHADOW)
        .background(c.surface, shape)
        .drawBehind {
            val w = size.width; val h = size.height
            val f = 18.dp.toPx()
            drawRect(color = c.kin, topLeft = Offset(0f, 14.dp.toPx()), size = Size(3.dp.toPx(), (h - 28.dp.toPx()).coerceAtLeast(0f)))
            val fold = Path().apply { moveTo(w - f, 0f); lineTo(w, f); lineTo(w - f, f); close() }
            drawPath(fold, c.line)
        }
}

/** 折り返しなどの小さい印: ピン留め(線の画鋲。モックの pin 相当) */
private val PinIcon: ImageVector by lazy {
    ImageVector.Builder("pin", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes("M9 4h6l-1 5 3 3v1H7v-1l3-3-1-5z"),
            stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
        .addPath(
            pathData = addPathNodes("M12 13v7"),
            stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

/* ---------------- データの補助(data.js の memoText・noteLine など) ---------------- */

private val REF_RE = Regex("""\{([ea]):([^}]+)\}""")
private val MARK_RE = Regex("""\{[ea]:[^}]*\}""")

/** 候補に出すエンティティ(me 以外・並びはそのまま) */
private fun AppData.memoEntKeys(): List<String> = entities.keys.filter { it != "me" }

/** 記録の参照の文字: 支払いは支払い・店、ほかは題名(無ければ日付) */
private fun AppData.recLabel(id: String): String {
    val a = acts[id] ?: return ""
    if (a.type == "支払い") return payLabel(a)
    return a.title.ifEmpty { a.date?.let { Dates.fmtMD(it) } ?: "" }
}

/** 検索に使うメモの文字: 本文・縁の名前・記録の題名 */
private fun AppData.memoText(id: String): String {
    val m = memos[id] ?: return ""
    val names = m.links.mapNotNull { entities[it]?.n }.joinToString(" ")
    val rec = m.rec?.let { acts[it]?.title } ?: ""
    return listOf(m.body, names, rec).joinToString(" ")
}

/** 縁のメモの1行目(参照の記号は消す) */
private fun noteLine(n: Note?): String = (n?.body?.firstOrNull()?.getOrNull(1) ?: "").replace(MARK_RE, "")

/** "10/28 21:04" を並べ替えの数値にする */
private fun noteKey(upd: String): Long = runCatching {
    val (md, hm) = upd.split(" ")
    val (m, d) = md.split("/")
    val (h, mi) = hm.split(":")
    m.toLong() * 100000000L + d.toLong() * 1000000L + h.toLong() * 10000L + mi.toLong() * 100L
}.getOrDefault(0L)

/** 縁のメモのキー(E に載っている縁だけ)。更新日の新しい順 */
private fun AppData.noteIds(): List<String> =
    notes.keys.filter { entities.containsKey(it) }.sortedByDescending { noteKey(notes[it]!!.upd) }

private fun AppData.noteText(id: String): String = (entities[id]?.n ?: "") + " " + noteLine(notes[id])

/** 日の見出し(relDay): 今日・明日・昨日・明後日、それ以外は M月D日(曜) */
private fun relDayLabel(date: String, today: String): String = when (Dates.daysBetween(today, date)) {
    0L -> "今日"; 1L -> "明日"; -1L -> "昨日"; 2L -> "明後日"
    else -> Dates.fmtMDW(date)
}

/* ---------------- 操作(保存・削除) ---------------- */

/** 書く欄の保存(取り消せる) */
private fun saveMemoDraft(app: AppState) {
    val body = MemoDraft.body.trim()
    if (body.isEmpty()) { app.toast("メモを入れてください"); return }
    val id = uid("q")
    val links = MemoDraft.links.toList()
    app.store.update { d -> d.copy(memos = d.memos + (id to Memo(body, "${app.today} ${app.now}", links))) }
    MemoDraft.body = ""
    MemoDraft.links.clear()
    MemoDraft.pick = false
    app.toast("メモを保存しました", undo = true)
}

/** メモの編集シートを開く(カードを押した時) */
fun openMemoEditor(app: AppState, id: String) {
    val m = app.data.memos[id] ?: return
    val edit = MemoEdit(id, m.body, m.links, m.pin)
    app.openSheet(title = "メモ", mainLabel = "保存", onMain = { saveMemoEdit(app, edit) }) {
        MemoEditBody(edit)
    }
}

private fun saveMemoEdit(app: AppState, e: MemoEdit) {
    val b = e.body.trim()
    if (b.isEmpty()) { app.toast("本文を入れてください"); return }
    app.store.update { d ->
        d.memos[e.id]?.let { m -> d.copy(memos = d.memos + (e.id to m.copy(body = b, links = e.links.toList(), pin = e.pin))) } ?: d
    }
    app.closeSheet()
    app.rerender()
    app.toast("メモを保存しました")
}

private fun deleteMemo(app: AppState, id: String) {
    app.store.update { d -> d.copy(memos = d.memos - id) }
    app.closeSheet()
    app.toast("メモを削除しました", undo = true)
}

/* ---------------- 部品 ---------------- */

/** 本文の入力(textarea の見た目)。空のときは placeholder を出す */
@Composable
private fun MemoBodyField(value: String, onValue: (String) -> Unit, placeholder: String = "思いついたことを書く") {
    val c = V4.c
    Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)) {
        if (value.isEmpty()) Text(placeholder, style = V4.t.body, color = c.tx3)
        BasicTextField(
            value, onValue,
            modifier = Modifier.fillMaxWidth(),
            textStyle = V4.t.body.copy(color = c.tx),
            cursorBrush = SolidColor(c.brand),
        )
    }
}

/** エンティティの参照(ドット+名前)。link=false は押せない表示 */
@Composable
private fun EntRef(id: String, name: String, link: Boolean = true) {
    val app = LocalApp.current
    val c = V4.c
    Row(
        Modifier.heightIn(min = 28.dp).let { if (link) it.clickable { app.go("e", id) } else it },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Knot(8.dp)
        Text(name, style = plex(13, 500, 1.4), color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 記録の参照(種類のアイコン+題名)。押すと記録へ */
@Composable
private fun RecRef(id: String) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val a = d.acts[id] ?: return
    Row(
        Modifier.heightIn(min = 28.dp).clickable { app.go("a", id) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(V4Icons.actIcon(a.type), null, tint = c.tx2, modifier = Modifier.size(14.dp))
        Text(d.recLabel(id), style = plex(13, 500, 1.4), color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 候補のチップ(エンティティを選ぶ)。選ばれた時は薄い青の面と青い文字 */
@Composable
private fun EntChip(name: String, id: String, selected: Boolean, onClick: () -> Unit) {
    val c = V4.c
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).background(if (selected) c.meSoft else c.sunk)
            .press(onClick = onClick).padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Knot(8.dp)
        Text(name, style = plex(13, if (selected) 600 else 500, 1.4), color = if (selected) c.me else c.tx2, maxLines = 1)
    }
}

/** 1件のメモ = 角を折った紙。押すと編集シート。withDate はピン留めの群(M/D HH:MM) */
@Composable
fun MemoCard(id: String, withDate: Boolean = false, onClick: (() -> Unit)? = null) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val m = d.memos[id] ?: return
    val day = m.at.substring(0, 10)
    val hm = m.at.substring(11, 16)
    Column(
        Modifier.fillMaxWidth().paper()
            .press { if (onClick != null) onClick() else openMemoEditor(app, id) }
            .padding(start = 20.dp, end = 34.dp, top = 14.dp)
            .heightIn(min = 56.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(m.body, style = V4.t.body, color = c.tx, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (withDate) "${Dates.md(day)} $hm" else hm, style = plex(12, 400, 1.4).copy(fontFeatureSettings = "tnum"), color = c.tx3)
            m.links.forEach { k -> d.entities[k]?.let { EntRef(k, it.n) } }
            m.rec?.let { rid -> if (d.acts.containsKey(rid)) RecRef(rid) }
            Spacer(Modifier.weight(1f))
            if (m.pin) Icon(PinIcon, contentDescription = "ピン留め", tint = c.brand, modifier = Modifier.size(16.dp))
        }
    }
}

/** 縁のメモの紙。compact=#/memo の末尾(更新日→1行)、そうでなければ #/notes(本文2行→更新日) */
@Composable
private fun NoteCard(id: String, compact: Boolean) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val n = d.notes[id] ?: return
    val name = d.entities[id]?.n ?: return
    val head: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Knot(8.dp)
            Text("${name}のメモ", style = plex(15, 600, 1.3), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Column(
        Modifier.fillMaxWidth().paper()
            .press { app.go("n", id) }
            .padding(start = 20.dp, end = if (compact) 34.dp else 40.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        head()
        if (compact) {
            Text("更新 ${n.upd}", style = V4.t.cap, color = c.tx3)
            Text(noteLine(n), style = plex(13, 500, 1.4), color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(noteLine(n), style = plex(14, 400, 1.5), color = c.tx2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(n.upd, style = V4.t.cap, color = c.tx3)
        }
    }
}

/** 日や一覧の下に並べる紙の列(左右20、間12) */
@Composable
private fun PaperList(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = V4Dim.gutter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** 書く欄(常に開いている白い紙+エンティティを付ける+保存) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoComposer() {
    val app = LocalApp.current
    val d = LocalData.current
    Column(Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 12.dp).fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().paper().padding(start = 20.dp, end = 34.dp, top = 14.dp, bottom = 14.dp)) {
            MemoBodyField(MemoDraft.body, { MemoDraft.body = it })
            if (MemoDraft.links.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MemoDraft.links.toList().forEach { k -> d.entities[k]?.let { EntRef(k, it.n, link = false) } }
                }
            }
        }
        if (MemoDraft.pick) {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                d.memoEntKeys().forEach { k ->
                    val on = MemoDraft.links.contains(k)
                    EntChip(d.entities[k]!!.n, k, on) {
                        if (on) MemoDraft.links.remove(k) else MemoDraft.links.add(k)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TonalButton("エンティティを付ける") { MemoDraft.pick = !MemoDraft.pick }
            MainButton("保存", modifier = Modifier.weight(1f)) { saveMemoDraft(app) }
        }
    }
}

/** 検索の入力欄(メモを探す)。開いている間だけ出す */
@Composable
private fun MemoSearch(q: String, onQ: (String) -> Unit) {
    val c = V4.c
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 4.dp).fillMaxWidth()
            .heightIn(min = 44.dp).clip(shape).background(c.surface).border(BorderStroke(1.dp, c.line), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(V4Icons.search, null, tint = c.tx3, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text("メモを探す", style = V4.t.body, color = c.tx3)
            BasicTextField(
                q, onQ, singleLine = true,
                textStyle = V4.t.body.copy(color = c.tx),
                cursorBrush = SolidColor(c.brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* ---------------- 本文の参照(本文の {e:id} / {a:id}) ---------------- */

/**
 * 縁のメモの本文。{e:id} は縁の参照(押すとエンティティへ)、{a:id} は記録の参照(押すと記録へ)。
 * 参照は文字列の注釈(href)で押し先を決め、点は inline content で描く。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RichText(text: String, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val inline = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        var last = 0
        REF_RE.findAll(text).forEachIndexed { i, m ->
            append(text.substring(last, m.range.first))
            last = m.range.last + 1
            val kind = m.groupValues[1]
            val key = m.groupValues[2]
            val id = "ref$i"
            if (kind == "e") {
                val ent = d.entities[key]
                pushStringAnnotation("href", if (ent != null) "#/e/$key" else "")
                appendInlineContent(id, "●")
                append(" ")
                pushStyle(SpanStyle(color = c.tx2, fontWeight = FontWeight(500), fontSize = 15.sp))
                append(ent?.n ?: "未登録")
                pop()
                pop()
                inline[id] = InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.Center)) {
                    Knot(8.dp, unreg = ent == null)
                }
            } else {
                val a = d.acts[key]
                if (a != null) {
                    pushStringAnnotation("href", "#/a/$key")
                    appendInlineContent(id, "■")
                    append(" ")
                    pushStyle(SpanStyle(color = c.tx2, fontWeight = FontWeight(500), fontSize = 14.sp))
                    append(d.recLabel(key))
                    pop()
                    pop()
                    inline[id] = InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.Center)) {
                        Icon(V4Icons.actIcon(a.type), null, tint = c.tx2, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        append(text.substring(last))
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated,
        style = plex(16, 400, 1.9),
        color = c.tx,
        inlineContent = inline,
        onTextLayout = { layout = it },
        modifier = modifier.fillMaxWidth().pointerInput(annotated) {
            detectTapGestures { pos ->
                val l = layout ?: return@detectTapGestures
                val off = l.getOffsetForPosition(pos)
                annotated.getStringAnnotations("href", off, off).firstOrNull()?.let { if (it.item.isNotEmpty()) app.goHash(it.item) }
            }
        },
    )
}

/* ---------------- 画面 ---------------- */

/** #/memo メモのタブ */
@Composable
fun MemoScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    var searchOpen by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val needle = q.lowercase()
    fun hit(id: String) = needle.isEmpty() || d.memoText(id).lowercase().contains(needle)

    val sorted = d.memos.entries.sortedByDescending { it.value.at }
    val pinned = sorted.filter { it.value.pin && hit(it.key) }.map { it.key }
    val rest = sorted.filter { !it.value.pin && hit(it.key) }.map { it.key }
    // 日ごとの組: 降順のまま、同じ日付が続く間を1組にする
    val groups = mutableListOf<Pair<String, MutableList<String>>>()
    for (id in rest) {
        val day = d.memos[id]!!.at.substring(0, 10)
        if (groups.lastOrNull()?.first == day) groups.last().second.add(id) else groups.add(day to mutableListOf(id))
    }
    val allNotes = d.noteIds()
    val notes = allNotes.filter { needle.isEmpty() || d.noteText(it).lowercase().contains(needle) }
    val hasAny = d.memos.isNotEmpty() || allNotes.isNotEmpty()
    val none = pinned.isEmpty() && rest.isEmpty() && notes.isEmpty()

    Screen {
        LargeTitle(
            "メモ",
            actions = listOf(
                TitleAction(icon = V4Icons.search, label = "メモを探す", onClick = {
                    if (searchOpen) { searchOpen = false; q = "" } else searchOpen = true
                }),
            ),
        )
        if (searchOpen) MemoSearch(q) { q = it }
        MemoComposer()
        if (!hasAny) {
            Empty(V4Icons.memo, "メモ なし")
        } else if (none) {
            Text("該当 なし", style = plex(15, 500), color = c.tx3, modifier = Modifier.padding(start = V4Dim.gutter, top = 32.dp))
        } else {
            if (pinned.isNotEmpty()) {
                SectionHead("ピン留め", n = pinned.size.toString())
                PaperList { pinned.forEach { MemoCard(it, withDate = true) } }
            }
            groups.forEach { (day, ids) ->
                SectionHead(relDayLabel(day, app.today), n = "${ids.size}件")
                PaperList { ids.forEach { MemoCard(it) } }
            }
            if (notes.isNotEmpty()) {
                SectionHead("エンティティのメモ", n = notes.size.toString())
                PaperList { notes.forEach { NoteCard(it, compact = true) } }
            }
        }
    }
}

/** #/n/{id} 縁のメモの本文 */
@Composable
fun NoteScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val id = r.id ?: ""
    val e = d.entities[id]
    if (e == null) {
        Screen(topBar = { AppBar("") }) { Empty(V4Icons.note, "メモ なし") }
        return
    }
    val n = d.notes[id]
    val pill = RoundedCornerShape(50)
    Screen(topBar = {
        AppBar("", actions = listOf(BarAction(V4Icons.edit, "メモを編集") { app.toast("編集はモックでは未対応です") }))
    }) {
        // 持ち主の縁(押すとエンティティへ)
        Row(
            Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 12.dp).fillMaxWidth()
                .heightIn(min = 44.dp).e1(pill).clip(pill).background(c.surface)
                .press { app.go("e", id) }
                .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Knot(8.dp)
            Text(e.n, style = V4.t.label, color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(V4Icons.chev, null, tint = c.tx3, modifier = Modifier.size(16.dp))
        }
        // 本文の紙(.en-doc)
        Column(
            Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 10.dp).fillMaxWidth()
                .paper(tl = 4.dp, br = 16.dp, bl = 16.dp)
                .padding(start = 24.dp, end = 30.dp, top = 22.dp, bottom = 26.dp),
        ) {
            Text("${e.n}のメモ", style = V4.t.title, color = c.tx)
            Text(
                if (n == null) "未作成" else "主観・最新、更新 ${n.upd}、${n.ver}版目",
                style = V4.t.cap, color = c.tx3, modifier = Modifier.padding(top = 4.dp),
            )
            val blocks = n?.body ?: emptyList()
            if (blocks.isEmpty()) Text("本文 なし", style = V4.t.body, color = c.tx3, modifier = Modifier.padding(top = 22.dp))
            blocks.forEach { blk ->
                Text(blk.getOrElse(0) { "" }, style = plex(14, 600, 1.4), color = c.tx2, modifier = Modifier.padding(top = 22.dp))
                RichText(blk.getOrElse(1) { "" }, Modifier.padding(top = 6.dp))
            }
        }
        // サブページ(押し先は無い)
        if (n != null && n.subs.isNotEmpty()) {
            SectionHead("サブページ", n = n.subs.size.toString())
            Objs {
                n.subs.forEach { s ->
                    ObjCard {
                        V4Row(
                            t = s.getOrElse(0) { "" },
                            lead = { Ico(V4Icons.lines) },
                            trailing = { Text(s.getOrElse(1) { "" }, style = V4.t.label, color = c.tx3) },
                            chev = true,
                        )
                    }
                }
            }
        }
    }
}

/** #/notes エンティティのメモの一覧 */
@Composable
fun NotesScreen(r: Route) {
    val d = LocalData.current
    val ids = d.noteIds()
    Screen(topBar = { AppBar("エンティティのメモ") }) {
        if (ids.isEmpty()) {
            Empty(V4Icons.lines, "メモ なし")
        } else {
            PaperList { ids.forEach { NoteCard(it, compact = false) } }
        }
    }
}

/* 編集シートの本文 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoEditBody(e: MemoEdit) {
    val d = LocalData.current
    val app = LocalApp.current
    Column(Modifier.padding(top = 4.dp)) {
        Column(Modifier.padding(horizontal = V4Dim.gutter).fillMaxWidth().paper().padding(start = 20.dp, end = 34.dp, top = 14.dp, bottom = 14.dp)) {
            MemoBodyField(e.body, { e.body = it }, placeholder = "")
        }
        SectionHead("エンティティ")
        FlowRow(
            Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            d.memoEntKeys().forEach { k ->
                val on = e.links.contains(k)
                EntChip(d.entities[k]!!.n, k, on) {
                    if (on) e.links.remove(k) else e.links.add(k)
                }
            }
        }
        ListCard(Modifier.padding(top = 12.dp)) {
            V4Row(t = "ピン留め", trailing = { Toggle(e.pin) { e.pin = !e.pin } })
        }
        DeleteButton("メモを削除", modifier = Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 20.dp).fillMaxWidth()) {
            deleteMemo(app, e.id)
        }
    }
}
