package com.keyxif.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keyxif.app.domain.customtemplate.CanvasTransform
import com.keyxif.app.domain.customtemplate.NormalizedBounds
import com.keyxif.app.domain.customtemplate.ResizeHandle
import com.keyxif.app.domain.customtemplate.ScreenBounds
import com.keyxif.app.domain.customtemplate.cardBounds
import com.keyxif.app.domain.customtemplate.centeredContainPhotoBounds
import com.keyxif.app.domain.customtemplate.elementContainerTransform
import com.keyxif.app.domain.customtemplate.elementBounds
import com.keyxif.app.domain.customtemplate.hitTestCard
import com.keyxif.app.domain.customtemplate.hitTestCardHandle
import com.keyxif.app.domain.customtemplate.hitTestElementHandleInContainer
import com.keyxif.app.domain.customtemplate.hitTestElementInContainers
import com.keyxif.app.domain.customtemplate.moveBounds
import com.keyxif.app.domain.customtemplate.outsideContentAreas
import com.keyxif.app.domain.customtemplate.photoBounds
import com.keyxif.app.domain.customtemplate.resizeFrameAspectRatio
import com.keyxif.app.domain.customtemplate.resizeBounds
import com.keyxif.app.domain.customtemplate.resizePhotoBounds
import com.keyxif.app.domain.model.BuildInfoField
import com.keyxif.app.domain.model.CUSTOM_TEMPLATE_FRAME_CONTAINER_ID
import com.keyxif.app.domain.model.CUSTOM_TEMPLATE_PHOTO_CONTAINER_ID
import com.keyxif.app.domain.model.CardStyle
import com.keyxif.app.domain.model.CanvasCoordinateSpace
import com.keyxif.app.domain.model.CanvasElement
import com.keyxif.app.domain.model.ColorChipShape
import com.keyxif.app.domain.model.CustomTemplateCardStylePreset
import com.keyxif.app.domain.model.CustomTemplateCardSpaceSeverity
import com.keyxif.app.domain.model.CustomTemplateCardSpaceWarning
import com.keyxif.app.domain.model.CustomTemplateEditorState
import com.keyxif.app.domain.model.CustomTemplateEditorTab
import com.keyxif.app.domain.model.CustomTemplateSelection
import com.keyxif.app.domain.model.CustomTemplateSnapGuide
import com.keyxif.app.domain.model.ElementContent
import com.keyxif.app.domain.model.ElementFontWeight
import com.keyxif.app.domain.model.ElementTextAlign
import com.keyxif.app.domain.model.FrameAspectPreset
import com.keyxif.app.domain.model.InternalCard
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.SnapGuideOrientation
import coil.compose.AsyncImage
import com.keyxif.app.ui.CustomTemplateAlignment

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CustomTemplateEditorScreen(
    editorState: CustomTemplateEditorState,
    selectedPhotoUri: Uri?,
    paletteColors: List<Int>,
    buildInfo: KeyboardBuildInfo,
    logoModel: Any?,
    onSelectTab: (CustomTemplateEditorTab) -> Unit,
    onSelectTarget: (CustomTemplateSelection) -> Unit,
    onSelectElement: (String?) -> Unit,
    onSelectCard: (String?) -> Unit,
    onAddTextElement: (BuildInfoField) -> Unit,
    onAddLogoElement: () -> Unit,
    onAddColorChipElement: () -> Unit,
    onAddCard: (CustomTemplateCardStylePreset) -> Unit,
    onUpdateCardBounds: (String, NormalizedBounds) -> Unit,
    onApplyCardStyle: (CustomTemplateCardStylePreset) -> Unit,
    onUpdateSelectedCardStyle: (CardStyle) -> Unit,
    onDuplicateSelected: () -> Unit,
    onBeginInteraction: () -> Unit,
    onUpdateElementBounds: (String, NormalizedBounds) -> Unit,
    onUpdateElementPlacement: (String, NormalizedBounds, CanvasCoordinateSpace, String) -> Unit,
    onUpdatePhotoBounds: (NormalizedBounds) -> Unit,
    onUpdateFrameSize: (Float, Float) -> Unit,
    onUpdateOuterMargins: (Float, Float, Float, Float) -> Unit,
    onFramePreset: (FrameAspectPreset) -> Unit,
    onPhotoAspectRatioResolved: (Float) -> Unit,
    onFinishInteraction: () -> Unit,
    onDeleteSelected: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: (String) -> Unit,
    onNudgeSelected: (Float, Float) -> Unit,
    onAlignSelected: (CustomTemplateAlignment) -> Unit,
    onResetTemplate: () -> Unit,
    onClose: () -> Unit,
) {
    var templateName by remember(editorState.draft.id) { mutableStateOf(editorState.draft.name) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(editorState.draft.name) {
        templateName = editorState.draft.name
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("저장하지 않고 나갈까요?") },
            text = { Text("아직 저장하지 않은 커스텀 템플릿 변경사항이 있습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseConfirm = false
                        onClose()
                    },
                ) { Text("나가기") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) { Text("계속 편집") }
            },
        )
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("템플릿을 초기화할까요?") },
            text = { Text("현재 편집 중인 배치와 요소가 기본 상태로 돌아갑니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onResetTemplate()
                    },
                ) { Text("초기화") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("취소") }
            },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "커스텀 템플릿",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "사진을 기준으로 외부 영역과 요소를 자유롭게 배치합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                editorState.collisionWarning?.let { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = editorState.undoStack.isNotEmpty(),
                    onClick = onUndo,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "되돌리기")
                }
                IconButton(
                    enabled = editorState.redoStack.isNotEmpty(),
                    onClick = onRedo,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "다시 실행")
                }
                FilledTonalButton(onClick = { showResetConfirm = true }) {
                    Text("초기화")
                }
                Button(onClick = {
                    if (editorState.isDirty) showCloseConfirm = true else onClose()
                }) {
                    Text("목록")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = templateName,
                onValueChange = { templateName = it },
                singleLine = true,
                label = { Text("템플릿 이름") },
            )
            Button(onClick = { onSave(templateName) }) {
                Text("저장")
            }
        }

        CustomTemplatePreview(
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val step = if (event.isShiftPressed) 0.025f else 0.006f
                    when (event.key) {
                        Key.Delete, Key.Backspace -> {
                            onDeleteSelected()
                            true
                        }
                        Key.DirectionLeft -> {
                            onNudgeSelected(-step, 0f)
                            true
                        }
                        Key.DirectionRight -> {
                            onNudgeSelected(step, 0f)
                            true
                        }
                        Key.DirectionUp -> {
                            onNudgeSelected(0f, -step)
                            true
                        }
                        Key.DirectionDown -> {
                            onNudgeSelected(0f, step)
                            true
                        }
                        else -> false
                    }
                },
            editorState = editorState,
            selectedPhotoUri = selectedPhotoUri,
            paletteColors = paletteColors,
            buildInfo = buildInfo,
            logoModel = logoModel,
            onSelectTarget = onSelectTarget,
            onSelectElement = onSelectElement,
            onSelectCard = onSelectCard,
            onBeginInteraction = onBeginInteraction,
            onUpdateElementBounds = onUpdateElementBounds,
            onUpdateElementPlacement = onUpdateElementPlacement,
            onUpdateCardBounds = onUpdateCardBounds,
            onUpdatePhotoBounds = onUpdatePhotoBounds,
            onUpdateFrameSize = onUpdateFrameSize,
            onUpdateOuterMargins = onUpdateOuterMargins,
            onPhotoAspectRatioResolved = onPhotoAspectRatioResolved,
            onFinishInteraction = onFinishInteraction,
        )

        if (editorState.activeTab == CustomTemplateEditorTab.Frame) {
            val margins = outerMarginsFromPhoto(editorState.draft.photoPlacement)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "사진을 기준으로 바깥 카드 영역을 붙입니다. 프레임 모서리 핀을 드래그해서 여백을 조절하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "바깥 영역 L ${(margins.left * 100).toInt()} · R ${(margins.right * 100).toInt()} · T ${(margins.top * 100).toInt()} · B ${(margins.bottom * 100).toInt()}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FilledTonalButton(
                        onClick = {
                            onBeginInteraction()
                            onUpdateOuterMargins(0f, 0f, 0f, 0f)
                            onFinishInteraction()
                        },
                    ) {
                        Text("사진 외부 영역 없음")
                    }
                }
            }
        }

        if (editorState.activeTab == CustomTemplateEditorTab.Element) {
            ElementToolPanel(
                selectedElementId = editorState.selectedElementId,
                selectedTarget = editorState.selectedTarget,
                usedFields = editorState.draft.elements
                    .mapNotNull { (it.content as? ElementContent.BuildField)?.field }
                    .toSet(),
                onAddTextElement = onAddTextElement,
                onAddLogoElement = onAddLogoElement,
                onAddColorChipElement = onAddColorChipElement,
                onDuplicateSelected = onDuplicateSelected,
                onDeleteSelected = onDeleteSelected,
            )
        }

        if (editorState.activeTab == CustomTemplateEditorTab.Card) {
            CardToolPanel(
                editorState = editorState,
                selectedCardId = editorState.selectedCardId,
                onAddCard = onAddCard,
                onApplyCardStyle = onApplyCardStyle,
                onUpdateSelectedCardStyle = onUpdateSelectedCardStyle,
                onDeleteSelected = onDeleteSelected,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CustomTemplateEditorTab.entries.forEach { tab ->
                EditorTabChip(
                    modifier = Modifier.weight(1f),
                    tab = tab,
                    selected = tab == editorState.activeTab,
                    onClick = { onSelectTab(tab) },
                )
            }
        }
    }
}

