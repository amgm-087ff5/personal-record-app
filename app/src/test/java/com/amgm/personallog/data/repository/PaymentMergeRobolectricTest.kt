package com.amgm.personallog.data.repository

import android.content.Context
import androidx.room.Room
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.data.model.PaymentStatus
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.model.SplitSheet
import com.amgm.personallog.domain.PaymentMerge
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 重複した支払いのマージ(PaymentRepository.mergePayments)を、実際の Room DB(Robolectric の SQLite)で確かめる:
 * 中身をマージ先から採る / マージ元から採る / 状態と日時 / 返す画像パス。Plan はテストで直接組み立てる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PaymentMergeRobolectricTest {
    private lateinit var db: AppDatabase
    private lateinit var payments: PaymentRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        val splits = SplitRepository(db, db.splitDao(), db.paymentDao())
        payments = PaymentRepository(db, db.paymentDao(), db.expenseItemDao(), db.expenseDao(), splits)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun payment(image: String, at: String = "2026-10-31 20:00", createdAt: Long = 0) = Payment(
        id = 0, purchasedAt = at, storeName = "店", totalAmount = 700, imagePath = image, rawJson = "{}", createdAt = createdAt,
    )

    private fun item(name: String, amount: Int) = PaymentItem(id = 0, paymentId = 0, name = name, amount = amount, category = "食費")

    private fun makePayment(image: String, items: List<PaymentItem>, createdAt: Long): Long = runBlocking {
        payments.save(payment(image, createdAt = createdAt), items)
    }

    /** 割り勘の入力(シートと参加者1人)を付ける。 */
    private fun addSplit(paymentId: Long) = runBlocking {
        db.splitDao().upsertSheet(SplitSheet(paymentId = paymentId))
        db.splitDao().insertParticipant(SplitParticipant(paymentId = paymentId))
    }

    private fun planOf(target: Long, contentFrom: Long, removed: List<Long>, imageFrom: Long = target, edit: (Payment) -> Payment = { it }) =
        PaymentMerge.Plan(
            payment = edit(runBlocking { payments.getPayment(target)!! }),
            contentFrom = contentFrom,
            imageFrom = imageFrom,
            removed = removed,
        )

    private fun count(sql: String): Int =
        db.openHelper.writableDatabase.query(sql).use { c -> c.moveToFirst(); c.getInt(0) }

    @Test
    fun keepTargetContent_removesSourceAndLeavesNoOrphan() {
        val t = makePayment("t.jpg", listOf(item("牛乳", 200), item("洗剤", 500)), 1)
        val s = makePayment("s.jpg", listOf(item("パン", 700)), 2)
        addSplit(s)
        val targetItemIds = runBlocking { payments.getItems(t) }.map { it.id }

        runBlocking { payments.mergePayments(planOf(t, contentFrom = t, removed = listOf(s))) }

        assertNull(runBlocking { payments.getPayment(s) })
        assertTrue(runBlocking { payments.getItems(s) }.isEmpty())
        assertEquals(targetItemIds, runBlocking { payments.getItems(t) }.map { it.id })
        assertTrue(runBlocking { db.expenseDao().getByPaymentId(s) }.isEmpty())
        assertTrue(runBlocking { db.expenseItemDao().getByPaymentId(s) }.isEmpty())
        assertEquals(0, count("SELECT COUNT(*) FROM expenses WHERE paymentId IS NULL"))
        assertEquals(1, count("SELECT COUNT(*) FROM expenses"))
        assertEquals(0, count("SELECT COUNT(*) FROM split_sheets"))
        assertEquals(0, count("SELECT COUNT(*) FROM split_participants"))
        // 家計簿はマージ先の品目から作り直されている
        assertEquals(targetItemIds, runBlocking { db.expenseItemDao().getByPaymentId(t) }.map { it.paymentItemId })
    }

    @Test
    fun takeSourceContent_movesItemsAndSplitToTarget() {
        val t = makePayment("t.jpg", listOf(item("牛乳", 200)), 1)
        val s = makePayment("s.jpg", listOf(item("パン", 300), item("卵", 400)), 2)
        addSplit(s)
        val sourceItemIds = runBlocking { payments.getItems(s) }.map { it.id }
        val oldTargetItemIds = runBlocking { payments.getItems(t) }.map { it.id }

        runBlocking { payments.mergePayments(planOf(t, contentFrom = s, removed = listOf(s))) }

        val items = runBlocking { payments.getItems(t) }
        assertEquals(sourceItemIds, items.map { it.id })
        assertTrue(items.none { it.id in oldTargetItemIds })
        assertNotNull(runBlocking { db.splitDao().getSheet(t) })
        assertEquals(1, runBlocking { db.splitDao().getParticipants(t) }.size)
        assertEquals(1, count("SELECT COUNT(*) FROM split_sheets"))
        assertNull(runBlocking { payments.getPayment(s) })
        // 自分の支出は T の持ち物になり、孤児は残らない
        val groups = runBlocking { db.expenseDao().getByPaymentId(t) }
        assertEquals(1, groups.size)
        assertEquals(0, count("SELECT COUNT(*) FROM expenses WHERE paymentId IS NULL"))
        assertEquals(sourceItemIds, runBlocking { db.expenseItemDao().getByPaymentId(t) }.map { it.paymentItemId })
    }

    @Test
    fun savesStatusAndPurchasedAt() {
        val t = makePayment("t.jpg", listOf(item("牛乳", 700)), 1)
        val s = makePayment("s.jpg", listOf(item("牛乳", 700)), 2)

        runBlocking {
            payments.mergePayments(
                planOf(t, contentFrom = t, removed = listOf(s)) {
                    it.copy(purchasedAt = "2026-11-01 09:30", status = PaymentStatus.CONFIRMED)
                },
            )
        }

        val merged = runBlocking { payments.getPayment(t)!! }
        assertEquals("2026-11-01 09:30", merged.purchasedAt)
        assertEquals(PaymentStatus.CONFIRMED, merged.status)
        val group = runBlocking { db.expenseDao().getByPaymentId(t) }.single()
        assertEquals("2026-11-01", group.date)
        assertEquals("09:30", group.time)
    }

    @Test
    fun returnsUnusedImagePaths() {
        val t = makePayment("t.jpg", listOf(item("牛乳", 700)), 1)
        val s1 = makePayment("s1.jpg", listOf(item("牛乳", 700)), 2)
        val s2 = makePayment("s2.jpg", listOf(item("牛乳", 700)), 3)
        val s3 = makePayment("", listOf(item("牛乳", 700)), 4)

        // 画像はマージ元 s1 のものを採る: T の元の画像と、採らなかった s2 の画像が不要になる(空と、採った画像は返さない)
        val paths = runBlocking {
            payments.mergePayments(
                planOf(t, contentFrom = t, removed = listOf(s1, s2, s3), imageFrom = s1) { it.copy(imagePath = "s1.jpg") },
            )
        }

        assertEquals(setOf("t.jpg", "s2.jpg"), paths.toSet())
        assertEquals(paths.size, paths.distinct().size)
        assertEquals("s1.jpg", runBlocking { payments.getPayment(t)!!.imagePath })
    }

    @Test
    fun mergeSnapshots_ordersByCreatedAtAndSkipsMissing() {
        val a = makePayment("a.jpg", listOf(item("牛乳", 700)), 5)
        val b = makePayment("b.jpg", listOf(item("牛乳", 700)), 1)
        addSplit(a)

        val snaps = runBlocking { payments.mergeSnapshots(listOf(a, b, 9999)) }

        assertEquals(listOf(b, a), snaps.map { it.payment.id })
        assertEquals(listOf(false, true), snaps.map { it.hasSplit })
        assertEquals(1, snaps[0].items.size)
    }
}
