package com.amgm.personallog.ui.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 品目の編集シートの純粋ロジック(ページの変更・保存の順番・入力エラー・末尾の操作)。 */
class ItemPagerLogicTest {
    /** 保存済みの品目(id のページ)。 */
    private fun saved(
        id: Long,
        name: String = "牛乳",
        amount: String = "200",
        category: String? = "食費",
        memo: String = "",
        unknown: Boolean = false,
    ) = ItemDraft(key = id, id = id, name = name, amountText = amount, category = category, memo = memo, unknown = unknown)

    /** 新しい品目の空ページ。 */
    private fun blank(key: Long = -1L) =
        ItemDraft(key = key, id = null, name = "", amountText = "", category = "食費", memo = "", unknown = false)

    private fun latestOf(vararg items: ItemDraft): (Long) -> ItemDraft? {
        val map = items.associateBy { it.id }
        return { id -> map[id] }
    }

    // ---- 変更の内訳

    @Test
    fun unchangedSavedPage_hasNoChanges() {
        val d = saved(1)
        assertFalse(ItemPagerLogic.changes(d, d).hasChanges)
    }

    @Test
    fun blankNewPage_isNotDirty() {
        assertFalse(ItemPagerLogic.changes(blank(), null).hasChanges)
    }

    @Test
    fun newPageWithName_isDirty() {
        val d = blank().copy(name = "パン")
        assertTrue(ItemPagerLogic.changes(d, null).fieldsChanged)
    }

    @Test
    fun amountEdited_isFieldChange() {
        val latest = saved(1)
        val d = latest.copy(amountText = "300")
        val ch = ItemPagerLogic.changes(d, latest)
        assertTrue(ch.fieldsChanged)
        assertFalse(ch.unknownChanged)
    }

    @Test
    fun amountIgnoredWhileUnknown() {
        // 不明の品目は計算された金額が入るので、欄の文字の差は変更にしない
        val latest = saved(1, unknown = true, amount = "700")
        val d = latest.copy(amountText = "123")
        assertFalse(ItemPagerLogic.changes(d, latest).fieldsChanged)
    }

    // ---- 保存の順番

    @Test
    fun planSave_unchangedPage_hasNoOps() {
        val d = saved(1)
        assertTrue(ItemPagerLogic.planSave(listOf(d), latestOf(d)).isEmpty())
    }

    @Test
    fun planSave_amountChange_savesTypedAmount() {
        val latest = saved(1)
        val d = latest.copy(amountText = "300")
        val ops = ItemPagerLogic.planSave(listOf(d), latestOf(latest))
        assertEquals(
            listOf(ItemSaveOp.Save(1L, "牛乳", "食費", "", AmountSource.Typed(300))),
            ops,
        )
    }

    @Test
    fun planSave_unknownOn_withNameChange_savesFieldsBeforeUnknown() {
        // 不明にする前に欄を保存する(不明の後だと、金額が計算値に変わったあとで書いてしまうため)
        val latest = saved(1)
        val d = latest.copy(name = "低脂肪乳", unknown = true)
        val ops = ItemPagerLogic.planSave(listOf(d), latestOf(latest))
        assertEquals(
            listOf(
                ItemSaveOp.Save(1L, "低脂肪乳", "食費", "", AmountSource.Current),
                ItemSaveOp.SetUnknown(1L, true),
            ),
            ops,
        )
    }

    @Test
    fun planSave_unknownOn_onlyFlag_setsUnknownOnly() {
        val latest = saved(1)
        val d = latest.copy(unknown = true)
        assertEquals(listOf(ItemSaveOp.SetUnknown(1L, true)), ItemPagerLogic.planSave(listOf(d), latestOf(latest)))
    }

    @Test
    fun planSave_unknownOff_unsetsBeforeSavingTypedAmount() {
        // 不明を外してから、入力された金額で保存する
        val latest = saved(1, unknown = true, amount = "700")
        val d = latest.copy(unknown = false, amountText = "500")
        val ops = ItemPagerLogic.planSave(listOf(d), latestOf(latest))
        assertEquals(
            listOf(
                ItemSaveOp.SetUnknown(1L, false),
                ItemSaveOp.Save(1L, "牛乳", "食費", "", AmountSource.Typed(500)),
            ),
            ops,
        )
    }

    @Test
    fun planSave_unknownStaysOn_withMemoChange_usesCurrentAmount() {
        val latest = saved(1, unknown = true, amount = "700")
        val d = latest.copy(memo = "店で確認")
        assertEquals(
            listOf(ItemSaveOp.Save(1L, "牛乳", "食費", "店で確認", AmountSource.Current)),
            ItemPagerLogic.planSave(listOf(d), latestOf(latest)),
        )
    }

