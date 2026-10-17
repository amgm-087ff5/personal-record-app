package com.amgm.personallog.domain

import com.amgm.personallog.data.model.ReceiptItem
import java.math.BigDecimal

/**
 * 税と支払額の計算(純Kotlin、JVMでテスト可能)。
 *
 * 金額は内部で「1/10000円」を単位とする整数(units)で持つ。税込価格(例: 120円×1.08=129.6円)を
 * 浮動小数点の誤差なしで足し合わせるため。
 *
 * 規則(利用者の決定):
 *  - 品目ごとの税込価格は小数点以下を保持する。
 *  - 合計(支払総額・自分の払う分・貰う分)は小数点以下を切り捨てる。
 *  - 家計簿の行(円の整数)は累積して切り捨てた差分で作るので、行の合計は切り捨て後の合計と一致する。
 *  - 割り勘はレシート全体で登録する: 「全体で誰がいくら払ったか」。自分の払う分(円・税込)は
 *    レシートごとに1つの金額で持つ。品目の選択による割り勘は、その金額を埋めるための補助機能。
 */
object TaxCalc {
    const val UNITS_PER_YEN = 10_000L
    const val DEFAULT_TAX_RATE = 10
    const val MIN_TAX_RATE = 0
    const val MAX_TAX_RATE = 100

    /** 税率は 0〜100 の整数。範囲外・不明は既定の10%。 */
    fun normalizeTaxRate(rate: Int?): Int =
        if (rate != null && rate in MIN_TAX_RATE..MAX_TAX_RATE) rate else DEFAULT_TAX_RATE

    /** 1個ぶんの金額・税から、税込での支払額(単位)を求める。 */
    fun paidUnits(amount: Int, taxRate: Int, priceIncludesTax: Boolean): Long {
        val a = amount.toLong()
        return if (priceIncludesTax) {
            a * UNITS_PER_YEN
        } else {
            a * (100L + normalizeTaxRate(taxRate)) * 100L
        }
    }

    /** 税込での支払額(単位)。税抜表示なら 金額×(100+税率)/100 円。 */
    fun paidUnits(item: ReceiptItem): Long = paidUnits(item.amount, item.taxRate, item.priceIncludesTax)

    /** 単位を円に変換して小数点以下を切り捨てる(負の値も床関数で切り捨て)。 */
    fun floorYen(units: Long): Long = Math.floorDiv(units, UNITS_PER_YEN)

    /** 税込の支払総額(円、切り捨て)。 */
    fun paidYen(items: List<ReceiptItem>): Long = floorYen(items.sumOf { paidUnits(it) })

    /**
     * 品目ごとの税込支払額(円の整数)。累積して切り捨てた差分にするので、合計は [paidYen] と一致する。
     */
    fun paidYenPerItem(items: List<ReceiptItem>): List<Long> {
        var cumulative = 0L
        var previousFloor = 0L
        return items.map { item ->
            cumulative += paidUnits(item)
            val floor = floorYen(cumulative)
            val part = floor - previousFloor
            previousFloor = floor
            part
        }
    }

    /**
     * レシート全体の「自分の払う分」(円・税込)を、選ばれた品目の税込支払額の合計から求める(切り捨て)。
     * 割り勘の補助機能で使う。
     */
    fun ownYenOfPicked(items: List<ReceiptItem>, picked: List<Boolean>): Long =
        floorYen(items.indices.filter { picked[it] }.sumOf { paidUnits(items[it]) })

    /**
     * 税率ごとの税抜額と税額。税込の品目の税抜は 金額×100/(100+税率) を小数のまま持ち、税率ごとの合計で切り捨てる
     * (整数の合計に対する床除算なので誤差が出ない)。税抜の品目の税抜は印字額そのまま。
     * 税額 = 税込(単位) − 税抜(単位)。値引き(負の金額)も同じ式で、税率が既定の10%なら税額がずれうる。
     */
    fun breakdownByRate(items: List<ReceiptItem>): TaxBreakdown {
        val rates = items.map { normalizeTaxRate(it.taxRate) }.distinct().sorted()
        val lines = rates.map { rate ->
            val ofRate = items.filter { normalizeTaxRate(it.taxRate) == rate }
            val inclusiveYen = ofRate.filter { it.priceIncludesTax }.sumOf { it.amount.toLong() }
            val exclusiveYen = ofRate.filterNot { it.priceIncludesTax }.sumOf { it.amount.toLong() }
            val netYen = Math.floorDiv(inclusiveYen * 100L, 100L + rate) + exclusiveYen
            val grossUnits = ofRate.sumOf { paidUnits(it) }
            TaxRateLine(
                taxRate = rate,
                grossUnits = grossUnits,
                netYen = netYen,
                taxUnits = grossUnits - netYen * UNITS_PER_YEN,
            )
        }
        return TaxBreakdown(lines)
    }

    /** 品目の税込価格の表示用(小数あり)。例: "129.6" / "120" */
    fun paidYenText(item: ReceiptItem): String = unitsToText(paidUnits(item))

    fun unitsToText(units: Long): String =
        BigDecimal.valueOf(units, 4).stripTrailingZeros().toPlainString()
}

/** 1つの税率ぶんの内訳。netYen は税抜(円・切り捨て済み)、taxUnits は税額(単位=1/10000円)。 */
data class TaxRateLine(val taxRate: Int, val grossUnits: Long, val netYen: Long, val taxUnits: Long) {
    /** 税額(円、切り捨て)。 */
    val taxYen: Long get() = TaxCalc.floorYen(taxUnits)
}

/** 税率ごとの内訳(税率の小さい順)。 */
data class TaxBreakdown(val lines: List<TaxRateLine>) {
    /** 税抜合計(円)。税率ごとに切り捨てた税抜の合計。 */
    val netYen: Long get() = lines.sumOf { it.netYen }

    /** 消費税額の合計(円、切り捨て)。 */
    val taxYen: Long get() = TaxCalc.floorYen(lines.sumOf { it.taxUnits })
}

/** 旧形式(品目ごとの自分の分を持っていた版)から、レシート全体の自分の分を求めるための1行。 */
data class PricedLine(
    val amount: Int,
    val taxRate: Int,
    val priceIncludesTax: Boolean,
    val ownAmount: Int? = null,
)

/**
 * 旧形式の品目の自分の分から、レシート全体の自分の分(円)を求める。
 * どの行にも自分の分が無ければ null(全額自分)。ある行は自分の分、無い行は税込価格の全額を足す。
 */
fun legacyReceiptOwnYen(lines: List<PricedLine>): Int? {
    if (lines.all { it.ownAmount == null }) return null
    val units = lines.sumOf { line ->
        line.ownAmount?.let { it.toLong() * TaxCalc.UNITS_PER_YEN }
            ?: TaxCalc.paidUnits(line.amount, line.taxRate, line.priceIncludesTax)
    }
    return TaxCalc.floorYen(units).toInt()
}
