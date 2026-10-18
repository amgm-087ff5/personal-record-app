package com.amgm.personallog.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Room が KSP でビルド時に生成する 9.json(app/schemas)と、Migrations.kt の v9 の SQL が一致するかを確かめる。
 * 一致しないと、端末でアプリが起動時に落ちる(Room の移行後の検証で "Migration didn't properly handle")。
 * 9.json は feat ブランチではコミットされないが、テストの前に KSP が生成する。7.json はコミット済み。
 * 区別: app/schemas の下は Room の生成物だけを置く。手で作った v8 の資料は src/test/resources/schemas-fixture/8.json にあり、
 * この検査には使わない(v8 の SQL は Migrations の定数と、生成された 9.json の同じ表との一致で確かめる)。
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

    private val v9 get() = entities(load(9))

    @Test
    fun schemaIsVersion9() {
        assertEquals(9, load(9)["version"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun tableSetIsUnchanged() {
        assertEquals(
            setOf("diary_entries", "diary_photos", "receipts", "receipt_items", "expenses", "expense_groups", "receipt_not_duplicates"),
            v9.keys,
        )
    }

    @Test
    fun expenses_createSqlAndIndicesMatchMigration() {
        val e = v9.getValue("expenses")
        assertEquals(Migrations.expensesV9CreateSql("expenses"), createSql(e, "expenses"))
        assertEquals(Migrations.expensesV9IndexSqls("expenses"), indexSqls(e, "expenses"))
    }

    @Test
    fun expenseGroups_addedColumnsMatchAlterTableDefinitions() {
        val g = v9.getValue("expense_groups")
        val fields = g["fields"]!!.jsonArray.map { it.jsonObject }.associateBy { it["columnName"]!!.jsonPrimitive.content }
        val defs = ArrayList<String>()
        for (c in Migrations.GROUPS_V9_ADDED_COLUMNS) {
            val f = fields.getValue(c.name)
            assertEquals("${c.name} の型", c.type, f["affinity"]!!.jsonPrimitive.content)
            assertEquals("${c.name} は NOT NULL", "true", f["notNull"]!!.jsonPrimitive.content)
            assertEquals("${c.name} の既定値", c.default, f["defaultValue"]!!.jsonPrimitive.content)
            // ALTER TABLE ADD COLUMN の列定義が、CREATE TABLE の列定義と同じ文字列になる
            val def = "`${c.name}` ${c.type} NOT NULL DEFAULT ${c.default}"
            assertTrue("${c.name} の列定義が createSql にある", createSql(g, "expense_groups").contains(def))
            assertEquals("ALTER TABLE `expense_groups` ADD COLUMN $def", Migrations.groupsV9AddColumnSql(c))
            defs += def
        }
        // 既存の列・外部キー・インデックスは v7 のまま。新しい列が最後の列の後ろ、外部キーの前に並ぶ
        val v7 = entities(load(7)).getValue("expense_groups")
        assertEquals(
            createSql(v7, "expense_groups").replace(", FOREIGN KEY", ", " + defs.joinToString(", ") + ", FOREIGN KEY"),
            createSql(g, "expense_groups"),
        )
        assertEquals(indexSqls(v7, "expense_groups"), indexSqls(g, "expense_groups"))
    }

    @Test
    fun receipts_createSqlMatchesMigration() {
        val e = v9.getValue("receipts")
        assertEquals(Migrations.receiptsV8CreateSql("receipts"), createSql(e, "receipts"))
        assertTrue("receipts にインデックスは無い", indexSqls(e, "receipts").isEmpty())
    }

    @Test
    fun receiptNotDuplicates_createSqlAndIndexMatchMigration() {
        val e = v9.getValue("receipt_not_duplicates")
        assertEquals(Migrations.notDuplicatesV8CreateSql("receipt_not_duplicates"), createSql(e, "receipt_not_duplicates"))
        assertEquals(
            listOf(Migrations.notDuplicatesV8IndexSql("receipt_not_duplicates")),
            indexSqls(e, "receipt_not_duplicates"),
        )
    }

    @Test
    fun otherTables_areUnchangedFromV7() {
        val v7 = entities(load(7))
        for (name in listOf("diary_entries", "diary_photos", "receipt_items")) {
            val e7 = v7.getValue(name)
            val e9 = v9.getValue(name)
            assertEquals("$name の createSql", createSql(e7, name), createSql(e9, name))
            assertEquals("$name のインデックス", indexSqls(e7, name), indexSqls(e9, name))
        }
    }

    // ---- v10(割り勘の7表を追加)----

    private val v10 get() = entities(load(10))

    @Test
    fun schema10_isVersion10() {
        assertEquals(10, load(10)["version"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun v10_tableSetIsV9PlusSplitTables() {
        assertEquals(v9.keys + Migrations.SPLIT_V10_TABLES, v10.keys)
    }

    @Test
    fun v10_existingTablesAreUnchangedFromV9() {
        for (name in v9.keys) {
            assertEquals("$name の createSql", createSql(v9.getValue(name), name), createSql(v10.getValue(name), name))
            assertEquals("$name のインデックス", indexSqls(v9.getValue(name), name), indexSqls(v10.getValue(name), name))
        }
    }

    @Test
    fun v10_splitTables_createSqlAndIndicesMatchMigration() {
        for (t in Migrations.SPLIT_V10_TABLES) {
            val e = v10.getValue(t)
            assertEquals("$t の createSql", Migrations.splitV10CreateSql(t), createSql(e, t))
            assertEquals("$t のインデックス", Migrations.splitV10IndexSqls(t), indexSqls(e, t))
        }
    }

    // ---- v11(割り勘の参加者・ラベル金額。割当の参照先を参加者に変更)----

    private val v11 get() = entities(load(11))

    private val v11NewTables = Migrations.SPLIT_V11_NEW_TABLES
    private val v11RebuiltTables = Migrations.SPLIT_V11_REBUILT_TABLES

    @Test
    fun schema11_isVersion11() {
        assertEquals(11, load(11)["version"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun v11_tableSetIsV10PlusNewTables() {
        assertEquals(v10.keys + v11NewTables, v11.keys)
    }

    @Test
    fun v11_tablesOutsideTheChangesAreUnchangedFromV10() {
        // split_sheets は列を足すだけ、作り直す3表は下で確かめる。それ以外は v10 と同じ
        val changed = v11RebuiltTables.toSet() + "split_sheets"
        for (name in v10.keys - changed) {
            assertEquals("$name の createSql", createSql(v10.getValue(name), name), createSql(v11.getValue(name), name))
            assertEquals("$name のインデックス", indexSqls(v10.getValue(name), name), indexSqls(v11.getValue(name), name))
        }
    }

    @Test
    fun v11_newAndRebuiltSplitTables_createSqlAndIndicesMatchMigration() {
        for (t in v11NewTables + v11RebuiltTables) {
            val e = v11.getValue(t)
            assertEquals("$t の createSql", Migrations.splitV11CreateSql(t), createSql(e, t))
            assertEquals("$t のインデックス", Migrations.splitV11IndexSqls(t), indexSqls(e, t))
        }
    }

    @Test
    fun v11_rebuiltTables_referenceParticipantsNotPersons() {
        for (t in v11RebuiltTables) {
            val sql = Migrations.splitV11CreateSql(t)
            assertTrue("$t は participantId を参照する", sql.contains("REFERENCES `split_participants`(`id`)"))
            assertFalse("$t は persons を参照しない", sql.contains("`personId`"))
        }
    }

    @Test
    fun v11_splitSheets_hasAddedColumnsOnly() {
        val fields = v11.getValue("split_sheets")["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        assertEquals(listOf("receiptId", "mode", "wholeKind", "warnings"), fields)
        // ALTER TABLE で足す列定義が、CREATE TABLE の列定義(11.json)と同じ文字列になる
        val v10Sql = Migrations.splitV10CreateSql("split_sheets")
        val added = Migrations.SPLIT_SHEETS_V11_ADDED_COLUMNS.map { c ->
            assertEquals(
                "ALTER TABLE `split_sheets` ADD COLUMN `${c.name}` ${c.type} NOT NULL DEFAULT ${c.default}",
                Migrations.splitSheetsV11AddColumnSql(c),
            )
            "`${c.name}` ${c.type} NOT NULL DEFAULT ${c.default}"
        }
        assertEquals(
            v10Sql.replace(", PRIMARY KEY(", ", " + added.joinToString(", ") + ", PRIMARY KEY("),
            createSql(v11.getValue("split_sheets"), "split_sheets"),
        )
    }
}
