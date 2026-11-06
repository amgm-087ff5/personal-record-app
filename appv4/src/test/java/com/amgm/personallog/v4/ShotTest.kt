package com.amgm.personallog.v4

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import androidx.compose.ui.test.onRoot
import com.amgm.personallog.v4.data.FixedClock
import com.amgm.personallog.v4.data.Seed
import com.amgm.personallog.v4.data.Store
import com.amgm.personallog.v4.data.settled
import com.amgm.personallog.v4.screens.create.openCreate
import com.amgm.personallog.v4.screens.create.openPaymentSheet
import com.amgm.personallog.v4.screens.create.openActivitySheet
import com.amgm.personallog.v4.screens.create.openAddKidSheet
import com.amgm.personallog.v4.screens.create.openPlanSheet
import com.amgm.personallog.v4.screens.create.openMemoSheet
import com.amgm.personallog.v4.screens.create.openEntitySheet
import com.amgm.personallog.v4.screens.create.openDiarySheet
import com.amgm.personallog.v4.screens.create.openReceiptSheet
import com.amgm.personallog.v4.screens.create.setResetConfirm
import com.amgm.personallog.v4.ui.AppRoot
import com.amgm.personallog.v4.ui.AppState
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 画面の撮影(v4 モックとの照合用)。見本データ(ずらさない)+いま=2026-10-31 13:20 で描く。
 * ./gradlew :appv4:testDebugUnitTest --tests '*ShotTest*' -Pshots=home,activities,a/l1,@create -PshotTheme=light
 * 「@名前」はシートなどの操作(ShotHooks)。出力: appv4/build/shots/<route>_<theme>.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi", application = android.app.Application::class)
class ShotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun shots() {
        val list = System.getProperty("shots")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        assumeTrue(list.isNotEmpty())
        val theme = System.getProperty("shotTheme") ?: "light"
        val h = System.getProperty("shotH")?.toIntOrNull()
        if (h != null) RuntimeEnvironment.setQualifiers("w412dp-h${h}dp-xhdpi")
        val out = File(System.getProperty("shotOut") ?: "build/shots").apply { mkdirs() }
        val store = Store(null, FixedClock())
        store.setForTest(Seed.data().settled().let { it.copy(settings = it.settings.copy(theme = theme)) })
        val app = AppState(store)
        rule.mainClock.autoAdvance = false
        rule.setContent { AppRoot(app) }
        for (s in list) {
            rule.runOnIdle {
                app.closeSheet()
                when {
                    s.startsWith("@") -> ShotHooks.run(app, s.drop(1))
                    else -> app.goHash(s)
                }
            }
            repeat(12) { rule.mainClock.advanceTimeBy(100); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread {
                v.measure(android.view.View.MeasureSpec.makeMeasureSpec(v.width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(v.height, android.view.View.MeasureSpec.EXACTLY))
                v.layout(0, 0, v.width, v.height)
                v.draw(Canvas(bmp))
            }
            val f = File(out, s.replace("/", "_").replace("@", "sheet-").replace("?", "_").replace("=", "-") + "_" + theme + ".png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("SHOT " + f.absolutePath)
        }
    }
}

/** 「@名前」の操作。画面の担当が増やしてよい(名前 → app に対する操作) */
object ShotHooks {
    /** 「@名前:引数」→ (app, 引数) */
    val hooks = linkedMapOf<String, (AppState, String?) -> Unit>(
        "create" to { app, _ -> openCreate(app) },
        "pay" to { app, _ -> openPaymentSheet(app) },
        "act" to { app, _ -> openActivitySheet(app) },
        "plan" to { app, _ -> openPlanSheet(app) },
        "todo" to { app, _ -> openPlanSheet(app, todo = true) },
        "memo" to { app, _ -> openMemoSheet(app) },
        "ent" to { app, _ -> openEntitySheet(app) },
        "diary" to { app, _ -> openDiarySheet(app, app.today) },
        "kid" to { app, _ -> openAddKidSheet(app, "l1") },
        "receipt" to { app, _ -> openReceiptSheet(app) },
        "reset" to { app, _ -> setResetConfirm(app) },
    )
    fun run(app: AppState, name: String) {
        val (n, arg) = name.split(":", limit = 2).let { it[0] to it.getOrNull(1) }
        (hooks[n] ?: error("ShotHooks に $n がありません")).invoke(app, arg)
    }
}
