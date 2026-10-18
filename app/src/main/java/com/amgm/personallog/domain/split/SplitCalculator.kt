package com.amgm.personallog.domain.split

import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import kotlin.math.abs

/**
 * 割り勘の計算(純Kotlin。JVMでテスト可能)。
 *
 * 入力(割り方)だけを保存し、各人の金額はこの計算で毎回求める。
 * 均等・割合・1人の割当は金額が変わっても自動で追従し、金額指定の割当は据え置いて警告を出す。
 * 空欄(金額・割合の未入力)は、残額・残りの個数を空欄の人で均等に分ける。
 * 不変条件: 結果の全行の合計は常に入力の合計(total)と一致する。
 */
object SplitCalculator {
    fun calculate(input: SplitInput): SplitResult = Calculation(input).result()

    /**
     * 金額指定の配分結果。shares は各人の額(空欄の超過時は0)、fixedSum は指定額の合計、
     * mismatch は指定額の合計(超過時は空欄を含めない)が総額と合わないこと(差は呼び出し側が仮計上する)。
     */
    private class AmountSplit(val shares: Map<Long, Int>, val fixedSum: Int, val mismatch: Boolean)

    /** 人ごとの指定額(null は空欄)を、指定あり(fixed)と空欄(blanks)に分ける。 */
    private fun partition(amounts: Map<Long, Int?>): Pair<Map<Long, Int>, List<Long>> {
        val fixed = LinkedHashMap<Long, Int>()
        val blanks = mutableListOf<Long>()
        for ((id, a) in amounts) {
            if (a == null) blanks += id else fixed[id] = a
        }
        return fixed to blanks
    }

    private class Calculation(private val input: SplitInput) {
        private val self: Long
        private val total: Int
        private val groupSum: Int
        private val payerIds: Set<Long>

        /** 参加者 P = people 全員(people の並び順)。 */
        private val participants: List<Long>
        private val peopleIds: Set<Long>
        private val order: Map<Long, Int>
        private val label: Map<Long, String>
        private val lines = mutableListOf<SplitLine>()
        private val warnings = mutableListOf<SplitWarning>()

        init {
            val selves = input.people.filter { it.isSelf }
            require(selves.size == 1) { "isSelf の人はちょうど1人にする(現在 ${selves.size} 人)" }
            self = selves.single().id
            participants = input.people.map { it.id }.distinct()
            peopleIds = participants.toSet()
            order = input.people.associate { it.id to it.order }
            label = input.people.associate { it.id to it.label }
            groupSum = input.groups.sumOf { it.amount }
            total = input.total ?: groupSum
            // 支払者: isPayer の人 → 無ければ payers の人 → それも無ければ自分。
            val flagged = input.people.filter { it.isPayer }.map { it.id }.toSet()
            payerIds = when {
                flagged.isNotEmpty() -> flagged
                input.payers.isNotEmpty() -> input.payers.map { it.personId }.toSet()
                else -> setOf(self)
            }
        }

        fun result(): SplitResult {
            // W4: 支払者の合計が支払いの合計と合わない。
            if (input.payers.isNotEmpty()) {
                val paid = input.payers.sumOf { it.amount }
                if (paid != total) warnings += SplitWarning.PayersMismatch(total, paid)
            }
            // W3: 品目の合計と支払いの合計がずれている(どの方式でも出す)。
            val residual = total - groupSum
            if (input.groups.isNotEmpty() && abs(residual) >= 2) {
                warnings += SplitWarning.ItemsTotalMismatch(residual)
            }

            when (input.mode) {
                SplitMode.ITEM -> itemLines(residual)
                SplitMode.WHOLE -> wholeLines()
                else -> selfLines()
            }

            // 行の順番は 人の order → groupId(残差・調整の行 groupId=null は最後)。
            val sorted = lines.sortedWith(
                compareBy<SplitLine>(
                    { order[it.personId] ?: Int.MAX_VALUE },
                    { it.personId },
                    { it.groupId == null },
                    { it.groupId ?: 0L },
                ),
            )
            return SplitResult(sorted, warnings.toList())
        }

        /** SELF: 各まとまりを自分が全額払う。残差(合計−品目の合計)も自分の行にする。 */
        private fun selfLines() {
            for (g in input.groups) add(self, g.id, g.amount)
            add(self, null, total - groupSum)
        }

