package com.amgm.personallog.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.amgm.personallog.data.repository.PaymentRepository
import com.amgm.personallog.data.repository.SplitRepository
import com.amgm.personallog.domain.SingleExpenseItemConversion
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
 * v14 -> v15 の移行(支出のメモに写していた店名を消す)を、本物の SQLite(Robolectric)で実行する。
 * 移行前の DB は app/schemas の 14.json(Room の生成物)の createSql・indices で作る。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV15RobolectricTest {
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

    private fun schema14(): List<String> {
        val rel = "schemas/com.amgm.personallog.data.db.AppDatabase/14.json"
        val file = listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile } ?: error("14.json が見つかりません")
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
     * v14 の DB を作る(支払い1「スーパー」、支払い2「食堂」):
     *  - まとまり1: メモ「スーパー」(店名の写し・未編集) → 空になる
     *  - まとまり2: メモ「食堂」だが利用者が直した(memoEdited = 1) → 残る
     *  - まとまり3: 支払い1に紐づくが、メモが店名と違う「友達と」 → 残る
     *  - 品目10: 品目の無い支払いの1品目(receiptItemId なし)、メモ「スーパー」 → 空になる
     *  - 品目11: 支払いの品目に対応する行(receiptItemId あり)、メモ「スーパー」 → 品目のメモなので残る
     */
    private fun createV14() {
        val cb = object : SupportSQLiteOpenHelper.Callback(14) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(false)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = schema14().forEach { db.execSQL(it) }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.path).callback(cb).build()
        val h = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = h.writableDatabase
        db.execSQL(
            "INSERT INTO receipts (id, purchasedAt, storeName, totalAmount, imagePath, rawJson, status, createdAt, totalSource) VALUES " +
                "(1, '2026-10-05 12:30', 'スーパー', 700, '', '', 'confirmed', 1000, 'manual'), " +
                "(2, '2026-10-06 13:00', '食堂', 1200, '', '', 'confirmed', 2000, 'manual')",
        )
        db.execSQL(
            "INSERT INTO receipt_items (id, receiptId, name, amount, category, taxRate, priceIncludesTax, memo) VALUES " +
                "(21, 1, '牛乳', 200, '食費', 8, 1, '')",
        )
        db.execSQL(
            "INSERT INTO expense_groups (id, date, time, amount, category, memo, receiptId, createdAt, uid, memoEdited) VALUES " +
                "(1, '2026-10-05', '12:30', 700, '日用品', 'スーパー', 1, 1000, 'g-1', 0), " +
                "(2, '2026-10-06', '13:00', 1200, '外食', '食堂', 2, 2000, 'g-2', 1), " +
                "(3, '2026-10-05', '12:30', 700, '日用品', '友達と', 1, 1000, 'g-3', 0)",
        )
        db.execSQL(
            "INSERT INTO expenses (id, date, time, amount, category, memo, receiptId, createdAt, groupId, receiptItemId, name, uid) VALUES " +
                "(10, '2026-10-05', '12:30', 700, '日用品', 'スーパー', 1, 1000, 1, NULL, '', 'e-10'), " +
                "(11, '2026-10-05', '12:30', 200, '食費', 'スーパー', 1, 1000, 3, 21, '牛乳', 'e-11')",
        )
        h.close()
    }

    private fun openRoom(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*Migrations.ALL)
        .allowMainThreadQueries()
        .build()

    private fun SupportSQLiteDatabase.str(sql: String): String = query(sql).use { it.moveToFirst(); it.getString(0) }

    @Test
    fun migration_clearsStoreNameCopiedIntoMemo_keepsUserMemos() {
        createV14()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals("", db.str("SELECT memo FROM expenses WHERE id = 1"))
            assertEquals("食堂", db.str("SELECT memo FROM expenses WHERE id = 2"))
            assertEquals("友達と", db.str("SELECT memo FROM expenses WHERE id = 3"))
            assertEquals("", db.str("SELECT memo FROM expense_items WHERE id = 10"))
            assertEquals("スーパー", db.str("SELECT memo FROM expense_items WHERE id = 11"))
            // 支払いの店名は変えない
            assertEquals("スーパー", db.str("SELECT storeName FROM payments WHERE id = 1"))
        } finally {
            room.close()
        }
    }
}
