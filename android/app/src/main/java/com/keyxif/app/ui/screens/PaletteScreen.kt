package com.keyxif.app.ui.screens

import android.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.MaskStroke
import com.keyxif.app.domain.model.NormalizedPoint
import com.keyxif.app.domain.model.NormalizedQuad
import com.keyxif.app.domain.model.PaletteAnalysisMode
import com.keyxif.app.domain.model.PhotoRenderStyle
import com.keyxif.app.domain.model.defaultPaletteAnalysisQuad
import com.keyxif.app.domain.model.toQuad
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
    onAnalysisQuadChange: (NormalizedQuad) -> Unit,
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
    var editingQuad by remember(photo?.id) {
        mutableStateOf(
            result?.analysisQuadNormalized
                ?: result?.analysisRectNormalized?.toQuad()
                ?: defaultPaletteAnalysisQuad(),
        )
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
                    PaletteAnalysisMode.RectSelection -> QuadEditorOverlay(editingQuad) { editingQuad = it }
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
                        OutlinedButton(onClick = { editingQuad = centerQuad(editingQuad) }) { Text("중앙으로") }
                        Button(onClick = { onAnalysisQuadChange(editingQuad); onReanalyze() }) { Text("적용") }
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
                    Text("카드 배경 색상", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        colors.take(settings.paletteColorCount).forEachIndexed { index, color ->
                            FilterChip(
                                selected = renderStyle.customCardBackgroundColor == null && renderStyle.paletteBackgroundColorIndex == index,
                                onClick = {
                                    onRenderStyleChange {
                                        it.copy(
                                            paletteBackgroundColorIndex = index,
                                            customCardBackgroundColor = null,
                                            usePaletteColorForCardBackground = true,
                                        )
                                    }
                                },
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
                if (photo != null) {
                    CustomColorSelector(
                        title = "사용자 지정 카드 배경",
                        selectedColor = renderStyle.customCardBackgroundColor,
                        onColorSelected = { color ->
                            onRenderStyleChange {
                                it.copy(customCardBackgroundColor = color, usePaletteColorForCardBackground = true)
                            }
                        },
                    )
                    Text("텍스트 색상", style = MaterialTheme.typography.titleSmall)
                    if (colors.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        colors.take(settings.paletteColorCount).forEachIndexed { index, color ->
                            FilterChip(
                                selected = renderStyle.customTextColor == null && renderStyle.paletteTextColorIndex == index,
                                onClick = {
                                    onRenderStyleChange {
                                        it.copy(
                                            paletteTextColorIndex = index,
                                            customTextColor = null,
                                            usePaletteColorForText = true,
                                        )
                                    }
                                },
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
                    CustomColorSelector(
                        title = "사용자 지정 텍스트 색상",
                        selectedColor = renderStyle.customTextColor,
                        onColorSelected = { color ->
                            onRenderStyleChange { it.copy(customTextColor = color, usePaletteColorForText = true) }
                        },
                    )
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
                        enabled = photo != null,
                    )
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("선택한 색상을 텍스트에 사용", style = MaterialTheme.typography.titleMedium)
                        Text("빌드 정보의 제목과 값을 같은 색상으로 표시합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = renderStyle.usePaletteColorForText,
                        onCheckedChange = { checked -> onRenderStyleChange { it.copy(usePaletteColorForText = checked) } },
                        enabled = photo != null,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CustomColorSelector(
    title: String,
    selectedColor: Int?,
    onColorSelected: (Int) -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var hexInput by remember(selectedColor) {
        mutableStateOf(selectedColor?.let { "#${it.toRgbHex()}" } ?: "#")
    }
    val parsedColor = parseHexColor(hexInput)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = hexInput,
                onValueChange = { value ->
                    val sanitized = value.trim().uppercase().filterIndexed { index, char ->
                        (index == 0 && char == '#') || char in '0'..'9' || char in 'A'..'F'
                    }
                    hexInput = if (sanitized.startsWith("#")) sanitized.take(7) else "#${sanitized.take(6)}"
                },
                modifier = Modifier.weight(1f),
                label = { Text("HEX 색상 코드") },
                placeholder = { Text("#RRGGBB") },
                singleLine = true,
                isError = hexInput.length > 1 && parsedColor == null,
            )
            Button(onClick = { parsedColor?.let(onColorSelected) }, enabled = parsedColor != null) {
                Text("적용")
            }
        }
        OutlinedButton(onClick = { showColorPicker = true }) {
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background((selectedColor ?: Color.WHITE).toComposeColor())
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text("컬러 피커 열기")
        }
    }

    if (showColorPicker) {
        HsvColorPickerDialog(
            initialColor = selectedColor ?: parsedColor ?: Color.WHITE,
            onDismiss = { showColorPicker = false },
            onConfirm = { color ->
                hexInput = "#${color.toRgbHex()}"
                onColorSelected(color)
                showColorPicker = false
            },
        )
    }
}

@Composable
private fun HsvColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { Color.colorToHSV(initialColor, it) }
    }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    val selected = Color.HSVToColor(floatArrayOf(hue, saturation, value))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("색상 선택", style = MaterialTheme.typography.titleLarge)
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(1.35f)
                        .pointerInput(hue) {
                            detectTapGestures { offset ->
                                saturation = (offset.x / size.width).coerceIn(0f, 1f)
                                value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            }
                        },
                ) {
                    val hueColor = androidx.compose.ui.graphics.Color(Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                    drawRect(brush = Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color.White, hueColor)))
                    drawRect(brush = Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black)))
                    val marker = androidx.compose.ui.geometry.Offset(saturation * size.width, (1f - value) * size.height)
                    drawCircle(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f), 10.dp.toPx(), marker, style = Stroke(3.dp.toPx()))
                    drawCircle(androidx.compose.ui.graphics.Color.White, 8.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
                }
                Canvas(
                    Modifier.fillMaxWidth().height(32.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset -> hue = (offset.x / size.width * 360f).coerceIn(0f, 359.99f) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = (change.position.x / size.width * 360f).coerceIn(0f, 359.99f)
                            }
                        },
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map {
                                androidx.compose.ui.graphics.Color(Color.HSVToColor(floatArrayOf(it % 360f, 1f, 1f)))
                            },
                        ),
                    )
                    val markerX = hue / 360f * size.width
                    drawLine(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(markerX, 0f), androidx.compose.ui.geometry.Offset(markerX, size.height), 4.dp.toPx())
                    drawLine(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.geometry.Offset(markerX, 0f), androidx.compose.ui.geometry.Offset(markerX, size.height), 2.dp.toPx())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(selected.toComposeColor())
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text("#${selected.toRgbHex()}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(onClick = { onConfirm(selected) }) { Text("적용") }
                }
            }
        }
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
private fun QuadEditorOverlay(quad: NormalizedQuad, onChange: (NormalizedQuad) -> Unit) {
    val latestQuad by rememberUpdatedState(quad)
    val pinColor = MaterialTheme.colorScheme.primary
    var dragHandle by remember { mutableStateOf(QuadDragHandle.None) }
    var dragOrigin by remember { mutableStateOf(quad) }
    var dragStart by remember { mutableStateOf(NormalizedPoint(0f, 0f)) }
    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    val current = latestQuad
                    dragOrigin = current
                    dragStart = NormalizedPoint(offset.x / size.width, offset.y / size.height)
                    val corners = listOf(
                        QuadDragHandle.TopLeft to current.topLeft.toOffset(size.width.toFloat(), size.height.toFloat()),
                        QuadDragHandle.TopRight to current.topRight.toOffset(size.width.toFloat(), size.height.toFloat()),
                        QuadDragHandle.BottomRight to current.bottomRight.toOffset(size.width.toFloat(), size.height.toFloat()),
                        QuadDragHandle.BottomLeft to current.bottomLeft.toOffset(size.width.toFloat(), size.height.toFloat()),
                    )
                    dragHandle = corners.minByOrNull { (_, point) -> hypot(offset.x - point.x, offset.y - point.y) }
                        ?.takeIf { (_, point) -> hypot(offset.x - point.x, offset.y - point.y) <= 32.dp.toPx() }
                        ?.first
                        ?: if (isPointInsideQuad(dragStart, current)) QuadDragHandle.Move else QuadDragHandle.None
                },
                onDrag = { change, amount ->
                    change.consume()
                    val point = NormalizedPoint(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        (change.position.y / size.height).coerceIn(0f, 1f),
                    )
                    val next = when (dragHandle) {
                        QuadDragHandle.Move -> {
                            val points = dragOrigin.points()
                            val dx = (point.x - dragStart.x).coerceIn(
                                -points.minOf { it.x },
                                1f - points.maxOf { it.x },
                            )
                            val dy = (point.y - dragStart.y).coerceIn(
                                -points.minOf { it.y },
                                1f - points.maxOf { it.y },
                            )
                            dragOrigin.translated(dx, dy)
                        }
                        QuadDragHandle.TopLeft -> dragOrigin.copy(topLeft = point)
                        QuadDragHandle.TopRight -> dragOrigin.copy(topRight = point)
                        QuadDragHandle.BottomRight -> dragOrigin.copy(bottomRight = point)
                        QuadDragHandle.BottomLeft -> dragOrigin.copy(bottomLeft = point)
                        QuadDragHandle.None -> latestQuad
                    }
                    onChange(next.normalized())
                },
                onDragEnd = { dragHandle = QuadDragHandle.None },
                onDragCancel = { dragHandle = QuadDragHandle.None },
            )
        },
    ) {
        val points = quad.points().map { it.toOffset(size.width, size.height) }
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, androidx.compose.ui.graphics.Color(0x4400C8FF))
        drawPath(path, androidx.compose.ui.graphics.Color.White, style = Stroke(3.dp.toPx()))
        points.forEach { point ->
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

private fun centerQuad(quad: NormalizedQuad): NormalizedQuad {
    val points = quad.points()
    val dx = (0.5f - points.map { it.x }.average().toFloat()).coerceIn(
        -points.minOf { it.x },
        1f - points.maxOf { it.x },
    )
    val dy = (0.5f - points.map { it.y }.average().toFloat()).coerceIn(
        -points.minOf { it.y },
        1f - points.maxOf { it.y },
    )
    return quad.translated(dx, dy)
}

private fun NormalizedQuad.translated(dx: Float, dy: Float): NormalizedQuad = copy(
    topLeft = topLeft.copy(x = topLeft.x + dx, y = topLeft.y + dy),
    topRight = topRight.copy(x = topRight.x + dx, y = topRight.y + dy),
    bottomRight = bottomRight.copy(x = bottomRight.x + dx, y = bottomRight.y + dy),
    bottomLeft = bottomLeft.copy(x = bottomLeft.x + dx, y = bottomLeft.y + dy),
)

private fun NormalizedPoint.toOffset(width: Float, height: Float) =
    androidx.compose.ui.geometry.Offset(x * width, y * height)

private fun isPointInsideQuad(point: NormalizedPoint, quad: NormalizedQuad): Boolean {
    val points = quad.points()
    var inside = false
    var previous = points.last()
    points.forEach { current ->
        if ((current.y > point.y) != (previous.y > point.y) &&
            point.x < (previous.x - current.x) * (point.y - current.y) /
                ((previous.y - current.y).takeIf { kotlin.math.abs(it) > 0.000001f } ?: 0.000001f) + current.x
        ) {
            inside = !inside
        }
        previous = current
    }
    return inside
}

private enum class QuadDragHandle { None, Move, TopLeft, TopRight, BottomRight, BottomLeft }

private fun parseHexColor(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it !in '0'..'9' && it.uppercaseChar() !in 'A'..'F' }) return null
    return (0xFF000000L or hex.toLong(16)).toInt()
}

private fun Int.toRgbHex(): String = (this and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
private fun Int.toComposeColor() = androidx.compose.ui.graphics.Color(this)
