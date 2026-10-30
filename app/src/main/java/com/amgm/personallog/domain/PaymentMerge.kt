package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.data.model.PaymentStatus
import com.amgm.personallog.receipt.NotDuplicateSet
import com.amgm.personallog.receipt.ReceiptCalc
import com.amgm.personallog.receipt.PaymentDedup
import java.util.Locale

/**
 * 支払いの重複マージ(純Kotlin、JVMテスト可能)。DB の書き込みはしない。
 *
 * 重複の組は [PaymentDedup.isDuplicate] で候補を作り、推移的につないだ組にする。
 * 各項目は比較キー(key)で同じか・空を埋めるか・食い違うかを判別し、食い違いは利用者が選ぶ。
 */
object PaymentMerge {
    enum class Field(val label: String) { DATE("日付"), TIME("時刻"), STORE("支払い先"), TOTAL("合計"), IMAGE("画像"), CONTENT("中身") }
    enum class Kind { SAME, FILL, CONFLICT }

    /** 比較の材料(DB から集めた1件分)。hasSplit は割り勘の入力(split_sheets 等)があるか。 */
    data class Snapshot(val payment: Payment, val items: List<PaymentItem>, val hasSplit: Boolean)

    /** label: "マージ先" / "マージ元1" / "マージ元2"…。text は表示用(空は ""）。key は比較用(空は ""）。 */
    data class Candidate(val paymentId: Long, val label: String, val text: String, val key: String)

    data class FieldDiff(val field: Field, val candidates: List<Candidate>, val kind: Kind) {
        val differs: Boolean get() = candidates.map { it.key }.distinct().size > 1
    }

    sealed interface Choice {
        data class From(val paymentId: Long) : Choice
        data class Manual(val text: String) : Choice
    }

    /** マージの結果(DB の書き込みはしない)。payment はマージ先の id のまま更新後の値。removed はマージ元の id。 */
    data class Plan(val payment: Payment, val contentFrom: Long, val imageFrom: Long, val removed: List<Long>)

    private val creationOrder = compareBy<Payment>({ it.createdAt }, { it.id })

