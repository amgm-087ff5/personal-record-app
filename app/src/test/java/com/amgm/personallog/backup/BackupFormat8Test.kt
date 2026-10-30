package com.amgm.personallog.backup

import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.PaymentPayer
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
import org.junit.Assert.fail
import org.junit.Test

class BackupFormat8Test {
    private val self = BackupPerson(id = 1, uid = "p-self", name = "自分", shortName = "私", isSelf = true, sortOrder = 0, createdAt = 10)
    private val friend = BackupPerson(id = 2, uid = "p-friend", name = "髙橋", shortName = "髙", isSelf = false, sortOrder = 1, createdAt = 11)

    private val paymentIdMap = mapOf(5L to 50L, 6L to 60L)
    private val itemIdMap = mapOf(11L to 111L, 12L to 112L)
    private val personIdMap = mapOf(1L to 100L, 2L to 200L)
    private val groupIdMap = mapOf(3L to 300L)
    private val participantIdMap = mapOf(21L to 310L, 22L to 320L, 23L to 330L)

    /**
     * 形式8の割り勘。支払い5(品目11)に、参加者21(自分・支払者)・22(髙橋・一言とラベル付き)・23(名前なし)。
     * ラベル「新人」は 3000 円。割当・支払内訳は参加者 id で参照する。
     */
    private fun v8Data() = BackupData(
        formatVersion = BACKUP_FORMAT_VERSION,
        dbVersion = 11,
        exportedAt = 1,
        persons = listOf(self, friend),
        splitSheets = listOf(BackupSplitSheet(5, SplitMode.ITEM, ShareKind.RATIO, "W1")),
        shareGroups = listOf(BackupShareGroup(id = 3, paymentId = 5, pieceCount = 2, isShared = true, sortOrder = 0)),
        shareGroupItems = listOf(BackupShareGroupItem(groupId = 3, paymentItemId = 11)),
        participants = listOf(
            BackupParticipant(id = 21, paymentId = 5, personId = 1, isPayer = true, sortOrder = 0),
            BackupParticipant(id = 22, paymentId = 5, personId = 2, memo = "一言", label = "新人", sortOrder = 1),
            BackupParticipant(id = 23, paymentId = 5, personId = null, sortOrder = 2),
        ),
        labelAmounts = listOf(BackupLabelAmount(paymentId = 5, label = "新人", amount = 3000)),
        itemShares = listOf(
            BackupItemShare(groupId = 3, participantId = 21, kind = ShareKind.RATIO, weight = 2),
            BackupItemShare(groupId = 3, participantId = 22, kind = ShareKind.RATIO, weight = 1),
        ),
        wholeShares = listOf(BackupWholeShare(paymentId = 5, participantId = 22, kind = ShareKind.AMOUNT, amount = 300)),
        paymentPayers = listOf(BackupPaymentPayer(id = 7, paymentId = 5, participantId = 21, method = "カード", amount = 980)),
    )

    @Test
    fun format8_roundTrip_keepsParticipantsLabelAmountsAndParticipantIds() {
        val data = v8Data()
        val text = BackupJson.encodeToString(BackupData.serializer(), data)
        val back = BackupJson.decodeFromString(BackupData.serializer(), text)
        assertEquals(data, back)
        assertEquals(BACKUP_FORMAT_VERSION, back.formatVersion)
        assertEquals(3, back.participants.size)
        assertEquals(listOf(BackupLabelAmount(5, "新人", 3000)), back.labelAmounts)
        assertEquals(21L, back.itemShares[0].participantId)
        assertEquals(22L, back.wholeShares.single().participantId)
        assertEquals(21L, back.paymentPayers.single().participantId)
        for (key in listOf("participants", "labelAmounts", "splitSheets", "shareGroups", "shareGroupItems", "itemShares", "wholeShares", "paymentPayers")) {
            assertTrue("$key が JSON に入る", text.contains("\"$key\""))
        }
        assertTrue(text.contains("\"participantId\":21"))
        assertTrue(text.contains("\"wholeKind\":\"ratio\""))
    }

    @Test
    fun format8_entityConversions_areLossless() {
        val p = SplitParticipant(id = 22, paymentId = 5, personId = 2, memo = "一言", label = "新人", isPayer = false, sortOrder = 1)
        assertEquals(p, BackupParticipant.from(p).toEntity())
        val la = SplitLabelAmount(5, "", 500)
        assertEquals(la, BackupLabelAmount.from(la).toEntity())
        val sheet = SplitSheet(5, SplitMode.WHOLE, ShareKind.RATIO, "W1,W3")
        assertEquals(sheet, BackupSplitSheet.from(sheet).toEntity())
        val ia = ItemShare(3, 21, ShareKind.AMOUNT, weight = null, amount = 100)
        assertEquals(ia, BackupItemShare.from(ia).toEntity())
        val pp = PaymentPayer(id = 7, paymentId = 5, participantId = 21, method = "現金", amount = 500)
        assertEquals(pp, BackupPaymentPayer.from(pp).toEntity())
    }

