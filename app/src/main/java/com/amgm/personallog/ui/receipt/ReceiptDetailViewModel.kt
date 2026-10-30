package com.amgm.personallog.ui.receipt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.data.repository.HeadcountSyncChange
import com.amgm.personallog.data.repository.ParticipantSyncService
import com.amgm.personallog.data.repository.ConfirmOutcome
import com.amgm.personallog.data.repository.PaymentConfirmer
import com.amgm.personallog.data.repository.ReceiptRepository
import com.amgm.personallog.data.settings.PaymentReadStore
import com.amgm.personallog.domain.PaymentLink
import com.amgm.personallog.domain.PaymentMerge
import kotlinx.coroutines.flow.first
import com.amgm.personallog.domain.TaxBreakdown
import com.amgm.personallog.domain.TaxCalc
import com.amgm.personallog.domain.UnknownAmounts
import com.amgm.personallog.domain.UnknownItems
import com.amgm.personallog.receipt.ReceiptCalc
import com.amgm.personallog.receipt.ReceiptDedup
import com.amgm.personallog.receipt.ReceiptAdjust
import com.amgm.personallog.receipt.ReceiptImageStore
import com.amgm.personallog.receipt.adjust
import com.amgm.personallog.receipt.ReceiptMapper
import com.amgm.personallog.receipt.ReceiptScanner
import com.amgm.personallog.receipt.ReceiptUnits
import com.amgm.personallog.receipt.ScanOutcome
import com.amgm.personallog.receipt.UnitRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 画面で編集中の1個ぶんの品目。数値は入力文字列のまま持つ。 */
data class ItemForm(
    val key: Long,
    val name: String,
    val memo: String,
    val amount: String,
    val category: String,
    val taxRate: String,
    val priceIncludesTax: Boolean,
    /** ReceiptItem の id。新規の行は 0。保存で id を保ち、家計簿の行との対応を切らないために持つ。 */
    val id: Long = 0L,
    /** 割り勘の補助: この品目を「自分の払う分」の計算に含めるか(画面だけの状態。保存しない)。 */
    val picked: Boolean = false,
    /** 金額が分からない品目(「不明」)。金額は 合計 − 分かっている品目の合計 から求める(UnknownAmounts)。 */
    val unknownAmount: Boolean = false,
) {
    /** 金額が未入力(数字として読めない)で、不明でもない品目。「未入力を全て不明にする」の対象。 */
    val isAmountBlank: Boolean get() = !unknownAmount && amount.trim().toIntOrNull() == null
}

/** 同じ品名・金額・税率・税込/税抜・カテゴリの行をまとめた表示単位(データにグループは持たない)。 */
data class ItemGroup(val head: ItemForm, val units: List<ItemForm>) {
    val keys: Set<Long> get() = units.mapTo(HashSet()) { it.key }
}

private data class GroupKey(
    val name: String,
    val amount: String,
    val taxRate: String,
    val priceIncludesTax: Boolean,
    val category: String,
    val unknownAmount: Boolean,
)

private fun ItemForm.groupKey() = GroupKey(name.trim(), amount.trim(), taxRate.trim(), priceIncludesTax, category, unknownAmount)

/** 同じ内容の行を1つの表示単位にまとめる。元の並び順を保つ。 */
internal fun groupItemForms(items: List<ItemForm>): List<ItemGroup> =
    items.groupBy { it.groupKey() }.values.map { units -> ItemGroup(units.first(), units) }

/**
 * 編集シートを開いた時点の対象(openedKeys)の中だけでグループ化し、key を含むまとまりを返す。
 * 編集中に他の品目と同じ内容になって全体のグループが統合されても、対象は開いた品目のまま保たれる。
 */
internal fun groupItemFormsWithin(items: List<ItemForm>, openedKeys: Set<Long>, key: Long): ItemGroup? =
    groupItemForms(items.filter { it.key in openedKeys }).firstOrNull { key in it.keys }

/** 入力中の1個ぶんを計算用のモデルにする。立て替えでなければ自分の分は無視する(全額自分)。 */
internal fun ItemForm.toItemModel(receiptId: Long = 0L): ReceiptItem = ReceiptItem(
    id = id,
    receiptId = receiptId,
    name = name.trim(),
    amount = amount.toIntOrNull() ?: 0,
    category = category,
    taxRate = TaxCalc.normalizeTaxRate(taxRate.toIntOrNull()),
    priceIncludesTax = priceIncludesTax,
    memo = memo.trim(),
    unknownAmount = unknownAmount,
)

/** 金額の入力: 数字と、先頭の「-」(値引き)だけを通す。 */
private fun String.filterAmount(): String = filterIndexed { i, c -> c.isDigit() || (c == '-' && i == 0) }

private fun ReceiptItem.toForm(key: Long) = ItemForm(
    key = key,
    id = id,
    name = name,
    memo = memo,
    amount = amount.toString(),
    category = category,
    taxRate = taxRate.toString(),
    priceIncludesTax = priceIncludesTax,
    unknownAmount = unknownAmount,
)

private fun UnitRow.toForm(key: Long) = ItemForm(
    key = key,
    name = name,
    memo = memo,
    amount = amount.toString(),
    category = category,
    taxRate = taxRate.toString(),
    priceIncludesTax = priceIncludesTax,
)

