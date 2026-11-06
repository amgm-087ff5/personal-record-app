package com.amgm.personallog.v4.screens.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.PayItem
import com.amgm.personallog.v4.data.amount
import com.amgm.personallog.v4.data.catAnc
import com.amgm.personallog.v4.data.catFxRaw
import com.amgm.personallog.v4.data.catName
import com.amgm.personallog.v4.data.catPath
import com.amgm.personallog.v4.data.dateOf
import com.amgm.personallog.v4.data.entName
import com.amgm.personallog.v4.data.payLabel
import com.amgm.personallog.v4.data.todos
import com.amgm.personallog.v4.data.yen
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.components.Banner
import com.amgm.personallog.v4.ui.components.DeleteButton
import com.amgm.personallog.v4.ui.components.e1
import com.amgm.personallog.v4.ui.components.Divider
import com.amgm.personallog.v4.ui.components.Field
import com.amgm.personallog.v4.ui.components.GhostButton
import com.amgm.personallog.v4.ui.components.Knot
import com.amgm.personallog.v4.ui.components.ListCard
import com.amgm.personallog.v4.ui.components.SectionHead
import com.amgm.personallog.v4.ui.components.Suggest
import com.amgm.personallog.v4.ui.components.Tag
import com.amgm.personallog.v4.ui.components.Toggle
import com.amgm.personallog.v4.ui.components.TonalButton
import com.amgm.personallog.v4.ui.components.V4Row
import com.amgm.personallog.v4.ui.components.press
import com.amgm.personallog.v4.ui.plex
import com.amgm.personallog.v4.ui.lightBrush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.math.abs

/*
 * レシートで支払い(複数枚の読み取り・一覧・1枚の確認・保存)。create.js の cr* のレシート部分。
 * 写真の読み取りは見本の結果を使う(モックと同じ)。写真そのものは縮小して保存する。
 */

private val POOLS = listOf("super", "drug", "cafe", "bakery", "conv")

/** 見本のレシート。off = 今日からの日数、items = (名前, 価格, 税率) */
private class Sample(val shop: String, val off: Int, val time: String, val items: List<Triple<String, Int, Int>>)

private val SAMPLES = mapOf(
    "super" to Sample("スーパー青葉台", 0, "16:20", listOf(Triple("牛乳", 238, 8), Triple("食器用洗剤", 298, 10), Triple("バナナ", 158, 8), Triple("食パン", 178, 8), Triple("食パン", 178, 8))),
    "drug" to Sample("ドラッグ桜", -4, "20:05", listOf(Triple("洗剤", 680, 10), Triple("歯ブラシ", 600, 10))),
    "cafe" to Sample("カフェ ミナト", 0, "11:10", listOf(Triple("カフェラテ", 520, 10), Triple("クッキー", 120, 8))),
    "bakery" to Sample("ベーカリー木漏れ日", -1, "08:05", listOf(Triple("クロワッサン", 280, 8), Triple("メロンパン", 220, 8))),
    "conv" to Sample("コンビニ青葉台", 0, "13:02", listOf(Triple("おにぎり", 160, 8), Triple("お茶", 140, 8))),
)

/** 写真1枚。thumb = 縮小画像(JPEG の base64)、hash = 元ファイルの SHA-256 */
private class Photo(val pool: String, val hash: String, val thumb: String, val bmp: ImageBitmap)

/** 読み取り1件。status = scan / ready / ok / dup / skip / saved */
internal class Rc(val id: Int, val pool: String, val hash: String, val thumb: String, val bmp: ImageBitmap?) {
    var status by mutableStateOf("scan")
    var shop by mutableStateOf("")
    var date by mutableStateOf("")
    var time by mutableStateOf("")
    var pay by mutableStateOf("現金")
    var act by mutableStateOf<String?>(null)
    var register by mutableStateOf(true)
    var printed by mutableStateOf(0)
    var cat by mutableStateOf("eatout")
    var fx by mutableStateOf<String?>(null)
    var sp by mutableStateOf<String?>(null)
    var with by mutableStateOf<String?>(null)
    val items = mutableStateListOf<PayItem>()
    var dupMsg by mutableStateOf<String?>(null)
    var dupLink by mutableStateOf<String?>(null)
    var override by mutableStateOf(false)
    val todo = mutableStateMapOf<String, Boolean>()
    /** 品目の編集中の行(-1 なし) */
    var openItem by mutableStateOf(-1)
    var secOpen by mutableStateOf(false)

