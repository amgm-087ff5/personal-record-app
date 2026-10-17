package com.amgm.personallog.backup

import com.amgm.personallog.data.model.DiaryEntry
import com.amgm.personallog.data.model.DiaryPhoto
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptNotDuplicate
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.data.model.newUid
import com.amgm.personallog.domain.PricedLine
import com.amgm.personallog.domain.TaxCalc
import com.amgm.personallog.domain.legacyReceiptOwnYen
import com.amgm.personallog.receipt.LegacyRow
import com.amgm.personallog.receipt.ReceiptUnits
import com.amgm.personallog.receipt.TotalSourceInit
import com.amgm.personallog.receipt.UnitRow
import com.amgm.personallog.domain.LedgerCalc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** バックアップZIPのフォーマット版。互換性を壊す変更をしたら上げる。 */
const val BACKUP_FORMAT_VERSION = 6
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
    /** v4 以降。v3 以前のファイルには無いので空として読む。 */
    val expenseGroups: List<BackupExpenseGroup> = emptyList(),
    /** v5 以降。「重複ではない」と判断したレシートの組。v4 以前のファイルには無いので空として読む。 */
    val receiptNotDuplicates: List<BackupNotDuplicate> = emptyList(),
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
    /** v5 以降は null(未入力・下書きのみ)がありうる。v4 以前は常に値がある。 */
    val totalAmount: Int? = null,
    val imagePath: String,
    val rawJson: String,
    val status: String,
    val createdAt: Long,
    /** v2で追加。古いバックアップには無いので既定値(false)になる。 */
    val isAdvance: Boolean = false,
    /** v3で追加(レシート全体の自分の分・円)。無い古いバックアップは品目の値から求める。 */
    val ownAmount: Int? = null,
    /** v5 以降。合計の出所(TotalSource)。無い古いバックアップは unknown。 */
    val totalSource: String = TotalSource.UNKNOWN,
    /** v5 以降。原本画像のSHA-256。復元(マージ)のレシート照合に使う。 */
    val imageHash: String? = null,
    /** v5 以降。画像の回転(時計回りの度)と、回転後の画像に対するトリミング範囲(0〜1)。 */
    val rotationDegrees: Float = 0f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
) {
    /**
     * items はこのレシートの品目(旧形式の自分の分を集計するため)。
     * legacyFormat: 形式4以前のバックアップ。合計と出所を TotalSourceInit で決める
     * (読み取り失敗の仮の0円を合計に戻さない。DB の v8 への移行と同じ判定)。
     */
    fun toEntity(items: List<BackupReceiptItem> = emptyList(), legacyFormat: Boolean = false): Receipt {
        val own = ownAmount ?: legacyReceiptOwnYen(
            items.flatMap { it.toUnitRows() }.map {
                PricedLine(it.amount, it.taxRate, it.priceIncludesTax, it.ownAmount)
            },
        )
        // 確定済みは合計が必須。合計の無い確定済みは下書きに戻して取り込む(家計簿を作らない)
        val init = if (legacyFormat) TotalSourceInit.decide(status, totalAmount ?: 0, rawJson) else null
        val total = if (init != null) init.totalAmount else totalAmount
        val source = init?.totalSource ?: totalSource
        val safeStatus = if (status == ReceiptStatus.CONFIRMED && total == null) ReceiptStatus.DRAFT else status
        return Receipt(
            id = id, purchasedAt = purchasedAt, storeName = storeName, totalAmount = total,
            imagePath = imagePath, rawJson = rawJson, status = safeStatus, createdAt = createdAt,
            imageHash = imageHash, isAdvance = isAdvance, ownAmount = own,
            totalSource = source,
            rotationDegrees = rotationDegrees,
            cropLeft = cropLeft, cropTop = cropTop, cropRight = cropRight, cropBottom = cropBottom,
        )
    }
    companion object {
        fun from(e: Receipt) = BackupReceipt(
            id = e.id, purchasedAt = e.purchasedAt, storeName = e.storeName, totalAmount = e.totalAmount,
            imagePath = e.imagePath, rawJson = e.rawJson, status = e.status, createdAt = e.createdAt,
            isAdvance = e.isAdvance, ownAmount = e.ownAmount,
            totalSource = e.totalSource, imageHash = e.imageHash,
            rotationDegrees = e.rotationDegrees,
            cropLeft = e.cropLeft, cropTop = e.cropTop, cropRight = e.cropRight, cropBottom = e.cropBottom,
        )
    }
}

