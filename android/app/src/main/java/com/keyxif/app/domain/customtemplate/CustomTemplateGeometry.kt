package com.keyxif.app.domain.customtemplate

import com.keyxif.app.domain.model.CanvasElement
import com.keyxif.app.domain.model.CanvasCoordinateSpace
import com.keyxif.app.domain.model.CanvasElementType
import com.keyxif.app.domain.model.CustomPhotoPlacement
import com.keyxif.app.domain.model.CustomTemplate
import com.keyxif.app.domain.model.CustomTemplateCardSpaceSeverity
import com.keyxif.app.domain.model.CustomTemplateCardSpaceWarning
import com.keyxif.app.domain.model.CustomTemplateSnapGuide
import com.keyxif.app.domain.model.ElementContent
import com.keyxif.app.domain.model.InternalCard
import com.keyxif.app.domain.model.SnapGuideOrientation

data class NormalizedBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
}

data class ScreenBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

data class SnapResult(
    val bounds: NormalizedBounds,
    val guides: List<CustomTemplateSnapGuide> = emptyList(),
)

private const val MIN_READABLE_TEXT_SIZE = 0.026f

enum class ResizeHandle {
    TopLeft,
    TopRight,
    BottomRight,
    BottomLeft,
}

enum class EditorHitTarget {
    Frame,
    Photo,
    Element,
}

data class CanvasTransform(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun toScreen(bounds: NormalizedBounds): ScreenBounds {
        return ScreenBounds(
            left = left + bounds.x * width,
            top = top + bounds.y * height,
            width = bounds.width * width,
            height = bounds.height * height,
        )
    }

    fun toNormalizedDelta(dx: Float, dy: Float): Pair<Float, Float> {
        return Pair(
            if (width == 0f) 0f else dx / width,
            if (height == 0f) 0f else dy / height,
        )
    }
}

fun elementBounds(element: CanvasElement): NormalizedBounds {
    return NormalizedBounds(
        x = element.x,
        y = element.y,
        width = element.width,
        height = element.height,
    )
}

fun CanvasElement.withBounds(bounds: NormalizedBounds): CanvasElement {
    return copy(
        x = bounds.x,
        y = bounds.y,
        width = bounds.width,
        height = bounds.height,
    )
}

fun photoBounds(photo: CustomPhotoPlacement): NormalizedBounds {
    return NormalizedBounds(
        x = photo.x,
        y = photo.y,
        width = photo.width,
        height = photo.height,
    )
}

fun cardBounds(card: InternalCard): NormalizedBounds {
    return NormalizedBounds(
        x = card.x,
        y = card.y,
        width = card.width,
        height = card.height,
    )
}

fun InternalCard.withBounds(bounds: NormalizedBounds): InternalCard {
    return copy(
        x = bounds.x,
        y = bounds.y,
        width = bounds.width,
        height = bounds.height,
    )
}

fun CustomPhotoPlacement.withBounds(
    bounds: NormalizedBounds,
    frameAspectRatio: Float,
): CustomPhotoPlacement {
    val contained = containPhotoBounds(
        bounds = bounds,
        photoAspectRatio = aspectRatio,
        frameAspectRatio = frameAspectRatio,
    )
    return copy(
        x = contained.x,
        y = contained.y,
        width = contained.width,
        height = contained.height,
        scale = contained.width,
    )
}

fun viewportTransform(
    availableWidth: Float,
    availableHeight: Float,
    aspectRatio: Float,
): CanvasTransform {
    if (availableWidth <= 0f || availableHeight <= 0f || aspectRatio <= 0f) {
        return CanvasTransform(0f, 0f, 0f, 0f)
    }
    val widthFromHeight = availableHeight * aspectRatio
    return if (widthFromHeight <= availableWidth) {
        val width = widthFromHeight
        CanvasTransform(
            left = (availableWidth - width) / 2f,
            top = 0f,
            width = width,
            height = availableHeight,
        )
    } else {
        val height = availableWidth / aspectRatio
        CanvasTransform(
            left = 0f,
            top = (availableHeight - height) / 2f,
            width = availableWidth,
            height = height,
        )
    }
}

