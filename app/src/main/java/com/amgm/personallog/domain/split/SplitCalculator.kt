package com.amgm.personallog.domain.split

import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import kotlin.math.abs

/**
 * 割り勘の計算(純Kotlin。JVMでテスト可能)。
 *
 * 入力(割り方)だけを保存し、各人の金額はこの計算で毎回求める。
 * 均等・割合・1人の割当は金額が変わっても自動で追従し、金額指定の割当は据え置いて警告を出す。
 * 不変条件: 結果の全行の合計は常に入力の合計(total)と一致する。
 */
object SplitCalculator {
    fun calculate(input: SplitInput): SplitResult = Calculation(input).result()

    private class Calculation(private val input: SplitInput) {
        private val self: Long
        private val total: Int
        private val groupSum: Int
        private val payerIds: Set<Long>
        private val peopleIds: Set<Long>
        private val order: Map<Long, Int>
        private val lines = mutableListOf<SplitLine>()
        private val warnings = mutableListOf<SplitWarning>()

        init {
            val selves = input.people.filter { it.isSelf }
            require(selves.size == 1) { "isSelf の人はちょうど1人にする(現在 ${selves.size} 人)" }
            self = selves.single().id
            peopleIds = input.people.map { it.id }.toSet()
            order = input.people.associate { it.id to it.order }
            groupSum = input.groups.sumOf { it.amount }
            total = input.total ?: groupSum
            // 支払者が空なら自分が合計を払った。
            payerIds = if (input.payers.isEmpty()) setOf(self) else input.payers.map { it.personId }.toSet()
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

        /** ITEM: まとまりごとに割当で割る。残差は参加者 P で均等に配る。 */
        private fun itemLines(residual: Int) {
            // 参加者 P = 自分 + 割当に出てくる人(people に居る人だけ)。
            val referenced = input.itemShares.values.flatten().map { it.personId }.filter { it in peopleIds }
            val participants = (listOf(self) + referenced).distinct()

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

        /** ITEM・割合: 重みで比例配分。重みが不正なら自分が全額。 */
        private fun ratioItem(g: SplitGroupInput, valid: List<ShareInput>) {
            val weights = personWeights(valid)
            val reason = when {
                valid.any { (it.weight ?: 0) < 0 } -> "negative weight"
                weights.sumOf { it.second.toLong() } == 0L -> "zero total weight"
                else -> null
            }
            if (reason != null) {
                warnings += SplitWarning.InvalidShares(g.id, reason)
                add(self, g.id, g.amount)
            } else {
                splitWeighted(g.id, weights, g.amount)
            }
        }

        /** ITEM・金額指定: 指定額をそのまま各人に。合計がずれたら警告し、差を自分の仮計上にする。 */
        private fun amountItem(g: SplitGroupInput, valid: List<ShareInput>) {
            val byPerson = valid.groupBy { it.personId }.map { (id, list) -> id to list.sumOf { it.amount ?: 0 } }
            for ((id, amt) in byPerson) add(id, g.id, amt)
            val allocated = byPerson.sumOf { it.second }
            if (allocated != g.amount) {
                warnings += SplitWarning.GroupAmountMismatch(g.id, g.amount, allocated)
                add(self, g.id, g.amount - allocated, provisional = true)
            }
        }

        /** WHOLE: 支払い全体を人で割る。カテゴリ内訳は品目の金額を各人の額に比例させて付ける。 */
        private fun wholeLines() {
            val raw = input.wholeShares
            val valid = raw.filter { it.personId in peopleIds }
            if (valid.size != raw.size) warnings += SplitWarning.InvalidShares(null, "unknown person")
            if (valid.isEmpty()) {
                selfLines()
                return
            }
            val kinds = valid.map { it.kind }.distinct()
            if (kinds.size > 1) {
                warnings += SplitWarning.InvalidShares(null, "mixed kinds")
                selfLines()
                return
            }
            val kind = kinds.single()

            val targets: Map<Long, Int> = when (kind) {
                ShareKind.ASSIGN, ShareKind.EQUAL -> {
                    val ids = valid.map { it.personId }.distinct()
                    YenAllocator.allocate(total, ids.map { it to 1 }, payerIds, order)
                }
                ShareKind.RATIO -> {
                    val weights = personWeights(valid)
                    val reason = when {
                        valid.any { (it.weight ?: 0) < 0 } -> "negative weight"
                        weights.sumOf { it.second.toLong() } == 0L -> "zero total weight"
                        else -> null
                    }
                    if (reason != null) {
                        warnings += SplitWarning.InvalidShares(null, reason)
                        selfLines()
                        return
                    }
                    YenAllocator.allocate(total, weights, payerIds, order)
                }
                ShareKind.AMOUNT -> valid.groupBy { it.personId }.mapValues { (_, list) -> list.sumOf { it.amount ?: 0 } }
                else -> {
                    warnings += SplitWarning.InvalidShares(null, "unknown kind")
                    selfLines()
                    return
                }
            }

            // W2: 金額指定の合計が支払いの合計と合わない。差は自分の仮計上。
            val allocated = targets.values.sum()
            if (kind == ShareKind.AMOUNT && allocated != total) {
                warnings += SplitWarning.WholeAmountMismatch(total, allocated)
                add(self, null, total - allocated, provisional = true)
            }
            distributeByCategories(targets)
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

        /** 同じ人の重みは合算する。 */
        private fun personWeights(valid: List<ShareInput>): List<Pair<Long, Int>> =
            valid.groupBy { it.personId }.map { (id, list) -> id to list.sumOf { it.weight ?: 0 } }

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
