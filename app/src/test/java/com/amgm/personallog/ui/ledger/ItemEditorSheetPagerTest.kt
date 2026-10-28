package com.amgm.personallog.ui.ledger

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.ui.design.SheetHost
import com.amgm.personallog.ui.theme.PersonalLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 品目の編集シートのページ送りと、末尾の「追加」の操作を確かめる。
 * 2品目の支出を、2品目目(最後のページ)で開く。画面の寸法は 411dp x 891dp(Robolectric)。
 * 時計は手で進める(追加のアニメーションを数えないため)。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ItemEditorSheetPagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val group = ExpenseGroup(receiptId = 1L, date = "2026-10-01", amount = 300, createdAt = 0L)

    private val items = listOf(
        Expense(id = 1L, date = "2026-10-01", amount = 200, category = "食費", name = "牛乳", createdAt = 0L, groupId = 1L),
        Expense(id = 2L, date = "2026-10-01", amount = 100, category = "食費", name = "パン", createdAt = 0L, groupId = 1L),
    )

    private fun setSheet(startItemId: Long = 2L) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PersonalLogTheme {
                SheetHost {
                    ItemEditorSheet(
                        group = group,
                        items = items,
                        startItemId = startItemId,
                        adding = false,
                        splitPeople = 1,
                        onlyOneItem = false,
                        onDismiss = {},
                        onSaveItem = { _, _, _, _, _, _ -> },
                        onSetUnknown = { _, _, _ -> },
                        onDeleteItem = {},
                    )
                }
            }
        }
        settle()
    }

    /** アニメーションを最後まで進め、画面を落ち着かせる。 */
    private fun settle() {
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }

    /** 時間だけ進める(入力なしで待つ)。 */
    private fun advance(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.waitForIdle()
    }

    @Test
    fun waitingWithoutInputAddsNothing() {
        setSheet()
        composeRule.onNodeWithText("2 / 2").assertIsDisplayed()

        advance(3000)

        composeRule.onNodeWithText("2 / 2").assertIsDisplayed()
        composeRule.onAllNodesWithText("3 / 3").assertCountEquals(0)
    }

    @Test
    fun addButtonAddsExactlyOneItemAndStays() {
        setSheet()

        composeRule.onNodeWithText("＋ 追加").performClick()
        settle()
        advance(3000)

        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()

        advance(3000)

        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
        composeRule.onAllNodesWithText("4 / 4").assertCountEquals(0)
    }

    @Test
    fun largeLeftSwipePastLastItemAddsExactlyOneItem() {
        setSheet()

        // 最後の品目の上で、幅の 90% を左へ引いて離す
        composeRule.onNodeWithTag("itemPager", useUnmergedTree = true).performTouchInput {
            swipe(
                start = Offset(centerX + width * 0.45f, centerY),
                end = Offset(centerX - width * 0.45f, centerY),
                durationMillis = 300,
            )
        }
        settle()
        advance(3000)

        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()

        advance(3000)

        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
        composeRule.onAllNodesWithText("4 / 4").assertCountEquals(0)
    }

    @Test
    fun smallLeftSwipePastLastItemAddsNothing() {
        setSheet()

        // 幅の 10% だけ左へ引いて離す(閾値の 30% に届かない)
        composeRule.onNodeWithTag("itemPager", useUnmergedTree = true).performTouchInput {
            swipe(
                start = Offset(centerX + width * 0.05f, centerY),
                end = Offset(centerX - width * 0.05f, centerY),
                durationMillis = 300,
            )
        }
        settle()
        advance(3000)

        composeRule.onNodeWithText("2 / 2").assertIsDisplayed()
        composeRule.onAllNodesWithText("3 / 3").assertCountEquals(0)
    }

    @Test
    fun normalSwipeFromFirstToLastItemAddsNothing() {
        // 1件目から大きく左へ送って最後の品目へ移るだけ。追加の引きとは数えない(指を置いた時点で最後の品目ではない)
        setSheet(startItemId = 1L)
        composeRule.onNodeWithText("1 / 2").assertIsDisplayed()

        composeRule.onNodeWithTag("itemPager", useUnmergedTree = true).performTouchInput {
            swipe(
                start = Offset(centerX + width * 0.45f, centerY),
                end = Offset(centerX - width * 0.45f, centerY),
                durationMillis = 300,
            )
        }
        settle()
        advance(3000)

        composeRule.onNodeWithText("2 / 2").assertIsDisplayed()
        composeRule.onAllNodesWithText("3 / 3").assertCountEquals(0)
    }
}
