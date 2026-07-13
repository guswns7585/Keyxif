package com.keyxif.app.ui.screens

import android.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.MaskStroke
import com.keyxif.app.domain.model.NormalizedPoint
import com.keyxif.app.domain.model.NormalizedRect
import com.keyxif.app.domain.model.PaletteAnalysisMode
import com.keyxif.app.domain.model.PhotoRenderStyle
import com.keyxif.app.domain.model.defaultPaletteAnalysisRect
import com.keyxif.app.domain.model.displayName
import com.keyxif.app.ui.KeyxifUiState
import kotlin.math.hypot
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PaletteScreen(
    state: KeyxifUiState,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onSelectPhoto: (String) -> Unit,
    onRenderStyleChange: ((PhotoRenderStyle) -> PhotoRenderStyle) -> Unit,
    onAnalysisModeChange: (PaletteAnalysisMode) -> Unit,
    onAnalysisRectChange: (NormalizedRect) -> Unit,
    onCenterRatioChange: (Float) -> Unit,
    onMaskChange: (List<MaskStroke>) -> Unit,
    onReanalyze: () -> Unit,
) {
    val settings = state.settings
    val photo = state.selectedPhoto
    val result = photo?.analysisResult
    val renderStyle = photo?.renderStyle ?: PhotoRenderStyle()
    val colors = result?.paletteColors.orEmpty()
    var imageAspect by remember(photo?.id) { mutableFloatStateOf(4f / 3f) }
    var editingRect by remember(photo?.id, result?.analysisRectNormalized) {
        mutableStateOf(result?.analysisRectNormalized ?: defaultPaletteAnalysisRect())
    }
    var editingStrokes by remember(photo?.id, result?.paintedMaskStrokes) {
        mutableStateOf(result?.paintedMaskStrokes.orEmpty())
    }
    var isEraser by remember(photo?.id) { mutableStateOf(false) }
    var brushSize by remember(photo?.id) { mutableFloatStateOf(0.06f) }
    var centerRatio by remember(photo?.id, result?.analysisCenterCropRatio) {
        mutableFloatStateOf(result?.analysisCenterCropRatio ?: 0.75f)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("색상 분석", style = MaterialTheme.typography.titleLarge)
        Text("사진마다 분석 영역과 카드 배경색을 따로 설정합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (state.photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.photos, key = { it.id }) { item ->
                    val selected = item.id == photo?.id
                    Surface(
                        modifier = Modifier.size(72.dp).then(
                            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier,
                        ),
                        shape = RoundedCornerShape(6.dp),
                        onClick = { onSelectPhoto(item.id) },
                    ) {
                        AsyncImage(model = item.uri, contentDescription = null, contentScale = ContentScale.Crop)
                    }
                }
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("대표 색상 분석 방식", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaletteAnalysisMode.entries.forEach { mode ->
                        FilterChip(
                            selected = result?.analysisMode == mode,
                            onClick = { onAnalysisModeChange(mode) },
                            label = { Text(mode.displayName()) },
                        )
                    }
                }
                if (result?.isAnalyzing == true) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("대표 색상을 분석하고 있습니다.", style = MaterialTheme.typography.bodySmall)
                }
                result?.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (photo != null) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(imageAspect.coerceIn(0.35f, 3.5f))
                    .clip(RoundedCornerShape(6.dp)).background(Color.BLACK.toComposeColor()),
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { success ->
                        val width = success.result.drawable.intrinsicWidth
                        val height = success.result.drawable.intrinsicHeight
                        if (width > 0 && height > 0) imageAspect = width.toFloat() / height
                    },
                )
                when (result?.analysisMode) {
                    PaletteAnalysisMode.AutoCenter -> AutoCenterOverlay(centerRatio)
                    PaletteAnalysisMode.RectSelection -> RectEditorOverlay(editingRect) { editingRect = it }
                    PaletteAnalysisMode.PaintedMask -> MaskEditorOverlay(
                        strokes = editingStrokes,
                        brushSize = brushSize,
                        isEraser = isEraser,
                        onStroke = { editingStrokes = editingStrokes + it },
                    )
                    else -> Unit
                }
            }

            when (result?.analysisMode) {
                PaletteAnalysisMode.AutoCenter -> {
                    Text("중앙 영역 범위 ${(centerRatio * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = centerRatio,
                        onValueChange = { centerRatio = it },
                        onValueChangeFinished = { onCenterRatioChange(centerRatio); onReanalyze() },
                        valueRange = 0.35f..1f,
                    )
                    Button(onClick = { onCenterRatioChange(centerRatio); onReanalyze() }) { Text("중앙 기준으로 다시 분석") }
                }
                PaletteAnalysisMode.RectSelection -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingRect = centerRect(editingRect) }) { Text("중앙으로") }
                        Button(onClick = { onAnalysisRectChange(editingRect); onReanalyze() }) { Text("적용") }
                    }
                }
                PaletteAnalysisMode.PaintedMask -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isEraser, onClick = { isEraser = false }, label = { Text("브러시") })
                        FilterChip(selected = isEraser, onClick = { isEraser = true }, label = { Text("지우개") })
                        listOf("작게" to 0.03f, "보통" to 0.06f, "크게" to 0.11f).forEach { (label, size) ->
                            FilterChip(selected = brushSize == size, onClick = { brushSize = size }, label = { Text(label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingStrokes = emptyList() }) { Text("전체 지우기") }
                        OutlinedButton(
                            enabled = editingStrokes.isNotEmpty(),
                            onClick = { editingStrokes = editingStrokes.dropLast(1) },
                        ) { Text("되돌리기") }
                        Button(onClick = { onMaskChange(editingStrokes); onReanalyze() }) { Text("적용") }
                    }
                }
                null -> Unit
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("추출된 색상", style = MaterialTheme.typography.titleMedium)
                if (colors.isEmpty()) {
                    Text(if (photo == null) "먼저 사진을 추가해 주세요." else "추출된 색상이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        colors.take(settings.paletteColorCount).forEachIndexed { index, color ->
                            FilterChip(
                                selected = renderStyle.paletteBackgroundColorIndex == index,
                                onClick = { onRenderStyleChange { it.copy(paletteBackgroundColorIndex = index) } },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(24.dp).clip(CircleShape).background(color.toComposeColor()).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                                        Spacer(Modifier.width(8.dp)); Text("#${color.toRgbHex()}")
                                    }
                                },
                            )
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("선택한 색상을 카드 배경으로 사용", style = MaterialTheme.typography.titleMedium)
                        Text("현재 사진에만 적용됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = renderStyle.usePaletteColorForCardBackground,
                        onCheckedChange = { checked -> onRenderStyleChange { it.copy(usePaletteColorForCardBackground = checked) } },
                        enabled = colors.isNotEmpty(),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AutoCenterOverlay(ratio: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val safeRatio = ratio.coerceIn(0.35f, 1f)
        val left = (1f - safeRatio) * size.width / 2f
        val top = (1f - safeRatio) * size.height / 2f
        drawRect(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(size.width * safeRatio, size.height * safeRatio),
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

@Composable
private fun RectEditorOverlay(rect: NormalizedRect, onChange: (NormalizedRect) -> Unit) {
    val latestRect by rememberUpdatedState(rect)
    val pinColor = MaterialTheme.colorScheme.primary
    var dragHandle by remember { mutableStateOf(RectDragHandle.None) }
    var dragOrigin by remember { mutableStateOf(rect) }
    var dragStart by remember { mutableStateOf(NormalizedPoint(0f, 0f)) }
    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    val current = latestRect
                    dragOrigin = current
                    dragStart = NormalizedPoint(offset.x / size.width, offset.y / size.height)
                    val corners = listOf(
                        RectDragHandle.TopLeft to androidx.compose.ui.geometry.Offset(current.left * size.width, current.top * size.height),
                        RectDragHandle.TopRight to androidx.compose.ui.geometry.Offset(current.right * size.width, current.top * size.height),
                        RectDragHandle.BottomLeft to androidx.compose.ui.geometry.Offset(current.left * size.width, current.bottom * size.height),
                        RectDragHandle.BottomRight to androidx.compose.ui.geometry.Offset(current.right * size.width, current.bottom * size.height),
                    )
                    dragHandle = corners.minByOrNull { (_, point) -> hypot(offset.x - point.x, offset.y - point.y) }
                        ?.takeIf { (_, point) -> hypot(offset.x - point.x, offset.y - point.y) <= 32.dp.toPx() }
                        ?.first
                        ?: if (dragStart.x in current.left..current.right && dragStart.y in current.top..current.bottom) RectDragHandle.Move else RectDragHandle.None
                },
                onDrag = { change, amount ->
                    change.consume()
                    val point = NormalizedPoint(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        (change.position.y / size.height).coerceIn(0f, 1f),
                    )
                    val minSize = 0.08f
                    val next = when (dragHandle) {
                        RectDragHandle.Move -> {
                            val width = dragOrigin.right - dragOrigin.left
                            val height = dragOrigin.bottom - dragOrigin.top
                            val left = (dragOrigin.left + point.x - dragStart.x).coerceIn(0f, 1f - width)
                            val top = (dragOrigin.top + point.y - dragStart.y).coerceIn(0f, 1f - height)
                            NormalizedRect(left, top, left + width, top + height)
                        }
                        RectDragHandle.TopLeft -> dragOrigin.copy(left = point.x.coerceAtMost(dragOrigin.right - minSize), top = point.y.coerceAtMost(dragOrigin.bottom - minSize))
                        RectDragHandle.TopRight -> dragOrigin.copy(right = point.x.coerceAtLeast(dragOrigin.left + minSize), top = point.y.coerceAtMost(dragOrigin.bottom - minSize))
                        RectDragHandle.BottomLeft -> dragOrigin.copy(left = point.x.coerceAtMost(dragOrigin.right - minSize), bottom = point.y.coerceAtLeast(dragOrigin.top + minSize))
                        RectDragHandle.BottomRight -> dragOrigin.copy(right = point.x.coerceAtLeast(dragOrigin.left + minSize), bottom = point.y.coerceAtLeast(dragOrigin.top + minSize))
                        RectDragHandle.None -> latestRect
                    }
                    onChange(next.normalized())
                },
                onDragEnd = { dragHandle = RectDragHandle.None },
                onDragCancel = { dragHandle = RectDragHandle.None },
            )
        },
    ) {
        val left = rect.left * size.width; val top = rect.top * size.height
        val right = rect.right * size.width; val bottom = rect.bottom * size.height
        drawRect(androidx.compose.ui.graphics.Color(0x4400C8FF), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top))
        drawRect(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top), style = Stroke(3.dp.toPx()))
        listOf(
            androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(right, top),
            androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(right, bottom),
        ).forEach { point ->
            drawCircle(androidx.compose.ui.graphics.Color.White, 10.dp.toPx(), point)
            drawCircle(pinColor, 7.dp.toPx(), point)
        }
    }
}