/**
 * 読み取り直しで品目を置き換えるとき、前の品目の id を新しい品目に引き継ぐ。
 * 品名・金額・税率・税込/税抜が同じ前の品目を、順番に1対1で対応させる(同じ内容が複数あっても1つずつ)。
 * 対応しなかった前の品目の id は引き継がない(保存でその品目と、その支出が消える)。
 */
internal fun carryOverItemIds(previous: List<ItemForm>, incoming: List<ItemForm>): List<ItemForm> {
    data class Key(val name: String, val amount: String, val taxRate: String, val includesTax: Boolean)
    fun ItemForm.key() = Key(name.trim(), amount.trim(), taxRate.trim(), priceIncludesTax)
    val prevIds = previous.filter { it.id != 0L }
    val usedPrev = HashSet<Long>() // 引き継ぎ済みの前の id
    val result = arrayOfNulls<Long>(incoming.size) // incoming の添字 -> 引き継ぐ id

    // 1段目: 内容(品名・金額・税率・税込区分)とカテゴリが同じものを優先。2段目: 内容だけが同じもの
    fun pass(sameCategory: Boolean) {
        for ((i, f) in incoming.withIndex()) {
            if (result[i] != null) continue
            val hit = prevIds.firstOrNull { p ->
                p.id !in usedPrev && p.key() == f.key() && (!sameCategory || p.category == f.category)
            } ?: continue
            usedPrev.add(hit.id)
            result[i] = hit.id
        }
    }
    pass(sameCategory = true)
    pass(sameCategory = false)

    // 3段目: 残りは位置で。残りの数が前後で同じときだけ(読み違えた行が同じ位置に来たと見なす)。数が違えば引き継がない
    val restPrev = prevIds.filter { it.id !in usedPrev }
    val restIdx = incoming.indices.filter { result[it] == null }
    if (restPrev.isNotEmpty() && restPrev.size == restIdx.size) {
        restIdx.forEachIndexed { n, i -> result[i] = restPrev[n].id }
    }
    return incoming.mapIndexed { i, f -> result[i]?.let { f.copy(id = it) } ?: f }
}

/** 重複の相手(警告の表示と「重複ではない」の保存に使う)。 */
data class DuplicateRef(val id: Long, val summary: String)

/** items から求めた計算結果。sum は分かっている品目(金額不明でない)の税込合計。 */
private class ItemsCalc(val items: List<ItemForm>, val sum: Long, val breakdown: TaxBreakdown)

/** 直近1件だけ覚える。copy() で items の参照が変わらない限り(合計欄や日付の入力など)、再計算しない。 */
private object ItemsCalcCache {
    @Volatile
    private var last: ItemsCalc? = null

    fun of(items: List<ItemForm>): ItemsCalc {
        last?.let { if (it.items === items) return it }
        // 金額不明の品目は、分かっている品目の合計にも税の内訳にも入れない(金額は合計から求める)
        val models = items.map { it.toItemModel() }.filterNot { it.unknownAmount }
        return ItemsCalc(items, TaxCalc.paidYen(models), TaxCalc.breakdownByRate(models)).also { last = it }
    }
}

/**
 * 人数を減らすとき、消す人を選ぶ確認の中身。selected は選んだ人の参加者 id(chooseCount 人ちょうどで決定できる)。
 * labels は参加者 id → 画面に出す名前。
 */
data class HeadcountChoice(
    val target: Int,
    val change: HeadcountSyncChange,
    val labels: Map<Long, String>,
    val selected: Set<Long>,
)

