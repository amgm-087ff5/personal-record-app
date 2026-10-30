package com.amgm.personallog.domain.split

import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import java.util.Locale

// 食べた記録(純Kotlin。Room・Android に依存しない)。
// 新しい表は作らず、割り勘の計算結果(食べた分 = ITEM 方式の計算)から求める。
// 「自分が何をどれだけ食べたか」だけを扱う。他の人の分は出さない。

/**
 * 食べた記録の1行。1行 = 支払いの1つのまとまりのうち、自分が食べた分。
 * - paymentId: 支払い(payments)の id。支払いに紐づかない行(後で単独で差し込む記録)は null。
 * - at: "yyyy-MM-dd" または "yyyy-MM-dd HH:mm"(支払いの日時)。
 * - name: 品名(まとまりの品目の名前。複数なら「最初の品名 ほかN品」)。
 * - quantity: 量の表示。「全部」「1/人数」「M/N個」「¥X分」のいずれか。
 * - amount: 食べた分の金額(円・税込)。
 * - storeName: 支払い先。draft: 下書きの支払いなら true。
 */
data class MealRecordRow(
    val paymentId: Long?,
    val groupId: Long?,
    val at: String,
    val name: String,
    val quantity: String,
    val amount: Int,
    val storeName: String,
    val draft: Boolean,
)

/**
 * 1つの支払いの食べた記録。rows は自分が食べた分だけ。
 * unsetGroups は「誰が食べたか未入力」のまとまりの数(割り当てが無い・解釈できないもの)。未入力は行にせず、自分が食べたことにもしない。
 * burden は自分の負担額(共通費を含む、割り勘の計算結果の自分の合計)。計算できないときは null。
 * selfPaid は自分が会計者として店に払った額。会計者の内訳が特定できないとき(会計者が2人以上)は null。
 */
data class PaymentMeal(
    val paymentId: Long?,
    val rows: List<MealRecordRow>,
    val unsetGroups: Int,
    val burden: Int? = null,
    val selfPaid: Int? = null,
)

object MealRecord {
    /** 割当の種類のうち、食べた記録の量を決められるもの。 */
    private val KNOWN_KINDS = setOf(ShareKind.ASSIGN, ShareKind.EQUAL, ShareKind.RATIO, ShareKind.AMOUNT)

    /**
     * まとまりの品名。品目が1つならその名前、複数なら「最初の品名 ほかN品」(同じ名前はまとめる)。名前が無ければ「品目」。
     */
    fun groupName(itemNames: List<String>): String {
        val names = itemNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return when (names.size) {
            0 -> "品目"
            1 -> names[0]
            else -> "${names[0]} ほか${names.size - 1}品"
        }
    }

    /**
     * 1つの支払いの食べた記録を作る。
     * - input: 割り勘の入力(SplitRepository.load(...).toBuild(...).input)。割り勘シートが無いときも、参加者は自分1人で入っている。
     * - groupItemNames: まとまりID → そこに入っている品目の品名。
     * - selfId: 自分の参加者 id。
     *
     * 自分1人の支払い(割り勘が無い・参加者が自分だけ): 共通費を除く全まとまりを「全部」として自分が食べたことにする。
     * 参加者が2人以上: 食べた分の計算(ITEM 方式)で自分の行があるまとまりだけを出す。
     *   割当が無い・解釈できないまとまりは未入力として数え、行にはしない。
     * 共通費(袋代・送料など)は食べた記録に出さない。
     */
    fun ofPayment(
        paymentId: Long?,
        at: String,
        storeName: String,
        draft: Boolean,
        input: SplitInput,
        groupItemNames: Map<Long, List<String>>,
        selfId: Long,
    ): PaymentMeal {
        val personIds = input.people.map { it.id }.toSet()
        val groups = input.groups.filter { !it.isShared }

        fun rowOf(groupId: Long, quantity: String, amount: Int) = MealRecordRow(
            paymentId = paymentId,
            groupId = groupId,
            at = at,
            name = groupName(groupItemNames[groupId].orEmpty()),
            quantity = quantity,
            amount = amount,
            storeName = storeName,
            draft = draft,
        )

        // 自分1人(割り勘が無い・参加者が自分だけ)
        if (personIds.all { it == selfId }) {
            val rows = groups.filter { it.amount != 0 }.map { rowOf(it.id, "全部", it.amount) }
            // 自分1人の支払いは、負担も払った額も支払いの合計(自分が払った)
            val whole = if (selfId in personIds) paymentTotal(input) else null
            return PaymentMeal(paymentId, rows, unsetGroups = 0, burden = whole, selfPaid = whole)
        }

        // 自分が参加者に入っていない(起きない想定)。食べた分は決められないので、すべて未入力として数える
        if (selfId !in personIds) {
            return PaymentMeal(paymentId, emptyList(), unsetGroups = groups.count { it.amount != 0 })
        }

        // 参加者2人以上: 食べた分の計算。自分が1人だけ「自分が本人」になるよう people の isSelf を揃える
        val normalized = input.copy(
            mode = SplitMode.ITEM,
            people = input.people.map { it.copy(isSelf = it.id == selfId) },
        )
        val result = SplitCalculator.calculate(normalized)
        // 割当を解釈できなかったまとまり(W5)は、自分が食べたことにせず未入力として数える
        val invalidGroups = result.warnings
            .filterIsInstance<SplitWarning.InvalidShares>()
            .mapNotNull { it.groupId }
            .toSet()

        val rows = mutableListOf<MealRecordRow>()
        var unset = 0
        for (g in groups) {
            if (g.amount == 0) continue
            val shares = input.itemShares[g.id].orEmpty().filter { it.personId in personIds }
            val kinds = shares.map { it.kind }.distinct()
            val assigned = shares.isNotEmpty() && kinds.size == 1 && kinds.single() in KNOWN_KINDS && g.id !in invalidGroups
            if (!assigned) {
                unset++
                continue
            }
            val mine = result.lines.filter { it.personId == selfId && it.groupId == g.id }.sumOf { it.amount }
            if (mine == 0) continue
            rows += rowOf(g.id, quantityOf(g, shares, selfId, mine), mine)
        }
        // 自分の負担額(共通費を含む)は、割り勘の計算を実際の方式で求めた自分の合計。払った額は会計者の指定から
        val burden = SplitCalculator.calculate(input).lines.filter { it.personId == selfId }.sumOf { it.amount }
        return PaymentMeal(paymentId, rows, unset, burden = burden, selfPaid = selfPaidOf(input, selfId))
    }

