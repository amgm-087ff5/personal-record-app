package com.amgm.personallog.ui.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.amgm.personallog.ui.members.MembersRoutes
import com.amgm.personallog.ui.members.MembersScreen

object SettingsRoutes {
    const val ROOT = "settings"
    const val HOME = "settings/home"
}

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    navigation(route = SettingsRoutes.ROOT, startDestination = SettingsRoutes.HOME) {
        composable(SettingsRoutes.HOME) {
            SettingsScreen(onOpenMembers = { navController.navigate(MembersRoutes.LIST) })
        }
        composable(MembersRoutes.LIST) {
            MembersScreen(onBack = { navController.popBackStack() })
        }
    }
}
