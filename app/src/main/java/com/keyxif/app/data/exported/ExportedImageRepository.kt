package com.keyxif.app.data.exported

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keyxif.app.domain.model.ExportedImage
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.keyxifExportedImagesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "keyxif_exported_images",
)

class ExportedImageRepository(
    private val context: Context,
) {
    val exportedImagesFlow: Flow<List<ExportedImage>> = context.keyxifExportedImagesDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            decode(preferences[Keys.EXPORTED_IMAGES_JSON].orEmpty())
        }

    suspend fun getAll(): List<ExportedImage> = exportedImagesFlow.first()

    suspend fun add(image: ExportedImage) {
        context.keyxifExportedImagesDataStore.edit { preferences ->
            val current = decode(preferences[Keys.EXPORTED_IMAGES_JSON].orEmpty())
            val next = (listOf(image) + current.filterNot { it.id == image.id })
                .sortedByDescending { it.createdAt }
            preferences[Keys.EXPORTED_IMAGES_JSON] = encode(next).toString()
        }
    }

    suspend fun remove(id: String) {
        context.keyxifExportedImagesDataStore.edit { preferences ->
            val next = decode(preferences[Keys.EXPORTED_IMAGES_JSON].orEmpty()).filterNot { it.id == id }
            preferences[Keys.EXPORTED_IMAGES_JSON] = encode(next).toString()
        }
    }

    suspend fun clear() {
        context.keyxifExportedImagesDataStore.edit { preferences ->
            preferences[Keys.EXPORTED_IMAGES_JSON] = JSONArray().toString()
        }
    }

    suspend fun pruneMissing(): Int {
        var removed = 0
        context.keyxifExportedImagesDataStore.edit { preferences ->
            val current = decode(preferences[Keys.EXPORTED_IMAGES_JSON].orEmpty())
            val existing = current.filter { it.canOpen() }
            removed = current.size - existing.size
            preferences[Keys.EXPORTED_IMAGES_JSON] = encode(existing).toString()
        }
        return removed
    }

    private fun ExportedImage.canOpen(): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { }
        }.isSuccess
    }

    private fun encode(images: List<ExportedImage>): JSONArray = JSONArray().apply {
        images.forEach { image -> put(image.toJson()) }
    }

    private fun decode(raw: String): List<ExportedImage> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toExportedImage())
                }
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    private fun ExportedImage.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("uri", uri)
        put("fileName", fileName)
        put("createdAt", createdAt)
        put("width", width)
        put("height", height)
        put("fileSizeBytes", fileSizeBytes)
        put("templateName", templateName)
        put("housing", housing)
        put("switchName", switchName)
        put("keycap", keycap)
        put("nickname", nickname)
        put(
            "paletteColors",
            JSONArray().apply {
                paletteColors.forEach(::put)
            },
        )
    }

    private fun JSONObject.toExportedImage(): ExportedImage {
        return ExportedImage(
            id = optString("id"),
            uri = optString("uri"),
            fileName = optString("fileName"),
            createdAt = optLong("createdAt"),
            width = optInt("width"),
            height = optInt("height"),
            fileSizeBytes = optLong("fileSizeBytes"),
            templateName = optString("templateName"),
            housing = optNullableString("housing"),
            switchName = optNullableString("switchName"),
            keycap = optNullableString("keycap"),
            nickname = optNullableString("nickname"),
            paletteColors = optJSONArray("paletteColors").toColorList(),
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).meaningfulBuildTextOrNull()
    }

    private fun JSONArray?.toColorList(): List<Int> {
        val array = this ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optInt(index))
            }
        }.filter { it != 0 }
    }

    private object Keys {
        val EXPORTED_IMAGES_JSON = stringPreferencesKey("exported_images_json")
    }
}
