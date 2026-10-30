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
    /** register は登録の途中(支払いの確定の後)かどうか。古い URL(register なし)は false として開く。 */
    const val PAYMENT = "ledger/payment/{kind}/{id}?register={register}"

    fun payment(kind: PaymentKind, id: Long, register: Boolean = false) =
        "ledger/payment/${kind.key}/$id" + if (register) "?register=true" else ""

    /**
     * 割り勘で自分が外れた支払い(家計簿に支出の行が無い)の詳細。PAYMENT と同じ画面を、種類 "receipt" と支払いの id で開く。
     * PaymentKind には入れない(支出の種類ではないため)。PaymentDetailViewModel が種類の文字列で見分ける。
     */
    const val EXCLUDED_KIND = "receipt"
    fun excludedPayment(paymentId: Long) = "ledger/payment/$EXCLUDED_KIND/$paymentId"

    /** 重複の可能性がある支払いの一覧(「重複を確認する」)。 */
    const val DUPLICATES = "ledger/duplicates"
    fun duplicates() = DUPLICATES

    /** 重複の候補1組のマージ画面。ids は支払い(payments)の id のカンマ区切り。並びは画面側で登録日時順に直す。 */
    const val MERGE = "ledger/merge/{ids}"
    const val MERGE_IDS_ARG = "ids"
    fun merge(paymentIds: List<Long>) = "ledger/merge/${paymentIds.joinToString(",")}"
}

/** 家計簿機能のナビゲーショングラフ。AppNavHost から呼ばれるので、機能内の遷移はここで完結させる。 */
fun NavGraphBuilder.ledgerGraph(navController: NavController) {
    navigation(route = LedgerRoutes.ROOT, startDestination = LedgerRoutes.HOME) {
        composable(LedgerRoutes.HOME) {
            LedgerScreen(
                onOpenPayment = { kind, id -> navController.navigate(LedgerRoutes.payment(kind, id)) },
                onOpenExcludedPayment = { paymentId -> navController.navigate(LedgerRoutes.excludedPayment(paymentId)) },
                // 下書きの支払いは、登録の手順(支払い → 自分の支出)の1つ目から開く
                onRegisterPayment = { paymentId -> navController.navigate(ReceiptRoutes.detail(paymentId, register = true)) },
                onOpenReceipt = { paymentId -> navController.navigate(ReceiptRoutes.detail(paymentId)) },
                onOpenReceiptList = { navController.navigate(ReceiptRoutes.home()) },
                onOpenDuplicates = { navController.navigate(LedgerRoutes.duplicates()) },
            )
        }
        composable(LedgerRoutes.DUPLICATES) {
            DuplicatesScreen(
                onBack = { navController.popBackStack() },
                onOpenPayment = { paymentId -> navController.navigate(ReceiptRoutes.detail(paymentId)) },
                onMerge = { ids -> navController.navigate(LedgerRoutes.merge(ids)) },
            )
        }
        composable(
            route = LedgerRoutes.MERGE,
            arguments = listOf(navArgument(LedgerRoutes.MERGE_IDS_ARG) { type = NavType.StringType }),
        ) {
            PaymentMergeScreen(
                onBack = { navController.popBackStack() },
                onOpenPayment = { paymentId -> navController.navigate(ReceiptRoutes.detail(paymentId)) },
                // マージ元の画面は消えた支払いを指すので、家計簿まで戻る
                onMerged = { navController.popBackStack(LedgerRoutes.HOME, inclusive = false) },
            )
        }
        composable(
            route = LedgerRoutes.PAYMENT,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("id") { type = NavType.LongType },
                navArgument("register") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            PaymentDetailScreen(
                register = entry.arguments?.getBoolean("register") ?: false,
                // 登録の「完了」: 登録の手順(支払い → 支出)を抜けて家計簿へ戻る
                onDone = { navController.popBackStack(LedgerRoutes.HOME, inclusive = false) },
                onBack = { navController.popBackStack() },
                onOpenSplit = { paymentId -> navController.navigate(SplitRoutes.sheet(paymentId)) },
                onOpenMerge = { ids -> navController.navigate(LedgerRoutes.merge(ids)) },
                onOpenReceipt = { paymentId ->
                    // 支払いの詳細から来たなら、新しく積まずに戻る(行き来で履歴が伸びない)
                    val prev = navController.previousBackStackEntry
                    if (prev?.destination?.route == ReceiptRoutes.DETAIL && prev.arguments?.getLong("id") == paymentId) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(ReceiptRoutes.detail(paymentId))
                    }
                },
            )
        }
    }
}
