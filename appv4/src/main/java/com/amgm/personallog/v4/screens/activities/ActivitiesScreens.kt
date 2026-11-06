package com.amgm.personallog.v4.screens.activities

import androidx.compose.runtime.Composable
import com.amgm.personallog.v4.data.actsOn
import com.amgm.personallog.v4.data.evOn
import com.amgm.personallog.v4.data.parentAct
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.Route
import com.amgm.personallog.v4.ui.StubScreen
import com.amgm.personallog.v4.ui.components.AxColumn
import com.amgm.personallog.v4.ui.components.AxEdge
import com.amgm.personallog.v4.ui.components.DayHead
import com.amgm.personallog.v4.ui.components.MomentRow
import com.amgm.personallog.v4.ui.components.NowLine
import com.amgm.personallog.v4.ui.components.PlanRowAx
import com.amgm.personallog.v4.ui.components.Screen

@Composable
fun ActivitiesScreen(r: Route) = StubScreen("ActivitiesScreen", r)

/** 本体の確認用(担当の実装で置き換わる) */
@Composable
fun DayScreen(r: Route) {
    val d = LocalData.current; val app = LocalApp.current
    Screen {
        AxColumn {
            AxEdge("これより前の記録 なし")
            for (day in listOf("2026-10-29", "2026-10-30", app.today)) {
                DayHead(day, diary = d.acts.values.firstOrNull { it.type == "日記" && it.date == day }?.body, empty = if (day == app.today) "今日の日記を書く" else null, sum = if (day == "2026-10-30") 3340 else null, first = day == "2026-10-29")
                d.actsOn(day).filter { d.parentAct(it.first) == null }.sortedBy { it.second.time }.forEach { MomentRow(it.first) }
                if (day == app.today) { d.evOn(day).filter { it.second.s != null && it.second.s!! < app.now }.forEach { PlanRowAx(it.first, future = false, late = true) }; NowLine(app.now); d.evOn(day).filter { it.second.s != null && it.second.s!! >= app.now }.forEach { PlanRowAx(it.first) } }
            }
            AxEdge("これより先の予定 3件", past = false, chev = true) {}
        }
    }
}