    /**
     * 重複の組。候補を推移的につないだ組(2件以上)で、各組は createdAt 昇順・同じなら id 昇順。
     * 組の並びは先頭の作成順。
     */
    fun duplicateSets(payments: List<Payment>, notDuplicates: NotDuplicateSet = emptySet()): List<List<Payment>> {
        val parent = IntArray(payments.size) { it }
        fun root(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (i in payments.indices) {
            for (j in i + 1 until payments.size) {
                if (PaymentDedup.isDuplicate(payments[i], payments[j], notDuplicates)) {
                    parent[root(i)] = root(j)
                }
            }
        }
        return payments.indices
            .groupBy { root(it) }
            .values
            .filter { it.size >= 2 }
            .map { idx -> idx.map { payments[it] }.sortedWith(creationOrder) }
            .sortedWith(compareBy<List<Payment>>({ it.first().createdAt }, { it.first().id }))
    }

    /** paymentId を含む組。組に入っていなければ null。 */
    fun setContaining(paymentId: Long, sets: List<List<Payment>>): List<Payment>? =
        sets.firstOrNull { set -> set.any { it.id == paymentId } }

    /** createdAt、同じなら id の昇順。 */
    fun ordered(snapshots: List<Snapshot>): List<Snapshot> =
        snapshots.sortedWith(compareBy({ it.payment.createdAt }, { it.payment.id }))

    /** 引数は ordered 済み。Field の定義順で、全項目を返す。 */
    fun fieldDiffs(snapshots: List<Snapshot>): List<FieldDiff> =
        Field.entries.map { field ->
            val candidates = snapshots.mapIndexed { i, s ->
                val label = if (i == 0) "マージ先" else "マージ元$i"
                candidateOf(field, s, label)
            }
            FieldDiff(field, candidates, kindOf(candidates))
        }

    fun hasDifference(diffs: List<FieldDiff>): Boolean = diffs.any { it.differs }

    /** SAME は先頭、FILL は最初に key が空でない候補。CONFLICT は含めない。 */
    fun defaultChoices(diffs: List<FieldDiff>): Map<Field, Choice> = buildMap {
        for (d in diffs) {
            when (d.kind) {
                Kind.SAME -> d.candidates.firstOrNull()?.let { put(d.field, Choice.From(it.paymentId)) }
                Kind.FILL -> d.candidates.firstOrNull { it.key.isNotEmpty() }?.let { put(d.field, Choice.From(it.paymentId)) }
                Kind.CONFLICT -> Unit
            }
        }
    }

    /** 手入力できるのは日付・時刻・支払い先だけ。 */
    fun canManual(field: Field): Boolean = field == Field.DATE || field == Field.TIME || field == Field.STORE

    /** 問題が無ければ null。 */
    fun validate(field: Field, text: String): String? = when (field) {
        Field.DATE -> if (ReceiptCalc.isValidDate(text)) null else "日付は yyyy-MM-dd の形式で入力してください"
        Field.TIME -> if (text.isEmpty() || ReceiptCalc.isValidTime(text)) null else "時刻は HH:mm の形式で入力してください"
        Field.STORE -> null
        else -> "手入力できない項目です"
    }

    /**
     * 引数は ordered 済み。choices に無い項目は defaultChoices で補う。
     * CONFLICT が未選択、または From の id が snapshots に無いと IllegalArgumentException。
     * 手入力できない項目(合計・画像・中身)に Manual が来た場合は無視して既定の候補を使う。
     */
    fun plan(snapshots: List<Snapshot>, choices: Map<Field, Choice>): Plan {
        val diffs = fieldDiffs(snapshots)
        val defaults = defaultChoices(diffs)
        val byId = snapshots.associateBy { it.payment.id }

        fun choice(field: Field): Choice =
            choices[field] ?: defaults[field] ?: throw IllegalArgumentException("${field.label}が未選択です")

        fun sourceOf(id: Long): Snapshot =
            byId[id] ?: throw IllegalArgumentException("支払い $id はマージ対象に無いため選べません")

        fun fromId(field: Field): Long {
            val id = (choices[field] as? Choice.From)?.paymentId
                ?: (defaults[field] as? Choice.From)?.paymentId
                ?: throw IllegalArgumentException("${field.label}が未選択です")
            sourceOf(id)
            return id
        }

        fun textOf(field: Field): String = when (val c = choice(field)) {
            is Choice.Manual -> c.text
            is Choice.From -> {
                sourceOf(c.paymentId)
                diffs.first { it.field == field }.candidates.first { it.paymentId == c.paymentId }.key
            }
        }

        val date = textOf(Field.DATE)
        val time = textOf(Field.TIME)
        val store = textOf(Field.STORE)
        val totalFrom = sourceOf(fromId(Field.TOTAL)).payment
        val imageFrom = fromId(Field.IMAGE)
        val contentFrom = fromId(Field.CONTENT)
        val img = sourceOf(imageFrom).payment
        val content = sourceOf(contentFrom).payment

        val target = snapshots.first().payment
        val payment = target.copy(
            purchasedAt = if (time.isEmpty()) date else "$date $time",
            storeName = store,
            totalAmount = totalFrom.totalAmount,
            totalSource = totalFrom.totalSource,
            imagePath = img.imagePath,
            imageHash = img.imageHash,
            rawJson = img.rawJson,
            rotationDegrees = img.rotationDegrees,
            cropLeft = img.cropLeft,
            cropTop = img.cropTop,
            cropRight = img.cropRight,
            cropBottom = img.cropBottom,
            isAdvance = content.isAdvance,
            ownAmount = content.ownAmount,
            headcount = content.headcount,
            status = if (snapshots.any { it.payment.status == PaymentStatus.CONFIRMED }) {
                PaymentStatus.CONFIRMED
            } else {
                PaymentStatus.DRAFT
            },
        )
        return Plan(
            payment = payment,
            contentFrom = contentFrom,
            imageFrom = imageFrom,
            removed = snapshots.drop(1).map { it.payment.id },
        )
    }

    // ---- 内部

    private fun kindOf(candidates: List<Candidate>): Kind {
        val keys = candidates.map { it.key }
        val filled = keys.filter { it.isNotEmpty() }.distinct()
        return when {
            filled.size >= 2 -> Kind.CONFLICT
            filled.size == 1 && keys.any { it.isEmpty() } -> Kind.FILL
            else -> Kind.SAME
        }
    }

    private fun candidateOf(field: Field, s: Snapshot, label: String): Candidate {
        val r = s.payment
        val (text, key) = when (field) {
            Field.DATE -> r.purchasedAt.take(10).let { it to it }
            Field.TIME -> (if (r.purchasedAt.length >= 16) r.purchasedAt.substring(11, 16) else "").let { it to it }
            Field.STORE -> r.storeName.trim().let { it to it }
            Field.TOTAL -> (r.totalAmount?.let { yen(it) } ?: "") to (r.totalAmount?.toString() ?: "")
            Field.IMAGE -> (if (r.imagePath.isEmpty()) "" else "画像あり") to
                (if (r.imagePath.isEmpty()) "" else (r.imageHash ?: r.imagePath))
            Field.CONTENT -> contentText(s) to contentKey(s)
        }
        return Candidate(r.id, label, text, key)
    }

    /** 中身が空(品目が無いか全部空で、立て替え・人数・割り勘も無い)なら "" 。 */
    private fun isEmptyContent(s: Snapshot): Boolean {
        val r = s.payment
        val blankItems = s.items.all { it.name.isBlank() && it.amount == 0 && !it.unknownAmount }
        return blankItems && !r.isAdvance && r.headcount == null && !s.hasSplit
    }

    private fun contentKey(s: Snapshot): String {
        if (isEmptyContent(s)) return ""
        val r = s.payment
        val items = s.items
            .sortedWith(compareBy<PaymentItem>({ it.name }, { it.amount }, { it.category }, { it.taxRate }, { it.priceIncludesTax }, { it.unknownAmount }, { it.memo }))
            .joinToString("") { lengthPrefixed(it.name, it.amount, it.category, it.taxRate, it.priceIncludesTax, it.unknownAmount, it.memo) }
        return items + lengthPrefixed(r.isAdvance, r.ownAmount, r.headcount, s.hasSplit)
    }

    private fun contentText(s: Snapshot): String {
        if (isEmptyContent(s)) return ""
        val r = s.payment
        val parts = buildList {
            if (s.items.isNotEmpty()) add("品目${s.items.size}件 ${yen(s.items.sumOf { it.amount })}")
            if (s.hasSplit) add("割り勘あり")
            if (r.isAdvance) add("立て替え")
            r.headcount?.let { add("${it}人") }
        }
        return parts.joinToString("・").ifEmpty { "内容あり" }
    }

    /** 区切りが混ざっても別の値と区別できるよう、各値の長さを前に付けて連結する。 */
    private fun lengthPrefixed(vararg values: Any?): String =
        values.joinToString("") { v -> v.toString().let { "${it.length}:$it" } }

    private fun yen(n: Int): String = String.format(Locale.JAPAN, "¥%,d", n)
}
