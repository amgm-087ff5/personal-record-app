package com.amgm.personallog.receipt

import com.amgm.personallog.data.model.PaymentStatus
import com.amgm.personallog.data.model.TotalSource

/** 合計の初期値の判定結果。totalAmount が null なら空欄に戻す。 */
data class TotalSourceDecision(val totalAmount: Int?, val totalSource: String)

/**
 * DB v8 への移行で、既存の支払いの totalSource(と、空欄に戻す合計)を rawJson から決める(純Kotlin)。
 *
 * rawJson に LLM が読んだ合計(total)があれば、それと保存値を比べる。
 * 以前は合計が読めないと品目の合計で自動補完していたので、total が無い確定済みは「品目から」とみなす。
 */
object TotalSourceInit {
    fun decide(status: String, storedTotal: Int, rawJson: String): TotalSourceDecision {
        val parsed = if (rawJson.isBlank()) null else ReceiptParser.parse(rawJson)
        val readable = parsed as? ParseResult.Success
        if (readable != null) {
            val printed = readable.parsed.total
            return when {
                printed != null && printed == storedTotal -> TotalSourceDecision(storedTotal, TotalSource.PRINTED)
                // 確定済みで印字と違う値は、手入力か旧ボタン(品目の合計)か区別できないので unknown
                printed != null && status == PaymentStatus.CONFIRMED -> TotalSourceDecision(storedTotal, TotalSource.UNKNOWN)
                printed != null -> TotalSourceDecision(storedTotal, TotalSource.MANUAL)
                // 読み取りで合計が無かった: 確定済みは以前の自動補完
                status == PaymentStatus.CONFIRMED -> TotalSourceDecision(storedTotal, TotalSource.ITEMS)
                // 下書き: 保存値が旧方式の補完値(品目の amount の単純合計)と同じなら空欄に戻す。違えば手入力なので残す
                status == PaymentStatus.DRAFT -> {
                    val legacyFill = readable.parsed.items.sumOf { it.amount.toLong() }
                    if (legacyFill == storedTotal.toLong()) TotalSourceDecision(null, TotalSource.UNKNOWN)
                    else TotalSourceDecision(storedTotal, TotalSource.MANUAL)
                }
                else -> TotalSourceDecision(storedTotal, TotalSource.UNKNOWN)
            }
        }
        // rawJson が空、または解釈できない(読み取り失敗)。下書きで保存値が0なら読み取り失敗の仮の値
        if (status == PaymentStatus.DRAFT && storedTotal == 0) {
            return TotalSourceDecision(null, TotalSource.UNKNOWN)
        }
        return TotalSourceDecision(storedTotal, TotalSource.UNKNOWN)
    }
}
