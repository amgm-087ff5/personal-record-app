package com.amgm.personallog.ui.ledger

import com.amgm.personallog.data.model.ExpenseItem
import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.domain.PaymentField

/**
 * 編集済みの欄のうち、レシート由来の行についてだけ「支払いと違う」の印を付ける(純Kotlin)。
 *
 * 支払いは手入力でも payments 表の1行なので、支出には paymentId が入る。paymentId の有無だけでは
 * レシート(画像・読取原文のある支払い)かどうかは分からないため、[fromScannedReceipt] で区別する。
 * 手入力の支払いには比べる相手が無いので、印は付けない。
 */
object PaymentMarks {
    /** 支払いがレシート(画像か読取原文がある)か。手入力の支払いは false。 */
    fun isScannedReceipt(r: Payment?): Boolean = r != null && (r.imagePath.isNotEmpty() || r.rawJson.isNotEmpty())

    /** 支出の品目の副行に出す税率の表示。例: 税込の8%は「8%」、税抜なら「8%」「税抜」。 */
    fun taxLabels(item: PaymentItem): List<String> =
        listOfNotNull("${item.taxRate}%", "税抜".takeIf { !item.priceIncludesTax })

    fun ofGroup(g: Expense, fromScannedReceipt: Boolean = true): Set<PaymentField> {
        if (!fromScannedReceipt) return emptySet()
        return buildSet {
            if (g.amountEdited) add(PaymentField.AMOUNT)
            if (g.categoryEdited) add(PaymentField.CATEGORY)
            if (g.memoEdited) add(PaymentField.MEMO)
            if (g.timeEdited) add(PaymentField.TIME)
        }
    }

    fun ofExpenseItem(e: ExpenseItem, fromScannedReceipt: Boolean = true): Set<PaymentField> {
        if (e.paymentId == null && e.paymentItemId == null) return emptySet()
        if (!fromScannedReceipt) return emptySet()
        return buildSet {
            if (e.amountEdited) add(PaymentField.AMOUNT)
            if (e.categoryEdited) add(PaymentField.CATEGORY)
            if (e.nameEdited) add(PaymentField.NAME)
            if (e.memoEdited) add(PaymentField.MEMO)
            if (e.timeEdited) add(PaymentField.TIME)
        }
    }
}
