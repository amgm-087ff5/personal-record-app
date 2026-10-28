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
 * v12 -> v13 の移行(receipts.headcount の追加)を、本物の SQLite(Robolectric)で実行する。
 * 移行前の DB は app/schemas の 12.json(Room の生成物)の createSql・indices で作る。
 * 確かめること: 既存の支払いは残り、headcount は null になる / DAO の setHeadcount で値が入り null に戻せる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV13RobolectricTest {
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

    /** 移行前(v12)の CREATE 文を 12.json から読む(テーブルとインデックス)。 */
    private fun schema12(): List<String> {
        val rel = "schemas/com.amgm.personallog.data.db.AppDatabase/12.json"
        val file = listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile } ?: error("12.json が見つかりません")
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

    /** v12 の DB を作り、支払いを1件入れて閉じる(headcount の列は無い)。 */
    private fun createV12() {
        val cb = object : SupportSQLiteOpenHelper.Callback(12) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(false)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = schema12().forEach { db.execSQL(it) }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(dbFile.path).callback(cb).build()
        val h = FrameworkSQLiteOpenHelperFactory().create(config)
        h.writableDatabase.execSQL(
            "INSERT INTO receipts (id, purchasedAt, storeName, totalAmount, imagePath, rawJson, status, createdAt) " +
                "VALUES (1, '2026-10-09 18:32', '居酒屋A', 5000, '', '{}', 'confirmed', 1000)",
        )
        // 確定済みの支払いには自分の支出がある(v16 で支出の無い確定済みの支払いは消えるため、実際のデータと同じ形にする)
        h.writableDatabase.execSQL(
            "INSERT INTO expense_groups (id, date, time, amount, category, memo, receiptId, createdAt, uid) " +
                "VALUES (1, '2026-10-09', '18:32', 5000, '外食', '', 1, 1000, 'g-1')",
        )
        h.close()
    }

    /** Room で開く(移行は Migrations.ALL で走る。Room の検証もここで走る)。 */
    private fun openRoom(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*Migrations.ALL)
        .allowMainThreadQueries()
        .build()

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }

    private fun SupportSQLiteDatabase.columnNames(table: String): List<String> =
        query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            buildList { while (c.moveToNext()) add(c.getString(nameIdx)) }
        }

    @Test
    fun migration_addsHeadcount_keepsOldRows_andDaoSetsAndClearsIt() = runBlocking {
        createV12()
        val room = openRoom()
        try {
            val db = room.openHelper.writableDatabase // 移行と Room の検証がここで走る。通らなければ例外
            // v13 -> v14 も Migrations.ALL で続けて走る
            assertEquals(AppDatabase.VERSION, db.version)
            // 列が増えている(既存の列は残る)
            assertTrue(db.columnNames("receipts").contains("headcount"))
            assertTrue(db.columnNames("receipts").contains("rawJson"))
            // 既存の支払いは残り、人数は不明(null)
            assertEquals(1L, db.long("SELECT COUNT(*) FROM receipts"))
            assertEquals(5000L, db.long("SELECT totalAmount FROM receipts WHERE id = 1"))
            assertNull(room.receiptDao().getById(1L)?.headcount)

            // DAO で人数を入れて、読み直せる。null に戻せる
            assertEquals(1, room.receiptDao().setHeadcount(1L, 3))
            assertEquals(3, room.receiptDao().getById(1L)?.headcount)
            assertEquals(5000, room.receiptDao().getById(1L)?.totalAmount)
            room.receiptDao().setHeadcount(1L, null)
            assertNull(room.receiptDao().getById(1L)?.headcount)
        } finally {
            room.close()
        }
    }
}
