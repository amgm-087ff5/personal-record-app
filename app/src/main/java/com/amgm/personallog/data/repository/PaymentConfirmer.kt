package com.amgm.personallog.data.repository

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.domain.TaxCalc
import com.amgm.personallog.receipt.ReceiptDedup
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 支払いの確定の結果。確定の判定は [PaymentConfirmer] が行い、各画面は結果に応じた確認を出す。
 */
sealed interface ConfirmOutcome {
    /** 確定した(支払いを確定し、家計簿の行を作った)。 */
    data object Confirmed : ConfirmOutcome

    /** 同じ内容の確定済みの支払いがある。「追加する」か「重複ではない」を選ばせる。 */
    data class NeedsDuplicateDecision(val duplicates: List<Receipt>) : ConfirmOutcome

    /** 合計が未入力。確定には合計が要る。itemsSum は品目の計算合計(合計に入れられる値)。 */
    data class MissingTotal(val itemsSum: Long) : ConfirmOutcome

    /** 再確定で家計簿の行が作り直される、または家計簿で直した内容が消える。確認のあと rebuildAccepted で続ける。 */
    data class NeedsRebuildDecision(val impact: ConfirmImpact) : ConfirmOutcome

    /** 失敗(メッセージは画面に出す)。 */
    data class Error(val message: String) : ConfirmOutcome
}

/**
 * 支払いの確定(下書き → 確定)。支払いの画面と支出の画面の両方から使う。
 * 判定は次の順:合計の有無 → 重複の候補 → 家計簿の作り直し・消えるもの → 保存と確定(1つのトランザクション)。
 * 人数の変更(割り勘の参加者の確認)は、保存の前に各画面が行う(ここでは扱わない)。
 */
@Singleton
class PaymentConfirmer @Inject constructor(
    private val receipts: ReceiptRepository,
) {
    /**
     * [receipt] と [items] を確定する。[checkDuplicate] が true なら重複の候補を調べる。
     * [rebuildAccepted] が true なら、家計簿の作り直しの確認は済んでいるものとして続ける。
     */
    suspend fun confirm(
        receipt: Receipt,
        items: List<ReceiptItem>,
        checkDuplicate: Boolean,
        rebuildAccepted: Boolean = false,
    ): ConfirmOutcome {
        if (receipt.totalAmount == null) return ConfirmOutcome.MissingTotal(itemsSumOf(items))
        return try {
            if (checkDuplicate) {
                val notDup = ReceiptDedup.notDuplicateSet(receipts.notDuplicates())
                val confirmed = receipts.allReceipts().filter { it.status == ReceiptStatus.CONFIRMED }
                val dups = ReceiptDedup.sameContent(receipt, confirmed, notDup)
                if (dups.isNotEmpty()) return ConfirmOutcome.NeedsDuplicateDecision(dups)
            }
            if (!rebuildAccepted) {
                val impact = receipts.confirmImpact(receipt, items)
                if (impact.needsWarning) return ConfirmOutcome.NeedsRebuildDecision(impact)
            }
            // 保存と確定は1つのトランザクション(途中で失敗しても保存だけ残らない)
            receipts.saveAndConfirm(receipt, items, perItem = true)
            ConfirmOutcome.Confirmed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ConfirmOutcome.Error("確定に失敗しました: ${e.message.orEmpty()}")
        }
    }

    /** 品目の計算合計(税込・金額不明の品目は含めない)。合計に入れる値。 */
    fun itemsSumOf(items: List<ReceiptItem>): Long = TaxCalc.paidYen(items.filterNot { it.unknownAmount })
}
