package com.amgm.personallog.v4.screens.create

import com.amgm.personallog.v4.ui.AppState

/*
 * 作成(中央の＋と各作成シート)— create.js。担当: 作成シートの担当。
 * ほかの画面はここの関数だけを呼ぶ(署名は変えない)。
 */

/** 中央の＋: 作成の一覧(いまのタブで一番使うものを先頭・塗り) */
fun openCreate(app: AppState) { app.toast("作成(未実装)") }

/** 金額だけで支払い。parent=親のアクティビティ、date/time=引き継ぐ日付・時刻 */
fun openPaymentSheet(app: AppState, parent: String? = null, date: String? = null, time: String? = null) { app.toast("支払い(未実装)") }

/** レシートで支払い */
fun openReceiptSheet(app: AppState) { app.toast("レシート(未実装)") }

/** アクティビティ(期間 or 瞬間)を作る。parent=親、date/time=引き継ぎ */
fun openActivitySheet(app: AppState, instant: Boolean = false, parent: String? = null, date: String? = null, time: String? = null) { app.toast("アクティビティ(未実装)") }

/** 活動の中に子を作る: 種類(支払い / 期間 / 瞬間)を選ぶシート → 親・日付・時刻を引き継ぐ */
fun openAddKidSheet(app: AppState, parent: String) { app.toast("子の追加(未実装)") }

/** 予定(todo=false)か ToDo(todo=true)を作る。date=日付、ev=親の予定(ToDo) */
fun openPlanSheet(app: AppState, date: String? = null, todo: Boolean = false, ev: String? = null) { app.toast("予定(未実装)") }

/** メモを書く。link=付けるエンティティ */
fun openMemoSheet(app: AppState, link: String? = null) { app.toast("メモ(未実装)") }

/** エンティティを追加 */
fun openEntitySheet(app: AppState) { app.toast("エンティティ(未実装)") }

/** 日記を書く・直す */
fun openDiarySheet(app: AppState, date: String) { app.toast("日記(未実装)") }
