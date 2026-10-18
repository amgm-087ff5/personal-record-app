package com.amgm.personallog.ui.members

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.amgm.personallog.data.model.Person

/**
 * メンバーの名前入力欄。タップすると登録メンバー(members)が候補に出て、入力すると部分一致で絞り込む。
 * 候補を選ぶと onPickMember。空欄でもよい(そのときは placeholder に「メンバー1」などを薄く出す)。実装はタスク B6。
 */
@Composable
fun MemberNameField(
    value: String,
    onValueChange: (String) -> Unit,
    members: List<Person>,
    onPickMember: (Person) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
): Unit = TODO("B6")

/**
 * 「メンバーを選ぶ」ダイアログ。登録メンバーを複数選んで一度に追加する。alreadySelected の人は選択済みで出す(外せない)。実装はタスク B6。
 */
@Composable
fun MemberPickerDialog(
    members: List<Person>,
    alreadySelected: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Person>) -> Unit,
): Unit = TODO("B6")

/** 設定のメンバー管理画面の行き先。画面はタスク B6。 */
object MembersRoutes {
    const val LIST = "settings/members"
}
