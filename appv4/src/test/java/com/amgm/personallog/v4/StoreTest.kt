package com.amgm.personallog.v4

import androidx.room.Room
import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.Dates
import com.amgm.personallog.v4.data.FixedClock
import com.amgm.personallog.v4.data.ObjDao
import com.amgm.personallog.v4.data.ObjRow
import com.amgm.personallog.v4.data.Seed
import com.amgm.personallog.v4.data.Store
import com.amgm.personallog.v4.data.V4Database
import com.amgm.personallog.v4.data.dayKeyOf
import com.amgm.personallog.v4.data.descOf
import com.amgm.personallog.v4.data.settled
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Store(Room の obj テーブル)の保存・読み込み・取り消し・初期化、見本データのずらし。
 * 書き込みは Dispatchers.IO で非同期に行われるので、DB の中身が落ち着くまで待ってから確かめる。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreTest {
    private lateinit var db: V4Database
    private lateinit var rec: RecDao
    private val clock = FixedClock()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), V4Database::class.java)
            .allowMainThreadQueries().build()
        rec = RecDao(db.dao())
    }

    @After fun tearDown() { db.close() }

    /** 書き込みを数えるための DAO の包み */
    private class RecDao(val inner: ObjDao) : ObjDao {
        val puts = CopyOnWriteArrayList<List<ObjRow>>()
        val deletes = CopyOnWriteArrayList<Pair<String, List<String>>>()
        override suspend fun all(): List<ObjRow> = inner.all()
        override suspend fun put(rows: List<ObjRow>) { puts.add(rows); inner.put(rows) }
        override suspend fun delete(coll: String, ids: List<String>) { deletes.add(coll to ids); inner.delete(coll, ids) }
        override suspend fun clear() = inner.clear()
    }

    private fun store(): Store = Store(rec, clock)

    private fun dbRows(): List<ObjRow> = runBlocking { db.dao().all() }

    /** 待つ条件が真になるまで(最大10秒) */
    private fun waitUntil(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!cond()) {
            if (System.currentTimeMillis() > end) fail("待ち時間切れ: $what")
            Thread.sleep(10)
        }
    }

    /** 非同期の書き込みが落ち着くまで待つ(DB の中身が 3 回続けて同じになったら終わり) */
    private fun settleDb() {
        var last = dbRows()
        var same = 0
        val end = System.currentTimeMillis() + 10_000
        while (same < 3) {
            if (System.currentTimeMillis() > end) fail("DB の書き込みが落ち着かない")
            Thread.sleep(30)
            val now = dbRows()
            if (now == last) same++ else { same = 0; last = now }
        }
    }

    private fun loaded(s: Store = store()): Store {
        s.load()
        waitUntil("load") { s.ready.value }
        settleDb()
        return s
    }

    /** DB の中身だけから作り直したデータ(保存 → 読み直しの往復) */
    private fun reloadedData(): AppData = loaded(store()).data.value

    @Test fun firstLoadSeedsSampleData() {
        val s = loaded()
        val seed = Seed.data().settled()
        assertEquals(seed.acts.size, s.data.value.acts.size)
        assertEquals(seed.acts.size, dbRows().count { it.coll == "acts" })
        assertEquals(seed, s.data.value)
    }

    @Test fun updateWritesOnlyChangedRowAndRoundTrips() {
        val s = loaded()
        rec.puts.clear()
        s.update { d -> d.copy(acts = d.acts + ("w1" to d.acts.getValue("w1").copy(title = "起床(変更)"))) }
        settleDb()
        val written = rec.puts.flatten().map { it.coll to it.id }
        assertEquals("変えた1行だけが保存されるはず", listOf("acts" to "w1"), written)
        assertEquals(s.data.value, reloadedData())
    }

    @Test fun deletedRowIsRemovedFromDb() {
        val s = loaded()
        s.update { d -> d.copy(expenses = d.expenses - "x3") }
        settleDb()
        assertFalse(dbRows().any { it.coll == "expenses" && it.id == "x3" })
        assertFalse(reloadedData().expenses.containsKey("x3"))
    }

    @Test fun undoRestoresStateAndDb() {
        val s = loaded()
        val before = s.data.value
        s.update { d -> d.copy(acts = d.acts - "p3", expenses = d.expenses - "x3") }
        settleDb()
        assertTrue(s.undo())
        settleDb()
        assertEquals(before, s.data.value)
        assertEquals(before, reloadedData())
        assertFalse("取り消しは1段だけ", s.undo())
    }

    @Test fun resetReturnsToSeed() {
        val s = loaded()
        s.update { d -> d.copy(acts = d.acts - "w2") }
        settleDb()
        s.reset()
        settleDb()
        val seed = Seed.data().settled()
        assertEquals(seed, s.data.value)
        assertEquals(seed, reloadedData())
    }

    @Test fun shiftMovesDatesDayKeysAndLinks() {
        val base = Seed.data()
        val s = Seed.shift(base, 6)
        assertLinks(base, "見本(ずらす前)")
        assertLinks(s, "ずらした後")

        // 日記: 日付・曜日・日のキー
        val diaries = s.acts.filter { it.value.type == "日記" }
        assertEquals(base.acts.values.count { it.type == "日記" }, diaries.size)
        for ((k, a) in diaries) {
            val dt = a.date!!
            assertEquals("日のキーは日付から作る", "d" + dt.replace("-", ""), k)
            assertEquals(Dates.parse(dt).dayOfMonth.toString(), a.dd)
            assertEquals(Dates.wd(dt), a.dw)
        }
        assertEquals("2026-11-05", s.acts.getValue("d20261105").date)

        // 親子: p16 → c1 → w2 → 日記(d1030 が 11/05 の日記になる)
        assertEquals("d20261105", s.dayKeyOf("p16"))
        assertEquals(listOf("c1", "p16"), s.descOf("w2"))

        // メモの日時・予定の日付
        assertEquals("2026-11-06 08:12", s.memos.getValue("q4").at)
        assertEquals("2026-11-06", s.plans.getValue("e9").d)
        assertEquals("2026-11-14", s.plans.getValue("e6").d)
        assertEquals("2026-11-15", s.plans.getValue("e6").d2)
        assertEquals("2026-11-04", s.plans.getValue("t7").due)
        assertEquals("2026-11-06", s.plans.getValue("t9").done)
        assertEquals(6, s.settings.seededShiftDays)
    }

    @Test fun repeatingPlansKeepTheirDayAfterShift() {
        // 「毎月N日」と書かれた予定は、ずらした後も日付の日が N のままのはず
        fun mismatches(d: AppData): List<String> = d.plans.mapNotNull { (k, p) ->
            val n = Regex("毎月(\\d+)日").matchEntire(p.rep ?: "")?.groupValues?.get(1)?.toInt()
            if (n == null || p.d == null) null
            else if (p.d.substring(8).toInt() == n) null
            else "$k: 繰り返し「${p.rep}」なのに日付は ${p.d}"
        }
        assertEquals("見本(ずらす前)", emptyList<String>(), mismatches(Seed.data()))
        assertEquals("ずらした後(6日)", emptyList<String>(), mismatches(Seed.shift(Seed.data(), 6)))
    }
}
