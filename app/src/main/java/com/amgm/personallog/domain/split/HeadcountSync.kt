package com.amgm.personallog.domain.split

// 割り勘の参加人数を変える純関数(Android に依存しない。標準ライブラリのみ)。
// 支払い側(参加者)と支出側(個別支出)の人数を同期する処理で使う。UI への接続は別。

/** 目標人数は 1 人以上で扱う(0 以下は 1 人)。 */
private fun atLeastOne(targetCount: Int): Int = maxOf(targetCount, 1)

/** 増やす人数(空欄のメンバーを足す数)。目標が今の人数以下なら 0。目標が 0 以下なら 1 人扱い。 */
fun shortfall(currentCount: Int, targetCount: Int): Int = maxOf(atLeastOne(targetCount) - currentCount, 0)

/** 減らす人数。今の人数が目標以下なら 0。目標が 0 以下なら 1 人扱い。 */
fun excess(currentCount: Int, targetCount: Int): Int = maxOf(currentCount - atLeastOne(targetCount), 0)

/**
 * 参加者1行ぶんの判定材料。
 * hasName: 名前がある(登録メンバー・入力中の名前)。hasAllocation: 割当の行がある。hasExpense: 個別支出(支出)がある。
 */
data class ParticipantFacts(
    val id: Long,
    val isSelf: Boolean,
    val hasName: Boolean,
    val hasAllocation: Boolean,
    val hasExpense: Boolean,
    val sortOrder: Int,
)

/**
 * 人数を減らすときの計画。
 * - autoRemoveIds: 確認なしで消す参加者(名前・割当・支出が無い行を末尾から)。
 * - needsChoice: 自動では消さず、ダイアログで選ばせる候補(末尾から。自分も含む)。
 * - remainingExcess: 候補を全部選んでも減らせない数(人数が足りないとき)。
 */
data class RemovalPlan(
    val autoRemoveIds: List<Long>,
    val needsChoice: List<Long>,
    val remainingExcess: Int,
)

/**
 * 減らす人数 excess を満たすための計画を作る。
 * 1) 自分でなく、名前・割当・支出の無い行を末尾から、excess を満たすまで autoRemoveIds に入れる。
 * 2) 足りなければ、自動で消した行以外の全員を末尾から needsChoice に入れる(利用者が選ぶ)。
 * 3) 候補を全部使っても満たせない数を remainingExcess に入れる。
 * 自分は自動では消さない(名前が無くても選択に回す)。excess が 0 以下なら何もしない。
 */
fun removalPlan(participants: List<ParticipantFacts>, excess: Int): RemovalPlan {
    if (excess <= 0) return RemovalPlan(emptyList(), emptyList(), 0)
    // 末尾(並び順の後ろ)から見る
    val tailFirst = participants.sortedBy { it.sortOrder }.asReversed()
    val autoIds = tailFirst
        .filter { !it.isSelf && !it.hasName && !it.hasAllocation && !it.hasExpense }
        .take(excess)
        .map { it.id }
    val remainingNeed = excess - autoIds.size
    if (remainingNeed == 0) return RemovalPlan(autoIds, emptyList(), 0)
    val choice = tailFirst.filter { it.id !in autoIds }.map { it.id }
    val remainingExcess = maxOf(remainingNeed - choice.size, 0)
    return RemovalPlan(autoIds, choice, remainingExcess)
}
