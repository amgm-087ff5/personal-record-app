package com.amgm.personallog.domain.split

/**
 * 品目1個ぶん(receipt_items の1行)の税込金額。家計簿の行は品目ごとなので、割り勘の結果を品目へ配り直すのに使う。
 * groupId はその品目が入っているまとまり(無ければ null)。category は家計簿のカテゴリ。
 */
data class ItemYen(val itemId: Long, val groupId: Long?, val yen: Long, val category: String)

/**
 * 割り勘の結果を家計簿(品目ごとの行)とカテゴリ内訳に変換する(純Kotlin)。
 * 実装はタスク B1。
 */
object SplitLedger {
    /**
     * その人(参加者 id)の各品目の金額。合計は result.totalOf(personId) と必ず一致する。
     * - groupId 付きの行は、そのまとまりの品目へ品目の金額の比で配る。
     * - groupId=null の行(残差・全体方式の調整・仮計上)は、全品目へ品目の金額の比で配る。
     * - 0円の品目は結果に含めない。items が空なら空の Map。
     */
    fun yenPerItem(result: SplitResult, personId: Long, items: List<ItemYen>): Map<Long, Long> = TODO("B1")

    /** その人のカテゴリ別の金額(金額の大きい順)。画面の「品目の金額の比で按分」の表示に使う。合計は totalOf と一致。 */
    fun categoryBreakdown(result: SplitResult, personId: Long, items: List<ItemYen>): List<Pair<String, Int>> = TODO("B1")
}
