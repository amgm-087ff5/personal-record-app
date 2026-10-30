package com.amgm.personallog.data.repository

import androidx.room.withTransaction
import com.amgm.personallog.data.db.AppDatabase
import com.amgm.personallog.data.db.PaymentDao
import com.amgm.personallog.data.db.SplitDao
import com.amgm.personallog.data.model.ItemShare
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.Payment
import com.amgm.personallog.data.model.PaymentItem
import com.amgm.personallog.data.model.ShareGroup
import com.amgm.personallog.data.model.ShareGroupItem
import com.amgm.personallog.data.model.ShareKind
import com.amgm.personallog.data.model.SplitLabelAmount
import com.amgm.personallog.data.model.SplitMode
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.data.model.SplitSheet
import com.amgm.personallog.data.model.WholeShare
import com.amgm.personallog.domain.UnknownAmounts
import com.amgm.personallog.domain.split.ItemYen
import com.amgm.personallog.domain.split.ShareInput
import com.amgm.personallog.domain.split.SplitGroupInput
import com.amgm.personallog.domain.split.SplitInput
import com.amgm.personallog.domain.split.SplitModeReading
import com.amgm.personallog.domain.split.SplitPerson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** まとまりと、そこに入っている品目の id。 */
data class GroupWithItems(val group: ShareGroup, val itemIds: List<Long>)

/**
 * 1つの支払いの割り勘シートの中身(画面の編集と計算の両方が使う)。
 * id が 0 以下の参加者・まとまりは未保存(画面で追加したもの)。保存時に新しい id に付け替え、割当の participantId / groupId も付け替える。
 * participants は sortOrder 順。自分(personId = 自分の人)は含むことも、外して含まないこともある。
 */
data class SplitSheetData(
    val paymentId: Long,
    val mode: String,
    val wholeKind: String,
    val participants: List<SplitParticipant>,
    val groups: List<GroupWithItems>,
    val itemShares: List<ItemShare>,
    val wholeShares: List<WholeShare>,
    val labelAmounts: List<SplitLabelAmount>,
    /** 画面表示用: 参加者 id → 登録メンバー(personId のある参加者だけ)。 */
    val persons: Map<Long, Person> = emptyMap(),
)

/** 計算の入力と、家計簿へ配り直すための品目の金額。selfParticipantId は自分の参加者 id。 */
data class SplitBuild(val input: SplitInput, val itemYen: List<ItemYen>, val selfParticipantId: Long)

/**
 * SplitSheetData を計算の入力にする(純粋な変換。画面のプレビューと家計簿の両方が使う)。
 * total は支払いの合計(null なら品目の合計)、items は支払いの品目(PaymentItem)。品目の税込円は UnknownAmounts.paidYenPerItem で求める
 * (金額不明の品目は、合計 − 分かっている品目の合計 を配った金額)。
 * 品目の並びは items と同じ。まとまりの金額は、そこに入っている品目(items の中のもの)の税込円の合計。
 */
fun SplitSheetData.toBuild(total: Int?, items: List<PaymentItem>): SplitBuild {
    val yenPerItem = UnknownAmounts.paidYenPerItem(items, total)
    val groupOfItem = HashMap<Long, Long>()
    for (g in groups) for (itemId in g.itemIds) groupOfItem[itemId] = g.group.id
    val itemYen = items.mapIndexed { i, item -> ItemYen(item.id, groupOfItem[item.id], yenPerItem[i], item.category) }
    val groupInputs = groups.map { g ->
        SplitGroupInput(
            id = g.group.id,
            amount = itemYen.filter { it.groupId == g.group.id }.sumOf { it.yen }.toInt(),
            isShared = g.group.isShared,
            pieceCount = g.group.pieceCount,
        )
    }
    val people = participants.mapIndexed { index, p ->
        SplitPerson(id = p.id, isSelf = persons[p.id]?.isSelf == true, order = index, isPayer = p.isPayer, label = p.label)
    }
    val itemShareInputs = itemShares.groupBy { it.groupId }.mapValues { (_, rows) ->
        rows.map { ShareInput(personId = it.participantId, kind = it.kind, weight = it.weight, amount = it.amount) }
    }
    val wholeShareInputs = wholeShares.map { ShareInput(personId = it.participantId, kind = it.kind, weight = it.weight, amount = it.amount) }
    // 自分の参加者が無い(自分を外した)ときは NO_SELF_PARTICIPANT。自分の分は0円になる
    val selfId = participants.firstOrNull { persons[it.id]?.isSelf == true }?.id ?: NO_SELF_PARTICIPANT_ID
    val input = SplitInput(
        total = total,
        mode = mode,
        people = people,
        groups = groupInputs,
        itemShares = itemShareInputs,
        wholeShares = wholeShareInputs,
        wholeKind = wholeKind,
        labelAmounts = labelAmounts.associate { it.label to it.amount },
    )
    return SplitBuild(input = input, itemYen = itemYen, selfParticipantId = selfId)
}

