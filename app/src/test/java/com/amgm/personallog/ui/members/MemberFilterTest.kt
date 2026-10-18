package com.amgm.personallog.ui.members

import com.amgm.personallog.data.model.Person
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberFilterTest {
    private fun p(id: Long, name: String) = Person(id = id, name = name, createdAt = 0)

    private val members = listOf(
        p(1, "山田太郎"),
        p(2, "佐藤花子"),
        p(3, "山本"),
    )

    private fun names(list: List<Person>) = list.map { it.name }

    @Test
    fun emptyQueryReturnsAllInOrder() {
        assertEquals(listOf("山田太郎", "佐藤花子", "山本"), names(filterMembers(members, "")))
        assertEquals(listOf("山田太郎", "佐藤花子", "山本"), names(filterMembers(members, "   ")))
    }

    @Test
    fun partialMatchKeepsOrder() {
        assertEquals(listOf("山田太郎", "山本"), names(filterMembers(members, "山")))
        assertEquals(listOf("佐藤花子"), names(filterMembers(members, "花子")))
        assertEquals(listOf("山田太郎"), names(filterMembers(members, "田太")))
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertEquals(emptyList<String>(), names(filterMembers(members, "鈴木")))
    }

    @Test
    fun queryIsTrimmed() {
        assertEquals(listOf("佐藤花子"), names(filterMembers(members, "  佐藤 ")))
    }

    @Test
    fun hiraganaAndKatakanaAreNotDistinguished() {
        val kana = listOf(p(1, "たなか"), p(2, "ヤマダ"))
        assertEquals(listOf("たなか"), names(filterMembers(kana, "タナ")))
        assertEquals(listOf("ヤマダ"), names(filterMembers(kana, "やまだ")))
        assertEquals(listOf("たなか"), names(filterMembers(listOf(p(1, "たなか")), "たなか")))
    }

    @Test
    fun fullWidthAndHalfWidthAreNotDistinguished() {
        val mixed = listOf(p(1, "ＡＢＣ太郎"), p(2, "xyz"), p(3, "ﾀﾅｶ"))
        // 全角英字の名前を半角で探す
        assertEquals(listOf("ＡＢＣ太郎"), names(filterMembers(mixed, "abc")))
        // 半角英字の名前を全角で探す
        assertEquals(listOf("xyz"), names(filterMembers(mixed, "ｘｙｚ")))
        // 半角カタカナの名前を全角で探す
        assertEquals(listOf("ﾀﾅｶ"), names(filterMembers(mixed, "タナカ")))
        // 全角数字で探す
        assertEquals(listOf("a1"), names(filterMembers(listOf(p(1, "ＡＢＣ太郎"), p(2, "a1")), "１")))
    }

    @Test
    fun caseIsNotDistinguished() {
        val en = listOf(p(1, "Alice"), p(2, "Bob"))
        assertEquals(listOf("Alice"), names(filterMembers(en, "ALI")))
        assertEquals(listOf("Bob"), names(filterMembers(en, "bO")))
    }

    @Test
    fun normalizeForMatchUnifiesKanaAndWidth() {
        assertEquals(normalizeForMatch("タナカ"), normalizeForMatch("たなか"))
        assertEquals(normalizeForMatch("ABC"), normalizeForMatch("ａｂｃ"))
        assertEquals(normalizeForMatch("ABC"), normalizeForMatch("abc"))
    }
}
