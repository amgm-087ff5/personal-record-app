package com.amgm.personallog.backup

import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.PaymentPayer
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.ShareGroupItem
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitSheet
import com.amgm.personallog.data.model.WholeShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormat7Test {
    private val self = BackupPerson(id = 1, uid = "p-self", name = "自分", shortName = "私", isSelf = true, sortOrder = 0, createdAt = 10)
    private val friend = BackupPerson(id = 2, uid = "p-friend", name = "髙橋", shortName = "髙", isSelf = false, sortOrder = 1, createdAt = 11)

    private val receiptIdMap = mapOf(5L to 50L, 6L to 60L)
    private val itemIdMap = mapOf(11L to 111L, 12L to 112L)
    private val personIdMap = mapOf(1L to 100L, 2L to 200L)

    /** 支払い5(品目11)に、品目ごと方式のまとまり・全体方式の割当・支払内訳を持つ、形式7のデータ。 */
    private fun fullData() = BackupData(
        formatVersion = BACKUP_FORMAT_VERSION,
        dbVersion = 10,
        exportedAt = 1,
        persons = listOf(self, friend),
        splitSheets = listOf(BackupSplitSheet(5, SplitMode.ITEM)),
        shareGroups = listOf(BackupShareGroup(id = 3, receiptId = 5, pieceCount = 2, isShared = false, sortOrder = 0)),
        shareGroupItems = listOf(BackupShareGroupItem(groupId = 3, receiptItemId = 11)),
        itemShares = listOf(
            BackupItemShare(groupId = 3, personId = 1, kind = ShareKind.RATIO, weight = 2),
            BackupItemShare(groupId = 3, personId = 2, kind = ShareKind.RATIO, weight = 1),
        ),
        wholeShares = listOf(BackupWholeShare(receiptId = 5, personId = 2, kind = ShareKind.AMOUNT, amount = 300)),
        paymentPayers = listOf(BackupPaymentPayer(id = 7, receiptId = 5, personId = 1, method = "カード", amount = 980)),
    )

    @Test
    fun format7_roundTrip_keepsSplitLists() {
        val data = fullData()
        val text = BackupJson.encodeToString(BackupData.serializer(), data)
        val back = BackupJson.decodeFromString(BackupData.serializer(), text)
        assertEquals(data, back)
        assertEquals(7, back.formatVersion)
        assertEquals(listOf(self, friend), back.persons)
        assertEquals(ShareKind.AMOUNT, back.wholeShares.single().kind)
        assertEquals(2, back.itemShares.size)
        for (key in listOf("persons", "splitSheets", "shareGroups", "shareGroupItems", "itemShares", "wholeShares", "paymentPayers")) {
            assertTrue("$key が JSON に入る", text.contains("\"$key\""))
        }
    }

    @Test
    fun splitEntities_roundTripThroughBackupClasses() {
        val p = Person(id = 4, uid = "u-4", name = "友人", shortName = "友", isSelf = false, sortOrder = 2, createdAt = 9)
        assertEquals(p, BackupPerson.from(p).toEntity())
        val sheet = SplitSheet(5, SplitMode.WHOLE)
        assertEquals(sheet, BackupSplitSheet.from(sheet).toEntity())
        val g = ShareGroup(id = 3, receiptId = 5, pieceCount = 3, isShared = true, sortOrder = 1)
        assertEquals(g, BackupShareGroup.from(g).toEntity())
        val gi = ShareGroupItem(3, 11)
        assertEquals(gi, BackupShareGroupItem.from(gi).toEntity())
        val ia = ItemShare(3, 2, ShareKind.AMOUNT, weight = null, amount = 100)
        assertEquals(ia, BackupItemShare.from(ia).toEntity())
        val ws = WholeShare(5, 2, ShareKind.EQUAL)
        assertEquals(ws, BackupWholeShare.from(ws).toEntity())
        val pp = PaymentPayer(id = 7, receiptId = 5, personId = 1, method = "現金", amount = 500)
        assertEquals(pp, BackupPaymentPayer.from(pp).toEntity())
    }

    @Test
    fun format6_withoutSplitKeys_isReadable_andSplitListsAreEmpty() {
        val text = """{"formatVersion":6,"dbVersion":9,"exportedAt":1,
            "expenses":[{"id":1,"date":"2026-10-05","amount":1,"category":"食費","createdAt":1}],
            "receiptNotDuplicates":[]}"""
        val d = BackupJson.decodeFromString(BackupData.serializer(), text)
        BackupManager.checkVersion(d.formatVersion)
        assertEquals(1, d.expenses.size)
        assertTrue(d.persons.isEmpty())
        assertTrue(d.splitSheets.isEmpty())
        assertTrue(d.shareGroups.isEmpty())
        assertTrue(d.shareGroupItems.isEmpty())
        assertTrue(d.itemShares.isEmpty())
        assertTrue(d.wholeShares.isEmpty())
        assertTrue(d.paymentPayers.isEmpty())
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
    fun remapShareGroups_followsReceiptId_andDropsMissingReceipt() {
        val groups = listOf(
            BackupShareGroup(id = 3, receiptId = 5, pieceCount = 2),
            BackupShareGroup(id = 4, receiptId = 9), // 支払い 9 が対応しない -> 捨てる
        )
        val r = remapShareGroups(groups, receiptIdMap)
        assertEquals(listOf(3L to ShareGroup(id = 0, receiptId = 50, pieceCount = 2)), r)
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
                BackupItemShare(3, 1, ShareKind.RATIO, weight = 2),
                BackupItemShare(3, 9, ShareKind.EQUAL), // 人 9 が無い
                BackupItemShare(4, 1, ShareKind.EQUAL), // まとまり 4 が無い
            ),
            wholeShares = listOf(
                BackupWholeShare(5, 2, ShareKind.AMOUNT, amount = 300),
                BackupWholeShare(9, 2, ShareKind.EQUAL), // 支払い 9 が無い
            ),
            paymentPayers = listOf(
                BackupPaymentPayer(7, 5, 1, "カード", 980),
                BackupPaymentPayer(8, 6, 3, "", 10), // 人 3 が無い
            ),
        )
        val r = remapSplitRows(data, receiptIdMap, itemIdMap, personIdMap, groupIdMap = mapOf(3L to 300L))
        assertEquals(listOf(SplitSheet(50, SplitMode.ITEM)), r.sheets)
        assertEquals(listOf(ShareGroupItem(300, 111)), r.groupItems)
        assertEquals(listOf(ItemShare(300, 100, ShareKind.RATIO, weight = 2, amount = null)), r.itemShares)
        assertEquals(listOf(WholeShare(50, 200, ShareKind.AMOUNT, amount = 300)), r.wholeShares)
        assertEquals(listOf(PaymentPayer(id = 0, receiptId = 50, personId = 100, method = "カード", amount = 980)), r.payers)
    }

    @Test
    fun remapSplitRows_keepsOneRowPerKey() {
        val data = BackupData(
            splitSheets = listOf(BackupSplitSheet(5, SplitMode.WHOLE), BackupSplitSheet(5, SplitMode.ITEM)),
            shareGroupItems = listOf(BackupShareGroupItem(3, 11), BackupShareGroupItem(3, 11)),
            itemShares = listOf(BackupItemShare(3, 1, ShareKind.EQUAL), BackupItemShare(3, 1, ShareKind.RATIO, weight = 2)),
        )
        val r = remapSplitRows(data, receiptIdMap, itemIdMap, personIdMap, groupIdMap = mapOf(3L to 300L))
        assertEquals(listOf(SplitSheet(50, SplitMode.WHOLE)), r.sheets) // 先頭が残る
        assertEquals(1, r.groupItems.size)
        assertEquals(ShareKind.EQUAL, r.itemShares.single().kind)
    }
}
