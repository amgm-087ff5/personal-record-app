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
 * 支払い(ExpenseGroup)の未読を覚える。レシート読み取りで作られ、まだタップしていないものが未読。
 * DB には入れず DataStore に保存する(端末ごと・バックアップ対象外)。
 */
@Singleton
class PaymentReadStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyUnread = stringSetPreferencesKey("unreadPaymentGroupIds")

    /** 未読の支払いの id。 */
    val unreadIds: Flow<Set<Long>> = dataStore.data.map { p ->
        p[keyUnread].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun markUnread(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        dataStore.edit { p ->
            p[keyUnread] = p[keyUnread].orEmpty() + ids.map { it.toString() }
        }
    }

    /** 既読にする(未読から外す)。 */
    suspend fun markRead(id: Long) {
        remove(listOf(id))
    }

    /** マージで消えた支払いを未読から外す。 */
    suspend fun forget(ids: Collection<Long>) {
        remove(ids)
    }

    private suspend fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val drop = ids.map { it.toString() }.toSet()
        dataStore.edit { p ->
            p[keyUnread] = p[keyUnread].orEmpty() - drop
        }
    }
}
