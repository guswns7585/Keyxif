package com.keyxif.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keyxif.app.domain.model.PhotoItem
import com.keyxif.app.domain.model.RenderStatus
import com.keyxif.app.ui.KeyxifUiState
import com.keyxif.app.ui.KeyxifViewModel
import com.keyxif.app.ui.components.RenderedPreview
import kotlin.math.max

@Composable
fun ExportScreen(
    state: KeyxifUiState,
    viewModel: KeyxifViewModel,
    onSaveOne: (String) -> Unit,
) {
    var expandedPhotoId by remember { mutableStateOf<String?>(null) }

    expandedPhotoId?.let { photoId ->
        if (state.settings.enableExportPreviewZoom) {
            FullscreenPreviewDialog(
                photos = state.photos,
                initialPhotoId = photoId,
                state = state,
                viewModel = viewModel,
                onDismiss = { expandedPhotoId = null },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "미리보기",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "WEBP 품질 ${state.settings.webpQuality}% · Pictures/${state.settings.saveDirectoryName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.exportProgress.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.exportProgress.total > 0) {
            val progress = state.exportProgress.current.toFloat() / state.exportProgress.total.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${state.exportProgress.current} / ${state.exportProgress.total} · 성공 ${state.exportProgress.successCount} · 실패 ${state.exportProgress.failureCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Adaptive(180.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.photos, key = { it.id }) { photo ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RenderedPreview(
                        photo = photo,
                        viewModel = viewModel,
                        renderKey = previewRenderKey("grid", state, photo),
                        modifier = if (state.settings.enableExportPreviewZoom) {
                            Modifier.clickable { expandedPhotoId = photo.id }
                        } else {
                            Modifier
                        },
                    )
                    Text(
                        text = photo.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = photo.statusText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (photo.renderStatus == RenderStatus.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedButton(
                        enabled = !state.exportProgress.isSaving,
                        onClick = { onSaveOne(photo.id) },
                    ) {
                        Text("이 사진 저장")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenPreviewDialog(
    photos: List<PhotoItem>,
    initialPhotoId: String,
    state: KeyxifUiState,
    viewModel: KeyxifViewModel,
    onDismiss: () -> Unit,
) {
    if (photos.isEmpty()) return
    val initialPage = photos.indexOfFirst { it.id == initialPhotoId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { photos.size },
    )
    val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: photos.first()
    var pagerScrollEnabled by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = pagerScrollEnabled,
                ) { page ->
                    val pagePhoto = photos[page]
                    ZoomableRenderedPreview(
                        photo = pagePhoto,
                        viewModel = viewModel,
                        renderKey = previewRenderKey("fullscreen", state, pagePhoto),
                        onZoomStateChange = { zoomed -> pagerScrollEnabled = !zoomed },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size} · ${currentPhoto.displayName}",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableRenderedPreview(
    photo: PhotoItem,
    viewModel: KeyxifViewModel,
    renderKey: Any,
    onZoomStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var preview by remember(photo.id, renderKey) { mutableStateOf(PreviewRenderState(isLoading = true)) }
    var scale by remember(photo.id, renderKey) { mutableStateOf(1f) }
    var offset by remember(photo.id, renderKey) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(photo.id, renderKey) {
        preview = PreviewRenderState(isLoading = true)
        preview = runCatching { viewModel.renderPreviewBitmap(photo.id, FULLSCREEN_PREVIEW_LONG_SIDE) }.fold(
            onSuccess = { bitmap ->
                bitmap?.let { PreviewRenderState(bitmap = it) }
                    ?: PreviewRenderState(errorMessage = "미리보기를 만들 수 없습니다.")
            },
            onFailure = { PreviewRenderState(errorMessage = "미리보기 렌더링 실패") },
        )
        scale = 1f
        offset = Offset.Zero
        onZoomStateChange(false)
    }

    DisposableEffect(preview.bitmap) {
        val ownedBitmap = preview.bitmap
        onDispose { ownedBitmap?.recycle() }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .navigationBarsPadding()
            .onSizeChanged { containerSize = it }
            .then(
                if (scale > 1.01f) {
                    Modifier.pointerInput(containerSize, scale) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset = (offset + dragAmount).coerceForScale(scale, containerSize)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .pointerInput(containerSize) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomStateChange(false)
                        } else {
                            scale = 2.4f
                            offset = Offset.Zero
                            onZoomStateChange(true)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        preview.bitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = photo.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )
        } ?: if (preview.isLoading) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Text(
                text = preview.errorMessage ?: "미리보기 실패",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun previewRenderKey(
    prefix: String,
    state: KeyxifUiState,
    photo: PhotoItem,
): String {
    return listOf(
        prefix,
        state.selectedTemplate.name,
        photo.buildInfo.hashCode(),
        photo.analysisResult.paletteColors.hashCode(),
        state.settings.textScale,
        state.settings.nicknameStyle.name,
        state.settings.showBuildInfoInPlainExport,
        state.settings.showPaletteColors,
        state.settings.paletteColorCount,
        state.settings.paletteAnalysisMode.name,
        state.settings.autoSelectLogoContrastVariant,
    ).joinToString("-")
}

private fun Offset.coerceForScale(
    scale: Float,
    size: IntSize,
): Offset {
    if (scale <= 1.01f || size.width <= 0 || size.height <= 0) return Offset.Zero
    val maxX = max(0f, size.width * (scale - 1f) / 2f)
    val maxY = max(0f, size.height * (scale - 1f) / 2f)
    return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
}

private fun PhotoItem.statusText(): String {
    return when (renderStatus) {
        RenderStatus.Idle -> "대기"
        RenderStatus.Rendering -> "저장 중"
        RenderStatus.Saved -> "완료"
        RenderStatus.Error -> errorMessage ?: "실패"
    }
}

private data class PreviewRenderState(
    val isLoading: Boolean = false,
    val bitmap: Bitmap? = null,
    val errorMessage: String? = null,
)

private const val MAX_PREVIEW_SCALE = 5f
private const val FULLSCREEN_PREVIEW_LONG_SIDE = 2048
