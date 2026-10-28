package com.amgm.personallog.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amgm.personallog.domain.PricedLine
import com.amgm.personallog.domain.SingleExpenseConversion
import com.amgm.personallog.domain.legacyReceiptOwnYen
import com.amgm.personallog.receipt.LegacyRow
import com.amgm.personallog.receipt.ReceiptUnits
import com.amgm.personallog.receipt.TotalSourceInit

/**
 * ルール(更新エラー/データ消失の防止):
 *  - AppDatabase の version を上げるたびに、ここへ Migration(旧, 新) を必ず追加し ALL に登録する。
 *  - fallbackToDestructiveMigration は絶対に使わない。
 *  - version を上げたら app/schemas/<version>.json もコミットする(mainのCIが自動コミットする)。
 *
 * v10(割り勘): 割り勘の7表(persons・split_sheets・share_groups・share_group_items・item_shares・whole_shares・payment_payers)を
 * 追加し、自分の人を1行作る(MIGRATION_9_10)。既存の表は変えない。
 *
 * v11(割り勘の参加者・ラベル金額): split_participants・split_label_amounts を追加し、split_sheets に wholeKind・warnings を足す。
 * item_shares・whole_shares・payment_payers は参照先を persons から参加者(split_participants)に変えるため作り直す(MIGRATION_10_11)。
 *
 * v12(支出は支払いから作る): 支払いに属さない支出グループ・単独の支出を、手入力の支払い(receipts)に変える(MIGRATION_11_12)。表の形は変えない。
 *
 * v13(参加人数): receipts に headcount を ALTER TABLE ADD COLUMN で足す(MIGRATION_12_13)。既存の行は null。
 *
 * v14(支出は必ず品目つき・金額不明): receipt_items と expenses に unknownAmount を足し、単独の支出(groupId なし)を
 * 自分の支出(expense_groups)+ 品名が空の1品目に変える(MIGRATION_13_14)。
 */
