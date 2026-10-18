package com.amgm.personallog.data.repository

import android.content.Context
import androidx.room.Room
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitParticipant
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
 * 割り勘の保存・読み込み・まとまりを、実際の Room DB(Robolectric の SQLite)で確かめる。
 * 外部キー(割当の削除の連鎖)は PRAGMA foreign_keys = ON にして確かめる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SplitRepositoryRobolectricTest {
    private lateinit var db: AppDatabase
    private lateinit var split: SplitRepository
    private lateinit var persons: PersonRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        split = SplitRepository(db, db.splitDao(), db.receiptDao())
        persons = PersonRepository(db.splitDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun item(name: String, amount: Int) = ReceiptItem(receiptId = 0, name = name, amount = amount, category = "食費")

    /** レシートと品目を入れて、レシートの id を返す。 */
    private fun receiptWith(items: List<ReceiptItem>, isAdvance: Boolean = false, ownAmount: Int? = null): Long = runBlocking {
        val id = db.receiptDao().insert(
            Receipt(
                purchasedAt = "2026-10-31 20:00", storeName = "店", totalAmount = null, imagePath = "", rawJson = "{}",
                createdAt = 0, isAdvance = isAdvance, ownAmount = ownAmount,
            ),
        )
        if (items.isNotEmpty()) db.receiptDao().insertItems(items.map { it.copy(receiptId = id) })
        id
    }

    private fun newParticipant(receiptId: Long, id: Long = -1, order: Int = 1) =
        SplitParticipant(id = id, receiptId = receiptId, personId = null, sortOrder = order)

    // ---- 読み込み・既定値 ----

    @Test
    fun load_newReceipt_hasOnlySelfParticipant_andSavesTheDefaultSheet() {
        runBlocking {
            val rid = receiptWith(listOf(item("牛乳", 200)))
            val data = split.load(rid)
            assertEquals(SplitMode.SELF, data.mode)
            assertEquals(ShareKind.AMOUNT, data.wholeKind)
            val self = persons.self()
            val only = data.participants.single()
            assertEquals(self.id, only.personId)
            assertEquals(self.id, data.persons.getValue(only.id).id)
            assertNotNull(db.splitDao().getSheet(rid))
        }
    }

    @Test
    fun load_legacyAdvanceWithoutSheet_returnsDefault_andDoesNotSave() {
        runBlocking {
            val rid = receiptWith(listOf(item("牛乳", 200)), isAdvance = true, ownAmount = 500)
            val data = split.load(rid)
            assertEquals(SplitMode.WHOLE, data.mode)
            assertEquals(ShareKind.AMOUNT, data.wholeKind)
            assertEquals(2, data.participants.size)
            val selfPart = data.participants[0]
            val nameless = data.participants[1]
            assertEquals(0L, selfPart.id)
            assertEquals(-1L, nameless.id)
            assertNull(nameless.personId)
            // 自分の分は旧い ownAmount、名前なしの人は空欄(割当の行が無い)
            assertEquals(500, data.wholeShares.single().amount)
            assertEquals(selfPart.id, data.wholeShares.single().participantId)
            assertEquals(1, data.groups.size)
            // 保存はしていない
            assertNull(db.splitDao().getSheet(rid))
            assertTrue(db.splitDao().getParticipants(rid).isEmpty())
        }
    }

    // ---- 保存 ----

    @Test
    fun save_registersNamedPeople_nameless_stayUnlinked_andSameNameIsSamePerson() {
        runBlocking {
            val rid = receiptWith(listOf(item("牛乳", 200)))
            val loaded = split.load(rid)
            val selfPart = loaded.participants.single()
            val data = loaded.copy(participants = loaded.participants + newParticipant(rid, -1, 1) + newParticipant(rid, -2, 2))
            val saved = split.save(data, names = mapOf(-1L to " 田中 ", -2L to "  "))

            assertEquals(3, saved.participants.size)
            assertTrue(saved.participants.all { it.id > 0 })
            val tanaka = db.splitDao().getPersonByName("田中")!!
            assertEquals(selfPart.personId, saved.participants[0].personId) // 自分は自分のまま
            assertEquals(tanaka.id, saved.participants[1].personId)
            assertNull(saved.participants[2].personId) // 空の名前は未登録

            // 同じ名前の別の支払いは、同じ人に付く(人は増えない)
            val rid2 = receiptWith(listOf(item("茶", 100)))
            val loaded2 = split.load(rid2)
            val saved2 = split.save(loaded2.copy(participants = loaded2.participants + newParticipant(rid2)), names = mapOf(-1L to "田中"))
            assertEquals(tanaka.id, saved2.participants.last().personId)
            assertEquals(2, db.splitDao().getPersons().size) // 自分 + 田中
        }
    }

    @Test
    fun save_remapsNewParticipantIds_inItemShares() {
        runBlocking {
            val rid = receiptWith(listOf(item("牛乳", 200)))
            val loaded = split.load(rid) // 読み込みで品目のまとまりができる
            val group = loaded.groups.single().group
            val data = loaded.copy(
                participants = loaded.participants + newParticipant(rid, -1, 1),
                itemShares = listOf(ItemShare(groupId = group.id, participantId = -1, kind = ShareKind.EQUAL)),
            )
            val saved = split.save(data, names = mapOf(-1L to "佐藤"))
            val sato = saved.participants.single { it.id != loaded.participants.single().id }
            assertTrue(sato.id > 0)
            val share = saved.itemShares.single()
            assertEquals(sato.id, share.participantId)
            assertEquals(ShareKind.EQUAL, share.kind)
            assertEquals(group.id, share.groupId)
        }
    }

    @Test
    fun save_removingParticipant_removesTheirShares() {
        runBlocking {
            val rid = receiptWith(listOf(item("牛乳", 200)))
            val loaded = split.load(rid)
            val group = loaded.groups.single().group
            val first = split.save(
                loaded.copy(
                    participants = loaded.participants + newParticipant(rid, -1, 1),
                    itemShares = listOf(ItemShare(groupId = group.id, participantId = -1, kind = ShareKind.EQUAL)),
                ),
                names = mapOf(-1L to "佐藤"),
            )
            assertEquals(1, db.splitDao().getItemShares(rid).size)
            // 佐藤を画面から消して保存する: 参加者も、その割当も消える
            split.save(first.copy(participants = first.participants.take(1)), names = emptyMap())
            assertEquals(1, db.splitDao().getParticipants(rid).size)
            assertTrue(db.splitDao().getItemShares(rid).isEmpty())
        }
    }

    // ---- まとまり ----

    @Test
    fun ensureGroups_sameItemsShareAGroup_sharedHintApplies_emptyGroupsAreRemoved() {
        runBlocking {
            val rid = receiptWith(
                listOf(item("辛味チキン", 300), item(" 辛味チキン", 300), item("ソーセージ", 150), item("袋", 5)),
            )
            val itemsBefore = db.receiptDao().getItems(rid)
            val bag = itemsBefore.single { it.name == "袋" }
            split.ensureGroups(rid, sharedHints = mapOf(bag.id to true))

            val groups = db.splitDao().getGroups(rid)
            assertEquals(3, groups.size)
            val membership = db.splitDao().getGroupItems(rid).associate { it.receiptItemId to it.groupId }
            val chicken = itemsBefore.filter { it.name.trim() == "辛味チキン" }.map { membership.getValue(it.id) }
            assertEquals(2, chicken.size)
            assertEquals(chicken[0], chicken[1])
            assertTrue(groups.single { it.id == membership.getValue(bag.id) }.isShared)

            // 品目を消すと、そのまとまりは空になるので消える
            db.receiptDao().deleteItemById(bag.id)
            split.ensureGroups(rid)
            assertEquals(2, db.splitDao().getGroups(rid).size)
        }
    }

    @Test
    fun ensureGroups_doesNotMoveItemsAlreadyInAGroup_evenWhenTheirAmountChanges() {
        runBlocking {
            val rid = receiptWith(listOf(item("牛乳", 200), item("洗剤", 500)))
            split.ensureGroups(rid)
            val sausage = db.receiptDao().getItems(rid).single { it.name == "洗剤" }
            val before = db.splitDao().getGroupItems(rid).single { it.receiptItemId == sausage.id }.groupId
            db.receiptDao().updateItem(sausage.copy(amount = 480, name = "洗剤(特売)"))
            split.ensureGroups(rid)
            val after = db.splitDao().getGroupItems(rid).single { it.receiptItemId == sausage.id }.groupId
            assertEquals(before, after)
        }
    }

    // ---- 人 ----

    @Test
    fun findOrCreate_trimsName_sameNameIsSamePerson_emptyNameIsRejected() {
        runBlocking {
            val a = persons.findOrCreate("  田中 ")
            val b = persons.findOrCreate("田中")
            assertEquals(a.id, b.id)
            assertEquals("田中", a.name)
            assertEquals("田", a.shortName)
            try {
                persons.findOrCreate("   ")
                fail("空の名前は受け付けないはず")
            } catch (e: IllegalArgumentException) {
                // 期待どおり
            }
        }
    }

    @Test
    fun personDelete_refusedWhenUsed_andSelfIsNeverDeleted() {
        runBlocking {
            val tanaka = persons.findOrCreate("田中")
            val rid = receiptWith(listOf(item("牛乳", 200)))
            val loaded = split.load(rid)
            split.save(loaded.copy(participants = loaded.participants + newParticipant(rid)), names = mapOf(-1L to "田中"))
            assertFalse(persons.delete(tanaka)) // 支払いの参加者として使われている
            val other = persons.findOrCreate("鈴木")
            assertTrue(persons.delete(other)) // 使われていなければ消せる
            assertFalse(persons.delete(persons.self()))
            assertEquals(2, db.splitDao().getPersons().size) // 自分 + 田中
        }
    }
}
