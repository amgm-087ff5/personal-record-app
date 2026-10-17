package com.amgm.personallog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.diary.DiaryRoutes
import com.amgm.personallog.ui.ledger.LedgerRoutes
import com.amgm.personallog.ui.receipt.ReceiptRoutes
import com.amgm.personallog.ui.settings.SettingsRoutes

/** 下部タブ。アイコンは使わず文字だけ。選ばれたタブの下に、窓明かりのような琥珀の灯りが点る。 */
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
    val barShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = barShape,
                clip = false,
                ambientColor = c.shadow.copy(alpha = if (c.isDark) 0.2f else 0.06f),
                spotColor = c.shadow.copy(alpha = if (c.isDark) 0.5f else 0.18f),
            )
            .background(c.paperRaised, barShape),
    ) {
        Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp)) {
            TopLevelTab.entries.forEach { tab ->
                val selected = tab.route == selectedRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = tab.label,
                        style = AppTheme.type.title.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (selected) c.ink else c.inkFaint,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .then(
                                if (selected) {
                                    Modifier
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = CircleShape,
                                            clip = false,
                                            ambientColor = c.glow,
                                            spotColor = c.glow,
                                        )
                                        .background(c.accent, CircleShape)
                                } else {
                                    Modifier.background(Color.Transparent, CircleShape)
                                },
                            ),
                    )
                }
            }
        }
    }
}
