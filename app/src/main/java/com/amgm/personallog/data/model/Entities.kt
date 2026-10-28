package com.amgm.personallog.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** 日記(1日1件)。date は "yyyy-MM-dd"。 */
@Entity(tableName = "diary_entries", indices = [Index(value = ["date"], unique = true)])
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 日記に紐づく写真。filePath はアプリ内部ストレージ(filesDir)からの相対パス。 */
@Entity(
    tableName = "diary_photos",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class DiaryPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val filePath: String,
    val takenAt: Long? = null,
    val sortOrder: Int = 0,
)

/** レシート。purchasedAt は "yyyy-MM-dd" または "yyyy-MM-dd HH:mm"。status は ReceiptStatus。 */
@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchasedAt: String,
    val storeName: String,
    /** 合計(円・レシートの印字)。null は未入力で、下書きのときだけ許す(確定には必須)。 */
    val totalAmount: Int?,
    val imagePath: String,
    /** Claude応答の原文(JSON文字列) */
    val rawJson: String,
    val status: String = ReceiptStatus.DRAFT,
    val createdAt: Long,
    /** 原本画像(imagePath)のSHA-256(16進小文字)。重複検知用。 */
    val imageHash: String? = null,
    /** 立て替え: 自分が払った(他の人の分を含む)レシートなら true。 */
    @ColumnInfo(defaultValue = "0")
    val isAdvance: Boolean = false,
    /** 立て替えのとき、レシート全体のうち自分が払う金額(円・税込)。null は支払総額の全額。 */
    val ownAmount: Int? = null,
    /** 合計の出所。TotalSource の値。 */
    @ColumnInfo(defaultValue = "'unknown'")
    val totalSource: String = TotalSource.UNKNOWN,
    /** 画像の回転(時計回りの度)。90度単位の回転と微調整の合計。 */
    @ColumnInfo(defaultValue = "0")
    val rotationDegrees: Float = 0f,
    /** トリミング範囲(回転後の画像に対する0〜1の比率)。 */
    @ColumnInfo(defaultValue = "0")
    val cropLeft: Float = 0f,
    @ColumnInfo(defaultValue = "0")
    val cropTop: Float = 0f,
    @ColumnInfo(defaultValue = "1")
    val cropRight: Float = 1f,
    @ColumnInfo(defaultValue = "1")
    val cropBottom: Float = 1f,
    /** 参加人数(自分を含む)。レシートの席料・注文タブレット等から読めた人数。不明は null（割り勘の既定は1人）。 */
    val headcount: Int? = null,
)

object ReceiptStatus {
    const val DRAFT = "draft"
    const val CONFIRMED = "confirmed"
}

/** Receipt.totalSource の値。合計がどこから来たか。 */
object TotalSource {
    /** レシートの印字をLLMが読んだ値 */
    const val PRINTED = "printed"

    /** 品目の計算合計から入れた値 */
    const val ITEMS = "items"

    /** 手入力した値 */
    const val MANUAL = "manual"

    /** 不明(移行前のデータなど) */
    const val UNKNOWN = "unknown"
}

/**
 * 「重複ではない」と判断したレシートの組。小さい id を receiptIdLow に正規化して持つ。
 * どちらかのレシートを削除すると組も消える(外部キーの CASCADE)。
 */
@Entity(
    tableName = "receipt_not_duplicates",
    primaryKeys = ["receiptIdLow", "receiptIdHigh"],
    foreignKeys = [
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptIdLow"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptIdHigh"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("receiptIdHigh")],
)
data class ReceiptNotDuplicate(
    val receiptIdLow: Long,
    val receiptIdHigh: Long,
    val createdAt: Long,
) {
    companion object {
        /** 2つの id から正規化した組を作る(小さい方が Low)。同じ id なら null。 */
        fun of(a: Long, b: Long, createdAt: Long): ReceiptNotDuplicate? =
            if (a == b) null else ReceiptNotDuplicate(minOf(a, b), maxOf(a, b), createdAt)
    }
}

/**
 * レシートの品目。1行 = 1個ぶん(数量は持たず、N個は N 行に分ける)。
 * 同じ品名・金額などの行は表示時にまとめる(データにグループは持たない)。
 */
@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("receiptId")],
)
data class ReceiptItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val name: String,
    /** 1個ぶんの印字金額。税込/税抜は priceIncludesTax で決まる。値引きは負の値。 */
    val amount: Int = 0,
    val category: String = Categories.UNCATEGORIZED,
    /** 消費税率(%)。0〜100の整数。 */
    @ColumnInfo(defaultValue = "10")
    val taxRate: Int = 10,
    /** amount が税込か。false なら税率から税込価格を計算する。 */
    @ColumnInfo(defaultValue = "1")
    val priceIncludesTax: Boolean = true,
    /** この1個へのメモ。 */
    val memo: String = "",
    /**
     * 金額が分からない品目(「不明」)。true なら amount は計算に使わない。
     * 不明の金額 = 合計 − 分かっている品目の合計(UnknownAmounts で求める。計算の正は合計額)。
     */
    @ColumnInfo(defaultValue = "0")
    val unknownAmount: Boolean = false,
)

