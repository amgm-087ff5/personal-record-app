package com.amgm.personallog.ui.receipt

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.domain.TaxCalc
import com.amgm.personallog.receipt.ReceiptAdjust
import com.amgm.personallog.receipt.ReceiptImageCodec
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkButtonKind
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.LeaderRow
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Pill
import com.amgm.personallog.ui.design.Pills
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.StampTone
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 税率の即選択。よく使う 8%(軽減)と 10%(標準)。それ以外は数字で直接入力する。 */
private val PRESET_TAX_RATES = listOf("8", "10")

/** 原本画像の高さ。 */
private val IMAGE_HEIGHT = 220.dp

/** 開いている編集シートの種類(rememberSaveable に文字列で持つ)。 */
private const val SHEET_NONE = ""
private const val SHEET_SHOP = "shop"
private const val SHEET_TOTAL = "total"
private const val SHEET_OWN = "own"
private const val SHEET_DELETE = "delete"
private const val SHEET_RESCAN = "rescan"
private const val SHEET_USE_ITEMS_SUM = "useItemsSum"

/** 最上部の合計。大きく(36sp前後・太字)。 */
@Composable
private fun dashboardTotalStyle(): TextStyle = AppTheme.type.numeralXL.copy(
    fontSize = 36.sp,
    lineHeight = 42.sp,
    fontWeight = FontWeight.Bold,
)

/**
 * レシートの確認・修正画面。
 * 合計のダッシュボード → 原本画像 → 紙面(店名・合計) → 品目の行 → 支払いまとめ → 操作。品目の編集は行をタップして開くシートで行う。
 */
