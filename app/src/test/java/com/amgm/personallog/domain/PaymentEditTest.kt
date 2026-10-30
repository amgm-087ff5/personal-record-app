package com.amgm.personallog.domain

import com.amgm.personallog.data.model.ExpenseItem
import com.amgm.personallog.data.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentEditTest {
    private val e = ExpenseItem(
        id = 1, date = "2026-10-05", time = "12:30", amount = 200, category = "食費",
        name = "牛乳", memo = "特売", paymentId = 1, paymentItemId = 11, createdAt = 0,
    )
    private val g = Expense(id = 2, date = "2026-10-05", time = "12:30", amount = 700, category = "日用品", memo = "スーパー", paymentId = 1, createdAt = 0)

    @Test
    fun expenseItem_noChange_setsNoFlags() {
        val r = PaymentEdit.editExpenseItem(e, e.date, e.time, e.amount, e.category, e.name, e.memo)
        assertFalse(r.amountEdited || r.categoryEdited || r.nameEdited || r.memoEdited || r.timeEdited)
    }

    @Test
    fun expenseItem_onlyChangedFieldsGetFlags() {
        val r = PaymentEdit.editExpenseItem(e, e.date, e.time, 250, e.category, "牛乳 1L", e.memo)
        assertTrue(r.amountEdited)
        assertTrue(r.nameEdited)
        assertFalse(r.categoryEdited)
        assertFalse(r.memoEdited)
        assertFalse(r.timeEdited)
        assertEquals(250, r.amount)
        assertEquals("牛乳 1L", r.name)
    }

    @Test
    fun expenseItem_dateOrTimeChangeSetsTimeEdited_andFlagsAreSticky() {
        val a = PaymentEdit.editExpenseItem(e, "2026-10-05", "13:00", 200, "食費", "牛乳", "特売")
        assertTrue(a.timeEdited)
        // 元の値に戻しても下ろさない
        val back = PaymentEdit.editExpenseItem(a, "2026-10-05", "12:30", 200, "食費", "牛乳", "特売")
        assertTrue(back.timeEdited)
    }

    @Test
    fun expenseItem_memoIsTrimmedBeforeComparing() {
        val r = PaymentEdit.editExpenseItem(e, e.date, e.time, e.amount, e.category, e.name, "  特売 ")
        assertFalse(r.memoEdited)
        assertEquals("特売", r.memo)
    }

    @Test
    fun group_flagsFollowChangedFields() {
        val r = PaymentEdit.editGroup(g, g.date, "13:00", 720, "食費", "スーパー")
        assertTrue(r.amountEdited)
        assertTrue(r.categoryEdited)
        assertTrue(r.timeEdited)
        assertFalse(r.memoEdited)
        val m = PaymentEdit.editGroup(g, g.date, g.time, g.amount, g.category, "行きつけ")
        assertTrue(m.memoEdited)
        assertFalse(m.amountEdited)
    }
}
