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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class SavedImageResult(
    val uri: Uri,
    val displayName: String,
    val outputFormat: OutputFormat,
)

class ImageExporter {
    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        settings: AppSettings,
    ): SavedImageResult {
        return save(
            context = context,
            bitmap = bitmap,
            displayName = displayName,
            requestedFormat = settings.outputFormat,
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
            requestedFormat = OutputFormat.WEBP,
            quality = quality,
            directoryName = directoryName,
        ).uri
    }

    private fun save(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        requestedFormat: OutputFormat,
        quality: Int,
        directoryName: String,
    ): SavedImageResult {
        val encoded = encodeRequestedFormat(
            context = context,
            bitmap = bitmap,
            displayName = displayName,
            requestedFormat = requestedFormat,
            quality = quality,
        )
        return try {
            val uri = publishToGallery(
                context = context,
                encodedFile = encoded.file,
                displayName = encoded.displayName,
                outputFormat = encoded.outputFormat,
                directoryName = directoryName,
            )
            SavedImageResult(
                uri = uri,
                displayName = encoded.displayName,
                outputFormat = encoded.outputFormat,
            )
        } finally {
            encoded.file.delete()
        }
    }

    private fun encodeRequestedFormat(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        requestedFormat: OutputFormat,
        quality: Int,
    ): EncodedImage {
        var firstFailure: Throwable? = null
        for (compressFormat in requestedFormat.compatibleCompressFormats) {
            try {
                return encodeToTemporaryFile(
                    context = context,
                    bitmap = bitmap,
                    displayName = displayName,
                    outputFormat = requestedFormat,
                    compressFormat = compressFormat,
                    quality = quality,
                )
            } catch (error: Throwable) {
                if (error is OutOfMemoryError) throw error
                if (error is TemporaryStorageException) throw error
                if (firstFailure == null) firstFailure = error else firstFailure.addSuppressed(error)
            }
        }
        throw ImageEncodingException(firstFailure ?: IOException("이미지 인코딩에 실패했습니다."))
    }

    private fun encodeToTemporaryFile(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        outputFormat: OutputFormat,
        compressFormat: Bitmap.CompressFormat,
        quality: Int,
    ): EncodedImage {
        val stagingDirectory = File(context.cacheDir, STAGING_DIRECTORY).apply { mkdirs() }
        stagingDirectory.listFiles()
            ?.filter { System.currentTimeMillis() - it.lastModified() > STALE_FILE_AGE_MS }
            ?.forEach(File::delete)
        val file = try {
            File.createTempFile("export-", ".${outputFormat.extension}", stagingDirectory)
        } catch (error: IOException) {
            throw TemporaryStorageException(error)
        }
        try {
            FileOutputStream(file).use { output ->
                if (!bitmap.compress(compressFormat, quality.coerceIn(1, 100), output)) {
                    throw ImageEncodingException(
                        IllegalStateException("${outputFormat.name} 인코더가 결과를 생성하지 못했습니다."),
                    )
                }
            }
            if (file.length() <= 0L) {
                throw ImageEncodingException(IllegalStateException("인코딩된 이미지가 비어 있습니다."))
            }
            return EncodedImage(
                file = file,
                displayName = displayName.withExtension(outputFormat.extension),
                outputFormat = outputFormat,
            )
        } catch (error: Throwable) {
            file.delete()
            throw when (error) {
                is ExportStorageException -> error
                is IOException -> TemporaryStorageException(error)
                else -> error
            }
        }
    }

    private fun publishToGallery(
        context: Context,
        encodedFile: File,
        displayName: String,
        outputFormat: OutputFormat,
        directoryName: String,
    ): Uri {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val safeDirectory = FileNameUtils.sanitize(directoryName).ifBlank { "Keyxif" }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return insertAndCopy(
                context = context,
                collection = collection,
                encodedFile = encodedFile,
                displayName = displayName,
                outputFormat = outputFormat,
                safeDirectory = safeDirectory,
                pending = false,
            )
        }

        val pendingUri = try {
            insertAndCopy(
                context = context,
                collection = collection,
                encodedFile = encodedFile,
                displayName = displayName,
                outputFormat = outputFormat,
                safeDirectory = safeDirectory,
                pending = true,
            )
        } catch (error: Throwable) {
            if (error is SecurityException || error is OutOfMemoryError) throw error
            return insertAndCopy(
                context = context,
                collection = collection,
                encodedFile = encodedFile,
                displayName = displayName,
                outputFormat = outputFormat,
                safeDirectory = safeDirectory,
                pending = false,
            )
        }
        val published = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(pendingUri, values, null, null) > 0 || !isPending(context, pendingUri)
        }.getOrDefault(false)
        if (published) return pendingUri

        // Some OEM MediaStore providers reject or misreport the pending update. Reinsert the
        // already encoded file as public content instead of failing the entire export.
        runCatching { resolver.delete(pendingUri, null, null) }
        return insertAndCopy(
            context = context,
            collection = collection,
            encodedFile = encodedFile,
            displayName = displayName,
            outputFormat = outputFormat,
            safeDirectory = safeDirectory,
            pending = false,
        )
    }

    private fun insertAndCopy(
        context: Context,
        collection: Uri,
        encodedFile: File,
        displayName: String,
        outputFormat: OutputFormat,
        safeDirectory: String,
        pending: Boolean,
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, outputFormat.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$safeDirectory")
                if (pending) put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = try {
            resolver.insert(collection, values)
                ?: throw GalleryInsertException(IllegalStateException("MediaStore insert returned null"))
        } catch (error: Throwable) {
            if (error is SecurityException || error is OutOfMemoryError || error is ExportStorageException) throw error
            throw GalleryInsertException(error)
        }
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                encodedFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw GalleryWriteException(IllegalStateException("MediaStore output stream is unavailable"))
            return uri
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
                .onFailure(error::addSuppressed)
            throw if (error is ExportStorageException) error else GalleryWriteException(error)
        }
    }

    private fun isPending(context: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.IS_PENDING),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) != 0
        } ?: true
    }

    private data class EncodedImage(
        val file: File,
        val displayName: String,
        val outputFormat: OutputFormat,
    )

    companion object {
        const val DEFAULT_WEBP_QUALITY = 92
        private const val STAGING_DIRECTORY = "keyxif_export"
        private const val STALE_FILE_AGE_MS = 24L * 60L * 60L * 1_000L
    }
}

