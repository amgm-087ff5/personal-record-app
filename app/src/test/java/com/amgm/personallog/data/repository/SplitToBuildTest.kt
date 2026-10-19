package com.amgm.personallog.data.repository

import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitLabelAmount
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.model.WholeShare
import com.amgm.personallog.domain.split.ShareInput
import com.amgm.personallog.domain.split.SplitGroupInput
import com.amgm.personallog.domain.split.SplitPerson
import org.junit.Assert.assertEquals
import org.junit.Test

/** SplitSheetData → SplitBuild(計算の入力)の変換を、DB なしで確かめる。 */
class SplitToBuildTest {
    private fun item(id: Long, name: String, amount: Int) =
        ReceiptItem(id = id, receiptId = 1, name = name, amount = amount, category = "食費")

    @Test
    fun toBuild_mapsGroupsParticipantsSharesAndLabels() {
        val items = listOf(item(1, "A", 300), item(2, "B", 200), item(3, "C", 100))
        val data = SplitSheetData(
            receiptId = 1,
            mode = SplitMode.ITEM,
            wholeKind = ShareKind.AMOUNT,
            participants = listOf(
                SplitParticipant(id = 10, receiptId = 1, personId = 100, isPayer = true, sortOrder = 0),
                SplitParticipant(id = 11, receiptId = 1, personId = null, label = "新人", sortOrder = 1),
            ),
            groups = listOf(
                GroupWithItems(ShareGroup(id = 1, receiptId = 1, pieceCount = 2, sortOrder = 0), listOf(1L, 2L)),
                GroupWithItems(ShareGroup(id = 2, receiptId = 1, isShared = true, sortOrder = 1), listOf(3L)),
            ),
            itemShares = listOf(
                ItemShare(groupId = 1, participantId = 10, kind = ShareKind.RATIO, weight = 1),
                ItemShare(groupId = 1, participantId = 11, kind = ShareKind.RATIO, weight = null),
            ),
            wholeShares = listOf(WholeShare(receiptId = 1, participantId = 11, kind = ShareKind.AMOUNT, amount = null)),
            labelAmounts = listOf(SplitLabelAmount(receiptId = 1, label = "新人", amount = 3000)),
            persons = mapOf(10L to Person(id = 100, name = "自分", shortName = "自", isSelf = true, createdAt = 0)),
        )

        val b = data.toBuild(total = 600, items = items)

        assertEquals(600, b.input.total)
        assertEquals(SplitMode.ITEM, b.input.mode)
        assertEquals(ShareKind.AMOUNT, b.input.wholeKind)
        assertEquals(
            listOf(SplitPerson(id = 10, isSelf = true, order = 0, isPayer = true, label = ""), SplitPerson(id = 11, isSelf = false, order = 1, label = "新人")),
            b.input.people,
        )
        // まとまりの金額は、そこに入っている品目の税込円の合計
        assertEquals(
            listOf(SplitGroupInput(id = 1, amount = 500, isShared = false, pieceCount = 2), SplitGroupInput(id = 2, amount = 100, isShared = true, pieceCount = null)),
            b.input.groups,
        )
        assertEquals(listOf(ShareInput(personId = 10, kind = ShareKind.RATIO, weight = 1), ShareInput(personId = 11, kind = ShareKind.RATIO)), b.input.itemShares.getValue(1L))
        assertEquals(listOf(ShareInput(personId = 11, kind = ShareKind.AMOUNT)), b.input.wholeShares)
        assertEquals(mapOf("新人" to 3000), b.input.labelAmounts)
        assertEquals(10L, b.selfParticipantId)
        // 品目の並びは items と同じ。まとまりは品目ごとの所属
        assertEquals(listOf(1L, 2L, 3L), b.itemYen.map { it.itemId })
        assertEquals(listOf(1L, 1L, 2L), b.itemYen.map { it.groupId })
        assertEquals(listOf(300L, 200L, 100L), b.itemYen.map { it.yen })
    }

    @Test
    fun toBuild_selfIsTheParticipantWhosePersonIsSelf_andMinusOneWhenNone() {
        val data = SplitSheetData(
            receiptId = 1,
            mode = SplitMode.WHOLE,
            wholeKind = ShareKind.AMOUNT,
            participants = listOf(SplitParticipant(id = 0, receiptId = 1, personId = 5, sortOrder = 0)),
            groups = emptyList(),
            itemShares = emptyList(),
            wholeShares = emptyList(),
            labelAmounts = emptyList(),
            persons = mapOf(0L to Person(id = 5, name = "自分", shortName = "自", isSelf = true, createdAt = 0)),
        )
        assertEquals(0L, data.toBuild(total = 100, items = emptyList()).selfParticipantId)
        assertEquals(NO_SELF_PARTICIPANT_ID, data.copy(persons = emptyMap()).toBuild(total = 100, items = emptyList()).selfParticipantId)
    }
}
