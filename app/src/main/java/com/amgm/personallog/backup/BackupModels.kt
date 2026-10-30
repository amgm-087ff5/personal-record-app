package com.amgm.personallog.backup

import com.amgm.personallog.data.db.Migrations
import com.amgm.personallog.data.model.DiaryEntry
import com.amgm.personallog.data.model.DiaryPhoto
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.PaymentPayer
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.data.model.ReceiptNotDuplicate
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.ShareGroupItem
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitLabelAmount
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.model.SplitSheet
import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.data.model.WholeShare
import com.amgm.personallog.data.model.newUid
import com.amgm.personallog.domain.PricedLine
import com.amgm.personallog.domain.SingleExpenseConversion
import com.amgm.personallog.domain.TaxCalc
import com.amgm.personallog.domain.legacyReceiptOwnYen
import com.amgm.personallog.receipt.LegacyRow
import com.amgm.personallog.receipt.ReceiptUnits
import com.amgm.personallog.receipt.TotalSourceInit
import com.amgm.personallog.receipt.UnitRow
import com.amgm.personallog.domain.LedgerCalc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * バックアップZIPのフォーマット版。互換性を壊す変更をしたら上げる。
 * 10: 品目・支出の品目に金額不明(unknownAmount)。単独の支出(groupId なし)は書き出さない(取り込みでは、まとまり+1品目に変える)。
 */
const val BACKUP_FORMAT_VERSION = 10
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
    /** v7 以降。以下の割り勘の表は、v7 以降のファイルにだけ入る。形式6以前のファイルには無いので空として読む。 */
    val persons: List<BackupPerson> = emptyList(),
    val splitSheets: List<BackupSplitSheet> = emptyList(),
    val shareGroups: List<BackupShareGroup> = emptyList(),
    val shareGroupItems: List<BackupShareGroupItem> = emptyList(),
    /** v8 以降。支払いごとの参加者・ラベル金額。形式7以前には無いので空として読む。 */
    val participants: List<BackupParticipant> = emptyList(),
    val labelAmounts: List<BackupLabelAmount> = emptyList(),
    /** v8 以降、割当・支払内訳の参照先は人(personId)から参加者(participantId)に変わった。形式7以前のものは取り込まない(BackupItemShare の注釈も参照)。 */
    val itemShares: List<BackupItemShare> = emptyList(),
    val wholeShares: List<BackupWholeShare> = emptyList(),
    val paymentPayers: List<BackupPaymentPayer> = emptyList(),
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
    /** v3で追加(支払い全体の自分の分・円)。無い古いバックアップは品目の値から求める。 */
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
    /** v9 以降。参加人数(自分を含む)。無い古いバックアップは null(不明)。 */
    val headcount: Int? = null,
) {
    /**
     * items はこの支払いの品目(旧形式の自分の分を集計するため)。
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
            headcount = headcount,
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
            headcount = e.headcount,
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
 * 「重複ではない」の組を、復元後の支払い id に付け替える。idMap はバックアップ内の id -> 復元後の id。
 * どちらかの対応が取れない組は捨てる。同じ支払いどうしの組になったもの・重複した組も捨てる。
 */
fun remapNotDuplicates(pairs: List<BackupNotDuplicate>, idMap: Map<Long, Long>): List<ReceiptNotDuplicate> =
    pairs.mapNotNull { p ->
        val low = idMap[p.receiptIdLow] ?: return@mapNotNull null
        val high = idMap[p.receiptIdHigh] ?: return@mapNotNull null
        ReceiptNotDuplicate.of(low, high, p.createdAt)
    }.distinctBy { it.receiptIdLow to it.receiptIdHigh }

/**
 * 復元(マージ)で、バックアップの支払いが既存のどの支払いに当たるかを探す(純Kotlin)。
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
     * 対応する未使用の既存支払いの id。無ければ null。見つけた id は使用済みにする。
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
    // v2まで: 品目ごとの自分の分。v3以降は常に null(支払い全体の ownAmount を使う)。
    val ownAmount: Int? = null,
    // 旧形式(v3まで)の項目。読み込み時に1個ずつの行へ変換する。新形式では null のまま出力される。
    val quantity: Double? = null,
    val unitPrice: Int? = null,
    val ownPercent: Int? = null,
    /** 形式10以降。金額が分からない品目。古い形式には無いので false。 */
    val unknownAmount: Boolean = false,
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
            taxRate = it.taxRate, priceIncludesTax = it.priceIncludesTax, memo = memo, unknownAmount = unknownAmount,
        )
    }

    companion object {
        fun from(e: ReceiptItem) = BackupReceiptItem(
            id = e.id, receiptId = e.receiptId, name = e.name, memo = e.memo, amount = e.amount, category = e.category,
            taxRate = e.taxRate, priceIncludesTax = e.priceIncludesTax, unknownAmount = e.unknownAmount,
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
    /** v4 以降。支出(まとまり)の品目なら、そのグループの id。 */
    val groupId: Long? = null,
    /** v7 以降。日時を支出側で変えたか。 */
    val timeEdited: Boolean = false,
    /** v6 以降。元になった支払いの品目の id(バックアップ内の id)。復元時に復元後の id へ付け替える。 */
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
    /** 形式10以降。金額が分からない品目。古い形式には無いので false。 */
    val unknownAmount: Boolean = false,
) {
    /** linkedTime: 紐づく支払いの時刻(支払いがあれば)。time が空のときの初期値に使う。 */
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
        unknownAmount = unknownAmount,
    )
    companion object {
        fun from(e: Expense) = BackupExpense(
            id = e.id, date = e.date, time = e.time, amount = e.amount, category = e.category, memo = e.memo,
            receiptId = e.receiptId, createdAt = e.createdAt, groupId = e.groupId, timeEdited = e.timeEdited,
            receiptItemId = e.receiptItemId, name = e.name,
            amountEdited = e.amountEdited, categoryEdited = e.categoryEdited, nameEdited = e.nameEdited,
            memoEdited = e.memoEdited, uid = e.uid, deletedByUser = e.deletedByUser, unknownAmount = e.unknownAmount,
        )
    }
}

