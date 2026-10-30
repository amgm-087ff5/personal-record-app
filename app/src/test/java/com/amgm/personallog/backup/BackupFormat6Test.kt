package com.amgm.personallog.backup

import com.amgm.personallog.data.model.ExpenseItem
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.PaymentItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormat6Test {
    private val group = Expense(
        id = 3, date = "2026-10-05", time = "12:30", amount = 700, category = "日用品", memo = "スーパー",
        paymentId = 5, createdAt = 40, uid = "g-uid-1",
        amountEdited = true, categoryEdited = false, memoEdited = true, timeEdited = true,
    )
    private val expenseItem = ExpenseItem(
        id = 9, date = "2026-10-05", time = "12:30", amount = 250, category = "食費", memo = "自分で",
        paymentId = 5, createdAt = 41, groupId = 3, timeEdited = true,
        paymentItemId = 11, name = "牛乳", amountEdited = true, categoryEdited = true, nameEdited = false,
        memoEdited = true, uid = "e-uid-1",
    )

    @Test
    fun format6_roundTrip_keepsNewColumns() {
        val data = BackupData(
            formatVersion = BACKUP_FORMAT_VERSION,
            dbVersion = 9,
            expenses = listOf(BackupExpense.from(group)),
            expenseItems = listOf(BackupExpenseItem.from(expenseItem)),
        )
        val text = BackupJson.encodeToString(BackupData.serializer(), data)
        val back = BackupJson.decodeFromString(BackupData.serializer(), text)
        assertEquals(data, back)
        assertEquals(BACKUP_FORMAT_VERSION, back.formatVersion)
        assertEquals(group, back.expenses.single().toEntity())
        assertEquals(expenseItem, back.expenseItems.single().toEntity())
        assertTrue(text.contains("\"receiptItemId\":11"))
        assertTrue(text.contains("\"uid\":\"e-uid-1\""))
    }

    @Test
    fun format6_roundTrip_keepsDeletedByUser() {
        val deleted = expenseItem.copy(deletedByUser = true)
        val data = BackupData(
            formatVersion = BACKUP_FORMAT_VERSION,
            dbVersion = 9,
            expenses = listOf(BackupExpense.from(group)),
            expenseItems = listOf(BackupExpenseItem.from(deleted), BackupExpenseItem.from(expenseItem.copy(id = 10, uid = "e-uid-2"))),
        )
        val text = BackupJson.encodeToString(BackupData.serializer(), data)
        val back = BackupJson.decodeFromString(BackupData.serializer(), text)
        assertEquals(data, back)
        assertTrue(back.expenseItems[0].toEntity().deletedByUser)
        assertFalse(back.expenseItems[1].toEntity().deletedByUser)
        assertEquals(deleted, back.expenseItems[0].toEntity())
        // deletedByUser の無い形式(5以前や古い6)は false として読む
        val old = BackupJson.decodeFromString(
            BackupData.serializer(),
            """{"formatVersion":6,"expenses":[{"id":1,"date":"2026-10-05","amount":1,"category":"食費","createdAt":1}]}""",
        )
        assertFalse(old.expenseItems.single().deletedByUser)
    }

    @Test
    fun format5_isStillReadable_newColumnsGetDefaults_andUidIsGenerated() {
        val text = """{"formatVersion":5,"dbVersion":8,"exportedAt":1,
            "expenseGroups":[{"id":3,"date":"2026-10-05","time":"12:30","amount":700,"category":"食費","memo":"店","receiptId":5,"createdAt":40}],
            "expenses":[{"id":9,"date":"2026-10-05","time":"12:30","amount":250,"category":"食費","memo":"店 牛乳","receiptId":5,"createdAt":41,"groupId":3,"timeEdited":true}]}"""
        val d = BackupJson.decodeFromString(BackupData.serializer(), text)
        BackupManager.checkVersion(d.formatVersion)
        val g = d.expenses.single()
        val e = d.expenseItems.single()
        assertEquals("", g.uid)
        assertEquals("", e.uid)
        assertNull(e.paymentItemId)
        assertEquals("", e.name)
        assertFalse(e.amountEdited || e.categoryEdited || e.nameEdited || e.memoEdited)
        assertTrue(e.timeEdited)
        val ge = g.toEntity()
        val ee = e.toEntity()
        // 古い形式の行には、取り込み時に新しい uid を振る
        assertTrue(ge.uid.isNotBlank())
        assertTrue(ee.uid.isNotBlank())
        assertNotEquals(ge.uid, ee.uid)
        assertFalse(ge.amountEdited || ge.categoryEdited || ge.memoEdited || ge.timeEdited)
        assertEquals("店 牛乳", ee.memo)
    }

    @Test
    fun checkVersion_acceptsFiveToTen_rejectsEleven() {
        BackupManager.checkVersion(5)
        BackupManager.checkVersion(6)
        BackupManager.checkVersion(7)
        BackupManager.checkVersion(8)
        BackupManager.checkVersion(9)
        BackupManager.checkVersion(10)
        try {
            BackupManager.checkVersion(11)
            org.junit.Assert.fail("expected BackupException")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("新しい"))
        }
    }

    // ------------------------------------------------------------ 復元(マージ)の照合

    @Test
    fun groupMerge_usesUidWhenPresent_evenIfOtherFieldsDiffer() {
        val idx = GroupMergeIndex(listOf(group.copy(id = 100, memo = "別の名前に直した")))
        // uid が同じなら、メモが違っても同じ支払い
        val incoming = group.copy(id = 0, memo = "スーパー")
        assertEquals(100L, idx.find("g-uid-1", incoming))
        // uid が違い、別の支払いのグループなら別の支出(支出は必ず支払いに属する。v16〜)
        assertNull(idx.find("other", group.copy(id = 0, paymentId = 999, memo = "別の名前に直した")))
    }

    @Test
    fun groupMerge_differentUidButSamePayment_fallsBackToLegacyKey() {
        // 別の端末で確定して uid が違うだけの、同じ支払いの支出は二重に登録しない
        val idx = GroupMergeIndex(listOf(group.copy(id = 100, uid = "device-b")))
        assertEquals(100L, idx.find("g-uid-1", group.copy(id = 0)))
        // 従来のキーが違っても、その支払いに支出(まとまり)が既にあれば追加しない(同じ支払いにグループが二重になるのを防ぐ)
        assertEquals(100L, idx.find("g-uid-1", group.copy(id = 0, amount = 1, memo = "直した")))
        // 別の支払いのグループなら別の支払い
        assertNull(idx.find("g-uid-1", group.copy(id = 0, amount = 1, paymentId = 6)))
    }

    @Test
    fun matchExistingItems_pairsSameContentOneToOne() {
        fun bi(id: Long, name: String, amount: Int, rate: Int = 10) =
            BackupPaymentItem(id = id, paymentId = 5, name = name, amount = amount, category = "食費", taxRate = rate)
        fun ri(id: Long, name: String, amount: Int, rate: Int = 10) =
            PaymentItem(id = id, paymentId = 77, name = name, amount = amount, category = "食費", taxRate = rate)
        val m = matchExistingItems(
            listOf(bi(1, "牛乳", 200), bi(2, "牛乳", 200), bi(3, "洗剤", 500), bi(4, "パン", 100, rate = 8)),
            listOf(ri(71, "牛乳", 200), ri(72, "洗剤", 500), ri(73, "牛乳", 200), ri(74, "パン", 100, rate = 10)),
        )
        assertEquals(mapOf(1L to 71L, 2L to 73L, 3L to 72L), m) // 税率が違うパンは対応させない
    }

    @Test
    fun expenseItemMerge_differentUidButSamePayment_fallsBackToLegacyKeyByCount() {
        val ex = ExpenseItem(
            date = "2026-10-05", time = "12:30", amount = 250, category = "食費", memo = "m", paymentId = 50, groupId = 60,
            createdAt = 0, uid = "device-b-1",
        )
        val idx = ExpenseItemMergeIndex(listOf(ex, ex.copy(uid = "device-b-2")))
        assertTrue(idx.isDuplicate(backupExpenseItem("a-1"), 50, 60))
        assertTrue(idx.isDuplicate(backupExpenseItem("a-2"), 50, 60))
        assertFalse(idx.isDuplicate(backupExpenseItem("a-3"), 50, 60)) // 3件目は件数が足りず新規
        // 支払いに属さない(復元後の paymentId が無い)行は、uid が違えば別の行
        val manual = ExpenseItemMergeIndex(listOf(ex.copy(paymentId = null, groupId = null)))
        assertFalse(manual.isDuplicate(backupExpenseItem("z"), null, null))
    }

    @Test
    fun groupMerge_oldFormatWithoutUid_usesLegacyKey() {
        val idx = GroupMergeIndex(listOf(group.copy(id = 100, uid = "whatever")))
        val sameKey = group.copy(id = 0, uid = "generated")
        assertEquals(100L, idx.find("", sameKey))
        // 1つの支払いに支出は1つ: 欄が違っても、同じ支払いに支出があれば追加しない
        assertEquals(100L, idx.find("", sameKey.copy(amount = 701)))
        // 別の支払いなら別の支出
        assertNull(idx.find("", sameKey.copy(amount = 701, paymentId = 999)))
        // 新しく入れた行も登録され、同じバックアップ内の重複に効く
        val fresh = sameKey.copy(amount = 5, paymentId = 998)
        assertNull(idx.find("", fresh))
        idx.add(fresh, 200)
        assertEquals(200L, idx.find("", fresh))
    }

    private fun backupExpenseItem(uid: String, amount: Int = 250) = BackupExpenseItem(
        id = 1, date = "2026-10-05", time = "12:30", amount = amount, category = "食費", memo = "m",
        paymentId = 5, createdAt = 1, groupId = 3, uid = uid,
    )

    @Test
    fun expenseItemMerge_usesUidWhenPresent() {
        val existing = ExpenseItem(date = "2026-10-05", amount = 999, category = "その他", memo = "直した", createdAt = 0, uid = "u1")
        val idx = ExpenseItemMergeIndex(listOf(existing))
        assertTrue(idx.isDuplicate(backupExpenseItem("u1"), 50, 60)) // 内容が違っても uid が同じ
        assertFalse(idx.isDuplicate(backupExpenseItem("u2"), 50, 60))
        assertTrue(idx.isDuplicate(backupExpenseItem("u2"), 50, 60)) // 同じバックアップ内の重複
    }

    @Test
    fun expenseItemMerge_oldFormat_usesLegacyKeysByCount() {
        val ex = ExpenseItem(
            date = "2026-10-05", time = "12:30", amount = 250, category = "食費", memo = "m", paymentId = 50, groupId = 60,
            createdAt = 0, uid = "x",
        )
        val idx = ExpenseItemMergeIndex(listOf(ex, ex.copy(uid = "y"))) // 同じ内容が2件ある
        assertTrue(idx.isDuplicate(backupExpenseItem(""), 50, 60))
        assertTrue(idx.isDuplicate(backupExpenseItem(""), 50, 60))
        assertFalse(idx.isDuplicate(backupExpenseItem(""), 50, 60)) // 3件目は新規(件数ベース)
        assertFalse(idx.isDuplicate(backupExpenseItem("", amount = 1), 50, 60))
    }

    @Test
    fun paymentItemId_isRemappedOrDropped() {
        val itemIdMap = mapOf(11L to 111L)
        val a = backupExpenseItem("a").copy(paymentItemId = 11)
        val b = backupExpenseItem("b").copy(paymentItemId = 12) // 対応が取れない
        assertEquals(111L, remapPaymentItemId(a.paymentItemId, itemIdMap))
        assertNull(remapPaymentItemId(b.paymentItemId, itemIdMap))
        assertNull(remapPaymentItemId(null, itemIdMap))
    }
}
