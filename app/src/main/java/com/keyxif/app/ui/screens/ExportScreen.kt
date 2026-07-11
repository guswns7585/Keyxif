package com.keyxif.app.ui.screens

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.keyxif.app.domain.model.PhotoItem
import com.keyxif.app.domain.model.RenderStatus
import com.keyxif.app.ui.KeyxifUiState
import com.keyxif.app.ui.KeyxifViewModel
import com.keyxif.app.ui.components.RenderedPreview
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExportScreen(
    state: KeyxifUiState,
    viewModel: KeyxifViewModel,
    onSaveOne: (String) -> Unit,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
) {
    var expandedPhotoId by remember { mutableStateOf<String?>(null) }
    val selectionMode = state.selectedExportPhotoIds.isNotEmpty()

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

        if (selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "선택된 사진 ${state.selectedExportPhotoIds.size}장",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    enabled = !state.exportProgress.isSaving,
                    onClick = onClearSelection,
                ) {
                    Text("선택 해제")
                }
            }
        }

        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Adaptive(180.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.photos, key = { it.id }) { photo ->
                val selectedForExport = photo.id in state.selectedExportPhotoIds
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        RenderedPreview(
                            photo = photo,
                            viewModel = viewModel,
                            renderKey = previewRenderKey("grid", state, photo),
                            modifier = Modifier.combinedClickable(
                                enabled = !state.exportProgress.isSaving,
                                onClick = {
                                    if (selectionMode) {
                                        onSelectionChange(photo.id, !selectedForExport)
                                    } else if (state.settings.enableExportPreviewZoom) {
                                        expandedPhotoId = photo.id
                                    }
                                },
                                onLongClick = {
                                    onSelectionChange(photo.id, true)
                                },
                            ),
                        )
                        if (selectionMode) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(26.dp),
                                shape = MaterialTheme.shapes.small,
                                color = if (selectedForExport) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                                },
                            ) {
                                if (selectedForExport) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "선택됨",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
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
        DialogBlurBehindEffect()
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            color = Color.Transparent,
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
private fun DialogBlurBehindEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val originalDimAmount = window?.attributes?.dimAmount ?: 0f
        if (window != null) {
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val attributes = window.attributes
            attributes.dimAmount = 0.16f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                attributes.blurBehindRadius = 48
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
            window.attributes = attributes
        }
        onDispose {
            if (window != null) {
                val attributes = window.attributes
                attributes.dimAmount = originalDimAmount
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    attributes.blurBehindRadius = 0
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
                window.attributes = attributes
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

    fun setScaleAndOffset(nextScale: Float, nextOffset: Offset) {
        scale = if (nextScale <= 1.01f) 1f else nextScale.coerceIn(1f, MAX_PREVIEW_SCALE)
        offset = if (scale <= 1.01f) Offset.Zero else nextOffset.coerceForScale(scale, containerSize)
        onZoomStateChange(scale > 1.01f)
    }

    LaunchedEffect(photo.id, renderKey) {
        preview = PreviewRenderState(isLoading = true)
        val rendered = runCatching { viewModel.renderPreviewBitmap(photo.id, FULLSCREEN_PREVIEW_LONG_SIDE) }.getOrNull()
        val fallback = rendered ?: runCatching { viewModel.renderSourcePreviewBitmap(photo.id, FULLSCREEN_PREVIEW_LONG_SIDE) }.getOrNull()
        preview = fallback?.let { PreviewRenderState(bitmap = it) }
            ?: PreviewRenderState(errorMessage = "미리보기를 만들 수 없습니다.")
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
            .background(Color.Transparent)
            .onSizeChanged { containerSize = it }
            .pointerInput(containerSize) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.size >= 2 -> {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val nextScale = (scale * zoomChange).coerceIn(1f, MAX_PREVIEW_SCALE)
                                setScaleAndOffset(nextScale, offset + panChange)
                                event.changes.forEach { it.consume() }
                            }
                            pressed.size == 1 && scale > 1.01f -> {
                                val panChange = pressed.first().positionChange()
                                if (panChange != Offset.Zero) {
                                    setScaleAndOffset(scale, offset + panChange)
                                    pressed.first().consume()
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(photo.id) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            setScaleAndOffset(1f, Offset.Zero)
                        } else {
                            setScaleAndOffset(2.4f, Offset.Zero)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        preview.bitmap?.let { bitmap ->
            val visualScale = scale * PREVIEW_EDGE_COVER_SCALE
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = photo.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = visualScale
                        scaleY = visualScale
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
        state.settings.showPaletteColors,
        state.settings.paletteColorCount,
        state.settings.paletteAnalysisMode.name,
        state.settings.paletteCenterCropRatio,
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
private const val PREVIEW_EDGE_COVER_SCALE = 1.003f
