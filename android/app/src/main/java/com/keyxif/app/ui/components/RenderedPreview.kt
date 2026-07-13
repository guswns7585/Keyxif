package com.keyxif.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.coroutines.cancellation.CancellationException

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
    var retryAttempt by remember(photo.id, renderKey) { mutableIntStateOf(0) }

    LaunchedEffect(photo.id, renderKey, retryAttempt) {
        preview = PreviewRenderState(isLoading = true)
        val rendered = renderOrNull {
            viewModel.renderPreviewBitmap(photo.id, GRID_PREVIEW_LONG_SIDE)
        }
        preview = when {
            rendered != null -> PreviewRenderState(bitmap = rendered)
            else -> {
                // 템플릿 렌더링이 실패해도 원본 사진이라도 보여준다.
                val fallback = renderOrNull {
                    viewModel.renderSourcePreviewBitmap(photo.id, GRID_PREVIEW_LONG_SIDE)
                }
                fallback?.let { PreviewRenderState(bitmap = it, isFallback = true) }
                    ?: PreviewRenderState(errorMessage = "미리보기 생성 실패")
            }
        }
    }

    DisposableEffect(preview.bitmap) {
        val ownedBitmap = preview.bitmap
        onDispose { ownedBitmap?.recycle() }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(contentAlignment = Alignment.Center) {
            preview.bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = photo.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (preview.isFallback) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    ) {
                        Text(
                            text = "원본 표시 중",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            } ?: if (preview.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = preview.errorMessage ?: "미리보기 생성 실패",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { retryAttempt++ }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(text = " 다시 시도", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * 렌더링 실패만 null로 바꾸고, 코루틴 취소는 그대로 전파한다.
 * (취소를 실패로 처리하면 스크롤 중 지나간 셀마다 에러 문구가 떠 버린다.)
 */
internal suspend fun renderOrNull(block: suspend () -> Bitmap?): Bitmap? {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("KeyxifPreview", "preview render failed", e)
        null
    } catch (e: OutOfMemoryError) {
        android.util.Log.w("KeyxifPreview", "preview render OOM", e)
        null
    }
}

private data class PreviewRenderState(
    val isLoading: Boolean = false,
    val bitmap: Bitmap? = null,
    val isFallback: Boolean = false,
    val errorMessage: String? = null,
)

private const val GRID_PREVIEW_LONG_SIDE = 768
