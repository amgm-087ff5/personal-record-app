package com.amgm.personallog.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * 割り勘と「支払い/支出」の再定義で足した表(DB v10 で追加、v11 で参加者・ラベル金額を追加し、割当の参照先を参加者に変更)。
 * 用語: 「支払い」= 店への支払いの事実(payments。Payment)。品目は payment_items(PaymentItem)。支出(expenses。Expense)= 支払いに対する自分の負担、その品目は expense_items(ExpenseItem)。v17 で旧名(receipts・receipt_items・expense_groups・expenses)から改名した。
 * 方針: 入力(割り方)だけを保存し、各人の金額は domain/split の計算で毎回求める。
 * 計画: Notion「支払い・支出の再定義」 https://app.notion.com/p/3fd0e054862281bda7bdfd24fa39e0fc
 */

/**
 * 人(登録メンバー)。割り勘の名前入力の候補に出る。アプリのアカウントとは別物。
 * 自分は isSelf = true の1行だけ(DB v10 の移行で作る)。shortName はチップ表示(例: 「髙」)。
 * 参加者(SplitParticipant)から参照されている人は削除できない(外部キーで止まる)。
 */
@Entity(tableName = "persons", indices = [Index(value = ["uid"], unique = true)])
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 端末をまたいで変わらない識別子(バックアップの照合キー)。 */
    val uid: String = newUid(),
    val name: String,
    @ColumnInfo(defaultValue = "''")
    val shortName: String = "",
    @ColumnInfo(defaultValue = "0")
    val isSelf: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    val createdAt: Long,
)

/**
 * 割り勘シート。支払い(payments)に0か1。行が無ければ「自分だけ」。
 * wholeKind は全体方式の入力の種類(ShareKind.AMOUNT=金額 / RATIO=割合)。
 * warnings は直近の計算で出た警告の種類(例 "W1,W3"。空なら無し)。家計簿の一覧のバッジに使う。
 */
@Entity(
    tableName = "split_sheets",
    foreignKeys = [
        ForeignKey(entity = Payment::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SplitSheet(
    @PrimaryKey val paymentId: Long,
    /** SplitMode の値。 */
    @ColumnInfo(defaultValue = "'self'")
    val mode: String = SplitMode.SELF,
    @ColumnInfo(defaultValue = "'amount'")
    val wholeKind: String = ShareKind.AMOUNT,
    @ColumnInfo(defaultValue = "''")
    val warnings: String = "",
)

/**
 * 支払いごとの参加者(その支払いの割り勘に出てくる人)。
 * personId が null の参加者は「名前なし」(表示は「メンバー1」など。メンバー登録はしない)。
 * 名前を入れた参加者は保存時に人(Person)として登録し、personId を付ける(同じ名前の人がいればその人)。
 * 自分の参加者は personId = 自分の人の id。memo はその支払いだけの一言メモ、label はその支払いだけのラベル(例: 新人)。
 */
@Entity(
    tableName = "split_participants",
    foreignKeys = [
        ForeignKey(entity = Payment::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"]),
    ],
    indices = [Index("paymentId"), Index("personId")],
)
data class SplitParticipant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val personId: Long? = null,
    @ColumnInfo(defaultValue = "''")
    val memo: String = "",
    @ColumnInfo(defaultValue = "''")
    val label: String = "",
    /** 店に払った人か(端数の1円は支払者でない人から配る)。誰も付いていなければ自分が払ったとみなす。 */
    @ColumnInfo(defaultValue = "0")
    val isPayer: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
)

/**
 * 全体方式・金額のときの、ラベルごとの1人あたり金額(例: 新人 3000円)。
 * label = "" は「その他」(ラベルの無い参加者)。行が無いラベルは均等割(残額を割る)。
 */
@Entity(
    tableName = "split_label_amounts",
    primaryKeys = ["paymentId", "label"],
    foreignKeys = [
        ForeignKey(entity = Payment::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SplitLabelAmount(
    val paymentId: Long,
    val label: String,
    val amount: Int,
)

/**
 * 品目のまとまり。割り勘の単位。同じ品目の複数行(辛味チキン2皿)や、1行のパック(ソーセージ7個入り)を1つにする。
 * pieceCount は「N個中M個」の N(任意。割合の空欄を残りの個数で均等にするのに使う)。
 * isShared は共通費(袋代・送料など)の印。品目ごと方式で割当が無ければ参加者で均等にする。
 */
@Entity(
    tableName = "share_groups",
    foreignKeys = [
        ForeignKey(entity = Payment::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("paymentId")],
)
data class ShareGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val pieceCount: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val isShared: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
)

/** まとまりに含まれる品目。1つの品目は高々1つのまとまりに入る(paymentItemId が一意)。 */
@Entity(
    tableName = "share_group_items",
    primaryKeys = ["groupId", "paymentItemId"],
    foreignKeys = [
        ForeignKey(entity = ShareGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PaymentItem::class, parentColumns = ["id"], childColumns = ["paymentItemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["paymentItemId"], unique = true)],
)
data class ShareGroupItem(
    val groupId: Long,
    val paymentItemId: Long,
)

/**
 * 品目ごと方式の割当(まとまり × 参加者)。行がある参加者がそのまとまりの対象者。
 * kind は ShareKind。weight は RATIO(個数など。null=空欄)、amount は AMOUNT(null=空欄=残額を均等)のときに使う。
 */
@Entity(
    tableName = "item_shares",
    primaryKeys = ["groupId", "participantId"],
    foreignKeys = [
        ForeignKey(entity = ShareGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SplitParticipant::class, parentColumns = ["id"], childColumns = ["participantId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("participantId")],
)
data class ItemShare(
    val groupId: Long,
    val participantId: Long,
    val kind: String,
    val weight: Int? = null,
    val amount: Int? = null,
)

/**
 * 全体方式の入力(支払い × 参加者)。行が無い参加者は空欄(残額を均等)。
 * kind は SplitSheet.wholeKind と同じ値(AMOUNT / RATIO)。amount・weight の null は空欄。
 */
@Entity(
    tableName = "whole_shares",
    primaryKeys = ["paymentId", "participantId"],
    foreignKeys = [
        ForeignKey(entity = Payment::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SplitParticipant::class, parentColumns = ["id"], childColumns = ["participantId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("participantId")],
)
data class WholeShare(
    val paymentId: Long,
    val participantId: Long,
    val kind: String,
    val weight: Int? = null,
    val amount: Int? = null,
)

/** 支払内訳: どの参加者が・どの手段で・いくら店に払ったか(任意。無ければ isPayer の参加者が合計を払った)。 */
@Entity(
    tableName = "payment_payers",
    foreignKeys = [
        ForeignKey(entity = Payment::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SplitParticipant::class, parentColumns = ["id"], childColumns = ["participantId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("paymentId"), Index("participantId")],
)
data class PaymentPayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val participantId: Long,
    /** 支払い手段(現金・カード・QRなど。自由入力。空は不明)。 */
    @ColumnInfo(defaultValue = "''")
    val method: String = "",
    val amount: Int,
)
