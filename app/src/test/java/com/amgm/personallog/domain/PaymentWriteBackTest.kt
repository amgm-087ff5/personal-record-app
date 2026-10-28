package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.TotalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PaymentWriteBackTest {
    /** 手入力の支払い(画像なし)。合計 700 = 牛乳 200 + 洗剤 500 を基本にする。 */
    private fun receipt(total: Int? = 700, imagePath: String = "") = Receipt(
        id = 1, purchasedAt = "2026-10-31 20:00", storeName = "店", totalAmount = total,
        imagePath = imagePath, rawJson = "{}", createdAt = 0,
    )

    private val milkItem = ReceiptItem(id = 11, receiptId = 1, name = "牛乳", amount = 200, category = "食費")
    private val soapItem = ReceiptItem(id = 12, receiptId = 1, name = "洗剤", amount = 500, category = "日用品")
    private val items = listOf(milkItem, soapItem)

    private fun milkExpense(
        amount: Int = 200,
        name: String = "牛乳",
        category: String = "食費",
        memo: String = "",
        nameEdited: Boolean = false,
        categoryEdited: Boolean = false,
        memoEdited: Boolean = false,
        amountEdited: Boolean = false,
    ) = Expense(
        id = 21, date = "2026-10-31", time = "20:00", amount = amount, category = category, memo = memo,
        receiptId = 1, receiptItemId = 11, groupId = 31, name = name, createdAt = 0,
        nameEdited = nameEdited, categoryEdited = categoryEdited, memoEdited = memoEdited, amountEdited = amountEdited,
    )

    private fun group(
        date: String = "2026-10-31",
        time: String = "20:00",
        amount: Int = 700,
        category: String = "食費",
        memo: String = "店",
        timeEdited: Boolean = false,
        amountEdited: Boolean = false,
    ) = ExpenseGroup(
        id = 31, date = date, time = time, amount = amount, category = category, memo = memo, receiptId = 1,
        createdAt = 0, timeEdited = timeEdited, amountEdited = amountEdited,
    )

    private fun values(
        date: String = "2026-10-31",
        time: String = "20:00",
        amount: Int = 200,
        category: String = "食費",
        name: String = "牛乳",
        memo: String = "",
    ) = PaymentValues(date = date, time = time, amount = amount, category = category, name = name, memo = memo)

    private fun apply(plan: WriteBackPlan): WriteBackPlan.Apply {
        if (plan is WriteBackPlan.Reject) fail("拒否されるはずではない: ${plan.message}")
        return plan as WriteBackPlan.Apply
    }

    @Test
    fun itemAmountChangeWithoutSplitBecomesTaxIncludedAndManualTotalFollows() {
        // 税抜の牛乳 200(税込 220)。手入力で合計 220 + 500 = 720 のとき、合計も新しい品目の合計に合わせる
        val taxExcludedMilk = milkItem.copy(priceIncludesTax = false)
        val itemsNow = listOf(taxExcludedMilk, soapItem)
        val r = receipt(total = 720)
        val e = milkExpense(amount = 220)
        val p = apply(
            PaymentWriteBack.plan(WriteBackTarget.Item(e), values(amount = 250), r, itemsNow, people = 1),
        )
        val changed = p.changedItems.single()
        assertEquals(250, changed.amount)
        assertTrue(changed.priceIncludesTax)
        assertEquals(750, p.receipt.totalAmount)
        assertEquals(TotalSource.ITEMS, p.receipt.totalSource)
        assertEquals(250, p.expectedAmount)
    }

    @Test
    fun itemAmountChangeOnPrintedReceiptKeepsPrintedTotal() {
        val r = receipt(total = 700, imagePath = "receipt.jpg")
        val p = apply(
            PaymentWriteBack.plan(WriteBackTarget.Item(milkExpense()), values(amount = 250), r, items, people = 1),
        )
        assertEquals(700, p.receipt.totalAmount)
        assertEquals(250, p.changedItems.single().amount)
    }

    @Test
    fun itemAmountChangeOnManualReceiptWithDifferentTotalKeepsTotal() {
        // 合計 700 は品目の合計 200 と一致しない: 合計は変えない
        val r = receipt(total = 700)
        val itemsNow = listOf(milkItem.copy(amount = 100), soapItem.copy(amount = 100))
        val p = apply(
            PaymentWriteBack.plan(WriteBackTarget.Item(milkExpense(amount = 100)), values(amount = 150), r, itemsNow, people = 1),
        )
        assertEquals(700, p.receipt.totalAmount)
    }

    @Test
    fun itemAmountChangeIsRejectedWhenSplitHasTwoOrMorePeople() {
        val plan = PaymentWriteBack.plan(WriteBackTarget.Item(milkExpense()), values(amount = 250), receipt(), items, people = 2)
        assertTrue(plan is WriteBackPlan.Reject)
        assertEquals(PaymentWriteBack.MESSAGE_SPLIT_AMOUNT, (plan as WriteBackPlan.Reject).message)
    }

    @Test
    fun itemNameCategoryAndMemoWriteBackAndClearTheirEditedFlags() {
        // 家計簿で直した欄(編集済み)を、支払いの品目へ書き戻す
        val e = milkExpense(name = "牛乳", category = "日用品", memo = "", nameEdited = true, categoryEdited = true, memoEdited = true)
        val p = apply(
            PaymentWriteBack.plan(
                WriteBackTarget.Item(e),
                values(name = "牛乳(特売)", category = "外食", memo = "自分で"),
                receipt(), items, people = 1,
            ),
        )
        val changed = p.changedItems.single()
        assertEquals("牛乳(特売)", changed.name)
        assertEquals("外食", changed.category)
        assertEquals("自分で", changed.memo)
        val out = p.expense!!
        assertFalse(out.nameEdited)
        assertFalse(out.categoryEdited)
        assertFalse(out.memoEdited)
        assertEquals(700, p.receipt.totalAmount) // 金額は変えていないので、合計も変わらない
    }

    @Test
    fun unchangedItemValuesTouchNothingAndKeepFlags() {
        val e = milkExpense(category = "日用品", categoryEdited = true)
        val r = receipt()
        val p = apply(PaymentWriteBack.plan(WriteBackTarget.Item(e), values(category = "日用品"), r, items, people = 1))
        assertTrue(p.changedItems.isEmpty())
        assertEquals(r, p.receipt)
        assertTrue(p.expense!!.categoryEdited)
        assertNull(p.expectedAmount)
    }

    @Test
    fun groupDateTimeWritesPurchasedAtAndClearsTimeEdited() {
        val g = group(timeEdited = true)
        val p = apply(
            PaymentWriteBack.plan(
                WriteBackTarget.Group(g),
                values(date = "2026-11-01", time = "09:30", amount = 700, category = "食費", memo = "店"),
                receipt(), items, people = 1,
            ),
        )
        assertEquals("2026-11-01 09:30", p.receipt.purchasedAt)
        assertFalse(p.group!!.timeEdited)
        assertEquals("2026-11-01", p.group!!.date)
    }

    @Test
    fun groupDateChangeOnlyUpdatesPurchasedAtWhenTimeIsBlank() {
        val p = apply(
            PaymentWriteBack.plan(
                WriteBackTarget.Group(group()),
                values(date = "2026-11-02", time = "", amount = 700, category = "食費", memo = "店"),
                receipt(), items, people = 1,
            ),
        )
        assertEquals("2026-11-02", p.receipt.purchasedAt)
    }

    @Test
    fun groupAmountChangeOnManualReceiptWritesTotalAsManual() {
        val p = apply(
            PaymentWriteBack.plan(
                WriteBackTarget.Group(group()),
                values(amount = 800, category = "食費", memo = "店"),
                receipt(total = 700), items, people = 1,
            ),
        )
        assertEquals(800, p.receipt.totalAmount)
        assertEquals(TotalSource.MANUAL, p.receipt.totalSource)
        assertFalse(p.group!!.amountEdited)
        assertEquals(800, p.expectedAmount)
    }

    @Test
    fun groupAmountChangeOnPrintedReceiptIsRejected() {
        val plan = PaymentWriteBack.plan(
            WriteBackTarget.Group(group()),
            values(amount = 800, category = "食費", memo = "店"),
            receipt(total = 700, imagePath = "receipt.jpg"), items, people = 1,
        )
        assertTrue(plan is WriteBackPlan.Reject)
        assertEquals(PaymentWriteBack.MESSAGE_PRINTED_TOTAL, (plan as WriteBackPlan.Reject).message)
    }

    @Test
    fun groupAmountChangeIsRejectedWhenSplitHasTwoOrMorePeople() {
        val plan = PaymentWriteBack.plan(
            WriteBackTarget.Group(group()),
            values(amount = 800, category = "食費", memo = "店"),
            receipt(total = 700), items, people = 3,
        )
        assertEquals(PaymentWriteBack.MESSAGE_SPLIT_AMOUNT, (plan as WriteBackPlan.Reject).message)
    }

    @Test
    fun groupCategoryAndMemoStayOnTheGroup() {
        val p = apply(
            PaymentWriteBack.plan(
                WriteBackTarget.Group(group()),
                values(amount = 700, category = "外食", memo = "ひとこと"),
                receipt(), items, people = 1,
            ),
        )
        assertEquals(receipt(), p.receipt) // 支払いは変わらない
        assertEquals("外食", p.group!!.category)
        assertEquals("ひとこと", p.group!!.memo)
        assertTrue(p.group!!.categoryEdited)
        assertTrue(p.group!!.memoEdited)
        assertNull(p.expectedAmount)
    }

    @Test
    fun blankItem_amountChangeIsRejected_nameCategoryMemoStayOnTheItem() {
        // 品目の無い支払いの1品目(receiptItemId なし)。金額は支出の金額に従うので、品目の側では直せない
        val blank = Expense(id = 41, date = "2026-10-31", time = "20:00", amount = 700, receiptId = 1, groupId = 31, createdAt = 0)
        val reject = PaymentWriteBack.plan(
            WriteBackTarget.Item(blank, onlyItem = true),
            PaymentValues("2026-10-31", "20:00", 800, "食費", "", ""),
            receipt(), emptyList(), people = 1,
        )
        assertEquals(PaymentWriteBack.MESSAGE_BLANK_ITEM_AMOUNT, (reject as WriteBackPlan.Reject).message)

        val p = apply(
            PaymentWriteBack.plan(
                WriteBackTarget.Item(blank, onlyItem = true),
                PaymentValues("2026-10-31", "20:00", 700, "外食", "ランチ", "ひとこと"),
                receipt(), emptyList(), people = 1,
            ),
        )
        assertEquals(receipt(), p.receipt) // 支払いは変わらない
        assertTrue(p.changedItems.isEmpty())
        assertEquals("ランチ", p.expense!!.name)
        assertEquals("外食", p.expense!!.category)
        assertEquals("ひとこと", p.expense!!.memo)
        assertTrue(p.expense!!.nameEdited && p.expense!!.categoryEdited && p.expense!!.memoEdited)
    }

    @Test
    fun onlyItem_amountChangeIsRejected() {
        // 品目が1つなら、品目の金額は支出の金額と同じ(支出の金額で直す)
        val plan = PaymentWriteBack.plan(
            WriteBackTarget.Item(milkExpense(), onlyItem = true),
            values(amount = 250),
            receipt(), items, people = 1,
        )
        assertEquals(PaymentWriteBack.MESSAGE_ONLY_ITEM_AMOUNT, (plan as WriteBackPlan.Reject).message)
    }

    @Test
    fun groupAmountChangeWithoutItemsWritesManualTotal() {
        // 品目の無い手入力の支払い: 支出の金額は支払いの合計へ書き戻す(以前の単独の支出と同じ)
        val p = apply(
            PaymentWriteBack.plan(
                WriteBackTarget.Group(group()),
                values(amount = 800, category = "食費", memo = "店"),
                receipt(total = 700), emptyList(), people = 1,
            ),
        )
        assertEquals(800, p.receipt.totalAmount)
        assertEquals(TotalSource.MANUAL, p.receipt.totalSource)
        assertFalse(p.group!!.amountEdited)
    }

    @Test
    fun groupAmountChangeOfAdvanceIsRejected() {
        val plan = PaymentWriteBack.plan(
            WriteBackTarget.Group(group()),
            values(amount = 800, category = "食費", memo = "店"),
            receipt().copy(isAdvance = true, ownAmount = 300), items, people = 1,
        )
        assertEquals(PaymentWriteBack.MESSAGE_ADVANCE_AMOUNT, (plan as WriteBackPlan.Reject).message)
    }

    @Test
    fun itemAmountChange_clearsUnknown_andKeepsTotalWhenOtherUnknownRemains() {
        val unknownItem = ReceiptItem(id = 13, receiptId = 1, name = "?", amount = 0, category = "食費", unknownAmount = true)
        val withUnknown = items + unknownItem
        val unknownExpense = Expense(
            id = 23, date = "2026-10-31", time = "20:00", amount = 300, category = "食費", receiptId = 1, receiptItemId = 13,
            groupId = 31, name = "?", createdAt = 0, unknownAmount = true,
        )
        val p = apply(
            PaymentWriteBack.plan(
                WriteBackTarget.Item(unknownExpense),
                values(amount = 280, name = "?"),
                receipt(total = 1000), withUnknown, people = 1,
            ),
        )
        assertFalse(p.changedItems.single().unknownAmount)
        assertEquals(280, p.changedItems.single().amount)
        assertFalse(p.expense!!.unknownAmount)
        // 合計は変えない(計算の正は合計額)
        assertEquals(1000, p.receipt.totalAmount)
    }

    @Test
    fun withStoreNameTrimsAndReplacesOnlyTheStore() {
        val r = PaymentWriteBack.withStoreName(receipt(), "  新しい店 ")
        assertEquals("新しい店", r.storeName)
        assertEquals(700, r.totalAmount)
    }

    @Test
    fun purchasedAtOfKeepsDateOnlyWhenTimeIsBlank() {
        assertEquals("2026-10-31", PaymentWriteBack.purchasedAtOf("2026-10-31", ""))
        assertEquals("2026-10-31 20:05", PaymentWriteBack.purchasedAtOf("2026-10-31", "20:05"))
    }
}
