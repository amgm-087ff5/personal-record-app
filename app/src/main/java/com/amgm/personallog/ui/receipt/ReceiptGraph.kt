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
    /** 支払い一覧。 */
    const val HOME = "receipt/home"
    /** register は登録の途中(家計簿の下書きから開いた)かどうか。古い URL(register なし)は false として開く。 */
    const val DETAIL = "receipt/detail/{id}?register={register}"

    fun home() = HOME
    fun detail(id: Long, register: Boolean = false) = "receipt/detail/$id" + if (register) "?register=true" else ""
}

/** 支払い機能のナビゲーショングラフ。AppNavHost から呼ばれるので、機能内の遷移はここで完結させる。 */
fun NavGraphBuilder.paymentGraph(navController: NavController) {
    navigation(route = ReceiptRoutes.ROOT, startDestination = ReceiptRoutes.HOME) {
        composable(route = ReceiptRoutes.HOME) {
            ReceiptScreen(
                onOpenReceipt = { id -> navController.navigate(ReceiptRoutes.detail(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = ReceiptRoutes.DETAIL,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("register") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val paymentId = entry.arguments?.getLong("id") ?: 0L
            ReceiptDetailScreen(
                register = entry.arguments?.getBoolean("register") ?: false,
                onBack = { navController.popBackStack() },
                // 登録の途中で確定できたら、その支払いの支出の画面(手順②)へ進む。支払いの画面は戻る履歴から外す
                onRegistered = { link ->
                    if (link != null) {
                        navController.navigate(LedgerRoutes.payment(link.kind, link.id, register = true)) {
                            popUpTo(ReceiptRoutes.DETAIL) { inclusive = true }
                        }
                    } else {
                        // 支出の行が無い(割り勘で自分が外れた)ときは、家計簿へ戻る
                        navController.popBackStack(LedgerRoutes.HOME, inclusive = false)
                    }
                },
                onOpenSplit = { navController.navigate(SplitRoutes.sheet(paymentId)) },
                onOpenMerge = { ids -> navController.navigate(LedgerRoutes.merge(ids)) },
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
        // 割り勘シート(支払い詳細の「割り勘」から開く)。paymentId は Long の引数
        composable(
            route = SplitRoutes.SHEET,
            arguments = listOf(navArgument("paymentId") { type = NavType.LongType }),
        ) {
            SplitSheetScreen(onBack = { navController.popBackStack() })
        }
    }
}
