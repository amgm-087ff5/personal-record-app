package com.amgm.personallog.ui.ledger

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.amgm.personallog.ui.receipt.RECEIPT_PICK_MAX
import com.amgm.personallog.ui.theme.PersonalLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 「＋ 記録」の3択(記録の方法)。説明文をタップしても、その方法が動くことを確かめる。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RecordMethodContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingEachCaptionInvokesMatchingOption() {
        var camera = 0
        var photos = 0
        var manual = 0
        composeRule.setContent {
            PersonalLogTheme {
                Column(Modifier.fillMaxSize()) {
                    RecordMethodContent(
                        onCamera = { camera++ },
                        onPickPhotos = { photos++ },
                        onManual = { manual++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText("カメラで撮って読み取ります。").performClick()
        assertEquals(1, camera)
        assertEquals(0, photos)
        assertEquals(0, manual)

        composeRule.onNodeWithText("最大${RECEIPT_PICK_MAX}枚。読み取った下書きを作ります。").performClick()
        assertEquals(1, camera)
        assertEquals(1, photos)
        assertEquals(0, manual)

        composeRule.onNodeWithText("日付や品目を自分で入力して作ります。").performClick()
        assertEquals(1, camera)
        assertEquals(1, photos)
        assertEquals(1, manual)
    }

    @Test
    fun tappingTitleInvokesMatchingOption() {
        var camera = 0
        composeRule.setContent {
            PersonalLogTheme {
                Column(Modifier.fillMaxSize()) {
                    RecordMethodContent(onCamera = { camera++ }, onPickPhotos = {}, onManual = {})
                }
            }
        }

        composeRule.onNodeWithText("レシートを撮る").performClick()
        assertEquals(1, camera)
    }
}
