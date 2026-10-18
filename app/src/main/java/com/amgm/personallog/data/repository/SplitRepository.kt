package com.amgm.personallog.data.repository

import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.db.ReceiptDao
import com.amgm.personallog.data.db.SplitDao
import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.SplitLabelAmount
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.model.SplitSheet
import com.amgm.personallog.data.model.WholeShare
import com.amgm.personallog.domain.split.ItemYen
import com.amgm.personallog.domain.split.SplitInput
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** まとまりと、そこに入っている品目の id。 */
data class GroupWithItems(val group: ShareGroup, val itemIds: List<Long>)

/**
 * 1つの支払いの割り勘シートの中身(画面の編集と計算の両方が使う)。
 * id が 0 以下の参加者・まとまりは未保存(画面で追加したもの)。保存時に新しい id に付け替え、割当の participantId / groupId も付け替える。
 * participants は sortOrder 順で、自分(personId = 自分の人)を必ず含む。
 */
data class SplitSheetData(
    val receiptId: Long,
    val mode: String,
    val wholeKind: String,
    val participants: List<SplitParticipant>,
    val groups: List<GroupWithItems>,
    val itemShares: List<ItemShare>,
    val wholeShares: List<WholeShare>,
    val labelAmounts: List<SplitLabelAmount>,
    /** 画面表示用: 参加者 id → 登録メンバー(personId のある参加者だけ)。 */
    val persons: Map<Long, Person> = emptyMap(),
)

/** 計算の入力と、家計簿へ配り直すための品目の金額。selfParticipantId は自分の参加者 id。 */
data class SplitBuild(val input: SplitInput, val itemYen: List<ItemYen>, val selfParticipantId: Long)

/**
 * SplitSheetData を計算の入力にする(純粋な変換。画面のプレビューと家計簿の両方が使う)。実装はタスク B2。
 * total は支払いの合計(null なら品目の合計)、items は支払いの品目(ReceiptItem)。品目の税込円は TaxCalc.paidYenPerItem で求める。
 */
fun SplitSheetData.toBuild(total: Int?, items: List<ReceiptItem>): SplitBuild = TODO("B2")

/** 割り勘の表の読み書き。家計簿の更新は ReceiptRepository が行う(ここからは呼ばない)。実装はタスク B2(ひな形の TODO を置き換える)。 */
@Singleton
class SplitRepository @Inject constructor(
    private val db: AppDatabase,
    private val splitDao: SplitDao,
    private val receiptDao: ReceiptDao,
) {
    /** 全支払いの割り勘シート(家計簿の一覧で警告のバッジを出すのに使う)。 */
    fun observeSheets(): Flow<List<SplitSheet>> = TODO("B2")

    /** その支払いのシートを読む。無ければ既定(自分だけ・自分の参加者1人)。品目のまとまりは ensureGroups してから返す。 */
    suspend fun load(receiptId: Long): SplitSheetData = TODO("B2")

    /**
     * 保存する(1つのトランザクション)。名前があって personId の無い参加者は人として登録して紐付ける(同名の人がいればその人)。
     * 名前の無い参加者は登録しない。消えた参加者・割当は消す。保存後の内容(id 確定)を返す。
     * 名前は SplitParticipant に列が無いので names(参加者 id → 入力された名前)で渡す。
     */
    suspend fun save(data: SplitSheetData, names: Map<Long, String>, now: Long = System.currentTimeMillis()): SplitSheetData = TODO("B2")

    /** 品目をまとまりに入れる(同じ品名・1個の金額・税率・税込区分なら同じまとまり)。空になったまとまりは消す。sharedHints は品目 id → 共通費か。 */
    suspend fun ensureGroups(receiptId: Long, sharedHints: Map<Long, Boolean> = emptyMap()): Unit = TODO("B2")

    /** 家計簿用: 割り勘があれば計算の入力を返す。シートが無い・自分だけなら null(従来どおり全額が自分)。 */
    suspend fun buildFor(receiptId: Long, total: Int?, items: List<ReceiptItem>): SplitBuild? = TODO("B2")

    /** 直近の計算の警告(例 "W1,W3")を保存する。 */
    suspend fun setWarnings(receiptId: Long, codes: String): Unit = TODO("B2")
}

/** 登録メンバー(人)。実装はタスク B2。 */
@Singleton
class PersonRepository @Inject constructor(
    private val splitDao: SplitDao,
) {
    /** 自分以外の登録メンバー(名前入力の候補・「メンバーを選ぶ」に出す)。 */
    fun observeMembers(): Flow<List<Person>> = TODO("B2")

    /** 自分の人(無ければ作る)。 */
    suspend fun self(now: Long = System.currentTimeMillis()): Person = TODO("B2")

    /** 名前で探し、無ければ登録する(前後の空白は除く。空の名前は不可)。 */
    suspend fun findOrCreate(name: String, now: Long = System.currentTimeMillis()): Person = TODO("B2")

    suspend fun rename(person: Person, name: String): Unit = TODO("B2")

    /** 削除する。どこかの支払いの参加者として使われていれば消さずに false。 */
    suspend fun delete(person: Person): Boolean = TODO("B2")
}
