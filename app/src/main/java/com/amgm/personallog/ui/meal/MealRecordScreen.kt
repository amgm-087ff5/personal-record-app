package com.amgm.personallog.ui.meal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.repository.MealPayment
import com.amgm.personallog.domain.PaymentKind
import com.amgm.personallog.domain.split.MealRecordRow
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable
import com.amgm.personallog.ui.ledger.LedgerLogic
import java.time.YearMonth

/**
 * 食べた記録の一覧(月ごと・日付の新しい順)。自分が何をどれだけ食べたかだけを出す。
 * 支払いの見出しをタップすると支払いの詳細(レシートの詳細)を開く。
 */
@Composable
fun MealRecordScreen(
    modifier: Modifier = Modifier,
    /** 支払いの詳細(レシートの詳細)を開く。 */
    onOpenReceipt: (Long) -> Unit = {},
    /** 紐づく家計簿の支払いを開く。 */
    onOpenPayment: (PaymentKind, Long) -> Unit = { _, _ -> },
    /** 誰が食べたか未入力の支払いの割り勘画面を開く。 */
    onOpenSplit: (Long) -> Unit = {},
    viewModel: MealRecordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    PaperScreen(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.edge,
                end = Space.edge,
                bottom = Space.xl + Space.xl,
            ),
        ) {
            item(key = "header") {
                MealMonthHeader(
                    month = state.month,
                    onPrev = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
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
                    Label("自分が食べた")
                    Text(
                        LedgerLogic.formatYen(state.total),
                        style = AppTheme.type.numeralXL,
                        color = c.ink,
                    )
                    if (state.payments.any { it.draft }) {
                        Text(
                            "下書きの支払いを含む",
                            style = AppTheme.type.caption,
                            color = c.accent,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
            }
            if (!state.loading && state.payments.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "この月の支払いはありません",
                        style = AppTheme.type.body,
                        color = c.inkSoft,
                        modifier = Modifier.padding(vertical = Space.s),
                    )
                }
            }
            items(state.payments, key = { "payment-" + it.paymentId }) { payment ->
                MealPaymentBlock(
                    payment = payment,
                    onOpenReceipt = { onOpenReceipt(payment.paymentId) },
                    onOpenPayment = onOpenPayment,
                    onOpenSplit = { onOpenSplit(payment.paymentId) },
                )
            }
        }
    }
}

@Composable
private fun MealMonthHeader(
    month: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val c = AppTheme.colors
    val ym = try {
        YearMonth.parse(month)
    } catch (e: Exception) {
        YearMonth.now()
    }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(top = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextAction("‹", onClick = onPrev, color = c.ink)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Label("${ym.year}年 食べた記録")
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

/** 1つの支払い: 見出し(日時・支払い先)、自分が食べた行、未入力・支出への操作。 */
@Composable
private fun MealPaymentBlock(
    payment: MealPayment,
    onOpenReceipt: () -> Unit,
    onOpenPayment: (PaymentKind, Long) -> Unit,
    onOpenSplit: () -> Unit,
) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Hairline(color = c.ink)
        Row(
            Modifier.fillMaxWidth().tappable(onOpenReceipt).padding(vertical = Space.s + Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    payment.storeName.ifBlank { "支払い先なし" },
                    style = AppTheme.type.title,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    payment.at.replace('-', '/'),
                    style = AppTheme.type.caption,
                    color = c.inkSoft,
                    maxLines = 1,
                )
            }
            if (payment.draft) Stamp("下書き", modifier = Modifier.padding(start = Space.xs))
        }
        payment.meal.rows.forEach { row ->
            MealRowLine(row, onClick = onOpenReceipt)
        }
        if (payment.meal.unsetGroups > 0) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "誰が食べたか未入力 ${payment.meal.unsetGroups}件",
                    style = AppTheme.type.caption,
                    color = c.accent,
                    modifier = Modifier.weight(1f),
                )
                TextAction("割り勘を開く", onClick = onOpenSplit)
            }
        }
        payment.link?.let { link ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("この支払いの支出を開く", onClick = { onOpenPayment(link.kind, link.id) }, color = c.inkSoft)
            }
        }
    }
}

@Composable
private fun MealRowLine(row: MealRecordRow, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().tappable(onClick).padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.name,
                style = AppTheme.type.body,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(row.quantity, style = AppTheme.type.caption, color = c.inkSoft, maxLines = 1)
        }
        Spacer(Modifier.width(Space.s))
        Text(LedgerLogic.formatYen(row.amount.toLong()), style = AppTheme.type.numeral, color = c.ink)
    }
}
