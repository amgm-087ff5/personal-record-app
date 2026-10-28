package com.amgm.personallog.ui.ledger

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import com.amgm.personallog.data.model.Receipt
import com.amgm.personallog.data.model.ReceiptItem
import com.amgm.personallog.domain.PaymentField

/**
 * 編集済みの欄のうち、レシート由来の行についてだけ「レシートと違う」の印を付ける(純Kotlin)。
 *
 * 支払いは手入力でも receipts 表の1行なので、支出には receiptId が入る。receiptId の有無だけでは
 * レシート(画像・読取原文のある支払い)かどうかは分からないため、[fromScannedReceipt] で区別する。
 * 手入力の支払いには比べる相手(レシート)が無いので、印は付けない。
 */
object PaymentMarks {
    /** 支払いがレシート(画像か読取原文がある)か。手入力の支払いは false。 */
    fun isScannedReceipt(r: Receipt?): Boolean = r != null && (r.imagePath.isNotEmpty() || r.rawJson.isNotEmpty())

    /** 支出の品目の副行に出す税率の表示。例: 税込の8%は「8%」、税抜なら「8%」「税抜」。 */
    fun taxLabels(item: ReceiptItem): List<String> =
        listOfNotNull("${item.taxRate}%", "税抜".takeIf { !item.priceIncludesTax })

    fun ofGroup(g: ExpenseGroup, fromScannedReceipt: Boolean = true): Set<PaymentField> {
        if (g.receiptId == null || !fromScannedReceipt) return emptySet()
        return buildSet {
            if (g.amountEdited) add(PaymentField.AMOUNT)
            if (g.categoryEdited) add(PaymentField.CATEGORY)
            if (g.memoEdited) add(PaymentField.MEMO)
            if (g.timeEdited) add(PaymentField.TIME)
        }
    }

    fun ofExpense(e: Expense, fromScannedReceipt: Boolean = true): Set<PaymentField> {
        if (e.receiptId == null && e.receiptItemId == null) return emptySet()
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
