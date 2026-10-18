package com.amgm.personallog.ui.split

/** 割り勘シート(支払いごと)。受け取る receiptId は支払い(receipts)の id。画面はタスク B5。 */
object SplitRoutes {
    const val SHEET = "split/sheet/{receiptId}"

    fun sheet(receiptId: Long) = "split/sheet/$receiptId"
}
