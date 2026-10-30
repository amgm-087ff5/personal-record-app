package com.amgm.personallog.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptHeadcountTest {
    /** headcount の値だけを変えた支払いJSON。value は JSON の値部分をそのまま入れる。 */
    private fun json(value: String?): String {
        val field = if (value == null) "" else ",\"headcount\":$value"
        return """{"date":"2026-10-09","time":"18:32","store":"居酒屋A","items":[],"total":5000$field,"note":null}"""
    }

    private fun headcountOf(text: String): Int? {
        val r = ReceiptParser.parse(text)
        assertTrue("expected success but was $r", r is ParseResult.Success)
        return (r as ParseResult.Success).receipt.headcount
    }

    @Test
    fun readsPrintedHeadcount() {
        assertEquals(3, headcountOf(json("3")))
        assertEquals(1, headcountOf(json("1")))
    }

    @Test
    fun integerLikeDecimalAndNumericString_areAccepted() {
        assertEquals(4, headcountOf(json("4.0")))
        assertEquals(4, headcountOf(json("\"4\"")))
    }

    @Test
    fun zeroNegativeAndNonInteger_becomeNull() {
        assertNull(headcountOf(json("0")))
        assertNull(headcountOf(json("-2")))
        assertNull(headcountOf(json("2.5")))
    }

    @Test
    fun invalidStrings_becomeNull() {
        assertNull(headcountOf(json("\"four\"")))
        assertNull(headcountOf(json("\"4人\"")))
        assertNull(headcountOf(json("\"\"")))
        assertNull(headcountOf(json("true")))
    }

    @Test
    fun nullOrMissing_becomeNull() {
        assertNull(headcountOf(json("null")))
        // 旧形式(headcount が無い応答)も読める
        assertNull(headcountOf(json(null)))
    }

    @Test
    fun otherFieldsStillParseWhenHeadcountIsBad() {
        val r = ReceiptParser.parse(json("\"xx\"")) as ParseResult.Success
        assertEquals("居酒屋A", r.receipt.store)
        assertEquals(5000, r.receipt.total)
    }

    @Test
    fun promptAsksForHeadcountAsIntegerOrNull() {
        val prompt = ClaudeRequest.PROMPT
        assertTrue(prompt.contains("\"headcount\":人数(整数"))
        assertTrue(prompt.contains("無ければnull"))
    }

    @Test
    fun draftReceipt_keepsReadHeadcount_andNullWhenUnknown() {
        val withCount = ParsedReceipt(
            date = "2026-10-09", time = "18:32", store = "居酒屋A", items = emptyList(),
            total = 5000, note = null, headcount = 3,
        )
        val (receipt, _) = ReceiptMapper.draftReceipt(
            ScanOutcome.Parsed(withCount, "{}"), "receipts/a.jpg", now = 1L, today = "2026-10-19",
        )
        assertEquals(3, receipt.headcount)

        val without = withCount.copy(headcount = null)
        val (none, _) = ReceiptMapper.draftReceipt(
            ScanOutcome.Parsed(without, "{}"), "receipts/a.jpg", now = 1L, today = "2026-10-19",
        )
        assertNull(none.headcount)

        // 読み取り失敗の下書きは人数を持たない
        val (failed, _) = ReceiptMapper.draftReceipt(
            ScanOutcome.Failed("x"), "receipts/a.jpg", now = 1L, today = "2026-10-19",
        )
        assertNull(failed.headcount)
    }
}
