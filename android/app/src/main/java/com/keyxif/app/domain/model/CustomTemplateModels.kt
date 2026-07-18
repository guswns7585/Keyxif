package com.keyxif.app.domain.model

import java.util.UUID

const val CUSTOM_TEMPLATE_VERSION = 1

data class CustomTemplate(
    val id: String,
    val templateVersion: Int = CUSTOM_TEMPLATE_VERSION,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val frame: BackgroundFrame,
    val photoPlacement: CustomPhotoPlacement,
    val elements: List<CanvasElement> = emptyList(),
    val internalCards: List<InternalCard> = emptyList(),
    val thumbnail: String? = null,
)

data class BackgroundFrame(
    val logicalWidth: Float,
    val logicalHeight: Float,
    val aspectRatio: Float,
    val fill: TemplateFill,
)

data class TemplateFill(
    val type: TemplateFillType = TemplateFillType.Solid,
    val color: String,
    val colorSlotId: String? = null,
)

enum class TemplateFillType {
    Solid,
}

data class CustomPhotoPlacement(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val scale: Float = 1f,
    val aspectRatio: Float = 1f,
    val fitMode: CustomPhotoFitMode = CustomPhotoFitMode.Contain,
    val visualGap: Float = 0f,
    val safePadding: Float = 0.035f,
)

enum class CustomPhotoFitMode {
    Contain,
}

data class CanvasElement(
    val id: String,
    val type: CanvasElementType,
    val containerId: String,
    val coordinateSpace: CanvasCoordinateSpace,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val zIndex: Int = 0,
    val locked: Boolean = false,
    val hidden: Boolean = false,
    val style: ElementStyle = ElementStyle(),
    val content: ElementContent = ElementContent.StaticText(),
)

enum class CanvasElementType {
    Text,
    Logo,
    ColorChip,
}

enum class CanvasCoordinateSpace {
    Frame,
    Photo,
    InternalCard,
}

data class ElementStyle(
    val textColor: String = "#111111",
    val fontSize: Float = 0.045f,
    val fontWeight: ElementFontWeight = ElementFontWeight.Regular,
    val textAlign: ElementTextAlign = ElementTextAlign.Start,
    val lineHeight: Float = 1.12f,
    val letterSpacing: Float = 0f,
    val maxLines: Int = 2,
    val uppercase: Boolean = false,
    val opacity: Float = 1f,
    val cornerRadius: Float = 0f,
    val chipShape: ColorChipShape = ColorChipShape.Rounded,
)

enum class ElementFontWeight {
    Regular,
    Medium,
    Bold,
}

enum class ElementTextAlign {
    Start,
    Center,
    End,
}

enum class ColorChipShape {
    Circle,
    Square,
    Rounded,
}

sealed class ElementContent {
    data class StaticText(val text: String = "") : ElementContent()
    data class BuildField(
        val field: BuildInfoField,
        val format: BuildFieldFormat = BuildFieldFormat.ValueOnly,
    ) : ElementContent()
    data object LogoImage : ElementContent()
    data class ColorChip(val color: String = "#B7C9BF", val colorSlotId: String? = null) : ElementContent()
}

enum class BuildInfoField {
    Board,
    Switch,
    Plate,
    Mount,
    Nickname,
}

enum class BuildFieldFormat {
    ValueOnly,
    LabelAndValue,
    Colon,
}

data class InternalCard(
    val id: String,
    val containerId: String = CUSTOM_TEMPLATE_PHOTO_CONTAINER_ID,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val zIndex: Int = 0,
    val locked: Boolean = false,
    val hidden: Boolean = false,
    val style: CardStyle = CardStyle(),
)

data class CardStyle(
    val backgroundColor: String = "#FFFFFF",
    val colorSlotId: String? = null,
    val opacity: Float = 0.92f,
    val radius: Float = 0.035f,
    val borderEnabled: Boolean = false,
    val borderColor: String = "#000000",
    val borderWidth: Float = 0.002f,
    val padding: Float = 0.035f,
    val shadowEnabled: Boolean = false,
    val shadowBlur: Float = 0.02f,
    val shadowOpacity: Float = 0.2f,
)

enum class CustomTemplateEditorTab(val label: String) {
    Frame("프레임"),
    Element("요소"),
    Card("카드"),
}