        /** ITEM: まとまりごとに割当で割る。残差は参加者 P(people 全員)で均等に配る。 */
        private fun itemLines(residual: Int) {
            for (g in input.groups) {
                val raw = input.itemShares[g.id].orEmpty()
                val valid = raw.filter { it.personId in peopleIds }
                if (valid.size != raw.size) {
                    warnings += SplitWarning.InvalidShares(g.id, "unknown person")
                }
                if (valid.isEmpty()) {
                    // 割当が無い: 共通費は参加者で均等、それ以外は自分が全額。
                    if (g.isShared) splitEqual(g.id, participants, g.amount) else add(self, g.id, g.amount)
                    continue
                }
                val kinds = valid.map { it.kind }.distinct()
                if (kinds.size > 1) {
                    warnings += SplitWarning.InvalidShares(g.id, "mixed kinds")
                    add(self, g.id, g.amount)
                    continue
                }
                when (kinds.single()) {
                    ShareKind.ASSIGN -> {
                        val ids = valid.map { it.personId }.distinct()
                        if (ids.size == 1) add(ids.single(), g.id, g.amount) else splitEqual(g.id, ids, g.amount)
                    }
                    ShareKind.EQUAL -> splitEqual(g.id, valid.map { it.personId }.distinct(), g.amount)
                    ShareKind.RATIO -> ratioItem(g, valid)
                    ShareKind.AMOUNT -> amountItem(g, valid)
                    else -> {
                        warnings += SplitWarning.InvalidShares(g.id, "unknown kind")
                        add(self, g.id, g.amount)
                    }
                }
            }

            if (residual != 0) splitEqual(null, participants, residual)
        }

        /**
         * ITEM・割合。weight=null の人は空欄。
         * 空欄があり pieceCount があれば、残りの個数 r = pieceCount − Σ指定 を空欄の人で均等に分ける。
         * 整数のまま比べるため、指定の重みを空欄の人数倍し、空欄の重みを r にする。r<0 は W5 で空欄は0。
         * pieceCount が無ければ空欄は重み1。
         */
        private fun ratioItem(g: SplitGroupInput, valid: List<ShareInput>) {
            if (valid.any { (it.weight ?: 0) < 0 }) {
                warnings += SplitWarning.InvalidShares(g.id, "negative weight")
                add(self, g.id, g.amount)
                return
            }
            val specified = LinkedHashMap<Long, Int>()
            val blanks = mutableListOf<Long>()
            for ((id, list) in valid.groupBy { it.personId }) {
                val ws = list.mapNotNull { it.weight }
                if (ws.isEmpty()) blanks += id else specified[id] = ws.sum()
            }
            val specifiedSum = specified.values.sum()
            val pieceCount = g.pieceCount
            val weights: List<Pair<Long, Int>> = when {
                blanks.isEmpty() -> specified.toList()
                pieceCount == null -> specified.toList() + blanks.map { it to 1 }
                else -> {
                    val r = pieceCount - specifiedSum
                    if (r < 0) {
                        warnings += SplitWarning.InvalidShares(g.id, "pieces exceed")
                        specified.toList() + blanks.map { it to 0 }
                    } else {
                        val k = blanks.size
                        specified.map { it.key to it.value * k } + blanks.map { it to r }
                    }
                }
            }
            if (weights.sumOf { it.second.toLong() } == 0L) {
                warnings += SplitWarning.InvalidShares(g.id, "zero total weight")
                add(self, g.id, g.amount)
                return
            }
            splitWeighted(g.id, weights, g.amount)
        }

        /**
         * ITEM・金額指定。指定額をそのまま各人に。空欄の人は残額を均等に分ける。
         * 指定額の合計が残額と逆向きに超過するときは空欄を0にし、W1 と差の仮計上(自分)を出す。
         * 空欄が無く合計がずれたときも同じく W1 と仮計上。
         */
        private fun amountItem(g: SplitGroupInput, valid: List<ShareInput>) {
            val amounts = valid.groupBy { it.personId }.mapValues { (_, list) ->
                list.mapNotNull { it.amount }.let { if (it.isEmpty()) null else it.sum() }
            }
            val (fixed, blanks) = partition(amounts)
            val split = splitAmounts(g.amount, fixed, blanks)
            for ((id, x) in split.shares) add(id, g.id, x)
            if (split.mismatch) {
                warnings += SplitWarning.GroupAmountMismatch(g.id, g.amount, split.fixedSum)
                add(self, g.id, g.amount - split.fixedSum, provisional = true)
            }
        }

        /**
         * WHOLE: 支払い全体を人で割る。カテゴリ内訳は品目の金額を各人の額に比例させて付ける。
         * people が自分だけなら SELF と同じ結果。種類は input.wholeKind で決める。
         */
        private fun wholeLines() {
            if (participants.size == 1) {
                selfLines()
                return
            }
            when (input.wholeKind) {
                ShareKind.AMOUNT -> wholeAmount()
                ShareKind.RATIO -> wholeRatio(equalWeights = false)
                ShareKind.EQUAL -> wholeRatio(equalWeights = true) // 旧データ: 全員の重みは1
                ShareKind.ASSIGN -> wholeAssign()                 // 旧データ: 行に出てくる人で均等
                else -> {
                    warnings += SplitWarning.InvalidShares(null, "unknown kind")
                    selfLines()
                }
            }
        }

        /** 支払い全体の行のうち、people にいる人の行だけを返す。不明な人の行は捨てて W5。 */
        private fun validWholeRows(): List<ShareInput> {
            val raw = input.wholeShares
            val valid = raw.filter { it.personId in peopleIds }
            if (valid.size != raw.size) warnings += SplitWarning.InvalidShares(null, "unknown person")
            return valid
        }

