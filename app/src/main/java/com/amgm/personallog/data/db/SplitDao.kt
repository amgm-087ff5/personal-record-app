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
import com.amgm.personallog.data.model.SplitLabelAmount
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.model.SplitSheet
import com.amgm.personallog.data.model.WholeShare
import kotlinx.coroutines.flow.Flow

/**
 * 割り勘(DB v11)の表の読み書き。複数の表をまとめて変えるときは、呼び出し側で db.withTransaction を使う。
 * 支払いを消すと split_sheets・split_participants・split_label_amounts・share_groups・whole_shares・payment_payers は
 * 外部キーの CASCADE で消える。参加者を消すと、その参加者の item_shares・whole_shares・payment_payers も消える。
 */
@Dao
interface SplitDao {
    // 人(登録メンバー)
    @Query("SELECT * FROM persons ORDER BY isSelf DESC, sortOrder, id")
    fun observePersons(): Flow<List<Person>>

    @Query("SELECT * FROM persons ORDER BY isSelf DESC, sortOrder, id")
    suspend fun getPersons(): List<Person>

    @Query("SELECT * FROM persons WHERE isSelf = 1 ORDER BY id LIMIT 1")
    suspend fun getSelf(): Person?

    @Query("SELECT * FROM persons WHERE uid = :uid LIMIT 1")
    suspend fun getPersonByUid(uid: String): Person?

    @Query("SELECT * FROM persons WHERE name = :name AND isSelf = 0 ORDER BY id LIMIT 1")
    suspend fun getPersonByName(name: String): Person?

    @Query("SELECT COUNT(*) FROM split_participants WHERE personId = :personId")
    suspend fun countParticipantsOfPerson(personId: Long): Int

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

    @Query("SELECT * FROM split_sheets")
    fun observeSheets(): Flow<List<SplitSheet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSheet(sheet: SplitSheet)

    @Query("UPDATE split_sheets SET warnings = :warnings WHERE receiptId = :receiptId")
    suspend fun setWarnings(receiptId: Long, warnings: String)

    // 参加者
    @Query("SELECT * FROM split_participants WHERE receiptId = :receiptId ORDER BY sortOrder, id")
    suspend fun getParticipants(receiptId: Long): List<SplitParticipant>

    @Query("SELECT * FROM split_participants ORDER BY receiptId, sortOrder, id")
    suspend fun getAllParticipants(): List<SplitParticipant>

    @Insert
    suspend fun insertParticipant(participant: SplitParticipant): Long

    @Update
    suspend fun updateParticipant(participant: SplitParticipant)

    @Query("DELETE FROM split_participants WHERE id IN (:ids)")
    suspend fun deleteParticipantsByIds(ids: List<Long>)

    // ラベルごとの金額(全体方式・金額)
    @Query("SELECT * FROM split_label_amounts WHERE receiptId = :receiptId")
    suspend fun getLabelAmounts(receiptId: Long): List<SplitLabelAmount>

    @Query("SELECT * FROM split_label_amounts")
    suspend fun getAllLabelAmounts(): List<SplitLabelAmount>

    @Query("DELETE FROM split_label_amounts WHERE receiptId = :receiptId")
    suspend fun deleteLabelAmounts(receiptId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabelAmounts(rows: List<SplitLabelAmount>)

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

    /** 分けるとき: 品目を元のまとまりから新しいまとまりへ移す(品目の id は一意なので衝突しない)。 */
    @Query("UPDATE share_group_items SET groupId = :toGroupId WHERE groupId = :fromGroupId AND receiptItemId IN (:itemIds)")
    suspend fun moveItems(fromGroupId: Long, toGroupId: Long, itemIds: List<Long>)

    /** まとめるとき: 消える側のまとまりの品目を、残す先へ全部移す。 */
    @Query("UPDATE share_group_items SET groupId = :toGroupId WHERE groupId IN (:fromGroupIds)")
    suspend fun moveAllItems(fromGroupIds: List<Long>, toGroupId: Long)

    @Query("DELETE FROM item_shares WHERE groupId = :groupId")
    suspend fun deleteItemSharesOfGroup(groupId: Long)

    @Query("DELETE FROM item_shares WHERE groupId IN (:groupIds)")
    suspend fun deleteItemSharesOfGroups(groupIds: List<Long>)

    @Query("DELETE FROM share_groups WHERE id IN (:groupIds)")
    suspend fun deleteGroupsByIds(groupIds: List<Long>)

    // 品目ごと方式の割当
    @Query(
        "SELECT item_shares.* FROM item_shares " +
            "INNER JOIN share_groups ON share_groups.id = item_shares.groupId " +
            "WHERE share_groups.receiptId = :receiptId",
    )
    suspend fun getItemShares(receiptId: Long): List<ItemShare>

    @Query("SELECT * FROM item_shares")
    suspend fun getAllItemShares(): List<ItemShare>

    @Query("DELETE FROM item_shares WHERE groupId IN (SELECT id FROM share_groups WHERE receiptId = :receiptId)")
    suspend fun deleteItemSharesOfReceipt(receiptId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemShares(shares: List<ItemShare>)

    // 全体方式の入力
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
