package com.keyxif.app.domain.renderer

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.BuildInfoRow
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.TemplateBackgroundTone
import com.keyxif.app.domain.model.displayNicknameOrNull
import com.keyxif.app.domain.model.displayTitleOrNull
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import com.keyxif.app.domain.model.toDisplayRows
import com.keyxif.app.domain.renderer.CanvasRenderUtils.drawLogo
import com.keyxif.app.domain.renderer.CanvasRenderUtils.drawRoundRect
import com.keyxif.app.domain.renderer.CanvasRenderUtils.drawTextBaseline
import com.keyxif.app.domain.renderer.CanvasRenderUtils.medium
import com.keyxif.app.domain.renderer.CanvasRenderUtils.nicknameText
import com.keyxif.app.domain.renderer.CanvasRenderUtils.regular
import com.keyxif.app.domain.renderer.CanvasRenderUtils.scaled
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class ClassicFrameRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.rgb(247, 247, 243)
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Light
    override fun layoutSpec() = TemplateLayoutSpec(
        mode = TemplateLayoutMode.ExternalBottomCard,
        bottomInsetFraction = BAR_RATIO,
    )

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val w = bounds.width()
        val h = bounds.height()
        val bar = RectF(0f, h * (1f - BAR_RATIO), w, h)
        val pad = w * 0.028f
        val logoWidth = min(w * 0.13f, bar.height() * 1.12f)
        val logoBox = RectF(pad, bar.top + bar.height() * 0.2f, pad + logoWidth, bar.bottom - bar.height() * 0.2f)
        val contentColor = assets.cardContentColor
        val secondaryColor = contentColor
        val logoActual = drawLogoIfPresent(canvas, logoBox, assets, contentColor, LogoAnchor.Start)

        val rows = info.toDisplayRows(includeNickname = true).take(6)
        val startX = (logoActual?.right ?: bar.left) + if (logoActual == null) pad else pad
        val availableWidth = (bar.right - startX - pad).coerceAtLeast(w * 0.35f)
        val labelPaint = medium(scaled(h * 0.012f, settings), secondaryColor)
        val valuePaint = medium(scaled(h * 0.0165f, settings), contentColor)

        if (rows.isNotEmpty()) {
            val columns = rows.size.coerceAtMost(3)
            val rowCount = ((rows.size + columns - 1) / columns).coerceAtLeast(1)
            val columnWidth = availableWidth / columns
            val firstLabelY = bar.top + bar.height() * if (rowCount == 1) 0.41f else 0.28f
            val rowStep = if (rowCount == 1) 0f else bar.height() * 0.39f
            rows.forEachIndexed { index, row ->
                val column = index % columns
                val rowIndex = index / columns
                val x = startX + column * columnWidth
                val labelY = firstLabelY + rowIndex * rowStep
                drawInfoRow(canvas, row, x, labelY, columnWidth * 0.88f, labelPaint, valuePaint, bar.height() * 0.18f)
            }
        }

        val paletteArea = RectF(
            startX,
            bar.bottom - bar.height() * 0.31f,
            w - pad,
            bar.bottom - bar.height() * 0.08f,
        )
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = CanvasRenderUtils.visiblePaletteColors(assets, settings),
            area = paletteArea,
            chipSize = bar.height() * 0.095f,
            gap = bar.height() * 0.045f,
            strokeColor = Color.argb(72, 0, 0, 0),
            alignment = PaletteChipAlignment.End,
            maxChips = if (rows.size > 3) 3 else settings.paletteColorCount,
        )
    }

    private companion object {
        const val BAR_RATIO = 0.14f
    }
}

class MinimalCaptionRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.rgb(252, 252, 249)
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Light
    override fun layoutSpec() = TemplateLayoutSpec(
        mode = TemplateLayoutMode.ExternalBottomCard,
        bottomInsetFraction = CAPTION_RATIO,
    )

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val w = bounds.width()
        val h = bounds.height()
        val captionTop = h * (1f - CAPTION_RATIO)
        val captionHeight = h * CAPTION_RATIO
        val pad = w * 0.045f
        val contentColor = assets.cardContentColor
        val secondaryColor = contentColor
        val titlePaint = medium(scaled(h * 0.024f, settings), contentColor)
        val bodyPaint = regular(scaled(h * 0.0155f, settings), secondaryColor)
        val logoWidth = min(w * 0.12f, captionHeight * 1.05f)
        val logoBox = RectF(w - pad - logoWidth, captionTop + captionHeight * 0.24f, w - pad, captionTop + captionHeight * 0.76f)
        val logoActual = drawLogoIfPresent(canvas, logoBox, assets, contentColor, LogoAnchor.End)
        val textRight = (logoActual?.left?.minus(pad)) ?: (w - pad)
        val title = info.displayTitleOrNull()
        val detail = detailTextExcluding(
            title,
            info.housing,
            info.switchName,
            info.keycap,
            nicknameDetail(info, settings, title),
        )

        title?.let {
            drawTextBaseline(canvas, it, pad, captionTop + captionHeight * 0.43f, titlePaint, textRight - pad)
        }
        if (detail.isNotBlank()) {
            val y = captionTop + captionHeight * if (title == null) 0.58f else 0.67f
            drawTextBaseline(canvas, detail, pad, y, bodyPaint, textRight - pad)
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = CanvasRenderUtils.visiblePaletteColors(assets, settings),
            area = RectF(pad, captionTop + captionHeight * 0.76f, textRight, captionTop + captionHeight * 0.98f),
            chipSize = captionHeight * 0.105f,
            gap = captionHeight * 0.048f,
            strokeColor = Color.argb(72, 0, 0, 0),
            alignment = PaletteChipAlignment.Start,
        )
    }

    private companion object {
        const val CAPTION_RATIO = 0.12f
    }
}

class BottomSpecBarRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.rgb(35, 38, 38)
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Dark
    override fun layoutSpec() = TemplateLayoutSpec(
        mode = TemplateLayoutMode.ExternalBottomCard,
        bottomInsetFraction = BAR_RATIO,
    )

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val w = bounds.width()
        val h = bounds.height()
        val top = h * (1f - BAR_RATIO)
        canvas.drawRect(0f, top, w, h, CanvasRenderUtils.paint(assets.cardBackgroundColor, 1f))
        val pad = w * 0.035f
        val primaryRows = info.toDisplayRows()
            .filter { it.label in setOf("BOARD", "Switch", "Keycap") }
            .take(3)
        val labelPaint = regular(scaled(h * 0.0105f, settings), assets.cardContentColor)
        val valuePaint = medium(scaled(h * 0.0165f, settings), assets.cardContentColor)
        val detailPaint = regular(scaled(h * 0.0138f, settings, settings.nicknameEmphasis), assets.cardContentColor).apply {
            textAlign = Paint.Align.RIGHT
        }

        if (primaryRows.isNotEmpty()) {
            val columnWidth = (w - pad * 2f) / primaryRows.size
            primaryRows.forEachIndexed { index, row ->
                val x = pad + columnWidth * index
                drawInfoRow(canvas, row, x, top + h * BAR_RATIO * 0.36f, columnWidth * 0.86f, labelPaint, valuePaint, h * BAR_RATIO * 0.29f)
            }
        }
        val detail = detailText(
            info.plate,
            info.mount,
            nicknameText(info, settings),
            separator = " / ",
        )
        if (detail.isNotBlank()) {
            canvas.drawText(TextDrawUtils.ellipsize(detail, detailPaint, w * 0.56f), w - pad, top + h * BAR_RATIO * 0.9f, detailPaint)
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = CanvasRenderUtils.visiblePaletteColors(assets, settings),
            area = RectF(w * 0.68f, top + h * BAR_RATIO * 0.08f, w - pad, top + h * BAR_RATIO * 0.32f),
            chipSize = h * BAR_RATIO * 0.12f,
            gap = h * BAR_RATIO * 0.055f,
            strokeColor = Color.argb(88, 255, 255, 255),
            alignment = PaletteChipAlignment.End,
            maxChips = if (primaryRows.size >= 3) 3 else settings.paletteColorCount,
        )
    }

    private companion object {
        const val BAR_RATIO = 0.10f
    }
}

