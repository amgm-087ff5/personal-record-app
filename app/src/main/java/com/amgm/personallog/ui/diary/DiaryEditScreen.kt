@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.amgm.personallog.ui.diary

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amgm.personallog.data.model.DiaryPhoto
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.BareField
import com.amgm.personallog.ui.design.Hairline
import com.amgm.personallog.ui.design.InkButton
import com.amgm.personallog.ui.design.InkButtonKind
import com.amgm.personallog.ui.design.InkSheet
import com.amgm.personallog.ui.design.Label
import com.amgm.personallog.ui.design.PaperScreen
import com.amgm.personallog.ui.design.Space
import com.amgm.personallog.ui.design.TextAction
import com.amgm.personallog.ui.design.tappable
import java.io.File

@Composable
fun DiaryEditScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DiaryEditViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteEntry by rememberSaveable { mutableStateOf(false) }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.addPhotos(uris)
    }

    LaunchedEffect(state.closed) {
        if (state.closed) onClose()
    }
    BackHandler { viewModel.saveAndClose() }

    val importing = state.importing != null
    val date = remember(state.date) { parseDiaryDate(state.date) }

    PaperScreen(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // 上端の細い行: 閉じる / 保存
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(text = "閉じる", onClick = { viewModel.saveAndClose() }, color = c.inkSoft)
                Spacer(Modifier.weight(1f))
                TextAction(text = "保存", onClick = { viewModel.saveAndClose() }, enabled = !state.loading)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.edge),
            ) {
                // 巨大な日付。タップで日付選択
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tappable { if (!importing && !state.loading) showDatePicker = true }
                        .padding(vertical = Space.m),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (date != null) {
                        Text(date.day.toString(), style = AppTheme.type.display, color = c.ink)
                        Spacer(Modifier.width(Space.m))
                        Column(modifier = Modifier.padding(bottom = Space.s)) {
                            Label("${date.year}年${date.month}月")
                            Label(date.weekdayLong, color = c.accent)
                        }
                    } else {
                        Text(DiaryLogic.formatDateLabel(state.date), style = AppTheme.type.headline, color = c.ink)
                    }
                }
                Hairline(color = c.ink)

                // 本文: 枠なし、紙に直接書く
                BareField(
                    value = viewModel.text,
                    onValueChange = viewModel::onTextChange,
                    textStyle = AppTheme.type.prose,
                    placeholder = "今日のこと…",
                    minLines = 8,
                    readOnly = state.loading,
                    modifier = Modifier.padding(top = Space.l),
                )

                state.error?.let { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            message,
                            style = AppTheme.type.body,
                            color = c.danger,
                            modifier = Modifier.weight(1f),
                        )
                        TextAction(text = "閉じる", onClick = { viewModel.clearError() }, color = c.inkSoft)
                    }
                }

                // 写真: フィルムストリップ
                Spacer(Modifier.height(Space.l))
                Hairline(dashed = true)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.m, bottom = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label("写真 ${state.photos.size}枚")
                    state.importing?.let { p ->
                        Label(
                            "  写真を取り込み中… ${p.done} / ${p.total}",
                            color = c.accent,
                        )
                    }
                }
                // 写真は読み込み後に後から入る。LazyRow だと、読み込み前からある「追加」の項目が先頭のまま残り、
                // 後から入った写真が左にはみ出すため、横スクロールの Row にまとめる(写真の数は少ない)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    state.photos.forEachIndexed { index, photo ->
                        PhotoThumb(
                            relativePath = photo.filePath,
                            reqPx = 360,
                            modifier = Modifier
                                .size(120.dp)
                                .combinedClickable(
                                    onClick = { viewerIndex = index },
                                    onLongClick = { pendingDeleteId = photo.id },
                                ),
                        )
                    }
                    AddPhotoTile(
                        enabled = !importing && !state.loading,
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
                if (state.photos.isNotEmpty()) {
                    Label("タップで拡大、長押しで削除", modifier = Modifier.padding(top = Space.s))
                }

                // 削除は最下部に離して
                Spacer(Modifier.height(Space.xl))
                Hairline()
                TextAction(
                    text = "この日の記録を消す",
                    onClick = { showDeleteEntry = true },
                    color = c.danger,
                    enabled = !state.loading,
                )
                Spacer(Modifier.height(Space.xl))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = DiaryLogic.dateToUtcMillis(state.date))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextAction(text = "OK", onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.changeDate(DiaryLogic.utcMillisToDate(it)) }
                    showDatePicker = false
                })
            },
            dismissButton = { TextAction(text = "キャンセル", onClick = { showDatePicker = false }, color = c.inkSoft) },
        ) { DatePicker(state = pickerState) }
    }

    if (showDeleteEntry) {
        ConfirmSheet(
            message = "この日の日記と写真(${state.photos.size}枚)をすべて削除します。元に戻せません。",
            confirmLabel = "削除",
            onConfirm = {
                showDeleteEntry = false
                viewModel.deleteEntry()
            },
            onDismiss = { showDeleteEntry = false },
        )
    }

    val pendingPhoto = state.photos.firstOrNull { it.id == pendingDeleteId }
    if (pendingDeleteId != null) {
        if (pendingPhoto == null) {
            pendingDeleteId = null
        } else {
            ConfirmSheet(
                message = "この写真を削除しますか?",
                confirmLabel = "削除",
                onConfirm = {
                    viewModel.deletePhoto(pendingPhoto)
                    pendingDeleteId = null
                },
                onDismiss = { pendingDeleteId = null },
            )
        }
    }

    viewerIndex?.let { start ->
        if (state.photos.isEmpty()) {
            viewerIndex = null
        } else {
            PhotoViewerDialog(
                photos = state.photos,
                startIndex = start.coerceIn(0, state.photos.size - 1),
                onDelete = { pendingDeleteId = it.id },
                onDismiss = { viewerIndex = null },
            )
        }
    }
}

