package com.amgm.personallog.v4.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity as RoomEntity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/*
 * 保存: Room の1テーブル obj(coll, id, json)。1件=1行(コレクションの1要素を JSON で)。
 * 画面は Store.data(StateFlow<AppData>)を読み、書くときは Store.update { it.copy(...) } だけを使う。
 * update は変わったコレクションの、変わった行だけを書く(差分)。取り消し(トースト)は Store.undo()。
 */

@RoomEntity(tableName = "obj", primaryKeys = ["coll", "id"])
data class ObjRow(val coll: String, val id: String, val json: String, val ord: Int)

@Dao
interface ObjDao {
    @Query("SELECT * FROM obj ORDER BY coll, ord")
    suspend fun all(): List<ObjRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(rows: List<ObjRow>)

    @Query("DELETE FROM obj WHERE coll = :coll AND id IN (:ids)")
    suspend fun delete(coll: String, ids: List<String>)

    @Query("DELETE FROM obj")
    suspend fun clear()
}

@Database(entities = [ObjRow::class], version = 1, exportSchema = true)
abstract class V4Database : RoomDatabase() {
    abstract fun dao(): ObjDao
}

/** 1つのコレクションの読み書き */
private class Coll<T>(
    val name: String,
    val ser: KSerializer<T>,
    val get: (AppData) -> Map<String, T>,
    val set: (AppData, Map<String, T>) -> AppData,
)

