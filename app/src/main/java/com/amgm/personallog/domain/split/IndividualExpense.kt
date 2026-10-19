package com.amgm.personallog.domain.split

import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode

// 個別支出(純Kotlin。Room・Android に依存しない)。
// 全体支出(割り勘シートの全員分の結果)のうち、1人ぶんを取り出す。
// 計算は既存の SplitCalculator の結果をそのまま使い、仕様は変えない。

/**
 * 個別支出の1行(まとまり1つぶん)。
 * - groupId=null は残差・調整の行(まとまりに属さない)。
 * - name は呼び出し側が付ける表示名(IndividualExpenses.of の groupNames。無ければ「品目」)。
 * - share は割合の表示(例: 「2/7個」「1/3」「全額」「500円」「残りを均等」)。食べた分の割当に基づく。
 * - eaten は食べた分の金額、burden は負担の金額(選んだ方式で計算した結果)。
 * - unset は割当が無い(未設定)。isShared は共通費か(未設定の扱いが変わる)。
 */
data class IndividualLine(
    val groupId: Long?,
    val name: String,
    val share: String,
    val eaten: Int,
    val burden: Int,
    val unset: Boolean,
    val isShared: Boolean,
)

/**
 * 1人ぶんの個別支出。
 * - eatenTotal: 食べた分の合計(ITEM の計算でその人の行の合計)。
 * - burdenTotal: 負担の合計(選んだ方式の計算でその人の行の合計)。
 * - difference: 負担 − 食べた分。マイナスは「多めに払ってもらった」、プラスは「多めに払った」。ITEM なら 0。
 * - wholeShare: 全体で割る(WHOLE)のときの、支払い全体に対するこの人の割合の表示。ITEM なら null。
 */
data class IndividualExpense(
    val personId: Long,
    val lines: List<IndividualLine>,
    val eatenTotal: Int,
    val burdenTotal: Int,
    val wholeShare: String?,
) {
    val difference: Int get() = burdenTotal - eatenTotal
}

object IndividualExpenses {
    /** まとまりの名前が無いときの表示名。 */
    const val UNKNOWN_NAME = "品目"

    /** 残差・調整の行の表示名。 */
    const val ADJUSTMENT_NAME = "残差・調整"

    /**
     * 人(personId = SplitPerson.id。割り勘の参加者の id)の個別支出。
     * 食べた分は SplitCalculator.calculate(input.copy(mode = ITEM))、負担は input の方式で計算する。
     * 関わりの無いまとまり(食べた分も負担も 0)は行に出さない。残差・調整の行は、0 でなければ最後に付ける。
     */
    fun of(input: SplitInput, personId: Long, groupNames: Map<Long, String> = emptyMap()): IndividualExpense {
        val eatenResult = SplitCalculator.calculate(input.copy(mode = SplitMode.ITEM))
        val burdenResult = SplitCalculator.calculate(input)
        val peopleIds = input.people.map { it.id }.toSet()
        val lines = mutableListOf<IndividualLine>()

        for (g in input.groups) {
            val eaten = eatenResult.lines.filter { it.personId == personId && it.groupId == g.id }.sumOf { it.amount }
            val burden = burdenResult.lines.filter { it.personId == personId && it.groupId == g.id }.sumOf { it.amount }
            if (eaten == 0 && burden == 0) continue
            val rows = validRows(input.itemShares[g.id].orEmpty(), peopleIds)
            lines += IndividualLine(
                groupId = g.id,
                name = groupNames[g.id] ?: UNKNOWN_NAME,
                share = itemShareText(g, rows, personId, peopleIds.size),
                eaten = eaten,
                burden = burden,
                unset = rows.isEmpty(),
                isShared = g.isShared,
            )
        }

        val eatenAdjustment = eatenResult.lines.filter { it.personId == personId && it.groupId == null }.sumOf { it.amount }
        val burdenAdjustment = burdenResult.lines.filter { it.personId == personId && it.groupId == null }.sumOf { it.amount }
        if (eatenAdjustment != 0 || burdenAdjustment != 0) {
            lines += IndividualLine(
                groupId = null,
                name = ADJUSTMENT_NAME,
                share = "残差・調整",
                eaten = eatenAdjustment,
                burden = burdenAdjustment,
                unset = false,
                isShared = false,
            )
        }

        val wholeShare = if (input.mode == SplitMode.WHOLE) {
            wholeShareText(input, personId, peopleIds)
        } else {
            null
        }

        return IndividualExpense(
            personId = personId,
            lines = lines,
            eatenTotal = eatenResult.totalOf(personId),
            burdenTotal = burdenResult.totalOf(personId),
            wholeShare = wholeShare,
        )
    }

    /** 割当のうち、参加者(people)に居る人の行だけ。 */
    private fun validRows(rows: List<ShareInput>, peopleIds: Set<Long>): List<ShareInput> =
        rows.filter { it.personId in peopleIds }

    /**
     * まとまりの割合の表示(食べた分の割当に基づく)。
     * 割当が無いときは、共通費は参加者で均等(1/人数)、それ以外は自分だけ(全額)。
     * 対象が1人だけなら「全額」。種類ごとに、個数(M/N個)・均等(1/人数)・金額・残りを均等。
     */
    private fun itemShareText(g: SplitGroupInput, rows: List<ShareInput>, personId: Long, peopleCount: Int): String {
        if (rows.isEmpty()) return if (g.isShared) "1/${peopleCount}(共通費は均等)" else "全額(自分)"
        val ids = rows.map { it.personId }.distinct()
        if (ids.size == 1) return if (ids.single() == personId) "全額" else "0"
        val own = rows.filter { it.personId == personId }
        if (own.isEmpty()) return "0"
        return when (rows.map { it.kind }.distinct().singleOrNull()) {
            ShareKind.ASSIGN, ShareKind.EQUAL -> "1/${ids.size}"
            ShareKind.RATIO -> {
                val weight = own.firstNotNullOfOrNull { it.weight }
                if (weight == null) {
                    "残りを均等"
                } else {
                    val explicit = own.mapNotNull { it.weight }.sum()
                    val pieces = g.pieceCount ?: rows.sumOf { it.weight ?: 1 }
                    "${explicit}/${pieces}個"
                }
            }
            ShareKind.AMOUNT -> {
                val amount = own.firstNotNullOfOrNull { it.amount }
                if (amount == null) "残りを均等" else "${amount}円"
            }
            else -> "不明"
        }
    }

    /**
     * 全体で割る(WHOLE)のときの、支払い全体に対するこの人の割合の表示。
     * 重みは SplitCalculator と同じ(割合の空欄は1、金額の空欄は残りを均等)。
     */
    private fun wholeShareText(input: SplitInput, personId: Long, peopleIds: Set<Long>): String {
        if (peopleIds.size <= 1) return "全額"
        val rows = validRows(input.wholeShares, peopleIds)
        val own = rows.filter { it.personId == personId }
        return when (input.wholeKind) {
            ShareKind.AMOUNT -> {
                val amount = own.firstNotNullOfOrNull { it.amount }
                if (amount == null) "残りを均等" else "${amount}円"
            }
            ShareKind.RATIO -> {
                val weights = peopleIds.associateWith { id ->
                    val list = rows.filter { it.personId == id }
                    if (list.isEmpty()) 1 else list.sumOf { it.weight ?: 1 }
                }
                val total = weights.values.sum()
                val mine = weights[personId] ?: 0
                if (total <= 0) "0" else "${mine}/${total}"
            }
            else -> "1/${peopleIds.size}"
        }
    }
}
