package com.amgm.personallog.domain.split

import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 食べた記録(MealRecord)の期待値テスト。人: 1=自分、2=山田。
 * 支払いの total は null(まとまりの合計)にして、残差の影響を出さない。
 */
class MealRecordTest {
    private val me = 1L
    private val other = 2L
    private val names = mapOf(10L to listOf("唐揚げ"), 11L to listOf("サラダ", "ポテト"), 12L to listOf("袋代"))

    private fun input(
        people: List<SplitPerson>,
        groups: List<SplitGroupInput>,
        itemShares: Map<Long, List<ShareInput>> = emptyMap(),
    ) = SplitInput(total = null, mode = SplitMode.ITEM, people = people, groups = groups, itemShares = itemShares)

    private val selfOnly = listOf(SplitPerson(id = me, isSelf = true, order = 0))
    private val two = listOf(
        SplitPerson(id = me, isSelf = true, order = 0),
        SplitPerson(id = other, isSelf = false, order = 1),
    )

    private fun meal(input: SplitInput, paymentId: Long? = 100L, draft: Boolean = false) =
        MealRecord.ofPayment(
            paymentId = paymentId,
            at = "2026-10-01 12:30",
            storeName = "店",
            draft = draft,
            input = input,
            groupItemNames = names,
            selfId = me,
        )

    @Test
    fun selfOnlyMarksEveryNonSharedGroupAsWhole() {
        val input = input(
            people = selfOnly,
            groups = listOf(
                SplitGroupInput(id = 10L, amount = 300),
                SplitGroupInput(id = 11L, amount = 500),
                SplitGroupInput(id = 12L, amount = 200, isShared = true),
            ),
        )
        val m = meal(input)
        assertEquals(2, m.rows.size)
        assertEquals("全部", m.rows[0].quantity)
        assertEquals(300, m.rows[0].amount)
        assertEquals("唐揚げ", m.rows[0].name)
        assertEquals("サラダ ほか1品", m.rows[1].name)
        assertEquals(0, m.unsetGroups)
        assertEquals(100L, m.rows[0].paymentId)
        assertEquals("店", m.rows[0].storeName)
    }

    @Test
    fun equalSplitGivesOneOverPeopleAndSharedGroupIsHidden() {
        val input = input(
            people = two,
            groups = listOf(
                SplitGroupInput(id = 10L, amount = 300),
                SplitGroupInput(id = 12L, amount = 200, isShared = true),
            ),
            itemShares = mapOf(10L to listOf(ShareInput(me, ShareKind.EQUAL), ShareInput(other, ShareKind.EQUAL))),
        )
        val m = meal(input)
        assertEquals(1, m.rows.size)
        assertEquals("1/2", m.rows[0].quantity)
        assertEquals(150, m.rows[0].amount)
        assertEquals(0, m.unsetGroups)
    }

    @Test
    fun assignToSelfAloneIsWhole() {
        val input = input(
            people = two,
            groups = listOf(SplitGroupInput(id = 10L, amount = 300)),
            itemShares = mapOf(10L to listOf(ShareInput(me, ShareKind.ASSIGN))),
        )
        val m = meal(input)
        assertEquals("全部", m.rows[0].quantity)
        assertEquals(300, m.rows[0].amount)
    }

    @Test
    fun assignToOtherGivesNoRowForSelf() {
        val input = input(
            people = two,
            groups = listOf(SplitGroupInput(id = 10L, amount = 300)),
            itemShares = mapOf(10L to listOf(ShareInput(other, ShareKind.ASSIGN))),
        )
        val m = meal(input)
        assertTrue(m.rows.isEmpty())
        assertEquals(0, m.unsetGroups)
    }

    @Test
    fun groupWithoutSharesIsCountedAsUnsetNotSelf() {
        val input = input(
            people = two,
            groups = listOf(
                SplitGroupInput(id = 10L, amount = 300),
                SplitGroupInput(id = 11L, amount = 500),
            ),
            itemShares = mapOf(10L to listOf(ShareInput(other, ShareKind.ASSIGN))),
        )
        val m = meal(input)
        assertTrue(m.rows.isEmpty())
        assertEquals(1, m.unsetGroups)
    }

