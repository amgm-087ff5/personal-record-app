package com.amgm.personallog.ui.receipt

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.amgm.personallog.ui.ledger.LedgerRoutes
import com.amgm.personallog.ui.split.SplitRoutes
import com.amgm.personallog.ui.split.SplitSheetScreen

object ReceiptRoutes {
    const val ROOT = "receipt"
    /** 支払い一覧。autoCapture が true なら表示直後に撮影を開く(家計簿の「レシートから登録」)。 */
    const val HOME = "receipt/home?autoCapture={autoCapture}"
    const val DETAIL = "receipt/detail/{id}"

    fun home(autoCapture: Boolean = false) = "receipt/home?autoCapture=$autoCapture"
    fun detail(id: Long) = "receipt/detail/$id"
}

/** レシート機能のナビゲーショングラフ。AppNavHost から呼ばれるので、機能内の遷移はここで完結させる。 */
fun NavGraphBuilder.receiptGraph(navController: NavController) {
    navigation(route = ReceiptRoutes.ROOT, startDestination = ReceiptRoutes.HOME) {
        composable(
            route = ReceiptRoutes.HOME,
            arguments = listOf(navArgument("autoCapture") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            ReceiptScreen(
                onOpenReceipt = { id -> navController.navigate(ReceiptRoutes.detail(id)) },
                onBack = { navController.popBackStack() },
                autoCapture = entry.arguments?.getBoolean("autoCapture") ?: false,
            )
        }
        composable(
            route = ReceiptRoutes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val receiptId = entry.arguments?.getLong("id") ?: 0L
            ReceiptDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenSplit = { navController.navigate(SplitRoutes.sheet(receiptId)) },
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
        // 割り勘シート(支払い詳細の「割り勘」から開く)。receiptId は Long の引数
        composable(
            route = SplitRoutes.SHEET,
            arguments = listOf(navArgument("receiptId") { type = NavType.LongType }),
        ) {
            SplitSheetScreen(onBack = { navController.popBackStack() })
        }
    }
}
