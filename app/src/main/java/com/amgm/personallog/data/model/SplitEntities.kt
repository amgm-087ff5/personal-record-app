package com.amgm.personallog.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * 割り勘と「支払い/支出」の再定義(DB v10)で足す表。
 * 用語: 「支払い」= 店への支払いの事実。物理テーブルは当面 receipts(Receipt)のまま。品目は receipt_items(ReceiptItem)。
 * 方針: 入力(割り方)だけを保存し、各人の金額は domain/split の計算で毎回求める。
 * 計画: Notion「支払い・支出の再定義」 https://app.notion.com/p/3fd0e054862281bda7bdfd24fa39e0fc
 */

/** 割り勘シートの方式(SplitSheet.mode)。 */
object SplitMode {
    /** 自分だけ(既定。シートの行が無い場合も同じ)。 */
    const val SELF = "self"

    /** 支払い全体を人で割る(WholeShare)。品目は参考。 */
    const val WHOLE = "whole"

    /** 品目のまとまりごとに割る(ItemShare)。 */
    const val ITEM = "item"
}

/** 割当の種類(WholeShare.kind / ItemShare.kind)。1つのまとまり(または全体)の中では1種類に揃える。 */
object ShareKind {
    /** その人1人に全部(ItemShare のみ)。 */
    const val ASSIGN = "assign"

    /** 対象者で均等(weight・amount は使わない)。 */
    const val EQUAL = "equal"

    /** 割合。weight(個数など、1以上の整数)で比例配分する。 */
    const val RATIO = "ratio"

    /** 金額指定。amount(円)。合計とずれたら警告(自動では配り直さない)。 */
    const val AMOUNT = "amount"
}

/**
 * 人。割り勘の支払者・負担者。アプリのアカウントとは別物。
 * 自分は isSelf = true の1行だけ(DB v10 の移行で作る)。shortName は品目に割り当てるときのチップ表示(例: 「髙」)。
 * 割当(ItemShare・WholeShare・PaymentPayer)から参照されている人は削除できない(外部キーで止まる)。
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

/** 割り勘シート。支払い(receipts)に0か1。行が無ければ「自分だけ」。 */
@Entity(
    tableName = "split_sheets",
    foreignKeys = [
        ForeignKey(entity = Receipt::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SplitSheet(
    @PrimaryKey val receiptId: Long,
    /** SplitMode の値。 */
    @ColumnInfo(defaultValue = "'self'")
    val mode: String = SplitMode.SELF,
)

/**
 * 品目のまとまり。割り勘の単位。同じ品目の複数行(辛味チキン2皿)や、1行のパック(ソーセージ7個入り)を1つにする。
 * pieceCount は「N個中M個」の N(任意。割合の合計の検算と表示に使う)。
 * isShared は共通費(袋代・送料など)の印。品目ごと方式で割当が無ければ参加者で均等にする。
 */
@Entity(
    tableName = "share_groups",
    foreignKeys = [
        ForeignKey(entity = Receipt::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("receiptId")],
)
data class ShareGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val pieceCount: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val isShared: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
)

/** まとまりに含まれる品目。1つの品目は高々1つのまとまりに入る(receiptItemId が一意)。 */
@Entity(
    tableName = "share_group_items",
    primaryKeys = ["groupId", "receiptItemId"],
    foreignKeys = [
        ForeignKey(entity = ShareGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ReceiptItem::class, parentColumns = ["id"], childColumns = ["receiptItemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["receiptItemId"], unique = true)],
)
data class ShareGroupItem(
    val groupId: Long,
    val receiptItemId: Long,
)

/** 品目ごと方式の割当(まとまり × 人)。kind は ShareKind。weight は RATIO、amount は AMOUNT のときだけ使う。 */
@Entity(
    tableName = "item_shares",
    primaryKeys = ["groupId", "personId"],
    foreignKeys = [
        ForeignKey(entity = ShareGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"]),
    ],
    indices = [Index("personId")],
)
data class ItemShare(
    val groupId: Long,
    val personId: Long,
    val kind: String,
    val weight: Int? = null,
    val amount: Int? = null,
)

/** 全体方式の割当(支払い × 人)。kind は ShareKind の EQUAL / RATIO / AMOUNT。 */
@Entity(
    tableName = "whole_shares",
    primaryKeys = ["receiptId", "personId"],
    foreignKeys = [
        ForeignKey(entity = Receipt::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"]),
    ],
    indices = [Index("personId")],
)
data class WholeShare(
    val receiptId: Long,
    val personId: Long,
    val kind: String,
    val weight: Int? = null,
    val amount: Int? = null,
)

/** 支払内訳: 誰が・どの手段で・いくら店に払ったか。行が無ければ「自分が合計を払った」とみなす。 */
@Entity(
    tableName = "payment_payers",
    foreignKeys = [
        ForeignKey(entity = Receipt::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Person::class, parentColumns = ["id"], childColumns = ["personId"]),
    ],
    indices = [Index("receiptId"), Index("personId")],
)
data class PaymentPayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val personId: Long,
    /** 支払い手段(現金・カード・QRなど。自由入力。空は不明)。 */
    @ColumnInfo(defaultValue = "''")
    val method: String = "",
    val amount: Int,
)