    @Test
    fun ratioWithBlankUsesRemainingPieces() {
        // 4個中、自分は1個(指定)、山田は空欄 → 残り3個は山田。自分は 500 の 1/4 = 125
        val input = input(
            people = two,
            groups = listOf(SplitGroupInput(id = 10L, amount = 500, pieceCount = 4)),
            itemShares = mapOf(
                10L to listOf(
                    ShareInput(me, ShareKind.RATIO, weight = 1),
                    ShareInput(other, ShareKind.RATIO, weight = null),
                ),
            ),
        )
        val m = meal(input)
        assertEquals("1/4個", m.rows[0].quantity)
        assertEquals(125, m.rows[0].amount)
    }

    @Test
    fun ratioWithoutBlankUsesSumOfWeights() {
        // 指定の比 2:1 → 自分は 300 の 2/3 = 200
        val input = input(
            people = two,
            groups = listOf(SplitGroupInput(id = 10L, amount = 300)),
            itemShares = mapOf(
                10L to listOf(
                    ShareInput(me, ShareKind.RATIO, weight = 2),
                    ShareInput(other, ShareKind.RATIO, weight = 1),
                ),
            ),
        )
        val m = meal(input)
        assertEquals("2/3個", m.rows[0].quantity)
        assertEquals(200, m.rows[0].amount)
    }

    @Test
    fun amountKindShowsSelfAmount() {
        val input = input(
            people = two,
            groups = listOf(SplitGroupInput(id = 10L, amount = 300)),
            itemShares = mapOf(
                10L to listOf(
                    ShareInput(me, ShareKind.AMOUNT, amount = 120),
                    ShareInput(other, ShareKind.AMOUNT, amount = null),
                ),
            ),
        )
        val m = meal(input)
        assertEquals("¥120分", m.rows[0].quantity)
        assertEquals(120, m.rows[0].amount)
    }

    @Test
    fun mixedKindsAreUnsetNotSelf() {
        val input = input(
            people = two,
            groups = listOf(SplitGroupInput(id = 10L, amount = 300)),
            itemShares = mapOf(
                10L to listOf(
                    ShareInput(me, ShareKind.ASSIGN),
                    ShareInput(other, ShareKind.EQUAL),
                ),
            ),
        )
        val m = meal(input)
        assertTrue(m.rows.isEmpty())
        assertEquals(1, m.unsetGroups)
    }

    @Test
    fun standaloneRowWithoutPaymentIdAndDraftFlagAreKept() {
        val input = input(
            people = selfOnly,
            groups = listOf(SplitGroupInput(id = 10L, amount = 300)),
        )
        val m = meal(input, paymentId = null, draft = true)
        assertEquals(null, m.paymentId)
        assertEquals(null, m.rows[0].paymentId)
        assertTrue(m.rows[0].draft)
    }

    @Test
    fun selfMissingFromPeopleCountsEveryGroupAsUnset() {
        val input = input(
            people = listOf(
                SplitPerson(id = other, isSelf = false, order = 0),
                SplitPerson(id = 3L, isSelf = false, order = 1),
            ),
            groups = listOf(SplitGroupInput(id = 10L, amount = 300), SplitGroupInput(id = 11L, amount = 200)),
        )
        val m = meal(input)
        assertTrue(m.rows.isEmpty())
        assertEquals(2, m.unsetGroups)
    }

    @Test
    fun groupNameJoinsDistinctNames() {
        assertEquals("唐揚げ", MealRecord.groupName(listOf("唐揚げ", " 唐揚げ ")))
        assertEquals("A ほか1品", MealRecord.groupName(listOf("A", "B", "A")))
        assertEquals("品目", MealRecord.groupName(emptyList()))
    }
}
