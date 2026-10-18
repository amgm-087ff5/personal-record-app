package com.amgm.personallog.domain.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 割り勘の結果を品目へ配り直す(SplitLedger)の期待値テスト。
 * 人: 1=自分(髙木)、2=山田。品目は id・まとまり・金額・カテゴリを指定して作る。
 */
class SplitLedgerTest {
    private val me = 1L
    private val other = 2L

    private fun result(vararg lines: SplitLine) = SplitResult(lines.toList(), emptyList())

    @Test
    fun sameGroupTwoDishes_splitByItemYen() {
        // 辛味チキン2皿(品目 A・B とも 400 円、同じまとまり)。自分の行は 300 → 150 / 150。
        val items = listOf(
            ItemYen(10L, 1L, 400L, "食"),
            ItemYen(11L, 1L, 400L, "食"),
        )
        val r = result(SplitLine(me, 1L, 300), SplitLine(other, 1L, 500))
        val mine = SplitLedger.yenPerItem(r, me, items)
        assertEquals(150L, mine[10L])
        assertEquals(150L, mine[11L])
        assertEquals(r.totalOf(me).toLong(), mine.values.sum())
        val theirs = SplitLedger.yenPerItem(r, other, items)
        assertEquals(250L, theirs[10L])
        assertEquals(250L, theirs[11L])
    }

    @Test
    fun groupNullLine_isSpreadOverAllItemsByYen() {
        // 全体方式の調整 100 円(まとまり無し)は、全品目へ金額の比で配る。
        // 400:200 → 40000/600=66 余り400、20000/600=33 余り200。余り1円は余りの大きい A へ: A 67 / B 33。
        val items = listOf(
            ItemYen(10L, 1L, 400L, "食"),
            ItemYen(20L, 2L, 200L, "日用"),
        )
        val r = result(SplitLine(me, null, 100))
        val mine = SplitLedger.yenPerItem(r, me, items)
        assertEquals(67L, mine[10L])
        assertEquals(33L, mine[20L])
        assertEquals(100L, mine.values.sum())
    }

    @Test
    fun negativeItem_getsNoShareAndTotalStillMatches() {
        // 値引き −100 円の品目(同じまとまり)は重み0。自分の 300 円は 500 円の品目へ全額。
        val items = listOf(
            ItemYen(10L, 1L, 500L, "食"),
            ItemYen(11L, 1L, -100L, "値引き"),
        )
        val r = result(SplitLine(me, 1L, 300), SplitLine(me, null, 50))
        val mine = SplitLedger.yenPerItem(r, me, items)
        assertEquals(350L, mine[10L])
        assertEquals(null, mine[11L])
        assertEquals(r.totalOf(me).toLong(), mine.values.sum())
    }

    @Test
    fun groupWithoutItems_fallsBackToFirstItem() {
        // 品目が無いまとまり(99)の行は、全品目の最初の品目(10)へ全額。
        val items = listOf(
            ItemYen(10L, 1L, 400L, "食"),
            ItemYen(20L, 2L, 200L, "日用"),
        )
        val r = result(SplitLine(me, 99L, 77))
        val mine = SplitLedger.yenPerItem(r, me, items)
        assertEquals(77L, mine[10L])
        assertEquals(null, mine[20L])
    }

    @Test
    fun totalAlwaysMatches_forMixedLines() {
        // 合計が一致すること(まとまり付き・調整行・0円の品目を含む)。
        val items = listOf(
            ItemYen(10L, 1L, 400L, "食"),
            ItemYen(11L, 1L, 200L, "食"),
            ItemYen(20L, 2L, 100L, "日用"),
            ItemYen(21L, 2L, 0L, "日用"),
        )
        val r = result(SplitLine(me, 1L, 301), SplitLine(me, 2L, 45), SplitLine(me, null, 7), SplitLine(other, 1L, 99))
        val mine = SplitLedger.yenPerItem(r, me, items)
        assertEquals(r.totalOf(me).toLong(), mine.values.sum())
        assertTrue(mine.values.all { it != 0L })
        assertEquals(null, mine[21L])
        val theirs = SplitLedger.yenPerItem(r, other, items)
        assertEquals(99L, theirs.values.sum())
    }

    @Test
    fun emptyItems_givesEmptyMap() {
        val r = result(SplitLine(me, null, 500))
        assertTrue(SplitLedger.yenPerItem(r, me, emptyList()).isEmpty())
    }

    @Test
    fun categoryBreakdown_sortedDescendingAndSumsToTotal() {
        // 品目 A(食 400, まとまり1)、B(日用 200, まとまり2)、C(食 100, まとまり2)。
        // 自分: まとまり1 は 300(A へ全額)、まとまり2 は 150(B:C=200:100 で 100 / 50)。
        // 食 = 300 + 50 = 350、日用 = 100。合計 450。
        val items = listOf(
            ItemYen(10L, 1L, 400L, "食"),
            ItemYen(20L, 2L, 200L, "日用"),
            ItemYen(21L, 2L, 100L, "食"),
        )
        val r = result(SplitLine(me, 1L, 300), SplitLine(me, 2L, 150))
        val breakdown = SplitLedger.categoryBreakdown(r, me, items)
        assertEquals(listOf("食" to 350, "日用" to 100), breakdown)
        assertEquals(r.totalOf(me), breakdown.sumOf { it.second })
    }

    @Test
    fun categoryBreakdown_noItems_usesUncategorizedOnlyWhenTotalNonZero() {
        val r = result(SplitLine(me, null, 500), SplitLine(other, null, 0))
        assertEquals(listOf("未分類" to 500), SplitLedger.categoryBreakdown(r, me, emptyList()))
        assertTrue(SplitLedger.categoryBreakdown(r, other, emptyList()).isEmpty())
    }
}