/** 支出(まとまり、v4 以降)。amount は支出の金額。 */
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
        // 取り込みでは、支払いに属さない支出を先に手入力の支払いに変えている(convertOrphanExpensesToManualPayments)
        receiptId = requireNotNull(receiptId) { "支払いに属さない支出は取り込めません(id=$id)" },
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

/**
 * 復元(マージ)で既存の支払いに当たったとき、バックアップの品目 id -> 既存の品目 id の対応を作る(純Kotlin)。
 * 品名・金額・税率・税込区分が同じ品目を、順番に1対1で対応させる。これで支出の receiptItemId を付け替えられる。
 */
fun matchExistingItems(backupItems: List<BackupReceiptItem>, existing: List<ReceiptItem>): Map<Long, Long> {
    data class Key(val name: String, val amount: Int, val taxRate: Int, val includesTax: Boolean)
    val pool = HashMap<Key, ArrayDeque<Long>>()
    for (e in existing.sortedBy { it.id }) {
        pool.getOrPut(Key(e.name.trim(), e.amount, e.taxRate, e.priceIncludesTax)) { ArrayDeque() }.addLast(e.id)
    }
    val out = HashMap<Long, Long>()
    for (b in backupItems.sortedBy { it.id }) {
        val u = b.toUnitRows().firstOrNull() ?: continue
        val id = pool[Key(u.name.trim(), u.amount, u.taxRate, u.priceIncludesTax)]?.removeFirstOrNull() ?: continue
        out[b.id] = id
    }
    return out
}

/** 支出の receiptItemId を、復元後の receipt_items の id に付け替える。対応が取れなければ null。 */
fun remapReceiptItemId(backupItemId: Long?, itemIdMap: Map<Long, Long>): Long? =
    backupItemId?.let { itemIdMap[it] }

/**
 * 復元(マージ)で、バックアップの支出(まとまり)が既存のどれに当たるかを探す(純Kotlin)。
 * uid があれば uid で照合する。uid が無い(形式5以前の)グループは従来のキー(日付・金額・カテゴリ・メモ・支払い)で照合する。
 * uid が一致しなくても、同じ支払いに属するグループ(entity.receiptId がある)は従来のキーでも照合する
 * (別の端末で確定して uid が違うだけの同じ支払いを、二重に登録しない)。
 */
