package com.amgm.personallog.ui.split

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitLabelAmount
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.model.WholeShare
import com.amgm.personallog.data.repository.PersonRepository
import com.amgm.personallog.data.repository.ReceiptRepository
import com.amgm.personallog.data.repository.SplitBuild
import com.amgm.personallog.data.repository.SplitRepository
import com.amgm.personallog.data.repository.SplitSheetData
import com.amgm.personallog.data.repository.mergeKeepGroup
import com.amgm.personallog.data.repository.mergeShareRows
import com.amgm.personallog.data.repository.splitEmptiesSource
import com.amgm.personallog.data.repository.toBuild
import com.amgm.personallog.domain.split.SplitCalculator
import com.amgm.personallog.domain.split.SplitGroupInput
import com.amgm.personallog.domain.split.SplitLedger
import com.amgm.personallog.domain.split.SplitResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 品目のまとまり1つぶんの表示用の値。 */
data class GroupUi(
    val id: Long,
    /** 品名(同じ品目の複数行なら1つにまとめた名前)。 */
    val name: String,
    /** まとまりに入っている品目の行数(「×N」の表示に使う)。 */
    val count: Int,
    /** まとまりの税込金額。 */
    val amount: Int,
    val isShared: Boolean,
    /** 有効な割り方(ShareKind)。割当が無いときは見本の値(既定は均等)。 */
    val kind: String,
    /** 「N個中M個」の N。 */
    val pieceCount: Int?,
)

/** 割り勘シートの画面の状態。計算結果は編集のたびに作り直す。 */
data class SplitSheetUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    /** 読み込んだ割り勘の中身(画面の入力欄の値を、ここから読む)。 */
    val data: SplitSheetData? = null,
    /** 割り方(SplitMode の値)。画面のタブと表示の切り替えに使う。 */
    val mode: String = SplitMode.SELF,
    /** 全体方式の入力の種類(ShareKind.AMOUNT / RATIO)。 */
    val wholeKind: String = ShareKind.AMOUNT,
    /** 支払いの合計。 */
    val total: Int? = null,
    /** 参加者(sortOrder 順)。 */
    val participants: List<SplitParticipant> = emptyList(),
    val selfId: Long = 0L,
    /** 参加者 id → 入力中の名前(登録済みの人は人の名前)。 */
    val names: Map<Long, String> = emptyMap(),
    /** 参加者 id → 表示名(自分は「自分」、空の名前は「メンバーN」)。 */
    val displayNames: Map<Long, String> = emptyMap(),
    /** 名前入力の候補と「メンバーを選ぶ」に出す登録メンバー(自分以外)。 */
    val members: List<Person> = emptyList(),
    /** 参加者 id → 各人の合計(計算結果。空欄の placeholder に使う)。 */
    val totals: Map<Long, Int> = emptyMap(),
    /** 参加者 id → まとまり id → その人のそのまとまりの金額。 */
    val groupYen: Map<Long, Map<Long, Int>> = emptyMap(),
    /** 参加者 id → カテゴリ別の金額(品目の金額の比で按分した内訳)。 */
    val breakdown: Map<Long, List<Pair<String, Int>>> = emptyMap(),
    val groups: List<GroupUi> = emptyList(),
    /** まとまり id → そこに入っている品目(品目 id と品名)。分けるときの選択に使う。 */
    val groupItems: Map<Long, List<Pair<Long, String>>> = emptyMap(),
    /** 既存のラベル(空は除く・重複なし)。 */
    val labels: List<String> = emptyList(),
    /** ラベル → 1人あたり金額(全体・金額のとき)。 */
    val labelAmounts: Map<String, Int> = emptyMap(),
    /** 計算の失敗など。出たときは計算結果の代わりに「計算できません」を出す。 */
    val calcError: String? = null,
    /** 警告の文言(W1〜W5)。 */
    val warnings: List<String> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    /** 保存できたら true。画面は閉じる。 */
    val saved: Boolean = false,
)

/**
 * 割り勘シートの編集。状態は SplitSheetData と names(参加者 id → 入力中の名前)で持つ。
 * 変更のたびに toBuild → calculate し、結果を UI 状態にする。計算が例外のときは「計算できません」を出す。
 * 新しく追加する参加者は負の仮 id を振る(保存時に確定する)。
 */