    fun total(): Int = items.sumOf { it.amount() }
    /** 保存する品目(名前があり金額が 0 より大きいもの) */
    fun valid(): List<PayItem> = items.filter { it.n.isNotBlank() && it.amount() > 0 }
}

internal class RcHolder {
    val list = mutableStateListOf<Rc>()
    /** entry = 入口 / batch = 一覧 / review = 1枚の確認 */
    var view by mutableStateOf("entry")
    var single by mutableStateOf(false)
    var cur by mutableStateOf<Rc?>(null)
}

/* ---------------- 写真・見本 ---------------- */

private fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

private fun toJpeg(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()

/** 幅 360 以下に縮める */
private fun scaled(bmp: Bitmap): Bitmap {
    if (bmp.width <= 360) return bmp
    val h = (bmp.height * 360 / bmp.width).coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bmp, 360, h, true)
}

private fun photoFromBitmap(pool: String, bmp: Bitmap): Photo {
    val orig = toJpeg(bmp)
    val small = toJpeg(scaled(bmp))
    return Photo(pool, sha256(orig), Base64.encodeToString(small, Base64.NO_WRAP), BitmapFactory.decodeByteArray(small, 0, small.size).asImageBitmap())
}

/** 見本のレシートの画像(同じ見本は同じ画像・同じハッシュになる) */
private fun samplePhoto(pool: String): Photo {
    val s = SAMPLES[pool]!!
    val bmp = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawColor(android.graphics.Color.rgb(250, 248, 242))
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(60, 60, 60); textSize = 22f }
    cv.drawText(s.shop, 24f, 50f, p)
    var y = 100f
    for ((n, price, _) in s.items) {
        cv.drawText(n, 24f, y, p)
        cv.drawText("¥$price", 260f, y, p)
        y += 36f
    }
    val jpeg = toJpeg(bmp)
    return Photo(pool, sha256(jpeg), Base64.encodeToString(jpeg, Base64.NO_WRAP), BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size).asImageBitmap())
}

/** 選んだファイルを読む(IO) */
private suspend fun loadUris(ctx: Context, uris: List<Uri>): List<Photo> = withContext(Dispatchers.IO) {
    uris.mapIndexedNotNull { i, u ->
        val bytes = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return@mapIndexedNotNull null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@mapIndexedNotNull null
        val small = toJpeg(scaled(bmp))
        Photo(POOLS[i % POOLS.size], sha256(bytes), Base64.encodeToString(small, Base64.NO_WRAP), BitmapFactory.decodeByteArray(small, 0, small.size).asImageBitmap())
    }
}

/* ---------------- 読み取り・重複 ---------------- */

/** 見本の内容を入れる(crToRc) */
private fun Rc.fillSample(d: AppData, today: String) {
    val s = SAMPLES[pool]!!
    shop = s.shop
    date = Dates.addDays(today, s.off)
    time = s.time
    items.clear()
    s.items.forEach { (n, p, t) -> items.add(PayItem(n = n, price = p, tax = t, b = "自分")) }
    printed = s.items.sumOf { it.second }
    register = true
    cat = d.guessCat(d.entByName(shop))
}

/** 重複の判定(保存済み → 同じ写真 → 同じ日時・金額の順) */
private fun checkDup(app: AppState, h: RcHolder, rc: Rc) {
    rc.dupMsg = null
    rc.dupLink = null
    if (rc.override) { rc.status = "ready"; return }
    val d = app.data
    val idx = h.list.indexOf(rc)
    val saved = d.acts.entries.firstOrNull { it.value.ext["hash"] == rc.hash }
    val earlier = h.list.take(idx).firstOrNull { it.hash == rc.hash && it.status != "skip" }
    val same = d.acts.entries.firstOrNull { (k, a) -> a.type == "支払い" && a.time == rc.time && d.dateOf(k) == rc.date && a.amt == rc.total() }
    when {
        saved != null -> { rc.dupMsg = "同じ写真を保存済み"; rc.dupLink = saved.key; rc.status = "dup" }
        earlier != null -> { rc.dupMsg = "同じ写真が${earlier.id + 1}枚目にあります"; rc.status = "dup" }
        same != null -> { rc.dupMsg = "同じ日時・金額の支払いあり"; rc.dupLink = same.key; rc.status = "dup" }
        else -> rc.status = "ready"
    }
}

