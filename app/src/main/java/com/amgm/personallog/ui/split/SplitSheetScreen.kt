package com.amgm.personallog.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.repository.canSplitOutItems
import com.amgm.personallog.domain.split.IndividualExpense
import com.amgm.personallog.domain.split.IndividualLine
import com.amgm.personallog.domain.split.SplitWarning
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.LeaderRow
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Pill
import com.amgm.personallog.ui.design.Pills
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.sheet
import com.amgm.personallog.ui.design.tappable
import com.amgm.personallog.ui.members.MemberNameField
import com.amgm.personallog.ui.members.MemberPickerDialog
import com.amgm.personallog.ui.receipt.SheetChoice
import com.amgm.personallog.ui.receipt.SheetField
import com.amgm.personallog.ui.receipt.SheetTitle

/**
 * 全体支出の画面(割り勘シート)。支払い1件について、誰が何をどれだけ負担するかを全員分まとめて設定する。
 * 方式は「食べた分どおり」「全体で割る」の2つ。最初は自分1人で開き、メンバーは後から足す。
 * 参加者の「個別支出」からは、その人が関わったまとまりと割合・食べた分・負担・差を見られる。
 */
@Composable
fun SplitSheetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplitSheetViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var bulkOpen by rememberSaveable { mutableStateOf(false) }
    /** 行を開いている参加者(メモ・ラベル・支払者・削除)。0 は無し。 */
    var openMemberId by rememberSaveable { mutableStateOf(0L) }
    /** 開いているまとまり(割り方・対象者・共通費)。0 は無し。 */
    var openGroupId by rememberSaveable { mutableStateOf(0L) }
    /** 個別支出を開いている参加者の id(null は無し。参加者 id 0 は旧い既定の自分にあり得るので 0 を無しにしない)。 */
    var openIndividualId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** まとめる操作中か(まとまりを選ぶ)と、選んだまとまり。 */
    var mergeMode by remember { mutableStateOf(false) }
    var mergeSelection by remember { mutableStateOf(setOf<Long>()) }
    /** 割当や品目が消える分割・結合の確認待ち。 */
    var pendingOp by remember { mutableStateOf<GroupOp?>(null) }

    fun executeGroupOp(op: GroupOp) {
        when (op) {
            is GroupOp.Split -> viewModel.splitGroupItems(op.groupId, op.itemIds)
            is GroupOp.Merge -> {
                viewModel.mergeGroups(op.groupIds)
                mergeMode = false
                mergeSelection = emptySet()
            }
        }
    }

    /** 分割・結合の要求。割当が外れる・まとまりが消えるときだけ確認を出す。 */
    fun requestGroupOp(op: GroupOp) {
        val needsConfirm = when (op) {
            is GroupOp.Split -> viewModel.splitWouldEmpty(op.groupId, op.itemIds)
            is GroupOp.Merge -> viewModel.mergeWouldClear(op.groupIds)
        }
        if (needsConfirm) pendingOp = op else executeGroupOp(op)
    }

    LaunchedEffect(s.saved) { if (s.saved) onBack() }

    PaperScreen(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize().imePadding()) {
            // 上部: 戻る・タイトル・保存、合計
            item(key = "top") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextAction("戻る", onClick = onBack, color = c.inkSoft)
                    Box(modifier = Modifier.weight(1f))
                    Text("全体支出", style = AppTheme.type.title, color = c.ink)
                    Box(modifier = Modifier.weight(1f))
                    TextAction("保存", onClick = viewModel::save, enabled = !s.busy && s.data != null)
                }
            }
            item(key = "total") {
                Column(modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.s)) {
                    Label("支払いの合計")
                    Text(
                        s.total?.let { formatYenText(it) } ?: "未入力",
                        style = AppTheme.type.numeralL,
                        color = c.ink,
                    )
                }
            }

            if (s.loading) {
                item(key = "loading") { Label("読み込み中…", modifier = Modifier.padding(horizontal = Space.edge)) }
            }
            if (s.notFound) {
                item(key = "notFound") {
                    Text("支払いが見つかりません。", style = AppTheme.type.body, color = c.inkSoft, modifier = Modifier.padding(horizontal = Space.edge))
                }
            }
            s.error?.let { msg ->
                item(key = "error") {
                    Column(modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.s)) {
                        Text(msg, style = AppTheme.type.body, color = c.danger)
                        TextAction("閉じる", onClick = viewModel::dismissError, color = c.danger)
                    }
                }
            }

            val data = s.data
            if (data != null) {
                // 警告は上に並べる
                s.calcError?.let { msg ->
                    item(key = "calcError") {
                        Text(msg, style = AppTheme.type.body, color = c.danger, modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.xs))
                    }
                }
                // 同じ文言の警告が2つ出うるので、キーは並びの番号で作る(重複キーは LazyColumn で落ちる)
                itemsIndexed(s.warnings, key = { index, _ -> "warn-$index" }) { _, w ->
                    Text(w, style = AppTheme.type.caption, color = c.danger, modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.xs))
                }

                // 方式のタブ(排他)
                item(key = "tabs") {
                    Pills(
                        options = MODES,
                        selected = s.mode,
                        onSelect = viewModel::setMode,
                        label = { modeLabel(it) },
                        modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.s),
                    )
                }

                if (s.mode == SplitMode.WHOLE) {
                    item(key = "wholeKind") {
                        Column(modifier = Modifier.padding(horizontal = Space.edge)) {
                            Label("入力の種類", modifier = Modifier.padding(bottom = Space.s))
                            Pills(
                                options = listOf(ShareKind.AMOUNT, ShareKind.RATIO),
                                selected = s.wholeKind,
                                onSelect = viewModel::setWholeKind,
                                label = { if (it == ShareKind.AMOUNT) "金額" else "割合" },
                            )
                            Text(
                                "空欄は残りの額を均等に割ります(薄い数字が計算結果)。",
                                style = AppTheme.type.caption,
                                color = c.inkFaint,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                        }
                    }
                }

                // 参加人数(参加者が自分1人でも出す)
                item(key = "count") {
                    ParticipantCountRow(
                        count = s.participants.size,
                        canRemove = s.removableId != null,
                        onMinus = viewModel::removeLastBlankParticipant,
                        onPlus = viewModel::addBlankParticipant,
                        onBulk = { bulkOpen = true },
                    )
                }

                // メンバー欄(両方式で共通)
                item(key = "membersTitle") {
                    Label("メンバー", modifier = Modifier.padding(start = Space.edge, end = Space.edge, top = Space.l, bottom = Space.s))
                }
                if (s.mode == SplitMode.WHOLE && s.wholeWarnings.isNotEmpty()) {
                    item(key = "wholeWarnings") {
                        WholeWarningBlock(
                            warnings = s.wholeWarnings,
                            onEqualize = viewModel::equalizeWhole,
                            onTakeDifference = viewModel::takeDifferenceForWhole,
                        )
                    }
                }
                items(s.participants, key = { it.id }) { p ->
                    MemberRow(
                        s = s,
                        p = p,
                        mode = s.mode,
                        open = openMemberId == p.id,
                        onToggleOpen = { openMemberId = if (openMemberId == p.id) 0L else p.id },
                        onOpenIndividual = { openIndividualId = p.id },
                        onSetName = { viewModel.setName(p.id, it) },
                        onPickMember = { viewModel.pickMember(p.id, it) },
                        onSetWhole = { viewModel.setWholeValue(p.id, it) },
                        onSetMemo = { viewModel.setMemo(p.id, it) },
                        onSetLabel = { viewModel.setLabel(p.id, it) },
                        onSetPayer = { viewModel.setPayer(p.id, it) },
                        onRemove = { viewModel.removeParticipant(p.id) },
                    )
                }
                item(key = "memberActions") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        TextAction("メンバーを追加", onClick = { pickerOpen = true })
                    }
                }

                if (s.mode == SplitMode.WHOLE && s.labels.isNotEmpty()) {
                    item(key = "labelAmounts") {
                        Column(modifier = Modifier.padding(horizontal = Space.edge).padding(top = Space.l)) {
                            Label("ラベルごとの金額(1人あたり)")
                            Text(
                                "新人はいくら、その他はいくら、と一括で決められます。空欄は均等割。",
                                style = AppTheme.type.caption,
                                color = c.inkFaint,
                                modifier = Modifier.padding(bottom = Space.s),
                            )
                        }
                    }
                    items(listOf("") + s.labels, key = { "label-$it" }) { label ->
                        LabelAmountRow(s = s, label = label, onSet = { viewModel.setLabelAmount(label, it) })
                    }
                }

                if (s.mode == SplitMode.ITEM) {
                    item(key = "groupsTitle") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = Space.edge, end = Space.edge, top = Space.l, bottom = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Label("品目のまとまり")
                            Box(modifier = Modifier.weight(1f))
                            TextAction(
                                if (mergeMode) "やめる" else "まとめる",
                                onClick = {
                                    mergeMode = !mergeMode
                                    mergeSelection = emptySet()
                                },
                            )
                        }
                    }
                    if (mergeMode) {
                        item(key = "mergeAction") {
                            Column(modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.xs)) {
                                Text(
                                    "まとめるまとまりを2つ以上選びます。残すのは並びが一番早いまとまりです。",
                                    style = AppTheme.type.caption,
                                    color = c.inkFaint,
                                )
                                TextAction(
                                    "${mergeSelection.size}件をまとめる",
                                    onClick = { requestGroupOp(GroupOp.Merge(mergeSelection)) },
                                    enabled = mergeSelection.size >= 2,
                                )
                            }
                        }
                    }
                    if (s.groups.isEmpty()) {
                        item(key = "noGroups") {
                            Text("品目がありません。", style = AppTheme.type.caption, color = c.inkSoft, modifier = Modifier.padding(horizontal = Space.edge))
                        }
                    }
                    items(s.groups, key = { "group-${it.id}" }) { g ->
                        GroupCard(
                            g = g,
                            selecting = mergeMode,
                            selected = g.id in mergeSelection,
                            participantCount = s.participants.size,
                            warnings = s.groupWarnings[g.id].orEmpty(),
                            provisional = s.provisionalByGroup[g.id],
                            onOpen = { openGroupId = g.id },
                            onSelect = {
                                mergeSelection = if (g.id in mergeSelection) mergeSelection - g.id else mergeSelection + g.id
                            },
                            onEqualize = { viewModel.equalizeGroup(g.id) },
                            onTakeDifference = { viewModel.takeDifferenceForGroup(g.id) },
                        )
                    }
                    val unsetCount = s.groups.count { it.unset }
                    if (s.participants.size >= 2 && unsetCount > 0) {
                        item(key = "equalUnset") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.xs),
                            ) {
                                TextAction(
                                    "未設定(${unsetCount}件)を全員で均等にする",
                                    onClick = viewModel::equalizeUnsetGroups,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        MemberPickerDialog(
            members = s.members,
            alreadySelected = s.participants.mapNotNull { it.personId }.toSet(),
            onDismiss = { pickerOpen = false },
            onConfirm = { list ->
                pickerOpen = false
                viewModel.addMembers(list)
            },
        )
    }

    if (bulkOpen) {
        BulkAddSheet(
            current = s.participants.size,
            onDismiss = { bulkOpen = false },
            onConfirm = { count ->
                bulkOpen = false
                viewModel.addBlankParticipants(count)
            },
        )
    }

    // まとまりの編集シート
    val openGroup = s.groups.firstOrNull { it.id == openGroupId }
    if (openGroup != null) {
        GroupSheet(
            s = s,
            g = openGroup,
            onSetKind = { viewModel.setGroupKind(openGroup.id, it) },
            onToggle = { viewModel.toggleGroupParticipant(openGroup.id, it) },
            onSetValue = { pid, text -> viewModel.setGroupValue(openGroup.id, pid, text) },
            onSetPieces = { viewModel.setPieceCount(openGroup.id, it) },
            onSetShared = { viewModel.setShared(openGroup.id, it) },
            onSplit = { requestGroupOp(GroupOp.Split(openGroup.id, it)) },
            onDismiss = { openGroupId = 0L },
        )
    } else if (openGroupId != 0L) {
        // 開いていたまとまりが消えたら閉じる
        openGroupId = 0L
    }

    // 参加者の個別支出のシート(参加者が消えたら閉じる)
    val individualPerson = s.participants.firstOrNull { it.id == openIndividualId }
    if (individualPerson != null) {
        IndividualSheet(
            s = s,
            p = individualPerson,
            onDismiss = { openIndividualId = null },
        )
    } else if (openIndividualId != null) {
        openIndividualId = null
    }

    pendingOp?.let { op ->
        GroupOpConfirm(
            message = when (op) {
                is GroupOp.Split -> "元のまとまりは品目が無くなるので消えます。そのまとまりの割当も消えます。"
                is GroupOp.Merge -> "割当が揃わないため、まとめた後のまとまりは割当を外して未設定(自分)になります。"
            },
            onConfirm = {
                pendingOp = null
                executeGroupOp(op)
            },
            onDismiss = { pendingOp = null },
        )
    }

    s.headcountConfirm?.let { change ->
        HeadcountConfirm(
            message = headcountConfirmText(change),
            onYes = { viewModel.confirmHeadcount(updateHeadcount = true) },
            onCancel = { viewModel.confirmHeadcount(updateHeadcount = false) },
            onDismiss = viewModel::dismissHeadcountConfirm,
        )
    }
}