@Composable
private fun SelectedTargetPropertyPanel(
    editorState: CustomTemplateEditorState,
    onAlignSelected: (CustomTemplateAlignment) -> Unit,
) {
    val selection = selectedTargetBounds(editorState)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selection?.label ?: "선택 없음",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selection?.bounds?.let {
                        "X ${it.x.toPercent()} 쨌 Y ${it.y.toPercent()} 쨌 W ${it.width.toPercent()} 쨌 H ${it.height.toPercent()}"
                    } ?: "캔버스에서 대상을 선택하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class SelectedTargetInfo(
    val label: String,
    val bounds: NormalizedBounds,
    val canAlign: Boolean,
)

private data class ElementPlacement(
    val bounds: NormalizedBounds,
    val coordinateSpace: CanvasCoordinateSpace,
    val containerId: String,
)

private fun elementPlacementForScreenBounds(
    rect: ScreenBounds,
    frameTransform: CanvasTransform,
    photo: com.keyxif.app.domain.model.CustomPhotoPlacement,
    cards: List<InternalCard>,
): ElementPlacement {
    val center = Offset(rect.left + rect.width / 2f, rect.top + rect.height / 2f)
    val photoScreen = frameTransform.toScreen(photoBounds(photo))
    val cardTarget = cards
        .filterNot { it.hidden }
        .sortedByDescending { it.zIndex }
        .firstNotNullOfOrNull { card ->
            val cardScreen = CanvasTransform(photoScreen.left, photoScreen.top, photoScreen.width, photoScreen.height)
                .toScreen(cardBounds(card))
            val padding = card.style.padding.coerceIn(0f, 0.3f)
            val paddingX = cardScreen.width * padding
            val paddingY = cardScreen.height * padding
            val container = CanvasTransform(
                left = cardScreen.left + paddingX,
                top = cardScreen.top + paddingY,
                width = (cardScreen.width - paddingX * 2f).coerceAtLeast(1f),
                height = (cardScreen.height - paddingY * 2f).coerceAtLeast(1f),
            )
            val inside = center.x in container.left..(container.left + container.width) &&
                center.y in container.top..(container.top + container.height)
            if (inside) card to container else null
        }
    if (cardTarget != null) {
        return ElementPlacement(
            bounds = boundsFromScreen(rect, cardTarget.second, safePadding = 0.018f),
            coordinateSpace = CanvasCoordinateSpace.InternalCard,
            containerId = cardTarget.first.id,
        )
    }
    val photoInside = center.x in photoScreen.left..photoScreen.right &&
        center.y in photoScreen.top..photoScreen.bottom
    if (photoInside) {
        return ElementPlacement(
            bounds = boundsFromScreen(
                rect,
                CanvasTransform(photoScreen.left, photoScreen.top, photoScreen.width, photoScreen.height),
                safePadding = 0.018f,
            ),
            coordinateSpace = CanvasCoordinateSpace.Photo,
            containerId = CUSTOM_TEMPLATE_PHOTO_CONTAINER_ID,
        )
    }
    return ElementPlacement(
        bounds = boundsFromScreen(rect, frameTransform, safePadding = 0.018f),
        coordinateSpace = CanvasCoordinateSpace.Frame,
        containerId = CUSTOM_TEMPLATE_FRAME_CONTAINER_ID,
    )
}

private fun boundsFromScreen(
    rect: ScreenBounds,
    container: CanvasTransform,
    safePadding: Float,
): NormalizedBounds {
    val safe = safePadding.coerceIn(0f, 0.24f)
    val width = (rect.width / container.width.coerceAtLeast(1f)).coerceIn(0.01f, (1f - safe * 2f).coerceAtLeast(0.01f))
    val height = (rect.height / container.height.coerceAtLeast(1f)).coerceIn(0.01f, (1f - safe * 2f).coerceAtLeast(0.01f))
    return NormalizedBounds(
        x = ((rect.left - container.left) / container.width.coerceAtLeast(1f)).coerceIn(safe, (1f - safe - width).coerceAtLeast(safe)),
        y = ((rect.top - container.top) / container.height.coerceAtLeast(1f)).coerceIn(safe, (1f - safe - height).coerceAtLeast(safe)),
        width = width,
        height = height,
    )
}

private fun squareColorChipBounds(
    bounds: NormalizedBounds,
    container: CanvasTransform,
): NormalizedBounds {
    val side = kotlin.math.max(bounds.width * container.width, bounds.height * container.height)
    val width = (side / container.width.coerceAtLeast(1f)).coerceIn(0.01f, 1f)
    val height = (side / container.height.coerceAtLeast(1f)).coerceIn(0.01f, 1f)
    return NormalizedBounds(
        x = bounds.x.coerceIn(0f, (1f - width).coerceAtLeast(0f)),
        y = bounds.y.coerceIn(0f, (1f - height).coerceAtLeast(0f)),
        width = width,
        height = height,
    )
}

private fun selectedTargetBounds(editorState: CustomTemplateEditorState): SelectedTargetInfo? {
    return when (editorState.selectedTarget) {
        CustomTemplateSelection.Photo -> SelectedTargetInfo(
            label = "?ъ쭊 ?곸뿭",
            bounds = photoBounds(editorState.draft.photoPlacement),
            canAlign = true,
        )
        CustomTemplateSelection.Card -> {
            val card = editorState.selectedCardId
                ?.let { id -> editorState.draft.internalCards.firstOrNull { it.id == id } }
                ?: return null
            SelectedTargetInfo("移대뱶", cardBounds(card), canAlign = true)
        }
        CustomTemplateSelection.Element -> {
            val element = editorState.selectedElementId
                ?.let { id -> editorState.draft.elements.firstOrNull { it.id == id } }
                ?: return null
            SelectedTargetInfo(element.type.name, elementBounds(element), canAlign = true)
        }
        CustomTemplateSelection.Frame -> SelectedTargetInfo(
            label = "프레임",
            bounds = NormalizedBounds(0f, 0f, 1f, 1f),
            canAlign = false,
        )
    }
}

private fun Float.toPercent(): String = "${(this * 100).toInt()}%"

private data class EditorOuterMargins(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
)

