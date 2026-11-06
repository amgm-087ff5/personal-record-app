package com.amgm.personallog.v4.data

import kotlinx.serialization.Serializable

/*
 * データモデル — UI v4 モック(v4/js/data.js・plans.js)をそのまま型にしたもの。
 * 意味は v4/DESIGN.md と claude/design-system-meanings*.md。
 * 1つのコレクション(A・X・E・PLN…)の1件が、Room の1行(JSON)になる(Store.kt)。
 * どの型にも ext(追加の文字列の項目)がある。画面が新しい項目を必要としたら、型を変えずに ext に入れてよい。
 */

/** エンティティの種類(人物・場所・店…)。p = 親の種類 */
@Serializable
data class TypeDef(val n: String, val p: String? = null)

/** エンティティの事実。ver = 版の説明、hist = 以前の値 [値, 期間, 理由]、rare = あまり使わない(折りたたむ) */
@Serializable
data class Fact(
    val k: String,
    val v: String,
    val ver: String? = null,
    val hist: List<List<String>> = emptyList(),
    val rare: Boolean = false,
)

/** 関係。mutual=true は ⟷(お互い)、false は →(一方向。例 勤務先) */
@Serializable
data class Rel(val kind: String, val to: String, val period: String = "", val mutual: Boolean = false)

/** エンティティ(縁): 人・場所・会社・物・書類・プロジェクト */
@Serializable
data class Entity(
    val n: String,
    val i: String = n.take(1),
    val t: List<String>,
    val facts: List<Fact> = emptyList(),
    val rels: List<Rel> = emptyList(),
    val ext: Map<String, String> = emptyMap(),
)

/** エンティティのノート(縁ごとの長い文書)。body = [見出し, 本文]。本文の {e:id} / {a:id} は参照 */
@Serializable
data class Note(
    val upd: String,
    val ver: Int,
    val body: List<List<String>>,
    val subs: List<List<String>> = emptyList(),
)

/** アクティビティの中でのエンティティの役割。ent が null なら未登録(name だけある) */
@Serializable
data class Role(val role: String, val ent: String? = null, val name: String? = null)

/** 品目の金額行。k = "amt"(円) / "pct"(%)。v は符号つき。1行目=本体 */
@Serializable
data class AmtLine(val n: String = "", val k: String = "amt", val v: Int)

/** 支払いの品目。price = 合計(lines があれば lines の合計)。b = 負担 自分/半分/相手の名前 */
@Serializable
data class PayItem(
    val n: String,
    val price: Int,
    val lines: List<AmtLine> = emptyList(),
    val b: String = "自分",
    val tax: Int = 10,
    val memo: String? = null,
)

/** 立替: who が相手、amt が相手の分、state = 未精算 / 精算済み */
@Serializable
data class Advance(val who: String, val amt: Int, val state: String = "未精算")

/**
 * 記録(アクティビティ)。type:
 *  - 日記 = 1日(date, dd, dw, body, photos, kids)
 *  - 期間の活動(time〜end): 食事・会話・作業・移動・買い物・出来事
 *  - 瞬間(time だけ): 起床・就寝・出発・到着・出来事・支払い
 * 親子: 日記 › 活動 › 活動 › 支払い(parent / kids)
 */
@Serializable
data class Activity(
    val type: String,
    val parent: String? = null,
    val date: String? = null,
    val dd: String? = null,
    val dw: String? = null,
    val body: String? = null,
    val photos: Int = 0,
    val kids: List<String> = emptyList(),
    val time: String? = null,
    val end: String? = null,
    val title: String = "",
    val roles: List<Role> = emptyList(),
    /** 支払い方法(現金・カード ••1234) */
    val pay: String? = null,
    val amt: Int? = null,
    val items: List<PayItem> = emptyList(),
    /** 自分の支出(X)のキー */
    val x: String? = null,
    val adv: Advance? = null,
    val ext: Map<String, String> = emptyMap(),
)

/** 自分の支出の品目。b = 全額 / 1/2 / 按分。unk = 中身が分からない */
@Serializable
data class XItem(
    val n: String,
    val price: Int,
    val b: String = "全額",
    val memo: String? = null,
    val unk: Boolean = false,
)

