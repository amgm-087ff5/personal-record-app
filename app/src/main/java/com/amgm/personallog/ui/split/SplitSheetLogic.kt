package com.amgm.personallog.ui.split

import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.model.SplitParticipant
import com.amgm.personallog.domain.split.SplitLine
import com.amgm.personallog.domain.split.SplitWarning

// 割り勘シートの純関数(Android に依存しない。JVM でテストする)。
// 入力文字列の解析、表示の文言、メンバーの番号付け、ラベル一覧、警告の文言をここにまとめる。

/**
 * 金額・割合の入力を整数にする。数字・カンマ・全角数字・「¥」「円」「%」・空白を許す。
 * 空欄(空・空白だけ)は null。数字として読めない、負の値、0 は null(= 無効。空欄扱いにしない場合は呼び出し側で判定する)。
 */
fun parseAmountText(text: String): Int? {
    val normalized = normalizeDigits(text)
        .replace(",", "")
        .replace("、", "")
        .replace("¥", "")
        .replace("￥", "")
        .replace("円", "")
        .replace("%", "")
        .replace(" ", "")
        .replace("　", "")
    if (normalized.isEmpty()) return null
    return normalized.toIntOrNull()
}

/** 入力欄の文字列が空欄か(空・空白だけ)。 */
fun isBlankInput(text: String): Boolean = text.isBlank()

/** 入力欄の文字を整える: 数字と「,」だけを残す(貼り付けた円記号などは落とす)。全角数字は半角にする。 */
fun filterNumericInput(text: String): String = normalizeDigits(text).filter { it.isDigit() || it == ',' }

/** 全角数字(０〜９)を半角にする。 */
fun normalizeDigits(text: String): String = buildString {
    for (ch in text) {
        if (ch in '０'..'９') append('0' + (ch - '０')) else append(ch)
    }
}

/** 金額の表示: 1234 → 「¥1,234」。負は「-¥1,234」。 */
fun formatYenText(amount: Int): String {
    val sign = if (amount < 0) "-" else ""
    val digits = kotlin.math.abs(amount.toLong()).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return "$sign¥$grouped"
}

/** 割合の表示: 3 → 「3」(個数・重み)。 */
fun formatWeightText(weight: Int): String = weight.toString()

/**
 * 名前がない参加者の表示名。「メンバー1」「メンバー2」…。index は 1 始まりの番号(参加者の並び順で数える)。
 */
fun memberPlaceholder(index: Int): String = "メンバー$index"

/**
 * 参加者の表示名。自分は常に「自分」。登録メンバー・入力中の名前があればそれ。無ければ番号付きの「メンバーN」。
 * names は参加者 id → 入力中の名前。number は 1 始まりの番号。
 */
fun displayNameOf(
    participant: SplitParticipant,
    isSelf: Boolean,
    names: Map<Long, String>,
    number: Int,
): String {
    if (isSelf) return "自分"
    val typed = names[participant.id]?.trim().orEmpty()
    return typed.ifEmpty { memberPlaceholder(number) }
}

/** 入力欄の placeholder 用の名前(空欄のとき薄く出す番号付きの名前)。 */
fun namePlaceholderOf(number: Int): String = memberPlaceholder(number)

/** 登録メンバーから名前入力欄の初期値(参加者 id → 名前)を作る。自分と未登録の人は空。 */
fun initialNames(
    participants: List<SplitParticipant>,
    persons: Map<Long, Person>,
): Map<Long, String> = participants.associate { p ->
    p.id to (persons[p.id]?.name ?: "")
}

/**
 * ラベル一覧。空でないラベルを重複なし・出現順で返す。
 * 参加者の sortOrder 順に読むので、画面のチップの並びが安定する。
 */
