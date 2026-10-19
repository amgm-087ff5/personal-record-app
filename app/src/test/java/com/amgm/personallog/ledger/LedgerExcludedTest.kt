package com.amgm.personallog.ledger

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.ui.ledger.LedgerEntry
import com.amgm.personallog.ui.ledger.LedgerLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 割り勘で自分が外れた支払い(0円の行)の抽出と、集計が自分の分だけを数えること。 */
class LedgerExcludedTest {
    private fun receipt(id: Long, purchasedAt: String, total: Int?) = Receipt(
        id = id,
        purchasedAt = purchasedAt,
        storeName = "店$id",
        totalAmount = total,
        imagePath = "",
        rawJson = "",
        status = ReceiptStatus.CONFIRMED,
        createdAt = id,
    )

    @Test
    fun excludedReceipts_onlySplitReceiptsWithoutRows_inRange_withTotal() {
        val receipts = listOf(
            receipt(1, "2026-10-05 12:00", 1000), // 割り勘あり・行なし・合計あり → 対象
            receipt(2, "2026-10-06 12:00", 500), // 行あり → 対象外
            receipt(3, "2026-10-07 12:00", 800), // 割り勘なし → 対象外
            receipt(4, "2026-10-08 12:00", null), // 合計なし(下書き) → 対象外
            receipt(5, "2026-09-30 12:00", 300), // 範囲外 → 対象外
        )
        val out = LedgerLogic.excludedReceipts(
            receipts = receipts,
            splitReceiptIds = setOf(1L, 2L, 4L, 5L),
            rowReceiptIds = setOf(2L),
            from = "2026-10-01",
            to = "2026-10-31",
        )
        assertEquals(listOf(1L), out.map { it.receipt.id })
    }

    @Test
    fun excludedEntry_isZeroYen_hasNoCategoryParts_andUsesReceiptDateTime() {
        val e = LedgerLogic.excludedReceipts(
            listOf(receipt(1, "2026-10-05 12:30", 1000)),
            setOf(1L),
            emptySet(),
            "2026-10-01",
            "2026-10-31",
        ).single()
        assertEquals(0L, e.amount)
        assertTrue(e.categoryParts.isEmpty())
        assertEquals("2026-10-05", e.date)
        assertEquals("12:30", e.time)
        assertEquals(1L, e.receiptId)
    }

    @Test
    fun totals_countOnlySelfShare_excludedRowAddsNothing() {
        val single = Expense(id = 1, date = "2026-10-03", amount = 400, category = "食費", createdAt = 1)
        val group = ExpenseGroup(id = 10, date = "2026-10-04", amount = 900, category = "外食", createdAt = 2, receiptId = 20)
        val groupItem = Expense(id = 2, date = "2026-10-04", amount = 900, category = "外食", createdAt = 2, groupId = 10)
        val excluded = LedgerLogic.excludedReceipts(
            listOf(receipt(30, "2026-10-05 12:00", 3000)),
            setOf(30L),
            emptySet(),
            "2026-10-01",
            "2026-10-31",
        )

        val without = LedgerLogic.buildEntries(listOf(single, groupItem), listOf(group))
        val with = LedgerLogic.buildEntries(listOf(single, groupItem), listOf(group), excluded)

        // 自分が外れた支払いは0円。月の合計・カテゴリ別の合計は変わらない
        assertEquals(LedgerLogic.totalsByCategory(without), LedgerLogic.totalsByCategory(with))
        assertEquals(
            LedgerLogic.monthlyTotals(without, listOf("2026-10")),
            LedgerLogic.monthlyTotals(with, listOf("2026-10")),
        )
        assertEquals(1300L, LedgerLogic.monthlyTotals(with, listOf("2026-10")).single().total)
        // ただし一覧には0円の行として出る
        assertEquals(3, with.size)
        assertTrue(with.any { it is LedgerEntry.Excluded && it.amount == 0L })
    }

    @Test
    fun entries_excludedRowIsSortedByDateLikeOthers() {
        val single = Expense(id = 1, date = "2026-10-03", amount = 400, category = "食費", createdAt = 1)
        val excluded = LedgerLogic.excludedReceipts(
            listOf(receipt(30, "2026-10-05 12:00", 3000)),
            setOf(30L),
            emptySet(),
            "2026-10-01",
            "2026-10-31",
        )
        val entries = LedgerLogic.buildEntries(listOf(single), emptyList(), excluded)
        assertTrue(entries.first() is LedgerEntry.Excluded)
        assertEquals("2026-10-05", entries.first().date)
    }
}
