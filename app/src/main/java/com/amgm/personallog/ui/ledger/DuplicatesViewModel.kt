package com.amgm.personallog.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.repository.PaymentRepository
import com.amgm.personallog.domain.PaymentMerge
import com.amgm.personallog.receipt.PaymentDedup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 重複の候補1組(一覧の1枚)。payments の先頭がマージ先で、以降はマージ元。
 * diffs は [PaymentMerge.fieldDiffs] の結果(Field の定義順)。
 */
data class DuplicateGroup(
    val payments: List<Payment>,
    val diffs: List<PaymentMerge.FieldDiff>,
) {
    /** 組の支払いの id(並びはマージ先から)。マージと「重複ではない」はこの id で動く。 */
    val ids: List<Long> = payments.map { it.id }

    /** 差分が1つでもある(比較キーが2種類以上の項目がある)。 */
    val hasDifference: Boolean = PaymentMerge.hasDifference(diffs)

    /** 差分のある項目だけ(一覧の差分に出す)。 */
    val differingDiffs: List<PaymentMerge.FieldDiff> = diffs.filter { it.differs }

    /** 候補から選ぶ必要がある項目(CONFLICT)の数。 */
    val conflictCount: Int = diffs.count { it.kind == PaymentMerge.Kind.CONFLICT }
}

/** 重複の一覧の状態。読み込み前は [Loading] で、「ありません」は出さない。 */
sealed interface DuplicatesUiState {
    object Loading : DuplicatesUiState
    data class Error(val message: String) : DuplicatesUiState
    data class Loaded(val groups: List<DuplicateGroup>) : DuplicatesUiState
}

/**
 * 重複の可能性がある支払いの一覧(DuplicatesScreen)の ViewModel。
 * 判定は [PaymentDedup] と [PaymentMerge.duplicateSets] に任せ、ここは組ごとの差分を用意する。
 */
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val repository: PaymentRepository,
) : ViewModel() {

    private val _actionError = MutableStateFlow<String?>(null)

    /** 「重複ではない」の保存に失敗したときの文言。成功すると消える。 */
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val state: StateFlow<DuplicatesUiState> = combine(
        repository.observePayments(),
        repository.observeNotDuplicates(),
    ) { payments, notDup ->
        PaymentMerge.duplicateSets(payments, PaymentDedup.notDuplicateSet(notDup))
    }
        .mapLatest<List<List<Payment>>, DuplicatesUiState> { sets ->
            DuplicatesUiState.Loaded(
                sets.map { buildGroup(it) }.filter { it.payments.size >= 2 },
            )
        }
        .catch { emit(DuplicatesUiState.Error("重複の候補を読み込めませんでした。")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuplicatesUiState.Loading)

    /** 組の支払いを比較して、一覧に出す形にする。 */
    private suspend fun buildGroup(set: List<Payment>): DuplicateGroup {
        val snapshots = repository.mergeSnapshots(set.map { it.id })
        return DuplicateGroup(
            payments = snapshots.map { it.payment },
            diffs = PaymentMerge.fieldDiffs(snapshots),
        )
    }

    /** 組のすべての組み合わせを「重複ではない」として保存する。組は一覧から消える。 */
    fun markNotDuplicate(ids: List<Long>) {
        viewModelScope.launch {
            try {
                for (i in ids.indices) {
                    for (j in i + 1 until ids.size) {
                        repository.markNotDuplicate(ids[i], ids[j])
                    }
                }
                _actionError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = "保存できませんでした。もう一度お試しください。"
            }
        }
    }
}
