package com.amgm.personallog.ui.receipt

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.repository.ReceiptRepository
import com.amgm.personallog.receipt.ReceiptDedup
import com.amgm.personallog.receipt.ReceiptImageHash
import com.amgm.personallog.receipt.ReceiptImageStore
import com.amgm.personallog.receipt.ReceiptMapper
import com.amgm.personallog.receipt.ReceiptScanner
import com.amgm.personallog.receipt.ScanOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
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
)

data class ReceiptListUiState(
    val imports: List<ImportItem> = emptyList(),
    val error: String? = null,
)

/** 取り込み元。写真ピッカーの Uri、または撮影で既に filesDir に保存した相対パス。 */
private class ImportSource(val uri: Uri? = null, val relativePath: String? = null)

@HiltViewModel
class ReceiptListViewModel @Inject constructor(
    private val repository: ReceiptRepository,
    private val store: ReceiptImageStore,
    private val scanner: ReceiptScanner,
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

    private val _openEvents = Channel<Long>(Channel.BUFFERED)

    /** 読み取り完了後に開くべきレシートID(1枚だけの取り込み)。 */
    val openEvents = _openEvents.receiveAsFlow()

    // 以下は ViewModel のメインスレッドからのみ触る
    private var nextKey = 1L
    private val sources = HashMap<Long, ImportSource>()

    /** 処理中の写真ピッカーのUri。同じ選択の連打で二重に投入しないため。 */
    private val inFlightUris = HashSet<String>()

    /** コピーとハッシュ計算の並行数上限。解析待ちと分けて、コピーが長く待たされないようにする。 */
    private val prepareLimiter = Semaphore(MAX_PARALLEL)

    /** 解析(Claude API)の同時実行上限。 */
    private val scanLimiter = Semaphore(MAX_PARALLEL)

    /** 重複確認と「ハッシュの予約」を直列化する。 */
    private val checkLock = Mutex()

    /** 同じ選択内の重複検出用: 画像ハッシュ -> 予約している項目。保存が終われば外す。 */
    private val claimedHashes = HashMap<String, Long>()

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
                patchItem(item.key) { it.copy(addAnyway = false, duplicateNote = null) }
                launchAnalyze(item.key, rel, item.imageHash)
            } else {
                sources.remove(item.key)
                patchItem(item.key) {
                    it.copy(phase = ImportPhase.DUPLICATE_SKIPPED, imagePath = null, addAnyway = false, duplicateNote = null)
                }
                if (rel != null) viewModelScope.launch(Dispatchers.IO) { store.delete(rel) }
            }
        }
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
                    val (path, h) = prepare(key, src) ?: return@launch
                    analyze(key, path, h, existingId = 0L)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(key, "取り込み中にエラーが発生しました: ${e.message.orEmpty()}")
            } finally {
                claimedHashes.values.remove(key)
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
                claimedHashes.containsKey(hash) -> "今回の選択に同じ画像があります"
                else -> {
                    claimedHashes[hash] = key
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
        val (result, id) = scanLimiter.withPermit {
            patchItem(key) { it.copy(phase = ImportPhase.SCANNING) }
            val scanned = scanner.scan(rel)
            val now = System.currentTimeMillis()
            val createdAt = if (existingId != 0L) (repository.getReceipt(existingId)?.createdAt ?: now) else now
            val (receipt, items) = ReceiptMapper.draftReceipt(
                scanned, rel, now,
                existingId = existingId,
                imageHash = hash,
                createdAt = createdAt,
            )
            scanned to repository.save(receipt, items)
        }
        finish(key, result, id)
    }

    private suspend fun finish(key: Long, outcome: ScanOutcome, id: Long) {
        val (phase, message) = when (outcome) {
            is ScanOutcome.Parsed -> ImportPhase.DONE to null
            is ScanOutcome.Unparsable -> ImportPhase.FAILED to "読み取り結果を解釈できませんでした。手入力で修正できます。"
            is ScanOutcome.Failed -> ImportPhase.FAILED to outcome.message
        }
        val autoOpen = find(key)?.autoOpen == true
        patchItem(key) { it.copy(phase = phase, receiptId = id, message = message) }
        if (autoOpen && outcome !is ScanOutcome.Failed) _openEvents.send(id)
    }

    private fun fail(key: Long, message: String) {
        patchItem(key) { it.copy(phase = ImportPhase.FAILED, message = message) }
    }

    private fun find(key: Long): ImportItem? = _state.value.imports.firstOrNull { it.key == key }

    private fun patchItem(key: Long, transform: (ImportItem) -> ImportItem) {
        _state.update { s -> s.copy(imports = s.imports.map { if (it.key == key) transform(it) else it }) }
    }
}
