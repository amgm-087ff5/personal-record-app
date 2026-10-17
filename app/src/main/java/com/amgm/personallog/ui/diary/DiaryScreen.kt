package com.amgm.personallog.ui.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.ScreenHeader
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable
import java.io.File

/** 日記タブのトップ(一覧)。onOpenDate は "yyyy-MM-dd" の編集画面を開く。 */
@Composable
fun DiaryScreen(
    modifier: Modifier = Modifier,
    onOpenDate: (String) -> Unit = {},
) {
    val viewModel: DiaryListViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = remember { DiaryLogic.today() }
    val months = remember(state.items) { state.items.groupBy { it.entry.date.take(7) } }

    PaperScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                kicker = diaryMonthLabel(today),
                title = "日記",
                trailing = { TextAction(text = "今日を書く", onClick = { onOpenDate(today) }) },
            )
            when {
                state.error != null -> Text(
                    state.error.orEmpty(),
                    style = AppTheme.type.body,
                    color = AppTheme.colors.danger,
                    modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.m),
                )
                state.loading -> Unit
                state.items.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = Space.edge),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "まだ何もない一日。",
                        style = AppTheme.type.headline,
                        color = AppTheme.colors.ink,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.l))
                    InkButton(text = "今日を書く", onClick = { onOpenDate(today) })
                    Spacer(Modifier.height(Space.xl))
                }
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    months.forEach { (month, list) ->
                        item(key = "month-$month") {
                            Column(modifier = Modifier.padding(top = Space.m)) {
                                Label(
                                    diaryMonthLabel(month + "-01"),
                                    modifier = Modifier.padding(horizontal = Space.edge, vertical = Space.s),
                                )
                                Hairline(color = AppTheme.colors.ink)
                            }
                        }
                        items(list, key = { it.entry.id }) { item ->
                            DiaryEntryRow(item = item, onClick = { onOpenDate(item.entry.date) })
                        }
                    }
                    item(key = "bottom-space") { Spacer(Modifier.height(Space.xl)) }
                }
            }
        }
    }
}

/** 1日分。左に日付列、右に写真と本文。 */
@Composable
private fun DiaryEntryRow(item: DiaryListItem, onClick: () -> Unit) {
    val c = AppTheme.colors
    val date = remember(item.entry.date) { parseDiaryDate(item.entry.date) }
    val text = item.entry.text.trim()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tappable(onClick)
                .padding(horizontal = Space.edge, vertical = Space.m),
        ) {
            Column(
                modifier = Modifier.width(60.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    date?.day?.toString() ?: item.entry.date,
                    style = AppTheme.type.numeralXL,
                    color = c.ink,
                )
                if (date != null) Label(date.weekdayShort)
            }
            Spacer(Modifier.width(Space.m))
            Column(modifier = Modifier.weight(1f)) {
                val photos = item.photos
                if (photos.isNotEmpty()) {
                    PhotoThumb(
                        relativePath = photos.first().filePath,
                        reqPx = 720,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                    )
                    if (text.isNotEmpty()) {
                        Text(
                            text,
                            style = AppTheme.type.prose,
                            color = c.ink,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Space.s),
                        )
                    }
                    val rest = photos.drop(1)
                    if (rest.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = Space.s),
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            rest.take(3).forEach { p ->
                                PhotoThumb(relativePath = p.filePath, reqPx = 200, modifier = Modifier.size(56.dp))
                            }
                            if (rest.size > 3) {
                                Label(
                                    "+${rest.size - 3}",
                                    modifier = Modifier.padding(start = Space.xs),
                                    color = c.inkSoft,
                                )
                            }
                        }
                    }
                } else {
                    val big = AppTheme.type.prose.copy(
                        fontSize = AppTheme.type.title.fontSize,
                        lineHeight = AppTheme.type.title.lineHeight,
                    )
                    Text(
                        text,
                        style = big,
                        color = c.ink,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Hairline(modifier = Modifier.padding(horizontal = Space.edge))
    }
}

/** filesDir 相対パスの写真をサムネとして表示(中央クロップ、角2dp)。 */
@Composable
fun PhotoThumb(relativePath: String, reqPx: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = remember(relativePath) { File(context.filesDir, relativePath) }
    val bitmap by rememberPhotoBitmap(file, reqPx)
    Box(
        modifier = modifier.clip(RoundedCornerShape(2.dp)),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