@HiltViewModel
class SplitSheetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val receipts: ReceiptRepository,
    private val splits: SplitRepository,
    private val persons: PersonRepository,
) : ViewModel() {

    private val receiptId: Long = savedStateHandle.get<Long>("receiptId") ?: 0L

    private val _state = MutableStateFlow(SplitSheetUiState())
    val state: StateFlow<SplitSheetUiState> = _state.asStateFlow()

    private var data: SplitSheetData? = null
    private var names: Map<Long, String> = emptyMap()
    private var items: List<ReceiptItem> = emptyList()
    private var total: Int? = null
    private var members: List<Person> = emptyList()

    /** まとまりに割当の行が無いときの割り方の見本(画面で選んだ方式を覚えておく)。 */
    private val groupKindHint = HashMap<Long, String>()

    init {
        viewModelScope.launch {
            try {
                val r = receipts.getReceipt(receiptId)
                if (r == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                    return@launch
                }
                items = receipts.getItems(receiptId)
                total = r.totalAmount
                val d = splits.load(receiptId)
                data = d
                names = initialNames(d.participants, d.persons)
                recompute()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = "読み込めませんでした: ${e.message.orEmpty()}") }
            }
        }
        viewModelScope.launch {
            try {
                persons.observeMembers().collect { list ->
                    members = list
                    recompute()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 候補が出せないだけで、割り勘の編集は続けられる
                members = emptyList()
                recompute()
            }
        }
    }

    // ---- 計算と表示の更新 ----

    /** 現在の data と names から、画面の状態を作り直す。 */
    private fun recompute() {
        val mem = members
        val d = data
        if (d == null) {
            _state.update { it.copy(members = mem) }
            return
        }
        val sorted = d.participants.sortedBy { it.sortOrder }
        val selfId = sorted.firstOrNull { d.persons[it.id]?.isSelf == true }?.id
            ?: sorted.firstOrNull()?.id ?: 0L

        // 表示名: 非自分の空欄は「メンバー1」「メンバー2」…(並び順で数える)
        val displayNames = HashMap<Long, String>()
        var number = 0
        for (p in sorted) {
            val isSelf = p.id == selfId
            if (!isSelf) number++
            displayNames[p.id] = displayNameOf(p, isSelf, names, number)
        }

        var result: SplitResult? = null
        var build: SplitBuild? = null
        var breakdown: Map<Long, List<Pair<String, Int>>> = emptyMap()
        var calcError: String? = null
        try {
            val b = d.toBuild(total, items)
            build = b
            val r = SplitCalculator.calculate(b.input)
            result = r
            breakdown = sorted.associate { it.id to SplitLedger.categoryBreakdown(r, it.id, b.itemYen) }
        } catch (e: Throwable) {
            // 未実装の関数(TODO)も Error として届くので Throwable で受ける
            calcError = "計算できません"
        }

        val groupNameMap = groupNames(d)
        val groupAmounts: Map<Long, Int> = build?.input?.groups
            ?.associate { g: SplitGroupInput -> g.id to g.amount }
            .orEmpty()
        val groupUis = d.groups.map { gw ->
            GroupUi(
                id = gw.group.id,
                name = groupNameMap[gw.group.id].orEmpty().ifEmpty { "(品名なし)" },
                count = gw.itemIds.size,
                amount = groupAmounts[gw.group.id] ?: 0,
                isShared = gw.group.isShared,
                kind = effectiveKind(d, gw.group.id),
                pieceCount = gw.group.pieceCount,
            )
        }

        val totals = result?.let { r -> sorted.associate { it.id to r.totalOf(it.id) } }.orEmpty()
        val groupYen = result?.let { groupYenOf(it) }.orEmpty()
        val warnings = result?.warnings?.map { w -> warningText(w) { gid -> groupNameMap[gid].orEmpty().ifEmpty { "品目" } } }
            .orEmpty()

        _state.update {
            it.copy(
                loading = false,
                notFound = false,
                data = d,
                mode = d.mode,
                wholeKind = d.wholeKind,
                total = total,
                participants = sorted,
                selfId = selfId,
                names = names,
                displayNames = displayNames,
                members = mem,
                totals = totals,
                groupYen = groupYen,
                breakdown = breakdown,
                groups = groupUis,
                groupItems = groupItemsOf(d),
                labels = labelsOf(sorted),
                labelAmounts = d.labelAmounts.associate { it.label to it.amount },
                calcError = calcError,
                warnings = warnings,
            )
        }
    }

    /** まとまりの有効な割り方。割当の行があればその種類、無ければ見本、それも無ければ均等。 */
    private fun effectiveKind(d: SplitSheetData, groupId: Long): String =
        d.itemShares.firstOrNull { it.groupId == groupId }?.kind
            ?: groupKindHint[groupId]
            ?: ShareKind.EQUAL

    /** まとまり id → 品名(同じ品名は1つにまとめ、「・」でつなぐ)。 */
    private fun groupNames(d: SplitSheetData): Map<Long, String> = d.groups.associate { gw ->
        val itemNames = items.filter { it.id in gw.itemIds }.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
        val base = itemNames.joinToString("・")
        val count = gw.itemIds.size
        gw.group.id to if (count > 1 && base.isNotEmpty()) "$base ×$count" else base
    }

    /** まとまり id → 品目(id と品名)。品名が空なら「(品名なし)」。 */
    private fun groupItemsOf(d: SplitSheetData): Map<Long, List<Pair<Long, String>>> {
        val itemsById = items.associateBy { it.id }
        return d.groups.associate { gw ->
            gw.group.id to gw.itemIds.map { id ->
                id to (itemsById[id]?.name?.trim()?.ifEmpty { "(品名なし)" } ?: "(品名なし)")
            }
        }
    }

    /** 計算結果から、参加者 → まとまり → 金額を作る(まとまりに属さない行は含めない)。 */
    private fun groupYenOf(r: SplitResult): Map<Long, Map<Long, Int>> {
        val out = HashMap<Long, HashMap<Long, Int>>()
        for (line in r.lines) {
            val g = line.groupId ?: continue
            val m = out.getOrPut(line.personId) { HashMap() }
            m[g] = (m[g] ?: 0) + line.amount
        }
        return out
    }

    /** 編集を反映して計算し直す。 */
    private fun commit(d: SplitSheetData, n: Map<Long, String> = names) {
        data = d
        names = n
        recompute()
    }

    /** 新しい参加者の仮 id(負の数。既存より小さい)。 */
    private fun nextTempId(d: SplitSheetData): Long = minOf(0L, d.participants.minOfOrNull { it.id } ?: 0L) - 1L

    private fun newParticipant(d: SplitSheetData, personId: Long?): SplitParticipant = SplitParticipant(
        id = nextTempId(d),
        receiptId = receiptId,
        personId = personId,
        sortOrder = (d.participants.maxOfOrNull { it.sortOrder } ?: 0) + 1,
    )

    private fun mapParticipant(id: Long, f: (SplitParticipant) -> SplitParticipant) {
        val d = data ?: return
        commit(d.copy(participants = d.participants.map { if (it.id == id) f(it) else it }))
    }

    // ---- 操作: 割り方 ----

    fun setMode(mode: String) {
        val d = data ?: return
        commit(d.copy(mode = mode))
    }

    /** 全体の入力の種類(金額 / 割合)。種類が変わると入力済みの値は空欄に戻す(単位が違うため)。 */
    fun setWholeKind(kind: String) {
        val d = data ?: return
        if (d.wholeKind == kind) return
        commit(
            d.copy(
                wholeKind = kind,
                wholeShares = d.wholeShares.map { it.copy(kind = kind, weight = null, amount = null) },
            ),
        )
    }

    // ---- 操作: 参加者 ----

    fun addBlankParticipant() {
        val d = data ?: return
        val p = newParticipant(d, null)
        commit(d.copy(participants = d.participants + p), names + (p.id to ""))
    }

    /** 登録メンバーを追加する。既にいる人は足さない。 */
    fun addMembers(people: List<Person>) {
        val d = data ?: return
        var list = d.participants
        var nm = names
        val seen = d.participants.mapNotNull { it.personId }.toMutableSet()
        val pers = d.persons.toMutableMap()
        for (person in people) {
            if (!seen.add(person.id)) continue
            val p = SplitParticipant(
                id = minOf(0L, list.minOfOrNull { it.id } ?: 0L) - 1L,
                receiptId = receiptId,
                personId = person.id,
                sortOrder = (list.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            )
            list = list + p
            nm = nm + (p.id to person.name)
            pers[p.id] = person
        }
        commit(d.copy(participants = list, persons = pers), nm)
    }

    /** 参加者を外す。自分は外せない。その人の割当も消す。 */
    fun removeParticipant(id: Long) {
        val d = data ?: return
        if (id == _state.value.selfId) return
        commit(
            d.copy(
                participants = d.participants.filterNot { it.id == id },
                itemShares = d.itemShares.filterNot { it.participantId == id },
                wholeShares = d.wholeShares.filterNot { it.participantId == id },
                persons = d.persons - id,
            ),
            names - id,
        )
    }

    /** 名前の入力。登録済みの人の名前を変えたら、その人との紐付けは外す(保存時に新しい名前で登録し直す)。 */
    fun setName(id: Long, text: String) {
        val d = data ?: return
        if (id == _state.value.selfId) return
        val linked = d.persons[id]
        val unlink = linked != null && linked.name != text.trim()
        val parts = if (unlink) {
            d.participants.map { if (it.id == id) it.copy(personId = null) else it }
        } else {
            d.participants
        }
        val pers = if (unlink) d.persons - id else d.persons
        commit(d.copy(participants = parts, persons = pers), names + (id to text))
    }

    /** 候補から登録メンバーを選ぶ。既にいる人は選べない(何もしない)。 */
    fun pickMember(id: Long, person: Person) {
        val d = data ?: return
        if (id == _state.value.selfId) return
        if (d.participants.any { it.personId == person.id && it.id != id }) return
        commit(
            d.copy(
                participants = d.participants.map { if (it.id == id) it.copy(personId = person.id) else it },
                persons = d.persons + (id to person),
            ),
            names + (id to person.name),
        )
    }

    fun setMemo(id: Long, text: String) = mapParticipant(id) { it.copy(memo = text) }

    fun setLabel(id: Long, text: String) = mapParticipant(id) { it.copy(label = text) }

    fun setPayer(id: Long, on: Boolean) = mapParticipant(id) { it.copy(isPayer = on) }

    // ---- 操作: 全体 ----

    /** 全体の入力欄。空欄(または読めない)なら行を消す(残額を均等割)。 */
    fun setWholeValue(id: Long, text: String) {
        val d = data ?: return
        val value = parseAmountText(text)
        val rest = d.wholeShares.filterNot { it.participantId == id }
        val row = when {
            value == null -> null
            d.wholeKind == ShareKind.RATIO -> WholeShare(receiptId = receiptId, participantId = id, kind = ShareKind.RATIO, weight = value)
            else -> WholeShare(receiptId = receiptId, participantId = id, kind = ShareKind.AMOUNT, amount = value)
        }
        commit(d.copy(wholeShares = rest + listOfNotNull(row)))
    }

    /** ラベルごとの1人あたり金額。空欄なら消す(そのラベルは均等割)。label "" は「その他」。 */
    fun setLabelAmount(label: String, text: String) {
        val d = data ?: return
        val value = parseAmountText(text)
        val rest = d.labelAmounts.filterNot { it.label == label }
        val row = value?.let { SplitLabelAmount(receiptId = receiptId, label = label, amount = it) }
        commit(d.copy(labelAmounts = rest + listOfNotNull(row)))
    }

    // ---- 操作: 品目ごと(まとまり) ----

    /** まとまりの割り方を変える。割当の値は空に戻す(単位が違うため)。1人に は先頭の1人だけ残す。 */
    fun setGroupKind(groupId: Long, kind: String) {
        val d = data ?: return
        if (effectiveKind(d, groupId) == kind) return
        groupKindHint[groupId] = kind
        val rows = d.itemShares
            .filter { it.groupId == groupId }
            .let { if (kind == ShareKind.ASSIGN) it.take(1) else it }
            .map { it.copy(kind = kind, weight = null, amount = null) }
        commit(d.copy(itemShares = d.itemShares.filterNot { it.groupId == groupId } + rows))
    }

    /** まとまりの対象者を切り替える。1人に のときは1人だけ選び直す(他の人は外れる)。 */
    fun toggleGroupParticipant(groupId: Long, participantId: Long) {
        val d = data ?: return
        val kind = effectiveKind(d, groupId)
        val has = d.itemShares.any { it.groupId == groupId && it.participantId == participantId }
        val others = if (kind == ShareKind.ASSIGN) {
            d.itemShares.filterNot { it.groupId == groupId }
        } else {
            d.itemShares.filterNot { it.groupId == groupId && it.participantId == participantId }
        }
        val added = if (has) null else ItemShare(groupId = groupId, participantId = participantId, kind = kind)
        commit(d.copy(itemShares = others + listOfNotNull(added)))
    }

    /** まとまりの対象者の値(個数の M・金額)。空欄は行を残したまま値だけ空にする(残りを均等)。 */
    fun setGroupValue(groupId: Long, participantId: Long, text: String) {
        val d = data ?: return
        val kind = effectiveKind(d, groupId)
        val value = parseAmountText(text)
        val old = d.itemShares.firstOrNull { it.groupId == groupId && it.participantId == participantId }
        if (old == null && value == null) return
        val row = ItemShare(
            groupId = groupId,
            participantId = participantId,
            kind = kind,
            weight = if (kind == ShareKind.RATIO) value else null,
            amount = if (kind == ShareKind.AMOUNT) value else null,
        )
        val others = d.itemShares.filterNot { it.groupId == groupId && it.participantId == participantId }
        commit(d.copy(itemShares = others + row))
    }

    /** 「N個中」の N。空欄は null。 */
    fun setPieceCount(groupId: Long, text: String) {
        val d = data ?: return
        val n = parseAmountText(text)
        commit(
            d.copy(
                groups = d.groups.map { gw ->
                    if (gw.group.id == groupId) gw.copy(group = gw.group.copy(pieceCount = n)) else gw
                },
            ),
        )
    }

    /** 共通費(袋代・送料など)の印。 */
    fun setShared(groupId: Long, on: Boolean) {
        val d = data ?: return
        commit(
            d.copy(
                groups = d.groups.map { gw ->
                    if (gw.group.id == groupId) gw.copy(group = gw.group.copy(isShared = on)) else gw
                },
            ),
        )
    }

    // ---- 操作: まとまりの分割・結合(その場で保存する) ----

    /** 分けると元のまとまりが消えるか(画面で確認を出すか決める)。 */
    fun splitWouldEmpty(groupId: Long, itemIds: Set<Long>): Boolean {
        val d = data ?: return false
        val members = d.groups.firstOrNull { it.group.id == groupId }?.itemIds?.toSet() ?: return false
        return splitEmptiesSource(members, itemIds)
    }

    /** まとめると割当が外れるか(統合できない割当が混ざるとき。画面で確認を出すか決める)。 */
    fun mergeWouldClear(groupIds: Set<Long>): Boolean {
        val d = data ?: return false
        val chosen = d.groups.map { it.group }.filter { it.id in groupIds }
        val keep = mergeKeepGroup(chosen) ?: return false
        val rows = d.itemShares
        return mergeShareRows(
            keepGroupId = keep.id,
            keepRows = rows.filter { it.groupId == keep.id },
            otherRows = chosen.filter { it.id != keep.id }.map { o -> rows.filter { it.groupId == o.id } },
        ).cleared
    }

    /** 選んだ品目を新しいまとまりに分ける。元が空になれば消える(画面で確認済みである前提)。 */
    fun splitGroupItems(groupId: Long, itemIds: Set<Long>) = runGroupOp {
        splits.splitGroupItems(receiptId, groupId, itemIds)
    }

    /** 選んだまとまりを1つにまとめる(画面で確認済みである前提)。 */
    fun mergeGroups(groupIds: Set<Long>) = runGroupOp {
        splits.mergeGroups(receiptId, groupIds)
    }

    /**
     * 分割・結合の共通の流れ: 未保存の編集を先に保存し(家計簿も合わせる)、操作し、家計簿を合わせて、読み直す。
     * 参加者とまとまりの id が保存で変わるので、名前の入力も読み直した内容に作り直す。
     */
    private fun runGroupOp(op: suspend () -> Unit) {
        val d = data ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                receipts.saveSplit(d, names)
                op()
                receipts.syncLedger(receiptId)
                val fresh = splits.load(receiptId)
                data = fresh
                names = initialNames(fresh.participants, fresh.persons)
                recompute()
                _state.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(busy = false, error = "まとまりを直せませんでした: ${e.message.orEmpty()}") }
            }
        }
    }

    // ---- 保存 ----

    /** 保存する。成功したら saved を立てる(画面は閉じる)。失敗はメッセージを出す。 */
    fun save() {
        val d = data ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val saved = receipts.saveSplit(d, names)
                data = saved
                recompute()
                _state.update { it.copy(busy = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(busy = false, error = "保存に失敗しました: ${e.message.orEmpty()}") }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
