package com.amgm.personallog.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amgm.personallog.receipt.LegacyRow
import com.amgm.personallog.receipt.ReceiptUnits

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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
