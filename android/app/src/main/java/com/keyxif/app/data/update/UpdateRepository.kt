package com.keyxif.app.data.update

import android.content.Context
import com.keyxif.app.domain.model.UpdateInfo
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UpdateRepository(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("keyxif_update", Context.MODE_PRIVATE)

    fun lastCheckedAt(): Long? {
        return preferences.getLong(KEY_LAST_CHECKED_AT, 0L).takeIf { it > 0L }
    }

    fun markChecked(timeMillis: Long = System.currentTimeMillis()) {
        preferences.edit().putLong(KEY_LAST_CHECKED_AT, timeMillis).apply()
    }

    fun isPlaceholderUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.isBlank() ||
            trimmed.contains("example.com", ignoreCase = true) ||
            trimmed == "https://"
    }

    suspend fun fetchUpdateInfo(updateJsonUrl: String): UpdateInfo = withContext(Dispatchers.IO) {
        require(!isPlaceholderUrl(updateJsonUrl)) { "업데이트 JSON URL이 설정되지 않았습니다." }
        val connection = (URL(updateJsonUrl.trim()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000
            readTimeout = 7_000
            requestMethod = "GET"
            useCaches = false
        }
        try {
            val statusCode = connection.responseCode
            require(statusCode in 200..299) { "업데이트 정보를 가져오지 못했습니다. ($statusCode)" }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(body).toUpdateInfo()
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toUpdateInfo(): UpdateInfo {
        val latestVersionCode = optInt("latestVersionCode", 0)
        require(latestVersionCode > 0) { "latestVersionCode가 올바르지 않습니다." }
        val apkUrl = optString("apkUrl").trim()
        require(apkUrl.isNotBlank()) { "apkUrl이 비어 있습니다." }
        return UpdateInfo(
            latestVersionCode = latestVersionCode,
            latestVersionName = optString("latestVersionName").ifBlank { latestVersionCode.toString() },
            minRequiredVersionCode = optInt("minRequiredVersionCode", 1).coerceAtLeast(1),
            title = optString("title").ifBlank { "새 버전이 있습니다" },
            message = optString("message"),
            apkUrl = apkUrl,
            releaseNoteUrl = optNullableString("releaseNoteUrl"),
            forceUpdate = optBoolean("forceUpdate", false),
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
    }

    private companion object {
        const val KEY_LAST_CHECKED_AT = "last_checked_at"
    }
}
