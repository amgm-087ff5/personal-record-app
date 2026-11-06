package com.amgm.personallog.v4

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.amgm.personallog.v4.data.FixedClock
import com.amgm.personallog.v4.data.Seed
import com.amgm.personallog.v4.data.Store
import com.amgm.personallog.v4.data.dateOf
import com.amgm.personallog.v4.data.dayKeyByDate
import com.amgm.personallog.v4.data.dayKeyOf
import com.amgm.personallog.v4.data.settled
import com.amgm.personallog.v4.screens.create.openPaymentSheet
import com.amgm.personallog.v4.screens.detail.deleteActivity
import com.amgm.personallog.v4.screens.plan.recordEvent
import com.amgm.personallog.v4.screens.plan.toggleTask
import com.amgm.personallog.v4.ui.AppRoot
import com.amgm.personallog.v4.ui.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * 画面の操作の流れ(シートの保存・完了・記録・削除・設定・ナビ)。
 * 見本データ + いま = 2026-10-31 13:20。保存は端末に書かない(Store(null))ので取り消しはメモリ上で確かめる。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi", application = Application::class)
class FlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var store: Store
    private lateinit var app: AppState

    private fun setup() {
        store = Store(null, FixedClock())
        store.setForTest(Seed.data().settled())
        app = AppState(store)
        rule.mainClock.autoAdvance = false
        rule.setContent { AppRoot(app) }
        idle()
    }

    /** 時間を進めて描き直す(アニメーションを終わらせる) */
    private fun idle() {
        repeat(12) { rule.mainClock.advanceTimeBy(100); ShadowLooper.idleMainLooper() }
    }

    /** 画面の操作を UI スレッドで行い、描き直す */
    private fun onMain(f: () -> Unit) {
        rule.runOnIdle(f)
        idle()
    }

    @Test fun paymentSheetSavesPaymentAndOwnExpense() {
        setup()
        val before = store.data.value.acts.keys.toSet()
        onMain { openPaymentSheet(app) }
        // 金額の入力欄(placeholder「¥0」と同じ箱に入っている入力欄)
        rule.onAllNodes(hasSetTextAction() and hasAnySibling(hasText("¥0")), useUnmergedTree = true)
            .onFirst().performTextInput("1200")
        idle()
        rule.onNodeWithText("支払いを保存").performClick()
        idle()

        val d = store.data.value
        val pay = d.acts.entries.single { it.key !in before && it.value.type == "支払い" }
        assertEquals(1200, pay.value.amt)
        val x = d.expenses[pay.value.x]
        assertNotNull("自分の支出が無い", x)
        assertEquals(1200, x!!.amt)
        assertEquals(pay.key, x.pay)
        assertEquals("その日の日付", app.today, d.dateOf(pay.key))
        val day = d.dayKeyByDate(app.today)!!
        assertTrue("その日の日記の kids に入っている(実際の親: ${pay.value.parent})", d.acts.getValue(day).kids.contains(pay.key))
        assertEquals("保存後は記録の詳細へ", pay.key, app.cur.id)

        // トーストの「取り消す」で消える
        rule.onNodeWithText("取り消す").performClick()
        idle()
        val after = store.data.value
        assertFalse(after.acts.containsKey(pay.key))
        assertFalse(after.expenses.containsKey(pay.value.x))
        assertEquals(before, after.acts.keys.toSet())
    }

    @Test fun toggleTaskDoneThenUndo() {
        setup()
        onMain { toggleTask(app, "t7") }
        assertEquals(Seed.MOCK_TODAY, store.data.value.plans.getValue("t7").done)
        onMain { app.undo() }
        assertNull(store.data.value.plans.getValue("t7").done)
    }

    @Test fun recordEventMakesActivityAndLinksPlan() {
        setup()
        onMain { recordEvent(app, "e9") }
        val d = store.data.value
        val rec = d.plans.getValue("e9").rec
        assertNotNull("予定 e9 に記録が結ばれていない", rec)
        val a = d.acts.getValue(rec!!)
        assertEquals("出来事", a.type)
        assertEquals("10:00", a.time)
        assertEquals("12:00", a.end)
        assertEquals("2026-10-31", d.dateOf(rec))
        assertTrue("その日の日記の kids に入っている", d.acts.getValue(d.dayKeyOf(rec)!!).kids.contains(rec))
        assertEquals("記録の詳細へ", rec, app.cur.id)
    }

    @Test fun recordPaymentPlanMakesPaymentAndExpense() {
        setup()
        // 見本の e4 は既に記録(p13)を持つので、記録されていない状態に戻してから記録する
        onMain {
            val d = store.data.value
            store.setForTest(d.copy(plans = d.plans + ("e4" to d.plans.getValue("e4").copy(rec = null))))
        }
        onMain { recordEvent(app, "e4") }
        val d = store.data.value
        val key = d.plans.getValue("e4").rec
        assertNotNull("支払い予定 e4 に記録が結ばれていない", key)
        val pay = d.acts.getValue(key!!)
        assertEquals("支払い", pay.type)
        assertEquals(85000, pay.amt)
        val x = d.expenses[pay.x]
        assertNotNull("自分の支出が無い", x)
        assertEquals(85000, x!!.amt)
        assertEquals("rent", x.cat)
        assertEquals(key, x.pay)
    }

    @Test fun deleteActivityRemovesDescendantsAndUndoRestoresAll() {
        setup()
        val before = store.data.value
        onMain { app.go("a", "w2") }
        onMain { deleteActivity(app, "w2") }
        val d = store.data.value
        listOf("w2", "c1", "p16").forEach { assertFalse("$it が残っている", d.acts.containsKey(it)) }
        assertFalse("支払いの支出 x16 が残っている", d.expenses.containsKey("x16"))
        assertFalse("日の kids に w2 が残っている", d.acts.getValue("d1030").kids.contains("w2"))
        rule.onNodeWithText("記録と中の 2 件を削除しました").assertExists()

        onMain { app.undo() }
        assertEquals("取り消しで全部戻る", before, store.data.value)
    }

    @Test fun darkThemeIsSavedInSettings() {
        setup()
        onMain { app.go("settings") }
        rule.onNodeWithText("ダーク").performClick()
        idle()
        assertEquals("dark", store.data.value.settings.theme)
    }

    @Test fun navPageFollowsTabs() {
        setup()
        assertEquals(0, app.navPage)
        onMain { app.go("ledger") }
        assertEquals(1, app.navPage)
        assertEquals("ledger", app.cur.name)
        onMain { app.go("home") }
        assertEquals(0, app.navPage)
        assertEquals("home", app.cur.name)
    }
}