/** まとまりの分割・結合の要求(確認を出すかどうかは画面で決める)。 */
private sealed interface GroupOp {
    data class Split(val groupId: Long, val itemIds: Set<Long>) : GroupOp
    data class Merge(val groupIds: Set<Long>) : GroupOp
}

/** 分割・結合の確認シート(割当や品目が消える場合だけ出す)。 */
@Composable
private fun GroupOpConfirm(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    InkSheet(onDismiss = onDismiss) {
        SheetTitle("確認")
        Text(message, style = AppTheme.type.body, color = c.ink, modifier = Modifier.padding(bottom = Space.m))
        TextAction("実行する", onClick = onConfirm, color = c.danger)
        TextAction("やめる", onClick = onDismiss, color = c.inkSoft)
    }
}

/** 保存時の支払い側の人数の確認。ボタンは「はい」「キャンセル」の2つだけ。「はい」は人数を変え、「キャンセル」は人数を変えない(どちらも割り勘は保存する)。 */
@Composable
private fun HeadcountConfirm(message: String, onYes: () -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    InkSheet(onDismiss = onDismiss) {
        SheetTitle("確認")
        Text(message, style = AppTheme.type.body, color = c.ink, modifier = Modifier.padding(bottom = Space.m))
        TextAction("はい", onClick = onYes, color = c.ink)
        TextAction("キャンセル", onClick = onCancel, color = c.inkSoft)
    }
}

