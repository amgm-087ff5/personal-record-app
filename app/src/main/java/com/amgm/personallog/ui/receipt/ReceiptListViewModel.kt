package com.amgm.personallog.ui.receipt

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.repository.ReceiptRepository
import com.amgm.personallog.data.repository.SplitRepository
import com.amgm.personallog.receipt.ReceiptAdjust
import com.amgm.personallog.receipt.ReceiptDedup
import com.amgm.personallog.receipt.ReceiptImageHash
import com.amgm.personallog.receipt.ReceiptImageStore
import com.amgm.personallog.receipt.ReceiptMapper
import com.amgm.personallog.receipt.ReceiptOrphans
import com.amgm.personallog.receipt.ReceiptScanner
import com.amgm.personallog.receipt.ReceiptUnits
import com.amgm.personallog.receipt.ScanOutcome
import com.amgm.personallog.receipt.adjust
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** 同時に走らせる解析(Claude API)の上限。コピー/ハッシュ計算の並行数も同じ値にする。 */
private const val MAX_PARALLEL = 3

/** 写真ピッカーで一度に選べる上限。 */
const val RECEIPT_PICK_MAX = 20

/** 取り込みの各項目の状態。 */
enum class ImportPhase(val active: Boolean) {
    /** 並行数の空きを待っている */
    WAITING(true),
    /** 端末内へのコピーとハッシュ計算、重複確認中 */
    PREPARING(true),
    /** Claude で解析中 */
    SCANNING(true),
    /** 解析済みの下書きとして保存済み */
    DONE(false),
    /** 失敗(タップで再試行。下書きがあれば下書きを読み直す) */
    FAILED(false),
    /** 重複の可能性があり、ユーザーの選択待ち。画像はまだ解析していない */
    DUPLICATE_PENDING(true),
    /** 重複としてスキップ(起票せず、コピーした画像も削除済み) */
    DUPLICATE_SKIPPED(false),
    /** コピーと重複確認が済み、向き・範囲の確認画面での補正待ち。解析はまだしていない */
    ADJUST_PENDING(true),
    /** 補正画面で取り消した(起票せず、コピーした画像も削除済み) */
    CANCELLED(false),
}

data class ImportItem(
    val key: Long,
    val phase: ImportPhase = ImportPhase.WAITING,
    /** filesDir からの相対パス(コピー後) */
    val imagePath: String? = null,
    val imageHash: String? = null,
    /** 下書きとして保存済みのレシートID */
    val receiptId: Long? = null,
    val message: String? = null,
    /** 重複の相手の要約(DUPLICATE_PENDING のとき) */
    val duplicateNote: String? = null,
    /** 確認ダイアログで「それでも追加」にチェックしたか */
    val addAnyway: Boolean = false,
    /** 1枚だけの取り込みなら、読み取り後に詳細画面を開く */
    val autoOpen: Boolean = false,
    /** 補正画面で決めた回転・トリミング。解析と下書き保存で使う */
    val adjust: ReceiptAdjust = ReceiptAdjust.NONE,
) {
    /** 補正画面の前の段階(待機・コピー・重複確認)にいるか。解析済みの再試行は含まない。 */
    val beforeAdjust: Boolean
        get() = phase == ImportPhase.PREPARING || (phase == ImportPhase.WAITING && imagePath == null)
}

data class ReceiptListUiState(
    val imports: List<ImportItem> = emptyList(),
    val error: String? = null,
) {
    /** 補正待ちの項目(取り込み順)。 */
    val adjustItems: List<ImportItem> get() = imports.filter { it.phase == ImportPhase.ADJUST_PENDING }

    /**
     * 補正画面を出してよいか。重複の確認待ちが先に解決され、コピー中の項目も無くなってから出す。
     */
    val adjustReady: Boolean
        get() = adjustItems.isNotEmpty() &&
            imports.none { it.phase == ImportPhase.DUPLICATE_PENDING || it.beforeAdjust }
}

/** 取り込み元。写真ピッカーの Uri、または撮影で既に filesDir に保存した相対パス。 */
private class ImportSource(val uri: Uri? = null, val relativePath: String? = null)

