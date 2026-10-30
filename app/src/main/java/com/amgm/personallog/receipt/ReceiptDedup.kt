package com.amgm.personallog.receipt

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptNotDuplicate
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

/** 「重複ではない」の組を (小さい id, 大きい id) の集合にしたもの。 */
typealias NotDuplicateSet = Set<Pair<Long, Long>>

/**
 * 支払いの重複判定(純Kotlin、JVMテスト可能)。店名は表記揺れがあるので使わない。
 *
 * 候補の条件:
 *  - 両方に時刻("yyyy-MM-dd HH:mm")があるとき: 差が [WINDOW_SECONDS] 秒(5分)以内(ちょうど5分を含む。日付またぎも計算する)で、合計が同じ。
 *  - どちらかに時刻が無いとき: 同じ日付で、合計が同じ。
 *  - 合計が null(下書きで未入力)の支払いは判定しない。
 * ユーザーが「重複ではない」と判断した組([ReceiptNotDuplicate])は候補から除く。
 */
object ReceiptDedup {
    /** 時刻がある同士で重複とみなす差(秒)。5分ちょうどを含む。 */
    const val WINDOW_SECONDS = 5 * 60L

    fun pairOf(a: Long, b: Long): Pair<Long, Long> = minOf(a, b) to maxOf(a, b)

    fun notDuplicateSet(pairs: Collection<ReceiptNotDuplicate>): NotDuplicateSet =
        pairs.mapTo(HashSet()) { it.receiptIdLow to it.receiptIdHigh }

    /** 購入日時を "yyyy-MM-dd HH:mm" として読む。時刻が無い・読めないときは null。 */
    fun dateTimeOf(purchasedAt: String): LocalDateTime? {
        if (purchasedAt.length < 16) return null
        return try {
            LocalDateTime.parse(purchasedAt.substring(0, 16).replace(' ', 'T'))
        } catch (e: Exception) {
            null
        }
    }

    /** 2つの日時の差が5分以内か(ちょうど5分を含む。前後どちらでも、日付をまたいでも)。 */
    fun withinWindow(a: LocalDateTime, b: LocalDateTime): Boolean =
        Math.abs(Duration.between(a, b).seconds) <= WINDOW_SECONDS

    /** 「重複ではない」の組を考えずに、2枚が内容として重複の候補か。 */
    fun isCandidate(a: Receipt, b: Receipt): Boolean {
        val ta = a.totalAmount ?: return false
        val tb = b.totalAmount ?: return false
        if (ta != tb) return false
        val da = dateTimeOf(a.purchasedAt)
        val db = dateTimeOf(b.purchasedAt)
        if (da != null && db != null) return withinWindow(da, db)
        val dateA = a.purchasedAt.take(10)
        return ReceiptCalc.isValidDate(dateA) && dateA == b.purchasedAt.take(10)
    }

    /** 2枚が重複の候補で、かつ「重複ではない」と判断されていないか。 */
    fun isDuplicate(a: Receipt, b: Receipt, notDuplicates: NotDuplicateSet = emptySet()): Boolean =
        a.id != b.id && pairOf(a.id, b.id) !in notDuplicates && isCandidate(a, b)

    /**
     * target より前に作られた(id が小さい)重複の候補。id の小さい順。詳細画面の警告に使う。
     * 後から作られた側にだけ警告を出す(原本には出さない)。
     */
    fun earlierDuplicates(target: Receipt, list: List<Receipt>, notDuplicates: NotDuplicateSet = emptySet()): List<Receipt> =
        list.filter { it.id < target.id && isDuplicate(target, it, notDuplicates) }.sortedBy { it.id }

    /**
     * 一覧の警告: 重複の候補がある「後から作られた側」 -> 最も早い相手(原本)。
     */
    fun laterDuplicates(list: List<Receipt>, notDuplicates: NotDuplicateSet = emptySet()): Map<Long, Receipt> {
        val sorted = list.sortedBy { it.id }
        val result = LinkedHashMap<Long, Receipt>()
        for ((i, r) in sorted.withIndex()) {
            val original = (0 until i).asSequence().map { sorted[it] }.firstOrNull { isDuplicate(r, it, notDuplicates) }
            if (original != null) result[r.id] = original
        }
        return result
    }

    /** 確定時の警告: target と重複の候補になる、確定済みの相手(target 自身は除く)。list には確定済みの支払いを渡す。 */
    fun sameContent(target: Receipt, list: List<Receipt>, notDuplicates: NotDuplicateSet = emptySet()): List<Receipt> =
        list.filter { isDuplicate(target, it, notDuplicates) }

    /** 警告文に出す要約。例: "2026-10-09 スーパーA ¥748" 。合計が未入力なら「合計 未入力」。 */
    fun summary(r: Receipt): String {
        val total = r.totalAmount?.let { String.format(Locale.JAPAN, "¥%,d", it) } ?: "合計 未入力"
        return "${r.purchasedAt} ${r.storeName.ifBlank { "(店名なし)" }} $total"
    }
}
