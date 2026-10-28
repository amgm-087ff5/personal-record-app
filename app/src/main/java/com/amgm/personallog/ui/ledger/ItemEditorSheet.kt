package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.Pill
import com.amgm.personallog.ui.design.Pills
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ------------------------------------------------------------ 純粋ロジック(JVM でテスト可能)

/**
 * 品目の1ページぶんの入力値。key は画面の中での識別子(既存の品目は id と同じ、新しい品目は負の値)。
 * unknown は「金額不明」の印。不明のとき amountText は使わず、画面には支出の金額から計算した値を出す。
 */
internal data class ItemDraft(
    val key: Long,
    val id: Long?,
    val name: String,
    val amountText: String,
    val category: String?,
    val memo: String,
    val unknown: Boolean,
) {
    companion object {
        /** 保存済みの品目から、ページの初期値を作る。 */
        fun of(e: Expense): ItemDraft = ItemDraft(
            key = e.id,
            id = e.id,
            name = e.name,
            amountText = e.amount.toString(),
            category = e.category,
            memo = e.memo,
            unknown = e.unknownAmount,
        )
    }
}

/** 金額の欄の値。Typed は入力された金額、Current は保存する時点の品目の金額(不明の品目は支出から計算された値)。 */
internal sealed interface AmountSource {
    data class Typed(val value: Int) : AmountSource
    object Current : AmountSource
}

/** 保存で行う1回の書き込み。順番どおりに1つずつ実行する(前の結果を次が使うため)。 */
internal sealed interface ItemSaveOp {
    /** 品目の「不明」を切り替える(既存の品目のみ)。 */
    data class SetUnknown(val id: Long, val unknown: Boolean) : ItemSaveOp

    /** 品目を直す。id が null なら新しい品目を足す。 */
    data class Save(
        val id: Long?,
        val name: String,
        val category: String,
        val memo: String,
        val amount: AmountSource,
    ) : ItemSaveOp
}

/** ページの変更の内訳。unknownChanged は「不明」の印、fieldsChanged は品名・カテゴリ・メモ・(不明でない時の)金額。 */
internal data class ItemChanges(val unknownChanged: Boolean, val fieldsChanged: Boolean) {
    val hasChanges: Boolean get() = unknownChanged || fieldsChanged
}

/** ページの入力エラー。保存を試したときだけ画面に出す。 */
internal data class PageErrors(val amount: String?, val category: String?) {
    val any: Boolean get() = amount != null || category != null
}

/** 金額の「不明」の印を出すか。 */
internal enum class UnknownToggle {
    /** 出さない(手入力の支出・割り勘が2人以上・品目が1つ・支払いの品目に紐づかない) */
    Hidden,

    /** 新しい品目。保存したあとで選べる(まだ支払いの品目が無い) */
    AfterSave,

    /** 押せる */
    Enabled,
}

/** 品目の編集シートの純粋ロジック。画面の状態は持たず、入力値と保存済みの値から答えを出す。 */
internal object ItemPagerLogic {
    /** 最後の品目より右へ引いたとき、ページ幅のこの割合を超えて離すと品目を追加する。 */
    const val ADD_THRESHOLD = 0.3f

    /** 金額の最大桁数(入力欄と同じ) */
    const val MAX_AMOUNT_DIGITS = 9

