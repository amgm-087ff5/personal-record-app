package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.CategoryTotal
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import java.time.YearMonth

/** 家計簿まわりの純粋ロジック(JVMでテスト可能)。 */
object LedgerCalc {
    /** "2026-10" -> ("2026-10-01", "2026-10-31")。DAOの BETWEEN 用。 */
    fun monthRange(yearMonth: String): Pair<String, String> {
        val ym = YearMonth.parse(yearMonth)
        return ym.atDay(1).toString() to ym.atEndOfMonth().toString()
    }

    /** 支出リストをカテゴリごとに合計し、金額の多い順に返す。 */
    fun totalsByCategory(expenses: List<Expense>): List<CategoryTotal> =
        expenses.groupBy { it.category }
            .map { (cat, list) -> CategoryTotal(cat, list.sumOf { it.amount.toLong() }) }
            .sortedByDescending { it.total }

    /**
     * レシート確定時の Expense 生成。家計簿に入るのは「自分の分」(品目の税込価格 × 負担割合)。
     * perItem=true なら品目ごと(0円の行は作らない)、false(または品目なし)なら合計1行。
     * 日付は purchasedAt の先頭10文字(yyyy-MM-dd)。
     */
    fun expensesFromReceipt(
        receipt: Receipt,
        items: List<ReceiptItem>,
        perItem: Boolean,
        now: Long,
    ): List<Expense> {
        val date = receipt.purchasedAt.take(10)
        if (perItem && items.isNotEmpty()) {
            val own = TaxCalc.ownYenPerItem(items)
            return items.mapIndexedNotNull { i, it ->
                if (own[i] == 0L) {
                    null
                } else {
                    Expense(
                        date = date,
                        amount = own[i].toInt(),
                        category = it.category,
                        memo = listOf(receipt.storeName, it.name).filter { s -> s.isNotBlank() }.joinToString(" "),
                        receiptId = receipt.id,
                        createdAt = now,
                    )
                }
            }
        }
        val category = items.maxByOrNull { it.amount }?.category ?: Categories.UNCATEGORIZED
        val amount = if (items.isEmpty()) receipt.totalAmount.toLong() else TaxCalc.ownYen(items)
        return listOf(
            Expense(
                date = date,
                amount = amount.toInt(),
                category = category,
                memo = receipt.storeName,
                receiptId = receipt.id,
                createdAt = now,
            ),
        )
    }
}
