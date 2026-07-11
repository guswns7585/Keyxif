package com.keyxif.app.domain.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.BuildInfoRow
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.NicknameStyle
import com.keyxif.app.domain.model.displayNicknameOrNull
import com.keyxif.app.domain.model.isMeaningfulBuildText
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import com.keyxif.app.domain.model.toDisplayRows
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class RenderAssets(
    val logoBitmap: Bitmap?,
    val logoLabel: String,
    val paletteColors: List<Int> = emptyList(),
    val hasLogo: Boolean = logoBitmap != null || logoLabel.isMeaningfulBuildText(),
)

enum class PaletteChipAlignment {
    Start,
    Center,
    End,
}

object CanvasRenderUtils {
    fun paint(
        color: Int,
        size: Float,
        style: Paint.Style = Paint.Style.FILL,
        typeface: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = size.coerceAtLeast(MIN_TEXT_SIZE_PX)
        this.style = style
        this.typeface = typeface
        isSubpixelText = true
        isLinearText = true
        isDither = true
        hinting = Paint.HINTING_ON
    }

    fun medium(size: Float, color: Int = Color.BLACK): Paint {
        return paint(color, size, typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL))
    }

    fun regular(size: Float, color: Int = Color.BLACK): Paint {
        return paint(color, size, typeface = Typeface.create("sans-serif", Typeface.NORMAL))
    }

    fun ellipsize(
        text: String,
        paint: Paint,
        maxWidth: Float,
    ): String = TextDrawUtils.ellipsize(text, paint, maxWidth)

    fun drawTextBaseline(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        paint: Paint,
        maxWidth: Float,
    ) {
        val safeText = ellipsize(text, paint, maxWidth)
        if (safeText.isNotBlank()) {
            canvas.drawText(safeText, x, baseline, paint)
        }
    }

    fun drawRoundRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        color: Int,
    ) {
        canvas.drawRoundRect(rect, radius, radius, paint(color, 1f))
    }

    fun drawGradientScrim(
        canvas: Canvas,
        rect: RectF,
        startColor: Int,
        endColor: Int,
        vertical: Boolean = true,
    ) {
        val gradient = if (vertical) {
            LinearGradient(0f, rect.top, 0f, rect.bottom, startColor, endColor, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(rect.left, 0f, rect.right, 0f, startColor, endColor, Shader.TileMode.CLAMP)
        }
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRect(rect, gradientPaint)
    }

    fun drawLogo(
        canvas: Canvas,
        rect: RectF,
        assets: RenderAssets,
        textColor: Int,
        backgroundColor: Int,
        anchor: LogoAnchor = LogoAnchor.Center,
        fitMode: LogoFitMode = LogoFitMode.Height,
    ): RectF {
        return LogoDrawUtils.draw(
            canvas = canvas,
            box = rect,
            assets = assets,
            textColor = textColor,
            backgroundColor = backgroundColor,
            anchor = anchor,
            fitMode = fitMode,
        )
    }

    fun buildParts(
        info: KeyboardBuildInfo,
        includeNickname: Boolean = true,
    ): List<BuildInfoRow> {
        return info.toDisplayRows(includeNickname = includeNickname)
    }

    fun scaled(
        base: Float,
        settings: AppSettings,
        extra: Float = 1f,
    ): Float = (base * settings.textScale.coerceIn(0.85f, 1.35f) * extra).coerceAtLeast(MIN_TEXT_SIZE_PX)

    fun nicknameText(
        info: KeyboardBuildInfo,
        settings: AppSettings,
    ): String {
        val nickname = info.displayNicknameOrNull() ?: return ""
        return when (settings.nicknameStyle) {
            NicknameStyle.Plain -> nickname
            NicknameStyle.AtPrefix -> if (nickname.startsWith("@")) nickname else "@$nickname"
            NicknameStyle.Credit -> "by $nickname"
        }
    }

    fun drawLabelValue(
        canvas: Canvas,
        label: String,
        value: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        labelPaint: Paint,
        valuePaint: Paint,
    ) {
        val displayValue = value.meaningfulBuildTextOrNull() ?: return
        val labelText = label.uppercase()
        canvas.drawText(labelText, x, baseline, labelPaint)
        val valueX = x + max(labelPaint.measureText(labelText) + 16f, maxWidth * 0.24f)
        drawTextBaseline(canvas, displayValue, valueX, baseline, valuePaint, maxWidth - (valueX - x))
    }

    fun visiblePaletteColors(
        assets: RenderAssets,
        settings: AppSettings,
    ): List<Int> {
        return if (settings.showPaletteColors) {
            assets.paletteColors.take(settings.paletteColorCount.coerceIn(3, 5))
        } else {
            emptyList()
        }
    }

    fun drawPaletteChips(
        canvas: Canvas,
        colors: List<Int>,
        right: Float,
        centerY: Float,
        chipSize: Float,
        gap: Float,
        strokeColor: Int,
    ): RectF? {
        val effectiveSize = chipSize * PALETTE_CHIP_SCALE
        val effectiveGap = gap.coerceAtLeast(effectiveSize * 0.32f)
        val area = RectF(0f, centerY - effectiveSize / 2f, right, centerY + effectiveSize / 2f)
        return drawPaletteChipsInRect(
            canvas = canvas,
            colors = colors,
            area = area,
            chipSize = chipSize,
            gap = effectiveGap,
            strokeColor = strokeColor,
            alignment = PaletteChipAlignment.End,
        )
    }

    fun drawPaletteChipsInRect(
        canvas: Canvas,
        colors: List<Int>,
        area: RectF,
        chipSize: Float,
        gap: Float,
        strokeColor: Int,
        alignment: PaletteChipAlignment = PaletteChipAlignment.End,
        maxChips: Int = colors.size,
    ): RectF? {
        if (colors.isEmpty() || chipSize <= 0f || area.width() <= 0f || area.height() <= 0f) return null
        val effectiveSize = chipSize * PALETTE_CHIP_SCALE
        if (area.height() < effectiveSize) return null

        val effectiveGap = gap.coerceAtLeast(effectiveSize * 0.32f)
        val capacity = floor((area.width() + effectiveGap) / (effectiveSize + effectiveGap))
            .toInt()
            .coerceAtMost(maxChips)
        if (capacity <= 0) return null

        val displayColors = colors.take(capacity)
        val totalWidth = effectiveSize * displayColors.size + effectiveGap * (displayColors.size - 1).coerceAtLeast(0)
        if (totalWidth > area.width()) return null

        var left = when (alignment) {
            PaletteChipAlignment.Start -> area.left
            PaletteChipAlignment.Center -> area.left + (area.width() - totalWidth) / 2f
            PaletteChipAlignment.End -> area.right - totalWidth
        }
        val actualLeft = left
        val top = area.centerY() - effectiveSize / 2f
        val radius = effectiveSize / 2f
        val strokePaint = paint(strokeColor, 1f, style = Paint.Style.STROKE).apply {
            // 저해상도 미리보기에서는 칩이 아주 작아져 상한(11%)이 1px보다 작아질 수 있다.
            val maxStrokeWidth = (effectiveSize * 0.11f).coerceAtLeast(1f)
            strokeWidth = (effectiveSize * 0.055f).coerceIn(1f, maxStrokeWidth)
        }
        displayColors.forEach { color ->
            canvas.drawCircle(left + radius, top + radius, radius, paint(color, 1f))
            canvas.drawCircle(left + radius, top + radius, radius, strokePaint)
            left += effectiveSize + effectiveGap
        }
        return RectF(actualLeft, top, actualLeft + totalWidth, top + effectiveSize)
    }

    private const val MIN_TEXT_SIZE_PX = 8.5f
    private const val PALETTE_CHIP_SCALE = 1.15f
}
