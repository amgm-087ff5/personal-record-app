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
     * レシート確定時の Expense 生成。家計簿に入るのは「自分の払う分」。
     *  - 立て替えでない: 全額が自分の分。perItem=true で品目があれば1個ずつの行、それ以外は合計1行。
     *  - 立て替え: レシート全体の自分の分(receipt.ownAmount)を合計1行。未入力なら支払総額の全額。
     */
    fun expensesFromReceipt(
        receipt: Receipt,
        items: List<ReceiptItem>,
        perItem: Boolean,
        now: Long,
    ): List<Expense> {
        val date = receipt.purchasedAt.take(10)
        val category = items.maxByOrNull { it.amount }?.category ?: Categories.UNCATEGORIZED
        val paidTotal = if (items.isEmpty()) receipt.totalAmount.toLong() else TaxCalc.paidYen(items)

        if (!receipt.isAdvance && perItem && items.isNotEmpty()) {
            val paid = TaxCalc.paidYenPerItem(items)
            return items.mapIndexedNotNull { i, it ->
                if (paid[i] == 0L) {
                    null
                } else {
                    Expense(
                        date = date,
                        amount = paid[i].toInt(),
                        category = it.category,
                        memo = listOf(receipt.storeName, it.name, it.memo).filter { s -> s.isNotBlank() }.joinToString(" "),
                        receiptId = receipt.id,
                        createdAt = now,
                    )
                }
            }
        }
        val amount = if (receipt.isAdvance) receipt.ownAmount?.toLong() ?: paidTotal else paidTotal
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
