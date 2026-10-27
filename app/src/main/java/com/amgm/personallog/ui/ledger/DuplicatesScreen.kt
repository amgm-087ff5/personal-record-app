package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.domain.PaymentKind
import com.amgm.personallog.domain.PaymentMerge
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.StampTone
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.sheet
import com.amgm.personallog.ui.design.tappable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 重複の可能性がある支払いの一覧。組ごとにカードで並べ、差分を確かめてからマージへ進む。 */
@Composable
fun DuplicatesScreen(
    onBack: () -> Unit,
    onOpenPayment: (PaymentKind, Long) -> Unit,
    onMerge: (List<Long>) -> Unit,
    viewModel: DuplicatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    // 「差分を開く」の開閉。組はマージ先の支払いの id で区別する
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    PaperScreen {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.m, end = Space.m, bottom = Space.xl),
        ) {
            item(key = "nav") {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.s)) {
                    TextAction("戻る", onClick = onBack, color = c.inkSoft)
                }
            }
            item(key = "title") {
                Column(Modifier.fillMaxWidth().padding(top = Space.s, bottom = Space.m)) {
                    Label("重複の確認")
                    Text("重複の可能性がある支払い", style = AppTheme.type.headline, color = c.ink)
                    Text(
                        "日時が${PaymentMerge.WINDOW_SECONDS / 60}分以内で金額が同じ支払いです。内容を確かめてからマージできます。",
                        style = AppTheme.type.caption,
                        color = c.inkSoft,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }
            state.error?.let { err ->
                item(key = "error") {
                    Text(err, style = AppTheme.type.caption, color = c.danger, modifier = Modifier.padding(vertical = Space.s))
                }
            }
            if (!state.loading && state.sets.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "重複の可能性がある支払いはありません",
                        style = AppTheme.type.body,
                        color = c.inkSoft,
                        modifier = Modifier.padding(vertical = Space.m),
                    )
                }
            }
            items(state.sets, key = { set -> "set-" + set.members.first().group.id }) { set ->
                val targetId = set.members.first().group.id
                DuplicateCard(
                    set = set,
                    expanded = expanded[targetId] == true,
                    onToggleExpanded = { expanded[targetId] = expanded[targetId] != true },
                    onOpenPayment = onOpenPayment,
                    onMerge = { onMerge(set.ids) },
                )
            }
        }
    }
}

/** 重複候補1組のカード。上から、差分の印・支払いの行(マージ先←マージ元)・差分の詳細・マージのボタン。 */
@Composable
private fun DuplicateCard(
    set: DuplicateSetUi,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenPayment: (PaymentKind, Long) -> Unit,
    onMerge: () -> Unit,
) {
    val c = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.m)
            .sheet()
            .padding(Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stamp(
                if (set.hasDifference) "差分あり" else "差分なし",
                tone = if (set.hasDifference) StampTone.Accent else StampTone.Moss,
            )
            if (set.conflictCount > 0) {
                Stamp(
                    "競合 ${set.conflictCount}件",
                    modifier = Modifier.padding(start = Space.xs),
                    tone = StampTone.Danger,
                )
            }
            Spacer(Modifier.weight(1f))
            Text("${set.members.size}件", style = AppTheme.type.caption, color = c.inkSoft)
        }

        Spacer(Modifier.height(Space.s))
        set.members.forEachIndexed { index, member ->
            if (index > 0) Hairline()
            DuplicateMemberRow(
                isTarget = index == 0,
                member = member,
                onClick = { onOpenPayment(PaymentKind.GROUP, member.group.id) },
            )
        }

        if (set.hasDifference) {
            Spacer(Modifier.height(Space.xs))
            TextAction(
                if (expanded) "差分を閉じる" else "差分を開く",
                onClick = onToggleExpanded,
                color = c.accent,
            )
            if (expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    set.diffs.filter { it.differs }.forEach { diff -> DiffLine(diff) }
                }
            }
        }

        Spacer(Modifier.height(Space.s))
        InkButton(text = "マージする", onClick = onMerge, modifier = Modifier.fillMaxWidth())
    }
}

/** 支払い1件の行。左に「マージ先」か「← マージ元」の印、右に日時・店名・登録日時と金額。 */
@Composable
private fun DuplicateMemberRow(
    isTarget: Boolean,
    member: DuplicateMemberUi,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val g = member.group
    val time = g.time.ifBlank { "時刻なし" }
    val store = member.storeName?.takeIf { it.isNotBlank() }
    Row(
        Modifier.fillMaxWidth().tappable(onClick).padding(vertical = Space.s + Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(Space.xl + Space.xl)) {
            Label(
                if (isTarget) "マージ先" else "← マージ元",
                color = if (isTarget) c.accent else c.inkFaint,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "${displayDate(g.date)} $time",
                style = AppTheme.type.body,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                store ?: "店名なし",
                style = AppTheme.type.caption,
                color = if (store != null) c.ink else c.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "登録 ${displayCreatedAt(g.createdAt)}",
                style = AppTheme.type.caption,
                color = c.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.s))
        Text(LedgerLogic.formatYen(g.amount.toLong()), style = AppTheme.type.numeral, color = c.ink)
    }
}

/** 差分の1項目。「項目名: マージ先の値 / マージ元の値…」。競合は朱色で強調する。 */
@Composable
private fun DiffLine(diff: PaymentMerge.FieldDiff) {
    val c = AppTheme.colors
    val conflict = diff.kind == PaymentMerge.Kind.CONFLICT
    val values = diff.candidates.joinToString(" / ") { candidate ->
        diffValueText(diff.field, candidate.text)
    }
    Text(
        "${fieldName(diff.field)}: $values",
        style = if (conflict) AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold) else AppTheme.type.body,
        color = if (conflict) c.danger else c.inkSoft,
    )
}

private fun fieldName(field: PaymentMerge.Field): String = when (field) {
    PaymentMerge.Field.DATE -> "日付"
    PaymentMerge.Field.TIME -> "時刻"
    PaymentMerge.Field.AMOUNT -> "金額"
    PaymentMerge.Field.CATEGORY -> "カテゴリ"
    PaymentMerge.Field.MEMO -> "メモ"
    PaymentMerge.Field.RECEIPT -> "紐づくレシート"
}

/** 差分に出す値。空は「(空)」、金額は ¥ 付き、レシートは番号で示す。 */
private fun diffValueText(field: PaymentMerge.Field, text: String): String {
    if (text.isBlank()) return "(空)"
    return when (field) {
        PaymentMerge.Field.AMOUNT -> LedgerLogic.formatYen(text.toLongOrNull() ?: 0L)
        PaymentMerge.Field.RECEIPT -> "レシート #$text"
        else -> text
    }
}

private fun displayDate(date: String): String = try {
    val d = LocalDate.parse(date)
    "${d.year}/${d.monthValue}/${d.dayOfMonth}"
} catch (e: Exception) {
    date
}

private fun displayCreatedAt(createdAt: Long): String =
    Instant.ofEpochMilli(createdAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"))
