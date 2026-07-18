package com.keyxif.app.domain.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.res.ResourcesCompat
import com.keyxif.app.data.presets.PresetData
import com.keyxif.app.data.repository.PresetRepository
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.BuildFieldFormat
import com.keyxif.app.domain.model.BuildInfoField
import com.keyxif.app.domain.model.CardTemplate
import com.keyxif.app.domain.model.CanvasCoordinateSpace
import com.keyxif.app.domain.model.CanvasElement
import com.keyxif.app.domain.model.CanvasElementType
import com.keyxif.app.domain.model.ColorChipShape
import com.keyxif.app.domain.model.CustomTemplate
import com.keyxif.app.domain.model.ElementContent
import com.keyxif.app.domain.model.ElementFontWeight
import com.keyxif.app.domain.model.ElementTextAlign
import com.keyxif.app.domain.model.InternalCard
import com.keyxif.app.domain.model.LogoPreset
import com.keyxif.app.domain.model.PhotoItem
import com.keyxif.app.domain.model.isMeaningfulBuildText
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import com.keyxif.app.util.BitmapUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
        CardTemplate.EditorialCover to EditorialCoverRenderer(),
        CardTemplate.SoftEditorial to SoftEditorialRenderer(),
        CardTemplate.PlainExport to PlainExportRenderer(),
    )

    fun render(
        context: Context,
        photo: PhotoItem,
        template: CardTemplate,
        settings: AppSettings = AppSettings(),
        maxLongSide: Int = BitmapUtils.SAVE_LONG_SIDE_LIMIT,
        customTemplate: CustomTemplate? = null,
    ): Bitmap {
        val source = BitmapUtils.decodeOrientedBitmap(context, photo.uri, maxLongSide)
        var output: Bitmap? = null
        var logoBitmap: Bitmap? = null
        var whiteLogoBitmap: Bitmap? = null
        var blackLogoBitmap: Bitmap? = null
        try {
            if (customTemplate != null) {
                val rendered = renderCustomTemplate(
                    context = context,
                    source = source,
                    photo = photo,
                    customTemplate = customTemplate,
                    settings = settings,
                    maxLongSide = maxLongSide,
                )
                return rendered
            }
            val templateRenderer = renderers.getValue(template)
            val layout = calculateRenderLayout(
                spec = templateRenderer.layoutSpec(),
                imageWidth = source.width,
                imageHeight = source.height,
                maxLongSide = maxLongSide,
            )
            val rendered = Bitmap.createBitmap(layout.finalWidth, layout.finalHeight, Bitmap.Config.ARGB_8888)
            output = rendered
            val canvas = Canvas(rendered)
            val bounds = RectF(0f, 0f, rendered.width.toFloat(), rendered.height.toFloat())
            val fallbackBackgroundColor = templateRenderer.backgroundColor()
            val cardBackgroundColor = CanvasRenderUtils.cardBackgroundColor(
                fallback = fallbackBackgroundColor,
                paletteColors = photo.analysisResult.paletteColors,
                settings = settings,
                usePaletteColorForCardBackground = photo.renderStyle.usePaletteColorForCardBackground,
                paletteBackgroundColorIndex = photo.renderStyle.paletteBackgroundColorIndex,
                customCardBackgroundColor = photo.renderStyle.customCardBackgroundColor,
            )
            canvas.drawColor(cardBackgroundColor)
            val photoBounds = RectF(
                layout.photoRect.left,
                layout.photoRect.top,
                layout.photoRect.right,
                layout.photoRect.bottom,
            )
            drawTemplatePhoto(
                canvas = canvas,
                bitmap = source,
                destination = photoBounds,
                placement = templateRenderer.photoPlacement(),
            )
            val customLogoBitmap = if (photo.buildInfo.logoDisabled) null else photo.buildInfo.customLogoUri?.let { uri ->
                runCatching { BitmapUtils.decodeOrientedBitmap(context, uri, 512) }.getOrNull()
            }
            val logoPreset = if (photo.buildInfo.logoDisabled) {
                null
            } else {
                val detectedPreset = presetRepository.logoForBuildInfo(photo.buildInfo)
                if (photo.buildInfo.customLogoUri != null && customLogoBitmap == null) {
                    presetRepository.logoPreset(PresetData.LogoIds.KEYXIF) ?: detectedPreset
                } else {
                    detectedPreset
                }
            }
            val resolvedLogo = LogoRenderResolver.resolveLogoForBackground(logoPreset, cardBackgroundColor)
            logoBitmap = if (photo.buildInfo.logoDisabled) {
                null
            } else {
                customLogoBitmap ?: decodeLogoBitmap(
                    context = context,
                    drawableResId = resolvedLogo.drawableResId,
                )
            }
            if (!photo.buildInfo.logoDisabled && customLogoBitmap == null && logoPreset != null) {
                whiteLogoBitmap = decodeLogoBitmap(context, logoPreset.whiteDrawableResId)
                blackLogoBitmap = decodeLogoBitmap(context, logoPreset.blackDrawableResId)
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
                whiteLogoBitmap = whiteLogoBitmap,
                blackLogoBitmap = blackLogoBitmap,
                logoLabel = logoLabel,
                paletteColors = photo.analysisResult.paletteColors,
                hasLogo = hasLogo,
                cardBackgroundColor = cardBackgroundColor,
                cardContentColor = if (settings.showPaletteColors && photo.renderStyle.usePaletteColorForText) {
                    photo.renderStyle.customTextColor
                        ?: photo.analysisResult.paletteColors
                        .getOrNull(photo.renderStyle.paletteTextColorIndex.coerceIn(0, 4))
                        ?: CanvasRenderUtils.readableContentColor(cardBackgroundColor)
                } else {
                    CanvasRenderUtils.readableContentColor(cardBackgroundColor)
                },
                hasExplicitTextColor = settings.showPaletteColors && photo.renderStyle.usePaletteColorForText,
                logoTintColor = if (customLogoBitmap != null) null else resolvedLogo.tintColor,
                sampleRenderedColor = { x, y ->
                    val px = x.toInt().coerceIn(0, rendered.width - 1)
                    val py = y.toInt().coerceIn(0, rendered.height - 1)
                    rendered.getPixel(px, py)
                },
            )
            drawPhotoOverlayLogoIfNeeded(
                context = context,
                canvas = canvas,
                photoBounds = photoBounds,
                logoPreset = logoPreset,
            )
            CanvasRenderUtils.withTemplateFont(context, settings) {
                templateRenderer.draw(canvas, bounds, photo.buildInfo, assets, settings)
            }
            output = null
            return rendered
        } finally {
            source.recycle()
            logoBitmap?.recycle()
            whiteLogoBitmap?.takeIf { it !== logoBitmap }?.recycle()
            blackLogoBitmap?.takeIf { it !== logoBitmap && it !== whiteLogoBitmap }?.recycle()
            output?.recycle()
        }
    }

    private fun renderCustomTemplate(
        context: Context,
        source: Bitmap,
        photo: PhotoItem,
        customTemplate: CustomTemplate,
        settings: AppSettings,
        maxLongSide: Int,
    ): Bitmap {
        var output: Bitmap? = null
        var logoBitmap: Bitmap? = null
        var whiteLogoBitmap: Bitmap? = null
        var blackLogoBitmap: Bitmap? = null
        try {
            val layout = calculateCustomTemplateLayout(
                template = customTemplate,
                imageWidth = source.width,
                imageHeight = source.height,
                maxLongSide = maxLongSide,
            )
            val rendered = Bitmap.createBitmap(layout.finalWidth, layout.finalHeight, Bitmap.Config.ARGB_8888)
            output = rendered
            val canvas = Canvas(rendered)
            val frameRect = RectF(0f, 0f, rendered.width.toFloat(), rendered.height.toFloat())
            val frameColor = resolveTemplateColor(
                fallback = parseTemplateColor(customTemplate.frame.fill.color, Color.rgb(247, 245, 240)),
                colorSlotId = customTemplate.frame.fill.colorSlotId,
                photo = photo,
                settings = settings,
                renderStyleFallback = photo.renderStyle.customCardBackgroundColor,
            )
            canvas.drawColor(frameColor)

            drawTemplatePhoto(
                canvas = canvas,
                bitmap = source,
                destination = layout.photoRect,
                placement = PhotoPlacement.FitCenter,
            )

            val customLogoBitmap = if (photo.buildInfo.logoDisabled) null else photo.buildInfo.customLogoUri?.let { uri ->
                runCatching { BitmapUtils.decodeOrientedBitmap(context, uri, 512) }.getOrNull()
            }
            val logoPreset = if (photo.buildInfo.logoDisabled) {
                null
            } else {
                val detectedPreset = presetRepository.logoForBuildInfo(photo.buildInfo)
                if (photo.buildInfo.customLogoUri != null && customLogoBitmap == null) {
                    presetRepository.logoPreset(PresetData.LogoIds.KEYXIF) ?: detectedPreset
                } else {
                    detectedPreset
                }
            }
            val resolvedLogo = LogoRenderResolver.resolveLogoForBackground(logoPreset, frameColor)
            logoBitmap = if (photo.buildInfo.logoDisabled) {
                null
            } else {
                customLogoBitmap ?: decodeLogoBitmap(context, resolvedLogo.drawableResId)
            }
            if (!photo.buildInfo.logoDisabled && customLogoBitmap == null && logoPreset != null) {
                whiteLogoBitmap = decodeLogoBitmap(context, logoPreset.whiteDrawableResId)
                blackLogoBitmap = decodeLogoBitmap(context, logoPreset.blackDrawableResId)
            }
            val logoLabel = if (photo.buildInfo.logoDisabled) {
                ""
            } else {
                logoPreset?.name ?: presetRepository.logoName(photo.buildInfo.logoId) ?: ""
            }
            val contentColor = resolveTextContentColor(frameColor, photo, settings)
            val assets = RenderAssets(
                logoBitmap = logoBitmap,
                whiteLogoBitmap = whiteLogoBitmap,
                blackLogoBitmap = blackLogoBitmap,
                logoLabel = logoLabel,
                paletteColors = photo.analysisResult.paletteColors,
                hasLogo = logoBitmap != null || logoLabel.isMeaningfulBuildText(),
                cardBackgroundColor = frameColor,
                cardContentColor = contentColor,
                hasExplicitTextColor = settings.showPaletteColors && photo.renderStyle.usePaletteColorForText,
                logoTintColor = if (customLogoBitmap != null) null else resolvedLogo.tintColor,
                sampleRenderedColor = { x, y ->
                    val px = x.toInt().coerceIn(0, rendered.width - 1)
                    val py = y.toInt().coerceIn(0, rendered.height - 1)
                    rendered.getPixel(px, py)
                },
            )
            drawPhotoOverlayLogoIfNeeded(context, canvas, layout.photoRect, logoPreset)

            CanvasRenderUtils.withTemplateFont(context, settings) {
                customTemplate.elements
                    .filter { !it.hidden && it.coordinateSpace != CanvasCoordinateSpace.InternalCard }
                    .sortedBy { it.zIndex }
                    .forEach { element ->
                        drawCustomElement(
                            canvas = canvas,
                            element = element,
                            container = containerRectFor(element.coordinateSpace, frameRect, layout.photoRect, null),
                            photo = photo,
                            settings = settings,
                            assets = assets,
                            defaultTextColor = contentColor,
                        )
                    }

                customTemplate.internalCards
                    .filterNot { it.hidden }
                    .sortedBy { it.zIndex }
                    .forEach { card ->
                        val cardRect = customRect(layout.photoRect, card.x, card.y, card.width, card.height)
                        drawInternalCard(canvas, card, cardRect, photo, settings)
                        val contentRect = RectF(
                            cardRect.left + cardRect.width() * card.style.padding.coerceIn(0f, 0.45f),
                            cardRect.top + cardRect.height() * card.style.padding.coerceIn(0f, 0.45f),
                            cardRect.right - cardRect.width() * card.style.padding.coerceIn(0f, 0.45f),
                            cardRect.bottom - cardRect.height() * card.style.padding.coerceIn(0f, 0.45f),
                        )
                        val saveCount = canvas.save()
                        canvas.clipRect(cardRect)
                        customTemplate.elements
                            .filter { !it.hidden && it.coordinateSpace == CanvasCoordinateSpace.InternalCard && it.containerId == card.id }
                            .sortedBy { it.zIndex }
                            .forEach { element ->
                                drawCustomElement(
                                    canvas = canvas,
                                    element = element,
                                    container = contentRect,
                                    photo = photo,
                                    settings = settings,
                                    assets = assets,
                                    defaultTextColor = CanvasRenderUtils.readableContentColor(
                                        parseTemplateColor(card.style.backgroundColor, Color.WHITE),
                                    ),
                                )
                            }
                        canvas.restoreToCount(saveCount)
                    }
            }

            output = null
            return rendered
        } finally {
            logoBitmap?.recycle()
            whiteLogoBitmap?.takeIf { it !== logoBitmap }?.recycle()
            blackLogoBitmap?.takeIf { it !== logoBitmap && it !== whiteLogoBitmap }?.recycle()
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

    private fun calculateCustomTemplateLayout(
        template: CustomTemplate,
        imageWidth: Int,
        imageHeight: Int,
        maxLongSide: Int,
    ): CustomTemplateRenderLayout {
        val placement = template.photoPlacement
        val normalizedPhotoWidth = placement.width.coerceIn(0.01f, 1f)
        val normalizedPhotoHeight = placement.height.coerceIn(0.01f, 1f)
        val photoX = placement.x.coerceIn(0f, 1f - normalizedPhotoWidth)
        val photoY = placement.y.coerceIn(0f, 1f - normalizedPhotoHeight)
        val marginLeft = (photoX / normalizedPhotoWidth).coerceIn(0f, 3f)
        val marginRight = ((1f - photoX - normalizedPhotoWidth) / normalizedPhotoWidth).coerceIn(0f, 3f)
        val marginTop = (photoY / normalizedPhotoHeight).coerceIn(0f, 3f)
        val marginBottom = ((1f - photoY - normalizedPhotoHeight) / normalizedPhotoHeight).coerceIn(0f, 3f)
        val naturalWidth = imageWidth * (1f + marginLeft + marginRight)
        val naturalHeight = imageHeight * (1f + marginTop + marginBottom)
        val limit = maxLongSide.takeIf { it > 0 } ?: BitmapUtils.SAVE_LONG_SIDE_LIMIT
        val scale = min(1f, limit / max(naturalWidth, naturalHeight))
        val finalWidth = max(1, (naturalWidth * scale).roundToInt())
        val finalHeight = max(1, (naturalHeight * scale).roundToInt())
        val photoRect = RectF(
            imageWidth * marginLeft * scale,
            imageHeight * marginTop * scale,
            imageWidth * (marginLeft + 1f) * scale,
            imageHeight * (marginTop + 1f) * scale,
        )
        return CustomTemplateRenderLayout(finalWidth, finalHeight, photoRect)
    }

    private fun drawInternalCard(
        canvas: Canvas,
        card: InternalCard,
        rect: RectF,
        photo: PhotoItem,
        settings: AppSettings,
    ) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val background = resolveTemplateColor(
            fallback = parseTemplateColor(card.style.backgroundColor, Color.WHITE),
            colorSlotId = card.style.colorSlotId,
            photo = photo,
            settings = settings,
            renderStyleFallback = photo.renderStyle.customCardBackgroundColor,
        )
        val radius = min(rect.width(), rect.height()) * card.style.radius.coerceIn(0f, 0.5f)
        if (card.style.shadowEnabled) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((card.style.shadowOpacity.coerceIn(0f, 1f) * 255).roundToInt(), 0, 0, 0)
                setShadowLayer(max(rect.width(), rect.height()) * card.style.shadowBlur.coerceIn(0f, 0.2f), 0f, rect.height() * 0.018f, color)
            }
            canvas.drawRoundRect(rect, radius, radius, shadowPaint)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(background, card.style.opacity)
        }
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (card.style.borderEnabled) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = parseTemplateColor(card.style.borderColor, Color.BLACK)
                strokeWidth = (min(rect.width(), rect.height()) * card.style.borderWidth.coerceAtLeast(0f)).coerceAtLeast(1f)
            }
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
    }

    private fun drawCustomElement(
        canvas: Canvas,
        element: CanvasElement,
        container: RectF,
        photo: PhotoItem,
        settings: AppSettings,
        assets: RenderAssets,
        defaultTextColor: Int,
    ) {
        if (container.width() <= 0f || container.height() <= 0f) return
        val rect = customRect(container, element.x, element.y, element.width, element.height)
        if (rect.width() <= 0f || rect.height() <= 0f) return
        when (element.type) {
            CanvasElementType.Text -> drawCustomTextElement(canvas, rect, element, photo, settings, defaultTextColor)
            CanvasElementType.Logo -> drawCustomLogoElement(canvas, rect, element, assets, defaultTextColor)
            CanvasElementType.ColorChip -> drawCustomColorChipElement(canvas, rect, element, photo, settings, defaultTextColor)
        }
    }

    private fun drawCustomTextElement(
        canvas: Canvas,
        rect: RectF,
        element: CanvasElement,
        photo: PhotoItem,
        settings: AppSettings,
        defaultTextColor: Int,
    ) {
        val raw = resolveElementText(element.content, photo) ?: return
        val text = if (element.style.uppercase) raw.uppercase() else raw
        if (text.isBlank()) return
        val color = parseTemplateColor(element.style.textColor, defaultTextColor)
        val paint = when (element.style.fontWeight) {
            ElementFontWeight.Bold, ElementFontWeight.Medium -> CanvasRenderUtils.medium(
                customFontSize(rect, element.style.fontSize, settings),
                withAlpha(color, element.style.opacity),
            )
            ElementFontWeight.Regular -> CanvasRenderUtils.regular(
                customFontSize(rect, element.style.fontSize, settings),
                withAlpha(color, element.style.opacity),
            )
        }.apply {
            textAlign = when (element.style.textAlign) {
                ElementTextAlign.Start -> Paint.Align.LEFT
                ElementTextAlign.Center -> Paint.Align.CENTER
                ElementTextAlign.End -> Paint.Align.RIGHT
            }
            letterSpacing = element.style.letterSpacing
        }
        val x = when (element.style.textAlign) {
            ElementTextAlign.Start -> rect.left
            ElementTextAlign.Center -> rect.centerX()
            ElementTextAlign.End -> rect.right
        }
        val maxLines = element.style.maxLines.coerceAtLeast(1)
        val lineHeight = paint.textSize * element.style.lineHeight.coerceIn(0.9f, 2.4f)
        val lines = wrapCustomText(text, paint, rect.width(), maxLines)
        val saveCount = canvas.save()
        canvas.clipRect(rect)
        var baseline = rect.top - paint.ascent()
        lines.forEach { line ->
            if (baseline <= rect.bottom + paint.descent()) {
                canvas.drawText(line, x, baseline, paint)
            }
            baseline += lineHeight
        }
        canvas.restoreToCount(saveCount)
    }

    private fun drawCustomLogoElement(
        canvas: Canvas,
        rect: RectF,
        element: CanvasElement,
        assets: RenderAssets,
        defaultTextColor: Int,
    ) {
        if (!assets.hasLogo) return
        val textColor = parseTemplateColor(element.style.textColor, defaultTextColor)
        val tintedAssets = if (assets.logoTintColor == null && settingsStyleWantsTextColor(element)) {
            assets.copy(logoTintColor = textColor)
        } else {
            assets
        }
        CanvasRenderUtils.drawLogo(
            canvas = canvas,
            rect = rect,
            assets = tintedAssets,
            textColor = withAlpha(textColor, element.style.opacity),
            backgroundColor = Color.TRANSPARENT,
            anchor = when (element.style.textAlign) {
                ElementTextAlign.Start -> LogoAnchor.Start
                ElementTextAlign.Center -> LogoAnchor.Center
                ElementTextAlign.End -> LogoAnchor.End
            },
            fitMode = LogoFitMode.Inside,
        )
    }

    private fun drawCustomColorChipElement(
        canvas: Canvas,
        rect: RectF,
        element: CanvasElement,
        photo: PhotoItem,
        settings: AppSettings,
        defaultTextColor: Int,
    ) {
        val chip = element.content as? ElementContent.ColorChip ?: return
        val side = min(rect.width(), rect.height())
        val chipRect = RectF(
            rect.centerX() - side / 2f,
            rect.centerY() - side / 2f,
            rect.centerX() + side / 2f,
            rect.centerY() + side / 2f,
        )
        val color = resolveTemplateColor(
            fallback = parseTemplateColor(chip.color, defaultTextColor),
            colorSlotId = chip.colorSlotId,
            photo = photo,
            settings = settings,
            renderStyleFallback = null,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = withAlpha(color, element.style.opacity) }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = withAlpha(CanvasRenderUtils.readableContentColor(color), 0.32f)
            strokeWidth = min(rect.width(), rect.height()) * 0.045f
        }
        when (element.style.chipShape) {
            ColorChipShape.Circle -> {
                val radius = side / 2f
                canvas.drawCircle(chipRect.centerX(), chipRect.centerY(), radius, paint)
                canvas.drawCircle(chipRect.centerX(), chipRect.centerY(), radius, strokePaint)
            }
            ColorChipShape.Square -> {
                canvas.drawRect(chipRect, paint)
                canvas.drawRect(chipRect, strokePaint)
            }
            ColorChipShape.Rounded -> {
                val radius = side * element.style.cornerRadius.coerceIn(0.02f, 0.5f)
                canvas.drawRoundRect(chipRect, radius, radius, paint)
                canvas.drawRoundRect(chipRect, radius, radius, strokePaint)
            }
        }
    }

    private fun resolveElementText(content: ElementContent, photo: PhotoItem): String? {
        return when (content) {
            is ElementContent.StaticText -> content.text.takeIf { it.isNotBlank() }
            is ElementContent.BuildField -> {
                val value = when (content.field) {
                    BuildInfoField.Board -> photo.buildInfo.housing
                    BuildInfoField.Switch -> photo.buildInfo.switchName
                    BuildInfoField.Plate -> photo.buildInfo.plate
                    BuildInfoField.Mount -> photo.buildInfo.mount
                    BuildInfoField.Nickname -> photo.buildInfo.nickname
                }.meaningfulBuildTextOrNull() ?: return null
                when (content.format) {
                    BuildFieldFormat.ValueOnly -> value
                    BuildFieldFormat.LabelAndValue -> "${content.field.label()} $value"
                    BuildFieldFormat.Colon -> "${content.field.label()}: $value"
                }
            }
            ElementContent.LogoImage -> null
            is ElementContent.ColorChip -> null
        }
    }

    private fun BuildInfoField.label(): String = when (this) {
        BuildInfoField.Board -> "BOARD"
        BuildInfoField.Switch -> "SWITCH"
        BuildInfoField.Plate -> "PLATE"
        BuildInfoField.Mount -> "MOUNT"
        BuildInfoField.Nickname -> "NICKNAME"
    }

    private fun wrapCustomText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (maxWidth <= 0f) return emptyList()
        val tokens = text
            .replace("/", " / ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = ""
        for (token in tokens) {
            val candidate = if (current.isBlank()) token else "$current $token"
            if (current.isBlank() || paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                lines += current
                current = token
            }
        }
        if (current.isNotBlank()) lines += current
        return if (lines.size <= maxLines) {
            lines
        } else {
            lines.take(maxLines).mapIndexed { index, line ->
                if (index == maxLines - 1) {
                    CanvasRenderUtils.ellipsize((listOf(line) + lines.drop(maxLines)).joinToString(" "), paint, maxWidth)
                } else {
                    line
                }
            }
        }
    }

    private fun containerRectFor(
        coordinateSpace: CanvasCoordinateSpace,
        frameRect: RectF,
        photoRect: RectF,
        internalCardRect: RectF?,
    ): RectF = when (coordinateSpace) {
        CanvasCoordinateSpace.Frame -> frameRect
        CanvasCoordinateSpace.Photo -> photoRect
        CanvasCoordinateSpace.InternalCard -> internalCardRect ?: frameRect
    }

    private fun customRect(container: RectF, x: Float, y: Float, width: Float, height: Float): RectF {
        val left = container.left + container.width() * x
        val top = container.top + container.height() * y
        return RectF(
            left,
            top,
            left + container.width() * width.coerceAtLeast(0.001f),
            top + container.height() * height.coerceAtLeast(0.001f),
        )
    }

    private fun customFontSize(rect: RectF, normalizedSize: Float, settings: AppSettings): Float {
        return (min(rect.width(), rect.height()) * normalizedSize.coerceIn(0.01f, 0.4f) * 2.2f * settings.textScale.coerceIn(0.9f, 1.55f))
            .coerceAtLeast(8.5f)
    }

    private fun parseTemplateColor(value: String, fallback: Int): Int {
        return runCatching { Color.parseColor(value) }.getOrDefault(fallback)
    }

    private fun resolveTextContentColor(backgroundColor: Int, photo: PhotoItem, settings: AppSettings): Int {
        if (settings.showPaletteColors && photo.renderStyle.usePaletteColorForText) {
            return photo.renderStyle.customTextColor
                ?: photo.analysisResult.paletteColors.getOrNull(photo.renderStyle.paletteTextColorIndex.coerceIn(0, 4))
                ?: CanvasRenderUtils.readableContentColor(backgroundColor)
        }
        return CanvasRenderUtils.readableContentColor(backgroundColor)
    }

    private fun resolveTemplateColor(
        fallback: Int,
        colorSlotId: String?,
        photo: PhotoItem,
        settings: AppSettings,
        renderStyleFallback: Int?,
    ): Int {
        if (settings.showPaletteColors) {
            val slot = colorSlotId?.lowercase().orEmpty()
            if (slot.contains("text")) {
                return resolveTextContentColor(fallback, photo, settings)
            }
            val paletteIndex = Regex("(\\d+)").find(slot)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (slot.contains("palette") && paletteIndex != null) {
                return photo.analysisResult.paletteColors.getOrNull(paletteIndex.coerceIn(0, 4)) ?: fallback
            }
            if (slot.contains("background") || slot.contains("card")) {
                return renderStyleFallback
                    ?: photo.analysisResult.paletteColors.getOrNull(photo.renderStyle.paletteBackgroundColorIndex.coerceIn(0, 4))
                    ?: fallback
            }
        }
        return fallback
    }

    private fun withAlpha(color: Int, opacity: Float): Int {
        val alpha = (Color.alpha(color) * opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun settingsStyleWantsTextColor(element: CanvasElement): Boolean {
        return element.style.textColor.isNotBlank()
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

    private fun drawPhotoOverlayLogoIfNeeded(
        context: Context,
        canvas: Canvas,
        photoBounds: RectF,
        logoPreset: LogoPreset?,
    ) {
        val overlayResId = logoPreset?.photoOverlayDrawableResId ?: return
        val overlay = decodeLogoBitmap(context, overlayResId) ?: return
        try {
            val targetWidth = photoBounds.width() * 0.33f
            val targetHeight = photoBounds.height() * 0.33f
            val box = RectF(
                photoBounds.right - targetWidth - photoBounds.width() * 0.035f,
                photoBounds.bottom - targetHeight - photoBounds.height() * 0.035f,
                photoBounds.right - photoBounds.width() * 0.035f,
                photoBounds.bottom - photoBounds.height() * 0.035f,
            )
            val target = LogoDrawUtils.fitInside(
                sourceWidth = overlay.width.toFloat(),
                sourceHeight = overlay.height.toFloat(),
                box = box,
                anchor = LogoAnchor.Center,
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                alpha = 230
            }
            val saveCount = canvas.save()
            canvas.clipRect(photoBounds)
            canvas.drawBitmap(overlay, null, target, paint)
            canvas.restoreToCount(saveCount)
        } finally {
            overlay.recycle()
        }
    }

    private data class CustomTemplateRenderLayout(
        val finalWidth: Int,
        val finalHeight: Int,
        val photoRect: RectF,
    )
}
