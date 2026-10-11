package com.amgm.personallog.domain

import com.amgm.personallog.data.model.ReceiptItem
import java.math.BigDecimal

/**
 * 税と負担の計算(純Kotlin、JVMでテスト可能)。
 *
 * 金額は内部で「1/10000円」を単位とする整数(units)で持つ。税込価格(例: 120円×1.08=129.6円)や
 * 負担割合(例: 33%)の小数を、浮動小数点の誤差なしで足し合わせるため。
 *
 * 丸めの規則(利用者の決定):
 *  - 品目ごとの税込価格・負担額は小数点以下を保持する。
 *  - 合計(支払総額・自分の分・貰う分)は小数点以下を切り捨てる(法律上の端数処理と同様)。
 *  - 家計簿の行(円の整数)は、品目を順に累積して切り捨てた差分で作るため、行の合計は切り捨て後の合計と一致する。
 */
object TaxCalc {
    /** 1円あたりの単位数。 */
    const val UNITS_PER_YEN = 10_000L
    const val DEFAULT_TAX_RATE = 10

    /** 税率は 8 か 10 のみ扱う。それ以外(不明含む)は既定の10%。 */
    fun normalizeTaxRate(rate: Int?): Int = if (rate == 8 || rate == 10) rate else DEFAULT_TAX_RATE

    fun normalizeOwnPercent(percent: Int): Int = percent.coerceIn(0, 100)

    /** 税込での支払額(単位)。税抜表示なら 金額×(100+税率)/100 を正確に表す。 */
    fun paidUnits(item: ReceiptItem): Long {
        val amount = item.amount.toLong()
        return if (item.priceIncludesTax) {
            amount * UNITS_PER_YEN
        } else {
            // amount * (100 + rate) / 100 円 = amount * (100 + rate) * 100 単位
            amount * (100L + normalizeTaxRate(item.taxRate)) * 100L
        }
    }

    /** 自分の負担額(単位)。税込支払額 × 負担割合。割り切れるよう 100 の倍数で計算している。 */
    fun ownUnits(item: ReceiptItem): Long =
        paidUnits(item) * normalizeOwnPercent(item.ownPercent) / 100L

    /** 単位を円に変換して小数点以下を切り捨てる(負の値も床関数で切り捨て)。 */
    fun floorYen(units: Long): Long = Math.floorDiv(units, UNITS_PER_YEN)

    /** 税込の支払総額(円、切り捨て)。 */
    fun paidYen(items: List<ReceiptItem>): Long = floorYen(items.sumOf { paidUnits(it) })

    /** 自分の分の合計(円、切り捨て)。 */
    fun ownYen(items: List<ReceiptItem>): Long = floorYen(items.sumOf { ownUnits(it) })

    /**
     * 品目ごとの自分の分(円の整数)。累積して切り捨てた差分にするので、合計は [ownYen] と一致する。
     * 0円の行も含めて品目と同じ順序・同じ個数で返す。
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

    /** 品目の自分の分の表示用(小数あり)。 */
    fun ownYenText(item: ReceiptItem): String = unitsToText(ownUnits(item))

    private fun unitsToText(units: Long): String =
        BigDecimal.valueOf(units, 4).stripTrailingZeros().toPlainString()
}
