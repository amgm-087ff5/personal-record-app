package com.amgm.personallog.domain.split

/**
 * 品目1個ぶん(receipt_items の1行)の税込金額。家計簿の行は品目ごとなので、割り勘の結果を品目へ配り直すのに使う。
 * groupId はその品目が入っているまとまり(無ければ null)。category は家計簿のカテゴリ。
 */
data class ItemYen(val itemId: Long, val groupId: Long?, val yen: Long, val category: String)

/**
 * 割り勘の結果を家計簿(品目ごとの行)とカテゴリ内訳に変換する(純Kotlin)。
 */
object SplitLedger {
    /**
     * その人(参加者 id)の各品目の金額。合計は result.totalOf(personId) と必ず一致する。
     * - groupId 付きの行は、そのまとまりの品目へ品目の金額の比で配る。
     * - groupId=null の行(残差・全体方式の調整・仮計上)は、全品目へ品目の金額の比で配る。
     * - 0円の品目は結果に含めない。items が空なら空の Map。
     * 品目の金額は負(値引き)のものは重み0とする。重みが全部0、または該当の品目が無いときは、
     * その行の全額を(まとまりの最初の品目、無ければ全品目の最初の品目)に入れる。
     */
    fun yenPerItem(result: SplitResult, personId: Long, items: List<ItemYen>): Map<Long, Long> {
        if (items.isEmpty()) return emptyMap()
        val order = items.withIndex().associate { it.value.itemId to it.index }
        val sums = HashMap<Long, Long>()
        for (line in result.lines) {
            if (line.personId != personId) continue
            val targets = if (line.groupId == null) items else items.filter { it.groupId == line.groupId }
            val weighted = targets.filter { it.yen > 0L }
            val dest: Map<Long, Int> = if (weighted.isEmpty()) {
                mapOf((targets.firstOrNull() ?: items.first()).itemId to line.amount)
            } else {
                YenAllocator.allocate(
                    line.amount,
                    weighted.map { it.itemId to it.yen.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
                    emptySet(),
                    order,
                )
            }
            for ((itemId, x) in dest) sums[itemId] = (sums[itemId] ?: 0L) + x
        }
        val out = LinkedHashMap<Long, Long>()
        for (item in items) {
            val y = sums[item.itemId] ?: continue
            if (y != 0L) out[item.itemId] = y
        }
        return out
    }

    /**
     * その人のカテゴリ別の金額(金額の大きい順)。画面の「品目の金額の比で按分」の表示に使う。合計は totalOf と一致。
     * items が空のときは、合計が0でなければ [("未分類", 合計)] とする。
     */
    fun categoryBreakdown(result: SplitResult, personId: Long, items: List<ItemYen>): List<Pair<String, Int>> {
        val total = result.totalOf(personId)
        if (items.isEmpty()) return if (total != 0) listOf("未分類" to total) else emptyList()
        val per = yenPerItem(result, personId, items)
        val byCategory = LinkedHashMap<String, Long>()
        for (item in items) {
            val y = per[item.itemId] ?: continue
            byCategory[item.category] = (byCategory[item.category] ?: 0L) + y
        }
        return byCategory
            .filterValues { it != 0L }
            .map { it.key to it.value.toInt() }
            .sortedByDescending { it.second }
    }
}