    /** 金額の文字を整数にする。空・数字以外・0円以下・大きすぎるときは null。 */
    fun parseAmount(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty() || !t.all { it in '0'..'9' }) return null
        val n = t.toLongOrNull() ?: return null
        return if (n in 1L..Int.MAX_VALUE.toLong()) n.toInt() else null
    }

    /** ページの入力エラー。「不明」のときは金額を見ない(計算された値を使う)。 */
    fun validate(draft: ItemDraft): PageErrors {
        val amountError = if (draft.unknown) {
            null
        } else {
            when {
                draft.amountText.isBlank() -> "金額を入力してください"
                parseAmount(draft.amountText) == null -> "金額は1円以上の整数で入力してください"
                else -> null
            }
        }
        val categoryError = if (draft.category.isNullOrBlank()) "カテゴリを選択してください" else null
        return PageErrors(amountError, categoryError)
    }

    /**
     * ページの変更の内訳。latest が null なら新しい品目: 何か入っていれば未保存。
     * 不明のときは金額の欄を見ない(計算された値が入るため、金額の差は変更にしない)。
     */
    fun changes(draft: ItemDraft, latest: ItemDraft?): ItemChanges {
        if (latest == null) {
            val filled = draft.name.isNotBlank() || draft.amountText.isNotBlank() || draft.memo.isNotBlank()
            return ItemChanges(unknownChanged = false, fieldsChanged = filled)
        }
        val unknownChanged = draft.unknown != latest.unknown
        val amountChanged = !draft.unknown && draft.amountText.trim() != latest.amountText.trim()
        val fieldsChanged = draft.name != latest.name ||
            draft.category != latest.category ||
            draft.memo != latest.memo ||
            amountChanged
        return ItemChanges(unknownChanged, fieldsChanged)
    }

    /**
     * 保存の書き込みを順番どおりに並べる。変更のあるページだけ。
     * 同じページで「不明」と他の欄を両方変えるときは、不明にする前に欄を保存する(不明の後だと金額が変わってしまうため)。
     * 不明を外すときは、欄を保存する前に外す(入力された金額を使うため)。新しい品目は最後に足す。
     * latestOf は保存済みの品目の値(id で引く)。draft の id が無いか、保存済みに無いページは対象外。
     */
    fun planSave(drafts: List<ItemDraft>, latestOf: (Long) -> ItemDraft?): List<ItemSaveOp> {
        val ops = mutableListOf<ItemSaveOp>()
        val adds = mutableListOf<ItemSaveOp>()
        for (d in drafts) {
            val id = d.id
            val latest = if (id != null) latestOf(id) else null
            if (id != null && latest == null) continue
            val ch = changes(d, latest)
            if (!ch.hasChanges) continue
            val category = d.category.orEmpty()
            val typed = parseAmount(d.amountText) ?: 0
            val memo = d.memo.trim()
            val name = d.name.trim()
            when {
                id == null -> adds += ItemSaveOp.Save(null, name, category, memo, AmountSource.Typed(typed))
                ch.unknownChanged && d.unknown -> {
                    if (ch.fieldsChanged) ops += ItemSaveOp.Save(id, name, category, memo, AmountSource.Current)
                    ops += ItemSaveOp.SetUnknown(id, true)
                }
                ch.unknownChanged -> {
                    ops += ItemSaveOp.SetUnknown(id, false)
                    if (ch.fieldsChanged) ops += ItemSaveOp.Save(id, name, category, memo, AmountSource.Typed(typed))
                }
                else -> {
                    val amount = if (d.unknown) AmountSource.Current else AmountSource.Typed(typed)
                    ops += ItemSaveOp.Save(id, name, category, memo, amount)
                }
            }
        }
        return ops + adds
    }

    /** 「不明」の印を出すか。 */
    fun unknownToggle(
        isNew: Boolean,
        receiptLinked: Boolean,
        splitPeople: Int,
        onlyOneItem: Boolean,
        hasReceiptItem: Boolean,
    ): UnknownToggle = when {
        !receiptLinked || splitPeople >= 2 || onlyOneItem -> UnknownToggle.Hidden
        isNew -> UnknownToggle.AfterSave
        !hasReceiptItem -> UnknownToggle.Hidden
        else -> UnknownToggle.Enabled
    }

    /** 削除を出すか。新しい品目は保存していないので消せない。品目は1つ以上必要なので、最後の1つも消せない。 */
    fun canDelete(isNew: Boolean, visibleCount: Int): Boolean = !isNew && visibleCount > 1

    /** 末尾の操作が「追加」か(「›」ではなく「＋」を出すか)。page は今のページ、count は品目のページ数(追加の仮想ページを除く)。 */
    fun trailingIsAdd(page: Int, count: Int): Boolean = page >= count - 1

    /** 引いた距離(px)の、ページ幅に対する割合(0〜1)。ページ幅が分からないとき(0以下)は 0。 */
    fun pullFraction(pullPx: Float, pageWidthPx: Int): Float =
        if (pageWidthPx > 0) (pullPx / pageWidthPx).coerceIn(0f, 1f) else 0f

    /** 手を離したとき、品目を追加するか。引いた割合が閾値以上のときだけ。 */
    fun addOnRelease(pull: Float): Boolean = pull >= ADD_THRESHOLD

    /** カテゴリの選択肢。今の値が既定に無ければ末尾に足す(既存の値を消さないため)。 */
    fun categoryOptions(current: String?): List<String> {
        val base = Categories.DEFAULTS.filter { it != Categories.UNCATEGORIZED }
        return if (current != null && current !in base) base + current else base
    }
}

