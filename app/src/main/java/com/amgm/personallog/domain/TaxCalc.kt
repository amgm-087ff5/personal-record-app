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
 *  - 自分の払う分は金額(税込・円)で入力する。未入力は全額自分。
 */
object TaxCalc {
    const val UNITS_PER_YEN = 10_000L
    const val DEFAULT_TAX_RATE = 10
    const val MIN_TAX_RATE = 0
    const val MAX_TAX_RATE = 100

    /** 税率は 0〜100 の整数。範囲外・不明は既定の10%。 */
    fun normalizeTaxRate(rate: Int?): Int =
        if (rate != null && rate in MIN_TAX_RATE..MAX_TAX_RATE) rate else DEFAULT_TAX_RATE

    /** 税込での支払額(単位)。税抜表示なら 金額×(100+税率)/100 円。 */
    fun paidUnits(item: ReceiptItem): Long {
        val amount = item.amount.toLong()
        return if (item.priceIncludesTax) {
            amount * UNITS_PER_YEN
        } else {
            amount * (100L + normalizeTaxRate(item.taxRate)) * 100L
        }
    }

    /** 自分の払う分(単位)。ownAmount(円)があればそれ、なければ支払額の全額。 */
    fun ownUnits(item: ReceiptItem): Long =
        item.ownAmount?.let { it.toLong() * UNITS_PER_YEN } ?: paidUnits(item)

    /** 単位を円に変換して小数点以下を切り捨てる(負の値も床関数で切り捨て)。 */
    fun floorYen(units: Long): Long = Math.floorDiv(units, UNITS_PER_YEN)

    /** 税込の支払総額(円、切り捨て)。 */
    fun paidYen(items: List<ReceiptItem>): Long = floorYen(items.sumOf { paidUnits(it) })

    /** 自分の払う分の合計(円、切り捨て)。 */
    fun ownYen(items: List<ReceiptItem>): Long = floorYen(items.sumOf { ownUnits(it) })

    /**
     * 品目ごとの自分の払う分(円の整数)。累積して切り捨てた差分にするので、合計は [ownYen] と一致する。
     */
    fun ownYenPerItem(items: List<ReceiptItem>): List<Long> {
        var cumulative = 0L
        var previousFloor = 0L
        return items.map { item ->
            cumulative += ownUnits(item)
            val floor = floorYen(cumulative)
            val part = floor - previousFloor
            previousFloor = floor
            part
        }
    }

    /** 品目の税込価格の表示用(小数あり)。例: "129.6" / "120" */
    fun paidYenText(item: ReceiptItem): String = unitsToText(paidUnits(item))

    /** 品目の自分の払う分の表示用(小数あり)。 */
    fun ownYenText(item: ReceiptItem): String = unitsToText(ownUnits(item))

    private fun unitsToText(units: Long): String =
        BigDecimal.valueOf(units, 4).stripTrailingZeros().toPlainString()
}
