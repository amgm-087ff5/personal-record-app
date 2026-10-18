package com.amgm.personallog.domain

import com.amgm.personallog.domain.split.ItemYen
import com.amgm.personallog.domain.split.SplitLedger
import com.amgm.personallog.domain.split.SplitResult
import com.amgm.personallog.domain.split.SplitWarning

/**
 * 割り勘の結果から求めた「自分の分」。家計簿の行は、この金額で作る(割り勘がある支払いだけ使う)。
 * - perItem: 品目の id(receiptItemId) → 自分が払う円。0円の品目は含まない。
 * - total: 自分の合計。perItem の合計と一致する。
 */
data class SelfShare(val perItem: Map<Long, Long>, val total: Long) {
    companion object {
        /** 割り勘の計算結果から自分の分を作る(純Kotlin)。 */
        fun of(result: SplitResult, selfParticipantId: Long, itemYen: List<ItemYen>): SelfShare =
            SelfShare(
                perItem = SplitLedger.yenPerItem(result, selfParticipantId, itemYen),
                total = result.totalOf(selfParticipantId).toLong(),
            )
    }
}

/** 割り勘の警告を、保存用のコード("W1,W3" の形)にする。種類ごとに1つ、W1〜W5の順。 */
object SplitWarningCodes {
    fun of(warnings: List<SplitWarning>): String =
        warnings.map { code(it) }.distinct().sorted().joinToString(",")

    private fun code(w: SplitWarning): String = when (w) {
        is SplitWarning.GroupAmountMismatch -> "W1"
        is SplitWarning.WholeAmountMismatch -> "W2"
        is SplitWarning.ItemsTotalMismatch -> "W3"
        is SplitWarning.PayersMismatch -> "W4"
        is SplitWarning.InvalidShares -> "W5"
    }
}
