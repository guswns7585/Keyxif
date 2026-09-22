package com.keyxif.app.domain.renderer

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.min

object LiquidGlassShaderRenderer {
    private const val MAX_BLUR_CACHE_PIXELS = 6_000_000L
    private const val MAX_BLUR_CACHE_ENTRIES = 12
    private val blurCacheLock = Any()
    private val blurCache = LinkedHashMap<String, Bitmap>(16, 0.75f, true)
    private var blurCachePixels = 0L

    fun drawIfSupported(
        canvas: Canvas,
        source: Bitmap,
        sourceCacheKey: String?,
        bounds: RectF,
        inner: RectF,
        innerRadius: Float,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return drawApi33(canvas, source, sourceCacheKey, bounds, inner, innerRadius)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawApi33(
        canvas: Canvas,
        source: Bitmap,
        sourceCacheKey: String?,
        bounds: RectF,
        inner: RectF,
        innerRadius: Float,
    ): Boolean = runCatching {
        val unit = min(bounds.width(), bounds.height())
        val blurred = cachedBlur(source, sourceCacheKey, bounds)
        val ownsBlurred = sourceCacheKey == null
        try {
            val shader = RuntimeShader(SHADER_SOURCE)
            shader.setInputShader(
                "image",
                BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
            )
            shader.setInputShader(
                "blurredImage",
                BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
            )
            shader.setFloatUniform("resolution", bounds.width(), bounds.height())
            shader.setFloatUniform("blurResolution", blurred.width.toFloat(), blurred.height.toFloat())
            shader.setFloatUniform(
                "blurScale",
                blurred.width / bounds.width(),
                blurred.height / bounds.height(),
            )
            shader.setFloatUniform("innerCenter", inner.centerX(), inner.centerY())
            shader.setFloatUniform("innerHalfSize", inner.width() * 0.5f, inner.height() * 0.5f)
            shader.setFloatUniform("innerRadius", innerRadius)
            shader.setFloatUniform("bevelWidth", unit * 0.026f)
            shader.setFloatUniform("refractionHeight", unit * 0.072f)
            shader.setFloatUniform("dispersion", (unit * 0.0018f).coerceIn(1.2f, 3.6f))
            canvas.drawRect(bounds, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.shader = shader
                alpha = 238
                isDither = true
            })
        } finally {
            if (ownsBlurred) blurred.recycle()
        }
    }.isSuccess

    private fun cachedBlur(source: Bitmap, sourceCacheKey: String?, bounds: RectF): Bitmap {
        val key = sourceCacheKey?.let {
            "$it|${bounds.width().toInt()}x${bounds.height().toInt()}"
        } ?: return createMultiPassBlur(source, bounds)
        synchronized(blurCacheLock) {
            blurCache[key]?.takeUnless(Bitmap::isRecycled)?.let { return it }
        }
        val created = createMultiPassBlur(source, bounds)
        synchronized(blurCacheLock) {
            blurCache[key]?.takeUnless(Bitmap::isRecycled)?.let {
                created.recycle()
                return it
            }
            blurCache[key] = created
            blurCachePixels += created.width.toLong() * created.height
            while (blurCache.size > MAX_BLUR_CACHE_ENTRIES || blurCachePixels > MAX_BLUR_CACHE_PIXELS) {
                val eldest = blurCache.entries.iterator().next()
                blurCache.remove(eldest.key)
                blurCachePixels -= eldest.value.width.toLong() * eldest.value.height
            }
        }
        return created
    }

