package com.amgm.personallog.domain

import com.amgm.personallog.data.model.ExpenseItem
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.ui.receipt.ItemForm
import com.amgm.personallog.ui.receipt.carryOverItemIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewFixesPlanTest {
    private fun payment(total: Int = 700) = Payment(
        id = 1, purchasedAt = "2026-10-05 12:30", storeName = "店", totalAmount = total,
        imagePath = "", rawJson = "{}", createdAt = 0,
    )

    private fun item(id: Long, name: String, amount: Int, category: String = "食費") =
        PaymentItem(id = id, paymentId = 1, name = name, amount = amount, category = category)

    private fun form(key: Long, id: Long, name: String, amount: Int, rate: Int = 10, incl: Boolean = true) = ItemForm(
        key = key, id = id, name = name, memo = "", amount = amount.toString(), category = "食費",
        taxRate = rate.toString(), priceIncludesTax = incl,
    )

    // ---- 0円の品目 ----

    @Test
    fun zeroYenItem_withEditedAmount_isKept_otherwiseDeleted() {
        val milk = item(11, "牛乳", 200)
        val soap = item(12, "洗剤", 500)
        val first = ConfirmPlanner.plan(payment(), listOf(milk, soap), emptyList(), emptyList(), 1) as ConfirmPlan.Rebuild
        val g = first.group!!.copy(id = 5)
        val children = first.children.mapIndexed { i, e -> e.copy(id = 100L + i, groupId = 5) }
        // 洗剤を0円にした(支払い側)。家計簿で洗剤の金額を直していれば残す
        val soapZero = soap.copy(amount = 0)
        val keepEdited = listOf(children[0], children[1].copy(amount = 480, amountEdited = true))
        val p1 = ConfirmPlanner.plan(payment(), listOf(milk, soapZero), listOf(g), keepEdited, 2) as ConfirmPlan.UpdateGroup
        assertTrue(p1.deleteIds.isEmpty())
        val p2 = ConfirmPlanner.plan(payment(), listOf(milk, soapZero), listOf(g), children, 2) as ConfirmPlan.UpdateGroup
        assertEquals(listOf(101L), p2.deleteIds)
    }

    @Test
    fun updateGroup_childrenFollowTheGroupDateTime() {
        val milk = item(11, "牛乳", 200)
        val first = ConfirmPlanner.plan(payment(), listOf(milk), emptyList(), emptyList(), 1) as ConfirmPlan.Rebuild
        // 家計簿でグループの日時を翌月に直した(timeEdited)
        val g = first.group!!.copy(id = 5, date = "2026-11-01", time = "09:00", timeEdited = true)
        val child = first.children[0].copy(id = 100, groupId = 5)
        val p = ConfirmPlanner.plan(payment(), listOf(milk), listOf(g), listOf(child), 2) as ConfirmPlan.UpdateGroup
        assertEquals("2026-11-01", p.group.date)
        assertEquals("2026-11-01", p.updates.single().date)
        assertEquals("09:00", p.updates.single().time)
    }

    // ---- 失われる件数 ----

    @Test
    fun lostEditedCount_countsEditedRowsOfRemovedItems() {
        val edited = ExpenseItem(id = 1, date = "d", amount = 1, createdAt = 0, groupId = 5, paymentItemId = 11, amountEdited = true)
        val plain = ExpenseItem(id = 2, date = "d", amount = 1, createdAt = 0, groupId = 5, paymentItemId = 12)
        val update = ConfirmPlan.UpdateGroup(Expense(paymentId = 1L, id = 5, date = "d", amount = 1, createdAt = 0), emptyList(), emptyList(), emptyList())
        assertEquals(1, ConfirmPlanner.lostEditedCount(update, emptyList(), emptyList(), listOf(edited, plain)))
        assertEquals(0, ConfirmPlanner.lostEditedCount(update, emptyList(), emptyList(), listOf(plain)))
    }

    @Test
    fun lostEditedCount_rebuildCountsAllEditedExistingRows() {
        val g = Expense(paymentId = 1L, id = 5, date = "d", amount = 1, createdAt = 0, memoEdited = true)
        val e1 = ExpenseItem(id = 1, date = "d", amount = 1, createdAt = 0, groupId = 5, paymentItemId = 11, nameEdited = true)
        val e2 = ExpenseItem(id = 2, date = "d", amount = 1, createdAt = 0, groupId = 5, paymentItemId = 12)
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
    fun carryOver_prefersSameCategory_thenSameContent() {
        val prev = listOf(
            form(1, 11, "牛乳", 200).copy(category = "外食"),
            form(2, 12, "牛乳", 200).copy(category = "食費"),
        )
        // 読み取り直しの1つ目は食費。内容もカテゴリも同じ 12 を優先する
        val next = listOf(form(10, 0, "牛乳", 200).copy(category = "食費"), form(11, 0, "牛乳", 200).copy(category = "日用品"))
        assertEquals(listOf(12L, 11L), carryOverItemIds(prev, next).map { it.id })
    }

    @Test
    fun carryOver_leftoversAreMatchedByPosition_onlyWhenCountsAreEqual() {
        val prev = listOf(form(1, 11, "牛乳", 200), form(2, 12, "パン", 100, rate = 8), form(3, 13, "水", 100))
        // 金額・税率・税込区分の読み違い: 内容は一致しないが、残りの数が同じなので位置で引き継ぐ
        val next = listOf(form(10, 0, "牛乳", 210), form(11, 0, "パン", 100, rate = 10), form(12, 0, "水", 100, incl = false))
        assertEquals(listOf(11L, 12L, 13L), carryOverItemIds(prev, next).map { it.id })
        // 残りの数が違うなら引き継がない
        val next2 = listOf(form(10, 0, "牛乳", 210), form(11, 0, "パン", 100, rate = 10))
        assertEquals(listOf(0L, 0L), carryOverItemIds(prev, next2).map { it.id })
    }

    @Test
    fun rebuild_keepsDeletedItems_hiddenAndOutOfChildren() {
        val milk = item(11, "牛乳", 200)
        val soap = item(12, "洗剤", 500)
        val first = ConfirmPlanner.plan(payment(), listOf(milk, soap), emptyList(), emptyList(), 1) as ConfirmPlan.Rebuild
        val g = first.group!!.copy(id = 5)
        val children = first.children.mapIndexed { i, e -> e.copy(id = 100L + i, groupId = 5) }
        val deleted = children[1].copy(deletedByUser = true)
        // 立て替えにした(v14〜 立て替えも支出+品目なので作り直さない)。消した品目は消したまま、自分の分は品目の比で配る
        val toAdvance = ConfirmPlanner.plan(
            payment().copy(isAdvance = true, ownAmount = 300), listOf(milk, soap), listOf(g), listOf(children[0], deleted), 2,
        ) as ConfirmPlan.UpdateGroup
        assertEquals(300, toAdvance.group.amount)
        assertEquals(listOf(86, 214), toAdvance.updates.map { it.amount })
        assertEquals(listOf(false, true), toAdvance.updates.map { it.deletedByUser })
        assertTrue(toAdvance.deleteIds.isEmpty())
        // 消した品目は「家計簿で直した」件数に入らない
        assertEquals(0, ConfirmPlanner.lostEditedCount(toAdvance, listOf(g), listOf(children[0], deleted), emptyList()))
        // 自分の分が0円の間に非表示で置いた行(groupId なし)から、支出へ作り直す。消した品目の行は children に入らない
        val back = ConfirmPlanner.plan(payment(), listOf(milk, soap), emptyList(), listOf(children[0].copy(groupId = null), deleted.copy(groupId = null)), 3) as ConfirmPlan.Rebuild
        assertEquals(listOf(11L), back.children.map { it.paymentItemId })
        assertEquals(listOf(12L), back.keepDeleted.map { it.paymentItemId })
    }

    @Test
    fun carryOver_ignoresWhitespaceInName_andNewRowsOfPrevious() {
        val prev = listOf(form(1, 0, "牛乳", 200), form(2, 12, "洗剤", 500))
        val next = listOf(form(10, 0, " 牛乳 ", 200), form(11, 0, "洗剤 ", 500))
        assertEquals(listOf(0L, 12L), carryOverItemIds(prev, next).map { it.id })
    }
}
