package com.amgm.personallog.receipt

import android.util.Base64
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.data.model.PaymentStatus
import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

sealed interface ScanOutcome {
    /** 読み取り成功。raw は Claude 応答の原文。 */
    data class Parsed(val parsed: ParsedReceipt, val raw: String) : ScanOutcome

    /** 応答は得たがJSONとして解釈できなかった(手入力で直す)。 */
    data class Unparsable(val raw: String, val reason: String) : ScanOutcome

    /** 送信前/通信のエラー(日本語メッセージ)。 */
    data class Failed(val message: String) : ScanOutcome
}

/** 画像1枚を Claude に送って結果を返す。送るのはレシート画像とプロンプトのみ。 */
class ReceiptScanner @Inject constructor(
    private val settings: SettingsRepository,
    private val store: ReceiptImageStore,
    private val client: ClaudeApiClient,
) {
    suspend fun scan(imagePath: String, adjust: ReceiptAdjust = ReceiptAdjust.NONE): ScanOutcome {
        val s = settings.settings.first()
        if (s.anthropicApiKey.isBlank()) {
            return ScanOutcome.Failed("APIキーが未設定です。設定タブでAPIキーを入れてください。")
        }
        val file = store.file(imagePath)
        if (!file.exists()) return ScanOutcome.Failed("画像ファイルが見つかりません。")
        return try {
            val body = withContext(Dispatchers.Default) {
                val bytes = ReceiptImageCodec.prepareForUpload(file, adjust)
                ClaudeRequest.buildBody(s.claudeModelId, Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            val response = client.post(s.anthropicApiKey.trim(), body)
            // 出力上限で途中で切れた応答は JSON が欠けている。解析失敗(手入力)ではなく撮り直しを促す
            if (ClaudeRequest.extractStopReason(response) == "max_tokens") {
                return ScanOutcome.Failed("読み取り結果が長すぎて途中で切れました。画像をトリミングするか、分けて撮り直してください")
            }
            val text = ClaudeRequest.extractText(response)
                ?: return ScanOutcome.Unparsable(response, "応答に本文がありませんでした")
            when (val r = ReceiptParser.parse(text)) {
                is ParseResult.Success -> ScanOutcome.Parsed(r.parsed, text)
                is ParseResult.Failure -> ScanOutcome.Unparsable(text, r.reason)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReceiptApiException) {
            ScanOutcome.Failed(e.message.orEmpty())
        } catch (e: OutOfMemoryError) {
            ScanOutcome.Failed("画像が大きすぎて処理できませんでした。")
        } catch (e: Exception) {
            ScanOutcome.Failed("読み取り中にエラーが発生しました: ${e.message.orEmpty()}")
        }
    }
}

/** 読み取り結果 <-> DBエンティティの変換(純Kotlin部分)。 */
object ReceiptMapper {
    fun purchasedAt(date: String, time: String?): String =
        if (time.isNullOrBlank()) date else "$date $time"

    /** 保存してある Claude 応答(rawJson)から、LLM が読んだ印字の合計を取り出す。読めなければ null。 */
    fun printedTotal(rawJson: String): Int? {
        if (rawJson.isBlank()) return null
        return (ReceiptParser.parse(rawJson) as? ParseResult.Success)?.parsed?.total
    }

    fun draftPayment(
        outcome: ScanOutcome,
        imagePath: String,
        now: Long,
        today: String = LocalDate.now().toString(),
        existingId: Long = 0,
        imageHash: String? = null,
        createdAt: Long = now,
        adjust: ReceiptAdjust = ReceiptAdjust.NONE,
    ): Pair<Payment, List<PaymentItem>> = when (outcome) {
        is ScanOutcome.Parsed -> {
            val p = outcome.parsed
            val items = p.items.flatMap { ReceiptUnits.fromParsed(it) }.map {
                PaymentItem(
                    paymentId = existingId, name = it.name, amount = it.amount, category = it.category,
                    taxRate = it.taxRate, priceIncludesTax = it.priceIncludesTax, memo = it.memo,
                )
            }
            Payment(
                id = existingId,
                purchasedAt = purchasedAt(p.date ?: today, p.time),
                storeName = p.store,
                // 合計は読めたときだけ入れる。読めなければ null(品目の合計で補完しない)
                totalAmount = p.total,
                totalSource = if (p.total != null) TotalSource.PRINTED else TotalSource.UNKNOWN,
                imagePath = imagePath,
                rawJson = outcome.raw,
                status = PaymentStatus.DRAFT,
                createdAt = createdAt,
                imageHash = imageHash,
                rotationDegrees = adjust.rotationDegrees,
                cropLeft = adjust.cropLeft, cropTop = adjust.cropTop,
                cropRight = adjust.cropRight, cropBottom = adjust.cropBottom,
                // 読めた人数。読めなければ null(割り勘の既定は1人)
                headcount = p.headcount,
            ) to items
        }
        is ScanOutcome.Unparsable -> Payment(
            id = existingId, purchasedAt = today, storeName = "", totalAmount = null,
            imagePath = imagePath, rawJson = outcome.raw, status = PaymentStatus.DRAFT, createdAt = createdAt,
            imageHash = imageHash,
            rotationDegrees = adjust.rotationDegrees,
            cropLeft = adjust.cropLeft, cropTop = adjust.cropTop, cropRight = adjust.cropRight, cropBottom = adjust.cropBottom,
        ) to emptyList()
        is ScanOutcome.Failed -> Payment(
            id = existingId, purchasedAt = today, storeName = "", totalAmount = null,
            imagePath = imagePath, rawJson = "", status = PaymentStatus.DRAFT, createdAt = createdAt,
            imageHash = imageHash,
            rotationDegrees = adjust.rotationDegrees,
            cropLeft = adjust.cropLeft, cropTop = adjust.cropTop, cropRight = adjust.cropRight, cropBottom = adjust.cropBottom,
        ) to emptyList()
    }
}
