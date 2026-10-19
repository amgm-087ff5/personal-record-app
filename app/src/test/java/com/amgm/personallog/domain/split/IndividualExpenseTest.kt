package com.amgm.personallog.domain.split

import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 個別支出(IndividualExpenses)の受け入れテスト。
 * 人(参加者 id): 1=自分、2〜5=他の人。order は id−1。
 */
class IndividualExpenseTest {
    private val people = listOf(
        SplitPerson(1L, isSelf = true, order = 0),
        SplitPerson(2L, isSelf = false, order = 1),
        SplitPerson(3L, isSelf = false, order = 2),
        SplitPerson(4L, isSelf = false, order = 3),
        SplitPerson(5L, isSelf = false, order = 4),
    )

    private fun input(
        mode: String,
        total: Int,
        groups: List<SplitGroupInput>,
        itemShares: Map<Long, List<ShareInput>> = emptyMap(),
        wholeShares: List<ShareInput> = emptyList(),
        wholeKind: String = ShareKind.AMOUNT,
    ) = SplitInput(
        total = total,
        mode = mode,
        people = people,
        groups = groups,
        itemShares = itemShares,
        wholeShares = wholeShares,
        wholeKind = wholeKind,
    )

    @Test
    fun sausageRatioEatenFollowsPieces() {
        // ソーセージ7個400円を 2:2:1:1:1 で食べた(食べた分どおり)。自分は2個、2人目も2個。
        val groups = listOf(SplitGroupInput(id = 10L, amount = 400, pieceCount = 7))
        val shares = mapOf(
            10L to listOf(
                ShareInput(1L, ShareKind.RATIO, weight = 2),
                ShareInput(2L, ShareKind.RATIO, weight = 2),
                ShareInput(3L, ShareKind.RATIO, weight = 1),
                ShareInput(4L, ShareKind.RATIO, weight = 1),
                ShareInput(5L, ShareKind.RATIO, weight = 1),
            ),
        )
        val input = input(SplitMode.ITEM, total = 400, groups = groups, itemShares = shares)
        val names = mapOf(10L to "ソーセージ")

        val self = IndividualExpenses.of(input, 1L, names)
        assertEquals(1, self.lines.size)
        val line = self.lines[0]
        assertEquals("ソーセージ", line.name)
        assertEquals("2/7個", line.share)
        assertEquals(114, line.eaten)
        assertEquals(114, line.burden)
        assertEquals(false, line.unset)
        assertEquals(114, self.eatenTotal)
        assertEquals(114, self.burdenTotal)
        // 食べた分どおりなら負担 = 食べた分、差は 0
        assertEquals(0, self.difference)
        assertNull(self.wholeShare)

        val second = IndividualExpenses.of(input, 2L, names)
        assertEquals("2/7個", second.lines[0].share)
        assertEquals(115, second.eatenTotal)
    }

    @Test
    fun wholeAmountSelfPaysThreeThousandShowsPlusDifference() {
        // 支払い5000円。品目は 3000円(2人目が食べた)と 2000円(自分が食べた)。
        // 全体で割る(金額): 自分は3000円、残り2000円を他の4人で均等に割る。
        val groups = listOf(
            SplitGroupInput(id = 10L, amount = 3000),
            SplitGroupInput(id = 11L, amount = 2000),
        )
        val shares = mapOf(
            10L to listOf(ShareInput(2L, ShareKind.ASSIGN)),
            11L to listOf(ShareInput(1L, ShareKind.ASSIGN)),
        )
        val whole = listOf(ShareInput(1L, ShareKind.AMOUNT, amount = 3000))
        val input = input(
            SplitMode.WHOLE,
            total = 5000,
            groups = groups,
            itemShares = shares,
            wholeShares = whole,
            wholeKind = ShareKind.AMOUNT,
        )

        val self = IndividualExpenses.of(input, 1L)
        // 食べた分は 2000円、負担は 3000円。多めに払った 1000円。
        assertEquals(2000, self.eatenTotal)
        assertEquals(3000, self.burdenTotal)
        assertEquals(1000, self.difference)
        assertEquals("3000円", self.wholeShare)
        assertEquals(2, self.lines.size)
        val g11 = self.lines.first { it.groupId == 11L }
        assertEquals(2000, g11.eaten)
        assertEquals(1200, g11.burden)
        assertEquals("全額", g11.share)
        val g10 = self.lines.first { it.groupId == 10L }
        assertEquals(0, g10.eaten)
        assertEquals(1800, g10.burden)

        // 2人目は食べた分 3000円、負担は 500円。多めに払ってもらった 2500円(マイナス)。
        val second = IndividualExpenses.of(input, 2L)
        assertEquals(3000, second.eatenTotal)
        assertEquals(500, second.burdenTotal)
        assertEquals(-2500, second.difference)
        assertEquals("残りを均等", second.wholeShare)
    }

    @Test
    fun sharedUnsetGroupIsSplitEquallyAndMarkedUnset() {
        // 共通費(袋代)1000円で割当なし。参加者5人で均等。
        val groups = listOf(SplitGroupInput(id = 10L, amount = 1000, isShared = true))
        val input = input(SplitMode.ITEM, total = 1000, groups = groups)

        val self = IndividualExpenses.of(input, 1L)
        assertEquals(1, self.lines.size)
        val line = self.lines[0]
        assertEquals("1/5(共通費は均等)", line.share)
        assertEquals(200, line.eaten)
        assertEquals(true, line.unset)
        assertEquals(true, line.isShared)
        assertEquals(200, self.eatenTotal)
    }

    @Test
    fun unsetPrivateGroupGoesToSelfAndOthersHaveNoLines() {
        // 割当なしの品目は自分に計上。他の人には行が出ない。
        val groups = listOf(SplitGroupInput(id = 10L, amount = 300))
        val input = input(SplitMode.ITEM, total = 300, groups = groups)

        val self = IndividualExpenses.of(input, 1L)
        assertEquals(1, self.lines.size)
        assertEquals("全額(自分)", self.lines[0].share)
        assertEquals(300, self.lines[0].eaten)
        assertEquals(true, self.lines[0].unset)
        assertEquals(false, self.lines[0].isShared)

        val other = IndividualExpenses.of(input, 2L)
        assertEquals(0, other.lines.size)
        assertEquals(0, other.eatenTotal)
        assertEquals(0, other.burdenTotal)
    }

    @Test
    fun residualIsShownAsSeparateAdjustmentLine() {
        // 品目 1000円(2人目が食べた)、支払い 1010円。残差 10円は5人で均等(各2円)。
        val groups = listOf(SplitGroupInput(id = 10L, amount = 1000))
        val shares = mapOf(10L to listOf(ShareInput(2L, ShareKind.ASSIGN)))
        val input = input(SplitMode.ITEM, total = 1010, groups = groups, itemShares = shares)

        val self = IndividualExpenses.of(input, 1L)
        assertEquals(1, self.lines.size)
        val adjustment = self.lines[0]
        assertNull(adjustment.groupId)
        assertEquals("残差・調整", adjustment.name)
        assertEquals(2, adjustment.eaten)
        assertEquals(2, self.eatenTotal)

        val second = IndividualExpenses.of(input, 2L)
        assertEquals(2, second.lines.size)
        assertEquals(1002, second.eatenTotal)
    }
}