/** 「重複ではない」の組(v5 以降)。receiptIdLow < receiptIdHigh。 */
@Serializable
data class BackupNotDuplicate(val receiptIdLow: Long, val receiptIdHigh: Long, val createdAt: Long = 0) {
    companion object {
        fun from(e: ReceiptNotDuplicate) = BackupNotDuplicate(e.receiptIdLow, e.receiptIdHigh, e.createdAt)
    }
}

/**
 * 「重複ではない」の組を、復元後のレシート id に付け替える。idMap はバックアップ内の id -> 復元後の id。
 * どちらかの対応が取れない組は捨てる。同じレシートどうしの組になったもの・重複した組も捨てる。
 */
fun remapNotDuplicates(pairs: List<BackupNotDuplicate>, idMap: Map<Long, Long>): List<ReceiptNotDuplicate> =
    pairs.mapNotNull { p ->
        val low = idMap[p.receiptIdLow] ?: return@mapNotNull null
        val high = idMap[p.receiptIdHigh] ?: return@mapNotNull null
        ReceiptNotDuplicate.of(low, high, p.createdAt)
    }.distinctBy { it.receiptIdLow to it.receiptIdHigh }

/**
 * 復元(マージ)で、バックアップのレシートが既存のどのレシートに当たるかを探す(純Kotlin)。
 * 照合キーは、imageHash があれば imageHash、無ければ従来のキー(日時・店名・合計・画像パス。合計は null もありうる)。
 * 同じキーが複数あっても1対1で対応させる(同じ画像を「それでも追加」した2枚が1枚に潰れない)。
 */
class ReceiptMergeIndex(existing: List<Receipt>) {
    private data class LegacyKey(val purchasedAt: String, val storeName: String, val totalAmount: Int?, val imagePath: String)

    private val byHash = HashMap<String, ArrayDeque<Long>>()
    private val byLegacy = HashMap<LegacyKey, ArrayDeque<Long>>()
    private val hashOf = HashMap<Long, String>()
    private val used = HashSet<Long>()

    init {
        for (r in existing) {
            r.imageHash?.takeIf { it.isNotEmpty() }?.let {
                byHash.getOrPut(it) { ArrayDeque() }.addLast(r.id)
                hashOf[r.id] = it
            }
            byLegacy.getOrPut(legacyKey(r)) { ArrayDeque() }.addLast(r.id)
        }
    }

    private fun legacyKey(r: Receipt) = LegacyKey(r.purchasedAt, r.storeName, r.totalAmount, r.imagePath)

    private fun poll(queue: ArrayDeque<Long>?, accept: (Long) -> Boolean = { true }): Long? {
        if (queue == null) return null
        val it = queue.iterator()
        while (it.hasNext()) {
            val id = it.next()
            if (id in used || !accept(id)) continue
            it.remove()
            used.add(id)
            return id
        }
        return null
    }

