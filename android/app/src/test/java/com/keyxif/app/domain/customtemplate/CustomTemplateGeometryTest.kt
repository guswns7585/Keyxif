package com.keyxif.app.domain.customtemplate

import com.keyxif.app.domain.model.CanvasCoordinateSpace
import com.keyxif.app.domain.model.CanvasElementType
import com.keyxif.app.domain.model.CustomTemplateCardSpaceSeverity
import com.keyxif.app.domain.model.ElementContent
import com.keyxif.app.domain.model.BuildInfoField
import com.keyxif.app.domain.model.createBlankCustomTemplate
import com.keyxif.app.domain.model.createCustomTemplateInternalCard
import com.keyxif.app.domain.model.createCustomTemplateTextElement
import com.keyxif.app.domain.model.createStage2TestCanvasElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTemplateGeometryTest {
    @Test
    fun moveBoundsKeepsElementInsideContainer() {
        val bounds = NormalizedBounds(x = 0.82f, y = 0.88f, width = 0.22f, height = 0.18f)

        val moved = moveBounds(bounds, dx = 0.2f, dy = 0.2f)

        assertEquals(0.78f, moved.x, 0.0001f)
        assertEquals(0.82f, moved.y, 0.0001f)
        assertEquals(0.22f, moved.width, 0.0001f)
        assertEquals(0.18f, moved.height, 0.0001f)
    }

    @Test
    fun resizeBoundsRespectsMinimumSizeAndContainerEdges() {
        val bounds = NormalizedBounds(x = 0.1f, y = 0.1f, width = 0.2f, height = 0.2f)

        val resized = resizeBounds(
            bounds = bounds,
            handle = ResizeHandle.TopLeft,
            dx = 0.3f,
            dy = 0.3f,
            minSize = 0.05f,
        )

        assertEquals(0.25f, resized.x, 0.0001f)
        assertEquals(0.25f, resized.y, 0.0001f)
        assertEquals(0.05f, resized.width, 0.0001f)
        assertEquals(0.05f, resized.height, 0.0001f)
    }

    @Test
    fun transformRoundTripsNormalizedDelta() {
        val transform = CanvasTransform(left = 12f, top = 20f, width = 300f, height = 500f)
        val bounds = NormalizedBounds(x = 0.1f, y = 0.2f, width = 0.3f, height = 0.4f)

        val screen = transform.toScreen(bounds)
        val delta = transform.toNormalizedDelta(dx = 30f, dy = 50f)

        assertEquals(42f, screen.left, 0.0001f)
        assertEquals(120f, screen.top, 0.0001f)
        assertEquals(90f, screen.width, 0.0001f)
        assertEquals(200f, screen.height, 0.0001f)
        assertEquals(0.1f, delta.first, 0.0001f)
        assertEquals(0.1f, delta.second, 0.0001f)
    }

    @Test
    fun centeredContainPhotoBoundsKeepsEditablePhotoBoxInsideFrame() {
        val bounds = centeredContainPhotoBounds(
            frameAspectRatio = 4f / 5f,
            photoAspectRatio = 16f / 9f,
            padding = 0.08f,
        )

        assertEquals(0.84f, bounds.width, 0.0001f)
        assertEquals(0.84f, bounds.height, 0.0001f)
        assertEquals(0.08f, bounds.x, 0.0001f)
        assertEquals(0.08f, bounds.y, 0.0001f)
    }

    @Test
    fun resizeFrameAspectRatioKeepsMinimumSize() {
        val resized = resizeFrameAspectRatio(
            currentWidth = 0.7f,
            currentHeight = 0.7f,
            handle = ResizeHandle.TopLeft,
            dx = 1f,
            dy = 1f,
        )

        assertEquals(0.65f, resized.first, 0.0001f)
        assertEquals(0.65f, resized.second, 0.0001f)
    }

    @Test
    fun outsideContentAreasExcludePhotoSafeRegion() {
        val areas = outsideContentAreas(
            photo = com.keyxif.app.domain.model.CustomPhotoPlacement(
                x = 0.2f,
                y = 0.2f,
                width = 0.6f,
                height = 0.5f,
                safePadding = 0.05f,
            ),
        )

        assertEquals(4, areas.size)
        assertEquals(0.15f, areas.first().height, 0.0001f)
        assertEquals(0.75f, areas[1].y, 0.0001f)
    }

    @Test
    fun snapBoundsAlignsToContainerCenterWithinThreshold() {
        val result = snapBounds(
            bounds = NormalizedBounds(x = 0.385f, y = 0.2f, width = 0.22f, height = 0.1f),
            coordinateSpace = CanvasCoordinateSpace.Frame,
            containerId = "frame",
        )

        assertEquals(0.39f, result.bounds.x, 0.0001f)
        assertTrue(result.guides.any { it.position == 0.5f })
    }

    @Test
    fun snapBoundsAlignsToSiblingEdge() {
        val result = snapBounds(
            bounds = NormalizedBounds(x = 0.318f, y = 0.5f, width = 0.18f, height = 0.1f),
            coordinateSpace = CanvasCoordinateSpace.Frame,
            containerId = "frame",
            siblings = listOf(NormalizedBounds(x = 0.1f, y = 0.1f, width = 0.22f, height = 0.1f)),
        )

        assertEquals(0.32f, result.bounds.x, 0.0001f)
        assertTrue(result.guides.any { it.position == 0.32f })
    }

    @Test
    fun frameCollisionDetectsPhotoSafeAreaOverlap() {
        val template = createBlankCustomTemplate().copy(
            photoPlacement = com.keyxif.app.domain.model.CustomPhotoPlacement(
                x = 0.2f,
                y = 0.2f,
                width = 0.6f,
                height = 0.5f,
                safePadding = 0.05f,
            ),
            elements = listOf(
                createStage2TestCanvasElement().copy(
                    x = 0.12f,
                    y = 0.16f,
                    width = 0.1f,
                    height = 0.1f,
                ),
            ),
        )

        assertEquals(1, frameElementsCollidingWithPhoto(template).size)
    }

    @Test
    fun validateCardSpaceBlocksSmallCardWithLongText() {
        val card = createCustomTemplateInternalCard(zIndex = 1).copy(width = 0.18f, height = 0.12f)
        val element = createCustomTemplateTextElement(
            field = BuildInfoField.Board,
            coordinateSpace = CanvasCoordinateSpace.InternalCard,
            zIndex = 2,
        ).copy(
            containerId = card.id,
            x = 0.04f,
            y = 0.08f,
            width = 0.5f,
            height = 0.12f,
            content = ElementContent.StaticText("Very long keyboard build information that cannot fit"),
        )
        val template = createBlankCustomTemplate().copy(
            internalCards = listOf(card),
            elements = listOf(element),
        )

        val warnings = validateCardSpace(template)

        assertEquals(1, warnings.size)
        assertEquals(CustomTemplateCardSpaceSeverity.Blocking, warnings.first().severity)
        assertTrue(warnings.first().messages.any { it.contains("텍스트") })
    }

    @Test
    fun validateCardSpaceBlocksElementOutsideInternalCard() {
        val card = createCustomTemplateInternalCard(zIndex = 1)
        val element = createCustomTemplateTextElement(
            field = BuildInfoField.Board,
            coordinateSpace = CanvasCoordinateSpace.InternalCard,
            zIndex = 2,
        ).copy(
            containerId = card.id,
            x = 0.82f,
            y = 0.1f,
            width = 0.3f,
            height = 0.1f,
        )
        val template = createBlankCustomTemplate().copy(
            internalCards = listOf(card),
            elements = listOf(element),
        )

        val warnings = validateCardSpace(template)

        assertEquals(CustomTemplateCardSpaceSeverity.Blocking, warnings.first().severity)
        assertTrue(warnings.first().messages.any { it.contains("밖") || it.contains("벗어") })
    }

    @Test
    fun validateCardSpaceWarnsWhenInternalElementsOverlap() {
        val card = createCustomTemplateInternalCard(zIndex = 1)
        val first = createCustomTemplateTextElement(
            field = BuildInfoField.Board,
            coordinateSpace = CanvasCoordinateSpace.InternalCard,
            zIndex = 2,
        ).copy(
            containerId = card.id,
            x = 0.1f,
            y = 0.1f,
            width = 0.32f,
            height = 0.16f,
        )
        val second = createStage2TestCanvasElement().copy(
            type = CanvasElementType.ColorChip,
            containerId = card.id,
            coordinateSpace = CanvasCoordinateSpace.InternalCard,
            x = 0.2f,
            y = 0.16f,
            width = 0.22f,
            height = 0.12f,
        )
        val template = createBlankCustomTemplate().copy(
            internalCards = listOf(card),
            elements = listOf(first, second),
        )

        val warnings = validateCardSpace(template)

        assertTrue(warnings.first().messages.any { it.contains("겹칩니다") })
    }
}
