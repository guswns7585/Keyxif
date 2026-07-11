package com.keyxif.app.domain.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.ColorUtils
import com.keyxif.app.domain.model.PaletteAnalysisMode
import com.keyxif.app.util.BitmapUtils
import kotlin.math.max
import kotlin.math.roundToInt

class PhotoPaletteAnalyzer {
    fun analyze(
        context: Context,
        uri: Uri,
        mode: PaletteAnalysisMode,
        maxColors: Int = MAX_COLORS,
    ): List<Int> {
        val decoded = BitmapUtils.decodeOrientedBitmap(context, uri, ANALYSIS_LONG_SIDE)
        var cropped: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            val source = when (mode) {
                PaletteAnalysisMode.FullImage -> decoded
                PaletteAnalysisMode.CenterCrop -> centerCrop(decoded).also { cropped = it }
            }
            val analysisBitmap = downscaleForAnalysis(source).also {
                if (it !== source) scaled = it
            }
            extractColors(analysisBitmap, maxColors.coerceIn(3, MAX_COLORS))
        } finally {
            scaled?.takeIf { it !== cropped && it !== decoded }?.recycle()
            cropped?.takeIf { it !== decoded }?.recycle()
            decoded.recycle()
        }
    }

    private fun centerCrop(bitmap: Bitmap): Bitmap {
        val cropWidth = (bitmap.width * CENTER_CROP_RATIO).roundToInt().coerceIn(1, bitmap.width)
        val cropHeight = (bitmap.height * CENTER_CROP_RATIO).roundToInt().coerceIn(1, bitmap.height)
        val left = ((bitmap.width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((bitmap.height - cropHeight) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }

    private fun downscaleForAnalysis(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= TARGET_LONG_SIDE) return bitmap
        val ratio = TARGET_LONG_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun extractColors(
        bitmap: Bitmap,
        maxColors: Int,
    ): List<Int> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val stride = (pixels.size / MAX_SAMPLES).coerceAtLeast(1)
        val buckets = HashMap<Int, ColorBucket>()
        val hsl = FloatArray(3)

        for (index in pixels.indices step stride) {
            val color = pixels[index]
            if (Color.alpha(color) < MIN_ALPHA) continue
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            ColorUtils.RGBToHSL(red, green, blue, hsl)
            if (isLowValueNoise(hsl)) continue
            val key = quantizedKey(red, green, blue)
            buckets.getOrPut(key) { ColorBucket() }.add(red, green, blue)
        }

        return buckets.values
            .asSequence()
            .filter { it.count >= MIN_BUCKET_COUNT }
            .map { it.toCandidate() }
            .sortedByDescending { it.score }
            .map { it.color }
            .distinctByDistance()
            .take(maxColors)
            .toList()
    }

    private fun isLowValueNoise(hsl: FloatArray): Boolean {
        val saturation = hsl[1]
        val lightness = hsl[2]
        return saturation < 0.035f && (lightness < 0.08f || lightness > 0.96f)
    }

    private fun quantizedKey(
        red: Int,
        green: Int,
        blue: Int,
    ): Int {
        return ((red / QUANTIZE_STEP) shl 8) or
            ((green / QUANTIZE_STEP) shl 4) or
            (blue / QUANTIZE_STEP)
    }

    private fun Sequence<Int>.distinctByDistance(): Sequence<Int> = sequence {
        val selected = mutableListOf<Int>()
        for (color in this@distinctByDistance) {
            if (selected.none { rgbDistanceSquared(it, color) < MIN_DISTANCE_SQUARED }) {
                selected += color
                yield(color)
            }
        }
    }

    private fun rgbDistanceSquared(
        first: Int,
        second: Int,
    ): Int {
        val red = Color.red(first) - Color.red(second)
        val green = Color.green(first) - Color.green(second)
        val blue = Color.blue(first) - Color.blue(second)
        return red * red + green * green + blue * blue
    }

    private data class ColorBucket(
        var count: Int = 0,
        var redSum: Long = 0L,
        var greenSum: Long = 0L,
        var blueSum: Long = 0L,
    ) {
        fun add(
            red: Int,
            green: Int,
            blue: Int,
        ) {
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
            val saturation = hsl[1]
            val lightness = hsl[2]
            val neutralPenalty = if (saturation < 0.08f) 0.58f else 1f
            val edgeLightnessPenalty = when {
                lightness < 0.08f || lightness > 0.94f -> 0.42f
                lightness < 0.14f || lightness > 0.88f -> 0.72f
                else -> 1f
            }
            val score = count * (0.62f + saturation) * neutralPenalty * edgeLightnessPenalty
            return ColorCandidate(
                color = Color.rgb(red, green, blue),
                score = score,
            )
        }
    }

    private data class ColorCandidate(
        val color: Int,
        val score: Float,
    )

    private companion object {
        const val ANALYSIS_LONG_SIDE = 256
        const val TARGET_LONG_SIDE = 160
        const val CENTER_CROP_RATIO = 0.75f
        const val MAX_SAMPLES = 12_000
        const val MAX_COLORS = 5
        const val MIN_ALPHA = 220
        const val MIN_BUCKET_COUNT = 2
        const val QUANTIZE_STEP = 16
        const val MIN_DISTANCE_SQUARED = 44 * 44
    }
}
