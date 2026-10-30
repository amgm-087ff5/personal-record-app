package com.amgm.personallog.ui.design

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.amgm.personallog.ui.theme.PersonalLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * BareField の入力。値を非同期に戻す画面(ViewModel の StateFlow)でも、戻ってきた古い値で
 * 入力中の文字を上書きしないこと、外からの整形・プログラム的な変更は反映されることを確かめる。
 * IME の変換中そのものは Robolectric では再現できないので、実機での確認が別に要る。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BareFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun field() = composeRule.onNode(hasSetTextAction())

    @Test
    fun staleValuesReturnedLaterDoNotOverwriteTyping() {
        val external = mutableStateOf("")
        val emitted = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PersonalLogTheme {
                // 外へ出した値を、すぐには戻さない(非同期の StateFlow を模す)。
                BareField(value = external.value, onValueChange = { emitted += it })
            }
        }
        composeRule.mainClock.advanceTimeBy(16)
        field().performTextInput("a")
        field().performTextInput("b")
        field().performTextInput("c")
        composeRule.mainClock.advanceTimeBy(16)
        assertEquals(listOf("a", "ab", "abc"), emitted)
        field().assertTextEquals("abc")

        // 遅れて古い値が順に戻ってきても、表示は連続入力のまま
        external.value = "a"
        composeRule.mainClock.advanceTimeBy(16)
        field().assertTextEquals("abc")
        external.value = "ab"
        composeRule.mainClock.advanceTimeBy(16)
        field().assertTextEquals("abc")
        external.value = "abc"
        composeRule.mainClock.advanceTimeBy(16)
        field().assertTextEquals("abc")

        // 追いついたあとの入力も続けられる
        field().performTextInput("d")
        composeRule.mainClock.advanceTimeBy(16)
        field().assertTextEquals("abcd")
        assertEquals("abcd", emitted.last())
    }

    @Test
    fun externalFormattingIsApplied() {
        val external = mutableStateOf("")
        composeRule.setContent {
            PersonalLogTheme {
                // ViewModel の filterAmount 相当: 数字以外を除く
                BareField(value = external.value, onValueChange = { external.value = it.filter(Char::isDigit) })
            }
        }
        field().performTextInput("12")
        field().assertTextEquals("12")
        // 数字と一緒に入った文字は取り除かれる
        field().performTextInput("3x")
        composeRule.waitForIdle()
        field().assertTextEquals("123")
        // 数字以外だけの入力は、外の値が変わらなくても表示が外の値に戻る
        field().performTextInput("z")
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        field().assertTextEquals("123")
    }

    @Test
    fun programmaticChangeIsApplied() {
        val external = mutableStateOf("")
        composeRule.setContent {
            PersonalLogTheme {
                BareField(value = external.value, onValueChange = { external.value = it })
            }
        }
        field().performTextInput("abc")
        field().assertTextEquals("abc")
        external.value = "xyz"
        composeRule.waitForIdle()
        field().assertTextEquals("xyz")
        external.value = ""
        composeRule.waitForIdle()
        field().assertTextEquals("")
    }

    @Test
    fun placeholderShowsOnlyWhileEmpty() {
        val external = mutableStateOf("")
        composeRule.setContent {
            PersonalLogTheme {
                BareField(value = external.value, onValueChange = { external.value = it }, placeholder = "店名など")
            }
        }
        composeRule.onNodeWithText("店名など").assertExists()
        field().performTextInput("x")
        composeRule.onNodeWithText("店名など").assertDoesNotExist()
    }

    // --- 状態ホルダ単体 ---

    @Test
    fun state_ignoresPendingAndAcceptsUnknown() {
        val s = BareFieldState("")
        assertTrue(s.edit(TextFieldValue("a", TextRange(1))))
        assertTrue(s.edit(TextFieldValue("ab", TextRange(2))))
        s.syncFrom("a") // 戻ってきた古い値
        assertEquals("ab", s.field.text)
        s.syncFrom("ab")
        assertEquals("ab", s.field.text)
        s.syncFrom("zzz") // 保留に無い値は受け入れ、末尾へ
        assertEquals("zzz", s.field.text)
        assertEquals(TextRange(3), s.field.selection)
    }

    @Test
    fun state_selectionOnlyChangeIsNotEmitted() {
        val s = BareFieldState("abc")
        assertFalse(s.edit(TextFieldValue("abc", TextRange(1))))
        assertEquals(TextRange(1), s.field.selection)
    }

    @Test
    fun state_shorterExternalValueClampsSelection() {
        val s = BareFieldState("12345")
        s.edit(TextFieldValue("12345a", TextRange(6)))
        s.syncFrom("1234") // 外の整形で文字数が減った(保留に無い値)
        assertEquals("1234", s.field.text)
        assertEquals(TextRange(4), s.field.selection)
    }

    @Test
    fun state_reconcileRestoresValueWhenExternalRejected() {
        val s = BareFieldState("12345")
        s.edit(TextFieldValue("12345a", TextRange(6)))
        s.syncFrom("12345") // 外は同じ値のまま(拒否)。この時点では表示を変えない
        assertEquals("12345a", s.field.text)
        s.reconcile("12345") // 入力が止まったあとに外の値へ合わせる
        assertEquals("12345", s.field.text)
        assertEquals(TextRange(5), s.field.selection)
    }

    @Test
    fun state_pendingIsBounded() {
        val s = BareFieldState("")
        var text = ""
        repeat(20) { text += "x"; s.edit(TextFieldValue(text, TextRange(text.length))) }
        // 直近の分は無視、上限より古い分は保留から消えているので外の変更として受け入れる
        s.syncFrom("x".repeat(19))
        assertEquals("x".repeat(20), s.field.text)
        s.syncFrom("x")
        assertEquals("x", s.field.text)
    }
}
