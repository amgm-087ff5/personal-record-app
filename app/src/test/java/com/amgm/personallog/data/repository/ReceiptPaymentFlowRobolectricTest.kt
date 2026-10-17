package com.amgm.personallog.data.repository

import android.content.Context
import androidx.room.Room
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.domain.PaymentEdit
import com.amgm.personallog.domain.PaymentField
import com.amgm.personallog.ui.ledger.LedgerLogic
import com.amgm.personallog.ui.receipt.ItemForm
import com.amgm.personallog.ui.receipt.carryOverItemIds
import com.amgm.personallog.ui.receipt.toItemModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 保存→確定の結合を、実際の Room DB(Robolectric の SQLite)で確かめる:
 * 品目の id が保たれる / 家計簿で直した欄が残る / 消した品目が復活しない / 読み取り直しで id が引き継がれる /
 * 保存と確定が1つのトランザクション / 月をまたぐ移動 / レシートの値に戻す。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReceiptPaymentFlowRobolectricTest {
    private lateinit var db: AppDatabase
    private lateinit var receipts: ReceiptRepository
    private lateinit var expenses: ExpenseRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        receipts = ReceiptRepository(db, db.receiptDao(), db.expenseDao(), db.expenseGroupDao())
        expenses = ExpenseRepository(db.expenseDao(), db.expenseGroupDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun receipt(total: Int? = 700, at: String = "2026-10-31 20:00", store: String = "店") = Receipt(
        id = 0, purchasedAt = at, storeName = store, totalAmount = total, imagePath = "", rawJson = "{}", createdAt = 0,
    )

    private fun item(name: String, amount: Int, id: Long = 0) =
        ReceiptItem(id = id, receiptId = 0, name = name, amount = amount, category = "食費")

    /** 牛乳200・洗剤500のレシートを保存して確定し、(レシート, 品目)を返す。 */
    private fun confirmedReceipt(): Pair<Receipt, List<ReceiptItem>> = runBlocking {
        val id = receipts.saveAndConfirm(receipt(), listOf(item("牛乳", 200), item("洗剤", 500)))
        receipts.getReceipt(id)!! to receipts.getItems(id)
    }

    private fun children(receiptId: Long): List<Expense> = runBlocking { db.expenseDao().getByReceiptId(receiptId) }

    @Test
    fun saveAndConfirm_keepsItemIds_andLinksExpenses() {
        val (r, items) = confirmedReceipt()
        val first = children(r.id)
        assertEquals(items.map { it.id }, first.map { it.receiptItemId })
        // 品名を直して、同じ id の品目のまま再確定する
        runBlocking {
            receipts.saveAndConfirm(r, listOf(items[0].copy(name = "牛乳(特売)"), items[1]))
        }
        val again = children(r.id)
        assertEquals(first.map { it.id }, again.map { it.id }) // 行も同じ
        assertEquals(items.map { it.id }, runBlocking { receipts.getItems(r.id) }.map { it.id }) // 品目の id が保たれる
        assertEquals("牛乳(特売)", again[0].name)
    }

    @Test
    fun reconfirm_keepsFieldsEditedInTheLedger() {
        val (r, items) = confirmedReceipt()
        val milk = children(r.id)[0]
        runBlocking { expenses.update(PaymentEdit.editExpense(milk, milk.date, milk.time, 250, "外食", milk.name, "自分で")) }
        runBlocking {
            receipts.saveAndConfirm(r, listOf(items[0].copy(name = "牛乳(特売)", amount = 210, category = "日用品"), items[1]))
        }
        val m = children(r.id)[0]
        assertEquals(250, m.amount) // 直した欄は残る
        assertEquals("外食", m.category)
        assertEquals("自分で", m.memo)
        assertEquals("牛乳(特売)", m.name) // 直していない欄はレシートに追従
        assertTrue(m.amountEdited && m.categoryEdited && m.memoEdited && !m.nameEdited)
    }

    @Test
    fun itemDeletedInTheLedger_isNotRevivedByReconfirm_andCanBeRestored() {
        val (r, items) = confirmedReceipt()
        val soap = children(r.id)[1]
        runBlocking { expenses.delete(soap) }
        // 行は残り、印が付く
        val afterDelete = children(r.id)
        assertEquals(2, afterDelete.size)
        assertTrue(afterDelete[1].deletedByUser)
        runBlocking { receipts.saveAndConfirm(r, items) }
        val after = children(r.id)
        assertEquals(2, after.size) // 作り直されない・増えない
        assertTrue(after[1].deletedByUser)
        // 一覧・集計には出ない(グループの金額は変わらず、差額になる)
        val g = runBlocking { db.expenseGroupDao().getByReceiptId(r.id) }.single()
        val entry = LedgerLogic.buildEntries(after, listOf(g)).single() as com.amgm.personallog.ui.ledger.LedgerEntry.Group
        assertEquals(listOf("牛乳"), entry.items.map { it.name })
        assertEquals(700L, entry.amount)
        assertEquals(500L, entry.difference)
        assertEquals(700L, LedgerLogic.totalsByCategory(listOf(entry)).sumOf { it.total })
        // 元に戻す
        runBlocking { expenses.restore(after[1]) }
        assertFalse(children(r.id)[1].deletedByUser)
    }

    @Test
    fun manualRow_isReallyDeleted() {
        val (r, _) = confirmedReceipt()
        val g = runBlocking { db.expenseGroupDao().getByReceiptId(r.id) }.single()
        val manualId = runBlocking {
            expenses.add(Expense(date = g.date, time = g.time, amount = 30, category = "その他", name = "袋", createdAt = 1, groupId = g.id))
        }
        runBlocking { expenses.delete(db.expenseDao().getById(manualId)!!) }
        assertEquals(null, runBlocking { db.expenseDao().getById(manualId) })
    }

    @Test
    fun rescan_carriesItemIds_soLedgerEditsSurvive() {
        val (r, items) = confirmedReceipt()
        val milk = children(r.id)[0]
        runBlocking { expenses.update(PaymentEdit.editExpense(milk, milk.date, milk.time, 250, milk.category, milk.name, milk.memo)) }
        fun form(i: ReceiptItem, key: Long, id: Long) = ItemForm(
            key = key, id = id, name = i.name, memo = "", amount = i.amount.toString(), category = i.category,
            taxRate = "10", priceIncludesTax = true,
        )
        val previous = items.mapIndexed { n, i -> form(i, n + 1L, i.id) }
        // 読み取り直し: 新しい品目(id なし)。牛乳・洗剤は同じ内容で読めた
        val rescanned = carryOverItemIds(previous, listOf(form(items[1], 10, 0), form(items[0], 11, 0)))
        assertEquals(listOf(items[1].id, items[0].id), rescanned.map { it.id })
        runBlocking { receipts.saveAndConfirm(r, rescanned.map { it.toItemModel(r.id) }) }
        assertEquals(items.map { it.id }.toSet(), runBlocking { receipts.getItems(r.id) }.map { it.id }.toSet())
        val m = children(r.id).single { it.receiptItemId == items[0].id }
        assertEquals(250, m.amount) // 家計簿で直した金額は残る
        assertTrue(m.amountEdited)
    }

    @Test
    fun confirmImpact_warnsAboutEditedRowsOfRemovedItems() {
        val (r, items) = confirmedReceipt()
        val milk = children(r.id)[0]
        runBlocking { expenses.update(PaymentEdit.editExpense(milk, milk.date, milk.time, 250, milk.category, milk.name, milk.memo)) }
        // 牛乳を品目から外す(読み取り直しで対応が取れなかった)と、直した1件が消える
        val impact = runBlocking { receipts.confirmImpact(r, listOf(items[1])) }
        assertFalse(impact.rebuild)
        assertEquals(1, impact.lostEdited)
        assertTrue(impact.needsWarning)
        // 何も外さなければ警告は要らない
        assertFalse(runBlocking { receipts.confirmImpact(r, items) }.needsWarning)
    }

    @Test
    fun saveAndConfirm_isOneTransaction_failureRollsBackTheSave() {
        val (r, items) = confirmedReceipt()
        try {
            // 合計が空だと確定に失敗する。保存した店名の変更も戻るはず
            runBlocking { receipts.saveAndConfirm(r.copy(storeName = "変えた店", totalAmount = null), items) }
            fail("確定に失敗するはず")
        } catch (e: IllegalStateException) {
            // 期待どおり
        }
        assertEquals("店", runBlocking { receipts.getReceipt(r.id) }!!.storeName)
        assertEquals(700, runBlocking { receipts.getReceipt(r.id) }!!.totalAmount)
    }

    @Test
    fun zeroYenItem_withEditedAmount_survivesReconfirm() {
        val (r, items) = confirmedReceipt()
        val soap = children(r.id)[1]
        runBlocking { expenses.update(PaymentEdit.editExpense(soap, soap.date, soap.time, 480, soap.category, soap.name, soap.memo)) }
        runBlocking { receipts.saveAndConfirm(r, listOf(items[0], items[1].copy(amount = 0))) }
        val soapAfter = children(r.id).single { it.receiptItemId == items[1].id }
        assertEquals(480, soapAfter.amount)
    }

    @Test
    fun groupMovedToNextMonth_childrenFollow_andBothMonthsAreCorrect() {
        val (r, _) = confirmedReceipt()
        val g = runBlocking { db.expenseGroupDao().getByReceiptId(r.id) }.single()
        val single = Expense(date = "2026-10-20", time = "10:00", amount = 50, category = "その他", createdAt = 1)
        runBlocking { expenses.add(single) }
        fun total(from: String, to: String): Long = runBlocking {
            val rows = expenses.observeLedgerRows(from, to).first()
            val groups = expenses.observeGroupsBetween(from, to).first()
            LedgerLogic.buildEntries(rows, groups).sumOf { it.amount }
        }
        assertEquals(750L, total("2026-10-01", "2026-10-31"))
        assertEquals(0L, total("2026-11-01", "2026-11-30"))
        // グループを翌月に動かす
        runBlocking { expenses.updateGroup(PaymentEdit.editGroup(g, "2026-11-02", "09:00", g.amount, g.category, g.memo)) }
        assertTrue(children(r.id).all { it.date == "2026-11-02" && it.time == "09:00" })
        assertEquals(50L, total("2026-10-01", "2026-10-31"))
        assertEquals(700L, total("2026-11-01", "2026-11-30"))
        // レシートを再保存・再確定しても、日時を直したグループと品目はそろったまま
        runBlocking { receipts.saveAndConfirm(receipts.getReceipt(r.id)!!.copy(purchasedAt = "2026-10-30 18:00"), receipts.getItems(r.id)) }
        assertTrue(children(r.id).all { it.date == "2026-11-02" })
        assertEquals(700L, total("2026-11-01", "2026-11-30"))
        assertEquals(50L, total("2026-10-01", "2026-10-31"))
    }

    @Test
    fun revertToReceiptValue_restoresTheField_andClearsEdited() {
        val (r, items) = confirmedReceipt()
        val milk = children(r.id)[0]
        runBlocking { expenses.update(PaymentEdit.editExpense(milk, milk.date, milk.time, 250, "外食", milk.name, "自分で")) }
        assertTrue(runBlocking { expenses.revertExpenseField(milk.id, PaymentField.AMOUNT) })
        val m = children(r.id)[0]
        assertEquals(200, m.amount)
        assertFalse(m.amountEdited)
        assertEquals("外食", m.category) // 他の欄はそのまま
        assertTrue(m.categoryEdited)
        // グループの金額
        val g = runBlocking { db.expenseGroupDao().getByReceiptId(r.id) }.single()
        runBlocking { expenses.updateGroup(PaymentEdit.editGroup(g, g.date, g.time, 900, g.category, "別の店")) }
        assertTrue(runBlocking { expenses.revertGroupField(g.id, PaymentField.AMOUNT) })
        assertTrue(runBlocking { expenses.revertGroupField(g.id, PaymentField.MEMO) })
        val g2 = runBlocking { db.expenseGroupDao().getByReceiptId(r.id) }.single()
        assertEquals(700, g2.amount)
        assertEquals("店", g2.memo)
        assertFalse(g2.amountEdited || g2.memoEdited)
        assertNotNull(items)
    }

    @Test
    fun revertToReceiptValue_time_followsTheReceiptAndClearsEdited() {
        val (r, _) = confirmedReceipt()
        val g = runBlocking { db.expenseGroupDao().getByReceiptId(r.id) }.single()
        runBlocking { expenses.updateGroup(PaymentEdit.editGroup(g, "2026-11-02", "09:00", g.amount, g.category, g.memo)) }
        assertTrue(runBlocking { expenses.revertGroupField(g.id, PaymentField.TIME) })
        val g2 = runBlocking { db.expenseGroupDao().getByReceiptId(r.id) }.single()
        assertEquals("2026-10-31", g2.date)
        assertEquals("20:00", g2.time)
        assertFalse(g2.timeEdited)
        assertTrue(children(r.id).all { it.date == "2026-10-31" && it.time == "20:00" })
    }

    @Test
    fun roundTripThroughAdvance_doesNotReviveDeletedItems() {
        val (r, items) = confirmedReceipt()
        val soap = children(r.id)[1]
        runBlocking { expenses.delete(soap) }
        // 立て替えにする: 確認は「作り直す」で、消した品目は消したまま残ると知らせる
        val advance = r.copy(isAdvance = true, ownAmount = 300)
        val impact = runBlocking { receipts.confirmImpact(advance, items) }
        assertTrue(impact.rebuild)
        assertEquals(1, impact.keptDeleted)
        assertEquals(0, impact.lostEdited)
        runBlocking { receipts.saveAndConfirm(advance, items) }
        val whileAdvance = children(r.id)
        assertEquals(1, whileAdvance.count { !it.deletedByUser })
        assertEquals(300, whileAdvance.single { !it.deletedByUser }.amount)
        assertTrue(whileAdvance.single { it.deletedByUser }.groupId == null)
        // 立て替えをやめて通常に戻す: 洗剤は復活しない
        runBlocking { receipts.saveAndConfirm(advance.copy(isAdvance = false, ownAmount = null), items) }
        val back = children(r.id)
        assertEquals(2, back.size)
        val gid = runBlocking { db.expenseGroupDao().getByReceiptId(r.id) }.single().id
        assertTrue(back.all { it.groupId == gid })
        assertEquals(listOf("洗剤"), back.filter { it.deletedByUser }.map { it.name })
        assertEquals(listOf("牛乳"), back.filter { !it.deletedByUser }.map { it.name })
        // 元に戻せる
        runBlocking { expenses.restore(back.single { it.deletedByUser }) }
        assertTrue(children(r.id).none { it.deletedByUser })
    }

    @Test
    fun revertSingleRow_ofAdvanceReceiptWithSeveralItems_usesOwnAmount() {
        val id = runBlocking {
            receipts.saveAndConfirm(
                receipt().copy(isAdvance = true, ownAmount = 300), listOf(item("牛乳", 200), item("洗剤", 500)),
            )
        }
        val single = children(id).single()
        assertEquals(300, single.amount)
        runBlocking { expenses.update(PaymentEdit.editExpense(single, single.date, single.time, 999, single.category, single.name, "自分で")) }
        assertTrue(runBlocking { expenses.revertExpenseField(single.id, PaymentField.AMOUNT) })
        assertTrue(runBlocking { expenses.revertExpenseField(single.id, PaymentField.MEMO) })
        val after = children(id).single()
        assertEquals(300, after.amount)
        assertFalse(after.amountEdited)
        assertEquals("店", after.memo)
        assertFalse(after.memoEdited)
    }

    @Test
    fun setDeletedByUser_touchesOnlyTheFlag() {
        val (r, _) = confirmedReceipt()
        val milk = children(r.id)[0]
        runBlocking { expenses.update(PaymentEdit.editExpense(milk, milk.date, milk.time, 250, milk.category, milk.name, milk.memo)) }
        runBlocking { db.expenseDao().setDeletedByUser(milk.id, true) }
        val after = children(r.id)[0]
        assertTrue(after.deletedByUser)
        assertEquals(250, after.amount)
        assertTrue(after.amountEdited)
    }
}
