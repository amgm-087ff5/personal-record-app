package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem

/** 支払いの詳細画面で「支払いと違う」の印を付ける欄(「支払いの値に戻す」の対象でもある)。 */
enum class PaymentField { AMOUNT, CATEGORY, NAME, MEMO, TIME }

/**
 * 「支払いの値に戻す」(純Kotlin)。家計簿で直した欄を、紐づく支払いから LedgerCalc と同じ規則で求めた値に戻し、
 * その欄の xxxEdited を 0 にする。値を求められないとき(支払いに合計が無い、対応する品目が無いなど)は null。
 */
object ReceiptRevert {
    /**
     * @param group この品目が属する支出(まとまり)。日時はまとまりに従うので、日時はまとまりの値に戻す。
     * @param share 割り勘の自分の分(割り勘が無いときは null)。あれば支払いの値の代わりに自分の分で求める。
     */
    fun expense(
        have: Expense,
        field: PaymentField,
        receipt: Receipt,
        items: List<ReceiptItem>,
        group: ExpenseGroup?,
        share: SelfShare? = null,
    ): Expense? {
        if (receipt.totalAmount == null && share == null) return null
        val itemId = have.receiptItemId
        val fresh = LedgerCalc.groupFromReceipt(receipt, items, 0L, share) ?: return null
        // 品目に対応する行はその品目の値。品目の無い支払いの1品目(receiptItemId なし)は、対応の無い1品目の値
        val want: Expense = fresh.items.firstOrNull { it.receiptItemId == itemId } ?: return null
        return when (field) {
            PaymentField.AMOUNT -> have.copy(amount = want.amount, amountEdited = false)
            PaymentField.CATEGORY -> have.copy(category = want.category, categoryEdited = false)
            PaymentField.MEMO -> have.copy(memo = want.memo, memoEdited = false)
            // 品目の無い支払いの1品目には、支払いの品名が無い
            PaymentField.NAME -> if (itemId == null) null else have.copy(name = want.name, nameEdited = false)
            PaymentField.TIME ->
                if (group != null) have.copy(date = group.date, time = group.time, timeEdited = false)
                else have.copy(date = want.date, time = want.time, timeEdited = false)
        }
    }

    fun group(
        have: ExpenseGroup,
        field: PaymentField,
        receipt: Receipt,
        items: List<ReceiptItem>,
        share: SelfShare? = null,
    ): ExpenseGroup? {
        if (receipt.totalAmount == null && share == null) return null
        val want = LedgerCalc.groupFromReceipt(receipt, items, 0L, share)?.group ?: return null
        return when (field) {
            PaymentField.AMOUNT -> have.copy(amount = want.amount, amountEdited = false)
            PaymentField.CATEGORY -> have.copy(category = want.category, categoryEdited = false)
            PaymentField.MEMO -> have.copy(memo = want.memo, memoEdited = false)
            PaymentField.TIME -> have.copy(date = want.date, time = want.time, timeEdited = false)
            PaymentField.NAME -> null
        }
    }
}
