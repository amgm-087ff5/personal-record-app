package com.amgm.personallog.ui.ledger

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.amgm.personallog.domain.PaymentKind
import com.amgm.personallog.ui.receipt.ReceiptRoutes
import com.amgm.personallog.ui.split.SplitRoutes

object LedgerRoutes {
    const val ROOT = "ledger"
    const val HOME = "ledger/home"
    const val PAYMENT = "ledger/payment/{kind}/{id}"

    fun payment(kind: PaymentKind, id: Long) = "ledger/payment/${kind.key}/$id"

    /**
     * 割り勘で自分が外れた支払い(家計簿に支出の行が無い)の詳細。PAYMENT と同じ画面を、種類 "receipt" と支払いの id で開く。
     * PaymentKind には入れない(支出の種類ではないため)。PaymentDetailViewModel が種類の文字列で見分ける。
     */
    const val EXCLUDED_KIND = "receipt"
    fun excludedPayment(receiptId: Long) = "ledger/payment/$EXCLUDED_KIND/$receiptId"
}

/** 家計簿機能のナビゲーショングラフ。AppNavHost から呼ばれるので、機能内の遷移はここで完結させる。 */
fun NavGraphBuilder.ledgerGraph(navController: NavController) {
    navigation(route = LedgerRoutes.ROOT, startDestination = LedgerRoutes.HOME) {
        composable(LedgerRoutes.HOME) {
            LedgerScreen(
                onOpenPayment = { kind, id -> navController.navigate(LedgerRoutes.payment(kind, id)) },
                onOpenExcludedPayment = { receiptId -> navController.navigate(LedgerRoutes.excludedPayment(receiptId)) },
                onOpenReceipt = { receiptId -> navController.navigate(ReceiptRoutes.detail(receiptId)) },
                onOpenReceiptList = { navController.navigate(ReceiptRoutes.home()) },
            )
        }
        composable(
            route = LedgerRoutes.PAYMENT,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("id") { type = NavType.LongType },
            ),
        ) {
            PaymentDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenSplit = { receiptId -> navController.navigate(SplitRoutes.sheet(receiptId)) },
                onOpenReceipt = { receiptId ->
                    // レシートの詳細から来たなら、新しく積まずに戻る(行き来で履歴が伸びない)
                    val prev = navController.previousBackStackEntry
                    if (prev?.destination?.route == ReceiptRoutes.DETAIL && prev.arguments?.getLong("id") == receiptId) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(ReceiptRoutes.detail(receiptId))
                    }
                },
            )
        }
    }
}
