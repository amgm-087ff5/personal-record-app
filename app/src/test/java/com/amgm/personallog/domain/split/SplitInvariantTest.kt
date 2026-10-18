package com.amgm.personallog.domain.split
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.ShareKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 乱数で2万件の入力を作って確かめる不変条件。
 * - どの方式でも「各人の合計の和 = 合計」、0円の行は無い。
 * - 品目(items)があるとき、各人の Σ yenPerItem == totalOf、カテゴリ内訳の合計 == totalOf。
 * 入力は isPayer・ラベル・ラベル金額・個数・空欄(null の金額・割合)・全体方式の種類もランダムに入れる。
 */
class SplitInvariantTest {
    @Test fun totalAlwaysMatches_andLedgerMatchesEachPerson() {
        val rnd = Random(42)
        val kinds = listOf(ShareKind.ASSIGN, ShareKind.EQUAL, ShareKind.RATIO, ShareKind.AMOUNT)
        val wholeKinds = listOf(ShareKind.AMOUNT, ShareKind.RATIO, ShareKind.EQUAL, ShareKind.ASSIGN)
        val labels = listOf("", "新人", "その他")
        repeat(20000) { n ->
            val np = rnd.nextInt(1, 6)
            val people = (1..np).map {
                SplitPerson(
                    id = it.toLong(),
                    isSelf = it == 1,
                    order = it - 1,
                    isPayer = rnd.nextInt(4) == 0,
                    label = labels[rnd.nextInt(labels.size)],
                )
            }
            val labelAmounts = labels.filter { rnd.nextBoolean() }.associateWith { rnd.nextInt(0, 3000) }
            val groups = (1..rnd.nextInt(0, 6)).map {
                SplitGroupInput(
                    id = 100L + it,
                    amount = rnd.nextInt(-300, 3000),
                    isShared = rnd.nextInt(5) == 0,
                    pieceCount = if (rnd.nextBoolean()) null else rnd.nextInt(0, 8),
                )
            }
            val mode = listOf(SplitMode.SELF, SplitMode.WHOLE, SplitMode.ITEM)[rnd.nextInt(3)]
            val total = if (rnd.nextBoolean()) null else groups.sumOf { it.amount } + rnd.nextInt(-5, 6)
            fun shares(): List<ShareInput> {
                val k = kinds[rnd.nextInt(4)]
                val base = people.filter { rnd.nextBoolean() }.map {
                    ShareInput(
                        personId = it.id,
                        kind = k,
                        weight = if (rnd.nextInt(3) == 0) null else rnd.nextInt(-1, 5),
                        amount = if (rnd.nextInt(3) == 0) null else rnd.nextInt(0, 1000),
                    )
                }
                // 不明な人の行も混ぜる(捨てて警告になる経路)。
                val unknown = if (rnd.nextInt(20) == 0) listOf(ShareInput(99L, k, weight = 1, amount = 1)) else emptyList()
                return base + unknown
            }
            val item = groups.associate { it.id to shares() }
            val whole = shares()
            val wholeKind = wholeKinds[rnd.nextInt(wholeKinds.size)]
            val payers = if (rnd.nextInt(4) == 0) listOf(PayerInput(people.random(rnd).id, rnd.nextInt(0, 5000))) else emptyList()
            val input = SplitInput(
                total = total,
                mode = mode,
                people = people,
                groups = groups,
                itemShares = item,
                wholeShares = whole,
                payers = payers,
                wholeKind = wholeKind,
                labelAmounts = labelAmounts,
            )
            val r = SplitCalculator.calculate(input)
            val t = total ?: groups.sumOf { it.amount }
            assertEquals("case $n total", t, r.total)
            assertEquals("case $n lines sum", t, r.lines.sumOf { it.amount })
            assertTrue("case $n zero line", r.lines.none { it.amount == 0 })

            // 品目への配り直し: 各人の品目合計は人の合計と一致し、カテゴリ内訳も合計と一致する。
            val items = groups.flatMap { g ->
                (0 until rnd.nextInt(0, 3)).map { j ->
                    ItemYen(g.id * 10 + j, g.id, rnd.nextLong(-50, 1000), listOf("食", "日用")[rnd.nextInt(2)])
                }
            } + if (rnd.nextInt(4) == 0) listOf(ItemYen(9999L, null, rnd.nextLong(0, 500), "雑")) else emptyList()
            for (p in people) {
                val perItem = SplitLedger.yenPerItem(r, p.id, items)
                if (items.isNotEmpty()) {
                    assertEquals("case $n ledger person ${p.id}", r.totalOf(p.id).toLong(), perItem.values.sum())
                } else {
                    assertTrue("case $n no items", perItem.isEmpty())
                }
                val breakdown = SplitLedger.categoryBreakdown(r, p.id, items)
                assertEquals("case $n breakdown person ${p.id}", r.totalOf(p.id), breakdown.sumOf { it.second })
            }
        }
    }

    @Test fun equalSplitPayerGetsLeastExtra() {
        for (a in 1..2000) for (np in 2..5) {
            val ids = (1L..np.toLong()).toList()
            val m = YenAllocator.allocate(a, ids.map { it to 1 }, setOf(1L), ids.associateWith { (it - 1).toInt() })
            assertEquals(a, m.values.sum())
            assertTrue("a=$a np=$np payer must not exceed others", m.getValue(1L) <= ids.drop(1).minOf { m.getValue(it) })
            val neg = YenAllocator.allocate(-a, ids.map { it to 1 }, setOf(1L), ids.associateWith { (it - 1).toInt() })
            assertEquals(-a, neg.values.sum())
            assertTrue("discount a=$a payer gets at least as much discount", neg.getValue(1L) <= ids.drop(1).minOf { neg.getValue(it) })
        }
    }
}
