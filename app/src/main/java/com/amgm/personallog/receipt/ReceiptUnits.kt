package com.amgm.personallog.receipt

import com.amgm.personallog.domain.TaxCalc

/** 1個ぶんの品目(DBの1行に対応)。 */
data class UnitRow(
    val name: String,
    val amount: Int,
    val category: String,
    val taxRate: Int,
    val priceIncludesTax: Boolean,
    val ownAmount: Int? = null,
    val memo: String = "",
    /** 割り勘の共通費かどうかの提案(LLM由来)。既定は false。 */
    val shared: Boolean = false,
)

/** 旧版(v3まで)の品目行。数量と負担割合(%)を持っていた。 */
data class LegacyRow(
    val name: String,
    val quantity: Double,
    val amount: Int,
    val category: String,
    val taxRate: Int,
    val priceIncludesTax: Boolean,
    val ownPercent: Int,
)

/** 数量を「1個=1行」に展開する(純Kotlin、JVMテスト可能)。 */
object ReceiptUnits {
    const val MAX_UNITS_PER_LINE = 99

    /** 数量が 2〜99 の整数なら個数、それ以外(小数・不明・1)は 1。 */
    fun unitCount(quantity: Double): Int {
        val n = quantity.toInt()
        return if (quantity == n.toDouble() && n in 2..MAX_UNITS_PER_LINE) n else 1
    }

    /** 金額を count 個に分ける。割り切れない端数は最後の1個に載せる(負の値も同様)。 */
    fun splitAmount(amount: Int, count: Int): List<Int> {
        if (count <= 1) return listOf(amount)
        val base = amount / count
        return List(count) { i -> if (i < count - 1) base else amount - base * (count - 1) }
    }

    /** Claude の読取結果の1行を、1個ずつの行に展開する。 */
    fun fromParsed(p: ParsedItem): List<UnitRow> {
        val rate = TaxCalc.normalizeTaxRate(p.taxRate)
        return splitAmount(p.amount, unitCount(p.quantity)).map { amt ->
            UnitRow(p.name, amt, p.category, rate, p.priceIncludesTax, shared = p.shared)
        }
    }

    /** 旧版の1行を、1個ずつの行に展開する。負担割合(%)は、1個ごとの税込価格に掛けた金額(円)に換算する。 */
    fun fromLegacy(row: LegacyRow): List<UnitRow> {
        val rate = TaxCalc.normalizeTaxRate(row.taxRate)
        val percent = row.ownPercent.coerceIn(0, 100)
        return splitAmount(row.amount, unitCount(row.quantity)).map { amt ->
            val paidUnits = if (row.priceIncludesTax) {
                amt.toLong() * TaxCalc.UNITS_PER_YEN
            } else {
                amt.toLong() * (100L + rate) * 100L
            }
            val own = if (percent == 100) {
                null
            } else {
                Math.floorDiv(paidUnits * percent, 100L * TaxCalc.UNITS_PER_YEN).toInt()
            }
            UnitRow(row.name, amt, row.category, rate, row.priceIncludesTax, own)
        }
    }
}
