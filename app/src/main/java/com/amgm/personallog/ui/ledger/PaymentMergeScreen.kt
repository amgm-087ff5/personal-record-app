package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.domain.PaymentMerge
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Pills
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.Stamp
import com.amgm.personallog.ui.design.StampTone
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.sheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CREATED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

/**
 * 重複候補 1 組を、項目ごとに見比べてひとつの支払いにまとめる画面。
 * 先頭がマージ先(登録日時が最も古い)。競合が残っている項目は、選ぶか手入力するまでマージできない。
 */
@Composable
fun PaymentMergeScreen(
    onBack: () -> Unit,
    onMerged: () -> Unit,
    viewModel: PaymentMergeViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    PaperScreen {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.s)) {
                TextAction("戻る", onClick = onBack, color = c.inkSoft)
            }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                item(key = "head") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Space.edge)) {
                        Label("重複の確認")
                        Text("支払いのマージ", style = AppTheme.type.headline, color = c.ink)
                    }
                }
                s.error?.let { err ->
                    item(key = "error") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Space.edge, vertical = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(err, style = AppTheme.type.caption, color = c.danger, modifier = Modifier.weight(1f))
                            TextAction("閉じる", onClick = viewModel::dismissError, color = c.danger)
                        }
                    }
                }
                if (s.loading) {
                    item(key = "loading") {
                        Text("読み込み中…", style = AppTheme.type.body, color = c.inkSoft, modifier = Modifier.padding(Space.edge))
                    }
                } else if (s.notFound) {
                    item(key = "not-found") {
                        Text(
                            "マージする支払いが見つかりません。重複の一覧から選び直してください。",
                            style = AppTheme.type.body,
                            color = c.inkSoft,
                            modifier = Modifier.padding(Space.edge),
                        )
                    }
                } else {
                    // 最上部: マージ先 ← マージ元1 ← マージ元2 … を、上から下へ矢印(↑)でつなぐ
                    items(s.members.size, key = { "member-" + s.members[it].id }) { i ->
                        val group = s.members[i]
                        MemberRow(
                            index = i,
                            group = group,
                            storeName = group.receiptId?.let { s.storeNames[it] },
                            hasReceipt = group.receiptId != null,
                        )
                    }
                    item(key = "fields-pad") { Spacer(Modifier.height(Space.m)) }
                    items(s.fieldDiffs, key = { it.field.name }) { diff ->
                        FieldCard(
                            diff = diff,
                            s = s,
                            onChoose = viewModel::choose,
                            onStartManual = viewModel::startManual,
                            onType = viewModel::typeManually,
                        )
                    }
                }
                item(key = "pad") { Spacer(Modifier.height(Space.xl)) }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.edge)
                    .padding(top = Space.s, bottom = Space.m),
            ) {
                if (!s.loading && !s.notFound && !s.busy && !s.canMerge) {
                    Text(
                        "項目を選ぶか直すと、マージできます",
                        style = AppTheme.type.caption,
                        color = c.danger,
                        modifier = Modifier.padding(bottom = Space.s),
                    )
                }
                InkButton(
                    text = if (s.busy) "マージしています…" else "マージする",
                    onClick = { viewModel.merge(onMerged) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = s.canMerge && !s.busy,
                )
            }
        }
    }
}

/** 最上部の支払い 1 件。先頭がマージ先、以降は上へ矢印を向けたマージ元。 */
@Composable
private fun MemberRow(index: Int, group: ExpenseGroup, storeName: String?, hasReceipt: Boolean) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.edge)) {
        if (index > 0) {
            Text(
                "↑",
                style = AppTheme.type.title,
                color = c.inkFaint,
                modifier = Modifier.padding(start = Space.s, top = Space.xs, bottom = Space.xs),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .sheet(RoundedCornerShape(14.dp), depth = 6.dp)
                .padding(Space.m),
        ) {
            Label(
                if (index == 0) "マージ先（登録日時が最も古い）" else "マージ元$index",
                color = if (index == 0) c.accent else c.inkFaint,
            )
            Text(
                "${group.date} ${group.time.ifBlank { "時刻なし" }}   ${LedgerLogic.formatYen(group.amount.toLong())}",
                style = AppTheme.type.title,
                color = c.ink,
                modifier = Modifier.padding(top = Space.xs),
            )
            val store = storeName?.takeIf { it.isNotBlank() }
            Text(
                when {
                    !hasReceipt -> "レシートなし"
                    store == null -> "店名なし"
                    else -> store
                },
                style = AppTheme.type.caption,
                color = c.inkSoft,
            )
            Text("登録 ${formatCreated(group.createdAt)}", style = AppTheme.type.caption, color = c.inkFaint)
        }
    }
}

