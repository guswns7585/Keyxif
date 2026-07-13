package com.keyxif.app.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.keyxif.app.MainActivity
import com.keyxif.app.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createNotificationChannel()
        val apkUrl = inputData.getString(KEY_APK_URL).orEmpty()
        val versionName = inputData.getString(KEY_VERSION_NAME).orEmpty().ifBlank { "latest" }
        val versionCode = inputData.getLong(KEY_VERSION_CODE, 0L)
        if (apkUrl.isBlank()) {
            return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to "APK URL이 비어 있습니다."))
        }

        val updateDir = File(applicationContext.cacheDir, "updates").apply {
            deleteRecursively()
            mkdirs()
        }
        val apkFile = File(updateDir, "keyxif-update-$versionName-$versionCode-$id.apk")

        runCatching {
            setForeground(createForegroundInfo(0, "0%"))
            setProgress(progressData(0, null, null))
            download(apkUrl, apkFile)
            check(apkFile.length() > 0L) { "다운로드된 APK 파일이 비어 있습니다." }
            validateDownloadedApk(apkFile, versionCode)
        }.onFailure { error ->
            postNotification("업데이트 다운로드 실패", error.message ?: "네트워크 상태를 확인해 주세요.", false)
            return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to (error.message ?: "업데이트 다운로드 실패")))
        }

        postNotification("Keyxif 업데이트 준비 완료", "설정을 열어 설치를 계속할 수 있습니다.", false)
        Result.success(
            workDataOf(
                KEY_PROGRESS_PERCENT to 100,
                KEY_APK_PATH to apkFile.absolutePath,
                KEY_APK_URL to apkUrl,
                KEY_VERSION_CODE to versionCode,
            ),
        )
    }

    private fun download(
        apkUrl: String,
        target: File,
    ) {
        val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache, no-store")
            setRequestProperty("Pragma", "no-cache")
        }
        try {
            val statusCode = connection.responseCode
            require(statusCode in 200..299) { "APK를 다운로드할 수 없습니다. ($statusCode)" }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            var downloadedBytes = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val percent = totalBytes?.let {
                            ((downloadedBytes * 100L) / it).toInt().coerceIn(0, 100)
                        }
                        if (percent != null && percent != lastPercent) {
                            lastPercent = percent
                            setProgressAsync(progressData(percent, null, target.absolutePath))
                            setForegroundAsync(createForegroundInfo(percent, "$percent%"))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun validateDownloadedApk(
        apkFile: File,
        expectedVersionCode: Long,
    ) {
        val packageInfo = applicationContext.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: error("다운로드한 파일이 올바른 APK가 아닙니다.")
        check(packageInfo.packageName == applicationContext.packageName) {
            "다른 앱의 APK가 다운로드되었습니다."
        }
        val downloadedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        check(expectedVersionCode <= 0L || downloadedVersionCode == expectedVersionCode) {
            "다운로드한 APK 버전이 일치하지 않습니다. (예상 $expectedVersionCode, 실제 $downloadedVersionCode)"
        }
    }

    private fun progressData(
        progressPercent: Int?,
        errorMessage: String?,
        apkPath: String?,
    ) = workDataOf(
        KEY_PROGRESS_PERCENT to (progressPercent ?: -1),
        KEY_ERROR_MESSAGE to errorMessage,
        KEY_APK_PATH to apkPath,
    )

    private fun createForegroundInfo(
        progress: Int,
        text: String,
    ): ForegroundInfo {
        val notification = notificationBuilder()
            .setContentTitle("Keyxif 업데이트 다운로드 중")
            .setContentText(text)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun postNotification(
        title: String,
        text: String,
        ongoing: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = notificationBuilder()
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(0, 0, false)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun notificationBuilder(): NotificationCompat.Builder {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_keyxif_notification)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "keyxif_update_download_work"
        const val CHANNEL_ID = "keyxif_update"
        const val CHANNEL_NAME = "Keyxif Update"
        const val KEY_APK_URL = "apk_url"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_APK_PATH = "apk_path"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val NOTIFICATION_ID = 2507

        fun request(
            apkUrl: String,
            versionName: String,
            versionCode: Long,
        ): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_APK_URL to apkUrl,
                        KEY_VERSION_NAME to versionName,
                        KEY_VERSION_CODE to versionCode,
                    ),
                )
                .build()
        }
    }
}
