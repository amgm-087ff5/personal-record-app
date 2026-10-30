package com.amgm.personallog.backup

import com.amgm.personallog.data.model.ExpenseItem
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.data.model.PaymentStatus
import com.amgm.personallog.domain.LedgerCalc
import com.amgm.personallog.domain.SingleExpenseItemConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 形式9以前の単独の支出(groupId なし)の取り込み: まとまり+品名が空の1品目に変換されることを確かめる(DB の v14 の移行と同じ規則)。 */
class BackupSingleExpenseItemTest {
    private val payment = BackupPayment(5, "2026-10-06 13:00", "食堂", 1200, "", "", PaymentStatus.CONFIRMED, 1)

    private fun data(vararg expenseItems: BackupExpenseItem, groups: List<BackupExpense> = emptyList()) = BackupData(
        formatVersion = 9, dbVersion = 13, exportedAt = 1, payments = listOf(payment), expenseItems = expenseItems.toList(), expenses = groups,
    )

    private val single = BackupExpenseItem(
        id = 3, date = "2026-10-06", time = "13:00", amount = 1200, category = "外食", memo = "自分で", paymentId = 5,
        createdAt = 2000, name = "ランチ", categoryEdited = true, memoEdited = true, timeEdited = true, uid = "e-3",
    )

    @Test
    fun single_becomesGroupWithOneBlankItem_sameValuesAsTheDbMigration() {
        val group = BackupExpense(id = 7, date = "2026-10-01", amount = 10, category = "食費", createdAt = 1, paymentId = 5)
        val out = convertSingleExpenseItemsToGroups(data(single, groups = listOf(group)))

        val newGroup = out.expenses.single { it.id != 7L }
        assertTrue("既存のまとまりの id と重ならない", newGroup.id > 7L)
        assertEquals("2026-10-06", newGroup.date)
        assertEquals("13:00", newGroup.time)
        assertEquals(1200, newGroup.amount)
        assertEquals("外食", newGroup.category)
        assertEquals("自分で", newGroup.memo)
        assertEquals(5L, newGroup.paymentId)
        assertEquals(2000L, newGroup.createdAt)
        assertTrue(newGroup.categoryEdited && newGroup.memoEdited && newGroup.timeEdited)
        assertFalse(newGroup.amountEdited)
        // uid は元の行の uid から決まる(端末の移行と同じ値。マージで二重にならない)
        assertEquals(SingleExpenseItemConversion.groupUid("e-3"), newGroup.uid)

        val item = out.expenseItems.single()
        assertEquals(newGroup.id, item.groupId)
        assertEquals("", item.name) // 品名は捨てる
        assertEquals(1200, item.amount)
        assertEquals("e-3", item.uid)
        assertFalse(item.categoryEdited || item.memoEdited || item.timeEdited || item.nameEdited || item.amountEdited)
    }

    @Test
    fun conversion_matchesTheDomainRuleUsedByTheMigration() {
        val e = ExpenseItem(
            id = 3, date = "2026-10-06", time = "13:00", amount = 1200, category = "外食", memo = "自分で", paymentId = 5,
            createdAt = 2000, name = "ランチ", categoryEdited = true, memoEdited = true, timeEdited = true, uid = "e-3",
        )
        val g = SingleExpenseItemConversion.groupOf(e)
        val out = convertSingleExpenseItemsToGroups(data(single)).expenses.single()
        assertEquals(g.copy(id = out.id), out.toEntity())
        val item = SingleExpenseItemConversion.itemOf(e, out.id)
        assertEquals(item, convertSingleExpenseItemsToGroups(data(single)).expenseItems.single().toEntity())
    }

    @Test
    fun deletedRowOfTheSamePayment_isAttachedToTheNewGroup_stillDeleted() {
        val deleted = single.copy(id = 4, deletedByUser = true, uid = "e-4", paymentItemId = 9)
        val out = convertSingleExpenseItemsToGroups(data(single, deleted))
        val gid = out.expenses.single().id
        assertEquals(listOf(gid, gid), out.expenseItems.map { it.groupId })
        assertTrue(out.expenseItems.single { it.id == 4L }.deletedByUser)
        assertEquals("ランチ", out.expenseItems.single { it.id == 4L }.name) // 消した行は変えない
    }

    @Test
    fun deletedOrGroupedRows_areNotConverted() {
        val deleted = single.copy(id = 4, deletedByUser = true, uid = "e-4")
        val grouped = single.copy(id = 5, groupId = 1, uid = "e-5")
        val d = data(deleted, grouped, groups = listOf(BackupExpense(id = 1, date = "2026-10-06", amount = 1200, category = "外食", createdAt = 1)))
        assertEquals(d, convertSingleExpenseItemsToGroups(d))
    }

    @Test
    fun blankTime_isFilledWithCreatedTime_onBothGroupAndItem() {
        val out = convertSingleExpenseItemsToGroups(data(single.copy(time = "")))
        val expected = LedgerCalc.timeOfMillis(2000)
        assertEquals(expected, out.expenses.single().time)
        assertEquals(expected, out.expenseItems.single().time)
    }

    @Test
    fun orphanThenSingle_everyExpenseItemEndsUpInAGroupWithAPayment() {
        // 形式4の、支払いに属さない単独の支出: 手入力の支払いを作り、さらにまとまり+1品目にする
        val orphan = BackupExpenseItem(id = 21, date = "2026-10-03", time = "09:00", amount = 500, category = "その他", createdAt = 1, name = "x")
        val old = BackupData(formatVersion = 4, dbVersion = 11, exportedAt = 1, expenseItems = listOf(orphan))
        val out = convertSingleExpenseItemsToGroups(convertOrphanExpenseItemsToManualPayments(old).data)
        val e = out.expenseItems.single()
        val g = out.expenses.single()
        assertEquals(g.id, e.groupId)
        assertEquals(e.paymentId, g.paymentId)
        assertEquals(500, g.amount)
        assertEquals("", e.name)
        assertTrue(out.expenseItems.none { it.groupId == null })
    }

    @Test
    fun groupUid_isStablePerSourceUid_andRandomWhenBlank() {
        assertEquals(SingleExpenseItemConversion.groupUid("a"), SingleExpenseItemConversion.groupUid("a"))
        assertNotEquals(SingleExpenseItemConversion.groupUid("a"), SingleExpenseItemConversion.groupUid("b"))
        assertNotEquals(SingleExpenseItemConversion.groupUid(""), SingleExpenseItemConversion.groupUid(""))
    }

    @Test
    fun unknownAmount_roundTripsThroughTheBackup() {
        val item = PaymentItem(id = 1, paymentId = 5, name = "?", amount = 0, unknownAmount = true)
        assertTrue(BackupPaymentItem.from(item).toEntities().single().unknownAmount)
        val e = ExpenseItem(id = 1, date = "d", amount = 1, createdAt = 0, groupId = 2, unknownAmount = true, uid = "u")
        assertTrue(BackupExpenseItem.from(e).toEntity().unknownAmount)
        // 古い形式(項目なし)は false
        val old = BackupJson.decodeFromString(
            BackupPaymentItem.serializer(), """{"id":1,"receiptId":5,"name":"x","category":"食費"}""",
        )
        assertFalse(old.unknownAmount)
    }
}