@Composable
fun ReceiptDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceiptDetailViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var sheet by rememberSaveable { mutableStateOf(SHEET_NONE) }
    var editGroupKey by rememberSaveable { mutableStateOf(0L) }
    var editUnitKey by rememberSaveable { mutableStateOf(0L) }
    var viewerOpen by rememberSaveable { mutableStateOf(false) }
    var adjustOpen by rememberSaveable { mutableStateOf(false) }
    var pendingRescanAdjust by rememberSaveable(stateSaver = ReceiptAdjustOrNullSaver) { mutableStateOf<ReceiptAdjust?>(null) }
    val groups = remember(s.items) { groupItemForms(s.items) }

    LaunchedEffect(s.finished) { if (s.finished) onBack() }

    PaperScreen(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize().imePadding()) {
            val hasImage = !s.loading && !s.notFound && s.imagePath.isNotBlank()
            val showDashboard = !s.loading && !s.notFound
            if (showDashboard) {
                item(key = "dashboard") { TotalDashboard(s) }
            }
            if (hasImage) {
                item(key = "image") {
                    ReceiptImage(imagePath = s.imagePath, adjust = s.adjust, onClick = { viewerOpen = true })
                }
            }
            if (hasImage) {
                item(key = "adjustLink") {
                    TextAction(
                        "向き・範囲を調整",
                        onClick = { adjustOpen = true },
                        enabled = !s.busy,
                        modifier = Modifier.padding(horizontal = Space.s),
                    )
                }
            }
            // 戻る・読み取り直すは画像の下の細い行
            item(key = "nav") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (hasImage || showDashboard) Modifier else Modifier.statusBarsPadding())
                        .padding(horizontal = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextAction("戻る", onClick = onBack, color = c.inkSoft)
                    Box(modifier = Modifier.weight(1f))
                    if (hasImage) {
                        TextAction("読み取り直す", onClick = { sheet = SHEET_RESCAN }, enabled = !s.busy)
                    }
                }
            }

            if (s.loading) {
                item(key = "loading") {
                    Label("読み込み中…", modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.m))
                }
            } else if (s.notFound) {
                item(key = "notFound") {
                    Text(
                        "レシートが見つかりません。",
                        style = AppTheme.type.body,
                        color = c.inkSoft,
                        modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.m),
                    )
                }
            } else {
                if (s.scanning) {
                    item(key = "scanning") {
                        Column(modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.s)) {
                            Label("読み取り中", color = c.accent)
                            Text("読み取り中です…(最大1〜2分)", style = AppTheme.type.caption, color = c.inkSoft)
                        }
                    }
                }

                s.error?.let { msg ->
                    item(key = "error") {
                        Column(modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.s)) {
                            Text(msg, style = AppTheme.type.body, color = c.danger)
                            TextAction("閉じる", onClick = viewModel::dismissError, color = c.danger)
                        }
                    }
                }
                s.info?.let { msg ->
                    item(key = "info") {
                        Column(modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.s)) {
                            Text(msg, style = AppTheme.type.body, color = c.moss)
                            TextAction("閉じる", onClick = viewModel::dismissInfo, color = c.inkSoft)
                        }
                    }
                }

                // 紙面: 店名・日付・状態、合計
                item(key = "paper") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.paperRaised)
                            .padding(horizontal = Space.edge)
                            .padding(top = Space.m),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().tappable { sheet = SHEET_SHOP }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Label(
                                    listOf(s.date, s.time).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "日付未入力" },
                                    modifier = Modifier.weight(1f),
                                )
                                if (s.confirmed) {
                                    Stamp("確定済み", tone = StampTone.Moss)
                                } else {
                                    Stamp("未確定(下書き)", tone = StampTone.Accent)
                                }
                            }
                            Text(
                                s.store.ifBlank { "(店名未入力)" },
                                style = AppTheme.type.headline,
                                color = c.ink,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                            if (s.isAdvance) {
                                Text("立て替えの支払い", style = AppTheme.type.caption, color = c.inkSoft)
                            }
                            Label("タップして店名・日付を編集", modifier = Modifier.padding(top = Space.xs))
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.m)
                                .tappable { sheet = SHEET_TOTAL },
                        ) {
                            Label("合計")
                            val total = s.total.toLongOrNull()
                            Text(
                                if (total != null) formatYen(total) else "未入力",
                                style = AppTheme.type.numeralL,
                                color = if (total != null) c.ink else c.danger,
                            )
                        }
                        // 合計欄のすぐ下: 品目の計算合計(税込の支払合計)を常に表示
                        Text(
                            "品目の計算合計 ${formatYen(s.itemsSum)}",
                            style = AppTheme.type.body,
                            color = c.inkSoft,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                        if (s.canUseItemsSum) {
                            TextAction(
                                "${formatYen(s.itemsSum)} を合計に入れる",
                                onClick = {
                                    // 合計欄に値があるときは印字の値を消すので、確認を挟む
                                    if (s.useItemsSumNeedsConfirm) sheet = SHEET_USE_ITEMS_SUM else viewModel.useItemsSumAsTotal()
                                },
                            )
                        }
                        if (s.confirmed) {
                            Text(
                                "確定済み(編集して「再確定」すると家計簿も更新されます)",
                                style = AppTheme.type.caption,
                                color = c.inkSoft,
                                modifier = Modifier.padding(top = Space.s),
                            )
                        }
                        if (s.possibleDuplicates.isNotEmpty()) {
                            Column(modifier = Modifier.padding(top = Space.m)) {
                                Stamp("重複の可能性", tone = StampTone.Danger)
                                Text(
                                    "時刻が近く(5分以内)合計が同じレシートが既にあります。時刻が無いときは同じ日付・同じ合計です。",
                                    style = AppTheme.type.caption,
                                    color = c.danger,
                                    modifier = Modifier.padding(top = Space.xs),
                                )
                                s.possibleDuplicates.forEach { other ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            other.summary,
                                            style = AppTheme.type.caption,
                                            color = c.danger,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextAction("重複ではない", onClick = { viewModel.markNotDuplicate(other.id) }, color = c.inkSoft)
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.height(Space.m))
                        Hairline(dashed = true)
                    }
                }

                // 品目
                item(key = "itemsTitle") {
                    Label("品目", modifier = Modifier.padding(start = Space.edge, end = Space.edge, top = Space.l, bottom = Space.s))
                }
                items(groups, key = { it.head.key }) { group ->
                    ItemGroupRows(
                        group = group,
                        isAdvance = s.isAdvance,
                        onEditGroup = { editGroupKey = group.head.key },
                        onEditUnit = { editUnitKey = it },
                    )
                }
                item(key = "addItem") {
                    TextAction(
                        "＋ 品目を追加",
                        onClick = {
                            viewModel.addItem()
                        },
                        modifier = Modifier.padding(horizontal = Space.s),
                    )
                }

                // 支払いまとめ
                item(key = "summary") {
                    Column(modifier = Modifier.padding(horizontal = Space.edge).padding(top = Space.l)) {
                        Hairline()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Space.m),
                            horizontalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            SummaryColumn("印字合計", s.total.toLongOrNull()?.let { formatYen(it) } ?: "未入力", Modifier.weight(1f))
                            SummaryColumn("税込合計", formatYen(s.itemsSum), Modifier.weight(1f))
                            SummaryColumn(
                                "自分の負担",
                                formatYen(s.ownTotal),
                                Modifier.weight(1f).then(if (s.isAdvance) Modifier.tappable { sheet = SHEET_OWN } else Modifier),
                            )
                        }
                        if (s.isAdvance) {
                            Text(
                                "貰う分(他の人の分): ${formatYen(s.theirTotal)}。「自分の負担」をタップして金額を直接入力できます。",
                                style = AppTheme.type.caption,
                                color = c.inkSoft,
                            )
                        }
                        Text(
                            "小数点以下は合計で切り捨てています。",
                            style = AppTheme.type.caption,
                            color = c.inkFaint,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }

                if (s.rawJson.isNotBlank() && s.items.isEmpty()) {
                    item(key = "raw") {
                        Column(modifier = Modifier.padding(horizontal = Space.edge).padding(top = Space.l)) {
                            Label("Claudeの応答原文(解釈できなかった場合の参考)")
                            SelectionContainer {
                                Text(s.rawJson, style = AppTheme.type.caption, color = c.inkSoft)
                            }
                        }
                    }
                }

                // 操作は最下部に離して置く
                item(key = "actions") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.edge)
                            .padding(top = Space.xl, bottom = Space.xl),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                            if (!s.confirmed) {
                                InkButton(
                                    "下書き保存",
                                    onClick = viewModel::saveDraft,
                                    kind = InkButtonKind.Outline,
                                    enabled = !s.busy,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            InkButton(
                                if (s.confirmed) "再確定" else "確定",
                                onClick = viewModel::confirm,
                                enabled = !s.busy,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TextAction(
                            "このレシートを削除",
                            onClick = { sheet = SHEET_DELETE },
                            color = c.danger,
                            enabled = !s.busy,
                            modifier = Modifier.padding(top = Space.l),
                        )
                    }
                }
            }
        }
    }

    if (viewerOpen) {
        ReceiptImageViewer(relativePath = s.imagePath, onDismiss = { viewerOpen = false }, adjust = s.adjust)
    }

    // 向き・範囲の調整(画像1枚の編集)
    if (adjustOpen) {
        ReceiptAdjustSingleDialog(
            imagePath = s.imagePath,
            initial = s.adjust,
            busy = s.busy,
            onSave = { adjustOpen = false; viewModel.saveAdjust(it) },
            // 読み取り直しは入力内容が置き換わるので、既存の確認を出してから実行する
            onSaveAndRescan = { adjustOpen = false; pendingRescanAdjust = it },
            onDismiss = { adjustOpen = false },
        )
    }
    pendingRescanAdjust?.let { a ->
        InkSheet(onDismiss = { pendingRescanAdjust = null }) {
            SheetTitle("保存して読み取り直す")
            Text(
                "向き・範囲を保存し、その補正で読み取り直します。現在の入力内容は、新しい読み取り結果で置き換わります。画像をClaudeに再送信します。",
                style = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.padding(bottom = Space.m),
            )
            TextAction("保存して読み取り直す", onClick = { pendingRescanAdjust = null; viewModel.saveAdjustAndRescan(a) })
            TextAction("向き・範囲だけ保存", onClick = { pendingRescanAdjust = null; viewModel.saveAdjust(a) }, color = AppTheme.colors.inkSoft)
            TextAction("キャンセル", onClick = { pendingRescanAdjust = null }, color = AppTheme.colors.inkSoft)
        }
    }

    // 品目(まとまり)の編集シート
    if (editGroupKey != 0L) {
        val group = groups.firstOrNull { editGroupKey in it.keys }
        if (group == null) {
            LaunchedEffect(Unit) { editGroupKey = 0L }
        } else {
            GroupEditSheet(
                group = group,
                isAdvance = s.isAdvance,
                viewModel = viewModel,
                onEditUnit = { editUnitKey = it },
                onDismiss = { editGroupKey = 0L },
            )
        }
    }

    // 1個ごとの編集シート
    if (editUnitKey != 0L) {
        val unit = s.items.firstOrNull { it.key == editUnitKey }
        if (unit == null) {
            LaunchedEffect(Unit) { editUnitKey = 0L }
        } else {
            UnitEditSheet(
                unit = unit,
                number = groups.firstOrNull { editUnitKey in it.keys }?.units?.indexOfFirst { it.key == editUnitKey }?.plus(1),
                isAdvance = s.isAdvance,
                viewModel = viewModel,
                onDismiss = { editUnitKey = 0L },
            )
        }
    }

    when (sheet) {
        SHEET_SHOP -> InkSheet(onDismiss = { sheet = SHEET_NONE }, modifier = Modifier.imePadding().verticalScroll(rememberScrollState())) {
            SheetTitle("店名・日付")
            SheetField("店名", s.store, viewModel::setStore)
            SheetField("日付 yyyy-MM-dd", s.date, viewModel::setDate)
            SheetField("時刻 HH:mm", s.time, viewModel::setTime)
            SheetChoice("支払い") {
                Pill(
                    text = "自分が立て替えた支払い(他の人の分を含む)",
                    selected = s.isAdvance,
                    onClick = { viewModel.setAdvance(!s.isAdvance) },
                )
            }
            TextAction("閉じる", onClick = { sheet = SHEET_NONE })
        }
        SHEET_TOTAL -> InkSheet(onDismiss = { sheet = SHEET_NONE }, modifier = Modifier.imePadding().verticalScroll(rememberScrollState())) {
            SheetTitle("合計")
            SheetField("合計(円・レシートの印字)", s.total, viewModel::setTotal, numeric = true)
            // 紙面と同じ: 品目の計算合計と、1タップで入れるボタン
            Text(
                "品目の計算合計 ${formatYen(s.itemsSum)}",
                style = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.padding(bottom = Space.xs),
            )
            if (s.canUseItemsSum) {
                TextAction(
                    "${formatYen(s.itemsSum)} を合計に入れる",
                    onClick = {
                        if (s.useItemsSumNeedsConfirm) sheet = SHEET_USE_ITEMS_SUM
                        else { viewModel.useItemsSumAsTotal(); sheet = SHEET_NONE }
                    },
                )
            }
            TextAction("閉じる", onClick = { sheet = SHEET_NONE })
        }
        SHEET_OWN -> InkSheet(onDismiss = { sheet = SHEET_NONE }, modifier = Modifier.imePadding().verticalScroll(rememberScrollState())) {
            SheetTitle("自分が払う分")
            SheetField(
                "自分が払う分(円・税込)",
                s.ownAmount,
                viewModel::setOwnAmount,
                placeholder = "空欄は全額 ${formatYen(s.itemsSum)}",
                numeric = true,
            )
            Text(
                "品目の「自分の分に含める」を選ぶと、その税込合計がここに入ります。手で直接入力も可。",
                style = AppTheme.type.caption,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.padding(bottom = Space.m),
            )
            TextAction("閉じる", onClick = { sheet = SHEET_NONE })
        }
        SHEET_DELETE -> InkSheet(onDismiss = { sheet = SHEET_NONE }) {
            SheetTitle("レシートを削除")
            Text(
                "レシート画像も端末から削除されます。このレシートから作った家計簿の行はどうしますか?",
                style = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.padding(bottom = Space.m),
            )
            TextAction(
                "家計簿の行も消す",
                onClick = { sheet = SHEET_NONE; viewModel.delete(alsoExpenses = true) },
                color = AppTheme.colors.danger,
            )
            TextAction(
                "家計簿の行は残す",
                onClick = { sheet = SHEET_NONE; viewModel.delete(alsoExpenses = false) },
                color = AppTheme.colors.danger,
            )
            TextAction("キャンセル", onClick = { sheet = SHEET_NONE }, color = AppTheme.colors.inkSoft)
        }
        SHEET_USE_ITEMS_SUM -> InkSheet(onDismiss = { sheet = SHEET_NONE }) {
            SheetTitle("合計を品目の計算合計に置き換えますか?")
            LeaderRow(
                label = "印字の合計(読み取った値)",
                value = s.printedTotal?.let { formatYen(it.toLong()) } ?: "なし",
            )
            LeaderRow(label = "いまの合計", value = s.total.toLongOrNull()?.let { formatYen(it) } ?: "未入力")
            LeaderRow(label = "品目の計算合計", value = formatYen(s.itemsSum))
            Text(
                "差は品目の読み違いかもしれません。置き換えると、印字の合計はこの画面から消えます(読み取り直すと戻せます)。",
                style = AppTheme.type.caption,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.padding(vertical = Space.m),
            )
            TextAction("入れる", onClick = { sheet = SHEET_NONE; viewModel.useItemsSumAsTotal() })
            TextAction("キャンセル", onClick = { sheet = SHEET_NONE }, color = AppTheme.colors.inkSoft)
        }
        SHEET_RESCAN -> InkSheet(onDismiss = { sheet = SHEET_NONE }) {
            SheetTitle("読み取り直す")
            Text(
                "現在の入力内容は、新しい読み取り結果で置き換わります。画像をClaudeに再送信します。",
                style = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.padding(bottom = Space.m),
            )
            TextAction("読み取り直す", onClick = { sheet = SHEET_NONE; viewModel.rescan() })
            TextAction("キャンセル", onClick = { sheet = SHEET_NONE }, color = AppTheme.colors.inkSoft)
        }
    }

    if (s.missingTotalPrompt) {
        InkSheet(onDismiss = viewModel::dismissMissingTotal) {
            SheetTitle("合計が未入力です")
            Text(
                "確定するには合計が必要です。" +
                    if (s.items.isNotEmpty()) "品目の計算合計を合計に入れて確定できます。" else "合計を入力してください。",
                style = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.padding(bottom = Space.m),
            )
            if (s.items.isNotEmpty()) {
                TextAction("品目の合計 ${formatYen(s.itemsSum)} を入れて確定", onClick = viewModel::confirmWithItemsSum)
            }
            TextAction("キャンセル", onClick = viewModel::dismissMissingTotal, color = AppTheme.colors.inkSoft)
        }
    }

    if (s.duplicateWarning.isNotEmpty()) {
        InkSheet(onDismiss = viewModel::dismissDuplicateWarning) {
            SheetTitle("重複の確認")
            Stamp("重複の可能性", tone = StampTone.Danger)
            Text(
                "既に同じレシートがあります。追加しますか?\n\n" + s.duplicateWarning.joinToString("\n") { it.summary },
                style = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.padding(vertical = Space.m),
            )
            TextAction("追加する", onClick = viewModel::confirmAnyway)
            TextAction("重複ではない(今後表示しない)", onClick = viewModel::confirmMarkingNotDuplicate)
            TextAction("キャンセル", onClick = viewModel::dismissDuplicateWarning, color = AppTheme.colors.inkSoft)
        }
    }
}

