package com.keyxif.app.domain.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.OutputFormat
import com.keyxif.app.util.FileNameUtils

class ImageExporter {
    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        settings: AppSettings,
    ): Uri {
        return save(
            context = context,
            bitmap = bitmap,
            displayName = displayName,
            outputFormat = settings.outputFormat,
            quality = settings.webpQuality,
            directoryName = settings.saveDirectoryName,
        )
    }

    fun saveWebp(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        quality: Int = DEFAULT_WEBP_QUALITY,
        directoryName: String = "Keyxif",
    ): Uri {
        return save(
            context = context,
            bitmap = bitmap,
            displayName = displayName,
            outputFormat = OutputFormat.WEBP,
            quality = quality,
            directoryName = directoryName,
        )
    }

    private fun save(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        outputFormat: OutputFormat,
        quality: Int,
        directoryName: String,
    ): Uri {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val safeDirectory = FileNameUtils.sanitize(directoryName).ifBlank { "Keyxif" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, outputFormat.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$safeDirectory")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: error("갤러리에 저장 항목을 만들 수 없습니다.")
        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(outputFormat.compressFormat, quality.coerceIn(1, 100), output)) {
                    "${outputFormat.name} 변환에 실패했습니다."
                }
            } ?: error("저장 스트림을 열 수 없습니다.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }.onFailure { error ->
            resolver.delete(uri, null, null)
            throw error
        }
        return uri
    }

    private val OutputFormat.mimeType: String
        get() = when (this) {
            OutputFormat.WEBP -> "image/webp"
            OutputFormat.PNG -> "image/png"
        }

    private val OutputFormat.compressFormat: Bitmap.CompressFormat
        get() = when (this) {
            OutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
        }

    companion object {
        const val DEFAULT_WEBP_QUALITY = 92
    }
}
