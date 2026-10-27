package com.amgm.personallog.ui.ledger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.repository.ExpenseRepository
import com.amgm.personallog.data.settings.PaymentReadStore
import com.amgm.personallog.domain.PaymentMerge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 重複候補 1 組のマージ画面の表示内容。members の先頭がマージ先(登録日時が最も古い)。 */
data class PaymentMergeUiState(
    val loading: Boolean = true,
    /** マージできる件数(2件以上)がそろわなかった。削除済みの支払いが混じったときなど。 */
    val notFound: Boolean = false,
    val members: List<ExpenseGroup> = emptyList(),
    /** レシートの id → 店名(店名が空のレシートは入らない)。 */
    val storeNames: Map<Long, String> = emptyMap(),
    val fieldDiffs: List<PaymentMerge.FieldDiff> = emptyList(),
    /** 項目ごとに今選ばれている値。CONFLICT は入るまで空。 */
    val chosen: Map<PaymentMerge.Field, String> = emptyMap(),
    /** 手入力の途中の文字(検証に通らなくても残す)。 */
    val drafts: Map<PaymentMerge.Field, String> = emptyMap(),
    /** 手入力の欄を開いている項目。 */
    val manualFields: Set<PaymentMerge.Field> = emptySet(),
    /** 手入力の検証エラー。空でなければマージできない。 */
    val errors: Map<PaymentMerge.Field, String> = emptyMap(),
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canMerge: Boolean
        get() = members.size >= 2 &&
            fieldDiffs.filter { it.kind == PaymentMerge.Kind.CONFLICT }.all { chosen.containsKey(it.field) } &&
            errors.isEmpty()
}

@HiltViewModel
class PaymentMergeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExpenseRepository,
    private val readStore: PaymentReadStore,
) : ViewModel() {
    /** ナビゲーションの引数 "ids"(カンマ区切りの支払いの id)。 */
    private val ids: List<Long> = savedStateHandle.get<String>(LedgerRoutes.MERGE_IDS_ARG).orEmpty()
        .split(",")
        .mapNotNull { it.trim().toLongOrNull() }
        .distinct()

    private val _state = MutableStateFlow(PaymentMergeUiState())
    val state: StateFlow<PaymentMergeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                // マージ先は登録日時が最も古いもの(同じなら id が小さい方)
                val groups = repository.groupsByIds(ids)
                    .sortedWith(compareBy<ExpenseGroup>({ it.createdAt }, { it.id }))
                if (groups.size < 2) {
                    _state.update { it.copy(loading = false, notFound = true, members = groups) }
                    return@launch
                }
                val storeNames = groups.mapNotNull { it.receiptId }.distinct().mapNotNull { rid ->
                    repository.receiptStoreName(rid)?.takeIf { it.isNotBlank() }?.let { rid to it }
                }.toMap()
                val diffs = PaymentMerge.fieldDiffs(groups)
                _state.update {
                    it.copy(
                        loading = false,
                        members = groups,
                        storeNames = storeNames,
                        fieldDiffs = diffs,
                        chosen = PaymentMerge.defaultTexts(diffs),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "読み込みに失敗しました: ${e.message}") }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** 候補のどれかを選ぶ。 */
    fun choose(field: PaymentMerge.Field, text: String) = _state.update {
        it.copy(chosen = it.chosen + (field to text), errors = it.errors - field, drafts = it.drafts - field)
    }

    /** 手入力の欄を開く。DATE・TIME・AMOUNT・MEMO だけ。 */
    fun startManual(field: PaymentMerge.Field) {
        if (!isTypable(field)) return
        _state.update { it.copy(manualFields = it.manualFields + field) }
    }

    /** 手入力。検証に通れば chosen に入れ、通らなければ errors に入れる。CATEGORY と RECEIPT は手入力できない。 */
    fun typeManually(field: PaymentMerge.Field, text: String) {
        if (!isTypable(field)) return
        val error = PaymentMerge.validate(field, text)
        _state.update {
            if (error == null) {
                it.copy(
                    chosen = it.chosen + (field to text),
                    errors = it.errors - field,
                    drafts = it.drafts + (field to text),
                )
            } else {
                it.copy(errors = it.errors + (field to error), drafts = it.drafts + (field to text))
            }
        }
    }

    /** 競合が残っていなければマージし、マージ元を未読から外して onDone を呼ぶ。失敗は error に入れる。 */
    fun merge(onDone: () -> Unit) {
        val s = _state.value
        if (s.busy || !s.canMerge) return
        val defaults = PaymentMerge.defaultTexts(s.fieldDiffs)
        // 全項目の値をそろえる(足りない項目は自動で決まった値で補う)
        val finalTexts = PaymentMerge.Field.entries.associateWith { f -> s.chosen[f] ?: defaults[f] ?: "" }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.mergeGroups(s.members, finalTexts)
                readStore.forget(s.members.drop(1).map { it.id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "マージに失敗しました: ${e.message}") }
                return@launch
            }
            _state.update { it.copy(busy = false) }
            onDone()
        }
    }

    private fun isTypable(field: PaymentMerge.Field): Boolean =
        field == PaymentMerge.Field.DATE ||
            field == PaymentMerge.Field.TIME ||
            field == PaymentMerge.Field.AMOUNT ||
            field == PaymentMerge.Field.MEMO
}
