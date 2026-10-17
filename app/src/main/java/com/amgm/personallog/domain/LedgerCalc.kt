package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.CategoryTotal
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** 家計簿まわりの純粋ロジック(JVMでテスト可能)。 */
object LedgerCalc {
    /** "2026-10" -> ("2026-10-01", "2026-10-31")。DAOの BETWEEN 用。 */
    fun monthRange(yearMonth: String): Pair<String, String> {
        val ym = YearMonth.parse(yearMonth)
        return ym.atDay(1).toString() to ym.atEndOfMonth().toString()
    }

    /**
     * "yyyy-MM-dd" または "yyyy-MM-dd HH:mm" を (date, time) に分ける。
     * 時刻が無ければ time は空文字("")のまま返す。00:00 を自動で補完しない。
     * 表示や時間別の集計では空を 0 時として扱う(LedgerChart.hourOf)。
     */
    fun splitPurchasedAt(purchasedAt: String): Pair<String, String> {
        val date = purchasedAt.take(10)
        val time = if (purchasedAt.length >= 16) purchasedAt.substring(11, 16) else ""
        return date to time
    }

    /** 作成時刻(epoch ミリ秒)を、現地時刻の "HH:mm" にする。時刻が不明な過去データの初期値。 */
    fun timeOfMillis(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
    }

    /** 支出リストをカテゴリごとに合計し、金額の多い順に返す。 */
    fun totalsByCategory(expenses: List<Expense>): List<CategoryTotal> =
        expenses.groupBy { it.category }
            .map { (cat, list) -> CategoryTotal(cat, list.sumOf { it.amount.toLong() }) }
            .sortedByDescending { it.total }

    /** レシート1枚ぶんの支払いグループ。items は品目ごとの支出(groupId は未設定)。 */
    data class ReceiptGroup(val group: ExpenseGroup, val items: List<Expense>)

    /**
     * 複数品目のレシートを1つの支払いグループにする。
     * 支払い金額(group.amount)はレシートの確定額(receipt.totalAmount)を正とし、品目は品目ごとの自分の払う分で持つ。
     * 立て替え・品目なし・品目がすべて0円のときは null(従来どおり単独の支出にする)。
     */
    fun groupFromReceipt(receipt: Receipt, items: List<ReceiptItem>, now: Long): ReceiptGroup? {
        if (receipt.isAdvance || items.isEmpty()) return null
        val (date, time) = splitPurchasedAt(receipt.purchasedAt)
        val paid = TaxCalc.paidYenPerItem(items)
        val children = items.mapIndexedNotNull { i, it ->
            if (paid[i] == 0L) {
                null
            } else {
                Expense(
                    date = date,
                    time = time,
                    amount = paid[i].toInt(),
                    category = it.category,
                    memo = listOf(it.name, it.memo).filter { s -> s.isNotBlank() }.joinToString(" "),
                    receiptId = receipt.id,
                    createdAt = now,
                )
            }
        }
        if (children.isEmpty()) return null
        val group = ExpenseGroup(
            date = date,
            time = time,
            amount = requireNotNull(receipt.totalAmount) { "合計が未入力のレシートは家計簿にできません(id=${receipt.id})" },
            category = items.maxByOrNull { it.amount }?.category ?: Categories.UNCATEGORIZED,
            memo = receipt.storeName,
            receiptId = receipt.id,
            createdAt = now,
        )
        return ReceiptGroup(group, children)
    }

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
        val (date, time) = splitPurchasedAt(receipt.purchasedAt)
        val category = items.maxByOrNull { it.amount }?.category ?: Categories.UNCATEGORIZED
        val paidTotal = if (items.isEmpty()) {
            requireNotNull(receipt.totalAmount) { "合計が未入力のレシートは家計簿にできません(id=${receipt.id})" }.toLong()
        } else {
            TaxCalc.paidYen(items)
        }

        if (!receipt.isAdvance && perItem && items.isNotEmpty()) {
            val paid = TaxCalc.paidYenPerItem(items)
            return items.mapIndexedNotNull { i, it ->
                if (paid[i] == 0L) {
                    null
                } else {
                    Expense(
                        date = date,
                        time = time,
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
                time = time,
                amount = amount.toInt(),
                category = category,
                memo = receipt.storeName,
                receiptId = receipt.id,
                createdAt = now,
            ),
        )
    }
}