fun labelsOf(participants: List<SplitParticipant>): List<String> =
    participants.sortedBy { it.sortOrder }
        .map { it.label.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/** ラベルの表示: 空なら「その他」。 */
fun labelDisplay(label: String): String = label.ifEmpty { "その他" }

/**
 * 警告の文言。W1〜W5(設計メモの文言どおり)。
 * groupName は W1 の「〇〇」に入れる品目名(不明なら「品目」)。
 */
fun warningText(warning: SplitWarning, groupName: (Long) -> String = { "品目" }): String = when (warning) {
    is SplitWarning.GroupAmountMismatch ->
        "${groupName(warning.groupId)}の割当が金額と合いません(差 ${formatYenText(warning.expected - warning.allocated)})"
    is SplitWarning.WholeAmountMismatch -> "全体の割当が合計と合いません"
    is SplitWarning.ItemsTotalMismatch ->
        "品目の合計と支払いの合計が ${formatYenText(kotlin.math.abs(warning.residual))} ずれています(読み漏れの可能性)"
    is SplitWarning.PayersMismatch -> "会計者の合計が合いません"
    is SplitWarning.InvalidShares -> "割当を解釈できません"
}

/** 警告の種類の符号(家計簿のバッジ用 "W1,W3" 形式)。 */
fun warningCode(warning: SplitWarning): String = when (warning) {
    is SplitWarning.GroupAmountMismatch -> "W1"
    is SplitWarning.WholeAmountMismatch -> "W2"
    is SplitWarning.ItemsTotalMismatch -> "W3"
    is SplitWarning.PayersMismatch -> "W4"
    is SplitWarning.InvalidShares -> "W5"
}

/** 警告の一覧を "W1,W3" の形にする(重複を除き、並びを安定させる)。空なら空文字。 */
fun warningCodes(warnings: List<SplitWarning>): String =
    warnings.map { warningCode(it) }.distinct().sorted().joinToString(",")

/**
 * 割り勘の表示用に、カテゴリ内訳を文字列にする: 「食費 ¥1,200・日用品 ¥300」。
 * 内訳が空なら空文字。
 */
fun breakdownText(breakdown: List<Pair<String, Int>>): String =
    breakdown.joinToString("・") { (category, yen) -> "$category ${formatYenText(yen)}" }

// ---- 参加人数・まとめて追加・−の可否 ----

/** まとめて追加で一度に足せる人数の上限(入力の誤りで大量に行が増えるのを防ぐ)。 */
const val MAX_BULK_ADD = 50

/** まとめて追加の入力(人数)。1〜MAX_BULK_ADD の整数だけ有効。空欄・範囲外は null。 */
fun bulkAddCount(text: String): Int? = parseAmountText(text)?.takeIf { it in 1..MAX_BULK_ADD }

/** まとめて追加の入力中に出す合計人数(今の参加人数 + 入力の人数)。入力が無効なら null。 */
fun totalAfterBulkAdd(current: Int, text: String): Int? = bulkAddCount(text)?.let { current + it }

/** 参加人数の表示: 「合計3人」(自分を含む)。 */
fun participantCountText(count: Int): String = "合計${count}人"

/** 参加者1行ぶんの判定材料。participants は並び順(sortOrder)の早い順に渡す。 */
data class ParticipantFacts(
    val id: Long,
    val isSelf: Boolean,
    /** 入力中の名前(登録メンバーなら人の名前。空なら名前なし)。 */
    val typedName: String,
    val isPayer: Boolean,
    /** 品目ごと・全体のどちらかに割当の行がある。 */
    val hasShare: Boolean,
)

/**
 * − で消せる参加者の id。末尾の1人が「自分(名前は見ない)か名前なし・割当なし・会計者でない」ときだけ id を返す。
 * それ以外(末尾が条件に合わない・参加者がいない)は null(− は押せない)。
 * 自分の typedName は「自分」が入るので、自分は名前の判定を飛ばす。
 */
fun removableLastParticipantId(participants: List<ParticipantFacts>): Long? {
    val last = participants.lastOrNull() ?: return null
    val removable = (last.isSelf || last.typedName.isBlank()) && !last.hasShare && !last.isPayer
    return if (removable) last.id else null
}

// ---- 未設定のまとまり・均等 ----

/** 未設定のまとまり(割当の行が1つも無いまとまり)の id。 */
fun unsetGroupIds(groupIds: List<Long>, groupIdsWithShares: Set<Long>): List<Long> =
    groupIds.filter { it !in groupIdsWithShares }

/** 未設定を全員で均等にするときの割当の組(まとまり id, 参加者 id)。参加者が2人未満なら空(均等にする意味が無い)。 */
fun equalShareTargets(unsetIds: List<Long>, participantIds: List<Long>): List<Pair<Long, Long>> {
    val ids = participantIds.distinct()
    if (ids.size < 2) return emptyList()
    return unsetIds.flatMap { g -> ids.map { p -> g to p } }
}

/** 未設定のまとまりの表示。共通費は参加者で均等、それ以外は自分に計上。 */
fun unsetNoteText(isShared: Boolean): String = if (isShared) "未設定(共通費は全員で均等)" else "未設定(自分に計上)"

// ---- 警告(行のバッジ・解消・仮計上) ----

/** 「差額を自分に」の後の自分の金額。current は今の自分の金額(空欄は null)、expected − allocated が足す差額。 */
fun selfAmountAfterDifference(current: Int?, expected: Int, allocated: Int): Int = (current ?: 0) + (expected - allocated)

/** 警告の行バッジの文言。W1・W2 は金額のずれ(差)、W5 は割当の解釈不可。W3・W4 は一覧だけに出すので文言をそのまま使う。 */
fun badgeText(warning: SplitWarning): String = when (warning) {
    is SplitWarning.GroupAmountMismatch ->
        "W1 金額が合わない(差 ${formatYenText(warning.expected - warning.allocated)})"
    is SplitWarning.WholeAmountMismatch ->
        "W2 全体の金額が合わない(差 ${formatYenText(warning.expected - warning.allocated)})"
    is SplitWarning.InvalidShares -> "W5 割当を解釈できない"
    is SplitWarning.ItemsTotalMismatch, is SplitWarning.PayersMismatch -> warningText(warning)
}

/** まとまりの行に付ける警告(W1・まとまりの付いた W5)。まとまり id → 警告。 */
fun groupWarningsOf(warnings: List<SplitWarning>): Map<Long, List<SplitWarning>> {
    val out = LinkedHashMap<Long, MutableList<SplitWarning>>()
    for (w in warnings) {
        val g = when (w) {
            is SplitWarning.GroupAmountMismatch -> w.groupId
            is SplitWarning.InvalidShares -> w.groupId
            else -> null
        } ?: continue
        out.getOrPut(g) { mutableListOf() } += w
    }
    return out
}

/** 全体の欄に付ける警告(W2・まとまりの無い W5)。 */
fun wholeWarningsOf(warnings: List<SplitWarning>): List<SplitWarning> = warnings.filter { w ->
    when (w) {
        is SplitWarning.WholeAmountMismatch -> true
        is SplitWarning.InvalidShares -> w.groupId == null
        else -> false
    }
}

/** 仮計上(金額指定のずれを自分に入れた分)をまとまりごとに合計する。全体の仮計上(groupId が無い行)は含めない。 */
fun provisionalByGroup(lines: List<SplitLine>): Map<Long, Int> {
    val out = LinkedHashMap<Long, Int>()
    for (line in lines) {
        val g = line.groupId ?: continue
        if (line.provisional) out[g] = (out[g] ?: 0) + line.amount
    }
    return out
}

/** 全体の仮計上(groupId が無い仮計上の行の合計)。 */
fun provisionalWholeOf(lines: List<SplitLine>): Int =
    lines.filter { it.provisional && it.groupId == null }.sumOf { it.amount }

/** 仮計上の表示: 「仮 ¥1,200」。 */
fun provisionalText(amount: Int): String = "仮 ${formatYenText(amount)}"