data class DetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val imagePath: String = "",
    val rawJson: String = "",
    val createdAt: Long = 0,
    val confirmed: Boolean = false,
    val date: String = "",
    val time: String = "",
    val store: String = "",
    /** 合計欄の入力値。空文字は未入力(null)。 */
    val total: String = "",
    /** 合計の出所(TotalSource)。合計欄を手で編集したら manual。 */
    val totalSource: String = TotalSource.UNKNOWN,
    /** 読み取り(rawJson)で LLM が読んだ印字の合計。上書きの確認で見せる。読めなければ null。 */
    val printedTotal: Int? = null,
    val items: List<ItemForm> = emptyList(),
    /** 立て替え(自分が払った。他の人の分を含む)かどうか。 */
    val isAdvance: Boolean = false,
    /** 立て替えのとき、レシート全体のうち自分が払う金額(円・税込)。空は支払総額の全額。 */
    val ownAmount: String = "",
    val busy: Boolean = false,
    val scanning: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val finished: Boolean = false,
    /** 確定が済んだときの、この支払いから作られた支出(まとまり)。登録の手順で次の画面を決めるのに使う。無ければ null。 */
    val finishedLink: PaymentLink? = null,
    /** 原本画像のハッシュ。保存時に引き継ぐ(フォームからは変えない)。 */
    val imageHash: String? = null,
    /** 重複の可能性がある先行のレシート(「重複ではない」の組は除く)。警告カードに相手ごとに出す。 */
    val possibleDuplicates: List<DuplicateRef> = emptyList(),
    /** 重複の組(支払いの id。マージ先が先頭)。組に2件以上あるときだけ空でない。警告カードの「マージする」に使う。 */
    val mergeIds: List<Long> = emptyList(),
    /** 確定時に重複の候補の確定済みレシートが見つかった場合、その相手(確認ダイアログ用)。空なら出さない。 */
    val duplicateWarning: List<DuplicateRef> = emptyList(),
    /** 確定しようとしたが合計が未入力のとき true(「合計が未入力です」の確認用)。 */
    val missingTotalPrompt: Boolean = false,
    /** 保存してある画像の補正値(回転・トリミング)。 */
    val adjust: ReceiptAdjust = ReceiptAdjust.NONE,
    /** 再確定で、家計簿の行が作り直される(家計簿で直した内容が消える)ときの確認用。 */
    val rebuildWarning: Boolean = false,
    /** 警告の中身: 家計簿の行が作り直されるか(false なら、品目の削除で一部が消えるだけ)。 */
    val rebuildRebuilds: Boolean = false,
    /** 警告の中身: 家計簿で直した行のうち消える件数。 */
    val lostEditedCount: Int = 0,
    /** 警告の中身: 家計簿で消した品目のうち、消したまま残る件数。 */
    val keptDeletedCount: Int = 0,
    /** このレシートに紐づく家計簿の自分の支出(まとまり)。無ければ null。 */
    val paymentLink: PaymentLink? = null,
    /** 参加人数(整数。空は未入力)。保存で receipts.headcount に入り、割り勘の参加者もそれに合わせる。 */
    val headcount: String = "",
    /** 人数を減らしたとき、消す人を選ぶ確認。null なら出さない。 */
    val headcountChoice: HeadcountChoice? = null,
) {
    /** 手入力の支払い(レシート画像なし)か。画像の表示・補正・再読取は出さない。読み込み後だけ意味がある。 */
    val manualEntry: Boolean get() = !loading && !notFound && imagePath.isBlank()

    /**
     * 税込の支払総額(品目ごとの税込価格の合計、円は切り捨て)。金額不明の品目は含めない(分かっている品目の合計)。
     * items が変わったときだけ計算する。
     */
    val itemsSum: Long get() = ItemsCalcCache.of(items).sum

    /** 金額不明の品目があるか。 */
    val hasUnknownItem: Boolean get() = items.any { it.unknownAmount }

    /** 金額不明の品目に入る金額の合計(合計 − 分かっている品目の合計。負なら0)。不明の品目が無い・合計が未入力なら null。 */
    val unknownTotal: Long?
        get() {
            if (!hasUnknownItem) return null
            val t = total.toIntOrNull() ?: return null
            return (t.toLong() - itemsSum).coerceAtLeast(0L)
        }

    /** 自分の払う分(円)。立て替えでなければ支払総額と同じ。立て替えで未入力なら支払総額の全額。 */
    val ownTotal: Long
        get() = if (isAdvance) ownAmount.toLongOrNull() ?: itemsSum else itemsSum

    /** 貰う分(立て替えのときだけ意味がある): 支払総額 - 自分の払う分。 */
    val theirTotal: Long get() = itemsSum - ownTotal

    /**
     * 合計欄 - 分かっている品目の税込合計。エラーでなければ null(合計欄が未入力でも null)。
     * 金額不明の品目があるときは、差が0以上ならエラーにしない(差は不明の品目に入る)。差が負のときだけエラー。
     */
    val mismatch: Long?
        get() = UnknownAmounts.mismatch(total.toIntOrNull()?.toLong(), itemsSum, hasUnknownItem)

    /** 「未入力を全て不明にする」を出せるか(markBlankItemsUnknown)。合計と品目が合わず、金額が未入力の品目があるとき。 */
    val canMarkBlankAsUnknown: Boolean
        get() = mismatch != null && UnknownItems.canMarkBlankAsUnknown(items) { it.isAmountBlank }

    /**
     * 「差額を不明の品目として足す」で足す金額(addDifferenceAsUnknownItem)。出さないときは null。
     * 金額が未入力の品目・不明の品目が無く、合計 − 品目の合計 が正のとき。
     */
    val differenceForUnknownItem: Long?
        get() = UnknownItems.differenceForUnknownItem(
            total = total.toIntOrNull()?.toLong(),
            knownSum = itemsSum,
            hasBlank = items.any { it.isAmountBlank },
            hasUnknown = hasUnknownItem,
        )

    /** 合計欄が未入力(数字として読めない)か。 */
    val totalMissing: Boolean get() = total.toIntOrNull() == null

    /** 税率ごとの税抜・税額の内訳(最上部のダッシュボード用)。 */
    val taxBreakdown: TaxBreakdown get() = ItemsCalcCache.of(items).breakdown

    /**
     * 「¥N を合計に入れる」ボタンを出すか。品目が1件以上あり、合計欄が空か、品目の計算合計と1円でも違うとき。
     */
    val canUseItemsSum: Boolean get() = items.isNotEmpty() && (totalMissing || mismatch != null)

    /** ボタンを押したとき、印字の合計を上書きするので確認が要るか(合計欄に値があるとき)。 */
    val useItemsSumNeedsConfirm: Boolean get() = !totalMissing
}

