package com.amgm.personallog.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 未読の支払い(読み取り完了したのにまだ開いていないもの)の id を覚える。
 * 値は Payment.id の文字列。支出画面か支払い詳細を開いたら既読にする。
 */
@Singleton
class PaymentReadStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringSetPreferencesKey("unreadPaymentIds")

    val unreadIds: Flow<Set<Long>> = dataStore.data.map { p ->
        p[key].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun markUnread(id: Long) {
        dataStore.edit { p -> p[key] = p[key].orEmpty() + id.toString() }
    }

    suspend fun markRead(id: Long) {
        dataStore.edit { p ->
            val current = p[key].orEmpty()
            if (id.toString() in current) p[key] = current - id.toString()
        }
    }

    /** 支払いが消えたときなどに、まとめて忘れる。 */
    suspend fun forget(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val drop = ids.map { it.toString() }.toSet()
        dataStore.edit { p ->
            val current = p[key].orEmpty()
            if (current.any { it in drop }) p[key] = current - drop
        }
    }
}
