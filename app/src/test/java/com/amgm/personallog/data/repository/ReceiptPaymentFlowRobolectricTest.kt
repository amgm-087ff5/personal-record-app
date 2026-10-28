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
import org.junit.Assert.assertNull
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
        val splits = SplitRepository(db, db.splitDao(), db.receiptDao())
        receipts = ReceiptRepository(db, db.receiptDao(), db.expenseDao(), db.expenseGroupDao(), splits)
        expenses = ExpenseRepository(db.expenseDao(), db.expenseGroupDao(), db, splits)
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
        // 移行前から残る行を想定する: 品目の対応(receiptItemId)が無い行は、家計簿の削除で本当に消す(品目の行のように印だけ付けない)
        val legacyId = runBlocking {
            db.expenseDao().insert(Expense(date = g.date, time = g.time, amount = 30, category = "その他", name = "袋", createdAt = 1, groupId = g.id))
        }
        runBlocking { expenses.delete(db.expenseDao().getById(legacyId)!!) }
        assertEquals(null, runBlocking { db.expenseDao().getById(legacyId) })
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
        // 品目の無い支出も、支払い(合計だけの手入力)を保存して作る
        runBlocking { receipts.saveAndConfirm(receipt(total = 50, at = "2026-10-20 10:00", store = "単独"), emptyList()) }
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
        assertEquals("", g2.memo)  // 店名は支払い(storeName)に持ち、支出のメモには写さない(v15)
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
        // 立て替えにする: v14〜 立て替えも支出+品目なので作り直さない(確認は不要)
        val advance = r.copy(isAdvance = true, ownAmount = 300)
        val impact = runBlocking { receipts.confirmImpact(advance, items) }
        assertFalse(impact.rebuild)
        assertEquals(0, impact.lostEdited)
        runBlocking { receipts.saveAndConfirm(advance, items) }
        val gid = runBlocking { db.expenseGroupDao().getByReceiptId(r.id) }.single().id
        val whileAdvance = children(r.id)
        assertEquals(300, runBlocking { db.expenseGroupDao().getById(gid) }!!.amount)
        assertTrue(whileAdvance.all { it.groupId == gid })
        assertEquals(listOf("洗剤"), whileAdvance.filter { it.deletedByUser }.map { it.name })
        // 立て替えをやめて通常に戻す: 洗剤は復活しない
        runBlocking { receipts.saveAndConfirm(advance.copy(isAdvance = false, ownAmount = null), items) }
        val back = children(r.id)
        assertEquals(2, back.size)
        assertTrue(back.all { it.groupId == gid })
        assertEquals(listOf("洗剤"), back.filter { it.deletedByUser }.map { it.name })
        assertEquals(listOf("牛乳"), back.filter { !it.deletedByUser }.map { it.name })
        // 元に戻せる
        runBlocking { expenses.restore(back.single { it.deletedByUser }) }
        assertTrue(children(r.id).none { it.deletedByUser })
    }

    @Test
    fun advanceReceiptWithSeveralItems_isGroupOfOwnAmount_andRevertUsesIt() {
        val id = runBlocking {
            receipts.saveAndConfirm(
                receipt().copy(isAdvance = true, ownAmount = 300), listOf(item("牛乳", 200), item("洗剤", 500)),
            )
        }
        // 自分の分 300 を品目の金額の比(200:500)で配る
        assertEquals(listOf(86, 214), children(id).map { it.amount })
        val g = runBlocking { db.expenseGroupDao().getByReceiptId(id) }.single()
        assertEquals(300, g.amount)
        runBlocking { expenses.updateGroup(PaymentEdit.editGroup(g, g.date, g.time, 999, g.category, "自分で")) }
        assertTrue(runBlocking { expenses.revertGroupField(g.id, PaymentField.AMOUNT) })
        assertTrue(runBlocking { expenses.revertGroupField(g.id, PaymentField.MEMO) })
        val after = runBlocking { db.expenseGroupDao().getByReceiptId(id) }.single()
        assertEquals(300, after.amount)
        assertFalse(after.amountEdited)
        assertEquals("", after.memo)  // 店名は支払い(storeName)に持ち、支出のメモには写さない(v15)
        assertFalse(after.memoEdited)
    }

    @Test
    fun itemlessPayment_isGroupWithOneBlankItem_thatFollowsTheGroupAmount() {
        val id = runBlocking { receipts.saveAndConfirm(receipt(total = 1200, store = "コンビニ"), emptyList()) }
        val g = runBlocking { db.expenseGroupDao().getByReceiptId(id) }.single()
        val only = children(id).single()
        assertEquals(g.id, only.groupId)
        assertEquals("", only.name)
        assertEquals(1200, only.amount)
        assertEquals("", only.memo)  // 店名は支払い(storeName)に持ち、支出のメモには写さない(v15)
        // 支出の金額を家計簿で直すと、1品目の金額も追従する
        runBlocking { expenses.updateGroup(PaymentEdit.editGroup(g, g.date, g.time, 1500, "外食", g.memo)) }
        val after = children(id).single()
        assertEquals(1500, after.amount)
        assertEquals("外食", after.category)
        // 合計を変えて再確定すると、まとまりと1品目は同じ行のまま追従する(金額は編集済みなので残る)
        runBlocking { receipts.saveAndConfirm(receipts.getReceipt(id)!!.copy(totalAmount = 1300), emptyList()) }
        assertEquals(listOf(only.id), children(id).map { it.id })
        assertEquals(1500, children(id).single().amount)
        // 最後の1品目は消せない
        try {
            runBlocking { expenses.delete(children(id).single()) }
            fail("最後の品目は消せないはず")
        } catch (e: IllegalStateException) {
            assertEquals(ExpenseRepository.MESSAGE_LAST_ITEM, e.message)
        }
        assertEquals(1, children(id).size)
    }

    @Test
    fun unknownItems_markBlank_andAddDifference_rebuildTheLedger() {
        // 品目の金額が未入力(0円)のまま、合計 1000 の支払い
        val id = runBlocking { receipts.saveAndConfirm(receipt(total = 1000), listOf(item("牛乳", 200), item("何か", 0))) }
        // 0円の品目は家計簿に作らない。残った1品目は、支出の金額(合計 1000)と同じ金額になる
        assertEquals(listOf(1000), children(id).map { it.amount })
        // 未入力を全て不明にする: 何かが 800 を受け持つ
        assertNull(runBlocking { receipts.markBlankItemsUnknown(id) })
        assertEquals(listOf(true), runBlocking { receipts.getItems(id) }.filter { it.name == "何か" }.map { it.unknownAmount })
        assertEquals(listOf(200, 800), children(id).map { it.amount })
        // もう未入力は無い
        assertEquals(ReceiptRepository.MESSAGE_NO_BLANK_ITEM, runBlocking { receipts.markBlankItemsUnknown(id) })

        // 差額を不明の品目として足す: 未入力も不明も無く、合計が品目より多いとき
        val id2 = runBlocking { receipts.saveAndConfirm(receipt(total = 1000), listOf(item("牛乳", 200), item("洗剤", 500))) }
        assertNull(runBlocking { receipts.addDifferenceAsUnknownItem(id2) })
        val items2 = runBlocking { receipts.getItems(id2) }
        assertEquals(3, items2.size)
        assertTrue(items2.last().unknownAmount)
        assertEquals(listOf(200, 500, 300), children(id2).map { it.amount })
        // 不明の品目が既にあるので、もう足せない
        assertEquals(ReceiptRepository.MESSAGE_NO_DIFFERENCE, runBlocking { receipts.addDifferenceAsUnknownItem(id2) })
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
