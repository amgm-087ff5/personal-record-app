package com.amgm.personallog.ui.ledger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.repository.ExpenseRepository
import com.amgm.personallog.data.repository.ReceiptRepository
import com.amgm.personallog.data.repository.SplitRepository
import com.amgm.personallog.data.repository.toBuild
import com.amgm.personallog.data.settings.PaymentReadStore
import com.amgm.personallog.domain.ExpenseItems
import com.amgm.personallog.domain.PaymentEdit
import com.amgm.personallog.domain.PaymentMerge
import com.amgm.personallog.domain.UnknownAmounts
import com.amgm.personallog.domain.UnknownItems
import com.amgm.personallog.domain.PaymentField
import com.amgm.personallog.domain.PaymentKind
import com.amgm.personallog.domain.PaymentValues
import com.amgm.personallog.domain.WriteBackTarget
import com.amgm.personallog.domain.split.IndividualExpense
import com.amgm.personallog.domain.split.IndividualExpenses
import com.amgm.personallog.receipt.ReceiptDedup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 重複の組の1件(支払い)。summary は ReceiptDedup.summary。 */
data class DuplicateMember(val receiptId: Long, val summary: String)

/**
 * 紐づく支払いの重複の組。members は組の全員で、先頭がマージ先(登録日時の古い順・同じなら id の小さい順)。
 * hasDifference は、マージのときに選ぶ必要がある項目(比較キーが2種類以上)が1つでもあるか。
 */
data class DuplicateInfo(
    val setIds: List<Long>,
    val members: List<DuplicateMember>,
    /** この支払いがマージ先か。 */
    val isTarget: Boolean,
    val hasDifference: Boolean,
)

/**
 * 支出の詳細画面の表示内容。group が非 null なら自分の支出(まとまり)。items はその品目(必ず1つ以上)。
 * v14 で単独の支出は無くなった(旧ルートの EXPENSE は、その品目が属する支出を開く)。
 */
