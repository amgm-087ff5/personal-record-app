package com.amgm.personallog.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.io.File

/**
 * v8 -> v9 の移行を、本物の SQLite(Robolectric)で実行する。
 * v8 の DB はテスト用の資料 schemas-fixture/8.json(手作り)の createSql・indices で作る。v7 は app/schemas の 7.json(Room の生成物)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV9RobolectricTest {
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

    /**
     * 移行前の版の DB を作るための CREATE 文を JSON から読む。
     * 区別: 7.json は Room が生成してコミットされた本物の生成物(app/schemas)。
     * 8.json は手で作ったテスト用の資料(identityHash は仮の値)で、app/src/test/resources/schemas-fixture に置く。
     * 生成物の置き場(app/schemas)に手作りの版を置くと、main の CI が生成する本物と衝突するため、分けてある。
     */
    private fun schema(version: Int): List<String> {
        val text: String = if (version == 8) {
            val url = javaClass.classLoader!!.getResource("schemas-fixture/8.json") ?: error("schemas-fixture/8.json が見つかりません")
            url.readText()
        } else {
            val rel = "schemas/com.amgm.personallog.data.db.AppDatabase/$version.json"
            val file = listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile } ?: error("$version.json が見つかりません")
            file.readText()
        }
        val entities = Json.parseToJsonElement(text).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
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
        foreignKeys: Boolean = false,
        onCreate: (SupportSQLiteDatabase) -> Unit = {},
        onUpgrade: (SupportSQLiteDatabase, Int) -> Unit = { _, _ -> },
    ): SupportSQLiteOpenHelper {
        val cb = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(foreignKeys)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = onUpgrade(db, oldVersion)
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.path).callback(cb).build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }
    private fun SupportSQLiteDatabase.str(sql: String): String = query(sql).use { it.moveToFirst(); it.getString(0) }
    private fun SupportSQLiteDatabase.seq(table: String): Long? =
        query("SELECT seq FROM sqlite_sequence WHERE name = '$table'").use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun insertPayment(db: SupportSQLiteDatabase, id: Long, v8: Boolean) {
        val cols = if (v8) ", totalSource" else ""
        val vals = if (v8) ", 'printed'" else ""
        db.execSQL(
            "INSERT INTO receipts (id, purchasedAt, storeName, totalAmount, imagePath, rawJson, status, createdAt, imageHash, isAdvance, ownAmount$cols) " +
                "VALUES (?, '2026-10-09 18:32', '店', 748, ?, ?, 'confirmed', ?, ?, 0, NULL$vals)",
            arrayOf<Any?>(id, "receipts/$id.jpg", """{"total":748,"items":[{"name":"牛乳","amount":748}]}""", id * 1000, "hash$id"),
        )
    }

    /**
     * レシート2・品目3・グループ2(1つはレシート由来、1つは手入力)・支出3
     * (グループ内、レシート由来の単独、手入力の単独)が入った DB を作って閉じる。
     * v8 のとき: expenses を最後に1件入れて消し、採番を先へ進めておく(最大 id は 3、seq は 9)。
     */
    private fun createV8(withRows: Boolean = true, withOrphans: Boolean = false) {
        val h = helper(8, onCreate = { db -> schema(8).forEach { db.execSQL(it) } })
        val db = h.writableDatabase
        fillRows(db, v8 = true, withRows = withRows, withOrphans = withOrphans)
        db.version = 8
        h.close()
    }

    private fun fillRows(db: SupportSQLiteDatabase, v8: Boolean, withRows: Boolean = true, withOrphans: Boolean = false) {
        if (withRows) {
            insertPayment(db, 1, v8)
            insertPayment(db, 2, v8)
            for ((i, rid) in listOf(1L, 1L, 2L).withIndex()) {
                db.execSQL(
                    "INSERT INTO receipt_items (receiptId, name, amount, category, taxRate, priceIncludesTax, memo) VALUES (?, ?, 100, '食費', 10, 1, '')",
                    arrayOf<Any?>(rid, "品$i"),
                )
            }
            db.execSQL("INSERT INTO expense_groups (id, date, time, amount, category, memo, receiptId, createdAt) VALUES (1, '2026-10-09', '18:32', 748, '食費', 'g1', 1, 1)")
            db.execSQL("INSERT INTO expense_groups (id, date, time, amount, category, memo, receiptId, createdAt) VALUES (2, '2026-10-10', '09:00', 300, '交通', 'g2', NULL, 2)")
            // グループ内の支出
            db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, timeEdited) VALUES (1, '2026-10-09', '18:32', 400, '食費', 'e1', 1, 1, 1, 1)")
            // レシート由来の単独の支出
            db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, timeEdited) VALUES (2, '2026-10-09', '18:32', 300, '食費', 'e2', 2, 2, NULL, 0)")
            // 手入力の単独の支出
            db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, timeEdited) VALUES (3, '2026-10-11', '', 120, '日用品', 'e3', NULL, 3, NULL, 0)")
        }
        if (v8) {
            db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, timeEdited) VALUES (9, 'd', '', 1, 'c', '', NULL, 9, NULL, 0)")
            db.execSQL("DELETE FROM expenses WHERE id = 9")
        }
        if (withOrphans) {
            // 移行と無関係な古い不整合
            db.execSQL("INSERT INTO diary_photos (entryId, filePath, takenAt, sortOrder) VALUES (999, 'x.jpg', NULL, 0)")
            // expenses 自身の不整合も、移行前から1件ある(存在しない支払いを指す)
            db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, timeEdited) VALUES (4, 'd', '', 1, 'c', 'orphan', 777, 4, NULL, 0)")
        }
    }

    private fun createV7() {
        val h = helper(7, onCreate = { db -> schema(7).forEach { db.execSQL(it) } })
        val db = h.writableDatabase
        fillRows(db, v8 = false)
        db.version = 7
        h.close()
    }

    private fun migrate(foreignKeys: Boolean = false): SupportSQLiteDatabase =
        helper(9, foreignKeys = foreignKeys, onUpgrade = { db, _ -> Migrations.MIGRATION_8_9.migrate(db) }).writableDatabase

    // ---- テスト ----

    @Test
    fun migration_keepsCountsAndForeignKeyLinks() {
        createV8()
        val db = migrate()
        assertEquals(2L, db.long("SELECT COUNT(*) FROM receipts"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM receipt_items"))
        assertEquals(2L, db.long("SELECT COUNT(*) FROM expense_groups"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM expenses"))
        assertEquals(1L, db.long("SELECT COUNT(*) FROM expenses e JOIN expense_groups g ON e.groupId = g.id WHERE e.id = 1 AND g.id = 1"))
        assertEquals(1L, db.long("SELECT COUNT(*) FROM expenses e JOIN receipts r ON e.receiptId = r.id WHERE e.id = 2 AND e.groupId IS NULL"))
        assertEquals(1L, db.long("SELECT COUNT(*) FROM expense_groups g JOIN receipts r ON g.receiptId = r.id WHERE g.id = 1"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM receipt_items i JOIN receipts r ON i.receiptId = r.id"))
        // 既存の列の値が保たれる
        assertEquals(400L, db.long("SELECT amount FROM expenses WHERE id = 1"))
        assertEquals("e1", db.str("SELECT memo FROM expenses WHERE id = 1"))
        assertEquals("18:32", db.str("SELECT time FROM expenses WHERE id = 1"))
        assertEquals(1L, db.long("SELECT timeEdited FROM expenses WHERE id = 1"))
        assertEquals("g2", db.str("SELECT memo FROM expense_groups WHERE id = 2"))
        // 外部キーを有効にしても違反は無い
        db.execSQL("PRAGMA foreign_keys = ON")
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        db.close()
    }

    @Test
    fun migration_fillsUniqueNonEmptyUids_andDefaultsEditedFlagsToZero() {
        createV8()
        val db = migrate()
        assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE uid IS NULL OR uid = ''"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM expense_groups WHERE uid IS NULL OR uid = ''"))
        assertEquals(3L, db.long("SELECT COUNT(DISTINCT uid) FROM expenses"))
        assertEquals(2L, db.long("SELECT COUNT(DISTINCT uid) FROM expense_groups"))
        // 支出とグループをまたいでも重複しない
        assertEquals(5L, db.long("SELECT COUNT(DISTINCT uid) FROM (SELECT uid FROM expenses UNION ALL SELECT uid FROM expense_groups)"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE length(uid) <> 32"))
        // xxxEdited の既定値は 0(timeEdited は v7 から持ち越した値)
        for (col in listOf("amountEdited", "categoryEdited", "nameEdited", "memoEdited")) {
            assertEquals(col, 0L, db.long("SELECT COUNT(*) FROM expenses WHERE `$col` <> 0"))
        }
        assertEquals(1L, db.long("SELECT COUNT(*) FROM expenses WHERE timeEdited = 1"))
        for (col in listOf("amountEdited", "categoryEdited", "memoEdited", "timeEdited")) {
            assertEquals(col, 0L, db.long("SELECT COUNT(*) FROM expense_groups WHERE `$col` <> 0"))
        }
        // 家計簿で消した品目の印(deletedByUser)は、既存の行ではすべて 0
        assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE deletedByUser <> 0"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE receiptItemId IS NOT NULL"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE name <> ''"))
        db.close()
    }

    @Test
    fun migration_keepsAutoIncrementSequence() {
        createV8()
        val db = migrate()
        assertEquals(9L, db.seq("expenses"))
        db.execSQL("INSERT INTO expenses (date, time, amount, category, memo, createdAt, uid) VALUES ('d', '', 1, 'c', '', 1, 'u-new')")
        assertEquals(10L, db.long("SELECT MAX(id) FROM expenses"))
        db.close()
    }

    @Test
    fun migration_emptyExpenseItemsTable_keepsSequence() {
        createV8(withRows = false)
        val db = migrate()
        assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses"))
        assertEquals(9L, db.seq("expenses"))
        db.close()
    }

    @Test
    fun migration_ignoresUnrelatedOrPreExistingForeignKeyViolations() {
        createV8(withOrphans = true)
        val db = migrate() // 例外にならない
        assertEquals(4L, db.long("SELECT COUNT(*) FROM expenses"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE uid = ''"))
        db.close()
    }

    @Test
    fun migration_stopsWhenForeignKeysAreOn_andLeavesDataUntouched() {
        createV8()
        try {
            migrate(foreignKeys = true)
            fail("外部キーが有効なら移行は止まるはず")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("外部キー"))
        }
        val h = helper(8)
        val db = h.writableDatabase
        assertEquals(8, db.version)
        assertEquals(3L, db.long("SELECT COUNT(*) FROM expenses"))
        assertEquals(2L, db.long("SELECT COUNT(*) FROM expense_groups"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM receipt_items"))
        // 列も足されていない
        db.query("SELECT * FROM expense_groups").use { assertEquals(-1, it.getColumnIndex("uid")) }
        h.close()
    }

    @Test
    fun roomOpensMigratedDatabase_andPassesItsOwnSchemaValidation() {
        createV8()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val db = room.openHelper.writableDatabase // 移行と Room の検証がここで走る。通らなければ例外
            assertEquals(AppDatabase.VERSION, db.version) // v9 -> v10 も Migrations.ALL で続けて走る
            assertEquals(3L, db.long("SELECT COUNT(*) FROM expense_items"))
            // v14 の移行で、単独の支出2件(id 2・3)もまとまりになる
            assertEquals(4L, db.long("SELECT COUNT(*) FROM expenses"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM expense_items WHERE groupId IS NULL"))
            assertEquals(3L, db.long("SELECT COUNT(*) FROM payment_items"))
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            room.close()
        }
    }

    @Test
    fun chain_v7_to_v8_to_v9_runsAndRoomValidates() {
        createV7()
        // 手で続けて実行する(1回の onUpgrade の中で 7->8->9)
        val h = helper(9, onUpgrade = { db, old ->
            assertEquals(7, old)
            Migrations.MIGRATION_7_8.migrate(db)
            Migrations.MIGRATION_8_9.migrate(db)
        })
        val db = h.writableDatabase
        assertEquals(2L, db.long("SELECT COUNT(*) FROM receipts"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM receipt_items"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM expenses"))
        assertEquals(2L, db.long("SELECT COUNT(*) FROM expense_groups"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE uid = ''"))
        assertEquals(5L, db.long("SELECT COUNT(DISTINCT uid) FROM (SELECT uid FROM expenses UNION ALL SELECT uid FROM expense_groups)"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM receipt_not_duplicates"))
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        h.close()

        // 同じ手順で作った DB を Room が開いて検証する(Room の Migrations.ALL を通す)
        context.deleteDatabase(name)
        createV7()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val rdb = room.openHelper.writableDatabase
            assertEquals(AppDatabase.VERSION, rdb.version)
            assertEquals(3L, rdb.long("SELECT COUNT(*) FROM expense_items"))
            assertEquals(0L, rdb.long("SELECT COUNT(*) FROM expense_items WHERE uid = ''"))
        } finally {
            room.close()
        }
    }

    @Test
    fun preMigrationBackup_copiesV8_butNotCurrentVersion() {
        createV8()
        val root = File(context.filesDir, "db-backup")
        assertEquals(8, PreMigrationBackup.readUserVersion(dbFile))
        val dest = PreMigrationBackup.backupIfNeeded(dbFile, root, AppDatabase.VERSION)
        assertNotNull(dest)
        assertTrue(File(dest, name).isFile)
        // 現行の版(AppDatabase.VERSION)の DB は複製しない
        context.deleteDatabase(name)
        createV8()
        helper(AppDatabase.VERSION, onUpgrade = { db, _ ->
            Migrations.MIGRATION_8_9.migrate(db)
            Migrations.MIGRATION_9_10.migrate(db)
        }).writableDatabase.close()
        File(root, "v9").deleteRecursively()
        assertNull(PreMigrationBackup.backupIfNeeded(dbFile, root, AppDatabase.VERSION))
        assertFalse(File(root, "v9").exists())
    }
}