/** 品目の同一判定のキー(品名の前後空白を除く・1個の金額・税率・税込区分)。 */
private data class ItemKey(val name: String, val amount: Int, val taxRate: Int, val priceIncludesTax: Boolean)

private fun keyOf(item: PaymentItem): ItemKey = ItemKey(item.name.trim(), item.amount, item.taxRate, item.priceIncludesTax)

// ---- まとまりの分割・結合の判定(Android に依存しない純関数。テストは ui/split/SplitGroupRulesTest.kt) ----

/** 品目を切り出せるか: 選んだ品目が空でなく、すべてそのまとまりに入っている。 */
fun canSplitOutItems(groupItemIds: Set<Long>, selectedIds: Set<Long>): Boolean =
    selectedIds.isNotEmpty() && groupItemIds.containsAll(selectedIds)

/** 切り出すと元のまとまりに品目が残らないか(残らなければ元のまとまりは消える)。 */
fun splitEmptiesSource(groupItemIds: Set<Long>, selectedIds: Set<Long>): Boolean =
    canSplitOutItems(groupItemIds, selectedIds) && groupItemIds.all { it in selectedIds }

/** まとめる先(残す先): 選んだまとまりのうち並び順(sortOrder)が一番早いもの。 */
fun mergeKeepGroup(groups: List<ShareGroup>): ShareGroup? =
    groups.minWithOrNull(compareBy<ShareGroup>({ it.sortOrder }, { it.id }))

/** 割当をまとめた結果。rows は残す先のまとまりの割当(groupId は残す先)。cleared は、統合できず割当を外したとき true。 */
data class MergedShares(val rows: List<ItemShare>, val cleared: Boolean)

/**
 * まとめるときの割当。残す先の割当を基準にする。割当が空(未設定)の側は判定に入れない。
 *  - 割当のある側の種類(kind)と対象者が全部同じなら統合する(基準の行を残す。基準が空なら、残りの割当の1つを使う)。
 *  - 1つでも種類か対象者が違えば統合せず、割当を全部外す(未設定にする。cleared = true)。
 * keepRows は残す先の割当、otherRows は消える側のまとまりごとの割当。
 */
fun mergeShareRows(keepGroupId: Long, keepRows: List<ItemShare>, otherRows: List<List<ItemShare>>): MergedShares {
    val sources = (listOf(keepRows) + otherRows).filter { it.isNotEmpty() }
    if (sources.isEmpty()) return MergedShares(rows = emptyList(), cleared = false)
    val base = sources.first()
    val shape = shareShapeOf(base)
    return if (sources.all { shareShapeOf(it) == shape }) {
        MergedShares(rows = base.map { it.copy(groupId = keepGroupId) }, cleared = false)
    } else {
        MergedShares(rows = emptyList(), cleared = true)
    }
}

/** 割当の形(種類の集合と対象者の集合)。同じなら統合できる。 */
private data class ShareShape(val kinds: Set<String>, val participants: Set<Long>)

private fun shareShapeOf(rows: List<ItemShare>): ShareShape =
    ShareShape(kinds = rows.map { it.kind }.toSet(), participants = rows.map { it.participantId }.toSet())

/** まとめた後のまとまり: 並び・個数は残す先のもの、共通費の印はどれか1つでも付いていれば付く。 */
fun mergedGroupAttributes(keep: ShareGroup, all: List<ShareGroup>): ShareGroup =
    keep.copy(isShared = all.any { it.isShared }, pieceCount = keep.pieceCount)

