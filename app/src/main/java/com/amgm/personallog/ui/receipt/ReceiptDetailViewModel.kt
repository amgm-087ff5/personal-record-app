package com.amgm.personallog.ui.receipt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.db.ExpenseDao
import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.repository.ReceiptRepository
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
    val total: String = "",
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
    /** 同じ内容の別レシートがある場合、その要約(警告表示用)。 */
    val possibleDuplicate: String? = null,
    /** 確定時に同じ内容の確定済みレシートが見つかった場合、その要約(確認ダイアログ用)。 */
    val duplicateWarning: String? = null,
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
}

@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReceiptRepository,
    private val expenseDao: ExpenseDao,
    private val store: ReceiptImageStore,
    private val scanner: ReceiptScanner,
) : ViewModel() {

    private val receiptId: Long = savedStateHandle.get<Long>("id") ?: 0L
    private var nextKey = 1L

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
            val original = ReceiptDedup.laterDuplicates(repository.allReceipts())[receiptId]
            _state.value = DetailUiState(
                loading = false,
                imagePath = r.imagePath,
                rawJson = r.rawJson,
                createdAt = r.createdAt,
                imageHash = r.imageHash,
                possibleDuplicate = original?.let { ReceiptDedup.summary(it) },
                confirmed = r.status == ReceiptStatus.CONFIRMED,
                isAdvance = r.isAdvance,
                ownAmount = r.ownAmount?.toString().orEmpty(),
                date = d,
                time = t,
                store = r.storeName,
                total = r.totalAmount.toString(),
                items = items.map { it.toForm(newKey()) },
            )
        }
    }

    private fun newKey(): Long = nextKey++

    private fun splitPurchasedAt(s: String): Pair<String, String> =
        s.take(10) to s.drop(10).trim()

    fun setDate(v: String) = _state.update { it.copy(date = v) }
    fun setTime(v: String) = _state.update { it.copy(time = v) }
    fun setStore(v: String) = _state.update { it.copy(store = v) }
    fun setTotal(v: String) = _state.update { it.copy(total = v.filter { c -> c.isDigit() || c == '-' }) }
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
        s.copy(items = s.items + src.copy(key = newKey(), memo = "", picked = false))
    }

    /** 1個だけ削除する。 */
    fun removeUnit(key: Long) = _state.update { s -> s.copy(items = s.items.filterNot { it.key == key }) }

    /** 同じ品目のまとまりごと削除する。 */
    fun removeGroup(keys: Set<Long>) = _state.update { s -> s.copy(items = s.items.filterNot { it.key in keys }) }

    /** 品目合計を total に反映する。 */
    fun useItemsSumAsTotal() = _state.update { it.copy(total = it.itemsSum.toString()) }

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
        val total = s.total.toIntOrNull() ?: TaxCalc.paidYen(items).toInt()
        val receipt = Receipt(
            id = receiptId,
            purchasedAt = ReceiptMapper.purchasedAt(date, time),
            storeName = s.store.trim(),
            totalAmount = total,
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
     * 同じ内容(日付・店名・合計)の確定済みレシートが既にあれば、確認ダイアログを出して止める(既定はキャンセル)。
     */
    fun confirm() = startConfirm(checkDuplicate = true)

    /** 重複確認ダイアログで「追加する」を選んだときの確定。 */
    fun confirmAnyway() {
        _state.update { it.copy(duplicateWarning = null) }
        startConfirm(checkDuplicate = false)
    }

    fun dismissDuplicateWarning() = _state.update { it.copy(duplicateWarning = null) }

    private fun startConfirm(checkDuplicate: Boolean) {
        if (_state.value.busy) return
        val built = buildFromForm(strict = true) ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                if (checkDuplicate) {
                    val key = ReceiptDedup.keyOf(built.first)
                    val dup = if (key == null) {
                        null
                    } else {
                        val confirmed = repository.allReceipts().filter { it.status == ReceiptStatus.CONFIRMED }
                        ReceiptDedup.sameContent(key, confirmed, receiptId).firstOrNull()
                    }
                    if (dup != null) {
                        _state.update { it.copy(busy = false, duplicateWarning = ReceiptDedup.summary(dup)) }
                        return@launch
                    }
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
                            total = (p.total ?: ReceiptCalc.itemsSum(units.map { it.amount }).toInt()).toString(),
                            items = units.map { it.toForm(newKey()) },
                            info = "読み取りました。内容を確認してください。",
                        )
                    }
                    is ScanOutcome.Unparsable -> cur.copy(
                        busy = false,
                        scanning = false,
                        rawJson = outcome.raw,
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
                    if (alsoExpenses) expenseDao.deleteByReceiptId(receiptId)
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
