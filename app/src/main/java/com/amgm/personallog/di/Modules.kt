package com.amgm.personallog.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.db.DiaryDao
import com.amgm.personallog.data.db.ExpenseItemDao
import com.amgm.personallog.data.db.ExpenseDao
import com.amgm.personallog.data.db.Migrations
import com.amgm.personallog.data.db.PreMigrationBackup
import com.amgm.personallog.data.db.PaymentDao
import com.amgm.personallog.data.db.SplitDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        // 移行に失敗しても元のデータが残るよう、Room を開く前に古い版の DB を複製しておく
        PreMigrationBackup.backupIfNeeded(context, AppDatabase.NAME, AppDatabase.VERSION)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // fallbackToDestructiveMigration は使わない。version を上げたら Migrations.kt に追加する。
            .addMigrations(*Migrations.ALL)
            .build()
    }

    @Provides fun provideDiaryDao(db: AppDatabase): DiaryDao = db.diaryDao()
    @Provides fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideExpenseItemDao(db: AppDatabase): ExpenseItemDao = db.expenseItemDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideSplitDao(db: AppDatabase): SplitDao = db.splitDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}