class GroupMergeIndex(existing: List<ExpenseGroup>) {
    private data class Key(val date: String, val amount: Int, val category: String, val memo: String, val receiptId: Long?)

    private val byUid = HashMap<String, Long>()
    private val byKey = HashMap<Key, Long>()

    /** 既存の支払いに紐づく支出(まとまり。支払い id -> グループ id)。支払い1枚につきグループは1つ。 */
    private val byReceipt = HashMap<Long, Long>()

    init {
        existing.forEach {
            register(it, it.id)
            it.receiptId?.let { rid -> byReceipt.putIfAbsent(rid, it.id) }
        }
    }

    private fun keyOf(g: ExpenseGroup) = Key(g.date, g.amount, g.category, g.memo, g.receiptId)

    private fun register(g: ExpenseGroup, id: Long) {
        if (g.uid.isNotEmpty()) byUid.putIfAbsent(g.uid, id)
        byKey.putIfAbsent(keyOf(g), id)
    }

    /**
     * backup が既存に当たれば既存の id。当たらなければ null。
     * @param entity バックアップのグループを復元後の支払い id などに直した実体(uid は backupUid を入れておく)
     */
    fun find(backupUid: String, entity: ExpenseGroup): Long? {
        if (backupUid.isEmpty()) {
            byKey[keyOf(entity)]?.let { return it }
        } else {
            byUid[backupUid]?.let { return it }
            // uid は違うが同じ支払いの支出: 従来のキーでも照合する
            if (entity.receiptId != null) byKey[keyOf(entity)]?.let { return it }
        }
        // 既存の支払いに支出(まとまり)が既にあれば、バックアップのグループは追加しない(同じ支払いにグループが二重になるのを防ぐ)
        return entity.receiptId?.let { byReceipt[it] }
    }

    /** 新しく入れたグループを登録する(同じバックアップ内の重複にも効く)。 */
    fun add(entity: ExpenseGroup, newId: Long) = register(entity, newId)
}

/**
 * 復元(マージ)で、バックアップの支出が既存と重複しているかを判定する(純Kotlin)。
 * uid があれば uid で照合する。uid が無い(形式5以前の)支出は従来のキー(日付・金額・カテゴリ・メモ・支払い・グループ)の件数ベースで照合する。
 * uid が一致しなくても、同じ支払いに属する支出(復元後の receiptId がある)は従来のキーの件数ベースでも照合する。
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
            // uid は初めて。ただし同じ支払いの支出なら、従来のキーの件数でも照合する
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

// ---------------------------------------------------------------- 支払いに属さない支出(旧形式の取り込み)

/**
 * 支払いに属さない支出を手入力の支払いに変えた結果。
 * manualReceiptIds: 変換で作った支払いの backup id。旧形式(形式4以前)のファイルでも、TotalSourceInit で合計の出所を変えないために使う。
 */
data class ManualPaymentConversion(val data: BackupData, val manualReceiptIds: Set<Long>)

/**
 * 支払いに属さない支出グループ・単独の支出を、手入力の支払いに変える(純Kotlin)。
 * 「支払いに属さない」は、receiptId が無い、または取り込み元に無いレシートを指すもの。
 * 変換内容は DB の MIGRATION_11_12 と同じ:
 *  - 対象ごとに手入力の支払いを1つ作る(画像なし・確定・合計のみ・storeName は空)。日時は日付(時刻があれば "yyyy-MM-dd HH:mm")、金額は支出金額。
 *  - グループの中の支出は品目に写し、支出に receiptId と receiptItemId を張る。グループにも receiptId を張る。
 *  - 単独の支出は品目を作らず、receiptId だけ張る。
 *  - 金額・品名・カテゴリ・メモ・作成時刻は変えない。グループと単独の支出は、カテゴリとメモを編集済みにする(次の同期で既定値に戻らないように)。
 *  - 時刻が空の支出・グループは、取り込み時の既定(作成時刻)を "HH:mm" で入れる。空のままだと支払いの時刻を借りてしまうため。
 * 作る支払い・品目の id は、バックアップ内のどの id とも重ならない値にする。
 */
