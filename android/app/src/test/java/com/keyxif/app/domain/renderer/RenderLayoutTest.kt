package com.keyxif.app.domain.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RenderLayoutTest {
    @Test
    fun bottomSpecBarExpandsBelowTheWholePhoto() {
        val layout = calculateRenderLayout(BottomSpecBarRenderer().layoutSpec(), 1600, 1000, 4096)

        assertEquals(TemplateLayoutMode.ExternalBottomCard, layout.mode)
        assertEquals(1600, layout.finalWidth)
        assertTrue(layout.finalHeight > 1000)
        assertFullPhoto(layout, 1600f / 1000f)
        assertEquals(layout.photoRect.bottom, layout.cardRect?.top)
    }

    @Test
    fun posterMarginExpandsOnEveryConfiguredEdge() {
        val layout = calculateRenderLayout(PosterMarginRenderer().layoutSpec(), 1600, 1000, 4096)

        assertEquals(TemplateLayoutMode.ExternalFrame, layout.mode)
        assertTrue(layout.finalWidth > 1600)
        assertTrue(layout.finalHeight > 1000)
        assertTrue(layout.photoRect.left > 0f)
        assertTrue(layout.photoRect.top > 0f)
        assertFullPhoto(layout, 1600f / 1000f)
    }

    @Test
    fun sideSpecRailExpandsToTheRightOfTheWholePhoto() {
        val layout = calculateRenderLayout(SideSpecRailRenderer().layoutSpec(), 1600, 1000, 4096)

        assertEquals(TemplateLayoutMode.ExternalSideCard, layout.mode)
        assertTrue(layout.finalWidth > 1600)
        assertEquals(1000, layout.finalHeight)
        assertFullPhoto(layout, 1600f / 1000f)
        assertEquals(layout.photoRect.right, layout.cardRect?.left)
    }

    @Test
    fun finalLongSideLimitScalesTheWholeCompositeWithoutChangingPhotoRatio() {
        val layout = calculateRenderLayout(MuseumMatRenderer().layoutSpec(), 2400, 1600, 1000)

        assertEquals(1000, maxOf(layout.finalWidth, layout.finalHeight))
        assertFullPhoto(layout, 2400f / 1600f)
    }

    @Test
    fun externalLayoutsKeepPortraitAndSquarePhotosUncropped() {
        val specs = listOf(
            BottomSpecBarRenderer().layoutSpec(),
            PosterMarginRenderer().layoutSpec(),
            SideSpecRailRenderer().layoutSpec(),
        )
        specs.forEach { spec ->
            assertFullPhoto(calculateRenderLayout(spec, 1000, 1600, 4096), 1000f / 1600f)
            assertFullPhoto(calculateRenderLayout(spec, 1200, 1200, 4096), 1f)
        }
    }

    @Test
    fun plainExportKeepsTheOriginalCanvas() {
        val layout = calculateRenderLayout(PlainExportRenderer().layoutSpec(), 1600, 1000, 4096)

        assertEquals(1600, layout.finalWidth)
        assertEquals(1000, layout.finalHeight)
        assertEquals(0f, layout.photoRect.left)
        assertEquals(0f, layout.photoRect.top)
        assertEquals(1600f, layout.photoRect.right)
        assertEquals(1000f, layout.photoRect.bottom)
    }

    private fun assertFullPhoto(layout: RenderLayout, expectedAspectRatio: Float) {
        val actualAspectRatio = layout.photoRect.width / layout.photoRect.height
        assertTrue(abs(expectedAspectRatio - actualAspectRatio) < 0.002f)
    }
}