    @Test
    fun format7_fileWithPersonIdShares_isReadable_sharesDropped_sheetsAndGroupsKept() {
        // 形式7: 割当・支払内訳の参照先は人(personId)。読めるが、取り込みでは割当・支払内訳を捨てる
        val text = """{"formatVersion":7,"dbVersion":10,"exportedAt":1,
            "persons":[{"id":1,"uid":"p-self","name":"自分","shortName":"私","isSelf":true,"sortOrder":0,"createdAt":10},
                       {"id":2,"uid":"p-friend","name":"髙橋","shortName":"髙","isSelf":false,"sortOrder":1,"createdAt":11}],
            "splitSheets":[{"receiptId":5,"mode":"item"}],
            "shareGroups":[{"id":3,"receiptId":5,"pieceCount":2,"isShared":false,"sortOrder":0}],
            "shareGroupItems":[{"groupId":3,"receiptItemId":11}],
            "itemShares":[{"groupId":3,"personId":1,"kind":"ratio","weight":2},{"groupId":3,"personId":2,"kind":"ratio","weight":1}],
            "wholeShares":[{"receiptId":5,"personId":2,"kind":"amount","amount":300}],
            "paymentPayers":[{"id":7,"receiptId":5,"personId":1,"method":"カード","amount":980}]}"""
        val d = BackupJson.decodeFromString(BackupData.serializer(), text)
        BackupManager.checkVersion(d.formatVersion)
        assertEquals(7, d.formatVersion)
        assertEquals(2, d.persons.size)
        assertEquals(2, d.itemShares.size)
        assertEquals(1, d.paymentPayers.size)
        // 形式8で足した項目は既定値。参加者・ラベル金額は無い
        assertEquals(ShareKind.AMOUNT, d.splitSheets.single().wholeKind)
        assertEquals("", d.splitSheets.single().warnings)
        assertTrue(d.participants.isEmpty())
        assertTrue(d.labelAmounts.isEmpty())
        // 取り込み: 割当・支払内訳は捨て、シート・まとまり・品目のまとまりは残す
        val r = remapSplitRows(d, paymentIdMap, itemIdMap, groupIdMap, participantIdMap = emptyMap())
        assertTrue(r.itemShares.isEmpty())
        assertTrue(r.wholeShares.isEmpty())
        assertTrue(r.payers.isEmpty())
        assertEquals(listOf(SplitSheet(50, SplitMode.ITEM)), r.sheets)
        assertEquals(listOf(ShareGroupItem(300, 111)), r.groupItems)
    }

    @Test
    fun format6_withoutSplitKeys_isReadable_andSplitListsAreEmpty() {
        val text = """{"formatVersion":6,"dbVersion":9,"exportedAt":1,
            "expenses":[{"id":1,"date":"2026-10-05","amount":1,"category":"食費","createdAt":1}],
            "receiptNotDuplicates":[]}"""
        val d = BackupJson.decodeFromString(BackupData.serializer(), text)
        BackupManager.checkVersion(d.formatVersion)
        assertEquals(1, d.expenseItems.size)
        assertTrue(d.persons.isEmpty())
        assertTrue(d.splitSheets.isEmpty())
        assertTrue(d.shareGroups.isEmpty())
        assertTrue(d.shareGroupItems.isEmpty())
        assertTrue(d.participants.isEmpty())
        assertTrue(d.labelAmounts.isEmpty())
        assertTrue(d.itemShares.isEmpty())
        assertTrue(d.wholeShares.isEmpty())
        assertTrue(d.paymentPayers.isEmpty())
    }

    @Test
    fun checkVersion_acceptsUpToTen_rejectsEleven() {
        BackupManager.checkVersion(7)
        BackupManager.checkVersion(8)
        BackupManager.checkVersion(9)
        BackupManager.checkVersion(10)
        try {
            BackupManager.checkVersion(11)
            fail("expected BackupException")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("新しい"))
        }
    }

    // ------------------------------------------------------------ 参加者の付け替え

    @Test
    fun remapParticipants_followsPaymentAndPerson_keepsNullPerson_andDropsMissingPayment() {
        val ps = listOf(
            BackupParticipant(id = 21, paymentId = 5, personId = 1, isPayer = true),
            BackupParticipant(id = 22, paymentId = 5, personId = 9, label = "新人"), // 人 9 が無い -> personId は null
            BackupParticipant(id = 23, paymentId = 5, personId = null), // 名前なし
            BackupParticipant(id = 24, paymentId = 9, personId = 2), // 支払い 9 が無い -> 捨てる
        )
        val r = remapParticipants(ps, paymentIdMap, personIdMap)
        assertEquals(
            listOf(
                21L to SplitParticipant(id = 0, paymentId = 50, personId = 100, isPayer = true),
                22L to SplitParticipant(id = 0, paymentId = 50, personId = null, label = "新人"),
                23L to SplitParticipant(id = 0, paymentId = 50, personId = null),
            ),
            r,
        )
    }

    @Test
    fun remapSplitRows_v8_keepsSharesThatPointAtMappedParticipants() {
        val r = remapSplitRows(v8Data(), paymentIdMap, itemIdMap, groupIdMap, participantIdMap)
        assertEquals(
            listOf(ItemShare(300, 310, ShareKind.RATIO, weight = 2, amount = null), ItemShare(300, 320, ShareKind.RATIO, weight = 1, amount = null)),
            r.itemShares,
        )
        assertEquals(listOf(WholeShare(50, 320, ShareKind.AMOUNT, amount = 300)), r.wholeShares)
        assertEquals(listOf(PaymentPayer(id = 0, paymentId = 50, participantId = 310, method = "カード", amount = 980)), r.payers)
        assertEquals(listOf(SplitLabelAmount(50, "新人", 3000)), r.labelAmounts)
        assertFalse(r.sheets.isEmpty())
    }
}