fun convertOrphanExpensesToManualPayments(data: BackupData): ManualPaymentConversion {
    val receiptIds = data.receipts.map { it.id }.toSet()
    fun isOrphan(receiptId: Long?) = receiptId == null || receiptId !in receiptIds
    val needsWork = data.expenseGroups.any { isOrphan(it.receiptId) } ||
        data.expenses.any { it.groupId == null && isOrphan(it.receiptId) }
    if (!needsWork) return ManualPaymentConversion(data, emptySet())

    var nextId = maxReferencedId(data) + 1
    val newReceipts = ArrayList<BackupReceipt>()
    val newItems = ArrayList<BackupReceiptItem>()
    // 変換したグループの backup id -> 作った支払いの id
    val paymentOfGroup = HashMap<Long, Long>()

    val groups = data.expenseGroups.map { g ->
        if (!isOrphan(g.receiptId)) return@map g
        val rid = nextId++
        newReceipts += manualPaymentOf(rid, g.date, g.time, g.amount, g.createdAt)
        paymentOfGroup[g.id] = rid
        g.copy(
            time = g.time.ifBlank { LedgerCalc.timeOfMillis(g.createdAt) },
            receiptId = rid,
            categoryEdited = true,
            memoEdited = true,
        )
    }

    val expenses = data.expenses.map { e ->
        val time = e.time.ifBlank { LedgerCalc.timeOfMillis(e.createdAt) }
        val gid = e.groupId
        when {
            // 変換したグループの中の支出: 品目に写し、支出に支払いと品目を張る
            gid != null && gid in paymentOfGroup -> {
                val rid = paymentOfGroup.getValue(gid)
                val itemId = nextId++
                newItems += BackupReceiptItem(
                    id = itemId, receiptId = rid, name = e.name, memo = e.memo, amount = e.amount, category = e.category,
                )
                e.copy(time = time, receiptId = rid, receiptItemId = itemId)
            }
            // 単独の支出: 支払いを作り、支出に張る(品目は作らない)
            gid == null && isOrphan(e.receiptId) -> {
                val rid = nextId++
                newReceipts += manualPaymentOf(rid, e.date, e.time, e.amount, e.createdAt)
                e.copy(time = time, receiptId = rid, categoryEdited = true, memoEdited = true)
            }
            else -> e
        }
    }

    val out = data.copy(
        receipts = data.receipts + newReceipts,
        receiptItems = data.receiptItems + newItems,
        expenses = expenses,
        expenseGroups = groups,
    )
    return ManualPaymentConversion(out, newReceipts.map { it.id }.toSet())
}

// ---------------------------------------------------------------- 単独の支出(形式9以前の取り込み)

/**
 * 単独の支出(groupId が無く、家計簿で消していない支出)を、自分の支出(まとまり)+ 品名が空の1品目に変える(純Kotlin)。
 * 変換内容は DB の MIGRATION_13_14 と同じ(SingleExpenseConversion)。家計簿で消した groupId の無い行は、同じ支払いの
 * 単独の支出から作ったまとまりがあれば、そこに付け替える(消したまま)。支払いに属さない支出の変換
 * (convertOrphanExpensesToManualPayments)の後に呼ぶ(そこで作った単独の支出も変える)。
 * 作るまとまりの id は、バックアップ内のどのまとまりの id とも重ならない値にする。
 */
