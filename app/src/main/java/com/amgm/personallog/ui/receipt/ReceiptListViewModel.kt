package com.amgm.personallog.ui.receipt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.repository.ReceiptRepository
import com.amgm.personallog.receipt.ReceiptAdjust
import com.amgm.personallog.receipt.ReceiptDedup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 支払い一覧(ReceiptScreen)の ViewModel。一覧・絞り込み・重複の表示を持つ。
 * 取り込みの処理は [ReceiptImportCoordinator] が行い、ここはその状態を見せて操作を頼むだけ。
 */
@HiltViewModel
class ReceiptListViewModel @Inject constructor(
    private val repository: ReceiptRepository,
    private val importer: ReceiptImportCoordinator,
) : ViewModel() {

    val receipts: StateFlow<List<Receipt>> = repository.observeReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 同じ内容の別レシートがある「後から作られた側」 -> 原本。一覧の警告に使う。 */
    val possibleDuplicates: StateFlow<Map<Long, Receipt>> =
        combine(receipts, repository.observeNotDuplicates()) { list, notDup ->
            // 「重複ではない」と判断した組は警告しない
            ReceiptDedup.laterDuplicates(list, ReceiptDedup.notDuplicateSet(notDup))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 取り込みの状態(アプリで1つ。家計簿と共有)。 */
    val state: StateFlow<ReceiptListUiState> = importer.state

    fun dismissError() = importer.dismissError()

    fun toggleAddAnyway(key: Long) = importer.toggleAddAnyway(key)

    fun resolveDuplicates(addChecked: Boolean) = importer.resolveDuplicates(addChecked)

    fun setAdjust(key: Long, adjust: ReceiptAdjust) = importer.setAdjust(key, adjust)

    fun analyzeAdjusted() = importer.analyzeAdjusted()

    fun analyzeWithoutAdjust() = importer.analyzeWithoutAdjust()

    fun cancelAdjust() = importer.cancelAdjust()
}