    private fun createMultiPassBlur(source: Bitmap, bounds: RectF): Bitmap {
        val scale = min(1f / 3f, 1400f / max(bounds.width(), bounds.height()).coerceAtLeast(1f))
        val width = max(1, (bounds.width() * scale).toInt())
        val height = max(1, (bounds.height() * scale).toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(
            source,
            null,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
        )
        var pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val radius = (min(width, height) / 90).coerceIn(3, 16)
        val horizontal = IntArray(pixels.size)
        val vertical = IntArray(pixels.size)
        repeat(3) {
            blurRows(pixels, horizontal, width, height, radius)
            blurColumns(horizontal, vertical, width, height, radius)
            pixels = vertical
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
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

    // SDF lensing model inspired by public AGSL/WebGL optical-glass implementations.
    // The shader remains self-contained so final bitmap exports do not depend on a live View tree.
    private const val SHADER_SOURCE = """
        uniform shader image;
        uniform shader blurredImage;
        uniform float2 resolution;
        uniform float2 blurResolution;
        uniform float2 blurScale;
        uniform float2 innerCenter;
        uniform float2 innerHalfSize;
        uniform float innerRadius;
        uniform float bevelWidth;
        uniform float refractionHeight;
        uniform float dispersion;

        float roundedRectSdf(float2 point, float2 halfSize, float radius) {
            float2 q = abs(point) - halfSize + radius;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
        }

        float2 safeCoord(float2 coord) {
            return clamp(coord, float2(0.5), resolution - float2(0.5));
        }

        float2 safeBlurCoord(float2 coord) {
            return clamp(coord, float2(0.5), blurResolution - float2(0.5));
        }

        half4 main(float2 fragCoord) {
            float2 point = fragCoord - innerCenter;
            float distance = roundedRectSdf(point, innerHalfSize, innerRadius);
            float edge = 1.0 - smoothstep(0.0, bevelWidth, max(distance, 0.0));
            float edgePosition = clamp(max(distance, 0.0) / max(bevelWidth, 0.0001), 0.0, 1.0);

            float epsilon = 1.25;
            float2 gradient = float2(
                roundedRectSdf(point + float2(epsilon, 0.0), innerHalfSize, innerRadius) -
                    roundedRectSdf(point - float2(epsilon, 0.0), innerHalfSize, innerRadius),
                roundedRectSdf(point + float2(0.0, epsilon), innerHalfSize, innerRadius) -
                    roundedRectSdf(point - float2(0.0, epsilon), innerHalfSize, innerRadius)
            );
            float gradientLength = max(length(gradient), 0.0001);
            float2 normal = gradient / gradientLength;

            float curvedEdge = edge * edge * (3.0 - 2.0 * edge);
            float lensProfile = pow(curvedEdge, 1.35);
            float displacement = refractionHeight * lensProfile;
            float2 refractedCoord = safeCoord(fragCoord - normal * displacement);

            half3 soft = blurredImage.eval(safeBlurCoord(refractedCoord * blurScale)).rgb;
            half3 sharp = image.eval(refractedCoord).rgb;
            float localDetail = float(length(sharp - soft));
            float flatBoost = 1.0 - smoothstep(0.025, 0.16, localDetail);
            half blurAmount = half(0.50 - edge * 0.08);
            half3 color = mix(sharp, soft, blurAmount);

            float2 spectralOffset = normal * dispersion * (0.35 + edge * 0.65);
            color.r = mix(color.r, image.eval(safeCoord(refractedCoord - spectralOffset)).r, half(edge * 0.42));
            color.b = mix(color.b, image.eval(safeCoord(refractedCoord + spectralOffset)).b, half(edge * 0.42));

            half luminance = dot(color, half3(0.2126, 0.7152, 0.0722));
            color += half3((half(0.5) - luminance) * half(0.055));
            color = mix(half3(luminance), color, half(1.08));

            float2 lightDirection = normalize(float2(-0.58, -0.82));
            float facingLight = max(dot(-normal, lightDirection), 0.0);
            float facingShadow = max(dot(normal, lightDirection), 0.0);
            float shadowBand = 1.0 - smoothstep(0.22, 0.92, edgePosition);
            float refractionCaustic = smoothstep(0.03, 0.20, edgePosition) *
                (1.0 - smoothstep(0.28, 0.64, edgePosition));
            float highlightWidth = clamp(1.0 / max(bevelWidth, 1.0), 0.012, 0.10);
            float highlightDistance = (edgePosition - 0.145) / highlightWidth;
            float highlightCaustic = exp(-highlightDistance * highlightDistance);
            float causticStrength = refractionCaustic *
                (0.010 + pow(facingLight, 1.75) * 0.070) *
                (0.72 + flatBoost * 0.28);
            float specular = pow(facingLight, 1.45) * highlightCaustic * (0.152 + flatBoost * 0.138);
            float contactShade = pow(facingShadow, 1.20) * shadowBand * (0.060 + flatBoost * 0.055);
            half3 causticTint = mix(half3(1.0), clamp(soft * half(1.22), half3(0.0), half3(1.0)), half(0.16));
            color = mix(color, causticTint, half(causticStrength));
            color += half3(specular);
            color *= half(1.0 - contactShade);
            return half4(clamp(color, half3(0.0), half3(1.0)), 1.0);
        }
    """
}
