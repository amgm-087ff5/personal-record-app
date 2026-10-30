package com.amgm.personallog.data.repository

import android.content.Context
import androidx.room.Room
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 支払いの確定の判定([PaymentConfirmer])を、実際の Room DB(Robolectric の SQLite)で確かめる。
 * 支払いの画面と支出の画面が同じ判定を使うので、ここで結果の種類を固定する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PaymentConfirmerRobolectricTest {
    private lateinit var db: AppDatabase
    private lateinit var receipts: ReceiptRepository
    private lateinit var confirmer: PaymentConfirmer

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val splits = SplitRepository(db, db.splitDao(), db.receiptDao())
        receipts = ReceiptRepository(db, db.receiptDao(), db.expenseDao(), db.expenseGroupDao(), splits)
        confirmer = PaymentConfirmer(receipts)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun receipt(total: Int? = 700, at: String = "2026-10-31 20:00") = Receipt(
        id = 0, purchasedAt = at, storeName = "店", totalAmount = total, imagePath = "", rawJson = "{}", createdAt = 0,
    )

    private fun items() = listOf(
        ReceiptItem(receiptId = 0, name = "牛乳", amount = 200, category = "食費"),
        ReceiptItem(receiptId = 0, name = "洗剤", amount = 500, category = "食費"),
    )

    /** 下書きを保存して、(支払い, 品目)を返す。 */
    private fun draft(total: Int? = 700): Pair<Receipt, List<ReceiptItem>> = runBlocking {
        val id = receipts.save(receipt(total), items())
        receipts.getReceipt(id)!! to receipts.getItems(id)
    }

    @Test
    fun confirmWithTotalBecomesConfirmed() = runBlocking {
        val (r, its) = draft()
        val outcome = confirmer.confirm(r, its, checkDuplicate = true)
        assertEquals(ConfirmOutcome.Confirmed, outcome)
        assertEquals(ReceiptStatus.CONFIRMED, receipts.getReceipt(r.id)!!.status)
    }

    @Test
    fun withoutTotalReturnsMissingTotalWithItemsSum() = runBlocking {
        val (r, its) = draft(total = null)
        val outcome = confirmer.confirm(r, its, checkDuplicate = true)
        // 品目の計算合計(200 + 500)を返す。支払いは確定されない
        assertEquals(ConfirmOutcome.MissingTotal(700L), outcome)
        assertEquals(ReceiptStatus.DRAFT, receipts.getReceipt(r.id)!!.status)
    }

    @Test
    fun sameContentConfirmedPaymentReturnsDuplicateDecision() = runBlocking {
        // 先に確定した同じ内容の支払い
        val first = receipts.saveAndConfirm(receipt(), items())
        val (r, its) = draft()
        val outcome = confirmer.confirm(r, its, checkDuplicate = true)
        assertTrue(outcome is ConfirmOutcome.NeedsDuplicateDecision)
        val dups = (outcome as ConfirmOutcome.NeedsDuplicateDecision).duplicates
        assertEquals(listOf(first), dups.map { it.id })
        // 重複の判定で止めた支払いは、確定されない
        assertEquals(ReceiptStatus.DRAFT, receipts.getReceipt(r.id)!!.status)
    }

    @Test
    fun duplicateCheckSkippedWhenRequestedToContinue() = runBlocking {
        receipts.saveAndConfirm(receipt(), items())
        val (r, its) = draft()
        // 「追加する」を選んだあとは重複を調べない
        assertEquals(ConfirmOutcome.Confirmed, confirmer.confirm(r, its, checkDuplicate = false))
    }
}
