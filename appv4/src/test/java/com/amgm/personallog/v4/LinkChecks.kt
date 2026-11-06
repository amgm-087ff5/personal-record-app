package com.amgm.personallog.v4

import com.amgm.personallog.v4.data.AppData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * データの参照の整合性(親子・日記の kids・記録への参照・支出の結び付き・予定の ToDo)。
 * 壊れていたら、どの行がどう壊れたかをメッセージに書いて落とす。
 */
internal fun assertLinks(d: AppData, label: String) {
    for ((k, a) in d.acts) {
        for (kid in a.kids) {
            assertTrue("$label: $k の kids に $kid が無い", d.acts.containsKey(kid))
            assertEquals("$label: $kid の親が $k ではない", k, d.acts.getValue(kid).parent)
        }
        a.parent?.let { p ->
            assertTrue("$label: $k の親 $p が無い", d.acts.containsKey(p))
            assertTrue("$label: 親 $p の kids に $k が無い", d.acts.getValue(p).kids.contains(k))
        }
        a.x?.let { x -> assertTrue("$label: $k の支出 $x が無い", d.expenses.containsKey(x)) }
    }
    for ((k, x) in d.expenses) {
        assertTrue("$label: 支出 $k の支払い ${x.pay} が無い", d.acts.containsKey(x.pay))
        assertEquals("$label: 支出 $k と支払い ${x.pay} の x が一致しない", k, d.acts.getValue(x.pay).x)
    }
    for ((k, m) in d.memos) {
        m.rec?.let { assertTrue("$label: メモ $k の記録 $it が無い", d.acts.containsKey(it)) }
    }
    for ((k, p) in d.plans) {
        p.rec?.let { assertTrue("$label: 予定 $k の記録 $it が無い", d.acts.containsKey(it)) }
        p.payRef?.let { assertTrue("$label: ToDo $k の支払い $it が無い", d.acts.containsKey(it)) }
        p.ev?.let { assertTrue("$label: ToDo $k の親の予定 $it が無い", d.plans.containsKey(it)) }
        if (!p.isTodo) {
            val kids = d.plans.filterValues { it.isTodo && it.ev == k }.keys
            assertEquals("$label: 予定 $k の todo と、ToDo の ev が一致しない", kids, p.todo.toSet())
        }
    }
}
