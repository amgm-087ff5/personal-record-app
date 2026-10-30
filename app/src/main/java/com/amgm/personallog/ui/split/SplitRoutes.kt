package com.amgm.personallog.ui.split

/** 割り勘シート(支払いごと)。受け取る paymentId は支払い(payments)の id。画面はタスク B5。 */
object SplitRoutes {
    const val SHEET = "split/sheet/{paymentId}"

    fun sheet(paymentId: Long) = "split/sheet/$paymentId"
}
