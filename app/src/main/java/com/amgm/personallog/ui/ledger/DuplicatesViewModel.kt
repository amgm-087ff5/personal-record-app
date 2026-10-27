package com.amgm.personallog.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.repository.ExpenseRepository
import com.amgm.personallog.domain.PaymentMerge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 重複候補の組の中の1件(支払い)。storeName は紐づくレシートの店名(無ければ null)。 */
data class DuplicateMemberUi(
    val group: ExpenseGroup,
    val storeName: String?,
)

/** 重複候補の1組。members[0] がマージ先(登録日時が最も古い支払い)。 */
data class DuplicateSetUi(
    val members: List<DuplicateMemberUi>,
    /** 項目ごとの比較結果。「差分を開く」で表示する。 */
    val diffs: List<PaymentMerge.FieldDiff>,
    /** 組に差分ありの項目があるか。 */
    val hasDifference: Boolean,
    /** 競合(入力済みの値が2種類以上)の項目数。 */
    val conflictCount: Int,
) {
    /** 組の支払いの id。マージ先から順。 */
    val ids: List<Long> get() = members.map { it.group.id }
}

data class DuplicatesUiState(
    val loading: Boolean = true,
    val sets: List<DuplicateSetUi> = emptyList(),
    val error: String? = null,
)

/** 重複の可能性がある支払いの一覧。判定は PaymentMerge に任せ、ここは表示用の組に直す。 */
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val repository: ExpenseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllGroups()
                .catch { e ->
                    _uiState.update { it.copy(loading = false, error = "重複の確認に失敗しました: ${e.message}") }
                }
                .collect { groups ->
                    val sets = PaymentMerge.duplicateSets(groups).map { toSetUi(it) }
                    _uiState.update { it.copy(loading = false, sets = sets, error = null) }
                }
        }
    }

    /** 1組を表示用に直す。店名はレシートから引くので suspend。 */
    private suspend fun toSetUi(group: List<ExpenseGroup>): DuplicateSetUi {
        val diffs = PaymentMerge.fieldDiffs(group)
        return DuplicateSetUi(
            members = group.map { DuplicateMemberUi(it, repository.receiptStoreName(it.receiptId)) },
            diffs = diffs,
            hasDifference = PaymentMerge.hasDifference(diffs),
            conflictCount = diffs.count { it.kind == PaymentMerge.Kind.CONFLICT },
        )
    }
}
