package com.amgm.personallog.receipt

import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.data.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {
    private val sample = """{"date":"2026-10-09","time":"18:32","store":"スーパーA","items":[{"name":"牛乳","quantity":1,"unitPrice":198,"amount":198,"category":"食費"},{"name":"洗剤","quantity":2,"unitPrice":300,"amount":600,"category":"日用品"},{"name":"クーポン値引き","quantity":1,"unitPrice":-50,"amount":-50,"category":"食費"}],"total":748,"note":null}"""

    private fun success(text: String): ParsedReceipt {
        val r = ReceiptParser.parse(text)
        assertTrue("expected success but was $r", r is ParseResult.Success)
        return (r as ParseResult.Success).receipt
    }

    @Test
    fun parsesPlainJson() {
        val p = success(sample)
        assertEquals("2026-10-09", p.date)
        assertEquals("18:32", p.time)
        assertEquals("スーパーA", p.store)
        assertEquals(3, p.items.size)
        assertEquals(748, p.total)
        assertNull(p.note)
        assertEquals(2.0, p.items[1].quantity, 0.0)
    }

    @Test
    fun parsesFencedJsonWithSurroundingText() {
        val p = success("読み取り結果です。\n```json\n$sample\n```\n以上です。")
        assertEquals("スーパーA", p.store)
        assertEquals(3, p.items.size)
    }

    @Test
    fun keepsNegativeDiscountAmount() {
        val p = success(sample)
        assertEquals(-50, p.items[2].amount)
        assertEquals(748L, ReceiptCalc.itemsSum(p.items.map { it.amount }))
        assertNull(ReceiptCalc.mismatch(p.items.map { it.amount }, p.total))
    }

    @Test
    fun brokenJsonFails() {
        assertTrue(ReceiptParser.parse("""{"date":"2026-10-09","items":[{"name":""") is ParseResult.Failure)
        assertTrue(ReceiptParser.parse("読み取れませんでした") is ParseResult.Failure)
        assertTrue(ReceiptParser.parse("""{"date": 2026-10-09, }""") is ParseResult.Failure)
    }

    @Test
    fun extractIgnoresBracesInStrings() {
        val t = """前置き {"store":"A}店","items":[]} 後ろ {"x":1}"""
        assertEquals("""{"store":"A}店","items":[]}""", ReceiptParser.extractJsonObject(t))
    }

    @Test
    fun toleratesNullsStringsAndMissingFields() {
        val p = success("""{"date":"2026/10/9","time":null,"store":null,"items":[{"name":"X","amount":"1,200円","category":"交通"}],"total":null}""")
        assertEquals("2026-10-09", p.date)
        assertNull(p.time)
        assertEquals("", p.store)
        assertEquals(1200, p.items[0].amount)
        assertEquals(1.0, p.items[0].quantity, 0.0)
        assertEquals("交通費", p.items[0].category)
        assertNull(p.total)
    }

    @Test
    fun unknownCategoryBecomesOther() {
        assertEquals("その他", ReceiptParser.normalizeCategory("ペット"))
        assertEquals("未分類", ReceiptParser.normalizeCategory(null))
        assertEquals("医療・健康", ReceiptParser.normalizeCategory("医療"))
    }

    @Test
    fun mismatchDetected() {
        assertEquals(10L, ReceiptCalc.mismatch(listOf(100, 200), 310))
        assertNull(ReceiptCalc.mismatch(listOf(100, 200), 300))
        assertNull(ReceiptCalc.mismatch(listOf(100, 200), null))
    }

    @Test
    fun validators() {
        assertTrue(ReceiptCalc.isValidDate("2026-10-10"))
        assertTrue(!ReceiptCalc.isValidDate("2026-13-10"))
        assertTrue(ReceiptCalc.isValidTime("09:05"))
        assertTrue(!ReceiptCalc.isValidTime("9:5"))
    }

    @Test
    fun requestBodyStructure() {
        val body = ClaudeRequest.buildBody("claude-haiku-5-5", "QUJD")
        val root = ReceiptParser.parseObject(body)
        assertNotNull(root)
        val s = root.toString()
        assertTrue(s.contains("\"model\":\"claude-haiku-5-5\""))
        assertTrue(s.contains("\"max_tokens\":${ClaudeRequest.MAX_TOKENS}"))
        assertTrue(s.contains("\"media_type\":\"image/jpeg\""))
        assertTrue(s.contains("\"data\":\"QUJD\""))
        assertTrue(s.contains("\"type\":\"base64\""))
        assertTrue(s.indexOf("\"type\":\"image\"") < s.indexOf("\"type\":\"text\""))
        assertTrue(ClaudeRequest.PROMPT.contains("JSONだけ"))
    }

    @Test
    fun responseTextExtraction() {
        val resp = """{"id":"m","type":"message","content":[{"type":"text","text":"{\"a\":1}"}],"stop_reason":"end_turn"}"""
        assertEquals("{\"a\":1}", ClaudeRequest.extractText(resp))
        assertNull(ClaudeRequest.extractText("not json"))
        assertEquals("bad key", ClaudeRequest.extractErrorMessage("""{"type":"error","error":{"type":"authentication_error","message":"bad key"}}"""))
    }

    @Test
    fun httpErrorMessages() {
        assertTrue(ClaudeRequest.messageForHttpError(401, null).contains("APIキー"))
        assertTrue(ClaudeRequest.messageForHttpError(429, null).contains("429"))
        assertTrue(ClaudeRequest.messageForHttpError(503, null).contains("503"))
    }

    @Test
    fun mapperBuildsDraftReceipts() {
        val parsed = success(sample)
        val (r, items) = ReceiptMapper.draftReceipt(ScanOutcome.Parsed(parsed, sample), "receipts/a.jpg", 1L, today = "2026-01-01")
        assertEquals("2026-10-09 18:32", r.purchasedAt)
        assertEquals(748, r.totalAmount)
        assertEquals(ReceiptStatus.DRAFT, r.status)
        // 洗剤(2個)は1個ずつの行に展開されるので 1+2+1 = 4行
        assertEquals(4, items.size)
        val (r2, items2) = ReceiptMapper.draftReceipt(ScanOutcome.Unparsable("oops", "x"), "receipts/a.jpg", 1L, today = "2026-01-01")
        assertEquals("2026-01-01", r2.purchasedAt)
        assertEquals("oops", r2.rawJson)
        assertTrue(items2.isEmpty())
    }

    private val noTotal = """{"date":"2026-10-09","time":"18:32","store":"スーパーA","items":[{"name":"牛乳","amount":198,"category":"食費"}],"total":null}"""

    @Test
    fun mapperLeavesTotalNullWhenNotRead_andDoesNotFillFromItems() {
        val parsed = success(noTotal)
        assertNull(parsed.total)
        val (r, items) = ReceiptMapper.draftReceipt(ScanOutcome.Parsed(parsed, noTotal), "receipts/a.jpg", 1L, today = "2026-01-01")
        assertNull(r.totalAmount)
        assertEquals(TotalSource.UNKNOWN, r.totalSource)
        assertEquals(1, items.size)
        // total のキーが無い場合も同じ
        val missingKey = """{"date":"2026-10-09","store":"A","items":[{"name":"牛乳","amount":198}]}"""
        val (r2, _) = ReceiptMapper.draftReceipt(ScanOutcome.Parsed(success(missingKey), missingKey), "a.jpg", 1L, today = "2026-01-01")
        assertNull(r2.totalAmount)
    }

    @Test
    fun mapperMarksReadTotalAsPrinted() {
        val (r, _) = ReceiptMapper.draftReceipt(ScanOutcome.Parsed(success(sample), sample), "receipts/a.jpg", 1L, today = "2026-01-01")
        assertEquals(748, r.totalAmount)
        assertEquals(TotalSource.PRINTED, r.totalSource)
    }

    @Test
    fun mapperUsesNullTotalForUnparsableAndFailed() {
        val (u, _) = ReceiptMapper.draftReceipt(ScanOutcome.Unparsable("oops", "x"), "a.jpg", 1L, today = "2026-01-01")
        assertNull(u.totalAmount)
        assertEquals(TotalSource.UNKNOWN, u.totalSource)
        val (f, _) = ReceiptMapper.draftReceipt(ScanOutcome.Failed("通信エラー"), "a.jpg", 1L, today = "2026-01-01")
        assertNull(f.totalAmount)
        assertEquals(TotalSource.UNKNOWN, f.totalSource)
    }

    @Test
    fun printedTotalIsReadFromRawJson() {
        assertEquals(748, ReceiptMapper.printedTotal(sample))
        assertNull(ReceiptMapper.printedTotal(noTotal))
        assertNull(ReceiptMapper.printedTotal(""))
        assertNull(ReceiptMapper.printedTotal("not json"))
    }

    /** 新形式: 品目ごとに receipt(事実)と ledger(家計簿への提案)を入れ子にした応答。 */
    private val nested = """{"date":"2026-10-09","time":"18:32","store":"スーパーA","items":[
        {"receipt":{"name":"牛乳","quantity":1,"unitPrice":198,"amount":198,"taxRate":8,"priceIncludesTax":true},"ledger":{"category":"食費","shared":false}},
        {"receipt":{"name":"洗剤","quantity":2,"unitPrice":300,"amount":600,"taxRate":10,"priceIncludesTax":true},"ledger":{"category":"日用品","shared":false}},
        {"receipt":{"name":"レジ袋","quantity":1,"unitPrice":3,"amount":3,"taxRate":10,"priceIncludesTax":true},"ledger":{"category":"その他","shared":true}},
        {"receipt":{"name":"クーポン値引き","quantity":1,"unitPrice":-50,"amount":-50,"taxRate":null,"priceIncludesTax":null},"ledger":{"category":"食費","shared":false}}
    ],"total":751,"note":null}"""

    @Test
    fun newFormat_readsFactFromReceiptAndSuggestionFromLedger() {
        val p = success(nested)
        assertEquals("2026-10-09", p.date)
        assertEquals(751, p.total)
        assertEquals(4, p.items.size)
        val milk = p.items[0]
        assertEquals("牛乳", milk.name)
        assertEquals(198, milk.amount)
        assertEquals(8, milk.taxRate)
        assertEquals(true, milk.priceIncludesTax)
        assertEquals("食費", milk.category)
        assertEquals(false, milk.shared)
        val bag = p.items[2]
        assertEquals("その他", bag.category)
        assertEquals(true, bag.shared)
    }

    @Test
    fun newFormat_quantityOverOneUsesReceiptAmount() {
        // 数量2でも、印字の amount(割引後の合計)をそのまま使う。単価×数量では計算しない
        val text = """{"items":[{"receipt":{"name":"洗剤","quantity":2,"unitPrice":300,"amount":580},"ledger":{"category":"日用品","shared":false}}],"total":580}"""
        val p = success(text)
        assertEquals(2.0, p.items[0].quantity, 0.0)
        assertEquals(300, p.items[0].unitPrice)
        assertEquals(580, p.items[0].amount)
    }

    @Test
    fun newFormat_negativeDiscountItemAndNullTaxFields() {
        val p = success(nested)
        val discount = p.items[3]
        assertEquals(-50, discount.amount)
        assertEquals(10, discount.taxRate)
        assertEquals(true, discount.priceIncludesTax)
        // 合計の検証: 品目の合計(負の値を含む)と total が一致する
        assertEquals(751L, ReceiptCalc.itemsSum(p.items.map { it.amount }))
        assertNull(ReceiptCalc.mismatch(p.items.map { it.amount }, p.total))
    }

    @Test
    fun newFormat_missingLedgerBecomesUncategorizedAndNotShared() {
        val text = """{"items":[{"receipt":{"name":"X","amount":100}}],"total":100}"""
        val item = success(text).items[0]
        assertEquals("未分類", item.category)
        assertEquals(false, item.shared)
        assertEquals(100, item.amount)
    }

    @Test
    fun newFormat_nullCategoryBecomesUncategorizedButKeepsShared() {
        val text = """{"items":[{"receipt":{"name":"送料","amount":300},"ledger":{"category":null,"shared":true}}],"total":300}"""
        val item = success(text).items[0]
        assertEquals("未分類", item.category)
        assertEquals(true, item.shared)
    }

    @Test
    fun oldFormat_flatItemStillReadsCategoryAndSharedFalse() {
        // 旧形式(品目の直下に category)の応答も今までどおり読める
        val p = success(sample)
        assertEquals("食費", p.items[0].category)
        assertEquals("日用品", p.items[1].category)
        assertEquals(600, p.items[1].amount)
        assertTrue(p.items.none { it.shared })
    }

    @Test
    fun printedTotalIsReadFromNewFormat() {
        // 保存済みの rawJson(新形式)から印字合計を取り出せる(TotalSourceInit と同じ経路)
        assertEquals(751, ReceiptMapper.printedTotal(nested))
    }
}
