package com.amgm.personallog.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.amgm.personallog.data.repository.ReceiptRepository
import com.amgm.personallog.data.repository.SplitRepository
import com.amgm.personallog.domain.SingleExpenseConversion
import com.amgm.personallog.ui.ledger.LedgerEntry
import com.amgm.personallog.ui.ledger.LedgerLogic
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * v13 -> v14 の移行(unknownAmount の追加・単独の支出をまとまり+1品目に変える)を、本物の SQLite(Robolectric)で実行する。
 * 移行前の DB は app/schemas の 13.json(Room の生成物)の createSql・indices で作る。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV14RobolectricTest {
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

    /** 移行前(v13)の CREATE 文を 13.json から読む(テーブルとインデックス)。 */
    private fun schema13(): List<String> {
        val rel = "schemas/com.amgm.personallog.data.db.AppDatabase/13.json"
        val file = listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile } ?: error("13.json が見つかりません")
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

    /**
     * v13 の DB を作る:
     *  - 支払い1(合計 700・品目2つ): まとまり1 + 品目の支出 1・2
     *  - 支払い2(合計 1200・品目なし): 単独の支出 3(品名「ランチ」、カテゴリ・メモを編集済み)
     *  - 支払い3(立て替え・合計 900): 単独の支出 4(自分の分 300)と、家計簿で消した品目 5(groupId なし・deletedByUser)
     */
    private fun createV13() {
        val cb = object : SupportSQLiteOpenHelper.Callback(13) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(false)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = schema13().forEach { db.execSQL(it) }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.path).callback(cb).build()
        val h = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = h.writableDatabase
        db.execSQL(
            "INSERT INTO receipts (id, purchasedAt, storeName, totalAmount, imagePath, rawJson, status, createdAt, totalSource) VALUES " +
                "(1, '2026-10-05 12:30', 'スーパー', 700, '', '', 'confirmed', 1000, 'manual'), " +
                "(2, '2026-10-06 13:00', '食堂', 1200, '', '', 'confirmed', 2000, 'manual'), " +
                "(3, '2026-10-07 19:00', '居酒屋', 900, '', '', 'confirmed', 3000, 'manual')",
        )
        db.execSQL("UPDATE receipts SET isAdvance = 1, ownAmount = 300 WHERE id = 3")
        db.execSQL(
            "INSERT INTO receipt_items (id, receiptId, name, amount, category, taxRate, priceIncludesTax, memo) VALUES " +
                "(11, 1, '牛乳', 200, '食費', 10, 1, ''), (12, 1, '洗剤', 500, '日用品', 10, 1, ''), " +
                "(31, 3, 'ビール', 400, '外食', 10, 1, ''), (32, 3, '枝豆', 500, '外食', 10, 1, '')",
        )
        db.execSQL(
            "INSERT INTO expense_groups (id, date, time, amount, category, memo, receiptId, createdAt, uid) VALUES " +
                "(1, '2026-10-05', '12:30', 700, '日用品', 'スーパー', 1, 1000, 'g-1')",
        )
        db.execSQL(
            "INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, receiptItemId, name, uid) VALUES " +
                "(1, '2026-10-05', '12:30', 200, '食費', '', 1, 1000, 1, 11, '牛乳', 'e-1'), " +
                "(2, '2026-10-05', '12:30', 500, '日用品', '', 1, 1000, 1, 12, '洗剤', 'e-2')",
        )
        db.execSQL(
            "INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, name, uid, categoryEdited, memoEdited, timeEdited) " +
                "VALUES (3, '2026-10-06', '13:00', 1200, '外食', '自分で', 2, 2000, 'ランチ', 'e-3', 1, 1, 1)",
        )
        db.execSQL(
            "INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, uid) " +
                "VALUES (4, '2026-10-07', '19:00', 300, '外食', '居酒屋', 3, 3000, 'e-4')",
        )
        db.execSQL(
            "INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, receiptItemId, name, uid, deletedByUser) " +
                "VALUES (5, '2026-10-07', '19:00', 500, '外食', '', 3, 3000, 32, '枝豆', 'e-5', 1)",
        )
        h.close()
    }

    /** Room で開く(移行は Migrations.ALL で走る。Room の検証もここで走る)。 */
    private fun openRoom(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*Migrations.ALL)
        .allowMainThreadQueries()
        .build()

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }
    private fun SupportSQLiteDatabase.str(sql: String): String = query(sql).use { it.moveToFirst(); it.getString(0) }

    private fun SupportSQLiteDatabase.columnNames(table: String): List<String> =
        query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            buildList { while (c.moveToNext()) add(c.getString(nameIdx)) }
        }

    @Test
    fun migration_addsUnknownColumns_andTurnsSingleExpensesIntoGroupsWithOneItem() = runBlocking {
        createV13()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase // 移行と Room の検証がここで走る。通らなければ例外
            assertEquals(14, db.version)
            assertEquals(AppDatabase.VERSION, db.version)
            assertTrue(db.columnNames("receipt_items").contains("unknownAmount"))
            assertTrue(db.columnNames("expenses").contains("unknownAmount"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM receipt_items WHERE unknownAmount != 0"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE unknownAmount != 0"))

            // 支出の行の数と id は変わらない。まとまりは 1 + 単独の支出2つ
            assertEquals(5L, db.long("SELECT COUNT(*) FROM expenses"))
            assertEquals(3L, db.long("SELECT COUNT(*) FROM expense_groups"))
            // 単独の支出は残らない。消した品目の非表示の行は、同じ支払いのまとまりへ消したまま付け替わる
            assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE groupId IS NULL"))
            assertEquals(db.long("SELECT groupId FROM expenses WHERE id = 4"), db.long("SELECT groupId FROM expenses WHERE id = 5"))
            assertEquals(1L, db.long("SELECT deletedByUser FROM expenses WHERE id = 5"))

            // 支出 3: 同じ値のまとまり。品名は捨て、編集済みの印はまとまりへ移る
            val g3 = db.long("SELECT groupId FROM expenses WHERE id = 3")
            assertEquals("2026-10-06", db.str("SELECT date FROM expense_groups WHERE id = $g3"))
            assertEquals("13:00", db.str("SELECT time FROM expense_groups WHERE id = $g3"))
            assertEquals(1200L, db.long("SELECT amount FROM expense_groups WHERE id = $g3"))
            assertEquals("外食", db.str("SELECT category FROM expense_groups WHERE id = $g3"))
            assertEquals("自分で", db.str("SELECT memo FROM expense_groups WHERE id = $g3"))
            assertEquals(2L, db.long("SELECT receiptId FROM expense_groups WHERE id = $g3"))
            assertEquals(2000L, db.long("SELECT createdAt FROM expense_groups WHERE id = $g3"))
            assertEquals(1L, db.long("SELECT categoryEdited FROM expense_groups WHERE id = $g3"))
            assertEquals(1L, db.long("SELECT memoEdited FROM expense_groups WHERE id = $g3"))
            assertEquals(1L, db.long("SELECT timeEdited FROM expense_groups WHERE id = $g3"))
            assertEquals(0L, db.long("SELECT amountEdited FROM expense_groups WHERE id = $g3"))
            assertEquals(SingleExpenseConversion.groupUid("e-3"), db.str("SELECT uid FROM expense_groups WHERE id = $g3"))
            assertEquals("", db.str("SELECT name FROM expenses WHERE id = 3"))
            assertEquals("e-3", db.str("SELECT uid FROM expenses WHERE id = 3"))
            assertEquals(1200L, db.long("SELECT amount FROM expenses WHERE id = 3"))
            assertEquals(0L, db.long("SELECT categoryEdited + memoEdited + timeEdited + nameEdited FROM expenses WHERE id = 3"))

            // 家計簿の一覧: 3件の支出。単独の支出の金額はそのまま(合計は 700 + 1200 + 300)
            val repo = room.expenseDao()
            val rows = repo.observeLedgerRows("2026-10-01", "2026-10-31").first()
            val groups = room.expenseGroupDao().observeBetween("2026-10-01", "2026-10-31").first()
            val entries = LedgerLogic.buildEntries(rows, groups)
            assertEquals(3, entries.size)
            assertTrue(entries.all { it is LedgerEntry.Group })
            assertEquals(2200L, entries.sumOf { it.amount })
            // カテゴリ: 単独の支出だった行は、まとまりのカテゴリで数える
            assertEquals(
                mapOf("食費" to 200L, "日用品" to 500L, "外食" to 1500L),
                LedgerLogic.totalsByCategory(entries).associate { it.category to it.total },
            )
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_thenSync_keepsAmountsAndIds_andAdvanceGetsItems() = runBlocking {
        createV13()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            val splits = SplitRepository(room, room.splitDao(), room.receiptDao())
            val receipts = ReceiptRepository(room, room.receiptDao(), room.expenseDao(), room.expenseGroupDao(), splits)
            val g3 = db.long("SELECT groupId FROM expenses WHERE id = 3")
            val g4 = db.long("SELECT groupId FROM expenses WHERE id = 4")
            for (rid in listOf(1L, 2L, 3L)) receipts.syncLedger(rid)

            // 品目の無い支払い(2): まとまりと1品目は同じ行のまま。カテゴリ・メモは編集済みなので残る
            assertEquals(g3, db.long("SELECT groupId FROM expenses WHERE id = 3"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM expenses WHERE receiptId = 2"))
            assertEquals(1200L, db.long("SELECT amount FROM expenses WHERE id = 3"))
            assertEquals("外食", db.str("SELECT category FROM expense_groups WHERE id = $g3"))
            assertEquals("自分で", db.str("SELECT memo FROM expenses WHERE id = 3"))

            // 立て替え(3): まとまりは同じ行のまま。単独の支出だった1品目は、品目の行(自分の分 300 を 400:500 で配る)に置き換わる。
            // 消した品目(枝豆)は消したまま残る
            assertEquals(listOf(g4), db.query("SELECT id FROM expense_groups WHERE receiptId = 3").use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } })
            assertEquals(300L, db.long("SELECT amount FROM expense_groups WHERE id = $g4"))
            assertNull(room.expenseDao().getById(4))
            val rows3 = room.expenseDao().getByReceiptId(3)
            assertTrue(rows3.all { it.groupId == g4 })
            assertEquals(listOf("ビール"), rows3.filter { !it.deletedByUser }.map { it.name })
            assertEquals(listOf("枝豆"), rows3.filter { it.deletedByUser }.map { it.name })
            assertEquals(5L, rows3.single { it.deletedByUser }.id)

            // 支払い1 は変わらない
            assertEquals(listOf(1L, 2L), room.expenseDao().getByReceiptId(1).map { it.id })
            assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE groupId IS NULL"))
        } finally {
            room.close()
        }
    }

    @Test
    fun preMigrationBackup_copiesV13() {
        createV13()
        val root = File(context.filesDir, "db-backup")
        assertEquals(13, PreMigrationBackup.readUserVersion(dbFile))
        val dest = PreMigrationBackup.backupIfNeeded(dbFile, root, AppDatabase.VERSION)
        assertNotNull(dest)
        assertTrue(File(dest, name).isFile)
        // 同じ版では一度だけ
        assertNull(PreMigrationBackup.backupIfNeeded(dbFile, root, AppDatabase.VERSION))
        assertFalse(File(root, "v14").exists())
    }
}
