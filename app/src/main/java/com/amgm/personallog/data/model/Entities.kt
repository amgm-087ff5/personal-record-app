package com.amgm.personallog.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val totalAmount: Int,
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
)

object ReceiptStatus {
    const val DRAFT = "draft"
    const val CONFIRMED = "confirmed"
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
)

/**
 * 1回の支払い(レシートなど)をまとめる親。amount は支払い金額(店への払い総額。確定値で、正)。
 * 品目は Expense.groupId で紐づく。品目の合計と amount がずれる場合は表示で差額を示す。
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
    val amount: Int,
    val category: String = Categories.UNCATEGORIZED,
    val memo: String = "",
    val receiptId: Long? = null,
    val createdAt: Long,
)

/**
 * 家計簿の1行。amount は円で、支出は正の値。receiptId はレシート由来の場合のみ。
 * groupId は支払いグループの品目の場合のみ(単独の支出は null)。
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
    ],
    indices = [Index("receiptId"), Index("date"), Index("groupId")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val amount: Int,
    val category: String = Categories.UNCATEGORIZED,
    val memo: String = "",
    val receiptId: Long? = null,
    val createdAt: Long,
    val groupId: Long? = null,
)

/** 月別カテゴリ集計の結果行(Roomのクエリ結果としても使う)。 */
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