/**
 * 最上部のダッシュボード(画像より上)。合計を大きく出し、直下に警告、その下に補助の数字を小さく出す。
 * 品目から計算した額は、合計の位置には出さない。
 */
@Composable
private fun TotalDashboard(s: DetailUiState) {
    val c = AppTheme.colors
    val total = s.total.toLongOrNull()
    val breakdown = s.taxBreakdown
    val small = AppTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 18.sp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.paperRaised)
            .statusBarsPadding()
            .padding(horizontal = Space.edge)
            .padding(top = Space.s, bottom = Space.m),
    ) {
        Label("合計")
        if (total != null) {
            Text(formatYen(total), style = dashboardTotalStyle(), color = c.ink)
        } else {
            Text("合計 未入力", style = dashboardTotalStyle(), color = c.danger)
        }
        // 警告は合計の直下。一致なら何も出さない
        val diff = s.mismatch
        when {
            // 手で消した(出所が manual)ときは「未入力です」。読み取りで合計が無かったときは「読み取れませんでした」
            total == null -> Text(
                if (s.totalSource == TotalSource.MANUAL) "合計が未入力です" else "合計を読み取れませんでした",
                style = AppTheme.type.body,
                color = c.danger,
            )
            diff != null -> Text(
                "合計と品目の合計が ${kotlin.math.abs(diff)} 円ちがいます",
                style = AppTheme.type.body,
                color = c.danger,
            )
        }
        Column(modifier = Modifier.padding(top = Space.xs)) {
            val parts = buildList {
                add("品目の計算合計 ${formatYen(s.itemsSum)}")
                add("税抜合計 ${formatYen(breakdown.netYen)}")
                add("消費税額 ${formatYen(breakdown.taxYen)}")
            }
            Text(parts.joinToString("  /  "), style = small, color = c.inkSoft)
            if (breakdown.lines.isNotEmpty()) {
                Text(
                    "税率ごとの消費税  " + breakdown.lines.joinToString("・") { "${it.taxRate}% ${formatYen(it.taxYen)}" },
                    style = small,
                    color = c.inkSoft,
                )
            }
            if (s.isAdvance) {
                Text("自分が払う分 ${formatYen(s.ownTotal)}", style = small, color = c.inkSoft)
            }
        }
    }
}