@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReceiptRepository,
    private val store: ReceiptImageStore,
    private val scanner: ReceiptScanner,
    private val syncService: ParticipantSyncService,
    private val confirmer: PaymentConfirmer,
    private val readStore: PaymentReadStore,
) : ViewModel() {

    private val receiptId: Long = savedStateHandle.get<Long>("id") ?: 0L
    private var nextKey = 1L

    /** 保存済みの人数(割り勘の参加者と合わせた後の値)。人数が変わったかの判定に使う。 */
    private var storedHeadcount: Int? = null

    /** 人数の確認(消す人の選択)の「決定」の後に続ける保存。キャンセルではこれを捨てる(保存しない)。 */
    private var pendingSave: (() -> Unit)? = null

    /** 読み込んだレシート。フォームに無い列(回転・トリミングなど)を保存で失わないよう、これを土台にする。 */
    private var loadedReceipt: Receipt? = null

    /** 重複の判定に使う、このレシート以外の保存済みレシートと「重複ではない」の組(読み込み時に取得)。 */
    private var otherReceipts: List<Receipt> = emptyList()
    private var notDuplicateSet: MutableSet<Pair<Long, Long>> = HashSet()

    /** 重複の組(支払いの id。マージ先が先頭)。組に入っていなければ空。 */
    private fun mergeIdsOf(r: Receipt): List<Long> =
        PaymentMerge.setContaining(r.id, PaymentMerge.duplicateSets(listOf(r) + otherReceipts, notDuplicateSet))
            ?.map { it.id }.orEmpty()

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val r = repository.getReceipt(receiptId)
            if (r == null) {
                _state.update { it.copy(loading = false, notFound = true) }
                return@launch
            }
            val items = repository.getItems(receiptId)
            val (d, t) = splitPurchasedAt(r.purchasedAt)
            loadedReceipt = r
            storedHeadcount = r.headcount
            notDuplicateSet = HashSet(ReceiptDedup.notDuplicateSet(repository.notDuplicates()))
            otherReceipts = repository.allReceipts().filter { it.id != r.id }
            val earlier = ReceiptDedup.earlierDuplicates(r, otherReceipts, notDuplicateSet)
            _state.value = DetailUiState(
                loading = false,
                imagePath = r.imagePath,
                rawJson = r.rawJson,
                createdAt = r.createdAt,
                imageHash = r.imageHash,
                possibleDuplicates = earlier.map { DuplicateRef(it.id, ReceiptDedup.summary(it)) },
                mergeIds = mergeIdsOf(r),
                confirmed = r.status == ReceiptStatus.CONFIRMED,
                isAdvance = r.isAdvance,
                ownAmount = r.ownAmount?.toString().orEmpty(),
                date = d,
                time = t,
                store = r.storeName,
                // 未入力(null)は空文字。"null" という文字列にしない
                total = r.totalAmount?.toString().orEmpty(),
                totalSource = r.totalSource,
                printedTotal = ReceiptMapper.printedTotal(r.rawJson),
                items = items.map { it.toForm(newKey()) },
                adjust = r.adjust(),
                headcount = r.headcount?.toString().orEmpty(),
            )
            markRead()
            repository.observePaymentLink(receiptId).collect { link ->
                _state.update { it.copy(paymentLink = link) }
            }
        }
    }

    /** 支払いを開いたので既読にする。既読の保存に失敗しても、画面は続ける。 */
    private suspend fun markRead() {
        try {
            readStore.markRead(receiptId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 未読の印が残るだけなので、無視する
        }
    }

    private fun newKey(): Long = nextKey++

    /**
     * 重複の警告を、いまの入力(日付・時刻・合計)で計算し直す。保存済みの「重複ではない」の組も考慮する。
     * 読み込み前(loadedReceipt が null)は何もしない。
     */
    private fun recomputeDuplicates() {
        val base = loadedReceipt ?: return
        _state.update { s ->
            val current = base.copy(
                purchasedAt = ReceiptMapper.purchasedAt(s.date.trim(), s.time.trim()),
                totalAmount = s.total.toIntOrNull(),
            )
            val earlier = ReceiptDedup.earlierDuplicates(current, otherReceipts, notDuplicateSet)
            s.copy(possibleDuplicates = earlier.map { DuplicateRef(it.id, ReceiptDedup.summary(it)) })
        }
    }

    private fun splitPurchasedAt(s: String): Pair<String, String> =
        s.take(10) to s.drop(10).trim()

    fun setDate(v: String) {
        _state.update { it.copy(date = v) }
        recomputeDuplicates()
    }
    fun setTime(v: String) {
        _state.update { it.copy(time = v) }
        recomputeDuplicates()
    }
    fun setStore(v: String) = _state.update { it.copy(store = v) }
    /**
     * 合計欄を手で編集した。出所は manual にする。空にしても manual のまま(画面の警告を
     * 「読み取れませんでした」ではなく「未入力です」にするため。保存では空なら unknown に戻る)。
     */
    fun setTotal(v: String) {
        _state.update { it.copy(total = v.filter { c -> c.isDigit() || c == '-' }, totalSource = TotalSource.MANUAL) }
        recomputeDuplicates()
    }
    /** 人数の入力: 数字だけを通す(3桁まで)。 */
    fun setHeadcount(v: String) = _state.update { it.copy(headcount = v.filter { c -> c.isDigit() }.take(3)) }

    /** 人数の確認で、消す人を1人選ぶ・外す。選べるのは chooseCount 人まで。 */
    fun toggleHeadcountChoice(id: Long) = _state.update { s ->
        val c = s.headcountChoice ?: return@update s
        val selected = when {
            id in c.selected -> c.selected - id
            c.selected.size < c.change.chooseCount -> c.selected + id
            else -> c.selected
        }
        s.copy(headcountChoice = c.copy(selected = selected))
    }

    /** 人数の確認の「決定」: 自動で消す人と選んだ人を消し、人数を保存してから、止めていた保存を続ける。 */
    fun confirmHeadcountChoice() {
        val c = _state.value.headcountChoice ?: return
        if (!c.change.isValidChoice(c.selected)) return
        val next = pendingSave
        pendingSave = null
        _state.update { it.copy(headcountChoice = null, busy = true, error = null) }
        viewModelScope.launch {
            try {
                syncService.apply(receiptId, c.target, c.change.autoRemoveIds + c.selected, addCount = 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "人数に合わせられませんでした: ${e.message.orEmpty()}") }
                return@launch
            }
            storedHeadcount = c.target
            _state.update { it.copy(busy = false) }
            next?.invoke()
        }
    }

    /**
     * 人数の確認を閉じる。「キャンセル」・外側タップ・戻るは、すべてこれだけを行う。
     * 保存はせず、人数の入力欄も参加者も変えない。止めていた保存(pendingSave)は捨てる。
     */
    fun dismissHeadcountChoice() {
        pendingSave = null
        _state.update { it.copy(headcountChoice = null) }
    }

    /**
     * 保存の前に、人数が前の値と変わっていれば割り勘の参加者を合わせる。
     * 消す人を選ぶ必要があれば確認を出し、続き(next)は確認のあとに行う。割り勘が無い支払いは何もせず続ける。
     */
    private fun syncHeadcountThen(next: () -> Unit) {
        val s = _state.value
        if (s.busy || s.headcountChoice != null) return
        val target = s.headcount.trim().toIntOrNull()?.takeIf { it >= 1 }
        // 空・不正・変更なしは何もせず続ける(不正な入力は buildFromForm が出す)
        if (target == null || target == storedHeadcount) {
            next()
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val planned = try {
                syncService.plan(receiptId, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "人数の確認に失敗しました: ${e.message.orEmpty()}") }
                return@launch
            }
            // 割り勘が無い・変更が無い: 人数は続きの保存で書く
            if (planned == null || planned.change.isEmpty) {
                _state.update { it.copy(busy = false) }
                next()
                return@launch
            }
            if (planned.change.needsChoice) {
                pendingSave = next
                _state.update {
                    it.copy(
                        busy = false,
                        headcountChoice = HeadcountChoice(target, planned.change, planned.labels, selected = emptySet()),
                    )
                }
                return@launch
            }
            try {
                syncService.apply(receiptId, target, planned.change.autoRemoveIds, planned.change.add)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "人数に合わせられませんでした: ${e.message.orEmpty()}") }
                return@launch
            }
            storedHeadcount = target
            _state.update { it.copy(busy = false) }
            next()
        }
    }

    fun setAdvance(v: Boolean) = _state.update { it.copy(isAdvance = v) }
    fun setOwnAmount(v: String) = _state.update { it.copy(ownAmount = v.filterAmount()) }

    private fun updateKeys(keys: Set<Long>, f: (ItemForm) -> ItemForm) =
        _state.update { s -> s.copy(items = s.items.map { if (it.key in keys) f(it) else it }) }

    // 同じ品目のまとまり(グループ)への一括変更。グループ内の行は同じ値なので、変更後も同じグループのまま。
    fun setGroupName(keys: Set<Long>, v: String) = updateKeys(keys) { it.copy(name = v) }
    fun setGroupAmount(keys: Set<Long>, v: String) = updateKeys(keys) { it.copy(amount = v.filterAmount()) }
    fun setGroupTaxRate(keys: Set<Long>, v: String) = updateKeys(keys) { it.copy(taxRate = v.filter(Char::isDigit).take(3)) }
    fun toggleGroupTaxIncluded(keys: Set<Long>) = updateKeys(keys) { it.copy(priceIncludesTax = !it.priceIncludesTax) }
    fun setGroupCategory(keys: Set<Long>, v: String) = updateKeys(keys) { it.copy(category = v) }

    // 1個ずつの変更。金額を個別に変えると、その1個だけ別のグループになる。
    fun setUnitMemo(key: Long, v: String) = updateKeys(setOf(key)) { it.copy(memo = v) }
    fun setUnitAmount(key: Long, v: String) = updateKeys(setOf(key)) { it.copy(amount = v.filterAmount()) }

    /**
     * 割り勘の補助: 品目(グループ)を「自分の分に含める」を切り替える。
     * 含めた品目の税込価格の合計を、レシート全体の「自分の払う分」に入れる。
     */
    fun togglePicked(keys: Set<Long>) = _state.update { s ->
        val allPicked = s.items.filter { it.key in keys }.all { it.picked }
        val items = s.items.map { if (it.key in keys) it.copy(picked = !allPicked) else it }
        val picked = items.filter { it.picked }
        val own = if (picked.isEmpty()) {
            ""
        } else {
            TaxCalc.ownYenOfPicked(items.map { it.toItemModel() }, items.map { it.picked }).toString()
        }
        s.copy(items = items, ownAmount = own)
    }

    /** 新しい品目(1個)を末尾に追加する。 */
    fun addItem() = _state.update {
        it.copy(
            items = it.items + ItemForm(
                key = newKey(),
                name = "",
                memo = "",
                amount = "",
                category = Categories.UNCATEGORIZED,
                taxRate = TaxCalc.DEFAULT_TAX_RATE.toString(),
                priceIncludesTax = true,
            ),
        )
    }

    /** 同じ品目をもう1個追加する(名前・金額・税などは同じ。メモと自分の分は空)。 */
    fun addSameItem(key: Long) = _state.update { s ->
        val src = s.items.firstOrNull { it.key == key } ?: return@update s
        s.copy(items = s.items + src.copy(key = newKey(), id = 0L, memo = "", picked = false))
    }

    /** 1個だけ削除する。 */
    fun removeUnit(key: Long) = _state.update { s -> s.copy(items = s.items.filterNot { it.key == key }) }

    /** 同じ品目のまとまりごと削除する。 */
    fun removeGroup(keys: Set<Long>) = _state.update { s -> s.copy(items = s.items.filterNot { it.key in keys }) }

    // ------------------------------------------------------------ 金額不明(段階2の画面から呼ぶ)

    /** 1つの品目(key)の「金額不明」を切り替える。不明にしても入力済みの金額は消さない(不明を外すと戻る)。 */
    fun setUnitUnknown(key: Long, unknown: Boolean) = updateKeys(setOf(key)) { it.copy(unknownAmount = unknown) }

    /** 「未入力を全て不明にする」: 金額が未入力の品目をすべて金額不明にする(画面の入力だけ。保存は確定・下書き保存で行う)。 */
    fun markBlankItemsUnknown() = _state.update { s ->
        s.copy(items = UnknownItems.markBlankAsUnknown(s.items, { it.isAmountBlank }) { it.copy(unknownAmount = true) })
    }

    /**
     * 「差額を不明の品目として足す」: 合計 − 品目の合計 を受け持つ、金額不明の品目を1つ末尾に足す
     * (品名は空・未分類・税込10%。金額欄は空)。足せないとき(differenceForUnknownItem が null)は何もしない。
     */
    fun addDifferenceAsUnknownItem() = _state.update { s ->
        s.copy(
            items = UnknownItems.withDifferenceItem(
                items = s.items,
                total = s.total.toIntOrNull()?.toLong(),
                knownSum = s.itemsSum,
                isBlank = { it.isAmountBlank },
                isUnknown = { it.unknownAmount },
                newUnknownItem = {
                    ItemForm(
                        key = newKey(),
                        name = "",
                        memo = "",
                        amount = "",
                        category = Categories.UNCATEGORIZED,
                        taxRate = TaxCalc.DEFAULT_TAX_RATE.toString(),
                        priceIncludesTax = true,
                        unknownAmount = true,
                    )
                },
            ),
        )
    }

    /** 品目の計算合計(税込の支払合計)を合計に入れる。出所は items。 */
    fun useItemsSumAsTotal() {
        _state.update { it.copy(total = it.itemsSum.toString(), totalSource = TotalSource.ITEMS) }
        recomputeDuplicates()
    }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissInfo() = _state.update { it.copy(info = null) }

    /** フォームを検証して Receipt と品目に変換。不正なら error をセットして null。 */
    private fun buildFromForm(strict: Boolean): Pair<Receipt, List<ReceiptItem>>? {
        val s = _state.value
        val date = s.date.trim()
        if (!ReceiptCalc.isValidDate(date)) {
            _state.update { it.copy(error = "日付は yyyy-MM-dd 形式(例 2026-10-10)で入力してください。") }
            return null
        }
        val time = s.time.trim()
        if (time.isNotEmpty() && !ReceiptCalc.isValidTime(time)) {
            _state.update { it.copy(error = "時刻は HH:mm 形式(例 14:05)で入力するか、空にしてください。") }
            return null
        }
        if (strict) {
            // 金額不明の品目は金額が空でよい(金額は合計から求める)
            val bad = s.items.indexOfFirst { it.isAmountBlank }
            if (bad >= 0) {
                _state.update { it.copy(error = "品目${bad + 1}の金額が入力されていません。") }
                return null
            }
        }
        val headcountText = s.headcount.trim()
        val headcount: Int? = if (headcountText.isEmpty()) null else headcountText.toIntOrNull()?.takeIf { it >= 1 }
        if (headcountText.isNotEmpty() && headcount == null) {
            _state.update { it.copy(error = "人数は1以上の整数で入力するか、空にしてください。") }
            return null
        }
        val badRate = s.items.indexOfFirst { val r = it.taxRate.toIntOrNull(); r == null || r !in TaxCalc.MIN_TAX_RATE..TaxCalc.MAX_TAX_RATE }
        if (badRate >= 0) {
            _state.update { it.copy(error = "品目${badRate + 1}の税率は0〜100の整数で入力してください。") }
            return null
        }
        if (s.isAdvance && s.ownAmount.isNotEmpty() && s.ownAmount.toIntOrNull() == null) {
            _state.update { it.copy(error = "「自分の払う分」は金額(円)で入力してください。") }
            return null
        }
        val items = s.items.map { it.toItemModel(receiptId) }
        val base = loadedReceipt ?: run {
            _state.update { it.copy(error = "レシートを読み込めていないため保存できません。") }
            return null
        }
        // 合計は補完しない。空なら null のまま(下書きは保存できる。確定は startConfirm で止める)
        val total = s.total.toIntOrNull()
        val receipt = base.copy(
            id = receiptId,
            purchasedAt = ReceiptMapper.purchasedAt(date, time),
            storeName = s.store.trim(),
            totalAmount = total,
            totalSource = if (total == null) TotalSource.UNKNOWN else s.totalSource,
            imagePath = s.imagePath,
            rawJson = s.rawJson,
            status = if (s.confirmed) ReceiptStatus.CONFIRMED else ReceiptStatus.DRAFT,
            createdAt = s.createdAt,
            imageHash = s.imageHash,
            isAdvance = s.isAdvance,
            ownAmount = if (s.isAdvance) s.ownAmount.toIntOrNull() else null,
            rotationDegrees = s.adjust.rotationDegrees,
            cropLeft = s.adjust.cropLeft, cropTop = s.adjust.cropTop,
            cropRight = s.adjust.cropRight, cropBottom = s.adjust.cropBottom,
            headcount = headcount,
        )
        return receipt to items
    }

    /** 下書きとして保存(確定済みレシートでは使わない)。 */
    fun saveDraft() {
        if (_state.value.busy) return
        syncHeadcountThen { doSaveDraft() }
    }

    private fun doSaveDraft() {
        if (_state.value.busy) return
        val built = buildFromForm(strict = false) ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.save(built.first, built.second)
                storedHeadcount = built.first.headcount
                _state.update { it.copy(busy = false, info = "下書きを保存しました。") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "保存に失敗しました: ${e.message.orEmpty()}") }
            }
        }
    }

    /**
     * 確定: レシート・品目を保存し、家計簿(Expense)を作り直す。
     * 合計が空なら確認(missingTotalPrompt)を出して止める。
     * 重複の候補(時刻5分以内+同じ合計。「重複ではない」の組は除く)の確定済みレシートが既にあれば、確認を出して止める(既定はキャンセル)。
     */
    fun confirm() = startConfirm(checkDuplicate = true)

    /** 再確定の確認で「作り直して確定」を選んだとき。 */
    fun confirmRebuild() {
        _state.update { it.copy(rebuildWarning = false) }
        startConfirm(checkDuplicate = false, rebuildAccepted = true)
    }

    fun dismissRebuildWarning() = _state.update { it.copy(rebuildWarning = false) }

    /** 重複確認で「追加する」を選んだときの確定。 */
    fun confirmAnyway() {
        _state.update { it.copy(duplicateWarning = emptyList()) }
        startConfirm(checkDuplicate = false)
    }

    /** 重複確認で「重複ではない(今後表示しない)」を選んだとき: 組を保存して確定を続ける。 */
    fun confirmMarkingNotDuplicate() {
        val refs = _state.value.duplicateWarning
        if (refs.isEmpty() || _state.value.busy) return
        _state.update { it.copy(duplicateWarning = emptyList(), busy = true, error = null) }
        viewModelScope.launch {
            try {
                refs.forEach {
                    repository.markNotDuplicate(receiptId, it.id)
                    notDuplicateSet.add(ReceiptDedup.pairOf(receiptId, it.id))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "保存に失敗しました: ${e.message.orEmpty()}") }
                return@launch
            }
            _state.update { it.copy(busy = false) }
            startConfirm(checkDuplicate = false)
        }
    }

    fun dismissDuplicateWarning() = _state.update { it.copy(duplicateWarning = emptyList()) }

    /** 「合計が未入力です」で「品目の合計 ¥N を入れて確定」を選んだとき。 */
    fun confirmWithItemsSum() {
        _state.update { it.copy(missingTotalPrompt = false) }
        if (_state.value.items.isEmpty()) return
        useItemsSumAsTotal()
        startConfirm(checkDuplicate = true)
    }

    fun dismissMissingTotal() = _state.update { it.copy(missingTotalPrompt = false) }

    /** 詳細画面の警告カードで「重複ではない」を押した: 相手との組を保存し、その警告を消す。 */
    fun markNotDuplicate(otherId: Long) {
        viewModelScope.launch {
            try {
                repository.markNotDuplicate(receiptId, otherId)
                notDuplicateSet.add(ReceiptDedup.pairOf(receiptId, otherId))
                val base = loadedReceipt
                _state.update { s ->
                    s.copy(
                        possibleDuplicates = s.possibleDuplicates.filterNot { it.id == otherId },
                        mergeIds = base?.let { mergeIdsOf(it) } ?: s.mergeIds,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "保存に失敗しました: ${e.message.orEmpty()}") }
            }
        }
    }

    private fun startConfirm(checkDuplicate: Boolean, rebuildAccepted: Boolean = false) {
        if (_state.value.busy) return
        syncHeadcountThen { startConfirmNow(checkDuplicate, rebuildAccepted) }
    }

    private fun startConfirmNow(checkDuplicate: Boolean, rebuildAccepted: Boolean) {
        if (_state.value.busy) return
        val built = buildFromForm(strict = true) ?: return
        if (built.first.totalAmount == null) {
            _state.update { it.copy(missingTotalPrompt = true) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            // 確定の判定は PaymentConfirmer(支出の画面と共通)。ここは結果に応じた確認を出すだけ
            when (val outcome = confirmer.confirm(built.first, built.second, checkDuplicate, rebuildAccepted)) {
                ConfirmOutcome.Confirmed -> {
                    storedHeadcount = built.first.headcount
                    // 確定で作られた支出(まとまり)を、finished と一緒に出す(確定の直後は DB の観測より先に来るため、ここで読む)
                    val link = repository.observePaymentLink(receiptId).first()
                    _state.update { it.copy(busy = false, confirmed = true, finished = true, finishedLink = link) }
                }
                is ConfirmOutcome.NeedsDuplicateDecision -> _state.update {
                    it.copy(busy = false, duplicateWarning = outcome.duplicates.map { d -> DuplicateRef(d.id, ReceiptDedup.summary(d)) })
                }
                is ConfirmOutcome.MissingTotal -> _state.update { it.copy(busy = false, missingTotalPrompt = true) }
                is ConfirmOutcome.NeedsRebuildDecision -> _state.update {
                    it.copy(
                        busy = false,
                        rebuildWarning = true,
                        rebuildRebuilds = outcome.impact.rebuild,
                        lostEditedCount = outcome.impact.lostEdited,
                        keptDeletedCount = outcome.impact.keptDeleted,
                    )
                }
                is ConfirmOutcome.Error -> _state.update { it.copy(busy = false, error = outcome.message) }
            }
        }
    }

    /** 補正値(回転・トリミング)の列だけを保存する。フォームの入力内容は保存しない。 */
    fun saveAdjust(adjust: ReceiptAdjust) {
        persistAdjust(adjust, rescanAfter = false)
    }

    /** 補正値を保存してから、その補正で読み取り直す(フォームは結果で置き換わる)。 */
    fun saveAdjustAndRescan(adjust: ReceiptAdjust) {
        persistAdjust(adjust, rescanAfter = true)
    }

    private fun persistAdjust(adjust: ReceiptAdjust, rescanAfter: Boolean) {
        if (_state.value.busy) {
            _state.update { it.copy(error = BUSY_ADJUST_MESSAGE) }
            return
        }
        val clean = adjust.sanitized()
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.setAdjust(receiptId, clean)
                loadedReceipt = loadedReceipt?.copy(
                    rotationDegrees = clean.rotationDegrees,
                    cropLeft = clean.cropLeft, cropTop = clean.cropTop,
                    cropRight = clean.cropRight, cropBottom = clean.cropBottom,
                )
                _state.update {
                    it.copy(busy = false, adjust = clean, info = if (rescanAfter) null else "向き・範囲を保存しました。")
                }
                // 画像の無い(手入力の)支払いは読み取らない
                if (rescanAfter && _state.value.imagePath.isNotBlank()) rescan()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "保存に失敗しました: ${e.message.orEmpty()}") }
            }
        }
    }

    /** 画像をもう一度読み取り、フォームを結果で置き換える。 */
    fun rescan() {
        val s = _state.value
        if (s.busy || s.imagePath.isBlank()) return
        _state.update { it.copy(busy = true, scanning = true, error = null, info = null) }
        viewModelScope.launch {
            val outcome = scanner.scan(s.imagePath, s.adjust)
            _state.update { cur ->
                when (outcome) {
                    is ScanOutcome.Parsed -> {
                        val p = outcome.receipt
                        val units = p.items.flatMap { ReceiptUnits.fromParsed(it) }
                        cur.copy(
                            busy = false,
                            scanning = false,
                            rawJson = outcome.raw,
                            date = p.date ?: cur.date.ifBlank { LocalDate.now().toString() },
                            time = p.time.orEmpty(),
                            store = p.store,
                            // 合計は読めたときだけ入れる。読めなければ空(品目の合計で補完しない)
                            total = p.total?.toString().orEmpty(),
                            totalSource = if (p.total != null) TotalSource.PRINTED else TotalSource.UNKNOWN,
                            printedTotal = p.total,
                            // 前の品目の id を引き継ぐ(家計簿の行との対応と、家計簿で直した内容を保つ)
                            items = carryOverItemIds(cur.items, units.map { it.toForm(newKey()) }),
                            info = "読み取りました。内容を確認してください。",
                        )
                    }
                    is ScanOutcome.Unparsable -> cur.copy(
                        busy = false,
                        scanning = false,
                        rawJson = outcome.raw,
                        printedTotal = null,
                        error = "読み取り結果を解釈できませんでした。手入力で修正してください。",
                    )
                    is ScanOutcome.Failed -> cur.copy(busy = false, scanning = false, error = outcome.message)
                }
            }
            recomputeDuplicates()
        }
    }

    /** 支払いを削除する。支払いから作った支出・品目とレシート画像も消す(親の無い支出を残さない)。 */
    fun delete() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.deletePayment(receiptId)?.let { store.delete(it.imagePath) }
                _state.update { it.copy(busy = false, finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "削除に失敗しました: ${e.message.orEmpty()}") }
            }
        }
    }
}

/** 処理中に向き・範囲を保存しようとしたときの案内(黙って無視しない)。 */
internal const val BUSY_ADJUST_MESSAGE = "処理中のため向き・範囲を保存できませんでした。終わってからもう一度保存してください。"
