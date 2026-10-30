package com.amgm.personallog.receipt

import com.amgm.personallog.data.model.PaymentStatus
import com.amgm.personallog.data.model.TotalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** DB v8 への移行で、既存データの totalSource(と空欄に戻す合計)を決める判定。 */
class TotalSourceInitTest {
    private fun raw(total: String?) =
        """{"date":"2026-10-09","time":"18:32","store":"A","items":[{"name":"牛乳","amount":198}]""" +
            (if (total != null) ""","total":$total""" else "") + "}"

    private val confirmed = PaymentStatus.CONFIRMED
    private val draft = PaymentStatus.DRAFT

    @Test
    fun printedTotalEqualsStored_isPrinted() {
        val d = TotalSourceInit.decide(confirmed, 748, raw("748"))
        assertEquals(TotalSourceDecision(748, TotalSource.PRINTED), d)
        // 下書きでも同じ
        assertEquals(TotalSourceDecision(748, TotalSource.PRINTED), TotalSourceInit.decide(draft, 748, raw("748")))
    }

    @Test
    fun printedTotalDiffersFromStored_isManual() {
        // 確定済みは、手入力か旧ボタン(品目の合計)か区別できないので unknown
        assertEquals(TotalSourceDecision(750, TotalSource.UNKNOWN), TotalSourceInit.decide(confirmed, 750, raw("748")))
        assertEquals(TotalSourceDecision(750, TotalSource.MANUAL), TotalSourceInit.decide(draft, 750, raw("748")))
    }

    @Test
    fun noPrintedTotal_draft_keepsManualValueThatDiffersFromLegacyFill() {
        // 品目の単純合計は198。保存値500は手で入れた値なので残す
        assertEquals(TotalSourceDecision(500, TotalSource.MANUAL), TotalSourceInit.decide(draft, 500, raw(null)))
        // 品目が2つ(198+100)の補完値と同じなら空欄に戻す
        val two = """{"date":"2026-10-09","store":"A","items":[{"name":"a","amount":198},{"name":"b","amount":100}]}"""
        assertNull(TotalSourceInit.decide(draft, 298, two).totalAmount)
        assertEquals(TotalSourceDecision(299, TotalSource.MANUAL), TotalSourceInit.decide(draft, 299, two))
    }

    @Test
    fun noPrintedTotal_confirmed_isItems() {
        assertEquals(TotalSourceDecision(198, TotalSource.ITEMS), TotalSourceInit.decide(confirmed, 198, raw(null)))
        assertEquals(TotalSourceDecision(198, TotalSource.ITEMS), TotalSourceInit.decide(confirmed, 198, raw("null")))
    }

    @Test
    fun noPrintedTotal_draft_isClearedToNull() {
        val d = TotalSourceInit.decide(draft, 198, raw(null))
        assertNull(d.totalAmount)
        assertEquals(TotalSource.UNKNOWN, d.totalSource)
        assertNull(TotalSourceInit.decide(draft, 198, raw("null")).totalAmount)
    }

    @Test
    fun emptyRawJson_draftWithZero_isClearedToNull() {
        val d = TotalSourceInit.decide(draft, 0, "")
        assertNull(d.totalAmount)
        assertEquals(TotalSource.UNKNOWN, d.totalSource)
    }

    @Test
    fun everythingElse_isUnknownAndKeepsTheValue() {
        // rawJson が空で、下書きでも保存値が0でない(手で入力した)
        assertEquals(TotalSourceDecision(500, TotalSource.UNKNOWN), TotalSourceInit.decide(draft, 500, ""))
        // rawJson が空の確定済み
        assertEquals(TotalSourceDecision(500, TotalSource.UNKNOWN), TotalSourceInit.decide(confirmed, 500, ""))
        assertEquals(TotalSourceDecision(0, TotalSource.UNKNOWN), TotalSourceInit.decide(confirmed, 0, ""))
        // 解釈できない応答で、保存値が0でない
        assertEquals(TotalSourceDecision(300, TotalSource.UNKNOWN), TotalSourceInit.decide(draft, 300, "oops"))
        // 未知の状態
        assertEquals(TotalSourceDecision(10, TotalSource.UNKNOWN), TotalSourceInit.decide("other", 10, raw(null)))
    }

    @Test
    fun unparsableDraftWithZero_isClearedToo() {
        assertNull(TotalSourceInit.decide(draft, 0, "読み取れませんでした").totalAmount)
    }
}