fun moveBounds(
    bounds: NormalizedBounds,
    dx: Float,
    dy: Float,
): NormalizedBounds {
    return bounds.copy(
        x = (bounds.x + dx).coerceIn(0f, 1f - bounds.width),
        y = (bounds.y + dy).coerceIn(0f, 1f - bounds.height),
    )
}

fun clampBoundsToSafeArea(
    bounds: NormalizedBounds,
    safePadding: Float = 0f,
    minSize: Float = 0.01f,
): NormalizedBounds {
    val safe = safePadding.coerceIn(0f, 0.24f)
    val width = bounds.width.coerceIn(minSize, (1f - safe * 2f).coerceAtLeast(minSize))
    val height = bounds.height.coerceIn(minSize, (1f - safe * 2f).coerceAtLeast(minSize))
    return NormalizedBounds(
        x = bounds.x.coerceIn(safe, (1f - safe - width).coerceAtLeast(safe)),
        y = bounds.y.coerceIn(safe, (1f - safe - height).coerceAtLeast(safe)),
        width = width,
        height = height,
    )
}

fun snapBounds(
    bounds: NormalizedBounds,
    coordinateSpace: CanvasCoordinateSpace,
    containerId: String,
    siblings: List<NormalizedBounds> = emptyList(),
    safePadding: Float = 0f,
    threshold: Float = 0.015f,
): SnapResult {
    val safe = safePadding.coerceIn(0f, 0.24f)
    var next = clampBoundsToSafeArea(bounds, safe)
    val guides = mutableListOf<CustomTemplateSnapGuide>()

    val horizontalTargets = mutableListOf(
        safe,
        0.5f,
        1f - safe,
    )
    val verticalTargets = mutableListOf(
        safe,
        0.5f,
        1f - safe,
    )
    siblings.forEach { sibling ->
        horizontalTargets += listOf(sibling.x, sibling.x + sibling.width / 2f, sibling.right)
        verticalTargets += listOf(sibling.y, sibling.y + sibling.height / 2f, sibling.bottom)
    }

    val xSnap = bestSnapOffset(
        candidates = listOf(next.x, next.x + next.width / 2f, next.right),
        targets = horizontalTargets,
        threshold = threshold,
    )
    if (xSnap != null) {
        next = next.copy(x = (next.x + xSnap.offset).coerceIn(safe, 1f - safe - next.width))
        guides += CustomTemplateSnapGuide(
            orientation = SnapGuideOrientation.Vertical,
            coordinateSpace = coordinateSpace,
            containerId = containerId,
            position = xSnap.target.coerceIn(0f, 1f),
        )
    }

    val ySnap = bestSnapOffset(
        candidates = listOf(next.y, next.y + next.height / 2f, next.bottom),
        targets = verticalTargets,
        threshold = threshold,
    )
    if (ySnap != null) {
        next = next.copy(y = (next.y + ySnap.offset).coerceIn(safe, 1f - safe - next.height))
        guides += CustomTemplateSnapGuide(
            orientation = SnapGuideOrientation.Horizontal,
            coordinateSpace = coordinateSpace,
            containerId = containerId,
            position = ySnap.target.coerceIn(0f, 1f),
        )
    }

    return SnapResult(bounds = next, guides = guides.distinct())
}

fun resizeBounds(
    bounds: NormalizedBounds,
    handle: ResizeHandle,
    dx: Float,
    dy: Float,
    minSize: Float = 0.04f,
): NormalizedBounds {
    val min = minSize.coerceIn(0.01f, 0.5f)
    return when (handle) {
        ResizeHandle.TopLeft -> fromEdges(
            left = (bounds.x + dx).coerceIn(0f, bounds.right - min),
            top = (bounds.y + dy).coerceIn(0f, bounds.bottom - min),
            right = bounds.right,
            bottom = bounds.bottom,
        )
        ResizeHandle.TopRight -> fromEdges(
            left = bounds.x,
            top = (bounds.y + dy).coerceIn(0f, bounds.bottom - min),
            right = (bounds.right + dx).coerceIn(bounds.x + min, 1f),
            bottom = bounds.bottom,
        )
        ResizeHandle.BottomRight -> fromEdges(
            left = bounds.x,
            top = bounds.y,
            right = (bounds.right + dx).coerceIn(bounds.x + min, 1f),
            bottom = (bounds.bottom + dy).coerceIn(bounds.y + min, 1f),
        )
        ResizeHandle.BottomLeft -> fromEdges(
            left = (bounds.x + dx).coerceIn(0f, bounds.right - min),
            top = bounds.y,
            right = bounds.right,
            bottom = (bounds.bottom + dy).coerceIn(bounds.y + min, 1f),
        )
    }
}

