package com.amgm.personallog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.diary.DiaryRoutes
import com.amgm.personallog.ui.ledger.LedgerRoutes
import com.amgm.personallog.ui.receipt.ReceiptRoutes
import com.amgm.personallog.ui.settings.SettingsRoutes

/** 下部タブ。アイコンは使わず、明朝の文字だけで示す。 */
enum class TopLevelTab(val route: String, val label: String) {
    Diary(DiaryRoutes.ROOT, "日記"),
    Receipt(ReceiptRoutes.ROOT, "レシート"),
    Ledger(LedgerRoutes.ROOT, "家計簿"),
    Settings(SettingsRoutes.ROOT, "設定"),
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    Scaffold(
        containerColor = AppTheme.colors.paper,
        bottomBar = {
            InkTabBar(
                selectedRoute = TopLevelTab.entries.firstOrNull { tab ->
                    destination?.hierarchy?.any { it.route == tab.route } == true
                }?.route,
                onSelect = { tab -> navigateToTab(navController, tab) },
            )
        },
    ) { innerPadding ->
        AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding))
    }
}

private fun navigateToTab(navController: NavController, tab: TopLevelTab) {
    navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun InkTabBar(selectedRoute: String?, onSelect: (TopLevelTab) -> Unit) {
    val c = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth().background(c.paper)) {
        Hairline()
        Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp)) {
            TopLevelTab.entries.forEach { tab ->
                val selected = tab.route == selectedRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Text(
                        text = tab.label,
                        style = AppTheme.type.title.copy(fontSize = 15.sp, lineHeight = 20.sp),
                        color = if (selected) c.ink else c.inkFaint,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(20.dp)
                            .height(2.dp)
                            .background(if (selected) c.accent else androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }
    }
}
