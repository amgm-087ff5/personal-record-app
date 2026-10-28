package com.amgm.personallog.domain

import com.amgm.personallog.data.model.Expense
import com.amgm.personallog.data.model.ExpenseGroup
import java.util.UUID

/**
 * v13 以前の「単独の支出」(expenses の行で groupId なし)を、自分の支出(まとまり)+ 品名が空の1品目に変える規則(純Kotlin)。
 * DB の移行(MIGRATION_13_14)と、古いバックアップの取り込み(convertSingleExpensesToGroups)の両方がこれを使う。
 *
 * 規則(利用者の決定 2026-10-28):
 *  - 対象は groupId が無く、家計簿で消していない(deletedByUser = 0)行。
 *    消した行(deletedByUser = 1)は一覧・集計に出ない非表示の行(立て替えの間に消した品目など)なので変えない。
 *    支払いを同期したとき、その支払いの支出に付け替えられる(ConfirmPlanner の作り直し)。
 *  - まとまり: 日付・時刻・金額・カテゴリ・メモ・receiptId・作成時刻・編集済みの印(金額・カテゴリ・メモ・日時)を写す。uid は新しく振る。
 *  - 品目(元の行): groupId をまとまりに張り、品名は空にする(品名は捨てる)。編集済みの印はまとまりへ移したので下ろす
 *    (品目のカテゴリ・メモは、まとまりのものに追従する。ExpenseItems.normalize)。金額・カテゴリ・メモ・id・uid はそのまま。
 */
object SingleExpenseConversion {
    /** 変える対象の行か。 */
    fun isTarget(groupId: Long?, deletedByUser: Boolean): Boolean = groupId == null && !deletedByUser

    /**
     * まとまりの uid。元の行の uid があれば、それから決まる値にする(同じ行は端末の移行でもバックアップの取り込みでも同じ uid になり、
     * 取り込み(マージ)で二重にならない)。元の行の uid が空なら新しく振る。
     */
    fun groupUid(expenseUid: String): String =
        if (expenseUid.isBlank()) UUID.randomUUID().toString()
        else UUID.nameUUIDFromBytes("v14-single-expense-group:$expenseUid".toByteArray(Charsets.UTF_8)).toString()

    /** 単独の支出から、まとまりを作る(id は 0。呼び出し側が振る)。 */
    fun groupOf(e: Expense): ExpenseGroup = ExpenseGroup(
        date = e.date,
        time = e.time,
        amount = e.amount,
        category = e.category,
        memo = e.memo,
        receiptId = e.receiptId,
        createdAt = e.createdAt,
        uid = groupUid(e.uid),
        amountEdited = e.amountEdited,
        categoryEdited = e.categoryEdited,
        memoEdited = e.memoEdited,
        timeEdited = e.timeEdited,
    )

    /** 単独の支出を、まとまり(groupId)の1品目にする。 */
    fun itemOf(e: Expense, groupId: Long): Expense = e.copy(
        groupId = groupId,
        name = "",
        amountEdited = false,
        categoryEdited = false,
        nameEdited = false,
        memoEdited = false,
        timeEdited = false,
    )
}
