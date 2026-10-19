package com.amgm.personallog.domain.split

import com.amgm.personallog.data.model.SplitMode

// 保存済みの方式を、今の画面の方式に読み替える(純Kotlin。DB の版は上げない)。
// 旧い「自分だけ」(SELF)は「参加者は自分1人・食べた分どおり(ITEM)」として読む。
// 計算の側は未知の値を SELF と同じ扱いにしている(SplitCalculator)ので、読み替えの対象も同じにする。

object SplitModeReading {
    /** 画面で使う方式。WHOLE はそのまま、それ以外(SELF と未知の値)は ITEM。 */
    fun modeOf(saved: String): String = if (saved == SplitMode.WHOLE) SplitMode.WHOLE else SplitMode.ITEM

    /** 保存が「自分だけ」扱いか(参加者を自分1人に絞る必要がある)。SELF と未知の値。 */
    fun isSelfOnly(saved: String): Boolean = saved != SplitMode.WHOLE && saved != SplitMode.ITEM
}
