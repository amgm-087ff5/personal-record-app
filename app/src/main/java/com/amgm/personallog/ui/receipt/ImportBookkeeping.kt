package com.amgm.personallog.ui.receipt

/** 同じ選択内の重複検出用: 画像ハッシュ -> 予約している項目(純Kotlin)。 */
class HashClaims {
    private val map = HashMap<String, Long>()

    fun isClaimed(hash: String): Boolean = map.containsKey(hash)

    /** まだ誰も予約していなければ予約する。予約できたら true。 */
    fun claimIfAbsent(hash: String, key: Long): Boolean {
        if (map.containsKey(hash)) return false
        map[hash] = key
        return true
    }

    /** 重複と分かっていても追加すると決めた項目の予約(既に予約があれば何もしない)。 */
    fun claimAnyway(hash: String, key: Long) {
        map.putIfAbsent(hash, key)
    }

    /** その項目の予約をすべて外す。 */
    fun release(key: Long) {
        map.values.removeAll { it == key }
    }
}