data class CustomTemplateEditorState(
    val draft: CustomTemplate = createBlankCustomTemplate(),
    val activeTab: CustomTemplateEditorTab = CustomTemplateEditorTab.Frame,
    val selectedTarget: CustomTemplateSelection = CustomTemplateSelection.Frame,
    val selectedElementId: String? = null,
    val selectedCardId: String? = null,
    val snapGuides: List<CustomTemplateSnapGuide> = emptyList(),
    val collisionWarning: String? = null,
    val cardSpaceWarnings: List<CustomTemplateCardSpaceWarning> = emptyList(),
    val cardSpaceWarningShownForCardIds: Set<String> = emptySet(),
    val undoStack: List<CustomTemplate> = emptyList(),
    val redoStack: List<CustomTemplate> = emptyList(),
    val isDirty: Boolean = false,
)

enum class CustomTemplateSelection {
    Frame,
    Photo,
    Element,
    Card,
}

data class CustomTemplateSnapGuide(
    val orientation: SnapGuideOrientation,
    val coordinateSpace: CanvasCoordinateSpace,
    val containerId: String,
    val position: Float,
)

enum class SnapGuideOrientation {
    Vertical,
    Horizontal,
}

data class CustomTemplateCardSpaceWarning(
    val cardId: String,
    val severity: CustomTemplateCardSpaceSeverity,
    val messages: List<String>,
)

enum class CustomTemplateCardSpaceSeverity {
    Warning,
    Blocking,
}

enum class CustomTemplateCardStylePreset(val label: String) {
    Light("밝은 카드"),
    Dark("어두운 카드"),
    Glass("글래스"),
}

data class FrameAspectPreset(
    val label: String,
    val width: Float,
    val height: Float,
) {
    val aspectRatio: Float get() = width / height
}

val DEFAULT_FRAME_ASPECT_PRESETS: List<FrameAspectPreset> = listOf(
    FrameAspectPreset("1:1", 1f, 1f),
    FrameAspectPreset("4:5", 4f, 5f),
    FrameAspectPreset("3:2", 3f, 2f),
    FrameAspectPreset("16:9", 16f, 9f),
    FrameAspectPreset("9:16", 9f, 16f),
)

const val CUSTOM_TEMPLATE_FRAME_CONTAINER_ID = "frame"
const val CUSTOM_TEMPLATE_PHOTO_CONTAINER_ID = "photo"
const val CUSTOM_TEMPLATE_TEST_ELEMENT_ID = "test-rect"

fun createBlankCustomTemplate(
    now: Long = System.currentTimeMillis(),
    id: String = UUID.randomUUID().toString(),
): CustomTemplate {
    val width = 1f
    val height = 1.25f
    return CustomTemplate(
        id = id,
        name = "새 커스텀 템플릿",
        createdAt = now,
        updatedAt = now,
        frame = BackgroundFrame(
            logicalWidth = width,
            logicalHeight = height,
            aspectRatio = width / height,
            fill = TemplateFill(color = "#F7F5F0"),
        ),
        photoPlacement = CustomPhotoPlacement(
            x = 0.08f,
            y = 0.08f,
            width = 0.84f,
            height = 0.68f,
            aspectRatio = 1f,
        ),
    )
}

fun createStage2TestCanvasElement(): CanvasElement {
    return CanvasElement(
        id = CUSTOM_TEMPLATE_TEST_ELEMENT_ID,
        type = CanvasElementType.ColorChip,
        containerId = CUSTOM_TEMPLATE_FRAME_CONTAINER_ID,
        coordinateSpace = CanvasCoordinateSpace.Frame,
        x = 0.1f,
        y = 0.8f,
        width = 0.28f,
        height = 0.1f,
        zIndex = 0,
        style = ElementStyle(cornerRadius = 0.018f, opacity = 0.95f),
        content = ElementContent.ColorChip(color = "#B7C9BF"),
    )
}

fun createStage2EditorState(): CustomTemplateEditorState {
    val draft = createBlankCustomTemplate()
    val testElement = createStage2TestCanvasElement()
    return CustomTemplateEditorState(
        draft = draft.copy(elements = listOf(testElement)),
        selectedTarget = CustomTemplateSelection.Frame,
        selectedElementId = testElement.id,
    )
}

