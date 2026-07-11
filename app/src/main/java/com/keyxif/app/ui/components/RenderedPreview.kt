package com.keyxif.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.keyxif.app.domain.model.PhotoItem
import com.keyxif.app.ui.KeyxifViewModel

@Composable
fun RenderedPreview(
    photo: PhotoItem,
    viewModel: KeyxifViewModel,
    renderKey: Any,
    modifier: Modifier = Modifier,
) {
    var preview by remember(photo.id, renderKey) {
        mutableStateOf(PreviewRenderState(isLoading = true))
    }

    LaunchedEffect(photo.id, renderKey) {
        preview = PreviewRenderState(isLoading = true)
        val rendered = runCatching {
            viewModel.renderPreviewBitmap(photo.id, GRID_PREVIEW_LONG_SIDE)
        }.getOrNull()
        preview = rendered?.let { PreviewRenderState(bitmap = it) }
            ?: PreviewRenderState(errorMessage = "템플릿 미리보기를 만들 수 없습니다.")
    }

    DisposableEffect(preview.bitmap) {
        val ownedBitmap = preview.bitmap
        onDispose { ownedBitmap?.recycle() }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            preview.bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = photo.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } ?: if (preview.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = preview.errorMessage ?: "미리보기 실패",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private data class PreviewRenderState(
    val isLoading: Boolean = false,
    val bitmap: Bitmap? = null,
    val errorMessage: String? = null,
)

private const val GRID_PREVIEW_LONG_SIDE = 1280