    /**
     * 対応する未使用の既存レシートの id。無ければ null。見つけた id は使用済みにする。
     * ハッシュがあれば、まずハッシュで探す。無ければ従来のキーでも探す
     * (既存側にハッシュが未記録の場合。既存側に別のハッシュがあるものは別の画像なので対応させない)。
     */
    fun take(r: Receipt): Long? {
        val hash = r.imageHash?.takeIf { it.isNotEmpty() }
        if (hash == null) return poll(byLegacy[legacyKey(r)])
        return poll(byHash[hash]) ?: poll(byLegacy[legacyKey(r)]) { hashOf[it] == null }
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
    /** v7 以降。"HH:mm"。古いバックアップには無いので、取り込み時に作成時刻から補う。 */
    val time: String = "",
    val amount: Int,
    val category: String,
    val memo: String = "",
    val receiptId: Long? = null,
    val createdAt: Long,
    /** v4 以降。支払いグループの品目なら、そのグループの id。 */
    val groupId: Long? = null,
    /** v7 以降。日時を支出側で変えたか。 */
    val timeEdited: Boolean = false,
    /** v6 以降。元になったレシートの品目の id(バックアップ内の id)。復元時に復元後の id へ付け替える。 */
    val receiptItemId: Long? = null,
    /** v6 以降。品名。 */
    val name: String = "",
    /** v6 以降。金額・カテゴリ・品名・メモを支払い側で変えたか。 */
    val amountEdited: Boolean = false,
    val categoryEdited: Boolean = false,
    val nameEdited: Boolean = false,
    val memoEdited: Boolean = false,
    /** v6 以降。端末をまたいで変わらない識別子。古い形式は空(取り込み時に新しく振る)。 */
    val uid: String = "",
    /** v6 以降。家計簿で消したレシート由来の品目の印。古い形式には無いので false。 */
    val deletedByUser: Boolean = false,
) {
    /** linkedTime: 紐づくレシートの時刻(レシートがあれば)。time が空のときの初期値に使う。 */
    fun toEntity(linkedTime: String? = null) = Expense(
        id = id,
        date = date,
        time = time.ifBlank { linkedTime ?: LedgerCalc.timeOfMillis(createdAt) },
        amount = amount,
        category = category,
        memo = memo,
        receiptId = receiptId,
        createdAt = createdAt,
        groupId = groupId,
        timeEdited = timeEdited,
        receiptItemId = receiptItemId,
        name = name,
        amountEdited = amountEdited,
        categoryEdited = categoryEdited,
        nameEdited = nameEdited,
        memoEdited = memoEdited,
        uid = uid.ifBlank { newUid() },
        deletedByUser = deletedByUser,
    )
    companion object {
        fun from(e: Expense) = BackupExpense(
            id = e.id, date = e.date, time = e.time, amount = e.amount, category = e.category, memo = e.memo,
            receiptId = e.receiptId, createdAt = e.createdAt, groupId = e.groupId, timeEdited = e.timeEdited,
            receiptItemId = e.receiptItemId, name = e.name,
            amountEdited = e.amountEdited, categoryEdited = e.categoryEdited, nameEdited = e.nameEdited,
            memoEdited = e.memoEdited, uid = e.uid, deletedByUser = e.deletedByUser,
        )
    }
}

/** 支払いグループ(v4 以降)。amount は支払い金額。 */
@Serializable
data class BackupExpenseGroup(
    val id: Long,
    val date: String,
    /** v7 以降。"HH:mm"。古いバックアップには無いので、取り込み時に作成時刻から補う。 */
    val time: String = "",
    val amount: Int,
    val category: String,
    val memo: String = "",
    val receiptId: Long? = null,
    val createdAt: Long,
    /** v6 以降。端末をまたいで変わらない識別子。古い形式は空(取り込み時に新しく振る)。 */
    val uid: String = "",
    /** v6 以降。金額・カテゴリ・メモ・日時を支払い側で変えたか。 */
    val amountEdited: Boolean = false,
    val categoryEdited: Boolean = false,
    val memoEdited: Boolean = false,
    val timeEdited: Boolean = false,
) {
    fun toEntity(linkedTime: String? = null) = ExpenseGroup(
        id = id,
        date = date,
        time = time.ifBlank { linkedTime ?: LedgerCalc.timeOfMillis(createdAt) },
        amount = amount,
        category = category,
        memo = memo,
        receiptId = receiptId,
        createdAt = createdAt,
        uid = uid.ifBlank { newUid() },
        amountEdited = amountEdited,
        categoryEdited = categoryEdited,
        memoEdited = memoEdited,
        timeEdited = timeEdited,
    )
    companion object {
        fun from(g: ExpenseGroup) = BackupExpenseGroup(
            id = g.id, date = g.date, time = g.time, amount = g.amount, category = g.category, memo = g.memo,
            receiptId = g.receiptId, createdAt = g.createdAt,
            uid = g.uid, amountEdited = g.amountEdited, categoryEdited = g.categoryEdited,
            memoEdited = g.memoEdited, timeEdited = g.timeEdited,
        )
    }
}

/** 支出の receiptItemId を、復元後の receipt_items の id に付け替える。対応が取れなければ null。 */
fun remapReceiptItemId(backupItemId: Long?, itemIdMap: Map<Long, Long>): Long? =
    backupItemId?.let { itemIdMap[it] }

/**
 * 復元(マージ)で、バックアップの支払いグループが既存のどれに当たるかを探す(純Kotlin)。
 * uid があれば uid で照合する。uid が無い(形式5以前の)グループは従来のキー(日付・金額・カテゴリ・メモ・レシート)で照合する。
 * uid が一致しなくても、同じレシートに属するグループ(entity.receiptId がある)は従来のキーでも照合する
 * (別の端末で確定して uid が違うだけの同じ支払いを、二重に登録しない)。
 */
class GroupMergeIndex(existing: List<ExpenseGroup>) {
    private data class Key(val date: String, val amount: Int, val category: String, val memo: String, val receiptId: Long?)