@Composable
private fun MaskEditorOverlay(
    strokes: List<MaskStroke>,
    brushSize: Float,
    isEraser: Boolean,
    onStroke: (MaskStroke) -> Unit,
) {
    val current = remember { mutableStateListOf<NormalizedPoint>() }
    Canvas(
        Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.pointerInput(brushSize, isEraser, strokes) {
            detectDragGestures(
                onDragStart = { offset -> current.clear(); current += NormalizedPoint(offset.x / size.width, offset.y / size.height) },
                onDrag = { change, _ -> change.consume(); current += NormalizedPoint(change.position.x / size.width, change.position.y / size.height) },
                onDragEnd = { if (current.isNotEmpty()) onStroke(MaskStroke(current.toList(), brushSize, isEraser)); current.clear() },
                onDragCancel = { current.clear() },
            )
        },
    ) {
        fun draw(stroke: MaskStroke) {
            val points = stroke.points
            if (points.isEmpty()) return
            val color = if (stroke.isEraser) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color(0x4DFF3158)
            val blend = if (stroke.isEraser) BlendMode.Clear else BlendMode.SrcOver
            val width = stroke.brushSizeNormalized * maxOf(size.width, size.height)
            val stableBlend = if (stroke.isEraser) blend else BlendMode.Src
            if (points.size == 1) drawCircle(color, width / 2f, androidx.compose.ui.geometry.Offset(points[0].x * size.width, points[0].y * size.height), blendMode = stableBlend)
            else points.zipWithNext().forEach { (a, b) -> drawLine(color, androidx.compose.ui.geometry.Offset(a.x * size.width, a.y * size.height), androidx.compose.ui.geometry.Offset(b.x * size.width, b.y * size.height), width, StrokeCap.Round, blendMode = stableBlend) }
        }
        strokes.forEach(::draw)
        if (current.isNotEmpty()) draw(MaskStroke(current.toList(), brushSize, isEraser))
    }
}

private fun centerRect(rect: NormalizedRect): NormalizedRect {
    val halfW = (rect.right - rect.left) / 2f; val halfH = (rect.bottom - rect.top) / 2f
    return NormalizedRect(0.5f - halfW, 0.5f - halfH, 0.5f + halfW, 0.5f + halfH).normalized()
}

private enum class RectDragHandle { None, Move, TopLeft, TopRight, BottomLeft, BottomRight }

private fun Int.toRgbHex(): String = (this and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
private fun Int.toComposeColor() = androidx.compose.ui.graphics.Color(this)