/** 一覧を読み取る(件ごとに少しずつずらして結果を入れる)。1枚だけで重複が無ければ確認へ */
private fun scanAll(app: AppState, h: RcHolder, scope: CoroutineScope) {
    h.list.forEachIndexed { i, rc ->
        scope.launch {
            delay(500L + i * 350L)
            rc.fillSample(app.data, app.today)
            checkDup(app, h, rc)
            if (h.view == "batch" && h.list.size == 1 && rc.status == "ready") {
                h.single = true
                h.cur = rc
                h.view = "review"
                showReceipt(app, h)
            }
        }
    }
}

private fun startBatch(app: AppState, h: RcHolder, scope: CoroutineScope, photos: List<Photo>) {
    h.list.clear()
    photos.forEachIndexed { i, p -> h.list.add(Rc(i, p.pool, p.hash, p.thumb, p.bmp)) }
    h.view = "batch"
    h.single = false
    showReceipt(app, h)
    scanAll(app, h, scope)
}

private fun openReview(app: AppState, h: RcHolder, rc: Rc) {
    h.cur = rc
    h.single = false
    h.view = "review"
    showReceipt(app, h)
}

/** 今の段階のシートを開く(主ボタンの文言が段階で変わるため開き直す) */
internal fun showReceipt(app: AppState, h: RcHolder) {
    when (h.view) {
        "batch" -> {
            val ok = h.list.count { it.status == "ok" }
            app.openSheet(
                title = "レシートで支払い",
                mainLabel = if (ok > 0) "${ok}件の支払いを保存" else "支払いを保存",
                mainEnabled = { h.list.any { it.status == "ok" } },
                onMain = { saveBatch(app, h) },
                full = true,
            ) { ReceiptBatch(app, h) }
        }
        "review" -> {
            val single = h.single
            app.openSheet(
                title = "レシートで支払い",
                mainLabel = if (single) "支払いを保存" else "確認済みにする",
                onMain = { if (single) saveSingle(app, h) else confirmItem(app, h) },
                full = true,
            ) { ReceiptReview(app, h) }
        }
        else -> app.openSheet(title = "レシートで支払い", full = true) { ReceiptEntry(app, h) }
    }
}

/** 開く入口(中央の＋ → レシート) */
internal fun openReceipt(app: AppState) {
    showReceipt(app, RcHolder())
}

/* ---------------- 保存 ---------------- */

private fun Rc.toPayIn(d: AppData): PayIn {
    val v = valid()
    return PayIn(
        date = date, time = time, act = act, pay = pay, shop = shop, register = register,
        items = v, total = v.sumOf { it.amount() },
        cat = cat,
        fx = if (d.catFxRaw(cat) == "ask") fx else null,
        sp = if (d.catAnc(cat).reversed().firstNotNullOfOrNull { d.cats[it]?.sp } == "ask") sp else null,
        with = with, todo = todo.filterValues { it }.keys.toList(),
        photo = thumb, hash = hash,
    )
}

/** 1枚の確認 → 一覧へ戻って確認済みにする(重複の判定は入れ直す) */
private fun confirmItem(app: AppState, h: RcHolder) {
    val rc = h.cur ?: return
    if (rc.valid().isEmpty()) { app.toast("品目を入れてください"); return }
    checkDup(app, h, rc)
    if (rc.status != "dup") rc.status = "ok"
    h.view = "batch"
    showReceipt(app, h)
}

private fun saveSingle(app: AppState, h: RcHolder) {
    val rc = h.cur ?: return
    if (rc.valid().isEmpty()) { app.toast("品目を入れてください"); return }
    var id: String? = null
    app.store.update { d ->
        val (nd, k) = d.addPayment(rc.toPayIn(d), app.today)
        id = k
        nd
    }
    rc.status = "saved"
    h.list.clear()
    h.cur = null
    h.view = "entry"
    app.closeSheet()
    id?.let { app.go("a", it) }
    app.toast("支払いを保存しました", undo = true)
}

