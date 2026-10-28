package com.amgm.personallog.backup

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.domain.LedgerCalc
import com.amgm.personallog.domain.SingleExpenseConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 形式9以前の単独の支出(groupId なし)の取り込み: まとまり+品名が空の1品目に変換されることを確かめる(DB の v14 の移行と同じ規則)。 */
class BackupSingleExpenseTest {
    private val receipt = BackupReceipt(5, "2026-10-06 13:00", "食堂", 1200, "", "", ReceiptStatus.CONFIRMED, 1)

    private fun data(vararg expenses: BackupExpense, groups: List<BackupExpenseGroup> = emptyList()) = BackupData(
        formatVersion = 9, dbVersion = 13, exportedAt = 1, receipts = listOf(receipt), expenses = expenses.toList(), expenseGroups = groups,
    )

    private val single = BackupExpense(
        id = 3, date = "2026-10-06", time = "13:00", amount = 1200, category = "外食", memo = "自分で", receiptId = 5,
        createdAt = 2000, name = "ランチ", categoryEdited = true, memoEdited = true, timeEdited = true, uid = "e-3",
    )

    @Test
    fun single_becomesGroupWithOneBlankItem_sameValuesAsTheDbMigration() {
        val group = BackupExpenseGroup(id = 7, date = "2026-10-01", amount = 10, category = "食費", createdAt = 1, receiptId = 5)
        val out = convertSingleExpensesToGroups(data(single, groups = listOf(group)))

        val newGroup = out.expenseGroups.single { it.id != 7L }
        assertTrue("既存のまとまりの id と重ならない", newGroup.id > 7L)
        assertEquals("2026-10-06", newGroup.date)
        assertEquals("13:00", newGroup.time)
        assertEquals(1200, newGroup.amount)
        assertEquals("外食", newGroup.category)
        assertEquals("自分で", newGroup.memo)
        assertEquals(5L, newGroup.receiptId)
        assertEquals(2000L, newGroup.createdAt)
        assertTrue(newGroup.categoryEdited && newGroup.memoEdited && newGroup.timeEdited)
        assertFalse(newGroup.amountEdited)
        // uid は元の行の uid から決まる(端末の移行と同じ値。マージで二重にならない)
        assertEquals(SingleExpenseConversion.groupUid("e-3"), newGroup.uid)

        val item = out.expenses.single()
        assertEquals(newGroup.id, item.groupId)
        assertEquals("", item.name) // 品名は捨てる
        assertEquals(1200, item.amount)
        assertEquals("e-3", item.uid)
        assertFalse(item.categoryEdited || item.memoEdited || item.timeEdited || item.nameEdited || item.amountEdited)
    }

    @Test
    fun conversion_matchesTheDomainRuleUsedByTheMigration() {
        val e = Expense(
            id = 3, date = "2026-10-06", time = "13:00", amount = 1200, category = "外食", memo = "自分で", receiptId = 5,
            createdAt = 2000, name = "ランチ", categoryEdited = true, memoEdited = true, timeEdited = true, uid = "e-3",
        )
        val g = SingleExpenseConversion.groupOf(e)
        val out = convertSingleExpensesToGroups(data(single)).expenseGroups.single()
        assertEquals(g.copy(id = out.id), out.toEntity())
        val item = SingleExpenseConversion.itemOf(e, out.id)
        assertEquals(item, convertSingleExpensesToGroups(data(single)).expenses.single().toEntity())
    }

    @Test
    fun deletedRowOfTheSamePayment_isAttachedToTheNewGroup_stillDeleted() {
        val deleted = single.copy(id = 4, deletedByUser = true, uid = "e-4", receiptItemId = 9)
        val out = convertSingleExpensesToGroups(data(single, deleted))
        val gid = out.expenseGroups.single().id
        assertEquals(listOf(gid, gid), out.expenses.map { it.groupId })
        assertTrue(out.expenses.single { it.id == 4L }.deletedByUser)
        assertEquals("ランチ", out.expenses.single { it.id == 4L }.name) // 消した行は変えない
    }

    @Test
    fun deletedOrGroupedRows_areNotConverted() {
        val deleted = single.copy(id = 4, deletedByUser = true, uid = "e-4")
        val grouped = single.copy(id = 5, groupId = 1, uid = "e-5")
        val d = data(deleted, grouped, groups = listOf(BackupExpenseGroup(id = 1, date = "2026-10-06", amount = 1200, category = "外食", createdAt = 1)))
        assertEquals(d, convertSingleExpensesToGroups(d))
    }

    @Test
    fun blankTime_isFilledWithCreatedTime_onBothGroupAndItem() {
        val out = convertSingleExpensesToGroups(data(single.copy(time = "")))
        val expected = LedgerCalc.timeOfMillis(2000)
        assertEquals(expected, out.expenseGroups.single().time)
        assertEquals(expected, out.expenses.single().time)
    }

    @Test
    fun orphanThenSingle_everyExpenseEndsUpInAGroupWithAPayment() {
        // 形式4の、支払いに属さない単独の支出: 手入力の支払いを作り、さらにまとまり+1品目にする
        val orphan = BackupExpense(id = 21, date = "2026-10-03", time = "09:00", amount = 500, category = "その他", createdAt = 1, name = "x")
        val old = BackupData(formatVersion = 4, dbVersion = 11, exportedAt = 1, expenses = listOf(orphan))
        val out = convertSingleExpensesToGroups(convertOrphanExpensesToManualPayments(old).data)
        val e = out.expenses.single()
        val g = out.expenseGroups.single()
        assertEquals(g.id, e.groupId)
        assertEquals(e.receiptId, g.receiptId)
        assertEquals(500, g.amount)
        assertEquals("", e.name)
        assertTrue(out.expenses.none { it.groupId == null })
    }

    @Test
    fun groupUid_isStablePerSourceUid_andRandomWhenBlank() {
        assertEquals(SingleExpenseConversion.groupUid("a"), SingleExpenseConversion.groupUid("a"))
        assertNotEquals(SingleExpenseConversion.groupUid("a"), SingleExpenseConversion.groupUid("b"))
        assertNotEquals(SingleExpenseConversion.groupUid(""), SingleExpenseConversion.groupUid(""))
    }

    @Test
    fun unknownAmount_roundTripsThroughTheBackup() {
        val item = ReceiptItem(id = 1, receiptId = 5, name = "?", amount = 0, unknownAmount = true)
        assertTrue(BackupReceiptItem.from(item).toEntities().single().unknownAmount)
        val e = Expense(id = 1, date = "d", amount = 1, createdAt = 0, groupId = 2, unknownAmount = true, uid = "u")
        assertTrue(BackupExpense.from(e).toEntity().unknownAmount)
        // 古い形式(項目なし)は false
        val old = BackupJson.decodeFromString(
            BackupReceiptItem.serializer(), """{"id":1,"receiptId":5,"name":"x","category":"食費"}""",
        )
        assertFalse(old.unknownAmount)
    }
}
