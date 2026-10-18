package com.amgm.personallog.data.db

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * v9 -> v10 の移行(MIGRATION_9_10)を、本物の SQLite(Robolectric)で実行する。
 * 移行前の DB は app/schemas の 9.json(Room の生成物)の createSql・indices で作り、移行の関数を直接流す。
 * Room では開かない(v10 の表は v11 で作り直されるため、Room の検証と SplitDao は v11 の検査で使う)。
 * 検査は生の SQL で書く。v11 への移行は MigrationV11RobolectricTest が確かめる。
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

    private val dbFile: java.io.File get() = context.getDatabasePath(name)

    /** 移行前(v9)の CREATE 文を 9.json から読む(テーブルとインデックス)。 */
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

    /** 移行前(v9)の DB を作り、データを入れて開いたままのヘルパーを返す。レシート2・品目3・グループ2・支出3。 */
    private fun createV9(): SupportSQLiteOpenHelper {
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
        return h
    }

    /** レシート1に、友人1人(品目ごと・全体・支払内訳)の割り勘を生の SQL で入れる(v10 の列で)。友人の人 id を返す。 */
    private fun seedSplit(db: SupportSQLiteDatabase): Long {
        db.execSQL("INSERT INTO persons (uid, name, shortName, isSelf, sortOrder, createdAt) VALUES ('friend-uid', '友人', '友', 0, 1, 2)")
        val friend = db.long("SELECT id FROM persons WHERE uid = 'friend-uid'")
        val self = db.long("SELECT id FROM persons WHERE isSelf = 1")
        db.execSQL("INSERT INTO split_sheets (receiptId, mode) VALUES (1, 'item')")
        db.execSQL("INSERT INTO share_groups (receiptId, pieceCount, isShared, sortOrder) VALUES (1, 2, 0, 0)")
        val gid = db.long("SELECT id FROM share_groups WHERE receiptId = 1")
        db.execSQL("INSERT INTO share_group_items (groupId, receiptItemId) VALUES ($gid, 1)")
        db.execSQL("INSERT INTO item_shares (groupId, personId, kind) VALUES ($gid, $friend, 'equal')")
        db.execSQL("INSERT INTO whole_shares (receiptId, personId, kind, weight) VALUES (1, $friend, 'ratio', 2)")
        db.execSQL("INSERT INTO payment_payers (receiptId, personId, method, amount) VALUES (1, $self, '', 748)")
        return friend
    }

    /** 移行前の DB を作り、v10 への移行だけを流す。開いたヘルパーを返す(呼び出し側で閉じる)。 */
    private fun migratedToV10(): SupportSQLiteOpenHelper {
        val h = createV9()
        Migrations.MIGRATION_9_10.migrate(h.writableDatabase)
        return h
    }

    // ---- テスト ----

    @Test
    fun migration_keepsExistingCounts_andCreatesSevenTables() {
        val h = migratedToV10()
        try {
            val db = h.writableDatabase
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
            h.close()
        }
    }

    @Test
    fun migration_createsExactlyOneSelfPerson() {
        val h = migratedToV10()
        try {
            val db = h.writableDatabase
            assertEquals(1L, db.long("SELECT COUNT(*) FROM persons"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM persons WHERE isSelf = 1"))
            assertEquals("自分", db.str("SELECT name FROM persons WHERE isSelf = 1"))
            assertEquals("自", db.str("SELECT shortName FROM persons WHERE isSelf = 1"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM persons WHERE uid IS NULL OR uid = ''"))
        } finally {
            h.close()
        }
    }

    @Test
    fun migrationStep_rerun_doesNotAddSecondSelf() {
        val h = migratedToV10()
        try {
            val db = h.writableDatabase
            Migrations.MIGRATION_9_10.migrate(db) // 同じ移行をもう一度流しても、自分は増えない・既存の件数も変わらない
            assertEquals(1L, db.long("SELECT COUNT(*) FROM persons WHERE isSelf = 1"))
            assertEquals(2L, db.long("SELECT COUNT(*) FROM receipts"))
        } finally {
            h.close()
        }
    }

    @Test
    fun emptySplitTables_areUsableWithRawSql() {
        val h = migratedToV10()
        try {
            val db = h.writableDatabase
            db.execSQL("PRAGMA foreign_keys = ON")
            val friend = seedSplit(db)
            assertEquals(1L, db.long("SELECT COUNT(*) FROM item_shares WHERE personId = $friend"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM whole_shares WHERE personId = $friend"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM payment_payers WHERE receiptId = 1"))
            assertEquals(2L, db.long("SELECT COUNT(*) FROM persons"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM share_group_items WHERE receiptItemId = 1"))
            assertEquals("item", db.str("SELECT mode FROM split_sheets WHERE receiptId = 1"))
        } finally {
            h.close()
        }
    }

    @Test
    fun deletingReceipt_cascadesToSplitTables_butKeepsPersons() {
        val h = migratedToV10()
        try {
            val db = h.writableDatabase
            seedSplit(db)
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
            h.close()
        }
    }

    @Test
    fun deletingPersonWhichIsAssigned_isBlockedByForeignKey() {
        val h = migratedToV10()
        try {
            val db = h.writableDatabase
            seedSplit(db)
            db.execSQL("PRAGMA foreign_keys = ON")
            try {
                db.execSQL("DELETE FROM persons WHERE isSelf = 0") // 割当から参照されている友人は消せない
                fail("割当から参照されている人は削除できないはず")
            } catch (e: SQLiteException) {
                assertTrue(e.message.orEmpty().contains("FOREIGN KEY"))
            }
            assertEquals(2L, db.long("SELECT COUNT(*) FROM persons"))
        } finally {
            h.close()
        }
    }
}
