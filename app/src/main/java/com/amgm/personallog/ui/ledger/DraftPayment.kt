package com.amgm.personallog.ui.ledger

import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.model.TotalSource
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
     * 入力を検証し、手入力の支払い(レシートの表の下書き)と品目を作る。保存は ReceiptRepository.save に任せる。
     * 家計簿の支出は、その保存のなかで支払いから作られる(支出を単独では作らない)。
     * 品目は空欄の行を捨てて1件以上、金額は1円以上の整数。合計は1円以上。店名(memo)は任意。
     * 手入力なので imagePath と rawJson は空、totalSource は MANUAL(ManualReceipt と同じ作り方)。
     */
    fun build(
        dateText: String,
        timeText: String,
        totalText: String,
        memo: String,
        drafts: List<DraftItem>,
        now: Long,
    ): DraftPaymentResult {
        val errors = ArrayList<String>()
        val base = LedgerLogic.validate(dateText, timeText, totalText, "-")
        base.dateError?.let { errors += it }
        base.timeError?.let { errors += it }
        base.amountError?.let { errors += "合計: $it" }
        val rows = drafts.filterNot { it.isBlank }
        if (rows.isEmpty()) errors += "品目を1つ以上入力してください"
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
        val date = base.date!!
        val time = base.time!!
        val receipt = Receipt(
            purchasedAt = ReceiptMapper.purchasedAt(date, time),
            storeName = memo.trim(),
            totalAmount = base.amount!!,
            imagePath = "",
            rawJson = "",
            status = ReceiptStatus.DRAFT,
            createdAt = now,
            totalSource = TotalSource.MANUAL,
        )
        val items = rows.mapIndexed { i, r ->
            ReceiptItem(
                receiptId = 0,
                name = r.name.trim(),
                amount = amounts[i],
                category = r.category,
                memo = r.memo.trim(),
            )
        }
        return DraftPaymentResult(emptyList(), receipt, items)
    }
}
