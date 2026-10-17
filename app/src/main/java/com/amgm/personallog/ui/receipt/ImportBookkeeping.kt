package com.amgm.personallog.ui.receipt

/**
 * 読み取り完了後に詳細画面を開く先の管理(純Kotlin)。
 * 補正画面が出ている間は開く先を保留し、閉じたら最後の1件だけ開く。
 * 画面の外で溜めない: 開く先は常に高々1件で、画面が受け取って消費するまで StateFlow に1つだけ置く。
 */
class DeferredOpen {
    private var deferred: Long? = null

    /** 開く先 id を受け取る。今すぐ開くなら id、保留するなら null を返す。 */
    fun offer(id: Long, adjustActive: Boolean): Long? {
        if (adjustActive) {
            deferred = id
            return null
        }
        return id
    }

    /** 補正画面が閉じたとき。保留していた id があれば返して空にする。 */
    fun release(adjustActive: Boolean): Long? {
        if (adjustActive) return null
        return deferred.also { deferred = null }
    }

    /** 取り込みの項目が無くなったとき等に捨てる。 */
    fun clear() {
        deferred = null
    }
}

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
