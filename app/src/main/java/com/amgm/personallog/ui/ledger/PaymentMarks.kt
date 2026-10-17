package com.amgm.personallog.ui.ledger

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.domain.PaymentField

/** 編集済みの欄のうち、レシート由来の行についてだけ「レシートと違う」の印を付ける(純Kotlin)。 */
object PaymentMarks {
    fun ofGroup(g: ExpenseGroup): Set<PaymentField> {
        if (g.receiptId == null) return emptySet()
        return buildSet {
            if (g.amountEdited) add(PaymentField.AMOUNT)
            if (g.categoryEdited) add(PaymentField.CATEGORY)
            if (g.memoEdited) add(PaymentField.MEMO)
            if (g.timeEdited) add(PaymentField.TIME)
        }
    }

    fun ofExpense(e: Expense): Set<PaymentField> {
        if (e.receiptId == null && e.receiptItemId == null) return emptySet()
        return buildSet {
            if (e.amountEdited) add(PaymentField.AMOUNT)
            if (e.categoryEdited) add(PaymentField.CATEGORY)
            if (e.nameEdited) add(PaymentField.NAME)
            if (e.memoEdited) add(PaymentField.MEMO)
            if (e.timeEdited) add(PaymentField.TIME)
        }
    }
}