        /**
         * WHOLE・金額指定。各人の値 = 本人の金額 ?: labelAmounts[本人のラベル](ラベル無しは labelAmounts[""])?: 空欄。
         * 空欄は残額を均等。超過・不一致は W2 と差の仮計上(自分)。
         */
        private fun wholeAmount() {
            val valid = validWholeRows()
            val rowAmounts = valid.groupBy { it.personId }.mapValues { (_, list) ->
                list.mapNotNull { it.amount }.let { if (it.isEmpty()) null else it.sum() }
            }
            val amounts: Map<Long, Int?> = participants.associateWith { id ->
                rowAmounts[id] ?: input.labelAmounts[label[id].orEmpty()]
            }
            val (fixed, blanks) = partition(amounts)
            val split = splitAmounts(total, fixed, blanks)
            if (split.mismatch) {
                warnings += SplitWarning.WholeAmountMismatch(total, split.fixedSum)
                add(self, null, total - split.fixedSum, provisional = true)
            }
            distributeByCategories(split.shares)
        }

        /**
         * WHOLE・割合。重み = 本人の weight ?: 1(空欄は1)。equalWeights は旧データの EQUAL(全員1)。
         */
        private fun wholeRatio(equalWeights: Boolean) {
            val valid = validWholeRows()
            if (!equalWeights && valid.any { (it.weight ?: 0) < 0 }) {
                warnings += SplitWarning.InvalidShares(null, "negative weight")
                selfLines()
                return
            }
            val weights = participants.map { id ->
                val rows = valid.filter { it.personId == id }
                id to if (equalWeights || rows.isEmpty()) 1 else rows.sumOf { it.weight ?: 1 }
            }
            if (weights.sumOf { it.second.toLong() } == 0L) {
                warnings += SplitWarning.InvalidShares(null, "zero total weight")
                selfLines()
                return
            }
            distributeByCategories(YenAllocator.allocate(total, weights, payerIds, order))
        }

        /** WHOLE・旧データの ASSIGN: 行に出てくる人で均等。行が無ければ SELF と同じ。 */
        private fun wholeAssign() {
            val valid = validWholeRows()
            if (valid.isEmpty()) {
                selfLines()
                return
            }
            val ids = valid.map { it.personId }.distinct()
            distributeByCategories(YenAllocator.allocate(total, ids.map { it to 1 }, payerIds, order))
        }

        /** 金額指定の配分。fixed は指定額、blanks は空欄(残額を均等)。残額が総額と逆向きに超過なら空欄は0。 */
        private fun splitAmounts(amount: Int, fixed: Map<Long, Int>, blanks: List<Long>): AmountSplit {
            val fixedSum = fixed.values.sum()
            if (blanks.isEmpty()) return AmountSplit(fixed, fixedSum, fixedSum != amount)
            val rem = amount - fixedSum
            val overflow = (amount >= 0 && rem < 0) || (amount < 0 && rem > 0)
            if (overflow) return AmountSplit(fixed + blanks.associateWith { 0 }, fixedSum, true)
            val shares = YenAllocator.allocate(rem, blanks.map { it to 1 }, payerIds, order)
            return AmountSplit(fixed + shares, fixedSum, false)
        }

        /**
         * 各人の額 A_p(targets)を、品目のまとまりの内訳に分ける。
         * 全員の A_p ≥ 0 かつ ΣA_p > 0 のとき、各まとまり g.amount を A_p の重みで配り、
         * 各人の不足分(A_p − 内訳の合計)を (p, null) の行にする。満たさなければ各人1行。
         */
        private fun distributeByCategories(targets: Map<Long, Int>) {
            val sum = targets.values.sum()
            val canBreakDown = targets.values.all { it >= 0 } && sum > 0
            if (!canBreakDown) {
                for ((id, a) in targets) add(id, null, a)
                return
            }
            val weights = targets.map { it.key to it.value }
            val paid = HashMap<Long, Int>()
            for (g in input.groups) {
                for ((id, x) in YenAllocator.allocate(g.amount, weights, payerIds, order)) {
                    add(id, g.id, x)
                    paid[id] = (paid[id] ?: 0) + x
                }
            }
            for ((id, a) in targets) add(id, null, a - (paid[id] ?: 0))
        }

        private fun splitEqual(groupId: Long?, ids: List<Long>, amount: Int) {
            splitWeighted(groupId, ids.map { it to 1 }, amount)
        }

        private fun splitWeighted(groupId: Long?, weights: List<Pair<Long, Int>>, amount: Int) {
            for ((id, x) in YenAllocator.allocate(amount, weights, payerIds, order)) add(id, groupId, x)
        }

        /** 0円の行は作らない。 */
        private fun add(personId: Long, groupId: Long?, amount: Int, provisional: Boolean = false) {
            if (amount != 0) lines += SplitLine(personId, groupId, amount, provisional)
        }
    }
}