// ------------------------------------------------------------ シート

/** 金額の欄(と不明の印)の幅。品名の欄は残りを使う。 */
private val AMOUNT_WIDTH: Dp = 168.dp

/** ページの高さ。詳細(メモ・削除)は中で縦にスクロールする。 */
private val PAGER_HEIGHT: Dp = 330.dp

/**
 * 品目の編集ドロワー。支出の品目を横スワイプで前後へ移れる(一覧のタップで、そのページを開く)。
 *
 * - ページ: 1ページ=1品目。1段目に「品名」と「金額」(同じ行)、2段目に「カテゴリ」、折りたたみの「詳細」にメモと削除。
 * - 最後の品目の「›」は「＋」になり、新しい空の品目ページを足して移る。
 * - 過度なスワイプ(最後の品目より右へ引く): 指で引いた距離を数え、引いている間は右端に「＋」を出す。
 *   ページ幅の [ItemPagerLogic.ADD_THRESHOLD] を超えて指を離したときだけ、品目を1つ足して移る。
 *   足すのは指の操作だけ(ボタン・アニメーションのスクロールでは足さない)。引きしろの伸び(オーバースクロール)は使わない。
 * - 保存: 変更のあるページだけ、順番に書き込む(失敗したら止めて理由を出す)。閉じるときに未保存があれば破棄を確認する。
 *
 * 画面の入力は remember で持つ(画面の回転・再生成では失われる。品目の内容は保存済みから作り直す)。
 */