private fun saveBatch(app: AppState, h: RcHolder) {
    val oks = h.list.filter { it.status == "ok" }
    if (oks.isEmpty()) return
    val ids = ArrayList<String>()
    app.store.update { d ->
        var x = d
        for (rc in oks) {
            val (nd, k) = x.addPayment(rc.toPayIn(x), app.today)
            x = nd
            ids.add(k)
        }
        x
    }
    oks.forEach { it.status = "saved" }
    h.list.clear()
    h.cur = null
    h.view = "entry"
    app.closeSheet()
    if (ids.size == 1) app.go("a", ids[0]) else app.go("activities")
    app.toast("${ids.size}件の支払いを保存しました", undo = true)
}

/* ---------------- 画面の部品 ---------------- */

private fun statusLabel(s: String): String = when (s) {
    "scan" -> "読み取り中"
    "ready" -> "確認待ち"
    "ok" -> "確認済み"
    "dup" -> "重複の疑い"
    "skip" -> "スキップ"
    else -> "保存済み"
}

@Composable
private fun ThumbImage(rc: Rc, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(V4.c.sunk)) {
        rc.bmp?.let { Image(bitmap = it, contentDescription = "レシートの写真", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun BackRow(label: String, onClick: () -> Unit) {
    val c = V4.c
    Row(
        Modifier.fillMaxWidth().height(44.dp).press(onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(V4Icons.back, null, tint = c.brand, modifier = Modifier.size(18.dp))
        Text(label, style = plex(15, 500), color = c.brand)
    }
}

/* ---------------- 入口 ---------------- */

@Composable
private fun ReceiptEntry(app: AppState, h: RcHolder) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val c = V4.c
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) startBatch(app, h, scope, listOf(photoFromBitmap(POOLS[0], bmp)))
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size > 20) app.toast("最大20枚まで。先頭の20枚を読み込みます")
            scope.launch {
                val ps = loadUris(ctx, uris.take(20))
                if (ps.isNotEmpty()) startBatch(app, h, scope, ps)
            }
        }
    }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReceiptTile(V4Icons.camera, "撮影する", "1枚", Modifier.weight(1f)) { camera.launch(null) }
            ReceiptTile(V4Icons.photo, "写真を選ぶ", "最大20枚", Modifier.weight(1f)) { pick.launch("image/*") }
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = V4Dim.gutter, vertical = 6.dp).height(48.dp)
                .clip(RoundedCornerShape(10.dp)).press(onClick = {
                    startBatch(app, h, scope, listOf("super", "drug", "cafe", "super").map { samplePhoto(it) })
                }),
            contentAlignment = Alignment.Center,
        ) { Text("サンプルのレシートで試す", style = plex(15, 600), color = c.me) }
    }
}

@Composable
private fun ReceiptTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, small: String, modifier: Modifier, onClick: () -> Unit) {
    val c = V4.c
    val shape = RoundedCornerShape(V4Dim.rMd)
    Column(
        modifier.heightIn(min = 132.dp).e1(shape).clip(shape).background(c.surface).press(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.ev.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.ev, modifier = Modifier.size(24.dp))
        }
        Text(label, style = plex(16, 600, 1.3), color = c.tx)
        Text(small, style = V4.t.cap, color = c.tx3)
    }
}

/* ---------------- 一覧 ---------------- */

@Composable
private fun ReceiptBatch(app: AppState, h: RcHolder) {
    val c = V4.c
    val n = h.list.size
    val done = h.list.count { it.status != "scan" }
    Column {
        Column(
            Modifier.padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.sunk)) {
                Box(Modifier.fillMaxWidth(if (n == 0) 0f else done.toFloat() / n).height(6.dp).background(c.brand))
            }
            Text("$done/${n}枚 読み取り済み", style = V4.t.label, color = c.tx2)
        }
        ListCard {
            h.list.forEachIndexed { i, rc -> BatchRow(app, h, rc, divider = i > 0) }
        }
    }
}

