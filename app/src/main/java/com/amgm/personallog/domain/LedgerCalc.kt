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
     * 立て替えでないレシートは全額が自分の分(ownAmount は無視する)。
     * perItem=true なら1個ずつの行(自分の分が0円の行は作らない)、false(または品目なし)なら合計1行。
     */
    fun expensesFromReceipt(
        receipt: Receipt,
        items: List<ReceiptItem>,
        perItem: Boolean,
        now: Long,
    ): List<Expense> {
        val date = receipt.purchasedAt.take(10)
        val effective = if (receipt.isAdvance) items else items.map { it.copy(ownAmount = null) }
        if (perItem && effective.isNotEmpty()) {
            val own = TaxCalc.ownYenPerItem(effective)
            return effective.mapIndexedNotNull { i, it ->
                if (own[i] == 0L) {
                    null
                } else {
                    Expense(
                        date = date,
                        amount = own[i].toInt(),
                        category = it.category,
                        memo = listOf(receipt.storeName, it.name, it.memo).filter { s -> s.isNotBlank() }.joinToString(" "),
                        receiptId = receipt.id,
                        createdAt = now,
                    )
                }
            }
        }
        val category = effective.maxByOrNull { it.amount }?.category ?: Categories.UNCATEGORIZED
        val amount = if (effective.isEmpty()) receipt.totalAmount.toLong() else TaxCalc.ownYen(effective)
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