private val MODES = listOf(SplitMode.ITEM, SplitMode.WHOLE)

private fun modeLabel(mode: String): String = when (mode) {
    SplitMode.WHOLE -> "全体で割る"
    else -> "食べた分どおり"
}

private fun kindLabel(kind: String): String = when (kind) {
    ShareKind.ASSIGN -> "1人に"
    ShareKind.EQUAL -> "均等"
    ShareKind.RATIO -> "個数"
    else -> "金額"
}

/** 参加人数の行: 「合計N人」と − / +、まとめて追加。− は末尾の名前なし行が消せるときだけ押せる。 */
@Composable
private fun ParticipantCountRow(
    count: Int,
    canRemove: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onBulk: () -> Unit,
) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.edge, vertical = Space.xs)
            .sheet(RoundedCornerShape(16.dp), depth = 8.dp)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Label("参加人数(自分を含む)")
            Text(participantCountText(count), style = AppTheme.type.numeralL, color = c.ink)
        }
        TextAction("−", onClick = onMinus, enabled = canRemove, color = c.ink)
        TextAction("+", onClick = onPlus, color = c.ink)
        TextAction("まとめて追加", onClick = onBulk)
    }
}

/** まとめて追加の確認シート。人数を数字で入れると、入力中に「(合計〇人)」を出す。 */
@Composable
private fun BulkAddSheet(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val c = AppTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    val count = bulkAddCount(text)
    val total = totalAfterBulkAdd(current, text)
    InkSheet(onDismiss = onDismiss, modifier = Modifier.imePadding()) {
        SheetTitle("まとめて追加")
        SheetChoice("何人追加しますか?") {
            BareField(
                value = text,
                onValueChange = { text = filterNumericInput(it) },
                placeholder = "人数",
                singleLine = true,
                underline = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = AppTheme.type.body,
            )
        }
        Text(
            total?.let { "(合計${it}人)" } ?: "1〜${MAX_BULK_ADD}人の数字を入れてください",
            style = AppTheme.type.caption,
            color = c.inkSoft,
            modifier = Modifier.padding(bottom = Space.m),
        )
        TextAction("追加する", onClick = { count?.let(onConfirm) }, enabled = count != null)
        TextAction("やめる", onClick = onDismiss, color = c.inkSoft)
    }
}

