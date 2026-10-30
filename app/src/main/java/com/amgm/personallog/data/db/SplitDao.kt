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
    @Query("SELECT * FROM split_sheets WHERE paymentId = :paymentId LIMIT 1")
    suspend fun getSheet(paymentId: Long): SplitSheet?

    @Query("SELECT * FROM split_sheets")
    suspend fun getAllSheets(): List<SplitSheet>

    @Query("SELECT * FROM split_sheets")
    fun observeSheets(): Flow<List<SplitSheet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSheet(sheet: SplitSheet)

    @Query("UPDATE split_sheets SET warnings = :warnings WHERE paymentId = :paymentId")
    suspend fun setWarnings(paymentId: Long, warnings: String)

    // 参加者
    @Query("SELECT * FROM split_participants WHERE paymentId = :paymentId ORDER BY sortOrder, id")
    suspend fun getParticipants(paymentId: Long): List<SplitParticipant>

    @Query("SELECT * FROM split_participants ORDER BY paymentId, sortOrder, id")
    suspend fun getAllParticipants(): List<SplitParticipant>

    @Insert
    suspend fun insertParticipant(participant: SplitParticipant): Long

    @Update
    suspend fun updateParticipant(participant: SplitParticipant)

    @Query("DELETE FROM split_participants WHERE id IN (:ids)")
    suspend fun deleteParticipantsByIds(ids: List<Long>)

    // ラベルごとの金額(全体方式・金額)
    @Query("SELECT * FROM split_label_amounts WHERE paymentId = :paymentId")
    suspend fun getLabelAmounts(paymentId: Long): List<SplitLabelAmount>

    @Query("SELECT * FROM split_label_amounts")
    suspend fun getAllLabelAmounts(): List<SplitLabelAmount>

    @Query("DELETE FROM split_label_amounts WHERE paymentId = :paymentId")
    suspend fun deleteLabelAmounts(paymentId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabelAmounts(rows: List<SplitLabelAmount>)

    // 品目のまとまり
    @Query("SELECT * FROM share_groups WHERE paymentId = :paymentId ORDER BY sortOrder, id")
    suspend fun getGroups(paymentId: Long): List<ShareGroup>

    @Query("SELECT * FROM share_groups ORDER BY paymentId, sortOrder, id")
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
            "WHERE share_groups.paymentId = :paymentId",
    )
    suspend fun getGroupItems(paymentId: Long): List<ShareGroupItem>

    @Query("SELECT * FROM share_group_items")
    suspend fun getAllGroupItems(): List<ShareGroupItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupItems(items: List<ShareGroupItem>)

    @Query("DELETE FROM share_group_items WHERE paymentItemId = :paymentItemId")
    suspend fun deleteGroupItemByItem(paymentItemId: Long)

    /** 分けるとき: 品目を元のまとまりから新しいまとまりへ移す(品目の id は一意なので衝突しない)。 */
    @Query("UPDATE share_group_items SET groupId = :toGroupId WHERE groupId = :fromGroupId AND paymentItemId IN (:itemIds)")
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
            "WHERE share_groups.paymentId = :paymentId",
    )
    suspend fun getItemShares(paymentId: Long): List<ItemShare>

    @Query("SELECT * FROM item_shares")
    suspend fun getAllItemShares(): List<ItemShare>

    @Query("DELETE FROM item_shares WHERE groupId IN (SELECT id FROM share_groups WHERE paymentId = :paymentId)")
    suspend fun deleteItemSharesOfPayment(paymentId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemShares(shares: List<ItemShare>)

    // 全体方式の入力
    @Query("SELECT * FROM whole_shares WHERE paymentId = :paymentId")
    suspend fun getWholeShares(paymentId: Long): List<WholeShare>

    @Query("SELECT * FROM whole_shares")
    suspend fun getAllWholeShares(): List<WholeShare>

    @Query("DELETE FROM whole_shares WHERE paymentId = :paymentId")
    suspend fun deleteWholeShares(paymentId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWholeShares(shares: List<WholeShare>)

    // 支払内訳
    @Query("SELECT * FROM payment_payers WHERE paymentId = :paymentId ORDER BY id")
    suspend fun getPayers(paymentId: Long): List<PaymentPayer>

    @Query("SELECT * FROM payment_payers ORDER BY id")
    suspend fun getAllPayers(): List<PaymentPayer>

    @Query("DELETE FROM payment_payers WHERE paymentId = :paymentId")
    suspend fun deletePayers(paymentId: Long)

    @Insert
    suspend fun insertPayers(payers: List<PaymentPayer>)

    // 支払いのマージ(中身の付け替え)。share_group_items / item_shares は親の id にぶら下がるので、親の paymentId を付け替えれば一緒に移る

    /** 割り勘の入力(シートか参加者)があるか。 */
    @Query(
        "SELECT (SELECT COUNT(*) FROM split_sheets WHERE paymentId = :paymentId) + " +
            "(SELECT COUNT(*) FROM split_participants WHERE paymentId = :paymentId)",
    )
    suspend fun countSplitRows(paymentId: Long): Int

    @Query("DELETE FROM split_sheets WHERE paymentId = :paymentId")
    suspend fun deleteSheet(paymentId: Long)

    /** 参加者を消すと、その参加者の item_shares・whole_shares・payment_payers も CASCADE で消える。 */
    @Query("DELETE FROM split_participants WHERE paymentId = :paymentId")
    suspend fun deleteParticipantsOfPayment(paymentId: Long)

    /** まとまりを消すと、share_group_items・item_shares も CASCADE で消える。 */
    @Query("DELETE FROM share_groups WHERE paymentId = :paymentId")
    suspend fun deleteGroupsOfPayment(paymentId: Long)

    @Query("UPDATE split_sheets SET paymentId = :toPaymentId WHERE paymentId = :fromPaymentId")
    suspend fun moveSheet(fromPaymentId: Long, toPaymentId: Long)

    @Query("UPDATE split_participants SET paymentId = :toPaymentId WHERE paymentId = :fromPaymentId")
    suspend fun moveParticipants(fromPaymentId: Long, toPaymentId: Long)

    @Query("UPDATE split_label_amounts SET paymentId = :toPaymentId WHERE paymentId = :fromPaymentId")
    suspend fun moveLabelAmounts(fromPaymentId: Long, toPaymentId: Long)

    @Query("UPDATE share_groups SET paymentId = :toPaymentId WHERE paymentId = :fromPaymentId")
    suspend fun moveGroups(fromPaymentId: Long, toPaymentId: Long)

    @Query("UPDATE whole_shares SET paymentId = :toPaymentId WHERE paymentId = :fromPaymentId")
    suspend fun moveWholeShares(fromPaymentId: Long, toPaymentId: Long)

    @Query("UPDATE payment_payers SET paymentId = :toPaymentId WHERE paymentId = :fromPaymentId")
    suspend fun movePayers(fromPaymentId: Long, toPaymentId: Long)
}