class CornerMarkRenderer : TemplateRenderer {
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Dark

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val title = info.displayTitleOrNull()
        val subtitle = detailTextExcluding(title, info.housing, info.switchName, info.keycap)
        if (title == null && subtitle.isBlank() && !assets.hasLogo) return

        val w = bounds.width()
        val h = bounds.height()
        val pad = min(w, h) * 0.025f
        val titlePaint = medium(scaled(h * 0.017f, settings), assets.cardContentColor)
        val subPaint = regular(scaled(h * 0.011f, settings, settings.nicknameEmphasis), assets.cardContentColor)
        val cardScale = 1.15f
        val maxCardWidth = w * 0.48f
        val minCardWidth = if (title == null && subtitle.isBlank()) w * 0.092f else w * 0.195f
        val minCardHeight = h * 0.060f
        val maxCardHeight = h * 0.132f
        val horizontalPadding = min(w, h) * 0.014f * cardScale
        val verticalPadding = min(w, h) * 0.010f * cardScale
        val gap = min(w, h) * 0.012f
        val maxLogoWidth = maxCardWidth * 0.34f
        val maxLogoHeight = h * 0.046f
        val logoTarget = assets.logoBitmap?.takeIf { assets.hasLogo }?.let { bitmap ->
            LogoDrawUtils.fitInside(
                sourceWidth = bitmap.width.toFloat(),
                sourceHeight = bitmap.height.toFloat(),
                box = RectF(0f, 0f, maxLogoWidth, maxLogoHeight),
                anchor = LogoAnchor.Center,
            )
        }
        val logoWidth = logoTarget?.width() ?: if (assets.hasLogo) maxLogoHeight else 0f
        val logoHeight = logoTarget?.height() ?: if (assets.hasLogo) maxLogoHeight else 0f

        val titleWidth = title?.let { titlePaint.measureText(it) } ?: 0f
        val subtitleWidth = if (subtitle.isBlank()) 0f else subPaint.measureText(subtitle)
        val naturalTextWidth = max(titleWidth, subtitleWidth)
        val maxTextWidth = if (!assets.hasLogo) {
            maxCardWidth - horizontalPadding * 2f
        } else {
            maxCardWidth - horizontalPadding * 2f - logoWidth - gap
        }.coerceAtLeast(w * 0.08f)
        val textWidth = naturalTextWidth.coerceAtMost(maxTextWidth)
        val textBlockHeight = if (title == null && subtitle.isBlank()) {
            0f
        } else {
            (if (title != null) titlePaint.textSize else 0f) + if (subtitle.isNotBlank()) subPaint.textSize * 1.25f else 0f
        }
        val contentWidth = horizontalPadding * 2f + textWidth + if (assets.hasLogo && textWidth > 0f) logoWidth + gap else logoWidth
        val contentHeight = verticalPadding * 2f + max(logoHeight, textBlockHeight)
        val markWidth = contentWidth.coerceIn(minCardWidth, maxCardWidth)
        val markHeight = contentHeight.coerceIn(minCardHeight, maxCardHeight)
        val mark = RectF(w - pad - markWidth, pad, w - pad, pad + markHeight)

        drawRoundRect(canvas, mark, markHeight * 0.18f, Color.argb(150, 12, 13, 13))

        var textX = mark.left + horizontalPadding
        if (assets.hasLogo) {
            val logoBox = RectF(
                mark.left + horizontalPadding,
                mark.centerY() - maxLogoHeight / 2f,
                mark.left + horizontalPadding + maxLogoWidth,
                mark.centerY() + maxLogoHeight / 2f,
            )
            val logoActual = drawLogoIfPresent(
                canvas = canvas,
                rect = logoBox,
                assets = assets,
                textColor = Color.WHITE,
                anchor = LogoAnchor.Start,
                fitMode = LogoFitMode.Inside,
            )
            textX = (logoActual?.right ?: logoBox.left) + if (textWidth > 0f) gap else 0f
        }

        if (textWidth <= 0f) return
        val availableTextWidth = mark.right - textX - horizontalPadding
        if (subtitle.isBlank()) {
            val baseline = mark.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2f
            title?.let { drawTextBaseline(canvas, it, textX, baseline, titlePaint, availableTextWidth) }
        } else {
            val firstBaseline = mark.centerY() - (titlePaint.textSize + subPaint.textSize * 0.35f) / 2f + titlePaint.textSize * 0.86f
            title?.let { drawTextBaseline(canvas, it, textX, firstBaseline, titlePaint, availableTextWidth) }
            drawTextBaseline(canvas, subtitle, textX, firstBaseline + subPaint.textSize * 1.2f, subPaint, availableTextWidth)
        }
    }
}

class PosterMarginRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.rgb(248, 248, 245)
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Light
    override fun layoutSpec() = TemplateLayoutSpec(
        mode = TemplateLayoutMode.ExternalFrame,
        leftInsetFraction = 0.035f,
        topInsetFraction = 0.035f,
        rightInsetFraction = 0.035f,
        bottomInsetFraction = 0.17f,
    )

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val w = bounds.width()
        val h = bounds.height()
        val footerTop = h * 0.845f
        val pad = w * 0.055f
        val logoWidth = min(w * 0.14f, h * 0.105f)
        val logoBox = RectF(w - pad - logoWidth, footerTop + h * 0.025f, w - pad, footerTop + h * 0.09f)
        val contentColor = assets.cardContentColor
        val secondaryColor = contentColor
        val titlePaint = medium(scaled(h * 0.028f, settings), contentColor)
        val specPaint = regular(scaled(h * 0.015f, settings), secondaryColor)
        val signaturePaint = medium(scaled(h * 0.014f, settings, settings.nicknameEmphasis), secondaryColor).apply {
            textAlign = Paint.Align.RIGHT
        }
        val logoActual = drawLogoIfPresent(canvas, logoBox, assets, contentColor, LogoAnchor.End)
        val textRight = (logoActual?.left?.minus(pad * 1.5f)) ?: (w - pad)
        val title = info.displayTitleOrNull()
        val details = detailTextExcluding(title, info.housing, info.switchName, info.keycap)
        val signature = nicknameDetail(info, settings, title)

        title?.let {
            drawTextBaseline(canvas, it, pad, footerTop + h * 0.052f, titlePaint, textRight - pad)
        }
        if (details.isNotBlank()) {
            val y = footerTop + h * if (title == null) 0.062f else 0.086f
            drawTextBaseline(canvas, details, pad, y, specPaint, textRight - pad)
        }
        if (!signature.isNullOrBlank()) {
            canvas.drawText(TextDrawUtils.ellipsize(signature, signaturePaint, w * 0.38f), w - pad, h - h * 0.025f, signaturePaint)
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = CanvasRenderUtils.visiblePaletteColors(assets, settings),
            area = RectF(pad, h - h * 0.062f, textRight, h - h * 0.028f),
            chipSize = h * 0.014f,
            gap = h * 0.006f,
            strokeColor = Color.argb(70, 0, 0, 0),
            alignment = PaletteChipAlignment.Start,
        )
    }
}

class LiquidGlassFrameRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.rgb(226, 229, 228)
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Mixed

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val w = bounds.width()
        val h = bounds.height()
        val unit = min(w, h)
        val sideWidth = unit * 0.058f
        val topWidth = unit * 0.056f
        val bottomHeight = max(h * 0.145f, unit * 0.12f)
        val outer = RectF(0f, 0f, w, h)
        val inner = RectF(
            outer.left + sideWidth,
            outer.top + topWidth,
            outer.right - sideWidth,
            outer.bottom - bottomHeight,
        )
        val outerRadius = 0f
        val innerRadius = unit * 0.025f
        val contentColor = if (assets.hasExplicitTextColor) assets.cardContentColor else Color.WHITE
        val scrimBase = if (CanvasRenderUtils.relativeLuminance(contentColor) >= 0.18) Color.BLACK else Color.WHITE
        val ring = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRoundRect(outer, outerRadius, outerRadius, Path.Direction.CW)
            addRoundRect(inner, innerRadius, innerRadius, Path.Direction.CW)
        }

        val saveCount = canvas.save()
        canvas.clipPath(ring)
        assets.sourcePhotoBitmap?.let { source ->
            val shaderRendered = LiquidGlassShaderRenderer.drawIfSupported(
                canvas = canvas,
                source = source,
                sourceCacheKey = assets.sourceCacheKey,
                bounds = bounds,
                inner = inner,
                innerRadius = innerRadius,
            )
            if (!shaderRendered) {
                val cachedLayer = LiquidGlassFallbackLayerCache.getOrCreate(
                    sourceCacheKey = assets.sourceCacheKey,
                    outputWidth = w.toInt(),
                    outputHeight = h.toInt(),
                    inner = inner,
                    innerRadius = innerRadius,
                ) {
                    createLiquidGlassLayer(
                        source = source,
                        outputWidth = w.toInt(),
                        outputHeight = h.toInt(),
                        inner = inner,
                        innerRadius = innerRadius,
                    )
                }
                try {
                    canvas.drawBitmap(
                        cachedLayer.bitmap,
                        null,
                        bounds,
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                            alpha = 220
                        },
                    )
                } finally {
                    if (cachedLayer.ownedByCaller) cachedLayer.bitmap.recycle()
                }
            }
        }
        if (assets.hasExplicitCardBackgroundColor) {
            val tint = assets.cardBackgroundColor
            canvas.drawRect(
                bounds,
                CanvasRenderUtils.paint(
                    Color.argb(30, Color.red(tint), Color.green(tint), Color.blue(tint)),
                    1f,
                ),
            )
        }
        val sheen = LinearGradient(
            outer.left,
            outer.top,
            outer.right,
            outer.bottom,
            intArrayOf(
                Color.argb(20, 255, 255, 255),
                Color.TRANSPARENT,
                Color.argb(12, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(bounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sheen })
        canvas.restoreToCount(saveCount)
        val contentLeft = inner.left + unit * 0.015f
        val contentTop = inner.bottom + bottomHeight * 0.20f
        val contentRight = inner.right - unit * 0.015f
        val logoWidth = min(w * 0.16f, bottomHeight * 1.12f)
        val logoBox = RectF(
            contentRight - logoWidth,
            contentTop,
            contentRight,
            outer.bottom - bottomHeight * 0.18f,
        )
        val logoColor = if (scrimBase == Color.BLACK) Color.WHITE else Color.rgb(18, 19, 18)
        val logoActual = drawLogoIfPresent(canvas, logoBox, assets.withLogoContrast(logoColor), logoColor, LogoAnchor.End, LogoFitMode.Inside)
        val textRight = (logoActual?.left?.minus(unit * 0.022f)) ?: contentRight
        val title = info.displayTitleOrNull()
        val details = detailTextExcluding(title, info.housing, info.switchName, info.plate, info.mount, info.keycap)
        val titlePaint = medium(scaled(h * 0.025f, settings), contentColor)
        val detailPaint = regular(scaled(h * 0.0135f, settings), contentColor)
        title?.let {
            drawLiquidGlassText(
                canvas = canvas,
                text = it,
                x = contentLeft,
                baseline = contentTop + titlePaint.textSize,
                paint = titlePaint,
                maxWidth = textRight - contentLeft,
                contrastColor = scrimBase,
                unit = unit,
                isDetail = false,
            )
        }
        if (details.isNotBlank()) {
            val detailY = contentTop + if (title == null) detailPaint.textSize * 1.15f else titlePaint.textSize * 1.78f
            drawLiquidGlassText(
                canvas = canvas,
                text = details,
                x = contentLeft,
                baseline = detailY,
                paint = detailPaint,
                maxWidth = textRight - contentLeft,
                contrastColor = scrimBase,
                unit = unit,
                isDetail = true,
            )
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = CanvasRenderUtils.visiblePaletteColors(assets, settings),
            area = RectF(contentLeft, outer.bottom - bottomHeight * 0.30f, textRight, outer.bottom - bottomHeight * 0.10f),
            chipSize = bottomHeight * 0.095f,
            gap = bottomHeight * 0.048f,
            strokeColor = Color.argb(84, Color.red(contentColor), Color.green(contentColor), Color.blue(contentColor)),
            alignment = PaletteChipAlignment.Start,
        )
    }
}

private object LiquidGlassFallbackLayerCache {
    private const val MAX_CACHE_PIXELS = 6_000_000L
    private const val MAX_CACHE_ENTRIES = 8
    private val lock = Any()
    private val cache = java.util.LinkedHashMap<String, Bitmap>(12, 0.75f, true)
    private var cachedPixels = 0L

    data class Result(val bitmap: Bitmap, val ownedByCaller: Boolean)

    fun getOrCreate(
        sourceCacheKey: String?,
        outputWidth: Int,
        outputHeight: Int,
        inner: RectF,
        innerRadius: Float,
        create: () -> Bitmap,
    ): Result {
        val key = sourceCacheKey?.let {
            listOf(
                it,
                outputWidth,
                outputHeight,
                inner.left.toInt(),
                inner.top.toInt(),
                inner.right.toInt(),
                inner.bottom.toInt(),
                innerRadius.toInt(),
            ).joinToString("|")
        } ?: return Result(create(), ownedByCaller = true)
        synchronized(lock) {
            cache[key]?.takeUnless(Bitmap::isRecycled)?.let {
                return Result(it, ownedByCaller = false)
            }
        }
        val created = create()
        synchronized(lock) {
            cache[key]?.takeUnless(Bitmap::isRecycled)?.let {
                created.recycle()
                return Result(it, ownedByCaller = false)
            }
            cache[key] = created
            cachedPixels += created.width.toLong() * created.height
            while (cache.size > MAX_CACHE_ENTRIES || cachedPixels > MAX_CACHE_PIXELS) {
                val eldest = cache.entries.iterator().next()
                cache.remove(eldest.key)
                cachedPixels -= eldest.value.width.toLong() * eldest.value.height
            }
        }
        return Result(created, ownedByCaller = false)
    }
}

private fun drawLiquidGlassText(
    canvas: Canvas,
    text: String,
    x: Float,
    baseline: Float,
    paint: Paint,
    maxWidth: Float,
    contrastColor: Int,
    unit: Float,
    isDetail: Boolean,
) {
    val safeText = CanvasRenderUtils.ellipsize(text, paint, maxWidth)
    if (safeText.isBlank()) return

    val outline = Paint(paint).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, unit * if (isDetail) 0.0009f else 0.00075f)
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(
            if (isDetail) 118 else 98,
            Color.red(contrastColor),
            Color.green(contrastColor),
            Color.blue(contrastColor),
        )
        clearShadowLayer()
    }
    canvas.drawText(safeText, x, baseline, outline)

    val shadowOffset = unit * if (contrastColor == Color.BLACK) 0.0011f else -0.0007f
    val fill = Paint(paint).apply {
        style = Paint.Style.FILL
        setShadowLayer(
            unit * if (isDetail) 0.0032f else 0.0042f,
            0f,
            shadowOffset,
            Color.argb(
                if (isDetail) 158 else 142,
                Color.red(contrastColor),
                Color.green(contrastColor),
                Color.blue(contrastColor),
            ),
        )
    }
    canvas.drawText(safeText, x, baseline, fill)
}