/** 全体の金額指定のずれ(W2)と、解消の操作。金額欄は下の各人の行で直せる。 */
@Composable
private fun WholeWarningBlock(
    warnings: List<SplitWarning>,
    onEqualize: () -> Unit,
    onTakeDifference: () -> Unit,
) {
    val c = AppTheme.colors
    val hasDiff = warnings.any { it is SplitWarning.WholeAmountMismatch }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.edge, vertical = Space.xs)) {
        warnings.forEach { w ->
            Text(badgeText(w), style = AppTheme.type.caption, color = c.danger)
        }
        Row(
            modifier = Modifier.padding(top = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            TextAction("均等に配り直す", onClick = onEqualize, color = c.danger)
            if (hasDiff) TextAction("差額を自分に", onClick = onTakeDifference)
        }
        Text(
            "手で直すときは、下の各人の金額欄を直してください。",
            style = AppTheme.type.caption,
            color = c.inkFaint,
        )
    }
}

/** 1人ぶんの行。名前・(全体なら)金額か割合・(開いたら)メモ・ラベル・支払者・削除。 */
@Composable
private fun MemberRow(
    s: SplitSheetUiState,
    p: SplitParticipant,
    mode: String,
    open: Boolean,
    onToggleOpen: () -> Unit,
    onOpenIndividual: () -> Unit,
    onSetName: (String) -> Unit,
    onPickMember: (com.amgm.personallog.data.model.Person) -> Unit,
    onSetWhole: (String) -> Unit,
    onSetMemo: (String) -> Unit,
    onSetLabel: (String) -> Unit,
    onSetPayer: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val c = AppTheme.colors
    val isSelf = p.id == s.selfId
    val total = s.totals[p.id]
    /** 自分に入った仮計上(金額指定のずれ)の合計。自分の行にだけ付く。 */
    val provisionalTotal = if (isSelf) s.provisionalWhole + s.provisionalByGroup.values.sum() else 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.edge, vertical = Space.xs)
            .sheet(RoundedCornerShape(16.dp), depth = 8.dp)
            .padding(horizontal = Space.m, vertical = Space.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                if (isSelf) {
                    Text("自分", style = AppTheme.type.title, color = c.ink)
                } else {
                    MemberNameField(
                        value = s.names[p.id].orEmpty(),
                        onValueChange = onSetName,
                        members = s.members,
                        onPickMember = onPickMember,
                        placeholder = s.displayNames[p.id].orEmpty(),
                    )
                }
            }
            Text(total?.let { formatYenText(it) } ?: "", style = AppTheme.type.numeral, color = c.ink)
            TextAction(if (open) "閉じる" else "詳細", onClick = onToggleOpen, color = c.inkSoft)
        }
        if (provisionalTotal != 0) {
            Text(
                provisionalText(provisionalTotal),
                style = AppTheme.type.caption,
                color = c.danger,
            )
        }

        if (mode == SplitMode.WHOLE) {
            val row = s.data?.wholeShares?.firstOrNull { it.participantId == p.id }
            val text = row?.let { if (s.wholeKind == ShareKind.RATIO) it.weight?.toString() else it.amount?.toString() }.orEmpty()
            BareField(
                value = text,
                onValueChange = { onSetWhole(filterNumericInput(it)) },
                placeholder = total?.let { formatYenText(it) } ?: "",
                singleLine = true,
                underline = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = AppTheme.type.body,
                modifier = Modifier.padding(top = Space.xs),
            )
            // 全体で割るときは、食べた分(品目どおりの分)と負担(全体で割った分)を並べる
            s.individuals[p.id]?.let { ind ->
                Text(
                    "食べた分 ${formatYenText(ind.eatenTotal)} / 負担 ${formatYenText(ind.burdenTotal)}",
                    style = AppTheme.type.caption,
                    color = c.ink,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
            val breakdown = s.breakdown[p.id].orEmpty()
            if (breakdown.isNotEmpty()) {
                Text(
                    "内訳(品目の金額の比で按分): ${breakdownText(breakdown)}",
                    style = AppTheme.type.caption,
                    color = c.inkSoft,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }

        TextAction(
            "個別支出",
            onClick = onOpenIndividual,
            color = c.inkSoft,
            modifier = Modifier.padding(top = Space.xs),
        )

        if (open) {
            Column(modifier = Modifier.padding(top = Space.s)) {
                SheetField("一言メモ(この支払いだけ)", p.memo, onSetMemo)
                if (s.labels.isNotEmpty()) {
                    Label("ラベル", modifier = Modifier.padding(bottom = Space.s))
                    Pills(
                        options = s.labels,
                        selected = p.label,
                        onSelect = { onSetLabel(if (it == p.label) "" else it) },
                        label = { it },
                        modifier = Modifier.padding(bottom = Space.s),
                    )
                }
                SheetField("ラベル(例: 新人)", p.label, onSetLabel, placeholder = "なし")
                Pill(text = "店に払った人", selected = p.isPayer, onClick = { onSetPayer(!p.isPayer) })
                TextAction("この人を外す", onClick = onRemove, color = c.danger, modifier = Modifier.padding(top = Space.s))
            }
        }
    }
}

/**
 * 個別支出のシート。1人ぶんの、関わったまとまり・割合・食べた分(と全体で割るときは負担)・差。
 * 差 = 負担 − 食べた分。マイナスは「多めに払ってもらった」、プラスは「多めに払った」。
 */
@Composable
private fun IndividualSheet(s: SplitSheetUiState, p: SplitParticipant, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    val ind = s.individuals[p.id]
    val isWhole = s.mode == SplitMode.WHOLE
    InkSheet(onDismiss = onDismiss, modifier = Modifier.imePadding()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Label("個別支出")
            Text(s.displayNames[p.id].orEmpty(), style = AppTheme.type.title, color = c.ink)
            if (ind == null) {
                Text("計算できません", style = AppTheme.type.body, color = c.danger)
            } else {
                if (ind.lines.isEmpty()) {
                    Text("この支払いでは負担がありません。", style = AppTheme.type.body, color = c.inkSoft)
                }
                ind.lines.forEach { line -> IndividualLineRow(line, isWhole) }
                Hairline(dashed = true)
                LeaderRow(
                    label = "食べた分",
                    value = formatYenText(ind.eatenTotal),
                    labelStyle = AppTheme.type.caption,
                    valueStyle = AppTheme.type.caption,
                    labelColor = c.inkSoft,
                    valueColor = c.inkSoft,
                )
                LeaderRow(
                    label = if (isWhole) "負担(全体で割る)" else "負担",
                    value = formatYenText(ind.burdenTotal),
                    labelStyle = AppTheme.type.body,
                    valueStyle = AppTheme.type.numeral,
                    labelColor = c.ink,
                    valueColor = c.ink,
                )
                ind.wholeShare?.let { share ->
                    Text("全体に対する割合: $share", style = AppTheme.type.caption, color = c.inkSoft)
                }
                Text(differenceText(ind.difference), style = AppTheme.type.caption, color = c.ink)
            }
            TextAction("閉じる", onClick = onDismiss, color = c.inkSoft, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 個別支出の1行(まとまり1つぶん)。食べた分を大きく、割合と未設定の印を小さく付ける。全体で割るときは負担も出す。 */
@Composable
private fun IndividualLineRow(line: IndividualLine, isWhole: Boolean) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.name, style = AppTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
            Text(formatYenText(line.eaten), style = AppTheme.type.numeral, color = c.ink)
        }
        val shareText = if (line.groupId == null) "残差・調整(参加者で均等)" else "割合 ${line.share}"
        Text(shareText, style = AppTheme.type.caption, color = c.inkSoft)
        if (isWhole) {
            Text("負担 ${formatYenText(line.burden)}", style = AppTheme.type.caption, color = c.inkSoft)
        }
        if (line.unset) {
            Text(unsetNoteText(line.isShared), style = AppTheme.type.caption, color = c.inkFaint)
        }
    }
}

/** 差の文言。負担 − 食べた分。 */
private fun differenceText(difference: Int): String = when {
    difference < 0 -> "多めに払ってもらった ${formatYenText(-difference)}"
    difference > 0 -> "多めに払った ${formatYenText(difference)}"
    else -> "食べた分どおり(差はありません)"
}

/** ラベルごとの1人あたり金額の1行。label "" は「その他」。空欄の placeholder は同じラベルの人の計算結果。 */
@Composable
private fun LabelAmountRow(s: SplitSheetUiState, label: String, onSet: (String) -> Unit) {
    val c = AppTheme.colors
    val sample = s.participants.firstOrNull { it.label == label && it.id != s.selfId }
        ?: s.participants.firstOrNull { it.label == label }
    val placeholder = sample?.let { s.totals[it.id] }?.let { formatYenText(it) } ?: "均等"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.edge, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(labelDisplay(label), style = AppTheme.type.body, color = c.ink, modifier = Modifier.padding(end = Space.m))
        BareField(
            value = s.labelAmounts[label]?.toString().orEmpty(),
            onValueChange = { onSet(filterNumericInput(it)) },
            placeholder = placeholder,
            singleLine = true,
            underline = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = AppTheme.type.body,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * まとまり1つ分の行: 品名 ······ 金額、割り方・共通費・N個中・未設定・仮の補足。
 * 警告(W1・W5)があるときは、バッジと解消の操作(均等に配り直す・差額を自分に・手で直す)を出す。
 * タップで編集シート(まとめる操作中は選択)。
 */
@Composable
private fun GroupCard(
    g: GroupUi,
    selecting: Boolean,
    selected: Boolean,
    participantCount: Int,
    warnings: List<SplitWarning>,
    provisional: Int?,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onEqualize: () -> Unit,
    onTakeDifference: () -> Unit,
) {
    val c = AppTheme.colors
    val unsetNote = g.unset && participantCount >= 2
    val note = buildString {
        if (selecting && selected) append("選択中 ・ ")
        append(if (unsetNote) unsetNoteText(g.isShared) else kindLabel(g.kind))
        if (g.pieceCount != null) append(" ・ ${g.pieceCount}個中")
        if (g.isShared && !unsetNote) append(" ・ 共通費")
    }
    val hasDiff = warnings.any { it is SplitWarning.GroupAmountMismatch }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.edge, vertical = Space.xs)
            .sheet(RoundedCornerShape(16.dp), depth = 8.dp)
            .tappable(onClick = if (selecting) onSelect else onOpen)
            .padding(horizontal = Space.edge, vertical = Space.s),
    ) {
        LeaderRow(label = g.name + if (g.count > 1 && !g.name.contains("×")) " ×${g.count}" else "", value = formatYenText(g.amount))
        Text(note, style = AppTheme.type.caption, color = c.inkSoft)
        provisional?.takeIf { it != 0 }?.let { amount ->
            Text(provisionalText(amount), style = AppTheme.type.caption, color = c.danger)
        }
        warnings.forEach { w ->
            Text(badgeText(w), style = AppTheme.type.caption, color = c.danger, modifier = Modifier.padding(top = Space.xs))
        }
        if (warnings.isNotEmpty() && !selecting) {
            Row(
                modifier = Modifier.padding(top = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                TextAction("均等に配り直す", onClick = onEqualize, color = c.danger)
                if (hasDiff) TextAction("差額を自分に", onClick = onTakeDifference)
                TextAction("手で直す", onClick = onOpen, color = c.inkSoft)
            }
        }
    }
}

/** まとまりの編集シート: 割り方・N個中・対象者(と個数・金額)・共通費。 */
@Composable
private fun GroupSheet(
    s: SplitSheetUiState,
    g: GroupUi,
    onSetKind: (String) -> Unit,
    onToggle: (Long) -> Unit,
    onSetValue: (Long, String) -> Unit,
    onSetPieces: (String) -> Unit,
    onSetShared: (Boolean) -> Unit,
    onSplit: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    val rows = s.data?.itemShares.orEmpty().filter { it.groupId == g.id }
    val groupItems = s.groupItems[g.id].orEmpty()
    /** 分けるために選んだ品目。 */
    var picks by remember(g.id) { mutableStateOf(setOf<Long>()) }
    InkSheet(onDismiss = onDismiss, modifier = Modifier.imePadding()) {
        SheetTitle(g.name)
        SheetChoice("割り方") {
            Pills(
                options = listOf(ShareKind.ASSIGN, ShareKind.EQUAL, ShareKind.RATIO, ShareKind.AMOUNT),
                selected = g.kind,
                onSelect = onSetKind,
                label = { kindLabel(it) },
            )
        }
        if (g.kind == ShareKind.RATIO) {
            SheetField(
                "N個中(任意。空欄なら個数の欄は残りを均等)",
                g.pieceCount?.toString().orEmpty(),
                onSetPieces,
                numeric = true,
            )
        }
        if (g.kind == ShareKind.AMOUNT) {
            Text(
                "金額指定は合計とずれると警告が出ます(自動では配り直しません)。",
                style = AppTheme.type.caption,
                color = c.inkSoft,
                modifier = Modifier.padding(bottom = Space.m),
            )
        }
        SheetChoice("対象者") {
            s.participants.forEach { p ->
                val row = rows.firstOrNull { it.participantId == p.id }
                val name = if (p.id == s.selfId) "自分" else s.displayNames[p.id].orEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Pill(text = name, selected = row != null, onClick = { onToggle(p.id) })
                    if (row != null && (g.kind == ShareKind.RATIO || g.kind == ShareKind.AMOUNT)) {
                        val shown = if (g.kind == ShareKind.RATIO) row.weight?.toString() else row.amount?.toString()
                        BareField(
                            value = shown.orEmpty(),
                            onValueChange = { onSetValue(p.id, filterNumericInput(it)) },
                            placeholder = s.groupYen[p.id]?.get(g.id)?.let { formatYenText(it) } ?: "",
                            singleLine = true,
                            underline = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = AppTheme.type.body,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (groupItems.size > 1) {
            SheetChoice("品目を分ける") {
                Column {
                    Text(
                        "選んだ品目を新しいまとまりにします。割当と共通費は引き継ぎません(未設定=自分)。",
                        style = AppTheme.type.caption,
                        color = c.inkSoft,
                        modifier = Modifier.padding(bottom = Space.s),
                    )
                    groupItems.forEach { (itemId, name) ->
                        Pill(
                            text = name,
                            selected = itemId in picks,
                            onClick = { picks = if (itemId in picks) picks - itemId else picks + itemId },
                        )
                    }
                    TextAction(
                        "選んだ品目を分ける",
                        onClick = {
                            onSplit(picks)
                            picks = emptySet()
                        },
                        enabled = canSplitOutItems(groupItems.map { it.first }.toSet(), picks),
                    )
                }
            }
        }
        SheetChoice("共通費") {
            Pill(
                text = "共通費(割当が無いと参加者で均等)",
                selected = g.isShared,
                onClick = { onSetShared(!g.isShared) },
            )
        }
        Hairline(modifier = Modifier.padding(bottom = Space.s))
        TextAction("閉じる", onClick = onDismiss)
    }
}
