package com.keyxif.app.domain.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
            .filter { it.label in setOf("Housing", "Switch", "Keycap") }
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

class DarkGlassStripRenderer : TemplateRenderer {
    override fun logoBackgroundTone(): TemplateBackgroundTone = TemplateBackgroundTone.Dark

    override fun draw(canvas: Canvas, bounds: RectF, info: KeyboardBuildInfo, assets: RenderAssets, settings: AppSettings) {
        val rows = info.toDisplayRows()
            .filter { it.label in setOf("Housing", "Switch", "Keycap") }
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
            drawTextBaseline(canvas, "KEYXIF BUILD CARD", pad, labelTop + h * 0.006f, labelPaint, w * 0.45f)
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

private fun String?.normalizedDisplayKey(): String {
    return this
        .meaningfulBuildTextOrNull()
        .orEmpty()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}
