package com.amgm.personallog.data.repository

import androidx.room.withTransaction
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.db.DiaryDao
import com.amgm.personallog.data.db.ExpenseDao
import com.amgm.personallog.data.db.ExpenseGroupDao
import com.amgm.personallog.data.db.ReceiptDao
import com.amgm.personallog.data.model.DiaryEntry
import com.amgm.personallog.data.model.DiaryPhoto
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptNotDuplicate
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.domain.ConfirmPlan
import com.amgm.personallog.domain.ConfirmPlanner
import com.amgm.personallog.domain.ItemSaver
import com.amgm.personallog.domain.LedgerCalc
import com.amgm.personallog.domain.PaymentField
import com.amgm.personallog.domain.PaymentLink
import com.amgm.personallog.domain.ReceiptRevert
import com.amgm.personallog.domain.SelfShare
import com.amgm.personallog.domain.SplitWarningCodes
import com.amgm.personallog.domain.split.SplitCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(
    private val dao: DiaryDao,
) {
    fun observeEntries(): Flow<List<DiaryEntry>> = dao.observeAll()
    fun observeEntriesBetween(from: String, to: String): Flow<List<DiaryEntry>> = dao.observeBetween(from, to)
    fun observeEntry(date: String): Flow<DiaryEntry?> = dao.observeByDate(date)
    fun observePhotos(entryId: Long): Flow<List<DiaryPhoto>> = dao.observePhotos(entryId)
    fun observeAllPhotos(): Flow<List<DiaryPhoto>> = dao.observeAllPhotos()

    /** その日の日記を保存(無ければ作成、あれば本文を更新)。entryId を返す。 */
    suspend fun saveText(date: String, text: String, now: Long = System.currentTimeMillis()): Long {
        val existing = dao.getByDate(date)
        return if (existing == null) {
            dao.insert(DiaryEntry(date = date, text = text, createdAt = now, updatedAt = now))
        } else {
            dao.update(existing.copy(text = text, updatedAt = now))
            existing.id
        }
    }

    /** 写真追加用: その日の日記が無ければ空本文で作って entryId を返す。 */
    suspend fun getOrCreateEntryId(date: String, now: Long = System.currentTimeMillis()): Long =
        dao.getByDate(date)?.id
            ?: dao.insert(DiaryEntry(date = date, text = "", createdAt = now, updatedAt = now))

    suspend fun deleteEntry(entry: DiaryEntry) = dao.delete(entry)

    suspend fun addPhoto(entryId: Long, relativePath: String, takenAt: Long? = null): Long {
        val order = dao.getPhotos(entryId).size
        return dao.insertPhoto(DiaryPhoto(entryId = entryId, filePath = relativePath, takenAt = takenAt, sortOrder = order))
    }

    suspend fun updatePhoto(photo: DiaryPhoto) = dao.updatePhoto(photo)
    suspend fun deletePhoto(photo: DiaryPhoto) = dao.deletePhoto(photo)
}

/** 割り勘から求めた家計簿用の結果。share は自分の分(割り勘が無い・自分だけなら null)、warnings は警告コード("W1,W3"。無ければ "")。 */
internal data class SplitShare(val share: SelfShare?, val warnings: String)

/** この支払いの割り勘を計算し、家計簿に使う自分の分と警告を求める。割り勘が無ければ share も warnings も空。 */
internal suspend fun SplitRepository.shareOf(receiptId: Long, total: Int?, items: List<ReceiptItem>): SplitShare {
    val build = buildFor(receiptId, total, items) ?: return SplitShare(null, "")
    val result = SplitCalculator.calculate(build.input)
    return SplitShare(
        share = SelfShare.of(result, build.selfParticipantId, build.itemYen),
        warnings = SplitWarningCodes.of(result.warnings),
    )
}

/** 確定で家計簿の行がどうなるか(確認の表示用)。 */
data class ConfirmImpact(val rebuild: Boolean, val lostEdited: Int, val keptDeleted: Int = 0) {
    /** 知らせて確認を取るべきか。 */
    val needsWarning: Boolean get() = rebuild || lostEdited > 0

    companion object {
        val NONE = ConfirmImpact(rebuild = false, lostEdited = 0)
    }
}

