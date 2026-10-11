package com.amgm.personallog.receipt

import com.amgm.personallog.data.model.Receipt
import java.text.Normalizer
import java.util.Locale

/**
 * レシートの重複判定(純Kotlin、JVMテスト可能)。
 * 判定キーは「日付(yyyy-MM-dd)+ 店名(正規化) + 合計金額」。店名が空・合計が0以下・日付不正のものは対象外。
 */
object ReceiptDedup {
    data class Key(val date: String, val store: String, val total: Int)

    /** 比較用の店名: 全角半角・半角カナを NFKC で揃え、空白を除き、英字は小文字にする(簡易)。 */
    fun normalizeStore(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFKC)
            .filterNot { it.isWhitespace() }
            .lowercase(Locale.ROOT)

    fun keyOf(purchasedAt: String, storeName: String, totalAmount: Int): Key? {
        val date = purchasedAt.take(10)
        val store = normalizeStore(storeName)
        if (store.isEmpty() || totalAmount <= 0 || !ReceiptCalc.isValidDate(date)) return null
        return Key(date, store, totalAmount)
    }

    fun keyOf(r: Receipt): Key? = keyOf(r.purchasedAt, r.storeName, r.totalAmount)

    /**
     * 同じ内容のレシートが複数ある場合、id の小さい方を原本とみなし、後から作られた側(id の大きい方)を
     * 「重複の可能性」として返す。戻り値は [後の側のid -> 原本]。
     */
    fun laterDuplicates(list: List<Receipt>): Map<Long, Receipt> =
        list.mapNotNull { r -> keyOf(r)?.let { k -> k to r } }
            .groupBy({ it.first }, { it.second })
            .values
            .flatMap { group ->
                val sorted = group.sortedBy { it.id }
                sorted.drop(1).map { it.id to sorted.first() }
            }
            .toMap()

    /** 同じキーのレシート(excludeId 自身は除く)。 */
    fun sameContent(key: Key, list: List<Receipt>, excludeId: Long): List<Receipt> =
        list.filter { it.id != excludeId && keyOf(it) == key }

    /** 警告文に出す要約。例: "2026-10-09 スーパーA ¥748" */
    fun summary(r: Receipt): String =
        "${r.purchasedAt} ${r.storeName.ifBlank { "(店名なし)" }} ${String.format(Locale.JAPAN, "¥%,d", r.totalAmount)}"
}
