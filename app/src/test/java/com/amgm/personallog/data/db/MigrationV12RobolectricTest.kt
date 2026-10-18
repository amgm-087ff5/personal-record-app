package com.amgm.personallog.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.amgm.personallog.data.repository.ReceiptRepository
import com.amgm.personallog.data.repository.SplitRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * v11 -> v12 の移行(支出は支払いから作る)を、本物の SQLite(Robolectric)で実行する。
 * 移行前の DB は app/schemas の 11.json(Room の生成物)の createSql・indices で作り、支払いに属さない支出を入れておく。
 * 確かめること: 行の数と id が変わらない / 支払いが1件ずつ・品目に対応が付く / 移行後に同期しても金額の合計が変わらない。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV12RobolectricTest {
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

    /** 移行前(v11)の CREATE 文を 11.json から読む(テーブルとインデックス)。 */
    private fun schema11(): List<String> {
        val rel = "schemas/com.amgm.personallog.data.db.AppDatabase/11.json"
        val file = listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile } ?: error("11.json が見つかりません")
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
    private fun SupportSQLiteDatabase.ids(sql: String): List<Long> =
        query(sql).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    /**
     * v11 の DB を作り、支払いに属さない支出を入れて閉じる。
     *  - グループ1(2026-10-05 12:30・900円・食費・メモ「スーパー」): 品目3つ(500・300・100)。300円の品目は家計簿で消してある(deletedByUser)。
     *  - グループ2(2026-10-06・時刻なし・1500円・外食): 品目1つ。
     *  - 単独の支出: 1200円(2026-10-07 08:10・交通費・メモ「電車」)と、250円(2026-10-08・時刻なし・食費・メモ「コンビニ」)。
     */
    private fun createV11() {
        val h = helper(11, created = { db -> schema11().forEach { db.execSQL(it) } })
        val db = h.writableDatabase
        db.execSQL("INSERT INTO expense_groups (id, date, time, amount, category, memo, createdAt) VALUES (1, '2026-10-05', '12:30', 900, '食費', 'スーパー', 1000)")
        db.execSQL("INSERT INTO expense_groups (id, date, time, amount, category, memo, createdAt) VALUES (2, '2026-10-06', '', 1500, '外食', '居酒屋', 2000)")
        db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, createdAt, groupId, name, uid) VALUES (1, '2026-10-05', '12:30', 500, '食費', '', 1000, 1, 'りんご', 'e-1')")
        db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, createdAt, groupId, name, uid, deletedByUser) VALUES (2, '2026-10-05', '12:30', 300, '日用品', '箱入り', 1000, 1, 'ラップ', 'e-2', 1)")
        db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, createdAt, groupId, name, uid) VALUES (3, '2026-10-05', '12:30', 100, '食費', '', 1000, 1, '牛乳', 'e-3')")
        db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, createdAt, groupId, name, uid) VALUES (4, '2026-10-06', '', 1500, '外食', '', 2000, 2, '', 'e-4')")
        db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, createdAt, uid) VALUES (5, '2026-10-07', '08:10', 1200, '交通費', '電車', 3000, 'e-5')")
        db.execSQL("INSERT INTO expenses (id, date, time, amount, category, memo, createdAt, uid) VALUES (6, '2026-10-08', '', 250, '食費', 'コンビニ', 4000, 'e-6')")
        h.close()
    }

    /** Room で開く(移行は Migrations.ALL で走る。Room の検証もここで走る)。 */
    private fun openRoom(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*Migrations.ALL)
        .allowMainThreadQueries()
        .build()

    @Test
    fun migration_keepsRowCountsAndIds_andMakesEveryPaymentWithItsItems() {
        createV11()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase // 移行と Room の検証がここで走る。通らなければ例外
            assertEquals(AppDatabase.VERSION, db.version)
            // 行の数と id は変わらない
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), db.ids("SELECT id FROM expenses ORDER BY id"))
            assertEquals(listOf(1L, 2L), db.ids("SELECT id FROM expense_groups ORDER BY id"))
            // 支払いは、支払いに属さなかった支出グループ2つ + 単独の支出2つ
            assertEquals(4L, db.long("SELECT COUNT(*) FROM receipts"))
            // 品目は、グループの中の支出の行の数(3 + 1)
            assertEquals(4L, db.long("SELECT COUNT(*) FROM receipt_items"))
            // すべての支出に支払いが付く
            assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE receiptId IS NULL"))
            // グループの品目は、品目の行に対応が付く(子は receiptItemId あり)。単独の支出は品目なし
            assertEquals(4L, db.long("SELECT COUNT(*) FROM expenses WHERE groupId IS NOT NULL AND receiptItemId IS NOT NULL"))
            assertEquals(2L, db.long("SELECT COUNT(*) FROM expenses WHERE groupId IS NULL AND receiptItemId IS NULL"))
            // 支払いは1件ずつ: 支払いの行どうしが別々に付いている
            val g1 = db.long("SELECT receiptId FROM expense_groups WHERE id = 1")
            val g2 = db.long("SELECT receiptId FROM expense_groups WHERE id = 2")
            val s5 = db.long("SELECT receiptId FROM expenses WHERE id = 5")
            val s6 = db.long("SELECT receiptId FROM expenses WHERE id = 6")
            assertEquals(4, listOf(g1, g2, s5, s6).toSet().size)
            assertEquals(3L, db.long("SELECT COUNT(*) FROM expenses WHERE receiptId = $g1"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM expenses WHERE receiptId = $g2"))
            // 支払いの中身: 手入力(画像なし・確定済み・店名は空)。日時は支出の日時に従う
            assertEquals("2026-10-05 12:30", db.str("SELECT purchasedAt FROM receipts WHERE id = $g1"))
            assertEquals("2026-10-06", db.str("SELECT purchasedAt FROM receipts WHERE id = $g2"))
            assertEquals("2026-10-08", db.str("SELECT purchasedAt FROM receipts WHERE id = $s6"))
            assertEquals(900L, db.long("SELECT totalAmount FROM receipts WHERE id = $g1"))
            assertEquals(250L, db.long("SELECT totalAmount FROM receipts WHERE id = $s6"))
            assertEquals("manual", db.str("SELECT totalSource FROM receipts WHERE id = $g1"))
            assertEquals("confirmed", db.str("SELECT status FROM receipts WHERE id = $g1"))
            assertEquals("", db.str("SELECT imagePath FROM receipts WHERE id = $g1"))
            assertEquals("", db.str("SELECT storeName FROM receipts WHERE id = $g1"))
            assertEquals(1000L, db.long("SELECT createdAt FROM receipts WHERE id = $g1"))
            // 品目は写され、支出側と対応する(名前・金額・カテゴリ・メモ)
            assertEquals(
                listOf("りんご" to 500L, "ラップ" to 300L, "牛乳" to 100L),
                db.query("SELECT name, amount FROM receipt_items WHERE receiptId = $g1 ORDER BY id").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0) to c.getLong(1)) }
                },
            )
            assertEquals(
                "日用品",
                db.str("SELECT ri.category FROM receipt_items ri JOIN expenses e ON e.receiptItemId = ri.id WHERE e.id = 2"),
            )
            assertEquals(
                "箱入り",
                db.str("SELECT ri.memo FROM receipt_items ri JOIN expenses e ON e.receiptItemId = ri.id WHERE e.id = 2"),
            )
            // 家計簿で消した品目は、消したまま(移行で復活させない)
            assertEquals(1L, db.long("SELECT deletedByUser FROM expenses WHERE id = 2"))
        } finally {
            room.close()
        }
    }

    @Test
    fun migration_thenSync_keepsTheTotals_andEachPaymentOwnsItsRows() {
        createV11()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            fun sums(): List<Long> = listOf(
                db.long("SELECT COALESCE(SUM(amount), 0) FROM expenses"),
                db.long("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE deletedByUser = 0"),
                db.long("SELECT COALESCE(SUM(amount), 0) FROM expense_groups"),
            )
            val before = sums()
            val receiptIds = db.ids("SELECT id FROM receipts ORDER BY id")
            // 移行後の支払いを、1件ずつ同期する(保存・確定と同じ経路)
            val splits = SplitRepository(room, room.splitDao(), room.receiptDao())
            val receipts = ReceiptRepository(room, room.receiptDao(), room.expenseDao(), room.expenseGroupDao(), splits)
            for (rid in receiptIds) runBlocking { receipts.syncLedger(rid) }

            // 金額の合計は変わらない。行の数と id も変わらない
            assertEquals(before, sums())
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), db.ids("SELECT id FROM expenses ORDER BY id"))
            assertEquals(listOf(1L, 2L), db.ids("SELECT id FROM expense_groups ORDER BY id"))
            // 同期のあとも、支払いごとの行は各支払いに残る(別の支払いの行を取り込まない)
            val g1 = db.long("SELECT receiptId FROM expense_groups WHERE id = 1")
            assertEquals(3L, db.long("SELECT COUNT(*) FROM expenses WHERE receiptId = $g1"))
            // メモ(店名)とカテゴリは、同期で既定値に戻らない
            assertEquals("スーパー", db.str("SELECT memo FROM expense_groups WHERE id = 1"))
            assertEquals("食費", db.str("SELECT category FROM expense_groups WHERE id = 1"))
            assertEquals("電車", db.str("SELECT memo FROM expenses WHERE id = 5"))
            assertEquals("交通費", db.str("SELECT category FROM expenses WHERE id = 5"))
            // 単独の支出は単独1行のまま(グループは作られない)
            assertTrue(db.long("SELECT COUNT(*) FROM expense_groups WHERE receiptId = (SELECT receiptId FROM expenses WHERE id = 5)") == 0L)
        } finally {
            room.close()
        }
    }
}
