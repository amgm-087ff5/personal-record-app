package com.amgm.personallog.data.repository

import com.amgm.personallog.domain.split.ParticipantFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 支払い側の人数の変更を割り勘の参加者へ合わせる計画(planHeadcountChange)の判定。 */
class ParticipantSyncServiceTest {

    /** 名前あり・割当なし・支出なしの参加者。 */
    private fun named(id: Long, order: Int = id.toInt()) =
        ParticipantFacts(id = id, isSelf = false, hasName = true, hasAllocation = false, hasExpense = false, sortOrder = order)

    /** 名前なし・割当なし・支出なし(空欄のメンバー)。 */
    private fun nameless(id: Long, order: Int = id.toInt()) =
        ParticipantFacts(id = id, isSelf = false, hasName = false, hasAllocation = false, hasExpense = false, sortOrder = order)

    private fun self(id: Long, order: Int = id.toInt()) =
        ParticipantFacts(id = id, isSelf = true, hasName = true, hasAllocation = false, hasExpense = false, sortOrder = order)

    @Test
    fun increase_addsBlankParticipants_andRemovesNothing() {
        val change = planHeadcountChange(listOf(self(0)), target = 3)
        assertEquals(2, change.add)
        assertTrue(change.autoRemoveIds.isEmpty())
        assertTrue(change.choiceIds.isEmpty())
        assertFalse(change.needsChoice)
        assertFalse(change.isEmpty)
    }

    @Test
    fun sameCount_isEmpty() {
        val change = planHeadcountChange(listOf(self(0), named(1)), target = 2)
        assertTrue(change.isEmpty)
    }

    @Test
    fun targetBelowOne_isTreatedAsOne() {
        // 1人のときに 0 は変更なし
        assertTrue(planHeadcountChange(listOf(self(0)), target = 0).isEmpty)
        // 3人のとき 0 は 1 人扱い。空欄の2人は確認なしで消える
        val change = planHeadcountChange(listOf(self(0), nameless(1), nameless(2)), target = 0)
        assertEquals(listOf(2L, 1L), change.autoRemoveIds)
        assertFalse(change.needsChoice)
    }

    @Test
    fun decrease_removesBlankRowsAutomatically_fromTheTail() {
        val participants = listOf(self(0), named(1), nameless(2), nameless(3))
        val change = planHeadcountChange(participants, target = 2)
        assertEquals(listOf(3L, 2L), change.autoRemoveIds)
        assertEquals(0, change.chooseCount)
        assertFalse(change.needsChoice)
        assertEquals(0, change.add)
    }

    @Test
    fun decrease_withoutBlankRows_needsChoice_andSelfIsACandidate() {
        val participants = listOf(self(0), named(1), named(2))
        val change = planHeadcountChange(participants, target = 1)
        assertTrue(change.autoRemoveIds.isEmpty())
        // 末尾から候補。自分も含む
        assertEquals(listOf(2L, 1L, 0L), change.choiceIds)
        assertEquals(2, change.chooseCount)
        assertTrue(change.needsChoice)
    }

    @Test
    fun choice_mustBeExactlyTheRequiredCount() {
        val change = planHeadcountChange(listOf(self(0), named(1), named(2)), target = 1)
        assertTrue(change.isValidChoice(setOf(1L, 2L)))
        assertTrue(change.isValidChoice(setOf(0L, 2L)))
        // 足りない・多すぎるときは決定できない(キャンセル相当は VM で人数を戻す)
        assertFalse(change.isValidChoice(setOf(2L)))
        assertFalse(change.isValidChoice(emptySet()))
        assertFalse(change.isValidChoice(setOf(0L, 1L, 2L)))
    }

    @Test
    fun mixed_autoRemovesOnlyBlankRows_thenChoosesTheRest() {
        // 0 自分, 1 空欄, 2 名前あり, 3 空欄だが割当あり(消せない)。target 2 なので 2 人減らす
        val participants = listOf(
            self(0),
            nameless(1),
            named(2),
            ParticipantFacts(id = 3, isSelf = false, hasName = false, hasAllocation = true, hasExpense = false, sortOrder = 3),
        )
        val change = planHeadcountChange(participants, target = 2)
        assertEquals(listOf(1L), change.autoRemoveIds)
        // 残りは 1 人だけ選ぶ。候補は末尾から(自動で消した 1 以外)
        assertEquals(listOf(3L, 2L, 0L), change.choiceIds)
        assertEquals(1, change.chooseCount)
    }

    @Test
    fun payer_isNotAutoRemoved() {
        // 名前なしでも支払った人(支出あり)は自動では消さない
        val payer = ParticipantFacts(id = 2, isSelf = false, hasName = false, hasAllocation = false, hasExpense = true, sortOrder = 2)
        val change = planHeadcountChange(listOf(self(0), nameless(1), payer), target = 2)
        assertEquals(listOf(1L), change.autoRemoveIds)
        assertEquals(0, change.chooseCount)
    }

    @Test
    fun self_isNotAutoRemoved_evenWithoutName() {
        val selfNoName = ParticipantFacts(id = 0, isSelf = true, hasName = false, hasAllocation = false, hasExpense = false, sortOrder = 0)
        val change = planHeadcountChange(listOf(selfNoName, nameless(1)), target = 1)
        assertEquals(listOf(1L), change.autoRemoveIds)
        assertEquals(0, change.chooseCount)
        assertFalse(change.autoRemoveIds.contains(0L))
    }
}
