package com.amgm.personallog.ui.ledger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.repository.PaymentRepository
import com.amgm.personallog.data.settings.PaymentReadStore
import com.amgm.personallog.domain.PaymentMerge
import com.amgm.personallog.receipt.ReceiptImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 重複の1組のマージ画面の状態。 */
data class PaymentMergeUiState(
    val loading: Boolean = true,
    /** 登録の古い順。先頭がマージ先。 */
    val snapshots: List<PaymentMerge.Snapshot> = emptyList(),
    val diffs: List<PaymentMerge.FieldDiff> = emptyList(),
    val choices: Map<PaymentMerge.Field, PaymentMerge.Choice> = emptyMap(),
    /** 手入力の途中文字(入力欄に出す値)。 */
    val drafts: Map<PaymentMerge.Field, String> = emptyMap(),
    /** 手入力の検査エラー。 */
    val errors: Map<PaymentMerge.Field, String> = emptyMap(),
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    /** すべての CONFLICT に選択があり、手入力にエラーが無く、処理中でないとき。 */
    val canMerge: Boolean
        get() = !loading && !busy && snapshots.size >= 2 && errors.isEmpty() &&
            diffs.filter { it.kind == PaymentMerge.Kind.CONFLICT }.all { it.field in choices }
}

@HiltViewModel
class PaymentMergeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PaymentRepository,
    private val readStore: PaymentReadStore,
    private val images: ReceiptImageStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentMergeUiState())
    val state: StateFlow<PaymentMergeUiState> = _state.asStateFlow()

    init {
        val ids = savedStateHandle.get<String>(LedgerRoutes.MERGE_IDS_ARG).orEmpty()
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
        viewModelScope.launch {
            try {
                val snapshots = if (ids.size < 2) emptyList() else repository.mergeSnapshots(ids)
                if (snapshots.size < 2) {
                    _state.update { it.copy(loading = false, error = "マージできる支払いがありません") }
                    return@launch
                }
                val ordered = PaymentMerge.ordered(snapshots)
                val diffs = PaymentMerge.fieldDiffs(ordered)
                _state.update {
                    it.copy(
                        loading = false,
                        snapshots = ordered,
                        diffs = diffs,
                        choices = PaymentMerge.defaultChoices(diffs),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "読み込みに失敗しました: ${e.message.orEmpty()}") }
            }
        }
    }

    /** 候補の支払いの値を選ぶ。手入力の文字と検査エラーは消す。 */
    fun choose(field: PaymentMerge.Field, paymentId: Long) {
        _state.update {
            it.copy(
                choices = it.choices + (field to PaymentMerge.Choice.From(paymentId)),
                drafts = it.drafts - field,
                errors = it.errors - field,
            )
        }
    }

    /** 手入力する(日付・時刻・支払い先だけ)。検査が通れば選択にし、通らなければエラーを出して選択を外す。 */
    fun typeManually(field: PaymentMerge.Field, text: String) {
        if (!PaymentMerge.canManual(field)) return
        val error = PaymentMerge.validate(field, text)
        _state.update {
            if (error == null) {
                it.copy(
                    choices = it.choices + (field to PaymentMerge.Choice.Manual(text)),
                    drafts = it.drafts + (field to text),
                    errors = it.errors - field,
                )
            } else {
                it.copy(
                    choices = it.choices - field,
                    drafts = it.drafts + (field to text),
                    errors = it.errors + (field to error),
                )
            }
        }
    }

    /** マージを実行する。成功したら onDone を呼ぶ。 */
    fun merge(onDone: () -> Unit) {
        val current = _state.value
        if (!current.canMerge) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val plan = PaymentMerge.plan(current.snapshots, current.choices)
                val unusedImages = repository.mergePayments(plan)
                unusedImages.forEach { images.delete(it) }
                readStore.forget(plan.removed)
                _state.update { it.copy(busy = false, done = true) }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "マージに失敗しました: ${e.message.orEmpty()}") }
            }
        }
    }
}
