package com.amgm.personallog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.amgm.personallog.data.model.DiaryEntry
import com.amgm.personallog.data.model.DiaryPhoto
import com.amgm.personallog.data.model.ExpenseItem
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.data.model.PaymentNotDuplicate
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
interface PaymentDao {
    /**
     * 支出(まとまり)の無い下書きの支払い(割り勘のシートが無いもの)の id。家計簿の行を作り直す対象。
     * 支払い ⇒ 支出 の親子のうち「子の無い支払い」を残さないため(下書きは確認待ちとして家計簿に出す)。
     */
    @Query(
        "SELECT id FROM payments WHERE status = 'draft' " +
            "AND id NOT IN (SELECT paymentId FROM expenses) AND id NOT IN (SELECT paymentId FROM split_sheets)",
    )
    suspend fun draftIdsWithoutExpense(): List<Long>

    @Query("SELECT * FROM payments ORDER BY purchasedAt DESC, id DESC")
    fun observeAll(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE purchasedAt >= :from AND purchasedAt < :toExclusive ORDER BY purchasedAt DESC, id DESC")
    fun observeBetween(from: String, toExclusive: String): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE id = :id")
    fun observeById(id: Long): Flow<Payment?>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: Long): Payment?

    @Query("SELECT * FROM payments ORDER BY id")
    suspend fun getAll(): List<Payment>

    @Query("SELECT * FROM payments WHERE imageHash = :hash ORDER BY id")
    suspend fun getByImageHash(hash: String): List<Payment>

    @Query("SELECT * FROM payments WHERE imageHash IS NULL")
    suspend fun getWithoutImageHash(): List<Payment>

    /** 他の項目を上書きしないよう imageHash だけを更新する(未記録の行のみ)。 */
    @Query("UPDATE payments SET imageHash = :hash WHERE id = :id AND imageHash IS NULL")
    suspend fun setImageHashIfMissing(id: Long, hash: String): Int

    /** 補正値(回転・トリミング)の列だけを更新する。 */
    @Query(
        "UPDATE payments SET rotationDegrees = :rotation, cropLeft = :left, cropTop = :top, " +
            "cropRight = :right, cropBottom = :bottom WHERE id = :id",
    )
    suspend fun setAdjust(id: Long, rotation: Float, left: Float, top: Float, right: Float, bottom: Float): Int

    /** 参加人数(headcount)の列だけを更新する。null は「不明」。 */
    @Query("UPDATE payments SET headcount = :headcount WHERE id = :id")
    suspend fun setHeadcount(id: Long, headcount: Int?): Int

    @Insert
    suspend fun insert(payment: Payment): Long

    @Update
    suspend fun update(payment: Payment)

    @Delete
    suspend fun delete(payment: Payment)

    @Query("SELECT * FROM payment_items WHERE paymentId = :paymentId ORDER BY id")
    fun observeItems(paymentId: Long): Flow<List<PaymentItem>>

    @Query("SELECT * FROM payment_items WHERE paymentId = :paymentId ORDER BY id")
    suspend fun getItems(paymentId: Long): List<PaymentItem>

    @Insert
    suspend fun insertItems(items: List<PaymentItem>)

    @Insert
    suspend fun insertItem(item: PaymentItem): Long

    @Query("SELECT * FROM payment_items ORDER BY id")
    suspend fun getAllItems(): List<PaymentItem>

    @Update
    suspend fun updateItem(item: PaymentItem)

    @Delete
    suspend fun deleteItem(item: PaymentItem)

    @Query("DELETE FROM payment_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM payment_items WHERE paymentId = :paymentId")
    suspend fun deleteItemsByPayment(paymentId: Long)

    /** マージ: 品目を別の支払いへ付け替える(品目の id は変えない)。 */
    @Query("UPDATE payment_items SET paymentId = :toPaymentId WHERE paymentId = :fromPaymentId")
    suspend fun moveItems(fromPaymentId: Long, toPaymentId: Long)

