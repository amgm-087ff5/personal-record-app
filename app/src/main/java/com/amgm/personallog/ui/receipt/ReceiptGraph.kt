package com.amgm.personallog.ui.receipt

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.amgm.personallog.ui.ledger.LedgerRoutes

object ReceiptRoutes {
    const val ROOT = "receipt"
    const val HOME = "receipt/home"
    const val DETAIL = "receipt/detail/{id}"

    fun detail(id: Long) = "receipt/detail/$id"
}

/** レシート機能のナビゲーショングラフ。AppNavHost から呼ばれるので、機能内の遷移はここで完結させる。 */
fun NavGraphBuilder.receiptGraph(navController: NavController) {
    navigation(route = ReceiptRoutes.ROOT, startDestination = ReceiptRoutes.HOME) {
        composable(ReceiptRoutes.HOME) {
            ReceiptScreen(onOpenReceipt = { id -> navController.navigate(ReceiptRoutes.detail(id)) })
        }
        composable(
            route = ReceiptRoutes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            ReceiptDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenPayment = { link ->
                    // 支払いの詳細から来たなら、新しく積まずに戻る(行き来で履歴が伸びない)
                    val prev = navController.previousBackStackEntry
                    val sameTarget = prev?.destination?.route == LedgerRoutes.PAYMENT &&
                        prev.arguments?.getString("kind") == link.kind.key &&
                        prev.arguments?.getLong("id") == link.id
                    if (sameTarget) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(LedgerRoutes.payment(link.kind, link.id))
                    }
                },
            )
        }
    }
}
