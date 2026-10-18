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
import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.PaymentPayer
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.ShareGroupItem
import com.amgm.personallog.data.model.SplitSheet
import com.amgm.personallog.data.model.WholeShare

/**
 * 注意: スキーマを変えて version を上げるときは、必ず Migrations.kt に Migration を追加すること。
 * fallbackToDestructiveMigration は使わない(データが消えるため)。
 */
@Database(
    entities = [
        DiaryEntry::class, DiaryPhoto::class, Receipt::class, ReceiptItem::class, Expense::class, ExpenseGroup::class, ReceiptNotDuplicate::class,
        // v10: 割り勘の表。人・割り勘シート・品目のまとまり・割当・支払内訳。注意: この注釈の中には丸括弧を書かない。SchemaVersionTest の正規表現が版を見つけられなくなる
        Person::class, SplitSheet::class, ShareGroup::class, ShareGroupItem::class, ItemShare::class, WholeShare::class, PaymentPayer::class,
    ],
    version = 10, // AppDatabase.VERSION と同じにする(テストで確認)
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun expenseGroupDao(): ExpenseGroupDao
    abstract fun splitDao(): SplitDao

    companion object {
        const val NAME = "personal_log.db"

        /** Room の版。@Database の version と PreMigrationBackup が共有する。 */
        const val VERSION = 10
    }
}
