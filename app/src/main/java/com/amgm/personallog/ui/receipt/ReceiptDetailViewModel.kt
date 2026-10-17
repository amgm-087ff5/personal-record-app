package com.amgm.personallog.ui.receipt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.data.repository.ReceiptRepository
import com.amgm.personallog.domain.PaymentLink
import com.amgm.personallog.domain.TaxBreakdown
import com.amgm.personallog.domain.TaxCalc
import com.amgm.personallog.receipt.ReceiptCalc
import com.amgm.personallog.receipt.ReceiptDedup
import com.amgm.personallog.receipt.ReceiptImageStore
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
)

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
)

private fun ItemForm.groupKey() = GroupKey(name.trim(), amount.trim(), taxRate.trim(), priceIncludesTax, category)

/** 同じ内容の行を1つの表示単位にまとめる。元の並び順を保つ。 */
internal fun groupItemForms(items: List<ItemForm>): List<ItemGroup> =
    items.groupBy { it.groupKey() }.values.map { units -> ItemGroup(units.first(), units) }

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

/** 重複の相手(警告の表示と「重複ではない」の保存に使う)。 */
data class DuplicateRef(val id: Long, val summary: String)

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
    /** 原本画像のハッシュ。保存時に引き継ぐ(フォームからは変えない)。 */
    val imageHash: String? = null,
    /** 重複の可能性がある先行のレシート(「重複ではない」の組は除く)。警告カードに相手ごとに出す。 */
    val possibleDuplicates: List<DuplicateRef> = emptyList(),
    /** 確定時に重複の候補の確定済みレシートが見つかった場合、その相手(確認ダイアログ用)。空なら出さない。 */
    val duplicateWarning: List<DuplicateRef> = emptyList(),
    /** 確定しようとしたが合計が未入力のとき true(「合計が未入力です」の確認用)。 */
    val missingTotalPrompt: Boolean = false,
    /** 再確定で、家計簿の行が作り直される(家計簿で直した内容が消える)ときの確認用。 */
    val rebuildWarning: Boolean = false,
    /** このレシートに紐づく家計簿の支払い(グループか単独の支出)。無ければ null。 */
    val paymentLink: PaymentLink? = null,
) {
    private val models: List<ReceiptItem> get() = items.map { it.toItemModel() }

    /** 税込の支払総額(品目ごとの税込価格の合計、円は切り捨て)。 */
    val itemsSum: Long get() = TaxCalc.paidYen(models)

    /** 自分の払う分(円)。立て替えでなければ支払総額と同じ。立て替えで未入力なら支払総額の全額。 */
    val ownTotal: Long
        get() = if (isAdvance) ownAmount.toLongOrNull() ?: itemsSum else itemsSum

    /** 貰う分(立て替えのときだけ意味がある): 支払総額 - 自分の払う分。 */
    val theirTotal: Long get() = itemsSum - ownTotal

    /** 合計欄 - 税込支払総額。一致、または合計欄が未入力なら null。 */
    val mismatch: Long?
        get() {
            val t = total.toIntOrNull() ?: return null
            val diff = t.toLong() - itemsSum
            return if (diff == 0L) null else diff
        }

    /** 合計欄が未入力(数字として読めない)か。 */
    val totalMissing: Boolean get() = total.toIntOrNull() == null

    /** 税率ごとの税抜・税額の内訳(最上部のダッシュボード用)。 */
    val taxBreakdown: TaxBreakdown get() = TaxCalc.breakdownByRate(models)

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
) : ViewModel() {

    private val receiptId: Long = savedStateHandle.get<Long>("id") ?: 0L
    private var nextKey = 1L

    /** 読み込んだレシート。フォームに無い列(回転・トリミングなど)を保存で失わないよう、これを土台にする。 */
    private var loadedReceipt: Receipt? = null

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
            val notDup = ReceiptDedup.notDuplicateSet(repository.notDuplicates())
            val earlier = ReceiptDedup.earlierDuplicates(r, repository.allReceipts(), notDup)
            _state.value = DetailUiState(
                loading = false,
                imagePath = r.imagePath,
                rawJson = r.rawJson,
                createdAt = r.createdAt,
                imageHash = r.imageHash,
                possibleDuplicates = earlier.map { DuplicateRef(it.id, ReceiptDedup.summary(it)) },
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
            )
            repository.observePaymentLink(receiptId).collect { link ->
                _state.update { it.copy(paymentLink = link) }
            }
        }
    }

    private fun newKey(): Long = nextKey++

    private fun splitPurchasedAt(s: String): Pair<String, String> =
        s.take(10) to s.drop(10).trim()

    fun setDate(v: String) = _state.update { it.copy(date = v) }
    fun setTime(v: String) = _state.update { it.copy(time = v) }
    fun setStore(v: String) = _state.update { it.copy(store = v) }
    /** 合計欄を手で編集した。値があれば manual。空にしたら出所も unknown に戻す。 */
    fun setTotal(v: String) = _state.update {
        val t = v.filter { c -> c.isDigit() || c == '-' }
        it.copy(total = t, totalSource = if (t.toIntOrNull() == null) TotalSource.UNKNOWN else TotalSource.MANUAL)
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

    /** 品目の計算合計(税込の支払合計)を合計に入れる。出所は items。 */
    fun useItemsSumAsTotal() = _state.update { it.copy(total = it.itemsSum.toString(), totalSource = TotalSource.ITEMS) }

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
            val bad = s.items.indexOfFirst { it.amount.toIntOrNull() == null }
            if (bad >= 0) {
                _state.update { it.copy(error = "品目${bad + 1}の金額が入力されていません。") }
                return null
            }
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
        )
        return receipt to items
    }

    /** 下書きとして保存(確定済みレシートでは使わない)。 */
    fun saveDraft() {
        if (_state.value.busy) return
        val built = buildFromForm(strict = false) ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.save(built.first, built.second)
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
                refs.forEach { repository.markNotDuplicate(receiptId, it.id) }
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
                _state.update { s -> s.copy(possibleDuplicates = s.possibleDuplicates.filterNot { it.id == otherId }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "保存に失敗しました: ${e.message.orEmpty()}") }
            }
        }
    }

    private fun startConfirm(checkDuplicate: Boolean, rebuildAccepted: Boolean = false) {
        if (_state.value.busy) return
        val built = buildFromForm(strict = true) ?: return
        if (built.first.totalAmount == null) {
            _state.update { it.copy(missingTotalPrompt = true) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                if (checkDuplicate) {
                    val notDup = ReceiptDedup.notDuplicateSet(repository.notDuplicates())
                    val confirmed = repository.allReceipts().filter { it.status == ReceiptStatus.CONFIRMED }
                    val dups = ReceiptDedup.sameContent(built.first, confirmed, notDup)
                    if (dups.isNotEmpty()) {
                        _state.update {
                            it.copy(busy = false, duplicateWarning = dups.map { d -> DuplicateRef(d.id, ReceiptDedup.summary(d)) })
                        }
                        return@launch
                    }
                }
                // 再確定で家計簿の行が作り直される(立て替えの切り替え・古い形式)なら、先に知らせる
                if (!rebuildAccepted && repository.confirmWouldRebuild(built.first, built.second)) {
                    _state.update { it.copy(busy = false, rebuildWarning = true) }
                    return@launch
                }
                repository.save(built.first, built.second)
                repository.confirm(receiptId, perItem = true)
                _state.update { it.copy(busy = false, confirmed = true, finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "確定に失敗しました: ${e.message.orEmpty()}") }
            }
        }
    }

    /** 画像をもう一度読み取り、フォームを結果で置き換える。 */
    fun rescan() {
        val s = _state.value
        if (s.busy || s.imagePath.isBlank()) return
        _state.update { it.copy(busy = true, scanning = true, error = null, info = null) }
        viewModelScope.launch {
            val outcome = scanner.scan(s.imagePath)
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
                            items = units.map { it.toForm(newKey()) },
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
        }
    }

    /** レシート削除。画像ファイルも消す。alsoExpenses=true なら家計簿の行も消す(false なら残る)。 */
    fun delete(alsoExpenses: Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val r = repository.getReceipt(receiptId)
                if (r != null) {
                    if (alsoExpenses) repository.deleteExpensesOf(receiptId)
                    repository.delete(r)
                    store.delete(r.imagePath)
                }
                _state.update { it.copy(busy = false, finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = "削除に失敗しました: ${e.message.orEmpty()}") }
            }
        }
    }
}
