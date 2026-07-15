package com.keyxif.app.domain.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.TemplateBackgroundTone

interface TemplateRenderer {
    fun backgroundColor(): Int = Color.rgb(18, 18, 18)

    fun layoutSpec(): TemplateLayoutSpec = TemplateLayoutSpec()

    fun photoBounds(bounds: RectF): RectF {
        val spec = layoutSpec()
        return RectF(
            bounds.left + bounds.width() * spec.leftInsetFraction,
            bounds.top + bounds.height() * spec.topInsetFraction,
            bounds.right - bounds.width() * spec.rightInsetFraction,
            bounds.bottom - bounds.height() * spec.bottomInsetFraction,
        )
    }

    fun photoPlacement(): PhotoPlacement = PhotoPlacement.FitCenter

    fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Mixed

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        info: KeyboardBuildInfo,
        assets: RenderAssets,
        settings: AppSettings,
    )
}

enum class PhotoPlacement {
    CenterCrop,
    FitCenter,
}
