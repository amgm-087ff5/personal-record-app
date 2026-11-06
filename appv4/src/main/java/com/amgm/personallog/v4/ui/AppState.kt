package com.amgm.personallog.v4.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Store

/*
 * 画面の状態 — ui.js のルーター(go / goBack / lastTab)・シート(openSheet / closeSheet)・トースト(toast)を移したもの。
 * 画面からは val app = LocalApp.current で使う:
 *   app.go("a", id)                … #/a/{id} へ(ルート名と引数)
 *   app.goBack()
 *   app.openSheet(title = "…", mainLabel = "保存", onMain = { … }) { 本文 }
 *   app.closeSheet()
 *   app.toast("保存しました", undo = true)   … undo=true なら「取り消す」で Store.undo()
 *   app.store.update { d -> d.copy(...) }
 */

/** #/name/a/b?k=v に当たる。args = スラッシュの後ろ、q = クエリ */
data class Route(val name: String, val args: List<String> = emptyList(), val q: Map<String, String> = emptyMap()) {
    val id: String? get() = args.firstOrNull()
    fun arg(i: Int): String? = args.getOrNull(i)
}

/** 下部ナビのタブ(ルートの名前と同じ) */
object Tabs {
    const val HOME = "home"; const val ACTIVITIES = "activities"; const val TODO = "todo"; const val MEMO = "memo"
    const val ENTITIES = "entities"; const val LEDGER = "ledger"; const val SETTINGS = "settings"
    val ROOTS = setOf(HOME, ACTIVITIES, TODO, MEMO, ENTITIES, LEDGER, SETTINGS)
    /** NAV_PAGES: 1枚目(毎日)・2枚目(整理)。null=中央の＋、"blank"=空き */
    val PAGES: List<List<Pair<String, String>?>> = listOf(
        listOf(HOME to "ホーム", ACTIVITIES to "アクティビティ", null, TODO to "ToDo", MEMO to "メモ"),
        listOf(ENTITIES to "エンティティ", LEDGER to "家計簿", null, SETTINGS to "設定", "blank" to ""),
    )
    fun pageOf(tab: String): Int = PAGES.indexOfFirst { p -> p.any { it?.first == tab } }.coerceAtLeast(0)
}

/** シートの中身。title・左の操作(無ければ ✕)・下の主ボタン・本文 */
class SheetSpec(
    val title: String,
    val mainLabel: String? = null,
    val mainEnabled: () -> Boolean = { true },
    val onMain: (() -> Unit)? = null,
    val leftLabel: String? = null,
    val onLeft: (() -> Unit)? = null,
    val full: Boolean = false,
    val content: @Composable () -> Unit,
)

class ToastSpec(val msg: String, val undo: Boolean, val serial: Int)

@Stable
class AppState(val store: Store) {
    val stack = mutableStateListOf(Route(Tabs.HOME))
    /** 詳細を開いても下部ナビのタブは変わらない(どこから来たかを失わない) */
    var lastTab by mutableStateOf(Tabs.HOME)
        private set
    /** 下部ナビの今の枚目(0=毎日 / 1=整理) */
    var navPage by mutableIntStateOf(0)
    var sheet by mutableStateOf<SheetSpec?>(null)
        private set
    var toast by mutableStateOf<ToastSpec?>(null)
        private set
    private var toastSerial = 0
    /** 同じ画面を描き直す合図(rerender)。データは StateFlow なので普段は不要 */
    var tick by mutableIntStateOf(0)

    val cur: Route get() = stack.last()
    val data: AppData get() = store.data.value
    val today: String get() = store.clock.today()
    val now: String get() = store.clock.now()

    fun go(name: String, vararg args: String, q: Map<String, String> = emptyMap()) = go(Route(name, args.toList(), q))
    fun go(r: Route) {
        sheet = null
        if (r.name in Tabs.ROOTS) {
            // タブへ: 積み上げをそのタブだけにする
            stack.clear(); stack.add(r); lastTab = r.name; navPage = Tabs.pageOf(r.name)
        } else stack.add(r)
    }
    /** "#/a/l1" の形でも開ける(本文の参照など) */
    fun goHash(h: String) {
        val s = h.removePrefix("#/").removePrefix("/")
        val (path, qs) = s.split("?", limit = 2).let { it[0] to it.getOrNull(1) }
        val parts = path.split("/").filter { it.isNotEmpty() }
        val q = qs?.split("&")?.mapNotNull { kv -> kv.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to java.net.URLDecoder.decode(it[1], "UTF-8") } }?.toMap() ?: emptyMap()
        if (parts.isEmpty()) go(Tabs.HOME) else go(Route(parts[0], parts.drop(1), q))
    }
    fun goBack(): Boolean {
        if (sheet != null) { sheet = null; return true }
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        if (cur.name != Tabs.HOME) { go(Tabs.HOME); return true }
        return false
    }
    fun replace(r: Route) { if (stack.isNotEmpty()) stack[stack.lastIndex] = r else stack.add(r) }

    fun openSheet(
        title: String,
        mainLabel: String? = null,
        onMain: (() -> Unit)? = null,
        mainEnabled: () -> Boolean = { true },
        leftLabel: String? = null,
        onLeft: (() -> Unit)? = null,
        full: Boolean = false,
        content: @Composable () -> Unit,
    ) { sheet = SheetSpec(title, mainLabel, mainEnabled, onMain, leftLabel, onLeft, full, content) }
    fun closeSheet() { sheet = null }

    /** 操作の結果。undo=true なら「取り消す」(Store.undo) を出し5秒、無ければ2.6秒 */
    fun toast(msg: String, undo: Boolean = false) { toast = ToastSpec(msg, undo, ++toastSerial) }
    fun dismissToast(serial: Int) { if (toast?.serial == serial) toast = null }
    fun undo() { store.undo(); toast = null }
    fun rerender() { tick++ }
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState がありません") }