@Singleton
class ReceiptRepository @Inject constructor(
    private val db: AppDatabase,
    private val receiptDao: ReceiptDao,
    private val expenseDao: ExpenseDao,
    private val expenseGroupDao: ExpenseGroupDao,
    private val splitRepository: SplitRepository,
) {
    fun observeReceipts(): Flow<List<Receipt>> = receiptDao.observeAll()
    fun observeReceipt(id: Long): Flow<Receipt?> = receiptDao.observeById(id)
    fun observeItems(receiptId: Long): Flow<List<ReceiptItem>> = receiptDao.observeItems(receiptId)
    suspend fun getReceipt(id: Long): Receipt? = receiptDao.getById(id)
    suspend fun getItems(receiptId: Long): List<ReceiptItem> = receiptDao.getItems(receiptId)
    suspend fun allReceipts(): List<Receipt> = receiptDao.getAll()
    suspend fun receiptsByImageHash(hash: String): List<Receipt> = receiptDao.getByImageHash(hash)
    suspend fun receiptsWithoutImageHash(): List<Receipt> = receiptDao.getWithoutImageHash()
    suspend fun setImageHashIfMissing(id: Long, hash: String) {
        receiptDao.setImageHashIfMissing(id, hash)
    }

    /** 補正値の列だけを更新する(他の項目は変えない)。 */
    suspend fun setAdjust(id: Long, a: com.amgm.personallog.receipt.ReceiptAdjust) {
        receiptDao.setAdjust(id, a.rotationDegrees, a.cropLeft, a.cropTop, a.cropRight, a.cropBottom)
    }

    /** 「重複ではない」と判断した組を保存する(小さい id を Low に正規化。同じ id や保存済みの組は何もしない)。 */
    suspend fun markNotDuplicate(idA: Long, idB: Long, now: Long = System.currentTimeMillis()) {
        val pair = ReceiptNotDuplicate.of(idA, idB, now) ?: return
        receiptDao.insertNotDuplicate(pair)
    }

    suspend fun notDuplicates(): List<ReceiptNotDuplicate> = receiptDao.getAllNotDuplicates()
    fun observeNotDuplicates(): Flow<List<ReceiptNotDuplicate>> = receiptDao.observeNotDuplicates()

    /**
     * レシートと品目を保存(id=0なら新規)。品目は差分で保存する: id がある行は更新、無い行は追加、画面から消えた行は削除。
     * こうして品目の id を保ち、家計簿の行(receiptItemId)との対応を切らない。receiptId を返す。
     * sharedFlags は共通費の提案(items と同じ並び。件数が合わなければ無視する)。新しいまとまりの共通費の印になる。
     * 提案は品目の id が決まった後・まとまりを作る前に渡す。後から当てても、もうまとまりに入った品目は動かないため。
     */
    suspend fun save(receipt: Receipt, items: List<ReceiptItem>, sharedFlags: List<Boolean> = emptyList()): Long = db.withTransaction {
        val id = if (receipt.id == 0L) {
            receiptDao.insert(receipt)
        } else {
            receiptDao.update(receipt)
            receipt.id
        }
        val plan = ItemSaver.plan(id, receiptDao.getItems(id), items)
        if (plan.deleteIds.isNotEmpty()) {
            // 外部キーの SET NULL だと古い行と区別が付かなくなるので、消えた品目の支出は先に消す
            expenseDao.deleteByReceiptItemIds(plan.deleteIds)
            plan.deleteIds.forEach { receiptDao.deleteItemById(it) }
        }
        plan.updates.forEach { receiptDao.updateItem(it) }
        if (plan.inserts.isNotEmpty()) receiptDao.insertItems(plan.inserts)
        // 保存後の品目 id に提案を対応させる(件数が違えば提案は無視)
        val hints: Map<Long, Boolean> = if (sharedFlags.size == items.size) {
            ItemSaver.savedIdsInOrder(items, plan, receiptDao.getItems(id).map { it.id }).zip(sharedFlags).toMap()
        } else {
            emptyMap<Long, Boolean>()
        }
        // レシートの日時は家計簿側の初期値。支出側で日時を変えていない行だけ追従させる。
        val (date, time) = LedgerCalc.splitPurchasedAt(receipt.purchasedAt)
        expenseDao.syncDateTimeFromReceipt(id, date, time)
        expenseGroupDao.syncDateTimeFromReceipt(id, date, time)
        // 品目の日時はグループに従う(グループの日時を支払い側で変えていても揃える)
        expenseDao.syncChildrenOfReceiptGroups(id)
        // 下書きも家計簿に入れる。品目のまとまりを揃えてから、割り勘を含めた家計簿の行を作る
        splitRepository.ensureGroups(id, hints)
        applyLedger(id, perItem = true, now = System.currentTimeMillis())
        id
    }

    /**
     * レシートを確定し、家計簿を更新する。再確定では品目ごとに差分で更新し、家計簿で直した欄(編集済み)は残す。
     * 立て替えと通常を切り替えたときや、v9 以前に確定した古い行は、作り直す(ConfirmPlanner)。
     */
    suspend fun confirm(receiptId: Long, perItem: Boolean, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val receipt = receiptDao.getById(receiptId) ?: return@withTransaction
            // 合計は確定には必須。未入力なら家計簿を作らない(既存の家計簿も消さない)
            checkNotNull(receipt.totalAmount) { "合計が未入力のため確定できません" }
            applyLedger(receiptId, perItem, now)
            receiptDao.update(receipt.copy(status = ReceiptStatus.CONFIRMED))
        }
    }

    /**
     * 保存と確定を1つのトランザクションで行う。途中で失敗したら、保存した内容も含めてすべて戻る
     * (保存だけ済んで確定に失敗した状態を残さない)。receiptId を返す。
     */
    suspend fun saveAndConfirm(
        receipt: Receipt,
        items: List<ReceiptItem>,
        perItem: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): Long = db.withTransaction {
        val id = save(receipt, items)
        confirm(id, perItem, now)
        id
    }

    /**
     * いま保存してある家計簿の行が、この内容での確定で失われるか(確認の文面に使う)。
     * items は画面の品目(新規は id=0)。
     *  - rebuild: 家計簿の行が作り直される(立て替えの切り替え、古い形式)。
     *  - lostEdited: 家計簿で直した行のうち、作り直しまたは品目の削除で消える件数。
     */
    suspend fun confirmImpact(receipt: Receipt, items: List<ReceiptItem>, perItem: Boolean = true): ConfirmImpact {
        if (receipt.id == 0L || receipt.totalAmount == null) return ConfirmImpact.NONE
        val groups = expenseGroupDao.getByReceiptId(receipt.id)
        val existing = expenseDao.getByReceiptId(receipt.id)
        if (groups.isEmpty() && existing.isEmpty()) return ConfirmImpact.NONE
        // 保存で消える品目の支出は、先に消えたものとして計画する
        val stored = receiptDao.getItems(receipt.id)
        val plan = ItemSaver.plan(receipt.id, stored, items)
        val deleted = plan.deleteIds.toSet()
        val removed = existing.filter { it.receiptItemId != null && it.receiptItemId in deleted }
        val remaining = existing.filter { it.receiptItemId == null || it.receiptItemId !in deleted }
        // 割り勘があれば、確定後の行は自分の分になる(その内容で作り直すかを判定する)
        val share = splitRepository.shareOf(receipt.id, receipt.totalAmount, items).share
        val p = ConfirmPlanner.plan(receipt, items, groups, remaining, perItem, 0L, share)
        return ConfirmImpact(
            rebuild = ConfirmPlanner.discardsExisting(p, groups, remaining),
            lostEdited = ConfirmPlanner.lostEditedCount(p, groups, remaining, removed),
            keptDeleted = (p as? ConfirmPlan.Rebuild)?.keepDeleted?.size ?: 0,
        )
    }

    /** 互換用: 作り直されるか。 */
    suspend fun confirmWouldRebuild(receipt: Receipt, items: List<ReceiptItem>, perItem: Boolean = true): Boolean =
        confirmImpact(receipt, items, perItem).rebuild

    /** このレシートに紐づく家計簿の支払い(グループまたは単独の支出)。確定済みで無ければ null。 */
    fun observePaymentLink(receiptId: Long): Flow<PaymentLink?> =
        combine(expenseGroupDao.observeByReceiptId(receiptId), expenseDao.observeByReceiptId(receiptId)) { groups, expenses ->
            PaymentLink.resolve(groups, expenses)
        }

    /** レシート由来の家計簿の行(支出とグループ)を消す。 */
    suspend fun deleteExpensesOf(receiptId: Long) {
        expenseDao.deleteByReceiptId(receiptId)
        expenseGroupDao.deleteByReceiptId(receiptId)
    }

    /**
     * 家計簿をこの支払いの今の内容に合わせる(下書きでも行う。状態は変えない)。割り勘があれば自分の分だけを家計簿にする。
     * 合計も品目も無ければ何もしない(既存の家計簿も消さない)。
     */
    suspend fun syncLedger(receiptId: Long, now: Long = System.currentTimeMillis()) {
        db.withTransaction { applyLedger(receiptId, perItem = true, now = now) }
    }

    /**
     * 割り勘シートを保存し、家計簿を合わせる(1つのトランザクション)。保存後のシートを返す。
     * 旧い立て替え(isAdvance・ownAmount)は割り勘シートに置き換わるので消す。
     */
    suspend fun saveSplit(
        data: SplitSheetData,
        names: Map<Long, String>,
        now: Long = System.currentTimeMillis(),
    ): SplitSheetData = db.withTransaction {
        val saved = splitRepository.save(data, names, now)
        receiptDao.getById(data.receiptId)?.let { r ->
            if (r.isAdvance || r.ownAmount != null) receiptDao.update(r.copy(isAdvance = false, ownAmount = null))
        }
        syncLedger(data.receiptId, now)
        saved
    }

    /**
     * 家計簿の行を、いまの支払い(品目・合計・割り勘)に合わせて作る・直す。状態は変えない。
     * 呼び出し側のトランザクションの中で使う。割り勘の警告コードも保存する。
     */
    private suspend fun applyLedger(receiptId: Long, perItem: Boolean, now: Long) {
        val receipt = receiptDao.getById(receiptId) ?: return
        val items = receiptDao.getItems(receiptId)
        // 合計も品目も無ければ、家計簿に入れるものが無い
        if (receipt.totalAmount == null && items.isEmpty()) return
        val groups = expenseGroupDao.getByReceiptId(receiptId)
        val existing = expenseDao.getByReceiptId(receiptId)
        val split = splitRepository.shareOf(receiptId, receipt.totalAmount, items)
        splitRepository.setWarnings(receiptId, split.warnings)
        when (val plan = ConfirmPlanner.plan(receipt, items, groups, existing, perItem, now, split.share)) {
            is ConfirmPlan.Rebuild -> {
                deleteExpensesOf(receiptId)
                if (plan.group != null) {
                    val groupId = expenseGroupDao.insert(plan.group)
                    expenseDao.insertAll(plan.children.map { it.copy(groupId = groupId) })
                    // 家計簿で消した品目は、消したまま新しいグループに付け替えて残す(立て替えとの往復で復活させない)
                    if (plan.keepDeleted.isNotEmpty()) {
                        expenseDao.insertAll(
                            plan.keepDeleted.map { it.copy(groupId = groupId, date = plan.group.date, time = plan.group.time) },
                        )
                    }
                } else {
                    expenseDao.insertAll(plan.children)
                    // グループの無い間は、消した品目を非表示のまま残す(グループに戻ったときに付け替える)
                    if (plan.keepDeleted.isNotEmpty()) expenseDao.insertAll(plan.keepDeleted.map { it.copy(groupId = null) })
                }
            }
            is ConfirmPlan.UpdateGroup -> {
                expenseGroupDao.update(plan.group)
                if (plan.deleteIds.isNotEmpty()) expenseDao.deleteByIds(plan.deleteIds)
                if (plan.updates.isNotEmpty()) expenseDao.updateAll(plan.updates)
                if (plan.inserts.isNotEmpty()) expenseDao.insertAll(plan.inserts.map { it.copy(groupId = plan.group.id) })
            }
            is ConfirmPlan.UpdateSingle -> expenseDao.update(plan.expense)
        }
    }

    /** レシート削除。紐づく Expense は receiptId が NULL になって残る(家計簿は消さない)。 */
    suspend fun delete(receipt: Receipt) = receiptDao.delete(receipt)
}

