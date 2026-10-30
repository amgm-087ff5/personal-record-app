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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * v15 -> v16 の移行(支払い ⇒ 支出 の親子を崩さない)を、本物の SQLite(Robolectric)で実行する。
 * 移行前の DB は app/schemas の 15.json(Room の生成物)の createSql・indices で作る。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV16RobolectricTest {
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

    private fun schema15(): List<String> {
        val rel = "schemas/com.amgm.personallog.data.db.AppDatabase/15.json"
        val file = listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile } ?: error("15.json が見つかりません")
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
     * v15 の DB を作る:
     *  - 支払い1(確定・700): 支出1 + 品目1(品目の receiptId が空) → 残る。品目の receiptId は 1 に揃う
     *  - 支払い2(確定・1200・品目あり): 支出なし(支出だけ消した) → 支払い・品目・消した品目・重複でない印ごと消える
     *  - 支払い3(確定・900・割り勘のシートあり): 支出なし(自分の負担0円の可能性) → 残る
     *  - 支払い4(下書き・500): 支出なし → 残る(起動後に支出を作り直す)
     *  - 支払い5(下書き・合計なし・品目なし): 支出なし(読み取り前) → 残る
     *  - 支出6(支払いなし・300): 手入力の支払いが作られて紐づく
     */
    private fun createV15() {
        val cb = object : SupportSQLiteOpenHelper.Callback(15) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(false)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = schema15().forEach { db.execSQL(it) }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.path).callback(cb).build()
        val h = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = h.writableDatabase
        db.execSQL(
            "INSERT INTO receipts (id, purchasedAt, storeName, totalAmount, imagePath, rawJson, status, createdAt, totalSource) VALUES " +
                "(1, '2026-10-05 12:30', 'スーパー', 700, '', '{}', 'confirmed', 1000, 'manual'), " +
                "(2, '2026-10-06 13:00', '食堂', 1200, 'receipts/2.jpg', '{}', 'confirmed', 2000, 'printed'), " +
                "(3, '2026-10-07 19:00', '居酒屋', 900, '', '{}', 'confirmed', 3000, 'manual'), " +
                "(4, '2026-10-08 09:00', 'パン屋', 500, '', '{}', 'draft', 4000, 'printed'), " +
                "(5, '2026-10-09 10:00', '', NULL, 'receipts/5.jpg', '{}', 'draft', 5000, 'unknown')",
        )
        db.execSQL(
            "INSERT INTO receipt_items (id, receiptId, name, amount, category, taxRate, priceIncludesTax, memo) VALUES " +
                "(11, 1, '牛乳', 700, '食費', 10, 1, ''), (21, 2, '定食', 1200, '外食', 10, 1, '')",
        )
        db.execSQL("INSERT INTO split_sheets (receiptId) VALUES (3)")
        db.execSQL("INSERT INTO receipt_not_duplicates (receiptIdLow, receiptIdHigh, createdAt) VALUES (1, 2, 1)")
        db.execSQL(
            "INSERT INTO expense_groups (id, date, time, amount, category, memo, receiptId, createdAt, uid) VALUES " +
                "(1, '2026-10-05', '12:30', 700, '食費', '', 1, 1000, 'g-1'), " +
                "(6, '2026-10-10', '08:00', 300, 'その他', 'メモ', NULL, 6000, 'g-6')",
        )
        db.execSQL(
            "INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, receiptItemId, name, uid, deletedByUser) VALUES " +
                "(1, '2026-10-05', '12:30', 700, '食費', '', NULL, 1000, 1, 11, '牛乳', 'e-1', 0), " +
                "(2, '2026-10-06', '13:00', 1200, '外食', '', 2, 2000, NULL, 21, '定食', 'e-2', 1), " +
                "(6, '2026-10-10', '08:00', 300, 'その他', '', NULL, 6000, 6, NULL, '', 'e-6', 0)",
        )
        h.close()
    }

    private fun openRoom(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*Migrations.ALL)
        .allowMainThreadQueries()
        .build()

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }

    private fun SupportSQLiteDatabase.ids(sql: String): List<Long> = query(sql).use { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0)) }
    }

    @Test
    fun migration_keepsParentChild_deletesConfirmedPaymentsWithoutExpense() = runBlocking {
        createV15()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase // 移行と Room の検証がここで走る。通らなければ例外
            assertEquals(AppDatabase.VERSION, db.version)

            // 支払い2(確定・支出なし)は消え、子の行も残らない。1・3・4・5 は残り、支出6 のための手入力の支払いが増える
            val payments = db.ids("SELECT id FROM payments ORDER BY id")
            assertEquals(listOf(1L, 3L, 4L, 5L), payments.take(4))
            assertEquals(5, payments.size)
            assertEquals(0L, db.long("SELECT COUNT(*) FROM payment_items WHERE paymentId = 2"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM expense_items WHERE paymentId = 2 OR id = 2"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM payment_not_duplicates"))

            // 支出6 は手入力の支払い(確定・合計 300)に紐づく。品目も支払いの品目に対応する
            val manual = payments.last()
            assertEquals(manual, db.long("SELECT paymentId FROM expenses WHERE id = 6"))
            assertEquals(300L, db.long("SELECT totalAmount FROM payments WHERE id = $manual"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM payments WHERE id = $manual AND status = 'confirmed'"))
            assertEquals(manual, db.long("SELECT paymentId FROM expense_items WHERE id = 6"))
            // 品目1 の支払いは、まとまりの支払いに揃う
            assertEquals(1L, db.long("SELECT paymentId FROM expense_items WHERE id = 1"))

            // 家計簿に入る支出は変わらない
            assertEquals(2L, db.long("SELECT COUNT(*) FROM expenses"))
            assertEquals(1000L, db.long("SELECT SUM(amount) FROM expenses"))

            // 表の形: 支出の receiptId は NOT NULL、支払いを消すと支出・品目も消える(CASCADE)
            val notNull = db.query("PRAGMA table_info(`expenses`)").use { c ->
                var v = -1
                while (c.moveToNext()) if (c.getString(c.getColumnIndexOrThrow("name")) == "paymentId") v = c.getInt(c.getColumnIndexOrThrow("notnull"))
                v
            }
            assertEquals(1, notNull)
            for (t in listOf("expenses", "expense_items")) {
                val onDelete = db.query("PRAGMA foreign_key_list(`$t`)").use { c ->
                    var v = ""
                    while (c.moveToNext()) if (c.getString(c.getColumnIndexOrThrow("table")) == "payments") v = c.getString(c.getColumnIndexOrThrow("on_delete"))
                    v
                }
                assertEquals("$t の paymentId", "CASCADE", onDelete)
            }

            // 外部キーが有効な実行時: 支払いを消すと、支出と品目も消える
            room.paymentDao().delete(room.paymentDao().getById(1L)!!)
            assertEquals(0L, db.long("SELECT COUNT(*) FROM expenses WHERE id = 1"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM expense_items WHERE id = 1"))

            // 支出の無い下書き(支払い4)は、起動後の作り直しの対象。割り勘のシートがある支払い3 は対象外
            assertEquals(listOf(4L, 5L), room.paymentDao().draftIdsWithoutExpense().sorted())
            assertTrue(db.long("SELECT COUNT(*) FROM split_sheets WHERE paymentId = 3") == 1L)
        } finally {
            room.close()
        }
    }
}