/** 分けた後の新しいまとまり: 割当なし(未設定=自分)・共通費なし・個数は null。 */
fun newGroupAfterSplit(paymentId: Long, sortOrder: Int): ShareGroup =
    ShareGroup(paymentId = paymentId, pieceCount = null, isShared = false, sortOrder = sortOrder)

// ---- 支払い側の人数(headcount)と既定の参加者(純関数。テストは ui/split/SplitHeadcountRulesTest.kt) ----

/** 自分の参加者が無いときの参加者 id(DB の id とは重ならない値。計算では自分の分は0円になる)。 */
const val NO_SELF_PARTICIPANT_ID: Long = Long.MIN_VALUE

/** 保存時の人数の確認に使う。from は今の人数(headcount。null は 1)、to は割り勘の参加人数(自分を含む合計)。 */
data class HeadcountChange(val from: Int, val to: Int)

/** 割り勘の参加人数と、支払いの人数(headcount。null は 1)を比べる。同じなら null(確認は出さない)。 */
fun headcountChangeOf(headcount: Int?, participantCount: Int): HeadcountChange? {
    val from = headcount ?: 1
    return if (from == participantCount) null else HeadcountChange(from = from, to = participantCount)
}

/** 初めて作る割り勘で、自分の他に入れる名前なしの人数。headcount が 2 以上なら headcount−1、null や 1 以下なら 0。 */
fun defaultBlankCount(headcount: Int?): Int = maxOf((headcount ?: 1) - 1, 0)