@Composable
private fun BatchRow(app: AppState, h: RcHolder, rc: Rc, divider: Boolean) {
    val d = LocalData.current
    val c = V4.c
    val canOpen = rc.status == "ready" || rc.status == "ok" || rc.status == "dup"
    Column {
        if (divider) Divider(14.dp)
        Row(
            Modifier.fillMaxWidth().let { if (canOpen) it.press(onClick = { openReview(app, h, rc) }) else it }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThumbImage(rc, 40.dp)
            Column(Modifier.weight(1f)) {
                if (rc.status == "scan") {
                    Box(Modifier.size(120.dp, 14.dp).clip(RoundedCornerShape(4.dp)).background(c.sunk))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (d.entByName(rc.shop) != null) Knot(8.dp)
                        Text(rc.shop.ifEmpty { "店名なし" }, style = plex(15, 500, 1.4), color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${Dates.md(rc.date)} ${rc.time}", style = V4.t.cap, color = c.tx3)
                }
            }
            if (rc.status != "scan") Text(yen(rc.total()), style = plex(15, 600).copy(fontFeatureSettings = "tnum"), color = c.tx)
        }
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(statusLabel(rc.status))
                rc.dupLink?.let { link ->
                    Text(d.payLabel(d.acts[link]), style = V4.t.cap, color = c.me, modifier = Modifier.press(onClick = { app.go("a", link) }))
                }
            }
            rc.dupMsg?.let { Text(it, style = V4.t.cap, color = c.tx2) }
            when (rc.status) {
                "ready", "ok" -> GhostButton("スキップ", color = c.tx2) {
                    rc.status = "skip"
                    showReceipt(app, h)
                }
                "skip" -> GhostButton("戻す") {
                    checkDup(app, h, rc)
                    showReceipt(app, h)
                }
                "dup" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalButton("別の支払いとして残す", small = true) {
                        rc.override = true
                        checkDup(app, h, rc)
                        showReceipt(app, h)
                    }
                    GhostButton("スキップ", color = c.tx2) {
                        rc.status = "skip"
                        showReceipt(app, h)
                    }
                }
            }
        }
    }
}

/* ---------------- 1枚の確認 ---------------- */