/** 項目ごとの比較カード。SAME は差分なし、FILL は自動で補った値、CONFLICT は選ぶか手入力するまで待つ。 */
@Composable
private fun FieldCard(
    diff: PaymentMerge.FieldDiff,
    s: PaymentMergeUiState,
    onChoose: (PaymentMerge.Field, String) -> Unit,
    onStartManual: (PaymentMerge.Field) -> Unit,
    onType: (PaymentMerge.Field, String) -> Unit,
) {
    val c = AppTheme.colors
    val field = diff.field
    val chosen = s.chosen[field]
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.edge)
            .padding(top = Space.m)
            .sheet(RoundedCornerShape(14.dp), depth = 6.dp)
            .padding(Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(fieldLabel(field), color = c.inkSoft, modifier = Modifier.weight(1f))
            when (diff.kind) {
                PaymentMerge.Kind.SAME -> Text("差分なし", style = AppTheme.type.caption, color = c.inkFaint)
                PaymentMerge.Kind.FILL -> Stamp("差分あり", tone = StampTone.Accent)
                PaymentMerge.Kind.CONFLICT -> Stamp("競合", tone = StampTone.Danger)
            }
        }

        when (diff.kind) {
            PaymentMerge.Kind.SAME -> Text(
                displayText(field, chosen.orEmpty(), s.storeNames),
                style = AppTheme.type.body,
                color = c.ink,
                modifier = Modifier.padding(top = Space.xs),
            )
            PaymentMerge.Kind.FILL -> Text(
                "未入力の方を補う: 採用 ${displayText(field, chosen.orEmpty(), s.storeNames)}",
                style = AppTheme.type.body,
                color = c.ink,
                modifier = Modifier.padding(top = Space.xs),
            )
            PaymentMerge.Kind.CONFLICT -> if (chosen == null) {
                Text("選んでください", style = AppTheme.type.caption, color = c.danger, modifier = Modifier.padding(top = Space.xs))
            }
        }

        if (diff.kind != PaymentMerge.Kind.SAME) {
            // 空の候補は、日付と金額では選べない(空の日付・金額はマージ結果にできないため)
            val options = diff.candidates.filter { it.text.isNotEmpty() || canBeEmpty(field) }
            Pills(
                options = options,
                selected = options.firstOrNull { chosen != null && it.text == chosen },
                onSelect = { onChoose(field, it.text) },
                label = { "${it.label}: ${displayText(field, it.text, s.storeNames)}" },
                modifier = Modifier.padding(top = Space.s),
            )
            if (isTypable(field)) {
                if (field in s.manualFields) {
                    BareField(
                        value = s.drafts[field] ?: chosen.orEmpty(),
                        onValueChange = { onType(field, it) },
                        placeholder = placeholderOf(field),
                        singleLine = true,
                        keyboardOptions = keyboardOf(field),
                        underline = true,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                    )
                    s.errors[field]?.let { err ->
                        Text(err, style = AppTheme.type.caption, color = c.danger, modifier = Modifier.padding(top = Space.xs))
                    }
                } else {
                    TextAction("手入力", onClick = { onStartManual(field) })
                }
            }
        }
    }
}

private fun formatCreated(createdAt: Long): String =
    Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).format(CREATED_FORMAT)

private fun fieldLabel(field: PaymentMerge.Field): String = when (field) {
    PaymentMerge.Field.DATE -> "日付"
    PaymentMerge.Field.TIME -> "時刻"
    PaymentMerge.Field.AMOUNT -> "金額"
    PaymentMerge.Field.CATEGORY -> "カテゴリ"
    PaymentMerge.Field.MEMO -> "メモ"
    PaymentMerge.Field.RECEIPT -> "レシート"
}

/** 候補の値を見やすい文字にする。金額は ¥、レシートは店名、空は「（空）」。 */
private fun displayText(field: PaymentMerge.Field, text: String, storeNames: Map<Long, String>): String = when (field) {
    PaymentMerge.Field.AMOUNT -> text.toLongOrNull()?.let { LedgerLogic.formatYen(it) } ?: emptyText(text)
    PaymentMerge.Field.RECEIPT -> text.toLongOrNull()?.let { storeNames[it] ?: "レシートあり" } ?: "レシートなし"
    else -> emptyText(text)
}

private fun emptyText(text: String): String = text.ifEmpty { "（空）" }

private fun canBeEmpty(field: PaymentMerge.Field): Boolean =
    field != PaymentMerge.Field.DATE && field != PaymentMerge.Field.AMOUNT

/** 手入力できるのは日付・時刻・金額・メモだけ。カテゴリとレシートは候補から選ぶ。 */
private fun isTypable(field: PaymentMerge.Field): Boolean =
    field == PaymentMerge.Field.DATE ||
        field == PaymentMerge.Field.TIME ||
        field == PaymentMerge.Field.AMOUNT ||
        field == PaymentMerge.Field.MEMO

private fun placeholderOf(field: PaymentMerge.Field): String = when (field) {
    PaymentMerge.Field.DATE -> "2026-10-27"
    PaymentMerge.Field.TIME -> "18:30（空でも可）"
    PaymentMerge.Field.AMOUNT -> "1234"
    else -> "メモ"
}

private fun keyboardOf(field: PaymentMerge.Field): KeyboardOptions = when (field) {
    PaymentMerge.Field.AMOUNT -> KeyboardOptions(keyboardType = KeyboardType.Number)
    PaymentMerge.Field.DATE, PaymentMerge.Field.TIME -> KeyboardOptions(keyboardType = KeyboardType.Ascii)
    else -> KeyboardOptions.Default
}
