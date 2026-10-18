package com.amgm.personallog.domain.split

// 割り勘の入力・結果の型(純Kotlin。Room・Android に依存しない)。
// 方式・割当の種類の文字列は com.amgm.personallog.data.model.SplitMode / ShareKind を使う。

/** 割り勘の対象者。order は人の並び順(端数の配り順に使う)。 */
data class SplitPerson(val id: Long, val isSelf: Boolean, val order: Int)

/**
 * 品目のまとまり(割り勘の単位)。amount はその税込金額(負もあり)。
 * isShared は共通費(袋代など)。割当が無いときに参加者全員で均等に割る。
 */
data class SplitGroupInput(val id: Long, val amount: Int, val isShared: Boolean = false)

/** 割当1件。kind は ShareKind の値。weight は割合、amount は金額指定に使う。 */
data class ShareInput(val personId: Long, val kind: String, val weight: Int? = null, val amount: Int? = null)

/** 支払者と、その人が払った金額。 */
data class PayerInput(val personId: Long, val amount: Int)

/**
 * 割り勘の入力一式。
 * - total: 支払いの合計。null なら Σまとまり を合計とする。
 * - mode: SplitMode の値。未知の値は SELF と同じ扱い。
 * - people: isSelf=true がちょうど1人であること。
 * - itemShares: まとまりID → 割当(ITEM 方式)。
 * - wholeShares: 支払い全体への割当(WHOLE 方式)。
 * - payers: 空なら自分が合計を払った。
 */
data class SplitInput(
    val total: Int?,
    val mode: String,
    val people: List<SplitPerson>,
    val groups: List<SplitGroupInput>,
    val itemShares: Map<Long, List<ShareInput>> = emptyMap(),
    val wholeShares: List<ShareInput> = emptyList(),
    val payers: List<PayerInput> = emptyList(),
)

/** 1人ぶんの金額1行。groupId=null は残差・調整・仮計上の行。provisional=true は仮計上(警告の対象)。 */
data class SplitLine(val personId: Long, val groupId: Long?, val amount: Int, val provisional: Boolean = false)

/** 計算結果。lines の合計は必ず入力の合計(total)と一致する。 */
data class SplitResult(val lines: List<SplitLine>, val warnings: List<SplitWarning>) {
    /** その人の行の合計。 */
    fun totalOf(personId: Long): Int = lines.filter { it.personId == personId }.sumOf { it.amount }

    /** 全行の合計(支払いの合計と一致する)。 */
    val total: Int get() = lines.sumOf { it.amount }
}

/** 割り勘の注意。自動では配り直さず、利用者に知らせるためのもの。 */
sealed interface SplitWarning {
    /** W1: 金額指定のまとまりの合計が、まとまりの金額と合わない。 */
    data class GroupAmountMismatch(val groupId: Long, val expected: Int, val allocated: Int) : SplitWarning

    /** W2: 金額指定(全体)の合計が支払いの合計と合わない。 */
    data class WholeAmountMismatch(val expected: Int, val allocated: Int) : SplitWarning

    /** W3: 品目の合計と支払いの合計がずれている(残差 R = 合計 − 品目の合計)。 */
    data class ItemsTotalMismatch(val residual: Int) : SplitWarning

    /** W4: 支払者の合計が支払いの合計と合わない。 */
    data class PayersMismatch(val expected: Int, val paid: Int) : SplitWarning

    /** W5: 割当が解釈できない(存在しない人・種類の混在・重みの不正など)。reason は理由の短い説明。 */
    data class InvalidShares(val groupId: Long?, val reason: String) : SplitWarning
}