@Composable
private fun ReceiptReview(app: AppState, h: RcHolder) {
    val rc = h.cur ?: return
    val d = LocalData.current
    val ctx = LocalContext.current
    val c = V4.c
    val total = rc.total()
    val people = d.peopleList()
    val withSur = rc.with?.let { d.entities[it]?.n?.let(::surname) }
    val (mine, _) = splitMine(rc.valid(), total, withSur)
    val adv = total - mine
    val act = d.actResolve(rc.date, rc.time, rc.act)
    val actOpts = d.actOptions(rc.date, act)
    val catOpts = d.cats.keys.map { it to d.catPath(it) }
    val askFx = d.catFxRaw(rc.cat) == "ask"
    val askSp = d.catAnc(rc.cat).reversed().firstNotNullOfOrNull { d.cats[it]?.sp } == "ask"
    val printedDiff = rc.printed - total

    Column {
        if (!h.single) BackRow("${h.list.size}枚の一覧に戻る") {
            h.view = "batch"
            showReceipt(app, h)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = V4Dim.gutter, end = V4Dim.gutter, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThumbImage(rc, 56.dp)
            rc.dupMsg?.let { msg ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tag("重複の疑い")
                    Text(msg, style = V4.t.cap, color = c.tx2)
                }
            }
        }
        ListCard {
            Field("店", rc.shop, { v ->
                rc.shop = v
                rc.cat = d.guessCat(d.entByName(v))
            }, placeholder = "店名")
            SuggestChips(d.placeHits(rc.shop), padH = 14.dp) { v ->
                rc.shop = v
                rc.cat = d.guessCat(d.entByName(v))
            }
            if (rc.shop.isNotBlank() && d.entByName(rc.shop) == null) {
                Column {
                    Divider(14.dp)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Knot(10.dp, unreg = true)
                        Text(rc.shop, style = plex(15, 500, 1.3), color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("を店として登録", style = V4.t.label, color = c.tx2)
                        Toggle(on = rc.register, onClick = { rc.register = !rc.register })
                    }
                }
            }
            PickRow("日付", rc.date, divider = true) { showDatePicker(ctx, rc.date) { rc.date = it } }
            PickRow("時刻", rc.time, divider = true) { showTimePicker(ctx, rc.time) { rc.time = it } }
            SelectRow("支払い方法", rc.pay, listOf("現金" to "現金", "カード ••1234" to "カード ••1234", "電子マネー" to "電子マネー"), divider = true) { rc.pay = it }
            SelectRow("活動の中", actOpts.firstOrNull { it.first == (act ?: "") }?.second ?: "なし(日の直下)", actOpts, divider = true) { rc.act = it }
        }

        SectionHead("品目", n = "${rc.items.size}", actLabel = "品目を追加", actIcon = V4Icons.plus) {
            rc.items.add(PayItem(n = "", price = 0, tax = 8, b = "自分"))
            rc.openItem = rc.items.size - 1
        }
        ListCard {
            rc.items.forEachIndexed { i, _ ->
                ItemRow(rc, i, withSur, divider = i > 0)
            }
        }
        ListCard(Modifier.padding(top = 12.dp)) {
            V4Row(t = "合計", v = yen(total), amt = true)
        }
        if (rc.printed != total) {
            Banner(warn = true, text = "印字 ${yen(rc.printed)} / 差 ${yen(abs(printedDiff))}")
        }

        val names = rc.items.map { it.n }.filter { it.isNotBlank() }
        val shopTodos = d.todos.filter { (_, t) ->
            t.list == "shop" && t.done == null && names.any { n -> t.t.contains(n) || n.contains(t.t) }
        }
        shopTodos.forEach { (k, t) ->
            Suggest("買い物リストの「${t.t}」", if (rc.todo[k] == true) "取り消す" else "完了にする") {
                rc.todo[k] = rc.todo[k] != true
            }
        }

        ListCard(Modifier.padding(top = 12.dp)) {
            V4Row(
                t = "分け方・カテゴリ",
                s = d.catName(rc.cat) + " " + (if (rc.with == null) "分けない" else d.entName(rc.with) + "と分ける"),
                chev = true,
                onClick = { rc.secOpen = !rc.secOpen },
            )
        }
        if (askFx || askSp) {
            ListCard(Modifier.padding(top = 8.dp)) {
                if (askFx) LdMiniRow("固定・変動", listOf("固定", "変動"), rc.fx) { rc.fx = it }
                if (askSp) LdMiniRow("特別感", listOf("日常", "非日常"), rc.sp, divider = askFx) { rc.sp = it }
            }
        }

        if (rc.secOpen) {
            SectionHead("誰かと分ける")
            ChipRow {
                SChip("分けない", rc.with == null) {
                    rc.with = null
                    rc.items.indices.forEach { i -> rc.items[i] = rc.items[i].copy(b = "自分") }
                }
                people.forEach { (k, nm) -> SChip(nm, rc.with == k) { rc.with = k } }
            }
            if (rc.with != null) {
                SectionHead("品目ごとの負担")
                ListCard {
                    rc.items.forEachIndexed { i, it ->
                        RowField(it.n.ifEmpty { "品名なし" }, divider = i > 0) {
                            SChip("自分", it.b == "自分") { setItem(rc, i) { p -> p.copy(b = "自分") } }
                            SChip("半分", it.b == "半分") { setItem(rc, i) { p -> p.copy(b = "半分") } }
                            SChip(withSur ?: "", it.b == withSur) { setItem(rc, i) { p -> p.copy(b = withSur ?: "自分") } }
                        }
                    }
                }
            }
            ListCard(Modifier.padding(top = 8.dp)) {
                SelectRow("カテゴリ", d.catPath(rc.cat), catOpts) { rc.cat = it }
                V4Row(t = "自分の支出", v = yen(mine), amt = true, divider = true)
                if (adv > 0) V4Row(t = "相手の分・立替", v = yen(adv), divider = true)
            }
        }
    }
}

/** 品目の1つを直す(金額は amount() で計算し直す) */
private fun setItem(rc: Rc, i: Int, f: (PayItem) -> PayItem) {
    val p = f(rc.items[i])
    rc.items[i] = p.copy(price = p.amount())
}

