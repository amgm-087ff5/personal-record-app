package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnknownAmountsTest {
    private fun item(id: Long, amount: Int, unknown: Boolean = false, included: Boolean = true) = ReceiptItem(
        id = id, receiptId = 1, name = "品$id", amount = amount, priceIncludesTax = included, unknownAmount = unknown,
    )

    // ---- 不明の金額

    @Test
    fun splitEqually_spreadsTheRemainderFromTheFront() {
        assertEquals(listOf(334L, 333L, 333L), UnknownAmounts.splitEqually(1000, 3))
        assertEquals(listOf(0L, 0L), UnknownAmounts.splitEqually(-5, 2))
        assertTrue(UnknownAmounts.splitEqually(100, 0).isEmpty())
    }

    @Test
    fun paidYenPerItem_unknownItemsShareTotalMinusKnown() {
        // 分かっている品目: 200 + 税抜 100(税込 110)。合計 1000 -> 不明 2品目で 690 を 345 + 345
        val items = listOf(item(1, 200), item(2, 0, unknown = true), item(3, 100, included = false), item(4, 999, unknown = true))
        assertEquals(listOf(200L, 345L, 110L, 345L), UnknownAmounts.paidYenPerItem(items, 1000))
        assertEquals(1000L, UnknownAmounts.itemsYen(items, 1000))
        assertEquals(310L, UnknownAmounts.knownPaidYen(items))
        // 合計が未入力なら、不明の品目は0円
        assertEquals(listOf(200L, 0L, 110L, 0L), UnknownAmounts.paidYenPerItem(items, null))
        // 不明が無ければ TaxCalc と同じ
        val plain = listOf(item(1, 200), item(3, 100, included = false))
        assertEquals(TaxCalc.paidYenPerItem(plain), UnknownAmounts.paidYenPerItem(plain, 1000))
    }

    @Test
    fun mismatch_unknownWithNonNegativeRemainderIsNotAnError() {
        // 不明あり: 差が0以上ならエラーにしない、負ならエラー
        assertNull(UnknownAmounts.mismatch(1000L, 700L, hasUnknown = true))
        assertNull(UnknownAmounts.mismatch(700L, 700L, hasUnknown = true))
        assertEquals(-100L, UnknownAmounts.mismatch(600L, 700L, hasUnknown = true))
        // 不明なし: 差が0でなければエラー
        assertEquals(300L, UnknownAmounts.mismatch(1000L, 700L, hasUnknown = false))
        assertNull(UnknownAmounts.mismatch(700L, 700L, hasUnknown = false))
        // 合計が未入力ならエラーにしない(別の警告)
        assertNull(UnknownAmounts.mismatch(null, 700L, hasUnknown = false))
        // ReceiptItem に当てたもの
        assertNull(UnknownAmounts.mismatch(1000, listOf(item(1, 700), item(2, 0, unknown = true))))
        assertEquals(300L, UnknownAmounts.mismatch(1000, listOf(item(1, 700))))
    }

    // ---- 「未入力を全て不明にする」「差額を不明の品目として足す」

    @Test
    fun markBlankAsUnknown_marksOnlyBlankItems() {
        val items = listOf(item(1, 200), item(2, 0), item(3, 0, unknown = true))
        val out = UnknownAmounts.markBlankAsUnknown(items)
        assertEquals(listOf(false, true, true), out.map { it.unknownAmount })
        assertEquals(items.map { it.id }, out.map { it.id })
        // 汎用版(画面の入力中の品目): 空欄の文字列を不明に
        data class Row(val amount: String, val unknown: Boolean = false)
        val rows = listOf(Row("100"), Row(""), Row(" "))
        val marked = UnknownItems.markBlankAsUnknown(rows, { it.amount.isBlank() && !it.unknown }) { it.copy(unknown = true) }
        assertEquals(listOf(false, true, true), marked.map { it.unknown })
        assertTrue(UnknownItems.canMarkBlankAsUnknown(rows) { it.amount.isBlank() })
        assertFalse(UnknownItems.canMarkBlankAsUnknown(listOf(Row("1"))) { it.amount.isBlank() })
    }

    @Test
    fun differenceItem_isOfferedOnlyWithoutBlankOrUnknown_andPositiveDifference() {
        assertEquals(300L, UnknownItems.differenceForUnknownItem(1000, 700, hasBlank = false, hasUnknown = false))
        assertNull(UnknownItems.differenceForUnknownItem(1000, 700, hasBlank = true, hasUnknown = false))
        assertNull(UnknownItems.differenceForUnknownItem(1000, 700, hasBlank = false, hasUnknown = true))
        assertNull(UnknownItems.differenceForUnknownItem(700, 700, hasBlank = false, hasUnknown = false))
        assertNull(UnknownItems.differenceForUnknownItem(600, 700, hasBlank = false, hasUnknown = false))
        assertNull(UnknownItems.differenceForUnknownItem(null, 700, hasBlank = false, hasUnknown = false))

        val items = listOf(item(1, 200), item(2, 500))
        assertEquals(300L, UnknownAmounts.differenceForUnknownItem(1000, items))
        val added = UnknownAmounts.withDifferenceItem(1, 1000, items)
        assertEquals(3, added.size)
        assertTrue(added.last().unknownAmount)
        assertEquals(0L, added.last().id)
        // 足したあとは差が不明の品目に入り、不一致ではなくなる
        assertNull(UnknownAmounts.mismatch(1000, added))
        assertEquals(listOf(200L, 500L, 300L), UnknownAmounts.paidYenPerItem(added, 1000))
        // 足せないときはそのまま
        assertEquals(items, UnknownAmounts.withDifferenceItem(1, 700, items))
    }

    // ---- 支出の品目(ExpenseItems)

    private fun group(amount: Int) = ExpenseGroup(receiptId = 1L, id = 5, date = "2026-10-05", time = "12:00", amount = amount, category = "食費", memo = "店", createdAt = 0)
    private fun row(id: Long, amount: Int, unknown: Boolean = false, deleted: Boolean = false, itemId: Long? = id) = Expense(
        id = id, date = "2026-10-01", amount = amount, category = "日用品", memo = "x", groupId = 5, receiptItemId = itemId,
        createdAt = 0, unknownAmount = unknown, deletedByUser = deleted,
    )

    @Test
    fun normalize_oneItemFollowsTheGroupAmount() {
        assertEquals(listOf(900), ExpenseItems.normalize(group(900), listOf(row(1, 200))).map { it.amount })
        // 品目の無い支払いの1品目は、カテゴリ・メモ・日時も支出に従う(品目で直した欄は残す)
        val blank = row(1, 200, itemId = null)
        val n = ExpenseItems.normalize(group(900), listOf(blank)).single()
        assertEquals(900, n.amount)
        assertEquals("食費", n.category)
        assertEquals("店", n.memo)
        assertEquals("2026-10-05", n.date)
        val edited = ExpenseItems.normalize(group(900), listOf(blank.copy(categoryEdited = true))).single()
        assertEquals("日用品", edited.category)
    }

    @Test
    fun normalize_unknownItemsTakeTheRemainder_deletedAreIgnored() {
        val items = listOf(row(1, 200), row(2, 0, unknown = true), row(3, 100, deleted = true), row(4, 0, unknown = true))
        assertEquals(listOf(200, 400, 100, 400), ExpenseItems.normalize(group(1000), items).map { it.amount })
        // 消した品目を除いて1つしか残らなくても、行が2つ以上なら残った品目の金額は変えない(消した品目の分は差額)
        val two = listOf(row(1, 200), row(3, 500, deleted = true))
        assertEquals(listOf(200, 500), ExpenseItems.normalize(group(700), two).map { it.amount })
        assertEquals(500L, ExpenseItems.mismatch(group(700), two))
    }

    @Test
    fun mismatch_ofExpenseItems_followsTheSameRule() {
        assertNull(ExpenseItems.mismatch(group(700), listOf(row(1, 200))))
        assertEquals(300L, ExpenseItems.mismatch(group(1000), listOf(row(1, 200), row(2, 500))))
        assertNull(ExpenseItems.mismatch(group(1000), listOf(row(1, 200), row(2, 0, unknown = true))))
        assertEquals(-100L, ExpenseItems.mismatch(group(600), listOf(row(1, 200), row(2, 500), row(3, 0, unknown = true))))
    }

    @Test
    fun allocateByRatio_isLargestRemainder_andSumsExactly() {
        assertEquals(listOf(86L, 214L), ExpenseItems.allocateByRatio(300, listOf(200, 500)))
        assertEquals(listOf(34L, 33L, 33L), ExpenseItems.allocateByRatio(100, listOf(1, 1, 1)))
        // 値引き(負)は重み0
        assertEquals(listOf(100L, 0L), ExpenseItems.allocateByRatio(100, listOf(300, -50)))
        assertNull(ExpenseItems.allocateByRatio(100, listOf(0, -1)))
        val r = ExpenseItems.allocateByRatio(1001, listOf(333, 333, 334))!!
        assertEquals(1001L, r.sum())
    }
}
