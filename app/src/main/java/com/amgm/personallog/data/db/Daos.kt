package com.amgm.personallog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.amgm.personallog.data.model.CategoryTotal
import com.amgm.personallog.data.model.DiaryEntry
import com.amgm.personallog.data.model.DiaryPhoto
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptNotDuplicate
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY date DESC")
    fun observeAll(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeBetween(from: String, to: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DiaryEntry?>

    @Query("SELECT * FROM diary_entries WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DiaryEntry?

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun getById(id: Long): DiaryEntry?

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)

    @Query("SELECT * FROM diary_photos WHERE entryId = :entryId ORDER BY sortOrder, id")
    fun observePhotos(entryId: Long): Flow<List<DiaryPhoto>>

    @Query("SELECT * FROM diary_photos ORDER BY id")
    fun observeAllPhotos(): Flow<List<DiaryPhoto>>

    @Query("SELECT * FROM diary_photos WHERE entryId = :entryId ORDER BY sortOrder, id")
    suspend fun getPhotos(entryId: Long): List<DiaryPhoto>

    @Insert
    suspend fun insertPhoto(photo: DiaryPhoto): Long

    @Update
    suspend fun updatePhoto(photo: DiaryPhoto)

    @Delete
    suspend fun deletePhoto(photo: DiaryPhoto)
}

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts ORDER BY purchasedAt DESC, id DESC")
    fun observeAll(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE purchasedAt >= :from AND purchasedAt < :toExclusive ORDER BY purchasedAt DESC, id DESC")
    fun observeBetween(from: String, toExclusive: String): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    fun observeById(id: Long): Flow<Receipt?>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getById(id: Long): Receipt?

    @Query("SELECT * FROM receipts ORDER BY id")
    suspend fun getAll(): List<Receipt>

    @Query("SELECT * FROM receipts WHERE imageHash = :hash ORDER BY id")
    suspend fun getByImageHash(hash: String): List<Receipt>

    @Query("SELECT * FROM receipts WHERE imageHash IS NULL")
    suspend fun getWithoutImageHash(): List<Receipt>

    /** 他の項目を上書きしないよう imageHash だけを更新する(未記録の行のみ)。 */
    @Query("UPDATE receipts SET imageHash = :hash WHERE id = :id AND imageHash IS NULL")
    suspend fun setImageHashIfMissing(id: Long, hash: String): Int

    @Insert
    suspend fun insert(receipt: Receipt): Long

    @Update
    suspend fun update(receipt: Receipt)

    @Delete
    suspend fun delete(receipt: Receipt)

    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId ORDER BY id")
    fun observeItems(receiptId: Long): Flow<List<ReceiptItem>>

    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId ORDER BY id")
    suspend fun getItems(receiptId: Long): List<ReceiptItem>

    @Insert
    suspend fun insertItems(items: List<ReceiptItem>)

    @Insert
    suspend fun insertItem(item: ReceiptItem): Long

    @Query("SELECT * FROM receipt_items ORDER BY id")
    suspend fun getAllItems(): List<ReceiptItem>

    @Update
    suspend fun updateItem(item: ReceiptItem)

    @Delete
    suspend fun deleteItem(item: ReceiptItem)

    @Query("DELETE FROM receipt_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun deleteItemsByReceipt(receiptId: Long)

    /** 「重複ではない」の組を追加する。既にある組は何もしない。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotDuplicate(pair: ReceiptNotDuplicate)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotDuplicates(pairs: List<ReceiptNotDuplicate>)

    @Query("SELECT * FROM receipt_not_duplicates ORDER BY receiptIdLow, receiptIdHigh")
    suspend fun getAllNotDuplicates(): List<ReceiptNotDuplicate>

    @Query("SELECT * FROM receipt_not_duplicates ORDER BY receiptIdLow, receiptIdHigh")
    fun observeNotDuplicates(): Flow<List<ReceiptNotDuplicate>>
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Expense>>

    /** from / to は "yyyy-MM-dd"(両端を含む)。 */
    @Query("SELECT * FROM expenses WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeBetween(from: String, to: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE receiptId = :receiptId ORDER BY id")
    suspend fun getByReceiptId(receiptId: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE receiptId = :receiptId ORDER BY id")
    fun observeByReceiptId(receiptId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeById(id: Long): Flow<Expense?>

    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY id")
    fun observeByGroupId(groupId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY id")
    suspend fun getByGroupId(groupId: Long): List<Expense>

    @Update
    suspend fun updateAll(expenses: List<Expense>)

    @Query("DELETE FROM expenses WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 品目が消えたとき、その品目から作った支出も消す(外部キーの SET NULL で古い行と区別が付かなくなるのを防ぐ)。 */
    @Query("DELETE FROM expenses WHERE receiptItemId IN (:itemIds)")
    suspend fun deleteByReceiptItemIds(itemIds: List<Long>)

    @Insert
    suspend fun insert(expense: Expense): Long

    @Insert
    suspend fun insertAll(expenses: List<Expense>)

    @Update
    suspend fun update(expense: Expense)

    /** レシートの日時が変わったとき、紐づく支出のうち日時を支出側で変えていないものだけ追従させる。 */
    @Query("UPDATE expenses SET date = :date, time = :time WHERE receiptId = :receiptId AND timeEdited = 0")
    suspend fun syncDateTimeFromReceipt(receiptId: Long, date: String, time: String)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses WHERE receiptId = :receiptId")
    suspend fun deleteByReceiptId(receiptId: Long)

    @Query("DELETE FROM expenses WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: Long)

    /** 月別(期間別)のカテゴリ集計。支出の多い順。 */
    @Query(
        "SELECT category AS category, SUM(amount) AS total FROM expenses " +
            "WHERE date BETWEEN :from AND :to GROUP BY category ORDER BY total DESC",
    )
    fun observeCategoryTotals(from: String, to: String): Flow<List<CategoryTotal>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE date BETWEEN :from AND :to")
    fun observeTotal(from: String, to: String): Flow<Long>
}

@Dao
interface ExpenseGroupDao {
    @Query("SELECT * FROM expense_groups ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseGroup>>

    /** from / to は "yyyy-MM-dd"(両端を含む)。 */
    @Query("SELECT * FROM expense_groups WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeBetween(from: String, to: String): Flow<List<ExpenseGroup>>

    /** レシートの日時が変わったとき、紐づく支払いグループのうち日時を支払い側で変えていないものだけ追従させる。 */
    @Query("UPDATE expense_groups SET date = :date, time = :time WHERE receiptId = :receiptId AND timeEdited = 0")
    suspend fun syncDateTimeFromReceipt(receiptId: Long, date: String, time: String)

    @Query("SELECT * FROM expense_groups WHERE receiptId = :receiptId ORDER BY id")
    suspend fun getByReceiptId(receiptId: Long): List<ExpenseGroup>

    @Query("SELECT * FROM expense_groups WHERE receiptId = :receiptId ORDER BY id")
    fun observeByReceiptId(receiptId: Long): Flow<List<ExpenseGroup>>

    @Query("SELECT * FROM expense_groups WHERE id = :id")
    fun observeById(id: Long): Flow<ExpenseGroup?>

    @Query("SELECT * FROM expense_groups WHERE id = :id")
    suspend fun getById(id: Long): ExpenseGroup?

    @Insert
    suspend fun insert(group: ExpenseGroup): Long

    @Update
    suspend fun update(group: ExpenseGroup)

    @Delete
    suspend fun delete(group: ExpenseGroup)

    @Query("DELETE FROM expense_groups WHERE receiptId = :receiptId")
    suspend fun deleteByReceiptId(receiptId: Long)
}
