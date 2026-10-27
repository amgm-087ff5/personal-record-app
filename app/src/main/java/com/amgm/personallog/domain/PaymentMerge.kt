package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.ExpenseGroup
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 支払い(ExpenseGroup)の重複確認とマージの判定(純Kotlin)。
 * 重複候補は金額が完全一致し、日時が5分以内(ちょうど5分を含む)のもの。時刻が無ければ同じ日付で判定する。
 * 自動ではマージしない。画面で差分を見てから、ユーザーが確定した値で merge する。
 */
object PaymentMerge {
    const val WINDOW_SECONDS = 300L

    enum class Field { DATE, TIME, AMOUNT, CATEGORY, MEMO, RECEIPT }

    /** SAME=全部空または同じ / FILL=空と値が混在(値は1種類) / CONFLICT=値が2種類以上 */
    enum class Kind { SAME, FILL, CONFLICT }

    /** label: "マージ先" / "マージ元1" ...。text は空のとき "" */
    data class Candidate(val label: String, val text: String)

    data class FieldDiff(val field: Field, val candidates: List<Candidate>, val kind: Kind) {
        /** 差分あり（比較対象の値に空を含めて2種類以上）。 */
        val differs: Boolean get() = candidates.map { it.text }.distinct().size > 1
    }

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 支払い1件の日時。date="yyyy-MM-dd", time="HH:mm"。time が空なら null。読めなければ null。 */
    fun dateTimeOf(group: ExpenseGroup): LocalDateTime? {
        if (group.time.isEmpty()) return null
        return try {
            LocalDateTime.of(LocalDate.parse(group.date), LocalTime.parse(group.time, TIME_FORMAT))
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** 2件が重複の候補か（金額一致 + 日時5分以内。時刻が無ければ同日）。 */
    fun isCandidate(a: ExpenseGroup, b: ExpenseGroup): Boolean =
        isCandidate(a, b, dateTimeOf(a), dateTimeOf(b))

    private fun isCandidate(a: ExpenseGroup, b: ExpenseGroup, ta: LocalDateTime?, tb: LocalDateTime?): Boolean {
        if (a.id == b.id || a.amount != b.amount) return false
        return if (ta != null && tb != null) {
            Duration.between(ta, tb).abs().seconds <= WINDOW_SECONDS
        } else {
            a.date == b.date
        }
    }

    /**
     * 重複候補の組。連結成分で作り、各組は [マージ先, マージ元...] の順（createdAt 昇順、同じなら id 昇順）。
     * 2件以上の組だけ返す。組の並びは先頭の createdAt 昇順、同じなら id 昇順。
     */
    fun duplicateSets(groups: List<ExpenseGroup>): List<List<ExpenseGroup>> {
        val n = groups.size
        val times = groups.map { dateTimeOf(it) }
        val parent = IntArray(n) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (isCandidate(groups[i], groups[j], times[i], times[j])) {
                    val ri = find(i)
                    val rj = find(j)
                    if (ri != rj) parent[rj] = ri
                }
            }
        }

        val components = LinkedHashMap<Int, MutableList<ExpenseGroup>>()
        for (i in 0 until n) {
            components.getOrPut(find(i)) { mutableListOf() }.add(groups[i])
        }
        return components.values
            .filter { it.size >= 2 }
            .map { set -> set.sortedWith(compareBy<ExpenseGroup> { it.createdAt }.thenBy { it.id }) }
            .sortedWith(compareBy<List<ExpenseGroup>> { it[0].createdAt }.thenBy { it[0].id })
    }

    /** group[0] がマージ先。全フィールドについて比較結果を返す（Field の定義順）。 */
    fun fieldDiffs(group: List<ExpenseGroup>): List<FieldDiff> =
        Field.entries.map { field ->
            val candidates = group.mapIndexed { i, g -> Candidate(labelOf(i), textOf(field, g)) }
            FieldDiff(field, candidates, kindOf(candidates.map { it.text }))
        }

