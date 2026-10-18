package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmPlannerTest {
    private fun receipt(isAdvance: Boolean = false, total: Int = 700, store: String = "スーパー", at: String = "2026-10-05 12:30") = Receipt(
        id = 1, purchasedAt = at, storeName = store, totalAmount = total,
        imagePath = "", rawJson = "{}", createdAt = 0, isAdvance = isAdvance,
    )

    private fun item(id: Long, name: String, amount: Int, category: String, memo: String = "") =
        ReceiptItem(id = id, receiptId = 1, name = name, amount = amount, category = category, memo = memo)

    private val milk = item(11, "牛乳", 200, "食費", memo = "特売")
    private val soap = item(12, "洗剤", 500, "日用品")

    /** 1回目の確定の結果をDBに入れたものとして、id を振る。 */
    private fun stored(plan: ConfirmPlan.Rebuild): Pair<ExpenseGroup, List<Expense>> {
        val g = plan.group!!.copy(id = 5)
        return g to plan.children.mapIndexed { i, e -> e.copy(id = 100L + i, groupId = 5) }
    }

    private fun first(r: Receipt = receipt(), items: List<ReceiptItem> = listOf(milk, soap)) =
        ConfirmPlanner.plan(r, items, emptyList(), emptyList(), perItem = true, now = 1) as ConfirmPlan.Rebuild

    @Test
    fun firstConfirm_createsGroupAndLinkedItems_withNameAndItemMemoOnly() {
        val p = first()
        assertEquals("スーパー", p.group!!.memo)
        assertEquals(700, p.group!!.amount)
        assertEquals(listOf(11L, 12L), p.children.map { it.receiptItemId })
        assertEquals(listOf("牛乳", "洗剤"), p.children.map { it.name })
        // メモは品目のメモだけ。店名と品名を連結しない
        assertEquals(listOf("特売", ""), p.children.map { it.memo })
        assertFalse(ConfirmPlanner.discardsExisting(p, emptyList(), emptyList()))
    }

    @Test
    fun reconfirm_keepsEditedFields_andUpdatesTheRest() {
        val (g, children) = stored(first())
        // 家計簿で: 牛乳の金額とメモ、グループのメモ(店名)を直した
        val editedMilk = children[0].copy(amount = 250, amountEdited = true, memo = "自分で", memoEdited = true)
        val editedGroup = g.copy(memo = "行きつけ", memoEdited = true)
        // レシートの方は: 牛乳の品名・カテゴリを直し、洗剤の金額を変え、合計も変えた
        val items = listOf(milk.copy(name = "牛乳(特売)", category = "外食"), soap.copy(amount = 480))
        val plan = ConfirmPlanner.plan(
            receipt(total = 700), items, listOf(editedGroup), listOf(editedMilk, children[1]), perItem = true, now = 2,
        ) as ConfirmPlan.UpdateGroup

        assertEquals(5L, plan.group.id)
        assertEquals("行きつけ", plan.group.memo) // 編集済みは残る
        assertEquals(700, plan.group.amount)
        assertTrue(plan.inserts.isEmpty())
        assertTrue(plan.deleteIds.isEmpty())
        val m = plan.updates.single { it.receiptItemId == 11L }
        assertEquals(250, m.amount) // 編集済み
        assertEquals("自分で", m.memo) // 編集済み
        assertEquals("牛乳(特売)", m.name) // 編集していない欄はレシートに追従
        assertEquals("外食", m.category)
        val s = plan.updates.single { it.receiptItemId == 12L }
        assertEquals(480, s.amount)
        assertEquals(children[1].id, s.id) // 行は同じ(id が保たれる)
    }

    @Test
    fun reconfirm_groupFollowsReceiptWhenNotEdited() {
        val (g, children) = stored(first())
        val plan = ConfirmPlanner.plan(
            receipt(total = 720, store = "新しい店", at = "2026-10-06 09:00"),
            listOf(milk, soap), listOf(g), children, perItem = true, now = 2,
        ) as ConfirmPlan.UpdateGroup
        assertEquals(720, plan.group.amount)
        assertEquals("新しい店", plan.group.memo)
        assertEquals("2026-10-06", plan.group.date)
        assertEquals("09:00", plan.group.time)
        assertTrue(plan.updates.all { it.date == "2026-10-06" && it.time == "09:00" })
    }

    @Test
    fun reconfirm_editedAmountCategoryAndTimeOfGroupStay() {
        val (g, children) = stored(first())
        val edited = g.copy(
            amount = 650, amountEdited = true, category = "趣味・娯楽", categoryEdited = true,
            date = "2026-10-01", time = "08:00", timeEdited = true,
        )
        val plan = ConfirmPlanner.plan(
            receipt(total = 700, at = "2026-10-06 09:00"), listOf(milk, soap), listOf(edited), children, perItem = true, now = 2,
        ) as ConfirmPlan.UpdateGroup
        assertEquals(650, plan.group.amount)
        assertEquals("趣味・娯楽", plan.group.category)
        assertEquals("2026-10-01", plan.group.date)
        assertEquals("08:00", plan.group.time)
    }

    @Test
    fun itemDateTime_alwaysFollowsTheGroup() {
        val (g, children) = stored(first())
        val moved = children[0].copy(date = "2026-10-02", time = "07:00", timeEdited = true)
        val plan = ConfirmPlanner.plan(
            receipt(at = "2026-10-06 09:00"), listOf(milk, soap), listOf(g), listOf(moved, children[1]), perItem = true, now = 2,
        ) as ConfirmPlan.UpdateGroup
        // 品目の日時はグループに従う(月・日の振り分けはグループの日付。品目だけ別の日にしない)
        assertEquals("2026-10-06", plan.updates.single { it.receiptItemId == 11L }.date)
        assertEquals("2026-10-06", plan.updates.single { it.receiptItemId == 12L }.date)
    }

    @Test
    fun addedItem_isInserted_removedItem_isDeleted_zeroYenItem_isNotCreatedAndDeleted() {
        val (g, children) = stored(first())
        val extra = children[1].copy(id = 777, receiptItemId = 99) // レシートから消えた品目の支出
        val free = item(14, "袋", 0, "日用品")
        val bread = item(13, "パン", 150, "食費")
        // 洗剤(12)は0円になった。パン(13)を追加。袋(14)は最初から0円
        val items = listOf(milk, soap.copy(amount = 0), bread, free)
        val plan = ConfirmPlanner.plan(
            receipt(), items, listOf(g), listOf(children[0], children[1], extra), perItem = true, now = 2,
        ) as ConfirmPlan.UpdateGroup

        assertEquals(listOf(13L), plan.inserts.map { it.receiptItemId })
        assertEquals("パン", plan.inserts.single().name)
        assertEquals(listOf(11L), plan.updates.map { it.receiptItemId })
        assertEquals(setOf(children[1].id, 777L), plan.deleteIds.toSet())
    }

    @Test
    fun legacyRows_withoutReceiptItemId_areRebuilt() {
        val legacyGroup = ExpenseGroup(id = 5, date = "2026-10-05", amount = 700, memo = "スーパー", receiptId = 1, createdAt = 0)
        val legacyChild = Expense(id = 100, date = "2026-10-05", amount = 200, memo = "スーパー 牛乳 特売", receiptId = 1, groupId = 5, createdAt = 0)
        val plan = ConfirmPlanner.plan(
            receipt(), listOf(milk, soap), listOf(legacyGroup), listOf(legacyChild), perItem = true, now = 2,
        )
        assertTrue(plan is ConfirmPlan.Rebuild)
        assertTrue(ConfirmPlanner.discardsExisting(plan, listOf(legacyGroup), listOf(legacyChild)))
        assertEquals(listOf(11L, 12L), (plan as ConfirmPlan.Rebuild).children.map { it.receiptItemId })
    }

    @Test
    fun advance_isSingleRow_andKeepsEditsOnReconfirm() {
        val adv = receipt(isAdvance = true).copy(ownAmount = 300)
        val firstPlan = ConfirmPlanner.plan(adv, listOf(milk, soap), emptyList(), emptyList(), perItem = true, now = 1)
        val row = (firstPlan as ConfirmPlan.Rebuild).children.single().copy(id = 50)
        assertNull(firstPlan.group)
        assertEquals(300, row.amount)
        assertNull(row.receiptItemId)

        val edited = row.copy(category = "外食", categoryEdited = true)
        val plan = ConfirmPlanner.plan(
            adv.copy(ownAmount = 350), listOf(milk, soap), emptyList(), listOf(edited), perItem = true, now = 2,
        ) as ConfirmPlan.UpdateSingle
        assertEquals(50L, plan.expense.id)
        assertEquals(350, plan.expense.amount)
        assertEquals("外食", plan.expense.category)
    }

    @Test
    fun switchingAdvanceAndNormal_rebuilds() {
        // 通常 -> 立て替え
        val (g, children) = stored(first())
        val toAdvance = ConfirmPlanner.plan(
            receipt(isAdvance = true).copy(ownAmount = 300), listOf(milk, soap), listOf(g), children, perItem = true, now = 2,
        )
        assertTrue(toAdvance is ConfirmPlan.Rebuild)
        assertNull((toAdvance as ConfirmPlan.Rebuild).group)
        assertEquals(1, toAdvance.children.size)
        assertTrue(ConfirmPlanner.discardsExisting(toAdvance, listOf(g), children))

        // 立て替え -> 通常
        val single = Expense(id = 50, date = "2026-10-05", amount = 300, memo = "スーパー", receiptId = 1, createdAt = 0)
        val toNormal = ConfirmPlanner.plan(
            receipt(), listOf(milk, soap), emptyList(), listOf(single), perItem = true, now = 2,
        )
        assertTrue(toNormal is ConfirmPlan.Rebuild)
        assertNotNullGroup(toNormal as ConfirmPlan.Rebuild)
        assertTrue(ConfirmPlanner.discardsExisting(toNormal, emptyList(), listOf(single)))
    }

    private fun assertNotNullGroup(p: ConfirmPlan.Rebuild) = assertTrue(p.group != null)

    @Test
    fun manualItemsOfTheGroup_areNotTouched() {
        // 手で足した品目は receiptId を持たないので existingExpenses に入らない = 更新・削除の対象外
        val (g, children) = stored(first())
        val plan = ConfirmPlanner.plan(receipt(), listOf(milk, soap), listOf(g), children, perItem = true, now = 2)
        assertTrue(plan is ConfirmPlan.UpdateGroup)
        assertTrue((plan as ConfirmPlan.UpdateGroup).deleteIds.isEmpty())
    }

    @Test
    fun allItemsZero_fallsBackToRebuild() {
        val (g, children) = stored(first())
        val plan = ConfirmPlanner.plan(
            receipt(), listOf(milk.copy(amount = 0), soap.copy(amount = 0)), listOf(g), children, perItem = true, now = 2,
        )
        assertTrue(plan is ConfirmPlan.Rebuild)
        assertTrue((plan as ConfirmPlan.Rebuild).children.isEmpty())
    }

    // ------------------------------------------------------------ 品目の保存を差分にする

    @Test
    fun itemSaver_keepsIds_updatesInsertsAndDeletes() {
        val existing = listOf(milk, soap, item(13, "パン", 150, "食費"))
        val incoming = listOf(
            milk.copy(amount = 210), // 更新(id 保持)
            item(0, "新品", 100, "食費"), // 追加
            soap, // そのまま(更新扱い)
        )
        val plan = ItemSaver.plan(1, existing, incoming)
        assertEquals(listOf(11L, 12L), plan.updates.map { it.id })
        assertEquals(210, plan.updates[0].amount)
        assertEquals(listOf(0L), plan.inserts.map { it.id })
        assertEquals("新品", plan.inserts.single().name)
        assertEquals(listOf(13L), plan.deleteIds)
    }

    @Test
    fun itemSaver_unknownIdIsInsertedAsNew_andDuplicateIdOnlyUpdatesOnce() {
        val plan = ItemSaver.plan(1, listOf(milk), listOf(milk, milk.copy(name = "コピー"), item(999, "不明", 1, "食費")))
        assertEquals(1, plan.updates.size)
        assertEquals(listOf("コピー", "不明"), plan.inserts.map { it.name })
        assertTrue(plan.inserts.all { it.id == 0L && it.receiptId == 1L })
        assertTrue(plan.deleteIds.isEmpty())
    }

    @Test
    fun itemSaver_emptyIncomingDeletesAll() {
        val plan = ItemSaver.plan(1, listOf(milk, soap), emptyList())
        assertEquals(listOf(11L, 12L), plan.deleteIds)
    }

    // ---- 割り勘(share)

    @Test
    fun share_firstConfirm_groupIsSelfTotal_andRowsAreSelfAmounts() {
        val share = SelfShare(perItem = mapOf(11L to 100L, 12L to 500L), total = 600L)
        val p = ConfirmPlanner.plan(receipt(), listOf(milk, soap), emptyList(), emptyList(), perItem = true, now = 1, share = share) as ConfirmPlan.Rebuild
        assertEquals(600, p.group!!.amount)
        assertEquals(listOf(100, 500), p.children.map { it.amount })
        assertEquals(listOf(11L, 12L), p.children.map { it.receiptItemId })
    }

    @Test
    fun share_ignoresAdvance() {
        val share = SelfShare(perItem = mapOf(11L to 200L), total = 200L)
        val p = ConfirmPlanner.plan(receipt(isAdvance = true), listOf(milk, soap), emptyList(), emptyList(), perItem = true, now = 1, share = share) as ConfirmPlan.Rebuild
        assertTrue(p.group != null)
        assertEquals(listOf(11L), p.children.map { it.receiptItemId })
    }

    @Test
    fun share_allZero_leavesNoRows() {
        val share = SelfShare(perItem = emptyMap(), total = 0L)
        val p = ConfirmPlanner.plan(receipt(), listOf(milk, soap), emptyList(), emptyList(), perItem = true, now = 1, share = share) as ConfirmPlan.Rebuild
        assertNull(p.group)
        assertTrue(p.children.isEmpty())
    }

    @Test
    fun share_switchingFromSingleRow_rebuildsWithGroup() {
        val single = Expense(id = 50, date = "2026-10-05", amount = 700, receiptId = 1, createdAt = 0)
        val share = SelfShare(perItem = mapOf(11L to 200L, 12L to 500L), total = 700L)
        val p = ConfirmPlanner.plan(receipt(), listOf(milk, soap), emptyList(), listOf(single), perItem = true, now = 1, share = share)
        assertTrue(p is ConfirmPlan.Rebuild && p.group != null)
    }

    @Test
    fun share_reconfirm_updatesUneditedRowsAndGroupTotal() {
        val first = first(
            r = receipt(),
            items = listOf(milk, soap),
        )
        val (g, children) = stored(first)
        val share = SelfShare(perItem = mapOf(11L to 150L, 12L to 500L), total = 650L)
        val p = ConfirmPlanner.plan(receipt(), listOf(milk, soap), listOf(g), children, perItem = true, now = 2, share = share) as ConfirmPlan.UpdateGroup
        assertEquals(650, p.group.amount)
        assertEquals(listOf(150), p.updates.filter { it.receiptItemId == 11L }.map { it.amount })
    }

    @Test
    fun savedIdsInOrder_updatesKeepIdAndInsertsTakeNewIdsInInputOrder() {
        // DBには 11(牛乳)と 13(消す品目)がある。画面は 新規(A)・牛乳(11)・新規(C) の順
        val existing = listOf(milk, item(13, "消す", 100, "食費"))
        val incoming = listOf(item(0, "A", 1, "食費"), milk, item(0, "C", 2, "食費"))
        val plan = ItemSaver.plan(1, existing, incoming)
        // 保存後: 11 は残り、新規は 14, 15 を採番される(挿入は順に採番される前提)
        val ids = ItemSaver.savedIdsInOrder(incoming, plan, listOf(11L, 14L, 15L))
        assertEquals(listOf(14L, 11L, 15L), ids)
    }

    @Test
    fun savedIdsInOrder_sameIdTwiceSecondIsInsert() {
        // 同じ id の品目が2つ来たら、1つ目だけ更新。2つ目は新規として扱う(ItemSaver.plan と同じ)
        val incoming = listOf(milk, milk.copy(name = "コピー"))
        val plan = ItemSaver.plan(1, listOf(milk), incoming)
        val ids = ItemSaver.savedIdsInOrder(incoming, plan, listOf(11L, 20L))
        assertEquals(listOf(11L, 20L), ids)
    }
}