@Singleton
class ExpenseRepository @Inject constructor(
    private val dao: ExpenseDao,
    private val groupDao: ExpenseGroupDao,
    private val db: AppDatabase,
    private val splitRepository: SplitRepository,
) {
    fun observeAll(): Flow<List<Expense>> = dao.observeAll()
    fun observeBetween(from: String, to: String): Flow<List<Expense>> = dao.observeBetween(from, to)

    /** 家計簿の画面用の行。品目はグループの日付で期間に入れる(月をまたいで二重に数えない)。 */
    fun observeLedgerRows(from: String, to: String): Flow<List<Expense>> = dao.observeLedgerRows(from, to)
    fun observeGroupsBetween(from: String, to: String): Flow<List<ExpenseGroup>> = groupDao.observeBetween(from, to)

    /** yearMonth は "yyyy-MM"。 */
    fun observeMonth(yearMonth: String): Flow<List<Expense>> {
        val (from, to) = LedgerCalc.monthRange(yearMonth)
        return dao.observeBetween(from, to)
    }

    fun observeGroup(id: Long): Flow<ExpenseGroup?> = groupDao.observeById(id)
    fun observeGroupItems(groupId: Long): Flow<List<Expense>> = dao.observeByGroupId(groupId)
    fun observeExpense(id: Long): Flow<Expense?> = dao.observeById(id)

    /** グループを更新する。日時が変わったら品目の日時も合わせる(品目の日付がグループとずれて月をまたいで数えられるのを防ぐ)。 */
    suspend fun updateGroup(group: ExpenseGroup) = db.withTransaction {
        groupDao.update(group)
        dao.syncGroupChildrenDateTime(group.id, group.date, group.time)
    }
    suspend fun updateAll(expenses: List<Expense>) = dao.updateAll(expenses)

    /** レシート画像のサムネイル・「レシートを開く」用。 */
    suspend fun receipt(id: Long): Receipt? = db.receiptDao().getById(id)

    suspend fun update(expense: Expense) = dao.update(expense)

    /**
     * 支出を消す。レシート由来の品目(グループの中の、receiptItemId がある行)は、行を消さずに deletedByUser を立てる
     * (レシートの再確定で復活させない)。手入力の行・単独の支出は本当に消す。
     */
    suspend fun delete(expense: Expense) {
        if (expense.receiptItemId != null && expense.groupId != null) {
            dao.setDeletedByUser(expense.id, true)
        } else {
            dao.delete(expense)
        }
    }

    /** 家計簿で消した品目を元に戻す。 */
    suspend fun restore(expense: Expense) = dao.setDeletedByUser(expense.id, false)

    /**
     * 品目(または単独の支出)の1つの欄を、紐づくレシートの値に戻す(その欄の編集済みを下ろす)。
     * 値を求められなければ false(何も変えない)。
     */
    suspend fun revertExpenseField(expenseId: Long, field: PaymentField): Boolean = db.withTransaction {
        val e = dao.getById(expenseId) ?: return@withTransaction false
        val receiptId = e.receiptId ?: return@withTransaction false
        val receipt = db.receiptDao().getById(receiptId) ?: return@withTransaction false
        val items = db.receiptDao().getItems(receiptId)
        val group = e.groupId?.let { groupDao.getById(it) }
        // 割り勘があれば、レシートの値の代わりに自分の分へ戻す
        val share = splitRepository.shareOf(receiptId, receipt.totalAmount, items).share
        val reverted = ReceiptRevert.expense(e, field, receipt, items, group, share) ?: return@withTransaction false
        dao.update(reverted)
        true
    }

    /** 支払いグループの1つの欄を、紐づくレシートの値に戻す。日時を戻したら品目の日時も合わせる。 */
    suspend fun revertGroupField(groupId: Long, field: PaymentField): Boolean = db.withTransaction {
        val g = groupDao.getById(groupId) ?: return@withTransaction false
        val receiptId = g.receiptId ?: return@withTransaction false
        val receipt = db.receiptDao().getById(receiptId) ?: return@withTransaction false
        val items = db.receiptDao().getItems(receiptId)
        val share = splitRepository.shareOf(receiptId, receipt.totalAmount, items).share
        val reverted = ReceiptRevert.group(g, field, receipt, items, share) ?: return@withTransaction false
        updateGroup(reverted)
        true
    }
    suspend fun get(id: Long): Expense? = dao.getById(id)

    /** 支出が紐づくレシートの日時 "yyyy-MM-dd HH:mm"(レシートに時刻が無ければ "yyyy-MM-dd")。紐づかなければ null。 */
    suspend fun linkedReceiptDateTime(expense: Expense): String? = receiptDateTime(expense.receiptId)

    /** レシートの日時 "yyyy-MM-dd HH:mm"(時刻が無ければ "yyyy-MM-dd")。receiptId が null や無いレシートなら null。 */
    suspend fun receiptDateTime(receiptId: Long?): String? {
        if (receiptId == null) return null
        val r = db.receiptDao().getById(receiptId) ?: return null
        val (date, time) = LedgerCalc.splitPurchasedAt(r.purchasedAt)
        return if (time.isEmpty()) date else "$date $time"
    }

    /** 支払いグループを、品目ごと消す。 */
    suspend fun deleteGroup(group: ExpenseGroup) = db.withTransaction {
        dao.deleteByGroupId(group.id)
        groupDao.delete(group)
    }
}
