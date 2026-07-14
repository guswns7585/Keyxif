package com.keyxif.app.domain.renderer

import com.keyxif.app.domain.model.LogoColorPolicy
import com.keyxif.app.domain.model.LogoPreset

data class ResolvedLogoRender(
    val drawableResId: Int?,
    val tintColor: Int? = null,
)

object BackgroundContrast {
    private const val DARK_THRESHOLD = 0.36

    fun relativeLuminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun isDark(color: Int): Boolean = relativeLuminance(color) < DARK_THRESHOLD
}

object LogoRenderResolver {
    fun resolveLogoForBackground(
        logo: LogoPreset?,
        backgroundColor: Int,
    ): ResolvedLogoRender {
        logo ?: return ResolvedLogoRender(null)
        return when (logo.colorPolicy) {
            LogoColorPolicy.AUTO_MONO_TINT -> ResolvedLogoRender(
                drawableResId = logo.drawableResId
                    ?: logo.blackDrawableResId
                    ?: logo.whiteDrawableResId,
                tintColor = if (BackgroundContrast.isDark(backgroundColor)) WHITE else BLACK,
            )
            LogoColorPolicy.MANUAL_LIGHT_DARK -> ResolvedLogoRender(
                drawableResId = if (BackgroundContrast.isDark(backgroundColor)) {
                    logo.whiteDrawableResId ?: logo.drawableResId ?: logo.blackDrawableResId
                } else {
                    logo.blackDrawableResId ?: logo.drawableResId ?: logo.whiteDrawableResId
                },
            )
        }
    }

    private const val BLACK: Int = 0xFF000000.toInt()
    private const val WHITE: Int = 0xFFFFFFFF.toInt()
}
