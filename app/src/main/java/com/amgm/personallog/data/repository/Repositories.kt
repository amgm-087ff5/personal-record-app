package com.amgm.personallog.data.repository

import androidx.room.withTransaction
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.db.DiaryDao
import com.amgm.personallog.data.db.ExpenseItemDao
import com.amgm.personallog.data.db.ExpenseDao
import com.amgm.personallog.data.db.PaymentDao
import com.amgm.personallog.data.model.DiaryEntry
import com.amgm.personallog.data.model.DiaryPhoto
import com.amgm.personallog.data.model.ExpenseItem
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.data.model.PaymentNotDuplicate
import com.amgm.personallog.data.model.PaymentStatus
import com.amgm.personallog.domain.ConfirmPlan
import com.amgm.personallog.domain.ConfirmPlanner
import com.amgm.personallog.domain.ExpenseItems
import com.amgm.personallog.domain.ItemSaver
import com.amgm.personallog.domain.LedgerCalc
import com.amgm.personallog.domain.PaymentMerge
import com.amgm.personallog.domain.PaymentField
import com.amgm.personallog.domain.PaymentLink
import com.amgm.personallog.domain.PaymentValues
import com.amgm.personallog.domain.PaymentWriteBack
import com.amgm.personallog.domain.PaymentRevert
import com.amgm.personallog.domain.WriteBackPlan
import com.amgm.personallog.domain.WriteBackTarget
import com.amgm.personallog.domain.SelfShare
import com.amgm.personallog.domain.SplitWarningCodes
import com.amgm.personallog.domain.UnknownAmounts
import com.amgm.personallog.domain.split.SplitCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
internal suspend fun SplitRepository.shareOf(paymentId: Long, total: Int?, items: List<PaymentItem>): SplitShare {
    val build = buildFor(paymentId, total, items) ?: return SplitShare(null, "")
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
class PaymentRepository @Inject constructor(
    private val db: AppDatabase,
    private val paymentDao: PaymentDao,
    private val expenseItemDao: ExpenseItemDao,
    private val expenseDao: ExpenseDao,
    private val splitRepository: SplitRepository,
) {
    fun observePayments(): Flow<List<Payment>> = paymentDao.observeAll()
    fun observePayment(id: Long): Flow<Payment?> = paymentDao.observeById(id)
    fun observeItems(paymentId: Long): Flow<List<PaymentItem>> = paymentDao.observeItems(paymentId)
    suspend fun getPayment(id: Long): Payment? = paymentDao.getById(id)
    suspend fun getItems(paymentId: Long): List<PaymentItem> = paymentDao.getItems(paymentId)
    suspend fun allPayments(): List<Payment> = paymentDao.getAll()

    /**
     * 支出の無い下書きの支払いに、家計簿の行(自分の支出)を作る。v16 の移行で残した下書き(支出だけ消された・古い版で作られた)を、
     * 確認待ちとして家計簿に出すため。合計も品目も無いものは何もしない(applyLedger)。
     */
    suspend fun repairDraftsWithoutExpense() {
        for (id in paymentDao.draftIdsWithoutExpense()) syncLedger(id)
    }
    suspend fun paymentsByImageHash(hash: String): List<Payment> = paymentDao.getByImageHash(hash)
    suspend fun paymentsWithoutImageHash(): List<Payment> = paymentDao.getWithoutImageHash()
    suspend fun setImageHashIfMissing(id: Long, hash: String) {
        paymentDao.setImageHashIfMissing(id, hash)
    }

    /** 補正値の列だけを更新する(他の項目は変えない)。 */
    suspend fun setAdjust(id: Long, a: com.amgm.personallog.receipt.ReceiptAdjust) {
        paymentDao.setAdjust(id, a.rotationDegrees, a.cropLeft, a.cropTop, a.cropRight, a.cropBottom)
    }

    /** 「重複ではない」と判断した組を保存する(小さい id を Low に正規化。同じ id や保存済みの組は何もしない)。 */
    suspend fun markNotDuplicate(idA: Long, idB: Long, now: Long = System.currentTimeMillis()) {
        val pair = PaymentNotDuplicate.of(idA, idB, now) ?: return
        paymentDao.insertNotDuplicate(pair)
    }

    suspend fun notDuplicates(): List<PaymentNotDuplicate> = paymentDao.getAllNotDuplicates()
    fun observeNotDuplicates(): Flow<List<PaymentNotDuplicate>> = paymentDao.observeNotDuplicates()

    /**
     * 支払いと品目を保存(id=0なら新規)。品目は差分で保存する: id がある行は更新、無い行は追加、画面から消えた行は削除。
     * こうして品目の id を保ち、家計簿の行(paymentItemId)との対応を切らない。paymentId を返す。
     * sharedFlags は共通費の提案(items と同じ並び。件数が合わなければ無視する)。新しいまとまりの共通費の印になる。
     * 提案は品目の id が決まった後・まとまりを作る前に渡す。後から当てても、もうまとまりに入った品目は動かないため。
     */
    suspend fun save(payment: Payment, items: List<PaymentItem>, sharedFlags: List<Boolean> = emptyList()): Long = db.withTransaction {
        val id = if (payment.id == 0L) {
            paymentDao.insert(payment)
        } else {
            paymentDao.update(payment)
            payment.id
        }
        val plan = ItemSaver.plan(id, paymentDao.getItems(id), items)
        if (plan.deleteIds.isNotEmpty()) {
            // 外部キーの SET NULL だと古い行と区別が付かなくなるので、消えた品目の支出は先に消す
            expenseItemDao.deleteByPaymentItemIds(plan.deleteIds)
            plan.deleteIds.forEach { paymentDao.deleteItemById(it) }
        }
        plan.updates.forEach { paymentDao.updateItem(it) }
        if (plan.inserts.isNotEmpty()) paymentDao.insertItems(plan.inserts)
        // 保存後の品目 id に提案を対応させる(件数が違えば提案は無視)
        val hints: Map<Long, Boolean> = if (sharedFlags.size == items.size) {
            ItemSaver.savedIdsInOrder(items, plan, paymentDao.getItems(id).map { it.id }).zip(sharedFlags).toMap()
        } else {
            emptyMap<Long, Boolean>()
        }
        // 支払いの日時は家計簿側の初期値。支出側で日時を変えていない行だけ追従させる。
        val (date, time) = LedgerCalc.splitPurchasedAt(payment.purchasedAt)
        expenseItemDao.syncDateTimeFromPayment(id, date, time)
        expenseDao.syncDateTimeFromPayment(id, date, time)
        // 品目の日時は支出に従う(グループの日時を支払い側で変えていても揃える)
        expenseItemDao.syncChildrenOfPaymentGroups(id)
        // 下書きも家計簿に入れる。品目のまとまりを揃えてから、割り勘を含めた家計簿の行を作る
        splitRepository.ensureGroups(id, hints)
        applyLedger(id, now = System.currentTimeMillis())
        id
    }

    /**
     * 支払いを確定し、家計簿を更新する。再確定では品目ごとに差分で更新し、家計簿で直した欄(編集済み)は残す。
     * v9 以前に確定した古い行は、作り直す(ConfirmPlanner)。
     * perItem は互換のための引数で使わない(v14〜 支出は必ず品目つき)。
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun confirm(paymentId: Long, perItem: Boolean = true, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val payment = paymentDao.getById(paymentId) ?: return@withTransaction
            // 合計は確定には必須。未入力なら家計簿を作らない(既存の家計簿も消さない)
            checkNotNull(payment.totalAmount) { "合計が未入力のため確定できません" }
            applyLedger(paymentId, now)
            paymentDao.update(payment.copy(status = PaymentStatus.CONFIRMED))
        }
    }

    /**
     * 保存と確定を1つのトランザクションで行う。途中で失敗したら、保存した内容も含めてすべて戻る
     * (保存だけ済んで確定に失敗した状態を残さない)。paymentId を返す。
     */
    suspend fun saveAndConfirm(
        payment: Payment,
        items: List<PaymentItem>,
        perItem: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): Long = db.withTransaction {
        val id = save(payment, items)
        confirm(id, perItem, now)
        id
    }

    /**
     * いま保存してある家計簿の行が、この内容での確定で失われるか(確認の文面に使う)。
     * items は画面の品目(新規は id=0)。
     *  - rebuild: 家計簿の行が作り直される(古い形式)。
     *  - lostEdited: 家計簿で直した行のうち、作り直しまたは品目の削除で消える件数。
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun confirmImpact(payment: Payment, items: List<PaymentItem>, perItem: Boolean = true): ConfirmImpact {
        if (payment.id == 0L || payment.totalAmount == null) return ConfirmImpact.NONE
        val groups = expenseDao.getByPaymentId(payment.id)
        val existing = expenseItemDao.getByPaymentId(payment.id)
        if (groups.isEmpty() && existing.isEmpty()) return ConfirmImpact.NONE
        // 保存で消える品目の支出は、先に消えたものとして計画する
        val stored = paymentDao.getItems(payment.id)
        val plan = ItemSaver.plan(payment.id, stored, items)
        val deleted = plan.deleteIds.toSet()
        val removed = existing.filter { it.paymentItemId != null && it.paymentItemId in deleted }
        val remaining = existing.filter { it.paymentItemId == null || it.paymentItemId !in deleted }
        // 割り勘があれば、確定後の行は自分の分になる(その内容で作り直すかを判定する)
        val share = splitRepository.shareOf(payment.id, payment.totalAmount, items).share
        val p = ConfirmPlanner.plan(payment, items, groups, remaining, 0L, share)
        return ConfirmImpact(
            rebuild = ConfirmPlanner.discardsExisting(p, groups, remaining),
            lostEdited = ConfirmPlanner.lostEditedCount(p, groups, remaining, removed),
            keptDeleted = (p as? ConfirmPlan.Rebuild)?.keepDeleted?.size ?: 0,
        )
    }

    /** 互換用: 作り直されるか。 */
    suspend fun confirmWouldRebuild(payment: Payment, items: List<PaymentItem>, perItem: Boolean = true): Boolean =
        confirmImpact(payment, items, perItem).rebuild

    /** この支払いに紐づく自分の支出(まとまり)。無ければ null。 */
    fun observePaymentLink(paymentId: Long): Flow<PaymentLink?> =
        expenseDao.observeByPaymentId(paymentId).map { groups -> PaymentLink.resolve(groups) }

    /** レシート由来の家計簿の行(支出とグループ)を消す。 */
    suspend fun deleteExpenseItemsOf(paymentId: Long) {
        expenseItemDao.deleteByPaymentId(paymentId)
        expenseDao.deleteByPaymentId(paymentId)
    }

    // ------------------------------------------------------------ 重複した支払いのマージ

    /** マージの比較の材料(支払い・品目・割り勘の有無)を集め、登録順(createdAt、id)に並べて返す。見つからない id は除く。 */
    suspend fun mergeSnapshots(ids: List<Long>): List<PaymentMerge.Snapshot> {
        val snapshots = ids.distinct().mapNotNull { id ->
            val payment = paymentDao.getById(id) ?: return@mapNotNull null
            PaymentMerge.Snapshot(
                payment = payment,
                items = paymentDao.getItems(id),
                hasSplit = db.splitDao().countSplitRows(id) > 0,
            )
        }
        return PaymentMerge.ordered(snapshots)
    }

    /**
     * マージを実行する(1つのトランザクション)。マージ先(plan.payment.id)の中身を、plan.contentFrom の支払いの一式に置き換え、
     * マージ元(plan.removed)を消して、家計簿を作り直す。もう使われない画像の相対パスを返す(呼び出し側が消す)。
     */
    suspend fun mergePayments(plan: PaymentMerge.Plan, now: Long = System.currentTimeMillis()): List<String> = db.withTransaction {
        val target = plan.payment.id
        val from = plan.contentFrom
        val split = db.splitDao()
        val removedPayments = plan.removed.mapNotNull { paymentDao.getById(it) }
        val oldTarget = paymentDao.getById(target)
        val unused = (removedPayments.map { it.imagePath } + listOfNotNull(oldTarget?.imagePath))
            .filter { it.isNotBlank() && it != plan.payment.imagePath }
            .distinct()

        if (from != target) {
            // マージ先の中身を消す(消すのは paymentId = マージ先の行だけ。split_sheets / whole_shares は主キーに paymentId を含むので先に空ける)
            deleteExpenseItemsOf(target)
            paymentDao.deleteItemsByPayment(target)
            split.deleteLabelAmounts(target)
            split.deleteSheet(target)
            split.deleteGroupsOfPayment(target)
            split.deleteParticipantsOfPayment(target)
            split.deleteWholeShares(target)
            split.deletePayers(target)
            // 採る側の中身をマージ先へ付け替える(id は変えないので、まとまり・割当・家計簿の品目との対応も保たれる)
            paymentDao.moveItems(from, target)
            split.moveSheet(from, target)
            split.moveParticipants(from, target)
            split.moveLabelAmounts(from, target)
            split.moveGroups(from, target)
            split.moveWholeShares(from, target)
            split.movePayers(from, target)
            expenseDao.moveToPayment(from, target)
            expenseItemDao.moveToPayment(from, target)
        }
        // マージ元に残る自分の支出・品目を消す(expenses は SET_NULL なので、消さないと孤児になる)
        plan.removed.forEach { deleteExpenseItemsOf(it) }
        paymentDao.update(plan.payment)
        if (plan.removed.isNotEmpty()) paymentDao.deleteByIds(plan.removed)
        applyLedger(target, now)
        unused
    }

    /**
     * 家計簿をこの支払いの今の内容に合わせる(下書きでも行う。状態は変えない)。割り勘があれば自分の分だけを家計簿にする。
     * 合計も品目も無ければ何もしない(既存の家計簿も消さない)。
     */
    suspend fun syncLedger(paymentId: Long, now: Long = System.currentTimeMillis()) {
        db.withTransaction { applyLedger(paymentId, now = now) }
    }

    // ------------------------------------------------------------ 金額不明の品目(支払いの品目に印を付け、家計簿を作り直す)

    /**
     * 「未入力を全て不明にする」: 支払いの品目のうち金額が未入力(0円)のものを、金額不明にして保存する。
     * 対象が無ければ何もせず理由を返す。成功なら null。
     */
    suspend fun markBlankItemsUnknown(paymentId: Long): String? = db.withTransaction {
        val payment = paymentDao.getById(paymentId) ?: return@withTransaction MESSAGE_NO_PAYMENT
        val items = paymentDao.getItems(paymentId)
        if (items.none(UnknownAmounts::isBlank)) return@withTransaction MESSAGE_NO_BLANK_ITEM
        save(payment, UnknownAmounts.markBlankAsUnknown(items))
        null
    }

    /**
     * 「差額を不明の品目として足す」: 合計 − 分かっている品目の合計 を受け持つ、金額不明の品目を1つ足して保存する。
     * 足せない(未入力の品目がある・不明の品目が既にある・差が0以下・合計が無い)ときは何もせず理由を返す。成功なら null。
     */
    suspend fun addDifferenceAsUnknownItem(paymentId: Long): String? = db.withTransaction {
        val payment = paymentDao.getById(paymentId) ?: return@withTransaction MESSAGE_NO_PAYMENT
        val items = paymentDao.getItems(paymentId)
        if (UnknownAmounts.differenceForUnknownItem(payment.totalAmount, items) == null) {
            return@withTransaction MESSAGE_NO_DIFFERENCE
        }
        save(payment, UnknownAmounts.withDifferenceItem(paymentId, payment.totalAmount, items))
        null
    }

    /** 支払いの品目1つの「金額不明」を切り替えて保存する。成功なら null。 */
    suspend fun setItemUnknown(paymentId: Long, itemId: Long, unknown: Boolean): String? = db.withTransaction {
        val payment = paymentDao.getById(paymentId) ?: return@withTransaction MESSAGE_NO_PAYMENT
        val items = paymentDao.getItems(paymentId)
        if (items.none { it.id == itemId }) return@withTransaction "支払いの品目が見つかりません。"
        save(payment, items.map { if (it.id == itemId) it.copy(unknownAmount = unknown) else it })
        null
    }

    /** 割り勘の参加者の数。割り勘シートが無ければ1人(自分だけ)。 */
    suspend fun participantCount(paymentId: Long): Int =
        if (db.splitDao().getSheet(paymentId) == null) 1 else splitRepository.load(paymentId).participants.size

    /**
     * 支出の画面で直した欄を、支払い・品目へ書き戻し、家計簿を作り直す(1つのトランザクション)。
     * 許されない変更(割り勘で参加者2人以上の金額など)は何も書かず、理由を返す。成功なら null。
     * 支出の金額は、立て替えなら支払いの自分の分(ownAmount)、それ以外は支払いの合計(印字のあるレシートも MANUAL にして)へ書き戻す。
     * 書き戻したあと、金額が家計簿に入っていなければ例外にして、すべて戻す。
     */
    suspend fun writeBackFromLedger(
        target: WriteBackTarget,
        values: PaymentValues,
        now: Long = System.currentTimeMillis(),
    ): String? = db.withTransaction {
        val paymentId = target.paymentId ?: return@withTransaction "この支出は支払いに紐づいていないため、書き戻せません。"
        val payment = paymentDao.getById(paymentId) ?: return@withTransaction "支払いが見つかりません。"
        val items = paymentDao.getItems(paymentId)
        // 割り勘シートが無ければ1人(画面の splitPeople と同じ数え方。load はシートを作ってしまうし、旧い立て替えを2人と数える)
        val people = participantCount(paymentId)
        when (val plan = PaymentWriteBack.plan(target, values, payment, items, people)) {
            is WriteBackPlan.Reject -> plan.message
            is WriteBackPlan.Apply -> {
                if (plan.payment != payment) paymentDao.update(plan.payment)
                plan.changedItems.forEach { paymentDao.updateItem(it) }
                plan.expenseItem?.let { expenseItemDao.update(it) }
                plan.group?.let { expenseDao.update(it) }
                // 品目が1つだけの品目の金額は支出(まとまり)の金額として書き戻すので、まとまりの金額の編集済みも下ろす
                if (target is WriteBackTarget.Item && target.onlyItem && plan.expectedAmount != null) {
                    expenseDao.getByPaymentId(paymentId).filter { it.amountEdited }
                        .forEach { expenseDao.update(it.copy(amountEdited = false)) }
                }
                applyLedger(paymentId, now = now)
                plan.expectedAmount?.let { want ->
                    val got = when (target) {
                        is WriteBackTarget.Item -> expenseItemDao.getByPaymentId(paymentId)
                            .firstOrNull { it.paymentItemId == target.expenseItem.paymentItemId }?.amount
                        is WriteBackTarget.Group -> expenseDao.getByPaymentId(paymentId).singleOrNull()?.amount
                    }
                    check(got == want) { "支払いに書き戻せませんでした(家計簿の金額 $got、入力 $want)" }
                }
                null
            }
        }
    }

    /** 支払い先(店名)を直し、家計簿を合わせる(1つのトランザクション)。 */
    suspend fun saveStoreName(paymentId: Long, storeName: String, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val r = paymentDao.getById(paymentId) ?: return@withTransaction
            val updated = PaymentWriteBack.withStoreName(r, storeName)
            if (updated == r) return@withTransaction
            paymentDao.update(updated)
            applyLedger(paymentId, now = now)
        }
    }

    /**
     * 割り勘シートを保存し、家計簿を合わせる(1つのトランザクション)。保存後のシートを返す。
     * 旧い立て替え(isAdvance・ownAmount)は割り勘シートに置き換わるので消す。
     *
     * 規則の例外(設計判断待ち・(g)): 割り勘で自分の分を変えると、支出の金額だけが変わる(支払いの合計は変えない)。
     * 自分の分は割り勘の計算結果で支払いに対応する欄が無く、支払いの金額は変えない規則(明示しない限り)と整合する。
     * 参加者2人以上のときの支出の金額の直接編集を割り勘画面へ誘導するかは、利用者の判断を待つ。
     */
    suspend fun saveSplit(
        data: SplitSheetData,
        names: Map<Long, String>,
        now: Long = System.currentTimeMillis(),
    ): SplitSheetData = db.withTransaction {
        val saved = splitRepository.save(data, names, now)
        paymentDao.getById(data.paymentId)?.let { r ->
            if (r.isAdvance || r.ownAmount != null) paymentDao.update(r.copy(isAdvance = false, ownAmount = null))
        }
        syncLedger(data.paymentId, now)
        saved
    }

    /**
     * 家計簿の行を、いまの支払い(品目・合計・割り勘)に合わせて作る・直す。状態は変えない。
     * 呼び出し側のトランザクションの中で使う。割り勘の警告コードも保存する。
     */
    private suspend fun applyLedger(paymentId: Long, now: Long) {
        val payment = paymentDao.getById(paymentId) ?: return
        val items = paymentDao.getItems(paymentId)
        // 合計も品目も無ければ、家計簿に入れるものが無い
        if (payment.totalAmount == null && items.isEmpty()) return
        val groups = expenseDao.getByPaymentId(paymentId)
        val existing = expenseItemDao.getByPaymentId(paymentId)
        val split = splitRepository.shareOf(paymentId, payment.totalAmount, items)
        splitRepository.setWarnings(paymentId, split.warnings)
        when (val plan = ConfirmPlanner.plan(payment, items, groups, existing, now, split.share)) {
            is ConfirmPlan.Rebuild -> {
                deleteExpenseItemsOf(paymentId)
                if (plan.group != null) {
                    val groupId = expenseDao.insert(plan.group)
                    expenseItemDao.insertAll(plan.children.map { it.copy(groupId = groupId) })
                    // 家計簿で消した品目は、消したまま新しいグループに付け替えて残す(作り直しで復活させない)
                    if (plan.keepDeleted.isNotEmpty()) {
                        expenseItemDao.insertAll(
                            plan.keepDeleted.map { it.copy(groupId = groupId, date = plan.group.date, time = plan.group.time) },
                        )
                    }
                } else if (plan.keepDeleted.isNotEmpty()) {
                    // 自分の分が0円の間は、消した品目を非表示のまま残す(自分の分ができたときに付け替える)
                    expenseItemDao.insertAll(plan.keepDeleted.map { it.copy(groupId = null) })
                }
            }
            is ConfirmPlan.UpdateGroup -> {
                expenseDao.update(plan.group)
                if (plan.deleteIds.isNotEmpty()) expenseItemDao.deleteByIds(plan.deleteIds)
                if (plan.updates.isNotEmpty()) expenseItemDao.updateAll(plan.updates)
                if (plan.inserts.isNotEmpty()) expenseItemDao.insertAll(plan.inserts.map { it.copy(groupId = plan.group.id) })
            }
        }
    }

    /**
     * 支払いを削除する。支払いから作った自分の支出・品目、支払いの品目・割り勘・重複でない印も一緒に消える
     * (支払い ⇒ 支出 の親子をまとめて消す。親の無い支出・子の無い支払いを残さない)。
     * 支出の画面・家計簿の一覧・支払いの画面のどこから消しても、これを使う。消した支払いを返す(レシート画像の削除に使う)。
     * 自分の負担を0円にしたいときは削除ではなく負担割りで行う(0円の支払いは家計簿に0円の行として残る)。
     */
    suspend fun deletePayment(paymentId: Long): Payment? = db.withTransaction {
        val r = paymentDao.getById(paymentId) ?: return@withTransaction null
        // 外部キー(CASCADE)でも消えるが、外部キーの有効・無効に頼らず明示して消す
        deleteExpenseItemsOf(paymentId)
        paymentDao.delete(r)
        r
    }

    companion object {
        const val MESSAGE_NO_PAYMENT = "支払いが見つかりません。"
        const val MESSAGE_NO_BLANK_ITEM = "金額が未入力の品目はありません。"
        const val MESSAGE_NO_DIFFERENCE = "差額を不明の品目として足せません(未入力・不明の品目があるか、差額がありません)。"
    }
}

