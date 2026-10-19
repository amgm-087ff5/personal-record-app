package com.amgm.personallog.ui.split

import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.domain.split.SplitCalculator
import com.amgm.personallog.domain.split.SplitGroupInput
import com.amgm.personallog.domain.split.SplitInput
import com.amgm.personallog.domain.split.SplitLine
import com.amgm.personallog.domain.split.SplitModeReading
import com.amgm.personallog.domain.split.SplitPerson
import com.amgm.personallog.domain.split.SplitWarning
import com.amgm.personallog.domain.split.ShareInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitSheetRulesTest {

    private fun fact(id: Long, isSelf: Boolean = false, name: String = "", payer: Boolean = false, share: Boolean = false) =
        ParticipantFacts(id = id, isSelf = isSelf, typedName = name, isPayer = payer, hasShare = share)

    @Test
    fun 参加人数の表示は自分を含む合計() {
        assertEquals("合計1人", participantCountText(1))
        assertEquals("合計3人", participantCountText(3))
    }

    @Test
    fun まとめて追加は1から50人の整数だけ有効() {
        assertNull(bulkAddCount(""))
        assertNull(bulkAddCount("0"))
        assertEquals(1, bulkAddCount("1"))
        assertEquals(3, bulkAddCount("３"))
        assertEquals(50, bulkAddCount("50"))
        assertNull(bulkAddCount("51"))
        assertNull(bulkAddCount("abc"))
    }

    @Test
    fun まとめて追加の合計は今の人数に足す() {
        assertEquals(5, totalAfterBulkAdd(1, "4"))
        assertNull(totalAfterBulkAdd(2, ""))
    }

    @Test
    fun 末尾の名前なし行だけ消せる() {
        val list = listOf(fact(1, isSelf = true), fact(-2))
        assertEquals(-2L, removableLastParticipantId(list))
    }

    @Test
    fun 末尾が名前ありなら消せない() {
        assertNull(removableLastParticipantId(listOf(fact(1, isSelf = true), fact(-2), fact(-3, name = "田中"))))
    }

    @Test
    fun 末尾が自分なら名前があっても消せる() {
        assertEquals(1L, removableLastParticipantId(listOf(fact(1, isSelf = true, name = "自分"))))
        assertEquals(1L, removableLastParticipantId(listOf(fact(-2), fact(1, isSelf = true, name = "自分"))))
    }

    @Test
    fun 末尾が会計者か割当ありなら消せない() {
        assertNull(removableLastParticipantId(listOf(fact(1, isSelf = true), fact(-2, payer = true))))
        assertNull(removableLastParticipantId(listOf(fact(1, isSelf = true), fact(-2, share = true))))
    }

    @Test
    fun 参加者が無ければ消せない() {
        assertNull(removableLastParticipantId(emptyList()))
    }

    @Test
    fun 未設定のまとまりは割当の行が無いまとまり() {
        assertEquals(listOf(1L, 3L), unsetGroupIds(listOf(1L, 2L, 3L), setOf(2L)))
        assertEquals(emptyList<Long>(), unsetGroupIds(listOf(1L), setOf(1L)))
    }

    @Test
    fun 未設定を全員で均等にするのは2人以上のときだけ() {
        assertEquals(
            listOf(1L to 10L, 1L to 11L, 3L to 10L, 3L to 11L),
            equalShareTargets(listOf(1L, 3L), listOf(10L, 11L)),
        )
        assertTrue(equalShareTargets(listOf(1L), listOf(10L)).isEmpty())
        assertTrue(equalShareTargets(emptyList(), listOf(10L, 11L)).isEmpty())
    }

    @Test
    fun 差額を自分に足すと行の有無で金額が変わる() {
        assertEquals(400, selfAmountAfterDifference(300, expected = 1000, allocated = 900))
        assertEquals(100, selfAmountAfterDifference(null, expected = 1000, allocated = 900))
    }

    @Test
    fun 差額を自分に超過のときは自分の金額を減らして合わせる() {
        assertEquals(100, selfAmountAfterDifference(200, expected = 900, allocated = 1000))
    }

    @Test
    fun 差額を自分に入れると金額のずれの警告が消える() {
        fun input(selfAmount: Int) = SplitInput(
            total = 1000,
            mode = SplitMode.ITEM,
            people = listOf(SplitPerson(id = 1L, isSelf = true, order = 0), SplitPerson(id = 2L, isSelf = false, order = 1)),
            groups = listOf(SplitGroupInput(id = 10L, amount = 1000)),
            itemShares = mapOf(
                10L to listOf(
                    ShareInput(personId = 1L, kind = ShareKind.AMOUNT, amount = selfAmount),
                    ShareInput(personId = 2L, kind = ShareKind.AMOUNT, amount = 600),
                ),
            ),
        )
        val before = SplitCalculator.calculate(input(300))
        val w = before.warnings.single() as SplitWarning.GroupAmountMismatch
        val after = SplitCalculator.calculate(input(selfAmountAfterDifference(300, w.expected, w.allocated)))
        assertTrue(after.warnings.isEmpty())
        assertEquals(1000, after.total)
    }

    @Test
    fun 警告のバッジ文言は差を書く() {
        assertEquals("W1 金額が合わない(差 ¥100)", badgeText(SplitWarning.GroupAmountMismatch(1L, expected = 1000, allocated = 900)))
        assertEquals("W2 全体の金額が合わない(差 -¥100)", badgeText(SplitWarning.WholeAmountMismatch(expected = 900, allocated = 1000)))
        assertEquals("W5 割当を解釈できない", badgeText(SplitWarning.InvalidShares(null, "unknown person")))
    }

    @Test
    fun 行の警告はW1とまとまりのW5だけ() {
        val ws = listOf(
            SplitWarning.GroupAmountMismatch(1L, 1000, 900),
            SplitWarning.InvalidShares(2L, "mixed kinds"),
            SplitWarning.InvalidShares(null, "unknown person"),
            SplitWarning.WholeAmountMismatch(1000, 900),
            SplitWarning.ItemsTotalMismatch(50),
        )
        val byGroup = groupWarningsOf(ws)
        assertEquals(setOf(1L, 2L), byGroup.keys)
        assertEquals(1, byGroup.getValue(1L).size)
        val whole = wholeWarningsOf(ws)
        assertEquals(2, whole.size)
        assertTrue(whole.any { it is SplitWarning.WholeAmountMismatch })
        assertTrue(whole.any { it is SplitWarning.InvalidShares && it.groupId == null })
    }

    @Test
    fun 仮計上はまとまりごとと全体に分ける() {
        val lines = listOf(
            SplitLine(1L, 10L, 100, provisional = true),
            SplitLine(1L, 10L, 50, provisional = true),
            SplitLine(1L, 11L, 300),
            SplitLine(1L, null, -20, provisional = true),
        )
        assertEquals(mapOf(10L to 150), provisionalByGroup(lines))
        assertEquals(-20, provisionalWholeOf(lines))
    }

    @Test
    fun 旧の自分だけは食べた分どおりに読み替える() {
        assertEquals(SplitMode.ITEM, SplitModeReading.modeOf(SplitMode.SELF))
        assertTrue(SplitModeReading.isSelfOnly(SplitMode.SELF))
        assertTrue(SplitModeReading.isSelfOnly("unknown"))
    }

    @Test
    fun 全体と食べた分どおりの保存はそのまま読む() {
        assertEquals(SplitMode.WHOLE, SplitModeReading.modeOf(SplitMode.WHOLE))
        assertEquals(SplitMode.ITEM, SplitModeReading.modeOf(SplitMode.ITEM))
        assertFalse(SplitModeReading.isSelfOnly(SplitMode.WHOLE))
        assertFalse(SplitModeReading.isSelfOnly(SplitMode.ITEM))
    }

    @Test
    fun 参加者が自分1人なら食べた分どおりは全額が自分() {
        val people = listOf(SplitPerson(id = 1L, isSelf = true, order = 0))
        val groups = listOf(SplitGroupInput(id = 10L, amount = 600), SplitGroupInput(id = 11L, amount = 300, isShared = true))
        val r = SplitCalculator.calculate(SplitInput(total = 900, mode = SplitMode.ITEM, people = people, groups = groups))
        assertEquals(900, r.totalOf(1L))
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun 参加者が自分1人なら全体の割り方も全額が自分() {
        val people = listOf(SplitPerson(id = 1L, isSelf = true, order = 0))
        val groups = listOf(SplitGroupInput(id = 10L, amount = 600), SplitGroupInput(id = 11L, amount = 300))
        val r = SplitCalculator.calculate(
            SplitInput(total = 900, mode = SplitMode.WHOLE, people = people, groups = groups, wholeKind = ShareKind.AMOUNT),
        )
        assertEquals(900, r.totalOf(1L))
    }
}
