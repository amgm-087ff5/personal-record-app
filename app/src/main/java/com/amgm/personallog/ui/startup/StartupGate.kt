package com.amgm.personallog.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.ui.AppRoot
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.sheet
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

/** 起動時にDBを開いて確認し、失敗したらクラッシュせず原因を画面に表示する。 */
@Composable
fun StartupGate() {
    val vm: StartupViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    PaperScreen {
        when (val s = state) {
            StartupState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Label("読み込み中", color = AppTheme.colors.inkSoft)
            }
            StartupState.Ready -> AppRoot()
            is StartupState.Failed -> DbErrorScreen(s.detail)
        }
    }
}

@Composable
private fun DbErrorScreen(detail: String) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Space.edge, vertical = Space.xl),
    ) {
        Label("起動エラー", color = c.danger)
        Spacer(Modifier.height(Space.s))
        Text("起動できませんでした", style = t.headline, color = c.danger)
        Spacer(Modifier.height(Space.m))
        Text(
            "データベースを開けませんでした。" +
                "アプリは終了せずにこの画面を表示しています。下の内容を開発担当に伝えてください。" +
                "データを消したくない場合は、アンインストールせずに最新版を上書きインストールしてください。",
            style = t.body,
            color = c.ink,
        )
        Spacer(Modifier.height(Space.l))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .sheet(RoundedCornerShape(14.dp), depth = 8.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(c.accent),
            )
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    detail,
                    style = t.caption.copy(fontFamily = FontFamily.Monospace),
                    color = c.ink,
                    modifier = Modifier.padding(Space.m),
                )
            }
        }
    }
}