/** フィルムストリップ末尾の、破線枠の「＋」タイル。 */
@Composable
private fun AddPhotoTile(enabled: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val stroke = if (enabled) c.inkFaint else c.line
    Box(
        modifier = Modifier
            .size(120.dp)
            .drawBehind {
                drawRoundRect(
                    color = stroke,
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            }
            .tappable { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("＋", style = AppTheme.type.numeralL, color = if (enabled) c.inkSoft else c.inkFaint)
            Label("写真を追加")
        }
    }
}

@Composable
private fun ConfirmSheet(message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    InkSheet(onDismiss = onDismiss) {
        Text(message, style = AppTheme.type.prose, color = AppTheme.colors.ink)
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            InkButton(
                text = "キャンセル",
                onClick = onDismiss,
                kind = InkButtonKind.Outline,
                modifier = Modifier.weight(1f),
            )
            InkButton(text = confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhotoViewerDialog(
    photos: List<DiaryPhoto>,
    startIndex: Int,
    onDelete: (DiaryPhoto) -> Unit,
    onDismiss: () -> Unit,
) {
    val photosState = rememberUpdatedState(photos)
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { photosState.value.size })
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val photo = photosState.value.getOrNull(page)
                if (photo != null) {
                    val file = remember(photo.filePath) { File(context.filesDir, photo.filePath) }
                    val bitmap by rememberPhotoBitmap(file, 2048)
                    val bmp = bitmap
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text("読み込み中…", style = AppTheme.type.body, color = Color.White)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = Space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${photos.size}",
                    style = AppTheme.type.numeral,
                    color = Color.White,
                    modifier = Modifier.padding(start = Space.s),
                )
                Row {
                    TextAction(
                        text = "削除",
                        onClick = { photos.getOrNull(pagerState.currentPage)?.let(onDelete) },
                        color = Color.White,
                    )
                    TextAction(text = "閉じる", onClick = onDismiss, color = Color.White)
                }
            }
        }
    }
}
