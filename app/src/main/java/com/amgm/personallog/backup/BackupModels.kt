package com.amgm.personallog.backup

import com.amgm.personallog.data.model.DiaryEntry
import com.amgm.personallog.data.model.DiaryPhoto
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.domain.PricedLine
import com.amgm.personallog.domain.TaxCalc
import com.amgm.personallog.domain.legacyReceiptOwnYen
import com.amgm.personallog.receipt.LegacyRow
import com.amgm.personallog.receipt.ReceiptUnits
import com.amgm.personallog.receipt.UnitRow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** バックアップZIPのフォーマット版。互換性を壊す変更をしたら上げる。 */
const val BACKUP_FORMAT_VERSION = 3
const val BACKUP_DATA_ENTRY = "data.json"
const val BACKUP_FILES_PREFIX = "files/"

val BackupJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

/** data.json の中身。設定(APIキー等)は含めない。 */
@Serializable
data class BackupData(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val dbVersion: Int = 1,
    val exportedAt: Long = 0,
    val diaryEntries: List<BackupDiaryEntry> = emptyList(),
    val diaryPhotos: List<BackupDiaryPhoto> = emptyList(),
    val receipts: List<BackupReceipt> = emptyList(),
    val receiptItems: List<BackupReceiptItem> = emptyList(),
    val expenses: List<BackupExpense> = emptyList(),
)

@Serializable
data class BackupDiaryEntry(val id: Long, val date: String, val text: String, val createdAt: Long, val updatedAt: Long) {
    fun toEntity() = DiaryEntry(id, date, text, createdAt, updatedAt)
    companion object {
        fun from(e: DiaryEntry) = BackupDiaryEntry(e.id, e.date, e.text, e.createdAt, e.updatedAt)
    }
}

@Serializable
data class BackupDiaryPhoto(val id: Long, val entryId: Long, val filePath: String, val takenAt: Long? = null, val sortOrder: Int = 0) {
    fun toEntity() = DiaryPhoto(id, entryId, filePath, takenAt, sortOrder)
    companion object {
        fun from(e: DiaryPhoto) = BackupDiaryPhoto(e.id, e.entryId, e.filePath, e.takenAt, e.sortOrder)
    }
}

@Serializable
data class BackupReceipt(
    val id: Long,
    val purchasedAt: String,
    val storeName: String,
    val totalAmount: Int,
    val imagePath: String,
    val rawJson: String,
    val status: String,
    val createdAt: Long,
    /** v2で追加。古いバックアップには無いので既定値(false)になる。 */
    val isAdvance: Boolean = false,
    /** v3で追加(レシート全体の自分の分・円)。無い古いバックアップは品目の値から求める。 */
    val ownAmount: Int? = null,
) {
    /** items はこのレシートの品目(旧形式の自分の分を集計するため)。 */
    fun toEntity(items: List<BackupReceiptItem> = emptyList()): Receipt {
        val own = ownAmount ?: legacyReceiptOwnYen(
            items.flatMap { it.toUnitRows() }.map {
                PricedLine(it.amount, it.taxRate, it.priceIncludesTax, it.ownAmount)
            },
        )
        return Receipt(id, purchasedAt, storeName, totalAmount, imagePath, rawJson, status, createdAt, isAdvance = isAdvance, ownAmount = own)
    }
    companion object {
        fun from(e: Receipt) = BackupReceipt(e.id, e.purchasedAt, e.storeName, e.totalAmount, e.imagePath, e.rawJson, e.status, e.createdAt, e.isAdvance, e.ownAmount)
    }
}

@Serializable
data class BackupReceiptItem(
    val id: Long,
    val receiptId: Long,
    val name: String,
    val memo: String = "",
    val amount: Int = 0,
    val category: String,
    val taxRate: Int = 10,
    val priceIncludesTax: Boolean = true,
    // v2まで: 品目ごとの自分の分。v3以降は常に null(レシート全体の ownAmount を使う)。
    val ownAmount: Int? = null,
    // 旧形式(v3まで)の項目。読み込み時に1個ずつの行へ変換する。新形式では null のまま出力される。
    val quantity: Double? = null,
    val unitPrice: Int? = null,
    val ownPercent: Int? = null,
) {
    /** 1個ずつの行に変換する。旧形式の数量は1個ずつの行に展開する。 */
    fun toUnitRows(): List<UnitRow> {
        if (quantity == null && ownPercent == null) {
            return listOf(UnitRow(name, amount, category, TaxCalc.normalizeTaxRate(taxRate), priceIncludesTax, ownAmount, memo))
        }
        return ReceiptUnits.fromLegacy(
            LegacyRow(
                name = name,
                quantity = quantity ?: 1.0,
                amount = amount,
                category = category,
                taxRate = taxRate,
                priceIncludesTax = priceIncludesTax,
                ownPercent = ownPercent ?: 100,
            ),
        )
    }

    /** 取り込み用の品目に変換する。 */
    fun toEntities(): List<ReceiptItem> = toUnitRows().map {
        ReceiptItem(
            receiptId = receiptId, name = it.name, amount = it.amount, category = it.category,
            taxRate = it.taxRate, priceIncludesTax = it.priceIncludesTax, memo = memo,
        )
    }

    companion object {
        fun from(e: ReceiptItem) = BackupReceiptItem(
            id = e.id, receiptId = e.receiptId, name = e.name, memo = e.memo, amount = e.amount, category = e.category,
            taxRate = e.taxRate, priceIncludesTax = e.priceIncludesTax,
        )
    }
}

@Serializable
data class BackupExpense(
    val id: Long,
    val date: String,
    val amount: Int,
    val category: String,
    val memo: String = "",
    val receiptId: Long? = null,
    val createdAt: Long,
) {
    fun toEntity() = Expense(id, date, amount, category, memo, receiptId, createdAt)
    companion object {
        fun from(e: Expense) = BackupExpense(e.id, e.date, e.amount, e.category, e.memo, e.receiptId, e.createdAt)
    }
}

enum class ImportMode { MERGE, REPLACE }

/** 進捗表示用。total が 0 以下なら不定。 */
data class BackupProgress(val message: String, val current: Int = 0, val total: Int = 0)

data class ExportResult(val diaryEntries: Int, val receipts: Int, val expenses: Int, val files: Int)

data class ImportResult(
    val diaryEntries: Int,
    val diaryPhotos: Int,
    val receipts: Int,
    val expenses: Int,
    val skipped: Int,
    val files: Int,
)

/** ユーザーに理由を見せてよい失敗。 */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
