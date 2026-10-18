package com.amgm.personallog.domain.split
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.ShareKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 乱数で2万件の入力を作り、どの方式でも「各人の合計の和 = 合計」と「均等割で支払者が余りの1円を負わない」ことを確かめる。 */
class SplitInvariantTest {
    @Test fun totalAlwaysMatches_andPayerNeverWorseOnEqual() {
        val rnd = Random(42)
        val kinds = listOf(ShareKind.ASSIGN, ShareKind.EQUAL, ShareKind.RATIO, ShareKind.AMOUNT)
        repeat(20000) { n ->
            val np = rnd.nextInt(1, 6)
            val people = (1..np).map { SplitPerson(it.toLong(), it == 1, it - 1) }
            val groups = (1..rnd.nextInt(0, 6)).map { SplitGroupInput(100L + it, rnd.nextInt(-300, 3000), rnd.nextInt(5) == 0) }
            val mode = listOf(SplitMode.SELF, SplitMode.WHOLE, SplitMode.ITEM)[rnd.nextInt(3)]
            val total = if (rnd.nextBoolean()) null else groups.sumOf { it.amount } + rnd.nextInt(-5, 6)
            fun shares(): List<ShareInput> { val k = kinds[rnd.nextInt(4)]; return people.filter { rnd.nextBoolean() }.map { ShareInput(it.id, k, rnd.nextInt(0, 5), rnd.nextInt(0, 1000)) } }
            val item = groups.associate { it.id to shares() }
            val whole = shares()
            val payers = if (rnd.nextInt(4) == 0) listOf(PayerInput(people.random(rnd).id, rnd.nextInt(0, 5000))) else emptyList()
            val input = SplitInput(total, mode, people, groups, item, whole, payers)
            val r = SplitCalculator.calculate(input)
            val t = total ?: groups.sumOf { it.amount }
            assertEquals("case $n total", t, r.total)
            assertEquals("case $n lines sum", t, r.lines.sumOf { it.amount })
            assertTrue("case $n zero line", r.lines.none { it.amount == 0 })
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
