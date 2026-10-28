package com.amgm.personallog.ui.ledger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.domain.PaymentKind
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.ProportionBand
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable
import com.amgm.personallog.ui.receipt.ImportPanel
import com.amgm.personallog.ui.receipt.RECEIPT_PICK_MAX
import com.amgm.personallog.ui.receipt.ReceiptImportDialogs
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 家計簿タブのトップ画面。 */
@Composable
fun LedgerScreen(
    modifier: Modifier = Modifier,
    onOpenPayment: (PaymentKind, Long) -> Unit = { _, _ -> },
    /** 割り勘で自分が外れた支払い(0円の行)の詳細を、支払い(レシート)の id で開く。 */
    onOpenExcludedPayment: (Long) -> Unit = {},
    /** 下書きの支払いを、登録の手順(支払い → 自分の支出)で開く。支払いの id。 */
    onRegisterPayment: (Long) -> Unit = {},
    /** 手入力で保存した支払いの詳細(レシートの詳細)を開く。取り込みの「開く ›」で支出が無いときも使う。 */
    onOpenReceipt: (Long) -> Unit = {},
    /** 支払い一覧(支払い画面)を開く。 */
    onOpenReceiptList: () -> Unit = {},
    /** 重複の一覧を開く(月の合計の「重複を確認する ›」から)。 */
    onOpenDuplicates: () -> Unit = {},
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val imports by viewModel.imports.collectAsStateWithLifecycle()
    val importDrafts by viewModel.importDraftIds.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()
    // 家計簿の支出は、支払い(レシートの表)を保存したときに作られる。支出だけを単独で追加する入口は無い
    // 「＋ 記録」で開く3択シート(レシートを撮る / 写真から選ぶ / 手動で登録)
    var creatingPayment by remember { mutableStateOf(false) }
    // 撮影中の保存先(画面回転・プロセス再生成後も結果を受け取れるよう保存する)
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCapturePath
        pendingCapturePath = null
        if (path != null) {
            if (ok) viewModel.onImportCaptured(path) else viewModel.discardImportCapture(path)
        }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(RECEIPT_PICK_MAX)) { uris ->
        if (uris.isNotEmpty()) viewModel.onImportPhotosPicked(uris)
    }
    // 「レシートを撮る」と同じ処理(カメラを開く。起動できなければ案内を出す)
    val startCamera: () -> Unit = {
        try {
            val path = viewModel.newImportCameraPath()
            pendingCapturePath = path
            takePicture.launch(viewModel.importCameraUri(path))
        } catch (e: Exception) {
            pendingCapturePath = null
            viewModel.showImportError("カメラを起動できませんでした。「写真から選ぶ」をお使いください。")
        }
    }
    // 取り込みの「開く ›」: その支払いの支出(まとまり)を開く。支出が無ければ支払いの詳細
    // 下書きは登録の手順で開く(「確認する ›」)。確定済みは支出の画面(「開く ›」)
    val openImported: (Long) -> Unit = { receiptId ->
        if (receiptId in importDrafts) {
            onRegisterPayment(receiptId)
        } else {
            scope.launch {
                val groupId = viewModel.paymentGroupIdOfReceipt(receiptId)
                if (groupId != null) onOpenPayment(PaymentKind.GROUP, groupId) else onOpenReceipt(receiptId)
            }
        }
    }
    // 「手動で登録」で開く手入力の支払いシート
    var manualPaymentOpen by remember { mutableStateOf(false) }
    val expandedGroups = remember { mutableStateMapOf<Long, Boolean>() }
    var deletingGroup by remember { mutableStateOf<ExpenseGroup?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImportPicked(uri)
    }

    PaperScreen(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.edge,
                    end = Space.edge,
                    bottom = Space.xl + Space.xl,
                ),
            ) {
                item(key = "header") {
                    MonthHeader(
                        month = state.month,
                        onPrev = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                        onMenu = { menuOpen = true },
                    )
                }
                // 取り込みの進捗は合計の上。取り込みが無ければ出さない
                if (imports.imports.isNotEmpty() || imports.error != null) {
                    item(key = "imports") {
                        ImportPanel(
                            imports = imports.imports,
                            error = imports.error,
                            onDismissError = viewModel::dismissImportError,
                            onRetry = viewModel::retryImport,
                            onOpen = openImported,
                            draftReceiptIds = importDrafts,
                            onClear = viewModel::clearImports,
                        )
                    }
                }
                state.error?.let { err ->
                    item(key = "error") {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(err, style = AppTheme.type.caption, color = c.danger, modifier = Modifier.weight(1f))
                            TextAction("閉じる", onClick = viewModel::dismissError, color = c.danger)
                        }
                        Hairline(color = c.danger)
                    }
                }
                item(key = "total") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = Space.m, bottom = Space.l),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Label("支出")
                        Text(
                            LedgerLogic.formatYen(state.total),
                            style = AppTheme.type.numeralXL,
                            color = c.ink,
                        )
                        // 下書きや割り勘の警告があると、合計にそのまま入っているので知らせる
                        if (state.draftCount > 0) {
                            Text(
                                "下書きの支払い ${state.draftCount}件を含む",
                                style = AppTheme.type.caption,
                                color = c.accent,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                        }
                        if (state.warnCount > 0) {
                            Text(
                                "要確認の支払い ${state.warnCount}件(負担割りに警告があります)",
                                style = AppTheme.type.caption,
                                color = c.danger,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                        }
                        if (state.duplicateCount > 0) {
                            Text(
                                "重複の可能性がある支払い ${state.duplicateCount}件を含む(「重複?」の行)",
                                style = AppTheme.type.caption,
                                color = c.danger,
                                modifier = Modifier.padding(top = Space.xs).tappable(onOpenDuplicates),
                            )
                            TextAction("重複を確認する  ›", onClick = onOpenDuplicates, color = c.accent)
                        }
                    }
                }
                item(key = "cat") {
                    Column(Modifier.fillMaxWidth()) {
                        Label("カテゴリ別")
                        Spacer(Modifier.height(Space.s))
                        if (state.shares.isEmpty()) {
                            Text(
                                if (state.loading) "読み込み中…" else "この月の支出はありません",
                                style = AppTheme.type.body,
                                color = c.inkSoft,
                                modifier = Modifier.padding(vertical = Space.s),
                            )
                        } else {
                            ProportionBand(
                                parts = state.shares.map {
                                    it.total.toFloat() to categoryColor(it.category, c.series, c.inkFaint)
                                },
                            )
                            Spacer(Modifier.height(Space.m))
                            CategoryLegend(state.shares)
                            Spacer(Modifier.height(Space.s))
                            Text(ALLOCATION_NOTE, style = AppTheme.type.caption, color = c.inkFaint)
                        }
                    }
                }
                item(key = "chart") {
                    Column(Modifier.fillMaxWidth().padding(top = Space.l)) {
                        Label("内訳(カテゴリ別の積み上げ)")
                        Spacer(Modifier.height(Space.s))
                        StackedChart(
                            level = state.chartLevel,
                            label = state.chartLabel,
                            bars = state.chartBars,
                            legend = state.chartLegend,
                            onLevel = viewModel::selectChartLevel,
                            onPrev = viewModel::chartPrevious,
                            onNext = viewModel::chartNext,
                            onBarTap = viewModel::onChartBarTapped,
                        )
                    }
                }
                item(key = "list-title") {
                    Column(Modifier.fillMaxWidth().padding(top = Space.l, bottom = Space.s)) {
                        Label("支出一覧")
                        if (state.days.isEmpty()) {
                            Text(
                                "右下の「＋ 記録」から支払いを追加できます",
                                style = AppTheme.type.body,
                                color = c.inkSoft,
                                modifier = Modifier.padding(vertical = Space.s),
                            )
                        }
                    }
                }
                items(state.days, key = { "day-" + it.date }) { day ->
                    DayBlock(
                        day = day,
                        expandedGroups = expandedGroups,
                        draftReceiptIds = state.draftReceiptIds,
                        warnReceiptIds = state.warnReceiptIds,
                        imageReceiptIds = state.imageReceiptIds,
                        duplicateReceiptIds = state.duplicateReceiptIds,
                        unreadReceiptIds = state.unreadReceiptIds,
                        storeNames = state.storeNames,
                        onToggleGroup = { id -> expandedGroups[id] = expandedGroups[id] != true },
                        onOpen = { entry ->
                            // 下書きの行は、登録の手順(支払いの画面から)で開く。確定済みの行は支出の画面
                            val draftId = entry.receiptId?.takeIf { it in state.draftReceiptIds }
                            if (draftId != null) {
                                onRegisterPayment(draftId)
                            } else {
                                when (entry) {
                                    is LedgerEntry.Group -> onOpenPayment(PaymentKind.GROUP, entry.group.id)
                                    is LedgerEntry.Excluded -> onOpenExcludedPayment(entry.receipt.id)
                                }
                            }
                        },
                        onDeleteGroup = { deletingGroup = it.group },
                    )
                }
            }

            InkButton(
                text = "＋ 記録",
                onClick = { creatingPayment = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.m),
            )
        }
    }

    if (menuOpen) {
        InkSheet(onDismiss = { menuOpen = false }) {
            Text(
                "支払い一覧",
                style = AppTheme.type.body,
                color = c.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .tappable {
                        menuOpen = false
                        onOpenReceiptList()
                    }
                    .padding(vertical = Space.m),
            )
            Hairline()
            Spacer(Modifier.height(Space.l))
            Label("バックアップ")
            Spacer(Modifier.height(Space.s))
            Text(
                "エクスポート(ZIPに保存)",
                style = AppTheme.type.body,
                color = c.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .tappable {
                        menuOpen = false
                        exportLauncher.launch("personal-log-${LocalDate.now()}.zip")
                    }
                    .padding(vertical = Space.m),
            )
            Hairline()
            Text(
                "インポート(ZIPから復元)",
                style = AppTheme.type.body,
                color = c.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .tappable {
                        menuOpen = false
                        importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    }
                    .padding(vertical = Space.m),
            )
        }
    }

    if (creatingPayment) {
        InkSheet(onDismiss = { creatingPayment = false }) {
            RecordMethodContent(
                onCamera = {
                    creatingPayment = false
                    startCamera()
                },
                onPickPhotos = {
                    creatingPayment = false
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onManual = {
                    creatingPayment = false
                    manualPaymentOpen = true
                },
            )
        }
    }

    // 取り込みの確認(同じ画像の確認・向きと範囲の補正)。支払い一覧と同じ状態を出す
    ReceiptImportDialogs(
        state = imports,
        onToggleAddAnyway = viewModel::toggleImportAddAnyway,
        onResolveDuplicates = viewModel::resolveImportDuplicates,
        onSetAdjust = viewModel::setImportAdjust,
        onAnalyze = viewModel::analyzeImportAdjusted,
        onAnalyzeWithout = viewModel::analyzeImportWithoutAdjust,
        onCancelAdjust = viewModel::cancelImportAdjust,
    )

    if (manualPaymentOpen) {
        val initialDate = LedgerLogic.defaultDate(state.month, LocalDate.now())
        ManualPaymentSheet(
            initialDate = initialDate,
            initialTime = LedgerLogic.defaultTime(initialDate, LocalDate.now(), LocalTime.now()),
            onDismiss = { manualPaymentOpen = false },
            onSave = { receipt, items, headcount, done ->
                viewModel.addManualPayment(receipt, items, headcount) { id, err ->
                    done(err)
                    // 保存できたら、その支払いの詳細を開く(レシート一覧の手入力と同じ遷移)
                    if (id != null) onOpenReceipt(id)
                }
            },
        )
    }
    deletingGroup?.let { target ->
        ConfirmSheet(
            title = "支出を削除",
            message = "「${target.memo.ifBlank { target.category }}」の支出を削除します。元の支払い(店名・日時・合計)とレシート画像も一緒に削除されます。",
            confirmText = "削除",
            onDismiss = { deletingGroup = null },
            onConfirm = {
                viewModel.deletePayment(target)
                deletingGroup = null
            },
        )
    }

    BackupSheets(
        state = state.backup,
        onPickMode = viewModel::confirmImport,
        onCancelImport = viewModel::cancelImport,
        onDismissResult = viewModel::dismissBackupResult,
    )
}