/**
 * 自分の支出(支払い1件に対する自分の負担)。amount は自分の負担額(確定値で、正)。
 * 品目は Expense.groupId で紐づき、必ず1行以上ある(品目の無い支払いは、品名が空の1品目を持つ)。
 * 品目が1つのとき、その品目の金額は amount と同じ。品目の合計と amount がずれる場合は表示で差額を示す。
 */
@Entity(
    tableName = "expense_groups",
    foreignKeys = [
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("receiptId"), Index("date")],
)
data class ExpenseGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    /** 支払った時刻 "HH:mm"。レシートの日時に従う。 */
    @ColumnInfo(defaultValue = "''")
    val time: String = "",
    val amount: Int,
    val category: String = Categories.UNCATEGORIZED,
    val memo: String = "",
    val receiptId: Long? = null,
    val createdAt: Long,
    /** 端末をまたいで変わらない識別子(バックアップの復元時の照合キー)。 */
    @ColumnInfo(defaultValue = "''")
    val uid: String = newUid(),
    /** 金額・カテゴリ・メモ・日時を支払い側で変えたか。false の間だけ、レシートの再確定に追従する。 */
    @ColumnInfo(defaultValue = "0")
    val amountEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val categoryEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val memoEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val timeEdited: Boolean = false,
)

/** 新しい uid(UUID)。 */
fun newUid(): String = UUID.randomUUID().toString()

/**
 * 支出の品目(自分の支出 expense_groups の子)。amount は円で、支出は正の値。receiptId は支払い由来の場合のみ。
 * groupId は必ず入る(v14 で「単独の支出」= groupId なし をやめた)。null は v13 以前の古い行と、取り込み前の旧形式だけ。
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ExpenseGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReceiptItem::class,
            parentColumns = ["id"],
            childColumns = ["receiptItemId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("receiptId"), Index("date"), Index("groupId"), Index("receiptItemId")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    /** 支払った時刻 "HH:mm"(必須)。レシート由来の初期値はレシートの時刻。 */
    @ColumnInfo(defaultValue = "''")
    val time: String = "",
    val amount: Int,
    val category: String = Categories.UNCATEGORIZED,
    val memo: String = "",
    val receiptId: Long? = null,
    val createdAt: Long,
    val groupId: Long? = null,
    /**
     * 日時(date/time)を支出側で変更したか。false の間だけ、紐づくレシートの日時変更に追従する。
     */
    @ColumnInfo(defaultValue = "0")
    val timeEdited: Boolean = false,
    /** 元になったレシートの品目。レシートの再確定で、同じ品目の行を探すのに使う。手入力・古い行は null。 */
    val receiptItemId: Long? = null,
    /** 品名。 */
    @ColumnInfo(defaultValue = "''")
    val name: String = "",
    /** 金額・カテゴリ・品名・メモを支払い側で変えたか。false の間だけ、レシートの再確定に追従する。 */
    @ColumnInfo(defaultValue = "0")
    val amountEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val categoryEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val nameEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val memoEdited: Boolean = false,
    /** 端末をまたいで変わらない識別子(バックアップの復元時の照合キー)。 */
    val uid: String = newUid(),
    /**
     * 家計簿でユーザーが消したレシート由来の品目。行は消さずにこの印を付ける(レシートの再確定で復活させないため)。
     * 一覧・集計・支払い詳細の品目からは外し、支払い詳細の「消した品目」から元に戻せる。手入力の行は本当に消す。
     */
    @ColumnInfo(defaultValue = "0")
    val deletedByUser: Boolean = false,
    /**
     * 金額が分からない品目(「不明」)。amount には、支出の金額 − 分かっている品目の合計を配った値が入る
     * (UnknownAmounts.resolveExpenseItems。計算の正は支出の金額)。
     */
    @ColumnInfo(defaultValue = "0")
    val unknownAmount: Boolean = false,
)

/** カテゴリ別の合計1行(画面の集計で使う)。 */
data class CategoryTotal(
    val category: String,
    val total: Long,
)

object Categories {
    const val UNCATEGORIZED = "未分類"
    val DEFAULTS = listOf(
        "食費", "日用品", "交通費", "外食", "医療・健康", "衣服・美容",
        "趣味・娯楽", "光熱費・通信費", "住居", "その他", UNCATEGORIZED,
    )
}
