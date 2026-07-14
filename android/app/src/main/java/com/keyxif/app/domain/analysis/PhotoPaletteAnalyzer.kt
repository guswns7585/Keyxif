package com.keyxif.app.domain.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.core.graphics.ColorUtils
import com.keyxif.app.domain.model.MaskStroke
import com.keyxif.app.domain.model.NormalizedRect
import com.keyxif.app.domain.model.NormalizedQuad
import com.keyxif.app.domain.model.PaletteAnalysisMode
import com.keyxif.app.util.BitmapUtils
import com.keyxif.app.domain.model.toQuad
import kotlin.math.max
import kotlin.math.roundToInt

class PhotoPaletteAnalyzer {
    fun analyze(
        context: Context,
        uri: Uri,
        mode: PaletteAnalysisMode,
        maxColors: Int = MAX_COLORS,
        centerCropRatio: Float = DEFAULT_CENTER_CROP_RATIO,
        rectNormalized: NormalizedRect? = null,
        quadNormalized: NormalizedQuad? = null,
        maskStrokes: List<MaskStroke> = emptyList(),
    ): List<Int> {
        val decoded = BitmapUtils.decodeOrientedBitmap(context, uri, ANALYSIS_LONG_SIDE)
        var cropped: Bitmap? = null
        var mask: Bitmap? = null
        return try {
            val source = when (mode) {
                PaletteAnalysisMode.AutoCenter -> centerCrop(decoded, centerCropRatio).also { cropped = it }
                PaletteAnalysisMode.RectSelection -> decoded
                PaletteAnalysisMode.PaintedMask -> decoded
            }
            mask = when (mode) {
                PaletteAnalysisMode.RectSelection -> createQuadMask(
                    decoded.width,
                    decoded.height,
                    quadNormalized ?: rectNormalized?.toQuad()
                        ?: throw IllegalArgumentException("분석할 영역을 지정해 주세요."),
                )
                PaletteAnalysisMode.PaintedMask -> createMask(decoded.width, decoded.height, maskStrokes)
                PaletteAnalysisMode.AutoCenter -> null
            }
            extractColors(source, maxColors.coerceIn(3, MAX_COLORS), mask)
        } finally {
            mask?.recycle()
            cropped?.takeIf { it !== decoded }?.recycle()
            decoded.recycle()
        }
    }

    private fun centerCrop(bitmap: Bitmap, ratio: Float): Bitmap {
        val safeRatio = ratio.coerceIn(0.35f, 1f)
        val cropWidth = (bitmap.width * safeRatio).roundToInt().coerceIn(1, bitmap.width)
        val cropHeight = (bitmap.height * safeRatio).roundToInt().coerceIn(1, bitmap.height)
        return Bitmap.createBitmap(
            bitmap,
            ((bitmap.width - cropWidth) / 2).coerceAtLeast(0),
            ((bitmap.height - cropHeight) / 2).coerceAtLeast(0),
            cropWidth,
            cropHeight,
        )
    }