private fun createLiquidGlassLayer(
    source: Bitmap,
    outputWidth: Int,
    outputHeight: Int,
    inner: RectF,
    innerRadius: Float,
): Bitmap {
    val sampleScale = min(1f, 1200f / max(outputWidth, outputHeight).coerceAtLeast(1))
    val sampleWidth = max(1, (outputWidth * sampleScale).toInt())
    val sampleHeight = max(1, (outputHeight * sampleScale).toInt())
    val sampled = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
    Canvas(sampled).drawBitmap(
        source,
        null,
        RectF(0f, 0f, sampleWidth.toFloat(), sampleHeight.toFloat()),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
    )

    val pixels = IntArray(sampleWidth * sampleHeight)
    sampled.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
    val radius = (min(sampleWidth, sampleHeight) / 90).coerceIn(3, 16)
    val horizontal = IntArray(pixels.size)
    val blurred = IntArray(pixels.size)
    var blurSource = pixels
    repeat(3) {
        blurRows(blurSource, horizontal, sampleWidth, sampleHeight, radius)
        blurColumns(horizontal, blurred, sampleWidth, sampleHeight, radius)
        blurSource = blurred
    }
    val glass = IntArray(pixels.size)
    val scaleX = sampleWidth.toFloat() / outputWidth.coerceAtLeast(1)
    val scaleY = sampleHeight.toFloat() / outputHeight.coerceAtLeast(1)
    val left = inner.left * scaleX
    val top = inner.top * scaleY
    val right = inner.right * scaleX
    val bottom = inner.bottom * scaleY
    val roundedRadius = innerRadius * min(scaleX, scaleY)
    val centerX = (left + right) * 0.5f
    val centerY = (top + bottom) * 0.5f
    val halfX = max(0f, (right - left) * 0.5f - roundedRadius)
    val halfY = max(0f, (bottom - top) * 0.5f - roundedRadius)
    val edgeBand = (min(sampleWidth, sampleHeight) * 0.026f).coerceAtLeast(5f)
    val maxShift = (min(sampleWidth, sampleHeight) * 0.024f).coerceAtLeast(4f)
    val chromaShift = (min(sampleWidth, sampleHeight) * 0.0012f).coerceIn(0.45f, 1.2f)

    for (y in 0 until sampleHeight) {
        for (x in 0 until sampleWidth) {
            val index = y * sampleWidth + x
            val base = blendColors(blurred[index], pixels[index], 0.50f)
            val dx = x - centerX
            val dy = y - centerY
            val qx = kotlin.math.abs(dx) - halfX
            val qy = kotlin.math.abs(dy) - halfY
            val outsideX = max(qx, 0f)
            val outsideY = max(qy, 0f)
            val signedDistance = kotlin.math.sqrt(outsideX * outsideX + outsideY * outsideY) + min(max(qx, qy), 0f) - roundedRadius
            if (signedDistance < 0f || signedDistance > edgeBand) {
                glass[index] = base
                continue
            }

            val signX = if (dx < 0f) -1f else 1f
            val signY = if (dy < 0f) -1f else 1f
            val normalX: Float
            val normalY: Float
            if (outsideX > 0f && outsideY > 0f) {
                val length = kotlin.math.sqrt(outsideX * outsideX + outsideY * outsideY).coerceAtLeast(0.0001f)
                normalX = outsideX / length * signX
                normalY = outsideY / length * signY
            } else if (qx > qy) {
                normalX = signX
                normalY = 0f
            } else {
                normalX = 0f
                normalY = signY
            }
            val t = (1f - signedDistance / edgeBand).coerceIn(0f, 1f)
            val edgeWeight = t * t * (3f - 2f * t)
            val shift = maxShift * edgeWeight
            val sourceX = x - normalX * shift
            val sourceY = y - normalY * shift
            val center = sampleBilinear(pixels, sampleWidth, sampleHeight, sourceX, sourceY)
            val redSample = sampleBilinear(
                pixels,
                sampleWidth,
                sampleHeight,
                sourceX - normalX * chromaShift,
                sourceY - normalY * chromaShift,
            )
            val blueSample = sampleBilinear(
                pixels,
                sampleWidth,
                sampleHeight,
                sourceX + normalX * chromaShift,
                sourceY + normalY * chromaShift,
            )
            val refracted = Color.argb(
                Color.alpha(center),
                Color.red(redSample),
                Color.green(center),
                Color.blue(blueSample),
            )
            val detailDifference = (
                kotlin.math.abs(Color.red(pixels[index]) - Color.red(blurred[index])) +
                    kotlin.math.abs(Color.green(pixels[index]) - Color.green(blurred[index])) +
                    kotlin.math.abs(Color.blue(pixels[index]) - Color.blue(blurred[index]))
                ) / (255f * 3f)
            val flatBoost = (1f - ((detailDifference - 0.025f) / 0.135f)).coerceIn(0f, 1f)
            val facingLight = max(normalX * 0.58f + normalY * 0.82f, 0f)
            val facingShadow = max(normalX * -0.58f + normalY * -0.82f, 0f)
            val edgePosition = (signedDistance / edgeBand).coerceIn(0f, 1f)
            val refractionCaustic = smoothStep(0.03f, 0.20f, edgePosition) *
                (1f - smoothStep(0.28f, 0.64f, edgePosition))
            val highlightWidth = (1f / edgeBand.coerceAtLeast(1f)).coerceIn(0.012f, 0.10f)
            val highlightDistance = (edgePosition - 0.145f) / highlightWidth
            val highlightCaustic = kotlin.math.exp(
                (-highlightDistance * highlightDistance).toDouble(),
            ).toFloat()
            val causticStrength = refractionCaustic *
                (0.010f + facingLight * facingLight * 0.070f) *
                (0.72f + flatBoost * 0.28f)
            val lightAmount =
                highlightCaustic * facingLight * (0.152f + flatBoost * 0.138f) -
                    edgeWeight * facingShadow * (0.060f + flatBoost * 0.055f)
            val litColor = offsetColor(
                blendColors(base, refracted, 0.90f * edgeWeight),
                (lightAmount * 255f).toInt(),
            )
            val causticTint = blendColors(Color.WHITE, blurred[index], 0.16f)
            glass[index] = blendColors(litColor, causticTint, causticStrength)
        }
    }

    sampled.setPixels(glass, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
    return sampled
}

private fun sampleBilinear(source: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
    val safeX = x.coerceIn(0f, (width - 1).toFloat())
    val safeY = y.coerceIn(0f, (height - 1).toFloat())
    val x0 = kotlin.math.floor(safeX).toInt()
    val y0 = kotlin.math.floor(safeY).toInt()
    val x1 = min(x0 + 1, width - 1)
    val y1 = min(y0 + 1, height - 1)
    val tx = safeX - x0
    val ty = safeY - y0
    val topLeft = source[y0 * width + x0]
    val topRight = source[y0 * width + x1]
    val bottomLeft = source[y1 * width + x0]
    val bottomRight = source[y1 * width + x1]

    fun channel(selector: (Int) -> Int): Int {
        val top = selector(topLeft) + (selector(topRight) - selector(topLeft)) * tx
        val bottom = selector(bottomLeft) + (selector(bottomRight) - selector(bottomLeft)) * tx
        return (top + (bottom - top) * ty).toInt().coerceIn(0, 255)
    }

    return Color.argb(
        channel(Color::alpha),
        channel(Color::red),
        channel(Color::green),
        channel(Color::blue),
    )
}

private fun blendColors(background: Int, foreground: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    val inverse = 1f - t
    return Color.argb(
        (Color.alpha(background) * inverse + Color.alpha(foreground) * t).toInt(),
        (Color.red(background) * inverse + Color.red(foreground) * t).toInt(),
        (Color.green(background) * inverse + Color.green(foreground) * t).toInt(),
        (Color.blue(background) * inverse + Color.blue(foreground) * t).toInt(),
    )
}

private fun offsetColor(color: Int, offset: Int): Int = Color.argb(
    Color.alpha(color),
    (Color.red(color) + offset).coerceIn(0, 255),
    (Color.green(color) + offset).coerceIn(0, 255),
    (Color.blue(color) + offset).coerceIn(0, 255),
)

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun blurRows(source: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
    val prefixA = IntArray(width + 1)
    val prefixR = IntArray(width + 1)
    val prefixG = IntArray(width + 1)
    val prefixB = IntArray(width + 1)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            val color = source[row + x]
            prefixA[x + 1] = prefixA[x] + Color.alpha(color)
            prefixR[x + 1] = prefixR[x] + Color.red(color)
            prefixG[x + 1] = prefixG[x] + Color.green(color)
            prefixB[x + 1] = prefixB[x] + Color.blue(color)
        }
        for (x in 0 until width) {
            val left = max(0, x - radius)
            val right = min(width - 1, x + radius)
            val count = right - left + 1
            output[row + x] = Color.argb(
                (prefixA[right + 1] - prefixA[left]) / count,
                (prefixR[right + 1] - prefixR[left]) / count,
                (prefixG[right + 1] - prefixG[left]) / count,
                (prefixB[right + 1] - prefixB[left]) / count,
            )
        }
    }
}

