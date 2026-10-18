package com.amgm.personallog.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** @Database(version = N) のリテラルと AppDatabase.VERSION(PreMigrationBackup が使う)が食い違わないことを確かめる。 */
class SchemaVersionTest {
    private fun source(): String {
        val rel = "src/main/java/com/amgm/personallog/data/db/AppDatabase.kt"
        return listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile }?.readText() ?: error("AppDatabase.kt が見つかりません")
    }

    @Test
    fun databaseAnnotationVersionEqualsConstant() {
        val m = Regex("""@Database\([^)]*?\bversion\s*=\s*(\d+)""", RegexOption.DOT_MATCHES_ALL).find(source())
        assertTrue("@Database の version が見つかりません", m != null)
        assertEquals(AppDatabase.VERSION, m!!.groupValues[1].toInt())
    }

    @Test
    fun versionIs11_andMigrationsReachIt() {
        assertEquals(11, AppDatabase.VERSION)
        assertEquals(AppDatabase.VERSION, Migrations.ALL.maxOf { it.endVersion })
    }
}
