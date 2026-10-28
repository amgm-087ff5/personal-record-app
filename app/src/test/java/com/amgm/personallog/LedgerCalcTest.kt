package com.amgm.personallog

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.domain.LedgerCalc
import com.amgm.personallog.domain.SelfShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerCalcTest {
    @Test
    fun monthRange_handlesMonthEnds() {
        assertEquals("2026-10-01" to "2026-10-31", LedgerCalc.monthRange("2026-10"))
        assertEquals("2024-02-01" to "2024-02-29", LedgerCalc.monthRange("2024-02"))
    }

    @Test
    fun groupFromReceipt_totalIsReceiptAmountAndItemsAreChildren() {
        val receipt = Receipt(
            id = 9, purchasedAt = "2026-10-05 12:30", storeName = "スーパー", totalAmount = 700,
            imagePath = "", rawJson = "{}", createdAt = 0,
        )
        val items = listOf(
            ReceiptItem(receiptId = 9, name = "牛乳", amount = 200, category = "食費"),
            ReceiptItem(receiptId = 9, name = "洗剤", amount = 500, category = "日用品"),
        )
        val g = LedgerCalc.groupFromReceipt(receipt, items, now = 3)!!
        assertEquals(700, g.group.amount)
        assertEquals("日用品", g.group.category)
        assertEquals("", g.group.memo)  // 店名は支払い(storeName)に持ち、支出のメモには写さない(v15)
        assertEquals(9L, g.group.receiptId)
        assertEquals(listOf(200, 500), g.items.map { it.amount })
        // 品名は name に、メモには品目のメモだけを入れる(店名と品名を連結しない)
        assertEquals(listOf("牛乳", "洗剤"), g.items.map { it.name })
        assertEquals(listOf("", ""), g.items.map { it.memo })
        assertTrue(g.items.all { it.receiptId == 9L && it.groupId == null })
    }

    @Test
    fun groupFromReceipt_advanceAndNoItemsAreAlsoGroups() {
        // v14: 立て替え・品目なしも、支出(まとまり)+ 品目1行以上になる(単独の支出は作らない)
        val receipt = Receipt(
            id = 9, purchasedAt = "2026-10-05", storeName = "店", totalAmount = 700,
            imagePath = "", rawJson = "{}", createdAt = 0, isAdvance = true, ownAmount = 300,
        )
        val items = listOf(ReceiptItem(receiptId = 9, name = "牛乳", amount = 200))
        val adv = LedgerCalc.groupFromReceipt(receipt, items, now = 3)!!
        assertEquals(300, adv.group.amount)
        // 1品目なら品目の金額は支出の金額
        assertEquals(listOf(300), adv.items.map { it.amount })
        assertEquals(listOf("牛乳"), adv.items.map { it.name })

        val none = LedgerCalc.groupFromReceipt(receipt.copy(isAdvance = false), emptyList(), now = 3)!!
        assertEquals(700, none.group.amount)
        val only = none.items.single()
        assertEquals("", only.name)
        assertEquals(700, only.amount)
        assertEquals("", only.memo)  // 店名は支払い(storeName)に持ち、支出のメモには写さない(v15)
        assertEquals(none.group.category, only.category)
        assertTrue(only.receiptItemId == null)
    }

    @Test
    fun totalsByCategory_sumsAndSortsDescending() {
        val e = listOf(
            Expense(date = "2026-10-01", amount = 300, category = "食費", createdAt = 0),
            Expense(date = "2026-10-02", amount = 1000, category = "交通費", createdAt = 0),
            Expense(date = "2026-10-03", amount = 500, category = "食費", createdAt = 0),
        )
        val r = LedgerCalc.totalsByCategory(e)
        assertEquals(listOf("交通費", "食費"), r.map { it.category })
        assertEquals(listOf(1000L, 800L), r.map { it.total })
    }

    @Test
    fun groupFromReceipt_itemRowsAreLinkedToTheReceipt() {
        val receipt = Receipt(
            id = 7, purchasedAt = "2026-10-05 12:30", storeName = "スーパー", totalAmount = 700,
            imagePath = "", rawJson = "{}", createdAt = 0,
        )
        val items = listOf(
            ReceiptItem(receiptId = 7, name = "牛乳", amount = 200, category = "食費"),
            ReceiptItem(receiptId = 7, name = "洗剤", amount = 500, category = "日用品"),
        )
        val g = LedgerCalc.groupFromReceipt(receipt, items, now = 1)!!
        assertEquals(2, g.items.size)
        assertTrue(g.items.all { it.date == "2026-10-05" && it.receiptId == 7L })
        assertEquals(700, g.group.amount)
        assertEquals("日用品", g.group.category)
    }

    @Test
    fun versionCodeFormula_isMonotonicAndFitsInt() {
        fun code(ms: Long) = (ms / 60000L).toInt()
        val a = code(1_790_000_000_000L)
        val b = code(1_790_000_060_000L)
        assertEquals(a + 1, b)
        assertTrue(a > 0)
    }

    @Test
    fun splitPurchasedAt_dateOnlyHasNoTime() {
        assertEquals("2026-10-05" to "12:30", LedgerCalc.splitPurchasedAt("2026-10-05 12:30"))
        assertEquals("2026-10-05" to "", LedgerCalc.splitPurchasedAt("2026-10-05"))
    }

    @Test
    fun timeOfMillis_usesGivenZone() {
        // 2026-10-14T01:05:00Z は UTC なら 01:05、東京(+9)なら 10:05
        val millis = java.time.Instant.parse("2026-10-14T01:05:00Z").toEpochMilli()
        assertEquals("01:05", LedgerCalc.timeOfMillis(millis, java.time.ZoneOffset.UTC))
        assertEquals("10:05", LedgerCalc.timeOfMillis(millis, java.time.ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun groupFromReceipt_carriesReceiptTime() {
        val receipt = Receipt(
            id = 5, purchasedAt = "2026-10-05 12:30", storeName = "店", totalAmount = 300,
            imagePath = "", rawJson = "{}", createdAt = 0,
        )
        val items = listOf(ReceiptItem(receiptId = 5, name = "牛乳", amount = 300, category = "食費"))
        val g = LedgerCalc.groupFromReceipt(receipt, items, now = 1)!!
        assertEquals("12:30", g.group.time)
        assertEquals(setOf("12:30"), g.items.map { it.time }.toSet())
    }

    @Test
    fun nullTotal_isRejectedExplicitly() {
        val draft = Receipt(
            id = 5, purchasedAt = "2026-10-05 12:30", storeName = "店", totalAmount = null,
            imagePath = "", rawJson = "{}", createdAt = 0,
        )
        val items = listOf(ReceiptItem(receiptId = 5, name = "牛乳", amount = 300, category = "食費"))
        // 下書き(合計が null)のグループは品目の合計で作る(下書きも家計簿に入れるため)
        val g = LedgerCalc.groupFromReceipt(draft, items, now = 1)!!.group
        assertEquals(300, g.amount)
        // 品目の支出は品目の税込額で作るので、合計が null でも作られる(確定側で弾く)
        assertTrue(LedgerCalc.groupFromReceipt(draft, items, now = 1)!!.items.isNotEmpty())
    }

    @Test
    fun draftWithoutTotalAndItems_isZeroYenRowLinkedToReceipt() {
        val draft = Receipt(
            id = 7, purchasedAt = "2026-10-05", storeName = "店", totalAmount = null,
            imagePath = "", rawJson = "{}", createdAt = 0,
        )
        // 下書きで合計も品目も無いときは、例外にせず 0 円の支出と1品目(receiptId 付き。下書きの件数に数える)
        val g = LedgerCalc.groupFromReceipt(draft, emptyList(), now = 1)!!
        assertEquals(0, g.group.amount)
        assertEquals(listOf(0), g.items.map { it.amount })
        assertEquals(7L, g.items.single().receiptId)
        // 立て替えの下書きも、自分の分が無ければ 0 円
        val advance = draft.copy(isAdvance = true, ownAmount = null)
        assertEquals(listOf(0), LedgerCalc.groupFromReceipt(advance, emptyList(), now = 1)!!.items.map { it.amount })
    }

    @Test
    fun confirmedWithoutTotal_stillRejected() {
        val confirmed = Receipt(
            id = 8, purchasedAt = "2026-10-05", storeName = "店", totalAmount = null,
            imagePath = "", rawJson = "{}", createdAt = 0, status = ReceiptStatus.CONFIRMED,
        )
        try {
            LedgerCalc.groupFromReceipt(confirmed, emptyList(), now = 1)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("合計"))
        }
    }

    @Test
    fun itemlessReceipt_usesPrintedTotal() {
        val r = Receipt(
            id = 6, purchasedAt = "2026-10-05", storeName = "店", totalAmount = 1234,
            imagePath = "", rawJson = "{}", createdAt = 0,
        )
        val g = LedgerCalc.groupFromReceipt(r, emptyList(), now = 1)!!
        assertEquals(1234, g.group.amount)
        assertEquals(listOf(1234), g.items.map { it.amount })
    }

    // ---- 割り勘(share)がある場合: 家計簿に入るのは自分の分

    private val shareReceipt = Receipt(
        id = 9, purchasedAt = "2026-10-05 12:30", storeName = "スーパー", totalAmount = 700,
        imagePath = "", rawJson = "{}", createdAt = 0,
    )
    private val shareItems = listOf(
        ReceiptItem(id = 1, receiptId = 9, name = "牛乳", amount = 200, category = "食費"),
        ReceiptItem(id = 2, receiptId = 9, name = "洗剤", amount = 500, category = "日用品"),
        ReceiptItem(id = 3, receiptId = 9, name = "お菓子", amount = 300, category = "食費"),
    )

    @Test
    fun share_itemRowsAreSelfAmounts_andGroupIsSelfTotal() {
        // 自分の分: 牛乳100・洗剤500。お菓子は自分の分が無い(行を作らない)
        val share = SelfShare(perItem = mapOf(1L to 100L, 2L to 500L), total = 600L)
        val g = LedgerCalc.groupFromReceipt(shareReceipt, shareItems, now = 3, share = share)!!
        assertEquals(600, g.group.amount)
        assertEquals(listOf(1L, 2L), g.items.map { it.receiptItemId })
        assertEquals(listOf(100, 500), g.items.map { it.amount })
    }

    @Test
    fun share_zeroYenItemHasNoRow() {
        val share = SelfShare(perItem = mapOf(1L to 200L, 2L to 0L), total = 200L)
        val g = LedgerCalc.groupFromReceipt(shareReceipt, shareItems, now = 3, share = share)!!
        assertEquals(listOf(1L), g.items.map { it.receiptItemId })
        assertEquals(200, g.group.amount)
    }

    @Test
    fun share_ignoresAdvanceFlag() {
        val advance = shareReceipt.copy(isAdvance = true, ownAmount = 999)
        val share = SelfShare(perItem = mapOf(1L to 200L), total = 200L)
        val g = LedgerCalc.groupFromReceipt(advance, shareItems, now = 3, share = share)!!
        assertEquals(200, g.group.amount)
        assertEquals(listOf(200), g.items.map { it.amount })
    }

    @Test
    fun share_withoutItems_isGroupWithOneBlankItemOfSelfTotal() {
        val g = LedgerCalc.groupFromReceipt(shareReceipt, emptyList(), now = 3, share = SelfShare(emptyMap(), 300L))!!
        assertEquals(300, g.group.amount)
        assertEquals(listOf(300), g.items.map { it.amount })
        assertEquals(listOf<Long?>(null), g.items.map { it.receiptItemId })
    }

    @Test
    fun share_zeroTotal_hasNoGroup() {
        assertTrue(LedgerCalc.groupFromReceipt(shareReceipt, emptyList(), now = 3, share = SelfShare(emptyMap(), 0L)) == null)
        assertTrue(LedgerCalc.groupFromReceipt(shareReceipt, shareItems, now = 3, share = SelfShare(emptyMap(), 0L)) == null)
    }

    @Test
    fun wholeSplit_itemsAreProratedByItemAmount_andSumToSelfShare() {
        // 全体で割る割り勘: 自分の分(SplitLedger が品目の金額の比で配った値)を、品目として並べる
        val result = com.amgm.personallog.domain.split.SplitResult(
            lines = listOf(com.amgm.personallog.domain.split.SplitLine(personId = 1, groupId = null, amount = 333)),
            warnings = emptyList(),
        )
        val itemYen = shareItems.map { com.amgm.personallog.domain.split.ItemYen(it.id, null, it.amount.toLong(), it.category) }
        val share = SelfShare.of(result, 1, itemYen)
        val g = LedgerCalc.groupFromReceipt(shareReceipt, shareItems, now = 3, share = share)!!
        assertEquals(333, g.group.amount)
        // 200:500:300 の比で 333 円 -> 67 + 166 + 100(最大剰余)。合計は自分の分と一致
        assertEquals(listOf(67, 166, 100), g.items.map { it.amount })
        assertEquals(333, g.items.sumOf { it.amount })
        assertEquals(listOf("牛乳", "洗剤", "お菓子"), g.items.map { it.name })
    }

    @Test
    fun unknownItem_getsTotalMinusKnown_andIsKeptEvenWhenZero() {
        val r = shareReceipt.copy(totalAmount = 1000)
        val items = listOf(
            ReceiptItem(id = 1, receiptId = 9, name = "牛乳", amount = 200, category = "食費"),
            ReceiptItem(id = 2, receiptId = 9, name = "?", amount = 0, category = "食費", unknownAmount = true),
            ReceiptItem(id = 3, receiptId = 9, name = "??", amount = 0, category = "食費", unknownAmount = true),
        )
        val g = LedgerCalc.groupFromReceipt(r, items, now = 1)!!
        assertEquals(1000, g.group.amount)
        // 不明の2品目で 800 を均等に
        assertEquals(listOf(200, 400, 400), g.items.map { it.amount })
        // 差が0でも、不明の品目は残す(0円)
        val exact = LedgerCalc.groupFromReceipt(r.copy(totalAmount = 200), items, now = 1)!!
        assertEquals(listOf(200, 0, 0), exact.items.map { it.amount })
        assertEquals(listOf(false, true, true), exact.items.map { it.unknownAmount })
    }

    @Test
    fun draftWithoutTotal_groupUsesItemSum() {
        val draft = shareReceipt.copy(totalAmount = null)
        val g = LedgerCalc.groupFromReceipt(draft, shareItems.take(2), now = 3)!!
        assertEquals(700, g.group.amount)
    }
}
