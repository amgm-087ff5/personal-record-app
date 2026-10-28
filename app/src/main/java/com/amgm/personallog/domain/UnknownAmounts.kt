package com.amgm.personallog.domain

import com.amgm.personallog.data.model.ReceiptItem

/**
 * 金額が「不明」の品目の計算(純Kotlin)。支払いの品目(receipt_items)・支出の品目(expenses)の両方で使う。
 *
 * 規則(利用者の決定 2026-10-28):
 *  - 計算の正は合計額。品目はメモに近い内訳。
 *  - 不明の品目は、分かっている品目の合計に入れない。不明の金額 = 合計 − 分かっている品目の合計。
 *  - 不明の品目が複数あるときは、差を均等に分ける。割り切れない1円は前の品目から1円ずつ足す(合計は必ず差と一致)。
 *  - 差が負(分かっている品目だけで合計を超える)のときは、不明の品目は0円とし、不一致のエラーのまま。
 *  - 不明の品目があり差が0以上なら、不一致のエラーにしない。
 */
object UnknownAmounts {
    /** amount を count 個に均等に分ける。端数は前から1円ずつ。count が0なら空。amount が負なら0として扱う。 */
    fun splitEqually(amount: Long, count: Int): List<Long> {
        if (count <= 0) return emptyList()
        val a = amount.coerceAtLeast(0L)
        val base = a / count
        val rest = (a % count).toInt()
        return List(count) { i -> base + if (i < rest) 1L else 0L }
    }

    /** 分かっている品目(不明でない)の税込の支払総額(円、切り捨て)。 */
    fun knownPaidYen(items: List<ReceiptItem>): Long = TaxCalc.paidYen(items.filterNot { it.unknownAmount })

    /** 不明の品目に配る金額の合計。合計が無い(下書き)・不明の品目が無いときは0。差が負なら0。 */
    fun unknownYen(items: List<ReceiptItem>, total: Int?): Long {
        if (total == null || items.none { it.unknownAmount }) return 0L
        return (total.toLong() - knownPaidYen(items)).coerceAtLeast(0L)
    }

    /**
     * 品目ごとの税込支払額(円の整数。items と同じ並び)。
     * 分かっている品目は TaxCalc.paidYenPerItem(分かっている品目だけで累積して切り捨て)、不明の品目は unknownYen を均等に分けた値。
     */
    fun paidYenPerItem(items: List<ReceiptItem>, total: Int?): List<Long> {
        if (items.none { it.unknownAmount }) return TaxCalc.paidYenPerItem(items)
        val known = TaxCalc.paidYenPerItem(items.filterNot { it.unknownAmount }).iterator()
        val unknown = splitEqually(unknownYen(items, total), items.count { it.unknownAmount }).iterator()
        return items.map { if (it.unknownAmount) unknown.next() else known.next() }
    }

    /** 品目の合計(不明の品目は求めた金額で数える)。合計があり不明の差が0以上なら、合計と一致する。 */
    fun itemsYen(items: List<ReceiptItem>, total: Int?): Long = paidYenPerItem(items, total).sum()

    /**
     * 合計と品目の不一致(合計 − 分かっている品目の合計)。エラーでなければ null。
     *  - 不明の品目がある: 差が負のときだけエラー。
     *  - 不明の品目が無い: 差が0でなければエラー。
     * total が null(未入力)なら null(別の警告で扱う)。
     */
    fun mismatch(total: Long?, knownSum: Long, hasUnknown: Boolean): Long? {
        if (total == null) return null
        val diff = total - knownSum
        return when {
            hasUnknown -> diff.takeIf { it < 0L }
            else -> diff.takeIf { it != 0L }
        }
    }

    /** 支払いの品目と合計の不一致(mismatch を ReceiptItem に当てたもの)。 */
    fun mismatch(total: Int?, items: List<ReceiptItem>): Long? =
        mismatch(total?.toLong(), knownPaidYen(items), items.any { it.unknownAmount })

