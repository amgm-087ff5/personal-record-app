package com.amgm.personallog.ui.split

import com.amgm.personallog.data.repository.HeadcountChange
import com.amgm.personallog.data.repository.defaultBlankCount
import com.amgm.personallog.data.repository.headcountChangeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 保存時の支払い側の人数の確認と、初めて作る割り勘の既定の人数。 */
class SplitHeadcountRulesTest {

    @Test
    fun 人数が一致すれば確認は出さない() {
        assertNull(headcountChangeOf(headcount = 3, participantCount = 3))
        // null は 1 人として比べる
        assertNull(headcountChangeOf(headcount = null, participantCount = 1))
    }

    @Test
    fun 人数が違えば確認を出す() {
        assertEquals(HeadcountChange(from = 3, to = 2), headcountChangeOf(headcount = 3, participantCount = 2))
        // 支払い側が未入力(null)なら 1 人として比べる
        assertEquals(HeadcountChange(from = 1, to = 4), headcountChangeOf(headcount = null, participantCount = 4))
    }

    @Test
    fun 確認の文言() {
        assertEquals("支払い側の人数を3人→2人に変更しますか？", headcountConfirmText(HeadcountChange(from = 3, to = 2)))
    }

    @Test
    fun 初めての割り勘の既定は自分の他に人数ぶんの空欄() {
        assertEquals(2, defaultBlankCount(3))
        assertEquals(1, defaultBlankCount(2))
        // null や 1 なら自分だけ(既存どおり)
        assertEquals(0, defaultBlankCount(null))
        assertEquals(0, defaultBlankCount(1))
    }
}
