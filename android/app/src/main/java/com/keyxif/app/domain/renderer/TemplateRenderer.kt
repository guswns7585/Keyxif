package com.keyxif.app.domain.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.TemplateBackgroundTone

interface TemplateRenderer {
    fun backgroundColor(): Int = Color.rgb(18, 18, 18)

    fun photoBounds(bounds: RectF): RectF = RectF(bounds)

    fun photoPlacement(): PhotoPlacement = PhotoPlacement.CenterCrop

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
