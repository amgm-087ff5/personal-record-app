package com.amgm.personallog.domain.split

import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 割り勘の計算(SplitCalculator)の受け入れテスト。
 * 人: 1=髙木(自分・支払者の既定)、2=山田、3=佐藤、4=D、5=E。order は id−1。
 * 各テストで result.total == 支払いの合計 も確かめる。
 */
class SplitCalculatorTest {
    private val people = listOf(
        SplitPerson(1L, isSelf = true, order = 0),
        SplitPerson(2L, isSelf = false, order = 1),
        SplitPerson(3L, isSelf = false, order = 2),
        SplitPerson(4L, isSelf = false, order = 3),
        SplitPerson(5L, isSelf = false, order = 4),
    )

    private fun input(
        mode: String,
        groups: List<SplitGroupInput> = emptyList(),
        total: Int? = null,
        itemShares: Map<Long, List<ShareInput>> = emptyMap(),
        wholeShares: List<ShareInput> = emptyList(),
        payers: List<PayerInput> = emptyList(),
        people: List<SplitPerson> = this.people,
        wholeKind: String = ShareKind.AMOUNT,
    ) = SplitInput(
        total = total,
        mode = mode,
        people = people,
        groups = groups,
        itemShares = itemShares,
        wholeShares = wholeShares,
        payers = payers,
        wholeKind = wholeKind,
    )

    private fun equal(vararg ids: Long) = ids.map { ShareInput(it, ShareKind.EQUAL) }
    private fun ratio(vararg pairs: Pair<Long, Int>) = pairs.map { ShareInput(it.first, ShareKind.RATIO, weight = it.second) }
    private fun amounts(vararg pairs: Pair<Long, Int>) = pairs.map { ShareInput(it.first, ShareKind.AMOUNT, amount = it.second) }