private fun blurColumns(source: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
    val prefixA = IntArray(height + 1)
    val prefixR = IntArray(height + 1)
    val prefixG = IntArray(height + 1)
    val prefixB = IntArray(height + 1)
    for (x in 0 until width) {
        for (y in 0 until height) {
            val color = source[y * width + x]
            prefixA[y + 1] = prefixA[y] + Color.alpha(color)
            prefixR[y + 1] = prefixR[y] + Color.red(color)
            prefixG[y + 1] = prefixG[y] + Color.green(color)
            prefixB[y + 1] = prefixB[y] + Color.blue(color)
        }
        for (y in 0 until height) {
            val top = max(0, y - radius)
            val bottom = min(height - 1, y + radius)
            val count = bottom - top + 1
            output[y * width + x] = Color.argb(
                (prefixA[bottom + 1] - prefixA[top]) / count,
                (prefixR[bottom + 1] - prefixR[top]) / count,
                (prefixG[bottom + 1] - prefixG[top]) / count,
                (prefixB[bottom + 1] - prefixB[top]) / count,
            )
        }
    }
}

class DarkGlassStripRenderer : TemplateRenderer {
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Dark

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val rows = info.toDisplayRows()
            .filter { it.label in setOf("BOARD", "Switch", "Keycap") }
            .take(3)
        val colors = CanvasRenderUtils.visiblePaletteColors(assets, settings)
        if (rows.isEmpty() && !assets.hasLogo && colors.isEmpty()) return

        val w = bounds.width()
        val h = bounds.height()
        val stripHeight = h * 0.115f
        val strip = RectF(0f, h - stripHeight, w, h)
        canvas.drawRect(strip, CanvasRenderUtils.paint(Color.argb(184, 8, 10, 11), 1f))
        val pad = w * 0.025f
        val logoWidth = min(w * 0.105f, stripHeight * 1.15f)
        val logoBox = RectF(pad, strip.top + stripHeight * 0.22f, pad + logoWidth, strip.bottom - stripHeight * 0.22f)
        val logoActual = drawLogoIfPresent(canvas, logoBox, assets, Color.WHITE, LogoAnchor.Start)
        val startX = (logoActual?.right ?: strip.left) + pad
        val rowRight = if (colors.isNotEmpty()) w * 0.68f else w - pad
        val labelPaint = regular(scaled(h * 0.0105f, settings), assets.cardContentColor)
        val valuePaint = medium(scaled(h * 0.0168f, settings), assets.cardContentColor)
        if (rows.isNotEmpty()) {
            val columnWidth = ((rowRight - startX).coerceAtLeast(w * 0.25f)) / rows.size
            rows.forEachIndexed { index, row ->
                val x = startX + index * columnWidth
                drawInfoRow(canvas, row, x, strip.top + stripHeight * 0.43f, columnWidth * 0.88f, labelPaint, valuePaint, stripHeight * 0.27f)
            }
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = colors,
            area = RectF(w * 0.70f, strip.top + stripHeight * 0.36f, w - pad, strip.bottom - stripHeight * 0.25f),
            chipSize = stripHeight * 0.105f,
            gap = stripHeight * 0.05f,
            strokeColor = Color.argb(88, 255, 255, 255),
            alignment = PaletteChipAlignment.End,
            maxChips = if (rows.size >= 3) 3 else settings.paletteColorCount,
        )
    }
}

class SideSpecRailRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.rgb(243, 244, 241)
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Light
    override fun layoutSpec() = TemplateLayoutSpec(
        mode = TemplateLayoutMode.ExternalSideCard,
        rightInsetFraction = RAIL_RATIO,
    )

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val w = bounds.width()
        val h = bounds.height()
        val railLeft = w * (1f - RAIL_RATIO)
        val rail = RectF(railLeft, 0f, w, h)
        canvas.drawRect(rail, CanvasRenderUtils.paint(assets.cardBackgroundColor, 1f))
        canvas.drawRect(rail.left, 0f, rail.left + 2f, h, CanvasRenderUtils.paint(Color.argb(80, Color.red(assets.cardContentColor), Color.green(assets.cardContentColor), Color.blue(assets.cardContentColor)), 1f))
        val pad = w * 0.028f
        val maxLogoWidth = rail.width() * 0.72f
        val maxLogoHeight = h * 0.105f
        val logoBox = RectF(
            rail.left + (rail.width() - maxLogoWidth) / 2f,
            h * 0.055f,
            rail.left + (rail.width() + maxLogoWidth) / 2f,
            h * 0.055f + maxLogoHeight,
        )
        val logoActual = drawLogoIfPresent(
            canvas = canvas,
            rect = logoBox,
            assets = assets,
            textColor = assets.cardContentColor,
            anchor = LogoAnchor.Center,
            fitMode = LogoFitMode.Inside,
        )
        val title = info.displayTitleOrNull()
        val rows = rowsExcludingTitle(info.toDisplayRows(includeNickname = true), title).take(5)
        val titlePaint = medium(scaled(h * 0.024f, settings), assets.cardContentColor)
        val labelPaint = regular(scaled(h * 0.0115f, settings), assets.cardContentColor)
        val valuePaint = medium(scaled(h * 0.0155f, settings), assets.cardContentColor)
        var cursorY = (logoActual?.bottom ?: rail.top) + if (assets.hasLogo) h * 0.06f else h * 0.075f
        title?.let {
            drawTextBaseline(canvas, it, rail.left + pad, cursorY, titlePaint, rail.width() - pad * 2f)
            cursorY += h * 0.075f
        }
        rows.forEach { row ->
            val valueLines = wrapTextAtWords(row.value, valuePaint, rail.width() - pad * 2f, maxLines = 2)
            drawTextBaseline(canvas, row.label.uppercase(Locale.ROOT), rail.left + pad, cursorY, labelPaint, rail.width() - pad * 2f)
            valueLines.forEachIndexed { index, line ->
                drawTextBaseline(canvas, line, rail.left + pad, cursorY + h * (0.028f + index * 0.024f), valuePaint, rail.width() - pad * 2f)
            }
            cursorY += h * if (valueLines.size > 1) 0.115f else 0.095f
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = CanvasRenderUtils.visiblePaletteColors(assets, settings),
            area = RectF(rail.left + pad, max(cursorY, h * 0.18f), rail.right - pad, min(h * 0.93f, max(cursorY, h * 0.18f) + h * 0.04f)),
            chipSize = h * 0.014f,
            gap = h * 0.006f,
            strokeColor = Color.argb(70, 0, 0, 0),
            alignment = PaletteChipAlignment.Start,
            maxChips = 3,
        )
    }

    private companion object {
        const val RAIL_RATIO = 0.18f
    }
}

class TopNameplateRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.rgb(247, 247, 243)
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Light
    override fun layoutSpec() = TemplateLayoutSpec(
        mode = TemplateLayoutMode.ExternalFrame,
        topInsetFraction = HEADER_RATIO,
    )

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val w = bounds.width()
        val h = bounds.height()
        val header = RectF(0f, 0f, w, h * HEADER_RATIO)
        canvas.drawRect(header, CanvasRenderUtils.paint(assets.cardBackgroundColor, 1f))
        val pad = w * 0.035f
        val logoWidth = min(w * 0.11f, header.height() * 0.58f)
        val logoBox = RectF(pad, header.centerY() - logoWidth / 2f, pad + logoWidth, header.centerY() + logoWidth / 2f)
        val logoActual = drawLogoIfPresent(canvas, logoBox, assets, assets.cardContentColor, LogoAnchor.Start)
        val titlePaint = medium(scaled(h * 0.025f, settings), assets.cardContentColor)
        val bodyPaint = regular(scaled(h * 0.0145f, settings), assets.cardContentColor)
        val textX = (logoActual?.right ?: header.left) + pad * if (logoActual == null) 1f else 0.78f
        val right = w - pad
        val title = info.displayTitleOrNull()
        val detail = detailTextExcluding(
            title,
            info.housing,
            info.switchName,
            info.keycap,
            nicknameDetail(info, settings, title),
        )
        title?.let {
            drawTextBaseline(canvas, it, textX, header.top + header.height() * 0.42f, titlePaint, right - textX)
        }
        if (detail.isNotBlank()) {
            drawTextBaseline(canvas, detail, textX, header.top + header.height() * if (title == null) 0.55f else 0.67f, bodyPaint, right - textX)
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = CanvasRenderUtils.visiblePaletteColors(assets, settings),
            area = RectF(w * 0.68f, header.top + header.height() * 0.68f, right, header.bottom - 5f),
            chipSize = header.height() * 0.09f,
            gap = header.height() * 0.04f,
            strokeColor = Color.argb(70, 0, 0, 0),
            alignment = PaletteChipAlignment.End,
            maxChips = if (detail.isBlank()) settings.paletteColorCount else 3,
        )
        canvas.drawRect(0f, header.bottom - 2f, w, header.bottom, CanvasRenderUtils.paint(Color.argb(80, Color.red(assets.cardContentColor), Color.green(assets.cardContentColor), Color.blue(assets.cardContentColor)), 1f))
    }

    private companion object {
        const val HEADER_RATIO = 0.12f
    }
}

class MuseumMatRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.rgb(246, 245, 239)
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Light
    override fun layoutSpec() = TemplateLayoutSpec(
        mode = TemplateLayoutMode.ExternalFrame,
        leftInsetFraction = 0.055f,
        topInsetFraction = 0.055f,
        rightInsetFraction = 0.055f,
        bottomInsetFraction = 0.23f,
    )

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val w = bounds.width()
        val h = bounds.height()
        val photo = photoBounds(bounds)
        val labelTop = photo.bottom + h * 0.045f
        val pad = w * 0.06f
        val logoSize = min(w * 0.12f, h * 0.08f)
        val logoBox = RectF(w - pad - logoSize, labelTop, w - pad, labelTop + logoSize)
        val logoActual = drawLogoIfPresent(canvas, logoBox, assets, assets.cardContentColor, LogoAnchor.End)
        val titlePaint = medium(scaled(h * 0.026f, settings), assets.cardContentColor)
        val bodyPaint = regular(scaled(h * 0.015f, settings), assets.cardContentColor)
        val labelPaint = regular(scaled(h * 0.012f, settings), assets.cardContentColor)
        val textRight = (logoActual?.left?.minus(pad * 1.6f)) ?: (w - pad)
        val title = info.displayTitleOrNull()
        val details = detailTextExcluding(
            title,
            info.housing,
            info.switchName,
            info.plate,
            info.mount,
            info.keycap,
            nicknameDetail(info, settings, title),
        )
        if (title != null || details.isNotBlank()) {
            drawTextBaseline(canvas, "BUILD INFO", pad, labelTop + h * 0.006f, labelPaint, w * 0.45f)
        }
        title?.let {
            drawTextBaseline(canvas, it, pad, labelTop + h * 0.05f, titlePaint, textRight - pad)
        }
        if (details.isNotBlank()) {
            val y = labelTop + h * if (title == null) 0.06f else 0.09f
            drawTextBaseline(canvas, details, pad, y, bodyPaint, textRight - pad)
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = CanvasRenderUtils.visiblePaletteColors(assets, settings),
            area = RectF(pad, labelTop + h * 0.102f, textRight, labelTop + h * 0.135f),
            chipSize = h * 0.014f,
            gap = h * 0.006f,
            strokeColor = Color.argb(70, 0, 0, 0),
            alignment = PaletteChipAlignment.Start,
        )
    }
}

class CompactTicketRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.rgb(235, 236, 232)
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Light
    override fun layoutSpec() = TemplateLayoutSpec(
        mode = TemplateLayoutMode.ExternalBottomCard,
        bottomInsetFraction = TICKET_RATIO,
    )

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val title = info.displayTitleOrNull()
        val detail = detailTextExcluding(
            title,
            info.housing,
            info.switchName,
            info.keycap,
            nicknameDetail(info, settings, title),
        )
        val colors = CanvasRenderUtils.visiblePaletteColors(assets, settings)
        if (title == null && detail.isBlank() && !assets.hasLogo && colors.isEmpty()) return

        val w = bounds.width()
        val h = bounds.height()
        val ticketTop = h * (1f - TICKET_RATIO)
        val pad = w * 0.035f
        val ticket = RectF(pad, ticketTop + h * 0.018f, w - pad, h - h * 0.018f)
        drawRoundRect(canvas, ticket, ticket.height() * 0.18f, assets.cardBackgroundColor)
        val logoSize = min(ticket.height() * 0.58f, w * 0.1f)
        val logoBox = RectF(ticket.left + ticket.height() * 0.2f, ticket.centerY() - logoSize / 2f, ticket.left + ticket.height() * 0.2f + logoSize, ticket.centerY() + logoSize / 2f)
        val logoActual = drawLogoIfPresent(canvas, logoBox, assets, assets.cardContentColor, LogoAnchor.Start)
        val titlePaint = medium(scaled(h * 0.0195f, settings), assets.cardContentColor)
        val bodyPaint = regular(scaled(h * 0.013f, settings), assets.cardContentColor)
        val textX = (logoActual?.right ?: ticket.left) + ticket.height() * if (logoActual == null) 0.2f else 0.2f
        val paletteLeft = ticket.right - ticket.width() * 0.24f
        val right = if (colors.isEmpty()) {
            ticket.right - ticket.height() * 0.2f
        } else {
            paletteLeft - pad * 0.5f
        }
        title?.let {
            drawTextBaseline(canvas, it, textX, ticket.top + ticket.height() * 0.42f, titlePaint, right - textX)
        }
        if (detail.isNotBlank()) {
            drawTextBaseline(canvas, detail, textX, ticket.top + ticket.height() * if (title == null) 0.56f else 0.7f, bodyPaint, right - textX)
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = colors,
            area = RectF(paletteLeft, ticket.top + ticket.height() * 0.30f, ticket.right - ticket.height() * 0.18f, ticket.bottom - ticket.height() * 0.30f),
            chipSize = ticket.height() * 0.13f,
            gap = ticket.height() * 0.055f,
            strokeColor = Color.argb(68, 0, 0, 0),
            alignment = PaletteChipAlignment.End,
            maxChips = 3,
        )
    }

    private companion object {
        const val TICKET_RATIO = 0.13f
    }
}

class CleanSignatureRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.rgb(250, 250, 247)
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Light
    override fun layoutSpec() = TemplateLayoutSpec(
        mode = TemplateLayoutMode.ExternalBottomCard,
        bottomInsetFraction = 0.155f,
    )

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val lines = listOfNotNull(
            info.housing.meaningfulBuildTextOrNull(),
            info.keycap.meaningfulBuildTextOrNull(),
            nicknameText(info, settings).meaningfulBuildTextOrNull(),
        )
        val colors = CanvasRenderUtils.visiblePaletteColors(assets, settings)
        if (lines.isEmpty() && !assets.hasLogo && colors.isEmpty()) return

        val w = bounds.width()
        val h = bounds.height()
        val footerTop = h * 0.845f
        val pad = w * 0.052f
        val logoSize = min(w * 0.09f, h * 0.07f)
        val logoBox = RectF(w - pad - logoSize, footerTop + h * 0.035f, w - pad, footerTop + h * 0.035f + logoSize)
        val logoActual = drawLogoIfPresent(canvas, logoBox, assets, assets.cardContentColor, LogoAnchor.End)
        val titlePaint = medium(scaled(h * 0.026f, settings), assets.cardContentColor)
        val bodyPaint = regular(scaled(h * 0.0145f, settings), assets.cardContentColor)
        val nickPaint = medium(scaled(h * 0.019f, settings, settings.nicknameEmphasis), assets.cardContentColor)
        val textRight = (logoActual?.left?.minus(pad))
            ?: if (colors.isNotEmpty()) logoBox.left - pad else w - pad
        val baselines = listOf(footerTop + h * 0.050f, footerTop + h * 0.086f, footerTop + h * 0.124f)
        lines.take(3).forEachIndexed { index, value ->
            val paint = when (index) {
                0 -> titlePaint
                1 -> bodyPaint
                else -> nickPaint
            }
            drawTextBaseline(canvas, value, pad, baselines[index], paint, textRight - pad)
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = colors,
            area = RectF(
                logoBox.left,
                (logoActual?.bottom ?: logoBox.bottom) + h * 0.010f,
                logoBox.right,
                footerTop + h * 0.148f,
            ),
            chipSize = h * 0.014f,
            gap = h * 0.006f,
            strokeColor = Color.argb(70, 0, 0, 0),
            alignment = PaletteChipAlignment.Center,
            maxChips = if (lines.size >= 3) 3 else settings.paletteColorCount,
        )
    }
}

