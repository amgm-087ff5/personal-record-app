package com.amgm.personallog.data.repository

import android.content.Context
import androidx.room.Room
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 支払い ⇒ 支出 の親子は、まとめて消す。支出だけ消して「子の無い支払い」を残すと、
 * 0円の支払いと区別できず、支払いの再保存・割り勘の変更で支出が復活していた(2026-10-29 の不具合)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DeletePaymentRobolectricTest {
    private lateinit var db: AppDatabase
    private lateinit var receipts: ReceiptRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val splits = SplitRepository(db, db.splitDao(), db.receiptDao())
        receipts = ReceiptRepository(db, db.receiptDao(), db.expenseDao(), db.expenseGroupDao(), splits)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun receipt(status: String = ReceiptStatus.DRAFT) = Receipt(
        id = 0, purchasedAt = "2026-10-29 12:00", storeName = "店", totalAmount = 700, imagePath = "receipts/x.jpg",
        rawJson = "{}", status = status, createdAt = 0,
    )

    private fun item(name: String, amount: Int) = ReceiptItem(id = 0, receiptId = 0, name = name, amount = amount, category = "食費")

    @Test
    fun deletePayment_removesPaymentExpenseAndItems_together() = runBlocking {
        val id = receipts.saveAndConfirm(receipt(), listOf(item("牛乳", 200), item("洗剤", 500)))
        assertEquals(1, db.expenseGroupDao().getByReceiptId(id).size)

        val deleted = receipts.deletePayment(id)

        assertEquals("receipts/x.jpg", deleted?.imagePath) // 画像の削除に使う
        assertNull(db.receiptDao().getById(id))
        assertTrue(db.receiptDao().getItems(id).isEmpty())
        assertTrue(db.expenseGroupDao().getByReceiptId(id).isEmpty())
        assertTrue(db.expenseDao().getByReceiptId(id).isEmpty())
        // 支出だけが残る・支払いだけが残る、のどちらも無い
        assertTrue(db.expenseGroupDao().observeAll().first().isEmpty())
        assertTrue(db.receiptDao().getAll().isEmpty())
    }

    @Test
    fun deletingTheParentPayment_cascadesToExpenses() = runBlocking {
        val id = receipts.saveAndConfirm(receipt(), listOf(item("牛乳", 700)))
        // 外部キー(CASCADE): 支払いの行を消すだけでも支出・品目は残らない
        db.receiptDao().delete(db.receiptDao().getById(id)!!)
        assertTrue(db.expenseGroupDao().getByReceiptId(id).isEmpty())
        assertTrue(db.expenseDao().getAll().isEmpty())
    }

    @Test
    fun draftWithoutExpense_getsItsExpenseOnRepair() = runBlocking {
        // 古い版などで支出の無いまま残った下書き: 起動時の作り直しで、確認待ちの支出として家計簿に出る
        val id = db.receiptDao().insert(receipt())
        db.receiptDao().insertItem(item("パン", 700).copy(receiptId = id))
        assertEquals(listOf(id), db.receiptDao().draftIdsWithoutExpense())

        receipts.repairDraftsWithoutExpense()

        assertEquals(700, db.expenseGroupDao().getByReceiptId(id).single().amount)
        assertTrue(db.receiptDao().draftIdsWithoutExpense().isEmpty())
    }
}
