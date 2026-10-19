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
import com.amgm.personallog.domain.PaymentEdit
import com.amgm.personallog.domain.PaymentField
import com.amgm.personallog.domain.PaymentKind
import com.amgm.personallog.domain.PaymentValues
import com.amgm.personallog.domain.WriteBackTarget
import com.amgm.personallog.domain.split.IndividualExpense
import com.amgm.personallog.domain.split.IndividualExpenses
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 支出の詳細画面の表示内容。group が非 null ならまとまり(支払い全体)、single が非 null なら単独の支出。 */
data class PaymentUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val kind: PaymentKind = PaymentKind.GROUP,
    val group: ExpenseGroup? = null,
    val items: List<Expense> = emptyList(),
    /** 家計簿で消した支払い由来の品目(一覧・合計には入らない。「元に戻す」で品目に戻る)。 */
    val deletedItems: List<Expense> = emptyList(),
    val single: Expense? = null,
    /** 紐づく支払い。サムネイル・支払い先・「レシートを開く」に使う。 */
    val receipt: Receipt? = null,
    /** 支払いの日時 "yyyy-MM-dd HH:mm"。日時を変えるときの警告に使う。 */
    val receiptAt: String? = null,
    /** 割り勘の参加者の数(割り勘が無ければ1)。2人以上では金額をここで直せない。 */
    val splitPeople: Int = 1,
    /** 割り勘があるとき(参加者2人以上)の、自分の個別支出。計算できなければ null。 */
    val individual: IndividualExpense? = null,
    val error: String? = null,
    /** 削除などで、この画面の対象が無くなった。 */
    val finished: Boolean = false,
) {
    val itemSum: Long get() = items.sumOf { it.amount.toLong() }

    /** 支出金額 - 品目の合計。まとまりでなければ 0。 */
    val difference: Long get() = group?.let { it.amount.toLong() - itemSum } ?: 0L
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PaymentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExpenseRepository,
    private val receipts: ReceiptRepository,
    private val splits: SplitRepository,
) : ViewModel() {
    private val kind: PaymentKind = PaymentKind.of(savedStateHandle.get<String>("kind")) ?: PaymentKind.GROUP
    private val id: Long = savedStateHandle.get<Long>("id") ?: 0L

    private val _state = MutableStateFlow(PaymentUiState(kind = kind))
    val state: StateFlow<PaymentUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val flow: Flow<PaymentUiState> = when (kind) {
                PaymentKind.GROUP ->
                    combine(repository.observeGroup(id), repository.observeGroupItems(id)) { g, items -> g to items }
                        .map { (g, items) ->
                            if (g == null) {
                                PaymentUiState(loading = false, notFound = true, kind = kind)
                            } else {
                                val receipt = g.receiptId?.let { repository.receipt(it) }
                                val (gone, visible) = items.sortedBy { it.id }.partition { it.deletedByUser }
                                val people = g.receiptId?.let { receipts.participantCount(it) } ?: 1
                                PaymentUiState(
                                    loading = false, kind = kind, group = g, items = visible, deletedItems = gone,
                                    receipt = receipt, receiptAt = repository.receiptDateTime(g.receiptId),
                                    splitPeople = people,
                                    individual = if (people >= 2) selfIndividual(g.receiptId) else null,
                                )
                            }
                        }
                PaymentKind.EXPENSE ->
                    repository.observeExpense(id).map { e ->
                        if (e == null) {
                            PaymentUiState(loading = false, notFound = true, kind = kind)
                        } else {
                            val receipt = e.receiptId?.let { repository.receipt(it) }
                            val people = e.receiptId?.let { receipts.participantCount(it) } ?: 1
                            PaymentUiState(
                                loading = false, kind = kind, single = e, items = listOf(e),
                                receipt = receipt, receiptAt = repository.receiptDateTime(e.receiptId),
                                splitPeople = people,
                                individual = if (people >= 2) selfIndividual(e.receiptId) else null,
                            )
                        }
                    }
            }
            flow.catch { e ->
                _state.update { it.copy(loading = false, error = "読み込みに失敗しました: ${e.message}") }
            }.collect { s -> _state.update { s.copy(error = it.error, finished = it.finished) } }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

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

    /** 品目(または単独の支出)を直す。original が null なら、まとまりへ品目を足す。 */
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
                val target = if (original.receiptItemId != null) {
                    WriteBackTarget.Item(original)
                } else {
                    WriteBackTarget.Single(original)
                }
                receipts.writeBackFromLedger(target, PaymentValues(d, t, amount, category, name, memo))
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

    /** 品目(または単独の支出)の1つの欄を、支払いの値に戻す。 */
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

    fun deleteItem(expense: Expense) = run {
        repository.delete(expense)
        if (kind == PaymentKind.EXPENSE) _state.update { it.copy(finished = true) }
    }

    fun deleteGroup() {
        val g = _state.value.group ?: return
        run {
            repository.deleteGroup(g)
            _state.update { it.copy(finished = true) }
        }
    }
}
