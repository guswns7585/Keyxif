package com.keyxif.app.domain.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.keyxif.app.R
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.BuildInfoRow
import com.keyxif.app.domain.model.KeyboardBuildInfo
import com.keyxif.app.domain.model.NicknameStyle
import com.keyxif.app.domain.model.TemplateFont
import com.keyxif.app.domain.model.displayNicknameOrNull
import com.keyxif.app.domain.model.isMeaningfulBuildText
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import com.keyxif.app.domain.model.toDisplayRows
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class RenderAssets(
    val logoBitmap: Bitmap?,
    val whiteLogoBitmap: Bitmap? = null,
    val blackLogoBitmap: Bitmap? = null,
    val logoLabel: String,
    val paletteColors: List<Int> = emptyList(),
    val hasLogo: Boolean = logoBitmap != null || logoLabel.isMeaningfulBuildText(),
    val cardBackgroundColor: Int = Color.TRANSPARENT,
    val cardContentColor: Int = Color.BLACK,
    val hasExplicitTextColor: Boolean = false,
    val logoTintColor: Int? = null,
    val sampleRenderedColor: ((Float, Float) -> Int?)? = null,
)

enum class PaletteChipAlignment {
    Start,
    Center,
    End,
}

object CanvasRenderUtils {
    private val templateTypeface = ThreadLocal<Typeface?>()

    fun <T> withTemplateFont(
        context: Context,
        settings: AppSettings,
        block: () -> T,
    ): T {
        val previous = templateTypeface.get()
        templateTypeface.set(resolveTemplateTypeface(context, settings.templateFont))
        return try {
            block()
        } finally {
            if (previous == null) {
                templateTypeface.remove()
            } else {
                templateTypeface.set(previous)
            }
        }
    }

    private fun resolveTemplateTypeface(context: Context, font: TemplateFont): Typeface? {
        val resId = when (font) {
            TemplateFont.System -> return null
            TemplateFont.IbmPlexSansKr -> R.font.ibm_plex_sans_kr_regular
            TemplateFont.NotoSansKr -> R.font.noto_sans_kr_regular
            TemplateFont.NotoSerifKr -> R.font.noto_serif_kr_regular
            TemplateFont.NanumGothic -> R.font.nanum_gothic_regular
            TemplateFont.GowunBatang -> R.font.gowun_batang_regular
            TemplateFont.BlackHanSans -> R.font.black_han_sans_regular
            TemplateFont.NanumPenScript -> R.font.nanum_pen_script_regular
            TemplateFont.Gugi -> R.font.gugi_regular
        }
        return runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()
    }

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
        val typeface = templateTypeface.get()
            ?.let { Typeface.create(it, Typeface.BOLD) }
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        return paint(color, size, typeface = typeface)
    }

    fun regular(size: Float, color: Int = Color.BLACK): Paint {
        val typeface = templateTypeface.get()
            ?: Typeface.create("sans-serif", Typeface.NORMAL)
        return paint(color, size, typeface = typeface)
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
    ): Float = (base * settings.textScale.coerceIn(0.9f, 1.55f) * extra).coerceAtLeast(MIN_TEXT_SIZE_PX)

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

    fun cardBackgroundColor(
        fallback: Int,
        paletteColors: List<Int>,
        settings: AppSettings,
        usePaletteColorForCardBackground: Boolean = settings.usePaletteColorForCardBackground,
        paletteBackgroundColorIndex: Int = settings.paletteBackgroundColorIndex,
        customCardBackgroundColor: Int? = null,
    ): Int {
        if (!settings.showPaletteColors || !usePaletteColorForCardBackground) return fallback
        customCardBackgroundColor?.takeIf { Color.alpha(it) > 0 }?.let { return it }
        return paletteColors
            .filter { Color.alpha(it) > 0 }
            .getOrNull(paletteBackgroundColorIndex.coerceIn(0, 4))
            ?: fallback
    }

    fun readableContentColor(backgroundColor: Int): Int {
        return if (relativeLuminance(backgroundColor) >= 0.36) Color.rgb(20, 21, 20) else Color.WHITE
    }

    fun relativeLuminance(color: Int): Double {
        return BackgroundContrast.relativeLuminance(color)
    }

    fun readableSecondaryColor(backgroundColor: Int): Int {
        val primary = readableContentColor(backgroundColor)
        return if (primary == Color.WHITE) Color.argb(218, 255, 255, 255) else Color.rgb(78, 80, 76)
    }

    fun isDarkColor(color: Int): Boolean = BackgroundContrast.isDark(color)

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
