package com.amgm.personallog.v4

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.amgm.personallog.v4.data.Store
import com.amgm.personallog.v4.ui.AppRoot
import com.amgm.personallog.v4.ui.AppState

class V4App : Application() {
    val store: Store by lazy { Store.open(this).also { it.load() } }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as V4App).store
        setContent {
            val app = remember { AppState(store) }
            BackHandler(enabled = app.sheet != null || app.stack.size > 1 || app.cur.name != "home") { app.goBack() }
            AppRoot(app)
        }
    }
}
