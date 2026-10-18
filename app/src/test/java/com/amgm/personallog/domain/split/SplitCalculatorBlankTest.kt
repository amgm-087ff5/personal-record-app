package com.amgm.personallog.domain.split

import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 空欄(金額・割合の未入力)・ラベル金額・個数・参加者 P=全員 の受け入れテスト(SplitCalculator)。
 * 人: 1=髙木(自分・支払者の既定)、2=山田、3=佐藤、4=D、5=E。order は id−1。
 */
class SplitCalculatorBlankTest {
    private val people = listOf(
        SplitPerson(1L, isSelf = true, order = 0),
        SplitPerson(2L, isSelf = false, order = 1),
        SplitPerson(3L, isSelf = false, order = 2),
        SplitPerson(4L, isSelf = false, order = 3),
        SplitPerson(5L, isSelf = false, order = 4),
    )

    /** 金額指定の行。amount=null は空欄。 */
    private fun amt(id: Long, amount: Int?) = ShareInput(id, ShareKind.AMOUNT, amount = amount)

    /** 割合の行。weight=null は空欄。 */
    private fun rat(id: Long, weight: Int?) = ShareInput(id, ShareKind.RATIO, weight = weight)

    private fun item(
        groups: List<SplitGroupInput>,
        shares: Map<Long, List<ShareInput>>,
        people: List<SplitPerson> = this.people,
        total: Int? = null,
    ) = SplitInput(
        total = total,
        mode = SplitMode.ITEM,
        people = people,
        groups = groups,
        itemShares = shares,
    )

    private fun whole(
        total: Int,
        shares: List<ShareInput>,
        kind: String = ShareKind.AMOUNT,
        people: List<SplitPerson> = this.people,
        labelAmounts: Map<String, Int> = emptyMap(),
    ) = SplitInput(
        total = total,
        mode = SplitMode.WHOLE,
        people = people,
        groups = emptyList(),
        wholeShares = shares,
        wholeKind = kind,
        labelAmounts = labelAmounts,
    )