class EditorialCoverRenderer : TemplateRenderer {
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Mixed
    override fun photoPlacement(): PhotoPlacement = PhotoPlacement.CenterCrop

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val w = bounds.width()
        val h = bounds.height()
        val pad = min(w, h) * 0.038f
        val title = info.displayTitleOrNull()
        val mainLine = info.housing.meaningfulBuildTextOrNull() ?: title
        val switchLine = info.switchName.meaningfulBuildTextOrNull()
        val keycapLine = info.keycap.meaningfulBuildTextOrNull()
        val specLine = detailText(info.plate, info.mount, separator = "  /  ")
        val signature = nicknameDetail(info, settings, title)
        if (title == null && mainLine == null && switchLine == null && keycapLine == null && signature.isNullOrBlank() && !assets.hasLogo) return
        val isLandscape = w > h * 1.12f

        CanvasRenderUtils.drawGradientScrim(
            canvas = canvas,
            rect = RectF(0f, 0f, w, h * if (isLandscape) 0.30f else 0.36f),
            startColor = Color.argb(if (isLandscape) 96 else 122, 0, 0, 0),
            endColor = Color.TRANSPARENT,
        )
        CanvasRenderUtils.drawGradientScrim(
            canvas = canvas,
            rect = RectF(0f, h * if (isLandscape) 0.48f else 0.52f, w, h),
            startColor = Color.TRANSPARENT,
            endColor = Color.argb(150, 0, 0, 0),
        )

        val logoWidth = if (isLandscape) w * 0.155f else w * 0.20f
        val logoTop = if (isLandscape) pad else h * 0.065f
        val logoBox = RectF(w - pad - logoWidth, logoTop, w - pad, logoTop + h * if (isLandscape) 0.095f else 0.082f)
        val logoTone = contrastColorAt(assets, logoBox.centerX(), logoBox.centerY())
        drawLogoIfPresent(canvas, logoBox, assets.withLogoContrast(logoTone), logoTone, LogoAnchor.End, LogoFitMode.Inside)

        val contentColor = if (assets.hasExplicitTextColor) assets.cardContentColor else Color.WHITE
        val mainPaint = medium(scaled(h * if (isLandscape) 0.072f else 0.070f, settings), contentColor)
        val coverLinePaint = regular(scaled(h * if (isLandscape) 0.022f else 0.018f, settings), contentColor)
        val smallPaint = regular(scaled(h * 0.014f, settings), contentColor)
        val titleBaseline = h * if (isLandscape) 0.18f else 0.15f
        val titleWidth = if (isLandscape) w * 0.64f else w * 0.78f
        mainLine?.let {
            drawAdaptiveTextByCharacter(canvas, it.uppercase(Locale.ROOT), pad, titleBaseline, mainPaint, titleWidth, assets)
        }
        fun drawLines(text: String, x: Float, firstBaseline: Float, paint: Paint, maxWidth: Float, lineStep: Float, maxLines: Int) {
            wrapTextAtSeparators(text, paint, maxWidth, maxLines).forEachIndexed { index, line ->
                drawAdaptiveTextByCharacter(canvas, line, x, firstBaseline + lineStep * index, paint, maxWidth, assets)
            }
        }
        if (isLandscape) {
            val detailWidth = w * 0.66f
            if (!switchLine.isNullOrBlank()) drawLines("SWITCH  $switchLine", pad, h * 0.68f, coverLinePaint, detailWidth, h * 0.046f, maxLines = 2)
            if (!keycapLine.isNullOrBlank()) drawLines("KEYCAP  $keycapLine", pad, h * 0.76f, coverLinePaint, detailWidth, h * 0.046f, maxLines = 2)
            if (specLine.isNotBlank()) drawLines(specLine.uppercase(Locale.ROOT), pad, h * 0.855f, smallPaint, detailWidth, h * 0.032f, maxLines = 2)
        } else {
            if (!switchLine.isNullOrBlank()) drawLines("SWITCH  $switchLine", pad, h * 0.735f, coverLinePaint, w * 0.68f, h * 0.032f, maxLines = 2)
            if (!keycapLine.isNullOrBlank()) drawLines("KEYCAP  $keycapLine", pad, h * 0.790f, coverLinePaint, w * 0.68f, h * 0.032f, maxLines = 2)
            if (specLine.isNotBlank()) drawLines(specLine.uppercase(Locale.ROOT), pad, h * 0.850f, smallPaint, w * 0.68f, h * 0.026f, maxLines = 2)
        }
        if (!signature.isNullOrBlank()) {
            val signaturePaint = medium(scaled(h * 0.018f, settings, settings.nicknameEmphasis), contentColor).apply {
                textAlign = Paint.Align.RIGHT
            }
            drawAdaptiveTextByCharacter(
                canvas = canvas,
                text = signature,
                x = w - pad,
                baseline = h - pad * 0.88f,
                paint = signaturePaint,
                maxWidth = w * 0.42f,
                assets = assets,
            )
        }
    }
}

class SoftEditorialRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.BLACK
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Mixed
    override fun photoPlacement(): PhotoPlacement = PhotoPlacement.CenterCrop

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val w = bounds.width()
        val h = bounds.height()
        val isLandscape = w > h * 1.12f
        val pad = min(w, h) * 0.042f
        val title = info.displayTitleOrNull()
        val rows = rowsExcludingTitle(info.toDisplayRows(includeNickname = true), title).take(4)
        if (title == null && rows.isEmpty() && !assets.hasLogo) return
        CanvasRenderUtils.drawGradientScrim(
            canvas = canvas,
            rect = RectF(0f, h * if (isLandscape) 0.54f else 0.58f, w, h),
            startColor = Color.TRANSPARENT,
            endColor = Color.argb(132, 0, 0, 0),
        )
        CanvasRenderUtils.drawGradientScrim(
            canvas = canvas,
            rect = RectF(0f, 0f, w, h * 0.20f),
            startColor = Color.argb(64, 0, 0, 0),
            endColor = Color.TRANSPARENT,
        )
        val logoBox = if (isLandscape) {
            RectF(w - pad - w * 0.155f, pad, w - pad, pad + h * 0.095f)
        } else {
            RectF(w - pad - w * 0.24f, pad, w - pad, pad + h * 0.085f)
        }
        val logoTone = contrastColorAt(assets, logoBox.centerX(), logoBox.centerY())
        drawLogoIfPresent(canvas, logoBox, assets.withLogoContrast(logoTone), logoTone, LogoAnchor.End, LogoFitMode.Inside)
        val contentColor = if (assets.hasExplicitTextColor) assets.cardContentColor else Color.WHITE
        val titlePaint = medium(scaled(h * if (isLandscape) 0.040f else 0.044f, settings), contentColor)
        title?.let {
            val titleMaxWidth = if (isLandscape) w * 0.62f else w * 0.72f
            val lines = wrapTextAtWords(it.uppercase(Locale.ROOT), titlePaint, titleMaxWidth, maxLines = 2)
            val titleStart = if (isLandscape) h * 0.68f else h * 0.73f
            lines.forEachIndexed { index, line ->
                drawAdaptiveTextByCharacter(canvas, line, pad, titleStart + h * (index * 0.048f), titlePaint, titleMaxWidth, assets)
            }
        }
        val linePaint = regular(scaled(h * if (isLandscape) 0.0155f else 0.0145f, settings), contentColor)
        val rowText = rows.take(if (isLandscape) 3 else 4).joinToString("  /  ") { "${it.label.uppercase(Locale.ROOT)} ${it.value}" }
        if (rowText.isNotBlank()) {
            val x = pad
            val y = if (isLandscape) h * 0.80f else h * 0.845f
            val maxWidth = if (isLandscape) w * 0.76f else w * 0.76f
            wrapTextAtSeparators(rowText, linePaint, maxWidth, maxLines = if (isLandscape) 3 else 3)
                .forEachIndexed { index, line ->
                    drawAdaptiveTextByCharacter(canvas, line, x, y + h * 0.026f * index, linePaint, maxWidth, assets)
                }
        }
        CanvasRenderUtils.drawPaletteChipsInRect(
            canvas = canvas,
            colors = CanvasRenderUtils.visiblePaletteColors(assets, settings),
            area = if (isLandscape) RectF(pad, h * 0.905f, w * 0.64f, h * 0.95f) else RectF(pad, h * 0.905f, w * 0.64f, h * 0.95f),
            chipSize = h * 0.014f,
            gap = h * 0.006f,
            strokeColor = Color.argb(82, 255, 255, 255),
            alignment = PaletteChipAlignment.Start,
        )
    }
}