    /** 組の中に差分ありの項目があるか。 */
    fun hasDifference(diffs: List<FieldDiff>): Boolean = diffs.any { it.differs }

    /** 自動で決まる値（SAME と FILL）。CONFLICT のフィールドは含めない。 */
    fun defaultTexts(diffs: List<FieldDiff>): Map<Field, String> =
        diffs.filter { it.kind != Kind.CONFLICT }.associate { d ->
            d.field to (d.candidates.map { it.text }.firstOrNull { it.isNotEmpty() } ?: "")
        }

    /** 手入力の検証。問題なければ null、あればエラー文。 */
    fun validate(field: Field, text: String): String? = when (field) {
        Field.DATE -> if (isDate(text)) null else "日付は yyyy-MM-dd で入力してください"
        Field.TIME -> if (text.isEmpty() || isTime(text)) null else "時刻は HH:mm で入力してください"
        Field.AMOUNT -> if (isAmount(text)) null else "金額は1以上の数字で入力してください"
        Field.MEMO -> null
        Field.CATEGORY, Field.RECEIPT -> "手入力できない項目です"
    }

    /**
     * マージ結果の支払いを返す（target のコピー）。finalTexts に無い欄は target の値のまま。
     * 変更があった欄の xxxEdited を true にする。DB の書き込みはしない。
     */
    fun merge(group: List<ExpenseGroup>, finalTexts: Map<Field, String>): ExpenseGroup {
        val target = group.first()

        val date = finalTexts[Field.DATE] ?: target.date
        val time = finalTexts[Field.TIME] ?: target.time
        val amount = finalTexts[Field.AMOUNT]?.let { text ->
            text.trim().toIntOrNull() ?: throw IllegalArgumentException("金額を数値にできません: $text")
        } ?: target.amount
        val category = finalTexts[Field.CATEGORY]?.let { text ->
            if (text.isBlank()) Categories.UNCATEGORIZED else text
        } ?: target.category
        val memo = finalTexts[Field.MEMO] ?: target.memo
        val receiptId = when (val text = finalTexts[Field.RECEIPT]) {
            null -> target.receiptId
            else -> if (text.isBlank()) null else text.trim().toLongOrNull() ?: target.receiptId
        }

        val dateTimeChanged = date != target.date || time != target.time
        return target.copy(
            date = date,
            time = time,
            amount = amount,
            category = category,
            memo = memo,
            receiptId = receiptId,
            timeEdited = target.timeEdited || dateTimeChanged,
            amountEdited = target.amountEdited || amount != target.amount,
            categoryEdited = target.categoryEdited || category != target.category,
            memoEdited = target.memoEdited || memo != target.memo,
        )
    }

    private fun labelOf(index: Int): String = if (index == 0) "マージ先" else "マージ元$index"

    private fun textOf(field: Field, g: ExpenseGroup): String = when (field) {
        Field.DATE -> g.date
        Field.TIME -> g.time
        Field.AMOUNT -> g.amount.toString()
        Field.CATEGORY -> if (g.category == Categories.UNCATEGORIZED) "" else g.category
        Field.MEMO -> g.memo
        Field.RECEIPT -> g.receiptId?.toString() ?: ""
    }

    private fun kindOf(texts: List<String>): Kind {
        val filled = texts.filter { it.isNotEmpty() }.distinct()
        return when {
            filled.size >= 2 -> Kind.CONFLICT
            filled.size == 1 && texts.any { it.isEmpty() } -> Kind.FILL
            else -> Kind.SAME
        }
    }

    private fun isDate(text: String): Boolean = try {
        LocalDate.parse(text)
        true
    } catch (e: DateTimeParseException) {
        false
    }

    private fun isTime(text: String): Boolean = try {
        LocalTime.parse(text, TIME_FORMAT)
        true
    } catch (e: DateTimeParseException) {
        false
    }

    private fun isAmount(text: String): Boolean =
        text.isNotEmpty() && text.all { it in '0'..'9' } && (text.toIntOrNull() ?: 0) >= 1
}
