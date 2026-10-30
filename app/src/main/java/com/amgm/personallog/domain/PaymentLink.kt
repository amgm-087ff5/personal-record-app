package com.amgm.personallog.domain

import com.amgm.personallog.data.model.ExpenseGroup

/** 支払いの種類。画面の遷移先(ルート)で使う文字列を持つ。 */
enum class PaymentKind(val key: String) {
    GROUP("group"),

    /**
     * 旧ルート(v13 以前の「単独の支出」。id は expenses の id)。v14 で単独の支出は無くなったので新しくは作らない。
     * 古いルートで開かれたときは、その品目が属する支出(まとまり)を開く(PaymentDetailViewModel)。
     */
    EXPENSE("expense"),
    ;

    companion object {
        fun of(key: String?): PaymentKind? = entries.firstOrNull { it.key == key }
    }
}

/** 支払いに紐づく家計簿の支出。amount は家計簿に入っている金額(自分の負担額)。 */
data class PaymentLink(val kind: PaymentKind, val id: Long, val amount: Long) {
    companion object {
        /** 支出(まとまり)があればそれ。無ければ null(自分の分が0円で家計簿に行が無いなど)。 */
        fun resolve(groups: List<ExpenseGroup>): PaymentLink? =
            groups.firstOrNull()?.let { PaymentLink(PaymentKind.GROUP, it.id, it.amount.toLong()) }
    }
}
