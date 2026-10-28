package com.amgm.personallog.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.amgm.personallog.ui.theme.PersonalLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 重ね表示のシート([SheetHost] と [InkSheet])の位置と操作を確かめる。
 * 画面の寸法は 411dp x 891dp(Robolectric)。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class InkSheetHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var density: Density

    /** シートを出しているかどうか。閉じると false になり、シートは画面から取り除かれる。 */
    private val sheetOpen = mutableStateOf(true)

    /** 中身の高さ。true なら 40 行(上端の余白の確認・最小化の確認)、false なら短い中身(下端の確認)。 */
    private fun setSheet(tall: Boolean, onDismiss: () -> Unit = {}) {
        // 位置の確認は時計を手で進めて行う(アニメーションの途中で数えないため)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            density = LocalDensity.current
            PersonalLogTheme {
                SheetHost {
                    Box(Modifier.fillMaxSize())
                    if (sheetOpen.value) {
                        InkSheet(
                            onDismiss = {
                                sheetOpen.value = false
                                onDismiss()
                            },
                            title = "テスト",
                            // 入力のシート: 下スワイプは閉じずに最小化する
                            swipeToDismiss = false,
                            modifier = if (tall) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                        ) {
                            if (tall) {
                                repeat(40) { Text("行 $it") }
                            } else {
                                Text("短い本文")
                                Text("決定", modifier = Modifier.clickable {})
                            }
                        }
                    }
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

    private fun dp(value: androidx.compose.ui.unit.Dp): Float = with(density) { value.toPx() }

    @Test
    fun closeButtonIsDisplayedBelowTopGapAndInsideRoot() {
        setSheet(tall = true)

        val close = composeRule.onNodeWithContentDescription("閉じる", useUnmergedTree = true)
        close.assertIsDisplayed()

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val button = close.fetchSemanticsNode().boundsInRoot
        // 上端は、ステータスバーの下に 72dp 以上の余白を残した位置にある
        assertTrue("閉じるの上端が 72dp 以上下にあること: top=${button.top}", button.top >= dp(72.dp))
        assertTrue("閉じるが画面の内側にあること", button.top >= root.top && button.bottom <= root.bottom)
    }

    @Test
    fun bottomOfShortSheetIsInsideRoot() {
        setSheet(tall = false)

        val action = composeRule.onNodeWithText("決定", useUnmergedTree = true)
        action.assertIsDisplayed()

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val button = action.fetchSemanticsNode().boundsInRoot
        assertTrue("短いシートの下端のボタンが画面の内側にあること: bottom=${button.bottom}", button.bottom <= root.bottom)
    }

    @Test
    fun swipeDownMinimizesAndTapReopens() {
        setSheet(tall = true)
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithText("テスト", useUnmergedTree = true)
        val expandedTop = title.fetchSemanticsNode().boundsInRoot.top

        // 見出しの上で下へスワイプする。速く大きく動かすので、最小化の段まで下がる
        val distance = dp(400.dp)
        title.performTouchInput {
            swipeDown(startY = centerY, endY = centerY + distance)
        }
        settle()

        // 最小化: 見出しは画面の下の方に残り、「タップで開く」が出る
        composeRule.onNodeWithText("タップで開く", useUnmergedTree = true).assertIsDisplayed()
        val minimizedTop = title.fetchSemanticsNode().boundsInRoot.top
        assertTrue("最小化で見出しが下に動くこと", minimizedTop > expandedTop)
        assertTrue("最小化した見出しが画面の下端から 80dp 以内にあること", root.bottom - minimizedTop <= dp(80.dp))

        // 見出しをタップすると開き直る
        title.performClick()
        settle()
        composeRule.onAllNodesWithText("タップで開く", useUnmergedTree = true).assertCountEquals(0)
        val reopenedTop = title.fetchSemanticsNode().boundsInRoot.top
        assertTrue("開き直すと見出しが上に戻ること", reopenedTop < minimizedTop)
    }

    @Test
    fun closeButtonCallsOnDismissAndRemovesSheet() {
        var dismissCount = 0
        setSheet(tall = false, onDismiss = { dismissCount++ })

        composeRule.onNodeWithContentDescription("閉じる", useUnmergedTree = true).performClick()
        settle()

        assertEquals(1, dismissCount)
        composeRule.onAllNodesWithText("テスト", useUnmergedTree = true).assertCountEquals(0)
    }

    /** 押せない説明文と、押せる行を1つずつ置いたシート。[swipeToDismiss] で種類を切り替える。 */
    private fun setSheetWithText(swipeToDismiss: Boolean, onDismiss: () -> Unit = {}, onRow: () -> Unit = {}) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            density = LocalDensity.current
            PersonalLogTheme {
                SheetHost {
                    Box(Modifier.fillMaxSize())
                    if (sheetOpen.value) {
                        InkSheet(
                            onDismiss = {
                                sheetOpen.value = false
                                onDismiss()
                            },
                            title = "テスト",
                            swipeToDismiss = swipeToDismiss,
                        ) {
                            Text("説明")
                            Text("押せる行", modifier = Modifier.clickable { onRow() })
                        }
                    }
                }
            }
        }
        settle()
    }

    @Test
    fun tapOnNonClickableTextDoesNotDismissSheet() {
        var dismissCount = 0
        setSheetWithText(swipeToDismiss = true, onDismiss = { dismissCount++ })

        composeRule.onNodeWithText("説明", useUnmergedTree = true).performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        assertEquals(0, dismissCount)
        composeRule.onNodeWithText("テスト", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tapOnNonClickableTextDoesNotMinimizeInputSheet() {
        var dismissCount = 0
        setSheetWithText(swipeToDismiss = false, onDismiss = { dismissCount++ })

        composeRule.onNodeWithText("説明", useUnmergedTree = true).performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        assertEquals(0, dismissCount)
        composeRule.onAllNodesWithText("タップで開く", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText("テスト", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun clickableRowInsideSheetStillTriggersOnClick() {
        var rowClicks = 0
        var dismissCount = 0
        setSheetWithText(swipeToDismiss = true, onDismiss = { dismissCount++ }, onRow = { rowClicks++ })

        composeRule.onNodeWithText("押せる行", useUnmergedTree = true).performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        assertEquals(1, rowClicks)
        assertEquals(0, dismissCount)
    }
}
