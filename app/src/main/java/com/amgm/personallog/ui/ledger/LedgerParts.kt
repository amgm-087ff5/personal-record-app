@file:OptIn(ExperimentalMaterial3Api::class)

package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amgm.personallog.backup.ImportMode
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.ExpenseItem
import com.amgm.personallog.domain.LedgerCalc
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.LeaderRow
import com.amgm.personallog.ui.design.Pills
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.StampTone
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset


internal fun entryKey(entry: LedgerEntry): String = when (entry) {
    is LedgerEntry.Group -> "grp-" + entry.group.id
    is LedgerEntry.Excluded -> "rcp-" + entry.payment.id
}

/** カテゴリの色。定義済みカテゴリは順番で固定し、それ以外は名前から決まる色にする。未分類は inkFaint。 */
internal fun categoryColor(category: String, series: List<Color>, uncategorized: Color): Color {
    if (category == Categories.UNCATEGORIZED) return uncategorized
    val fixed = Categories.DEFAULTS.indexOf(category)
    val index = if (fixed >= 0) fixed else (category.hashCode() and Int.MAX_VALUE)
    return series[index % series.size]
}

private const val WEEKDAYS = "月火水木金土日"

// ------------------------------------------------------------ 内訳

/** ●色 名前 ······ 金額  割合 を縦に並べた凡例。 */
@Composable
internal fun CategoryLegend(shares: List<CategoryShare>, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        shares.forEach { share ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(categoryColor(share.category, c.series, c.inkFaint)),
                )
                Spacer(Modifier.width(Space.s))
                LeaderRow(
                    label = share.category,
                    value = LedgerLogic.formatYen(share.total),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${share.percent}%",
                    style = AppTheme.type.caption,
                    color = c.inkSoft,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(Space.xl),
                )
            }
        }
    }
}


// ------------------------------------------------------------ グラフ

/** グラフの寸法。階層(半年/月・一月/週・一週/日・一日/時)を切り替えても高さが変わらないよう固定にする。 */
private val CHART_PLOT_HEIGHT = 176.dp
private val CHART_VALUE_LABEL_HEIGHT = 16.dp
private val CHART_X_LABEL_HEIGHT = 28.dp
private val CHART_TICK_LABEL_HEIGHT = 14.dp
private val CHART_AXIS_WIDTH = 52.dp

