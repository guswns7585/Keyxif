package com.keyxif.app.domain.renderer

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class TemplateLayoutMode {
    OverlayOnPhoto,
    ExternalBottomCard,
    ExternalSideCard,
    ExternalFrame,
}

data class TemplateLayoutSpec(
    val mode: TemplateLayoutMode = TemplateLayoutMode.OverlayOnPhoto,
    val leftInsetFraction: Float = 0f,
    val topInsetFraction: Float = 0f,
    val rightInsetFraction: Float = 0f,
    val bottomInsetFraction: Float = 0f,
) {
    init {
        require(leftInsetFraction >= 0f && rightInsetFraction >= 0f)
        require(topInsetFraction >= 0f && bottomInsetFraction >= 0f)
        require(leftInsetFraction + rightInsetFraction < 1f)
        require(topInsetFraction + bottomInsetFraction < 1f)
    }
}

data class RenderRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class RenderLayout(
    val finalWidth: Int,
    val finalHeight: Int,
    val photoRect: RenderRect,
    val cardRect: RenderRect?,
    val mode: TemplateLayoutMode,
)

fun calculateRenderLayout(
    spec: TemplateLayoutSpec,
    imageWidth: Int,
    imageHeight: Int,
    maxLongSide: Int,
): RenderLayout {
    require(imageWidth > 0 && imageHeight > 0)
    require(maxLongSide > 0)

    val photoWidthFraction = 1f - spec.leftInsetFraction - spec.rightInsetFraction
    val photoHeightFraction = 1f - spec.topInsetFraction - spec.bottomInsetFraction
    val naturalWidth = imageWidth / photoWidthFraction
    val naturalHeight = imageHeight / photoHeightFraction
    val outputScale = min(1f, maxLongSide / max(naturalWidth, naturalHeight))
    val finalWidth = max(1, (naturalWidth * outputScale).roundToInt())
    val finalHeight = max(1, (naturalHeight * outputScale).roundToInt())
    val photoRect = RenderRect(
        left = finalWidth * spec.leftInsetFraction,
        top = finalHeight * spec.topInsetFraction,
        right = finalWidth * (1f - spec.rightInsetFraction),
        bottom = finalHeight * (1f - spec.bottomInsetFraction),
    )
    val cardRect = when (spec.mode) {
        TemplateLayoutMode.ExternalBottomCard -> RenderRect(
            left = 0f,
            top = photoRect.bottom,
            right = finalWidth.toFloat(),
            bottom = finalHeight.toFloat(),
        )

        TemplateLayoutMode.ExternalSideCard -> RenderRect(
            left = photoRect.right,
            top = 0f,
            right = finalWidth.toFloat(),
            bottom = finalHeight.toFloat(),
        )

        TemplateLayoutMode.OverlayOnPhoto,
        TemplateLayoutMode.ExternalFrame,
        -> null
    }
    return RenderLayout(
        finalWidth = finalWidth,
        finalHeight = finalHeight,
        photoRect = photoRect,
        cardRect = cardRect,
        mode = spec.mode,
    )
}
