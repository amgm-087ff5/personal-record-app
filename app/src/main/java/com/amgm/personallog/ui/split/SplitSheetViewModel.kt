package com.amgm.personallog.ui.split

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitLabelAmount
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.model.WholeShare
import com.amgm.personallog.data.repository.PersonRepository
import com.amgm.personallog.data.repository.HeadcountChange
import com.amgm.personallog.data.repository.PaymentRepository
import com.amgm.personallog.data.repository.SplitBuild
import com.amgm.personallog.data.repository.SplitRepository
import com.amgm.personallog.data.repository.SplitSheetData
import com.amgm.personallog.data.repository.headcountChangeOf
import com.amgm.personallog.data.repository.mergeKeepGroup
import com.amgm.personallog.data.repository.mergeShareRows
import com.amgm.personallog.data.repository.splitEmptiesSource
import com.amgm.personallog.data.repository.toBuild
import com.amgm.personallog.domain.split.IndividualExpense
import com.amgm.personallog.domain.split.IndividualExpenses
import com.amgm.personallog.domain.split.SplitCalculator
import com.amgm.personallog.domain.split.SplitGroupInput
import com.amgm.personallog.domain.split.SplitLedger
import com.amgm.personallog.domain.split.SplitResult
import com.amgm.personallog.domain.split.SplitWarning
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
    /** 割当の行が1つも無い(未設定)。未設定は自分に計上(共通費は参加者で均等)。 */
    val unset: Boolean = false,
)

/** 割り勘シートの画面の状態。計算結果は編集のたびに作り直す。 */
data class SplitSheetUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    /** 読み込んだ割り勘の中身(画面の入力欄の値を、ここから読む)。 */
    val data: SplitSheetData? = null,
    /** 割り方(SplitMode の値。ITEM か WHOLE)。画面のタブと表示の切り替えに使う。 */
    val mode: String = SplitMode.ITEM,
    /** 全体方式の入力の種類(ShareKind.AMOUNT / RATIO)。 */
    val wholeKind: String = ShareKind.AMOUNT,
    /** 支払いの合計。 */
    val total: Int? = null,
    /** 参加者(sortOrder 順)。 */
    val participants: List<SplitParticipant> = emptyList(),
    /** 自分の参加者 id。自分を外しているときは null(仮の id と混ざらないよう番兵の数は使わない)。 */
    val selfId: Long? = null,
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
    /** 参加者 id → その人の個別支出(関わったまとまり・割合・食べた分・負担・差)。個別支出のシートと「食べた分 / 負担」の表示に使う。 */
    val individuals: Map<Long, IndividualExpense> = emptyMap(),
    val groups: List<GroupUi> = emptyList(),
    /** まとまり id → そこに入っている品目(品目 id と品名)。分けるときの選択に使う。 */
    val groupItems: Map<Long, List<Pair<Long, String>>> = emptyMap(),
    /** 既存のラベル(空は除く・重複なし)。 */
    val labels: List<String> = emptyList(),
    /** ラベル → 1人あたり金額(全体・金額のとき)。 */
    val labelAmounts: Map<String, Int> = emptyMap(),
    /** 計算の失敗など。出たときは計算結果の代わりに「計算できません」を出す。 */
    val calcError: String? = null,
    /** 警告の文言(W1〜W5)。上の一覧に出す。 */
    val warnings: List<String> = emptyList(),
    /** − で消せる参加者の id。無ければ null(− は押せない)。 */
    val removableId: Long? = null,
    /** まとまり id → その行に付ける警告(W1・まとまりの付いた W5)。 */
    val groupWarnings: Map<Long, List<SplitWarning>> = emptyMap(),
    /** 全体の欄に付ける警告(W2・まとまりの無い W5)。 */
    val wholeWarnings: List<SplitWarning> = emptyList(),
    /** まとまり id → 仮計上(自分に入った金額指定のずれ)。 */
    val provisionalByGroup: Map<Long, Int> = emptyMap(),
    /** 全体の仮計上(自分に入った分)。 */
    val provisionalWhole: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    /** 保存できたら true。画面は閉じる。 */
    val saved: Boolean = false,
    /** 保存時に支払い側の人数が違うときの確認(あれば確認を出す)。 */
    val headcountConfirm: HeadcountChange? = null,
)