    private fun createQuadMask(width: Int, height: Int, quad: NormalizedQuad): Bitmap {
        val safe = quad.normalized()
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val points = safe.points()
        val path = Path().apply {
            moveTo(points[0].x * width, points[0].y * height)
            points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        })
        return mask
    }

    private fun createMask(width: Int, height: Int, strokes: List<MaskStroke>): Bitmap {
        if (strokes.isEmpty()) {
            throw IllegalArgumentException(MASK_TOO_SMALL_MESSAGE)
        }
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.strokeWidth = stroke.brushSizeNormalized.coerceIn(0.01f, 0.25f) * max(width, height)
            paint.xfermode = if (stroke.isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            val first = stroke.points.first()
            if (stroke.points.size == 1) {
                canvas.drawCircle(first.x * width, first.y * height, paint.strokeWidth / 2f, paint.apply { style = Paint.Style.FILL })
                paint.style = Paint.Style.STROKE
            } else {
                var previous = first
                stroke.points.drop(1).forEach { point ->
                    canvas.drawLine(previous.x * width, previous.y * height, point.x * width, point.y * height, paint)
                    previous = point
                }
            }
        }
        paint.xfermode = null
        return mask
    }

    private fun extractColors(bitmap: Bitmap, maxColors: Int, mask: Bitmap?): List<Int> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val maskPixels = mask?.let {
            IntArray(it.width * it.height).also { values -> it.getPixels(values, 0, it.width, 0, 0, it.width, it.height) }
        }
        val selectedCount = maskPixels?.count { Color.alpha(it) >= MIN_MASK_ALPHA }
        if (selectedCount != null && selectedCount < MIN_MASK_PIXELS) {
            throw IllegalArgumentException(MASK_TOO_SMALL_MESSAGE)
        }
        val stride = (pixels.size / MAX_SAMPLES).coerceAtLeast(1)
        val buckets = HashMap<Int, ColorBucket>()
        val hsl = FloatArray(3)
        for (index in pixels.indices step stride) {
            if (maskPixels != null && Color.alpha(maskPixels[index]) < MIN_MASK_ALPHA) continue
            val color = pixels[index]
            if (Color.alpha(color) < MIN_ALPHA) continue
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            ColorUtils.RGBToHSL(red, green, blue, hsl)
            if (isLowValueNoise(hsl)) continue
            buckets.getOrPut(quantizedKey(red, green, blue)) { ColorBucket() }.add(red, green, blue)
        }
        return buckets.values.asSequence()
            .filter { it.count >= MIN_BUCKET_COUNT }
            .map { it.toCandidate() }
            .sortedByDescending { it.score }
            .map { it.color }
            .distinctByDistance()
            .take(maxColors)
            .toList()
    }

    private fun isLowValueNoise(hsl: FloatArray): Boolean =
        hsl[1] < 0.035f && (hsl[2] < 0.08f || hsl[2] > 0.96f)

    private fun quantizedKey(red: Int, green: Int, blue: Int): Int =
        ((red / QUANTIZE_STEP) shl 8) or ((green / QUANTIZE_STEP) shl 4) or (blue / QUANTIZE_STEP)

    private fun Sequence<Int>.distinctByDistance(): Sequence<Int> = sequence {
        val selected = mutableListOf<Int>()
        for (color in this@distinctByDistance) {
            if (selected.none { rgbDistanceSquared(it, color) < MIN_DISTANCE_SQUARED }) {
                selected += color
                yield(color)
            }
        }
    }

    private fun rgbDistanceSquared(first: Int, second: Int): Int {
        val red = Color.red(first) - Color.red(second)
        val green = Color.green(first) - Color.green(second)
        val blue = Color.blue(first) - Color.blue(second)
        return red * red + green * green + blue * blue
    }

    private data class ColorBucket(
        var count: Int = 0,
        var redSum: Long = 0,
        var greenSum: Long = 0,
        var blueSum: Long = 0,
    ) {
        fun add(red: Int, green: Int, blue: Int) {
            count += 1
            redSum += red
            greenSum += green
            blueSum += blue
        }

        fun toCandidate(): ColorCandidate {
            val red = (redSum / count).toInt().coerceIn(0, 255)
            val green = (greenSum / count).toInt().coerceIn(0, 255)
            val blue = (blueSum / count).toInt().coerceIn(0, 255)
            val hsl = FloatArray(3)
            ColorUtils.RGBToHSL(red, green, blue, hsl)
            val neutralPenalty = if (hsl[1] < 0.08f) 0.58f else 1f
            val edgePenalty = when {
                hsl[2] < 0.08f || hsl[2] > 0.94f -> 0.42f
                hsl[2] < 0.14f || hsl[2] > 0.88f -> 0.72f
                else -> 1f
            }
            return ColorCandidate(Color.rgb(red, green, blue), count * (0.62f + hsl[1]) * neutralPenalty * edgePenalty)
        }
    }

    private data class ColorCandidate(val color: Int, val score: Float)

    private companion object {
        const val ANALYSIS_LONG_SIDE = 640
        const val DEFAULT_CENTER_CROP_RATIO = 0.75f
        const val MAX_SAMPLES = 32_000
        const val MAX_COLORS = 5
        const val MIN_ALPHA = 220
        const val MIN_MASK_ALPHA = 64
        const val MIN_MASK_PIXELS = 300
        const val MIN_BUCKET_COUNT = 2
        const val QUANTIZE_STEP = 16
        const val MIN_DISTANCE_SQUARED = 44 * 44
        const val MASK_TOO_SMALL_MESSAGE = "선택된 영역이 너무 작습니다. 키보드 부분을 더 칠해 주세요."
    }
}