fun convertSingleExpensesToGroups(data: BackupData): BackupData {
    val targets = data.expenses.filter { SingleExpenseConversion.isTarget(it.groupId, it.deletedByUser) }
    if (targets.isEmpty()) return data
    var nextId = (data.expenseGroups.map { it.id } + data.expenses.mapNotNull { it.groupId }).maxOrNull()?.plus(1) ?: 1L
    val newGroups = ArrayList<BackupExpenseGroup>()
    // 支払い -> その支払いで最初に作ったまとまり(消した行の付け替え先)
    val groupOfReceipt = HashMap<Long, Long>()
    val converted = data.expenses.map { e ->
        if (!SingleExpenseConversion.isTarget(e.groupId, e.deletedByUser)) return@map e
        val gid = nextId++
        e.receiptId?.let { groupOfReceipt.putIfAbsent(it, gid) }
        // 時刻が空の古い行は、取り込み時の既定(作成時刻)をまとまりと品目の両方に入れる(品目の時刻はまとまりに従う)
        val time = e.time.ifBlank { LedgerCalc.timeOfMillis(e.createdAt) }
        newGroups += BackupExpenseGroup(
            id = gid,
            date = e.date,
            time = time,
            amount = e.amount,
            category = e.category,
            memo = e.memo,
            receiptId = e.receiptId,
            createdAt = e.createdAt,
            uid = SingleExpenseConversion.groupUid(e.uid),
            amountEdited = e.amountEdited,
            categoryEdited = e.categoryEdited,
            memoEdited = e.memoEdited,
            timeEdited = e.timeEdited,
        )
        e.copy(
            time = time,
            groupId = gid,
            name = "",
            amountEdited = false,
            categoryEdited = false,
            nameEdited = false,
            memoEdited = false,
            timeEdited = false,
        )
    }
    val expenses = converted.map { e ->
        val gid = e.receiptId?.let { groupOfReceipt[it] }
        if (e.groupId == null && e.deletedByUser && gid != null) e.copy(groupId = gid) else e
    }
    return data.copy(expenses = expenses, expenseGroups = data.expenseGroups + newGroups)
}

/** 手入力の支払い(画像なし・確定・合計のみ)。MIGRATION_11_12 の insertV12ManualReceipt と同じ内容。 */
private fun manualPaymentOf(id: Long, date: String, time: String, amount: Int, createdAt: Long) = BackupReceipt(
    id = id,
    purchasedAt = Migrations.v12PurchasedAt(date, time),
    storeName = "",
    totalAmount = amount,
    imagePath = "",
    rawJson = "{}",
    status = ReceiptStatus.CONFIRMED,
    createdAt = createdAt,
    totalSource = TotalSource.MANUAL,
)

/** バックアップ内で使われている支払い・品目の id の最大値(新しく振る id がこれと重ならないように)。 */
private fun maxReferencedId(data: BackupData): Long {
    val ids = buildList<Long> {
        data.receipts.forEach { add(it.id) }
        data.receiptItems.forEach { add(it.id); add(it.receiptId) }
        data.expenses.forEach { e ->
            e.receiptId?.let { add(it) }
            e.receiptItemId?.let { add(it) }
        }
        data.expenseGroups.forEach { g -> g.receiptId?.let { add(it) } }
        data.receiptNotDuplicates.forEach { add(it.receiptIdLow); add(it.receiptIdHigh) }
        data.splitSheets.forEach { add(it.receiptId) }
        data.participants.forEach { add(it.receiptId) }
        data.shareGroups.forEach { add(it.id); add(it.receiptId) }
        data.shareGroupItems.forEach { add(it.receiptItemId) }
        data.wholeShares.forEach { add(it.receiptId) }
        data.paymentPayers.forEach { add(it.receiptId) }
        data.labelAmounts.forEach { add(it.receiptId) }
    }
    return ids.maxOrNull() ?: 0L
}

// ---------------------------------------------------------------- 割り勘の表(v7 以降)

/** 人(v7 以降)。uid は端末をまたいで変わらない照合キー。id はバックアップ内の id(取り込み時に付け替える)。 */
@Serializable
data class BackupPerson(
    val id: Long,
    val uid: String = "",
    val name: String,
    val shortName: String = "",
    val isSelf: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
) {
    /** uid が空なら新しく振る(v7 では常に入る)。 */
    fun toEntity() = Person(
        id = id, uid = uid.ifBlank { newUid() }, name = name, shortName = shortName,
        isSelf = isSelf, sortOrder = sortOrder, createdAt = createdAt,
    )
    companion object {
        fun from(p: Person) = BackupPerson(p.id, p.uid, p.name, p.shortName, p.isSelf, p.sortOrder, p.createdAt)
    }
}

