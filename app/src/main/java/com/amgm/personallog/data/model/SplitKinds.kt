package com.amgm.personallog.data.model

// 割り勘の方式と割当の種類(DB に文字列で保存する値)。Room に依存しない純Kotlinにしておく(domain/split の計算とテストで使う)。

/** 割り勘シートの方式(SplitSheet.mode)。 */
object SplitMode {
    /** 自分だけ(既定。シートの行が無い場合も同じ)。 */
    const val SELF = "self"

    /** 支払い全体を人で割る(WholeShare)。品目は参考。 */
    const val WHOLE = "whole"

    /** 品目のまとまりごとに割る(ItemShare)。 */
    const val ITEM = "item"
}

/** 割当の種類(WholeShare.kind / ItemShare.kind)。1つのまとまり(または全体)の中では1種類に揃える。 */
object ShareKind {
    /** その人1人に全部(ItemShare のみ)。 */
    const val ASSIGN = "assign"

    /** 対象者で均等(weight・amount は使わない)。 */
    const val EQUAL = "equal"

    /** 割合。weight(個数など、1以上の整数)で比例配分する。 */
    const val RATIO = "ratio"

    /** 金額指定。amount(円)。合計とずれたら警告(自動では配り直さない)。 */
    const val AMOUNT = "amount"
}