private fun outerMarginsFromPhoto(photo: com.keyxif.app.domain.model.CustomPhotoPlacement): EditorOuterMargins {
    val width = photo.width.coerceIn(0.01f, 1f)
    val height = photo.height.coerceIn(0.01f, 1f)
    val x = photo.x.coerceIn(0f, 1f - width)
    val y = photo.y.coerceIn(0f, 1f - height)
    return EditorOuterMargins(
        left = (x / width).coerceIn(0f, 3f),
        right = ((1f - x - width) / width).coerceIn(0f, 3f),
        top = (y / height).coerceIn(0f, 3f),
        bottom = ((1f - y - height) / height).coerceIn(0f, 3f),
    )
}

@Composable
private fun CustomTemplatePreview(
    modifier: Modifier = Modifier,
    editorState: CustomTemplateEditorState,
    selectedPhotoUri: Uri?,
    paletteColors: List<Int>,
    buildInfo: KeyboardBuildInfo,
    logoModel: Any?,
    onSelectTarget: (CustomTemplateSelection) -> Unit,
    onSelectElement: (String?) -> Unit,
    onSelectCard: (String?) -> Unit,
    onBeginInteraction: () -> Unit,
    onUpdateElementBounds: (String, NormalizedBounds) -> Unit,
    onUpdateElementPlacement: (String, NormalizedBounds, CanvasCoordinateSpace, String) -> Unit,
    onUpdateCardBounds: (String, NormalizedBounds) -> Unit,
    onUpdatePhotoBounds: (NormalizedBounds) -> Unit,
    onUpdateFrameSize: (Float, Float) -> Unit,
    onUpdateOuterMargins: (Float, Float, Float, Float) -> Unit,
    onPhotoAspectRatioResolved: (Float) -> Unit,
    onFinishInteraction: () -> Unit,
) {
    val frame = editorState.draft.frame
    val photo = editorState.draft.photoPlacement
    val elements = editorState.draft.elements.sortedBy { it.zIndex }
    val cards = editorState.draft.internalCards.sortedBy { it.zIndex }
    val frameColor = frame.fill.color.toComposeColor(MaterialTheme.colorScheme.surfaceContainerLow)
    val photoColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val guideColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.primary
    val elementColor = MaterialTheme.colorScheme.secondaryContainer
    val elementStrokeColor = MaterialTheme.colorScheme.onSecondaryContainer
    val latestEditorState by rememberUpdatedState(editorState)
    var workspaceScale by remember { mutableStateOf(1f) }
    var workspaceOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .pointerInput(Unit) {
                    handleWorkspaceViewportTransform { pan, zoom ->
                        workspaceScale = (workspaceScale * zoom).coerceIn(0.75f, 4f)
                        workspaceOffset = Offset(
                            x = (workspaceOffset.x + pan.x).coerceIn(-1200f, 1200f),
                            y = (workspaceOffset.y + pan.y).coerceIn(-1200f, 1200f),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val maxStageWidth = maxWidth
            val maxStageHeight = maxHeight
            val safePhotoWidth = photo.width.coerceAtLeast(0.04f)
            val safePhotoHeight = photo.height.coerceAtLeast(0.04f)
            val safePhotoAspect = photo.aspectRatio.takeIf { it > 0f } ?: 1f
            val visualPhotoWidth = kotlin.math.min(
                maxStageWidth.value * 0.82f,
                maxStageHeight.value * 0.72f * safePhotoAspect,
            ).coerceAtLeast(180f)
            val visualPhotoHeight = (visualPhotoWidth / safePhotoAspect).coerceAtLeast(120f)
            val frameWidth = (visualPhotoWidth / safePhotoWidth).dp
            val frameHeight = (visualPhotoHeight / safePhotoHeight).dp
            val frameHandleGutter = 22.dp
            val frameHandleGutterPx = with(density) { frameHandleGutter.toPx() }
            val frameWidthPx = with(density) { frameWidth.toPx() }
            val frameHeightPx = with(density) { frameHeight.toPx() }
            Box(
                modifier = Modifier
                    .width(frameWidth + frameHandleGutter * 2)
                    .height(frameHeight + frameHandleGutter * 2)
                    .pointerInput(Unit) {
                        handleFrameOuterPointerInput(
                            editorStateProvider = { latestEditorState },
                            frameOffsetX = frameHandleGutterPx,
                            frameOffsetY = frameHandleGutterPx,
                            frameWidth = frameWidthPx,
                            frameHeight = frameHeightPx,
                            onSelectTarget = onSelectTarget,
                            onSelectElement = onSelectElement,
                            onSelectCard = onSelectCard,
                            onBeginInteraction = onBeginInteraction,
                            onUpdateOuterMargins = onUpdateOuterMargins,
                            onFinishInteraction = onFinishInteraction,
                        )
                    }
                    .graphicsLayer {
                        scaleX = workspaceScale
                        scaleY = workspaceScale
                        translationX = workspaceOffset.x
                        translationY = workspaceOffset.y
                    },
            ) {
            Surface(
                modifier = Modifier
                    .offset(frameHandleGutter, frameHandleGutter)
                    .width(frameWidth)
                    .height(frameHeight),
                shape = RoundedCornerShape(8.dp),
                color = frameColor,
                shadowElevation = 2.dp,
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(frameColor),
                ) {
                val photoX = maxWidth * photo.x
                val photoY = maxHeight * photo.y
                val photoWidth = maxWidth * photo.width
                val photoHeight = maxHeight * photo.height

                Canvas(modifier = Modifier.fillMaxSize()) {
                    outsideContentAreas(photo).forEach { area ->
                        val bounds = CanvasTransform(0f, 0f, size.width, size.height).toScreen(area)
                        drawRoundRect(
                            color = guideColor.copy(alpha = 0.06f),
                            topLeft = Offset(bounds.left, bounds.top),
                            size = Size(bounds.width, bounds.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    }
                    val photoRect = androidx.compose.ui.geometry.Rect(
                        left = size.width * photo.x,
                        top = size.height * photo.y,
                        right = size.width * (photo.x + photo.width),
                        bottom = size.height * (photo.y + photo.height),
                    )
                    drawRoundRect(
                        color = photoColor,
                        topLeft = Offset(photoRect.left, photoRect.top),
                        size = Size(photoRect.width, photoRect.height),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    )
                }

                if (selectedPhotoUri != null) {
                    AsyncImage(
                        model = selectedPhotoUri,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = photoX, y = photoY)
                            .width(photoWidth)
                            .height(photoHeight)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit,
                        onSuccess = { success ->
                            val size = success.painter.intrinsicSize
                            if (size.width > 0f && size.height > 0f) {
                                onPhotoAspectRatioResolved(size.width / size.height)
                            }
                        },
                    )
                }

                cards.forEach { card ->
                    if (!card.hidden) {
                        CardOverlay(
                            card = card,
                            elements = elements.filter {
                                it.coordinateSpace == CanvasCoordinateSpace.InternalCard &&
                                    it.containerId == card.id
                            },
                            photo = photo,
                            buildInfo = buildInfo,
                            logoModel = logoModel,
                            paletteColors = paletteColors,
                            warning = editorState.cardSpaceWarnings.firstOrNull { it.cardId == card.id },
                        )
                    }
                }

                elements.filterNot { it.coordinateSpace == CanvasCoordinateSpace.InternalCard }.forEach { element ->
                    if (!element.hidden) {
                        ElementOverlay(
                            element = element,
                            photo = photo,
                            cards = cards,
                            buildInfo = buildInfo,
                            logoModel = logoModel,
                            paletteColors = paletteColors,
                        )
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            handleEditorPointerInput(
                                editorStateProvider = { latestEditorState },
                                onSelectTarget = onSelectTarget,
                                onSelectElement = onSelectElement,
                                onSelectCard = onSelectCard,
                                onBeginInteraction = onBeginInteraction,
                                onUpdateElementBounds = onUpdateElementBounds,
                                onUpdateElementPlacement = onUpdateElementPlacement,
                                onUpdateCardBounds = onUpdateCardBounds,
                                onUpdatePhotoBounds = onUpdatePhotoBounds,
                                onUpdateFrameSize = onUpdateFrameSize,
                                onUpdateOuterMargins = onUpdateOuterMargins,
                                onFinishInteraction = onFinishInteraction,
                            )
                        },
                ) {
                    val transform = CanvasTransform(0f, 0f, size.width, size.height)
                    val photoRect = transform.toScreen(photoBounds(photo))
                    val photoSelected = editorState.selectedTarget == CustomTemplateSelection.Photo
                    val frameSelected = editorState.selectedTarget == CustomTemplateSelection.Frame ||
                        editorState.activeTab == CustomTemplateEditorTab.Frame

                    drawRoundRect(
                        color = if (photoSelected) selectedColor else guideColor,
                        topLeft = Offset(photoRect.left, photoRect.top),
                        size = Size(photoRect.width, photoRect.height),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(
                            width = if (photoSelected) 1.4.dp.toPx() else 0.9.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx())),
                        ),
                        alpha = 0.78f,
                    )
                    if (photoSelected) drawResizeHandles(transform.toScreen(photoBounds(photo)), selectedColor)

                    cards.forEach { card ->
                        if (!card.hidden) {
                            val photoTransform = CanvasTransform(photoRect.left, photoRect.top, photoRect.width, photoRect.height)
                            val bounds = photoTransform.toScreen(cardBounds(card))
                            val selected = editorState.selectedTarget == CustomTemplateSelection.Card &&
                                editorState.selectedCardId == card.id
                            val warning = editorState.cardSpaceWarnings.firstOrNull { it.cardId == card.id }
                            val warningColor = warning?.let {
                                if (it.severity == CustomTemplateCardSpaceSeverity.Blocking) Color(0xFFE5484D) else Color(0xFFF59E0B)
                            }
                            drawRoundRect(
                                color = warningColor ?: if (selected) selectedColor else elementStrokeColor.copy(alpha = 0.58f),
                                topLeft = Offset(bounds.left, bounds.top),
                                size = Size(bounds.width, bounds.height),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                                style = Stroke(width = if (warning != null || selected) 1.4.dp.toPx() else 0.8.dp.toPx()),
                            )
                            if (selected) drawResizeHandles(bounds, selectedColor)
                        }
                    }

                    elements.forEach { element ->
                        if (!element.hidden) {
                            val elementTransform = elementContainerTransform(element, transform, photo, cards)
                            val bounds = elementTransform.toScreen(elementBounds(element))
                            val selected = editorState.selectedTarget == CustomTemplateSelection.Element &&
                                editorState.selectedElementId == element.id
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = Offset(bounds.left, bounds.top),
                                size = Size(bounds.width, bounds.height),
                                cornerRadius = CornerRadius(
                                    element.style.cornerRadius * size.minDimension,
                                    element.style.cornerRadius * size.minDimension,
                                ),
                            )
                            drawRoundRect(
                                color = if (selected) selectedColor else elementStrokeColor.copy(alpha = 0.62f),
                                topLeft = Offset(bounds.left, bounds.top),
                                size = Size(bounds.width, bounds.height),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                style = Stroke(width = if (selected) 1.4.dp.toPx() else 0.8.dp.toPx()),
                            )
                            if (selected) drawResizeHandles(bounds, selectedColor)
                        }
                    }

                    editorState.snapGuides.forEach { guide ->
                        drawSnapGuide(
                            guide = guide,
                            frameTransform = transform,
                            photo = photo,
                            cards = cards,
                            color = selectedColor,
                        )
                    }

                }
                }
            }
            if (editorState.selectedTarget == CustomTemplateSelection.Frame ||
                editorState.activeTab == CustomTemplateEditorTab.Frame
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val handleRadius = 5.dp.toPx()
                    val left = frameHandleGutterPx
                    val top = frameHandleGutterPx
                    val right = frameHandleGutterPx + frameWidthPx
                    val bottom = frameHandleGutterPx + frameHeightPx
                    drawRoundRect(
                        color = selectedColor,
                        topLeft = Offset(left, top),
                        size = Size(frameWidthPx, frameHeightPx),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(width = 1.1.dp.toPx()),
                    )
                    listOf(
                        Offset(left, top),
                        Offset(right, top),
                        Offset(right, bottom),
                        Offset(left, bottom),
                    ).forEach { center ->
                        drawCircle(color = selectedColor, radius = handleRadius, center = center)
                        drawCircle(color = Color.White, radius = handleRadius * 0.42f, center = center)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ElementToolPanel(
    selectedElementId: String?,
    selectedTarget: CustomTemplateSelection,
    usedFields: Set<BuildInfoField>,
    onAddTextElement: (BuildInfoField) -> Unit,
    onAddLogoElement: () -> Unit,
    onAddColorChipElement: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (selectedTarget == CustomTemplateSelection.Photo) "?ъ쭊 ?덉뿉 異붽?" else "?꾨젅?꾩뿉 異붽?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onAddLogoElement) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("로고")
            }
            Button(onClick = onAddColorChipElement) {
                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("색상칩")
            }
            AssistChip(
                enabled = selectedElementId != null,
                onClick = onDuplicateSelected,
                label = { Text("복제") },
            )
            AssistChip(
                enabled = selectedElementId != null,
                onClick = onDeleteSelected,
                label = { Text("선택 삭제") },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BuildInfoField.entries.forEach { field ->
                AssistChip(
                    enabled = field !in usedFields,
                    onClick = { onAddTextElement(field) },
                    label = { Text(field.displayLabel()) },
                )
            }
        }
    }
}

@Composable
private fun CardToolPanel(
    editorState: CustomTemplateEditorState,
    selectedCardId: String?,
    onAddCard: (CustomTemplateCardStylePreset) -> Unit,
    onApplyCardStyle: (CustomTemplateCardStylePreset) -> Unit,
    onUpdateSelectedCardStyle: (CardStyle) -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "사진 안에 카드 추가",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CustomTemplateCardStylePreset.entries.forEach { preset ->
                Button(onClick = { onAddCard(preset) }) {
                    Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("${preset.label} 추가")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "선택 카드 스타일",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            CustomTemplateCardStylePreset.entries.forEach { preset ->
                AssistChip(
                    enabled = selectedCardId != null,
                    onClick = { onApplyCardStyle(preset) },
                    label = { Text(preset.label) },
                )
            }
        }
        FilledTonalButton(
            enabled = selectedCardId != null,
            onClick = onDeleteSelected,
        ) {
            Text("선택 카드 삭제")
        }
        val selectedCard = selectedCardId?.let { id ->
            editorState.draft.internalCards.firstOrNull { it.id == id }
        }
        if (selectedCard != null) {
            var colorText by remember(selectedCard.id, selectedCard.style.backgroundColor) {
                mutableStateOf(selectedCard.style.backgroundColor)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("?좏깮 移대뱶 吏곸젒 議곗젙", style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(selectedCard.style.backgroundColor.toComposeColor(MaterialTheme.colorScheme.surface)),
                        )
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = colorText,
                            onValueChange = {
                                colorText = it
                                if (it.matches(Regex("^#?[0-9a-fA-F]{6}$"))) {
                                    onUpdateSelectedCardStyle(
                                        selectedCard.style.copy(backgroundColor = it.ensureHexColor()),
                                    )
                                }
                            },
                            singleLine = true,
                            label = { Text("카드 배경색") },
                        )
                    }
                    StyleSlider(
                        label = "투명도",
                        value = selectedCard.style.opacity,
                        range = 0.05f..1f,
                    ) {
                        onUpdateSelectedCardStyle(selectedCard.style.copy(opacity = it))
                    }
                    StyleSlider(
                        label = "Radius",
                        value = selectedCard.style.radius,
                        range = 0f..0.18f,
                    ) {
                        onUpdateSelectedCardStyle(selectedCard.style.copy(radius = it))
                    }
                    StyleSlider(
                        label = "?대? ?щ갚",
                        value = selectedCard.style.padding,
                        range = 0f..0.24f,
                    ) {
                        onUpdateSelectedCardStyle(selectedCard.style.copy(padding = it))
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$label ${(value * 100).toInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.CardOverlay(
    card: InternalCard,
    elements: List<CanvasElement>,
    photo: com.keyxif.app.domain.model.CustomPhotoPlacement,
    buildInfo: KeyboardBuildInfo,
    logoModel: Any?,
    paletteColors: List<Int>,
    warning: CustomTemplateCardSpaceWarning?,
) {
    val left = maxWidth * photo.x + maxWidth * photo.width * card.x
    val top = maxHeight * photo.y + maxHeight * photo.height * card.y
    val width = maxWidth * photo.width * card.width
    val height = maxHeight * photo.height * card.height
    val radius = (card.style.radius * 420).dp
    val background = card.style.backgroundColor.toComposeColor(MaterialTheme.colorScheme.surface)
    val shape = RoundedCornerShape(radius)
    BoxWithConstraints(
        modifier = Modifier
            .offset(left, top)
            .width(width)
            .height(height)
            .clip(shape)
            .background(background.copy(alpha = card.style.opacity))
            .then(
                if (card.style.borderEnabled) {
                    Modifier.border(
                        width = (card.style.borderWidth * 420).dp.coerceAtLeast(0.5.dp),
                        color = card.style.borderColor.toComposeColor(MaterialTheme.colorScheme.outline),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        val paddingX = maxWidth * card.style.padding.coerceIn(0f, 0.3f)
        val paddingY = maxHeight * card.style.padding.coerceIn(0f, 0.3f)
        elements.sortedBy { it.zIndex }.forEach { element ->
            if (!element.hidden) {
                ElementOverlayInBox(
                    element = element,
                    containerLeft = paddingX,
                    containerTop = paddingY,
                    containerWidth = maxWidth - paddingX * 2f,
                    containerHeight = maxHeight - paddingY * 2f,
                    buildInfo = buildInfo,
                    logoModel = logoModel,
                    paletteColors = paletteColors,
                )
            }
        }
        if (warning != null) {
            val badgeColor = if (warning.severity == CustomTemplateCardSpaceSeverity.Blocking) {
                Color(0xFFE5484D)
            } else {
                Color(0xFFF59E0B)
            }
            Text(
                text = "공간 부족",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(badgeColor.copy(alpha = 0.88f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.ElementOverlay(
    element: CanvasElement,
    photo: com.keyxif.app.domain.model.CustomPhotoPlacement,
    cards: List<InternalCard>,
    buildInfo: KeyboardBuildInfo,
    logoModel: Any?,
    paletteColors: List<Int>,
) {
    val card = cards.firstOrNull { it.id == element.containerId }
    val left: androidx.compose.ui.unit.Dp
    val top: androidx.compose.ui.unit.Dp
    val width: androidx.compose.ui.unit.Dp
    val height: androidx.compose.ui.unit.Dp
    if (element.coordinateSpace == CanvasCoordinateSpace.InternalCard && card != null) {
        val cardLeft = maxWidth * photo.x + maxWidth * photo.width * card.x
        val cardTop = maxHeight * photo.y + maxHeight * photo.height * card.y
        val cardWidth = maxWidth * photo.width * card.width
        val cardHeight = maxHeight * photo.height * card.height
        val paddingX = cardWidth * card.style.padding.coerceIn(0f, 0.3f)
        val paddingY = cardHeight * card.style.padding.coerceIn(0f, 0.3f)
        left = cardLeft + paddingX + (cardWidth - paddingX * 2f) * element.x
        top = cardTop + paddingY + (cardHeight - paddingY * 2f) * element.y
        width = (cardWidth - paddingX * 2f) * element.width
        height = (cardHeight - paddingY * 2f) * element.height
    } else if (element.coordinateSpace == CanvasCoordinateSpace.Photo) {
        left = maxWidth * photo.x + maxWidth * photo.width * element.x
        top = maxHeight * photo.y + maxHeight * photo.height * element.y
        width = maxWidth * photo.width * element.width
        height = maxHeight * photo.height * element.height
    } else {
        left = maxWidth * element.x
        top = maxHeight * element.y
        width = maxWidth * element.width
        height = maxHeight * element.height
    }
    ElementOverlayInBox(
        element = element,
        containerLeft = left,
        containerTop = top,
        containerWidth = width,
        containerHeight = height,
        buildInfo = buildInfo,
        logoModel = logoModel,
        paletteColors = paletteColors,
    )
}

@Composable
private fun ElementOverlayInBox(
    element: CanvasElement,
    containerLeft: androidx.compose.ui.unit.Dp,
    containerTop: androidx.compose.ui.unit.Dp,
    containerWidth: androidx.compose.ui.unit.Dp,
    containerHeight: androidx.compose.ui.unit.Dp,
    buildInfo: KeyboardBuildInfo,
    logoModel: Any?,
    paletteColors: List<Int>,
) {
    Box(
        modifier = Modifier
            .offset(containerLeft, containerTop)
            .width(containerWidth)
            .height(containerHeight)
            .alpha(element.style.opacity),
        contentAlignment = element.style.textAlign.toAlignment(),
    ) {
        when (element.content) {
            is ElementContent.BuildField,
            is ElementContent.StaticText -> {
                Text(
                    text = element.displayText(buildInfo),
                    modifier = Modifier.fillMaxWidth(),
                    color = element.style.textColor.toComposeColor(MaterialTheme.colorScheme.onSurface),
                    fontSize = (
                        kotlin.math.min(containerWidth.value, containerHeight.value) *
                            element.style.fontSize.coerceIn(0.01f, 0.4f) *
                            2.2f
                        ).coerceAtLeast(8.5f).sp,
                    fontWeight = element.style.fontWeight.toComposeWeight(),
                    textAlign = element.style.textAlign.toComposeTextAlign(),
                    lineHeight = (
                        kotlin.math.min(containerWidth.value, containerHeight.value) *
                            element.style.fontSize.coerceIn(0.01f, 0.4f) *
                            2.2f *
                            element.style.lineHeight
                        ).coerceAtLeast(8.5f).sp,
                    maxLines = element.style.maxLines,
                )
            }
            ElementContent.LogoImage -> {
                if (logoModel != null) {
                    AsyncImage(
                        model = logoModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = "LOGO",
                        color = element.style.textColor.toComposeColor(MaterialTheme.colorScheme.onSurface),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            is ElementContent.ColorChip -> {
                val shape = when (element.style.chipShape) {
                    ColorChipShape.Circle -> CircleShape
                    ColorChipShape.Square -> RoundedCornerShape(0.dp)
                    ColorChipShape.Rounded -> RoundedCornerShape(999.dp)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(kotlin.math.min(containerWidth.value, containerHeight.value).dp)
                        .clip(shape)
                        .background(
                            paletteColors.firstOrNull()?.toComposeColor()
                                ?: element.content.color.toComposeColor(MaterialTheme.colorScheme.secondaryContainer),
                        ),
                )
            }
        }
    }
}

@Composable
private fun EditorTabChip(
    modifier: Modifier = Modifier,
    tab: CustomTemplateEditorTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon = when (tab) {
        CustomTemplateEditorTab.Frame -> Icons.Default.CropSquare
        CustomTemplateEditorTab.Element -> Icons.Default.TextFields
        CustomTemplateEditorTab.Card -> Icons.Default.Dashboard
    }
    AssistChip(
        modifier = modifier,
        onClick = onClick,
        label = { Text(tab.label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            labelColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            leadingIconContentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    )
}

private fun String.toComposeColor(fallback: Color): Color {
    return runCatching {
        val value = removePrefix("#").toLong(16)
        Color(
            red = ((value shr 16) and 0xFF) / 255f,
            green = ((value shr 8) and 0xFF) / 255f,
            blue = (value and 0xFF) / 255f,
            alpha = 1f,
        )
    }.getOrDefault(fallback)
}

private fun String.ensureHexColor(): String {
    val raw = trim().removePrefix("#")
    return if (raw.matches(Regex("^[0-9a-fA-F]{6}$"))) "#${raw.uppercase()}" else "#FFFFFF"
}

private fun BuildInfoField.displayLabel(): String {
    return when (this) {
        BuildInfoField.Board -> "보드"
        BuildInfoField.Switch -> "스위치"
        BuildInfoField.Plate -> "보강판"
        BuildInfoField.Mount -> "마운트"
        BuildInfoField.Nickname -> "닉네임"
    }
}

private fun CanvasElement.displayText(buildInfo: KeyboardBuildInfo): String {
    val raw = when (val data = content) {
        is ElementContent.StaticText -> data.text
        is ElementContent.BuildField -> buildInfo.valueFor(data.field)
        ElementContent.LogoImage -> ""
        is ElementContent.ColorChip -> ""
    }
    return if (style.uppercase) raw.uppercase() else raw
}

private fun KeyboardBuildInfo.valueFor(field: BuildInfoField): String {
    return when (field) {
        BuildInfoField.Board -> housing.ifBlank { "BOARD" }
        BuildInfoField.Switch -> switchName.ifBlank { "SWITCH" }
        BuildInfoField.Plate -> plate.ifBlank { "PLATE" }
        BuildInfoField.Mount -> mount.ifBlank { "MOUNT" }
        BuildInfoField.Nickname -> nickname.ifBlank { "NICKNAME" }
    }
}

private fun ElementFontWeight.toComposeWeight(): FontWeight {
    return when (this) {
        ElementFontWeight.Regular -> FontWeight.Normal
        ElementFontWeight.Medium -> FontWeight.Medium
        ElementFontWeight.Bold -> FontWeight.Bold
    }
}

private fun ElementTextAlign.toComposeTextAlign(): TextAlign {
    return when (this) {
        ElementTextAlign.Start -> TextAlign.Start
        ElementTextAlign.Center -> TextAlign.Center
        ElementTextAlign.End -> TextAlign.End
    }
}

private fun ElementTextAlign.toAlignment(): Alignment {
    return when (this) {
        ElementTextAlign.Start -> Alignment.CenterStart
        ElementTextAlign.Center -> Alignment.Center
        ElementTextAlign.End -> Alignment.CenterEnd
    }
}

private fun DrawScope.drawResizeHandles(
    bounds: com.keyxif.app.domain.customtemplate.ScreenBounds,
    color: Color,
    insetToCanvas: Boolean = false,
) {
    val handleRadius = 4.8.dp.toPx()
    listOf(
        Offset(bounds.left, bounds.top),
        Offset(bounds.right, bounds.top),
        Offset(bounds.right, bounds.bottom),
        Offset(bounds.left, bounds.bottom),
    ).forEach { center ->
        val visibleCenter = if (insetToCanvas) {
            Offset(
                x = center.x.coerceIn(handleRadius * 1.8f, size.width - handleRadius * 1.8f),
                y = center.y.coerceIn(handleRadius * 1.8f, size.height - handleRadius * 1.8f),
            )
        } else {
            center
        }
        drawCircle(color = color, radius = handleRadius, center = visibleCenter)
        drawCircle(color = Color.White, radius = handleRadius * 0.45f, center = visibleCenter)
    }
}

private fun Int.toComposeColor(): Color {
    return Color(
        red = ((this shr 16) and 0xFF) / 255f,
        green = ((this shr 8) and 0xFF) / 255f,
        blue = (this and 0xFF) / 255f,
        alpha = ((this ushr 24) and 0xFF).takeIf { it > 0 }?.let { it / 255f } ?: 1f,
    )
}

private fun DrawScope.drawSnapGuide(
    guide: CustomTemplateSnapGuide,
    frameTransform: CanvasTransform,
    photo: com.keyxif.app.domain.model.CustomPhotoPlacement,
    cards: List<InternalCard>,
    color: Color,
) {
    val container = guideContainerTransform(guide, frameTransform, photo, cards)
    val strokeWidth = 1.4.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
    if (guide.orientation == SnapGuideOrientation.Vertical) {
        val x = container.left + container.width * guide.position
        drawLine(
            color = color.copy(alpha = 0.72f),
            start = Offset(x, container.top),
            end = Offset(x, container.top + container.height),
            strokeWidth = strokeWidth,
            pathEffect = dash,
        )
    } else {
        val y = container.top + container.height * guide.position
        drawLine(
            color = color.copy(alpha = 0.72f),
            start = Offset(container.left, y),
            end = Offset(container.left + container.width, y),
            strokeWidth = strokeWidth,
            pathEffect = dash,
        )
    }
}

private fun guideContainerTransform(
    guide: CustomTemplateSnapGuide,
    frameTransform: CanvasTransform,
    photo: com.keyxif.app.domain.model.CustomPhotoPlacement,
    cards: List<InternalCard>,
): CanvasTransform {
    return when (guide.coordinateSpace) {
        CanvasCoordinateSpace.Frame -> frameTransform
        CanvasCoordinateSpace.Photo -> {
            val photoScreen = frameTransform.toScreen(photoBounds(photo))
            CanvasTransform(photoScreen.left, photoScreen.top, photoScreen.width, photoScreen.height)
        }
        CanvasCoordinateSpace.InternalCard -> {
            val photoScreen = frameTransform.toScreen(photoBounds(photo))
            val photoTransform = CanvasTransform(photoScreen.left, photoScreen.top, photoScreen.width, photoScreen.height)
            val card = cards.firstOrNull { it.id == guide.containerId }
            if (card == null) {
                photoTransform
            } else {
                val cardScreen = photoTransform.toScreen(cardBounds(card))
                val paddingX = cardScreen.width * card.style.padding.coerceIn(0f, 0.3f)
                val paddingY = cardScreen.height * card.style.padding.coerceIn(0f, 0.3f)
                CanvasTransform(
                    left = cardScreen.left + paddingX,
                    top = cardScreen.top + paddingY,
                    width = (cardScreen.width - paddingX * 2f).coerceAtLeast(1f),
                    height = (cardScreen.height - paddingY * 2f).coerceAtLeast(1f),
                )
            }
        }
    }
}

private suspend fun PointerInputScope.handleWorkspaceViewportTransform(
    onTransform: (pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        var lastCentroid: Offset? = null
        var lastDistance: Float? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size < 2) {
                lastCentroid = null
                lastDistance = null
                continue
            }
            val points = pressed.map { it.position }
            val centroid = points.reduce { sum, point -> sum + point } / points.size.toFloat()
            val distance = points
                .map { (it - centroid).getDistance() }
                .average()
                .toFloat()
                .coerceAtLeast(1f)
            val previousCentroid = lastCentroid
            val previousDistance = lastDistance
            if (previousCentroid != null && previousDistance != null) {
                onTransform(centroid - previousCentroid, distance / previousDistance.coerceAtLeast(1f))
                pressed.forEach { it.consume() }
            }
            lastCentroid = centroid
            lastDistance = distance
        }
    }
}

private suspend fun PointerInputScope.handleFrameOuterPointerInput(
    editorStateProvider: () -> CustomTemplateEditorState,
    frameOffsetX: Float,
    frameOffsetY: Float,
    frameWidth: Float,
    frameHeight: Float,
    onSelectTarget: (CustomTemplateSelection) -> Unit,
    onSelectElement: (String?) -> Unit,
    onSelectCard: (String?) -> Unit,
    onBeginInteraction: () -> Unit,
    onUpdateOuterMargins: (Float, Float, Float, Float) -> Unit,
    onFinishInteraction: () -> Unit,
) {
    awaitEachGesture {
        val firstDown = awaitPointerEvent(PointerEventPass.Initial)
            .changes
            .firstOrNull { it.pressed }
            ?: return@awaitEachGesture
        val editorState = editorStateProvider()
        val frameActive = editorState.selectedTarget == CustomTemplateSelection.Frame ||
            editorState.activeTab == CustomTemplateEditorTab.Frame
        if (!frameActive) return@awaitEachGesture
        val handleRadius = 22.dp.toPx()
        val handle = hitTestFrameHandleInOuter(
            screenX = firstDown.position.x,
            screenY = firstDown.position.y,
            frameOffsetX = frameOffsetX,
            frameOffsetY = frameOffsetY,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            handleRadius = handleRadius,
        ) ?: return@awaitEachGesture

        firstDown.consume()
        onSelectTarget(CustomTemplateSelection.Frame)
        onSelectElement(null)
        onSelectCard(null)
        onBeginInteraction()
        var outerMargins = outerMarginsFromPhoto(editorState.draft.photoPlacement)
        val photo = editorState.draft.photoPlacement
        val photoWidthPx = (frameWidth * photo.width).coerceAtLeast(1f)
        val photoHeightPx = (frameHeight * photo.height).coerceAtLeast(1f)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == firstDown.id } ?: break
            if (change.changedToUp()) break
            if (event.changes.count { it.pressed } >= 2) continue
            val delta = change.positionChange()
            if (delta.x != 0f || delta.y != 0f) {
                val dx = delta.x / photoWidthPx
                val dy = delta.y / photoHeightPx
                outerMargins = outerMargins.copy(
                    left = if (handle == ResizeHandle.TopLeft || handle == ResizeHandle.BottomLeft) {
                        (outerMargins.left - dx).coerceIn(0f, 3f)
                    } else {
                        outerMargins.left
                    },
                    right = if (handle == ResizeHandle.TopRight || handle == ResizeHandle.BottomRight) {
                        (outerMargins.right + dx).coerceIn(0f, 3f)
                    } else {
                        outerMargins.right
                    },
                    top = if (handle == ResizeHandle.TopLeft || handle == ResizeHandle.TopRight) {
                        (outerMargins.top - dy).coerceIn(0f, 3f)
                    } else {
                        outerMargins.top
                    },
                    bottom = if (handle == ResizeHandle.BottomLeft || handle == ResizeHandle.BottomRight) {
                        (outerMargins.bottom + dy).coerceIn(0f, 3f)
                    } else {
                        outerMargins.bottom
                    },
                )
                onUpdateOuterMargins(
                    outerMargins.left,
                    outerMargins.right,
                    outerMargins.top,
                    outerMargins.bottom,
                )
                change.consume()
            }
        }
        onFinishInteraction()
    }
}

private suspend fun PointerInputScope.handleEditorPointerInput(
    editorStateProvider: () -> CustomTemplateEditorState,
    onSelectTarget: (CustomTemplateSelection) -> Unit,
    onSelectElement: (String?) -> Unit,
    onSelectCard: (String?) -> Unit,
    onBeginInteraction: () -> Unit,
    onUpdateElementBounds: (String, NormalizedBounds) -> Unit,
    onUpdateElementPlacement: (String, NormalizedBounds, CanvasCoordinateSpace, String) -> Unit,
    onUpdateCardBounds: (String, NormalizedBounds) -> Unit,
    onUpdatePhotoBounds: (NormalizedBounds) -> Unit,
    onUpdateFrameSize: (Float, Float) -> Unit,
    onUpdateOuterMargins: (Float, Float, Float, Float) -> Unit,
    onFinishInteraction: () -> Unit,
) {
    awaitEachGesture {
        val firstDown = awaitPointerEvent(PointerEventPass.Main)
            .changes
            .firstOrNull { it.pressed }
            ?: return@awaitEachGesture
        val editorState = editorStateProvider()
        val transform = CanvasTransform(0f, 0f, size.width.toFloat(), size.height.toFloat())
        val selectedElement = editorState.selectedElementId
            ?.let { id -> editorState.draft.elements.firstOrNull { it.id == id } }
        val selectedCard = editorState.selectedCardId
            ?.let { id -> editorState.draft.internalCards.firstOrNull { it.id == id } }
        val handleRadius = 18.dp.toPx()
        val selectedPhoto = editorState.draft.photoPlacement
        val selectedFrame = editorState.selectedTarget == CustomTemplateSelection.Frame ||
            editorState.activeTab == CustomTemplateEditorTab.Frame
        val selectedPhotoActive = editorState.selectedTarget == CustomTemplateSelection.Photo
        val selectedCardActive = editorState.selectedTarget == CustomTemplateSelection.Card
        val frameHandle = if (selectedFrame) {
            hitTestFrameHandle(
                screenX = firstDown.position.x,
                screenY = firstDown.position.y,
                width = size.width.toFloat(),
                height = size.height.toFloat(),
                handleRadius = handleRadius,
            )
        } else {
            null
        }
        val photoHandle = if (selectedPhotoActive) {
            hitTestPhotoHandle(
                photo = selectedPhoto,
                transform = transform,
                screenX = firstDown.position.x,
                screenY = firstDown.position.y,
                handleRadius = handleRadius,
            )
        } else {
            null
        }
        val elementHandle = selectedElement?.let { element ->
            hitTestElementHandleInContainer(
                element = element,
                frameTransform = transform,
                photo = selectedPhoto,
                cards = editorState.draft.internalCards,
                screenX = firstDown.position.x,
                screenY = firstDown.position.y,
                handleRadiusPx = handleRadius,
            )
        }
        val cardHandle = if (selectedCardActive && selectedCard != null) {
            hitTestCardHandle(
                card = selectedCard,
                frameTransform = transform,
                photo = selectedPhoto,
                screenX = firstDown.position.x,
                screenY = firstDown.position.y,
                handleRadiusPx = handleRadius,
            )
        } else {
            null
        }
        val photoScreen = transform.toScreen(photoBounds(selectedPhoto))
        val photoHit = firstDown.position.x in photoScreen.left..photoScreen.right &&
            firstDown.position.y in photoScreen.top..photoScreen.bottom
        val elementTarget = if (elementHandle != null) {
            selectedElement
        } else {
            hitTestElementInContainers(
                elements = editorState.draft.elements,
                frameTransform = transform,
                photo = selectedPhoto,
                cards = editorState.draft.internalCards,
                screenX = firstDown.position.x,
                screenY = firstDown.position.y,
            )
        }
        val cardTarget = if (cardHandle != null) {
            selectedCard
        } else {
            hitTestCard(
                cards = editorState.draft.internalCards,
                frameTransform = transform,
                photo = selectedPhoto,
                screenX = firstDown.position.x,
                screenY = firstDown.position.y,
            )
        }

        val target = when {
            frameHandle != null -> GestureTarget.Frame(frameHandle)
            photoHandle != null -> GestureTarget.Photo(photoHandle)
            elementTarget != null -> GestureTarget.Element(elementTarget.id, elementHandle)
            cardTarget != null -> GestureTarget.Card(cardTarget.id, cardHandle)
            photoHit -> GestureTarget.Photo(null)
            isNearFrameEdge(firstDown.position.x, firstDown.position.y, size.width.toFloat(), size.height.toFloat(), handleRadius) ->
                GestureTarget.Frame(null)
            else -> null
        }

        if (target == null) {
            onSelectElement(null)
            onSelectCard(null)
            onSelectTarget(
                if (editorState.activeTab == CustomTemplateEditorTab.Frame) {
                    CustomTemplateSelection.Frame
                } else {
                    CustomTemplateSelection.Photo
                },
            )
            return@awaitEachGesture
        }

        firstDown.consume()
        when (target) {
            is GestureTarget.Element -> onSelectElement(target.elementId)
            is GestureTarget.Card -> onSelectCard(target.cardId)
            is GestureTarget.Frame -> {
                onSelectTarget(CustomTemplateSelection.Frame)
                onSelectElement(null)
                onSelectCard(null)
            }
            is GestureTarget.Photo -> {
                onSelectTarget(CustomTemplateSelection.Photo)
                onSelectElement(null)
                onSelectCard(null)
            }
        }
        onBeginInteraction()
        var elementBounds = (target as? GestureTarget.Element)
            ?.let { id -> editorState.draft.elements.firstOrNull { it.id == id.elementId } }
            ?.let(::elementBounds)
        val elementTransform = (target as? GestureTarget.Element)
            ?.let { id -> editorState.draft.elements.firstOrNull { it.id == id.elementId } }
            ?.let { elementContainerTransform(it, transform, selectedPhoto, editorState.draft.internalCards) }
            ?: transform
        var cardBounds = (target as? GestureTarget.Card)
            ?.let { id -> editorState.draft.internalCards.firstOrNull { it.id == id.cardId } }
            ?.let(::cardBounds)
        val photoTransform = transform.toScreen(photoBounds(selectedPhoto)).let {
            CanvasTransform(it.left, it.top, it.width, it.height)
        }
        var photoBounds = photoBounds(selectedPhoto)
        var logicalWidth = editorState.draft.frame.logicalWidth
        var logicalHeight = editorState.draft.frame.logicalHeight
        var outerMargins = outerMarginsFromPhoto(selectedPhoto)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == firstDown.id } ?: break
            if (change.changedToUp()) break
            if (event.changes.count { it.pressed } >= 2) continue
            val delta = change.positionChange()
            if (delta.x != 0f || delta.y != 0f) {
                when (target) {
                    is GestureTarget.Element -> {
                        val current = elementBounds ?: break
                        val (dx, dy) = elementTransform.toNormalizedDelta(delta.x, delta.y)
                        val updated = if (target.handle == null) {
                            moveBounds(current, dx, dy)
                        } else {
                            val resized = resizeBounds(current, target.handle, dx, dy)
                            val element = editorState.draft.elements.firstOrNull { it.id == target.elementId }
                            if (element?.type == com.keyxif.app.domain.model.CanvasElementType.ColorChip) {
                                squareColorChipBounds(resized, elementTransform)
                            } else {
                                resized
                            }
                        }
                        elementBounds = updated
                        if (target.handle == null) {
                            val placement = elementPlacementForScreenBounds(
                                rect = elementTransform.toScreen(updated),
                                frameTransform = transform,
                                photo = selectedPhoto,
                                cards = editorState.draft.internalCards,
                            )
                            onUpdateElementPlacement(
                                target.elementId,
                                placement.bounds,
                                placement.coordinateSpace,
                                placement.containerId,
                            )
                        } else {
                            onUpdateElementBounds(target.elementId, updated)
                        }
                    }
                    is GestureTarget.Photo -> {
                        val (dx, dy) = transform.toNormalizedDelta(delta.x, delta.y)
                        photoBounds = if (target.handle == null) {
                            moveBounds(photoBounds, dx, dy)
                        } else {
                            resizePhotoBounds(
                                bounds = photoBounds,
                                handle = target.handle,
                                dx = dx,
                                dy = dy,
                                photoAspectRatio = selectedPhoto.aspectRatio,
                                frameAspectRatio = editorState.draft.frame.aspectRatio,
                            )
                        }
                        onUpdatePhotoBounds(photoBounds)
                    }
                    is GestureTarget.Card -> {
                        val current = cardBounds ?: break
                        val (dx, dy) = photoTransform.toNormalizedDelta(delta.x, delta.y)
                        val updated = if (target.handle == null) {
                            moveBounds(current, dx, dy)
                        } else {
                            resizeBounds(current, target.handle, dx, dy, minSize = 0.08f)
                        }
                        cardBounds = updated
                        onUpdateCardBounds(target.cardId, updated)
                    }
                    is GestureTarget.Frame -> {
                        if (target.handle != null) {
                            val framePhotoBounds = transform.toScreen(photoBounds(selectedPhoto))
                            val logicalDelta = Pair(
                                delta.x / framePhotoBounds.width.coerceAtLeast(1f),
                                delta.y / framePhotoBounds.height.coerceAtLeast(1f),
                            )
                            outerMargins = outerMargins.copy(
                                left = if (target.handle == ResizeHandle.TopLeft || target.handle == ResizeHandle.BottomLeft) {
                                    (outerMargins.left - logicalDelta.first).coerceIn(0f, 3f)
                                } else {
                                    outerMargins.left
                                },
                                right = if (target.handle == ResizeHandle.TopRight || target.handle == ResizeHandle.BottomRight) {
                                    (outerMargins.right + logicalDelta.first).coerceIn(0f, 3f)
                                } else {
                                    outerMargins.right
                                },
                                top = if (target.handle == ResizeHandle.TopLeft || target.handle == ResizeHandle.TopRight) {
                                    (outerMargins.top - logicalDelta.second).coerceIn(0f, 3f)
                                } else {
                                    outerMargins.top
                                },
                                bottom = if (target.handle == ResizeHandle.BottomLeft || target.handle == ResizeHandle.BottomRight) {
                                    (outerMargins.bottom + logicalDelta.second).coerceIn(0f, 3f)
                                } else {
                                    outerMargins.bottom
                                },
                            )
                            onUpdateOuterMargins(
                                outerMargins.left,
                                outerMargins.right,
                                outerMargins.top,
                                outerMargins.bottom,
                            )
                        }
                    }
                }
                change.consume()
            }
        }
        onFinishInteraction()
    }
}

private sealed interface GestureTarget {
    data class Frame(val handle: ResizeHandle?) : GestureTarget
    data class Photo(val handle: ResizeHandle?) : GestureTarget
    data class Element(val elementId: String, val handle: ResizeHandle?) : GestureTarget
    data class Card(val cardId: String, val handle: ResizeHandle?) : GestureTarget
}

private fun hitTestFrameHandle(
    screenX: Float,
    screenY: Float,
    width: Float,
    height: Float,
    handleRadius: Float,
): ResizeHandle? {
    return listOf(
        ResizeHandle.TopLeft to Offset(0f, 0f),
        ResizeHandle.TopRight to Offset(width, 0f),
        ResizeHandle.BottomRight to Offset(width, height),
        ResizeHandle.BottomLeft to Offset(0f, height),
    ).firstOrNull { (_, point) ->
        kotlin.math.abs(screenX - point.x) <= handleRadius &&
            kotlin.math.abs(screenY - point.y) <= handleRadius
    }?.first
}

private fun hitTestFrameHandleInOuter(
    screenX: Float,
    screenY: Float,
    frameOffsetX: Float,
    frameOffsetY: Float,
    frameWidth: Float,
    frameHeight: Float,
    handleRadius: Float,
): ResizeHandle? {
    return listOf(
        ResizeHandle.TopLeft to Offset(frameOffsetX, frameOffsetY),
        ResizeHandle.TopRight to Offset(frameOffsetX + frameWidth, frameOffsetY),
        ResizeHandle.BottomRight to Offset(frameOffsetX + frameWidth, frameOffsetY + frameHeight),
        ResizeHandle.BottomLeft to Offset(frameOffsetX, frameOffsetY + frameHeight),
    ).firstOrNull { (_, point) ->
        kotlin.math.abs(screenX - point.x) <= handleRadius &&
            kotlin.math.abs(screenY - point.y) <= handleRadius
    }?.first
}

private fun hitTestPhotoHandle(
    photo: com.keyxif.app.domain.model.CustomPhotoPlacement,
    transform: CanvasTransform,
    screenX: Float,
    screenY: Float,
    handleRadius: Float,
): ResizeHandle? {
    val bounds = transform.toScreen(photoBounds(photo))
    return listOf(
        ResizeHandle.TopLeft to Offset(bounds.left, bounds.top),
        ResizeHandle.TopRight to Offset(bounds.right, bounds.top),
        ResizeHandle.BottomRight to Offset(bounds.right, bounds.bottom),
        ResizeHandle.BottomLeft to Offset(bounds.left, bounds.bottom),
    ).firstOrNull { (_, point) ->
        kotlin.math.abs(screenX - point.x) <= handleRadius &&
            kotlin.math.abs(screenY - point.y) <= handleRadius
    }?.first
}

private fun isNearFrameEdge(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    threshold: Float,
): Boolean {
    return x <= threshold || y <= threshold || width - x <= threshold || height - y <= threshold
}
