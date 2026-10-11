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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
