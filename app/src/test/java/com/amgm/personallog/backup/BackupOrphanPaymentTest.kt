package com.amgm.personallog.backup

import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.domain.LedgerCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 旧形式(支払いに属さない支出)の取り込み: 手入力の支払いに変換されることを確かめる。 */
class BackupOrphanPaymentTest {
    private val createdAt = 1_700_000_000_000L

    /** null でないことを確かめて値を返す(JUnit 4 の assertNotNull は値を返さない)。 */
    private fun <T : Any> notNull(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    /** 旧形式の支払いグループ(品目は子の支出 2 件)と、単独の支出 1 件。どれも支払いに属さない。 */
    private fun oldFormatData(): BackupData = BackupData(
        formatVersion = 4,
        dbVersion = 11,
        exportedAt = 1,
        expenseGroups = listOf(
            BackupExpenseGroup(id = 1, date = "2026-10-01", time = "12:30", amount = 1000, category = "食費", memo = "店", createdAt = 100),
        ),
        expenses = listOf(
            BackupExpense(id = 11, date = "2026-10-01", time = "12:30", amount = 600, category = "食費", memo = "m1", createdAt = 100, groupId = 1, name = "牛乳"),
            BackupExpense(id = 12, date = "2026-10-01", time = "12:30", amount = 400, category = "食費", memo = "", createdAt = 100, groupId = 1, name = "パン"),
            BackupExpense(id = 21, date = "2026-10-03", time = "", amount = 500, category = "その他", memo = "", createdAt = createdAt),
        ),
    )

    @Test
    fun orphanGroup_becomesManualPaymentWithLinkedItems() {
        val out = convertOrphanExpensesToManualPayments(oldFormatData()).data

        val group = out.expenseGroups.single()
        val groupReceiptId = notNull(group.receiptId)
        assertTrue(group.categoryEdited)
        assertTrue(group.memoEdited)

        val receipt = out.receipts.single { it.id == groupReceiptId }
        assertEquals("2026-10-01 12:30", receipt.purchasedAt)
        assertEquals(1000, receipt.totalAmount)
        assertEquals("", receipt.imagePath)
        assertEquals("", receipt.storeName)
        assertEquals("{}", receipt.rawJson)
        assertEquals(ReceiptStatus.CONFIRMED, receipt.status)
        assertEquals(TotalSource.MANUAL, receipt.totalSource)
        assertEquals(100L, receipt.createdAt)

        val children = out.expenses.filter { it.groupId == 1L }
        assertEquals(2, children.size)
        for (child in children) {
            assertEquals(groupReceiptId, child.receiptId)
            val itemId = notNull(child.receiptItemId)
            val item = out.receiptItems.single { it.id == itemId }
            assertEquals(groupReceiptId, item.receiptId)
            assertEquals(child.name, item.name)
            assertEquals(child.amount, item.amount)
            assertEquals(child.memo, item.memo)
        }
        assertEquals(listOf(600, 400), children.map { child -> out.receiptItems.single { it.id == child.receiptItemId }.amount })
    }

    @Test
    fun orphanSingle_becomesManualPaymentWithoutItems() {
        val out = convertOrphanExpensesToManualPayments(oldFormatData()).data

        val single = out.expenses.single { it.id == 21L }
        val singleReceiptId = notNull(single.receiptId)
        assertNull(single.receiptItemId)
        assertNull(single.groupId)
        assertTrue(single.categoryEdited)
        assertTrue(single.memoEdited)
        assertEquals(500, single.amount)
        assertEquals("その他", single.category)

        val receipt = out.receipts.single { it.id == singleReceiptId }
        assertEquals("2026-10-03", receipt.purchasedAt)
        assertEquals(500, receipt.totalAmount)
        assertEquals(TotalSource.MANUAL, receipt.totalSource)
        assertEquals(ReceiptStatus.CONFIRMED, receipt.status)
        // 単独の支出は品目を作らない
        assertTrue(out.receiptItems.none { it.receiptId == singleReceiptId })
    }

    @Test
    fun afterConversion_noExpenseOrGroupIsWithoutPayment() {
        val linked = BackupExpense(id = 31, date = "2026-10-02", time = "10:00", amount = 300, category = "食費", createdAt = 1, receiptId = 5)
        val data = oldFormatData().copy(
            receipts = listOf(BackupReceipt(5, "2026-10-02 10:00", "店", 300, "receipts/r.jpg", "{}", ReceiptStatus.CONFIRMED, 1)),
            expenses = oldFormatData().expenses + linked,
        )
        val out = convertOrphanExpensesToManualPayments(data).data

        assertEquals(0, out.expenses.count { it.groupId == null && it.receiptId == null })
        assertEquals(0, out.expenseGroups.count { it.receiptId == null })
        // 既に支払いに属している支出は変えない
        assertEquals(linked, out.expenses.single { it.id == 31L })
    }

    @Test
    fun blankTime_isFilledWithCreatedTime_soTheLedgerTimeDoesNotChange() {
        val out = convertOrphanExpensesToManualPayments(oldFormatData()).data
        val expected = LedgerCalc.timeOfMillis(createdAt)

        assertEquals(expected, out.expenses.single { it.id == 21L }.time)
        assertTrue(expected.matches(Regex("""\d{2}:\d{2}""")))
        // 時刻が空の支払いは日付だけの日時になる(時刻を借りない)
        val singleReceiptId = notNull(out.expenses.single { it.id == 21L }.receiptId)
        assertEquals("2026-10-03", out.receipts.single { it.id == singleReceiptId }.purchasedAt)
    }

    @Test
    fun newIds_doNotCollideWithIdsAlreadyInTheBackup() {
        // 取り込み元に無いレシート 9 を指す支出(支払いに属さない扱い)と、既存のレシート 1・品目 7
        val data = BackupData(
            formatVersion = 8,
            dbVersion = 11,
            exportedAt = 1,
            receipts = listOf(BackupReceipt(1, "2026-10-01", "店", 100, "receipts/a.jpg", "{}", ReceiptStatus.CONFIRMED, 1)),
            receiptItems = listOf(BackupReceiptItem(id = 7, receiptId = 1, name = "x", amount = 100, category = "食費")),
            expenses = listOf(BackupExpense(id = 41, date = "2026-10-04", time = "09:00", amount = 200, category = "食費", createdAt = 2, receiptId = 9)),
        )
        val out = convertOrphanExpensesToManualPayments(data).data

        val single = out.expenses.single { it.id == 41L }
        val newReceiptId = notNull(single.receiptId)
        assertTrue("元の参照先 9 を使い回さない", newReceiptId > 9L)
        assertEquals(2, out.receipts.map { it.id }.toSet().size)
        assertEquals(1, out.receipts.count { it.id == 1L })
    }

    @Test
    fun noOrphans_returnsDataUnchanged() {
        val data = BackupData(
            formatVersion = 8,
            dbVersion = 12,
            exportedAt = 1,
            receipts = listOf(BackupReceipt(1, "2026-10-01", "店", 100, "receipts/a.jpg", "{}", ReceiptStatus.CONFIRMED, 1)),
            expenses = listOf(BackupExpense(id = 1, date = "2026-10-01", amount = 100, category = "食費", createdAt = 1, receiptId = 1)),
        )
        val conversion = convertOrphanExpensesToManualPayments(data)

        assertEquals(data, conversion.data)
        assertTrue(conversion.manualReceiptIds.isEmpty())
    }

    @Test
    fun manualReceiptIds_listExactlyTheNewPayments() {
        val conversion = convertOrphanExpensesToManualPayments(oldFormatData())
        val out = conversion.data

        val expected = setOf(out.expenseGroups.single().receiptId, out.expenses.single { it.id == 21L }.receiptId)
        assertEquals(expected, conversion.manualReceiptIds)
        assertFalse(conversion.manualReceiptIds.contains(5L))
    }
}
