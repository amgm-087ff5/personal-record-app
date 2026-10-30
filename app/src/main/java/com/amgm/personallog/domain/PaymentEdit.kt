package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup

/**
 * 家計簿(支払い側)で直したとき、変わった欄の「編集済み」を立てる判定(純Kotlin)。
 * 編集済みの欄は、支払いを再確定しても上書きされない。一度立てたら、同じ値に戻しても下ろさない
 * (timeEdited の既存の扱いと同じ)。
 */
object PaymentEdit {
    fun editExpense(
        original: Expense,
        date: String,
        time: String,
        amount: Int,
        category: String,
        name: String,
        memo: String,
    ): Expense {
        val n = name.trim()
        val m = memo.trim()
        return original.copy(
            date = date,
            time = time,
            amount = amount,
            category = category,
            name = n,
            memo = m,
            amountEdited = original.amountEdited || amount != original.amount,
            categoryEdited = original.categoryEdited || category != original.category,
            nameEdited = original.nameEdited || n != original.name,
            memoEdited = original.memoEdited || m != original.memo,
            timeEdited = original.timeEdited || date != original.date || time != original.time,
        )
    }

    fun editGroup(
        original: ExpenseGroup,
        date: String,
        time: String,
        amount: Int,
        category: String,
        memo: String,
    ): ExpenseGroup {
        val m = memo.trim()
        return original.copy(
            date = date,
            time = time,
            amount = amount,
            category = category,
            memo = m,
            amountEdited = original.amountEdited || amount != original.amount,
            categoryEdited = original.categoryEdited || category != original.category,
            memoEdited = original.memoEdited || m != original.memo,
            timeEdited = original.timeEdited || date != original.date || time != original.time,
        )
    }
}
