package com.amgm.personallog.ui.split

import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.domain.split.SplitWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitSheetLogicTest {

    @Test
    fun 数字の解析は円記号・カンマ・全角を許す() {
        assertEquals(1234, parseAmountText("1,234"))
        assertEquals(1234, parseAmountText("¥1,234"))
        assertEquals(1234, parseAmountText("1234円"))
        assertEquals(1234, parseAmountText("１２３４"))
        assertEquals(30, parseAmountText("30%"))
    }

    @Test
    fun 空欄と読めない文字はnull() {
        assertNull(parseAmountText(""))
        assertNull(parseAmountText("   "))
        assertNull(parseAmountText("abc"))
        assertNull(parseAmountText("-"))
    }

    @Test
    fun 入力欄は数字とカンマだけを残す() {
        assertEquals("1,200", filterNumericInput("1,2a00円"))
        assertEquals("12", filterNumericInput("１２"))
    }

    @Test
    fun 金額の表示はカンマ区切りと円記号() {
        assertEquals("¥0", formatYenText(0))
        assertEquals("¥999", formatYenText(999))
        assertEquals("¥1,234", formatYenText(1234))
        assertEquals("¥1,234,567", formatYenText(1234567))
        assertEquals("-¥500", formatYenText(-500))
    }

    @Test
    fun メンバーの番号付けと表示名() {
        assertEquals("メンバー1", memberPlaceholder(1))
        val p = SplitParticipant(id = 5, receiptId = 1, sortOrder = 1)
        assertEquals("自分", displayNameOf(p, isSelf = true, names = emptyMap(), number = 1))
        assertEquals("メンバー2", displayNameOf(p, isSelf = false, names = mapOf(5L to "  "), number = 2))
        assertEquals("髙橋", displayNameOf(p, isSelf = false, names = mapOf(5L to " 髙橋 "), number = 2))
    }

    @Test
    fun ラベル一覧は空を除き重複なしで並び順を保つ() {
        val ps = listOf(
            SplitParticipant(id = 1, receiptId = 1, label = "新人", sortOrder = 1),
            SplitParticipant(id = 2, receiptId = 1, label = "", sortOrder = 2),
            SplitParticipant(id = 3, receiptId = 1, label = "新人", sortOrder = 3),
            SplitParticipant(id = 4, receiptId = 1, label = "常連", sortOrder = 4),
        )
        assertEquals(listOf("新人", "常連"), labelsOf(ps))
        assertEquals("その他", labelDisplay(""))
        assertEquals("新人", labelDisplay("新人"))
    }

    @Test
    fun 警告の文言はW1からW5() {
        val names = { _: Long -> "唐揚げ" }
        assertEquals(
            "唐揚げの割当が金額と合いません(差 ¥300)",
            warningText(SplitWarning.GroupAmountMismatch(1L, expected = 1000, allocated = 700), names),
        )
        assertEquals("全体の割当が合計と合いません", warningText(SplitWarning.WholeAmountMismatch(1000, 900)))
        assertEquals(
            "品目の合計と支払いの合計が ¥1,200 ずれています(読み漏れの可能性)",
            warningText(SplitWarning.ItemsTotalMismatch(-1200)),
        )
        assertEquals("支払者の合計が合いません", warningText(SplitWarning.PayersMismatch(1000, 900)))
        assertEquals("割当を解釈できません", warningText(SplitWarning.InvalidShares(null, "unknown person")))
    }

    @Test
    fun 警告の符号は重複を除いて並べる() {
        val list = listOf(
            SplitWarning.ItemsTotalMismatch(10),
            SplitWarning.InvalidShares(null, "x"),
            SplitWarning.ItemsTotalMismatch(-5),
            SplitWarning.WholeAmountMismatch(1, 2),
        )
        assertEquals("W2,W3,W5", warningCodes(list))
        assertEquals("", warningCodes(emptyList()))
    }

    @Test
    fun カテゴリ内訳の文字列() {
        assertEquals("食費 ¥1,200・日用品 ¥300", breakdownText(listOf("食費" to 1200, "日用品" to 300)))
        assertEquals("", breakdownText(emptyList()))
    }
}
