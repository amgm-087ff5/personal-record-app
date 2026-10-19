package com.amgm.personallog.ui.receipt

import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptStatus
import com.amgm.personallog.data.model.TotalSource
import com.amgm.personallog.receipt.ReceiptCalc
import com.amgm.personallog.receipt.ReceiptMapper

/**
 * 手入力の支払い(レシート画像なし)を作る純ロジック。
 * レシートと同じ表(Receipt)に、imagePath が空の下書きとして作る。保存は既存の ReceiptRepository.save に任せる。
 */
object ManualReceipt {

    sealed interface Result {
        data class Ok(val receipt: Receipt) : Result
        data class Invalid(val message: String) : Result
    }

    /**
     * 入力から下書きの Receipt を作る。
     * - date: 必須(yyyy-MM-dd)。time: 任意(HH:mm)。purchasedAt は既存と同じ "yyyy-MM-dd" または "yyyy-MM-dd HH:mm"。
     * - store: 任意。total: 任意(円・0以上の整数)。空は未入力(null)。
     * - 確定の判定: 合計か品目があれば確定(CONFIRMED)、どちらも無ければ下書き(DRAFT)。ここでは品目は無いので、合計の有無で決まる。
     *   確定の検証(ReceiptRepository.confirm)は合計を必須にしているため、確定の行には必ず合計が入る。
     * - totalSource は MANUAL(空の合計でも MANUAL のまま。詳細画面の「未入力」表示に合わせる)。
     */
    fun build(date: String, time: String, store: String, total: String, now: Long): Result {
        val d = date.trim()
        if (!ReceiptCalc.isValidDate(d)) {
            return Result.Invalid("日付は yyyy-MM-dd 形式(例 2026-10-10)で入力してください。")
        }
        val t = time.trim()
        if (t.isNotEmpty() && !ReceiptCalc.isValidTime(t)) {
            return Result.Invalid("時刻は HH:mm 形式(例 14:05)で入力するか、空にしてください。")
        }
        val totalText = total.trim()
        val amount: Int? = if (totalText.isEmpty()) {
            null
        } else {
            val parsed = totalText.toIntOrNull()
            if (parsed == null || parsed < 0) {
                return Result.Invalid("合計は0以上の整数(円)で入力するか、空にしてください。")
            }
            parsed
        }
        return Result.Ok(
            Receipt(
                purchasedAt = ReceiptMapper.purchasedAt(d, t.takeIf { it.isNotEmpty() }),
                storeName = store.trim(),
                totalAmount = amount,
                imagePath = "",
                rawJson = "",
                status = if (amount != null) ReceiptStatus.CONFIRMED else ReceiptStatus.DRAFT,
                createdAt = now,
                totalSource = TotalSource.MANUAL,
            ),
        )
    }
}
