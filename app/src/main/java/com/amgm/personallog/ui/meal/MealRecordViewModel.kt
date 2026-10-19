package com.amgm.personallog.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.repository.MealPayment
import com.amgm.personallog.data.repository.MealRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class MealUiState(
    /** 表示中の月 "yyyy-MM"。 */
    val month: String = YearMonth.now().toString(),
    val loading: Boolean = true,
    /** 月の支払いごとの食べた記録(日付の新しい順)。 */
    val payments: List<MealPayment> = emptyList(),
    /** 今月、自分が食べた分の合計(円)。下書きの支払いも含む。 */
    val total: Long = 0,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealRecordViewModel @Inject constructor(
    private val repository: MealRecordRepository,
) : ViewModel() {

    private val focus = MutableStateFlow(YearMonth.now())
    private val _uiState = MutableStateFlow(MealUiState())
    val uiState: StateFlow<MealUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            focus.flatMapLatest { ym ->
                _uiState.update { it.copy(month = ym.toString(), loading = true) }
                repository.observeMonth(ym.toString())
                    .catch { e ->
                        if (e is CancellationException) throw e
                        _uiState.update { it.copy(loading = false, error = "食べた記録の読み込みに失敗しました: ${e.message}") }
                    }
            }.collect { payments ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        payments = payments,
                        total = payments.sumOf { p -> p.meal.rows.sumOf { r -> r.amount.toLong() } },
                    )
                }
            }
        }
    }

    fun previousMonth() { focus.value = focus.value.minusMonths(1) }
    fun nextMonth() { focus.value = focus.value.plusMonths(1) }
    fun dismissError() { _uiState.update { it.copy(error = null) } }
}