    @Test
    fun planSave_newItemsAreSavedLast() {
        // 新しい品目は最後に足す。途中で失敗しても、足したあとに別の書き込みが残らないようにする
        val existing = saved(1)
        val changed = existing.copy(amountText = "300")
        val added = blank().copy(name = "パン", amountText = "150")
        val ops = ItemPagerLogic.planSave(listOf(added, changed), latestOf(existing))
        assertEquals(2, ops.size)
        assertTrue(ops[0] is ItemSaveOp.Save && (ops[0] as ItemSaveOp.Save).id == 1L)
        assertEquals(ItemSaveOp.Save(null, "パン", "食費", "", AmountSource.Typed(150)), ops[1])
    }

    @Test
    fun planSave_skipsPageWhoseItemIsGone() {
        val d = saved(9).copy(amountText = "300")
        assertTrue(ItemPagerLogic.planSave(listOf(d), latestOf()).isEmpty())
    }

    // ---- 入力エラー

    @Test
    fun parseAmount_acceptsPositiveDigitsOnly() {
        assertEquals(7, ItemPagerLogic.parseAmount("007"))
        assertEquals(12, ItemPagerLogic.parseAmount(" 12 "))
        assertNull(ItemPagerLogic.parseAmount(""))
        assertNull(ItemPagerLogic.parseAmount("0"))
        assertNull(ItemPagerLogic.parseAmount("-5"))
        assertNull(ItemPagerLogic.parseAmount("1.5"))
        assertNull(ItemPagerLogic.parseAmount("99999999999"))
    }

    @Test
    fun validate_blankAmountAndNoCategory() {
        val e = ItemPagerLogic.validate(blank().copy(category = null))
        assertEquals("金額を入力してください", e.amount)
        assertEquals("カテゴリを選択してください", e.category)
        assertTrue(e.any)
    }

    @Test
    fun validate_unknownIgnoresAmount() {
        val e = ItemPagerLogic.validate(saved(1, amount = "", unknown = true))
        assertNull(e.amount)
        assertFalse(e.any)
    }

    @Test
    fun validate_zeroAmountIsError() {
        assertEquals("金額は1円以上の整数で入力してください", ItemPagerLogic.validate(saved(1, amount = "0")).amount)
    }

    // ---- 末尾の操作・追加・閾値

    @Test
    fun trailingButton_isNextBeforeLastPage_andAddOnLast() {
        assertFalse(ItemPagerLogic.trailingIsAdd(0, 3))
        assertFalse(ItemPagerLogic.trailingIsAdd(1, 3))
        assertTrue(ItemPagerLogic.trailingIsAdd(2, 3))
        // 仮想の追加ページに落ち着いたときも「追加」
        assertTrue(ItemPagerLogic.trailingIsAdd(3, 3))
    }

    @Test
    fun overscroll_addsOnlyPastThreshold() {
        assertFalse(ItemPagerLogic.addOnRelease(0.29f, settledOnAddSlot = false))
        assertTrue(ItemPagerLogic.addOnRelease(0.3f, settledOnAddSlot = false))
        assertTrue(ItemPagerLogic.addOnRelease(0f, settledOnAddSlot = true))
    }

    @Test
    fun unknownToggle_byContext() {
        // 手入力の支出・割り勘2人以上・品目1つ・支払いの品目が無い → 出さない
        assertEquals(UnknownToggle.Hidden, ItemPagerLogic.unknownToggle(false, false, 1, false, true))
        assertEquals(UnknownToggle.Hidden, ItemPagerLogic.unknownToggle(false, true, 2, false, true))
        assertEquals(UnknownToggle.Hidden, ItemPagerLogic.unknownToggle(false, true, 1, true, true))
        assertEquals(UnknownToggle.Hidden, ItemPagerLogic.unknownToggle(false, true, 1, false, false))
        // 新しい品目は保存後に選べる
        assertEquals(UnknownToggle.AfterSave, ItemPagerLogic.unknownToggle(true, true, 1, false, false))
        // 支払いの品目に紐づく既存の品目は押せる
        assertEquals(UnknownToggle.Enabled, ItemPagerLogic.unknownToggle(false, true, 1, false, true))
    }

    @Test
    fun canDelete_onlySavedItemsAndNotTheLast() {
        assertFalse(ItemPagerLogic.canDelete(isNew = true, visibleCount = 3))
        assertFalse(ItemPagerLogic.canDelete(isNew = false, visibleCount = 1))
        assertTrue(ItemPagerLogic.canDelete(isNew = false, visibleCount = 2))
    }

    @Test
    fun categoryOptions_keepsCurrentValueThatIsNotDefault() {
        val options = ItemPagerLogic.categoryOptions("ラベル外")
        assertTrue(options.last() == "ラベル外")
        assertFalse(options.contains(com.amgm.personallog.data.model.Categories.UNCATEGORIZED))
    }
}