/**
 * 割り勘シート(v7 以降。wholeKind・warnings は v8 以降で、無い形式は既定値)。receiptId はバックアップ内の支払い id。
 */
@Serializable
data class BackupSplitSheet(
    val receiptId: Long,
    val mode: String = SplitMode.SELF,
    val wholeKind: String = ShareKind.AMOUNT,
    val warnings: String = "",
) {
    fun toEntity() = SplitSheet(receiptId, mode, wholeKind, warnings)
    companion object {
        fun from(s: SplitSheet) = BackupSplitSheet(s.receiptId, s.mode, s.wholeKind, s.warnings)
    }
}

/**
 * 支払いごとの参加者(v8 以降)。id・receiptId はバックアップ内の id。
 * personId は人の id(バックアップ内)。無い参加者は「名前なし」。取り込み時に人の対応が無ければ null にする。
 */
@Serializable
data class BackupParticipant(
    val id: Long,
    val receiptId: Long,
    val personId: Long? = null,
    val memo: String = "",
    val label: String = "",
    val isPayer: Boolean = false,
    val sortOrder: Int = 0,
) {
    fun toEntity() = SplitParticipant(
        id = id, receiptId = receiptId, personId = personId, memo = memo, label = label,
        isPayer = isPayer, sortOrder = sortOrder,
    )
    companion object {
        fun from(p: SplitParticipant) = BackupParticipant(
            p.id, p.receiptId, p.personId, p.memo, p.label, p.isPayer, p.sortOrder,
        )
    }
}

/** ラベルごとの1人あたり金額(v8 以降)。receiptId はバックアップ内の支払い id。label が "" は「その他」。 */
@Serializable
data class BackupLabelAmount(val receiptId: Long, val label: String, val amount: Int) {
    fun toEntity() = SplitLabelAmount(receiptId, label, amount)
    companion object {
        fun from(a: SplitLabelAmount) = BackupLabelAmount(a.receiptId, a.label, a.amount)
    }
}

/** 品目のまとまり(v7 以降)。id と receiptId はバックアップ内の id。 */
@Serializable
data class BackupShareGroup(
    val id: Long,
    val receiptId: Long,
    val pieceCount: Int? = null,
    val isShared: Boolean = false,
    val sortOrder: Int = 0,
) {
    fun toEntity() = ShareGroup(id, receiptId, pieceCount, isShared, sortOrder)
    companion object {
        fun from(g: ShareGroup) = BackupShareGroup(g.id, g.receiptId, g.pieceCount, g.isShared, g.sortOrder)
    }
}

/** まとまりに含まれる品目(v7 以降)。groupId と receiptItemId はバックアップ内の id。 */
@Serializable
data class BackupShareGroupItem(val groupId: Long, val receiptItemId: Long) {
    fun toEntity() = ShareGroupItem(groupId, receiptItemId)
    companion object {
        fun from(s: ShareGroupItem) = BackupShareGroupItem(s.groupId, s.receiptItemId)
    }
}

/**
 * 品目ごと方式の割当(v8 以降)。groupId と participantId はバックアップ内の id。
 * 形式7までは参照先が人(personId)で、参加者に付け替えられないため、personId は読むだけで捨てる(書き出しでは null)。
 * 形式7のファイルの割当は、取り込み側で丸ごと捨てる(形式7の時点では画面から割り勘を使えなかったので実データは無い想定)。
 */
@Serializable
data class BackupItemShare(
    val groupId: Long,
    val participantId: Long = 0,
    val kind: String,
    val weight: Int? = null,
    val amount: Int? = null,
    /** 形式7まで。読むだけで捨てる。 */
    val personId: Long? = null,
) {
    fun toEntity() = ItemShare(groupId, participantId, kind, weight, amount)
    companion object {
        fun from(s: ItemShare) = BackupItemShare(s.groupId, s.participantId, s.kind, s.weight, s.amount)
    }
}

