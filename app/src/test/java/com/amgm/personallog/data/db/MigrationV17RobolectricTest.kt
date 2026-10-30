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
 * v16 -> v17 の移行(用語の整理の改名)を、本物の SQLite(Robolectric)で実行する。
 *   receipts → payments / receipt_items → payment_items / receipt_not_duplicates → payment_not_duplicates
 *   expenses → expense_items / expense_groups → expenses / 列 receiptId → paymentId・receiptItemId → paymentItemId
 * 移行前の DB は app/schemas の 16.json(Room の生成物)の createSql・indices で作る。
 * 確かめること: 行数・id・値・採番が保たれる / 外部キーが新しい表を指す / インデックス名が Room の期待どおり /
 * 移行後に外部キーの連鎖(CASCADE)が効く / Room の検証を通る。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV17RobolectricTest {
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

    private fun schema16(): List<String> {
        val rel = "schemas/com.amgm.personallog.data.db.AppDatabase/16.json"
        val file = listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile } ?: error("16.json が見つかりません")
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

    private fun helper(foreignKeys: Boolean = false, create: (SupportSQLiteDatabase) -> Unit = {}): SupportSQLiteOpenHelper {
        val cb = object : SupportSQLiteOpenHelper.Callback(16) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(foreignKeys)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = create(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.path).callback(cb).build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    /**
     * v16 の DB を作る(全ての表に行を入れる。採番が最大 id より進んでいる表も作る):
     *  - 支払い 1(確定・1000・品目 11,12・割り勘あり)/ 2(下書き・品目 21)/ 3(確定・500)。支払い 8 は入れて消す(採番 8)
     *  - 重複でない印 (1, 3)
     *  - 支出 1(支払い 1・1000)/ 支出 3(支払い 3・500)。支出の品目 101(品目 11)・102(品目 12)・301(品目なし)。品目 900 は入れて消す(採番 900)
     *  - 割り勘: シート 1・参加者 1(自分)・2(名前なし)・ラベル金額・まとまり 1(品目 11)・割当・全体の割当・支払内訳
     */
    private fun createV16() {
        val h = helper(create = { db -> schema16().forEach { db.execSQL(it) } })
        val db = h.writableDatabase
        db.execSQL(
            "INSERT INTO receipts (id, purchasedAt, storeName, totalAmount, imagePath, rawJson, status, createdAt, totalSource, headcount) VALUES " +
                "(1, '2026-10-05 12:30', 'スーパー', 1000, 'receipts/1.jpg', '{\"a\":1}', 'confirmed', 1000, 'printed', 2), " +
                "(2, '2026-10-06 13:00', '食堂', NULL, 'receipts/2.jpg', '{}', 'draft', 2000, 'unknown', NULL), " +
                "(3, '2026-10-07', '', 500, '', '{}', 'confirmed', 3000, 'manual', NULL), " +
                "(8, '2026-10-08', 'x', 1, '', '{}', 'draft', 8000, 'manual', NULL)",
        )
        db.execSQL("DELETE FROM receipts WHERE id = 8")
        db.execSQL(
            "INSERT INTO receipt_items (id, receiptId, name, amount, category, taxRate, priceIncludesTax, memo, unknownAmount) VALUES " +
                "(11, 1, '牛乳', 300, '食費', 8, 1, 'メモ', 0), (12, 1, '卵', 700, '食費', 10, 0, '', 0), (21, 2, '定食', 900, '外食', 10, 1, '', 1)",
        )
        db.execSQL("INSERT INTO receipt_not_duplicates (receiptIdLow, receiptIdHigh, createdAt) VALUES (1, 3, 5)")
        db.execSQL(
            "INSERT INTO expense_groups (id, date, time, amount, category, memo, receiptId, createdAt, uid, amountEdited) VALUES " +
                "(1, '2026-10-05', '12:30', 1000, '食費', 'まとめ', 1, 1000, 'g-1', 1), " +
                "(3, '2026-10-07', '', 500, 'その他', '', 3, 3000, 'g-3', 0)",
        )
        db.execSQL(
            "INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, receiptItemId, name, uid, deletedByUser, unknownAmount) VALUES " +
                "(101, '2026-10-05', '12:30', 300, '食費', '', 1, 1000, 1, 11, '牛乳', 'e-101', 0, 0), " +
                "(102, '2026-10-05', '12:30', 700, '食費', '', 1, 1000, 1, 12, '卵', 'e-102', 1, 0), " +
                "(301, '2026-10-07', '', 500, 'その他', '', 3, 3000, 3, NULL, '', 'e-301', 0, 1), " +
                "(900, '2026-10-09', '', 1, 'その他', '', NULL, 9000, NULL, NULL, '', 'e-900', 0, 0)",
        )
        db.execSQL("DELETE FROM expenses WHERE id = 900")
        db.execSQL("INSERT INTO persons (id, uid, name, shortName, isSelf, sortOrder, createdAt) VALUES (1, 'p-1', '自分', '自', 1, 0, 1)")
        db.execSQL("INSERT INTO split_sheets (receiptId, mode, wholeKind, warnings) VALUES (1, 'item', 'amount', 'W1')")
        db.execSQL(
            "INSERT INTO split_participants (id, receiptId, personId, memo, label, isPayer, sortOrder) VALUES " +
                "(1, 1, 1, '', '', 1, 0), (2, 1, NULL, '友', '新人', 0, 1)",
        )
        db.execSQL("INSERT INTO split_label_amounts (receiptId, label, amount) VALUES (1, '新人', 300)")
        db.execSQL("INSERT INTO share_groups (id, receiptId, pieceCount, isShared, sortOrder) VALUES (1, 1, 2, 0, 0)")
        db.execSQL("INSERT INTO share_group_items (groupId, receiptItemId) VALUES (1, 11)")
        db.execSQL("INSERT INTO item_shares (groupId, participantId, kind, weight, amount) VALUES (1, 2, 'ratio', 2, NULL)")
        db.execSQL("INSERT INTO whole_shares (receiptId, participantId, kind, weight, amount) VALUES (1, 2, 'amount', NULL, 400)")
        db.execSQL("INSERT INTO payment_payers (id, receiptId, participantId, method, amount) VALUES (1, 1, 1, 'カード', 1000)")
        db.execSQL("INSERT INTO diary_entries (id, date, text, createdAt, updatedAt) VALUES (1, '2026-10-05', '日記', 1, 1)")
        h.close()
    }

    private fun openRoom(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*Migrations.ALL)
        .allowMainThreadQueries()
        .build()

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }
    private fun SupportSQLiteDatabase.str(sql: String): String = query(sql).use { it.moveToFirst(); it.getString(0) }

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> = query(sql).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }

    private fun SupportSQLiteDatabase.ids(sql: String): List<Long> = query(sql).use { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0)) }
    }

    private fun SupportSQLiteDatabase.columns(table: String): List<String> = strings("SELECT name FROM pragma_table_info('$table')")

    /** (参照先の表, 子の列, 親の列, ON DELETE) の一覧。 */
    private fun SupportSQLiteDatabase.foreignKeys(table: String): Set<List<String>> = query("PRAGMA foreign_key_list(`$table`)").use { c ->
        buildSet {
            while (c.moveToNext()) {
                add(
                    listOf(
                        c.getString(c.getColumnIndexOrThrow("table")),
                        c.getString(c.getColumnIndexOrThrow("from")),
                        c.getString(c.getColumnIndexOrThrow("to")),
                        c.getString(c.getColumnIndexOrThrow("on_delete")),
                    ),
                )
            }
        }
    }

    private val v17Tables = setOf(
        "diary_entries", "diary_photos", "payments", "payment_items", "expense_items", "expenses", "payment_not_duplicates",
        "persons", "split_sheets", "share_groups", "share_group_items", "item_shares", "whole_shares", "payment_payers",
        "split_participants", "split_label_amounts",
    )

    @Test
    fun migration_renamesTablesAndColumns_keepsRowsIdsAndValues() {
        createV16()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase // 移行と Room の検証がここで走る。通らなければ例外
            assertEquals(17, db.version)
            assertEquals(AppDatabase.VERSION, db.version)

            // 表の集合: 旧い名前は消え、v17 の名前だけが残る
            val tables = db.strings("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name NOT LIKE 'android_%'")
            assertEquals(v17Tables, tables.toSet())
            assertFalse(tables.any { it.endsWith("_new") })

            // 列: 旧い列名 receiptId・receiptItemId・receiptIdLow/High はどの表にも残らない
            for (t in v17Tables) {
                val cols = db.columns(t)
                assertTrue("$t に旧い列が残っている: $cols", cols.none { it.startsWith("receipt") })
            }
            assertTrue(db.columns("payment_items").contains("paymentId"))
            assertTrue(db.columns("expenses").contains("paymentId"))
            assertTrue(db.columns("expense_items").containsAll(listOf("paymentId", "paymentItemId", "groupId")))
            assertTrue(db.columns("payment_not_duplicates").containsAll(listOf("paymentIdLow", "paymentIdHigh")))
            assertTrue(db.columns("share_group_items").contains("paymentItemId"))

            // 行数・id が保たれる(旧 expense_groups → expenses、旧 expenses → expense_items)
            assertEquals(listOf(1L, 2L, 3L), db.ids("SELECT id FROM payments ORDER BY id"))
            assertEquals(listOf(11L, 12L, 21L), db.ids("SELECT id FROM payment_items ORDER BY id"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM payment_not_duplicates"))
            assertEquals(listOf(1L, 3L), db.ids("SELECT id FROM expenses ORDER BY id"))
            assertEquals(listOf(101L, 102L, 301L), db.ids("SELECT id FROM expense_items ORDER BY id"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM split_sheets"))
            assertEquals(listOf(1L, 2L), db.ids("SELECT id FROM split_participants ORDER BY id"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM split_label_amounts"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM share_groups"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM share_group_items"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM item_shares"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM whole_shares"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM payment_payers"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM persons"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM diary_entries"))

            // 値が保たれる
            assertEquals("スーパー", db.str("SELECT storeName FROM payments WHERE id = 1"))
            assertEquals("2026-10-05 12:30", db.str("SELECT purchasedAt FROM payments WHERE id = 1"))
            assertEquals(1000L, db.long("SELECT totalAmount FROM payments WHERE id = 1"))
            assertEquals("receipts/1.jpg", db.str("SELECT imagePath FROM payments WHERE id = 1"))
            assertEquals("""{"a":1}""", db.str("SELECT rawJson FROM payments WHERE id = 1"))
            assertEquals("printed", db.str("SELECT totalSource FROM payments WHERE id = 1"))
            assertEquals(2L, db.long("SELECT headcount FROM payments WHERE id = 1"))
            assertEquals("draft", db.str("SELECT status FROM payments WHERE id = 2"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM payments WHERE id = 2 AND totalAmount IS NULL"))
            assertEquals("牛乳", db.str("SELECT name FROM payment_items WHERE id = 11"))
            assertEquals(8L, db.long("SELECT taxRate FROM payment_items WHERE id = 11"))
            assertEquals(0L, db.long("SELECT priceIncludesTax FROM payment_items WHERE id = 12"))
            assertEquals(1L, db.long("SELECT unknownAmount FROM payment_items WHERE id = 21"))
            assertEquals(1L, db.long("SELECT paymentId FROM payment_items WHERE id = 11"))
            assertEquals(2L, db.long("SELECT paymentId FROM payment_items WHERE id = 21"))
            assertEquals(1L, db.long("SELECT paymentIdLow FROM payment_not_duplicates"))
            assertEquals(3L, db.long("SELECT paymentIdHigh FROM payment_not_duplicates"))

            // 支出(旧 expense_groups): 支払いとの対応・uid・編集済みの印
            assertEquals(1L, db.long("SELECT paymentId FROM expenses WHERE id = 1"))
            assertEquals(3L, db.long("SELECT paymentId FROM expenses WHERE id = 3"))
            assertEquals("g-1", db.str("SELECT uid FROM expenses WHERE id = 1"))
            assertEquals(1000L, db.long("SELECT amount FROM expenses WHERE id = 1"))
            assertEquals(1L, db.long("SELECT amountEdited FROM expenses WHERE id = 1"))
            assertEquals("まとめ", db.str("SELECT memo FROM expenses WHERE id = 1"))

            // 支出の品目(旧 expenses): 支出(groupId)・支払い・支払いの品目との対応
            assertEquals(1L, db.long("SELECT groupId FROM expense_items WHERE id = 101"))
            assertEquals(1L, db.long("SELECT paymentId FROM expense_items WHERE id = 101"))
            assertEquals(11L, db.long("SELECT paymentItemId FROM expense_items WHERE id = 101"))
            assertEquals(12L, db.long("SELECT paymentItemId FROM expense_items WHERE id = 102"))
            assertEquals(1L, db.long("SELECT deletedByUser FROM expense_items WHERE id = 102"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM expense_items WHERE id = 301 AND paymentItemId IS NULL AND unknownAmount = 1"))
            assertEquals("e-101", db.str("SELECT uid FROM expense_items WHERE id = 101"))
            assertEquals(1500L, db.long("SELECT SUM(amount) FROM expense_items"))
            assertEquals(1500L, db.long("SELECT SUM(amount) FROM expenses"))

            // 割り勘の表
            assertEquals("item", db.str("SELECT mode FROM split_sheets WHERE paymentId = 1"))
            assertEquals("W1", db.str("SELECT warnings FROM split_sheets WHERE paymentId = 1"))
            assertEquals(1L, db.long("SELECT isPayer FROM split_participants WHERE id = 1 AND paymentId = 1"))
            assertEquals("新人", db.str("SELECT label FROM split_participants WHERE id = 2"))
            assertEquals(300L, db.long("SELECT amount FROM split_label_amounts WHERE paymentId = 1 AND label = '新人'"))
            assertEquals(2L, db.long("SELECT pieceCount FROM share_groups WHERE id = 1 AND paymentId = 1"))
            assertEquals(11L, db.long("SELECT paymentItemId FROM share_group_items WHERE groupId = 1"))
            assertEquals(2L, db.long("SELECT weight FROM item_shares WHERE groupId = 1 AND participantId = 2"))
            assertEquals(400L, db.long("SELECT amount FROM whole_shares WHERE paymentId = 1 AND participantId = 2"))
            assertEquals("カード", db.str("SELECT method FROM payment_payers WHERE paymentId = 1"))

            // 外部キー違反は無い
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_foreignKeysPointToTheNewTables() {
        createV16()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            val toPayments = { from: String -> listOf("payments", from, "id", "CASCADE") }
            assertEquals(setOf(toPayments("paymentId")), db.foreignKeys("payment_items"))
            assertEquals(setOf(toPayments("paymentId")), db.foreignKeys("expenses"))
            assertEquals(
                setOf(
                    toPayments("paymentId"),
                    listOf("expenses", "groupId", "id", "CASCADE"),
                    listOf("payment_items", "paymentItemId", "id", "SET NULL"),
                ),
                db.foreignKeys("expense_items"),
            )
            assertEquals(setOf(toPayments("paymentIdLow"), toPayments("paymentIdHigh")), db.foreignKeys("payment_not_duplicates"))
            assertEquals(setOf(toPayments("paymentId")), db.foreignKeys("split_sheets"))
            assertEquals(
                setOf(toPayments("paymentId"), listOf("persons", "personId", "id", "NO ACTION")),
                db.foreignKeys("split_participants"),
            )
            assertEquals(setOf(toPayments("paymentId")), db.foreignKeys("split_label_amounts"))
            assertEquals(setOf(toPayments("paymentId")), db.foreignKeys("share_groups"))
            assertEquals(
                setOf(
                    listOf("share_groups", "groupId", "id", "CASCADE"),
                    listOf("payment_items", "paymentItemId", "id", "CASCADE"),
                ),
                db.foreignKeys("share_group_items"),
            )
            assertEquals(
                setOf(listOf("share_groups", "groupId", "id", "CASCADE"), listOf("split_participants", "participantId", "id", "CASCADE")),
                db.foreignKeys("item_shares"),
            )
            assertEquals(
                setOf(toPayments("paymentId"), listOf("split_participants", "participantId", "id", "CASCADE")),
                db.foreignKeys("whole_shares"),
            )
            assertEquals(
                setOf(toPayments("paymentId"), listOf("split_participants", "participantId", "id", "CASCADE")),
                db.foreignKeys("payment_payers"),
            )
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_indicesHaveTheNamesRoomExpects() {
        createV16()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            val names = db.strings("SELECT name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL").toSet()
            assertEquals(
                setOf(
                    "index_diary_entries_date", "index_diary_photos_entryId", "index_persons_uid",
                    "index_payment_items_paymentId",
                    "index_expense_items_paymentId", "index_expense_items_date", "index_expense_items_groupId", "index_expense_items_paymentItemId",
                    "index_expenses_paymentId", "index_expenses_date",
                    "index_payment_not_duplicates_paymentIdHigh",
                    "index_share_groups_paymentId", "index_share_group_items_paymentItemId", "index_item_shares_participantId",
                    "index_whole_shares_participantId", "index_payment_payers_paymentId", "index_payment_payers_participantId",
                    "index_split_participants_paymentId", "index_split_participants_personId",
                ),
                names,
            )
            // 索引の表: 新しい表に付いている(旧い表名のまま残っていない)
            assertEquals("expense_items", db.str("SELECT tbl_name FROM sqlite_master WHERE name = 'index_expense_items_date'"))
            assertEquals("expenses", db.str("SELECT tbl_name FROM sqlite_master WHERE name = 'index_expenses_date'"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE name = 'index_share_group_items_paymentItemId' AND sql LIKE 'CREATE UNIQUE INDEX%'"))
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_keepsAutoIncrementSequences() {
        createV16()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            // 支払い: 8 まで採番されていた(最大 id は 3)/ 支出の品目: 900 まで(最大 id は 301)
            assertEquals(8L, db.long("SELECT seq FROM sqlite_sequence WHERE name = 'payments'"))
            assertEquals(900L, db.long("SELECT seq FROM sqlite_sequence WHERE name = 'expense_items'"))
            assertEquals(3L, db.long("SELECT seq FROM sqlite_sequence WHERE name = 'expenses'"))
            assertEquals(21L, db.long("SELECT seq FROM sqlite_sequence WHERE name = 'payment_items'"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM sqlite_sequence WHERE name IN ('receipts', 'receipt_items', 'expense_groups')"))
            runBlocking {
                val id = room.paymentDao().insert(
                    com.amgm.personallog.data.model.Payment(purchasedAt = "2026-10-10", storeName = "", totalAmount = 1, imagePath = "", rawJson = "", createdAt = 1),
                )
                assertEquals(9L, id)
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_cascadesStillWork_andRoomReadsTheData() {
        createV16()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            runBlocking {
                // 新しい DAO で読める
                assertEquals(listOf(11L, 12L), room.paymentDao().getItems(1L).map { it.id })
                assertEquals(listOf(1L), room.expenseDao().getByPaymentId(1L).map { it.id })
                assertEquals(listOf(101L, 102L), room.expenseItemDao().getByGroupId(1L).map { it.id })
                assertEquals(listOf(1L, 2L), room.splitDao().getParticipants(1L).map { it.id })
                assertEquals(1, room.paymentDao().getAllNotDuplicates().size)
            }
            // 外部キーが有効な実行時: 支払い 1 を消すと、子も孫も連鎖で消える
            runBlocking { room.paymentDao().delete(room.paymentDao().getById(1L)!!) }
            for (t in listOf(
                "payment_items", "expenses", "expense_items", "payment_not_duplicates", "split_sheets", "split_participants",
                "split_label_amounts", "share_groups", "share_group_items", "item_shares", "whole_shares", "payment_payers",
            )) {
                val remaining = db.long("SELECT COUNT(*) FROM `$t` WHERE " + when (t) {
                    "payment_not_duplicates" -> "paymentIdLow = 1 OR paymentIdHigh = 1"
                    "share_group_items", "item_shares" -> "groupId = 1"
                    "expense_items" -> "paymentId = 1 OR groupId = 1"
                    else -> "paymentId = 1"
                })
                assertEquals("$t に支払い 1 の行が残っている", 0L, remaining)
            }
            // 他の支払いの行は残る
            assertEquals(listOf(2L, 3L), db.ids("SELECT id FROM payments ORDER BY id"))
            assertEquals(listOf(3L), db.ids("SELECT id FROM expenses ORDER BY id"))
            assertEquals(listOf(301L), db.ids("SELECT id FROM expense_items ORDER BY id"))
            assertEquals(listOf(21L), db.ids("SELECT id FROM payment_items ORDER BY id"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM persons")) // 人は消えない
            // 支払いの品目を消すと、支出の品目の紐づきは外れる(SET NULL)
            db.execSQL("INSERT INTO payment_items (id, paymentId, name, amount, category, memo) VALUES (31, 3, 'x', 500, 'その他', '')")
            db.execSQL("UPDATE expense_items SET paymentItemId = 31 WHERE id = 301")
            db.execSQL("DELETE FROM payment_items WHERE id = 31")
            assertEquals(1L, db.long("SELECT COUNT(*) FROM expense_items WHERE id = 301 AND paymentItemId IS NULL"))
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_emptyDatabase_migrates() {
        val h = helper(create = { db -> schema16().forEach { db.execSQL(it) } })
        h.writableDatabase
        h.close()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(17, db.version)
            assertEquals(0L, db.long("SELECT COUNT(*) FROM payments"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM expense_items"))
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_stopsWhenForeignKeysAreOn_andLeavesDataUntouched() {
        createV16()
        val h = helper(foreignKeys = true)
        val db = h.writableDatabase
        try {
            Migrations.MIGRATION_16_17.migrate(db)
            fail("外部キーが有効なら移行は止まるはず")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("外部キー"))
        }
        // 元のデータは v16 のまま残る
        assertEquals(3L, db.long("SELECT COUNT(*) FROM receipts"))
        assertEquals(2L, db.long("SELECT COUNT(*) FROM expense_groups"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM expenses"))
        h.close()
    }

    @Test
    fun v17Definitions_matchTheEntities() {
        // Migrations の v17 の定義と、Room が KSP で生成する 17.json(ビルド時に app/schemas へ出る)が一致する
        val rel = "schemas/com.amgm.personallog.data.db.AppDatabase/17.json"
        val file = listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile } ?: error("17.json が見つかりません。KSP が生成していません")
        val entities = Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }.associateBy { it["tableName"]!!.jsonPrimitive.content }
        assertEquals(v17Tables, entities.keys)
        for (t in Migrations.V17_TABLES) {
            val e = entities.getValue(t.newName)
            assertEquals(
                "${t.newName} の createSql",
                e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t.newName),
                t.createSql(t.newName),
            )
            val indices = (e["indices"] as? JsonArray).orEmpty().map { it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t.newName) }
            assertEquals("${t.newName} のインデックス", indices, t.indexSqls(t.newName))
        }
    }
}
