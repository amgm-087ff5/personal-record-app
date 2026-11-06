package com.amgm.personallog.v4.ui.components

import com.amgm.personallog.v4.data.AppData
import com.amgm.personallog.v4.data.todos

/** 期限切れの未完了ToDoの件数(ToDoのタブのバッジ。ui.js overdueCount) */
fun AppData.overdueCount(today: String): Int = todos.values.count { it.done == null && it.due != null && it.due < today }
