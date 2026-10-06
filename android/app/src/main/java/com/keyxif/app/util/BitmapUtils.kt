package com.keyxif.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapUtils {
    const val SAVE_LONG_SIDE_LIMIT = 4096
    const val PREVIEW_LONG_SIDE_LIMIT = 720

    fun decodeOrientedBitmap(
        context: Context,
        uri: Uri,
        maxLongSide: Int = SAVE_LONG_SIDE_LIMIT,
    ): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return decodeWithImageDecoder(context, uri, maxLongSide)
        }
        return decodeWithBitmapFactory(context, uri, maxLongSide)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(
        context: Context,
        uri: Uri,
        maxLongSide: Int,
    ): Bitmap {
        val source = if (uri.scheme == "file") {
            val path = uri.path ?: throw SourceImageDecodeException("원본 파일 경로가 없습니다.")
            ImageDecoder.createSource(File(path))
        } else {
            ImageDecoder.createSource(context.contentResolver, uri)
        }
        val decoded = try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setOnPartialImageListener { false }
                val width = info.size.width
                val height = info.size.height
                val longest = max(width, height)
                if (width > 0 && height > 0 && longest > maxLongSide) {
                    val ratio = maxLongSide.toFloat() / longest
                    decoder.setTargetSize(
                        (width * ratio).roundToInt().coerceAtLeast(1),
                        (height * ratio).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } catch (error: Throwable) {
            throw SourceImageDecodeException("원본 이미지가 불완전하거나 지원되지 않는 형식입니다.", error)
        }
        if (decoded.width <= 0 || decoded.height <= 0) {
            decoded.recycle()
            throw SourceImageDecodeException("원본 이미지의 크기가 올바르지 않습니다.")
        }
        if (decoded.config == Bitmap.Config.ARGB_8888) return decoded
        return try {
            decoded.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw SourceImageDecodeException("원본 이미지를 안정적인 색상 형식으로 변환하지 못했습니다.")
        } finally {
            decoded.recycle()
        }
    }

    private fun decodeWithBitmapFactory(
        context: Context,
        uri: Uri,
        maxLongSide: Int,
    ): Bitmap {
        val orientation = runCatching {
            openInputStream(context, uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openInputStream(context, uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }

        val sampleSize = calculateSampleSize(
            width = boundsOptions.outWidth,
            height = boundsOptions.outHeight,
            maxLongSide = maxLongSide,
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = try {
            openInputStream(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (error: Throwable) {
            throw SourceImageDecodeException("원본 이미지를 읽지 못했습니다.", error)
        } ?: throw SourceImageDecodeException("원본 이미지를 읽을 수 없습니다.")

        var current = decoded
        try {
            current = downscaleIfNeeded(current, maxLongSide)
            current = applyOrientation(current, orientation)
            return current
        } catch (error: Throwable) {
            if (!current.isRecycled) current.recycle()
            throw error
        }
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        maxLongSide: Int,
    ): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        val longest = max(width, height)
        while (longest / sample > maxLongSide * 1.25f) {
            sample *= 2
        }
        return sample
    }

    private fun applyOrientation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
        }
        if (matrix.isIdentity) return bitmap
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented != bitmap) bitmap.recycle()
        return oriented
    }

    private fun downscaleIfNeeded(
        bitmap: Bitmap,
        maxLongSide: Int,
    ): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxLongSide) return bitmap
        val ratio = maxLongSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun openInputStream(
        context: Context,
        uri: Uri,
    ): InputStream? {
        return if (uri.scheme == "file") {
            uri.path?.let(::File)?.inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }
}

class SourceImageDecodeException(
    message: String,
    cause: Throwable? = null,
) : java.io.IOException(message, cause)
