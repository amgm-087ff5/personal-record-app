package com.amgm.personallog.ui.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

/** 家計簿の支出の行の見出し: 支払い先(店名)があればそれ、無ければカテゴリ。 */
class LedgerRowTitleTest {

    @Test
    fun usesStoreNameWhenPresent() {
        assertEquals("スーパーA", LedgerLogic.rowTitle("スーパーA", "食費"))
    }

    @Test
    fun fallsBackToCategoryWhenStoreIsBlank() {
        assertEquals("食費", LedgerLogic.rowTitle("", "食費"))
        assertEquals("食費", LedgerLogic.rowTitle("   ", "食費"))
    }

    @Test
    fun fallsBackToCategoryWhenNoPayment() {
        assertEquals("交通費", LedgerLogic.rowTitle(null, "交通費"))
    }
}
