package com.amgm.personallog.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * v10 -> v11 の移行を、本物の SQLite(Robolectric)で実行する。
 * 移行前の DB は app/schemas の 10.json(Room の生成物)の createSql・indices で作り、旧い割り当て(personId)を入れておく。
 * 移行は Room(Migrations.ALL)に開かせて走らせ、Room の検証と SplitDao の読み取りもここで確かめる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV11RobolectricTest {
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

    /** 移行前(v10)の CREATE 文を 10.json から読む(テーブルとインデックス)。 */
    private fun schema10(): List<String> {
        val rel = "schemas/com.amgm.personallog.data.db.AppDatabase/10.json"
        val file = listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile } ?: error("10.json が見つかりません")
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

    private fun helper(version: Int, created: (SupportSQLiteDatabase) -> Unit = {}): SupportSQLiteOpenHelper {
        val cb = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(false)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = created(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.path).callback(cb).build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }
    private fun SupportSQLiteDatabase.str(sql: String): String = query(sql).use { it.moveToFirst(); it.getString(0) }

    /**
     * v10 の DB を作り、割り当てを入れて閉じる。
     * レシート1: 自分・A・B の割り勘(品目ごとの割当2まとまり、全体の割当、支払内訳は自分)。
     * レシート2: B だけの全体の割当(金額 300)。
     */
    private fun createV10() {
        val h = helper(10, created = { db -> schema10().forEach { db.execSQL(it) } })
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
        db.execSQL("INSERT INTO persons (id, uid, name, shortName, isSelf, sortOrder, createdAt) VALUES (1, 'self-uid', '自分', '自', 1, 0, 1)")
        db.execSQL("INSERT INTO persons (id, uid, name, shortName, isSelf, sortOrder, createdAt) VALUES (2, 'a-uid', 'A', 'A', 0, 1, 2)")
        db.execSQL("INSERT INTO persons (id, uid, name, shortName, isSelf, sortOrder, createdAt) VALUES (3, 'b-uid', 'B', 'B', 0, 2, 3)")
        db.execSQL("INSERT INTO split_sheets (receiptId, mode) VALUES (1, 'item')")
        db.execSQL("INSERT INTO split_sheets (receiptId, mode) VALUES (2, 'whole')")
        db.execSQL("INSERT INTO share_groups (id, receiptId, pieceCount, isShared, sortOrder) VALUES (1, 1, 2, 0, 0)")
        db.execSQL("INSERT INTO share_groups (id, receiptId, pieceCount, isShared, sortOrder) VALUES (2, 1, NULL, 1, 1)")
        db.execSQL("INSERT INTO share_group_items (groupId, receiptItemId) VALUES (1, 1)")
        db.execSQL("INSERT INTO share_group_items (groupId, receiptItemId) VALUES (2, 2)")
        db.execSQL("INSERT INTO item_shares (groupId, personId, kind, weight, amount) VALUES (1, 1, 'equal', NULL, NULL)")
        db.execSQL("INSERT INTO item_shares (groupId, personId, kind, weight, amount) VALUES (1, 2, 'ratio', 2, NULL)")
        db.execSQL("INSERT INTO item_shares (groupId, personId, kind, weight, amount) VALUES (2, 2, 'amount', NULL, 300)")
        db.execSQL("INSERT INTO whole_shares (receiptId, personId, kind, weight, amount) VALUES (1, 2, 'ratio', 2, NULL)")
        db.execSQL("INSERT INTO whole_shares (receiptId, personId, kind, weight, amount) VALUES (2, 3, 'amount', NULL, 300)")
        db.execSQL("INSERT INTO payment_payers (id, receiptId, personId, method, amount) VALUES (1, 1, 1, 'カード', 748)")
        h.close()
    }

    /** Room で開く(移行は Migrations.ALL で走る。Room の検証もここで走る)。 */
    private fun openRoom(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*Migrations.ALL)
        .allowMainThreadQueries()
        .build()

    // ---- テスト ----

    @Test
    fun migration_createsParticipantsFromPersons_perReceipt_selfFirst() {
        createV10()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase // 移行と Room の検証がここで走る。通らなければ例外
            assertEquals(AppDatabase.VERSION, db.version)
            // レシート1: 人1(自分)・人2。レシート2: 人3。(receiptId, personId) ごとに1人
            assertEquals(3L, db.long("SELECT COUNT(*) FROM split_participants"))
            assertEquals(2L, db.long("SELECT COUNT(*) FROM split_participants WHERE receiptId = 1"))
            assertEquals(0L, db.long("SELECT sortOrder FROM split_participants WHERE receiptId = 1 AND personId = 1"))
            assertEquals(1L, db.long("SELECT sortOrder FROM split_participants WHERE receiptId = 1 AND personId = 2"))
            assertEquals(0L, db.long("SELECT sortOrder FROM split_participants WHERE receiptId = 2 AND personId = 3"))
            // 支払内訳に出てくる人は isPayer、それ以外は付かない
            assertEquals(1L, db.long("SELECT isPayer FROM split_participants WHERE receiptId = 1 AND personId = 1"))
            assertEquals(0L, db.long("SELECT isPayer FROM split_participants WHERE receiptId = 1 AND personId = 2"))
            // 新しい表の空の表
            assertEquals(0L, db.long("SELECT COUNT(*) FROM split_label_amounts"))
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_mapsAssignmentsToParticipantIds() {
        createV10()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            val p1r1 = db.long("SELECT id FROM split_participants WHERE receiptId = 1 AND personId = 1")
            val p2r1 = db.long("SELECT id FROM split_participants WHERE receiptId = 1 AND personId = 2")
            val p3r2 = db.long("SELECT id FROM split_participants WHERE receiptId = 2 AND personId = 3")
            // 品目ごとの割当: 行数・種類・重み・金額が保たれ、参照先が参加者になる
            assertEquals(3L, db.long("SELECT COUNT(*) FROM item_shares"))
            assertEquals(p1r1, db.long("SELECT participantId FROM item_shares WHERE groupId = 1 AND kind = 'equal'"))
            assertEquals(p2r1, db.long("SELECT participantId FROM item_shares WHERE groupId = 1 AND kind = 'ratio'"))
            assertEquals(2L, db.long("SELECT weight FROM item_shares WHERE groupId = 1 AND kind = 'ratio'"))
            assertEquals(300L, db.long("SELECT amount FROM item_shares WHERE groupId = 2"))
            assertEquals(p2r1, db.long("SELECT participantId FROM item_shares WHERE groupId = 2"))
            // 全体の割当
            assertEquals(2L, db.long("SELECT COUNT(*) FROM whole_shares"))
            assertEquals(p2r1, db.long("SELECT participantId FROM whole_shares WHERE receiptId = 1"))
            assertEquals(p3r2, db.long("SELECT participantId FROM whole_shares WHERE receiptId = 2"))
            assertEquals(300L, db.long("SELECT amount FROM whole_shares WHERE receiptId = 2"))
            // 支払内訳: id・金額・手段が保たれ、参加者(自分)を指す
            assertEquals(1L, db.long("SELECT id FROM payment_payers"))
            assertEquals(p1r1, db.long("SELECT participantId FROM payment_payers WHERE id = 1"))
            assertEquals(748L, db.long("SELECT amount FROM payment_payers WHERE id = 1"))
            assertEquals("カード", db.str("SELECT method FROM payment_payers WHERE id = 1"))
            // 割り勘のシートは列が増え、既存の行は既定値
            assertEquals("amount", db.str("SELECT wholeKind FROM split_sheets WHERE receiptId = 1"))
            assertEquals("", db.str("SELECT warnings FROM split_sheets WHERE receiptId = 1"))
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_keepsReceiptsGroupsAndPersons() {
        createV10()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(2L, db.long("SELECT COUNT(*) FROM receipts"))
            assertEquals(3L, db.long("SELECT COUNT(*) FROM receipt_items"))
            assertEquals(2L, db.long("SELECT COUNT(*) FROM share_groups"))
            assertEquals(2L, db.long("SELECT COUNT(*) FROM share_group_items"))
            assertEquals(3L, db.long("SELECT COUNT(*) FROM persons"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM persons WHERE isSelf = 1"))
        } finally {
            room.close()
        }
    }

    @Test
    fun roomReadsMigratedData_throughSplitDao() {
        createV10()
        val room = openRoom()
        try {
            room.openHelper.writableDatabase // 移行を走らせる
            val dao = room.splitDao()
            runBlocking {
                val parts = dao.getParticipants(1L)
                assertEquals(listOf(1L, 2L), parts.map { it.personId })
                assertTrue(parts[0].isPayer)
                assertFalse(parts[1].isPayer)
                // 割当は参加者 id で読める(人 id ではない)
                val shares = dao.getItemShares(1L)
                assertEquals(3, shares.size)
                assertTrue(shares.all { s -> parts.any { it.id == s.participantId } })
                val whole = dao.getWholeShares(2L).single()
                assertEquals(dao.getParticipants(2L).single().id, whole.participantId)
                assertEquals(300, whole.amount)
                // 支払内訳も参加者で読める
                assertEquals(parts[0].id, dao.getPayers(1L).single().participantId)
                val sheet = dao.getSheet(1L)!!
                assertEquals("amount", sheet.wholeKind)
                assertEquals("", sheet.warnings)
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun migrationAbortsWhenForeignKeysAreOn_andChangesNothing() {
        createV10()
        val h = helper(10)
        try {
            val db = h.writableDatabase
            db.execSQL("PRAGMA foreign_keys = ON")
            try {
                Migrations.MIGRATION_10_11.migrate(db)
                fail("外部キーが有効なら移行を止めるはず")
            } catch (e: IllegalStateException) {
                assertTrue(e.message.orEmpty().contains("外部キー"))
            }
            // 何も変わっていない(旧い列のまま)
            assertEquals(3L, db.long("SELECT COUNT(*) FROM item_shares"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'item_shares'"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'split_participants'"))
        } finally {
            h.close()
        }
    }
}
