package com.amgm.personallog.v4.screens.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.components.Ico
import com.amgm.personallog.v4.ui.components.press
import com.amgm.personallog.v4.ui.lightBrush
import com.amgm.personallog.v4.ui.plex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * 作成(中央の＋と各作成シート)— create.js。公開の入口は署名を変えない。
 * 実装は CreateSheets.kt(金額・出来事・予定・メモ・エンティティ・日記)と CreateReceipt.kt(レシート)。
 */

/** 中央の＋: 作成の一覧(いまのタブで一番使うものを先頭・塗り) */
fun openCreate(app: AppState) {
    val tab = app.lastTab
    app.openSheet(title = "作成") { CreateList(app, tab) }
}

private class CreateItem(
    val label: String,
    val icon: ImageVector,
    val color: @Composable () -> Color,
    val primaryOn: List<String>,
    val action: (AppState) -> Unit,
)

private val CREATE_ITEMS = listOf(
    CreateItem("ToDo", V4Icons.todo, { V4.c.tx2 }, listOf("todo")) { openPlanSheet(it, todo = true) },
    CreateItem("金額だけで支払い", V4Icons.pay, { V4.c.ev }, emptyList()) { openPaymentSheet(it) },
    CreateItem("レシートで支払い", V4Icons.pay, { V4.c.ev }, listOf("home", "ledger", "settings")) { openReceiptSheet(it) },
    CreateItem("出来事", V4Icons.moment, { V4.c.ev }, listOf("activities")) { openActivitySheet(it) },
    CreateItem("予定", V4Icons.future, { V4.c.plan }, listOf("activities")) { openPlanSheet(it) },
    CreateItem("エンティティ", V4Icons.kin, { V4.c.kin }, listOf("entities")) { openEntitySheet(it) },
    CreateItem("今日の日記", V4Icons.diary, { V4.c.ev }, emptyList()) { openDiarySheet(it, it.today) },
    CreateItem("メモ", V4Icons.memo, { V4.c.ev }, listOf("memo")) { openMemoSheet(it) },
)

@Composable
private fun CreateList(app: AppState, tab: String) {
    val scope = rememberCoroutineScope()
    // 主の行 = 今のタブの primaryOn に入る最初の1件(無ければ登録の先頭)。残りは登録の順
    val primary = CREATE_ITEMS.firstOrNull { tab in it.primaryOn } ?: CREATE_ITEMS.first()
    val rest = CREATE_ITEMS.filter { it !== primary }
    Column(Modifier.padding(horizontal = V4Dim.gutter, vertical = 4.dp)) {
        CreateRow(app, scope, primary, true)
        rest.forEach { CreateRow(app, scope, it, false) }
    }
}

@Composable
private fun CreateRow(app: AppState, scope: kotlinx.coroutines.CoroutineScope, item: CreateItem, primary: Boolean) {
    val c = V4.c
    val click: () -> Unit = {
        // シートを閉じて 120ms 後に実行(モックと同じ)
        app.closeSheet()
        scope.launch { delay(120); item.action(app) }
        Unit
    }
    if (primary) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 56.dp)
                .clip(RoundedCornerShape(10.dp)).background(c.meFill).background(lightBrush())
                .press(onClick = click).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        ) {
            Ico(item.icon, onFill = true)
            Text(item.label, style = plex(15, 500, 1.3), color = Color.White)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).press(onClick = click).padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        ) {
            Ico(item.icon, item.color())
            Text(item.label, style = plex(15, 500, 1.3), color = c.tx)
        }
    }
}

/** 金額だけで支払い。parent=親のアクティビティ、date/time=引き継ぐ日付・時刻 */
fun openPaymentSheet(app: AppState, parent: String? = null, date: String? = null, time: String? = null) =
    openQuickPayment(app, parent, date, time)

/** レシートで支払い */
fun openReceiptSheet(app: AppState) = openReceipt(app)

/** アクティビティ(期間 or 瞬間)を作る。parent=親、date/time=引き継ぎ */
fun openActivitySheet(app: AppState, instant: Boolean = false, parent: String? = null, date: String? = null, time: String? = null) =
    openHappening(app, instant, parent, date, time)

/** 活動の中に子を作る: 種類(支払い / 期間 / 瞬間)を選ぶシート → 親・日付・時刻を引き継ぐ */
fun openAddKidSheet(app: AppState, parent: String) = openKidSheet(app, parent)

/** 予定(todo=false)か ToDo(todo=true)を作る。date=日付、ev=親の予定(ToDo) */
fun openPlanSheet(app: AppState, date: String? = null, todo: Boolean = false, ev: String? = null) =
    openPlan(app, date, todo, ev)

/** メモを書く。link=付けるエンティティ */
fun openMemoSheet(app: AppState, link: String? = null) = openMemo(app, link)

/** エンティティを追加 */
fun openEntitySheet(app: AppState) = openEntity(app)

/** 日記を書く・直す */
fun openDiarySheet(app: AppState, date: String) = openDiary(app, date)