/** 全体方式の割当(v8 以降)。receiptId と participantId はバックアップ内の id。personId は形式7まで(読むだけで捨てる)。 */
@Serializable
data class BackupWholeShare(
    val receiptId: Long,
    val participantId: Long = 0,
    val kind: String,
    val weight: Int? = null,
    val amount: Int? = null,
    /** 形式7まで。読むだけで捨てる。 */
    val personId: Long? = null,
) {
    fun toEntity() = WholeShare(receiptId, participantId, kind, weight, amount)
    companion object {
        fun from(s: WholeShare) = BackupWholeShare(s.receiptId, s.participantId, s.kind, s.weight, s.amount)
    }
}

/** 支払内訳(v8 以降)。id・receiptId・participantId はバックアップ内の id。personId は形式7まで(読むだけで捨てる)。 */
@Serializable
data class BackupPaymentPayer(
    val id: Long,
    val receiptId: Long,
    val participantId: Long = 0,
    val method: String = "",
    val amount: Int,
    /** 形式7まで。読むだけで捨てる。 */
    val personId: Long? = null,
) {
    fun toEntity() = PaymentPayer(id, receiptId, participantId, method, amount)
    companion object {
        fun from(p: PaymentPayer) = BackupPaymentPayer(p.id, p.receiptId, p.participantId, p.method, p.amount)
    }
}

/** 人の取り込み方(planPersons の結果)。 */
sealed interface PersonPlan {
    /** 端末の既存の人を使う(id は端末側の id)。 */
    data class Existing(val id: Long) : PersonPlan

    /** 新しく作る(person の id は使わず、自動採番に任せる)。 */
    data class New(val person: BackupPerson) : PersonPlan
}

/**
 * バックアップの人を、端末の人にどう対応させるかを決める(純Kotlin)。返り値は バックアップの人 id -> 取り込み方。
 * - 同じ uid の人が端末にいれば、それを使う。
 * - バックアップの自分(isSelf)は、端末の自分(selfId)に合わせる。端末に自分が無いときだけ、1人を自分として作る。
 * - それ以外は新しく作る。同じ uid がバックアップ内で重複していたら先頭だけ取り込み、後ろは対応なし(参照は捨てる)。
 * @param existingByUid 端末の人の uid -> id
 * @param selfId 端末の自分の id(無ければ null)
 */
fun planPersons(persons: List<BackupPerson>, existingByUid: Map<String, Long>, selfId: Long?): Map<Long, PersonPlan> {
    val out = HashMap<Long, PersonPlan>()
    val plannedUids = HashSet<String>()
    var selfPlanned = false
    for (p in persons) {
        val existingId = when {
            p.isSelf && selfId != null -> selfId
            p.uid.isNotEmpty() -> existingByUid[p.uid]
            else -> null
        }
        if (existingId != null) {
            out[p.id] = PersonPlan.Existing(existingId)
            continue
        }
        if (p.uid.isNotEmpty() && !plannedUids.add(p.uid)) continue
        val makeSelf = p.isSelf && selfId == null && !selfPlanned
        if (makeSelf) selfPlanned = true
        out[p.id] = PersonPlan.New(p.copy(isSelf = makeSelf))
    }
    return out
}

/**
 * 割り勘のまとまりを、復元後の支払い id に付け替える(純Kotlin)。返り値は (バックアップのまとまり id, 新しい行)。
 * 新しい行の id は 0(自動採番に任せる)。支払いの対応が無いまとまりは捨てる。
 */
fun remapShareGroups(groups: List<BackupShareGroup>, receiptIdMap: Map<Long, Long>): List<Pair<Long, ShareGroup>> =
    groups.mapNotNull { g ->
        val receiptId = receiptIdMap[g.receiptId] ?: return@mapNotNull null
        g.id to g.toEntity().copy(id = 0, receiptId = receiptId)
    }

/**
 * 割り勘の参加者を、復元後の支払い id・人 id に付け替える(純Kotlin)。返り値は (バックアップの参加者 id, 新しい行)。
 * 新しい行の id は 0(自動採番に任せる)。支払いの対応が無い参加者は捨てる。
 * 人の対応が無い参加者は残し、personId を null(名前なし・未登録扱い)にする。
 * @param personIdMap バックアップの人 id -> 端末の人 id
 */