@Composable
internal fun StackedChart(
    level: ChartLevel,
    label: String,
    bars: List<ChartBar>,
    legend: List<CategoryShare>,
    onLevel: (ChartLevel) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBarTap: (ChartBar) -> Unit,
) {
    val c = AppTheme.colors
    val series = c.series
    val faint = c.inkFaint
    val baseline = c.line
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextAction("‹", onClick = onPrev, color = c.ink)
            Text(
                label,
                style = AppTheme.type.title,
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextAction("›", onClick = onNext, color = c.ink)
        }

        val drawnMax = bars.maxOfOrNull { it.drawnTotal }?.coerceAtLeast(1L) ?: 1L
        if (bars.isNotEmpty()) {
            Spacer(Modifier.height(Space.s))
            // 縦軸の上端と目盛り(上端の半分と上端)。棒はこの上端に対する割合で描く。
            val axisMax = LedgerChart.axisMax(drawnMax)
            val ticks = LedgerChart.axisTicks(axisMax)
            // 時間別(24本)は細いので、棒の上の金額は省く。
            val dense = level == ChartLevel.DAY
            val tiny = AppTheme.type.label.copy(letterSpacing = 0.sp)
            // 階層を切り替えてもグラフの高さが変わらないよう、各部分の高さは固定にする。
            val barArea = CHART_PLOT_HEIGHT - CHART_VALUE_LABEL_HEIGHT
            val tickColor = c.line
            Row(Modifier.fillMaxWidth()) {
                // 縦軸の金額ラベル(目盛りの高さに合わせる)
                Box(Modifier.width(CHART_AXIS_WIDTH).height(CHART_PLOT_HEIGHT)) {
                    ticks.forEach { t ->
                        val y = CHART_PLOT_HEIGHT - barArea * (t.toFloat() / axisMax)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(CHART_TICK_LABEL_HEIGHT)
                                .offset(y = y - CHART_TICK_LABEL_HEIGHT / 2),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text(
                                LedgerLogic.formatYen(t),
                                style = tiny,
                                color = c.inkFaint,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(end = Space.xs),
                            )
                        }
                    }
                }
                // 棒の領域: 横スクロールなし。棒は幅いっぱいに均等に並ぶ。
                Box(Modifier.weight(1f)) {
                    Canvas(Modifier.fillMaxWidth().height(CHART_PLOT_HEIGHT)) {
                        val barAreaPx = barArea.toPx()
                        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                        ticks.forEach { t ->
                            val y = size.height - barAreaPx * (t.toFloat() / axisMax)
                            drawLine(
                                color = tickColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f,
                                pathEffect = dash,
                            )
                        }
                        drawLine(
                            color = baseline,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1f,
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        bars.forEachIndexed { i, bar ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .then(if (bar.drillable) Modifier.tappable { onBarTap(bar) } else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // 棒+その上の金額。金額は棒のすぐ上に載せる。
                                Column(
                                    Modifier.fillMaxWidth().height(CHART_PLOT_HEIGHT),
                                    verticalArrangement = Arrangement.Bottom,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (!dense) {
                                        Text(
                                            LedgerLogic.formatYen(bar.total),
                                            style = tiny,
                                            color = c.inkSoft,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier
                                                .height(CHART_VALUE_LABEL_HEIGHT)
                                                .wrapContentWidth(unbounded = true),
                                        )
                                    }
                                    val barHeight = if (bar.drawnTotal > 0L) barArea * (bar.drawnTotal.toFloat() / axisMax) else 0.dp
                                    Canvas(Modifier.fillMaxWidth().height(barHeight)) {
                                        val barWidth = size.width * 0.6f
                                        val x = (size.width - barWidth) / 2f
                                        var bottom = size.height
                                        bar.segments.forEach { seg ->
                                            if (seg.amount > 0L) {
                                                val h = size.height * (seg.amount.toFloat() / bar.drawnTotal)
                                                drawRect(
                                                    color = categoryColor(seg.category, series, faint),
                                                    topLeft = Offset(x, bottom - h),
                                                    size = Size(barWidth, h),
                                                )
                                                bottom -= h
                                            }
                                        }
                                    }
                                }
                                // 「9/28〜10/4」は「〜」、「10/12(月)」は「(」で2行に分ける。時間別は3時間おきだけ表示する。
                                val text = when {
                                    dense && i % 3 != 0 -> ""
                                    bar.label.contains("〜") -> bar.label.replace("〜", "〜\n")
                                    bar.label.contains("(") -> bar.label.replace("(", "\n(")
                                    else -> bar.label
                                }
                                Box(Modifier.fillMaxWidth().height(CHART_X_LABEL_HEIGHT), contentAlignment = Alignment.TopCenter) {
                                    Text(
                                        text,
                                        style = tiny,
                                        color = c.inkFaint,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        softWrap = false,
                                        modifier = Modifier.wrapContentWidth(unbounded = true),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                "表示するデータがありません",
                style = AppTheme.type.body,
                color = c.inkSoft,
                modifier = Modifier.padding(vertical = Space.s),
            )
        }

        if (legend.isNotEmpty()) {
            Spacer(Modifier.height(Space.m))
            CategoryLegend(legend)
            Spacer(Modifier.height(Space.s))
            Text(ALLOCATION_NOTE, style = AppTheme.type.caption, color = c.inkFaint)
        }

        Spacer(Modifier.height(Space.m))
        LevelTabs(level = level, onLevel = onLevel)
    }
}

/** 半年/月・一月/週・一週/日・一日/時 の文字タブ。選択中は ink + 朱の下線。 */
@Composable
private fun LevelTabs(level: ChartLevel, onLevel: (ChartLevel) -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        ChartLevel.values().forEach { lv ->
            val selected = lv == level
            Column(
                Modifier
                    .tappable { onLevel(lv) }
                    .padding(horizontal = Space.m, vertical = Space.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(lv.label, style = AppTheme.type.title, color = if (selected) c.ink else c.inkFaint)
                Spacer(Modifier.height(Space.xs))
                Box(
                    Modifier
                        .width(Space.l)
                        .height(2.dp)
                        .background(if (selected) c.accent else Color.Transparent),
                )
            }
        }
    }
}

// ------------------------------------------------------------ 日ごとの帳面

@Composable
internal fun DayBlock(
    day: DayGroup,
    expandedGroups: Map<Long, Boolean>,
    /** 下書きの支払いの id(行に「下書き」を付ける)。 */
    draftPaymentIds: Set<Long>,
    /** 割り勘に警告がある支払いの id(行に「要確認」を付ける)。 */
    warnPaymentIds: Set<Long>,
    /** 画像のあるレシート(レシート由来の支払い)の id。行の「レシート由来」は、ここに含まれる支払いの行だけに付ける。 */
    imagePaymentIds: Set<Long>,
    /** 重複の候補がある支払いの id(行に「重複?」を付ける)。 */
    duplicatePaymentIds: Set<Long> = emptySet(),
    /** 未読の支払いの id(行に「未読」を付ける)。 */
    unreadPaymentIds: Set<Long> = emptySet(),
    /** 支払いの id → 支払い先(店名)。行の見出しに使う。 */
    storeNames: Map<Long, String> = emptyMap(),
    onToggleGroup: (Long) -> Unit,
    /** 行をタップ: 支出の詳細画面を開く。 */
    onOpen: (LedgerEntry) -> Unit,
    onDeleteGroup: (LedgerEntry.Group) -> Unit,
) {
    val c = AppTheme.colors
    val date = try {
        LocalDate.parse(day.date)
    } catch (e: Exception) {
        null
    }
    Column(Modifier.fillMaxWidth()) {
        Hairline(color = c.ink)
        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier.width(Space.xl + Space.m).padding(top = Space.s, end = Space.s),
            ) {
                Text(date?.dayOfMonth?.toString() ?: day.date, style = AppTheme.type.numeralL, color = c.ink)
                if (date != null) {
                    val dow = date.dayOfWeek.value
                    Label(
                        WEEKDAYS[dow - 1] + "曜",
                        color = if (dow == 7) c.accent else c.inkFaint,
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    LedgerLogic.formatYen(day.total),
                    style = AppTheme.type.caption,
                    color = c.inkSoft,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Column(Modifier.weight(1f)) {
                day.items.forEachIndexed { index, entry ->
                    if (index > 0) Hairline()
                    when (entry) {
                        is LedgerEntry.Group -> GroupLine(
                            entry = entry,
                            storeName = entry.paymentId?.let { storeNames[it] },
                            draftPaymentIds = draftPaymentIds,
                            warnPaymentIds = warnPaymentIds,
                            imagePaymentIds = imagePaymentIds,
                            duplicatePaymentIds = duplicatePaymentIds,
                            unreadPaymentIds = unreadPaymentIds,
                            expanded = expandedGroups[entry.group.id] == true,
                            onToggle = { onToggleGroup(entry.group.id) },
                            onOpen = { onOpen(entry) },
                            onDelete = { onDeleteGroup(entry) },
                        )
                        is LedgerEntry.Excluded -> ExcludedLine(
                            entry = entry,
                            onClick = { onOpen(entry) },
                            draftPaymentIds = draftPaymentIds,
                            warnPaymentIds = warnPaymentIds,
                            duplicatePaymentIds = duplicatePaymentIds,
                            unreadPaymentIds = unreadPaymentIds,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 行の印: 未読なら「未読」(リップル付き)、支払いが下書きなら「下書き」、割り勘に警告があれば「要確認」、
 * 重複の候補があれば「重複?」。支払いに紐づかない行には付けない。
 */
@Composable
private fun RowBadges(
    paymentId: Long?,
    draftPaymentIds: Set<Long>,
    warnPaymentIds: Set<Long>,
    duplicatePaymentIds: Set<Long> = emptySet(),
    unreadPaymentIds: Set<Long> = emptySet(),
) {
    if (paymentId == null) return
    if (paymentId in unreadPaymentIds) UnreadBadge(modifier = Modifier.padding(start = Space.xs))
    if (paymentId in draftPaymentIds) Stamp("下書き", modifier = Modifier.padding(start = Space.xs))
    if (paymentId in warnPaymentIds) Stamp("要確認", modifier = Modifier.padding(start = Space.xs), tone = StampTone.Danger)
    if (paymentId in duplicatePaymentIds) Stamp("重複?", modifier = Modifier.padding(start = Space.xs), tone = StampTone.Danger)
}

@Composable
private fun ExpenseItemLine(
    expenseItem: ExpenseItem,
    onClick: () -> Unit,
    /** 行の印(下書き・要確認)を出すか。グループの中の品目には出さない。 */
    showBadges: Boolean = false,
    draftPaymentIds: Set<Long> = emptySet(),
    warnPaymentIds: Set<Long> = emptySet(),
    /** 画像のあるレシートの id。「レシート由来」の印は、ここに含まれる支払いの行にだけ付ける(手入力の支払いには付けない)。 */
    imagePaymentIds: Set<Long> = emptySet(),
    duplicatePaymentIds: Set<Long> = emptySet(),
) {
    val c = AppTheme.colors
    val title = listOf(expenseItem.name, expenseItem.memo).filter { it.isNotBlank() }.joinToString(" ").ifBlank { expenseItem.category }
    val sub = listOfNotNull(
        expenseItem.time.ifEmpty { null },
        if (expenseItem.name.isNotBlank() || expenseItem.memo.isNotBlank()) expenseItem.category else null,
        if (expenseItem.paymentId != null && expenseItem.paymentId in imagePaymentIds) "レシート由来" else null,
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().tappable(onClick).padding(vertical = Space.s + Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = AppTheme.type.title,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showBadges) RowBadges(expenseItem.paymentId, draftPaymentIds, warnPaymentIds, duplicatePaymentIds)
            }
            if (sub.isNotEmpty()) {
                Text(sub, style = AppTheme.type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Space.s))
        Text(LedgerLogic.formatYen(expenseItem.amount.toLong()), style = AppTheme.type.numeral, color = c.ink)
    }
}

/** 割り勘で自分が外れた支払いの行。金額は0円で、「自分の分 ¥0（対象外）」と出す。印(下書き・要確認)は他の行と同じ。 */
@Composable
private fun ExcludedLine(
    entry: LedgerEntry.Excluded,
    onClick: () -> Unit,
    draftPaymentIds: Set<Long>,
    warnPaymentIds: Set<Long>,
    duplicatePaymentIds: Set<Long>,
    unreadPaymentIds: Set<Long>,
) {
    val c = AppTheme.colors
    val time = LedgerCalc.splitPurchasedAt(entry.payment.purchasedAt).second
    val sub = listOfNotNull(
        time.ifEmpty { null },
        "自分の分 ${LedgerLogic.formatYen(0L)}（対象外）",
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().tappable(onClick).padding(vertical = Space.s + Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.payment.storeName.ifBlank { "支払い" },
                    style = AppTheme.type.title,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RowBadges(entry.paymentId, draftPaymentIds, warnPaymentIds, duplicatePaymentIds, unreadPaymentIds)
            }
            Text(sub, style = AppTheme.type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.s))
        Text(LedgerLogic.formatYen(entry.amount), style = AppTheme.type.numeral, color = c.inkSoft)
    }
}

@Composable
private fun GroupLine(
    entry: LedgerEntry.Group,
    /** 支払い先(店名)。空または無ければ見出しはカテゴリになる。 */
    storeName: String?,
    draftPaymentIds: Set<Long>,
    warnPaymentIds: Set<Long>,
    imagePaymentIds: Set<Long>,
    duplicatePaymentIds: Set<Long>,
    unreadPaymentIds: Set<Long>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = AppTheme.colors
    val title = LedgerLogic.rowTitle(storeName, entry.category)
    // 見出しを店名にしたときは、カテゴリを副行に回す。メモは空でなければ副行に出す
    val sub = listOfNotNull(
        entry.time.ifEmpty { null },
        entry.category.takeIf { !storeName.isNullOrBlank() },
        entry.group.memo.ifBlank { null },
        "${entry.items.size}品",
    ).joinToString(" · ")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().tappable(onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 開閉は矢印だけ。行のほかの場所をタップすると支出の詳細画面を開く
            Text(
                if (expanded) "▾" else "▸",
                style = AppTheme.type.title,
                color = c.accent,
                modifier = Modifier.tappable(onToggle).padding(end = Space.s, top = Space.s + Space.xs, bottom = Space.s + Space.xs),
            )
            Column(Modifier.weight(1f).padding(vertical = Space.s + Space.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = AppTheme.type.title,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    RowBadges(entry.paymentId, draftPaymentIds, warnPaymentIds, duplicatePaymentIds, unreadPaymentIds)
                }
                Text(sub, style = AppTheme.type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.s))
            Text(LedgerLogic.formatYen(entry.amount), style = AppTheme.type.numeral, color = c.ink)
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = Space.m, bottom = Space.s)) {
                entry.items.forEach { item ->
                    Hairline()
                    ExpenseItemLine(item, onClick = onOpen, imagePaymentIds = imagePaymentIds)
                }
                Hairline(dashed = true)
                Spacer(Modifier.height(Space.s))
                LeaderRow(
                    label = "品目の合計",
                    value = LedgerLogic.formatYen(entry.itemSum),
                    labelStyle = AppTheme.type.caption,
                    valueStyle = AppTheme.type.caption,
                    labelColor = c.inkSoft,
                    valueColor = c.inkSoft,
                )
                if (entry.difference != 0L) {
                    Text(
                        "差額 ${LedgerLogic.formatYen(entry.difference)}(品目の合計と支出金額が一致しません)",
                        style = AppTheme.type.caption,
                        color = c.danger,
                    )
                }
                TextAction("この支出を削除", onClick = onDelete, color = c.danger)
            }
        }
    }
}

// ------------------------------------------------------------ シート

/** 確認用の小さなシート(AlertDialog の代わり)。 */
@Composable
internal fun ConfirmSheet(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "キャンセル",
    danger: Boolean = true,
) {
    val c = AppTheme.colors
    InkSheet(onDismiss = onDismiss) {
        Text(title, style = AppTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Space.s))
        Text(message, style = AppTheme.type.body, color = c.inkSoft)
        Spacer(Modifier.height(Space.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextAction(dismissText, onClick = onDismiss, color = c.inkSoft)
            TextAction(confirmText, onClick = onConfirm, color = if (danger) c.danger else c.accent)
        }
    }
}

/** 支出の追加・編集シート。 */
@Composable
internal fun ExpenseItemEditorSheet(
    original: ExpenseItem?,
    initialDate: String,
    initialTime: String,
    /** 紐づく支払いの日時 "yyyy-MM-dd HH:mm"。紐づかなければ null。 */
    linkedPaymentAt: String?,
    onDismiss: () -> Unit,
    onSave: (date: String, time: String, amount: Int, category: String, name: String, memo: String, done: (String?) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
    /** 見出し。null なら「支出を追加/編集」。 */
    title: String? = null,
    /** 品名の欄を出すか(支払い=グループの編集では出さない)。 */
    showName: Boolean = true,
    /** 日時の欄を出すか。支出(まとまり)の品目の日時は支出に従うので、品目の編集では出さない。 */
    showDateTime: Boolean = true,
    /** 金額を読み取り専用にし、この注記を出す(割り勘が2人以上の支払い。金額は割り勘画面=全体支出で直す)。null なら直せる。 */
    lockedAmountNote: String? = null,
    /**
     * 紐づく支払いがレシート(画像・読取原文あり)か。手入力の支払いは false で、「レシート由来」の注記と
     * 「支払いの日時と異なります」の確認を出さない(比べる支払いが無い。日時は支払いへ書き戻す)。
     */
    fromScannedReceipt: Boolean = true,
) {
    val c = AppTheme.colors
    var dateText by remember { mutableStateOf(initialDate) }
    var timeText by remember { mutableStateOf(initialTime) }
    var pendingSave by remember { mutableStateOf<ExpenseItemValidation?>(null) }
    var amountText by remember { mutableStateOf(original?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(original?.category) }
    var memo by remember { mutableStateOf(original?.memo ?: "") }
    var name by remember { mutableStateOf(original?.name ?: "") }
    var validation by remember { mutableStateOf<ExpenseItemValidation?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // 開いた時の値から何か変えていれば未保存。閉じる(×・戻る・最小化からの下スワイプ)ときに破棄の確認を出す
    val dirty = dateText != initialDate || timeText != initialTime ||
        amountText != (original?.amount?.toString() ?: "") || category != original?.category ||
        memo != (original?.memo ?: "") || name != (original?.name ?: "")
    val requestClose: () -> Unit = {
        if (!saving) {
            if (dirty) confirmDiscard = true else onDismiss()
        }
    }

    val categories = remember(original) {
        val base = Categories.DEFAULTS.filter { it != Categories.UNCATEGORIZED }
        val extra = original?.category
        if (extra != null && extra !in base) base + extra else base
    }

    fun doSave(v: ExpenseItemValidation) {
        saving = true
        onSave(v.date!!, v.time!!, v.amount!!, v.category!!, name, memo) { err ->
            saving = false
            if (err == null) onDismiss() else saveError = err
        }
    }

    // 入力のシート: 下スワイプでは閉じず最小化して入力を保持する
    InkSheet(onDismiss = requestClose, swipeToDismiss = false, title = title ?: if (original == null) "支出を追加" else "支出を編集") {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            if (original?.paymentId != null && fromScannedReceipt) {
                Text(
                    "レシート由来です" + (linkedPaymentAt?.let { "(支払いの日時 $it)" } ?: ""),
                    style = AppTheme.type.caption,
                    color = c.accent,
                )
            }

            // 金額: 大きく中央。割り勘が2人以上のときは読み取り専用(直す場所は割り勘画面=全体支出)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Label("金額(円)")
                if (lockedAmountNote == null) {
                    BareField(
                        value = amountText,
                        onValueChange = { v -> amountText = v.filter { it in '0'..'9' }.take(9) },
                        textStyle = AppTheme.type.numeralXL.copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else {
                    Text(
                        LedgerLogic.formatYen(amountText.toLongOrNull() ?: 0L),
                        style = AppTheme.type.numeralXL.copy(textAlign = TextAlign.Center),
                        color = c.inkSoft,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        lockedAmountNote,
                        style = AppTheme.type.caption,
                        color = c.inkFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                    )
                }
                Hairline(color = c.ink)
            }

            // カテゴリ
            Label("カテゴリ")
            Pills(
                options = categories,
                selected = category,
                onSelect = { category = it },
                label = { it },
            )

            // 日時: タップできる文字
            Label("日時")
            if (showDateTime) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextAction(dateChip(dateText), onClick = { showDatePicker = true }, color = c.ink)
                    TextAction(timeText.ifBlank { "時刻を選ぶ" }, onClick = { showTimePicker = true }, color = c.ink)
                    Text("タップで変更", style = AppTheme.type.caption, color = c.inkFaint)
                }
            } else {
                Text(
                    "${dateChip(dateText)} ${timeText}(支出の日時に従います。直すときは支出の日時を直してください)",
                    style = AppTheme.type.caption,
                    color = c.inkFaint,
                )
            }

            if (showName) {
                Label("品名")
                BareField(
                    value = name,
                    onValueChange = { name = it },
                    underline = true,
                    placeholder = "品名",
                )
            }

            // メモ
            Label("メモ")
            BareField(
                value = memo,
                onValueChange = { memo = it },
                underline = true,
                placeholder = "メモ",
            )

            val errors = listOfNotNull(
                validation?.dateError,
                validation?.timeError,
                validation?.amountError,
                validation?.categoryError,
                saveError,
            )
            errors.forEach { Text(it, style = AppTheme.type.caption, color = c.danger) }

            InkButton(
                text = "保存",
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val v = LedgerLogic.validate(dateText, timeText, amountText, category)
                    validation = v
                    saveError = null
                    if (v.isValid) {
                        // 紐づく支払いと日時が違うまま日時を変えて保存しようとしたら、確認を挟む(支払いは変更しない)
                        val dateTimeChanged = original != null && (v.date != original.date || v.time != original.time)
                        val mismatch = fromScannedReceipt && linkedPaymentAt != null && "${v.date} ${v.time}" != linkedPaymentAt
                        if (dateTimeChanged && mismatch) pendingSave = v else doSave(v)
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (onDelete != null) {
                    TextAction("削除", enabled = !saving, onClick = { confirmDelete = true }, color = c.danger)
                } else {
                    Spacer(Modifier.width(Space.xs))
                }
                TextAction("キャンセル", enabled = !saving, onClick = requestClose, color = c.inkSoft)
            }
        }
    }

    if (confirmDiscard) {
        ConfirmSheet(
            title = "未保存の内容が残っています",
            message = "破棄しますか？ 入力した内容は保存されません。",
            confirmText = "破棄",
            dismissText = "入力に戻る",
            onConfirm = {
                confirmDiscard = false
                onDismiss()
            },
            onDismiss = { confirmDiscard = false },
        )
    }

    pendingSave?.let { v ->
        ConfirmSheet(
            title = "支払いの日時と異なります",
            message = "この支出は支払い(${linkedPaymentAt ?: ""})に紐づいています。" +
                "入力した日時(${v.date} ${v.time})は支払いと異なります。" +
                "支払いの日時は変更されません。このまま保存しますか？",
            confirmText = "このまま保存",
            dismissText = "戻る",
            danger = false,
            onDismiss = { pendingSave = null },
            onConfirm = {
                pendingSave = null
                doSave(v)
            },
        )
    }

    if (showDatePicker) {
        val initialMillis = try {
            LocalDate.parse(dateText.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextAction("決定", onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        dateText = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                })
            },
            dismissButton = { TextAction("キャンセル", onClick = { showDatePicker = false }, color = c.inkSoft) },
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val parts = timeText.split(":")
        val h0 = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12
        val m0 = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timeState = rememberTimePickerState(initialHour = h0, initialMinute = m0, is24Hour = true)
        InkSheet(onDismiss = { showTimePicker = false }) {
            Label("時刻")
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = timeState)
            }
            InkButton(
                text = "決定",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    timeText = timeState.hour.toString().padStart(2, '0') + ":" +
                        timeState.minute.toString().padStart(2, '0')
                    showTimePicker = false
                },
            )
        }
    }

    if (confirmDelete && onDelete != null) {
        ConfirmSheet(
            title = "削除しますか?",
            message = "この支出を削除します。元に戻せません。",
            confirmText = "削除",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

private fun dateChip(dateText: String): String = try {
    val d = LocalDate.parse(dateText.trim())
    "${d.monthValue}月${d.dayOfMonth}日"
} catch (e: Exception) {
    dateText.ifBlank { "日付を選ぶ" }
}

// ------------------------------------------------------------ バックアップ

@Composable
internal fun BackupSheets(
    state: BackupUiState,
    onPickMode: (ImportMode) -> Unit,
    onCancelImport: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val c = AppTheme.colors
    if (state.pendingImport != null) {
        var mode by remember { mutableStateOf(ImportMode.MERGE) }
        InkSheet(onDismiss = onCancelImport) {
            Text("バックアップを取り込む", style = AppTheme.type.title, color = c.ink)
            Spacer(Modifier.height(Space.s))
            Text("既存のデータの扱いを選んでください。", style = AppTheme.type.body, color = c.inkSoft)
            Spacer(Modifier.height(Space.s))
            ModeChoice("追加(重複はスキップ)", "今あるデータは残し、無いものだけ追加します", mode == ImportMode.MERGE) { mode = ImportMode.MERGE }
            Hairline()
            ModeChoice("すべて置き換え", "今ある日記・支払い・家計簿を全て削除してから取り込みます", mode == ImportMode.REPLACE) { mode = ImportMode.REPLACE }
            Spacer(Modifier.height(Space.m))
            InkButton("取り込む", onClick = { onPickMode(mode) }, modifier = Modifier.fillMaxWidth())
            TextAction("キャンセル", onClick = onCancelImport, color = c.inkSoft, modifier = Modifier.fillMaxWidth())
        }
    }
    state.progress?.let { p ->
        InkSheet(onDismiss = {}, closable = false) {
            Label("処理中")
            Spacer(Modifier.height(Space.s))
            Text(
                p.message + if (p.total > 0) "(${p.current}/${p.total})" else if (p.current > 0) "(${p.current})" else "",
                style = AppTheme.type.body,
                color = c.ink,
            )
            Spacer(Modifier.height(Space.m))
            if (p.total > 0) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(c.line)) {
                    Box(
                        Modifier
                            .fillMaxWidth((p.current.toFloat() / p.total).coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(c.accent),
                    )
                }
            } else {
                Hairline(dashed = true, color = c.accent)
            }
        }
    }
    if (state.resultTitle != null) {
        InkSheet(onDismiss = onDismissResult) {
            Text(state.resultTitle, style = AppTheme.type.title, color = c.ink)
            Spacer(Modifier.height(Space.s))
            Text(state.resultMessage ?: "", style = AppTheme.type.body, color = c.inkSoft)
            Spacer(Modifier.height(Space.m))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("OK", onClick = onDismissResult)
            }
        }
    }
}

@Composable
private fun ModeChoice(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().tappable(onClick).padding(vertical = Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Text(if (selected) "◉" else "○", style = AppTheme.type.title, color = if (selected) c.accent else c.inkFaint)
        Spacer(Modifier.width(Space.s))
        Column {
            Text(title, style = AppTheme.type.body, color = c.ink)
            Text(sub, style = AppTheme.type.caption, color = c.inkSoft)
        }
    }
}