@Singleton
class ExpenseRepository @Inject constructor(
    private val dao: ExpenseItemDao,
    private val groupDao: ExpenseDao,
    private val db: AppDatabase,
    private val splitRepository: SplitRepository,
) {
    fun observeAll(): Flow<List<ExpenseItem>> = dao.observeAll()
    fun observeBetween(from: String, to: String): Flow<List<ExpenseItem>> = dao.observeBetween(from, to)

    /** 家計簿の画面用の行。品目はグループの日付で期間に入れる(月をまたいで二重に数えない)。 */
    fun observeLedgerRows(from: String, to: String): Flow<List<ExpenseItem>> = dao.observeLedgerRows(from, to)
    fun observeGroupsBetween(from: String, to: String): Flow<List<Expense>> = groupDao.observeBetween(from, to)

    /** yearMonth は "yyyy-MM"。 */
    fun observeMonth(yearMonth: String): Flow<List<ExpenseItem>> {
        val (from, to) = LedgerCalc.monthRange(yearMonth)
        return dao.observeBetween(from, to)
    }

    /** その支払いから作られた支出(まとまり)。自分の支出が無い(割り勘で外れた)なら空。 */
    suspend fun groupsOfPayment(paymentId: Long): List<Expense> = groupDao.getByPaymentId(paymentId)
    fun observeGroup(id: Long): Flow<Expense?> = groupDao.observeById(id)
    fun observeGroupItems(groupId: Long): Flow<List<ExpenseItem>> = dao.observeByGroupId(groupId)
    fun observeExpenseItem(id: Long): Flow<ExpenseItem?> = dao.observeById(id)

    /**
     * グループを更新する。日時が変わったら品目の日時も合わせる(品目の日付がグループとずれて月をまたいで数えられるのを防ぐ)。
     * 品目が1つなら、その金額を支出の金額に合わせる(ExpenseItems.normalize)。
     */
    suspend fun updateGroup(group: Expense): Unit = db.withTransaction {
        groupDao.update(group)
        dao.syncGroupChildrenDateTime(group.id, group.date, group.time)
        normalizeItems(group)
    }

    /** 支出の品目を規則(1品目なら金額は支出と同じ・不明の品目の金額)に合わせる。変わった行だけ更新する。 */
    private suspend fun normalizeItems(group: Expense) {
        val items = dao.getByGroupId(group.id)
        val normalized = ExpenseItems.normalize(group, items)
        val changed = normalized.filterIndexed { i, e -> e != items[i] }
        if (changed.isNotEmpty()) dao.updateAll(changed)
    }
    suspend fun updateAll(expenseItems: List<ExpenseItem>) = dao.updateAll(expenseItems)

    /** レシート画像のサムネイル・「支払いを開く」用。 */
    suspend fun payment(id: Long): Payment? = db.paymentDao().getById(id)

    /** 品目を更新する。品目の属する支出の規則(1品目なら金額は支出と同じ・不明の品目の金額)に合わせ直す。 */
    suspend fun update(expenseItem: ExpenseItem): Unit = db.withTransaction {
        dao.update(expenseItem)
        val group = expenseItem.groupId?.let { groupDao.getById(it) }
        if (group != null) normalizeItems(group)
    }

    /**
     * 支出の品目を消す。レシート由来の品目(paymentItemId がある行)は、行を消さずに deletedByUser を立てる
     * (支払いの再確定で復活させない)。手入力の行は本当に消す。
     * 支出の品目は1つ以上必要なので、消していない品目が最後の1つなら消さずに例外にする(支出ごと消すのは支払いの削除 deletePayment)。
     *
     * 規則の例外(設計判断待ち・(e)): 品目の削除は支払いの品目(payment_items)へ反映しない。支出だけが deletedByUser で持つ
     * (支払いは店での会計の事実なので、品目は残す)。支払いの品目を消す・支払いの合計を直すかは、利用者の判断を待つ。
     */
    suspend fun delete(expenseItem: ExpenseItem): Unit = db.withTransaction {
        val groupId = expenseItem.groupId
        if (groupId != null) {
            val visible = dao.getByGroupId(groupId).count { !it.deletedByUser && it.id != expenseItem.id }
            check(visible >= 1) { MESSAGE_LAST_ITEM }
        }
        if (expenseItem.paymentItemId != null && groupId != null) {
            dao.setDeletedByUser(expenseItem.id, true)
        } else {
            dao.delete(expenseItem)
        }
    }

    /** 家計簿で消した品目を元に戻す。規則の例外(設計判断待ち・(e)): 削除と同じく、支払いの品目へは反映しない(支出の deletedByUser を下ろすだけ)。 */
    suspend fun restore(expenseItem: ExpenseItem) = dao.setDeletedByUser(expenseItem.id, false)

    /**
     * 品目の1つの欄を、紐づく支払いの値に戻す(その欄の編集済みを下ろす)。
     * 値を求められなければ false(何も変えない)。
     */
    suspend fun revertExpenseItemField(expenseItemId: Long, field: PaymentField): Boolean = db.withTransaction {
        val e = dao.getById(expenseItemId) ?: return@withTransaction false
        val paymentId = e.paymentId ?: return@withTransaction false
        val payment = db.paymentDao().getById(paymentId) ?: return@withTransaction false
        val items = db.paymentDao().getItems(paymentId)
        val group = e.groupId?.let { groupDao.getById(it) }
        // 割り勘があれば、支払いの値の代わりに自分の分へ戻す
        val share = splitRepository.shareOf(paymentId, payment.totalAmount, items).share
        val reverted = PaymentRevert.expenseItem(e, field, payment, items, group, share) ?: return@withTransaction false
        dao.update(reverted)
        true
    }

    /** 支出(まとまり)の1つの欄を、紐づく支払いの値に戻す。日時を戻したら品目の日時も合わせる。 */
    suspend fun revertGroupField(groupId: Long, field: PaymentField): Boolean = db.withTransaction {
        val g = groupDao.getById(groupId) ?: return@withTransaction false
        val paymentId = g.paymentId
        val payment = db.paymentDao().getById(paymentId) ?: return@withTransaction false
        val items = db.paymentDao().getItems(paymentId)
        val share = splitRepository.shareOf(paymentId, payment.totalAmount, items).share
        val reverted = PaymentRevert.group(g, field, payment, items, share) ?: return@withTransaction false
        updateGroup(reverted)
        true
    }
    suspend fun get(id: Long): ExpenseItem? = dao.getById(id)

    /** 支出が紐づく支払いの日時 "yyyy-MM-dd HH:mm"(支払いに時刻が無ければ "yyyy-MM-dd")。紐づかなければ null。 */
    suspend fun linkedPaymentDateTime(expenseItem: ExpenseItem): String? = paymentDateTime(expenseItem.paymentId)

    /** 支払いの日時 "yyyy-MM-dd HH:mm"(時刻が無ければ "yyyy-MM-dd")。paymentId が null や無い支払いなら null。 */
    suspend fun paymentDateTime(paymentId: Long?): String? {
        if (paymentId == null) return null
        val r = db.paymentDao().getById(paymentId) ?: return null
        val (date, time) = LedgerCalc.splitPurchasedAt(r.purchasedAt)
        return if (time.isEmpty()) date else "$date $time"
    }

    /** 支出(まとまり)を、品目ごと消す。 */
    /** 品目の属する支出(まとまり)の id。品目が無い・まとまりに属さない(v13 以前の古い行)なら null。旧ルートの解決に使う。 */
    suspend fun groupIdOfExpenseItem(expenseItemId: Long): Long? = dao.getById(expenseItemId)?.groupId

    suspend fun groupItems(groupId: Long): List<ExpenseItem> = dao.getByGroupId(groupId)

    companion object {
        const val MESSAGE_LAST_ITEM = "支出の品目は1つ以上必要です。支出ごと消すときは「この支出を削除」(元の支払いも消えます)を使ってください。"
    }
}