fun createCustomTemplateTextElement(
    field: BuildInfoField,
    coordinateSpace: CanvasCoordinateSpace,
    zIndex: Int,
    id: String = UUID.randomUUID().toString(),
): CanvasElement {
    return CanvasElement(
        id = id,
        type = CanvasElementType.Text,
        containerId = containerIdFor(coordinateSpace),
        coordinateSpace = coordinateSpace,
        x = 0.1f,
        y = if (coordinateSpace == CanvasCoordinateSpace.Photo) 0.72f else 0.82f,
        width = 0.48f,
        height = 0.09f,
        zIndex = zIndex,
        style = ElementStyle(
            textColor = if (coordinateSpace == CanvasCoordinateSpace.Photo) "#FFFFFF" else "#111111",
            fontSize = 0.052f,
            fontWeight = ElementFontWeight.Bold,
            maxLines = 2,
        ),
        content = ElementContent.BuildField(field = field),
    )
}

fun createCustomTemplateLogoElement(
    coordinateSpace: CanvasCoordinateSpace,
    zIndex: Int,
    id: String = UUID.randomUUID().toString(),
): CanvasElement {
    return CanvasElement(
        id = id,
        type = CanvasElementType.Logo,
        containerId = containerIdFor(coordinateSpace),
        coordinateSpace = coordinateSpace,
        x = if (coordinateSpace == CanvasCoordinateSpace.Photo) 0.72f else 0.68f,
        y = if (coordinateSpace == CanvasCoordinateSpace.Photo) 0.08f else 0.82f,
        width = 0.18f,
        height = 0.12f,
        zIndex = zIndex,
        content = ElementContent.LogoImage,
    )
}

fun createCustomTemplateColorChipElement(
    coordinateSpace: CanvasCoordinateSpace,
    zIndex: Int,
    color: String = "#B7C9BF",
    id: String = UUID.randomUUID().toString(),
): CanvasElement {
    return CanvasElement(
        id = id,
        type = CanvasElementType.ColorChip,
        containerId = containerIdFor(coordinateSpace),
        coordinateSpace = coordinateSpace,
        x = if (coordinateSpace == CanvasCoordinateSpace.Photo) 0.1f else 0.72f,
        y = if (coordinateSpace == CanvasCoordinateSpace.Photo) 0.08f else 0.82f,
        width = 0.12f,
        height = 0.12f,
        zIndex = zIndex,
        style = ElementStyle(
            cornerRadius = 0.02f,
            chipShape = ColorChipShape.Rounded,
        ),
        content = ElementContent.ColorChip(color = color),
    )
}

private fun containerIdFor(space: CanvasCoordinateSpace): String {
    return when (space) {
        CanvasCoordinateSpace.Frame -> CUSTOM_TEMPLATE_FRAME_CONTAINER_ID
        CanvasCoordinateSpace.Photo -> CUSTOM_TEMPLATE_PHOTO_CONTAINER_ID
        CanvasCoordinateSpace.InternalCard -> "internal-card"
    }
}

fun createCustomTemplateInternalCard(
    zIndex: Int,
    stylePreset: CustomTemplateCardStylePreset = CustomTemplateCardStylePreset.Light,
    id: String = UUID.randomUUID().toString(),
): InternalCard {
    return InternalCard(
        id = id,
        x = 0.14f,
        y = 0.14f,
        width = 0.48f,
        height = 0.28f,
        zIndex = zIndex,
        style = stylePreset.toCardStyle(),
    )
}

fun CustomTemplateCardStylePreset.toCardStyle(): CardStyle {
    return when (this) {
        CustomTemplateCardStylePreset.Light -> CardStyle(
            backgroundColor = "#FFFFFF",
            opacity = 0.9f,
            radius = 0.04f,
            borderEnabled = true,
            borderColor = "#FFFFFF",
            borderWidth = 0.002f,
            padding = 0.06f,
            shadowEnabled = true,
            shadowBlur = 0.025f,
            shadowOpacity = 0.18f,
        )
        CustomTemplateCardStylePreset.Dark -> CardStyle(
            backgroundColor = "#151716",
            opacity = 0.78f,
            radius = 0.04f,
            borderEnabled = true,
            borderColor = "#FFFFFF",
            borderWidth = 0.0015f,
            padding = 0.06f,
            shadowEnabled = true,
            shadowBlur = 0.03f,
            shadowOpacity = 0.28f,
        )
        CustomTemplateCardStylePreset.Glass -> CardStyle(
            backgroundColor = "#F7F5F0",
            opacity = 0.58f,
            radius = 0.05f,
            borderEnabled = true,
            borderColor = "#FFFFFF",
            borderWidth = 0.002f,
            padding = 0.065f,
            shadowEnabled = true,
            shadowBlur = 0.035f,
            shadowOpacity = 0.22f,
        )
    }
}
