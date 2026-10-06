package com.keyxif.app.domain.export

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.keyxif.app.BuildConfig
import com.keyxif.app.domain.model.PhotoItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal object ExportDiagnosticReporter {
    private const val DIRECTORY = "export_diagnostics"
    private const val RETENTION_MS = 30L * 24L * 60L * 60L * 1_000L

    fun appendFailure(
        context: Context,
        existingFile: File?,
        failureInfo: ExportFailureInfo,
        error: Throwable,
        photo: PhotoItem,
        payload: ExportWorkPayload,
        attemptedLongSide: Int,
        itemIndex: Int,
    ): File? = runCatching {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        cleanup(directory)
        val report = existingFile ?: File(
            directory,
            "keyxif-export-${timestampForFile()}-${UUID.randomUUID().toString().take(8)}.txt",
        ).also { file ->
            file.writeText(buildHeader(context, payload), Charsets.UTF_8)
        }
        report.appendText(
            buildFailureSection(
                failureInfo = failureInfo,
                error = error,
                photo = photo,
                attemptedLongSide = attemptedLongSide,
                itemIndex = itemIndex,
            ),
            Charsets.UTF_8,
        )
        report
    }.getOrNull()

    private fun buildHeader(context: Context, payload: ExportWorkPayload): String = buildString {
        appendLine("Keyxif export diagnostic report")
        appendLine("Generated: ${timestampForDisplay()}")
        appendLine("Report contains no photo pixels, build text, file names, or full content URIs.")
        appendLine()
        appendLine("[App]")
        appendLine("versionName=${BuildConfig.VERSION_NAME}")
        appendLine("versionCode=${BuildConfig.VERSION_CODE}")
        appendLine("buildType=${BuildConfig.BUILD_TYPE}")
        appendLine("template=${payload.customTemplate?.name?.let { "custom" } ?: payload.template.name}")
        appendLine("format=${payload.settings.outputFormat.name}")
        appendLine("quality=${payload.settings.webpQuality}")
        appendLine("requestedLongSide=${payload.settings.maxLongSidePx ?: "default"}")
        appendLine("keepOriginalResolution=${payload.settings.keepOriginalResolution}")
        appendLine("batchCount=${payload.photos.size}")
        appendLine()
        appendLine("[Device]")
        appendLine("manufacturer=${Build.MANUFACTURER}")
        appendLine("brand=${Build.BRAND}")
        appendLine("model=${Build.MODEL}")
        appendLine("device=${Build.DEVICE}")
        appendLine("sdk=${Build.VERSION.SDK_INT}")
        appendLine("release=${Build.VERSION.RELEASE}")
        appendLine("fingerprint=${Build.FINGERPRINT}")
        appendLine()
        appendLine("[Storage]")
        appendLine("externalState=${Environment.getExternalStorageState()}")
        appendLine("files=${storageSummary(context.filesDir)}")
        appendLine("cache=${storageSummary(context.cacheDir)}")
        appendLine("pictures=${storageSummary(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES))}")
        appendLine("persistedUriPermissions=${context.contentResolver.persistedUriPermissions.size}")
        appendLine()
    }

    private fun buildFailureSection(
        failureInfo: ExportFailureInfo,
        error: Throwable,
        photo: PhotoItem,
        attemptedLongSide: Int,
        itemIndex: Int,
    ): String = buildString {
        appendLine("[Failure $itemIndex]")
        appendLine("time=${timestampForDisplay()}")
        appendLine("code=${failureInfo.code.value}")
        appendLine("message=${failureInfo.message}")
        appendLine("sourceScheme=${photo.uri.scheme.orEmpty()}")
        appendLine("sourceAuthority=${photo.uri.authority.orEmpty()}")
        appendLine("attemptedLongSide=$attemptedLongSide")
        appendLine("analysisColorCount=${photo.analysisResult.paletteColors.size}")
        appendLine("exceptionChain=${exceptionChain(error)}")
        appendLine("stackTrace:")
        appendLine(sanitize(error.stackTraceToString()).lineSequence().take(80).joinToString("\n"))
        appendLine()
    }

    private fun exceptionChain(error: Throwable): String {
        val seen = mutableSetOf<Throwable>()
        val entries = mutableListOf<String>()
        fun visit(current: Throwable, label: String) {
            if (!seen.add(current) || entries.size >= 24) return
            entries += "$label${current::class.java.name}: ${sanitize(current.message.orEmpty())}"
            current.suppressed.forEachIndexed { index, suppressed -> visit(suppressed, "suppressed[$index] ") }
            current.cause?.let { visit(it, "cause ") }
        }
        visit(error, "")
        return entries.joinToString(" | ")
    }

    private fun storageSummary(file: File?): String {
        if (file == null) return "unavailable"
        return runCatching {
            val stat = StatFs(file.absolutePath)
            "available=${stat.availableBytes}, total=${stat.totalBytes}, writable=${file.canWrite()}"
        }.getOrElse { "unavailable:${it::class.java.simpleName}" }
    }

    private fun cleanup(directory: File) {
        val now = System.currentTimeMillis()
        directory.listFiles()?.forEach { file ->
            if (now - file.lastModified() > RETENTION_MS) file.delete()
        }
    }

    private fun sanitize(value: String): String {
        return value
            .replace(Regex("content://[^\\s]+"), "content://<redacted>")
            .replace(Regex("file:/+[^\\s]+"), "file://<redacted>")
            .replace(Regex("[A-Za-z]:\\\\[^\\r\\n\\t ]+"), "<local-path>")
    }

    private fun timestampForFile(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun timestampForDisplay(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
}
