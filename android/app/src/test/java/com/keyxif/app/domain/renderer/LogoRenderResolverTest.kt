package com.keyxif.app.domain.renderer

import com.keyxif.app.domain.model.LogoColorPolicy
import com.keyxif.app.domain.model.LogoPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogoRenderResolverTest {
    @Test
    fun autoMonoUsesBaseAndBlackTintOnLightBackground() {
        val result = LogoRenderResolver.resolveLogoForBackground(autoLogo(), 0xFFF5F5F5.toInt())
        assertEquals(10, result.drawableResId)
        assertEquals(0xFF000000.toInt(), result.tintColor)
    }

    @Test
    fun autoMonoUsesBaseAndWhiteTintOnDarkBackground() {
        val result = LogoRenderResolver.resolveLogoForBackground(autoLogo(), 0xFF151515.toInt())
        assertEquals(10, result.drawableResId)
        assertEquals(0xFFFFFFFF.toInt(), result.tintColor)
    }

    @Test
    fun manualPolicyUsesPreparedVariantsWithoutTint() {
        val logo = LogoPreset(
            id = "brand",
            name = "Brand",
            drawableResId = 10,
            whiteDrawableResId = 20,
            blackDrawableResId = 30,
            colorPolicy = LogoColorPolicy.MANUAL_LIGHT_DARK,
        )
        assertEquals(30, LogoRenderResolver.resolveLogoForBackground(logo, 0xFFFFFFFF.toInt()).drawableResId)
        assertEquals(20, LogoRenderResolver.resolveLogoForBackground(logo, 0xFF000000.toInt()).drawableResId)
        assertNull(LogoRenderResolver.resolveLogoForBackground(logo, 0xFF000000.toInt()).tintColor)
    }

    @Test
    fun manualPolicyFallsBackToBaseWhenVariantIsMissing() {
        val logo = LogoPreset(
            id = "colored",
            name = "Colored",
            drawableResId = 44,
            colorPolicy = LogoColorPolicy.MANUAL_LIGHT_DARK,
        )
        assertEquals(44, LogoRenderResolver.resolveLogoForBackground(logo, 0xFFFFFFFF.toInt()).drawableResId)
        assertEquals(44, LogoRenderResolver.resolveLogoForBackground(logo, 0xFF000000.toInt()).drawableResId)
    }

    private fun autoLogo() = LogoPreset(
        id = "mono",
        name = "Mono",
        drawableResId = 10,
        colorPolicy = LogoColorPolicy.AUTO_MONO_TINT,
    )
}