/** 支払いまとめの1列: 小さな見出しと大きめの数字。 */
@Composable
private fun SummaryColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Label(label)
        Text(value, style = AppTheme.type.numeralL, color = AppTheme.colors.ink)
    }
}

/** 品目のまとまり1つ分: 品名 ······ 税込額 の行、補足の caption、2個以上なら字下げした1個ずつの行。 */
@Composable
private fun ItemGroupRows(
    group: ItemGroup,
    isAdvance: Boolean,
    onEditGroup: () -> Unit,
    onEditUnit: (Long) -> Unit,
) {
    val c = AppTheme.colors
    val head = group.head
    val count = group.units.size
    val paid = TaxCalc.paidYen(group.units.map { it.toItemModel() })
    val allPicked = group.units.all { it.picked }
    val note = buildString {
        append(if (head.priceIncludesTax) "税込" else "税抜")
        append(' ').append(head.taxRate).append('%')
        append(" ・ ").append(head.category)
        if (count > 1) append(" ・ 1個 ").append(yenText(head.amount))
        else if (!head.priceIncludesTax) append(" ・ 税込 ").append(TaxCalc.paidYenText(head.toItemModel())).append('円')
        if (head.memo.isNotBlank() && count == 1) append(" ・ ").append(head.memo)
        if (isAdvance) append(if (allPicked) " ・ 自分の分" else " ・ 相手の分")
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tappable(onClick = onEditGroup)
                .padding(horizontal = Space.edge, vertical = Space.s),
        ) {
            LeaderRow(
                label = head.name.ifBlank { "(品名未入力)" } + if (count > 1) " ×$count" else "",
                value = formatYen(paid),
            )
            Text(note, style = AppTheme.type.caption, color = c.inkSoft)
        }
        if (count > 1) {
            group.units.forEachIndexed { index, unit ->
                Text(
                    "${index + 1}個目  ${yenText(unit.amount)}" +
                        (if (unit.memo.isNotBlank()) "  ${unit.memo}" else "") +
                        (if (isAdvance) (if (unit.picked) "  自分の分" else "") else ""),
                    style = AppTheme.type.caption,
                    color = c.inkSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tappable { onEditUnit(unit.key) }
                        .padding(start = Space.edge + Space.l, end = Space.edge, top = Space.xs, bottom = Space.xs),
                )
            }
        }
        Hairline(modifier = Modifier.padding(horizontal = Space.edge))
    }
}

