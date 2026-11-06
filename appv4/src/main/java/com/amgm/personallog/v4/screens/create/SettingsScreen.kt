package com.amgm.personallog.v4.screens.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.ui.AppState
import com.amgm.personallog.v4.ui.LocalApp
import com.amgm.personallog.v4.ui.LocalData
import com.amgm.personallog.v4.ui.Route
import com.amgm.personallog.v4.ui.V4
import com.amgm.personallog.v4.ui.V4Dim
import com.amgm.personallog.v4.ui.V4Icons
import com.amgm.personallog.v4.ui.components.Banner
import com.amgm.personallog.v4.ui.components.BtnRow
import com.amgm.personallog.v4.ui.components.Ico
import com.amgm.personallog.v4.ui.components.LargeTitle
import com.amgm.personallog.v4.ui.components.ListCard
import com.amgm.personallog.v4.ui.components.Prop
import com.amgm.personallog.v4.ui.components.Screen
import com.amgm.personallog.v4.ui.components.SectionHead
import com.amgm.personallog.v4.ui.components.TonalButton
import com.amgm.personallog.v4.ui.components.V4Row
import com.amgm.personallog.v4.ui.components.press
import com.amgm.personallog.v4.ui.plex

/*
 * 設定(route('settings'))。表示(テーマ)・記録の分類・データ・このモックについて。
 * 「データを初期状態に戻す」は確認のシートを開く。
 */
@Composable
fun SettingsScreen(r: Route) {
    val app = LocalApp.current
    val d = LocalData.current
    val c = V4.c
    val cur = d.settings.theme
    Screen {
        LargeTitle("設定")
        SectionHead("表示")
        ListCard {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("テーマ", style = plex(15, 500, 1.4), color = c.tx)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("system" to "端末に合わせる", "light" to "ライト", "dark" to "ダーク").forEach { (v, label) ->
                        ThemePill(label, cur == v) {
                            // テーマは取り消しの対象にしない
                            app.store.update(undoable = false) { x -> x.copy(settings = x.settings.copy(theme = v)) }
                            app.rerender()
                        }
                    }
                }
            }
        }

        SectionHead("記録の分類")
        ListCard {
            V4Row(
                t = "カテゴリ", v = "${d.cats.size}件", chev = true,
                lead = { Ico(V4Icons.cats, c.tx2) }, onClick = { app.go("cats") },
            )
            V4Row(
                t = "エンティティの種類", v = "${d.types.size}件", chev = true, divider = true,
                lead = { Ico(V4Icons.type, c.tx2) }, onClick = { app.go("types") },
            )
            V4Row(
                t = "ToDo のリスト", v = "${d.lists.size}件", chev = true, divider = true,
                lead = { Ico(V4Icons.todo, c.tx2) }, onClick = { app.toast("ToDo のリストは未対応です") },
            )
        }

        SectionHead("データ")
        ListCard {
            Prop("保存先", "この端末(アプリ内)")
            V4Row(
                t = "データを初期状態に戻す", tColor = c.danger, chev = true, divider = true,
                lead = { Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.danger.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) { Icon(V4Icons.trash, null, tint = c.danger, modifier = Modifier.size(18.dp)) } },
                onClick = { setResetConfirm(app) },
            )
        }

        SectionHead("このアプリについて")
        ListCard {
            Prop("版", "UI v4.15 · Android")
            Prop("今日", "${Dates.parse(app.today).year}年${Dates.fmtMDW(app.today)}", divider = true)
        }
    }
}

/** テーマの選択肢(押せる高さ44) */
@Composable
private fun ThemePill(label: String, on: Boolean, onClick: () -> Unit) {
    val c = V4.c
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(if (on) c.meSoft else c.sunk)
            .press(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = plex(15, if (on) 600 else 500), color = if (on) c.me else c.tx2, maxLines = 1, softWrap = false)
    }
}

/** データを初期状態に戻す(確認) */
fun setResetConfirm(app: AppState) {
    app.openSheet(title = "データを初期状態に戻す") {
        Column {
            Banner(warn = true, text = "この端末の記録・予定・メモをすべて消して、初期状態に戻します")
            BtnRow {
                TonalButton("やめる", modifier = Modifier.weight(1f)) { app.closeSheet() }
                Row(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)).background(V4.c.danger)
                        .press(onClick = {
                            app.store.reset()
                            app.go("home")
                        }),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(V4Icons.trash, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("初期状態に戻す", style = plex(15, 600), color = Color.White, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