/** 割り勘の表の読み書き。家計簿の更新は PaymentRepository が行う(ここからは呼ばない)。 */
@Singleton
class SplitRepository @Inject constructor(
    private val db: AppDatabase,
    private val splitDao: SplitDao,
    private val paymentDao: PaymentDao,
) {
    private val personRepo = PersonRepository(splitDao)

    /** 全支払いの割り勘シート(家計簿の一覧で警告のバッジを出すのに使う)。 */
    fun observeSheets(): Flow<List<SplitSheet>> = splitDao.observeSheets()

    /**
     * その支払いのシートを読む。シートが無ければ既定を作る(自分と、レシートの人数ぶんの空欄メンバー)。
     * 保存済みのシートは、自分の参加者を勝手に入れ直さない(自分を外した状態のまま返す)。品目のまとまりは ensureGroups してから返す。
     */
    suspend fun load(paymentId: Long): SplitSheetData = db.withTransaction {
        ensureGroups(paymentId)
        val payment = paymentDao.getById(paymentId)
        val sheet = splitDao.getSheet(paymentId)
        // 旧い立て替え(シートが無く isAdvance)は、保存せずに既定値を返す(保存するのは利用者が保存したとき)
        if (sheet == null && payment != null && payment.isAdvance) return@withTransaction legacyDefault(paymentId, payment)

        // 新しいシートの既定は「食べた分どおり」(ITEM)。初めて作るときだけ、既定の参加者(自分と人数ぶんの空欄)を入れる
        val isNew = sheet == null
        val savedSheet = sheet ?: SplitSheet(paymentId = paymentId, mode = SplitMode.ITEM).also { splitDao.upsertSheet(it) }
        val self = personRepo.self()
        if (isNew) addDefaultParticipants(paymentId, self, payment?.headcount)
        // 自分の参加者は勝手に入れ直さない(自分を外した状態を保つ)
        var participants = splitDao.getParticipants(paymentId)
        // 保存が「自分だけ」(旧 SELF)なら、参加者は自分1人に読み替える(割当も自分の分だけ残す。保存で他の参加者は消える)
        val selfOnly = SplitModeReading.isSelfOnly(savedSheet.mode)
        if (selfOnly) participants = participants.filter { it.personId == self.id }
        val keptIds = participants.map { it.id }.toSet()
        val itemShares = splitDao.getItemShares(paymentId).let { rows -> if (selfOnly) rows.filter { it.participantId in keptIds } else rows }
        val wholeShares = splitDao.getWholeShares(paymentId).let { rows -> if (selfOnly) rows.filter { it.participantId in keptIds } else rows }
        val byId = splitDao.getPersons().associateBy { it.id }
        val persons = participants.mapNotNull { p -> p.personId?.let { pid -> byId[pid]?.let { p.id to it } } }.toMap()
        SplitSheetData(
            paymentId = paymentId,
            mode = SplitModeReading.modeOf(savedSheet.mode),
            wholeKind = savedSheet.wholeKind,
            participants = participants,
            groups = groupsWithItems(paymentId),
            itemShares = itemShares,
            wholeShares = wholeShares,
            labelAmounts = splitDao.getLabelAmounts(paymentId),
            persons = persons,
        )
    }

    /** 初めて作る割り勘の既定の参加者: 自分(先頭)と、名前なし・割当なしの人を defaultBlankCount 人。 */
    private suspend fun addDefaultParticipants(paymentId: Long, self: Person, headcount: Int?) {
        splitDao.insertParticipant(SplitParticipant(paymentId = paymentId, personId = self.id, sortOrder = 0))
        repeat(defaultBlankCount(headcount)) { i ->
            splitDao.insertParticipant(SplitParticipant(paymentId = paymentId, personId = null, sortOrder = i + 1))
        }
    }

    /** 支払いの参加人数(headcount)を変える。null は「不明」。割り勘の保存とは別に呼ぶ。 */
    suspend fun setHeadcount(paymentId: Long, headcount: Int?) {
        paymentDao.setHeadcount(paymentId, headcount)
    }

    /**
     * 読み取りだけ(何も作らず保存しない)。その支払いの割り勘シートを返す。シートが無ければ null。
     * load と違い、自分の参加者・既定のシート・まとまりを作らない。表示用(食べた記録など)に使う。
     * 方式が「自分だけ」なら参加者と割当を自分の分に絞る(load と同じ読み替え)。
     */
    suspend fun peek(paymentId: Long): SplitSheetData? {
        val sheet = splitDao.getSheet(paymentId) ?: return null
        val self = splitDao.getSelf()
        val selfOnly = SplitModeReading.isSelfOnly(sheet.mode)
        var participants = splitDao.getParticipants(paymentId)
        if (selfOnly) participants = participants.filter { self != null && it.personId == self.id }
        val keptIds = participants.map { it.id }.toSet()
        val itemShares = splitDao.getItemShares(paymentId).filter { !selfOnly || it.participantId in keptIds }
        val wholeShares = splitDao.getWholeShares(paymentId).filter { !selfOnly || it.participantId in keptIds }
        val byId = splitDao.getPersons().associateBy { it.id }
        val persons = participants.mapNotNull { p -> p.personId?.let { pid -> byId[pid]?.let { p.id to it } } }.toMap()
        return SplitSheetData(
            paymentId = paymentId,
            mode = SplitModeReading.modeOf(sheet.mode),
            wholeKind = sheet.wholeKind,
            participants = participants,
            groups = groupsWithItems(paymentId),
            itemShares = itemShares,
            wholeShares = wholeShares,
            labelAmounts = splitDao.getLabelAmounts(paymentId),
            persons = persons,
        )
    }

    /** 支払いのまとまりと、そこに入っている品目の id(sortOrder 順)。 */
    private suspend fun groupsWithItems(paymentId: Long): List<GroupWithItems> {
        val groupItems = splitDao.getGroupItems(paymentId)
        return splitDao.getGroups(paymentId).map { g ->
            GroupWithItems(g, groupItems.filter { it.groupId == g.id }.map { it.paymentItemId })
        }
    }

    /**
     * 旧い立て替えの既定値(保存しない)。全体方式・金額で、自分の分は旧い ownAmount(空欄なら空)、
     * 名前なしの参加者を1人入れる(id=-1。全体の割当は空欄なので残額を均等)。自分の参加者 id は 0(未保存の扱い)。
     */
    private suspend fun legacyDefault(paymentId: Long, payment: Payment): SplitSheetData {
        val self = personRepo.self()
        val selfParticipant = SplitParticipant(id = 0, paymentId = paymentId, personId = self.id, sortOrder = 0)
        val namelessParticipant = SplitParticipant(id = -1, paymentId = paymentId, personId = null, sortOrder = 1)
        return SplitSheetData(
            paymentId = paymentId,
            mode = SplitMode.WHOLE,
            wholeKind = ShareKind.AMOUNT,
            participants = listOf(selfParticipant, namelessParticipant),
            groups = groupsWithItems(paymentId),
            itemShares = emptyList(),
            wholeShares = listOf(WholeShare(paymentId = paymentId, participantId = 0, kind = ShareKind.AMOUNT, amount = payment.ownAmount)),
            labelAmounts = emptyList(),
            persons = mapOf(0L to self),
        )
    }

    /**
     * 保存する(1つのトランザクション)。名前があって personId の無い参加者は人として登録して紐付ける(同名の人がいればその人)。
     * 名前の無い参加者は登録しない。消えた参加者・割当は消す。保存後の内容(id 確定)を返す。
     * 名前は SplitParticipant に列が無いので names(参加者 id → 入力された名前)で渡す。
     */
    suspend fun save(data: SplitSheetData, names: Map<Long, String>, now: Long = System.currentTimeMillis()): SplitSheetData =
        db.withTransaction {
            val paymentId = data.paymentId
            // シートは入力を保存する(警告は計算の結果なので今の値を保つ)
            val oldSheet = splitDao.getSheet(paymentId)
            splitDao.upsertSheet(
                SplitSheet(paymentId = paymentId, mode = data.mode, wholeKind = data.wholeKind, warnings = oldSheet?.warnings.orEmpty()),
            )

            val self = personRepo.self(now)
            val personsById = splitDao.getPersons().associateBy { it.id }
            val existing = splitDao.getParticipants(paymentId)
            val existingIds = existing.map { it.id }.toSet()

            // 画面から消えた参加者は消す(その参加者の割当・支払内訳は外部キーの CASCADE で消える)
            val keptIds = data.participants.filter { it.id > 0 }.map { it.id }.toSet()
            val removed = existing.map { it.id }.filter { it !in keptIds }
            if (removed.isNotEmpty()) splitDao.deleteParticipantsByIds(removed)

            // 参加者: 既存(id が DB にある)は更新、それ以外は追加。画面の仮の id → 保存後の id を覚える
            val participantIdMap = HashMap<Long, Long>()
            for (p in data.participants) {
                val personId = resolvePersonId(p, names, self, personsById, now)
                if (p.id > 0 && p.id in existingIds) {
                    splitDao.updateParticipant(p.copy(paymentId = paymentId, personId = personId))
                    participantIdMap[p.id] = p.id
                } else {
                    val newId = splitDao.insertParticipant(p.copy(id = 0, paymentId = paymentId, personId = personId))
                    participantIdMap[p.id] = newId
                }
            }

            // まとまり: 共通費と個数だけ更新する(品目の入り方は ensureGroups が決める)
            val groupsNow = splitDao.getGroups(paymentId).associateBy { it.id }
            for (g in data.groups) {
                val current = groupsNow[g.group.id] ?: continue
                splitDao.updateGroup(current.copy(isShared = g.group.isShared, pieceCount = g.group.pieceCount))
            }

            // 割当は入れ直す。参加者の付け替え後の id を使い、消えた参加者の分は落とす
            splitDao.deleteItemSharesOfPayment(paymentId)
            val itemRows = data.itemShares.mapNotNull { s ->
                val pid = participantIdMap[s.participantId] ?: return@mapNotNull null
                if (s.groupId !in groupsNow) return@mapNotNull null
                s.copy(participantId = pid)
            }
            if (itemRows.isNotEmpty()) splitDao.insertItemShares(itemRows)

            splitDao.deleteWholeShares(paymentId)
            val wholeRows = data.wholeShares.mapNotNull { s ->
                participantIdMap[s.participantId]?.let { s.copy(paymentId = paymentId, participantId = it) }
            }
            if (wholeRows.isNotEmpty()) splitDao.insertWholeShares(wholeRows)

            splitDao.deleteLabelAmounts(paymentId)
            if (data.labelAmounts.isNotEmpty()) {
                splitDao.insertLabelAmounts(data.labelAmounts.map { it.copy(paymentId = paymentId) })
            }

            load(paymentId)
        }

    /**
     * 参加者の personId を決める。
     *  - 自分の参加者は自分の人のまま。
     *  - 名前の入力があれば: 空なら未登録(null)。今の人と同じ名前ならそのまま。違えば findOrCreate(同名の人がいればその人)。
     *  - 名前の入力が無ければ、今の personId のまま。
     */
    private suspend fun resolvePersonId(
        p: SplitParticipant,
        names: Map<Long, String>,
        self: Person,
        personsById: Map<Long, Person>,
        now: Long,
    ): Long? {
        if (p.personId == self.id) return self.id
        val input = names[p.id] ?: return p.personId
        val name = input.trim()
        if (name.isEmpty()) return null
        val current = p.personId?.let { personsById[it] }
        if (current != null && current.name == name) return current.id
        return personRepo.findOrCreate(name, now).id
    }

    /**
     * 品目をまとまりに入れる(同じ品名・1個の金額・税率・税込区分なら同じまとまり)。空になったまとまりは消す。sharedHints は品目 id → 共通費か。
     * まとまりに入っている品目は動かさない(金額を直しても割当を保つ)。新しいまとまりの共通費の印は sharedHints から決める。
     */
    suspend fun ensureGroups(paymentId: Long, sharedHints: Map<Long, Boolean> = emptyMap()): Unit = db.withTransaction {
        val items = paymentDao.getItems(paymentId).sortedBy { it.id }
        val itemsById = items.associateBy { it.id }
        val groups = splitDao.getGroups(paymentId) // sortOrder 順
        val membership = splitDao.getGroupItems(paymentId)
        val inGroup = membership.map { it.paymentItemId }.toSet()
        val itemsOfGroup = membership.groupBy { it.groupId }

        // 同じキーの品目が既に入っているまとまり(sortOrder の早い方を優先)
        val groupOfKey = HashMap<ItemKey, Long>()
        for (g in groups) {
            val first = itemsOfGroup[g.id].orEmpty().firstNotNullOfOrNull { itemsById[it.paymentItemId] } ?: continue
            groupOfKey.getOrPut(keyOf(first)) { g.id }
        }

        var nextOrder = (groups.maxOfOrNull { it.sortOrder } ?: -1) + 1
        for (item in items) {
            if (item.id in inGroup) continue
            val key = keyOf(item)
            val groupId = groupOfKey[key] ?: run {
                val newId = splitDao.insertGroup(
                    ShareGroup(paymentId = paymentId, isShared = sharedHints[item.id] ?: false, sortOrder = nextOrder++),
                )
                groupOfKey[key] = newId
                newId
            }
            splitDao.insertGroupItems(listOf(ShareGroupItem(groupId = groupId, paymentItemId = item.id)))
        }

        // 品目が無くなった(空の)まとまりは消す
        val used = splitDao.getGroupItems(paymentId).map { it.groupId }.toSet()
        for (g in groups) {
            if (g.id !in used) splitDao.deleteGroup(g)
        }
    }

    /**
     * 品目を1つのまとまりから切り出し、新しいまとまりにする(1つのトランザクション)。
     * 新しいまとまりは割当なし・共通費なし・個数 null。元のまとまりに品目が残らなければ消す(割当も消える)。
     * 選び方が不正(空・そのまとまりに無い品目を含む)なら何もしない。家計簿は呼び出し側が syncLedger で合わせる。
     */
    suspend fun splitGroupItems(paymentId: Long, groupId: Long, itemIds: Set<Long>): Unit = db.withTransaction {
        val groups = splitDao.getGroups(paymentId)
        if (groups.none { it.id == groupId }) return@withTransaction
        val membership = splitDao.getGroupItems(paymentId).filter { it.groupId == groupId }.map { it.paymentItemId }.toSet()
        if (!canSplitOutItems(membership, itemIds)) return@withTransaction
        val nextOrder = (groups.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val newId = splitDao.insertGroup(newGroupAfterSplit(paymentId, nextOrder))
        splitDao.moveItems(fromGroupId = groupId, toGroupId = newId, itemIds = itemIds.toList())
        if (splitEmptiesSource(membership, itemIds)) {
            splitDao.deleteItemSharesOfGroup(groupId)
            splitDao.deleteGroupsByIds(listOf(groupId))
        }
    }

    /**
     * 2つ以上のまとまりを1つにする(1つのトランザクション)。残す先は並びが一番早いもの。
     * 品目は全部残す先に入る。個数は残す先の値、共通費はどれか付いていれば付く。割当は mergeShareRows で決める(統合、または外す)。
     * 家計簿は呼び出し側が syncLedger で合わせる。
     */
    suspend fun mergeGroups(paymentId: Long, groupIds: Set<Long>): Unit = db.withTransaction {
        val chosen = splitDao.getGroups(paymentId).filter { it.id in groupIds }
        if (chosen.size < 2) return@withTransaction
        val keep = mergeKeepGroup(chosen) ?: return@withTransaction
        val others = chosen.filter { it.id != keep.id }
        val allShares = splitDao.getItemShares(paymentId)
        val merged = mergeShareRows(
            keepGroupId = keep.id,
            keepRows = allShares.filter { it.groupId == keep.id },
            otherRows = others.map { o -> allShares.filter { it.groupId == o.id } },
        )
        val otherIds = others.map { it.id }
        splitDao.moveAllItems(fromGroupIds = otherIds, toGroupId = keep.id)
        splitDao.deleteItemSharesOfGroups(otherIds)
        splitDao.deleteGroupsByIds(otherIds)
        splitDao.deleteItemSharesOfGroup(keep.id)
        if (merged.rows.isNotEmpty()) splitDao.insertItemShares(merged.rows)
        splitDao.updateGroup(mergedGroupAttributes(keep, chosen))
    }

    /** 家計簿用: 割り勘があれば計算の入力を返す。シートが無い・自分だけなら null(従来どおり全額が自分)。 */
    suspend fun buildFor(paymentId: Long, total: Int?, items: List<PaymentItem>): SplitBuild? {
        val sheet = splitDao.getSheet(paymentId) ?: return null
        if (sheet.mode == SplitMode.SELF) return null
        val data = load(paymentId)
        // 参加者が自分1人だけ(0人を含む)なら割り勘は無い(全額が自分)。従来の「自分だけ」と同じに扱い、家計簿に警告を付けない。
        // 自分を外して他の人だけのときは割り勘を計算する(自分の分は0円)
        val onlySelf = data.participants.all { data.persons[it.id]?.isSelf == true }
        if (data.participants.size <= 1 && onlySelf) return null
        return data.toBuild(total, items)
    }

    /** 直近の計算の警告(例 "W1,W3")を保存する。 */
    suspend fun setWarnings(paymentId: Long, codes: String) {
        splitDao.setWarnings(paymentId, codes)
    }
}

/** 登録メンバー(人)。 */
@Singleton
class PersonRepository @Inject constructor(
    private val splitDao: SplitDao,
) {
    /** 自分以外の登録メンバー(名前入力の候補・「メンバーを選ぶ」に出す)。名前順。 */
    fun observeMembers(): Flow<List<Person>> =
        splitDao.observePersons().map { list -> list.filter { !it.isSelf }.sortedBy { it.name } }

    /** 自分の人(無ければ作る)。 */
    suspend fun self(now: Long = System.currentTimeMillis()): Person {
        splitDao.getSelf()?.let { return it }
        val person = Person(name = "自分", shortName = "自", isSelf = true, createdAt = now)
        return person.copy(id = splitDao.insertPerson(person))
    }

    /** 名前で探し、無ければ登録する(前後の空白は除く。空の名前は不可)。 */
    suspend fun findOrCreate(name: String, now: Long = System.currentTimeMillis()): Person {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "名前が空です" }
        splitDao.getPersonByName(trimmed)?.let { return it }
        // 短い表示名(チップ)は名前の先頭1文字
        val person = Person(name = trimmed, shortName = trimmed.take(1), createdAt = now)
        return person.copy(id = splitDao.insertPerson(person))
    }

    suspend fun rename(person: Person, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "名前が空です" }
        splitDao.updatePerson(person.copy(name = trimmed))
    }

    /** 削除する。どこかの支払いの参加者として使われていれば消さずに false。自分は消さない(false)。 */
    suspend fun delete(person: Person): Boolean {
        if (person.isSelf) return false
        if (splitDao.countParticipantsOfPerson(person.id) > 0) return false
        splitDao.deletePerson(person)
        return true
    }
}