    /** マージ: 支払いを id で削除する(品目・割り勘・「重複ではない」の組は CASCADE で消える)。 */
    @Query("DELETE FROM payments WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 「重複ではない」の組を追加する。既にある組は何もしない。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotDuplicate(pair: PaymentNotDuplicate)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotDuplicates(pairs: List<PaymentNotDuplicate>)

    @Query("SELECT * FROM payment_not_duplicates ORDER BY paymentIdLow, paymentIdHigh")
    suspend fun getAllNotDuplicates(): List<PaymentNotDuplicate>

    @Query("SELECT * FROM payment_not_duplicates ORDER BY paymentIdLow, paymentIdHigh")
    fun observeNotDuplicates(): Flow<List<PaymentNotDuplicate>>
}

@Dao
interface ExpenseItemDao {
    @Query("SELECT * FROM expense_items ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseItem>>

    /** from / to は "yyyy-MM-dd"(両端を含む)。 */
    @Query("SELECT * FROM expense_items WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeBetween(from: String, to: String): Flow<List<ExpenseItem>>

    @Query("SELECT * FROM expense_items WHERE id = :id")
    suspend fun getById(id: Long): ExpenseItem?

    @Query("SELECT * FROM expense_items WHERE paymentId = :paymentId ORDER BY id")
    suspend fun getByPaymentId(paymentId: Long): List<ExpenseItem>

    @Query("SELECT * FROM expense_items WHERE paymentId = :paymentId ORDER BY id")
    fun observeByPaymentId(paymentId: Long): Flow<List<ExpenseItem>>

    @Query("SELECT * FROM expense_items WHERE id = :id")
    fun observeById(id: Long): Flow<ExpenseItem?>

    @Query("SELECT * FROM expense_items WHERE groupId = :groupId ORDER BY id")
    fun observeByGroupId(groupId: Long): Flow<List<ExpenseItem>>

    @Query("SELECT * FROM expense_items WHERE groupId = :groupId ORDER BY id")
    suspend fun getByGroupId(groupId: Long): List<ExpenseItem>

    @Update
    suspend fun updateAll(expenseItems: List<ExpenseItem>)

    @Query("DELETE FROM expense_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 品目が消えたとき、その品目から作った支出も消す(外部キーの SET NULL で古い行と区別が付かなくなるのを防ぐ)。 */
    @Query("DELETE FROM expense_items WHERE paymentItemId IN (:itemIds)")
    suspend fun deleteByPaymentItemIds(itemIds: List<Long>)

    @Insert
    suspend fun insert(expenseItem: ExpenseItem): Long

    /** 家計簿で消した印だけを更新する(他の欄を古い値で上書きしない)。 */
    @Query("UPDATE expense_items SET deletedByUser = :flag WHERE id = :id")
    suspend fun setDeletedByUser(id: Long, flag: Boolean)

    @Query("SELECT * FROM expense_items ORDER BY id")
    suspend fun getAll(): List<ExpenseItem>

    @Insert
    suspend fun insertAll(expenseItems: List<ExpenseItem>)

    @Update
    suspend fun update(expenseItem: ExpenseItem)

    /** 支払いの日時が変わったとき、紐づく支出のうち日時を支出側で変えていないものだけ追従させる。 */
    @Query("UPDATE expense_items SET date = :date, time = :time WHERE paymentId = :paymentId AND timeEdited = 0")
    suspend fun syncDateTimeFromPayment(paymentId: Long, date: String, time: String)

    @Delete
    suspend fun delete(expenseItem: ExpenseItem)

    @Query("DELETE FROM expense_items WHERE paymentId = :paymentId")
    suspend fun deleteByPaymentId(paymentId: Long)

    @Query("DELETE FROM expense_items WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: Long)

    /** マージ: 支出を別の支払いへ付け替える。 */
    @Query("UPDATE expense_items SET paymentId = :toPaymentId WHERE paymentId = :fromPaymentId")
    suspend fun moveToPayment(fromPaymentId: Long, toPaymentId: Long)

    /**
     * 家計簿の画面用: 支出(グループ)の日付で期間に入る品目。v14〜 品目は必ずグループに属する。
     * 品目の日付が古いままでも、グループが期間内ならその品目が入る(月をまたいだ二重計上を防ぐ)。
     */
    @Query(
        "SELECT * FROM expense_items WHERE groupId IN (SELECT id FROM expenses WHERE date BETWEEN :from AND :to) " +
            "ORDER BY date DESC, id DESC",
    )
    fun observeLedgerRows(from: String, to: String): Flow<List<ExpenseItem>>

    /**
     * 自分の支出(グループ)が1つでもある支払いの id。期間に関係なく全件を見る
     * (割り勘で自分の分が 0 円の支払いを見分けるため。支出が無い支払いだけが対象になる)。
     * 自分の分が0円の間に非表示で残す消した品目(groupId なし)は、支出ではないので数えない。
     */
    @Query("SELECT paymentId FROM expenses WHERE paymentId IS NOT NULL")
    fun observePaymentIdsWithRows(): Flow<List<Long>>

    /** グループの日時が変わったとき、その品目の日時を合わせる。 */
    @Query("UPDATE expense_items SET date = :date, time = :time WHERE groupId = :groupId")
    suspend fun syncGroupChildrenDateTime(groupId: Long, date: String, time: String)

    /** レシート由来の全グループについて、品目の日時をグループの日時に合わせる(グループの日時が支払い側で変えられていても揃う)。 */
    @Query(
        "UPDATE expense_items SET " +
            "date = (SELECT g.date FROM expenses g WHERE g.id = expense_items.groupId), " +
            "time = (SELECT g.time FROM expenses g WHERE g.id = expense_items.groupId) " +
            "WHERE groupId IN (SELECT id FROM expenses WHERE paymentId = :paymentId)",
    )
    suspend fun syncChildrenOfPaymentGroups(paymentId: Long)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<Expense>>

    /** from / to は "yyyy-MM-dd"(両端を含む)。 */
    @Query("SELECT * FROM expenses WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeBetween(from: String, to: String): Flow<List<Expense>>

    /** 支払いの日時が変わったとき、紐づく支出(まとまり)のうち日時を支払い側で変えていないものだけ追従させる。 */
    @Query("UPDATE expenses SET date = :date, time = :time WHERE paymentId = :paymentId AND timeEdited = 0")
    suspend fun syncDateTimeFromPayment(paymentId: Long, date: String, time: String)

    @Query("SELECT * FROM expenses WHERE paymentId = :paymentId ORDER BY id")
    suspend fun getByPaymentId(paymentId: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE paymentId = :paymentId ORDER BY id")
    fun observeByPaymentId(paymentId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeById(id: Long): Flow<Expense?>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Insert
    suspend fun insert(group: Expense): Long

    @Update
    suspend fun update(group: Expense)

    @Delete
    suspend fun delete(group: Expense)

    @Query("DELETE FROM expenses WHERE paymentId = :paymentId")
    suspend fun deleteByPaymentId(paymentId: Long)

    /** マージ: 自分の支出(グループ)を別の支払いへ付け替える。 */
    @Query("UPDATE expenses SET paymentId = :toPaymentId WHERE paymentId = :fromPaymentId")
    suspend fun moveToPayment(fromPaymentId: Long, toPaymentId: Long)
}
