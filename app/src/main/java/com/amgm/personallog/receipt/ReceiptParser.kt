package com.amgm.personallog.receipt

import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.domain.TaxCalc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Claude が返した品目1行(正規化後)。値引きは amount が負。
 * amount は印字どおりの金額。priceIncludesTax が false なら税抜で、税込価格は taxRate から計算する。
 * shared は割り勘の共通費(袋代・送料など)かどうかの提案。LLM の提案であり、割り勘の金額はシステムが計算する。
 */
data class ParsedItem(
    val name: String,
    val quantity: Double,
    val unitPrice: Int,
    val amount: Int,
    val category: String,
    val taxRate: Int = TaxCalc.DEFAULT_TAX_RATE,
    val priceIncludesTax: Boolean = true,
    val shared: Boolean = false,
)

data class ParsedReceipt(
    val date: String?,
    val time: String?,
    val store: String,
    val items: List<ParsedItem>,
    val total: Int?,
    val note: String?,
    /** 参加人数(自分を含む)。印字・入力された人数。不明・不正値・旧形式は null。 */
    val headcount: Int? = null,
)

sealed interface ParseResult {
    data class Success(val receipt: ParsedReceipt) : ParseResult
    data class Failure(val reason: String) : ParseResult
}

/** Claude 応答テキストからのJSON抽出とパース。純Kotlin(JVMテスト可能)。 */
object ReceiptParser {
    /** プロンプトのカテゴリ名 -> アプリのカテゴリ名(Categories.DEFAULTS)。 */
    private val categoryMap = mapOf(
        "食費" to "食費",
        "外食" to "外食",
        "日用品" to "日用品",
        "交通" to "交通費",
        "交通費" to "交通費",
        "医療" to "医療・健康",
        "衣服" to "衣服・美容",
        "娯楽" to "趣味・娯楽",
        "光熱費" to "光熱費・通信費",
        "その他" to "その他",
    )

    fun normalizeCategory(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return Categories.UNCATEGORIZED
        if (s in Categories.DEFAULTS) return s
        return categoryMap[s] ?: "その他"
    }

    /** 最初の { から対応する } までを返す(文字列内の括弧は無視)。閉じていなければ null。 */
    fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    fun parse(text: String): ParseResult {
        val jsonText = extractJsonObject(text) ?: return ParseResult.Failure("JSONが見つかりませんでした")
        val obj = parseObject(jsonText) ?: return ParseResult.Failure("JSONの形式が正しくありません")

        val items = (obj["items"] as? JsonArray).orEmpty().mapNotNull { parseItem(it) }
        val date = obj.str("date")?.let { normalizeDate(it) }
        val time = obj.str("time")?.let { normalizeTime(it) }
        return ParseResult.Success(
            ParsedReceipt(
                date = date,
                time = time,
                store = obj.str("store").orEmpty(),
                items = items,
                total = obj["total"].asInt(),
                note = obj.str("note"),
                headcount = obj["headcount"].asHeadcount(),
            ),
        )
    }

    fun parseObject(text: String): JsonObject? =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        }

    /**
     * 品目1行を読む。新形式は { "receipt": 事実, "ledger": 家計簿への提案 } の入れ子。
     * receipt が無ければ旧形式(品目の直下に事実と category が並ぶ平らな形)として読む。
     */
    private fun parseItem(e: JsonElement): ParsedItem? {
        val o = e as? JsonObject ?: return null
        val fact = o["receipt"] as? JsonObject ?: o
        val ledger = o["ledger"] as? JsonObject ?: o
        val qty = fact["quantity"].asDouble()?.takeIf { it > 0 } ?: 1.0
        val unit = fact["unitPrice"].asInt()
        val amount = fact["amount"].asInt() ?: unit?.let { (it * qty).roundToInt() } ?: 0
        return ParsedItem(
            name = fact.str("name").orEmpty(),
            quantity = qty,
            unitPrice = unit ?: amount,
            amount = amount,
            // ledger が無い・category が null なら未分類(normalizeCategory の規則)
            category = normalizeCategory(ledger.str("category")),
            taxRate = TaxCalc.normalizeTaxRate(fact["taxRate"].asInt()),
            priceIncludesTax = fact.bool("priceIncludesTax") ?: true,
            // 旧形式には shared が無いので false
            shared = ledger.bool("shared") ?: false,
        )
    }

    /** true/false の真偽値。文字列や不明な値は null。 */
    private fun JsonObject.bool(key: String): Boolean? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return when (p.content.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun JsonObject.str(key: String): String? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content.trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
    }

    private fun JsonElement?.asDouble(): Double? {
        val p = this as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content.replace(",", "").replace("円", "").replace("\u00A5", "").replace("\uFFE5", "").trim().toDoubleOrNull()
    }

    private fun JsonElement?.asInt(): Int? = asDouble()?.roundToInt()

    /** 人数: 1以上の整数だけを受け付ける。0・負・小数・文字列の不正値は null。 */
    private fun JsonElement?.asHeadcount(): Int? {
        val d = asDouble() ?: return null
        if (d < 1.0 || d != floor(d) || d > Int.MAX_VALUE) return null
        return d.toInt()
    }

    private val dateRegex = Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})""")

    /** "2026/1/5" 等を "2026-01-05" に。解釈できなければ null。 */
    fun normalizeDate(s: String): String? {
        val m = dateRegex.find(s) ?: return null
        val (y, mo, d) = m.destructured
        val r = "%04d-%02d-%02d".format(y.toInt(), mo.toInt(), d.toInt())
        return if (ReceiptCalc.isValidDate(r)) r else null
    }

    private val timeRegex = Regex("""(\d{1,2}):(\d{2})""")

    fun normalizeTime(s: String): String? {
        val m = timeRegex.find(s) ?: return null
        val (h, mi) = m.destructured
        if (h.toInt() > 23 || mi.toInt() > 59) return null
        return "%02d:%02d".format(h.toInt(), mi.toInt())
    }
}

/** 金額検証などの純ロジック。 */
object ReceiptCalc {
    fun itemsSum(amounts: List<Int>): Long = amounts.sumOf { it.toLong() }

    /** 品目合計と total が一致しないなら差(total - 品目合計)を返す。一致 or total 不明なら null。 */
    fun mismatch(amounts: List<Int>, total: Int?): Long? {
        if (total == null) return null
        val diff = total.toLong() - itemsSum(amounts)
        return if (diff == 0L) null else diff
    }

    fun isValidDate(s: String): Boolean = try {
        java.time.LocalDate.parse(s)
        true
    } catch (e: Exception) {
        false
    }

    fun isValidTime(s: String): Boolean = Regex("""([01]\d|2[0-3]):[0-5]\d""").matches(s)
}
