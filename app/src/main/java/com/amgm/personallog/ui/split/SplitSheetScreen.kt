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

/** 割り勘シートの画面。タブ(自分だけ / 全体 / 品目ごと)で方式を切り替える。 */
@Composable
fun SplitSheetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplitSheetViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    /** 行を開いている参加者(メモ・ラベル・支払者・削除)。0 は無し。 */
    var openMemberId by rememberSaveable { mutableStateOf(0L) }
    /** 開いているまとまり(割り方・対象者・共通費)。0 は無し。 */
    var openGroupId by rememberSaveable { mutableStateOf(0L) }
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
                    Text("割り勘", style = AppTheme.type.title, color = c.ink)
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
                    Text("レシートが見つかりません。", style = AppTheme.type.body, color = c.inkSoft, modifier = Modifier.padding(horizontal = Space.edge))
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

                // タブ(割り方は排他)
                item(key = "tabs") {
                    Pills(
                        options = MODES,
                        selected = s.mode,
                        onSelect = viewModel::setMode,
                        label = { modeLabel(it) },
                        modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.s),
                    )
                }

                if (s.mode == SplitMode.SELF) {
                    item(key = "selfOnly") {
                        Text(
                            "全額が自分の支出になります。",
                            style = AppTheme.type.body,
                            color = c.inkSoft,
                            modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.m),
                        )
                    }
                } else {
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

                    // メンバー欄(全体・品目ごとで共通)
                    item(key = "membersTitle") {
                        Label("メンバー", modifier = Modifier.padding(start = Space.edge, end = Space.edge, top = Space.l, bottom = Space.s))
                    }
                    items(s.participants, key = { it.id }) { p ->
                        MemberRow(
                            s = s,
                            p = p,
                            mode = s.mode,
                            open = openMemberId == p.id,
                            onToggleOpen = { openMemberId = if (openMemberId == p.id) 0L else p.id },
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
                            TextAction("メンバーを選ぶ", onClick = { pickerOpen = true })
                            TextAction("+ 追加", onClick = viewModel::addBlankParticipant)
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
                                onOpen = { openGroupId = g.id },
                                onSelect = {
                                    mergeSelection = if (g.id in mergeSelection) mergeSelection - g.id else mergeSelection + g.id
                                },
                            )
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

private val MODES = listOf(SplitMode.SELF, SplitMode.WHOLE, SplitMode.ITEM)

private fun modeLabel(mode: String): String = when (mode) {
    SplitMode.SELF -> "自分だけ"
    SplitMode.WHOLE -> "全体"
    else -> "品目ごと"
}

private fun kindLabel(kind: String): String = when (kind) {
    ShareKind.ASSIGN -> "1人に"
    ShareKind.EQUAL -> "均等"
    ShareKind.RATIO -> "個数"
    else -> "金額"
}

/** 1人ぶんの行。名前・(全体なら)金額か割合・(開いたら)メモ・ラベル・支払者・削除。 */
@Composable
private fun MemberRow(
    s: SplitSheetUiState,
    p: SplitParticipant,
    mode: String,
    open: Boolean,
    onToggleOpen: () -> Unit,
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
            if (mode != SplitMode.SELF) {
                Text(total?.let { formatYenText(it) } ?: "", style = AppTheme.type.numeral, color = c.ink)
            }
            TextAction(if (open) "閉じる" else "詳細", onClick = onToggleOpen, color = c.inkSoft)
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
                if (!isSelf) {
                    TextAction("この人を外す", onClick = onRemove, color = c.danger, modifier = Modifier.padding(top = Space.s))
                }
            }
        }
    }
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

/** まとまり1つ分の行: 品名 ······ 金額、割り方・共通費・N個中の補足。タップで編集シート(まとめる操作中は選択)。 */
@Composable
private fun GroupCard(g: GroupUi, selecting: Boolean, selected: Boolean, onOpen: () -> Unit, onSelect: () -> Unit) {
    val c = AppTheme.colors
    val note = buildString {
        if (selecting && selected) append("選択中 ・ ")
        append(kindLabel(g.kind))
        if (g.pieceCount != null) append(" ・ ${g.pieceCount}個中")
        if (g.isShared) append(" ・ 共通費")
    }
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
