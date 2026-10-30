package com.amgm.personallog.ui.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
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

/**
 * メンバーの名前入力欄。タップすると登録メンバー(members)が候補に出て、入力すると部分一致で絞り込む。
 * 候補を選ぶと onPickMember(候補は閉じる。欄の文字をどう扱うかは呼び出し側が決める)。
 * 空欄でもよい(そのときは placeholder を薄く出す)。候補が0件なら一覧は出さない。
 */
@Composable
fun MemberNameField(
    value: String,
    onValueChange: (String) -> Unit,
    members: List<Person>,
    onPickMember: (Person) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // 入力が空ならフォーカス中は登録メンバー全件、入力があれば絞り込んだ結果を出す。
    val candidates = remember(members, value) { filterMembers(members, value) }
    val showList = focused && candidates.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        // 下線つきの BareField(IME変換中の状態を保つ共通部品)。フォーカスは子の入力欄にあるので hasFocus で見る。
        BareField(
            value = value,
            onValueChange = onValueChange,
            underline = true,
            singleLine = true,
            placeholder = placeholder,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.hasFocus },
        )

        if (showList) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.xs)
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .background(c.paperRaised, RoundedCornerShape(12.dp)),
            ) {
                candidates.forEach { person ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                focusManager.clearFocus()
                                onPickMember(person)
                            }
                            .padding(horizontal = Space.m),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(person.name, style = t.body, color = c.ink)
                    }
                }
            }
        }
    }
}

/**
 * 「メンバーを選ぶ」の選択シート。登録メンバーを複数チェックで選び、「追加」で新しく選んだ人だけ返す。
 * alreadySelected の人は選択済みで出し、外せない。上の欄で名前を絞り込める(MemberNameField と同じ絞り込み)。
 * 登録メンバーが0人のときは案内文だけを出す。
 */
@Composable
fun MemberPickerDialog(
    members: List<Person>,
    alreadySelected: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Person>) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    val shown = remember(members, query) { filterMembers(members, query) }
    // 今回新しく選んだ人(alreadySelected は含めない)。
    val newlyPicked = members.filter { it.id in picked && it.id !in alreadySelected }

    InkSheet(onDismiss = onDismiss) {
        Label("メンバーを選ぶ")
        Spacer(Modifier.height(Space.s))
        Hairline()
        Spacer(Modifier.height(Space.s))

        if (members.isEmpty()) {
            Text(
                "登録されたメンバーがいません。名前を入れて保存すると登録されます",
                style = t.body,
                color = c.inkSoft,
            )
            Spacer(Modifier.height(Space.m))
            TextAction(text = "閉じる", onClick = onDismiss, color = c.inkSoft, modifier = Modifier.fillMaxWidth())
            return@InkSheet
        }

        BareField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            underline = true,
            placeholder = "名前で絞り込む",
        )
        Spacer(Modifier.height(Space.s))

        if (shown.isEmpty()) {
            Text("一致するメンバーがいません", style = t.caption, color = c.inkSoft)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(shown, key = { it.id }) { person ->
                    val locked = person.id in alreadySelected
                    val checked = locked || person.id in picked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = !locked) {
                                picked = if (person.id in picked) picked - person.id else picked + person.id
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null, // 行全体で切り替える
                            enabled = !locked,
                            colors = CheckboxDefaults.colors(
                                checkedColor = c.accent,
                                checkmarkColor = c.onAccent,
                            ),
                        )
                        Spacer(Modifier.width(Space.s))
                        Text(
                            person.name,
                            style = t.body,
                            color = if (locked) c.inkFaint else c.ink,
                            modifier = Modifier.weight(1f),
                        )
                        if (locked) Text("追加済み", style = t.caption, color = c.inkFaint)
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.m))
        InkButton(
            text = if (newlyPicked.isEmpty()) "追加" else "追加（${newlyPicked.size}人）",
            onClick = { onConfirm(newlyPicked) },
            enabled = newlyPicked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.xs))
        TextAction(text = "閉じる", onClick = onDismiss, color = c.inkSoft, modifier = Modifier.fillMaxWidth())
    }
}

/** 設定のメンバー管理画面の行き先。 */
object MembersRoutes {
    const val LIST = "settings/members"
}
