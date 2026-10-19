package com.amgm.personallog.ui.meal

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.amgm.personallog.ui.ledger.LedgerRoutes
import com.amgm.personallog.ui.receipt.ReceiptRoutes
import com.amgm.personallog.ui.split.SplitRoutes

object MealRoutes {
    const val ROOT = "meal"
    const val HOME = "meal/home"
}

/** 食べた記録のナビゲーショングラフ。AppNavHost から呼ばれるので、機能内の遷移はここで完結させる。 */
fun NavGraphBuilder.mealGraph(navController: NavController) {
    navigation(route = MealRoutes.ROOT, startDestination = MealRoutes.HOME) {
        composable(MealRoutes.HOME) {
            MealRecordScreen(
                onOpenReceipt = { receiptId -> navController.navigate(ReceiptRoutes.detail(receiptId)) },
                onOpenPayment = { kind, id -> navController.navigate(LedgerRoutes.payment(kind, id)) },
                onOpenSplit = { receiptId -> navController.navigate(SplitRoutes.sheet(receiptId)) },
            )
        }
    }
}