@Composable
internal fun ItemEditorSheet(
    group: ExpenseGroup,
    /** 消していない品目(支出の中の並び順)。 */
    items: List<Expense>,
    /** 開く品目の id。追加のときは null。 */
    startItemId: Long?,
    /** true なら、末尾に空の新しい品目を足した状態で開く。 */
    adding: Boolean,
    splitPeople: Int,
    onlyOneItem: Boolean,
    onDismiss: () -> Unit,
    onSaveItem: (original: Expense?, amount: Int, category: String, name: String, memo: String, done: (String?) -> Unit) -> Unit,
    onSetUnknown: (expense: Expense, unknown: Boolean, done: (String?) -> Unit) -> Unit,
    onDeleteItem: (Expense) -> Unit,
) {
    val c = AppTheme.colors
    val latestItems by rememberUpdatedState(items)
    fun latestExpense(id: Long): Expense? = latestItems.firstOrNull { it.id == id }
    fun latestDraft(id: Long): ItemDraft? = latestExpense(id)?.let { ItemDraft.of(it) }
    fun isDirty(d: ItemDraft): Boolean = ItemPagerLogic.changes(d, d.id?.let { latestDraft(it) }).hasChanges

    // 新しい品目の key は負の値(既存の id と重ならない)
    val keySeq = remember { IntArray(1) }
    fun blankDraft(): ItemDraft {
        keySeq[0] += 1
        return ItemDraft(
            key = -keySeq[0].toLong(),
            id = null,
            name = "",
            amountText = "",
            category = group.category.takeIf { it != Categories.UNCATEGORIZED },
            memo = "",
            unknown = false,
        )
    }
    val drafts = remember {
        mutableStateListOf<ItemDraft>().apply {
            addAll(items.map { ItemDraft.of(it) })
            if (adding) add(blankDraft())
        }
    }
    val initialPage = if (adding) drafts.lastIndex else drafts.indexOfFirst { it.id == startItemId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { drafts.size })
    val scope = rememberCoroutineScope()

    var saving by remember { mutableStateOf(false) }
    var saveAttempted by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    val detailOpen = remember { mutableStateMapOf<Long, Boolean>() }
    // 最後の品目より右へ、指で引いた距離(px)。指を離すと 0 に戻す
    var pullPx by remember { mutableFloatStateOf(0f) }
    // 画面の幅(px)。ページの幅と同じ。引いた距離を割合にするのに使う
    var pageWidthPx by remember { mutableIntStateOf(0) }
    // 指で引いて離したときに増える。増えたら品目を足す(下の LaunchedEffect)
    var addTick by remember { mutableIntStateOf(0) }

    val receiptLinked = group.receiptId != null
    val amountLock: String? = when {
        splitPeople >= 2 -> "割り勘が2人以上のため、金額は支払いの画面で直します"
        onlyOneItem -> "品目が1つのときは、支出の金額と同じです"
        else -> null
    }

    fun replaceDraft(next: ItemDraft) {
        val i = drafts.indexOfFirst { it.key == next.key }
        if (i >= 0) drafts[i] = next
    }

    suspend fun addAndGo() {
        drafts.add(blankDraft())
        pagerState.animateScrollToPage(drafts.lastIndex)
    }

    // 指で引いて離したときの追加。離したあとの惰性のスクロールが止まってから足す(スクロールと競合させない)
    LaunchedEffect(addTick) {
        if (addTick > 0) {
            snapshotFlow { pagerState.isScrollInProgress }.first { !it }
            addAndGo()
        }
    }


    /** 閉じる。未保存があれば破棄の確認を出す。保存中は閉じない。 */
    val requestClose: () -> Unit = {
        if (!saving) {
            if (drafts.any { isDirty(it) }) confirmDiscard = true else onDismiss()
        }
    }

    /** 変更のあるページを順番に保存する。途中で失敗したら止め、その理由を出す(保存済みのページは次回は変更なしになる)。 */
    fun runOps(ops: List<ItemSaveOp>, index: Int) {
        if (index >= ops.size) {
            saving = false
            onDismiss()
            return
        }
        val fail: (String) -> Unit = { msg ->
            saving = false
            saveError = msg
        }
        when (val op = ops[index]) {
            is ItemSaveOp.SetUnknown -> {
                val e = latestExpense(op.id)
                if (e == null) {
                    fail(MESSAGE_MISSING)
                    return
                }
                onSetUnknown(e, op.unknown) { err -> if (err == null) runOps(ops, index + 1) else fail(err) }
            }
            is ItemSaveOp.Save -> {
                val original = op.id?.let { latestExpense(it) }
                if (op.id != null && original == null) {
                    fail(MESSAGE_MISSING)
                    return
                }
                val amount = when (val a = op.amount) {
                    is AmountSource.Typed -> a.value
                    AmountSource.Current -> original?.amount ?: 0
                }
                onSaveItem(original, amount, op.category, op.name, op.memo) { err ->
                    if (err == null) runOps(ops, index + 1) else fail(err)
                }
            }
        }
    }

    fun save() {
        saveAttempted = true
        // 入力エラーのページがあれば、そのページへ移って止める
        val invalid = drafts.indexOfFirst { isDirty(it) && ItemPagerLogic.validate(it).any }
        if (invalid >= 0) {
            scope.launch { pagerState.animateScrollToPage(invalid) }
            return
        }
        val ops = ItemPagerLogic.planSave(drafts.toList()) { id -> latestDraft(id) }
        if (ops.isEmpty()) {
            onDismiss()
            return
        }
        saving = true
        saveError = null
        runOps(ops, 0)
    }

    InkSheet(onDismiss = requestClose, swipeToDismiss = false, title = "品目") {
        Column(Modifier.fillMaxWidth().imePadding(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            val page = pagerState.currentPage
            val count = drafts.size
            // 右端の引きしろ: 最後の品目より右へ引いている割合(0〜1)。「＋」の表示に使う
            val pull = if (page == count - 1) ItemPagerLogic.pullFraction(pullPx, pageWidthPx) else 0f

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(PAGER_HEIGHT)
                    .onSizeChanged { pageWidthPx = it.width }
                    .testTag("itemPager")
                    // 過度なスワイプ: 最後の品目より右へ、指で引いた距離を数える。指の動きを Initial の段で見るだけで、
                    // 消費しない(ページ送りはそのまま動く)。プログラムによるスクロール(ボタン・アニメーション)は指ではないので数えない。
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var dragX = 0f
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                dragX += change.positionChange().x
                                // 最後の品目にいるときだけ、左へ引いた距離を数える(右へ戻すと減る)
                                if (pagerState.currentPage == drafts.lastIndex) {
                                    pullPx = (-dragX).coerceAtLeast(0f)
                                }
                            }
                            // 指を離した: 閾値を超えていれば、品目を1つ足して移る(追加は scope で行う)
                            val add = pagerState.currentPage == drafts.lastIndex &&
                                ItemPagerLogic.addOnRelease(ItemPagerLogic.pullFraction(pullPx, pageWidthPx))
                            pullPx = 0f
                            if (add && !saving) addTick++
                        }
                    },
            ) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { p ->
                    val draft = drafts.getOrNull(p) ?: return@HorizontalPager
                    val saved = draft.id?.let { latestExpense(it) }
                    val toggle = ItemPagerLogic.unknownToggle(
                        isNew = draft.id == null,
                        receiptLinked = receiptLinked,
                        splitPeople = splitPeople,
                        onlyOneItem = onlyOneItem,
                        hasReceiptItem = saved?.receiptItemId != null,
                    )
                    ItemPage(
                        draft = draft,
                        categories = ItemPagerLogic.categoryOptions(draft.category),
                        amountLock = amountLock,
                        computedAmount = saved?.amount?.toString(),
                        unknownToggle = toggle,
                        detailOpen = detailOpen[draft.key] == true,
                        canDelete = ItemPagerLogic.canDelete(isNew = draft.id == null, visibleCount = items.size),
                        errors = if (saveAttempted && isDirty(draft)) ItemPagerLogic.validate(draft) else null,
                        onChange = { replaceDraft(it) },
                        onToggleDetail = { detailOpen[draft.key] = detailOpen[draft.key] != true },
                        onDelete = { confirmDeleteId = draft.id },
                    )
                }
                if (pull > 0.02f) {
                    val ready = pull >= ItemPagerLogic.ADD_THRESHOLD
                    Column(
                        Modifier.align(Alignment.CenterEnd).padding(end = Space.s),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("＋", style = AppTheme.type.title, color = if (ready) c.accent else c.inkFaint)
                        Text(
                            if (ready) "離すと追加" else "右へ引く",
                            style = AppTheme.type.caption,
                            color = if (ready) c.accent else c.inkFaint,
                        )
                    }
                }
            }

            // 下部: ‹ i / N ›(最後の品目では › が ＋ になる)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextAction(
                    "‹",
                    onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                    enabled = page > 0,
                    color = c.inkSoft,
                )
                Text("${(page + 1).coerceAtMost(count)} / $count", style = AppTheme.type.caption, color = c.inkSoft, modifier = Modifier.testTag("pageCounter"))
                if (ItemPagerLogic.trailingIsAdd(page, count)) {
                    TextAction("＋ 追加", onClick = { scope.launch { addAndGo() } })
                } else {
                    TextAction("›", onClick = { scope.launch { pagerState.animateScrollToPage(page + 1) } }, color = c.inkSoft)
                }
            }

            saveError?.let { Text(it, style = AppTheme.type.caption, color = c.danger) }
            InkButton(
                text = if (saving) "保存中…" else "保存",
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = { save() },
            )
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

    confirmDeleteId?.let { id ->
        val target = latestExpense(id)
        if (target != null) {
            val others = drafts.any { it.id != id && isDirty(it) }
            ConfirmSheet(
                title = "品目を削除",
                message = "「${target.name.ifBlank { "品名なし" }}」を消します。支出の金額は変わらず、差額は支出のカテゴリに入ります。" +
                    (if (others) "\n未保存の変更は破棄されます。" else ""),
                confirmText = "削除",
                onConfirm = {
                    confirmDeleteId = null
                    onDeleteItem(target)
                    onDismiss()
                },
                onDismiss = { confirmDeleteId = null },
            )
        } else {
            confirmDeleteId = null
        }
    }
}