/** 品目(まとまり)の編集シート。品名・金額は罫線つき、税率・税込税抜・カテゴリ・負担は丸薬で選ぶ。 */
@Composable
private fun GroupEditSheet(
    group: ItemGroup,
    isAdvance: Boolean,
    viewModel: ReceiptDetailViewModel,
    onEditUnit: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val head = group.head
    val keys = group.keys
    val count = group.units.size
    val allPicked = group.units.all { it.picked }
    InkSheet(onDismiss = onDismiss, modifier = Modifier.imePadding().verticalScroll(rememberScrollState())) {
        SheetTitle(if (count > 1) "品目(同じもの×$count)" else "品目")
        SheetField("品名", head.name, { viewModel.setGroupName(keys, it) })
        SheetField(
            if (count > 1) "金額(1個・円)" else "金額(円)",
            head.amount,
            { viewModel.setGroupAmount(keys, it) },
            numeric = true,
        )
        SheetChoice("税込・税抜") {
            Pills(
                options = listOf(true, false),
                selected = head.priceIncludesTax,
                onSelect = { if (it != head.priceIncludesTax) viewModel.toggleGroupTaxIncluded(keys) },
                label = { if (it) "税込" else "税抜" },
            )
        }
        SheetChoice("税率") {
            Pills(
                options = PRESET_TAX_RATES,
                selected = head.taxRate,
                onSelect = { viewModel.setGroupTaxRate(keys, it) },
                label = { "$it%" },
            )
        }
        SheetField("税率(%)を直接入力", head.taxRate, { viewModel.setGroupTaxRate(keys, it) }, numeric = true)
        SheetChoice("カテゴリ") {
            Pills(
                options = if (head.category in Categories.DEFAULTS) Categories.DEFAULTS else Categories.DEFAULTS + head.category,
                selected = head.category,
                onSelect = { viewModel.setGroupCategory(keys, it) },
                label = { it },
            )
        }
        if (isAdvance) {
            SheetChoice("負担") {
                Pills(
                    options = listOf(true, false),
                    selected = allPicked,
                    onSelect = { if (it != allPicked) viewModel.togglePicked(keys) },
                    label = { if (it) (if (count > 1) "自分の分に含める(全部)" else "自分の分に含める") else "含めない" },
                )
            }
        }
        if (count == 1) {
            SheetField("メモ", head.memo, { viewModel.setUnitMemo(head.key, it) })
        } else {
            SheetChoice("1個ごとに編集") {
                group.units.forEachIndexed { index, unit ->
                    Text(
                        "${index + 1}個目  ${yenText(unit.amount)}" + if (unit.memo.isNotBlank()) "  ${unit.memo}" else "",
                        style = AppTheme.type.body,
                        color = AppTheme.colors.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tappable { onEditUnit(unit.key) }
                            .padding(vertical = Space.s),
                    )
                    Hairline()
                }
            }
            TextAction("同じ品目をもう1個追加", onClick = { viewModel.addSameItem(head.key) })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextAction(
                "この品目を削除",
                onClick = { viewModel.removeGroup(keys); onDismiss() },
                color = AppTheme.colors.danger,
            )
            TextAction("閉じる", onClick = onDismiss)
        }
    }
}

