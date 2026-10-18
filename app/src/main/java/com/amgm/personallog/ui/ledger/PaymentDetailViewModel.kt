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
import com.amgm.personallog.domain.PaymentEdit
import com.amgm.personallog.domain.PaymentField
import com.amgm.personallog.domain.PaymentKind
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

/** 支払いの詳細画面の表示内容。group が非 null ならグループ、single が非 null なら単独の支出。 */
data class PaymentUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val kind: PaymentKind = PaymentKind.GROUP,
    val group: ExpenseGroup? = null,
    val items: List<Expense> = emptyList(),
    /** 家計簿で消したレシート由来の品目(一覧・合計には入らない。「元に戻す」で品目に戻る)。 */
    val deletedItems: List<Expense> = emptyList(),
    val single: Expense? = null,
    /** 紐づくレシート(あれば)。サムネイルと「レシートを開く」に使う。 */
    val receipt: Receipt? = null,
    /** レシートの日時 "yyyy-MM-dd HH:mm"。日時を変えるときの警告に使う。 */
    val receiptAt: String? = null,
    val error: String? = null,
    /** 削除などで、この画面の対象が無くなった。 */
    val finished: Boolean = false,
) {
    val itemSum: Long get() = items.sumOf { it.amount.toLong() }

    /** 支払い金額 - 品目の合計。グループでなければ 0。 */
    val difference: Long get() = group?.let { it.amount.toLong() - itemSum } ?: 0L
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PaymentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExpenseRepository,
    private val receipts: ReceiptRepository,
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
                                PaymentUiState(
                                    loading = false, kind = kind, group = g, items = visible, deletedItems = gone,
                                    receipt = receipt, receiptAt = repository.receiptDateTime(g.receiptId),
                                )
                            }
                        }
                PaymentKind.EXPENSE ->
                    repository.observeExpense(id).map { e ->
                        if (e == null) {
                            PaymentUiState(loading = false, notFound = true, kind = kind)
                        } else {
                            val receipt = e.receiptId?.let { repository.receipt(it) }
                            PaymentUiState(
                                loading = false, kind = kind, single = e, items = listOf(e),
                                receipt = receipt, receiptAt = repository.receiptDateTime(e.receiptId),
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

    /** グループの合計・日時・メモ・カテゴリを直す。変わった欄は「編集済み」になる。 */
    fun saveGroup(
        original: ExpenseGroup,
        date: String,
        time: String,
        amount: Int,
        category: String,
        memo: String,
        onDone: (String?) -> Unit,
    ) = run(onDone) { repository.updateGroup(PaymentEdit.editGroup(original, date, time, amount, category, memo)) }

    /** 品目(または単独の支出)を直す。original が null なら、グループへ品目を足す。 */
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
    ) = run(onDone) {
        // グループの品目の日時はグループに従う(月・日の振り分けはグループの日付)。品目の側では変えない
        val d = group?.date ?: date
        val t = group?.time ?: time
        if (original != null) {
            repository.update(PaymentEdit.editExpense(original, d, t, amount, category, name, memo))
        } else if (group != null) {
            // 品目は支払い(レシート)の品目として足す。家計簿の品目は、支払いを保存するときに作られる(支出だけを単独では作らない)
            val receiptId = checkNotNull(group.receiptId) { "レシートに紐づかない支払いには品目を足せません" }
            val receipt = checkNotNull(receipts.getReceipt(receiptId)) { "レシートが見つかりません" }
            val item = ReceiptItem(
                receiptId = receiptId, name = name.trim(), amount = amount, category = category, memo = memo.trim(),
            )
            receipts.save(receipt, receipts.getItems(receiptId) + item)
        }
    }

    /** 品目の合計を、グループの合計に入れる(差額を無くす)。 */
    fun useItemsSumAsTotal() {
        val s = _state.value
        val g = s.group ?: return
        val sum = s.itemSum
        if (sum <= 0L || sum > Int.MAX_VALUE) return
        run { repository.updateGroup(PaymentEdit.editGroup(g, g.date, g.time, sum.toInt(), g.category, g.memo)) }
    }

    /** 家計簿で消した品目を元に戻す。 */
    fun restoreItem(expense: Expense) = run { repository.restore(expense) }

    /** 品目(または単独の支出)の1つの欄を、レシートの値に戻す。 */
    fun revertItemField(expense: Expense, field: PaymentField) = run {
        if (!repository.revertExpenseField(expense.id, field)) {
            _state.update { it.copy(error = "レシートの値を求められませんでした。") }
        }
    }

    /** 支払い(グループ)の1つの欄を、レシートの値に戻す。 */
    fun revertGroupField(group: ExpenseGroup, field: PaymentField) = run {
        if (!repository.revertGroupField(group.id, field)) {
            _state.update { it.copy(error = "レシートの値を求められませんでした。") }
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
