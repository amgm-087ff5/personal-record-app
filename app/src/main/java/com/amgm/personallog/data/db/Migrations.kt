package com.amgm.personallog.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amgm.personallog.domain.PricedLine
import com.amgm.personallog.domain.legacyReceiptOwnYen
import com.amgm.personallog.receipt.LegacyRow
import com.amgm.personallog.receipt.ReceiptUnits
import com.amgm.personallog.receipt.TotalSourceInit

/**
 * ルール(更新エラー/データ消失の防止):
 *  - AppDatabase の version を上げるたびに、ここへ Migration(旧, 新) を必ず追加し ALL に登録する。
 *  - fallbackToDestructiveMigration は絶対に使わない。
 *  - version を上げたら app/schemas/<version>.json もコミットする(mainのCIが自動コミットする)。
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
     * v8 の receipts の CREATE TABLE。Room が生成する 8.json の createSql(`${TABLE_NAME}` を表名にしたもの)と
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

    /** v8 の receipt_not_duplicates の CREATE TABLE(8.json の createSql と一致させる)。 */
    fun notDuplicatesV8CreateSql(table: String): String =
        "CREATE TABLE IF NOT EXISTS `$table` (" +
            "`receiptIdLow` INTEGER NOT NULL, " +
            "`receiptIdHigh` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`receiptIdLow`, `receiptIdHigh`), " +
            "FOREIGN KEY(`receiptIdLow`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`receiptIdHigh`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    /** v8 の receipt_not_duplicates のインデックス(8.json の indices[].createSql と一致させる)。 */
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
     * 最後に PRAGMA foreign_key_check も流す。
     */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
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
            // AUTOINCREMENT の採番を引き継ぐ(最大 id より後ろの採番を保つ)
            if (oldSeq != null) {
                db.execSQL("UPDATE `sqlite_sequence` SET `seq` = MAX(`seq`, ?) WHERE `name` = 'receipts'", arrayOf<Any?>(oldSeq))
            }

            db.execSQL(notDuplicatesV8CreateSql("receipt_not_duplicates"))
            db.execSQL(notDuplicatesV8IndexSql("receipt_not_duplicates"))

            val after = V8_CHILD_COUNT_QUERIES.map { (name, sql) -> name to db.count(sql) }
            if (before != after) {
                throw IllegalStateException("v8 への移行を中止しました: 件数が変わりました $before -> $after")
            }
            db.query("PRAGMA foreign_key_check").use { c ->
                if (c.moveToFirst()) {
                    throw IllegalStateException("v8 への移行を中止しました: 外部キーの整合性エラー(表 ${c.getString(0)})")
                }
            }
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
    )
}
