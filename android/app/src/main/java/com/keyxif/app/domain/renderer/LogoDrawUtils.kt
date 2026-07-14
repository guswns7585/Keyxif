package com.keyxif.app.domain.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import kotlin.math.min

enum class LogoAnchor {
    Start,
    Center,
    End,
}

enum class LogoFitMode {
    Height,
    Inside,
}

object LogoDrawUtils {
    fun draw(
        canvas: Canvas,
        box: RectF,
        assets: RenderAssets,
        textColor: Int,
        backgroundColor: Int,
        anchor: LogoAnchor = LogoAnchor.Center,
        fitMode: LogoFitMode = LogoFitMode.Height,
    ): RectF {
        if (box.width() <= 0f || box.height() <= 0f) return RectF(box)
        assets.logoBitmap?.let { bitmap ->
            val inset = box.height() * 0.04f
            val contentBox = RectF(
                box.left + inset,
                box.top + inset,
                box.right - inset,
                box.bottom - inset,
            )
            val target = when (fitMode) {
                LogoFitMode.Height -> fitHeight(
                    sourceWidth = bitmap.width.toFloat(),
                    sourceHeight = bitmap.height.toFloat(),
                    box = contentBox,
                    anchor = anchor,
                )
                LogoFitMode.Inside -> fitInside(
                    sourceWidth = bitmap.width.toFloat(),
                    sourceHeight = bitmap.height.toFloat(),
                    box = contentBox,
                    anchor = anchor,
                )
            }
            val saveCount = canvas.save()
            val radius = min(target.width(), target.height()) * 0.12f
            val path = Path().apply { addRoundRect(target, radius, radius, Path.Direction.CW) }
            canvas.clipPath(path)
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                assets.logoTintColor?.let { colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
            }
            canvas.drawBitmap(bitmap, null, target, bitmapPaint)
            canvas.restoreToCount(saveCount)
            return target
        }

        if (backgroundColor != Color.TRANSPARENT) {
            CanvasRenderUtils.drawRoundRect(
                canvas = canvas,
                rect = box,
                radius = min(box.width(), box.height()) * 0.18f,
                color = backgroundColor,
            )
        }
        val label = assets.logoLabel.take(14)
        if (label.isBlank() || !assets.hasLogo) {
            return RectF(box.left, box.top, box.left, box.top)
        }
        val textPaint = CanvasRenderUtils.medium(
            size = min(box.height() * 0.28f, box.width() * 0.16f),
            color = textColor,
        ).apply {
            textAlign = Paint.Align.CENTER
        }
        val baseline = box.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(
            TextDrawUtils.ellipsize(label.uppercase(), textPaint, box.width() * 0.78f),
            box.centerX(),
            baseline,
            textPaint,
        )
        return RectF(box)
    }

    fun fitCenter(
        sourceWidth: Float,
        sourceHeight: Float,
        box: RectF,
    ): RectF {
        if (sourceWidth <= 0f || sourceHeight <= 0f) return RectF(box)
        val scale = min(box.width() / sourceWidth, box.height() / sourceHeight)
        val width = sourceWidth * scale
        val height = sourceHeight * scale
        return RectF(
            box.centerX() - width / 2f,
            box.centerY() - height / 2f,
            box.centerX() + width / 2f,
            box.centerY() + height / 2f,
        )
    }

    fun fitInside(
        sourceWidth: Float,
        sourceHeight: Float,
        box: RectF,
        anchor: LogoAnchor,
    ): RectF {
        val target = fitCenter(sourceWidth, sourceHeight, box)
        val left = when (anchor) {
            LogoAnchor.Start -> box.left
            LogoAnchor.Center -> target.left
            LogoAnchor.End -> box.right - target.width()
        }
        return RectF(left, target.top, left + target.width(), target.bottom)
    }

    fun fitHeight(
        sourceWidth: Float,
        sourceHeight: Float,
        box: RectF,
        anchor: LogoAnchor,
    ): RectF {
        if (sourceWidth <= 0f || sourceHeight <= 0f) return RectF(box)
        val scale = box.height() / sourceHeight
        val width = sourceWidth * scale
        val height = sourceHeight * scale
        val left = when (anchor) {
            LogoAnchor.Start -> box.left
            LogoAnchor.Center -> box.centerX() - width / 2f
            LogoAnchor.End -> box.right - width
        }
        return RectF(
            left,
            box.centerY() - height / 2f,
            left + width,
            box.centerY() + height / 2f,
        )
    }
}