/** 月を巨大な数字で見せる上部。左右に前月/翌月、右上にメニュー。 */
@Composable
private fun MonthHeader(
    month: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = AppTheme.colors
    val ym = try {
        YearMonth.parse(month)
    } catch (e: Exception) {
        YearMonth.now()
    }
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextAction("⋯", onClick = onMenu, color = c.inkSoft)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextAction("‹", onClick = onPrev, color = c.ink)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Label("${ym.year}年")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(ym.monthValue.toString(), style = AppTheme.type.display, color = c.ink, textAlign = TextAlign.Center)
                    Text(
                        "月",
                        style = AppTheme.type.title,
                        color = c.inkSoft,
                        modifier = Modifier.padding(start = Space.xs, bottom = Space.s),
                    )
                }
            }
            TextAction("›", onClick = onNext, color = c.ink)
        }
    }
}

/**
 * 「＋ 記録」の3択シートの中身(記録の方法)。各項目は見出しと説明を1つの押せる行にし、説明をタップしても動く。
 */
@Composable
internal fun ColumnScope.RecordMethodContent(
    onCamera: () -> Unit,
    onPickPhotos: () -> Unit,
    onManual: () -> Unit,
) {
    Label("記録の方法")
    Spacer(Modifier.height(Space.s))
    RecordMethodRow("レシートを撮る", "カメラで撮って読み取ります。", onCamera)
    Hairline()
    RecordMethodRow("撮影済みの写真から選ぶ", "最大${RECEIPT_PICK_MAX}枚。読み取った下書きを作ります。", onPickPhotos)
    Hairline()
    RecordMethodRow("手動で登録", "日付や品目を自分で入力して作ります。", onManual)
}

/** 記録の方法の1行。見出しと説明の全体が押せる。 */
@Composable
private fun RecordMethodRow(title: String, caption: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .tappable(onClick)
            .padding(vertical = Space.m),
    ) {
        Text(title, style = AppTheme.type.body, color = c.ink)
        Text(caption, style = AppTheme.type.caption, color = c.inkSoft)
    }
}