internal open class ExportStorageException(
    message: String,
    cause: Throwable,
) : IOException(message, cause)

internal class ImageEncodingException(cause: Throwable) :
    ExportStorageException("이미지 형식 변환에 실패했습니다.", cause)

internal class TemporaryStorageException(cause: Throwable) :
    ExportStorageException("이미지 저장을 준비할 공간이 부족합니다.", cause)

internal class GalleryInsertException(cause: Throwable) :
    ExportStorageException("갤러리에 저장 항목을 만들 수 없습니다.", cause)

internal class GalleryWriteException(cause: Throwable) :
    ExportStorageException("갤러리에 이미지 데이터를 기록하지 못했습니다.", cause)

internal fun String.withExtension(extension: String): String =
    "${substringBeforeLast('.', this)}.$extension"

private val OutputFormat.extension: String
    get() = when (this) {
        OutputFormat.WEBP -> "webp"
        OutputFormat.PNG -> "png"
    }

private val OutputFormat.mimeType: String
    get() = when (this) {
        OutputFormat.WEBP -> "image/webp"
        OutputFormat.PNG -> "image/png"
    }

private val OutputFormat.compatibleCompressFormats: List<Bitmap.CompressFormat>
    get() = when (this) {
        OutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            listOf(Bitmap.CompressFormat.WEBP_LOSSY, Bitmap.CompressFormat.WEBP)
        } else {
            @Suppress("DEPRECATION")
            listOf(Bitmap.CompressFormat.WEBP)
        }
        OutputFormat.PNG -> listOf(Bitmap.CompressFormat.PNG)
    }
