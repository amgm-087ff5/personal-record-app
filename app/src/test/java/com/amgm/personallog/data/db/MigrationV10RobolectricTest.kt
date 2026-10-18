package com.amgm.personallog.data.db

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.PaymentPayer
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.ShareGroupItem
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitSheet
import com.amgm.personallog.data.model.WholeShare
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.io.File

/**
 * v9 -> v10 の移行を、本物の SQLite(Robolectric)で実行する。
 * 移行前の DB は app/schemas の 9.json(Room の生成物)の createSql・indices で作る。
 * 移行は Room(Migrations.ALL)に開かせて走らせ、割り勘の表の動きは SplitDao を通して確かめる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV10RobolectricTest {
    private lateinit var context: Context
    private val name = AppDatabase.NAME

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private val dbFile: File get() = context.getDatabasePath(name)

    /** 移行前の版(9)の CREATE 文を 9.json から読む(テーブルとインデックス)。 */
    private fun schema9(): List<String> {
        val rel = "schemas/com.amgm.personallog.data.db.AppDatabase/9.json"
        val file = listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile } ?: error("9.json が見つかりません")
        val entities = Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
        val sqls = ArrayList<String>()
        for (e in entities) {
            val o = e.jsonObject
            val table = o["tableName"]!!.jsonPrimitive.content
            sqls += o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            (o["indices"] as? JsonArray).orEmpty().forEach {
                sqls += it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            }
        }
        return sqls
    }

    private fun helper(
        version: Int,
        created: (SupportSQLiteDatabase) -> Unit = {},
        upgraded: (SupportSQLiteDatabase, Int) -> Unit = { _, _ -> },
    ): SupportSQLiteOpenHelper {
        val cb = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(false)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = created(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = upgraded(db, oldVersion)
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.path).callback(cb).build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }
    private fun SupportSQLiteDatabase.str(sql: String): String = query(sql).use { it.moveToFirst(); it.getString(0) }

    /** 移行前(v9)の DB を作る。レシート2・品目3・グループ2・支出3(v9 の列を全部指定する)。 */
    private fun createV9() {
        val h = helper(9, created = { db -> schema9().forEach { db.execSQL(it) } })
        val db = h.writableDatabase
        db.execSQL(
            "INSERT INTO receipts (id, purchasedAt, storeName, totalAmount, imagePath, rawJson, status, createdAt) " +
                "VALUES (1, '2026-10-09 18:32', '店', 748, 'receipts/1.jpg', '{}', 'confirmed', 1000)",
        )
        db.execSQL(
            "INSERT INTO receipts (id, purchasedAt, storeName, totalAmount, imagePath, rawJson, status, createdAt) " +
                "VALUES (2, '2026-10-10 09:00', '店2', 300, 'receipts/2.jpg', '{}', 'confirmed', 2000)",
        )
        for (rid in listOf(1, 1, 2)) {
            db.execSQL(
                "INSERT INTO receipt_items (receiptId, name, amount, category, taxRate, priceIncludesTax, memo) " +
                    "VALUES ($rid, '品', 100, '食費', 10, 1, '')",
            )
        }
        db.execSQL(
            "INSERT INTO expense_groups (id, date, time, amount, category, memo, receiptId, createdAt, uid) " +
                "VALUES (1, '2026-10-09', '18:32', 748, '食費', 'g1', 1, 1, 'gu-1')",
        )
        db.execSQL(
            "INSERT INTO expense_groups (id, date, time, amount, category, memo, receiptId, createdAt, uid) " +
                "VALUES (2, '2026-10-10', '09:00', 300, '交通', 'g2', NULL, 2, 'gu-2')",
        )
        db.execSQL(
            "INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, timeEdited, uid) " +
                "VALUES (1, '2026-10-09', '18:32', 400, '食費', 'e1', 1, 1, 1, 1, 'eu-1')",
        )
        db.execSQL(
            "INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, uid) " +
                "VALUES (2, '2026-10-09', '18:32', 300, '食費', 'e2', 2, 2, 'eu-2')",
        )
        db.execSQL(
            "INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, uid) " +
                "VALUES (3, '2026-10-11', '', 120, '日用品', 'e3', NULL, 3, 'eu-3')",
        )
        h.close()
    }

    /** Room で開く(移行は Migrations.ALL で走る。Room の検証もここで走る)。 */
    private fun openRoom(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*Migrations.ALL)
        .allowMainThreadQueries()
        .build()

    /** レシート1に、友人1人(品目ごと・全体・支払内訳)の割り勘を入れる。友人の id を返す。 */
    private fun seedSplit(room: AppDatabase): Long = runBlocking {
        val dao = room.splitDao()
        val self = dao.getSelf()!!
        val friend = dao.insertPerson(Person(name = "友人", shortName = "友", createdAt = 2L))
        dao.upsertSheet(SplitSheet(receiptId = 1L, mode = SplitMode.ITEM))
        val gid = dao.insertGroup(ShareGroup(receiptId = 1L, pieceCount = 2))
        dao.insertGroupItems(listOf(ShareGroupItem(groupId = gid, receiptItemId = 1L)))
        dao.insertItemShares(listOf(ItemShare(groupId = gid, personId = friend, kind = ShareKind.EQUAL)))
        dao.insertWholeShares(listOf(WholeShare(receiptId = 1L, personId = friend, kind = ShareKind.RATIO, weight = 2)))
        dao.insertPayers(listOf(PaymentPayer(receiptId = 1L, personId = self.id, amount = 748)))
        friend
    }

    // ---- テスト ----

    @Test
    fun migration_keepsExistingCounts_andCreatesSevenTables() {
        createV9()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase // 移行と Room の検証がここで走る。通らなければ例外
            assertEquals(AppDatabase.VERSION, db.version)
            assertEquals(2L, db.long("SELECT COUNT(*) FROM receipts"))
            assertEquals(3L, db.long("SELECT COUNT(*) FROM receipt_items"))
            assertEquals(2L, db.long("SELECT COUNT(*) FROM expense_groups"))
            assertEquals(3L, db.long("SELECT COUNT(*) FROM expenses"))
            // 既存の値とつながりが保たれる
            assertEquals("e1", db.str("SELECT memo FROM expenses WHERE id = 1"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM expenses e JOIN expense_groups g ON e.groupId = g.id WHERE e.id = 1"))
            // 新しい7表が出来ている
            for (t in Migrations.SPLIT_V10_TABLES) {
                assertEquals(t, 1L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$t'"))
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_createsExactlyOneSelfPerson() {
        createV9()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(1L, db.long("SELECT COUNT(*) FROM persons"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM persons WHERE isSelf = 1"))
            assertEquals("自分", db.str("SELECT name FROM persons WHERE isSelf = 1"))
            assertEquals("自", db.str("SELECT shortName FROM persons WHERE isSelf = 1"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM persons WHERE uid IS NULL OR uid = ''"))
            val self = runBlocking { room.splitDao().getSelf() }
            assertNotNull(self)
            assertTrue(self!!.isSelf)
        } finally {
            room.close()
        }
    }

    @Test
    fun migrationStep_rerun_doesNotAddSecondSelf() {
        createV9()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            Migrations.MIGRATION_9_10.migrate(db) // 同じ移行をもう一度流しても、自分は増えない・既存の件数も変わらない
            assertEquals(1L, db.long("SELECT COUNT(*) FROM persons WHERE isSelf = 1"))
            assertEquals(2L, db.long("SELECT COUNT(*) FROM receipts"))
        } finally {
            room.close()
        }
    }

    @Test
    fun emptySplitTables_areUsableThroughSplitDao() {
        createV9()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            val friend = seedSplit(room)
            val dao = room.splitDao()
            runBlocking {
                assertEquals(SplitMode.ITEM, dao.getSheet(1L)!!.mode)
                assertEquals(1, dao.getGroups(1L).size)
                assertEquals(1, dao.getGroupItems(1L).size)
                assertEquals(1, dao.getItemShares(1L).size)
                assertEquals(friend, dao.getItemShares(1L).single().personId)
                assertEquals(1, dao.getWholeShares(1L).size)
                assertEquals(1, dao.getPayers(1L).size)
                assertEquals(2, dao.getPersons().size)
                assertEquals(true, dao.getPersons().first().isSelf) // 自分が先頭
            }
            assertEquals(1L, db.long("SELECT COUNT(*) FROM share_group_items WHERE receiptItemId = 1"))
        } finally {
            room.close()
        }
    }

    @Test
    fun deletingReceipt_cascadesToSplitTables_butKeepsPersons() {
        createV9()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            seedSplit(room)
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM receipts WHERE id = 1")
            for (t in listOf("split_sheets", "share_groups", "share_group_items", "item_shares", "whole_shares", "payment_payers")) {
                assertEquals(t, 0L, db.long("SELECT COUNT(*) FROM $t"))
            }
            // 人は支払いに紐づかないので残る
            assertEquals(2L, db.long("SELECT COUNT(*) FROM persons"))
            // 既存の支出は SET NULL で残る(従来どおり)
            assertEquals(3L, db.long("SELECT COUNT(*) FROM expenses"))
        } finally {
            room.close()
        }
    }

    @Test
    fun deletingPersonWhichIsAssigned_isBlockedByForeignKey() {
        createV9()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            seedSplit(room)
            db.execSQL("PRAGMA foreign_keys = ON")
            try {
                db.execSQL("DELETE FROM persons WHERE isSelf = 0") // 割当から参照されている友人は消せない
                fail("割当から参照されている人は削除できないはず")
            } catch (e: SQLiteException) {
                assertTrue(e.message.orEmpty().contains("FOREIGN KEY"))
            }
            assertEquals(2L, db.long("SELECT COUNT(*) FROM persons"))
        } finally {
            room.close()
        }
    }
}