/**
 * 割り勘シートの編集。状態は SplitSheetData と names(参加者 id → 入力中の名前)で持つ。
 * 変更のたびに toBuild → calculate し、結果を UI 状態にする。計算が例外のときは「計算できません」を出す。
 * 新しく追加する参加者は負の仮 id を振る(保存時に確定する)。
 */
@HiltViewModel
class SplitSheetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val payments: PaymentRepository,
    private val splits: SplitRepository,
    private val persons: PersonRepository,
) : ViewModel() {

    private val paymentId: Long = savedStateHandle.get<Long>("paymentId") ?: 0L

    private val _state = MutableStateFlow(SplitSheetUiState())
    val state: StateFlow<SplitSheetUiState> = _state.asStateFlow()

    private var data: SplitSheetData? = null
    private var names: Map<Long, String> = emptyMap()
    private var items: List<PaymentItem> = emptyList()
    private var total: Int? = null
    private var members: List<Person> = emptyList()
    /** 支払いの参加人数(headcount。null は不明)。保存時の確認に使う。 */
    private var headcount: Int? = null

    /** まとまりに割当の行が無いときの割り方の見本(画面で選んだ方式を覚えておく)。 */
    private val groupKindHint = HashMap<Long, String>()

    init {
        viewModelScope.launch {
            try {
                val r = payments.getPayment(paymentId)
                if (r == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                    return@launch
                }
                items = payments.getItems(paymentId)
                total = r.totalAmount
                headcount = r.headcount
                val d = splits.load(paymentId)
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
        // 自分の参加者が無いとき(自分を外した)は null。先頭の人を「自分」にしない
        val selfId: Long? = sorted.firstOrNull { d.persons[it.id]?.isSelf == true }?.id

        // 表示名: 非自分の空欄は「メンバー1」「メンバー2」…(並び順で数える)
        val displayNames = HashMap<Long, String>()
        var number = 0
        for (p in sorted) {
            val isSelf = p.id == selfId
            if (!isSelf) number++
            displayNames[p.id] = displayNameOf(p, isSelf, names, number)
        }

        val groupNameMap = groupNames(d)
        var result: SplitResult? = null
        var build: SplitBuild? = null
        var breakdown: Map<Long, List<Pair<String, Int>>> = emptyMap()
        var individuals: Map<Long, IndividualExpense> = emptyMap()
        var calcError: String? = null
        try {
            val b = d.toBuild(total, items)
            build = b
            val r = SplitCalculator.calculate(b.input)
            result = r
            breakdown = sorted.associate { it.id to SplitLedger.categoryBreakdown(r, it.id, b.itemYen) }
            individuals = sorted.associate { it.id to IndividualExpenses.of(b.input, it.id, groupNameMap) }
        } catch (e: Throwable) {
            // 未実装の関数(TODO)も Error として届くので Throwable で受ける
            calcError = "計算できません"
        }

        val groupAmounts: Map<Long, Int> = build?.input?.groups
            ?.associate { g: SplitGroupInput -> g.id to g.amount }
            .orEmpty()
        val unsetSet = unsetGroupIds(d.groups.map { it.group.id }, d.itemShares.map { it.groupId }.toSet()).toSet()
        val groupUis = d.groups.map { gw ->
            GroupUi(
                id = gw.group.id,
                name = groupNameMap[gw.group.id].orEmpty().ifEmpty { "(品名なし)" },
                count = gw.itemIds.size,
                amount = groupAmounts[gw.group.id] ?: 0,
                isShared = gw.group.isShared,
                kind = effectiveKind(d, gw.group.id),
                pieceCount = gw.group.pieceCount,
                unset = gw.group.id in unsetSet,
            )
        }

        val totals = result?.let { r -> sorted.associate { it.id to r.totalOf(it.id) } }.orEmpty()
        val groupYen = result?.let { groupYenOf(it) }.orEmpty()
        val warnings = result?.warnings?.map { w -> warningText(w) { gid -> groupNameMap[gid].orEmpty().ifEmpty { "品目" } } }
            .orEmpty()
        val rawWarnings = result?.warnings.orEmpty()
        val lines = result?.lines.orEmpty()
        val removableId = removableLastParticipantId(
            sorted.map { p ->
                ParticipantFacts(
                    id = p.id,
                    isSelf = p.id == selfId,
                    typedName = names[p.id].orEmpty(),
                    isPayer = p.isPayer,
                    hasShare = d.itemShares.any { it.participantId == p.id } || d.wholeShares.any { it.participantId == p.id },
                )
            },
        )

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
                individuals = individuals,
                groups = groupUis,
                groupItems = groupItemsOf(d),
                labels = labelsOf(sorted),
                labelAmounts = d.labelAmounts.associate { it.label to it.amount },
                calcError = calcError,
                warnings = warnings,
                removableId = removableId,
                groupWarnings = groupWarningsOf(rawWarnings),
                wholeWarnings = wholeWarningsOf(rawWarnings),
                provisionalByGroup = provisionalByGroup(lines),
                provisionalWhole = provisionalWholeOf(lines),
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
        paymentId = paymentId,
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

    /** 名前なしの参加者を1人足す(+)。 */
    fun addBlankParticipant() = addBlankParticipants(1)

    /** 名前なしの参加者を count 人足す(まとめて追加・+)。 */
    fun addBlankParticipants(count: Int) {
        val d = data ?: return
        if (count <= 0) return
        var list = d.participants
        var nm = names
        repeat(count) {
            val p = newParticipant(d.copy(participants = list), null)
            list = list + p
            nm = nm + (p.id to "")
        }
        commit(d.copy(participants = list), nm)
    }

    /** 末尾の名前なし・割当なし・支払者でない参加者を1人消す(−)。消せる行が無ければ何もしない。 */
    fun removeLastBlankParticipant() {
        val id = _state.value.removableId ?: return
        removeParticipant(id)
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
                paymentId = paymentId,
                personId = person.id,
                sortOrder = (list.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            )
            list = list + p
            nm = nm + (p.id to person.name)
            pers[p.id] = person
        }
        commit(d.copy(participants = list, persons = pers), nm)
    }

    /** 参加者を外す(自分も外せる)。その人の割当も消す。 */
    fun removeParticipant(id: Long) {
        val d = data ?: return
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
            d.wholeKind == ShareKind.RATIO -> WholeShare(paymentId = paymentId, participantId = id, kind = ShareKind.RATIO, weight = value)
            else -> WholeShare(paymentId = paymentId, participantId = id, kind = ShareKind.AMOUNT, amount = value)
        }
        commit(d.copy(wholeShares = rest + listOfNotNull(row)))
    }

    /** ラベルごとの1人あたり金額。空欄なら消す(そのラベルは均等割)。label "" は「その他」。 */
    fun setLabelAmount(label: String, text: String) {
        val d = data ?: return
        val value = parseAmountText(text)
        val rest = d.labelAmounts.filterNot { it.label == label }
        val row = value?.let { SplitLabelAmount(paymentId = paymentId, label = label, amount = it) }
        commit(d.copy(labelAmounts = rest + listOfNotNull(row)))
    }

    // ---- 操作: 未設定・均等・差額(警告の解消) ----

    /** 未設定のまとまりを、参加者全員の均等の割当にする(参加者が2人以上のとき)。 */
    fun equalizeUnsetGroups() {
        val d = data ?: return
        val s = _state.value
        val unset = unsetGroupIds(d.groups.map { it.group.id }, d.itemShares.map { it.groupId }.toSet())
        val pairs = equalShareTargets(unset, s.participants.map { it.id })
        if (pairs.isEmpty()) return
        val rows = pairs.map { (g, p) -> ItemShare(groupId = g, participantId = p, kind = ShareKind.EQUAL) }
        commit(d.copy(itemShares = d.itemShares + rows))
    }

    /** まとまりの割当を、参加者全員の均等に配り直す(W1・W5 の解消)。 */
    fun equalizeGroup(groupId: Long) {
        val d = data ?: return
        val rows = _state.value.participants.map { p ->
            ItemShare(groupId = groupId, participantId = p.id, kind = ShareKind.EQUAL)
        }
        commit(d.copy(itemShares = d.itemShares.filterNot { it.groupId == groupId } + rows))
    }

    /** 全体の割当を消して、残りの額を均等に割り直す(W2・全体の W5 の解消)。ラベルごとの金額は残す。 */
    fun equalizeWhole() {
        val d = data ?: return
        commit(d.copy(wholeShares = emptyList()))
    }

    /** まとまりの金額指定のずれ(W1)を、自分の金額指定に足して合計を合わせる。自分の行が無ければ足す。 */
    fun takeDifferenceForGroup(groupId: Long) {
        val d = data ?: return
        val s = _state.value
        // 自分を外しているときは、ずれを自分に足せない(何もしない)
        val selfId = s.selfId ?: return
        val w = s.groupWarnings[groupId].orEmpty()
            .filterIsInstance<SplitWarning.GroupAmountMismatch>()
            .firstOrNull() ?: return
        val current = d.itemShares.firstOrNull { it.groupId == groupId && it.participantId == selfId }?.amount
        val value = selfAmountAfterDifference(current, w.expected, w.allocated)
        val row = ItemShare(groupId = groupId, participantId = selfId, kind = ShareKind.AMOUNT, amount = value)
        commit(d.copy(itemShares = d.itemShares.filterNot { it.groupId == groupId && it.participantId == selfId } + row))
    }

    /** 全体の金額指定のずれ(W2)を、自分の金額に足して合計を合わせる。自分の行が無ければ足す(ラベルの金額は今の値を起点にする)。 */
    fun takeDifferenceForWhole() {
        val d = data ?: return
        val s = _state.value
        // 自分を外しているときは、ずれを自分に足せない(何もしない)
        val selfId = s.selfId ?: return
        val w = s.wholeWarnings.filterIsInstance<SplitWarning.WholeAmountMismatch>().firstOrNull() ?: return
        val selfLabel = s.participants.firstOrNull { it.id == selfId }?.label.orEmpty()
        val current = d.wholeShares.firstOrNull { it.participantId == selfId }?.amount ?: s.labelAmounts[selfLabel]
        val value = selfAmountAfterDifference(current, w.expected, w.allocated)
        val row = WholeShare(paymentId = paymentId, participantId = selfId, kind = ShareKind.AMOUNT, amount = value)
        commit(d.copy(wholeShares = d.wholeShares.filterNot { it.participantId == selfId } + row))
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
        splits.splitGroupItems(paymentId, groupId, itemIds)
    }

    /** 選んだまとまりを1つにまとめる(画面で確認済みである前提)。 */
    fun mergeGroups(groupIds: Set<Long>) = runGroupOp {
        splits.mergeGroups(paymentId, groupIds)
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
                payments.saveSplit(d, names)
                op()
                payments.syncLedger(paymentId)
                val fresh = splits.load(paymentId)
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

    /**
     * 保存する。参加人数(自分を含む合計)と支払い側の人数(headcount。null は 1)が違えば、確認を出して止める。
     * 一致していれば、そのまま保存する。成功したら saved を立てる(画面は閉じる)。失敗はメッセージを出す。
     */
    fun save() {
        val d = data ?: return
        if (_state.value.busy) return
        val change = headcountChangeOf(headcount, d.participants.size)
        if (change != null) {
            _state.update { it.copy(headcountConfirm = change) }
            return
        }
        writeSave(d, headcountTo = null)
    }

    /**
     * 人数の確認で「はい」を選んだ後の保存。支払い側の人数を新しい人数に変えてから、割り勘を保存する。
     */
    fun confirmHeadcount() {
        val change = _state.value.headcountConfirm ?: return
        _state.update { it.copy(headcountConfirm = null) }
        val d = data ?: return
        if (_state.value.busy) return
        writeSave(d, headcountTo = change.to)
    }

    /**
     * 人数の確認を閉じる。「キャンセル」・外側のタップ・戻る操作がここに来る。何も保存しない。
     * 割り勘の編集内容はそのまま残す。
     */
    fun dismissHeadcountConfirm() = _state.update { it.copy(headcountConfirm = null) }

    /** 割り勘を保存する。headcountTo が null なら支払い側の人数は変えない(一致している場合の通常保存)。 */
    private fun writeSave(d: SplitSheetData, headcountTo: Int?) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val saved = payments.saveSplit(d, names)
                if (headcountTo != null) {
                    splits.setHeadcount(paymentId, headcountTo)
                    headcount = headcountTo
                }
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
