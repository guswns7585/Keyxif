package com.keyxif.app.domain.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.res.ResourcesCompat
import com.keyxif.app.data.repository.PresetRepository
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.CardTemplate
import com.keyxif.app.domain.model.LogoPreset
import com.keyxif.app.domain.model.PhotoItem
import com.keyxif.app.domain.model.TemplateBackgroundTone
import com.keyxif.app.domain.model.isMeaningfulBuildText
import com.keyxif.app.util.BitmapUtils
import kotlin.math.max

class KeyxifCanvasRenderer(
    private val presetRepository: PresetRepository = PresetRepository(),
) {
    private val renderers: Map<CardTemplate, TemplateRenderer> = mapOf(
        CardTemplate.ClassicFrame to ClassicFrameRenderer(),
        CardTemplate.MinimalCaption to MinimalCaptionRenderer(),
        CardTemplate.BottomSpecBar to BottomSpecBarRenderer(),
        CardTemplate.CornerMark to CornerMarkRenderer(),
        CardTemplate.PosterMargin to PosterMarginRenderer(),
        CardTemplate.DarkGlassStrip to DarkGlassStripRenderer(),
        CardTemplate.SideSpecRail to SideSpecRailRenderer(),
        CardTemplate.TopNameplate to TopNameplateRenderer(),
        CardTemplate.MuseumMat to MuseumMatRenderer(),
        CardTemplate.CompactTicket to CompactTicketRenderer(),
        CardTemplate.CleanSignature to CleanSignatureRenderer(),
        CardTemplate.PlainExport to PlainExportRenderer(),
    )

    fun render(
        context: Context,
        photo: PhotoItem,
        template: CardTemplate,
        settings: AppSettings = AppSettings(),
        maxLongSide: Int = BitmapUtils.SAVE_LONG_SIDE_LIMIT,
    ): Bitmap {
        val source = BitmapUtils.decodeOrientedBitmap(context, photo.uri, maxLongSide)
        var output: Bitmap? = null
        var logoBitmap: Bitmap? = null
        try {
            val rendered = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            output = rendered
            val canvas = Canvas(rendered)
            val bounds = RectF(0f, 0f, rendered.width.toFloat(), rendered.height.toFloat())
            val templateRenderer = renderers.getValue(template)
            canvas.drawColor(templateRenderer.backgroundColor())
            drawTemplatePhoto(
                canvas = canvas,
                bitmap = source,
                destination = templateRenderer.photoBounds(bounds),
                placement = templateRenderer.photoPlacement(),
            )
            val logoPreset = if (photo.buildInfo.logoDisabled) null else presetRepository.logoForBuildInfo(photo.buildInfo)
            logoBitmap = if (photo.buildInfo.logoDisabled) {
                null
            } else {
                photo.buildInfo.customLogoUri?.let { uri ->
                    runCatching {
                        BitmapUtils.decodeOrientedBitmap(context, uri, 512)
                    }.getOrNull()
                } ?: decodeLogoBitmap(
                    context = context,
                    drawableResId = resolveLogoDrawable(
                        logoPreset = logoPreset,
                        tone = templateRenderer.logoBackgroundTone(),
                        autoSelectVariant = settings.autoSelectLogoContrastVariant,
                    ),
                )
            }
            val logoLabel = if (photo.buildInfo.logoDisabled) {
                ""
            } else {
                logoPreset?.name
                    ?: presetRepository.logoName(photo.buildInfo.logoId)
                    ?: ""
            }
            val hasLogo = logoBitmap != null || logoLabel.isMeaningfulBuildText()
            val assets = RenderAssets(
                logoBitmap = logoBitmap,
                logoLabel = logoLabel,
                paletteColors = photo.analysisResult.paletteColors,
                hasLogo = hasLogo,
            )
            templateRenderer.draw(canvas, bounds, photo.buildInfo, assets, settings)
            output = null
            return rendered
        } finally {
            source.recycle()
            logoBitmap?.recycle()
            output?.recycle()
        }
    }

    private fun decodeLogoBitmap(
        context: Context,
        drawableResId: Int?,
    ): Bitmap? {
        val resId = drawableResId ?: return null
        val drawable = ResourcesCompat.getDrawable(context.resources, resId, context.theme) ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 256
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 256
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }

    private fun resolveLogoDrawable(
        logoPreset: LogoPreset?,
        tone: TemplateBackgroundTone,
        autoSelectVariant: Boolean,
    ): Int? {
        if (logoPreset == null) return null
        if (!autoSelectVariant) {
            return logoPreset.drawableResId
                ?: logoPreset.blackDrawableResId
                ?: logoPreset.whiteDrawableResId
        }
        return when (tone) {
            TemplateBackgroundTone.Light -> logoPreset.blackDrawableResId
                ?: logoPreset.drawableResId
                ?: logoPreset.whiteDrawableResId
            TemplateBackgroundTone.Dark -> logoPreset.whiteDrawableResId
                ?: logoPreset.drawableResId
                ?: logoPreset.blackDrawableResId
            TemplateBackgroundTone.Mixed -> logoPreset.drawableResId
                ?: logoPreset.blackDrawableResId
                ?: logoPreset.whiteDrawableResId
        }
    }

    private fun drawTemplatePhoto(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        placement: PhotoPlacement,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val widthScale = destination.width() / bitmap.width
        val heightScale = destination.height() / bitmap.height
        val scale = when (placement) {
            PhotoPlacement.CenterCrop -> max(widthScale, heightScale)
            PhotoPlacement.FitCenter -> minOf(widthScale, heightScale)
        }
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val centerX = destination.centerX()
        val centerY = destination.centerY()
        val target = RectF(
            centerX - scaledWidth / 2f,
            centerY - scaledHeight / 2f,
            centerX + scaledWidth / 2f,
            centerY + scaledHeight / 2f,
        )
        val saveCount = canvas.save()
        canvas.clipRect(destination)
        canvas.drawBitmap(bitmap, null, target, paint)
        canvas.restoreToCount(saveCount)
    }
}