    @Test
    fun itemAmount_blankSharesRemainderEvenly() {
        // 1000 円。髙木300 を指定、山田と佐藤は空欄。残り700 を2人で 350/350。
        val result = SplitCalculator.calculate(
            item(
                groups = listOf(SplitGroupInput(1L, 1000)),
                shares = mapOf(1L to listOf(amt(1L, 300), amt(2L, null), amt(3L, null))),
            ),
        )
        assertEquals(1000, result.total)
        assertEquals(300, result.totalOf(1L))
        assertEquals(350, result.totalOf(2L))
        assertEquals(350, result.totalOf(3L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun itemAmount_blankOverflowGivesBlanksZeroAndWarnsW1() {
        // 1000 円に対し 髙木600・山田500 を指定、佐藤は空欄。指定の合計 1100 で残額が負になるので超過。
        // 佐藤は0円、W1(1, 1000, 1100)、差 −100 は自分の仮計上。
        val result = SplitCalculator.calculate(
            item(
                groups = listOf(SplitGroupInput(1L, 1000)),
                shares = mapOf(1L to listOf(amt(1L, 600), amt(2L, 500), amt(3L, null))),
            ),
        )
        assertEquals(1000, result.total)
        assertEquals(listOf(SplitWarning.GroupAmountMismatch(1L, 1000, 1100)), result.warnings)
        assertEquals(0, result.totalOf(3L))
        assertEquals(listOf(SplitLine(1L, 1L, -100, provisional = true)), result.lines.filter { it.provisional })
        assertEquals(500, result.totalOf(1L))
        assertEquals(500, result.totalOf(2L))
    }

    @Test
    fun wholeLabelAmount_labelPeopleGetLabelAmountAndBlanksSplitTheRest() {
        // 5人、支払い 10000 円。佐藤・D・E は「新人」、新人の金額は 2000 円。他は空欄。
        // 新人2人で 4000、残り 6000 を空欄の3人で 2000 ずつ。全員 2000 円。
        val labeled = people.map { if (it.id >= 4L) it.copy(label = "新人") else it }
        val result = SplitCalculator.calculate(
            whole(10000, shares = emptyList(), people = labeled, labelAmounts = mapOf("新人" to 2000)),
        )
        assertEquals(10000, result.total)
        for (id in 1L..5L) assertEquals(2000, result.totalOf(id))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun wholeLabelAmount_ownAmountWinsOverLabel() {
        // 4=D は本人の金額 1500 を指定(ラベル「新人」の 2000 より優先)。5=E は新人で 2000。
        // 残り 6500 を空欄の 1・2・3 で均等: 2166 余り2 → 非支払者の 2・3 に1円ずつ。
        val labeled = people.map { if (it.id >= 4L) it.copy(label = "新人") else it }
        val result = SplitCalculator.calculate(
            whole(
                10000,
                shares = listOf(amt(4L, 1500)),
                people = labeled,
                labelAmounts = mapOf("新人" to 2000),
            ),
        )
        assertEquals(10000, result.total)
        assertEquals(2166, result.totalOf(1L))
        assertEquals(2167, result.totalOf(2L))
        assertEquals(2167, result.totalOf(3L))
        assertEquals(1500, result.totalOf(4L))
        assertEquals(2000, result.totalOf(5L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun wholeAmount_overflowGivesBlanksZeroAndWarnsW2() {
        // 3人、支払い 1000 円。髙木600・山田500 を指定、佐藤は空欄。指定 1100 で超過。
        // 佐藤は0円、W2(1000, 1100)、差 −100 は自分の仮計上。
        val result = SplitCalculator.calculate(
            whole(
                1000,
                shares = listOf(amt(1L, 600), amt(2L, 500)),
                people = people.take(3),
            ),
        )
        assertEquals(1000, result.total)
        assertEquals(listOf(SplitWarning.WholeAmountMismatch(1000, 1100)), result.warnings)
        assertEquals(0, result.totalOf(3L))
        assertEquals(listOf(SplitLine(1L, null, -100, provisional = true)), result.lines.filter { it.provisional })
    }

    @Test
    fun itemRatio_blankWithPieceCountSplitsRemainingPieces() {
        // ソーセージ 400 円、全部で 7 個。山田・髙木が 2 個ずつ、残り 3 個を空欄の3人で 1 個ずつ。
        // 2:2:1:1:1 と同じ結果になる: 114 / 115 / 57 / 57 / 57。
        val result = SplitCalculator.calculate(
            item(
                groups = listOf(SplitGroupInput(1L, 400, pieceCount = 7)),
                shares = mapOf(
                    1L to listOf(rat(1L, 2), rat(2L, 2), rat(3L, null), rat(4L, null), rat(5L, null)),
                ),
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
    fun itemRatio_piecesExceedWarnsW5AndBlanksGetZero() {
        // 3個入りに対し 2・3 が 2 個ずつ(計4個)。残りは −1 で超過。W5 を出し、空欄の 4 は 0 円。
        // 残りの 2 人で 200 / 200。
        val result = SplitCalculator.calculate(
            item(
                groups = listOf(SplitGroupInput(1L, 400, pieceCount = 3)),
                shares = mapOf(1L to listOf(rat(2L, 2), rat(3L, 2), rat(4L, null))),
            ),
        )
        assertEquals(400, result.total)
        assertEquals(listOf(SplitWarning.InvalidShares(1L, "pieces exceed")), result.warnings)
        assertEquals(0, result.totalOf(4L))
        assertEquals(200, result.totalOf(2L))
        assertEquals(200, result.totalOf(3L))
    }

    @Test
    fun itemRatio_blankWithoutPieceCountWeighsOne() {
        // 個数の指定が無い品目: 空欄の人は重み1。髙木(重み3)と佐藤(空欄=1)で 400 を 300 / 100。
        val result = SplitCalculator.calculate(
            item(
                groups = listOf(SplitGroupInput(1L, 400)),
                shares = mapOf(1L to listOf(rat(2L, 3), rat(3L, null))),
            ),
        )
        assertEquals(400, result.total)
        assertEquals(300, result.totalOf(2L))
        assertEquals(100, result.totalOf(3L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun wholeRatio_blankWeighsOne() {
        // 2人、支払い 300 円。髙木の重み2、山田は行が無い(空欄=1)。200 / 100。
        val result = SplitCalculator.calculate(
            whole(
                300,
                shares = listOf(rat(1L, 2)),
                kind = ShareKind.RATIO,
                people = people.take(2),
            ),
        )
        assertEquals(300, result.total)
        assertEquals(200, result.totalOf(1L))
        assertEquals(100, result.totalOf(2L))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun itemEqual_payerTakesTheRemainderLast() {
        // 3人で 400 円を均等(133/133/134 の余り1円)。支払者は山田(isPayer)なので、
        // 端数の1円は非支払者の髙木へ。支払者の山田は 133 円(最少)になる。
        val payerPeople = people.take(3).map { if (it.id == 2L) it.copy(isPayer = true) else it }
        val result = SplitCalculator.calculate(
            SplitInput(
                total = null,
                mode = SplitMode.ITEM,
                people = payerPeople,
                groups = listOf(SplitGroupInput(1L, 400)),
                itemShares = mapOf(1L to listOf(ShareInput(1L, ShareKind.EQUAL), ShareInput(2L, ShareKind.EQUAL), ShareInput(3L, ShareKind.EQUAL))),
            ),
        )
        assertEquals(400, result.total)
        assertEquals(134, result.totalOf(1L))
        assertEquals(133, result.totalOf(2L))
        assertEquals(133, result.totalOf(3L))
    }

    @Test
    fun sharedCost_splitsAmongAllPeople() {
        // 共通費 10 円。参加者 P は people 全員(3人)。3・3・3 の余り1を非支払者の山田へ: 髙木3・山田4・佐藤3。
        val result = SplitCalculator.calculate(
            SplitInput(
                total = null,
                mode = SplitMode.ITEM,
                people = people.take(3),
                groups = listOf(SplitGroupInput(11L, 10, isShared = true)),
            ),
        )
        assertEquals(10, result.total)
        assertEquals(3, result.totalOf(1L))
        assertEquals(4, result.totalOf(2L))
        assertEquals(3, result.totalOf(3L))
    }

    @Test
    fun residual_splitsAmongAllPeople() {
        // 品目 300(山田・髙木で 150 ずつ)に対し合計 302。残差2は P=3人(髙木・山田・佐藤)へ。
        // 非支払者の山田と佐藤に1円ずつ: 髙木150・山田151・佐藤1。W3(2)。
        val result = SplitCalculator.calculate(
            item(
                groups = listOf(SplitGroupInput(1L, 300)),
                shares = mapOf(1L to listOf(ShareInput(1L, ShareKind.EQUAL), ShareInput(2L, ShareKind.EQUAL))),
                people = people.take(3),
                total = 302,
            ),
        )
        assertEquals(302, result.total)
        assertEquals(listOf(SplitWarning.ItemsTotalMismatch(2)), result.warnings)
        assertEquals(150, result.totalOf(1L))
        assertEquals(151, result.totalOf(2L))
        assertEquals(1, result.totalOf(3L))
    }

    @Test
    fun wholeNoInput_splitsEvenlyAmongThreePeople() {
        // 入力なし・3人で 500 円。全員が空欄なので均等: 166 / 167 / 167。
        val result = SplitCalculator.calculate(whole(500, shares = emptyList(), people = people.take(3)))
        assertEquals(500, result.total)
        assertEquals(166, result.totalOf(1L))
        assertEquals(167, result.totalOf(2L))
        assertEquals(167, result.totalOf(3L))
        assertTrue(result.warnings.isEmpty())
    }
}
