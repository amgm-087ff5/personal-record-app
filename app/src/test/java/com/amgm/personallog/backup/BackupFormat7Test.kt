package com.amgm.personallog.backup

import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.PaymentPayer
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.ShareGroupItem
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitLabelAmount
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.model.SplitSheet
import com.amgm.personallog.data.model.WholeShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 割り勘の表の付け替え(形式8の参加者ベース)。形式7で人だった割当の参照先は、形式8では参加者に変わっている。
 */
class BackupFormat7Test {
    private val self = BackupPerson(id = 1, uid = "p-self", name = "自分", shortName = "私", isSelf = true, sortOrder = 0, createdAt = 10)
    private val friend = BackupPerson(id = 2, uid = "p-friend", name = "髙橋", shortName = "髙", isSelf = false, sortOrder = 1, createdAt = 11)

    private val paymentIdMap = mapOf(5L to 50L, 6L to 60L)
    private val itemIdMap = mapOf(11L to 111L, 12L to 112L)
    private val personIdMap = mapOf(1L to 100L, 2L to 200L)
    private val groupIdMap = mapOf(3L to 300L)
    private val participantIdMap = mapOf(21L to 310L, 22L to 320L)

    /** 支払い5(品目11)の割り勘。参加者21(自分)・22(髙橋)。品目ごと方式のまとまり・全体方式の割当・支払内訳を持つ。 */
    private fun fullData() = BackupData(
        formatVersion = BACKUP_FORMAT_VERSION,
        dbVersion = 11,
        exportedAt = 1,
        persons = listOf(self, friend),
        splitSheets = listOf(BackupSplitSheet(5, SplitMode.ITEM)),
        shareGroups = listOf(BackupShareGroup(id = 3, paymentId = 5, pieceCount = 2, isShared = false, sortOrder = 0)),
        shareGroupItems = listOf(BackupShareGroupItem(groupId = 3, paymentItemId = 11)),
        participants = listOf(
            BackupParticipant(id = 21, paymentId = 5, personId = 1, isPayer = true),
            BackupParticipant(id = 22, paymentId = 5, personId = 2),
        ),
        itemShares = listOf(
            BackupItemShare(groupId = 3, participantId = 21, kind = ShareKind.RATIO, weight = 2),
            BackupItemShare(groupId = 3, participantId = 22, kind = ShareKind.RATIO, weight = 1),
        ),
        wholeShares = listOf(BackupWholeShare(paymentId = 5, participantId = 22, kind = ShareKind.AMOUNT, amount = 300)),
        paymentPayers = listOf(BackupPaymentPayer(id = 7, paymentId = 5, participantId = 21, method = "カード", amount = 980)),
    )

    @Test
    fun splitEntities_roundTripThroughBackupClasses() {
        val p = Person(id = 4, uid = "u-4", name = "友人", shortName = "友", isSelf = false, sortOrder = 2, createdAt = 9)
        assertEquals(p, BackupPerson.from(p).toEntity())
        val sheet = SplitSheet(5, SplitMode.WHOLE, ShareKind.RATIO, "W1,W3")
        assertEquals(sheet, BackupSplitSheet.from(sheet).toEntity())
        val g = ShareGroup(id = 3, paymentId = 5, pieceCount = 3, isShared = true, sortOrder = 1)
        assertEquals(g, BackupShareGroup.from(g).toEntity())
        val gi = ShareGroupItem(3, 11)
        assertEquals(gi, BackupShareGroupItem.from(gi).toEntity())
        val sp = SplitParticipant(id = 21, paymentId = 5, personId = 1, memo = "一言", label = "新人", isPayer = true, sortOrder = 2)
        assertEquals(sp, BackupParticipant.from(sp).toEntity())
        val la = SplitLabelAmount(5, "新人", 3000)
        assertEquals(la, BackupLabelAmount.from(la).toEntity())
        val ia = ItemShare(3, 21, ShareKind.AMOUNT, weight = null, amount = 100)
        assertEquals(ia, BackupItemShare.from(ia).toEntity())
        val ws = WholeShare(5, 22, ShareKind.EQUAL)
        assertEquals(ws, BackupWholeShare.from(ws).toEntity())
        val pp = PaymentPayer(id = 7, paymentId = 5, participantId = 21, method = "現金", amount = 500)
        assertEquals(pp, BackupPaymentPayer.from(pp).toEntity())
    }

    // ------------------------------------------------------------ 人の照合

    @Test
    fun planPersons_sameUidUsesExisting_andBackupSelfMapsToDeviceSelf() {
        val plan = planPersons(listOf(self, friend), existingByUid = mapOf("p-friend" to 70L), selfId = 10L)
        // バックアップの自分は、端末の自分(10)に合わせる。新しく自分を作らない
        assertEquals(PersonPlan.Existing(10L), plan[1L])
        // 同じ uid の人は既存を使う
        assertEquals(PersonPlan.Existing(70L), plan[2L])
    }

