package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup

/** 支払いの種類。画面の遷移先(ルート)で使う文字列を持つ。 */
enum class PaymentKind(val key: String) {
    GROUP("group"),
    EXPENSE("expense"),
    ;

    companion object {
        fun of(key: String?): PaymentKind? = entries.firstOrNull { it.key == key }
    }
}

/** レシートに紐づく家計簿の支払い。amount は家計簿に入っている金額(グループは支払い金額、単独は自分の分)。 */
data class PaymentLink(val kind: PaymentKind, val id: Long, val amount: Long) {
    companion object {
        /** グループがあればそれ、無ければ単独の支出(グループの品目ではないもの)。どちらも無ければ null。 */
        fun resolve(groups: List<ExpenseGroup>, expenses: List<Expense>): PaymentLink? {
            groups.firstOrNull()?.let { return PaymentLink(PaymentKind.GROUP, it.id, it.amount.toLong()) }
            return expenses.firstOrNull { it.groupId == null }
                ?.let { PaymentLink(PaymentKind.EXPENSE, it.id, it.amount.toLong()) }
        }
    }
}
