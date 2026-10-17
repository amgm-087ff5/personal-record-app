package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.ui.receipt.ItemForm
import com.amgm.personallog.ui.receipt.carryOverItemIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewFixesPlanTest {
    private fun receipt(total: Int = 700) = Receipt(
        id = 1, purchasedAt = "2026-10-05 12:30", storeName = "店", totalAmount = total,
        imagePath = "", rawJson = "{}", createdAt = 0,
    )

    private fun item(id: Long, name: String, amount: Int, category: String = "食費") =
        ReceiptItem(id = id, receiptId = 1, name = name, amount = amount, category = category)

    private fun form(key: Long, id: Long, name: String, amount: Int, rate: Int = 10, incl: Boolean = true) = ItemForm(
        key = key, id = id, name = name, memo = "", amount = amount.toString(), category = "食費",
        taxRate = rate.toString(), priceIncludesTax = incl,
    )

    // ---- 0円の品目 ----

    @Test
    fun zeroYenItem_withEditedAmount_isKept_otherwiseDeleted() {
        val milk = item(11, "牛乳", 200)
        val soap = item(12, "洗剤", 500)
        val first = ConfirmPlanner.plan(receipt(), listOf(milk, soap), emptyList(), emptyList(), true, 1) as ConfirmPlan.Rebuild
        val g = first.group!!.copy(id = 5)
        val children = first.children.mapIndexed { i, e -> e.copy(id = 100L + i, groupId = 5) }
        // 洗剤を0円にした(レシート側)。家計簿で洗剤の金額を直していれば残す
        val soapZero = soap.copy(amount = 0)
        val keepEdited = listOf(children[0], children[1].copy(amount = 480, amountEdited = true))
        val p1 = ConfirmPlanner.plan(receipt(), listOf(milk, soapZero), listOf(g), keepEdited, true, 2) as ConfirmPlan.UpdateGroup
        assertTrue(p1.deleteIds.isEmpty())
        val p2 = ConfirmPlanner.plan(receipt(), listOf(milk, soapZero), listOf(g), children, true, 2) as ConfirmPlan.UpdateGroup
        assertEquals(listOf(101L), p2.deleteIds)
    }

    @Test
    fun updateGroup_childrenFollowTheGroupDateTime() {
        val milk = item(11, "牛乳", 200)
        val first = ConfirmPlanner.plan(receipt(), listOf(milk), emptyList(), emptyList(), true, 1) as ConfirmPlan.Rebuild
        // 家計簿でグループの日時を翌月に直した(timeEdited)
        val g = first.group!!.copy(id = 5, date = "2026-11-01", time = "09:00", timeEdited = true)
        val child = first.children[0].copy(id = 100, groupId = 5)
        val p = ConfirmPlanner.plan(receipt(), listOf(milk), listOf(g), listOf(child), true, 2) as ConfirmPlan.UpdateGroup
        assertEquals("2026-11-01", p.group.date)
        assertEquals("2026-11-01", p.updates.single().date)
        assertEquals("09:00", p.updates.single().time)
    }

    // ---- 失われる件数 ----

    @Test
    fun lostEditedCount_countsEditedRowsOfRemovedItems() {
        val edited = Expense(id = 1, date = "d", amount = 1, createdAt = 0, groupId = 5, receiptItemId = 11, amountEdited = true)
        val plain = Expense(id = 2, date = "d", amount = 1, createdAt = 0, groupId = 5, receiptItemId = 12)
        val update = ConfirmPlan.UpdateGroup(ExpenseGroup(id = 5, date = "d", amount = 1, createdAt = 0), emptyList(), emptyList(), emptyList())
        assertEquals(1, ConfirmPlanner.lostEditedCount(update, emptyList(), emptyList(), listOf(edited, plain)))
        assertEquals(0, ConfirmPlanner.lostEditedCount(update, emptyList(), emptyList(), listOf(plain)))
    }

    @Test
    fun lostEditedCount_rebuildCountsAllEditedExistingRows() {
        val g = ExpenseGroup(id = 5, date = "d", amount = 1, createdAt = 0, memoEdited = true)
        val e1 = Expense(id = 1, date = "d", amount = 1, createdAt = 0, groupId = 5, receiptItemId = 11, nameEdited = true)
        val e2 = Expense(id = 2, date = "d", amount = 1, createdAt = 0, groupId = 5, receiptItemId = 12)
        val rebuild = ConfirmPlan.Rebuild(null, emptyList())
        assertEquals(2, ConfirmPlanner.lostEditedCount(rebuild, listOf(g), listOf(e1, e2), emptyList()))
    }

    // ---- 読み取り直しでの id の引き継ぎ ----

    @Test
    fun carryOver_matchesSameContentOneToOneInOrder() {
        val prev = listOf(form(1, 11, "牛乳", 200), form(2, 12, "牛乳", 200), form(3, 13, "洗剤", 500))
        val next = listOf(form(10, 0, "牛乳", 200), form(11, 0, "洗剤", 500), form(12, 0, "牛乳", 200), form(13, 0, "牛乳", 200))
        val out = carryOverItemIds(prev, next)
        assertEquals(listOf(11L, 13L, 12L, 0L), out.map { it.id }) // 3つ目の牛乳は前に対応が無いので新規
    }

    @Test
    fun carryOver_doesNotMatchWhenAmountTaxOrNameDiffer() {
        val prev = listOf(form(1, 11, "牛乳", 200), form(2, 12, "パン", 100, rate = 8), form(3, 13, "水", 100))
        val next = listOf(form(10, 0, "牛乳", 210), form(11, 0, "パン", 100, rate = 10), form(12, 0, "水", 100, incl = false))
        assertEquals(listOf(0L, 0L, 0L), carryOverItemIds(prev, next).map { it.id })
    }

    @Test
    fun carryOver_ignoresWhitespaceInName_andNewRowsOfPrevious() {
        val prev = listOf(form(1, 0, "牛乳", 200), form(2, 12, "洗剤", 500))
        val next = listOf(form(10, 0, " 牛乳 ", 200), form(11, 0, "洗剤 ", 500))
        assertEquals(listOf(0L, 12L), carryOverItemIds(prev, next).map { it.id })
    }
}
