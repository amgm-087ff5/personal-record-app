package com.amgm.personallog.ui.members

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amgm.personallog.data.model.Person
import com.amgm.personallog.data.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** メンバー管理画面の状態と操作。登録メンバーは PersonRepository.observeMembers(自分を除く)から取る。 */
@HiltViewModel
class MembersViewModel @Inject constructor(
    private val repository: PersonRepository,
) : ViewModel() {

    /** 登録メンバーの一覧(自分は含まない)。 */
    val members: StateFlow<List<Person>> = repository.observeMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 画面に出す一言(削除できない等)。null なら何も出さない。 */
    var message by mutableStateOf<String?>(null)
        private set

    /** 名前で追加する。同じ名前が既にあれば新しく作らず既存の人が使われる(findOrCreate の仕様)。空の名前は無視。 */
    fun add(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.findOrCreate(trimmed)
            message = null
        }
    }

    /** 名前を変える。空の名前は保存せず案内を出す。 */
    fun rename(person: Person, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            message = "名前を入力してください"
            return
        }
        viewModelScope.launch {
            repository.rename(person, trimmed)
            message = null
        }
    }

    /** 削除する。割り勘で使われていれば消せないので案内を出す。 */
    fun delete(person: Person) {
        viewModelScope.launch {
            val deleted = repository.delete(person)
            message = if (deleted) null else "割り勘で使われているため削除できません"
        }
    }
}
