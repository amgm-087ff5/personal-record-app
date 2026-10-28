package com.amgm.personallog.ui.design

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.amgm.personallog.ui.theme.PersonalLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 登録の手順の表示。両方の手順の名前が出ること、今の手順だけが選ばれていることを確かめる。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RegisterStepsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsBothStepsWithStepOneCurrent() {
        composeRule.setContent {
            PersonalLogTheme {
                RegisterSteps(current = 1)
            }
        }
        composeRule.onNodeWithText("① 支払い").assertTextEquals("① 支払い").assertIsSelected()
        composeRule.onNodeWithText("② 自分の支出").assertTextEquals("② 自分の支出").assertIsNotSelected()
    }

    @Test
    fun stepTwoIsCurrentWhenAskedFor() {
        composeRule.setContent {
            PersonalLogTheme {
                RegisterSteps(current = 2)
            }
        }
        composeRule.onNodeWithText("① 支払い").assertIsNotSelected()
        composeRule.onNodeWithText("② 自分の支出").assertIsSelected()
    }
}
