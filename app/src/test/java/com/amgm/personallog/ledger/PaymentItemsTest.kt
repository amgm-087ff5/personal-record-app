package com.amgm.personallog.ledger

import com.amgm.personallog.data.model.CategoryTotal
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.domain.LedgerCalc
import com.amgm.personallog.domain.PaymentKind
import com.amgm.personallog.domain.PaymentField
import com.amgm.personallog.domain.PaymentLink
import com.amgm.personallog.ui.ledger.ChartLevel
import com.amgm.personallog.ui.ledger.DraftItem
import com.amgm.personallog.ui.ledger.DraftPayment
import com.amgm.personallog.ui.ledger.LedgerChart
import com.amgm.personallog.ui.ledger.LedgerEntry
import com.amgm.personallog.ui.ledger.LedgerLogic
import com.amgm.personallog.ui.ledger.PaymentMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaymentItemsTest {
    private fun group(amount: Int, category: String = "日用品") =
        ExpenseGroup(id = 7, date = "2026-10-03", time = "10:15", amount = amount, category = category, memo = "スーパー", createdAt = 5)

    private fun item(id: Long, amount: Int, category: String) =
        Expense(id = id, date = "2026-10-03", time = "10:15", amount = amount, category = category, groupId = 7, createdAt = 5)

    // ------------------------------------------------------------ 品目ごとのカテゴリ集計

    @Test
    fun group_isCountedPerItemCategory_notByGroupCategory() {
        val g = LedgerEntry.Group(group(700), listOf(item(1, 200, "食費"), item(2, 500, "日用品")))
        val totals = LedgerLogic.totalsByCategory(listOf(g))
        assertEquals(listOf(CategoryTotal("日用品", 500), CategoryTotal("食費", 200)), totals)
        assertEquals(700L, totals.sumOf { it.total }) // 合計は支払い金額と一致
    }

    @Test
    fun difference_goesToTheGroupCategory_andTotalsStillMatch() {
        // 支払い金額 1000、品目の合計 900 -> 差額 100 は日用品(グループのカテゴリ)
        val g = LedgerEntry.Group(group(1000, "日用品"), listOf(item(1, 400, "食費"), item(2, 500, "日用品")))
        assertEquals(100L, g.difference)
        val m = LedgerLogic.totalsByCategory(listOf(g)).associate { it.category to it.total }
        assertEquals(mapOf("食費" to 400L, "日用品" to 600L), m)
        assertEquals(g.amount, m.values.sum())
    }

    @Test
    fun negativeDifference_isAlsoAbsorbedByGroupCategory() {
        val g = LedgerEntry.Group(group(800, "日用品"), listOf(item(1, 500, "食費"), item(2, 400, "日用品")))
        assertEquals(-100L, g.difference)
        val m = LedgerLogic.totalsByCategory(listOf(g)).associate { it.category to it.total }
        assertEquals(mapOf("食費" to 500L, "日用品" to 300L), m)
        assertEquals(800L, m.values.sum())
    }

    @Test
    fun groupWithoutItems_isCountedByGroupCategory() {
        val g = LedgerEntry.Group(group(300, "食費"), emptyList())
        assertEquals(listOf(CategoryTotal("食費", 300)), LedgerLogic.totalsByCategory(listOf(g)))
    }

    @Test
    fun mixedEntries_sumToTheirAmounts_andDailyTotalsAreNotDoubleCounted() {
        val g = LedgerEntry.Group(group(700), listOf(item(1, 200, "食費"), item(2, 500, "日用品")))
        val single = LedgerEntry.Single(Expense(id = 9, date = "2026-10-03", amount = 300, category = "食費", createdAt = 6))
        val entries = listOf(g, single)
        val totals = LedgerLogic.totalsByCategory(entries)
        assertEquals(1000L, totals.sumOf { it.total })
        assertEquals(500L, totals.first { it.category == "食費" }.total)
        // 日ごとの合計は、グループの金額のまま
        assertEquals(1000L, LedgerLogic.groupByDate(entries).single().total)
        // 割合
        val shares = LedgerLogic.categoryShares(totals)
        assertEquals(listOf(50, 50), shares.map { it.percent })
    }

    @Test
    fun chart_segmentsLegendAndHoursUseItemCategories() {
        val g = LedgerEntry.Group(group(700), listOf(item(1, 200, "食費"), item(2, 500, "日用品")))
        val day = LocalDate.parse("2026-10-03")
        val segs = LedgerChart.segments(listOf(g), day, day)
        assertEquals(mapOf("日用品" to 500L, "食費" to 200L), segs.associate { it.category to it.amount })
        val hour = LedgerChart.hourSegments(listOf(g), day, 10)
        assertEquals(700L, hour.sumOf { it.amount })
        val legend = LedgerChart.legend(listOf(g), ChartLevel.DAY, day)
        assertEquals(setOf("食費", "日用品"), legend.map { it.category }.toSet())
        val bars = LedgerChart.bars(ChartLevel.WEEK, day, listOf(g))
        assertEquals(700L, bars.sumOf { it.total })
    }

    @Test
    fun negativeCategory_isHiddenFromShares_butNotFromTotals() {
        // 品目の合計が支払い金額を大きく超え、グループのカテゴリが負になる
        val g = LedgerEntry.Group(group(300, "日用品"), listOf(item(1, 500, "食費"), item(2, 100, "日用品")))
        val totals = LedgerLogic.totalsByCategory(listOf(g))
        assertEquals(300L, totals.sumOf { it.total })
        val shares = LedgerLogic.categoryShares(totals)
        assertTrue(shares.all { it.total > 0 })
    }

    @Test
    fun allocationNote_isTheAgreedSentence() {
        assertEquals(
            "品目ごとの金額は、レシート全体の端数を品目に割り振った値です。品目単位では1円ほどずれることがありますが、合計は一致します。",
            com.amgm.personallog.ui.ledger.ALLOCATION_NOTE,
        )
    }

    // ------------------------------------------------------------ 品目つきの手入力

    @Test
    fun draftPayment_buildsReceiptAndItems_forTheSavePath() {
        val r = DraftPayment.build(
            "2026-10-03", "10:15", "700", " スーパー ",
            listOf(
                DraftItem("牛乳", "200", "食費", "特売"),
                DraftItem("", "", "未分類", ""), // 空行は捨てる
                DraftItem("洗剤", "500", "日用品"),
            ),
            now = 9,
        )
        assertTrue(r.errors.toString(), r.isValid)
        // 支払い(レシートの表)は手入力。合計が入っているので確定。店名は memo から
        val receipt = r.receipt!!
        assertEquals(700, receipt.totalAmount)
        assertEquals("2026-10-03 10:15", receipt.purchasedAt)
        assertEquals("スーパー", receipt.storeName)
        assertEquals("", receipt.imagePath)
        assertEquals("", receipt.rawJson)
        assertEquals("confirmed", receipt.status)
        assertEquals("manual", receipt.totalSource)
        assertEquals(9L, receipt.createdAt)
        // 品目は空行を除いて2件。支払いの保存で receiptId が入る
        assertEquals(listOf("牛乳", "洗剤"), r.items.map { it.name })
        assertEquals(listOf("特売", ""), r.items.map { it.memo })
        assertEquals(listOf(200, 500), r.items.map { it.amount })
        assertEquals(listOf("食費", "日用品"), r.items.map { it.category })
        assertTrue(r.items.all { it.receiptId == 0L && it.id == 0L })
        // 家計簿のグループのカテゴリは、金額が最も高い品目のカテゴリ(支出は支払いから作られる)
        assertEquals("日用品", LedgerCalc.groupCategory(r.items))
    }

    @Test
    fun draftPayment_validation() {
        val ok = listOf(DraftItem("a", "100"))
        assertFalse(DraftPayment.build("2026-13-01", "10:15", "100", "", ok, 1).isValid)
        assertFalse(DraftPayment.build("2026-10-03", "1015", "100", "", ok, 1).isValid)
        assertFalse(DraftPayment.build("2026-10-03", "10:15", "0", "", ok, 1).isValid)
        assertFalse(DraftPayment.build("2026-10-03", "10:15", "100", "", listOf(DraftItem("a", "")), 1).isValid)
        assertFalse(DraftPayment.build("2026-10-03", "10:15", "100", "", listOf(DraftItem("a", "-5")), 1).isValid)
        assertTrue(DraftPayment.build("2026-10-03", "10:15", "100", "", ok, 1).isValid)
    }

    @Test
    fun draftPayment_confirmedWhenTotalOrItems_draftWhenNeither() {
        // 合計だけ(品目なし)は確定
        val totalOnly = DraftPayment.build("2026-10-03", "10:15", "100", "", emptyList(), 1)
        assertTrue(totalOnly.isValid)
        assertEquals("confirmed", totalOnly.receipt!!.status)
        assertEquals(100, totalOnly.receipt!!.totalAmount)
        // 合計が空で品目があるときは、品目の合計を入れて確定
        val fromItems = DraftPayment.build(
            "2026-10-03", "10:15", "", "", listOf(DraftItem("a", "120"), DraftItem("b", "380")), 1,
        )
        assertTrue(fromItems.errors.toString(), fromItems.isValid)
        assertEquals("confirmed", fromItems.receipt!!.status)
        assertEquals(500, fromItems.receipt!!.totalAmount)
        assertEquals("manual", fromItems.receipt!!.totalSource)
        // 入力した合計があれば、品目の合計より入力を優先する
        val typed = DraftPayment.build("2026-10-03", "10:15", "480", "", listOf(DraftItem("a", "120"), DraftItem("b", "380")), 1)
        assertEquals(480, typed.receipt!!.totalAmount)
        // 合計も品目も無ければ下書き(合計は未入力)。日付と時刻は必要
        val neither = DraftPayment.build("2026-10-03", "10:15", "", "", emptyList(), 1)
        assertTrue(neither.errors.toString(), neither.isValid)
        assertEquals("draft", neither.receipt!!.status)
        assertNull(neither.receipt!!.totalAmount)
        assertFalse(DraftPayment.build("2026-10-03", "", "", "", emptyList(), 1).isValid)
    }

    @Test
    fun draftPayment_itemsSumAndOneTapText() {
        val items = listOf(DraftItem("a", "120"), DraftItem("", ""), DraftItem("b", "380"), DraftItem("c", "x"))
        assertEquals(500L, DraftPayment.itemsSum(items))
        assertEquals("500", DraftPayment.sumText(items))
        assertNull(DraftPayment.sumText(listOf(DraftItem(), DraftItem())))
    }

    // ------------------------------------------------------------ 「レシートと違う」の印、レシートへのリンク

    @Test
    fun marks_onlyForReceiptLinkedRows() {
        val g = group(700).copy(receiptId = 1, amountEdited = true, timeEdited = true)
        assertEquals(setOf(PaymentField.AMOUNT, PaymentField.TIME), PaymentMarks.ofGroup(g))
        // 手入力の支払いは、直してもレシートと比べるものがない
        assertTrue(PaymentMarks.ofGroup(g.copy(receiptId = null)).isEmpty())
        val e = item(1, 200, "食費").copy(receiptId = 1, receiptItemId = 11, nameEdited = true, memoEdited = true, categoryEdited = true)
        assertEquals(setOf(PaymentField.NAME, PaymentField.MEMO, PaymentField.CATEGORY), PaymentMarks.ofExpense(e))
        assertTrue(PaymentMarks.ofExpense(e.copy(receiptId = null, receiptItemId = null)).isEmpty())
        // 手で足した品目(グループ内・レシートの品目と対応しない)は印なし
        assertTrue(PaymentMarks.ofExpense(item(2, 100, "食費").copy(amountEdited = true)).isEmpty())
    }

    @Test
    fun paymentLink_prefersGroup_thenSingleExpense() {
        val g = group(700).copy(receiptId = 1)
        val single = Expense(id = 4, date = "2026-10-03", amount = 300, createdAt = 1, receiptId = 1)
        val child = item(5, 200, "食費").copy(receiptId = 1)
        assertEquals(PaymentLink(PaymentKind.GROUP, 7, 700), PaymentLink.resolve(listOf(g), listOf(child)))
        assertEquals(PaymentLink(PaymentKind.EXPENSE, 4, 300), PaymentLink.resolve(emptyList(), listOf(single)))
        // グループの品目だけが残っていても、単独の支出としては出さない
        assertNull(PaymentLink.resolve(emptyList(), listOf(child)))
        assertNull(PaymentLink.resolve(emptyList(), emptyList()))
        assertEquals(PaymentKind.GROUP, PaymentKind.of("group"))
        assertNull(PaymentKind.of("x"))
    }
}
