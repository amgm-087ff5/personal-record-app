package com.amgm.personallog.domain

import com.amgm.personallog.data.model.ExpenseItem
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem

/** 支払いの詳細画面で「支払いと違う」の印を付ける欄(「支払いの値に戻す」の対象でもある)。 */
enum class PaymentField { AMOUNT, CATEGORY, NAME, MEMO, TIME }

/**
 * 「支払いの値に戻す」(純Kotlin)。家計簿で直した欄を、紐づく支払いから LedgerCalc と同じ規則で求めた値に戻し、
 * その欄の xxxEdited を 0 にする。値を求められないとき(支払いに合計が無い、対応する品目が無いなど)は null。
 */
object PaymentRevert {
    /**
     * @param group この品目が属する支出(まとまり)。日時はまとまりに従うので、日時はまとまりの値に戻す。
     * @param share 割り勘の自分の分(割り勘が無いときは null)。あれば支払いの値の代わりに自分の分で求める。
     */
    fun expenseItem(
        have: ExpenseItem,
        field: PaymentField,
        payment: Payment,
        items: List<PaymentItem>,
        group: Expense?,
        share: SelfShare? = null,
    ): ExpenseItem? {
        if (payment.totalAmount == null && share == null) return null
        val itemId = have.paymentItemId
        val fresh = LedgerCalc.groupFromPayment(payment, items, 0L, share) ?: return null
        // 品目に対応する行はその品目の値。品目の無い支払いの1品目(paymentItemId なし)は、対応の無い1品目の値
        val want: ExpenseItem = fresh.items.firstOrNull { it.paymentItemId == itemId } ?: return null
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
        have: Expense,
        field: PaymentField,
        payment: Payment,
        items: List<PaymentItem>,
        share: SelfShare? = null,
    ): Expense? {
        if (payment.totalAmount == null && share == null) return null
        val want = LedgerCalc.groupFromPayment(payment, items, 0L, share)?.group ?: return null
        return when (field) {
            PaymentField.AMOUNT -> have.copy(amount = want.amount, amountEdited = false)
            PaymentField.CATEGORY -> have.copy(category = want.category, categoryEdited = false)
            PaymentField.MEMO -> have.copy(memo = want.memo, memoEdited = false)
            PaymentField.TIME -> have.copy(date = want.date, time = want.time, timeEdited = false)
            PaymentField.NAME -> null
        }
    }
}