@Composable
private fun ItemRow(rc: Rc, i: Int, withSur: String?, divider: Boolean) {
    val c = V4.c
    val it = rc.items[i]
    val open = rc.openItem == i
    Column {
        if (divider) Divider(14.dp)
        Row(
            Modifier.fillMaxWidth().press(onClick = { rc.openItem = if (open) -1 else i }).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(it.n.ifEmpty { "品名なし" }, style = V4.t.body, color = if (it.n.isEmpty()) c.tx3 else c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (it.lines.isNotEmpty()) "オプション・割引 ${it.lines.size - 1}件" else "税${it.tax}%",
                    style = V4.t.cap, color = c.tx3,
                )
            }
            Text(yen(it.amount()), style = plex(15, 600).copy(fontFeatureSettings = "tnum"), color = c.tx)
        }
        if (open) ItemEditor(rc, i, withSur)
    }
}

@Composable
private fun ItemEditor(rc: Rc, i: Int, withSur: String?) {
    val it = rc.items[i]
    val kinds = listOf("amt+" to "＋円", "amt-" to "−円", "pct+" to "＋%", "pct-" to "−%")
    Column(Modifier.padding(bottom = 6.dp)) {
        Field("品名", it.n, { v -> setItem(rc, i) { p -> p.copy(n = v) } }, placeholder = "品名")
        if (it.lines.isEmpty()) {
            Field("金額", if (it.price == 0) "" else it.price.toString(), { v ->
                setItem(rc, i) { p -> p.copy(price = digitsOf(v)) }
            }, placeholder = "¥0", divider = true, amount = true, number = true)
        } else {
            V4Row(t = "本体 + オプション", v = yen(it.amount()), divider = true)
        }
        RowField("税率", divider = true) {
            SChip("税8%", it.tax == 8) { setItem(rc, i) { p -> p.copy(tax = 8) } }
            SChip("税10%", it.tax == 10) { setItem(rc, i) { p -> p.copy(tax = 10) } }
        }
        if (withSur != null) {
            RowField("負担", divider = true) {
                SChip("自分", it.b == "自分") { setItem(rc, i) { p -> p.copy(b = "自分") } }
                SChip("半分", it.b == "半分") { setItem(rc, i) { p -> p.copy(b = "半分") } }
                SChip(withSur, it.b == withSur) { setItem(rc, i) { p -> p.copy(b = withSur) } }
            }
        }
        it.lines.forEachIndexed { li, line ->
            if (li == 0) {
                Field("本体", line.v.toString(), { v ->
                    setItem(rc, i) { p -> p.copy(lines = p.lines.toMutableList().also { l -> l[0] = l[0].copy(v = digitsOf(v)) }) }
                }, divider = true, number = true)
            } else {
                val key = (if (line.k == "pct") "pct" else "amt") + (if (line.v >= 0) "+" else "-")
                Field("名前", line.n, { v ->
                    setItem(rc, i) { p -> p.copy(lines = p.lines.toMutableList().also { l -> l[li] = l[li].copy(n = v) }) }
                }, placeholder = "オプション・割引", divider = true)
                SelectRow("種類", kinds.first { it.first == key }.second, kinds, divider = true) { k ->
                    setItem(rc, i) { p ->
                        val sign = if (k.endsWith("+")) 1 else -1
                        val kind = if (k.startsWith("pct")) "pct" else "amt"
                        p.copy(lines = p.lines.toMutableList().also { l -> l[li] = l[li].copy(k = kind, v = sign * abs(l[li].v)) })
                    }
                }
                Field("金額", abs(line.v).toString(), { v ->
                    setItem(rc, i) { p ->
                        val sign = if (line.v < 0) -1 else 1
                        p.copy(lines = p.lines.toMutableList().also { l -> l[li] = l[li].copy(v = sign * digitsOf(v)) })
                    }
                }, divider = true, number = true)
                GhostButton("この行を削除", color = V4.c.tx2) {
                    setItem(rc, i) { p -> p.copy(lines = p.lines.toMutableList().also { l -> l.removeAt(li) }) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TonalButton("オプション・割引を追加", small = true) {
                setItem(rc, i) { p ->
                    val base = if (p.lines.isEmpty()) listOf(com.amgm.personallog.v4.data.AmtLine("本体", "amt", p.price)) else p.lines
                    p.copy(lines = base + com.amgm.personallog.v4.data.AmtLine("", "amt", 0))
                }
            }
            DeleteButton("削除") {
                rc.items.removeAt(i)
                rc.openItem = -1
            }
        }
    }
}
