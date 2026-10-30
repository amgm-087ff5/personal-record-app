package com.amgm.personallog.data.repository

import androidx.room.withTransaction
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.db.PaymentDao
import com.amgm.personallog.data.db.SplitDao
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.domain.split.ParticipantFacts
import com.amgm.personallog.domain.split.SplitModeReading
import com.amgm.personallog.domain.split.excess
import com.amgm.personallog.domain.split.removalPlan
import com.amgm.personallog.domain.split.shortfall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 支払い側の人数(payments.headcount)の変更を、割り勘の参加者(split_participants)に合わせる計画。
 * - add: 増やす人数(名前なし・割当なしの空欄メンバーを足す)。
 * - autoRemoveIds: 確認なしで消す参加者(名前・割当・支出の無い行)。
 * - choiceIds: 消す人を選ばせる候補(末尾から。自分も含む)。
 * - chooseCount: 候補から選んで消す人数(excess からの自動分を引いた数)。0 なら選択は不要。
 */
data class HeadcountSyncChange(
    val add: Int,
    val autoRemoveIds: List<Long>,
    val choiceIds: List<Long>,
    val chooseCount: Int,
) {
    /** 何も変えないか。 */
    val isEmpty: Boolean get() = add == 0 && autoRemoveIds.isEmpty() && chooseCount == 0

    /** 選択ダイアログが要るか。 */
    val needsChoice: Boolean get() = chooseCount > 0

    /** 選んだ人が、候補のうち chooseCount 人ちょうどか(決定ボタンを出す条件)。 */
    fun isValidChoice(selected: Set<Long>): Boolean =
        selected.size == chooseCount && selected.all { it in choiceIds }
}

/** 計画と、候補の表示名(参加者 id → 名前)。 */
data class HeadcountPlan(val change: HeadcountSyncChange, val labels: Map<Long, String>)

/**
 * 参加者の人数を、目標人数 target に合わせる計画を作る(純関数。DB に触れない)。
 * 今の人数 = participants.size。target が 1 未満なら 1 人扱い(domain/split の shortfall・excess と同じ)。
 * 減らすときは removalPlan に従う。候補を全部使っても足りない分は、chooseCount を候補数までに抑える。
 */
fun planHeadcountChange(participants: List<ParticipantFacts>, target: Int): HeadcountSyncChange {
    val current = participants.size
    val add = shortfall(current, target)
    val drop = excess(current, target)
    if (drop == 0) return HeadcountSyncChange(add = add, autoRemoveIds = emptyList(), choiceIds = emptyList(), chooseCount = 0)
    val plan = removalPlan(participants, drop)
    val chooseCount = minOf(drop - plan.autoRemoveIds.size, plan.needsChoice.size)
    return HeadcountSyncChange(
        add = 0,
        autoRemoveIds = plan.autoRemoveIds,
        choiceIds = plan.needsChoice,
        chooseCount = maxOf(chooseCount, 0),
    )
}

/**
 * 支払い側の人数を変えたとき、割り勘の参加者を合わせる(DB に書くのはここだけ。計算式は既存のものを使う)。
 * 割り勘シートが無い支払い・方式が「自分だけ」の支払い・人数が無いとき(null)は何もしない。
 */
@Singleton
class ParticipantSyncService @Inject constructor(
    private val db: AppDatabase,
    private val paymentRepository: PaymentRepository,
) {
    private val splitDao: SplitDao get() = db.splitDao()
    private val paymentDao: PaymentDao get() = db.paymentDao()

    /** 目標人数に合わせる計画を作る(DB は変えない)。対象外なら null。変更が無ければ change.isEmpty。 */
    suspend fun plan(paymentId: Long, target: Int?): HeadcountPlan? {
        if (target == null) return null
        val sheet = splitDao.getSheet(paymentId) ?: return null
        if (SplitModeReading.isSelfOnly(sheet.mode)) return null
        val self = splitDao.getSelf()
        val participants = splitDao.getParticipants(paymentId)
        val allocated = splitDao.getItemShares(paymentId).map { it.participantId }.toSet() +
            splitDao.getWholeShares(paymentId).map { it.participantId }.toSet()
        val persons = splitDao.getPersons().associateBy { it.id }
        val facts = participants.map { p ->
            ParticipantFacts(
                id = p.id,
                isSelf = self != null && p.personId == self.id,
                hasName = p.personId != null,
                hasAllocation = p.id in allocated,
                // 支出がある = 店に払った人(支払者)。払った人は自動では消さない
                hasExpense = p.isPayer,
                sortOrder = p.sortOrder,
            )
        }
        val labels = participants.withIndex().associate { (index, p) ->
            val label = when {
                self != null && p.personId == self.id -> "自分"
                p.personId != null -> persons[p.personId]?.name ?: "名前なし${index + 1}"
                else -> "名前なし${index + 1}"
            }
            p.id to label
        }
        return HeadcountPlan(change = planHeadcountChange(facts, target), labels = labels)
    }

    /**
     * 計画どおりに参加者を増やし・消し、人数(headcount)を保存し、家計簿を合わせる。
     * removeIds は自動で消す人と、選んだ人を合わせたもの。1つのトランザクションで参加者と人数を変える。
     */
    suspend fun apply(paymentId: Long, target: Int, removeIds: List<Long>, addCount: Int) {
        db.withTransaction {
            if (removeIds.isNotEmpty()) splitDao.deleteParticipantsByIds(removeIds)
            if (addCount > 0) {
                val top = (splitDao.getParticipants(paymentId).maxOfOrNull { it.sortOrder } ?: 0) + 1
                repeat(addCount) { i ->
                    splitDao.insertParticipant(SplitParticipant(paymentId = paymentId, personId = null, sortOrder = top + i))
                }
            }
            paymentDao.setHeadcount(paymentId, target)
        }
        paymentRepository.syncLedger(paymentId)
    }
}
