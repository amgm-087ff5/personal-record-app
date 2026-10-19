package com.amgm.personallog.data.repository

import com.amgm.personallog.data.db.ExpenseDao
import com.amgm.personallog.data.db.ExpenseGroupDao
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.domain.PaymentLink
import com.amgm.personallog.domain.split.MealRecord
import com.amgm.personallog.domain.split.PaymentMeal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 支払い1件の食べた記録(画面用)。
 * meal は自分が食べた分の行と、未入力のまとまりの数。link は紐づく家計簿の支払い(確定していなければ null)。
 */
data class MealPayment(
    val paymentId: Long,
    /** 支払いの日時 "yyyy-MM-dd" または "yyyy-MM-dd HH:mm"。 */
    val at: String,
    val storeName: String,
    val draft: Boolean,
    val meal: PaymentMeal,
    val link: PaymentLink?,
)

/**
 * 食べた記録の読み込み。新しい表は持たず、支払い(レシート)と割り勘の計算結果から毎回求める。
 * 注意: SplitRepository.load は割り勘シートや参加者が無いとき既定値を保存する(読み取りでも少し書き込む)。
 */
@Singleton
class MealRecordRepository @Inject constructor(
    private val receipts: ReceiptRepository,
    private val splits: SplitRepository,
    private val expenseDao: ExpenseDao,
    private val expenseGroupDao: ExpenseGroupDao,
) {
    /** 月(yyyy-MM)の食べた記録。支払いの日時の新しい順。下書きの支払いも含む。 */
    fun observeMonth(yearMonth: String): Flow<List<MealPayment>> =
        receipts.observeReceipts().map { all ->
            all.filter { it.purchasedAt.take(7) == yearMonth }
                .sortedWith(compareByDescending<Receipt> { it.purchasedAt }.thenByDescending { it.id })
                .map { mealOf(it) }
        }

    private suspend fun mealOf(receipt: Receipt): MealPayment {
        val items = receipts.getItems(receipt.id)
        val data = splits.load(receipt.id)
        val build = data.toBuild(receipt.totalAmount, items)
        val itemNames = items.associate { it.id to it.name }
        val groupItemNames = data.groups.associate { g -> g.group.id to g.itemIds.mapNotNull { itemNames[it] } }
        val draft = receipt.status == ReceiptStatus.DRAFT
        val meal = MealRecord.ofPayment(
            paymentId = receipt.id,
            at = receipt.purchasedAt,
            storeName = receipt.storeName,
            draft = draft,
            input = build.input,
            groupItemNames = groupItemNames,
            selfId = build.selfParticipantId,
        )
        val link = PaymentLink.resolve(
            expenseGroupDao.getByReceiptId(receipt.id),
            expenseDao.getByReceiptId(receipt.id),
        )
        return MealPayment(
            paymentId = receipt.id,
            at = receipt.purchasedAt,
            storeName = receipt.storeName,
            draft = draft,
            meal = meal,
            link = link,
        )
    }
}
