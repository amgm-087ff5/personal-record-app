package com.amgm.personallog.data.repository

import com.amgm.personallog.data.db.ExpenseDao
import com.amgm.personallog.data.db.ExpenseGroupDao
import com.amgm.personallog.data.db.SplitDao
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitParticipant
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
 * 読み取りだけで、DB には書き込まない(割り勘は SplitRepository.peek で読む。load は使わない)。
 */
@Singleton
class MealRecordRepository @Inject constructor(
    private val receipts: ReceiptRepository,
    private val splits: SplitRepository,
    private val splitDao: SplitDao,
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
        // 割り勘シートが無い支払いは、自分1人の既定の割り勘として扱う(保存はしない)
        val sheet = splits.peek(receipt.id) ?: selfOnlySheet(receipt.id)
        val withGroups = withItemGroups(sheet, items)
        val build = withGroups.toBuild(receipt.totalAmount, items)
        val itemNames = items.associate { it.id to it.name }
        val groupItemNames = withGroups.groups.associate { g -> g.group.id to g.itemIds.mapNotNull { itemNames[it] } }
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

    /**
     * 割り勘シートが無いときの既定(保存しない): 参加者は自分1人、割当なし。自分が未登録なら参加者は無い。
     * 自分の参加者 id は 0(未保存の扱い)。
     */
    private suspend fun selfOnlySheet(receiptId: Long): SplitSheetData {
        val self = splitDao.getSelf()
        return SplitSheetData(
            receiptId = receiptId,
            mode = SplitMode.SELF,
            wholeKind = ShareKind.AMOUNT,
            participants = if (self != null) listOf(SplitParticipant(id = 0, receiptId = receiptId, personId = self.id, sortOrder = 0)) else emptyList(),
            groups = emptyList(),
            itemShares = emptyList(),
            wholeShares = emptyList(),
            labelAmounts = emptyList(),
            persons = if (self != null) mapOf(0L to self) else emptyMap(),
        )
    }

    /**
     * まとまりに入っていない品目は、品目1つを1まとまりとして足す(まとまりの無い古い支払い用)。
     * 仮のまとまり id は品目 id の符号を反転したもの(保存されたまとまりの id と衝突しない)。割当は無い。
     */
    private fun withItemGroups(sheet: SplitSheetData, items: List<ReceiptItem>): SplitSheetData {
        val covered = sheet.groups.flatMap { it.itemIds }.toSet()
        val extra = items.filter { it.id !in covered }.map { item ->
            GroupWithItems(
                group = ShareGroup(id = -item.id, receiptId = sheet.receiptId),
                itemIds = listOf(item.id),
            )
        }
        return if (extra.isEmpty()) sheet else sheet.copy(groups = sheet.groups + extra)
    }
}
