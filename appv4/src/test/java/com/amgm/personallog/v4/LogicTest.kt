package com.amgm.personallog.v4

import com.amgm.personallog.v4.data.Activity
import com.amgm.personallog.v4.data.AmtLine
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Category
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.Expense
import com.amgm.personallog.v4.data.PayItem
import com.amgm.personallog.v4.data.Plan
import com.amgm.personallog.v4.data.PlanPay
import com.amgm.personallog.v4.data.Role
import com.amgm.personallog.v4.data.Seed
import com.amgm.personallog.v4.data.actsOf
import com.amgm.personallog.v4.data.amount
import com.amgm.personallog.v4.data.catFx
import com.amgm.personallog.v4.data.catSp
import com.amgm.personallog.v4.data.descOf
import com.amgm.personallog.v4.data.dayKeyOf
import com.amgm.personallog.v4.data.evOn
import com.amgm.personallog.v4.data.linesTotal
import com.amgm.personallog.v4.data.nextOn
import com.amgm.personallog.v4.data.pctYen
import com.amgm.personallog.v4.data.settled
import com.amgm.personallog.v4.data.xFx
import com.amgm.personallog.v4.data.xSp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 純粋な規則(金額・日付・予定・整理・親子・カテゴリの継承)。Android は使わない。 */
class LogicTest {
    private val seed = Seed.data()

    /* ---- 金額 ---- */

    @Test fun linesTotalRoundsPercentOnRunningSubtotal() {
        assertEquals(612, linesTotal(listOf(AmtLine(v = 680), AmtLine(n = "割引", k = "pct", v = -10))))
        assertEquals(1130, linesTotal(listOf(AmtLine(v = 1000), AmtLine(k = "pct", v = 8), AmtLine(v = 50))))
        assertEquals(116, linesTotal(listOf(AmtLine(v = 105), AmtLine(k = "pct", v = 10))))
        assertNull(linesTotal(emptyList()))
    }

    @Test fun payItemAmountIsLinesTotalWhenLinesExist() {
        val item = PayItem(n = "ランチ", price = 1, lines = listOf(AmtLine(v = 680), AmtLine(k = "pct", v = -10)))
        assertEquals(612, item.amount())
        assertEquals(500, PayItem(n = "x", price = 500).amount())
    }

    @Test fun pctYenIsTheYenOfThatLine() {
        assertEquals(-68, pctYen(listOf(AmtLine(v = 680), AmtLine(k = "pct", v = -10)), 1))
    }

    /* ---- 日付 ---- */

    @Test fun weekdayAndDateHelpers() {
        assertEquals("金", Dates.wd("2026-10-30"))
        assertEquals("土", Dates.wd("2026-10-31"))
        assertEquals("10/30(金)", Dates.mdw("2026-10-30"))
        assertEquals("2026-11-05", Dates.addDays("2026-10-30", 6))
        assertEquals("昨日", Dates.relDay("2026-10-30", "2026-10-31"))
    }

    /* ---- 予定 ---- */

    @Test fun evOnShowsMultiDayAndRepeatingEvents() {
        assertTrue(seed.evOn("2026-11-08").any { it.first == "e6" })
        assertTrue(seed.evOn("2026-11-09").any { it.first == "e6" })
        assertFalse(seed.evOn("2026-11-10").any { it.first == "e6" })
        assertTrue("毎月27日の支払い予定は 11/27 に出る", seed.evOn("2026-11-27").any { it.first == "e4" })
        assertFalse(seed.evOn("2026-11-28").any { it.first == "e4" })
        // 時刻順(開始の早い順)
        assertEquals(listOf("e9", "e1", "e2"), seed.evOn("2026-10-31").map { it.first })
    }

    @Test fun nextOnGivesNextOccurrence() {
        val e4 = seed.plans.getValue("e4")
        assertEquals("2026-11-27", nextOn(e4, "2026-10-31"))
        assertEquals("2026-11-27", nextOn(e4, "2026-10-27"))
        assertEquals("2026-11-27", nextOn(e4, "2026-11-01"))
        assertEquals("2026-10-31", nextOn(seed.plans.getValue("e9"), "2026-10-31"))
    }

