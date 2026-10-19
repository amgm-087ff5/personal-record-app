package com.amgm.personallog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.LeaderRow
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.ScreenHeader
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable

/**
 * 巻末の奥付(コロフォン)風の設定画面。
 * onOpenMembers は「メンバー」の行を押したときの行き先(メンバー管理画面)。
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenMembers: () -> Unit = {},
) {
    val context = LocalContext.current
    val info = remember { AppInfo.read(context) }
    val c = AppTheme.colors
    val t = AppTheme.type

    PaperScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            ScreenHeader(kicker = "あとがき", title = "設定")

            Column(modifier = Modifier.padding(horizontal = Space.edge)) {
                // 接続
                Label("接続")
                Spacer(Modifier.height(Space.s))
                Hairline()
                Spacer(Modifier.height(Space.m))

                Label("Anthropic APIキー")
                BareField(
                    value = viewModel.apiKey,
                    onValueChange = { viewModel.apiKey = it; viewModel.saved = false },
                    singleLine = true,
                    underline = true,
                    placeholder = "sk-ant-...",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(Space.l))

                Label("Claudeモデル ID")
                BareField(
                    value = viewModel.modelId,
                    onValueChange = { viewModel.modelId = it; viewModel.saved = false },
                    singleLine = true,
                    underline = true,
                )
                Spacer(Modifier.height(Space.s))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    TextAction(text = "保存", onClick = { viewModel.save() })
                    if (viewModel.saved) {
                        Text("保存しました", style = t.caption, color = c.moss)
                    }
                }

                Spacer(Modifier.height(Space.xl))

                // 割り勘
                Label("負担割り")
                Spacer(Modifier.height(Space.s))
                Hairline()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .tappable(onOpenMembers),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("メンバー", style = t.body, color = c.ink, modifier = Modifier.weight(1f))
                    Text("›", style = t.body, color = c.inkFaint)
                }
                Hairline()

                Spacer(Modifier.height(Space.xl))

                // アプリ情報
                Label("アプリ情報")
                Spacer(Modifier.height(Space.s))
                Hairline()
                Spacer(Modifier.height(Space.m))

                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    LeaderRow(label = "バージョン", value = info.versionName)
                    LeaderRow(label = "ビルド番号", value = info.versionCode.toString())
                }
                Spacer(Modifier.height(Space.l))

                Label("署名 SHA-256")
                Spacer(Modifier.height(Space.xs))
                SelectionContainer {
                    Text(
                        info.signatureSha256,
                        style = t.caption.copy(fontFamily = FontFamily.Monospace),
                        color = c.ink,
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    "ビルド間で同じなら上書き更新できます。",
                    style = t.caption,
                    color = c.inkSoft,
                )

                Spacer(Modifier.height(Space.xl))
            }
        }
    }
}