fun remapParticipants(
    participants: List<BackupParticipant>,
    receiptIdMap: Map<Long, Long>,
    personIdMap: Map<Long, Long>,
): List<Pair<Long, SplitParticipant>> =
    participants.mapNotNull { p ->
        val receiptId = receiptIdMap[p.receiptId] ?: return@mapNotNull null
        val personId = p.personId?.let { personIdMap[it] }
        p.id to p.toEntity().copy(id = 0, receiptId = receiptId, personId = personId)
    }

/** 割り勘の表(まとまり・参加者以外)を付け替えた結果。id は未採番(自動採番に任せる)。 */
data class SplitRemapped(
    val sheets: List<SplitSheet>,
    val groupItems: List<ShareGroupItem>,
    val itemShares: List<ItemShare>,
    val wholeShares: List<WholeShare>,
    val payers: List<PaymentPayer>,
    val labelAmounts: List<SplitLabelAmount>,
)

/**
 * 割り勘の表を、復元後の id に付け替える(純Kotlin)。参照先のどれかが対応しない行は捨てる。
 * 同じ主キーになる行は1つにまとめる(品目は1つのまとまりにしか入らない、参加者は1つの割当だけ、など)。
 * 形式7以前のファイルの itemShares・wholeShares・paymentPayers は、参照先が人で参加者に付け替えられないので捨てる
 * (形式7の時点では画面から割り勘を使えなかったため、実データは無い想定)。シート・まとまり・品目のまとまりは入れる。
 * @param receiptIdMap バックアップの支払い id -> 復元後の支払い id(取り込み対象の支払いだけ)
 * @param itemIdMap バックアップの品目 id -> 復元後の品目 id(取り込み対象の品目だけ)
 * @param groupIdMap バックアップのまとまり id -> 復元後のまとまり id
 * @param participantIdMap バックアップの参加者 id -> 復元後の参加者 id(取り込み対象の参加者だけ)
 */
fun remapSplitRows(
    data: BackupData,
    receiptIdMap: Map<Long, Long>,
    itemIdMap: Map<Long, Long>,
    groupIdMap: Map<Long, Long>,
    participantIdMap: Map<Long, Long>,
): SplitRemapped {
    // 形式7以前の割当・支払内訳は捨てる(参照先が人のため)
    val dropShares = data.formatVersion < 8
    return SplitRemapped(
        sheets = data.splitSheets.mapNotNull { s ->
            val r = receiptIdMap[s.receiptId] ?: return@mapNotNull null
            s.toEntity().copy(receiptId = r)
        }.distinctBy { it.receiptId },
        groupItems = data.shareGroupItems.mapNotNull { s ->
            val g = groupIdMap[s.groupId] ?: return@mapNotNull null
            val item = itemIdMap[s.receiptItemId] ?: return@mapNotNull null
            s.toEntity().copy(groupId = g, receiptItemId = item)
        }.distinctBy { it.receiptItemId },
        itemShares = if (dropShares) emptyList() else data.itemShares.mapNotNull { s ->
            val g = groupIdMap[s.groupId] ?: return@mapNotNull null
            val p = participantIdMap[s.participantId] ?: return@mapNotNull null
            s.toEntity().copy(groupId = g, participantId = p)
        }.distinctBy { it.groupId to it.participantId },
        wholeShares = if (dropShares) emptyList() else data.wholeShares.mapNotNull { s ->
            val r = receiptIdMap[s.receiptId] ?: return@mapNotNull null
            val p = participantIdMap[s.participantId] ?: return@mapNotNull null
            s.toEntity().copy(receiptId = r, participantId = p)
        }.distinctBy { it.receiptId to it.participantId },
        payers = if (dropShares) emptyList() else data.paymentPayers.mapNotNull { s ->
            val r = receiptIdMap[s.receiptId] ?: return@mapNotNull null
            val p = participantIdMap[s.participantId] ?: return@mapNotNull null
            s.toEntity().copy(id = 0, receiptId = r, participantId = p)
        },
        labelAmounts = data.labelAmounts.mapNotNull { s ->
            val r = receiptIdMap[s.receiptId] ?: return@mapNotNull null
            s.toEntity().copy(receiptId = r)
        }.distinctBy { it.receiptId to it.label },
    )
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
