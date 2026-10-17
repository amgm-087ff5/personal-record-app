package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem

/** 支払いの詳細画面で「レシートと違う」の印を付ける欄(「レシートの値に戻す」の対象でもある)。 */
enum class PaymentField { AMOUNT, CATEGORY, NAME, MEMO, TIME }

/**
 * 「レシートの値に戻す」(純Kotlin)。家計簿で直した欄を、紐づくレシートから LedgerCalc と同じ規則で求めた値に戻し、
 * その欄の xxxEdited を 0 にする。値を求められないとき(レシートに合計が無い、対応する品目が無い、品名のない単独の支出など)は null。
 */
object ReceiptRevert {
    /**
     * @param group この支出が属する支払いグループ(単独の支出は null)。日時はグループに従うので、日時はグループの値に戻す。
     */
    fun expense(have: Expense, field: PaymentField, receipt: Receipt, items: List<ReceiptItem>, group: ExpenseGroup?): Expense? {
        if (receipt.totalAmount == null) return null
        val itemId = have.receiptItemId
        val want: Expense = if (itemId != null) {
            LedgerCalc.groupFromReceipt(receipt, items, 0L)?.items?.firstOrNull { it.receiptItemId == itemId }
        } else {
            // 単独の支出は合計1行(支払総額、立て替えなら自分の分)。品目ごとの行ではない
            LedgerCalc.expensesFromReceipt(receipt, items, perItem = false, now = 0L).singleOrNull()
        } ?: return null
        return when (field) {
            PaymentField.AMOUNT -> have.copy(amount = want.amount, amountEdited = false)
            PaymentField.CATEGORY -> have.copy(category = want.category, categoryEdited = false)
            PaymentField.MEMO -> have.copy(memo = want.memo, memoEdited = false)
            // 単独の支出には、レシートの品名が無い
            PaymentField.NAME -> if (itemId == null) null else have.copy(name = want.name, nameEdited = false)
            PaymentField.TIME ->
                if (group != null) have.copy(date = group.date, time = group.time, timeEdited = false)
                else have.copy(date = want.date, time = want.time, timeEdited = false)
        }
    }

    fun group(have: ExpenseGroup, field: PaymentField, receipt: Receipt, items: List<ReceiptItem>): ExpenseGroup? {
        if (receipt.totalAmount == null) return null
        val want = LedgerCalc.groupFromReceipt(receipt, items, 0L)?.group ?: return null
        return when (field) {
            PaymentField.AMOUNT -> have.copy(amount = want.amount, amountEdited = false)
            PaymentField.CATEGORY -> have.copy(category = want.category, categoryEdited = false)
            PaymentField.MEMO -> have.copy(memo = want.memo, memoEdited = false)
            PaymentField.TIME -> have.copy(date = want.date, time = want.time, timeEdited = false)
            PaymentField.NAME -> null
        }
    }
}