fun containPhotoBounds(
    bounds: NormalizedBounds,
    photoAspectRatio: Float,
    frameAspectRatio: Float,
): NormalizedBounds {
    return clampBounds(bounds)
}

fun centeredContainPhotoBounds(
    frameAspectRatio: Float,
    photoAspectRatio: Float,
    padding: Float = 0.08f,
): NormalizedBounds {
    val safePadding = padding.coerceIn(0f, 0.35f)
    val maxBounds = NormalizedBounds(
        x = safePadding,
        y = safePadding,
        width = 1f - safePadding * 2f,
        height = 1f - safePadding * 2f,
    )
    return containPhotoBounds(
        bounds = maxBounds,
        photoAspectRatio = photoAspectRatio,
        frameAspectRatio = frameAspectRatio,
    )
}

fun resizePhotoBounds(
    bounds: NormalizedBounds,
    handle: ResizeHandle,
    dx: Float,
    dy: Float,
    photoAspectRatio: Float,
    frameAspectRatio: Float,
    minSize: Float = 0.08f,
): NormalizedBounds {
    return containPhotoBounds(
        bounds = resizeBounds(bounds, handle, dx, dy, minSize),
        photoAspectRatio = photoAspectRatio,
        frameAspectRatio = frameAspectRatio,
    )
}

fun resizeFrameAspectRatio(
    currentWidth: Float,
    currentHeight: Float,
    handle: ResizeHandle,
    dx: Float,
    dy: Float,
    minLogicalSize: Float = 0.65f,
): Pair<Float, Float> {
    val widthDelta = when (handle) {
        ResizeHandle.TopLeft,
        ResizeHandle.BottomLeft -> -dx
        ResizeHandle.TopRight,
        ResizeHandle.BottomRight -> dx
    }
    val heightDelta = when (handle) {
        ResizeHandle.TopLeft,
        ResizeHandle.TopRight -> -dy
        ResizeHandle.BottomRight,
        ResizeHandle.BottomLeft -> dy
    }
    return Pair(
        (currentWidth + widthDelta).coerceAtLeast(minLogicalSize),
        (currentHeight + heightDelta).coerceAtLeast(minLogicalSize),
    )
}

fun outsideContentAreas(
    photo: CustomPhotoPlacement,
    safePadding: Float = photo.safePadding,
): List<NormalizedBounds> {
    val p = photoBounds(photo)
    val safe = safePadding.coerceIn(0f, 0.2f)
    val left = (p.x - safe).coerceAtLeast(0f)
    val top = (p.y - safe).coerceAtLeast(0f)
    val right = (p.right + safe).coerceAtMost(1f)
    val bottom = (p.bottom + safe).coerceAtMost(1f)
    return listOf(
        NormalizedBounds(0f, 0f, 1f, top),
        NormalizedBounds(0f, bottom, 1f, 1f - bottom),
        NormalizedBounds(0f, top, left, bottom - top),
        NormalizedBounds(right, top, 1f - right, bottom - top),
    ).filter { it.width > 0.001f && it.height > 0.001f }
}

fun hitTestElement(
    elements: List<CanvasElement>,
    transform: CanvasTransform,
    screenX: Float,
    screenY: Float,
): CanvasElement? {
    return elements
        .filterNot { it.hidden || it.locked }
        .sortedByDescending { it.zIndex }
        .firstOrNull { element ->
            val bounds = transform.toScreen(elementBounds(element))
            screenX in bounds.left..bounds.right && screenY in bounds.top..bounds.bottom
        }
}

