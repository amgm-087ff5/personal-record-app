package com.amgm.personallog.data.repository

import android.content.Context
import androidx.room.Room
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.data.model.PaymentStatus
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
    private lateinit var payments: PaymentRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val splits = SplitRepository(db, db.splitDao(), db.paymentDao())
        payments = PaymentRepository(db, db.paymentDao(), db.expenseItemDao(), db.expenseDao(), splits)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun payment(status: String = PaymentStatus.DRAFT) = Payment(
        id = 0, purchasedAt = "2026-10-29 12:00", storeName = "店", totalAmount = 700, imagePath = "receipts/x.jpg",
        rawJson = "{}", status = status, createdAt = 0,
    )

    private fun item(name: String, amount: Int) = PaymentItem(id = 0, paymentId = 0, name = name, amount = amount, category = "食費")

    @Test
    fun deletePayment_removesPaymentExpenseAndItems_together() = runBlocking {
        val id = payments.saveAndConfirm(payment(), listOf(item("牛乳", 200), item("洗剤", 500)))
        assertEquals(1, db.expenseDao().getByPaymentId(id).size)

        val deleted = payments.deletePayment(id)

        assertEquals("receipts/x.jpg", deleted?.imagePath) // 画像の削除に使う
        assertNull(db.paymentDao().getById(id))
        assertTrue(db.paymentDao().getItems(id).isEmpty())
        assertTrue(db.expenseDao().getByPaymentId(id).isEmpty())
        assertTrue(db.expenseItemDao().getByPaymentId(id).isEmpty())
        // 支出だけが残る・支払いだけが残る、のどちらも無い
        assertTrue(db.expenseDao().observeAll().first().isEmpty())
        assertTrue(db.paymentDao().getAll().isEmpty())
    }

    @Test
    fun deletingTheParentPayment_cascadesToExpenses() = runBlocking {
        val id = payments.saveAndConfirm(payment(), listOf(item("牛乳", 700)))
        // 外部キー(CASCADE): 支払いの行を消すだけでも支出・品目は残らない
        db.paymentDao().delete(db.paymentDao().getById(id)!!)
        assertTrue(db.expenseDao().getByPaymentId(id).isEmpty())
        assertTrue(db.expenseItemDao().getAll().isEmpty())
    }

    @Test
    fun draftWithoutExpense_getsItsExpenseOnRepair() = runBlocking {
        // 古い版などで支出の無いまま残った下書き: 起動時の作り直しで、確認待ちの支出として家計簿に出る
        val id = db.paymentDao().insert(payment())
        db.paymentDao().insertItem(item("パン", 700).copy(paymentId = id))
        assertEquals(listOf(id), db.paymentDao().draftIdsWithoutExpense())

        payments.repairDraftsWithoutExpense()

        assertEquals(700, db.expenseDao().getByPaymentId(id).single().amount)
        assertTrue(db.paymentDao().draftIdsWithoutExpense().isEmpty())
    }
}
