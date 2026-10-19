package com.amgm.personallog.domain.split

import org.junit.Assert.assertEquals
import org.junit.Test

/** 参加人数を変える純関数(shortfall / excess / removalPlan)の期待値テスト。 */
class HeadcountSyncTest {

    // ---- shortfall / excess ----

    @Test
    fun 目標が0以下は1人扱い() {
        assertEquals(1, shortfall(0, 0))
        assertEquals(0, shortfall(1, 0))
        assertEquals(0, excess(1, 0))
        assertEquals(1, excess(2, 0))
        assertEquals(0, shortfall(0, -3))
        assertEquals(2, excess(3, -3))
    }

    @Test
    fun 同数なら増減は0() {
        assertEquals(0, shortfall(3, 3))
        assertEquals(0, excess(3, 3))
    }

    @Test
    fun 増やす人数と減らす人数() {
        assertEquals(4, shortfall(2, 6))
        assertEquals(0, excess(2, 6))
        assertEquals(0, shortfall(6, 2))
        assertEquals(4, excess(6, 2))
    }

    @Test
    fun 増減は今の人数と目標の差だけ() {
        assertEquals(1, shortfall(4, 5))
        assertEquals(1, excess(5, 4))
    }

    // ---- removalPlan ----

    private fun fact(
        id: Long,
        order: Int,
        isSelf: Boolean = false,
        name: Boolean = false,
        allocation: Boolean = false,
        expense: Boolean = false,
    ) = ParticipantFacts(
        id = id,
        isSelf = isSelf,
        hasName = name,
        hasAllocation = allocation,
        hasExpense = expense,
        sortOrder = order,
    )

    @Test
    fun 減らす数が0なら何もしない() {
        val plan = removalPlan(listOf(fact(1, 0, isSelf = true), fact(-2, 1)), 0)
        assertEquals(RemovalPlan(emptyList(), emptyList(), 0), plan)
    }

    @Test
    fun 名前も割当も支出も無い行を末尾から自動で消す() {
        val list = listOf(
            fact(1, 0, isSelf = true, name = true),
            fact(-2, 1),
            fact(-3, 2),
            fact(-4, 3),
        )
        val plan = removalPlan(list, 2)
        assertEquals(listOf(-4L, -3L), plan.autoRemoveIds)
        assertEquals(emptyList<Long>(), plan.needsChoice)
        assertEquals(0, plan.remainingExcess)
    }

    @Test
    fun 自動で消すのは必要な数まで() {
        val list = listOf(fact(1, 0, isSelf = true, name = true), fact(-2, 1), fact(-3, 2))
        val plan = removalPlan(list, 1)
        assertEquals(listOf(-3L), plan.autoRemoveIds)
        assertEquals(emptyList<Long>(), plan.needsChoice)
        assertEquals(0, plan.remainingExcess)
    }

    @Test
    fun 名前がある行は自動では消さず選択候補に入る() {
        val list = listOf(
            fact(1, 0, isSelf = true, name = true),
            fact(-2, 1, name = true),
            fact(-3, 2),
        )
        val plan = removalPlan(list, 1)
        assertEquals(listOf(-3L), plan.autoRemoveIds)
        assertEquals(0, plan.remainingExcess)
    }

    @Test
    fun 割当がある行は自動で消さない() {
        val list = listOf(fact(1, 0, isSelf = true, name = true), fact(-2, 1, allocation = true))
        val plan = removalPlan(list, 1)
        assertEquals(emptyList<Long>(), plan.autoRemoveIds)
        assertEquals(listOf(-2L, 1L), plan.needsChoice)
        assertEquals(0, plan.remainingExcess)
    }

    @Test
    fun 支出がある行は自動で消さない() {
        val list = listOf(fact(1, 0, isSelf = true, name = true), fact(-2, 1, expense = true))
        val plan = removalPlan(list, 1)
        assertEquals(emptyList<Long>(), plan.autoRemoveIds)
        assertEquals(listOf(-2L, 1L), plan.needsChoice)
    }

    @Test
    fun 選択候補には自分も入る() {
        val list = listOf(fact(1, 0, isSelf = true, name = true), fact(-2, 1, name = true))
        val plan = removalPlan(list, 1)
        assertEquals(emptyList<Long>(), plan.autoRemoveIds)
        // 末尾から。自分も候補に入る(名前が無くても自分は自動では消さない)
        assertEquals(listOf(-2L, 1L), plan.needsChoice)
        assertEquals(0, plan.remainingExcess)
    }

    @Test
    fun 自分だけで名前も無い行でも自動では消さない() {
        val list = listOf(fact(1, 0, isSelf = true))
        val plan = removalPlan(list, 1)
        assertEquals(emptyList<Long>(), plan.autoRemoveIds)
        assertEquals(listOf(1L), plan.needsChoice)
        assertEquals(0, plan.remainingExcess)
    }

    @Test
    fun 候補を全部使っても足りない数はremainingExcessに入る() {
        val list = listOf(fact(1, 0, isSelf = true, name = true), fact(-2, 1, name = true))
        val plan = removalPlan(list, 4)
        assertEquals(emptyList<Long>(), plan.autoRemoveIds)
        assertEquals(listOf(-2L, 1L), plan.needsChoice)
        assertEquals(2, plan.remainingExcess)
    }

    @Test
    fun 並び順ではなく並び順の値で末尾を判定する() {
        // 渡す順が並び順と逆でも、sortOrder の大きい行を末尾とみなす
        val list = listOf(fact(-3, 2), fact(1, 0, isSelf = true, name = true), fact(-2, 1))
        val plan = removalPlan(list, 1)
        assertEquals(listOf(-3L), plan.autoRemoveIds)
    }
}
