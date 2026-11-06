package com.amgm.personallog.v4.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.screens.activities.ActivitiesScreen
import com.amgm.personallog.v4.screens.activities.DayScreen
import com.amgm.personallog.v4.screens.create.SettingsScreen
import com.amgm.personallog.v4.screens.detail.ActivityDetailScreen
import com.amgm.personallog.v4.screens.detail.ItemDetailScreen
import com.amgm.personallog.v4.screens.entities.EntitiesScreen
import com.amgm.personallog.v4.screens.entities.EntityDetailScreen
import com.amgm.personallog.v4.screens.entities.TypesScreen
import com.amgm.personallog.v4.screens.home.HomeScreen
import com.amgm.personallog.v4.screens.ledger.CatsScreen
import com.amgm.personallog.v4.screens.ledger.ExpenseDetailScreen
import com.amgm.personallog.v4.screens.ledger.LedgerScreen
import com.amgm.personallog.v4.screens.memo.MemoScreen
import com.amgm.personallog.v4.screens.memo.NoteScreen
import com.amgm.personallog.v4.screens.memo.NotesScreen
import com.amgm.personallog.v4.screens.plan.EventDetailScreen
import com.amgm.personallog.v4.screens.plan.PlanScreen
import com.amgm.personallog.v4.screens.plan.TaskDetailScreen
import com.amgm.personallog.v4.screens.plan.TodoScreen

/*
 * 全体の枠 — index.html の #app / #nav / #sheet / #toast と ui.js の render() に当たる。
 * ルート名 → 画面(ROUTES)。名前はモックの route('…') と同じ。
 */
@Composable
fun ScreenFor(r: Route) {
    when (r.name) {
        "home" -> HomeScreen(r)
        "activities" -> ActivitiesScreen(r)
        "day" -> DayScreen(r)
        "a" -> ActivityDetailScreen(r)
        "i" -> ItemDetailScreen(r)
        "todo" -> TodoScreen(r)
        "plan" -> PlanScreen(r)
        "ev" -> EventDetailScreen(r)
        "task" -> TaskDetailScreen(r)
        "memo" -> MemoScreen(r)
        "n" -> NoteScreen(r)
        "notes" -> NotesScreen(r)
        "entities" -> EntitiesScreen(r)
        "e" -> EntityDetailScreen(r)
        "types" -> TypesScreen(r)
        "ledger" -> LedgerScreen(r)
        "x" -> ExpenseDetailScreen(r)
        "cats" -> CatsScreen(r)
        "settings" -> SettingsScreen(r)
        else -> ActivitiesScreen(r)
    }
}

/** 画面が読むデータ(StateFlow を Compose の状態にしたもの) */
val LocalData = androidx.compose.runtime.compositionLocalOf { AppData() }

@Composable
fun AppRoot(app: AppState) {
    val data by app.store.data.collectAsState()
    val ready by app.store.ready.collectAsState()
    V4Theme(data.settings.theme) {
        CompositionLocalProvider(LocalApp provides app, LocalData provides data) {
            Box(Modifier.fillMaxSize().background(V4.c.bg).background(V4.c.bgGrad)) {
                if (ready) {
                    Crossfade(targetState = app.cur, animationSpec = tween(140), label = "screen") { r ->
                        Box(Modifier.fillMaxSize()) { ScreenFor(r) }
                    }
                }
                BottomNav(Modifier.align(Alignment.BottomCenter))
                ToastHost(Modifier.align(Alignment.BottomCenter))
                SheetHost()
            }
        }
    }
}

/** まだ移植していない画面(土台の確認用) */
@Composable
fun StubScreen(name: String, r: Route) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = V4Dim.gutter)) {
        Spacer(Modifier.height(24.dp))
        Text(name, style = V4.t.display, color = V4.c.tx)
        Text("route=${r.name} args=${r.args}", style = V4.t.body, color = V4.c.tx2, modifier = Modifier.fillMaxWidth())
    }
}
