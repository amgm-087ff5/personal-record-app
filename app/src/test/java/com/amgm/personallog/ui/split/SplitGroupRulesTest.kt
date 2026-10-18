package com.amgm.personallog.ui.split

import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.repository.canSplitOutItems
import com.amgm.personallog.data.repository.mergeKeepGroup
import com.amgm.personallog.data.repository.mergeShareRows
import com.amgm.personallog.data.repository.mergedGroupAttributes
import com.amgm.personallog.data.repository.newGroupAfterSplit
import com.amgm.personallog.data.repository.splitEmptiesSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitGroupRulesTest {

    private fun share(group: Long, participant: Long, kind: String = ShareKind.EQUAL, weight: Int? = null, amount: Int? = null) =
        ItemShare(groupId = group, participantId = participant, kind = kind, weight = weight, amount = amount)

    private fun group(id: Long, sortOrder: Int, pieceCount: Int? = null, isShared: Boolean = false) =
        ShareGroup(id = id, receiptId = 1L, pieceCount = pieceCount, isShared = isShared, sortOrder = sortOrder)

    // ---- 分ける ----

    @Test
    fun 分けるのは選んだ品目が空でなくまとまりに入っているとき() {
        val members = setOf(1L, 2L, 3L)
        assertFalse(canSplitOutItems(members, emptySet()))
        assertTrue(canSplitOutItems(members, setOf(1L)))
        assertTrue(canSplitOutItems(members, setOf(1L, 2L, 3L)))
        assertFalse(canSplitOutItems(members, setOf(1L, 9L)))
    }

    @Test
    fun 一部だけ分けると元のまとまりは残る() {
        assertFalse(splitEmptiesSource(setOf(1L, 2L, 3L), setOf(1L)))
    }

    @Test
    fun 全部分けると元のまとまりは空になり消える() {
        assertTrue(splitEmptiesSource(setOf(1L, 2L, 3L), setOf(1L, 2L, 3L)))
    }

    @Test
    fun 分けた後の新しいまとまりは個数が_null_で共通費も外れる() {
        val g = newGroupAfterSplit(receiptId = 7L, sortOrder = 4)
        assertNull(g.pieceCount)
        assertFalse(g.isShared)
        assertEquals(4, g.sortOrder)
        assertEquals(7L, g.receiptId)
    }

    // ---- まとめる: 割当 ----

    @Test
    fun まとめる_割当がどちらも無ければ何も外れない() {
        val r = mergeShareRows(keepGroupId = 10L, keepRows = emptyList(), otherRows = listOf(emptyList()))
        assertTrue(r.rows.isEmpty())
        assertFalse(r.cleared)
    }

    @Test
    fun まとめる_種類と対象者が同じなら統合し残す先の行を使う() {
        val keep = listOf(share(10L, 1L), share(10L, 2L))
        val other = listOf(share(11L, 1L), share(11L, 2L))
        val r = mergeShareRows(keepGroupId = 10L, keepRows = keep, otherRows = listOf(other))
        assertFalse(r.cleared)
        assertEquals(keep, r.rows)
    }

    @Test
    fun まとめる_種類が違えば統合せず割当を外す() {
        val keep = listOf(share(10L, 1L, ShareKind.EQUAL), share(10L, 2L, ShareKind.EQUAL))
        val other = listOf(share(11L, 1L, ShareKind.RATIO, weight = 2), share(11L, 2L, ShareKind.RATIO, weight = 1))
        val r = mergeShareRows(keepGroupId = 10L, keepRows = keep, otherRows = listOf(other))
        assertTrue(r.cleared)
        assertTrue(r.rows.isEmpty())
    }

    @Test
    fun まとめる_対象者が違えば同じ種類でも統合せず割当を外す() {
        val keep = listOf(share(10L, 1L), share(10L, 2L))
        val other = listOf(share(11L, 1L), share(11L, 3L))
        val r = mergeShareRows(keepGroupId = 10L, keepRows = keep, otherRows = listOf(other))
        assertTrue(r.cleared)
        assertTrue(r.rows.isEmpty())
    }

    @Test
    fun まとめる_残す先が未設定で他が同じ割当なら_その割当を残す先へ付ける() {
        val other = listOf(share(11L, 1L, ShareKind.AMOUNT, amount = 300), share(11L, 2L, ShareKind.AMOUNT, amount = 200))
        val r = mergeShareRows(keepGroupId = 10L, keepRows = emptyList(), otherRows = listOf(other))
        assertFalse(r.cleared)
        assertEquals(listOf(share(10L, 1L, ShareKind.AMOUNT, amount = 300), share(10L, 2L, ShareKind.AMOUNT, amount = 200)), r.rows)
    }

    @Test
    fun まとめる_残す先に割当があり他が未設定なら_残す先の割当を残す() {
        val keep = listOf(share(10L, 1L, ShareKind.ASSIGN))
        val r = mergeShareRows(keepGroupId = 10L, keepRows = keep, otherRows = listOf(emptyList()))
        assertFalse(r.cleared)
        assertEquals(keep, r.rows)
    }

    @Test
    fun まとめる_残す先と他の両方に割当があり他が未設定の割当と混ざっても外れない() {
        val keep = listOf(share(10L, 1L))
        val r = mergeShareRows(keepGroupId = 10L, keepRows = keep, otherRows = listOf(emptyList(), listOf(share(11L, 1L))))
        assertFalse(r.cleared)
        assertEquals(keep, r.rows)
    }

    // ---- まとめる: 残す先・個数・共通費 ----

    @Test
    fun まとめる先は並びが一番早いまとまり() {
        val groups = listOf(group(id = 5L, sortOrder = 3), group(id = 6L, sortOrder = 1), group(id = 7L, sortOrder = 2))
        assertEquals(6L, mergeKeepGroup(groups)?.id)
        assertNull(mergeKeepGroup(emptyList()))
    }

    @Test
    fun まとめた後の個数は残す先の値を使う() {
        val keep = group(id = 5L, sortOrder = 1, pieceCount = 4)
        val other = group(id = 6L, sortOrder = 2, pieceCount = 9)
        assertEquals(4, mergedGroupAttributes(keep, listOf(keep, other)).pieceCount)
    }

    @Test
    fun まとめた後の個数は残す先が空なら空() {
        val keep = group(id = 5L, sortOrder = 1, pieceCount = null)
        val other = group(id = 6L, sortOrder = 2, pieceCount = 9)
        assertNull(mergedGroupAttributes(keep, listOf(keep, other)).pieceCount)
    }

    @Test
    fun まとめた後の共通費はどれか1つでも付いていれば付く() {
        val keep = group(id = 5L, sortOrder = 1, isShared = false)
        val other = group(id = 6L, sortOrder = 2, isShared = true)
        assertTrue(mergedGroupAttributes(keep, listOf(keep, other)).isShared)
        assertFalse(mergedGroupAttributes(keep, listOf(keep, group(id = 7L, sortOrder = 3))).isShared)
    }
}