class Store(private val dao: ObjDao?, val clock: AppClock = SystemClock) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 直前の状態(取り消し用。1段) */
    private var undoState: AppData? = null

    private val colls: List<Coll<*>> = listOf(
        Coll("types", TypeDef.serializer(), { it.types }) { d, m -> d.copy(types = m) },
        Coll("entities", Entity.serializer(), { it.entities }) { d, m -> d.copy(entities = m) },
        Coll("notes", Note.serializer(), { it.notes }) { d, m -> d.copy(notes = m) },
        Coll("acts", Activity.serializer(), { it.acts }) { d, m -> d.copy(acts = m) },
        Coll("expenses", Expense.serializer(), { it.expenses }) { d, m -> d.copy(expenses = m) },
        Coll("cats", Category.serializer(), { it.cats }) { d, m -> d.copy(cats = m) },
        Coll("memos", Memo.serializer(), { it.memos }) { d, m -> d.copy(memos = m) },
        Coll("plans", Plan.serializer(), { it.plans }) { d, m -> d.copy(plans = m) },
        Coll("lists", TodoList.serializer(), { it.lists }) { d, m -> d.copy(lists = m) },
        Coll("typeProps", ListSerializer(String.serializer()), { it.typeProps }) { d, m -> d.copy(typeProps = m) },
    )
    /** 1件ものは "single" コレクションに入れる */
    private val trendSer = ListSerializer(TrendMonth.serializer())
    private val colorsSer = ListSerializer(String.serializer())

    /** 起動時に読む。空なら見本データ(今日に合わせてずらす)を入れる */
    fun load(seedShiftToToday: Boolean = true) {
        scope.launch {
            lock.withLock {
                val rows = dao?.all() ?: emptyList()
                val d = if (rows.isEmpty()) {
                    val shift = if (seedShiftToToday) Dates.daysBetween(Seed.MOCK_TODAY, clock.today()).toInt() else 0
                    val seeded = Seed.shift(Seed.data(), shift).settled()
                    writeAll(seeded); seeded
                } else decode(rows)
                _data.value = d
                _ready.value = true
            }
        }
    }

    /** テスト・プレビュー用: データを直接置く(保存しない) */
    fun setForTest(d: AppData) { _data.value = d.settled(); _ready.value = true }

    /** データを変える。f は純粋な関数(今の AppData → 新しい AppData)。差分だけ保存する */
    fun update(undoable: Boolean = true, f: (AppData) -> AppData) {
        val before = _data.value
        val after = f(before).settled()
        if (after === before) return
        if (undoable) undoState = before
        _data.value = after
        scope.launch { lock.withLock { writeDiff(before, after) } }
    }

    /** 直前の update を取り消す(トーストの「取り消す」) */
    fun undo(): Boolean {
        val u = undoState ?: return false
        undoState = null
        val cur = _data.value
        _data.value = u
        scope.launch { lock.withLock { writeDiff(cur, u) } }
        return true
    }

    /** 全部消して見本データを入れ直す(設定の「初期化」) */
    fun reset() {
        scope.launch {
            lock.withLock {
                dao?.clear()
                val shift = Dates.daysBetween(Seed.MOCK_TODAY, clock.today()).toInt()
                val seeded = Seed.shift(Seed.data(), shift).settled()
                writeAll(seeded)
                undoState = null
                _data.value = seeded
            }
        }
    }

    /** 全部消して空にする */
    fun clearAll() {
        scope.launch {
            lock.withLock {
                dao?.clear()
                val empty = Seed.data().let { s -> AppData(types = s.types, cats = s.cats, lists = s.lists, typeProps = s.typeProps, colors = s.colors, entities = linkedMapOf("me" to s.entities["me"]!!), settings = _data.value.settings) }
                writeAll(empty); undoState = null; _data.value = empty
            }
        }
    }

    /* ---- 内部 ---- */
    @Suppress("UNCHECKED_CAST")
    private fun decode(rows: List<ObjRow>): AppData {
        var d = AppData()
        val by = rows.groupBy { it.coll }
        for (c in colls) {
            c as Coll<Any?>
            val m = LinkedHashMap<String, Any?>()
            for (r in by[c.name].orEmpty().sortedBy { it.ord }) runCatching { m[r.id] = json.decodeFromString(c.ser, r.json) }
            d = c.set(d, m)
        }
        by["single"].orEmpty().forEach { r ->
            when (r.id) {
                "trend" -> d = d.copy(trend = json.decodeFromString(trendSer, r.json))
                "colors" -> d = d.copy(colors = json.decodeFromString(colorsSer, r.json))
                "settings" -> d = d.copy(settings = json.decodeFromString(Settings.serializer(), r.json))
            }
        }
        return d
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun writeAll(d: AppData) {
        val dao = dao ?: return
        val rows = ArrayList<ObjRow>()
        for (c in colls) {
            c as Coll<Any?>
            c.get(d).entries.forEachIndexed { i, (k, v) -> rows.add(ObjRow(c.name, k, json.encodeToString(c.ser, v), i)) }
        }
        rows += singles(d)
        dao.put(rows)
    }

    private fun singles(d: AppData) = listOf(
        ObjRow("single", "trend", json.encodeToString(trendSer, d.trend), 0),
        ObjRow("single", "colors", json.encodeToString(colorsSer, d.colors), 1),
        ObjRow("single", "settings", json.encodeToString(Settings.serializer(), d.settings), 2),
    )

    @Suppress("UNCHECKED_CAST")
    private suspend fun writeDiff(a: AppData, b: AppData) {
        val dao = dao ?: return
        for (c in colls) {
            c as Coll<Any?>
            val ma = c.get(a); val mb = c.get(b)
            if (ma === mb) continue
            val put = ArrayList<ObjRow>()
            val orderChanged = ma.keys.toList() != mb.keys.toList()
            mb.entries.forEachIndexed { i, (k, v) -> if (orderChanged || ma[k] != v) put.add(ObjRow(c.name, k, json.encodeToString(c.ser, v), i)) }
            if (put.isNotEmpty()) dao.put(put)
            val del = ma.keys.filter { it !in mb.keys }
            if (del.isNotEmpty()) dao.delete(c.name, del)
        }
        if (a.trend != b.trend || a.colors != b.colors || a.settings != b.settings) dao.put(singles(b))
    }

    companion object {
        fun open(context: Context, clock: AppClock = SystemClock): Store {
            val db = Room.databaseBuilder(context.applicationContext, V4Database::class.java, "v4.db").build()
            return Store(db.dao(), clock)
        }
    }
}

@Suppress("unused")
private val unusedMapSer = MapSerializer(String.serializer(), String.serializer())