object Migrations {
    /**
     * v1 -> v2: receipts に原本画像のSHA-256(imageHash)を追加する。
     * Entity の Receipt.imageHash(String?, 既定値なし, index なし)と完全に一致させること。
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `receipts` ADD COLUMN `imageHash` TEXT")
        }
    }

    /**
     * v2 -> v3: 品目の税率・税込/税抜・負担割合と、レシートの立て替えフラグを追加する(実機で確認済み)。
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `receipts` ADD COLUMN `isAdvance` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `receipt_items` ADD COLUMN `taxRate` INTEGER NOT NULL DEFAULT 10")
            db.execSQL("ALTER TABLE `receipt_items` ADD COLUMN `priceIncludesTax` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `receipt_items` ADD COLUMN `ownPercent` INTEGER NOT NULL DEFAULT 100")
        }
    }

    /**
     * v3 -> v4: 品目を「1個=1行」にする。数量は行に展開し、負担割合(%)は金額(ownAmount)に換算する。
     * 列の削除を伴うため表を作り直す(SQLite の DROP COLUMN は端末の SQLite 版により使えないため)。
     * 作り直し後の定義は Entity(ReceiptItem)と一致させること。
     */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `receipt_items_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`receiptId` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`amount` INTEGER NOT NULL, " +
                    "`category` TEXT NOT NULL, " +
                    "`taxRate` INTEGER NOT NULL DEFAULT 10, " +
                    "`priceIncludesTax` INTEGER NOT NULL DEFAULT 1, " +
                    "`ownAmount` INTEGER, " +
                    "`memo` TEXT NOT NULL, " +
                    "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            val rows = ArrayList<Pair<Long, LegacyRow>>()
            db.query(
                "SELECT receiptId, name, quantity, amount, category, taxRate, priceIncludesTax, ownPercent " +
                    "FROM receipt_items ORDER BY id",
            ).use { c ->
                while (c.moveToNext()) {
                    rows.add(
                        c.getLong(0) to LegacyRow(
                            name = c.getString(1),
                            quantity = c.getDouble(2),
                            amount = c.getInt(3),
                            category = c.getString(4),
                            taxRate = c.getInt(5),
                            priceIncludesTax = c.getInt(6) != 0,
                            ownPercent = c.getInt(7),
                        ),
                    )
                }
            }
            for ((receiptId, legacy) in rows) {
                for (u in ReceiptUnits.fromLegacy(legacy)) {
                    db.execSQL(
                        "INSERT INTO `receipt_items_new` " +
                            "(`receiptId`, `name`, `amount`, `category`, `taxRate`, `priceIncludesTax`, `ownAmount`, `memo`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, '')",
                        arrayOf<Any?>(
                            receiptId,
                            u.name,
                            u.amount,
                            u.category,
                            u.taxRate,
                            if (u.priceIncludesTax) 1 else 0,
                            u.ownAmount,
                        ),
                    )
                }
            }
            db.execSQL("DROP TABLE `receipt_items`")
            db.execSQL("ALTER TABLE `receipt_items_new` RENAME TO `receipt_items`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipt_items_receiptId` ON `receipt_items` (`receiptId`)")
        }
    }

    /**
     * v4 -> v5: 割り勘をレシート全体で持つ。receipts に ownAmount(円)を追加し、
     * 品目ごとの自分の分(v4の receipt_items.ownAmount)からレシート全体の値を求めて入れる。
     * 品目の ownAmount 列は削除する(表を作り直す)。
     */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `receipts` ADD COLUMN `ownAmount` INTEGER")
            val lines = LinkedHashMap<Long, MutableList<PricedLine>>()
            db.query(
                "SELECT receiptId, amount, taxRate, priceIncludesTax, ownAmount FROM receipt_items ORDER BY id",
            ).use { c ->
                while (c.moveToNext()) {
                    lines.getOrPut(c.getLong(0)) { mutableListOf() }.add(
                        PricedLine(
                            amount = c.getInt(1),
                            taxRate = c.getInt(2),
                            priceIncludesTax = c.getInt(3) != 0,
                            ownAmount = if (c.isNull(4)) null else c.getInt(4),
                        ),
                    )
                }
            }
            for ((receiptId, rows) in lines) {
                val own = legacyReceiptOwnYen(rows) ?: continue
                db.execSQL("UPDATE `receipts` SET `ownAmount` = ? WHERE `id` = ?", arrayOf<Any?>(own, receiptId))
            }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `receipt_items_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`receiptId` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`amount` INTEGER NOT NULL, " +
                    "`category` TEXT NOT NULL, " +
                    "`taxRate` INTEGER NOT NULL DEFAULT 10, " +
                    "`priceIncludesTax` INTEGER NOT NULL DEFAULT 1, " +
                    "`memo` TEXT NOT NULL, " +
                    "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "INSERT INTO `receipt_items_new` (`id`, `receiptId`, `name`, `amount`, `category`, `taxRate`, `priceIncludesTax`, `memo`) " +
                    "SELECT `id`, `receiptId`, `name`, `amount`, `category`, `taxRate`, `priceIncludesTax`, `memo` FROM `receipt_items`",
            )
            db.execSQL("DROP TABLE `receipt_items`")
            db.execSQL("ALTER TABLE `receipt_items_new` RENAME TO `receipt_items`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipt_items_receiptId` ON `receipt_items` (`receiptId`)")
        }
    }

    /**
     * v5 -> v6: 支払いグループ(expense_groups)を追加し、expenses に groupId を足す。
     * 既存の支出は groupId=NULL(単独の支出)のまま。expenses は外部キーを増やすため表を作り直す。
     */
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `expense_groups` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`date` TEXT NOT NULL, " +
                    "`amount` INTEGER NOT NULL, " +
                    "`category` TEXT NOT NULL, " +
                    "`memo` TEXT NOT NULL, " +
                    "`receiptId` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_groups_receiptId` ON `expense_groups` (`receiptId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_groups_date` ON `expense_groups` (`date`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `expenses_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`date` TEXT NOT NULL, " +
                    "`amount` INTEGER NOT NULL, " +
                    "`category` TEXT NOT NULL, " +
                    "`memo` TEXT NOT NULL, " +
                    "`receiptId` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`groupId` INTEGER, " +
                    "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                    "FOREIGN KEY(`groupId`) REFERENCES `expense_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "INSERT INTO `expenses_new` (`id`, `date`, `amount`, `category`, `memo`, `receiptId`, `createdAt`) " +
                    "SELECT `id`, `date`, `amount`, `category`, `memo`, `receiptId`, `createdAt` FROM `expenses`",
            )
            db.execSQL("DROP TABLE `expenses`")
            db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_receiptId` ON `expenses` (`receiptId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_groupId` ON `expenses` (`groupId`)")
        }
    }

    /**
     * v6 -> v7: 支出と支払いグループに時刻 time("HH:mm")を足す。支出には timeEdited(日時を支出側で変えたか)も足す。
     * 既存行の時刻は、レシート紐づきならレシートの時刻(レシートに時刻が無ければ空のまま。00:00 は入れない)、それ以外は作成時刻の現地時刻で埋める。
     */
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `expenses` ADD COLUMN `time` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `expenses` ADD COLUMN `timeEdited` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `time` TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "UPDATE `expenses` SET `time` = COALESCE(" +
                    "(SELECT CASE WHEN length(r.purchasedAt) >= 16 THEN substr(r.purchasedAt, 12, 5) ELSE '' END " +
                    "FROM `receipts` r WHERE r.id = `expenses`.`receiptId`), " +
                    "strftime('%H:%M', `createdAt` / 1000, 'unixepoch', 'localtime'))",
            )
            db.execSQL(
                "UPDATE `expense_groups` SET `time` = COALESCE(" +
                    "(SELECT CASE WHEN length(r.purchasedAt) >= 16 THEN substr(r.purchasedAt, 12, 5) ELSE '' END " +
                    "FROM `receipts` r WHERE r.id = `expense_groups`.`receiptId`), " +
                    "strftime('%H:%M', `createdAt` / 1000, 'unixepoch', 'localtime'))",
            )
        }
    }

    /**
     * v8 の receipts の CREATE TABLE。Room が生成する v8 の createSql(`${TABLE_NAME}` を表名にしたもの)と
     * 完全に一致させること(SchemaMatchTest が検査する)。一致しないと端末でアプリが起動時に落ちる。
     */
    fun receiptsV8CreateSql(table: String): String =
        "CREATE TABLE IF NOT EXISTS `$table` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`purchasedAt` TEXT NOT NULL, " +
            "`storeName` TEXT NOT NULL, " +
            "`totalAmount` INTEGER, " +
            "`imagePath` TEXT NOT NULL, " +
            "`rawJson` TEXT NOT NULL, " +
            "`status` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`imageHash` TEXT, " +
            "`isAdvance` INTEGER NOT NULL DEFAULT 0, " +
            "`ownAmount` INTEGER, " +
            "`totalSource` TEXT NOT NULL DEFAULT 'unknown', " +
            "`rotationDegrees` REAL NOT NULL DEFAULT 0, " +
            "`cropLeft` REAL NOT NULL DEFAULT 0, " +
            "`cropTop` REAL NOT NULL DEFAULT 0, " +
            "`cropRight` REAL NOT NULL DEFAULT 1, " +
            "`cropBottom` REAL NOT NULL DEFAULT 1)"

    /** v8 の receipt_not_duplicates の CREATE TABLE(v8 の createSql と一致させる)。 */
    fun notDuplicatesV8CreateSql(table: String): String =
        "CREATE TABLE IF NOT EXISTS `$table` (" +
            "`receiptIdLow` INTEGER NOT NULL, " +
            "`receiptIdHigh` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`receiptIdLow`, `receiptIdHigh`), " +
            "FOREIGN KEY(`receiptIdLow`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`receiptIdHigh`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    /** v8 の receipt_not_duplicates のインデックス(v8 のテスト用資料 schemas-fixture/8.json の indices と一致させる)。 */
    fun notDuplicatesV8IndexSql(table: String): String =
        "CREATE INDEX IF NOT EXISTS `index_receipt_not_duplicates_receiptIdHigh` ON `$table` (`receiptIdHigh`)"

    /** receipts から v8 の receipts へコピーする列(新しい列は既定値に任せる)。 */
    private const val RECEIPTS_V7_COLUMNS =
        "`id`, `purchasedAt`, `storeName`, `totalAmount`, `imagePath`, `rawJson`, `status`, `createdAt`, `imageHash`, `isAdvance`, `ownAmount`"

    /** 親の表を作り直す前後で数える対象。減ったら外部キーの連鎖で子が消えたということ。 */
    internal val V8_CHILD_COUNT_QUERIES: List<Pair<String, String>> = listOf(
        "receipts" to "SELECT COUNT(*) FROM `receipts`",
        "receipt_items" to "SELECT COUNT(*) FROM `receipt_items`",
        "expenses(receiptIdあり)" to "SELECT COUNT(*) FROM `expenses` WHERE `receiptId` IS NOT NULL",
        "expense_groups(receiptIdあり)" to "SELECT COUNT(*) FROM `expense_groups` WHERE `receiptId` IS NOT NULL",
    )

    /** receipts を親とする外部キーを持つ子の表。 */
    private val V8_CHILD_TABLES = listOf("receipt_items", "expenses", "expense_groups")

    /**
     * 子の表の外部キー違反のうち、親が receipts のものの件数。
     * DB 全体への PRAGMA foreign_key_check は移行と無関係な古い不整合も拾って起動のたびに落ちるので、
     * receipts に関係するものだけを数える(移行の前後で増えたときだけ異常とする)。
     */
    internal fun SupportSQLiteDatabase.receiptsFkViolations(): Long {
        var n = 0L
        for (t in V8_CHILD_TABLES) {
            query("PRAGMA foreign_key_check(`$t`)").use { c ->
                val parentIdx = c.getColumnIndex("parent").takeIf { it >= 0 } ?: 2
                while (c.moveToNext()) if (c.getString(parentIdx) == "receipts") n++
            }
        }
        return n
    }

    /** expenses 自身の外部キー違反の件数(移行の前後で比べる。増えたときだけ異常とする)。 */
    internal fun SupportSQLiteDatabase.expensesFkViolations(): Long {
        var n = 0L
        query("PRAGMA foreign_key_check(`expenses`)").use { c -> while (c.moveToNext()) n++ }
        return n
    }

    private fun SupportSQLiteDatabase.count(sql: String): Long =
        query(sql).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    /**
     * v7 -> v8: 合計の必須化と重複判定のための変更。
     *  - receipts を作り直す(totalAmount の NOT NULL を外す。SQLite では制約だけ外せない)。同時に
     *    totalSource と、回転・トリミングの列(次の作業で使う。今回は列だけ)を足す。
     *  - 既存の totalSource は rawJson から決める(TotalSourceInit)。読み取れなかった下書きの合計は空欄(NULL)に戻す。
     *  - receipt_not_duplicates(「重複ではない」の組)を追加する。
     *
     * 安全策: receipts は receipt_items(CASCADE)・expenses・expense_groups(SET NULL)の親。
     * Room は onUpgrade では外部キー検査を有効にしていない想定だが、もし有効なら DROP TABLE が子を消す。
     * そのため作り直しの前後で子の件数を数え、1つでも違えば例外にする(トランザクションが戻り、データは守られる)。
     * 最後に receipts を親とする外部キー違反(PRAGMA foreign_key_check(子の表))が増えていないかも確かめる。
     * 移行の最初に PRAGMA foreign_keys が 1 なら、DROP の前に止める。
     */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 外部キーが有効なままだと DROP TABLE receipts が子(品目など)を連鎖で消す。PRAGMA はトランザクション内では
            // 変えられないので、ここで止める(トランザクションが戻り、データは守られる)。
            val fkOn = db.query("PRAGMA foreign_keys").use { c -> c.moveToFirst() && c.getInt(0) == 1 }
            if (fkOn) {
                throw IllegalStateException(
                    "v8 への移行を中止しました: 外部キー(PRAGMA foreign_keys)が有効です。" +
                        "有効なまま receipts を作り直すと、品目などの子のデータが連鎖で削除されるため、何も変更せずに止めました。",
                )
            }
            val fkBefore = db.receiptsFkViolations()
            val before = V8_CHILD_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }
            val oldSeq = db.query("SELECT `seq` FROM `sqlite_sequence` WHERE `name` = 'receipts'")
                .use { c -> if (c.moveToFirst()) c.getLong(0) else null }

            db.execSQL("DROP TABLE IF EXISTS `receipts_new`")
            db.execSQL(receiptsV8CreateSql("receipts_new"))
            db.execSQL(
                "INSERT INTO `receipts_new` ($RECEIPTS_V7_COLUMNS) SELECT $RECEIPTS_V7_COLUMNS FROM `receipts`",
            )

            // 合計の出所を rawJson から決める(純Kotlinの TotalSourceInit)
            class Row(val id: Long, val total: Int, val status: String, val rawJson: String)
            val rows = ArrayList<Row>()
            db.query("SELECT `id`, `totalAmount`, `status`, `rawJson` FROM `receipts_new` ORDER BY `id`").use { c ->
                while (c.moveToNext()) {
                    rows.add(Row(c.getLong(0), if (c.isNull(1)) 0 else c.getInt(1), c.getString(2), c.getString(3)))
                }
            }
            for (r in rows) {
                val d = TotalSourceInit.decide(r.status, r.total, r.rawJson)
                db.execSQL(
                    "UPDATE `receipts_new` SET `totalAmount` = ?, `totalSource` = ? WHERE `id` = ?",
                    arrayOf<Any?>(d.totalAmount, d.totalSource, r.id),
                )
            }

            db.execSQL("DROP TABLE `receipts`")
            db.execSQL("ALTER TABLE `receipts_new` RENAME TO `receipts`")
            // AUTOINCREMENT の採番を引き継ぐ。receipts が空だと新しい表の sqlite_sequence に行が無く UPDATE が効かないので、
            // 行を消して(古い採番, 最大 id)の大きい方で入れ直す
            val maxId = db.count("SELECT COALESCE(MAX(`id`), 0) FROM `receipts`")
            val seq = maxOf(oldSeq ?: 0L, maxId)
            db.execSQL("DELETE FROM `sqlite_sequence` WHERE `name` = 'receipts'")
            if (seq > 0L) {
                db.execSQL("INSERT INTO `sqlite_sequence` (`name`, `seq`) VALUES ('receipts', ?)", arrayOf<Any?>(seq))
            }

            db.execSQL(notDuplicatesV8CreateSql("receipt_not_duplicates"))
            db.execSQL(notDuplicatesV8IndexSql("receipt_not_duplicates"))

            val after = V8_CHILD_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }
            if (before != after) {
                throw IllegalStateException("v8 への移行を中止しました: 件数が変わりました $before -> $after")
            }
            val fkAfter = db.receiptsFkViolations()
            if (fkAfter > fkBefore) {
                throw IllegalStateException("v8 への移行を中止しました: receipts との外部キーの違反が増えました $fkBefore -> $fkAfter")
            }
        }
    }

    /**
     * v9 の expenses の CREATE TABLE。Room が生成する 9.json の createSql(`${TABLE_NAME}` を表名にしたもの)と
     * 完全に一致させること(SchemaMatchTest が検査する)。
     */
    fun expensesV9CreateSql(table: String): String =
        "CREATE TABLE IF NOT EXISTS `$table` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`date` TEXT NOT NULL, " +
            "`time` TEXT NOT NULL DEFAULT '', " +
            "`amount` INTEGER NOT NULL, " +
            "`category` TEXT NOT NULL, " +
            "`memo` TEXT NOT NULL, " +
            "`receiptId` INTEGER, " +
            "`createdAt` INTEGER NOT NULL, " +
            "`groupId` INTEGER, " +
            "`timeEdited` INTEGER NOT NULL DEFAULT 0, " +
            "`receiptItemId` INTEGER, " +
            "`name` TEXT NOT NULL DEFAULT '', " +
            "`amountEdited` INTEGER NOT NULL DEFAULT 0, " +
            "`categoryEdited` INTEGER NOT NULL DEFAULT 0, " +
            "`nameEdited` INTEGER NOT NULL DEFAULT 0, " +
            "`memoEdited` INTEGER NOT NULL DEFAULT 0, " +
            "`uid` TEXT NOT NULL, " +
            "`deletedByUser` INTEGER NOT NULL DEFAULT 0, " +
            "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
            "FOREIGN KEY(`groupId`) REFERENCES `expense_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`receiptItemId`) REFERENCES `receipt_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"

    /** v9 の expenses のインデックス(9.json の indices[].createSql と一致させる)。宣言順。 */
    fun expensesV9IndexSqls(table: String): List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS `index_expenses_receiptId` ON `$table` (`receiptId`)",
        "CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `$table` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_expenses_groupId` ON `$table` (`groupId`)",
        "CREATE INDEX IF NOT EXISTS `index_expenses_receiptItemId` ON `$table` (`receiptItemId`)",
    )

    /** v8 の expenses の列(v9 の表へコピーする)。 */
    private const val EXPENSES_V8_COLUMNS =
        "`id`, `date`, `time`, `amount`, `category`, `memo`, `receiptId`, `createdAt`, `groupId`, `timeEdited`"

    /** v9 で expense_groups に ALTER TABLE ADD COLUMN で足す列(列名, 型, 既定値)。すべて NOT NULL。 */
    data class AddedColumn(val name: String, val type: String, val default: String)

    val GROUPS_V9_ADDED_COLUMNS: List<AddedColumn> = listOf(
        AddedColumn("uid", "TEXT", "''"),
        AddedColumn("amountEdited", "INTEGER", "0"),
        AddedColumn("categoryEdited", "INTEGER", "0"),
        AddedColumn("memoEdited", "INTEGER", "0"),
        AddedColumn("timeEdited", "INTEGER", "0"),
    )

    fun groupsV9AddColumnSql(c: AddedColumn): String =
        "ALTER TABLE `expense_groups` ADD COLUMN `${c.name}` ${c.type} NOT NULL DEFAULT ${c.default}"

    /** 作り直しの前後で件数を比べる対象(expenses の件数と、expense_groups の件数)。 */
    internal val V9_COUNT_QUERIES: List<Pair<String, String>> = listOf(
        "expenses" to "SELECT COUNT(*) FROM `expenses`",
        "expense_groups" to "SELECT COUNT(*) FROM `expense_groups`",
        "receipt_items" to "SELECT COUNT(*) FROM `receipt_items`",
    )

    /**
     * v8 -> v9: 支払いの品目化と「編集済み」の記録。
     *  - expenses を作り直す(receiptItemId の外部キーを足すため)。expenses は子の表なので DROP しても他を壊さない。
     *    既存行の receiptItemId は NULL(対応付けできない)。uid は乱数で埋める。deletedByUser(家計簿で消した品目の印)は 0。
     *  - expense_groups は親(expenses が参照する)ので作り直さず、ALTER TABLE ADD COLUMN で列を足す。
     *    uid は DEFAULT '' で足してから UPDATE で乱数にする。
     * 安全策(7_8 と同じ):
     *  - 最初に PRAGMA foreign_keys を読み、1 なら何も変えずに止める。
     *  - 作り直す expenses に関わる外部キー違反だけを、移行の前後で数え、増えたときだけ止める
     *    (DB 全体の foreign_key_check は移行と無関係な古い不整合も拾うため使わない)。
     *  - 作り直しの前後で expenses・expense_groups・receipt_items の件数を数え、違えば例外にする。
     */
    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val fkOn = db.query("PRAGMA foreign_keys").use { c -> c.moveToFirst() && c.getInt(0) == 1 }
            if (fkOn) {
                throw IllegalStateException(
                    "v9 への移行を中止しました: 外部キー(PRAGMA foreign_keys)が有効です。" +
                        "有効なまま表を作り直すと、子のデータが連鎖で変わるおそれがあるため、何も変更せずに止めました。",
                )
            }
            val fkBefore = db.expensesFkViolations()
            val before = V9_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }
            val oldSeq = db.query("SELECT `seq` FROM `sqlite_sequence` WHERE `name` = 'expenses'")
                .use { c -> if (c.moveToFirst()) c.getLong(0) else null }

            // 親の表: 列を足すだけ
            for (c in GROUPS_V9_ADDED_COLUMNS) db.execSQL(groupsV9AddColumnSql(c))
            db.execSQL("UPDATE `expense_groups` SET `uid` = lower(hex(randomblob(16)))")

            // 子の表: 作り直し
            db.execSQL("DROP TABLE IF EXISTS `expenses_new`")
            db.execSQL(expensesV9CreateSql("expenses_new"))
            db.execSQL(
                "INSERT INTO `expenses_new` ($EXPENSES_V8_COLUMNS, `uid`) " +
                    "SELECT $EXPENSES_V8_COLUMNS, lower(hex(randomblob(16))) FROM `expenses`",
            )
            db.execSQL("DROP TABLE `expenses`")
            db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")
            // AUTOINCREMENT の採番を引き継ぐ(7_8 と同じ)。expenses が空だと新しい表の sqlite_sequence に行が無く UPDATE が効かないので、
            // 行を消して(古い採番, 最大 id)の大きい方で入れ直す
            val maxId = db.count("SELECT COALESCE(MAX(`id`), 0) FROM `expenses`")
            val seq = maxOf(oldSeq ?: 0L, maxId)
            db.execSQL("DELETE FROM `sqlite_sequence` WHERE `name` = 'expenses'")
            if (seq > 0L) {
                db.execSQL("INSERT INTO `sqlite_sequence` (`name`, `seq`) VALUES ('expenses', ?)", arrayOf<Any?>(seq))
            }
            for (sql in expensesV9IndexSqls("expenses")) db.execSQL(sql)

            val after = V9_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }
            if (before != after) {
                throw IllegalStateException("v9 への移行を中止しました: 件数が変わりました $before -> $after")
            }
            val fkAfter = db.expensesFkViolations()
            if (fkAfter > fkBefore) {
                throw IllegalStateException("v9 への移行を中止しました: expenses の外部キーの違反が増えました $fkBefore -> $fkAfter")
            }
        }
    }

    /** v10 で新しく作る割り勘の7表(宣言順)。既存の表は変えない。 */
    val SPLIT_V10_TABLES: List<String> = listOf(
        "persons", "split_sheets", "share_groups", "share_group_items", "item_shares", "whole_shares", "payment_payers",
    )

    /**
     * v10 の割り勘の表の CREATE TABLE(表名は引数。Room が生成する 10.json の createSql と完全に一致させること。
     * SchemaMatchTest が検査する)。FOREIGN KEY の区切りと末尾の空白は Room の出力に合わせている。
     */
    fun splitV10CreateSql(table: String): String = when (table) {
        "persons" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uid` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`shortName` TEXT NOT NULL DEFAULT '', " +
                "`isSelf` INTEGER NOT NULL DEFAULT 0, " +
                "`sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                "`createdAt` INTEGER NOT NULL)"
        "split_sheets" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`receiptId` INTEGER NOT NULL, " +
                "`mode` TEXT NOT NULL DEFAULT 'self', " +
                "PRIMARY KEY(`receiptId`), " +
                "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        "share_groups" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`receiptId` INTEGER NOT NULL, " +
                "`pieceCount` INTEGER, " +
                "`isShared` INTEGER NOT NULL DEFAULT 0, " +
                "`sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        "share_group_items" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`groupId` INTEGER NOT NULL, " +
                "`receiptItemId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`groupId`, `receiptItemId`), " +
                "FOREIGN KEY(`groupId`) REFERENCES `share_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`receiptItemId`) REFERENCES `receipt_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        "item_shares" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`groupId` INTEGER NOT NULL, " +
                "`personId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`weight` INTEGER, " +
                "`amount` INTEGER, " +
                "PRIMARY KEY(`groupId`, `personId`), " +
                "FOREIGN KEY(`groupId`) REFERENCES `share_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        "whole_shares" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`receiptId` INTEGER NOT NULL, " +
                "`personId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`weight` INTEGER, " +
                "`amount` INTEGER, " +
                "PRIMARY KEY(`receiptId`, `personId`), " +
                "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        "payment_payers" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`receiptId` INTEGER NOT NULL, " +
                "`personId` INTEGER NOT NULL, " +
                "`method` TEXT NOT NULL DEFAULT '', " +
                "`amount` INTEGER NOT NULL, " +
                "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        else -> throw IllegalArgumentException("v10 の割り勘の表ではありません: $table")
    }

    /** v10 の割り勘の表のインデックス(10.json の indices[].createSql と一致させる)。宣言順。 */
    fun splitV10IndexSqls(table: String): List<String> = when (table) {
        "persons" -> listOf("CREATE UNIQUE INDEX IF NOT EXISTS `index_persons_uid` ON `$table` (`uid`)")
        "split_sheets" -> emptyList()
        "share_groups" -> listOf("CREATE INDEX IF NOT EXISTS `index_share_groups_receiptId` ON `$table` (`receiptId`)")
        "share_group_items" ->
            listOf("CREATE UNIQUE INDEX IF NOT EXISTS `index_share_group_items_receiptItemId` ON `$table` (`receiptItemId`)")
        "item_shares" -> listOf("CREATE INDEX IF NOT EXISTS `index_item_shares_personId` ON `$table` (`personId`)")
        "whole_shares" -> listOf("CREATE INDEX IF NOT EXISTS `index_whole_shares_personId` ON `$table` (`personId`)")
        "payment_payers" -> listOf(
            "CREATE INDEX IF NOT EXISTS `index_payment_payers_receiptId` ON `$table` (`receiptId`)",
            "CREATE INDEX IF NOT EXISTS `index_payment_payers_personId` ON `$table` (`personId`)",
        )
        else -> throw IllegalArgumentException("v10 の割り勘の表ではありません: $table")
    }

    /**
     * v9 -> v10: 割り勘の7表を追加し、自分の人を1行作る。既存の表には触らない(作り直さない)。
     *  - 7表を CREATE TABLE(と各インデックス)で作る。既存の表は一切変えない。
     *  - persons に isSelf = 1 の行が無いときだけ「自分」を入れる(2回目の実行でも増えない)。uid は乱数。
     * 安全策: 移行の前後で既存の表の件数を数え、1つでも変わったら例外にする(トランザクションが戻り、データは守られる)。
     */
    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val before = V8_CHILD_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }

            for (t in SPLIT_V10_TABLES) {
                db.execSQL(splitV10CreateSql(t))
                for (sql in splitV10IndexSqls(t)) db.execSQL(sql)
            }
            db.execSQL(
                "INSERT INTO `persons` (`uid`, `name`, `shortName`, `isSelf`, `sortOrder`, `createdAt`) " +
                    "SELECT lower(hex(randomblob(16))), '自分', '自', 1, 0, CAST(strftime('%s','now') AS INTEGER) * 1000 " +
                    "WHERE NOT EXISTS (SELECT 1 FROM `persons` WHERE `isSelf` = 1)",
            )

            val after = V8_CHILD_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }
            if (before != after) {
                throw IllegalStateException("v10 への移行を中止しました: 件数が変わりました $before -> $after")
            }
        }
    }

    /** v11 で新しく作る割り勘の表(宣言順)。 */
    val SPLIT_V11_NEW_TABLES: List<String> = listOf("split_participants", "split_label_amounts")

    /** v11 で作り直す表(割当・支払内訳の参照先を persons から split_participants に変える)。宣言順。 */
    val SPLIT_V11_REBUILT_TABLES: List<String> = listOf("item_shares", "whole_shares", "payment_payers")

    /** v11 で split_sheets に ALTER TABLE ADD COLUMN で足す列。 */
    val SPLIT_SHEETS_V11_ADDED_COLUMNS: List<AddedColumn> = listOf(
        AddedColumn("wholeKind", "TEXT", "'amount'"),
        AddedColumn("warnings", "TEXT", "''"),
    )

    fun splitSheetsV11AddColumnSql(c: AddedColumn): String =
        "ALTER TABLE `split_sheets` ADD COLUMN `${c.name}` ${c.type} NOT NULL DEFAULT ${c.default}"

    /**
     * v11 の割り勘の表の CREATE TABLE(表名は引数。Room が生成する 11.json の createSql と完全に一致させること。
     * SchemaMatchTest が検査する)。FOREIGN KEY の区切りと末尾の空白は Room の出力に合わせている。
     */
    fun splitV11CreateSql(table: String): String = when (table) {
        "split_participants" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`receiptId` INTEGER NOT NULL, " +
                "`personId` INTEGER, " +
                "`memo` TEXT NOT NULL DEFAULT '', " +
                "`label` TEXT NOT NULL DEFAULT '', " +
                "`isPayer` INTEGER NOT NULL DEFAULT 0, " +
                "`sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        "split_label_amounts" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`receiptId` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "PRIMARY KEY(`receiptId`, `label`), " +
                "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        "item_shares" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`groupId` INTEGER NOT NULL, " +
                "`participantId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`weight` INTEGER, " +
                "`amount` INTEGER, " +
                "PRIMARY KEY(`groupId`, `participantId`), " +
                "FOREIGN KEY(`groupId`) REFERENCES `share_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`participantId`) REFERENCES `split_participants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        "whole_shares" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`receiptId` INTEGER NOT NULL, " +
                "`participantId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`weight` INTEGER, " +
                "`amount` INTEGER, " +
                "PRIMARY KEY(`receiptId`, `participantId`), " +
                "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`participantId`) REFERENCES `split_participants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        "payment_payers" ->
            "CREATE TABLE IF NOT EXISTS `$table` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`receiptId` INTEGER NOT NULL, " +
                "`participantId` INTEGER NOT NULL, " +
                "`method` TEXT NOT NULL DEFAULT '', " +
                "`amount` INTEGER NOT NULL, " +
                "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`participantId`) REFERENCES `split_participants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        else -> throw IllegalArgumentException("v11 の割り勘の表ではありません: $table")
    }

    /** v11 の割り勘の表のインデックス(11.json の indices[].createSql と一致させる)。宣言順。 */
    fun splitV11IndexSqls(table: String): List<String> = when (table) {
        "split_participants" -> listOf(
            "CREATE INDEX IF NOT EXISTS `index_split_participants_receiptId` ON `$table` (`receiptId`)",
            "CREATE INDEX IF NOT EXISTS `index_split_participants_personId` ON `$table` (`personId`)",
        )
        "split_label_amounts" -> emptyList()
        "item_shares" -> listOf("CREATE INDEX IF NOT EXISTS `index_item_shares_participantId` ON `$table` (`participantId`)")
        "whole_shares" -> listOf("CREATE INDEX IF NOT EXISTS `index_whole_shares_participantId` ON `$table` (`participantId`)")
        "payment_payers" -> listOf(
            "CREATE INDEX IF NOT EXISTS `index_payment_payers_receiptId` ON `$table` (`receiptId`)",
            "CREATE INDEX IF NOT EXISTS `index_payment_payers_participantId` ON `$table` (`participantId`)",
        )
        else -> throw IllegalArgumentException("v11 の割り勘の表ではありません: $table")
    }

    /** v11 の移行の前後で件数を比べる対象(v8 の子表と、割り勘の表)。 */
    internal val V11_COUNT_QUERIES: List<Pair<String, String>> = V8_CHILD_COUNT_QUERIES + listOf(
        "split_sheets" to "SELECT COUNT(*) FROM `split_sheets`",
        "share_groups" to "SELECT COUNT(*) FROM `share_groups`",
        "item_shares" to "SELECT COUNT(*) FROM `item_shares`",
        "whole_shares" to "SELECT COUNT(*) FROM `whole_shares`",
        "payment_payers" to "SELECT COUNT(*) FROM `payment_payers`",
    )

    /** 作り直す割り勘の表の外部キー違反の件数(PRAGMA foreign_key_check を表ごとに数える。移行と無関係な古い不整合は含めるが、移行の前後で比べる)。 */
    internal fun SupportSQLiteDatabase.splitFkViolations(tables: List<String>): Long {
        var n = 0L
        for (t in tables) query("PRAGMA foreign_key_check(`$t`)").use { c -> while (c.moveToNext()) n++ }
        return n
    }

    private fun android.database.Cursor.nullableInt(index: Int): Int? = if (isNull(index)) null else getInt(index)

    /**
     * v10 の割当・支払内訳(personId)を、v11 の参加者(participantId)へ写す。_old の表から読み、新しい表へ書く。
     *  - (receiptId, personId) ごとに参加者を1人作る。sortOrder は出現順で、自分の人は先頭。
     *  - 支払内訳(payment_payers)に出てくる人は isPayer = 1 にする(店に払った人)。
     *  - item_shares の receiptId は share_groups から引く。share_groups が無い割当は写せないので、件数の検査で止まる。
     * 写す前に読み出しきってから書く(読みながら書かない)。
     */
    private fun SupportSQLiteDatabase.copyLegacySplitShares() {
        class ItemRow(val groupId: Long, val receiptId: Long, val personId: Long, val kind: String, val weight: Int?, val amount: Int?)
        class WholeRow(val receiptId: Long, val personId: Long, val kind: String, val weight: Int?, val amount: Int?)
        class PayerRow(val id: Long, val receiptId: Long, val personId: Long, val method: String, val amount: Int)

        val items = ArrayList<ItemRow>()
        query(
            "SELECT `item_shares_old`.`groupId`, `share_groups`.`receiptId`, `item_shares_old`.`personId`, " +
                "`item_shares_old`.`kind`, `item_shares_old`.`weight`, `item_shares_old`.`amount` " +
                "FROM `item_shares_old` INNER JOIN `share_groups` ON `share_groups`.`id` = `item_shares_old`.`groupId` " +
                "ORDER BY `item_shares_old`.`rowid`",
        ).use { c ->
            while (c.moveToNext()) {
                items.add(ItemRow(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3), c.nullableInt(4), c.nullableInt(5)))
            }
        }
        val wholes = ArrayList<WholeRow>()
        query("SELECT `receiptId`, `personId`, `kind`, `weight`, `amount` FROM `whole_shares_old` ORDER BY `rowid`").use { c ->
            while (c.moveToNext()) wholes.add(WholeRow(c.getLong(0), c.getLong(1), c.getString(2), c.nullableInt(3), c.nullableInt(4)))
        }
        val payers = ArrayList<PayerRow>()
        query("SELECT `id`, `receiptId`, `personId`, `method`, `amount` FROM `payment_payers_old` ORDER BY `id`").use { c ->
            while (c.moveToNext()) payers.add(PayerRow(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3), c.getInt(4)))
        }

        // 人の並び: 出現順(割当 → 全体の割当 → 支払内訳)。自分の人を先頭にする(sortedBy は安定)
        val selfIds = HashSet<Long>()
        query("SELECT `id` FROM `persons` WHERE `isSelf` = 1").use { c -> while (c.moveToNext()) selfIds.add(c.getLong(0)) }
        val appearance = LinkedHashMap<Long, LinkedHashSet<Long>>()
        for (r in items) appearance.getOrPut(r.receiptId) { LinkedHashSet() }.add(r.personId)
        for (r in wholes) appearance.getOrPut(r.receiptId) { LinkedHashSet() }.add(r.personId)
        for (r in payers) appearance.getOrPut(r.receiptId) { LinkedHashSet() }.add(r.personId)
        val payerKeys = payers.map { it.receiptId to it.personId }.toHashSet()

        val participantOf = HashMap<Pair<Long, Long>, Long>()
        for ((receiptId, personSet) in appearance) {
            personSet.sortedBy { if (it in selfIds) 0 else 1 }.forEachIndexed { index, personId ->
                val isPayer = (receiptId to personId) in payerKeys
                execSQL(
                    "INSERT INTO `split_participants` (`receiptId`, `personId`, `memo`, `label`, `isPayer`, `sortOrder`) " +
                        "VALUES (?, ?, '', '', ?, ?)",
                    arrayOf<Any?>(receiptId, personId, if (isPayer) 1 else 0, index),
                )
                participantOf[receiptId to personId] = query("SELECT last_insert_rowid()").use { c ->
                    c.moveToFirst()
                    c.getLong(0)
                }
            }
        }

        for (r in items) {
            execSQL(
                "INSERT INTO `item_shares` (`groupId`, `participantId`, `kind`, `weight`, `amount`) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(r.groupId, participantOf.getValue(r.receiptId to r.personId), r.kind, r.weight, r.amount),
            )
        }
        for (r in wholes) {
            execSQL(
                "INSERT INTO `whole_shares` (`receiptId`, `participantId`, `kind`, `weight`, `amount`) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(r.receiptId, participantOf.getValue(r.receiptId to r.personId), r.kind, r.weight, r.amount),
            )
        }
        for (r in payers) {
            execSQL(
                "INSERT INTO `payment_payers` (`id`, `receiptId`, `participantId`, `method`, `amount`) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(r.id, r.receiptId, participantOf.getValue(r.receiptId to r.personId), r.method, r.amount),
            )
        }
    }

    /**
     * v10 -> v11: 割り勘の参加者とラベル金額を追加し、割当・支払内訳の参照先を persons から参加者に変える。
     *  - split_sheets に wholeKind・warnings を ALTER TABLE ADD COLUMN で足す(既存の行は既定値)。
     *  - split_participants・split_label_amounts を CREATE する(インデックスも)。
     *  - item_shares・whole_shares・payment_payers は作り直す: 旧表を _old に RENAME → 新しい表を CREATE → 旧データを写す
     *    (copyLegacySplitShares)→ _old を DROP → インデックス。
     * 安全策(8_9 と同じ): 最初に PRAGMA foreign_keys を読み、1 なら何も変えずに止める。
     * 作り直す3表の外部キー違反の数を移行の前後で比べ、増えたら止める。件数(レシート・品目・割り勘の表)も前後で比べ、変わったら止める。
     */
    val MIGRATION_10_11: Migration = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val fkOn = db.query("PRAGMA foreign_keys").use { c -> c.moveToFirst() && c.getInt(0) == 1 }
            if (fkOn) {
                throw IllegalStateException(
                    "v11 への移行を中止しました: 外部キー(PRAGMA foreign_keys)が有効です。" +
                        "有効なまま表を作り直すと、子のデータが連鎖で変わるおそれがあるため、何も変更せずに止めました。",
                )
            }
            val fkBefore = db.splitFkViolations(SPLIT_V11_REBUILT_TABLES)
            val before = V11_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }
            // RENAME すると採番の行も _old 側へ移るので、移す前の値を読んでおく
            val oldPayerSeq = db.query("SELECT `seq` FROM `sqlite_sequence` WHERE `name` = 'payment_payers'")
                .use { c -> if (c.moveToFirst()) c.getLong(0) else null }

            // 親の表と新しい表
            for (c in SPLIT_SHEETS_V11_ADDED_COLUMNS) db.execSQL(splitSheetsV11AddColumnSql(c))
            for (t in SPLIT_V11_NEW_TABLES) {
                db.execSQL(splitV11CreateSql(t))
                for (sql in splitV11IndexSqls(t)) db.execSQL(sql)
            }

            // 作り直す3表: 旧表を退避 → 新しい表 → 旧データを写す
            for (t in SPLIT_V11_REBUILT_TABLES) db.execSQL("ALTER TABLE `$t` RENAME TO `${t}_old`")
            for (t in SPLIT_V11_REBUILT_TABLES) db.execSQL(splitV11CreateSql(t))
            db.copyLegacySplitShares()

            // payment_payers の採番を引き継ぐ(8_9 と同じ。id を写したあと、古い採番と最大 id の大きい方で入れ直す)
            val maxPayerId = db.count("SELECT COALESCE(MAX(`id`), 0) FROM `payment_payers`")
            val seq = maxOf(oldPayerSeq ?: 0L, maxPayerId)
            db.execSQL("DELETE FROM `sqlite_sequence` WHERE `name` = 'payment_payers'")
            if (seq > 0L) {
                db.execSQL("INSERT INTO `sqlite_sequence` (`name`, `seq`) VALUES ('payment_payers', ?)", arrayOf<Any?>(seq))
            }

            // 旧表を消してから、新しい表のインデックスを作る(旧表のインデックスと名前が重なるものがあるため)
            for (t in SPLIT_V11_REBUILT_TABLES) db.execSQL("DROP TABLE `${t}_old`")
            for (t in SPLIT_V11_REBUILT_TABLES) {
                for (sql in splitV11IndexSqls(t)) db.execSQL(sql)
            }

            val after = V11_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }
            if (before != after) {
                throw IllegalStateException("v11 への移行を中止しました: 件数が変わりました $before -> $after")
            }
            val fkAfter = db.splitFkViolations(listOf("split_participants", "split_label_amounts") + SPLIT_V11_REBUILT_TABLES)
            if (fkAfter > fkBefore) {
                throw IllegalStateException("v11 への移行を中止しました: 割り勘の表の外部キーの違反が増えました $fkBefore -> $fkAfter")
            }
        }
    }

    /** v12 の移行の前後で件数と金額の合計を比べる対象(支出と支出グループ)。 */
    internal val V12_COUNT_QUERIES: List<Pair<String, String>> = listOf(
        "expenses" to "SELECT COUNT(*) FROM `expenses`",
        "expense_groups" to "SELECT COUNT(*) FROM `expense_groups`",
        "expenses の金額の合計" to "SELECT COALESCE(SUM(`amount`), 0) FROM `expenses`",
        "expense_groups の金額の合計" to "SELECT COALESCE(SUM(`amount`), 0) FROM `expense_groups`",
    )

    /** 手入力の支払いの表示用の日時: 時刻が無ければ日付だけ。 */
    internal fun v12PurchasedAt(date: String, time: String): String = if (time.isEmpty()) date else "$date $time"

    /** 直前に入れた行の id(同じ接続の last_insert_rowid)。 */
    private fun SupportSQLiteDatabase.lastRowId(): Long = query("SELECT last_insert_rowid()").use { c -> c.moveToFirst(); c.getLong(0) }

    /** 手入力の支払い(画像なし・確定済み・合計のみ)を receipts に1行作り、その id を返す。 */
    private fun SupportSQLiteDatabase.insertV12ManualReceipt(purchasedAt: String, total: Long, createdAt: Long): Long {
        execSQL(
            "INSERT INTO `receipts` (`purchasedAt`, `storeName`, `totalAmount`, `imagePath`, `rawJson`, `status`, `createdAt`, `totalSource`) " +
                "VALUES (?, '', ?, '', '{}', 'confirmed', ?, 'manual')",
            arrayOf<Any?>(purchasedAt, total, createdAt),
        )
        return lastRowId()
    }

    /**
     * v11 -> v12: 支払いに属さない支出を、手入力の支払い(receipts の行)に変える。支出は必ず支払いから作る、という規則に合わせる。
     *  - 対象: receiptId が null の支出グループ(品目はグループの中の支出の行)と、groupId も receiptId も無い単独の支出。
     *  - 支払いの行を対象ごとに1つ作る: imagePath='', rawJson='{}', status='confirmed', totalSource='manual', storeName=''。
     *    purchasedAt は日付(時刻があれば "yyyy-MM-dd HH:mm")、totalAmount は支払いの金額、createdAt は既存の値。
     *  - グループの品目は receipt_items に写す(name・amount・category・memo。税率は既定の10%、税込)。
     *    品目の支出の行には receiptId と receiptItemId を張る。家計簿で消した品目(deletedByUser)も含め、すべての品目に対応を付ける
     *    (対応が無い行は次の同期で作り直されるため)。
     *  - グループには receiptId を張る。単独の支出は品目を作らず、receiptId だけ張る。
     *  - 既存の支出・グループの id は変えない。新しい支出の行は作らない。金額・品名・カテゴリ・メモ・日時も変えない。
     *  - storeName は空なので、次の同期でグループ・単独の支出のメモ(店名)とカテゴリが既定値に戻らないよう、
     *    その2つは「編集済み」(memoEdited・categoryEdited)にしておく。
     * 安全策: 移行の前後で、支出とグループの件数と金額の合計が変わったら止める(トランザクションが戻る)。
     */
    val MIGRATION_11_12: Migration = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            class GroupRow(val id: Long, val date: String, val time: String, val amount: Long, val createdAt: Long)
            class ChildRow(val id: Long, val name: String, val amount: Long, val category: String, val memo: String)
            class SingleRow(val id: Long, val date: String, val time: String, val amount: Long, val createdAt: Long)

            val before = V12_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }

            // 1. 支払いに属さないグループ: 読み出しきってから書く
            val groups = ArrayList<GroupRow>()
            db.query(
                "SELECT `id`, `date`, `time`, `amount`, `createdAt` FROM `expense_groups` WHERE `receiptId` IS NULL ORDER BY `id`",
            ).use { c ->
                while (c.moveToNext()) {
                    groups += GroupRow(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4))
                }
            }
            for (g in groups) {
                val rid = db.insertV12ManualReceipt(v12PurchasedAt(g.date, g.time), g.amount, g.createdAt)
                db.execSQL(
                    "UPDATE `expense_groups` SET `receiptId` = ?, `categoryEdited` = 1, `memoEdited` = 1 WHERE `id` = ?",
                    arrayOf<Any?>(rid, g.id),
                )
                // 品目: グループの中の支出の行。写した品目の id を支出に張る
                val children = ArrayList<ChildRow>()
                db.query(
                    "SELECT `id`, `name`, `amount`, `category`, `memo` FROM `expenses` WHERE `groupId` = ${g.id} ORDER BY `id`",
                ).use { c ->
                    while (c.moveToNext()) {
                        children += ChildRow(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getString(4))
                    }
                }
                for (ch in children) {
                    db.execSQL(
                        "INSERT INTO `receipt_items` (`receiptId`, `name`, `amount`, `category`, `taxRate`, `priceIncludesTax`, `memo`) " +
                            "VALUES (?, ?, ?, ?, 10, 1, ?)",
                        arrayOf<Any?>(rid, ch.name, ch.amount, ch.category, ch.memo),
                    )
                    val itemId = db.lastRowId()
                    db.execSQL(
                        "UPDATE `expenses` SET `receiptId` = ?, `receiptItemId` = ? WHERE `id` = ?",
                        arrayOf<Any?>(rid, itemId, ch.id),
                    )
                }
            }

            // 2. 単独の支出: 支払いの行を作り、支出に張る(品目は作らない)
            val singles = ArrayList<SingleRow>()
            db.query(
                "SELECT `id`, `date`, `time`, `amount`, `createdAt` FROM `expenses` " +
                    "WHERE `groupId` IS NULL AND `receiptId` IS NULL ORDER BY `id`",
            ).use { c ->
                while (c.moveToNext()) {
                    singles += SingleRow(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4))
                }
            }
            for (s in singles) {
                val rid = db.insertV12ManualReceipt(v12PurchasedAt(s.date, s.time), s.amount, s.createdAt)
                db.execSQL(
                    "UPDATE `expenses` SET `receiptId` = ?, `categoryEdited` = 1, `memoEdited` = 1 WHERE `id` = ?",
                    arrayOf<Any?>(rid, s.id),
                )
            }

            val after = V12_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }
            if (before != after) {
                throw IllegalStateException("v12 への移行を中止しました: 件数または金額の合計が変わりました $before -> $after")
            }
        }
    }

    /**
     * v12 -> v13: receipts に参加人数(headcount, INTEGER, null 可・既定値なし)を追加する。
     * 既存の行は null(不明)になる。Entity の Receipt.headcount(Int?, 既定値なし)と完全に一致させること。
     */
    val MIGRATION_12_13: Migration = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `receipts` ADD COLUMN `headcount` INTEGER")
        }
    }

    /** v14 で足す列(receipt_items・expenses の unknownAmount)。Entity の @ColumnInfo(defaultValue = "0") と一致させること。 */
    internal val V14_ADD_COLUMN_SQLS: List<String> = listOf(
        "ALTER TABLE `receipt_items` ADD COLUMN `unknownAmount` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `expenses` ADD COLUMN `unknownAmount` INTEGER NOT NULL DEFAULT 0",
    )

    /** v14 の移行の前後で比べる値(件数と金額の合計。支出の金額の合計は、単独の支出がまとまりに移っても変わらない)。 */
    internal val V14_COUNT_QUERIES: List<Pair<String, String>> = listOf(
        "expenses" to "SELECT COUNT(*) FROM `expenses`",
        "expenses の金額の合計" to "SELECT COALESCE(SUM(`amount`), 0) FROM `expenses`",
        "家計簿に入る金額の合計" to
            "SELECT (SELECT COALESCE(SUM(`amount`), 0) FROM `expense_groups`) + " +
            "(SELECT COALESCE(SUM(`amount`), 0) FROM `expenses` WHERE `groupId` IS NULL AND `deletedByUser` = 0)",
    )

    /**
     * v13 -> v14: 支出は必ず「まとまり(expense_groups)+ 品目1行以上」にし、金額不明の印を足す。
     *  - receipt_items・expenses に unknownAmount(INTEGER NOT NULL DEFAULT 0)を足す。既存の行は 0(金額は分かっている)。
     *  - 単独の支出(groupId が無く、家計簿で消していない行)ごとに、まとまりを1つ作る(SingleExpenseConversion と同じ規則):
     *    日付・時刻・金額・カテゴリ・メモ・receiptId・作成時刻・編集済みの印を写し、uid は元の行の uid から決まる新しい値。
     *    元の行は、そのまとまりの品目にする(groupId を張り、品名は空。編集済みの印は下ろす)。id と uid は変えない。
     *  - 家計簿で消した行(deletedByUser = 1)で groupId が無いもの(立て替えの間に消した品目など。一覧・集計に出ない)は、
     *    同じ支払いの単独の支出から作ったまとまりがあれば、そのまとまりに付け替える(消したまま)。無ければ変えない。
     * 安全策: 移行の前後で、支出の件数・金額の合計・家計簿に入る金額の合計が変わったら止める(トランザクションが戻る)。
     */
    val MIGRATION_13_14: Migration = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V14_ADD_COLUMN_SQLS.forEach { db.execSQL(it) }
            val before = V14_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }

            class SingleRow(
                val id: Long, val date: String, val time: String, val amount: Long, val category: String, val memo: String,
                val receiptId: Long?, val createdAt: Long, val uid: String,
                val amountEdited: Long, val categoryEdited: Long, val memoEdited: Long, val timeEdited: Long,
            )
            // 読み出しきってから書く
            val singles = ArrayList<SingleRow>()
            db.query(
                "SELECT `id`, `date`, `time`, `amount`, `category`, `memo`, `receiptId`, `createdAt`, `uid`, " +
                    "`amountEdited`, `categoryEdited`, `memoEdited`, `timeEdited` FROM `expenses` " +
                    "WHERE `groupId` IS NULL AND `deletedByUser` = 0 ORDER BY `id`",
            ).use { c ->
                while (c.moveToNext()) {
                    singles += SingleRow(
                        id = c.getLong(0), date = c.getString(1), time = c.getString(2), amount = c.getLong(3),
                        category = c.getString(4), memo = c.getString(5), receiptId = if (c.isNull(6)) null else c.getLong(6),
                        createdAt = c.getLong(7), uid = c.getString(8),
                        amountEdited = c.getLong(9), categoryEdited = c.getLong(10), memoEdited = c.getLong(11), timeEdited = c.getLong(12),
                    )
                }
            }
            // 支払い -> その支払いで最初に作ったまとまり(消した行の付け替え先)
            val groupOfReceipt = HashMap<Long, Long>()
            for (e in singles) {
                db.execSQL(
                    "INSERT INTO `expense_groups` (`date`, `time`, `amount`, `category`, `memo`, `receiptId`, `createdAt`, `uid`, " +
                        "`amountEdited`, `categoryEdited`, `memoEdited`, `timeEdited`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        e.date, e.time, e.amount, e.category, e.memo, e.receiptId, e.createdAt,
                        SingleExpenseConversion.groupUid(e.uid),
                        e.amountEdited, e.categoryEdited, e.memoEdited, e.timeEdited,
                    ),
                )
                val gid = db.lastRowId()
                db.execSQL(
                    "UPDATE `expenses` SET `groupId` = ?, `name` = '', `amountEdited` = 0, `categoryEdited` = 0, " +
                        "`nameEdited` = 0, `memoEdited` = 0, `timeEdited` = 0 WHERE `id` = ?",
                    arrayOf<Any?>(gid, e.id),
                )
                e.receiptId?.let { groupOfReceipt.putIfAbsent(it, gid) }
            }
            for ((rid, gid) in groupOfReceipt) {
                db.execSQL(
                    "UPDATE `expenses` SET `groupId` = ? WHERE `groupId` IS NULL AND `deletedByUser` = 1 AND `receiptId` = ?",
                    arrayOf<Any?>(gid, rid),
                )
            }

            val after = V14_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }
            if (before != after) {
                throw IllegalStateException("v14 への移行を中止しました: 件数または金額の合計が変わりました $before -> $after")
            }
            val left = db.count("SELECT COUNT(*) FROM `expenses` WHERE `groupId` IS NULL AND `deletedByUser` = 0")
            if (left != 0L) throw IllegalStateException("v14 への移行を中止しました: 単独の支出が $left 件残りました")
        }
    }

    /**
     * v14 -> v15(支払い先は支払いだけに持つ): 表の形は変えない。支出のメモに写していた店名を消す。
     *  - expense_groups: メモを直していない(memoEdited = 0)行で、メモが紐づく支払いの店名と同じものを空にする。
     *  - expenses: 品目の無い支払いの1品目(receiptItemId なし)は支出のメモを写しているので、同じ条件で空にする。
     * 利用者が書いたメモ(memoEdited = 1、または店名と違うメモ)は残す。支払いの店名(receipts.storeName)は変えない。
     */
    internal val V15_SQLS: List<String> = listOf(
        "UPDATE `expense_groups` SET `memo` = '' WHERE `memoEdited` = 0 AND `memo` != '' AND `receiptId` IS NOT NULL " +
            "AND `memo` = (SELECT `storeName` FROM `receipts` WHERE `receipts`.`id` = `expense_groups`.`receiptId`)",
        "UPDATE `expenses` SET `memo` = '' WHERE `memoEdited` = 0 AND `memo` != '' AND `receiptItemId` IS NULL AND `receiptId` IS NOT NULL " +
            "AND `memo` = (SELECT `storeName` FROM `receipts` WHERE `receipts`.`id` = `expenses`.`receiptId`)",
    )

    val MIGRATION_14_15: Migration = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V15_SQLS.forEach { db.execSQL(it) }
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
    )
}