    /** 支払いの合計(円)。total が無ければ品目(まとまり)の合計。共通費を含む。 */
    private fun paymentTotal(input: SplitInput): Int = input.total ?: input.groups.sumOf { it.amount }

    /**
     * 自分が会計者として店に払った額。割り勘の計算(SplitCalculator)と同じ決め方:
     * 会計者(isPayer)が1人ならその人が合計を払う。2人以上は会計者ごとの金額が入力に無いので null。
     * 会計者が無ければ、payers(空なら自分が合計を払った)。
     */
    private fun selfPaidOf(input: SplitInput, selfId: Long): Int? {
        val flagged = input.people.filter { it.isPayer }.map { it.id }.toSet()
        return when {
            flagged.size == 1 -> if (selfId in flagged) paymentTotal(input) else 0
            flagged.size >= 2 -> null
            input.payers.isNotEmpty() -> input.payers.filter { it.personId == selfId }.sumOf { it.amount }
            else -> paymentTotal(input)
        }
    }

    /** 量の表示。shares は割当が1種類に揃っていて、空でないもの。mine は自分の食べた分(円)。 */
    private fun quantityOf(g: SplitGroupInput, shares: List<ShareInput>, selfId: Long, mine: Int): String {
        val kind = shares.first().kind
        if (kind == ShareKind.AMOUNT) return yen(mine) + "分"
        val people = shares.map { it.personId }.distinct()
        if (people == listOf(selfId)) return "全部"
        return when (kind) {
            ShareKind.ASSIGN, ShareKind.EQUAL -> "1/${people.size}"
            ShareKind.RATIO -> ratioQuantity(g, shares, selfId)
            else -> "全部"
        }
    }

    /**
     * 個数の割当の「M/N個」。計算(SplitCalculator.ratioItem)と同じ重みの決め方に合わせる。
     * - 空欄の人がいて pieceCount があるとき: N = pieceCount、空欄の人の M = 残りの個数(0以上)。
     * - 空欄の人がいて pieceCount が無いとき: N = 指定の合計 + 空欄の人数、空欄の人の M = 1。
     * - 空欄が無いとき: N = 指定の合計(計算も指定の比で割る)。
     */
    private fun ratioQuantity(g: SplitGroupInput, shares: List<ShareInput>, selfId: Long): String {
        val byPerson = shares.groupBy { it.personId }
        val specifiedSum = byPerson.values.sumOf { rows -> rows.mapNotNull { it.weight }.sum() }
        val blanks = byPerson.values.count { rows -> rows.none { it.weight != null } }
        val pieceCount = g.pieceCount
        val n = if (blanks > 0 && pieceCount != null) pieceCount else specifiedSum + blanks
        val selfRows = byPerson[selfId].orEmpty()
        val m = when {
            selfRows.any { it.weight != null } -> selfRows.mapNotNull { it.weight }.sum()
            pieceCount != null -> maxOf(pieceCount - specifiedSum, 0)
            else -> 1
        }
        return if (n <= 0) "全部" else "$m/${n}個"
    }

    private fun yen(amount: Int): String = "¥" + String.format(Locale.US, "%,d", amount)
}
