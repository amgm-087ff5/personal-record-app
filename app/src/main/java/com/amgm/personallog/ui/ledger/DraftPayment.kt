package com.amgm.personallog.ui.ledger

import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.domain.TaxCalc
import com.amgm.personallog.receipt.ReceiptMapper

/** 手入力の品目つき支払いの、入力途中の1品目。数値は文字列のまま。 */
data class DraftItem(
    val name: String = "",
    val amount: String = "",
    val category: String = Categories.UNCATEGORIZED,
    val memo: String = "",
) {
    /** 何も入力していない行(保存時に捨てる)。カテゴリは見ない。 */
    val isBlank: Boolean get() = name.isBlank() && amount.isBlank() && memo.isBlank()
}

/** 手入力の品目つき支払いの検証結果。エラーが無ければ receipt と items が入る。 */
data class DraftPaymentResult(
    val errors: List<String>,
    val receipt: Receipt? = null,
    val items: List<ReceiptItem> = emptyList(),
    /** 参加人数(任意)。空欄は null。保存時に receipts.headcount に入れる。 */
    val headcount: Int? = null,
) {
    val isValid: Boolean get() = errors.isEmpty() && receipt != null
}

object DraftPayment {
    /** 空欄でない品目の金額(円)の合計。数字として読めない行は 0 として数える。 */
    fun itemsSum(items: List<DraftItem>): Long =
        items.filterNot { it.isBlank }.sumOf { it.amount.trim().toLongOrNull() ?: 0L }

    /** 合計欄に入れる値の文字列。品目の合計が1円以上のときだけ。 */
    fun sumText(items: List<DraftItem>): String? = itemsSum(items).takeIf { it > 0 }?.toString()

    /**
     * 入力を検証し、手入力の支払い(レシートの表)と品目を作る。保存は ReceiptRepository の save(下書き)か saveAndConfirm(確定)に任せる。
     * 家計簿の支出は、その保存のなかで支払いから作られる(支出を単独では作らない)。
     * 確定の判定: 合計か品目があれば確定(CONFIRMED)、どちらも無ければ下書き(DRAFT)。
     *  - 合計は入力された値(1円以上の整数)。合計が空で品目があるときは、品目の合計(TaxCalc.paidYen)を入れる。
     *  - 確定の検証(ReceiptRepository.confirm)は合計を必須にしているので、確定には必ず合計が入る。
     * 日付と時刻は必須。品目は空欄の行を捨て、金額は1円以上の整数。店名(memo)は任意。
     * 手入力なので imagePath と rawJson は空、totalSource は MANUAL(ManualReceipt と同じ作り方)。
     */
    fun build(
        dateText: String,
        timeText: String,
        totalText: String,
        memo: String,
        drafts: List<DraftItem>,
        now: Long,
        headcountText: String = "",
    ): DraftPaymentResult {
        val errors = ArrayList<String>()
        val headcount = LedgerLogic.parseHeadcount(headcountText)
        headcount.error?.let { errors += "人数: $it" }
        // 日付と時刻だけ LedgerLogic の検証を使う。合計は下で見る(LedgerLogic は合計が空だと落ちるので、合計には検証用の固定値を渡す)
        val dateTime = LedgerLogic.validate(dateText, timeText, "1", "-")
        dateTime.dateError?.let { errors += it }
        dateTime.timeError?.let { errors += it }
        val typed = totalText.trim()
        val typedTotal: Int? = if (typed.isEmpty()) {
            null
        } else {
            val n = typed.toIntOrNull()
            when {
                !typed.all { c -> c in '0'..'9' } -> errors += "合計: 金額は正の整数で入力してください"
                n == null -> errors += "合計: 金額が大きすぎます"
                n <= 0 -> errors += "合計: 金額は1円以上にしてください"
            }
            n?.takeIf { it > 0 }
        }
        val rows = drafts.filterNot { it.isBlank }
        val amounts = rows.mapIndexed { i, r ->
            val t = r.amount.trim()
            val n = t.takeIf { s -> s.isNotEmpty() && s.all { c -> c in '0'..'9' } }?.toIntOrNull()
            if (n == null || n <= 0) {
                errors += "品目${i + 1}の金額は1円以上の整数で入力してください"
                0
            } else {
                n
            }
        }
        if (errors.isNotEmpty()) return DraftPaymentResult(errors)
        val items = rows.mapIndexed { i, r ->
            ReceiptItem(
                receiptId = 0,
                name = r.name.trim(),
                amount = amounts[i],
                category = r.category,
                memo = r.memo.trim(),
            )
        }
        val itemsYen = TaxCalc.paidYen(items)
        if (itemsYen > Int.MAX_VALUE) return DraftPaymentResult(listOf("品目の合計が大きすぎます"))
        // 合計: 入力があればそれ。空で品目があれば品目の合計。どちらも無ければ未入力(下書き)
        val total: Int? = typedTotal ?: if (items.isEmpty()) null else itemsYen.toInt()
        val receipt = Receipt(
            purchasedAt = ReceiptMapper.purchasedAt(dateTime.date!!, dateTime.time!!),
            storeName = memo.trim(),
            totalAmount = total,
            imagePath = "",
            rawJson = "",
            status = if (total != null) ReceiptStatus.CONFIRMED else ReceiptStatus.DRAFT,
            createdAt = now,
            totalSource = TotalSource.MANUAL,
        )
        return DraftPaymentResult(emptyList(), receipt, items, headcount = headcount.value)
    }
}
