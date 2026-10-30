package com.amgm.personallog.data.repository

import android.content.Context
import androidx.room.Room
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.domain.PaymentValues
import com.amgm.personallog.domain.PaymentWriteBack
import com.amgm.personallog.domain.WriteBackTarget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 支出の画面で直した欄が支払い・品目へ書き戻り、家計簿を作り直しても同じ値のまま残ることを、実際の Room DB で確かめる(往復)。
 * 支払いで直した欄は、支出へ追従する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PaymentWriteBackRobolectricTest {
    private lateinit var db: AppDatabase
    private lateinit var splits: SplitRepository
    private lateinit var receipts: ReceiptRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        splits = SplitRepository(db, db.splitDao(), db.receiptDao())
        receipts = ReceiptRepository(db, db.receiptDao(), db.expenseDao(), db.expenseGroupDao(), splits)
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

    /** 牛乳200・洗剤500(合計700・手入力)を保存して確定し、(支払いの id, 品目)を返す。 */
    private fun confirmedReceipt(): Pair<Long, List<ReceiptItem>> = runBlocking {
        val id = receipts.saveAndConfirm(receipt(), listOf(item("牛乳", 200), item("洗剤", 500)))
        id to receipts.getItems(id)
    }

    private fun children(receiptId: Long): List<Expense> = runBlocking { db.expenseDao().getByReceiptId(receiptId) }
    private fun groups(receiptId: Long): List<ExpenseGroup> = runBlocking { db.expenseGroupDao().getByReceiptId(receiptId) }
    private fun receiptOf(id: Long): Receipt = runBlocking { receipts.getReceipt(id)!! }
    private fun itemsOf(id: Long): List<ReceiptItem> = runBlocking { receipts.getItems(id) }
    private fun sync(id: Long) = runBlocking { receipts.syncLedger(id) }

    private fun writeBack(target: WriteBackTarget, values: PaymentValues): String? =
        runBlocking { receipts.writeBackFromLedger(target, values) }

    @Test
    fun itemEditedInLedgerWritesBackToItemAndSurvivesSync() {
        val (id, _) = confirmedReceipt()
        val milk = children(id)[0]
        val err = writeBack(
            WriteBackTarget.Item(milk),
            PaymentValues(milk.date, milk.time, milk.amount, "外食", "牛乳(特売)", "自分で"),
        )
        assertNull(err)
        // 支払い(品目)が変わる
        val item = itemsOf(id).first { it.id == milk.receiptItemId }
        assertEquals("外食", item.category)
        assertEquals("牛乳(特売)", item.name)
        assertEquals("自分で", item.memo)
        // 家計簿を作り直しても、支出は同じ値のまま(編集済みが下りている)
        sync(id)
        val m = children(id)[0]
        assertEquals("牛乳(特売)", m.name)
        assertEquals("外食", m.category)
        assertEquals("自分で", m.memo)
        assertEquals(200, m.amount)
        assertFalse(m.nameEdited || m.categoryEdited || m.memoEdited || m.amountEdited)
    }

    @Test
    fun groupDateEditedInLedgerWritesPurchasedAtAndFollowsIntoItems() {
        val (id, _) = confirmedReceipt()
        val g = groups(id).single()
        val err = writeBack(
            WriteBackTarget.Group(g),
            PaymentValues("2026-11-01", "09:30", g.amount, g.category, "", g.memo),
        )
        assertNull(err)
        assertEquals("2026-11-01 09:30", receiptOf(id).purchasedAt)
        sync(id)
        val after = groups(id).single()
        assertEquals("2026-11-01", after.date)
        assertEquals("09:30", after.time)
        assertFalse(after.timeEdited)
        children(id).forEach {
            assertEquals("2026-11-01", it.date)
            assertEquals("09:30", it.time)
        }
    }

    @Test
    fun paymentEditedAfterwardFollowsIntoLedger() {
        val (id, items) = confirmedReceipt()
        val r = receiptOf(id)
        runBlocking {
            receipts.saveAndConfirm(
                r.copy(purchasedAt = "2026-11-01 09:30"),
                listOf(items[0].copy(name = "牛乳(特売)", category = "日用品"), items[1]),
            )
        }
        val m = children(id)[0]
        assertEquals("牛乳(特売)", m.name)
        assertEquals("日用品", m.category)
        assertEquals("2026-11-01", groups(id).single().date)
        assertEquals("09:30", groups(id).single().time)
    }

    @Test
    fun taxExcludedItemAmountIsWrittenAsTaxIncludedAndRoundTrips() {
        // 税抜の牛乳 200(税込 220)+ 洗剤 500 = 720。手入力なので、合計も品目の合計に合わせる
        val id = runBlocking {
            receipts.saveAndConfirm(receipt(total = 720), listOf(item("牛乳", 200).copy(priceIncludesTax = false), item("洗剤", 500)))
        }
        val milk = children(id)[0]
        assertEquals(220, milk.amount)
        assertNull(writeBack(WriteBackTarget.Item(milk), PaymentValues(milk.date, milk.time, 250, milk.category, milk.name, milk.memo)))
        val changed = itemsOf(id).first { it.id == milk.receiptItemId }
        assertTrue(changed.priceIncludesTax)
        assertEquals(250, changed.amount)
        assertEquals(750, receiptOf(id).totalAmount)
        sync(id)
        assertEquals(250, children(id)[0].amount)
        sync(id) // 往復しても変わらない
        assertEquals(250, children(id)[0].amount)
    }

    @Test
    fun printedReceiptTotalIsKeptWhenItemAmountChanges() {
        val id = runBlocking {
            receipts.saveAndConfirm(receipt(total = 700).copy(imagePath = "receipts/r1.jpg"), listOf(item("牛乳", 200), item("洗剤", 500)))
        }
        val milk = children(id)[0]
        assertNull(writeBack(WriteBackTarget.Item(milk), PaymentValues(milk.date, milk.time, 250, milk.category, milk.name, milk.memo)))
        assertEquals(700, receiptOf(id).totalAmount) // 印字の合計は変えない
        sync(id)
        assertEquals(250, children(id)[0].amount)
        assertEquals(700, groups(id).single().amount)
    }

    @Test
    fun groupAmountEditedInLedgerWritesManualTotal() {
        val (id, _) = confirmedReceipt()
        val g = groups(id).single()
        assertNull(writeBack(WriteBackTarget.Group(g), PaymentValues(g.date, g.time, 800, g.category, "", g.memo)))
        assertEquals(800, receiptOf(id).totalAmount)
        sync(id)
        val after = groups(id).single()
        assertEquals(800, after.amount)
        assertFalse(after.amountEdited)
    }

    @Test
    fun amountWriteBackIsRejectedWhenSplitHasTwoPeople() {
        val (id, _) = confirmedReceipt()
        runBlocking {
            splits.load(id) // 自分の参加者とシートを作る
            db.splitDao().insertParticipant(SplitParticipant(receiptId = id, personId = null, label = "友人", sortOrder = 9))
        }
        assertEquals(2, runBlocking { receipts.participantCount(id) })
        val milk = children(id)[0]
        val err = writeBack(WriteBackTarget.Item(milk), PaymentValues(milk.date, milk.time, 250, milk.category, milk.name, milk.memo))
        assertEquals(PaymentWriteBack.MESSAGE_SPLIT_AMOUNT, err)
        // 何も書かれていない
        assertEquals(200, itemsOf(id).first { it.id == milk.receiptItemId }.amount)
        assertEquals(200, children(id)[0].amount)
    }

    @Test
    fun advanceGroupAmountWritesOwnAmountAndFollowsInTheLedger() {
        // 立て替え: 支出の金額は自分の分(ownAmount)。直すと支払いの自分の分へ入り、合計は変わらない
        val id = runBlocking {
            receipts.saveAndConfirm(receipt().copy(isAdvance = true, ownAmount = 300), listOf(item("牛乳", 200), item("洗剤", 500)))
        }
        val g = groups(id).single()
        assertEquals(300, g.amount)
        assertNull(writeBack(WriteBackTarget.Group(g), PaymentValues(g.date, g.time, 400, g.category, "", g.memo)))
        assertEquals(400, receiptOf(id).ownAmount)
        assertEquals(700, receiptOf(id).totalAmount)
        assertEquals(400, groups(id).single().amount)
        assertEquals(400, children(id).sumOf { it.amount })
        sync(id) // 往復しても変わらない
        assertEquals(400, groups(id).single().amount)
        assertFalse(groups(id).single().amountEdited)
    }

    @Test
    fun printedGroupAmountWritesManualTotalAndKeepsImage() {
        val id = runBlocking {
            receipts.saveAndConfirm(
                receipt(total = 700).copy(imagePath = "receipts/r1.jpg", rawJson = "{\"raw\":1}", totalSource = TotalSource.PRINTED),
                listOf(item("牛乳", 200), item("洗剤", 500)),
            )
        }
        val g = groups(id).single()
        assertNull(writeBack(WriteBackTarget.Group(g), PaymentValues(g.date, g.time, 650, g.category, "", g.memo)))
        val r = receiptOf(id)
        assertEquals(650, r.totalAmount)
        assertEquals(TotalSource.MANUAL, r.totalSource)
        assertEquals("receipts/r1.jpg", r.imagePath)
        assertEquals("{\"raw\":1}", r.rawJson)
        assertEquals(700, itemsOf(id).sumOf { it.amount }) // 品目は変えない(差額は表示に任せる)
        sync(id)
        assertEquals(650, groups(id).single().amount)
    }

    @Test
    fun onlyItemAmountWritesTotalAndTheItemAndFollowsInTheLedger() {
        val id = runBlocking { receipts.saveAndConfirm(receipt(total = 200), listOf(item("牛乳", 200))) }
        val milk = children(id).single()
        assertNull(writeBack(WriteBackTarget.Item(milk, onlyItem = true), PaymentValues(milk.date, milk.time, 250, milk.category, milk.name, milk.memo)))
        assertEquals(250, receiptOf(id).totalAmount)
        assertEquals(TotalSource.MANUAL, receiptOf(id).totalSource)
        assertEquals(250, itemsOf(id).single().amount)
        assertEquals(250, groups(id).single().amount)
        assertEquals(250, children(id).single().amount)
        sync(id)
        assertEquals(250, groups(id).single().amount)
        assertEquals(250, children(id).single().amount)
    }

    @Test
    fun onlyItemAmountOfPrintedReceiptWritesManualTotal() {
        val id = runBlocking {
            receipts.saveAndConfirm(receipt(total = 200).copy(imagePath = "receipts/r2.jpg"), listOf(item("牛乳", 200)))
        }
        val milk = children(id).single()
        assertNull(writeBack(WriteBackTarget.Item(milk, onlyItem = true), PaymentValues(milk.date, milk.time, 180, milk.category, milk.name, milk.memo)))
        assertEquals(180, receiptOf(id).totalAmount)
        assertEquals(TotalSource.MANUAL, receiptOf(id).totalSource)
        assertEquals("receipts/r2.jpg", receiptOf(id).imagePath)
        assertEquals(180, itemsOf(id).single().amount)
        assertEquals(180, groups(id).single().amount)
    }

    @Test
    fun onlyItemAmountOfAdvanceWritesOwnAmount() {
        val id = runBlocking {
            receipts.saveAndConfirm(receipt(total = 700).copy(isAdvance = true, ownAmount = 200), listOf(item("牛乳", 700)))
        }
        val milk = children(id).single()
        assertEquals(200, milk.amount)
        assertNull(writeBack(WriteBackTarget.Item(milk, onlyItem = true), PaymentValues(milk.date, milk.time, 260, milk.category, milk.name, milk.memo)))
        assertEquals(260, receiptOf(id).ownAmount)
        assertEquals(700, receiptOf(id).totalAmount)
        assertEquals(700, itemsOf(id).single().amount)
        assertEquals(260, groups(id).single().amount)
        assertEquals(260, children(id).single().amount)
    }

    @Test
    fun onlyItemAmountWithEditedGroupAmountStillFollows() {
        // まとまりの金額が編集済みでも、品目の金額の書き戻しで支出の金額が追従する
        val id = runBlocking { receipts.saveAndConfirm(receipt(total = 200), listOf(item("牛乳", 200))) }
        runBlocking { db.expenseGroupDao().update(groups(id).single().copy(amountEdited = true)) }
        val milk = children(id).single()
        assertNull(writeBack(WriteBackTarget.Item(milk, onlyItem = true), PaymentValues(milk.date, milk.time, 250, milk.category, milk.name, milk.memo)))
        assertEquals(250, groups(id).single().amount)
        assertFalse(groups(id).single().amountEdited)
    }

    @Test
    fun groupAmountWithoutSplitSheetIsNotBlockedByAdvanceLegacyDefault() {
        // 旧い立て替え(割り勘シートなし)を、割り勘2人と数えて拒否しない(画面の splitPeople と同じ 1 人)
        val id = runBlocking { receipts.saveAndConfirm(receipt().copy(isAdvance = true, ownAmount = 300), listOf(item("牛乳", 200), item("洗剤", 500))) }
        assertEquals(1, runBlocking { receipts.participantCount(id) })
        val g = groups(id).single()
        assertNull(writeBack(WriteBackTarget.Group(g), PaymentValues(g.date, g.time, 350, g.category, "", g.memo)))
        assertEquals(350, receiptOf(id).ownAmount)
    }

    @Test
    fun groupAmountIsRejectedWhenSplitHasTwoPeople() {
        val (id, _) = confirmedReceipt()
        runBlocking {
            splits.load(id)
            db.splitDao().insertParticipant(SplitParticipant(receiptId = id, personId = null, label = "友人", sortOrder = 9))
        }
        val g = groups(id).single()
        assertEquals(
            PaymentWriteBack.MESSAGE_SPLIT_AMOUNT,
            writeBack(WriteBackTarget.Group(g), PaymentValues(g.date, g.time, g.amount + 100, g.category, "", g.memo)),
        )
        assertEquals(700, receiptOf(id).totalAmount)
    }
}
