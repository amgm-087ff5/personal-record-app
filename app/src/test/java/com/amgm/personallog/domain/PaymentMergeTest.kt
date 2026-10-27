package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.domain.PaymentMerge.Field
import com.amgm.personallog.domain.PaymentMerge.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMergeTest {
    private fun g(
        id: Long,
        date: String = "2026-10-05",
        time: String = "12:00",
        amount: Int = 1000,
        createdAt: Long = 0,
        category: String = Categories.UNCATEGORIZED,
        memo: String = "",
        receiptId: Long? = null,
    ) = ExpenseGroup(
        id = id, date = date, time = time, amount = amount, category = category,
        memo = memo, receiptId = receiptId, createdAt = createdAt,
    )

    private fun kindOf(diffs: List<PaymentMerge.FieldDiff>, field: Field) =
        diffs.first { it.field == field }.kind

    @Test
    fun candidate_exactlyFiveMinutesApart_isCandidate() {
        assertTrue(PaymentMerge.isCandidate(g(1, time = "12:00"), g(2, time = "12:05")))
    }

    @Test
    fun candidate_sixMinutesApart_isNotCandidate() {
        // 時刻は HH:mm のため 5分1秒は表せない。直近の外側として6分で確かめる
        assertFalse(PaymentMerge.isCandidate(g(1, time = "12:00"), g(2, time = "12:06")))
    }

    @Test
    fun candidate_crossesMidnight() {
        val a = g(1, date = "2026-10-05", time = "23:58")
        val b = g(2, date = "2026-10-06", time = "00:03")
        assertTrue(PaymentMerge.isCandidate(a, b))
    }

    @Test
    fun candidate_amountDifferByOneYen_isNotCandidate() {
        assertFalse(PaymentMerge.isCandidate(g(1, amount = 1000), g(2, amount = 1001)))
    }

    @Test
    fun candidate_bothWithoutTime_sameDateAndAmount_isCandidate() {
        assertTrue(PaymentMerge.isCandidate(g(1, time = ""), g(2, time = "")))
        assertFalse(PaymentMerge.isCandidate(g(1, date = "2026-10-05", time = ""), g(2, date = "2026-10-06", time = "")))
    }

    @Test
    fun candidate_oneWithoutTime_comparesDateOnly() {
        assertTrue(PaymentMerge.isCandidate(g(1, time = ""), g(2, time = "18:00")))
        assertFalse(PaymentMerge.isCandidate(g(1, date = "2026-10-04", time = ""), g(2, time = "12:00")))
    }

    @Test
    fun duplicateSets_targetIsOldestCreatedAt() {
        val groups = listOf(
            g(10, createdAt = 300),
            g(11, createdAt = 100),
            g(12, createdAt = 200),
            g(13, amount = 500, createdAt = 50), // 別金額は組に入らない
        )
        val sets = PaymentMerge.duplicateSets(groups)
        assertEquals(1, sets.size)
        assertEquals(listOf(11L, 12L, 10L), sets[0].map { it.id })
    }

    @Test
    fun duplicateSets_singletonsAreDropped() {
        val groups = listOf(g(1, time = "08:00"), g(2, time = "20:00"))
        assertTrue(PaymentMerge.duplicateSets(groups).isEmpty())
    }

    @Test
    fun duplicateSets_connectsTransitively() {
        // 1と2は5分以内、2と3も5分以内。1と3は10分離れていても同じ組になる
        val groups = listOf(
            g(1, time = "12:00", createdAt = 1),
            g(2, time = "12:05", createdAt = 2),
            g(3, time = "12:10", createdAt = 3),
        )
        val sets = PaymentMerge.duplicateSets(groups)
        assertEquals(1, sets.size)
        assertEquals(listOf(1L, 2L, 3L), sets[0].map { it.id })
    }

    @Test
    fun fieldDiffs_timeDifference_isConflict() {
        val group = listOf(g(1, time = "12:00"), g(2, time = "12:03"))
        val diffs = PaymentMerge.fieldDiffs(group)
        assertEquals(Kind.CONFLICT, kindOf(diffs, Field.TIME))
        val time = diffs.first { it.field == Field.TIME }
        assertEquals(listOf("マージ先", "マージ元1"), time.candidates.map { it.label })
        assertEquals(listOf("12:00", "12:03"), time.candidates.map { it.text })
        assertTrue(PaymentMerge.hasDifference(diffs))
        assertFalse(PaymentMerge.defaultTexts(diffs).containsKey(Field.TIME))
    }

    @Test
    fun fieldDiffs_memoOnlyOnOneSide_isFill_andDefaultTextsHoldsValue() {
        val group = listOf(g(1, memo = ""), g(2, memo = "特売"))
        val diffs = PaymentMerge.fieldDiffs(group)
        assertEquals(Kind.FILL, kindOf(diffs, Field.MEMO))
        assertEquals("特売", PaymentMerge.defaultTexts(diffs)[Field.MEMO])
        assertEquals(Kind.SAME, kindOf(diffs, Field.AMOUNT))
        assertEquals(Kind.SAME, kindOf(diffs, Field.RECEIPT))
    }

    @Test
    fun fieldDiffs_uncategorizedIsEmptyText() {
        val group = listOf(g(1, category = Categories.UNCATEGORIZED), g(2, category = "食費"))
        val diffs = PaymentMerge.fieldDiffs(group)
        assertEquals(Kind.FILL, kindOf(diffs, Field.CATEGORY))
        assertEquals("食費", PaymentMerge.defaultTexts(diffs)[Field.CATEGORY])
    }

    @Test
    fun fieldDiffs_sameValues_noDifference() {
        val group = listOf(g(1, memo = "同じ"), g(2, memo = "同じ"))
        assertFalse(PaymentMerge.hasDifference(PaymentMerge.fieldDiffs(group)))
    }

    @Test
    fun merge_setsEditedFlagsForChangedFields_andKeepsTargetId() {
        val target = g(1, time = "12:00", amount = 1000, memo = "", createdAt = 1, receiptId = 5)
        val source = g(2, time = "12:03", amount = 1000, memo = "特売", createdAt = 2)
        val merged = PaymentMerge.merge(
            listOf(target, source),
            mapOf(
                Field.DATE to "2026-10-05",
                Field.TIME to "12:03",
                Field.AMOUNT to "1200",
                Field.CATEGORY to "",
                Field.MEMO to "特売",
                Field.RECEIPT to "",
            ),
        )
        assertEquals(1L, merged.id)
        assertEquals("12:03", merged.time)
        assertEquals(1200, merged.amount)
        assertEquals(Categories.UNCATEGORIZED, merged.category)
        assertEquals("特売", merged.memo)
        assertNull(merged.receiptId)
        assertTrue(merged.timeEdited)
        assertTrue(merged.amountEdited)
        assertFalse(merged.categoryEdited)
        assertTrue(merged.memoEdited)
    }

    @Test
    fun merge_unchangedFields_keepFlagsFalse() {
        val target = g(1, amount = 1000)
        val merged = PaymentMerge.merge(listOf(target), mapOf(Field.AMOUNT to "1000"))
        assertFalse(merged.amountEdited)
        assertFalse(merged.timeEdited)
        assertFalse(merged.memoEdited)
    }

    @Test
    fun merge_missingFieldsKeepTargetValues() {
        val target = g(1, memo = "元のメモ", receiptId = 7)
        val merged = PaymentMerge.merge(listOf(target, g(2)), mapOf(Field.AMOUNT to "900"))
        assertEquals("元のメモ", merged.memo)
        assertEquals(7L, merged.receiptId)
        assertEquals(900, merged.amount)
        assertNotNull(merged.date)
    }

    @Test
    fun validate_rules() {
        assertNull(PaymentMerge.validate(Field.DATE, "2026-10-05"))
        assertEquals("日付は yyyy-MM-dd で入力してください", PaymentMerge.validate(Field.DATE, "2026-2-30"))
        assertNull(PaymentMerge.validate(Field.TIME, ""))
        assertNull(PaymentMerge.validate(Field.TIME, "09:05"))
        assertNotNull(PaymentMerge.validate(Field.TIME, "25:00"))
        assertNull(PaymentMerge.validate(Field.AMOUNT, "1"))
        assertNotNull(PaymentMerge.validate(Field.AMOUNT, "0"))
        assertNotNull(PaymentMerge.validate(Field.AMOUNT, "-5"))
        assertNull(PaymentMerge.validate(Field.MEMO, ""))
        assertNotNull(PaymentMerge.validate(Field.CATEGORY, "食費"))
        assertNotNull(PaymentMerge.validate(Field.RECEIPT, "1"))
    }
}