/** 自分の支出(支払いのうち自分の分)。sp/fx = 支出ごとの上書き("ask" は未指定) */
@Serializable
data class Expense(
    val pay: String,
    val amt: Int,
    val cat: String,
    val sp: String? = null,
    val fx: String? = null,
    val memo: String = "",
    val items: List<XItem> = emptyList(),
    val ext: Map<String, String> = emptyMap(),
)

/** カテゴリ。c = 色(大分類だけ)、fx = 固定/変動/ask、sp = 日常/非日常/ask(子は親から継承) */
@Serializable
data class Category(
    val n: String,
    val p: String? = null,
    val c: String? = null,
    val fx: String? = null,
    val sp: String? = null,
)

/** 過去の月の大分類ごとの合計 */
@Serializable
data class TrendMonth(val label: String, val byRoot: Map<String, Int>)

/** すぐメモ。links = エンティティのキー、rec = 記録のキー */
@Serializable
data class Memo(
    val body: String,
    val at: String,
    val links: List<String> = emptyList(),
    val rec: String? = null,
    val pin: Boolean = false,
    val ext: Map<String, String> = emptyMap(),
)

/** 場所の参照: ent(登録済み)か name(未登録) */
@Serializable
data class PlaceRef(val ent: String? = null, val name: String? = null)

/** 支払い予定の金額とカテゴリ */
@Serializable
data class PlanPay(val amt: Int, val cat: String)

/** ToDo の小さいステップ */
@Serializable
data class SubTask(val t: String, val done: Boolean = false)

/**
 * 予定と ToDo(v4.13: 1つのデータ PLN)。kind = 予定 / 支払い予定 / ToDo。
 * 予定: t, d, d2, s, e, all, hol, place, who, todo, memo, alarm, rep, rec, ent, pay, from
 * ToDo: t, due, done(完了日), list, pri, ev(親の予定), ent, rep, sub, memo, payRef(結び付いた支払い)
 */
@Serializable
data class Plan(
    val kind: String,
    val t: String,
    val d: String? = null,
    val d2: String? = null,
    val s: String? = null,
    val e: String? = null,
    val all: Boolean = false,
    val hol: Boolean = false,
    val place: PlaceRef? = null,
    val who: List<String> = emptyList(),
    val todo: List<String> = emptyList(),
    val memo: String? = null,
    val alarm: String? = null,
    val rep: String? = null,
    val rec: String? = null,
    val ent: List<String> = emptyList(),
    val pay: PlanPay? = null,
    val from: String? = null,
    val due: String? = null,
    val done: String? = null,
    val list: String? = null,
    val pri: Boolean = false,
    val ev: String? = null,
    val sub: List<SubTask> = emptyList(),
    val payRef: String? = null,
    val ext: Map<String, String> = emptyMap(),
) {
    val isTodo: Boolean get() = kind == KIND_TODO

    companion object {
        const val KIND_EVENT = "予定"
        const val KIND_PAY = "支払い予定"
        const val KIND_TODO = "ToDo"
    }
}

/** ToDo のリスト。shop = 買い物リストの店 */
@Serializable
data class TodoList(val n: String, val shop: String? = null)

/** 設定(端末内) */
@Serializable
data class Settings(
    /** system / light / dark */
    val theme: String = "system",
    /** 見本データを入れた時の「今日」とのずれ(日)。表示のためではなく記録用 */
    val seededShiftDays: Int = 0,
)

/**
 * アプリの全データ。画面はこれを読むだけで、書くときは Store.update { } を使う。
 * Map の順序は挿入順(LinkedHashMap)。
 */
@Serializable
data class AppData(
    val types: Map<String, TypeDef> = emptyMap(),
    val entities: Map<String, Entity> = emptyMap(),
    val notes: Map<String, Note> = emptyMap(),
    val acts: Map<String, Activity> = emptyMap(),
    val expenses: Map<String, Expense> = emptyMap(),
    val cats: Map<String, Category> = emptyMap(),
    val trend: List<TrendMonth> = emptyList(),
    val memos: Map<String, Memo> = emptyMap(),
    val plans: Map<String, Plan> = emptyMap(),
    val lists: Map<String, TodoList> = emptyMap(),
    val typeProps: Map<String, List<String>> = emptyMap(),
    val colors: List<String> = emptyList(),
    val settings: Settings = Settings(),
)
