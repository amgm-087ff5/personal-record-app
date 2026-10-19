package com.amgm.personallog.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.db.DiaryDao
import com.amgm.personallog.data.db.ExpenseDao
import com.amgm.personallog.data.db.ExpenseGroupDao
import com.amgm.personallog.data.db.ReceiptDao
import com.amgm.personallog.data.db.SplitDao
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.domain.LedgerCalc
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** 全データ+画像のZIPエクスポート/インポート。設定(APIキー)は含めない。 */
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val diaryDao: DiaryDao,
    private val receiptDao: ReceiptDao,
    private val expenseDao: ExpenseDao,
    private val expenseGroupDao: ExpenseGroupDao,
    private val splitDao: SplitDao,
) {
    private val filesDir: File get() = context.filesDir

    // ---------------------------------------------------------------- export

    suspend fun export(uri: Uri, onProgress: (BackupProgress) -> Unit): ExportResult = withContext(Dispatchers.IO) {
        try {
            onProgress(BackupProgress("データを集めています"))
            val entries = diaryDao.observeAll().first()
            val photos = diaryDao.observeAllPhotos().first()
            val receipts = receiptDao.observeAll().first()
            val items = receipts.flatMap { receiptDao.getItems(it.id) }
            val expenses = expenseDao.observeAll().first()
            val groups = expenseGroupDao.observeAll().first()
            val persons = splitDao.getPersons()
            val data = BackupData(
                formatVersion = BACKUP_FORMAT_VERSION,
                dbVersion = db.openHelper.readableDatabase.version,
                exportedAt = System.currentTimeMillis(),
                diaryEntries = entries.map { BackupDiaryEntry.from(it) },
                diaryPhotos = photos.map { BackupDiaryPhoto.from(it) },
                receipts = receipts.map { BackupReceipt.from(it) },
                receiptItems = items.map { BackupReceiptItem.from(it) },
                expenses = expenses.map { BackupExpense.from(it) },
                expenseGroups = groups.map { BackupExpenseGroup.from(it) },
                receiptNotDuplicates = receiptDao.getAllNotDuplicates().map { BackupNotDuplicate.from(it) },
                persons = persons.map { BackupPerson.from(it) },
                splitSheets = splitDao.getAllSheets().map { BackupSplitSheet.from(it) },
                shareGroups = splitDao.getAllGroups().map { BackupShareGroup.from(it) },
                shareGroupItems = splitDao.getAllGroupItems().map { BackupShareGroupItem.from(it) },
                participants = splitDao.getAllParticipants().map { BackupParticipant.from(it) },
                labelAmounts = splitDao.getAllLabelAmounts().map { BackupLabelAmount.from(it) },
                itemShares = splitDao.getAllItemShares().map { BackupItemShare.from(it) },
                wholeShares = splitDao.getAllWholeShares().map { BackupWholeShare.from(it) },
                paymentPayers = splitDao.getAllPayers().map { BackupPaymentPayer.from(it) },
            )
            val json = BackupJson.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)
            val files = collectFiles()

            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw BackupException("保存先を開けませんでした")
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                onProgress(BackupProgress("データを書き込んでいます"))
                zip.putNextEntry(ZipEntry(BACKUP_DATA_ENTRY))
                zip.write(json)
                zip.closeEntry()
                files.forEachIndexed { i, (rel, file) ->
                    currentCoroutineContext().ensureActive()
                    onProgress(BackupProgress("画像を書き込んでいます", i + 1, files.size))
                    zip.putNextEntry(ZipEntry(BACKUP_FILES_PREFIX + rel))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            ExportResult(entries.size, receipts.size, expenses.size, files.size)
        } catch (e: Throwable) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (_: Throwable) {
                // 削除できなくても元のエラーを優先する
            }
            throw e
        }
    }

    /** filesDir 配下の対象ファイルを (相対パス, File) で列挙。 */
    private fun collectFiles(): List<Pair<String, File>> {
        val result = ArrayList<Pair<String, File>>()
        for (dirName in BackupPaths.ROOT_DIRS) {
            val root = File(filesDir, dirName)
            if (!root.isDirectory) continue
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(filesDir).path.replace(File.separatorChar, '/')
                if (BackupPaths.safeRelativePath(rel) != null) result.add(rel to f)
            }
        }
        return result.sortedBy { it.first }
    }

    // ---------------------------------------------------------------- import

    suspend fun importZip(uri: Uri, mode: ImportMode, onProgress: (BackupProgress) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
            val tempDir = File(context.cacheDir, "import_" + System.currentTimeMillis())
            try {
                if (!tempDir.mkdirs()) throw BackupException("一時フォルダを作れませんでした")
                onProgress(BackupProgress("ZIPを読み込んでいます"))
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw BackupException("ファイルを開けませんでした")
                var data: BackupData? = null
                var fileCount = 0
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        if (entry.isDirectory) continue
                        if (name == BACKUP_DATA_ENTRY) {
                            data = parseData(readLimited(zip, MAX_JSON_BYTES))
                        } else if (name.startsWith(BACKUP_FILES_PREFIX)) {
                            val rel = BackupPaths.entryNameToRelative(name)
                                ?: throw BackupException("安全でないファイル名が含まれているため取り込みを中止しました: $name")
                            val dest = File(tempDir, rel)
                            if (!isInside(tempDir, dest)) {
                                throw BackupException("安全でないファイル名が含まれているため取り込みを中止しました: $name")
                            }
                            dest.parentFile?.mkdirs()
                            dest.outputStream().use { zip.copyTo(it) }
                            fileCount++
                            if (fileCount % 10 == 0) onProgress(BackupProgress("画像を展開しています", fileCount))
                        }
                        // それ以外のエントリは無視
                    }
                }
                val backup = data ?: throw BackupException("このZIPは個人記録のバックアップではありません(data.json がありません)")
                validatePaths(backup)
                // 支払いに属さない支出は、手入力の支払いに変えてから入れる(支出は必ず支払いから作る)
                val conversion = convertOrphanExpensesToManualPayments(backup)

                onProgress(BackupProgress("データベースに取り込んでいます"))
                val result = importData(conversion.data, mode, fileCount, conversion.manualReceiptIds)

                onProgress(BackupProgress("画像を配置しています"))
                try {
                    placeFiles(tempDir, mode)
                } catch (e: Exception) {
                    throw BackupException("データは取り込みましたが、画像の配置に失敗しました: ${e.message}", e)
                }
                result
            } finally {
                tempDir.deleteRecursively()
            }
        }

    private fun parseData(bytes: ByteArray): BackupData {
        val parsed = try {
            BackupJson.decodeFromString(BackupData.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupException("data.json を読み込めませんでした(形式が不正です)", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException("data.json を読み込めませんでした(形式が不正です)", e)
        }
        checkVersion(parsed.formatVersion)
        return parsed
    }

    private fun validatePaths(data: BackupData) {
        val bad = data.diaryPhotos.map { it.filePath }.plus(data.receipts.map { it.imagePath })
            .firstOrNull { !BackupPaths.isSafeStoredPath(it) }
        if (bad != null) throw BackupException("安全でない画像パスが含まれているため取り込みを中止しました: $bad")
    }

    private suspend fun importData(
        data: BackupData,
        mode: ImportMode,
        fileCount: Int,
        manualReceiptIds: Set<Long>,
    ): ImportResult =
        db.withTransaction {
            if (mode == ImportMode.REPLACE) replaceAll(data, fileCount, manualReceiptIds)
            else mergeAll(data, fileCount, manualReceiptIds)
        }

    private suspend fun replaceAll(data: BackupData, fileCount: Int, manualReceiptIds: Set<Long>): ImportResult {
        expenseDao.observeAll().first().forEach { expenseDao.delete(it) }
        expenseGroupDao.observeAll().first().forEach { expenseGroupDao.delete(it) }
        receiptDao.observeAll().first().forEach { receiptDao.delete(it) }
        diaryDao.observeAll().first().forEach { diaryDao.delete(it) }

        data.diaryEntries.forEach { diaryDao.insert(it.toEntity()) }
        data.diaryPhotos.forEach { diaryDao.insertPhoto(it.toEntity()) }
        val replaceItemsByReceipt = data.receiptItems.groupBy { it.receiptId }
        // バックアップ内の支払い id -> 復元後の id(割り勘の表の付け替えに使う)
        val receiptIdMap = HashMap<Long, Long>()
        data.receipts.forEach { r ->
            receiptIdMap[r.id] = receiptDao.insert(
                r.toEntity(replaceItemsByReceipt[r.id].orEmpty(), legacyFormat = isLegacyReceipt(data, r.id, manualReceiptIds)),
            )
        }
        // 品目は1件ずつ入れて、バックアップ内の id -> 復元後の id を控える(支出の receiptItemId の付け替えに使う)
        val itemIdMap = HashMap<Long, Long>()
        for (bi in data.receiptItems) {
            val newIds = bi.toEntities().map { receiptDao.insertItem(it) }
            newIds.firstOrNull()?.let { itemIdMap[bi.id] = it }
        }
        val receiptIds = data.receipts.map { it.id }.toSet()
        // 「重複ではない」の組。replace は id をそのまま使うので、レシートが無い組だけ捨てる
        receiptDao.insertNotDuplicates(remapNotDuplicates(data.receiptNotDuplicates, receiptIds.associateWith { it }))
        val times = receiptTimes(data)
        data.expenseGroups.forEach { g ->
            expenseGroupDao.insert(g.toEntity(linkedTime(g.receiptId, times)).copy(receiptId = g.receiptId?.takeIf { it in receiptIds }))
        }
        val groupIds = data.expenseGroups.map { it.id }.toSet()
        if (data.expenses.isNotEmpty()) {
            expenseDao.insertAll(
                data.expenses.map { e ->
                    e.toEntity(linkedTime(e.receiptId, times)).copy(
                        receiptId = e.receiptId?.takeIf { it in receiptIds },
                        groupId = e.groupId?.takeIf { it in groupIds },
                        receiptItemId = remapReceiptItemId(e.receiptItemId, itemIdMap),
                    )
                },
            )
        }
        // 割り勘の表(v7 以降)。人は消されないので先に全部消してから入れ直す(支払いの削除で表は CASCADE で消えている)。
        // 形式6以前のファイルは人の行が無いので、消したあと自分1人だけになる
        splitDao.getPersons().forEach { splitDao.deletePerson(it) }
        val personIdMap = importPersons(data.persons)
        importSplitRows(data, receiptIdMap, itemIdMap, personIdMap)
        return ImportResult(
            diaryEntries = data.diaryEntries.size,
            diaryPhotos = data.diaryPhotos.size,
            receipts = data.receipts.size,
            expenses = data.expenses.size,
            skipped = 0,
            files = fileCount,
        )
    }

    /**
     * 旧形式(形式4以前)の支払いか。変換で作った手入力の支払いは、旧形式の合計判定(TotalSourceInit)を通さない。
     */
    private fun isLegacyReceipt(data: BackupData, receiptId: Long, manualReceiptIds: Set<Long>): Boolean =
        data.formatVersion < 5 && receiptId !in manualReceiptIds

    /** バックアップ内のレシート id -> レシートの時刻 "HH:mm"(時刻が無ければ 00:00)。古いバックアップの支出の初期値に使う。 */
    private fun receiptTimes(data: BackupData): Map<Long, String> =
        data.receipts.associate { it.id to LedgerCalc.splitPurchasedAt(it.purchasedAt).second }

    private fun linkedTime(receiptId: Long?, times: Map<Long, String>): String? = receiptId?.let { times[it] }

    private suspend fun mergeAll(data: BackupData, fileCount: Int, manualReceiptIds: Set<Long>): ImportResult {
        var skipped = 0
        var addedEntries = 0
        var addedPhotos = 0
        var addedReceipts = 0
        var addedExpenses = 0

        // 日記(日付で重複判定。既存があれば既存を優先)
        val entryByDate = diaryDao.observeAll().first().associate { it.date to it.id }.toMutableMap()
        val entryIdMap = HashMap<Long, Long>()
        for (e in data.diaryEntries) {
            val existing = entryByDate[e.date]
            if (existing != null) {
                entryIdMap[e.id] = existing
                skipped++
            } else {
                val newId = diaryDao.insert(e.toEntity().copy(id = 0))
                entryIdMap[e.id] = newId
                entryByDate[e.date] = newId
                addedEntries++
            }
        }
        val photoKeys = diaryDao.observeAllPhotos().first().map { it.entryId to it.filePath }.toMutableSet()
        for (p in data.diaryPhotos) {
            val entryId = entryIdMap[p.entryId]
            if (entryId == null || !photoKeys.add(entryId to p.filePath)) {
                skipped++
                continue
            }
            diaryDao.insertPhoto(p.toEntity().copy(id = 0, entryId = entryId))
            addedPhotos++
        }

        // レシート(画像ハッシュがあればハッシュで、無ければ日時・店名・合計・画像パスで照合)
        val receiptIndex = ReceiptMergeIndex(receiptDao.observeAll().first())
        val receiptIdMap = HashMap<Long, Long>()
        val itemsByReceipt = data.receiptItems.groupBy { it.receiptId }
        // バックアップ内の品目 id -> 復元後の品目 id。新しく入れたレシートの品目だけ対応が取れる(既存のレシートに当たったものは null になる)
        val itemIdMap = HashMap<Long, Long>()
        // 今回新しく入れた支払い・品目だけの対応(割り勘の表は、これに当たる分だけ入れる)
        val newReceiptIdMap = HashMap<Long, Long>()
        val newItemIdMap = HashMap<Long, Long>()
        for (r in data.receipts) {
            val entity = r.toEntity(itemsByReceipt[r.id].orEmpty(), legacyFormat = isLegacyReceipt(data, r.id, manualReceiptIds))
            val existing = receiptIndex.take(entity)
            if (existing != null) {
                receiptIdMap[r.id] = existing
                // 既存のレシートの品目と照合して、支出の receiptItemId を付け替えられるようにする
                itemIdMap.putAll(matchExistingItems(itemsByReceipt[r.id].orEmpty(), receiptDao.getItems(existing)))
                skipped++
            } else {
                val newId = receiptDao.insert(entity.copy(id = 0))
                receiptIdMap[r.id] = newId
                newReceiptIdMap[r.id] = newId
                for (bi in itemsByReceipt[r.id].orEmpty()) {
                    val newIds = bi.toEntities().map { receiptDao.insertItem(it.copy(id = 0, receiptId = newId)) }
                    newIds.firstOrNull()?.let {
                        itemIdMap[bi.id] = it
                        newItemIdMap[bi.id] = it
                    }
                }
                addedReceipts++
            }
        }

        // 「重複ではない」の組: 復元後のレシート id に付け替える。対応が取れない組は捨てる
        receiptDao.insertNotDuplicates(remapNotDuplicates(data.receiptNotDuplicates, receiptIdMap))

        // 支払いグループ(uid があれば uid で、無い古い形式は日付・金額・カテゴリ・メモ・レシートで重複判定)。
        // 既存と重複したグループの品目は飛ばす
        val groupIndex = GroupMergeIndex(expenseGroupDao.observeAll().first())
        val groupIdMap = HashMap<Long, Long>()
        val duplicateGroups = HashSet<Long>()
        val times = receiptTimes(data)
        for (g in data.expenseGroups) {
            val entity = g.toEntity(linkedTime(g.receiptId, times)).copy(id = 0, receiptId = g.receiptId?.let { receiptIdMap[it] })
            val existing = groupIndex.find(g.uid, entity)
            if (existing != null) {
                groupIdMap[g.id] = existing
                duplicateGroups.add(g.id)
                skipped++
            } else {
                val newId = expenseGroupDao.insert(entity)
                groupIdMap[g.id] = newId
                groupIndex.add(entity, newId)
            }
        }

        // 支出(uid があれば uid で、無い古い形式は同一内容の件数ベースで重複判定。同じ日に同額の買い物が2件あっても保たれる)
        val existingExpenses = expenseDao.observeAll().first()
        val expenseIndex = ExpenseMergeIndex(existingExpenses)
        val existingByUid = existingExpenses.filter { it.uid.isNotEmpty() }.associateBy { it.uid }
        val toInsert = ArrayList<com.amgm.personallog.data.model.Expense>()
        for (e in data.expenses) {
            // 同じ uid の既存の行がある場合、他の欄は既存を優先するが、家計簿で消した印(deletedByUser)だけは取り込む(OR)
            if (e.deletedByUser && e.uid.isNotEmpty()) {
                existingByUid[e.uid]?.takeIf { !it.deletedByUser }?.let { expenseDao.setDeletedByUser(it.id, true) }
            }
            if (e.groupId != null && e.groupId in duplicateGroups) {
                skipped++
                continue
            }
            val mappedReceipt = e.receiptId?.let { receiptIdMap[it] }
            val mappedGroup = e.groupId?.let { groupIdMap[it] }
            if (expenseIndex.isDuplicate(e, mappedReceipt, mappedGroup)) {
                skipped++
            } else {
                toInsert.add(
                    e.toEntity(linkedTime(e.receiptId, times)).copy(
                        id = 0,
                        receiptId = mappedReceipt,
                        groupId = mappedGroup,
                        receiptItemId = remapReceiptItemId(e.receiptItemId, itemIdMap),
                    ),
                )
                addedExpenses++
            }
        }
        if (toInsert.isNotEmpty()) expenseDao.insertAll(toInsert)

        // 割り勘の表(v7 以降)。人は uid で照合し(自分は端末の自分に合わせる)、表は「今回新しく入れた支払い」の分だけ入れる。
        // 既存の支払いと同じと判定されたもの(receiptIdMap にあって newReceiptIdMap に無いもの)は端末側の割り勘を残し、二重に入れない。
        // 既存の支払いに当たった分の割り勘は、バックアップ側を捨てる(端末側の割り勘を上書きしない)
        val personIdMap = importPersons(data.persons)
        importSplitRows(data, newReceiptIdMap, newItemIdMap, personIdMap)

        return ImportResult(addedEntries, addedPhotos, addedReceipts, addedExpenses, skipped, fileCount)
    }

    /**
     * バックアップの人を取り込み、バックアップの人 id -> 端末の人 id を返す(純粋な照合は planPersons)。
     * 同じ uid の人は既存を使い、自分は端末の自分に合わせる。端末に自分が無ければ作る(最後に必ず自分が1人いるようにする)。
     */
    private suspend fun importPersons(persons: List<BackupPerson>): Map<Long, Long> {
        val existing = splitDao.getPersons()
        val plan = planPersons(
            persons,
            existingByUid = existing.associate { it.uid to it.id },
            selfId = existing.firstOrNull { it.isSelf }?.id,
        )
        val map = HashMap<Long, Long>()
        for ((backupId, p) in plan) {
            map[backupId] = when (p) {
                is PersonPlan.Existing -> p.id
                is PersonPlan.New -> splitDao.insertPerson(p.person.toEntity().copy(id = 0))
            }
        }
        ensureSelf()
        return map
    }

    /** 端末に自分(isSelf)が1人も無ければ作る。 */
    private suspend fun ensureSelf() {
        if (splitDao.getSelf() == null) {
            splitDao.insertPerson(Person(name = SELF_NAME, isSelf = true, createdAt = System.currentTimeMillis()))
        }
    }

    /**
     * 割り勘の表を入れる。順番は 参加者 -> まとまり -> (品目のまとまり・割当・全体の入力・支払内訳・ラベル金額・シート)。
     * 参加者とまとまりは先に入れて新しい id を控え、そのあと他の行を付け替えて入れる。
     * receiptIdMap / itemIdMap は取り込み対象の分だけ渡す(既存に当たった支払いは渡さない)。
     * personIdMap はバックアップの人 id -> 端末の人 id(対応が無い人は参加者の personId を null にする)。
     */
    private suspend fun importSplitRows(
        data: BackupData,
        receiptIdMap: Map<Long, Long>,
        itemIdMap: Map<Long, Long>,
        personIdMap: Map<Long, Long>,
    ) {
        val participantIdMap = HashMap<Long, Long>()
        for ((backupParticipantId, participant) in remapParticipants(data.participants, receiptIdMap, personIdMap)) {
            participantIdMap[backupParticipantId] = splitDao.insertParticipant(participant)
        }
        val groupIdMap = HashMap<Long, Long>()
        for ((backupGroupId, group) in remapShareGroups(data.shareGroups, receiptIdMap)) {
            groupIdMap[backupGroupId] = splitDao.insertGroup(group)
        }
        val rows = remapSplitRows(data, receiptIdMap, itemIdMap, groupIdMap, participantIdMap)
        rows.sheets.forEach { splitDao.upsertSheet(it) }
        if (rows.groupItems.isNotEmpty()) splitDao.insertGroupItems(rows.groupItems)
        if (rows.itemShares.isNotEmpty()) splitDao.insertItemShares(rows.itemShares)
        if (rows.wholeShares.isNotEmpty()) splitDao.insertWholeShares(rows.wholeShares)
        if (rows.payers.isNotEmpty()) splitDao.insertPayers(rows.payers)
        if (rows.labelAmounts.isNotEmpty()) splitDao.insertLabelAmounts(rows.labelAmounts)
    }

    /** 一時ディレクトリに展開済みの画像を filesDir へ移す。 */
    private fun placeFiles(tempDir: File, mode: ImportMode) {
        val stamp = System.currentTimeMillis()
        if (mode == ImportMode.REPLACE) {
            val moved = ArrayList<Pair<File, File>>() // (元の場所, 退避先)
            try {
                for (name in BackupPaths.ROOT_DIRS) {
                    val dir = File(filesDir, name)
                    if (dir.exists()) {
                        val old = File(filesDir, "$name.old_$stamp")
                        if (!dir.renameTo(old)) throw java.io.IOException("既存フォルダを退避できません: $name")
                        moved.add(dir to old)
                    }
                }
                copyTree(tempDir, overwrite = true)
            } catch (e: Exception) {
                // 元に戻す
                for ((dir, old) in moved) {
                    dir.deleteRecursively()
                    old.renameTo(dir)
                }
                throw e
            }
            moved.forEach { it.second.deleteRecursively() }
        } else {
            copyTree(tempDir, overwrite = false)
        }
    }

    private fun copyTree(tempDir: File, overwrite: Boolean) {
        tempDir.walkTopDown().filter { it.isFile }.forEach { src ->
            val rel = src.relativeTo(tempDir).path.replace(File.separatorChar, '/')
            if (BackupPaths.safeRelativePath(rel) == null) return@forEach
            val dest = File(filesDir, rel)
            if (!isInside(filesDir, dest)) return@forEach
            if (dest.exists() && !overwrite) return@forEach
            dest.parentFile?.mkdirs()
            if (!src.renameTo(dest)) {
                src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            }
        }
    }

    private fun isInside(base: File, target: File): Boolean {
        val b = base.canonicalFile
        val t = target.canonicalFile
        return t.path.startsWith(b.path + File.separator)
    }

    companion object {
        private const val MAX_JSON_BYTES = 128 * 1024 * 1024

        /** 割り勘の「自分」を新しく作るときの名前(形式6以前のファイル、または自分が無いバックアップ)。 */
        private const val SELF_NAME = "自分"

        /** formatVersion が新しすぎる場合は拒否。 */
        fun checkVersion(formatVersion: Int) {
            if (formatVersion > BACKUP_FORMAT_VERSION) {
                throw BackupException(
                    "このバックアップは新しい形式(v$formatVersion)です。アプリを最新版に更新してから取り込んでください(このアプリは v$BACKUP_FORMAT_VERSION まで対応)。",
                )
            }
        }

        private fun readLimited(input: InputStream, limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) throw BackupException("data.json が大きすぎます")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
