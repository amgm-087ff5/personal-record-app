package com.amgm.personallog.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Room が KSP でビルド時に生成する 8.json(app/schemas)と、Migrations.kt の v8 の SQL が一致するかを確かめる。
 * 一致しないと、端末でアプリが起動時に落ちる(Room の移行後の検証で "Migration didn't properly handle")。
 * 8.json は feat ブランチではコミットされないが、テストの前に KSP が生成する。
 */
class SchemaMatchTest {
    private val dir = "schemas/com.amgm.personallog.data.db.AppDatabase"

    private fun load(version: Int): JsonObject {
        // テスト実行時の作業ディレクトリは app/。念のためリポジトリ直下からの実行にも対応する
        val file = listOf(File("$dir/$version.json"), File("app/$dir/$version.json")).firstOrNull { it.isFile }
            ?: error("$version.json が見つかりません(作業ディレクトリ=${File(".").absolutePath})。KSP が生成していません")
        return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
    }

    private fun entities(db: JsonObject): Map<String, JsonObject> =
        db["entities"]!!.jsonArray.map { it.jsonObject }.associateBy { it["tableName"]!!.jsonPrimitive.content }

    private fun createSql(e: JsonObject, table: String): String =
        e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)

    private fun indexSqls(e: JsonObject, table: String): List<String> =
        (e["indices"] as? JsonArray).orEmpty().map { it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table) }

    @Test
    fun schemaIsVersion8() {
        assertEquals(8, load(8)["version"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun receipts_createSqlMatchesMigration() {
        val e = entities(load(8)).getValue("receipts")
        assertEquals(Migrations.receiptsV8CreateSql("receipts"), createSql(e, "receipts"))
        assertTrue("receipts にインデックスは無い", indexSqls(e, "receipts").isEmpty())
    }

    @Test
    fun receiptNotDuplicates_createSqlAndIndexMatchMigration() {
        val e = entities(load(8)).getValue("receipt_not_duplicates")
        assertEquals(Migrations.notDuplicatesV8CreateSql("receipt_not_duplicates"), createSql(e, "receipt_not_duplicates"))
        assertEquals(
            listOf(Migrations.notDuplicatesV8IndexSql("receipt_not_duplicates")),
            indexSqls(e, "receipt_not_duplicates"),
        )
    }

    @Test
    fun otherTables_areUnchangedFromV7() {
        val v7 = entities(load(7))
        val v8 = entities(load(8))
        assertEquals(v7.keys - "receipts", v8.keys - "receipts" - "receipt_not_duplicates")
        for ((name, e7) in v7) {
            if (name == "receipts") continue
            val e8 = v8.getValue(name)
            assertEquals("$name の createSql", createSql(e7, name), createSql(e8, name))
            assertEquals("$name のインデックス", indexSqls(e7, name), indexSqls(e8, name))
        }
    }
}
