package com.amgm.personallog.backup

import com.amgm.personallog.data.model.PaymentStatus
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

    /** 旧形式の支出(まとまり。品目は子の支出 2 件)と、単独の支出 1 件。どれも支払いに属さない。 */
    private fun oldFormatData(): BackupData = BackupData(
        formatVersion = 4,
        dbVersion = 11,
        exportedAt = 1,
        expenses = listOf(
            BackupExpense(id = 1, date = "2026-10-01", time = "12:30", amount = 1000, category = "食費", memo = "店", createdAt = 100),
        ),
        expenseItems = listOf(
            BackupExpenseItem(id = 11, date = "2026-10-01", time = "12:30", amount = 600, category = "食費", memo = "m1", createdAt = 100, groupId = 1, name = "牛乳"),
            BackupExpenseItem(id = 12, date = "2026-10-01", time = "12:30", amount = 400, category = "食費", memo = "", createdAt = 100, groupId = 1, name = "パン"),
            BackupExpenseItem(id = 21, date = "2026-10-03", time = "", amount = 500, category = "その他", memo = "", createdAt = createdAt),
        ),
    )

    @Test
    fun orphanGroup_becomesManualPaymentWithLinkedItems() {
        val out = convertOrphanExpenseItemsToManualPayments(oldFormatData()).data

        val group = out.expenses.single()
        val groupPaymentId = notNull(group.paymentId)
        assertTrue(group.categoryEdited)
        assertTrue(group.memoEdited)

        val payment = out.payments.single { it.id == groupPaymentId }
        assertEquals("2026-10-01 12:30", payment.purchasedAt)
        assertEquals(1000, payment.totalAmount)
        assertEquals("", payment.imagePath)
        assertEquals("", payment.storeName)
        assertEquals("{}", payment.rawJson)
        assertEquals(PaymentStatus.CONFIRMED, payment.status)
        assertEquals(TotalSource.MANUAL, payment.totalSource)
        assertEquals(100L, payment.createdAt)

        val children = out.expenseItems.filter { it.groupId == 1L }
        assertEquals(2, children.size)
        for (child in children) {
            assertEquals(groupPaymentId, child.paymentId)
            val itemId = notNull(child.paymentItemId)
            val item = out.paymentItems.single { it.id == itemId }
            assertEquals(groupPaymentId, item.paymentId)
            assertEquals(child.name, item.name)
            assertEquals(child.amount, item.amount)
            assertEquals(child.memo, item.memo)
        }
        assertEquals(listOf(600, 400), children.map { child -> out.paymentItems.single { it.id == child.paymentItemId }.amount })
    }

    @Test
    fun orphanSingle_becomesManualPaymentWithoutItems() {
        val out = convertOrphanExpenseItemsToManualPayments(oldFormatData()).data

        val single = out.expenseItems.single { it.id == 21L }
        val singlePaymentId = notNull(single.paymentId)
        assertNull(single.paymentItemId)
        assertNull(single.groupId)
        assertTrue(single.categoryEdited)
        assertTrue(single.memoEdited)
        assertEquals(500, single.amount)
        assertEquals("その他", single.category)

        val payment = out.payments.single { it.id == singlePaymentId }
        assertEquals("2026-10-03", payment.purchasedAt)
        assertEquals(500, payment.totalAmount)
        assertEquals(TotalSource.MANUAL, payment.totalSource)
        assertEquals(PaymentStatus.CONFIRMED, payment.status)
        // 単独の支出は品目を作らない
        assertTrue(out.paymentItems.none { it.paymentId == singlePaymentId })
    }

    @Test
    fun afterConversion_noExpenseItemOrGroupIsWithoutPayment() {
        val linked = BackupExpenseItem(id = 31, date = "2026-10-02", time = "10:00", amount = 300, category = "食費", createdAt = 1, paymentId = 5)
        val data = oldFormatData().copy(
            payments = listOf(BackupPayment(5, "2026-10-02 10:00", "店", 300, "receipts/r.jpg", "{}", PaymentStatus.CONFIRMED, 1)),
            expenseItems = oldFormatData().expenseItems + linked,
        )
        val out = convertOrphanExpenseItemsToManualPayments(data).data

        assertEquals(0, out.expenseItems.count { it.groupId == null && it.paymentId == null })
        assertEquals(0, out.expenses.count { it.paymentId == null })
        // 既に支払いに属している支出は変えない
        assertEquals(linked, out.expenseItems.single { it.id == 31L })
    }

    @Test
    fun blankTime_isFilledWithCreatedTime_soTheLedgerTimeDoesNotChange() {
        val out = convertOrphanExpenseItemsToManualPayments(oldFormatData()).data
        val expected = LedgerCalc.timeOfMillis(createdAt)

        assertEquals(expected, out.expenseItems.single { it.id == 21L }.time)
        assertTrue(expected.matches(Regex("""\d{2}:\d{2}""")))
        // 時刻が空の支払いは日付だけの日時になる(時刻を借りない)
        val singlePaymentId = notNull(out.expenseItems.single { it.id == 21L }.paymentId)
        assertEquals("2026-10-03", out.payments.single { it.id == singlePaymentId }.purchasedAt)
    }

    @Test
    fun newIds_doNotCollideWithIdsAlreadyInTheBackup() {
        // 取り込み元に無いレシート 9 を指す支出(支払いに属さない扱い)と、既存のレシート 1・品目 7
        val data = BackupData(
            formatVersion = 8,
            dbVersion = 11,
            exportedAt = 1,
            payments = listOf(BackupPayment(1, "2026-10-01", "店", 100, "receipts/a.jpg", "{}", PaymentStatus.CONFIRMED, 1)),
            paymentItems = listOf(BackupPaymentItem(id = 7, paymentId = 1, name = "x", amount = 100, category = "食費")),
            expenseItems = listOf(BackupExpenseItem(id = 41, date = "2026-10-04", time = "09:00", amount = 200, category = "食費", createdAt = 2, paymentId = 9)),
        )
        val out = convertOrphanExpenseItemsToManualPayments(data).data

        val single = out.expenseItems.single { it.id == 41L }
        val newPaymentId = notNull(single.paymentId)
        assertTrue("元の参照先 9 を使い回さない", newPaymentId > 9L)
        assertEquals(2, out.payments.map { it.id }.toSet().size)
        assertEquals(1, out.payments.count { it.id == 1L })
    }

    @Test
    fun noOrphans_returnsDataUnchanged() {
        val data = BackupData(
            formatVersion = 8,
            dbVersion = 12,
            exportedAt = 1,
            payments = listOf(BackupPayment(1, "2026-10-01", "店", 100, "receipts/a.jpg", "{}", PaymentStatus.CONFIRMED, 1)),
            expenseItems = listOf(BackupExpenseItem(id = 1, date = "2026-10-01", amount = 100, category = "食費", createdAt = 1, paymentId = 1)),
        )
        val conversion = convertOrphanExpenseItemsToManualPayments(data)

        assertEquals(data, conversion.data)
        assertTrue(conversion.manualPaymentIds.isEmpty())
    }

    @Test
    fun manualPaymentIds_listExactlyTheNewPayments() {
        val conversion = convertOrphanExpenseItemsToManualPayments(oldFormatData())
        val out = conversion.data

        val expected = setOf(out.expenses.single().paymentId, out.expenseItems.single { it.id == 21L }.paymentId)
        assertEquals(expected, conversion.manualPaymentIds)
        assertFalse(conversion.manualPaymentIds.contains(5L))
    }
}
