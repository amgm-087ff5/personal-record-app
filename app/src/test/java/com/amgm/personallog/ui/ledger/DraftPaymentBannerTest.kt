package com.amgm.personallog.ui.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.ui.theme.PersonalLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 支出の画面の下書きの確定欄。「下書き」と案内が出ること、「確定」で確定の処理が呼ばれることを確かめる。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DraftPaymentBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun scannedDraft() = Receipt(
        purchasedAt = "2026-10-28", storeName = "店", totalAmount = 1000,
        imagePath = "receipt.jpg", rawJson = "", createdAt = 0L,
    )

    @Test
    fun showsDraftStampGuidanceAndCallsConfirmOnButtonClick() {
        var clicks = 0
        composeRule.setContent {
            PersonalLogTheme {
                DraftPaymentBanner(receipt = scannedDraft(), onConfirmClick = { clicks++ })
            }
        }
        composeRule.onNodeWithText("下書き").assertIsDisplayed()
        composeRule.onNodeWithText("読み取り結果を確認して確定してください").assertIsDisplayed()
        composeRule.onNodeWithText("確定").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }
}
