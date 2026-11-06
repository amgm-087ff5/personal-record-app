package com.amgm.personallog.v4.screens.plan

import com.amgm.personallog.v4.ui.AppState

/*
 * 予定・ToDo の操作 — plan.js の recordEvent / toggleTask。担当: ToDo・予定の担当。
 * ほかの画面(ホーム・エンティティ・アクティビティ)はここを呼ぶ(署名は変えない)。
 */

/** 予定を記録にする(支払い予定は支払いとして記録)。記録を作り、予定の rec に結び付け、トースト(取り消せる) */
fun recordEvent(app: AppState, evKey: String) { app.toast("記録にする(未実装)") }

/** ToDo の完了/未完了を切り替える(トーストで取り消せる) */
fun toggleTask(app: AppState, key: String) {
    app.store.update { d -> val t = d.plans[key] ?: return@update d; d.copy(plans = d.plans + (key to t.copy(done = if (t.done == null) app.today else null))) }
    app.toast(if (app.data.plans[key]?.done != null) "完了にしました" else "未完了に戻しました", undo = true)
}