fun hitTestHandle(
    element: CanvasElement,
    transform: CanvasTransform,
    screenX: Float,
    screenY: Float,
    handleRadiusPx: Float,
): ResizeHandle? {
    val bounds = transform.toScreen(elementBounds(element))
    return listOf(
        ResizeHandle.TopLeft to Pair(bounds.left, bounds.top),
        ResizeHandle.TopRight to Pair(bounds.right, bounds.top),
        ResizeHandle.BottomRight to Pair(bounds.right, bounds.bottom),
        ResizeHandle.BottomLeft to Pair(bounds.left, bounds.bottom),
    ).firstOrNull { (_, point) ->
        kotlin.math.abs(screenX - point.first) <= handleRadiusPx &&
            kotlin.math.abs(screenY - point.second) <= handleRadiusPx
    }?.first
}

fun elementContainerTransform(
    element: CanvasElement,
    frameTransform: CanvasTransform,
    photo: CustomPhotoPlacement,
    cards: List<InternalCard> = emptyList(),
): CanvasTransform {
    return when (element.coordinateSpace) {
        CanvasCoordinateSpace.Photo -> {
            val photoScreen = frameTransform.toScreen(photoBounds(photo))
            CanvasTransform(photoScreen.left, photoScreen.top, photoScreen.width, photoScreen.height)
        }
        CanvasCoordinateSpace.InternalCard -> {
            val photoScreen = frameTransform.toScreen(photoBounds(photo))
            val card = cards.firstOrNull { it.id == element.containerId }
            if (card == null) {
                CanvasTransform(photoScreen.left, photoScreen.top, photoScreen.width, photoScreen.height)
            } else {
                val cardScreen = CanvasTransform(photoScreen.left, photoScreen.top, photoScreen.width, photoScreen.height)
                    .toScreen(cardBounds(card))
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
        CanvasCoordinateSpace.Frame -> frameTransform
    }
}

fun hitTestElementInContainers(
    elements: List<CanvasElement>,
    frameTransform: CanvasTransform,
    photo: CustomPhotoPlacement,
    cards: List<InternalCard> = emptyList(),
    screenX: Float,
    screenY: Float,
): CanvasElement? {
    return elements
        .filterNot { it.hidden || it.locked }
        .sortedByDescending { it.zIndex }
        .firstOrNull { element ->
            val bounds = elementContainerTransform(element, frameTransform, photo, cards).toScreen(elementBounds(element))
            screenX in bounds.left..bounds.right && screenY in bounds.top..bounds.bottom
        }
}

fun hitTestElementHandleInContainer(
    element: CanvasElement,
    frameTransform: CanvasTransform,
    photo: CustomPhotoPlacement,
    cards: List<InternalCard> = emptyList(),
    screenX: Float,
    screenY: Float,
    handleRadiusPx: Float,
): ResizeHandle? {
    val transform = elementContainerTransform(element, frameTransform, photo, cards)
    return hitTestHandle(element, transform, screenX, screenY, handleRadiusPx)
}

fun hitTestCard(
    cards: List<InternalCard>,
    frameTransform: CanvasTransform,
    photo: CustomPhotoPlacement,
    screenX: Float,
    screenY: Float,
): InternalCard? {
    val photoScreen = frameTransform.toScreen(photoBounds(photo))
    val photoTransform = CanvasTransform(photoScreen.left, photoScreen.top, photoScreen.width, photoScreen.height)
    return cards
        .filterNot { it.hidden || it.locked }
        .sortedByDescending { it.zIndex }
        .firstOrNull { card ->
            val bounds = photoTransform.toScreen(cardBounds(card))
            screenX in bounds.left..bounds.right && screenY in bounds.top..bounds.bottom
        }
}

fun hitTestCardHandle(
    card: InternalCard,
    frameTransform: CanvasTransform,
    photo: CustomPhotoPlacement,
    screenX: Float,
    screenY: Float,
    handleRadiusPx: Float,
): ResizeHandle? {
    val photoScreen = frameTransform.toScreen(photoBounds(photo))
    val bounds = CanvasTransform(photoScreen.left, photoScreen.top, photoScreen.width, photoScreen.height)
        .toScreen(cardBounds(card))
    return listOf(
        ResizeHandle.TopLeft to Pair(bounds.left, bounds.top),
        ResizeHandle.TopRight to Pair(bounds.right, bounds.top),
        ResizeHandle.BottomRight to Pair(bounds.right, bounds.bottom),
        ResizeHandle.BottomLeft to Pair(bounds.left, bounds.bottom),
    ).firstOrNull { (_, point) ->
        kotlin.math.abs(screenX - point.first) <= handleRadiusPx &&
            kotlin.math.abs(screenY - point.second) <= handleRadiusPx
    }?.first
}

fun avoidPhotoSafeArea(
    bounds: NormalizedBounds,
    photo: CustomPhotoPlacement,
): NormalizedBounds {
    val safe = photo.safePadding.coerceIn(0f, 0.2f)
    val protectedLeft = (photo.x - safe).coerceAtLeast(0f)
    val protectedTop = (photo.y - safe).coerceAtLeast(0f)
    val protectedRight = (photo.x + photo.width + safe).coerceAtMost(1f)
    val protectedBottom = (photo.y + photo.height + safe).coerceAtMost(1f)
    val protected = NormalizedBounds(
        x = protectedLeft,
        y = protectedTop,
        width = protectedRight - protectedLeft,
        height = protectedBottom - protectedTop,
    )
    val clamped = clampBounds(bounds)
    if (!intersects(clamped, protected)) return clamped
    val candidates = listOf(
        clamped.copy(y = (protected.y - clamped.height).coerceAtLeast(0f)),
        clamped.copy(y = protected.bottom.coerceAtMost(1f - clamped.height)),
        clamped.copy(x = (protected.x - clamped.width).coerceAtLeast(0f)),
        clamped.copy(x = protected.right.coerceAtMost(1f - clamped.width)),
    ).map(::clampBounds)
        .filterNot { intersects(it, protected) }
    return candidates.minByOrNull { distanceSquared(clamped, it) } ?: clamped
}

fun frameElementsCollidingWithPhoto(draft: CustomTemplate): List<CanvasElement> {
    val safe = draft.photoPlacement.safePadding.coerceIn(0f, 0.2f)
    val protectedLeft = (draft.photoPlacement.x - safe).coerceAtLeast(0f)
    val protectedTop = (draft.photoPlacement.y - safe).coerceAtLeast(0f)
    val protectedRight = (draft.photoPlacement.x + draft.photoPlacement.width + safe).coerceAtMost(1f)
    val protectedBottom = (draft.photoPlacement.y + draft.photoPlacement.height + safe).coerceAtMost(1f)
    val protected = NormalizedBounds(
        x = protectedLeft,
        y = protectedTop,
        width = protectedRight - protectedLeft,
        height = protectedBottom - protectedTop,
    )
    return draft.elements.filter { element ->
        !element.hidden &&
            element.coordinateSpace == CanvasCoordinateSpace.Frame &&
            intersects(elementBounds(element), protected)
    }
}

fun validateCardSpace(
    draft: CustomTemplate,
    textResolver: (CanvasElement) -> String = ::defaultValidationText,
): List<CustomTemplateCardSpaceWarning> {
    return draft.internalCards
        .filterNot { it.hidden }
        .mapNotNull { card ->
            val cardElements = draft.elements
                .filter {
                    !it.hidden &&
                        it.coordinateSpace == CanvasCoordinateSpace.InternalCard &&
                        it.containerId == card.id
                }
            val blocking = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            val padding = card.style.padding.coerceIn(0f, 0.45f)
            val contentScale = (1f - padding * 2f).coerceAtLeast(0f)

            if (contentScale <= 0.08f) {
                blocking += "카드 padding 때문에 내부 콘텐츠 영역이 너무 작습니다."
            }

            cardElements.forEach { element ->
                val bounds = elementBounds(element)
                if (bounds.x < 0f || bounds.y < 0f || bounds.right > 1f || bounds.bottom > 1f) {
                    blocking += "요소가 카드 콘텐츠 영역 밖에 있습니다."
                }
                when (element.type) {
                    CanvasElementType.Text -> {
                        if (element.style.fontSize < MIN_READABLE_TEXT_SIZE) {
                            blocking += "텍스트 크기가 너무 작습니다."
                        }
                        if (isTextLikelyOverflowing(element, textResolver(element))) {
                            blocking += "텍스트가 카드 내부 영역을 초과할 수 있습니다."
                        }
                    }
                    CanvasElementType.Logo -> {
                        if (bounds.width < 0.08f || bounds.height < 0.08f) {
                            warnings += "로고 영역이 너무 작아 보일 수 있습니다."
                        }
                    }
                    CanvasElementType.ColorChip -> {
                        if (bounds.width < 0.035f || bounds.height < 0.035f) {
                            warnings += "색상칩 영역이 너무 작습니다."
                        }
                    }
                }
            }

            cardElements.forEachIndexed { index, element ->
                cardElements.drop(index + 1).forEach { other ->
                    if (intersects(elementBounds(element), elementBounds(other))) {
                        warnings += "카드 내부 요소가 서로 겹칩니다."
                    }
                }
            }

            val severity = if (blocking.isNotEmpty()) {
                CustomTemplateCardSpaceSeverity.Blocking
            } else if (warnings.isNotEmpty()) {
                CustomTemplateCardSpaceSeverity.Warning
            } else {
                return@mapNotNull null
            }
            CustomTemplateCardSpaceWarning(
                cardId = card.id,
                severity = severity,
                messages = (blocking + warnings).distinct(),
            )
        }
}

private fun fromEdges(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): NormalizedBounds {
    return NormalizedBounds(
        x = left,
        y = top,
        width = right - left,
        height = bottom - top,
    )
}

private fun clampBounds(bounds: NormalizedBounds): NormalizedBounds {
    val width = bounds.width.coerceIn(0.01f, 1f)
    val height = bounds.height.coerceIn(0.01f, 1f)
    return NormalizedBounds(
        x = bounds.x.coerceIn(0f, 1f - width),
        y = bounds.y.coerceIn(0f, 1f - height),
        width = width,
        height = height,
    )
}

private data class SnapOffset(
    val offset: Float,
    val target: Float,
)

private fun bestSnapOffset(
    candidates: List<Float>,
    targets: List<Float>,
    threshold: Float,
): SnapOffset? {
    return candidates
        .flatMap { candidate ->
            targets.map { target -> SnapOffset(offset = target - candidate, target = target) }
        }
        .filter { kotlin.math.abs(it.offset) <= threshold }
        .minByOrNull { kotlin.math.abs(it.offset) }
}

private fun intersects(a: NormalizedBounds, b: NormalizedBounds): Boolean {
    return a.x < b.right && a.right > b.x && a.y < b.bottom && a.bottom > b.y
}

private fun isTextLikelyOverflowing(
    element: CanvasElement,
    text: String,
): Boolean {
    val value = text.ifBlank { defaultValidationText(element) }
    val fontSize = element.style.fontSize.coerceAtLeast(0.001f)
    val maxLines = element.style.maxLines.coerceAtLeast(1)
    val lineHeight = element.style.lineHeight.coerceAtLeast(1f)
    val lineCapacity = (element.width / (fontSize * 0.55f)).toInt().coerceAtLeast(1)
    val estimatedLines = kotlin.math.ceil(value.length.toFloat() / lineCapacity).toInt().coerceAtLeast(1)
    val requiredHeight = fontSize * lineHeight * estimatedLines
    val allowedHeight = element.height.coerceAtLeast(0f)
    return estimatedLines > maxLines || requiredHeight > allowedHeight * 1.08f
}

private fun defaultValidationText(element: CanvasElement): String {
    return when (val content = element.content) {
        is ElementContent.StaticText -> content.text.ifBlank { "TEXT" }
        is ElementContent.BuildField -> content.field.name
        ElementContent.LogoImage -> ""
        is ElementContent.ColorChip -> ""
    }
}

private fun distanceSquared(a: NormalizedBounds, b: NormalizedBounds): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}
