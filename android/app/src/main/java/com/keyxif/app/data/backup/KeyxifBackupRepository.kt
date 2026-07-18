package com.keyxif.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.keyxif.app.data.exported.ExportedImageRepository
import com.keyxif.app.data.repository.BuildPresetRepository
import com.keyxif.app.data.repository.RecentSnapshot
import com.keyxif.app.data.repository.RecentStore
import com.keyxif.app.domain.export.ExportWorkPayload
import com.keyxif.app.domain.export.ExportWorkPayloadCodec
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.BuildPreset
import com.keyxif.app.domain.model.CardTemplate
import com.keyxif.app.domain.model.ExportedImage
import com.keyxif.app.domain.model.KeyboardBuildInfo
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class KeyxifBackupRepository(
    private val context: Context,
) {
    private val presetRepository = BuildPresetRepository(context)
    private val recentStore = RecentStore(context)
    private val exportedImageRepository = ExportedImageRepository(context)

    suspend fun create(destination: Uri, settings: AppSettings): BackupSummary = withContext(Dispatchers.IO) {
        val presets = presetRepository.getAll()
        val recents = recentStore.snapshot()
        val images = exportedImageRepository.getAll()
        val manifest = JSONObject().apply {
            put("format", BACKUP_FORMAT)
            put("version", BACKUP_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("settings", settingsToJson(settings))
            put("recents", recents.toJson())
        }
        var backedUpImages = 0
        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                manifest.put("presets", JSONArray().apply {
                    presets.forEachIndexed { index, preset ->
                        val logoEntry = preset.buildInfo.customLogoUri
                            ?.takeIf { copyUriToZip(it, "logos/$index.bin", zip) }
                            ?.let { "logos/$index.bin" }
                        put(preset.toJson(logoEntry))
                    }
                })
                manifest.put("exportedImages", JSONArray().apply {
                    images.forEachIndexed { index, image ->
                        val extension = image.fileName.substringAfterLast('.', "bin").take(8)
                        val entryName = "images/$index.$extension"
                        val copied = copyUriToZip(Uri.parse(image.uri), entryName, zip)
                        if (copied) backedUpImages++
                        put(image.toJson(if (copied) entryName else null))
                    }
                })
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        } ?: error("백업 파일을 만들 수 없습니다.")
        BackupSummary(presets.size, recents.totalCount(), backedUpImages, images.size - backedUpImages)
    }

    suspend fun restore(source: Uri): RestoreSummary = withContext(Dispatchers.IO) {
        val restoreRoot = File(context.cacheDir, "backup-restore-${UUID.randomUUID()}")
        restoreRoot.mkdirs()
        try {
            extractArchive(source, restoreRoot)
            val manifestFile = File(restoreRoot, MANIFEST_ENTRY)
            check(manifestFile.isFile) { "Keyxif 백업 정보가 없습니다." }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            check(manifest.optString("format") == BACKUP_FORMAT) { "Keyxif 백업 파일이 아닙니다." }
            check(manifest.optInt("version") in 1..BACKUP_VERSION) { "지원하지 않는 백업 버전입니다." }

            val settings = jsonToSettings(manifest.optJSONObject("settings") ?: JSONObject())
            val presets = manifest.optJSONArray("presets").toPresets(restoreRoot)
            val recents = manifest.optJSONObject("recents").toRecentSnapshot()
            val restoredImages = manifest.optJSONArray("exportedImages").restoreImages(restoreRoot)

            presetRepository.mergeAll(presets)
            recentStore.merge(recents)
            exportedImageRepository.addAll(restoredImages)
            RestoreSummary(settings, presets.size, recents.totalCount(), restoredImages.size)
        } finally {
            restoreRoot.deleteRecursively()
        }
    }

    private fun copyUriToZip(uri: Uri, entryName: String, zip: ZipOutputStream): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                zip.putNextEntry(ZipEntry(entryName))
                input.copyTo(zip)
                zip.closeEntry()
            } ?: error("파일을 열 수 없습니다.")
        }.isSuccess
    }

    private fun extractArchive(source: Uri, root: File) {
        val rootPath = root.canonicalFile.toPath()
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val output = File(root, entry.name).canonicalFile
                    check(output.toPath().startsWith(rootPath)) { "잘못된 백업 파일 경로입니다." }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("백업 파일을 열 수 없습니다.")
    }

    private fun settingsToJson(settings: AppSettings): JSONObject {
        return ExportWorkPayloadCodec.encode(
            ExportWorkPayload(emptyList(), CardTemplate.ClassicFrame, settings),
        ).getJSONObject("settings")
    }

    private fun jsonToSettings(json: JSONObject): AppSettings {
        val wrapper = JSONObject().apply {
            put("template", CardTemplate.ClassicFrame.name)
            put("settings", json)
            put("photos", JSONArray())
        }
        return ExportWorkPayloadCodec.decode(wrapper.toString()).settings
    }

    private fun BuildPreset.toJson(customLogoEntry: String?): JSONObject = JSONObject().apply {
        put("id", id)
        put("presetName", presetName)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("customLogoEntry", customLogoEntry ?: JSONObject.NULL)
        put("buildInfo", buildInfo.toJson())
    }

    private fun KeyboardBuildInfo.toJson(): JSONObject = JSONObject().apply {
        put("housing", housing)
        put("switchName", switchName)
        put("plate", plate)
        put("mount", mount)
        put("keycap", keycap)
        put("nickname", nickname)
        put("logoId", logoId ?: JSONObject.NULL)
        put("logoDisabled", logoDisabled)
    }

    private fun JSONArray?.toPresets(root: File): List<BuildPreset> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val info = json.optJSONObject("buildInfo") ?: JSONObject()
                val customLogoUri = json.optNullableString("customLogoEntry")?.let { entry ->
                    val source = safeArchiveFile(root, entry).takeIf(File::isFile) ?: return@let null
                    val targetDir = File(context.filesDir, "restored_logos").apply { mkdirs() }
                    val target = File(targetDir, "${UUID.randomUUID()}.bin")
                    source.copyTo(target, overwrite = true)
                    Uri.fromFile(target)
                }
                add(
                    BuildPreset(
                        id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                        presetName = json.optString("presetName").ifBlank { "복원된 프리셋" },
                        buildInfo = KeyboardBuildInfo(
                            housing = info.optString("housing"),
                            switchName = info.optString("switchName"),
                            plate = info.optString("plate"),
                            mount = info.optString("mount"),
                            keycap = info.optString("keycap"),
                            nickname = info.optString("nickname"),
                            logoId = info.optNullableString("logoId"),
                            customLogoUri = customLogoUri,
                            logoDisabled = info.optBoolean("logoDisabled", false),
                        ),
                        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                    ),
                )
            }
        }
    }

    private fun RecentSnapshot.toJson(): JSONObject = JSONObject().apply {
        put("housing", JSONArray(housing))
        put("switches", JSONArray(switches))
        put("keycaps", JSONArray(keycaps))
        put("nicknames", JSONArray(nicknames))
    }

    private fun JSONObject?.toRecentSnapshot(): RecentSnapshot = RecentSnapshot(
        housing = this?.optJSONArray("housing").toStrings(),
        switches = this?.optJSONArray("switches").toStrings(),
        keycaps = this?.optJSONArray("keycaps").toStrings(),
        nicknames = this?.optJSONArray("nicknames").toStrings(),
    )

    private fun ExportedImage.toJson(backupEntry: String?): JSONObject = JSONObject().apply {
        put("id", id)
        put("fileName", fileName)
        put("createdAt", createdAt)
        put("width", width)
        put("height", height)
        put("fileSizeBytes", fileSizeBytes)
        put("templateName", templateName)
        put("housing", housing ?: JSONObject.NULL)
        put("switchName", switchName ?: JSONObject.NULL)
        put("keycap", keycap ?: JSONObject.NULL)
        put("nickname", nickname ?: JSONObject.NULL)
        put("paletteColors", JSONArray(paletteColors))
        put("mimeType", context.contentResolver.getType(Uri.parse(uri)) ?: mimeTypeFor(fileName))
        put("backupEntry", backupEntry ?: JSONObject.NULL)
    }

    private fun JSONArray?.restoreImages(root: File): List<ExportedImage> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val entry = json.optNullableString("backupEntry") ?: continue
                val source = safeArchiveFile(root, entry).takeIf(File::isFile) ?: continue
                val fileName = json.optString("fileName").ifBlank { "Keyxif_restored_$index.webp" }
                val mimeType = json.optString("mimeType").ifBlank { mimeTypeFor(fileName) }
                val uri = runCatching { restoreImageFile(source, fileName, mimeType) }.getOrNull() ?: continue
                add(
                    ExportedImage(
                        id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                        uri = uri.toString(),
                        fileName = fileName,
                        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                        width = json.optInt("width"),
                        height = json.optInt("height"),
                        fileSizeBytes = source.length(),
                        templateName = json.optString("templateName"),
                        housing = json.optNullableString("housing"),
                        switchName = json.optNullableString("switchName"),
                        keycap = json.optNullableString("keycap"),
                        nickname = json.optNullableString("nickname"),
                        paletteColors = json.optJSONArray("paletteColors").toInts(),
                    ),
                )
            }
        }
    }

    private fun restoreImageFile(source: File, fileName: String, mimeType: String): Uri {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName.substringAfterLast('/').substringAfterLast('\\'))
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Keyxif")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("복원 이미지 항목을 만들 수 없습니다.")
        runCatching {
            resolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                ?: error("복원 이미지 스트림을 열 수 없습니다.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }.onFailure {
            resolver.delete(uri, null, null)
            throw it
        }
        return uri
    }

    private fun safeArchiveFile(root: File, entry: String): File {
        val rootPath = root.canonicalFile.toPath()
        val file = File(root, entry).canonicalFile
        check(file.toPath().startsWith(rootPath)) { "잘못된 백업 파일 경로입니다." }
        return file
    }

    private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }

    private fun JSONArray?.toInts(): List<Int> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) add(optInt(index))
    }.filter { it != 0 }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun RecentSnapshot.totalCount(): Int = housing.size + switches.size + keycaps.size + nicknames.size

    private fun mimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "image/webp"
    }

    private companion object {
        const val BACKUP_FORMAT = "keyxif-backup"
        const val BACKUP_VERSION = 1
        const val MANIFEST_ENTRY = "manifest.json"
    }
}

data class BackupSummary(
    val presetCount: Int,
    val recentCount: Int,
    val imageCount: Int,
    val skippedImageCount: Int,
)

data class RestoreSummary(
    val settings: AppSettings,
    val presetCount: Int,
    val recentCount: Int,
    val imageCount: Int,
)