/** 1品目のページ。上から: 品名と金額(同じ行)・金額の注記・カテゴリ・「詳細」(メモ・削除)。 */
@Composable
private fun ItemPage(
    draft: ItemDraft,
    categories: List<String>,
    /** 金額を直せない理由(null なら直せる)。 */
    amountLock: String?,
    /** 不明のときに出す、支出の金額から計算された金額。 */
    computedAmount: String?,
    unknownToggle: UnknownToggle,
    detailOpen: Boolean,
    canDelete: Boolean,
    errors: PageErrors?,
    onChange: (ItemDraft) -> Unit,
    onToggleDetail: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = AppTheme.colors
    val amountReadOnly = amountLock != null || draft.unknown
    val amountShown = if (draft.unknown) computedAmount.orEmpty() else draft.amountText
    val notes = listOfNotNull(
        amountLock,
        if (draft.unknown) "不明: 金額は支出の金額から計算した値です" else null,
        if (unknownToggle == UnknownToggle.AfterSave) "「不明」は、保存したあとで選べます" else null,
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        // 1段目(主): 品名と金額を同じ行に。金額の横に「不明」
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Label("品名")
                BareField(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    placeholder = "品名",
                    singleLine = true,
                    underline = true,
                )
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.width(AMOUNT_WIDTH)) {
                Label("金額(円)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BareField(
                        value = amountShown,
                        onValueChange = {
                            onChange(draft.copy(amountText = it.filter { ch -> ch in '0'..'9' }.take(ItemPagerLogic.MAX_AMOUNT_DIGITS)))
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = AppTheme.type.numeral.copy(textAlign = TextAlign.End),
                        color = if (amountReadOnly) c.inkSoft else c.ink,
                        placeholder = "0",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        underline = true,
                        readOnly = amountReadOnly,
                    )
                    if (unknownToggle == UnknownToggle.Enabled) {
                        Spacer(Modifier.width(Space.xs))
                        Pill(
                            text = "不明",
                            selected = draft.unknown,
                            onClick = { onChange(draft.copy(unknown = !draft.unknown)) },
                        )
                    }
                }
            }
        }
        errors?.amount?.let { Text(it, style = AppTheme.type.caption, color = c.danger) }
        notes.forEach { Text(it, style = AppTheme.type.caption, color = c.inkFaint) }

        // 2段目: カテゴリ
        Label("カテゴリ")
        Pills(
            options = categories,
            selected = draft.category,
            onSelect = { onChange(draft.copy(category = it)) },
            label = { it },
        )
        errors?.category?.let { Text(it, style = AppTheme.type.caption, color = c.danger) }

        // 折りたたみ: 詳細(メモ・削除)。普段は変えない情報なので、主の欄とは分けておく
        TextAction(if (detailOpen) "詳細 ▴" else "詳細 ▾", onClick = onToggleDetail, color = c.inkSoft)
        if (detailOpen) {
            Label("メモ")
            BareField(
                value = draft.memo,
                onValueChange = { onChange(draft.copy(memo = it)) },
                placeholder = "メモ",
                underline = true,
            )
            if (canDelete) {
                TextAction("この品目を削除", onClick = onDelete, color = c.danger, modifier = Modifier.padding(top = Space.s))
            } else if (draft.id != null) {
                Text(
                    "品目は1つ以上必要です。支出ごと消すときは、支出の画面から削除してください。",
                    style = AppTheme.type.caption,
                    color = c.inkFaint,
                )
            }
        }
    }
}

private const val MESSAGE_MISSING = "品目が見つかりません。画面を開き直してください。"
