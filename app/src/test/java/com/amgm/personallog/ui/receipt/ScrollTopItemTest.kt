package com.amgm.personallog.ui.receipt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 読み込み後に項目が増えても、一覧が途中から表示されないことを確かめる。
 * 先頭の項目を1つ(top)に集めた版は、読み込み後も先頭(index 0・オフセット0)のまま。
 * 条件付きの項目を「戻る」行の前に並べた旧い版は、先頭に見えていた項目が下にずれる。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ScrollTopItemTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topItemKeepsListAtTopWhenContentArrivesLater() {
        val loaded = mutableStateOf(false)
        val state = LazyListState()
        composeRule.setContent { TopItemList(loaded = loaded.value, state = state) }
        composeRule.runOnIdle { loaded.value = true }
        composeRule.waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)
    }

    @Test
    fun conditionalItemsBeforeNavShiftListWhenContentArrivesLater() {
        val loaded = mutableStateOf(false)
        val state = LazyListState()
        composeRule.setContent { ConditionalBeforeNavList(loaded = loaded.value, state = state) }
        composeRule.runOnIdle { loaded.value = true }
        composeRule.waitForIdle()
        assertEquals(1, state.firstVisibleItemIndex)
    }
}

/** 先頭の項目を1つにまとめた版。中身は読み込み後に入る。読み込み前も最小1dpを持つ(高さ0だと先頭から外れる)。 */
@Composable
private fun TopItemList(loaded: Boolean, state: LazyListState) {
    LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(300.dp)) {
        item(key = "top") {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 1.dp)) {
                if (loaded) Box(modifier = Modifier.fillMaxWidth().height(200.dp))
            }
        }
        item(key = "nav") { Box(modifier = Modifier.fillMaxWidth().height(40.dp)) }
        item(key = "body") { Box(modifier = Modifier.fillMaxWidth().height(1000.dp)) }
    }
}

/** 旧い構造。読み込み後に条件付きの項目を「戻る」行の前に足す。 */
@Composable
private fun ConditionalBeforeNavList(loaded: Boolean, state: LazyListState) {
    LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(300.dp)) {
        if (loaded) {
            item(key = "dash") { Box(modifier = Modifier.fillMaxWidth().height(200.dp)) }
        }
        item(key = "nav") { Box(modifier = Modifier.fillMaxWidth().height(40.dp)) }
        item(key = "body") { Box(modifier = Modifier.fillMaxWidth().height(1000.dp)) }
    }
}
