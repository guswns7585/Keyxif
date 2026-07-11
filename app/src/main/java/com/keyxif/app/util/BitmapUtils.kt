package com.keyxif.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
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

        val decoded = openInputStream(context, uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: error("이미지를 읽을 수 없습니다.")

        return downscaleIfNeeded(applyOrientation(decoded, orientation), maxLongSide)
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
