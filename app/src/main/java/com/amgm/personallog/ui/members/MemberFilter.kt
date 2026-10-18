package com.amgm.personallog.ui.members

import com.amgm.personallog.data.model.Person
import java.text.Normalizer
import java.util.Locale

/**
 * 名前の照合用に正規化する(純関数)。
 *  - NFKC で全角の英数・記号を半角に、半角カタカナを全角カタカナにそろえる(全角空白は半角空白に)。
 *  - 英字は小文字にそろえる(大文字小文字を区別しない)。
 *  - ひらがなはカタカナに寄せる(ひらがな⇔カタカナを区別しない)。
 */
fun normalizeForMatch(text: String): String {
    val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    return buildString(nfkc.length) {
        for (ch in nfkc) {
            append(
                when (ch) {
                    // ぁ(U+3041)〜ゖ(U+3096) はカタカナ(+0x60)へ。ゝゞ(U+309D/U+309E)も同様。
                    in 'ぁ'..'ゖ', in 'ゝ'..'ゞ' -> ch + 0x60
                    else -> ch
                },
            )
        }
    }
}

/**
 * members を query で絞り込む(純関数・入力順を保つ)。
 *  - query が空(空白だけを含む)なら全件を返す。
 *  - それ以外は正規化した名前に、正規化した query が部分一致するものだけを返す。
 */
fun filterMembers(members: List<Person>, query: String): List<Person> {
    val q = normalizeForMatch(query).trim()
    if (q.isEmpty()) return members
    return members.filter { normalizeForMatch(it.name).contains(q) }
}