    // ---- 保存済みの支払いの品目(ReceiptItem)に当てたもの。画面の入力中の品目は ReceiptDetailViewModel が ItemForm で行う

    /**
     * 保存済みの支払いの品目のうち「未入力」のもの: 金額が0で、不明でない。
     * 画面の空欄は保存すると0円になるため、0円の品目を未入力と見なす(0円の品目を本当に0円で持ちたいときは不明にしない)。
     */
    fun isBlank(item: ReceiptItem): Boolean = item.amount == 0 && !item.unknownAmount

    /** 「未入力を全て不明にする」(保存済みの品目)。 */
    fun markBlankAsUnknown(items: List<ReceiptItem>): List<ReceiptItem> =
        UnknownItems.markBlankAsUnknown(items, { isBlank(it) }) { it.copy(unknownAmount = true) }

    /** 「差額を不明の品目として足す」で足す金額(保存済みの品目と合計)。出さないときは null。 */
    fun differenceForUnknownItem(total: Int?, items: List<ReceiptItem>): Long? =
        UnknownItems.differenceForUnknownItem(
            total = total?.toLong(),
            knownSum = knownPaidYen(items),
            hasBlank = items.any { isBlank(it) },
            hasUnknown = items.any { it.unknownAmount },
        )

    /**
     * 差額を不明の品目として足す(保存済みの品目)。足す品目は品名が空・未分類・金額0(不明の品目の金額は使わない)。
     * 足さないときは items のまま。
     */
    fun withDifferenceItem(receiptId: Long, total: Int?, items: List<ReceiptItem>): List<ReceiptItem> =
        UnknownItems.withDifferenceItem(
            items = items,
            total = total?.toLong(),
            knownSum = knownPaidYen(items),
            isBlank = { isBlank(it) },
            isUnknown = { it.unknownAmount },
            newUnknownItem = { ReceiptItem(receiptId = receiptId, name = "", amount = 0, unknownAmount = true) },
        )
}

/**
 * 「未入力を全て不明にする」「差額を不明の品目として足す」の判定(純Kotlin)。
 * 支払いの画面(入力中の品目)と支出の画面(品目の行)のどちらからも使えるよう、品目の型は呼び出し側が決める。
 */
object UnknownItems {
    /** 金額が未入力(isBlank)で、まだ不明でない品目を、すべて不明にする(toUnknown)。他の品目と並びは変えない。 */
    fun <T> markBlankAsUnknown(items: List<T>, isBlank: (T) -> Boolean, toUnknown: (T) -> T): List<T> =
        items.map { if (isBlank(it)) toUnknown(it) else it }

    /** 「未入力を全て不明にする」を出せるか: 金額が未入力の品目が1つ以上ある。 */
    fun <T> canMarkBlankAsUnknown(items: List<T>, isBlank: (T) -> Boolean): Boolean = items.any(isBlank)

    /**
     * 「差額を不明の品目として足す」で足す金額。出さないときは null。
     * 出すのは、未入力の品目が無く・不明の品目も無く・合計 − 分かっている品目の合計 が正のとき
     * (差が負のときは不明の品目を足しても合わないので出さない。不明の品目が既にあれば差はそこに入るので不一致にならない)。
     */
    fun differenceForUnknownItem(total: Long?, knownSum: Long, hasBlank: Boolean, hasUnknown: Boolean): Long? {
        if (total == null || hasBlank || hasUnknown) return null
        return (total - knownSum).takeIf { it > 0L }
    }

    /** 差額を不明の品目として末尾に足す。足さないとき(differenceForUnknownItem が null)は items のまま。 */
    fun <T> withDifferenceItem(
        items: List<T>,
        total: Long?,
        knownSum: Long,
        isBlank: (T) -> Boolean,
        isUnknown: (T) -> Boolean,
        newUnknownItem: (difference: Long) -> T,
    ): List<T> {
        val diff = differenceForUnknownItem(total, knownSum, items.any(isBlank), items.any(isUnknown)) ?: return items
        return items + newUnknownItem(diff)
    }
}
