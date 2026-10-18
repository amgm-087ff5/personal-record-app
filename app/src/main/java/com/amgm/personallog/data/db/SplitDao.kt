package com.amgm.personallog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.PaymentPayer
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.ShareGroupItem
import com.amgm.personallog.data.model.SplitSheet
import com.amgm.personallog.data.model.WholeShare
import kotlinx.coroutines.flow.Flow

/**
 * 割り勘(DB v10)の表の読み書き。複数の表をまとめて変えるときは、呼び出し側で db.withTransaction を使う。
 * 支払いを消すと split_sheets・share_groups・whole_shares・payment_payers は外部キーの CASCADE で消える。
 */
@Dao
interface SplitDao {
    // 人
    @Query("SELECT * FROM persons ORDER BY isSelf DESC, sortOrder, id")
    fun observePersons(): Flow<List<Person>>

    @Query("SELECT * FROM persons ORDER BY isSelf DESC, sortOrder, id")
    suspend fun getPersons(): List<Person>

    @Query("SELECT * FROM persons WHERE isSelf = 1 ORDER BY id LIMIT 1")
    suspend fun getSelf(): Person?

    @Query("SELECT * FROM persons WHERE uid = :uid LIMIT 1")
    suspend fun getPersonByUid(uid: String): Person?

    @Insert
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    // 割り勘シート
    @Query("SELECT * FROM split_sheets WHERE receiptId = :receiptId LIMIT 1")
    suspend fun getSheet(receiptId: Long): SplitSheet?

    @Query("SELECT * FROM split_sheets")
    suspend fun getAllSheets(): List<SplitSheet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSheet(sheet: SplitSheet)

    // 品目のまとまり
    @Query("SELECT * FROM share_groups WHERE receiptId = :receiptId ORDER BY sortOrder, id")
    suspend fun getGroups(receiptId: Long): List<ShareGroup>

    @Query("SELECT * FROM share_groups ORDER BY receiptId, sortOrder, id")
    suspend fun getAllGroups(): List<ShareGroup>

    @Insert
    suspend fun insertGroup(group: ShareGroup): Long

    @Update
    suspend fun updateGroup(group: ShareGroup)

    @Delete
    suspend fun deleteGroup(group: ShareGroup)

    @Query(
        "SELECT share_group_items.* FROM share_group_items " +
            "INNER JOIN share_groups ON share_groups.id = share_group_items.groupId " +
            "WHERE share_groups.receiptId = :receiptId",
    )
    suspend fun getGroupItems(receiptId: Long): List<ShareGroupItem>

    @Query("SELECT * FROM share_group_items")
    suspend fun getAllGroupItems(): List<ShareGroupItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupItems(items: List<ShareGroupItem>)

    @Query("DELETE FROM share_group_items WHERE receiptItemId = :receiptItemId")
    suspend fun deleteGroupItemByItem(receiptItemId: Long)

    // 品目ごと方式の割当
    @Query(
        "SELECT item_shares.* FROM item_shares " +
            "INNER JOIN share_groups ON share_groups.id = item_shares.groupId " +
            "WHERE share_groups.receiptId = :receiptId",
    )
    suspend fun getItemShares(receiptId: Long): List<ItemShare>

    @Query("SELECT * FROM item_shares")
    suspend fun getAllItemShares(): List<ItemShare>

    @Query("DELETE FROM item_shares WHERE groupId = :groupId")
    suspend fun deleteItemSharesOfGroup(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemShares(shares: List<ItemShare>)

    // 全体方式の割当
    @Query("SELECT * FROM whole_shares WHERE receiptId = :receiptId")
    suspend fun getWholeShares(receiptId: Long): List<WholeShare>

    @Query("SELECT * FROM whole_shares")
    suspend fun getAllWholeShares(): List<WholeShare>

    @Query("DELETE FROM whole_shares WHERE receiptId = :receiptId")
    suspend fun deleteWholeShares(receiptId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWholeShares(shares: List<WholeShare>)

    // 支払内訳
    @Query("SELECT * FROM payment_payers WHERE receiptId = :receiptId ORDER BY id")
    suspend fun getPayers(receiptId: Long): List<PaymentPayer>

    @Query("SELECT * FROM payment_payers ORDER BY id")
    suspend fun getAllPayers(): List<PaymentPayer>

    @Query("DELETE FROM payment_payers WHERE receiptId = :receiptId")
    suspend fun deletePayers(receiptId: Long)

    @Insert
    suspend fun insertPayers(payers: List<PaymentPayer>)
}
