package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.data.model.PaymentStatus
import com.amgm.personallog.receipt.PaymentDedup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMergeTest {
    private fun payment(
        id: Long,
        purchasedAt: String = "2026-10-05 12:00",
        store: String = "スーパーA",
        total: Int? = 1234,
        createdAt: Long = id,
        imagePath: String = "",
        imageHash: String? = null,
        status: String = PaymentStatus.DRAFT,
        isAdvance: Boolean = false,
        ownAmount: Int? = null,
        headcount: Int? = null,
    ) = Payment(
        id = id, purchasedAt = purchasedAt, storeName = store, totalAmount = total,
        imagePath = imagePath, rawJson = "", status = status, createdAt = createdAt,
        imageHash = imageHash, isAdvance = isAdvance, ownAmount = ownAmount, headcount = headcount,
    )

    private fun item(name: String, amount: Int) = PaymentItem(paymentId = 1, name = name, amount = amount)

    private fun snap(r: Payment, items: List<PaymentItem> = emptyList(), hasSplit: Boolean = false) =
        PaymentMerge.Snapshot(r, items, hasSplit)

    private fun diffOf(diffs: List<PaymentMerge.FieldDiff>, field: PaymentMerge.Field) =
        diffs.first { it.field == field }

    // ---- 重複の組

    @Test
    fun duplicateSets_sameTotalWithin3Minutes_formsOneSet_mergeTargetIsOlderCreated() {
        val a = payment(1, purchasedAt = "2026-10-05 12:00", createdAt = 200)
        val b = payment(2, purchasedAt = "2026-10-05 12:03", createdAt = 100)
        val sets = PaymentMerge.duplicateSets(listOf(a, b))
        assertEquals(1, sets.size)
        assertEquals(listOf(2L, 1L), sets[0].map { it.id })
    }

    @Test
    fun duplicateSets_differentTotal_notGrouped() {
        val a = payment(1, total = 1234)
        val b = payment(2, total = 1235)
        assertTrue(PaymentMerge.duplicateSets(listOf(a, b)).isEmpty())
    }

    @Test
    fun duplicateSets_chainAB4minBC4min_isOneSetOfThree() {
        // A-C は8分で直接は候補にならないが、B でつながるので1組になる
        val a = payment(1, purchasedAt = "2026-10-05 12:00")
        val b = payment(2, purchasedAt = "2026-10-05 12:04")
        val c = payment(3, purchasedAt = "2026-10-05 12:08")
        val sets = PaymentMerge.duplicateSets(listOf(c, a, b))
        assertEquals(1, sets.size)
        assertEquals(listOf(1L, 2L, 3L), sets[0].map { it.id })
    }

    @Test
    fun duplicateSets_notDuplicatePair_isExcluded() {
        val a = payment(1)
        val b = payment(2)
        val notDup = setOf(PaymentDedup.pairOf(1, 2))
        assertTrue(PaymentMerge.duplicateSets(listOf(a, b), notDup).isEmpty())
    }

    @Test
    fun setContaining_findsTheSetOfTheId() {
        val a = payment(1)
        val b = payment(2)
        val sets = PaymentMerge.duplicateSets(listOf(a, b))
        assertEquals(listOf(1L, 2L), PaymentMerge.setContaining(2, sets)?.map { it.id })
        assertNull(PaymentMerge.setContaining(9, sets))
    }

    // ---- 項目の判別

    @Test
    fun fieldDiffs_timeDiffers_isConflict() {
        val s = PaymentMerge.ordered(listOf(snap(payment(1, purchasedAt = "2026-10-05 12:00")), snap(payment(2, purchasedAt = "2026-10-05 12:03"))))
        val diffs = PaymentMerge.fieldDiffs(s)
        val time = diffOf(diffs, PaymentMerge.Field.TIME)
        assertEquals(PaymentMerge.Kind.CONFLICT, time.kind)
        assertTrue(time.differs)
        assertEquals(listOf("12:00", "12:03"), time.candidates.map { it.text })
        assertEquals(listOf("マージ先", "マージ元1"), time.candidates.map { it.label })
        assertTrue(PaymentMerge.hasDifference(diffs))
    }

    @Test
    fun fieldDiffs_storeOnlyOneSideHasName_isFill() {
        val s = PaymentMerge.ordered(listOf(snap(payment(1, store = "")), snap(payment(2, store = "スーパーA"))))
        val store = diffOf(PaymentMerge.fieldDiffs(s), PaymentMerge.Field.STORE)
        assertEquals(PaymentMerge.Kind.FILL, store.kind)
        assertTrue(store.differs)
    }

    @Test
    fun fieldDiffs_sameItems_contentIsSame() {
        val items = listOf(item("牛乳", 200))
        val s = PaymentMerge.ordered(listOf(snap(payment(1), items), snap(payment(2), items)))
        val content = diffOf(PaymentMerge.fieldDiffs(s), PaymentMerge.Field.CONTENT)
        assertEquals(PaymentMerge.Kind.SAME, content.kind)
        assertFalse(content.differs)
    }

    @Test
    fun fieldDiffs_oneSideHasNoItems_contentIsFill() {
        val s = PaymentMerge.ordered(listOf(snap(payment(1)), snap(payment(2), listOf(item("牛乳", 200)))))
        val content = diffOf(PaymentMerge.fieldDiffs(s), PaymentMerge.Field.CONTENT)
        assertEquals(PaymentMerge.Kind.FILL, content.kind)
    }

    @Test
    fun fieldDiffs_differentItems_contentIsConflict() {
        val s = PaymentMerge.ordered(listOf(snap(payment(1), listOf(item("牛乳", 200))), snap(payment(2), listOf(item("牛乳", 300)))))
        val content = diffOf(PaymentMerge.fieldDiffs(s), PaymentMerge.Field.CONTENT)
        assertEquals(PaymentMerge.Kind.CONFLICT, content.kind)
    }

    // ---- 既定の選択

    @Test
    fun defaultChoices_neverContainsConflict() {
        val s = PaymentMerge.ordered(listOf(snap(payment(1, purchasedAt = "2026-10-05 12:00")), snap(payment(2, purchasedAt = "2026-10-05 12:03"))))
        val choices = PaymentMerge.defaultChoices(PaymentMerge.fieldDiffs(s))
        assertFalse(choices.containsKey(PaymentMerge.Field.TIME))
        assertEquals(PaymentMerge.Choice.From(1L), choices[PaymentMerge.Field.DATE])
    }

    // ---- plan

    @Test
    fun plan_manualTime_setsPurchasedAt() {
        val s = PaymentMerge.ordered(listOf(snap(payment(1, purchasedAt = "2026-10-05 12:00")), snap(payment(2, purchasedAt = "2026-10-05 12:03"))))
        val diffs = PaymentMerge.fieldDiffs(s)
        val choices = PaymentMerge.defaultChoices(diffs) + (PaymentMerge.Field.TIME to PaymentMerge.Choice.Manual("12:01"))
        val plan = PaymentMerge.plan(s, choices)
        assertEquals("2026-10-05 12:01", plan.payment.purchasedAt)
        assertEquals(1L, plan.payment.id)
        assertEquals(listOf(2L), plan.removed)
    }

    @Test
    fun plan_contentFromMerged_copiesAdvanceFields() {
        val s = PaymentMerge.ordered(
            listOf(
                snap(payment(1)),
                snap(payment(2, isAdvance = true, ownAmount = 800, headcount = 2)),
            ),
        )
        val choices = PaymentMerge.defaultChoices(PaymentMerge.fieldDiffs(s)) +
            (PaymentMerge.Field.CONTENT to PaymentMerge.Choice.From(2L))
        val plan = PaymentMerge.plan(s, choices)
        assertEquals(2L, plan.contentFrom)
        assertTrue(plan.payment.isAdvance)
        assertEquals(800, plan.payment.ownAmount)
        assertEquals(2, plan.payment.headcount)
    }

    @Test
    fun plan_anyConfirmed_makesStatusConfirmed() {
        val s = PaymentMerge.ordered(listOf(snap(payment(1)), snap(payment(2, status = PaymentStatus.CONFIRMED))))
        val plan = PaymentMerge.plan(s, PaymentMerge.defaultChoices(PaymentMerge.fieldDiffs(s)))
        assertEquals(PaymentStatus.CONFIRMED, plan.payment.status)
    }

    @Test
    fun plan_allDraft_staysDraft() {
        val s = PaymentMerge.ordered(listOf(snap(payment(1)), snap(payment(2))))
        val plan = PaymentMerge.plan(s, PaymentMerge.defaultChoices(PaymentMerge.fieldDiffs(s)))
        assertEquals(PaymentStatus.DRAFT, plan.payment.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun plan_unselectedConflict_throws() {
        val s = PaymentMerge.ordered(listOf(snap(payment(1, purchasedAt = "2026-10-05 12:00")), snap(payment(2, purchasedAt = "2026-10-05 12:03"))))
        PaymentMerge.plan(s, emptyMap())
    }

    @Test(expected = IllegalArgumentException::class)
    fun plan_fromUnknownId_throws() {
        val s = PaymentMerge.ordered(listOf(snap(payment(1)), snap(payment(2))))
        PaymentMerge.plan(s, mapOf(PaymentMerge.Field.CONTENT to PaymentMerge.Choice.From(99L)))
    }

    // ---- 手入力の検証

    @Test
    fun validate_invalidDate_returnsError() {
        assertNotNull(PaymentMerge.validate(PaymentMerge.Field.DATE, "2026-13-01"))
        assertNull(PaymentMerge.validate(PaymentMerge.Field.DATE, "2026-10-29"))
    }

    @Test
    fun validate_emptyTime_isOk_andBadTimeIsError() {
        assertNull(PaymentMerge.validate(PaymentMerge.Field.TIME, ""))
        assertNull(PaymentMerge.validate(PaymentMerge.Field.TIME, "12:01"))
        assertNotNull(PaymentMerge.validate(PaymentMerge.Field.TIME, "25:00"))
    }

    @Test
    fun canManual_onlyDateTimeStore() {
        assertTrue(PaymentMerge.canManual(PaymentMerge.Field.DATE))
        assertTrue(PaymentMerge.canManual(PaymentMerge.Field.TIME))
        assertTrue(PaymentMerge.canManual(PaymentMerge.Field.STORE))
        assertFalse(PaymentMerge.canManual(PaymentMerge.Field.TOTAL))
        assertFalse(PaymentMerge.canManual(PaymentMerge.Field.IMAGE))
        assertFalse(PaymentMerge.canManual(PaymentMerge.Field.CONTENT))
    }
}
