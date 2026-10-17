package com.amgm.personallog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.amgm.personallog.data.model.DiaryEntry
import com.amgm.personallog.data.model.DiaryPhoto
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptNotDuplicate

/**
 * 注意: スキーマを変えて version を上げるときは、必ず Migrations.kt に Migration を追加すること。
 * fallbackToDestructiveMigration は使わない(データが消えるため)。
 */
@Database(
    entities = [DiaryEntry::class, DiaryPhoto::class, Receipt::class, ReceiptItem::class, Expense::class, ExpenseGroup::class, ReceiptNotDuplicate::class],
    version = 8, // AppDatabase.VERSION と同じにする(テストで確認)
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun expenseGroupDao(): ExpenseGroupDao

    companion object {
        const val NAME = "personal_log.db"

        /** Room の版。@Database の version と PreMigrationBackup が共有する。 */
        const val VERSION = 8
    }
}
