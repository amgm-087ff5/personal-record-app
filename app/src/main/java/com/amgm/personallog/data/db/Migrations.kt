package com.amgm.personallog.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * ルール(更新エラー/データ消失の防止):
 *  - AppDatabase の version を上げるたびに、ここへ Migration(旧, 新) を必ず追加し ALL に登録する。
 *  - fallbackToDestructiveMigration は絶対に使わない。
 *  - version を上げたら app/schemas/<version>.json もコミットする(mainのCIが自動コミットする)。
 */
object Migrations {
    /**
     * v1 -> v2: receipts に原本画像のSHA-256(imageHash)を追加する。
     * Entity の Receipt.imageHash(String?, 既定値なし, index なし)と完全に一致させること:
     * NULL許容・DEFAULT無し・TEXT。列の位置は Receipt の最後(createdAt の後)。
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `receipts` ADD COLUMN `imageHash` TEXT")
        }
    }

    /**
     * v2 -> v3: 品目の税率・税込/税抜・負担割合と、レシートの立て替えフラグを追加する。
     * 既存行は「印字どおりの金額を税込として扱い、全額自分の負担」になる(従来の動作と同じ)。
     * Entity の @ColumnInfo(defaultValue) と完全に一致させること。
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `receipts` ADD COLUMN `isAdvance` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `receipt_items` ADD COLUMN `taxRate` INTEGER NOT NULL DEFAULT 10")
            db.execSQL("ALTER TABLE `receipt_items` ADD COLUMN `priceIncludesTax` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `receipt_items` ADD COLUMN `ownPercent` INTEGER NOT NULL DEFAULT 100")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