    @Test fun nextOnMonthEndReturnsRealDate() {
        // 毎月31日の予定が今日 10/31 のとき、次回は 11/31 ではなく実在する日付のはず
        val p = Plan(kind = Plan.KIND_EVENT, t = "家賃", d = "2026-10-31", rep = "毎月31日")
        val next = nextOn(p, "2026-10-31")!!
        assertTrue("次回が実在しない日付: $next", runCatching { LocalDate.parse(next) }.isSuccess)
        assertTrue(next > "2026-10-31")
    }

    @Test fun settledFixesTodoLinksAndKinds() {
        val plans = linkedMapOf(
            "ev1" to Plan(kind = Plan.KIND_EVENT, t = "予定", d = "2026-10-31", s = "10:00", e = "11:00", todo = listOf("bogus")),
            "td1" to Plan(kind = Plan.KIND_TODO, t = "子", ev = "ev1"),
            "td2" to Plan(kind = Plan.KIND_TODO, t = "親が無い", ev = "gone"),
            "td3" to Plan(kind = Plan.KIND_TODO, t = "親がToDo", ev = "td1"),
            "pay1" to Plan(kind = Plan.KIND_EVENT, t = "家賃", d = "2026-10-27", pay = PlanPay(85000, "rent")),
        )
        val s = AppData(plans = plans).settled()
        assertEquals("予定の todo は ev から作り直される", listOf("td1"), s.plans.getValue("ev1").todo)
        assertNull("親が消えた ToDo は外れる", s.plans.getValue("td2").ev)
        assertNull("親が ToDo の ToDo は外れる", s.plans.getValue("td3").ev)
        assertEquals("pay を持つ予定は支払い予定になる", Plan.KIND_PAY, s.plans.getValue("pay1").kind)
        assertEquals(s, s.settled())
    }

    /* ---- 記録の親子・関わり ---- */

    @Test fun actsOfSkipsChildWhenParentIsAlreadyListed() {
        val acts = linkedMapOf(
            "P" to Activity("出来事", title = "P", roles = listOf(Role("参加", "h")), kids = listOf("C")),
            "C" to Activity("出来事", parent = "P", title = "C", roles = listOf(Role("参加", "h"))),
            "Q" to Activity("出来事", title = "Q", roles = listOf(Role("場所", "other"))),
            "C2" to Activity("出来事", parent = "Q", title = "C2", roles = listOf(Role("参加", "h"))),
            "D" to Activity("出来事", title = "D", roles = listOf(Role("参加", "h"))),
        )
        val keys = AppData(acts = acts).actsOf("h").map { it.first }
        assertEquals("親が既に入っている子(C)は除く。親が関わらない子(C2)は入る", listOf("P", "C2", "D"), keys)
    }

    @Test fun descendantsAndDayKeyFollowParents() {
        assertEquals(listOf("c1", "p16"), seed.descOf("w2"))
        assertEquals("d1030", seed.dayKeyOf("p16"))
    }

    /* ---- カテゴリの継承と「都度指定」(ask) ---- */

    @Test fun categoryFixedAndSpecialInheritAndAsk() {
        val d = seed.copy(
            cats = seed.cats + listOf(
                "gift2" to Category("子の贈り物", p = "gift"),
                "food2" to Category("自分で決める食費", p = "food", fx = "固定"),
            ),
        )
        assertEquals("親(食費)の変動を継承", "変動", d.catFx("cook"))
        assertEquals("通信の固定", "固定", d.catFx("phone"))
        assertNull("ask は未指定(null)", d.catFx("gift"))
        assertNull("親が ask なら子も未指定", d.catFx("gift2"))
        assertEquals("子の設定が親より優先", "固定", d.catFx("food2"))
        assertEquals("特別感の継承", "非日常", d.catSp("gift"))
        assertEquals("特別感の継承(日常)", "日常", d.catSp("cook"))
        assertEquals("カテゴリの特別感", "非日常", d.xSp(Expense(pay = "p", amt = 1, cat = "taxi")))
        assertNull("支出の sp=ask は未指定", d.xSp(Expense(pay = "p", amt = 1, cat = "cook", sp = "ask")))
        assertEquals("支出の sp は親より優先", "非日常", d.xSp(Expense(pay = "p", amt = 1, cat = "cook", sp = "非日常")))
        assertNull("支出の fx=ask は未指定", d.xFx(Expense(pay = "p", amt = 1, cat = "phone", fx = "ask")))
        assertEquals("支出の fx 未指定はカテゴリから", "変動", d.xFx(Expense(pay = "p", amt = 1, cat = "cook")))
    }
}