data class PaymentUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val kind: PaymentKind = PaymentKind.GROUP,
    val group: ExpenseGroup? = null,
    val items: List<Expense> = emptyList(),
    /** 家計簿で消した支払い由来の品目(一覧・合計には入らない。「元に戻す」で品目に戻る)。 */
    val deletedItems: List<Expense> = emptyList(),
    /** 紐づく支払い。サムネイル・支払い先・「レシートを開く」に使う。 */
    val receipt: Receipt? = null,
    /** 紐づく支払いの品目(金額不明の判定に使う。計算の正は支払い)。 */
    val paymentItems: List<ReceiptItem> = emptyList(),
    /** 支払いの日時 "yyyy-MM-dd HH:mm"。日時を変えるときの警告に使う。 */
    val receiptAt: String? = null,
    /** 割り勘の参加者の数(割り勘が無ければ1)。2人以上では金額をここで直せない。 */
    val splitPeople: Int = 1,
    /** 割り勘があるとき(参加者2人以上)の、自分の個別支出。計算できなければ null。 */
    val individual: IndividualExpense? = null,
    /** 割り勘で自分が参加者に入っていない(自分の分が0円で、家計簿に行が無い)支払い。receipt だけが入る。 */
    val selfExcluded: Boolean = false,
    /** 紐づく支払いが重複の組に入っていれば、その組の情報(マージ先・マージ元のどちらの画面にも出す)。 */
    val duplicate: DuplicateInfo? = null,
    val error: String? = null,
    /** 削除などで、この画面の対象が無くなった。 */
    val finished: Boolean = false,
) {
    val itemSum: Long get() = items.sumOf { it.amount.toLong() }

    /** 支出金額 - 品目の合計。まとまりでなければ 0。金額不明の品目は差を受け持つので、差が0以上なら 0 になる。 */
    val difference: Long get() = group?.let { it.amount.toLong() - itemSum } ?: 0L

    /**
     * 品目が1つだけか(消した品目も含めた行の数。ExpenseItems.normalize と同じ数え方)。
     * 1つだけなら品目の金額は支出の金額と同じ(品目の側では直せない)。
     */
    val onlyOneItem: Boolean get() = items.size + deletedItems.size == 1

    /** 消していない品目が1つだけか。最後の1つは消せない(支出の品目は1つ以上)。 */
    val lastVisibleItem: Boolean get() = items.size <= 1

    /** 金額不明の品目があるか。 */
    val hasUnknownItem: Boolean get() = items.any { it.unknownAmount }

    /** 支出の金額と品目の不一致(支出の金額 − 分かっている品目の合計)。エラーでなければ null(ExpenseItems.mismatch)。 */
    val mismatch: Long? get() = group?.let { ExpenseItems.mismatch(it, items) }

    /** 支払いの合計と品目の不一致(合計 − 分かっている品目の合計)。エラーでなければ null。支払いが無ければ null。 */
    val paymentMismatch: Long?
        get() = receipt?.takeIf { paymentItems.isNotEmpty() }?.let { UnknownAmounts.mismatch(it.totalAmount, paymentItems) }

    /**
     * 「未入力を全て不明にする」を出せるか(markBlankItemsUnknown)。
     * 支払いの合計と品目が合わず、金額が未入力(0円)の支払いの品目があるとき。
     */
    val canMarkBlankAsUnknown: Boolean
        get() = paymentMismatch != null && UnknownItems.canMarkBlankAsUnknown(paymentItems, UnknownAmounts::isBlank)

    /** 「差額を不明の品目として足す」で足す金額(addDifferenceAsUnknownItem)。出さないときは null。 */
    val differenceForUnknownItem: Long?
        get() = receipt?.takeIf { paymentItems.isNotEmpty() }?.let { UnknownAmounts.differenceForUnknownItem(it.totalAmount, paymentItems) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PaymentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExpenseRepository,
    private val receipts: ReceiptRepository,
    private val splits: SplitRepository,
    private val readStore: PaymentReadStore,
) : ViewModel() {
    private val rawKind: String? = savedStateHandle.get<String>("kind")
    /** 割り勘で自分が外れた支払い(家計簿に行が無い)の詳細か。id は支払い(レシート)の id。 */
    private val excludedMode: Boolean = rawKind == LedgerRoutes.EXCLUDED_KIND
    private val kind: PaymentKind = PaymentKind.of(rawKind) ?: PaymentKind.GROUP
    private val id: Long = savedStateHandle.get<Long>("id") ?: 0L

    private val _state = MutableStateFlow(PaymentUiState(kind = kind))
    val state: StateFlow<PaymentUiState> = _state.asStateFlow()

    /** 既読にした支払い(同じ支払いを何度も既読にしないため)。 */
    private var markedReadId: Long? = null

    /** 組の差分の計算結果(組の id が変わったときだけ計算し直す。mergeSnapshots は DB を読む)。 */
    private var differenceCache: Pair<List<Long>, Boolean>? = null

    /**
     * 自分が外れた支払いの画面。家計簿に行が無いので、支払いと割り勘だけを見る。
     * 家計簿に行が出来た(割り勘で自分が入った)ときは、通常の詳細とは別の画面なので notFound にして戻す。
     */
    private fun excludedFlow(receiptId: Long): Flow<PaymentUiState> =
        combine(receipts.observeReceipt(receiptId), receipts.observePaymentLink(receiptId)) { r, link -> r to link }
            .map { (r, link) ->
                if (r == null || link != null) {
                    PaymentUiState(loading = false, notFound = true, kind = kind)
                } else {
                    PaymentUiState(
                        loading = false,
                        kind = kind,
                        receipt = r,
                        receiptAt = repository.receiptDateTime(receiptId),
                        splitPeople = receipts.participantCount(receiptId),
                        selfExcluded = true,
                    )
                }
            }

    /**
     * まとまりに紐づく支払いと、その品目。確定(下書き→確定)は支払いの行を書き換えるので、支払いも観測する。
     * 支出の行が変わらなくても、支払いの状態(下書きか)の変化がこの画面に届くようにする。
     */
    private fun paymentFlow(gid: Long): Flow<Pair<Receipt?, List<ReceiptItem>>> =
        repository.observeGroup(gid).map { it?.receiptId }.distinctUntilChanged().flatMapLatest { rid ->
            if (rid == null) {
                flowOf<Pair<Receipt?, List<ReceiptItem>>>(null to emptyList())
            } else {
                combine(receipts.observeReceipt(rid), receipts.observeItems(rid)) { r, items -> r to items }
            }
        }

    /** 支出(まとまり)の画面。 */
    private fun groupFlow(gid: Long): Flow<PaymentUiState> =
        combine(repository.observeGroup(gid), repository.observeGroupItems(gid), paymentFlow(gid)) { g, items, payment ->
            if (g == null) {
                PaymentUiState(loading = false, notFound = true, kind = PaymentKind.GROUP)
            } else {
                val (receipt, paymentItems) = payment
                val (gone, visible) = items.sortedBy { it.id }.partition { it.deletedByUser }
                val people = g.receiptId?.let { receipts.participantCount(it) } ?: 1
                PaymentUiState(
                    loading = false, kind = PaymentKind.GROUP, group = g, items = visible, deletedItems = gone,
                    receipt = receipt, paymentItems = paymentItems, receiptAt = repository.receiptDateTime(g.receiptId),
                    splitPeople = people,
                    individual = if (people >= 2) selfIndividual(g.receiptId) else null,
                )
            }
        }

    init {
        viewModelScope.launch {
            val flow: Flow<PaymentUiState> = when {
                excludedMode -> excludedFlow(id)
                kind == PaymentKind.GROUP -> groupFlow(id)
                else -> {
                    // 旧ルート(単独の支出の id): 移行で品目になったので、その品目が属する支出を開く
                    val gid = try {
                        repository.groupIdOfExpense(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (gid == null) {
                        flowOf(PaymentUiState(loading = false, notFound = true, kind = PaymentKind.GROUP))
                    } else {
                        groupFlow(gid)
                    }
                }
            }
            // 重複の組(支払い一覧と同じ判定。マージ先・マージ元のどちらの画面にも出す)
            val sets = combine(receipts.observeReceipts(), receipts.observeNotDuplicates()) { list, notDup ->
                PaymentMerge.duplicateSets(list, ReceiptDedup.notDuplicateSet(notDup))
            }
            combine(flow, sets) { st, all -> st to all }
                .mapLatest { (st, all) ->
                    val rid = st.receipt?.id
                    st.copy(duplicate = rid?.let { duplicateInfo(it, PaymentMerge.setContaining(it, all)) })
                }
                .catch { e ->
                    _state.update { it.copy(loading = false, error = "読み込みに失敗しました: ${e.message}") }
                }.collect { s ->
                    // 支払いが分かった時点で既読にする
                    s.receipt?.id?.let { markRead(it) }
                    // 画面のエラーと削除の完了は、DB から来た値で消さない
                    _state.update { st -> s.copy(error = st.error, finished = st.finished) }
                }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** 支払いを既読にする(1回だけ)。既読の保存に失敗しても、画面は続ける。 */
    private fun markRead(receiptId: Long) {
        if (markedReadId == receiptId) return
        markedReadId = receiptId
        viewModelScope.launch {
            try {
                readStore.markRead(receiptId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 未読の印が残るだけなので、無視する
            }
        }
    }

    /**
     * 支払いの組の情報。set が null(組に入っていない)なら null。読めなければ、差分の無い組として扱わず null にする。
     */
    private suspend fun duplicateInfo(receiptId: Long, set: List<Receipt>?): DuplicateInfo? {
        if (set == null) return null
        val ids = set.map { it.id }
        val cached = differenceCache
        val differs = if (cached != null && cached.first == ids) {
            cached.second
        } else {
            try {
                PaymentMerge.hasDifference(PaymentMerge.fieldDiffs(receipts.mergeSnapshots(ids)))
                    .also { differenceCache = ids to it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
        }
        return DuplicateInfo(
            setIds = ids,
            members = set.map { DuplicateMember(it.id, ReceiptDedup.summary(it)) },
            isTarget = ids.first() == receiptId,
            hasDifference = differs,
        )
    }

    /**
     * 支払いの割り勘から、自分の個別支出を作る(読み込みは SplitRepository.load と toBuild。保存はしない)。
     * 支払いが無いか、計算できないときは null。まとまりの名前は、そこに入っている品名をつなぐ。
     */
    private suspend fun selfIndividual(receiptId: Long?): IndividualExpense? {
        if (receiptId == null) return null
        return try {
            val receipt = receipts.getReceipt(receiptId) ?: return null
            val items = receipts.getItems(receiptId)
            val data = splits.load(receiptId)
            val build = data.toBuild(receipt.totalAmount, items)
            val itemsById = items.associateBy { it.id }
            val names = data.groups.associate { gw ->
                val itemNames = gw.itemIds.mapNotNull { itemsById[it]?.name?.trim()?.ifEmpty { null } }.distinct()
                gw.group.id to itemNames.joinToString("・").ifEmpty { IndividualExpenses.UNKNOWN_NAME }
            }
            IndividualExpenses.of(build.input, build.selfParticipantId, names)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun run(onDone: ((String?) -> Unit)? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                onDone?.invoke(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = "保存に失敗しました: ${e.message ?: e.javaClass.simpleName}"
                if (onDone != null) onDone(msg) else _state.update { it.copy(error = msg) }
            }
        }
    }

    /**
     * 保存の本体。block は「理由(拒否・失敗)」を返す(成功なら null)。
     * onDone があれば、その結果を渡す(シートに出す)。無ければ画面のエラーに出す。
     */
    private fun write(onDone: ((String?) -> Unit)?, block: suspend () -> String?) {
        viewModelScope.launch {
            val msg = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "保存に失敗しました: ${e.message ?: e.javaClass.simpleName}"
            }
            if (onDone != null) onDone(msg) else if (msg != null) _state.update { it.copy(error = msg) }
        }
    }

    /** 支出のまとまりの金額・日時・カテゴリ・メモを直す。日時と金額は支払いへ書き戻す。 */
    fun saveGroup(
        original: ExpenseGroup,
        date: String,
        time: String,
        amount: Int,
        category: String,
        memo: String,
        onDone: (String?) -> Unit,
    ) = write(onDone) {
        if (original.receiptId == null) {
            repository.updateGroup(PaymentEdit.editGroup(original, date, time, amount, category, memo))
            null
        } else {
            receipts.writeBackFromLedger(
                WriteBackTarget.Group(original),
                PaymentValues(date, time, amount, category, name = "", memo = memo),
            )
        }
    }

    /** 品目を直す。original が null なら、まとまりへ品目を足す。 */
    fun saveItem(
        group: ExpenseGroup?,
        original: Expense?,
        date: String,
        time: String,
        amount: Int,
        category: String,
        name: String,
        memo: String,
        onDone: (String?) -> Unit,
    ) = write(onDone) {
        // まとまりの品目の日時はまとまりに従う(月・日の振り分けはまとまりの日付)。品目の側では変えない
        val d = group?.date ?: date
        val t = group?.time ?: time
        if (original != null) {
            if (original.receiptId == null) {
                repository.update(PaymentEdit.editExpense(original, d, t, amount, category, name, memo))
                null
            } else {
                // 品目が1つだけなら、その金額は支出の金額と同じ(品目の側では直せない)
                val onlyItem = _state.value.onlyOneItem
                receipts.writeBackFromLedger(
                    WriteBackTarget.Item(original, onlyItem = onlyItem),
                    PaymentValues(d, t, amount, category, name, memo),
                )
            }
        } else if (group != null) {
            // 品目は支払いの品目として足す。家計簿の品目は、支払いを保存するときに作られる(支出だけを単独では作らない)
            val receiptId = checkNotNull(group.receiptId) { "支払いに紐づかない支出には品目を足せません" }
            val receipt = checkNotNull(receipts.getReceipt(receiptId)) { "支払いが見つかりません" }
            val item = ReceiptItem(
                receiptId = receiptId, name = name.trim(), amount = amount, category = category, memo = memo.trim(),
            )
            receipts.save(receipt, receipts.getItems(receiptId) + item)
            null
        } else {
            null
        }
    }

    /** 支払い先(店名)を直す。 */
    fun saveStore(receiptId: Long, storeName: String, onDone: (String?) -> Unit) = write(onDone) {
        if (storeName.isBlank()) {
            "支払い先を入力してください。"
        } else {
            receipts.saveStoreName(receiptId, storeName)
            null
        }
    }

    /** 品目の合計を、まとまりの金額に入れる(差額を無くす)。 */
    fun useItemsSumAsTotal() {
        val s = _state.value
        val g = s.group ?: return
        val sum = s.itemSum
        if (sum <= 0L || sum > Int.MAX_VALUE) return
        write(null) {
            if (g.receiptId == null) {
                repository.updateGroup(PaymentEdit.editGroup(g, g.date, g.time, sum.toInt(), g.category, g.memo))
                null
            } else {
                receipts.writeBackFromLedger(
                    WriteBackTarget.Group(g),
                    PaymentValues(g.date, g.time, sum.toInt(), g.category, name = "", memo = g.memo),
                )
            }
        }
    }

    /** 家計簿で消した品目を元に戻す。 */
    fun restoreItem(expense: Expense) = run { repository.restore(expense) }

    /** 品目の1つの欄を、支払いの値に戻す。 */
    fun revertItemField(expense: Expense, field: PaymentField) = run {
        if (!repository.revertExpenseField(expense.id, field)) {
            _state.update { it.copy(error = "支払いの値を求められませんでした。") }
        }
    }

    /** 支出のまとまりの1つの欄を、支払いの値に戻す。 */
    fun revertGroupField(group: ExpenseGroup, field: PaymentField) = run {
        if (!repository.revertGroupField(group.id, field)) {
            _state.update { it.copy(error = "支払いの値を求められませんでした。") }
        }
    }

    /** 品目を消す。支出の品目は1つ以上必要なので、最後の1つは消さずに理由を出す(支出ごと消すのは deleteGroup)。 */
    fun deleteItem(expense: Expense) {
        if (_state.value.items.size <= 1) {
            _state.update { it.copy(error = ExpenseRepository.MESSAGE_LAST_ITEM) }
            return
        }
        run { repository.delete(expense) }
    }

    // ------------------------------------------------------------ 金額不明(段階2の画面から呼ぶ)

    /**
     * 「未入力を全て不明にする」。支出の品目のうち金額が未入力(0円)のものを、金額不明にする。
     * 支払いに紐づく支出では、計算の正は支払いなので、支払いの品目(金額が0のもの)を不明にして家計簿を作り直す
     * (ReceiptRepository.markBlankItemsUnknown)。割り勘が2人以上のときは、金額の内訳は割り勘で決まるので行わない。
     */
    fun markBlankItemsUnknown(onDone: ((String?) -> Unit)? = null) = write(onDone) {
        val g = _state.value.group ?: return@write null
        val receiptId = g.receiptId ?: return@write MESSAGE_NOT_LINKED
        if (_state.value.splitPeople >= 2) return@write MESSAGE_SPLIT_UNKNOWN
        receipts.markBlankItemsUnknown(receiptId)
    }

    /**
     * 「差額を不明の品目として足す」。支払いの合計 − 分かっている品目の合計 を、金額不明の品目1つとして支払いに足す
     * (ReceiptRepository.addDifferenceAsUnknownItem)。割り勘が2人以上のときは行わない。
     */
    fun addDifferenceAsUnknownItem(onDone: ((String?) -> Unit)? = null) = write(onDone) {
        val g = _state.value.group ?: return@write null
        val receiptId = g.receiptId ?: return@write MESSAGE_NOT_LINKED
        if (_state.value.splitPeople >= 2) return@write MESSAGE_SPLIT_UNKNOWN
        receipts.addDifferenceAsUnknownItem(receiptId)
    }

    /**
     * 1つの品目の「金額不明」を切り替える。支払いの品目に紐づく品目だけ(支払いの品目の印を変えて家計簿を作り直す)。
     * 品目の無い支払いの1品目(receiptItemId なし)は、金額が支出と同じなので不明にできない。
     */
    fun setItemUnknown(expense: Expense, unknown: Boolean, onDone: ((String?) -> Unit)? = null) = write(onDone) {
        val receiptId = expense.receiptId ?: return@write MESSAGE_NOT_LINKED
        val itemId = expense.receiptItemId ?: return@write MESSAGE_BLANK_ITEM_UNKNOWN
        if (_state.value.splitPeople >= 2) return@write MESSAGE_SPLIT_UNKNOWN
        receipts.setItemUnknown(receiptId, itemId, unknown)
    }

    companion object {
        const val MESSAGE_NOT_LINKED = "この支出は支払いに紐づいていません。"
        const val MESSAGE_SPLIT_UNKNOWN = "負担割りがあるため、品目の金額は支払いの画面で直してください。"
        const val MESSAGE_BLANK_ITEM_UNKNOWN = "品目が1つのときは、品目の金額は支出の金額と同じです。"
    }

    fun deleteGroup() {
        val g = _state.value.group ?: return
        run {
            repository.deleteGroup(g)
            _state.update { it.copy(finished = true) }
        }
    }
}
