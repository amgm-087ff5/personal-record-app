package com.amgm.personallog.data.db

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * MIGRATION_8_9 の手順(SQLの順序と件数チェック)を、記録するだけの偽DBで確かめる。
 * SQL の中身が 9.json と合うかは SchemaMatchTest が、実際の SQLite での動作は端末で確かめる。
 */
class MigrationV9Test {
    private class FakeDb(
        /** 件数クエリの結果を、呼ばれた順に返す(前に3つ、後に3つ)。 */
        private val counts: List<Long>,
    ) {
        val executed = ArrayList<String>()
        private var countCalls = 0

        private fun cursor(value: Long?): Cursor = Proxy.newProxyInstance(
            Cursor::class.java.classLoader,
            arrayOf(Cursor::class.java),
        ) { _, m, _ ->
            when (m.name) {
                "moveToFirst" -> value != null
                "moveToNext" -> false
                "getLong" -> value ?: 0L
                "isNull" -> value == null
                "close" -> null
                else -> null
            }
        } as Cursor

        val db: SupportSQLiteDatabase = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, m, args ->
            when (m.name) {
                "execSQL" -> {
                    executed += args!![0] as String
                    null
                }
                "query" -> {
                    val sql = args!![0] as String
                    when {
                        sql.startsWith("SELECT COUNT(*)") -> cursor(counts[countCalls++])
                        sql.contains("sqlite_sequence") -> cursor(42L)
                        sql.startsWith("PRAGMA foreign_key_check") -> cursor(null)
                        else -> cursor(null)
                    }
                }
                else -> null
            }
        } as SupportSQLiteDatabase
    }

    @Test
    fun runsAlterBeforeRebuild_andFillsUids() {
        val f = FakeDb(listOf(5, 2, 7, 5, 2, 7))
        Migrations.MIGRATION_8_9.migrate(f.db)
        val sql = f.executed

        // 親(expense_groups)は ALTER で列を足す。作り直さない
        assertTrue(sql.none { it.contains("DROP TABLE `expense_groups`") })
        for (c in Migrations.GROUPS_V9_ADDED_COLUMNS) assertTrue(sql.contains(Migrations.groupsV9AddColumnSql(c)))
        val uidFill = sql.indexOfFirst { it.startsWith("UPDATE `expense_groups` SET `uid` = lower(hex(randomblob(16)))") }
        assertTrue(uidFill > sql.indexOf(Migrations.groupsV9AddColumnSql(Migrations.GROUPS_V9_ADDED_COLUMNS.first { it.name == "uid" })))

        // 子(expenses)は作り直す: 新しい表 -> 既存行のコピー(uid は乱数) -> DROP -> RENAME -> インデックス
        val create = sql.indexOf(Migrations.expensesV9CreateSql("expenses_new"))
        val copy = sql.indexOfFirst { it.startsWith("INSERT INTO `expenses_new`") && it.contains("randomblob(16)") }
        val drop = sql.indexOf("DROP TABLE `expenses`")
        val rename = sql.indexOf("ALTER TABLE `expenses_new` RENAME TO `expenses`")
        assertTrue(create in 0 until copy)
        assertTrue(copy < drop && drop < rename)
        for (i in Migrations.expensesV9IndexSqls("expenses")) assertTrue(sql.indexOf(i) > rename)
        // 他の表は DROP しない
        assertEquals(1, sql.count { it.startsWith("DROP TABLE `expenses`") })
        assertTrue(sql.none { it.contains("DROP TABLE `receipt") })
    }

    @Test
    fun countMismatch_abortsTheMigration() {
        // 作り直しのあとで expenses が1件減った
        val f = FakeDb(listOf(5, 2, 7, 4, 2, 7))
        try {
            Migrations.MIGRATION_8_9.migrate(f.db)
            fail("件数が違うのに移行が成功した")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("件数"))
        }
    }

    @Test
    fun countsAreTakenBeforeAndAfter() {
        assertEquals(
            listOf("expenses", "expense_groups", "receipt_items"),
            Migrations.V9_COUNT_QUERIES.map { it.first },
        )
    }
}
