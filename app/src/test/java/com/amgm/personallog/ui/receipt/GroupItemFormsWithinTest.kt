package com.amgm.personallog.ui.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 品目の編集シート: 開いた時点の品目だけを対象にし、編集中に他のグループと統合されても広がらない。 */
class GroupItemFormsWithinTest {
    private fun form(key: Long, name: String, amount: String = "100") = ItemForm(
        key = key, name = name, memo = "", amount = amount, category = "食費",
        taxRate = "10", priceIncludesTax = true,
    )

    @Test
    fun keepsOpenedKeysWhenGroupsMerge() {
        // 開いた時点: 牛乳(1,2)、牛乳2(3)。牛乳2を「牛乳」に直すと全体では3件が1グループになる。
        val opened = setOf(3L)
        val items = listOf(form(1, "牛乳"), form(2, "牛乳"), form(3, "牛乳"))
        assertEquals(setOf(1L, 2L, 3L), groupItemForms(items).single().keys)
        val within = groupItemFormsWithin(items, opened, 3L)!!
        assertEquals(setOf(3L), within.keys)
    }

    @Test
    fun followsSubsetWhenOpenedItemsDiverge() {
        // 1個ごとの編集で、開いた3件のうち1件の金額が変わった場合は、その1件は別のまとまりになる。
        val opened = setOf(1L, 2L, 3L)
        val items = listOf(form(1, "牛乳"), form(2, "牛乳"), form(3, "牛乳", amount = "120"), form(4, "牛乳"))
        assertEquals(setOf(1L, 2L), groupItemFormsWithin(items, opened, 1L)!!.keys)
        assertEquals(setOf(3L), groupItemFormsWithin(items, opened, 3L)!!.keys)
    }

    @Test
    fun returnsNullWhenKeyIsGone() {
        val items = listOf(form(1, "牛乳"))
        assertNull(groupItemFormsWithin(items, setOf(1L, 2L), 2L))
        assertNull(groupItemFormsWithin(items, emptySet(), 1L))
    }
}
