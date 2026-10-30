package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.domain.PaymentMerge
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkButtonKind
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.StampTone
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.sheet
import com.amgm.personallog.ui.design.tappable
import com.amgm.personallog.ui.receipt.formatYen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MERGE_TARGET_LABEL = "マージ先"

private val registeredFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

private fun registeredText(createdAt: Long): String =
    Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).format(registeredFormat)

/**
 * 重複の可能性がある支払いの一覧(LedgerRoutes.DUPLICATES)。
 * 組ごとに支払いを並べ、先頭をマージ先、以降をマージ元として向きを示す。
 * 「マージする」はマージの画面へ、「重複ではない」は組を一覧から外す。
 */
@Composable
fun DuplicatesScreen(
    onBack: () -> Unit,
    onOpenPayment: (Long) -> Unit,
    onMerge: (List<Long>) -> Unit,
    viewModel: DuplicatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    PaperScreen {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                DuplicatesHeader(onBack = onBack)
            }

            when (val s = state) {
                DuplicatesUiState.Loading -> item(key = "loading") {
                    Text(
                        "読み込んでいます…",
                        style = AppTheme.type.body,
                        color = AppTheme.colors.inkSoft,
                        modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.l),
                    )
                }
                is DuplicatesUiState.Error -> item(key = "error") {
                    Text(
                        s.message,
                        style = AppTheme.type.body,
                        color = AppTheme.colors.danger,
                        modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.l),
                    )
                }
                is DuplicatesUiState.Loaded -> {
                    if (s.groups.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "重複の可能性がある支払いはありません。",
                                style = AppTheme.type.body,
                                color = AppTheme.colors.inkSoft,
                                modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.l),
                            )
                        }
                    } else {
                        items(s.groups, key = { it.ids.first() }) { group ->
                            DuplicateCard(
                                group = group,
                                onOpenPayment = onOpenPayment,
                                onMerge = { onMerge(group.ids) },
                                onNotDuplicate = { viewModel.markNotDuplicate(group.ids) },
                            )
                        }
                    }
                }
            }

            actionError?.let { message ->
                item(key = "actionError") {
                    Text(
                        message,
                        style = AppTheme.type.body,
                        color = AppTheme.colors.danger,
                        modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.m),
                    )
                }
            }

            item(key = "bottomSpace") { Box(modifier = Modifier.height(Space.xl)) }
        }
    }
}

/** 上部: 小さな kicker と見出し、右に戻る。見出しは長いので ScreenHeader の大きな字ではなく headline を使う。 */
@Composable
private fun DuplicatesHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Space.edge, end = Space.edge, top = Space.l, bottom = Space.m),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Label("支払いの整理")
            Text(
                "重複の可能性がある支払い",
                style = AppTheme.type.headline,
                color = AppTheme.colors.ink,
            )
        }
        TextAction("戻る", onClick = onBack, color = AppTheme.colors.inkSoft)
    }
}

/** 組の1枚: 支払いを縦に並べ、差分の判子と開閉、操作ボタンを出す。 */
@Composable
private fun DuplicateCard(
    group: DuplicateGroup,
    onOpenPayment: (Long) -> Unit,
    onMerge: () -> Unit,
    onNotDuplicate: () -> Unit,
) {
    var diffOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.edge, vertical = Space.xs)
            .sheet(RoundedCornerShape(16.dp), depth = 10.dp)
            .padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (group.hasDifference) {
                Stamp("差分あり", tone = StampTone.Danger)
            } else {
                Stamp("差分なし", tone = StampTone.Moss)
            }
            if (group.conflictCount > 0) {
                Text(
                    "要選択 ${group.conflictCount}件",
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.danger,
                )
            }
        }

        group.payments.forEachIndexed { index, payment ->
            if (index > 0) Hairline()
            PaymentRow(
                role = if (index == 0) MERGE_TARGET_LABEL else "↑ マージ元$index",
                payment = payment,
                onClick = { onOpenPayment(payment.id) },
            )
        }

        if (group.hasDifference) {
            TextAction(
                if (diffOpen) "差分を閉じる" else "差分を開く",
                onClick = { diffOpen = !diffOpen },
                color = AppTheme.colors.accent,
            )
            if (diffOpen) {
                group.differingDiffs.forEach { diff -> DiffRow(diff) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            InkButton(
                "マージする",
                onClick = onMerge,
                modifier = Modifier.weight(1f),
                kind = InkButtonKind.Solid,
            )
            InkButton(
                "重複ではない",
                onClick = onNotDuplicate,
                modifier = Modifier.weight(1f),
                kind = InkButtonKind.Outline,
            )
        }
    }
}

/** 支払い1行: 左に役割・日時・支払い先・登録日時、右に合計。タップで支払い詳細へ。 */
@Composable
private fun PaymentRow(role: String, payment: Payment, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Space.m)) {
            Label(role, color = if (role == MERGE_TARGET_LABEL) c.accent else c.inkFaint)
            Text(
                payment.storeName.ifBlank { "(支払い先なし)" },
                style = AppTheme.type.title,
                color = c.ink,
            )
            Text(payment.purchasedAt, style = AppTheme.type.caption, color = c.inkSoft)
            Text(
                "登録 ${registeredText(payment.createdAt)}",
                style = AppTheme.type.caption,
                color = c.inkFaint,
            )
        }
        val total = payment.totalAmount
        if (total == null) {
            Text("合計 未入力", style = AppTheme.type.numeral, color = c.danger)
        } else {
            Text(formatYen(total.toLong()), style = AppTheme.type.numeral, color = c.ink)
        }
    }
}

/** 差分の1項目: 「項目名: マージ先 値 / マージ元1 値 …」。CONFLICT は色と判子で強調する。 */
@Composable
private fun DiffRow(diff: PaymentMerge.FieldDiff) {
    val c = AppTheme.colors
    val conflict = diff.kind == PaymentMerge.Kind.CONFLICT
    val values = diff.candidates.joinToString(" / ") { candidate ->
        "${candidate.label} ${candidate.text.ifEmpty { "(空)" }}"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Label(diff.field.label, color = if (conflict) c.danger else c.inkFaint)
            if (conflict) Stamp("要選択", tone = StampTone.Danger)
        }
        Text(
            values,
            style = AppTheme.type.body,
            color = if (conflict) c.danger else c.ink,
        )
    }
}
