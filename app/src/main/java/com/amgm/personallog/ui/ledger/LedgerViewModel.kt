package com.amgm.personallog.ui.ledger

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.amgm.personallog.backup.BackupException
import com.amgm.personallog.backup.BackupManager
import com.amgm.personallog.backup.BackupProgress
import com.amgm.personallog.backup.ImportMode
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.repository.ExpenseRepository
import com.amgm.personallog.data.repository.ReceiptRepository
import com.amgm.personallog.data.repository.SplitRepository
import com.amgm.personallog.domain.LedgerCalc
import com.amgm.personallog.receipt.ReceiptAdjust
import com.amgm.personallog.receipt.ReceiptDedup
import com.amgm.personallog.ui.receipt.ReceiptImportCoordinator
import com.amgm.personallog.ui.receipt.ReceiptListUiState
import com.amgm.personallog.ui.receipt.draftReceiptIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BackupUiState(
    val progress: BackupProgress? = null,
    val pendingImport: Uri? = null,
    val resultTitle: String? = null,
    val resultMessage: String? = null,
)

data class LedgerUiState(
    val month: String = YearMonth.now().toString(),
    val loading: Boolean = true,
    val total: Long = 0,
    val shares: List<CategoryShare> = emptyList(),
    val days: List<DayGroup> = emptyList(),
    val chartLevel: ChartLevel = ChartLevel.YEAR,
    /** 積み上げグラフの見出し(例: "2026年10月")。 */
    val chartLabel: String = "",
    val chartBars: List<ChartBar> = emptyList(),
    /** グラフ表示期間全体のカテゴリ別内訳(円と割合)。 */
    val chartLegend: List<CategoryShare> = emptyList(),
    /** この月の行のうち、下書きのレシートに紐づく件数(合計に入っている下書き)。 */
    val draftCount: Int = 0,
    /** この月の行のうち、割り勘に警告がある支払いの件数。 */
    val warnCount: Int = 0,
    /** 下書きのレシートの id(行の「下書き」の印に使う)。 */
    val draftReceiptIds: Set<Long> = emptySet(),
    /** 割り勘に警告があるレシートの id(行の「要確認」の印に使う)。 */
    val warnReceiptIds: Set<Long> = emptySet(),
    /** 画像のあるレシート(レシート由来の支払い)の id。行の「レシート由来」の印に使う。手入力の支払い(画像なし)は含まない。 */
    val imageReceiptIds: Set<Long> = emptySet(),
    /** 重複の候補がある支払い(後から作られた側)の id。行の「重複?」の印に使う。支払い一覧と同じ判定。 */
    val duplicateReceiptIds: Set<Long> = emptySet(),
    /** この月の行のうち、重複の候補がある支払いの件数。 */
    val duplicateCount: Int = 0,
    val error: String? = null,
    val backup: BackupUiState = BackupUiState(),
)