/** 2個以上の品目の、1個ぶんの編集シート。金額・メモ・負担・削除。 */
@Composable
private fun UnitEditSheet(
    unit: ItemForm,
    number: Int?,
    isAdvance: Boolean,
    viewModel: ReceiptDetailViewModel,
    onDismiss: () -> Unit,
) {
    InkSheet(onDismiss = onDismiss, modifier = Modifier.imePadding().verticalScroll(rememberScrollState())) {
        SheetTitle(if (number != null) "${unit.name.ifBlank { "品目" }} ${number}個目" else unit.name.ifBlank { "品目" })
        SheetField("この1個の金額(円)", unit.amount, { viewModel.setUnitAmount(unit.key, it) }, numeric = true)
        SheetField("メモ", unit.memo, { viewModel.setUnitMemo(unit.key, it) })
        if (isAdvance) {
            SheetChoice("負担") {
                Pills(
                    options = listOf(true, false),
                    selected = unit.picked,
                    onSelect = { if (it != unit.picked) viewModel.togglePicked(setOf(unit.key)) },
                    label = { if (it) "この1個を自分の分に含める" else "含めない" },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextAction(
                "この1個を削除",
                onClick = { viewModel.removeUnit(unit.key); onDismiss() },
                color = AppTheme.colors.danger,
            )
            TextAction("閉じる", onClick = onDismiss)
        }
    }
}

/** 原本画像。全幅・高さ固定で切り抜き、タップで拡大表示(ReceiptImageViewer)。 */
@Composable
private fun ReceiptImage(imagePath: String, adjust: ReceiptAdjust, onClick: () -> Unit) {
    val context = LocalContext.current
    // 表示用は 1024px まで。拡大表示(ReceiptImageViewer)は別に高解像度で読む。
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imagePath, adjust) {
        value = withContext(Dispatchers.IO) {
            try {
                ReceiptImageCodec.decodeAdjusted(File(context.filesDir, imagePath), adjust, 1024)?.asImageBitmap()
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }
    val bmp = bitmap
    if (bmp == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IMAGE_HEIGHT)
                .background(AppTheme.colors.paperRaised),
            contentAlignment = Alignment.Center,
        ) {
            Label("(画像を表示できません)")
        }
    } else {
        Image(
            bitmap = bmp,
            contentDescription = "レシート画像(タップで拡大表示)",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(IMAGE_HEIGHT)
                .tappable(onClick = onClick),
        )
    }
}
