package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.domain.PaymentMerge
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Pills
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.sheet
import com.amgm.personallog.ui.design.tappable
import com.amgm.personallog.ui.receipt.formatYen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 重複している支払いを1つにまとめる画面。
 * 上に登録の組(マージ先・マージ元)、下に項目ごとの比較。選べない所は「選んでください」を出し、そろうまでマージできない。
 */
@Composable
fun PaymentMergeScreen(
    onBack: () -> Unit,
    onOpenPayment: (Long) -> Unit,
    onMerged: () -> Unit,
    viewModel: PaymentMergeViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    PaperScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction("戻る", onClick = onBack, color = c.inkSoft)
            }
            Text(
                text = "支払いのマージ",
                style = AppTheme.type.headline,
                color = c.ink,
                modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.s),
            )
            if (s.loading) {
                Text(
                    text = "読み込んでいます…",
                    style = AppTheme.type.caption,
                    color = c.inkSoft,
                    modifier = Modifier.padding(horizontal = Space.edge),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Space.edge, vertical = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                itemsIndexed(s.snapshots) { index, snapshot ->
                    SnapshotRow(
                        index = index,
                        snapshot = snapshot,
                        onClick = { onOpenPayment(snapshot.payment.id) },
                    )
                }
                items(s.diffs) { diff ->
                    MergeFieldCard(
                        diff = diff,
                        choice = s.choices[diff.field],
                        draft = s.drafts[diff.field].orEmpty(),
                        error = s.errors[diff.field],
                        onChoose = { viewModel.choose(diff.field, it) },
                        onType = { viewModel.typeManually(diff.field, it) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.edge, vertical = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                s.error?.let { Text(it, style = AppTheme.type.caption, color = c.danger) }
                InkButton(
                    text = if (s.busy) "マージしています…" else "マージする",
                    onClick = { viewModel.merge(onMerged) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = s.canMerge,
                )
            }
        }
    }
}

/** 組の1件。先頭がマージ先、以降はマージ元。タップでその支払いの詳細へ。 */
@Composable
private fun SnapshotRow(index: Int, snapshot: PaymentMerge.Snapshot, onClick: () -> Unit) {
    val c = AppTheme.colors
    val r = snapshot.payment
    val total = r.totalAmount?.let { formatYen(it.toLong()) } ?: "合計なし"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick)
            .padding(vertical = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Label(if (index == 0) "マージ先（登録日時が最も古い）" else "↑ マージ元$index")
        Text(
            text = "${r.purchasedAt.ifBlank { "日時なし" }}　${r.storeName.ifBlank { "支払い先なし" }}",
            style = AppTheme.type.body,
            color = c.ink,
        )
        Text(
            text = "$total ・ 登録 ${formatCreatedAt(r.createdAt)}",
            style = AppTheme.type.caption,
            color = c.inkSoft,
        )
    }
}

/** 項目1つのカード。SAME は値だけ、FILL は補う値、CONFLICT は候補から選ぶ(canManual なら手入力も)。 */
@Composable
private fun MergeFieldCard(
    diff: PaymentMerge.FieldDiff,
    choice: PaymentMerge.Choice?,
    draft: String,
    error: String?,
    onChoose: (Long) -> Unit,
    onType: (String) -> Unit,
) {
    val c = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sheet()
            .padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(diff.field.label, style = AppTheme.type.title, color = c.ink)
        if (diff.field == PaymentMerge.Field.CONTENT) {
            Text(
                text = "品目・割り勘・立て替え・人数・自分の支出は、選んだ支払いのものをまとめて使います",
                style = AppTheme.type.caption,
                color = c.inkSoft,
            )
        }
        when (diff.kind) {
            PaymentMerge.Kind.SAME -> {
                Text(
                    text = diff.candidates.first().text.ifEmpty { "(空)" },
                    style = AppTheme.type.body,
                    color = c.ink,
                )
                Label("差分なし")
            }
            PaymentMerge.Kind.FILL -> {
                Label("未入力の方を補います")
                ChoosePills(diff = diff, choice = choice, onChoose = onChoose)
            }
            PaymentMerge.Kind.CONFLICT -> {
                ChoosePills(diff = diff, choice = choice, onChoose = onChoose)
                if (choice == null) {
                    Text("選んでください", style = AppTheme.type.caption, color = c.danger)
                }
                if (PaymentMerge.canManual(diff.field)) {
                    Label("手入力")
                    BareField(
                        value = draft,
                        onValueChange = onType,
                        placeholder = manualPlaceholder(diff.field),
                        singleLine = true,
                        underline = true,
                    )
                    error?.let { Text(it, style = AppTheme.type.caption, color = c.danger) }
                }
            }
        }
    }
}

/** 同じ値の支払いをまとめた選択肢。値が空の支払いは含めない。 */
private data class MergeOption(val paymentIds: List<Long>, val label: String, val text: String)

@Composable
private fun ChoosePills(
    diff: PaymentMerge.FieldDiff,
    choice: PaymentMerge.Choice?,
    onChoose: (Long) -> Unit,
) {
    val options = diff.candidates
        .filter { it.key.isNotEmpty() }
        .groupBy { it.key }
        .values
        .map { group ->
            MergeOption(
                paymentIds = group.map { it.paymentId },
                label = group.joinToString("・") { it.label },
                text = group.first().text,
            )
        }
    val selected = (choice as? PaymentMerge.Choice.From)?.let { from ->
        options.firstOrNull { from.paymentId in it.paymentIds }
    }
    Pills(
        options = options,
        selected = selected,
        onSelect = { onChoose(it.paymentIds.first()) },
        label = { "${it.label}: ${it.text.ifEmpty { "(空)" }}" },
    )
}

private fun manualPlaceholder(field: PaymentMerge.Field): String = when (field) {
    PaymentMerge.Field.DATE -> "yyyy-MM-dd"
    PaymentMerge.Field.TIME -> "HH:mm（空も可）"
    else -> "支払い先の名前"
}

private fun formatCreatedAt(millis: Long): String =
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(millis))