    private val byUid = HashMap<String, Long>()
    private val byKey = HashMap<Key, Long>()

    init {
        existing.forEach { register(it, it.id) }
    }

    private fun keyOf(g: ExpenseGroup) = Key(g.date, g.amount, g.category, g.memo, g.receiptId)

    private fun register(g: ExpenseGroup, id: Long) {
        if (g.uid.isNotEmpty()) byUid.putIfAbsent(g.uid, id)
        byKey.putIfAbsent(keyOf(g), id)
    }

    /**
     * backup が既存に当たれば既存の id。当たらなければ null。
     * @param entity バックアップのグループを復元後のレシート id などに直した実体(uid は backupUid を入れておく)
     */
    fun find(backupUid: String, entity: ExpenseGroup): Long? {
        if (backupUid.isEmpty()) return byKey[keyOf(entity)]
        byUid[backupUid]?.let { return it }
        // uid は違うが同じレシートの支払い: 従来のキーでも照合する
        return if (entity.receiptId != null) byKey[keyOf(entity)] else null
    }

    /** 新しく入れたグループを登録する(同じバックアップ内の重複にも効く)。 */
    fun add(entity: ExpenseGroup, newId: Long) = register(entity, newId)
}

/**
 * 復元(マージ)で、バックアップの支出が既存と重複しているかを判定する(純Kotlin)。
 * uid があれば uid で照合する。uid が無い(形式5以前の)支出は従来のキー(日付・金額・カテゴリ・メモ・レシート・グループ)の件数ベースで照合する。
 * uid が一致しなくても、同じレシートに属する支出(復元後の receiptId がある)は従来のキーの件数ベースでも照合する。
 */
class ExpenseMergeIndex(existing: List<Expense>) {
    private data class Key(
        val date: String,
        val amount: Int,
        val category: String,
        val memo: String,
        val receiptId: Long?,
        val groupId: Long?,
    )

    private val uids = HashSet<String>()
    private val remaining = HashMap<Key, Int>()

    init {
        for (e in existing) {
            if (e.uid.isNotEmpty()) uids.add(e.uid)
            val k = Key(e.date, e.amount, e.category, e.memo, e.receiptId, e.groupId)
            remaining[k] = (remaining[k] ?: 0) + 1
        }
    }

    /**
     * 重複なら true(取り込まない)。重複でなければ false を返し、同じバックアップ内の重複を防ぐため uid を使用済みにする。
     * @param mappedReceiptId / mappedGroupId 復元後の id に付け替えたもの
     */
    fun isDuplicate(e: BackupExpense, mappedReceiptId: Long?, mappedGroupId: Long?): Boolean {
        val k = Key(e.date, e.amount, e.category, e.memo, mappedReceiptId, mappedGroupId)
        if (e.uid.isNotEmpty()) {
            if (!uids.add(e.uid)) {
                // 同じ uid の既存の行に当たった。その行は従来のキーの数からも1つ引く(別の uid の行が同じ行に当たらないように)
                consume(k)
                return true
            }
            // uid は初めて。ただし同じレシートの支払いなら、従来のキーの件数でも照合する
            return mappedReceiptId != null && consume(k)
        }
        return consume(k)
    }

    private fun consume(k: Key): Boolean {
        val left = remaining[k] ?: 0
        if (left <= 0) return false
        remaining[k] = left - 1
        return true
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
