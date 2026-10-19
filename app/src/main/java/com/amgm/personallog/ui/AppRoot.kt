package com.amgm.personallog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.amgm.personallog.ui.meal.MealRoutes
import com.amgm.personallog.ui.receipt.ReceiptRoutes
import com.amgm.personallog.ui.settings.SettingsRoutes

/** 下部タブ。アイコンと文字を並べる。選ばれたタブは琥珀色になり、文字は太くなる。 */
enum class TopLevelTab(val route: String, val label: String, val icon: ImageVector) {
    Diary(DiaryRoutes.ROOT, "日記", Icons.Filled.Edit),
    Receipt(ReceiptRoutes.ROOT, "支払い", Icons.Filled.Check),
    Meal(MealRoutes.ROOT, "自分の内訳", Icons.Filled.List),
    Ledger(LedgerRoutes.ROOT, "家計簿", Icons.Filled.Home),
    Settings(SettingsRoutes.ROOT, "設定", Icons.Filled.Settings),
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
                val color = if (selected) c.accent else c.inkFaint
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = tab.label,
                        style = AppTheme.type.title.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = color,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