    @Test
    fun itemAmount_mismatchWarnsAndPutsDifferenceOnSelfAsProvisional() {
        // 辛味チキン 500 円。髙木200・山田100・佐藤100 で 400 円。差 100 は自分の仮計上。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 500)),
                itemShares = mapOf(1L to amounts(1L to 200, 2L to 100, 3L to 100)),
            ),
        )
        assertEquals(500, result.total)
        assertEquals(listOf(SplitWarning.GroupAmountMismatch(1L, 500, 400)), result.warnings)
        val provisional = result.lines.filter { it.provisional }
        assertEquals(listOf(SplitLine(1L, 1L, 100, provisional = true)), provisional)
        assertEquals(300, result.totalOf(1L))
    }

    @Test
    fun itemEqual3People_400_splitsWithPayerLast() {
        // 支払者=自分(髙木)。400 は 133/134/133。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 400)),
                itemShares = mapOf(1L to equal(1L, 2L, 3L)),
            ),
        )
        assertEquals(400, result.total)
        assertEquals(133, result.totalOf(1L))
        assertEquals(134, result.totalOf(2L))
        assertEquals(133, result.totalOf(3L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun itemEqual3People_500_splitsEvenly() {
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 500)),
                itemShares = mapOf(1L to equal(1L, 2L, 3L)),
            ),
        )
        assertEquals(500, result.total)
        assertEquals(166, result.totalOf(1L))
        assertEquals(167, result.totalOf(2L))
        assertEquals(167, result.totalOf(3L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun itemAssignOnePerson_getsFullAmountWithoutWarning() {
        // 山田1人に 400。割り勘にならない。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 400)),
                itemShares = mapOf(1L to listOf(ShareInput(2L, ShareKind.ASSIGN))),
            ),
        )
        assertEquals(400, result.total)
        assertEquals(400, result.totalOf(2L))
        assertEquals(0, result.totalOf(1L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun wholeEqual3People_500() {
        // 支払い全体を3人で均等。166/167/167。
        // 仕様変更: 全体方式の対象は people 全員。3人の場合は people を3人に絞り、旧データの EQUAL として扱う。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.WHOLE,
                total = 500,
                wholeShares = equal(1L, 2L, 3L),
                people = people.take(3),
                wholeKind = ShareKind.EQUAL,
            ),
        )
        assertEquals(500, result.total)
        assertEquals(166, result.totalOf(1L))
        assertEquals(167, result.totalOf(2L))
        assertEquals(167, result.totalOf(3L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun wholeAmount_totalMismatchWarnsAndPutsDifferenceOnSelfAsProvisional() {
        // 合計505 に対し 山田300・佐藤200 で 500。差 5 は自分の仮計上。
        // 仕様変更: 空欄(行が無い人)は残額を均等に割るため、この期待値を出すには全員に金額を入れる必要がある。
        // 3人に絞り、自分は 0 円を指定する(空欄にしないため)。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.WHOLE,
                total = 505,
                wholeShares = amounts(1L to 0, 2L to 300, 3L to 200),
                people = people.take(3),
            ),
        )
        assertEquals(505, result.total)
        assertEquals(listOf(SplitWarning.WholeAmountMismatch(505, 500)), result.warnings)
        assertEquals(listOf(SplitLine(1L, null, 5, provisional = true)), result.lines.filter { it.provisional })
        assertEquals(300, result.totalOf(2L))
        assertEquals(200, result.totalOf(3L))
    }

    @Test
    fun itemRatio_800With3_3_2() {
        // 辛味チキン2皿を1まとまり 800 円。3:3:2 で 300/300/200。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 800)),
                itemShares = mapOf(1L to ratio(1L to 3, 2L to 3, 3L to 2)),
            ),
        )
        assertEquals(800, result.total)
        assertEquals(300, result.totalOf(1L))
        assertEquals(300, result.totalOf(2L))
        assertEquals(200, result.totalOf(3L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun itemRatio_400With2_2_1_1_1() {
        // ソーセージ 400 円を 2:2:1:1:1。114/115/57/57/57。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 400)),
                itemShares = mapOf(1L to ratio(1L to 2, 2L to 2, 3L to 1, 4L to 1, 5L to 1)),
            ),
        )
        assertEquals(400, result.total)
        assertEquals(114, result.totalOf(1L))
        assertEquals(115, result.totalOf(2L))
        assertEquals(57, result.totalOf(3L))
        assertEquals(57, result.totalOf(4L))
        assertEquals(57, result.totalOf(5L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun itemAmount_exactWithoutWarning() {
        // 1000 円を 400/400/200 で指定。合計が一致するので警告なし。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 1000)),
                itemShares = mapOf(1L to amounts(1L to 400, 2L to 400, 3L to 200)),
            ),
        )
        assertEquals(1000, result.total)
        assertEquals(400, result.totalOf(1L))
        assertEquals(400, result.totalOf(2L))
        assertEquals(200, result.totalOf(3L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun itemResidual_splitsEquallyAndWarnsWhenAtLeastTwo() {
        // 品目合計 500(全員均等)に対し合計 505。W3(5)。
        // 500 の内訳は 髙木166・山田167・佐藤167。
        // 仕様変更: 残差の参加者 P は people 全員(5人)になったので、残差5は5人で1円ずつ。
        // 旧期待値(3人で 1/2/2 → 167/169/169)から、167/168/168/1/1 に変更。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 500)),
                total = 505,
                itemShares = mapOf(1L to equal(1L, 2L, 3L)),
            ),
        )
        assertEquals(505, result.total)
        assertEquals(listOf(SplitWarning.ItemsTotalMismatch(5)), result.warnings)
        assertEquals(167, result.totalOf(1L))
        assertEquals(168, result.totalOf(2L))
        assertEquals(168, result.totalOf(3L))
        assertEquals(1, result.totalOf(4L))
        assertEquals(1, result.totalOf(5L))
    }

    @Test
    fun itemSharedCost_splitsAmongAllPeople() {
        // 袋代5円は割当なしの共通費。仕様変更で参加者 P は people 全員(5人)。
        // 5 を5人で1円ずつ。割当は 600 円を山田(2)へ。
        // 旧期待値(参加者 = 自分+山田の2人で 髙木2・山田603)から、髙木1・山田601・他3人1ずつ に変更。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(
                    SplitGroupInput(11L, 5, isShared = true),
                    SplitGroupInput(12L, 600),
                ),
                itemShares = mapOf(12L to listOf(ShareInput(2L, ShareKind.ASSIGN))),
            ),
        )
        assertEquals(605, result.total)
        assertEquals(1, result.totalOf(1L))
        assertEquals(601, result.totalOf(2L))
        assertEquals(1, result.totalOf(3L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun itemRatio_negativeAmountGivesPayerTheRemainderFirst() {
        // 値引き -50 を 1:2。支払者(髙木)が先に端数を受けて -17 / -33。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, -50)),
                itemShares = mapOf(1L to ratio(1L to 1, 2L to 2)),
            ),
        )
        assertEquals(-50, result.total)
        assertEquals(-17, result.totalOf(1L))
        assertEquals(-33, result.totalOf(2L))
    }

    @Test
    fun itemRatio_zeroTotalWeight_warnsAndGivesSelfTheFullAmount() {
        // 割合の合計が0。W5 を出して自分に全額。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 300)),
                itemShares = mapOf(1L to ratio(2L to 0, 3L to 0)),
            ),
        )
        assertEquals(300, result.total)
        assertEquals(listOf(SplitWarning.InvalidShares(1L, "zero total weight")), result.warnings)
        assertEquals(300, result.totalOf(1L))
    }

    @Test
    fun itemMixedKinds_warnsAndGivesSelfTheFullAmount() {
        // 同じまとまりで均等と1人指定が混ざる。W5 を出して自分に全額。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 300)),
                itemShares = mapOf(
                    1L to listOf(
                        ShareInput(2L, ShareKind.EQUAL),
                        ShareInput(3L, ShareKind.ASSIGN),
                    ),
                ),
            ),
        )
        assertEquals(300, result.total)
        assertEquals(listOf(SplitWarning.InvalidShares(1L, "mixed kinds")), result.warnings)
        assertEquals(300, result.totalOf(1L))
    }

    @Test
    fun itemUnknownPerson_warnsAndGivesSelfTheFullAmount() {
        // 存在しない人(99)への割当は捨てる。残りが無いので自分に全額。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.ITEM,
                groups = listOf(SplitGroupInput(1L, 300)),
                itemShares = mapOf(1L to listOf(ShareInput(99L, ShareKind.ASSIGN))),
            ),
        )
        assertEquals(300, result.total)
        assertEquals(listOf(SplitWarning.InvalidShares(1L, "unknown person")), result.warnings)
        assertEquals(300, result.totalOf(1L))
    }

    @Test
    fun selfMode_givesEverythingToSelfWithoutWarnings() {
        val result = SplitCalculator.calculate(
            input(
                SplitMode.SELF,
                groups = listOf(SplitGroupInput(1L, 300), SplitGroupInput(2L, 200)),
            ),
        )
        assertEquals(500, result.total)
        assertEquals(500, result.totalOf(1L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun wholeRatio_2_3_2_withPayerSelf() {
        // 支払い全体 400 円を 2:3:2 で割る(支払者=髙木)。
        // 手計算: 800/7=114 余り2、1200/7=171 余り3、800/7=114 余り2。
        // 基本額の合計 399 で残り1円。非支払者で余りが最大の山田(余り3)へ。
        // よって 114 / 172 / 114。
        // 仕様変更: 割合の対象は people 全員。3人に絞り、wholeKind を RATIO と明示する(期待値は不変)。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.WHOLE,
                total = 400,
                wholeShares = ratio(1L to 2, 2L to 3, 3L to 2),
                people = people.take(3),
                wholeKind = ShareKind.RATIO,
            ),
        )
        assertEquals(400, result.total)
        assertEquals(114, result.totalOf(1L))
        assertEquals(172, result.totalOf(2L))
        assertEquals(114, result.totalOf(3L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun payersMismatch_warnsAndKeepsComputing() {
        // 支払者の合計 900 が支払いの合計 1000 と合わない。W4 を出す(計算は続ける)。
        val result = SplitCalculator.calculate(
            input(
                SplitMode.SELF,
                groups = listOf(SplitGroupInput(1L, 1000)),
                payers = listOf(PayerInput(1L, 600), PayerInput(2L, 300)),
            ),
        )
        assertEquals(1000, result.total)
        assertEquals(listOf(SplitWarning.PayersMismatch(1000, 900)), result.warnings)
    }

    @Test
    fun noSelfPerson_throwsIllegalArgument() {
        // 自分(isSelf)が0人なら例外。
        val noSelf = people.map { it.copy(isSelf = false) }
        try {
            SplitCalculator.calculate(input(SplitMode.SELF, groups = listOf(SplitGroupInput(1L, 100)), people = noSelf))
            fail("IllegalArgumentException が出るはず")
        } catch (e: IllegalArgumentException) {
            // 期待どおり
        }
    }
}