    @Test
    fun planPersons_newPeopleAreCreated_andSelfOnlyWhenDeviceHasNone() {
        // 端末に自分が無い: バックアップの自分を1人だけ自分として作る
        val plan = planPersons(listOf(self, friend), emptyMap(), selfId = null)
        val newSelf = plan[1L] as PersonPlan.New
        assertTrue(newSelf.person.isSelf)
        assertEquals("p-self", newSelf.person.uid)
        val newFriend = plan[2L] as PersonPlan.New
        assertFalse(newFriend.person.isSelf)
        // 自分が2人あっても、作る自分は1人
        val two = planPersons(listOf(self, self.copy(id = 9, uid = "p-self-2")), emptyMap(), selfId = null)
        assertTrue((two[1L] as PersonPlan.New).person.isSelf)
        assertFalse((two[9L] as PersonPlan.New).person.isSelf)
    }

    @Test
    fun planPersons_duplicateUidInBackup_keepsFirstOnly() {
        val plan = planPersons(listOf(friend, friend.copy(id = 5)), emptyMap(), selfId = 10L)
        assertTrue(plan.containsKey(2L))
        assertFalse(plan.containsKey(5L)) // 後ろは対応なし(参照は捨てられる)
    }

    // ------------------------------------------------------------ 付け替え

    @Test
    fun remapShareGroups_followsPaymentId_andDropsMissingPayment() {
        val groups = listOf(
            BackupShareGroup(id = 3, paymentId = 5, pieceCount = 2),
            BackupShareGroup(id = 4, paymentId = 9), // 支払い 9 が対応しない -> 捨てる
        )
        val r = remapShareGroups(groups, paymentIdMap)
        assertEquals(listOf(3L to ShareGroup(id = 0, paymentId = 50, pieceCount = 2)), r)
    }

    @Test
    fun remapSplitRows_remapsIds_andDropsRowsWithMissingReference() {
        val data = fullData().copy(
            splitSheets = listOf(BackupSplitSheet(5, SplitMode.ITEM), BackupSplitSheet(9, SplitMode.WHOLE)),
            shareGroupItems = listOf(
                BackupShareGroupItem(3, 11),
                BackupShareGroupItem(3, 99), // 品目 99 が無い
                BackupShareGroupItem(4, 11), // まとまり 4 が無い
            ),
            itemShares = listOf(
                BackupItemShare(3, 21, ShareKind.RATIO, weight = 2),
                BackupItemShare(3, 29, ShareKind.EQUAL), // 参加者 29 が無い
                BackupItemShare(4, 21, ShareKind.EQUAL), // まとまり 4 が無い
            ),
            wholeShares = listOf(
                BackupWholeShare(5, 22, ShareKind.AMOUNT, amount = 300),
                BackupWholeShare(9, 22, ShareKind.EQUAL), // 支払い 9 が無い
            ),
            paymentPayers = listOf(
                BackupPaymentPayer(7, 5, 21, "カード", 980),
                BackupPaymentPayer(8, 6, 23, "", 10), // 参加者 23 が無い
            ),
        )
        val r = remapSplitRows(data, paymentIdMap, itemIdMap, groupIdMap, participantIdMap)
        assertEquals(listOf(SplitSheet(50, SplitMode.ITEM)), r.sheets)
        assertEquals(listOf(ShareGroupItem(300, 111)), r.groupItems)
        assertEquals(listOf(ItemShare(300, 310, ShareKind.RATIO, weight = 2, amount = null)), r.itemShares)
        assertEquals(listOf(WholeShare(50, 320, ShareKind.AMOUNT, amount = 300)), r.wholeShares)
        assertEquals(listOf(PaymentPayer(id = 0, paymentId = 50, participantId = 310, method = "カード", amount = 980)), r.payers)
    }

    @Test
    fun remapSplitRows_keepsOneRowPerKey() {
        val data = BackupData(
            splitSheets = listOf(BackupSplitSheet(5, SplitMode.WHOLE), BackupSplitSheet(5, SplitMode.ITEM)),
            shareGroupItems = listOf(BackupShareGroupItem(3, 11), BackupShareGroupItem(3, 11)),
            itemShares = listOf(
                BackupItemShare(3, 21, ShareKind.EQUAL),
                BackupItemShare(3, 21, ShareKind.RATIO, weight = 2),
            ),
        )
        val r = remapSplitRows(data, paymentIdMap, itemIdMap, groupIdMap, participantIdMap)
        assertEquals(listOf(SplitSheet(50, SplitMode.WHOLE)), r.sheets) // 先頭が残る
        assertEquals(1, r.groupItems.size)
        assertEquals(ShareKind.EQUAL, r.itemShares.single().kind)
    }

    @Test
    fun remapSplitRows_labelAmounts_followPayment_dropMissing_andKeepOnePerLabel() {
        val data = BackupData(
            labelAmounts = listOf(
                BackupLabelAmount(5, "新人", 3000),
                BackupLabelAmount(5, "新人", 2000), // 同じ支払い・同じラベル -> 先頭だけ
                BackupLabelAmount(5, "", 500),
                BackupLabelAmount(9, "新人", 100), // 支払い 9 が無い -> 捨てる
            ),
        )
        val r = remapSplitRows(data, paymentIdMap, itemIdMap, groupIdMap, participantIdMap)
        assertEquals(listOf(SplitLabelAmount(50, "新人", 3000), SplitLabelAmount(50, "", 500)), r.labelAmounts)
    }
}
