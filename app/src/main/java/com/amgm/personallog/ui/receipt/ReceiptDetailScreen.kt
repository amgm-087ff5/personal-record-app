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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.domain.TaxCalc
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

/**
 * レシートの確認・修正画面。
 * 原本画像 → 紙面(店名・合計) → 品目の行 → 支払いまとめ → 操作。品目の編集は行をタップして開くシートで行う。
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
    val groups = remember(s.items) { groupItemForms(s.items) }

    LaunchedEffect(s.finished) { if (s.finished) onBack() }

    PaperScreen(modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize().imePadding()) {
            val hasImage = !s.loading && !s.notFound && s.imagePath.isNotBlank()
            if (hasImage) {
                item(key = "image") {
                    ReceiptImage(imagePath = s.imagePath, onClick = { viewerOpen = true })
                }
            }
            // 戻る・読み取り直すは画像の下の細い行
            item(key = "nav") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (hasImage) Modifier else Modifier.statusBarsPadding())
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
                            Text(
                                formatYen((s.total.toLongOrNull() ?: s.itemsSum)),
                                style = AppTheme.type.numeralXL,
                                color = c.ink,
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
                        s.possibleDuplicate?.let { original ->
                            Column(modifier = Modifier.padding(top = Space.m)) {
                                Stamp("重複の可能性", tone = StampTone.Danger)
                                Text(
                                    "同じ内容(日付・店名・合計)のレシートが既にあります。\n$original",
                                    style = AppTheme.type.caption,
                                    color = c.danger,
                                    modifier = Modifier.padding(top = Space.xs),
                                )
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
                            SummaryColumn("印字合計", formatYen(s.total.toLongOrNull() ?: s.itemsSum), Modifier.weight(1f))
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
                        s.mismatch?.let { diff ->
                            Column(modifier = Modifier.padding(top = Space.m)) {
                                Stamp("不一致", tone = StampTone.Danger)
                                Text(
                                    "印字合計と税込合計が一致しません(印字合計 - 税込合計 = ${formatYen(diff)})。読み取りミスか、端数の差の可能性があります。",
                                    style = AppTheme.type.caption,
                                    color = c.danger,
                                    modifier = Modifier.padding(top = Space.xs),
                                )
                                TextAction("合計欄を支払合計に合わせる", onClick = viewModel::useItemsSumAsTotal)
                            }
                        }
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
        ReceiptImageViewer(relativePath = s.imagePath, onDismiss = { viewerOpen = false })
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

    s.duplicateWarning?.let { existing ->
        InkSheet(onDismiss = viewModel::dismissDuplicateWarning) {
            SheetTitle("重複の確認")
            Stamp("重複の可能性", tone = StampTone.Danger)
            Text(
                "既に同じレシートがあります。追加しますか?\n\n$existing",
                style = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.padding(vertical = Space.m),
            )
            TextAction("追加する", onClick = viewModel::confirmAnyway)
            TextAction("キャンセル", onClick = viewModel::dismissDuplicateWarning, color = AppTheme.colors.inkSoft)
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
private fun ReceiptImage(imagePath: String, onClick: () -> Unit) {
    val context = LocalContext.current
    // 表示用は 1024px まで。拡大表示(ReceiptImageViewer)は別に高解像度で読む。
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) {
            try {
                ReceiptImageCodec.decodeScaled(File(context.filesDir, imagePath), 1024)?.asImageBitmap()
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
                .background(AppTheme.colors.paperRaised)
                .statusBarsPadding(),
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
