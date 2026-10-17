package com.amgm.personallog.ui.diary

import java.time.LocalDate

/** "yyyy-MM-dd" を表示用に分解したもの。 */
internal data class DiaryDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val weekdayShort: String,
    val weekdayLong: String,
)

private val DIARY_WEEKDAYS = arrayOf("月", "火", "水", "木", "金", "土", "日")

/** 不正な文字列は null。 */
internal fun parseDiaryDate(date: String): DiaryDate? {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    val w = DIARY_WEEKDAYS[d.dayOfWeek.value - 1]
    return DiaryDate(d.year, d.monthValue, d.dayOfMonth, w, w + "曜日")
}

/** "2026-10-17" -> "2026年10月"。不正な文字列はそのまま返す。 */
internal fun diaryMonthLabel(date: String): String {
    val d = parseDiaryDate(date) ?: return date
    return "${d.year}年${d.month}月"
}
