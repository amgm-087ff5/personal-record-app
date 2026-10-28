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
 * v7 -> v8 の移行を、本物の SQLite(Robolectric)で実行する。
 * v7 の DB は app/schemas の 7.json の createSql・indices で作る(実機の v7 と同じ形)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV8RobolectricTest {
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

    // ---- v7 の DB を作る ----

    private fun schema7(): List<String> {
        val candidates = listOf(File("schemas/com.amgm.personallog.data.db.AppDatabase/7.json"), File("app/schemas/com.amgm.personallog.data.db.AppDatabase/7.json"))
        val file = candidates.firstOrNull { it.isFile } ?: error("7.json が見つかりません")
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
        foreignKeys: Boolean = false,
        onCreate: (SupportSQLiteDatabase) -> Unit = {},
        onUpgrade: (SupportSQLiteDatabase) -> Unit = {},
    ): SupportSQLiteOpenHelper {
        val cb = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(foreignKeys)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = onUpgrade(db)
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.path).callback(cb).build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun raw(total: String?, amount: Int = 198) =
        """{"date":"2026-10-09","time":"18:32","store":"A","items":[{"name":"牛乳","amount":$amount}]""" +
            (if (total != null) ""","total":$total""" else "") + "}"

    private fun insertReceipt(db: SupportSQLiteDatabase, id: Long, total: Int, status: String, rawJson: String) {
        db.execSQL(
            "INSERT INTO receipts (id, purchasedAt, storeName, totalAmount, imagePath, rawJson, status, createdAt, imageHash, isAdvance, ownAmount) " +
                "VALUES (?, '2026-10-09 18:32', '店', ?, ?, ?, ?, ?, ?, 0, NULL)",
            arrayOf<Any?>(id, total, "receipts/$id.jpg", rawJson, status, id * 1000, "hash$id"),
        )
    }

    /** 5件のレシート・品目・支出・グループが入った v7 を作って閉じる。 */
    private fun createV7(withOrphans: Boolean = false, withReceipts: Boolean = true) {
        val h = helper(7, onCreate = { db -> schema7().forEach { db.execSQL(it) } })
        val db = h.writableDatabase
        if (withReceipts) {
            insertReceipt(db, 1, 748, "confirmed", raw("748", 748))
            insertReceipt(db, 2, 198, "draft", raw(null)) // 旧方式の補完値と同じ -> 空欄
            insertReceipt(db, 3, 500, "draft", raw(null)) // 手で入れた値 -> 残す
            insertReceipt(db, 4, 0, "draft", "") // 読み取り失敗の仮の0円 -> 空欄
            insertReceipt(db, 5, 750, "confirmed", raw("748", 748)) // 確定済みで印字と違う -> unknown
            for ((i, rid) in listOf(1L, 1L, 3L).withIndex()) {
                db.execSQL(
                    "INSERT INTO receipt_items (receiptId, name, amount, category, taxRate, priceIncludesTax, memo) VALUES (?, ?, 100, '食費', 10, 1, '')",
                    arrayOf<Any?>(rid, "品$i"),
                )
            }
            db.execSQL("INSERT INTO expense_groups (id, date, time, amount, category, memo, receiptId, createdAt) VALUES (1, '2026-10-09', '18:32', 748, '食費', '', 1, 1)")
            db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, timeEdited) VALUES (1, '2026-10-09', '18:32', 400, '食費', '', 1, 1, 1, 0)")
            db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, timeEdited) VALUES (2, '2026-10-09', '', 300, '食費', '', NULL, 2, NULL, 0)")
        }
        // 採番を進めておく: id 9 を入れて消す(最大 id は 5 のまま、seq は 9)
        insertReceipt(db, 9, 1, "draft", "")
        db.execSQL("DELETE FROM receipts WHERE id = 9")
        if (withOrphans) {
            // 移行と無関係な古い不整合(外部キーが無効の時期に入ったもの)
            db.execSQL("INSERT INTO diary_photos (entryId, filePath, takenAt, sortOrder) VALUES (999, 'x.jpg', NULL, 0)")
            // receipts を親とする不整合も、移行前から1件ある
            db.execSQL("INSERT INTO receipt_items (receiptId, name, amount, category, taxRate, priceIncludesTax, memo) VALUES (777, '孤児', 1, '食費', 10, 1, '')")
        }
        db.version = 7
        h.close()
    }

    private fun migrate(foreignKeys: Boolean = false): SupportSQLiteDatabase {
        val h = helper(8, foreignKeys = foreignKeys, onUpgrade = { db -> Migrations.MIGRATION_7_8.migrate(db) })
        return h.writableDatabase
    }

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }
    private fun SupportSQLiteDatabase.seq(): Long? =
        query("SELECT seq FROM sqlite_sequence WHERE name = 'receipts'").use { if (it.moveToFirst()) it.getLong(0) else null }

    // ---- テスト ----

    @Test
    fun migration_keepsRowsAndForeignKeyLinks() {
        createV7()
        val db = migrate()
        assertEquals(5L, db.long("SELECT COUNT(*) FROM receipts"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM receipt_items"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM receipt_items i JOIN receipts r ON i.receiptId = r.id"))
        assertEquals(1L, db.long("SELECT COUNT(*) FROM expense_groups WHERE receiptId = 1"))
        assertEquals(1L, db.long("SELECT COUNT(*) FROM expenses WHERE receiptId = 1 AND groupId = 1"))
        assertEquals(2L, db.long("SELECT COUNT(*) FROM expenses"))
        // 外部キーを有効にしても違反は無い
        db.execSQL("PRAGMA foreign_keys = ON")
        assertEquals(0L, db.receiptsFkViolationsForTest())
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        // 画像ハッシュなど既存の列の値も保たれる
        assertEquals(1L, db.long("SELECT COUNT(*) FROM receipts WHERE id = 3 AND imageHash = 'hash3' AND imagePath = 'receipts/3.jpg'"))
        db.close()
    }

    private fun SupportSQLiteDatabase.receiptsFkViolationsForTest(): Long = with(Migrations) { receiptsFkViolations() }

    @Test
    fun migration_decidesTotalAndSource() {
        createV7()
        val db = migrate()
        fun row(id: Long): Pair<Int?, String> = db.query("SELECT totalAmount, totalSource FROM receipts WHERE id = $id").use {
            assertTrue(it.moveToFirst())
            (if (it.isNull(0)) null else it.getInt(0)) to it.getString(1)
        }
        assertEquals(748 to "printed", row(1))
        assertEquals(null to "unknown", row(2)) // 旧方式の補完値 -> 空欄
        assertEquals(500 to "manual", row(3)) // 手で入れた合計は残る
        assertEquals(null to "unknown", row(4)) // 読み取り失敗の仮の0円 -> 空欄
        assertEquals(750 to "unknown", row(5)) // 確定済みで印字と違う値は unknown
        db.close()
    }

    @Test
    fun migration_keepsAutoIncrementSequence() {
        createV7()
        assertEquals(9L, run { val h = helper(7); val d = h.writableDatabase; val s = d.seq(); h.close(); s })
        val db = migrate()
        assertEquals(9L, db.seq())
        db.execSQL("INSERT INTO receipts (purchasedAt, storeName, imagePath, rawJson, status, createdAt) VALUES ('d', 's', 'p', '', 'draft', 1)")
        assertEquals(10L, db.long("SELECT MAX(id) FROM receipts"))
        db.close()
    }

    @Test
    fun migration_emptyReceiptsTable_keepsSequence() {
        createV7(withReceipts = false)
        val db = migrate()
        assertEquals(0L, db.long("SELECT COUNT(*) FROM receipts"))
        assertEquals(9L, db.seq())
        db.close()
    }

    @Test
    fun migration_ignoresUnrelatedOrPreExistingForeignKeyViolations() {
        createV7(withOrphans = true)
        val db = migrate() // 例外にならない
        assertEquals(5L, db.long("SELECT COUNT(*) FROM receipts"))
        assertEquals(4L, db.long("SELECT COUNT(*) FROM receipt_items"))
        db.close()
    }

    @Test
    fun migration_stopsWhenForeignKeysAreOn_andLeavesDataUntouched() {
        createV7()
        try {
            migrate(foreignKeys = true)
            fail("外部キーが有効なら移行は止まるはず")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("外部キー"))
        }
        // 元のデータは v7 のまま残る
        val h = helper(7)
        val db = h.writableDatabase
        assertEquals(5L, db.long("SELECT COUNT(*) FROM receipts"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM receipt_items"))
        assertEquals(7, db.version)
        h.close()
    }

    @Test
    fun roomOpensMigratedDatabase_andPassesItsOwnSchemaValidation() {
        createV7()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val db = room.openHelper.writableDatabase // 移行と Room の検証がここで走る。通らなければ例外
            assertEquals(AppDatabase.VERSION, db.version)
            // 5 件 + v11 -> v12 で、支払いに属さない単独の支出(id 2)が手入力の支払いになった1件
            // − v15 -> v16 で、支出の無い確定済みの支払い(id 5)は消える(下書き 2・3・4 は残る)
            assertEquals(5L, db.long("SELECT COUNT(*) FROM receipts"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM receipts WHERE id = 5"))
            assertEquals(3L, db.long("SELECT COUNT(*) FROM receipt_items"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM receipt_not_duplicates"))
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            room.close()
        }
    }

    @Test
    fun preMigrationBackup_copiesOnceAndNeverOverwrites() {
        createV7()
        val root = File(context.filesDir, "db-backup")
        assertEquals(7, PreMigrationBackup.readUserVersion(dbFile))
        val dest = PreMigrationBackup.backupIfNeeded(dbFile, root, 8)
        assertNotNull(dest)
        assertTrue(File(dest, name).isFile)
        assertEquals(dbFile.length(), File(dest, name).length())
        // 元の DB を変えても、同じ版では上書きしない
        val h = helper(7)
        h.writableDatabase.execSQL("DELETE FROM receipt_items")
        h.close()
        assertNull(PreMigrationBackup.backupIfNeeded(dbFile, root, 8))
        val copy = File(dest, name)
        val h2 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(copy.path)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build(),
        )
        assertEquals(3L, h2.writableDatabase.long("SELECT COUNT(*) FROM receipt_items"))
        h2.close()
    }

    @Test
    fun preMigrationBackup_skipsMissingAndCurrentVersion() {
        val root = File(context.filesDir, "db-backup")
        assertNull(PreMigrationBackup.backupIfNeeded(dbFile, root, 8)) // ファイルが無い
        createV7()
        assertNull(PreMigrationBackup.backupIfNeeded(dbFile, root, 7)) // 既に最新
        assertFalse(File(root, "v7").exists())
    }
}
