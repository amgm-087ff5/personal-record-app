package com.amgm.personallog.ui.members

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.ScreenHeader
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable

/**
 * 設定から開くメンバー管理画面。登録メンバーの一覧、名前での追加、行をタップしての名前変更・削除。
 * 割り勘で使われている人は削除できず、その旨を画面に出す。
 */
@Composable
fun MembersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MembersViewModel = hiltViewModel(),
) {
    val members by viewModel.members.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val t = AppTheme.type
    var newName by remember { mutableStateOf("") }
    // 変更シートを開いている人(null なら閉じている)。
    var editing by remember { mutableStateOf<Person?>(null) }

    PaperScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            ScreenHeader(
                kicker = "割り勘",
                title = "メンバー",
                trailing = { TextAction(text = "戻る", onClick = onBack) },
            )

            Column(modifier = Modifier.padding(horizontal = Space.edge)) {
                // 追加
                Label("追加")
                Spacer(Modifier.height(Space.s))
                Hairline()
                Spacer(Modifier.height(Space.m))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val addNow = {
                        viewModel.add(newName)
                        newName = ""
                    }
                    BareField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        underline = true,
                        placeholder = "名前",
                        keyboardActions = KeyboardActions(onDone = { addNow() }),
                    )
                    TextAction(
                        text = "追加",
                        onClick = addNow,
                        enabled = newName.isNotBlank(),
                    )
                }

                viewModel.message?.let { text ->
                    Spacer(Modifier.height(Space.s))
                    Text(text, style = t.caption, color = c.danger)
                }

                Spacer(Modifier.height(Space.xl))

                // 一覧
                Label("登録メンバー")
                Spacer(Modifier.height(Space.s))
                Hairline()

                if (members.isEmpty()) {
                    Spacer(Modifier.height(Space.m))
                    Text(
                        "まだメンバーがいません。上の欄から名前を入れて追加できます。",
                        style = t.body,
                        color = c.inkSoft,
                    )
                } else {
                    members.forEach { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .tappable { editing = person },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                person.name,
                                style = t.body,
                                color = c.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text("変更", style = t.caption, color = c.accent)
                        }
                        Hairline()
                    }
                }

                Spacer(Modifier.height(Space.xl))
            }
        }
    }

    editing?.let { person ->
        MemberEditSheet(
            person = person,
            onDismiss = { editing = null },
            onRename = { name ->
                viewModel.rename(person, name)
                editing = null
            },
            onDelete = {
                viewModel.delete(person)
                editing = null
            },
        )
    }
}

/** メンバー1人の名前変更・削除のシート。 */
@Composable
private fun MemberEditSheet(
    person: Person,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val c = AppTheme.colors
    var name by remember(person.id) { mutableStateOf(person.name) }

    InkSheet(onDismiss = onDismiss) {
        Label("メンバーの変更")
        Spacer(Modifier.height(Space.s))
        BareField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            underline = true,
            placeholder = "名前",
        )
        Spacer(Modifier.height(Space.m))
        InkButton(
            text = "保存",
            onClick = { onRename(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.s))
        TextAction(
            text = "削除",
            onClick = onDelete,
            color = c.danger,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
