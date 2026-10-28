package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Categories
import com.amgm.personallog.data.model.CategoryTotal
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptStatus
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** 家計簿まわりの純粋ロジック(JVMでテスト可能)。 */
object LedgerCalc {
    /** "2026-10" -> ("2026-10-01", "2026-10-31")。DAOの BETWEEN 用。 */
    fun monthRange(yearMonth: String): Pair<String, String> {
        val ym = YearMonth.parse(yearMonth)
        return ym.atDay(1).toString() to ym.atEndOfMonth().toString()
    }

    /**
     * "yyyy-MM-dd" または "yyyy-MM-dd HH:mm" を (date, time) に分ける。
     * 時刻が無ければ time は空文字("")のまま返す。00:00 を自動で補完しない。
     * 表示や時間別の集計では空を 0 時として扱う(LedgerChart.hourOf)。
     */
    fun splitPurchasedAt(purchasedAt: String): Pair<String, String> {
        val date = purchasedAt.take(10)
        val time = if (purchasedAt.length >= 16) purchasedAt.substring(11, 16) else ""
        return date to time
    }

    /** 作成時刻(epoch ミリ秒)を、現地時刻の "HH:mm" にする。時刻が不明な過去データの初期値。 */
    fun timeOfMillis(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
    }

    /** 支出リストをカテゴリごとに合計し、金額の多い順に返す。 */
    fun totalsByCategory(expenses: List<Expense>): List<CategoryTotal> =
        expenses.groupBy { it.category }
            .map { (cat, list) -> CategoryTotal(cat, list.sumOf { it.amount.toLong() }) }
            .sortedByDescending { it.total }

    /** 支払い1件ぶんの自分の支出。items は品目(groupId は未設定)。必ず1行以上。 */
    data class ReceiptGroup(val group: ExpenseGroup, val items: List<Expense>)

    /** グループのカテゴリの既定値: 税込でなく印字の金額が最も高い品目のカテゴリ。 */
    fun groupCategory(items: List<ReceiptItem>): String =
        items.maxByOrNull { it.amount }?.category ?: Categories.UNCATEGORIZED

    /**
     * 支払い(レシート・手入力)から、自分の支出(まとまり1つ + 品目1行以上)を作る。
     *
     * 支出の金額(自分の負担):
     *  - share(割り勘の自分の分)があるとき: share.total。立て替えの指定は無視する(割り勘が立て替えに置き換わる)。
     *  - 立て替え: レシート全体の自分の分(receipt.ownAmount)。未入力なら支払総額。
     *  - それ以外: 支払いの合計(receipt.totalAmount)。合計が未入力(下書き)なら品目の合計。
     * 品目:
     *  - share があるとき: 品目ごとの自分の分(share.perItem)。全体で割る割り勘も、SplitLedger が品目の金額の比で配った値になる。
     *  - 立て替え: 支出の金額を、品目の税込の金額の比で配る(最大剰余。合計は支出の金額と一致)。
     *  - それ以外: 品目ごとの税込の支払額。金額が不明の品目は、合計 − 分かっている品目の合計(UnknownAmounts)。
     *  - 0円の品目は作らない(金額不明の品目は残す)。品目が無い・全部0円のときは、品名が空の1品目(receiptItemId なし。金額・カテゴリ・メモは支出と同じ)。
     *  - 品目が1つだけなら、その金額は支出の金額にする(ExpenseItems.normalize)。
     * 自分の分が0円(割り勘で自分を外した)ときだけ null(家計簿に行を作らない)。
     * 合計も品目も無い確定済みの支払いは不正なので例外(下書きなら0円の支出)。
     */
    fun groupFromReceipt(receipt: Receipt, items: List<ReceiptItem>, now: Long, share: SelfShare? = null): ReceiptGroup? {
        if (share != null && share.total == 0L) return null
        val (date, time) = splitPurchasedAt(receipt.purchasedAt)
        val advance = share == null && receipt.isAdvance
        val amount: Long = when {
            share != null -> share.total
            advance -> receipt.ownAmount?.toLong() ?: paidTotal(receipt, items)
            else -> receipt.totalAmount?.toLong() ?: paidTotal(receipt, items)
        }
        val paid: List<Long> = when {
            share != null -> items.map { share.perItem[it.id] ?: 0L }
            advance -> ExpenseItems.allocateByRatio(amount, UnknownAmounts.paidYenPerItem(items, receipt.totalAmount))
                ?: List(items.size) { 0L }
            else -> UnknownAmounts.paidYenPerItem(items, receipt.totalAmount)
        }
        val group = ExpenseGroup(
            date = date,
            time = time,
            amount = amount.toInt(),
            category = groupCategory(items),
            memo = receipt.storeName,
            receiptId = receipt.id,
            createdAt = now,
        )
        val children = items.mapIndexedNotNull { i, it ->
            // 0円の品目は作らない。ただし割り勘の無い金額不明の品目は残す(金額は支出の金額から求め直す。ExpenseItems.normalize)
            if (paid[i] == 0L && !(share == null && it.unknownAmount)) {
                null
            } else {
                Expense(
                    date = date,
                    time = time,
                    amount = paid[i].toInt(),
                    category = it.category,
                    name = it.name,
                    memo = it.memo,
                    receiptId = receipt.id,
                    receiptItemId = it.id.takeIf { id -> id != 0L },
                    createdAt = now,
                    unknownAmount = it.unknownAmount,
                )
            }
        }.ifEmpty { listOf(blankItem(group)) }
        return ReceiptGroup(group, ExpenseItems.normalize(group, children))
    }

    /**
     * 品目の無い支払いの支出が持つ1品目。品名は空、金額・カテゴリ・メモ・日時は支出(group)と同じ。
     * receiptItemId は無い(支払いの品目に対応しない)。
     */
    fun blankItem(group: ExpenseGroup): Expense = Expense(
        date = group.date,
        time = group.time,
        amount = group.amount,
        category = group.category,
        memo = group.memo,
        receiptId = group.receiptId,
        createdAt = group.createdAt,
        name = "",
    )

    /**
     * 品目の税込合計(不明の品目は除く)、品目が無ければ合計欄。
     * 下書きで合計も品目も無いときは 0 円(下書きも家計簿に入れ、集計で下書きの件数に数える)。確定済みで合計が無いのは不正なので例外。
     */
    private fun paidTotal(receipt: Receipt, items: List<ReceiptItem>): Long =
        if (items.isEmpty()) {
            receipt.totalAmount?.toLong() ?: run {
                require(receipt.status == ReceiptStatus.DRAFT) { "合計が未入力のレシートは家計簿にできません(id=${receipt.id})" }
                0L
            }
        } else {
            UnknownAmounts.itemsYen(items, receipt.totalAmount)
        }
}