private data class Snapshot(
    val month: String,
    val total: Long,
    val shares: List<CategoryShare>,
    val days: List<DayGroup>,
    val chartLabel: String,
    val chartBars: List<ChartBar>,
    val chartLegend: List<CategoryShare>,
    val draftCount: Int,
    val warnCount: Int,
    val draftReceiptIds: Set<Long>,
    val warnReceiptIds: Set<Long>,
    val imageReceiptIds: Set<Long>,
    val duplicateReceiptIds: Set<Long>,
    val duplicateCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val receipts: ReceiptRepository,
    private val splits: SplitRepository,
    private val backupManager: BackupManager,
    private val db: AppDatabase,
    private val importer: ReceiptImportCoordinator,
) : ViewModel() {

    /** 表示の基準日。月ヘッダーと積み上げグラフはこの日付から決まる。 */
    private val focus = MutableStateFlow(LocalDate.now())
    private val level = MutableStateFlow(ChartLevel.YEAR)
    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    /** レシートの取り込みの状態(アプリで1つ。支払い一覧と共有)。 */
    val imports: StateFlow<ReceiptListUiState> = importer.state

    /** 取り込みの結果のうち、まだ下書きの支払いの id(「開く ›」を「確認する ›」にする)。 */
    val importDraftIds: StateFlow<Set<Long>> = combine(imports, receipts.observeReceipts()) { st, list ->
        draftReceiptIds(st.imports.map { it.receiptId }, list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun newImportCameraPath(): String = importer.newCameraPath()

    fun importCameraUri(relativePath: String): Uri = importer.cameraUri(relativePath)

    fun discardImportCapture(relativePath: String) = importer.discardCapture(relativePath)

    fun onImportCaptured(relativePath: String) = importer.onCaptured(relativePath)

    fun onImportPhotosPicked(uris: List<Uri>) = importer.onPicked(uris)

    fun showImportError(message: String) = importer.showError(message)

    fun dismissImportError() = importer.dismissError()

    fun retryImport(key: Long) = importer.retry(key)

    fun clearImports() = importer.clearFinished()

    fun toggleImportAddAnyway(key: Long) = importer.toggleAddAnyway(key)

    fun resolveImportDuplicates(addChecked: Boolean) = importer.resolveDuplicates(addChecked)

    fun setImportAdjust(key: Long, adjust: ReceiptAdjust) = importer.setAdjust(key, adjust)

    fun analyzeImportAdjusted() = importer.analyzeAdjusted()

    fun analyzeImportWithoutAdjust() = importer.analyzeWithoutAdjust()

    fun cancelImportAdjust() = importer.cancelAdjust()

    /**
     * 取り込んだレシートから作られた支出(まとまり)の id。支出が無ければ null(その場合は支払いの詳細を開く)。
     */
    suspend fun paymentGroupIdOfReceipt(receiptId: Long): Long? =
        repository.groupsOfReceipt(receiptId).firstOrNull()?.id

    init {
        viewModelScope.launch {
            combine(focus, level) { f, l -> f to l }.flatMapLatest { (f, l) ->
                val ym = YearMonth.from(f).toString()
                _uiState.update { it.copy(month = ym, loading = true, chartLevel = l) }
                // 月の集計と積み上げグラフの両方を含む範囲だけを読む
                val (monthFrom, monthTo) = LedgerCalc.monthRange(ym)
                val (chartStart, chartEnd) = LedgerChart.containerRange(l, f)
                val ms = LocalDate.parse(monthFrom)
                val me = LocalDate.parse(monthTo)
                val from = (if (ms.isBefore(chartStart)) ms else chartStart).toString()
                val to = (if (me.isAfter(chartEnd)) me else chartEnd).toString()
                // 下書きの印と割り勘の警告も、家計簿の行と同じ期間で読む
                combine(
                    repository.observeLedgerRows(from, to),
                    repository.observeGroupsBetween(from, to),
                    combine(receipts.observeReceipts(), receipts.observeNotDuplicates()) { list, notDup -> list to notDup },
                    splits.observeSheets(),
                    db.expenseDao().observeReceiptIdsWithRows(),
                ) { all, groups, (receiptList, notDup), sheets, rowReceiptIds ->
                    // 割り勘で自分を外した支払い(自分の分が0円で行が無い)は、0円の行として一覧に出す
                    val excluded = LedgerLogic.excludedReceipts(
                        receipts = receiptList,
                        splitReceiptIds = sheets.mapTo(HashSet()) { it.receiptId },
                        rowReceiptIds = rowReceiptIds.toHashSet(),
                        from = from,
                        to = to,
                    )
                    snapshot(
                        f, l, all, groups,
                        draftIds = receiptList.filter { it.status == ReceiptStatus.DRAFT }.mapTo(HashSet()) { it.id },
                        warnIds = sheets.filter { it.warnings.isNotBlank() }.mapTo(HashSet()) { it.receiptId },
                        imageIds = receiptList.filter { it.imagePath.isNotEmpty() }.mapTo(HashSet()) { it.id },
                        duplicateIds = ReceiptDedup.laterDuplicates(receiptList, ReceiptDedup.notDuplicateSet(notDup)).keys,
                        excluded = excluded,
                    )
                }
                    .catch { e ->
                        _uiState.update { it.copy(loading = false, error = "家計簿の読み込みに失敗しました: ${e.message}") }
                    }
            }.collect { s ->
                _uiState.update {
                    it.copy(
                        month = s.month,
                        loading = false,
                        total = s.total,
                        shares = s.shares,
                        days = s.days,
                        chartLabel = s.chartLabel,
                        chartBars = s.chartBars,
                        chartLegend = s.chartLegend,
                        draftCount = s.draftCount,
                        warnCount = s.warnCount,
                        draftReceiptIds = s.draftReceiptIds,
                        warnReceiptIds = s.warnReceiptIds,
                        imageReceiptIds = s.imageReceiptIds,
                        duplicateReceiptIds = s.duplicateReceiptIds,
                        duplicateCount = s.duplicateCount,
                    )
                }
            }
        }
    }

    private fun snapshot(
        f: LocalDate,
        l: ChartLevel,
        all: List<Expense>,
        groups: List<ExpenseGroup>,
        draftIds: Set<Long>,
        warnIds: Set<Long>,
        imageIds: Set<Long>,
        duplicateIds: Set<Long>,
        excluded: List<LedgerEntry.Excluded>,
    ): Snapshot {
        val ym = YearMonth.from(f).toString()
        // 集計は自分の分だけ。0円の行(自分が外れた支払い)は金額もカテゴリ内訳も0なので、合計には足さない
        val entries = LedgerLogic.buildEntries(all, groups, excluded)
        val current = entries.filter { it.date.startsWith(ym) }
        return Snapshot(
            month = ym,
            total = current.sumOf { it.amount },
            shares = LedgerLogic.categoryShares(LedgerLogic.totalsByCategory(current)),
            days = LedgerLogic.groupByDate(current),
            chartLabel = LedgerChart.containerLabel(l, f),
            chartBars = LedgerChart.bars(l, f, entries),
            chartLegend = LedgerChart.legend(entries, l, f),
            // 下書き・要確認は支払い(レシート)単位のフラグ。支出の行数ではなく、今月の行に出てくる支払いの数で数える
            draftCount = current.mapNotNull { it.receiptId }.distinct().count { it in draftIds },
            warnCount = current.mapNotNull { it.receiptId }.distinct().count { it in warnIds },
            draftReceiptIds = draftIds,
            warnReceiptIds = warnIds,
            imageReceiptIds = imageIds,
            duplicateReceiptIds = duplicateIds,
            duplicateCount = current.mapNotNull { it.receiptId }.distinct().count { it in duplicateIds },
        )
    }

    /** ヘッダーの ◀▶。月を変えると積み上げグラフの基準もその月の1日になる。 */
    fun previousMonth() { focus.value = YearMonth.from(focus.value).minusMonths(1).atDay(1) }
    fun nextMonth() { focus.value = YearMonth.from(focus.value).plusMonths(1).atDay(1) }

    /** 積み上げグラフの階層ボタン。基準日は変えず、その基準日を含む期間の棒に切り替える。 */
    fun selectChartLevel(newLevel: ChartLevel) { level.value = newLevel }

    /** グラフの ◀▶。階層の単位で1つ前後へ移動する。 */
    fun chartPrevious() { focus.value = LedgerChart.shift(level.value, focus.value, -1) }
    fun chartNext() { focus.value = LedgerChart.shift(level.value, focus.value, 1) }

    /** 棒をタップ: 1段下の階層で、その棒の期間を表示する。 */
    fun onChartBarTapped(bar: ChartBar) {
        val child = level.value.child ?: return
        if (!bar.drillable) return
        level.value = child
        focus.value = bar.start
    }
    fun dismissError() { _uiState.update { it.copy(error = null) } }

    /** 支出が紐づくレシートの日時 "yyyy-MM-dd HH:mm"。紐づかなければ null。 */
    suspend fun linkedReceiptDateTime(expense: Expense): String? = repository.linkedReceiptDateTime(expense)

    /**
     * 手入力の支払いを保存する(レシートの表と品目)。家計簿の支出は、この保存のなかで支払いから作られる。
     * onDone には保存した支払いの id と、エラーメッセージ(成功なら null)を渡す。
     */
    fun addManualPayment(
        receipt: Receipt,
        items: List<ReceiptItem>,
        headcount: Int?,
        onDone: (Long?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                // 保存と人数の書き込みを1つのトランザクションにする(人数だけ残る状態を作らない)
                val id = db.withTransaction {
                    // 確定(合計か品目がある)は詳細画面の確定と同じ saveAndConfirm を通す。下書きは save のまま
                    val savedId = if (receipt.status == ReceiptStatus.CONFIRMED) {
                        receipts.saveAndConfirm(receipt, items)
                    } else {
                        receipts.save(receipt, items)
                    }
                    if (headcount != null) db.receiptDao().setHeadcount(savedId, headcount)
                    savedId
                }
                onDone(id, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onDone(null, "保存に失敗しました: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 支払いグループを品目ごと消す。 */
    fun deleteGroup(group: ExpenseGroup) {
        viewModelScope.launch {
            try {
                repository.deleteGroup(group)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "削除に失敗しました: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                repository.delete(expense)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "削除に失敗しました: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    // ------------------------------------------------------------ backup

    private fun setProgress(p: BackupProgress?) {
        _uiState.update { it.copy(backup = it.backup.copy(progress = p)) }
    }

    private fun showResult(title: String, message: String) {
        _uiState.update {
            it.copy(backup = it.backup.copy(progress = null, resultTitle = title, resultMessage = message))
        }
    }

    private fun describe(e: Throwable): String =
        if (e is BackupException) (e.message ?: "失敗しました") else "失敗しました: ${e.message ?: e.javaClass.simpleName}"

    fun exportTo(uri: Uri) {
        if (_uiState.value.backup.progress != null) return
        setProgress(BackupProgress("準備しています"))
        viewModelScope.launch {
            try {
                val r = backupManager.export(uri) { setProgress(it) }
                showResult(
                    "エクスポート完了",
                    "日記 ${r.diaryEntries}件、支払い ${r.receipts}件、支出 ${r.expenses}件、画像 ${r.files}枚を書き出しました。",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showResult("エクスポート失敗", describe(e))
            }
        }
    }

    fun onImportPicked(uri: Uri) {
        _uiState.update { it.copy(backup = it.backup.copy(pendingImport = uri)) }
    }

    fun cancelImport() {
        _uiState.update { it.copy(backup = it.backup.copy(pendingImport = null)) }
    }

    fun confirmImport(mode: ImportMode) {
        val uri = _uiState.value.backup.pendingImport ?: return
        if (_uiState.value.backup.progress != null) return
        _uiState.update { it.copy(backup = it.backup.copy(pendingImport = null, progress = BackupProgress("準備しています"))) }
        viewModelScope.launch {
            try {
                val r = backupManager.importZip(uri, mode) { setProgress(it) }
                showResult(
                    "インポート完了",
                    "日記 ${r.diaryEntries}件、写真 ${r.diaryPhotos}件、支払い ${r.receipts}件、支出 ${r.expenses}件を取り込みました。" +
                        "重複などでスキップ ${r.skipped}件、画像 ${r.files}枚。",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val msg = describe(e)
                val note = if (msg.startsWith("データは取り込みました")) "" else "\n(データは変更されていません)"
                showResult("インポート失敗", msg + note)
            }
        }
    }

    fun dismissBackupResult() {
        _uiState.update { it.copy(backup = it.backup.copy(resultTitle = null, resultMessage = null)) }
    }
}