class PlainExportRenderer : TemplateRenderer {
    override fun backgroundColor(): Int = Color.BLACK
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Mixed

    override fun photoPlacement(): PhotoPlacement = PhotoPlacement.FitCenter

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        return
    }
}

private fun drawInfoRow(
    canvas: Canvas,
    row: BuildInfoRow,
    x: Float,
    labelBaseline: Float,
    maxWidth: Float,
    labelPaint: Paint,
    valuePaint: Paint,
    valueOffset: Float,
) {
    drawTextBaseline(canvas, row.label.uppercase(Locale.ROOT), x, labelBaseline, labelPaint, maxWidth)
    drawTextBaseline(canvas, row.value, x, labelBaseline + valueOffset, valuePaint, maxWidth)
}

private fun wrapTextAtWords(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    var current = ""
    words.forEach { word ->
        val candidate = if (current.isBlank()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
            current = candidate
        } else {
            lines += current
            current = word
        }
    }
    if (current.isNotBlank()) lines += current
    if (lines.size <= maxLines) return lines.map { TextDrawUtils.ellipsize(it, paint, maxWidth) }
    return lines.take(maxLines).mapIndexed { index, line ->
        if (index == maxLines - 1) {
            TextDrawUtils.ellipsize((listOf(line) + lines.drop(maxLines)).joinToString(" "), paint, maxWidth)
        } else {
            line
        }
    }
}

private fun wrapTextAtSeparators(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
    val parts = text.split("/").map { it.trim() }.filter { it.isNotBlank() }
    if (parts.size <= 1) return wrapTextAtWords(text, paint, maxWidth, maxLines)
    val lines = mutableListOf<String>()
    var current = ""
    parts.forEach { part ->
        val candidate = if (current.isBlank()) part else "$current  /  $part"
        if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
            current = candidate
        } else {
            lines += current
            current = part
        }
    }
    if (current.isNotBlank()) lines += current
    val expanded = lines.flatMap { line ->
        if (paint.measureText(line) <= maxWidth) listOf(line) else wrapTextAtWords(line, paint, maxWidth, maxLines)
    }
    if (expanded.size <= maxLines) return expanded
    return expanded.take(maxLines).mapIndexed { index, line ->
        if (index == maxLines - 1) {
            TextDrawUtils.ellipsize((listOf(line) + expanded.drop(maxLines)).joinToString(" / "), paint, maxWidth)
        } else {
            line
        }
    }
}

private fun drawLogoIfPresent(
    canvas: Canvas,
    rect: RectF,
    assets: RenderAssets,
    textColor: Int,
    anchor: LogoAnchor,
    fitMode: LogoFitMode = LogoFitMode.Height,
): RectF? {
    if (!assets.hasLogo) return null
    return drawLogo(
        canvas = canvas,
        rect = rect,
        assets = assets,
        textColor = textColor,
        backgroundColor = Color.TRANSPARENT,
        anchor = anchor,
        fitMode = fitMode,
    )
}

private fun detailText(
    vararg values: String?,
    separator: String = "  /  ",
): String {
    return values
        .mapNotNull { it.meaningfulBuildTextOrNull() }
        .distinctBy { it.normalizedDisplayKey() }
        .joinToString(separator)
}

private fun detailTextExcluding(
    title: String?,
    vararg values: String?,
): String {
    val titleKey = title.normalizedDisplayKey()
    return values
        .mapNotNull { it.meaningfulBuildTextOrNull() }
        .filterNot { titleKey.isNotBlank() && it.normalizedDisplayKey() == titleKey }
        .distinctBy { it.normalizedDisplayKey() }
        .joinToString("  /  ")
}

private fun nicknameDetail(
    info: KeyboardBuildInfo,
    settings: AppSettings,
    title: String?,
): String? {
    val rawNickname = info.displayNicknameOrNull()
    if (rawNickname.normalizedDisplayKey().isNotBlank() && rawNickname.normalizedDisplayKey() == title.normalizedDisplayKey()) {
        return null
    }
    return nicknameText(info, settings).meaningfulBuildTextOrNull()
}

private fun rowsExcludingTitle(
    rows: List<BuildInfoRow>,
    title: String?,
): List<BuildInfoRow> {
    val titleKey = title.normalizedDisplayKey()
    if (titleKey.isBlank()) return rows
    return rows.filterNot { it.value.normalizedDisplayKey() == titleKey }
}

private fun drawAdaptiveTextByCharacter(
    canvas: Canvas,
    text: String,
    x: Float,
    baseline: Float,
    paint: Paint,
    maxWidth: Float,
    assets: RenderAssets,
) {
    val safeText = TextDrawUtils.ellipsize(text, paint, maxWidth)
    if (safeText.isBlank()) return
    val originalAlign = paint.textAlign
    val textWidth = paint.measureText(safeText)
    val startX = when (originalAlign) {
        Paint.Align.RIGHT -> x - textWidth
        Paint.Align.CENTER -> x - textWidth / 2f
        else -> x
    }
    paint.textAlign = Paint.Align.LEFT
    var cursor = startX
    safeText.forEach { char ->
        val value = char.toString()
        val advance = paint.measureText(value)
        val sampleX = cursor + advance / 2f
        val sampleY = baseline - paint.textSize * 0.45f
        val color = contrastColorAt(assets, sampleX, sampleY)
        val shadowColor = if (color == Color.WHITE) Color.argb(116, 0, 0, 0) else Color.argb(86, 255, 255, 255)
        paint.setShadowLayer(paint.textSize * 0.055f, 0f, paint.textSize * 0.025f, shadowColor)
        paint.color = color
        canvas.drawText(value, cursor, baseline, paint)
        cursor += advance
    }
    paint.clearShadowLayer()
    paint.textAlign = originalAlign
}

private fun contrastColorAt(
    assets: RenderAssets,
    x: Float,
    y: Float,
): Int {
    val color = assets.sampleRenderedColor?.invoke(x, y) ?: assets.cardBackgroundColor
    return if (CanvasRenderUtils.isDarkColor(color)) Color.WHITE else Color.rgb(18, 19, 18)
}

private fun RenderAssets.withLogoContrast(color: Int): RenderAssets {
    val contrastBitmap = if (color == Color.WHITE) whiteLogoBitmap else blackLogoBitmap
    return if (contrastBitmap != null) {
        copy(logoBitmap = contrastBitmap, logoTintColor = null)
    } else if (logoTintColor != null) {
        copy(logoTintColor = color)
    } else {
        this
    }
}

private fun withAlpha(color: Int, alpha: Int): Int {
    return Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )
}

private fun String?.normalizedDisplayKey(): String {
    return this
        .meaningfulBuildTextOrNull()
        .orEmpty()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}
