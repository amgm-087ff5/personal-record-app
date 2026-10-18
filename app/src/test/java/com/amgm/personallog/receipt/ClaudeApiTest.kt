package com.amgm.personallog.receipt

import com.amgm.personallog.data.model.Categories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeApiTest {
    @Test
    fun promptListsEveryDefaultCategoryExceptUncategorized() {
        val prompt = ClaudeRequest.PROMPT
        val expected = Categories.DEFAULTS.filter { it != Categories.UNCATEGORIZED }
        // アプリのカテゴリ名(住居を含む)が、プロンプトの語彙として並んでいること
        assertTrue(prompt.contains("ledger.category は次のいずれか1つにしてください: " + expected.joinToString(", ")))
        expected.forEach { assertTrue("カテゴリ $it がプロンプトにない", prompt.contains(it)) }
        assertTrue(prompt.contains("住居"))
        // 「未分類」は LLM に選ばせない(欠けたときはアプリ側で未分類にする)
        assertFalse(prompt.contains(Categories.UNCATEGORIZED))
    }

    @Test
    fun promptAsksForNestedReceiptAndLedgerFields() {
        val prompt = ClaudeRequest.PROMPT
        assertTrue(prompt.contains("\"receipt\""))
        assertTrue(prompt.contains("\"ledger\""))
        assertTrue(prompt.contains("\"shared\""))
        assertTrue(prompt.contains("receipt.amount"))
        assertTrue(prompt.contains("JSONだけ"))
    }

    @Test
    fun stopReason_maxTokensIsReturned() {
        val resp = """{"type":"message","content":[{"type":"text","text":"{\"items\":["}],"stop_reason":"max_tokens"}"""
        assertEquals("max_tokens", ClaudeRequest.extractStopReason(resp))
    }

    @Test
    fun stopReason_endTurnIsReturned() {
        val resp = """{"type":"message","content":[{"type":"text","text":"{}"}],"stop_reason":"end_turn"}"""
        assertEquals("end_turn", ClaudeRequest.extractStopReason(resp))
    }

    @Test
    fun stopReason_missingOrNullIsNull() {
        assertNull(ClaudeRequest.extractStopReason("""{"type":"message","content":[]}"""))
        assertNull(ClaudeRequest.extractStopReason("""{"type":"message","stop_reason":null}"""))
        assertNull(ClaudeRequest.extractStopReason("not json"))
    }

    @Test
    fun extractText_returnsFirstTextBlockAsBefore() {
        // 非テキストのブロック(thinking など)を飛ばして、最初の text を返す
        val resp = """{"content":[{"type":"thinking","thinking":"..."},{"type":"text","text":"{\"a\":1}"}],"stop_reason":"end_turn"}"""
        assertEquals("{\"a\":1}", ClaudeRequest.extractText(resp))
    }

    @Test
    fun extractText_nullWhenNoUsableTextBlock() {
        assertNull(ClaudeRequest.extractText("""{"id":"m"}"""))
        assertNull(ClaudeRequest.extractText("""{"content":[]}"""))
        assertNull(ClaudeRequest.extractText("""{"content":[{"type":"tool_use","id":"x"}]}"""))
        assertNull(ClaudeRequest.extractText("""{"content":[{"type":"text","text":1}]}"""))
        assertNull(ClaudeRequest.extractText("not json"))
        assertNull(ClaudeRequest.extractText("[1,2]"))
    }

    @Test
    fun extractText_stillReturnsTextWhenTruncated() {
        // 途中切れの判定は ReceiptScanner 側。extractText の挙動は変えない
        val resp = """{"content":[{"type":"text","text":"{\"items\":["}],"stop_reason":"max_tokens"}"""
        assertEquals("{\"items\":[", ClaudeRequest.extractText(resp))
    }
}