@HiltViewModel
class ReceiptListViewModel @Inject constructor(
    private val repository: ReceiptRepository,
    private val store: ReceiptImageStore,
    private val scanner: ReceiptScanner,
    private val splitRepository: SplitRepository,
) : ViewModel() {

    val receipts: StateFlow<List<Receipt>> = repository.observeReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 同じ内容の別レシートがある「後から作られた側」 -> 原本。一覧の警告に使う。 */
    val possibleDuplicates: StateFlow<Map<Long, Receipt>> =
        combine(receipts, repository.observeNotDuplicates()) { list, notDup ->
            // 「重複ではない」と判断した組は警告しない
            ReceiptDedup.laterDuplicates(list, ReceiptDedup.notDuplicateSet(notDup))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _state = MutableStateFlow(ReceiptListUiState())
    val state: StateFlow<ReceiptListUiState> = _state.asStateFlow()

    private val _pendingOpen = MutableStateFlow<Long?>(null)

    /**
     * 読み取り完了後に開くべきレシートID(1枚だけの取り込み)。画面が受け取ったら [consumeOpen] で消す。
     * イベントとして溜めず、常に高々1件。
     */
    val pendingOpen: StateFlow<Long?> = _pendingOpen.asStateFlow()

    fun consumeOpen(id: Long) {
        _pendingOpen.compareAndSet(id, null)
    }

    // 以下は ViewModel のメインスレッドからのみ触る
    private var nextKey = 1L
    private val sources = HashMap<Long, ImportSource>()

    /** 補正画面の表示中に読み取りが終わった、開き待ちのレシートID。補正画面が閉じたら開く。 */
    private val deferredOpen = DeferredOpen()

    /** 処理中の写真ピッカーのUri。同じ選択の連打で二重に投入しないため。 */
    private val inFlightUris = HashSet<String>()

    /** コピーとハッシュ計算の並行数上限。解析待ちと分けて、コピーが長く待たされないようにする。 */
    private val prepareLimiter = Semaphore(MAX_PARALLEL)

    /** 解析(Claude API)の同時実行上限。 */
    private val scanLimiter = Semaphore(MAX_PARALLEL)

    /** 重複確認と「ハッシュの予約」を直列化する。 */
    private val checkLock = Mutex()

    /** 同じ選択内の重複検出用: 画像ハッシュ -> 予約している項目。保存が終われば外す。 */
    private val claimedHashes = HashClaims()

    init {
        cleanupOrphanImages()
    }

    /**
     * 補正待ちの間にプロセスが終了すると、コピーした画像がどのレシートにも載らないまま残る。
     * filesDir/receipts のうち、どのレシートからも参照されておらず、作られてから1時間以上経ち、
     * 今取り込み中でもないファイルを消す。
     */
    private fun cleanupOrphanImages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = store.listStored()
                if (files.isEmpty()) return@launch
                val referenced = repository.allReceipts().map { it.imagePath }
                // 取り込み中の項目は、DB を読んだ後に確認する(読んでいる間に増えた分も守る)
                val inUse = _state.value.imports.mapNotNull { it.imagePath }
                val targets = ReceiptOrphans.select(files, referenced, inUse, System.currentTimeMillis())
                targets.forEach { store.delete(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 掃除は付随の処理。失敗しても取り込みには影響させない
            }
        }
    }

    /**
     * 手入力の支払いを下書きとして保存し、その詳細画面を開く(レシート画像は無い)。
     * 不正な入力は保存せず、理由を返す(null なら保存を始めた)。品目は詳細画面で足す。
     */
    fun addManual(date: String, time: String, store: String, total: String): String? {
        val receipt = when (val built = ManualReceipt.build(date, time, store, total, System.currentTimeMillis())) {
            is ManualReceipt.Result.Invalid -> return built.message
            is ManualReceipt.Result.Ok -> built.receipt
        }
        viewModelScope.launch {
            try {
                // 合計がある手入力は確定として保存する(詳細画面の確定と同じ saveAndConfirm)。合計が無いものは下書き
                val id = if (receipt.status == ReceiptStatus.CONFIRMED) {
                    repository.saveAndConfirm(receipt, emptyList())
                } else {
                    repository.save(receipt, emptyList())
                }
                _pendingOpen.value = id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "保存に失敗しました: ${e.message.orEmpty()}") }
            }
        }
        return null
    }

    fun newCameraPath(): String = store.newRelativePath()

    fun cameraUri(relativePath: String): Uri = store.uriFor(relativePath)

    fun discardCapture(relativePath: String) = store.delete(relativePath)

    fun dismissError() = _state.update { it.copy(error = null) }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    /** 撮影済みの画像(filesDir相対パス)を取り込む。 */
    fun onCaptured(relativePath: String) {
        enqueue(listOf(ImportSource(relativePath = relativePath)))
    }

    /** 写真ピッカーで選んだ画像を取り込む。処理中と同じ Uri は二重に入れない。 */
    fun onPicked(uris: List<Uri>) {
        val fresh = uris.distinct().filter { inFlightUris.add(it.toString()) }
        if (fresh.isEmpty()) return
        enqueue(fresh.map { ImportSource(uri = it) })
    }

    /** 失敗した項目を再試行する。下書きがあれば、その下書きを読み直す。 */
    fun retry(key: Long) {
        val item = find(key) ?: return
        val src = sources[key] ?: return
        if (item.phase != ImportPhase.FAILED) return
        src.uri?.let { inFlightUris.add(it.toString()) }
        patchItem(key) { it.copy(phase = ImportPhase.WAITING, message = null) }
        launchItem(key, src)
    }

    fun toggleAddAnyway(key: Long) = patchItem(key) { it.copy(addAnyway = !it.addAnyway) }

    /**
     * 重複確認の結果を適用する。
     * addChecked=true なら「それでも追加」にチェックしたものだけ解析し、残りはスキップする。
     * addChecked=false(既定)なら確認待ちを全部スキップする。
     */
    fun resolveDuplicates(addChecked: Boolean) {
        val pending = _state.value.imports.filter { it.phase == ImportPhase.DUPLICATE_PENDING }
        for (item in pending) {
            val rel = item.imagePath
            if (addChecked && item.addAnyway && rel != null) {
                // 「それでも追加」も補正画面を通してから解析する。同じ選択内の他の項目に対しては、これも予約する
                item.imageHash?.let { claimedHashes.claimAnyway(it, item.key) }
                patchItem(item.key) {
                    it.copy(addAnyway = false, duplicateNote = null, phase = ImportPhase.ADJUST_PENDING)
                }
            } else {
                sources.remove(item.key)
                patchItem(item.key) {
                    it.copy(phase = ImportPhase.DUPLICATE_SKIPPED, imagePath = null, addAnyway = false, duplicateNote = null)
                }
                if (rel != null) viewModelScope.launch(Dispatchers.IO) { store.delete(rel) }
            }
        }
    }

    /** 補正画面で1枚の補正値を変える(補正待ちの項目だけ)。 */
    fun setAdjust(key: Long, adjust: ReceiptAdjust) {
        patchItem(key) { if (it.phase == ImportPhase.ADJUST_PENDING) it.copy(adjust = adjust.sanitized()) else it }
    }

    /** 「この補正で解析」: 補正待ちの全項目を、それぞれの補正で解析へ進める。 */
    fun analyzeAdjusted() = startAnalysis(clearAdjust = false)

    /** 「補正なしで全部解析」: 補正値を捨てて、補正待ちの全項目を解析へ進める。 */
    fun analyzeWithoutAdjust() = startAnalysis(clearAdjust = true)

    private fun startAnalysis(clearAdjust: Boolean) {
        val pending = _state.value.adjustItems.filter { it.imagePath != null }
        val keys = pending.map { it.key }.toSet()
        // 補正画面が途中で崩れないよう、全項目を一度に待機へ移す
        _state.update { s ->
            s.copy(imports = s.imports.map {
                if (it.key in keys) {
                    it.copy(phase = ImportPhase.WAITING, adjust = if (clearAdjust) ReceiptAdjust.NONE else it.adjust)
                } else it
            })
        }
        for (item in pending) {
            val rel = item.imagePath ?: continue
            launchAnalyze(item.key, rel, item.imageHash)
        }
        flushDeferredOpens()
    }

    /** 補正画面が閉じていれば、保留していた詳細画面への移動を実行する。 */
    private fun flushDeferredOpens() {
        if (_state.value.adjustItems.isNotEmpty()) return
        deferredOpen.release(adjustActive = false)?.let { _pendingOpen.value = it }
    }

    /** 「取り消し」: 補正待ちの全項目の取り込みをやめ、コピーした画像を消す。 */
    fun cancelAdjust() {
        val pending = _state.value.adjustItems
        for (item in pending) {
            val rel = item.imagePath
            sources.remove(item.key)
            claimedHashes.release(item.key)
            patchItem(item.key) { it.copy(phase = ImportPhase.CANCELLED, imagePath = null) }
            if (rel != null) viewModelScope.launch(Dispatchers.IO) { store.delete(rel) }
        }
        flushDeferredOpens()
    }

    /** 終わった項目を一覧から消す(処理中・確認待ちが無いときだけ呼ぶ想定)。 */
    fun clearFinished() {
        val finished = _state.value.imports.filterNot { it.phase.active }.map { it.key }
        finished.forEach { sources.remove(it) }
        _state.update { s -> s.copy(imports = s.imports.filter { it.phase.active }) }
    }

    private fun enqueue(list: List<ImportSource>) {
        val autoOpen = list.size == 1
        val items = list.map { src ->
            val key = nextKey++
            sources[key] = src
            ImportItem(key = key, autoOpen = autoOpen)
        }
        _state.update { it.copy(imports = it.imports + items, error = null) }
        list.zip(items).forEach { (src, item) -> launchItem(item.key, src) }
    }

    /** 1件分の取り込みを独立したコルーチンで行う。例外は項目ごとに閉じ込め、他の項目へ影響させない。 */
    private fun launchItem(key: Long, src: ImportSource) {
        viewModelScope.launch {
            try {
                val item = find(key)
                val rel = item?.imagePath
                val hash = item?.imageHash
                val savedId = item?.receiptId
                if (savedId != null && rel != null && hash != null) {
                    // 再試行: 既に保存済みの下書きを読み直す
                    analyze(key, rel, hash, existingId = savedId)
                } else {
                    // 重複確認まで済んだら、解析は補正画面の後(ADJUST_PENDING で止める)
                    val (path, _) = prepare(key, src) ?: return@launch
                    patchItem(key) { it.copy(phase = ImportPhase.ADJUST_PENDING, imagePath = path) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(key, "取り込み中にエラーが発生しました: ${e.message.orEmpty()}")
            } finally {
                // 補正待ちの間は同じ選択内の重複検出のため予約を残す(解析後・取り消しで外す)
                if (find(key)?.phase != ImportPhase.ADJUST_PENDING) claimedHashes.release(key)
                src.uri?.let { inFlightUris.remove(it.toString()) }
            }
        }
    }

    /** 重複確認で「それでも追加」を選んだ項目の解析。 */
    private fun launchAnalyze(key: Long, rel: String, hash: String?) {
        viewModelScope.launch {
            try {
                analyze(key, rel, hash, existingId = 0L)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(key, "読み取り中にエラーが発生しました: ${e.message.orEmpty()}")
            } finally {
                claimedHashes.release(key)
            }
        }
    }

    /**
     * コピー → ハッシュ → 重複確認。取り込めたら (相対パス, ハッシュ)。
     * 重複の可能性があれば確認待ち(DUPLICATE_PENDING)にして null を返す。
     */
    private suspend fun prepare(key: Long, src: ImportSource): Pair<String, String>? {
        val rel: String = prepareLimiter.withPermit {
            patchItem(key) { it.copy(phase = ImportPhase.PREPARING) }
            src.relativePath ?: find(key)?.imagePath ?: store.importFrom(
                requireNotNull(src.uri) { "取り込み元がありません" },
            )
        }
        patchItem(key) { it.copy(imagePath = rel) }

        val hash = withContext(Dispatchers.IO) { ReceiptImageHash.sha256Hex(store.file(rel)) }
        patchItem(key) { it.copy(imageHash = hash) }

        val note: String? = checkLock.withLock {
            val saved = findSavedByHash(hash)
            when {
                saved != null -> "既存のレシート: ${ReceiptDedup.summary(saved)}"
                claimedHashes.isClaimed(hash) -> "今回の選択に同じ画像があります"
                else -> {
                    claimedHashes.claimIfAbsent(hash, key)
                    null
                }
            }
        }
        if (note != null) {
            patchItem(key) { it.copy(phase = ImportPhase.DUPLICATE_PENDING, duplicateNote = note) }
            return null
        }
        return rel to hash
    }

    /**
     * 保存済みレシートから同じハッシュのものを探す。
     * ハッシュ未記録の既存レシートは、ここで画像から計算して埋める(checkLock の内側でのみ呼ぶ)。
     */
    private suspend fun findSavedByHash(hash: String): Receipt? {
        for (r in repository.receiptsWithoutImageHash()) {
            // 手入力の支払いは画像が無い。空のパスは filesDir そのものを指すので、計算しない
            if (r.imagePath.isBlank()) continue
            val h = withContext(Dispatchers.IO) {
                try {
                    ReceiptImageHash.sha256Hex(store.file(r.imagePath))
                } catch (e: IOException) {
                    null
                }
            } ?: continue
            repository.setImageHashIfMissing(r.id, h)
        }
        return repository.receiptsByImageHash(hash).firstOrNull()
    }

    /** Claude で解析して下書きを保存する。existingId があればその下書きを上書きする(読み直し)。 */
    private suspend fun analyze(key: Long, rel: String, hash: String?, existingId: Long) {
        patchItem(key) { it.copy(phase = ImportPhase.WAITING, message = null) }
        // 読み直しは、取り込み時の補正ではなく、下書き(existingId)に保存してある最新の補正を使う
        val adjust = (if (existingId != 0L) repository.getReceipt(existingId)?.adjust() else null)
            ?: find(key)?.adjust ?: ReceiptAdjust.NONE
        val (result, id) = scanLimiter.withPermit {
            patchItem(key) { it.copy(phase = ImportPhase.SCANNING) }
            val scanned = scanner.scan(rel, adjust)
            val now = System.currentTimeMillis()
            val createdAt = if (existingId != 0L) (repository.getReceipt(existingId)?.createdAt ?: now) else now
            val (receipt, items) = ReceiptMapper.draftReceipt(
                scanned, rel, now,
                existingId = existingId,
                imageHash = hash,
                createdAt = createdAt,
                adjust = adjust,
            )
            val sharedFlags = (scanned as? ScanOutcome.Parsed)?.let { parsed ->
                parsed.receipt.items.flatMap { ReceiptUnits.fromParsed(it) }.map { it.shared }
            }.orEmpty()
            val savedId = repository.save(receipt, items, sharedFlags = sharedFlags)
            // 共通費の提案は保存の中でまとまりに反映済み。家計簿を今の割り勘に合わせる
            repository.syncLedger(savedId)
            scanned to savedId
        }
        finish(key, result, id)
    }

    /**
     * 読み取りが共通費(割り勘の提案)とした品目を、割り勘のまとまりに反映する。
     * 保存した品目(id順)と読み取りの行は同じ順に並ぶので、件数が合うときだけ対応させる。
     */
    private suspend fun suggestShared(receiptId: Long, outcome: ScanOutcome) {
        val parsed = outcome as? ScanOutcome.Parsed ?: return
        val rows = parsed.receipt.items.flatMap { ReceiptUnits.fromParsed(it) }
        val saved = repository.getItems(receiptId)
        if (saved.size != rows.size) return
        val hints = saved.zip(rows).associate { (item, row) -> item.id to row.shared }
        splitRepository.ensureGroups(receiptId, hints)
        // まとまりを組み直したかもしれないので、家計簿を今の割り勘に合わせる(割り勘の自分の分を反映する)
        repository.syncLedger(receiptId)
    }

    private suspend fun finish(key: Long, outcome: ScanOutcome, id: Long) {
        val (phase, message) = when (outcome) {
            is ScanOutcome.Parsed -> ImportPhase.DONE to null
            is ScanOutcome.Unparsable -> ImportPhase.FAILED to "読み取り結果を解釈できませんでした。手入力で修正できます。"
            is ScanOutcome.Failed -> ImportPhase.FAILED to outcome.message
        }
        val autoOpen = find(key)?.autoOpen == true
        patchItem(key) { it.copy(phase = phase, receiptId = id, message = message) }
        if (autoOpen && outcome !is ScanOutcome.Failed) {
            // 別の取り込みの補正画面を出している間は、詳細画面への移動を保留する(閉じた後に開く)
            deferredOpen.offer(id, adjustActive = _state.value.adjustItems.isNotEmpty())?.let { _pendingOpen.value = it }
        }
    }

    private fun fail(key: Long, message: String) {
        patchItem(key) { it.copy(phase = ImportPhase.FAILED, message = message) }
    }

    private fun find(key: Long): ImportItem? = _state.value.imports.firstOrNull { it.key == key }

    private fun patchItem(key: Long, transform: (ImportItem) -> ImportItem) {
        _state.update { s -> s.copy(imports = s.imports.map { if (it.key == key) transform(it) else it }) }
    }
}
