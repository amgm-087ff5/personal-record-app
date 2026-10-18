package com.amgm.personallog.domain.split

/**
 * 円を重みに比例して人に配る(割り勘の端数処理)。純Kotlin。
 *
 * 規則(利用者の決定 2026-10-18):
 *  - s = 符号(amount)、a = |amount|、W = Σw。各人 base = a*w/W(切り捨て)、rem = a*w % W。r = a − Σbase。
 *  - 残り r 円を、w>0 の人に1円ずつ配る。
 *    - amount ≥ 0: 支払者でない人が先(支払者は最後)。同じ区分の中は rem の大きい順→order の小さい順→personId。
 *    - amount < 0(値引き): 支払者が先(支払者が損をしない向き)。同じ区分の中は同じ順。
 *  - 結果 = s*(base + 配った1円)。
 */
object YenAllocator {
    /**
     * amount 円を weights(人, 重み≥0)で配る。結果の合計は必ず amount。重み0の人は0円。
     * Σ重み=0 のときは IllegalArgumentException。
     */
    fun allocate(
        amount: Int,
        weights: List<Pair<Long, Int>>,
        payerIds: Set<Long>,
        order: Map<Long, Int>,
    ): Map<Long, Int> {
        require(weights.all { it.second >= 0 }) { "重みは0以上にする" }
        require(weights.map { it.first }.distinct().size == weights.size) { "同じ人の重みが重複している" }
        val totalWeight = weights.sumOf { it.second.toLong() }
        require(totalWeight > 0L) { "重みの合計が0なので配れない" }

        val sign = if (amount < 0) -1L else 1L
        val a = Math.abs(amount.toLong())
        val base = HashMap<Long, Long>()
        val rem = HashMap<Long, Long>()
        for ((id, w) in weights) {
            val product = a * w
            base[id] = product / totalWeight
            rem[id] = product % totalWeight
        }
        val residual = (a - base.values.sum()).toInt()

        // 端数を配る順番。amount ≥ 0 は支払者を後ろ(rank 1)、amount < 0 は支払者を前(rank 0)にする。
        val payerLast = amount >= 0
        val candidates = weights.filter { it.second > 0 }.map { it.first }
            .sortedWith(
                compareBy<Long>(
                    { if ((it in payerIds) == payerLast) 1 else 0 },
                    { -rem.getValue(it) },
                    { order[it] ?: Int.MAX_VALUE },
                    { it },
                ),
            )
        require(residual <= candidates.size) { "端数の配り先が足りない" }
        val extra = candidates.take(residual).toSet()

        val result = LinkedHashMap<Long, Int>()
        for ((id, _) in weights) {
            val share = base.getValue(id) + if (id in extra) 1L else 0L
            result[id] = (sign * share).toInt()
        }
        return result
    }
}
