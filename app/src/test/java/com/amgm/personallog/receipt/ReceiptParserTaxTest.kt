package com.amgm.personallog.receipt

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptParserTaxTest {
    @Test
    fun parsesTaxRateAndTaxIncludedFlag() {
        val text = """{"date":"2026-10-05","store":"店","items":[
            {"name":"A","quantity":1,"unitPrice":120,"amount":120,"taxRate":8,"priceIncludesTax":false,"category":"食費"},
            {"name":"B","quantity":1,"unitPrice":300,"amount":300,"taxRate":null,"priceIncludesTax":null,"category":"日用品"}
        ],"total":429}"""
        val r = (ReceiptParser.parse(text) as ParseResult.Success).receipt
        assertEquals(8, r.items[0].taxRate)
        assertEquals(false, r.items[0].priceIncludesTax)
        assertEquals(10, r.items[1].taxRate)
        assertEquals(true, r.items[1].priceIncludesTax)
    }

    @Test
    fun missingTaxFields_defaultToTaxIncludedTenPercent() {
        val text = """{"date":"2026-10-05","store":"店","items":[{"name":"A","amount":100,"category":"食費"}],"total":100}"""
        val r = (ReceiptParser.parse(text) as ParseResult.Success).receipt
        assertEquals(10, r.items[0].taxRate)
        assertEquals(true, r.items[0].priceIncludesTax)
    }
}
