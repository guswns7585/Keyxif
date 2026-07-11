package com.keyxif.app.domain.export

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
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
import com.keyxif.app.data.exported.ExportedImageRepository
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.ExportedImage
import com.keyxif.app.domain.model.PhotoItem
import com.keyxif.app.domain.model.meaningfulBuildTextOrNull
import com.keyxif.app.domain.renderer.KeyxifCanvasRenderer
import com.keyxif.app.util.BitmapUtils
import com.keyxif.app.util.FileNameUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val renderer = KeyxifCanvasRenderer()
    private val exporter = ImageExporter()
    private val exportedImageRepository = ExportedImageRepository(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createNotificationChannel()
        val payloadPath = inputData.getString(KEY_PAYLOAD_PATH) ?: return@withContext Result.failure(
            workDataOf(KEY_MESSAGE to "저장 요청 정보를 찾을 수 없습니다."),
        )
        val payloadFile = File(payloadPath)
        val payload = runCatching {
            ExportWorkPayloadCodec.decode(payloadFile.readText(Charsets.UTF_8))
        }.getOrElse { error ->
            return@withContext Result.failure(workDataOf(KEY_MESSAGE to (error.message ?: "저장 요청을 읽을 수 없습니다.")))
        }
        val total = payload.photos.size
        if (total == 0) {
            return@withContext Result.failure(workDataOf(KEY_MESSAGE to "저장할 사진이 없습니다."))
        }

        var success = 0
        var failure = 0
        var current = 0
        var lastSavedUri: Uri? = null
        val failedIds = mutableListOf<String>()
        val photoIds = payload.photos.map { it.id }

        try {
            setForeground(createForegroundInfo(current, total, success, failure, "Keyxif 저장 준비 중"))
            setProgress(progressData(current, total, success, failure, "Keyxif 저장 준비 중"))

            for ((index, photo) in payload.photos.withIndex()) {
                current = index + 1
                val progressMessage = "$current / $total 처리 중"
                setForeground(createForegroundInfo(current, total, success, failure, progressMessage))
                setProgress(progressData(current, total, success, failure, progressMessage, photo.id))

                val saved = runCatching {
                    savePhoto(
                        photo = photo,
                        index = current,
                        payload = payload,
                    )
                }.onSuccess { result ->
                    lastSavedUri = result.uri
                    exportedImageRepository.add(result.exportedImage)
                }.isSuccess

                if (saved) {
                    success++
                } else {
                    failure++
                    failedIds += photo.id
                    if (!payload.settings.skipFailedOnBatchSave) break
                }
                setProgress(progressData(current, total, success, failure, "$current / $total 처리 중", photo.id))
            }

            val message = "저장 완료: 성공 ${success}장, 실패 ${failure}장"
            setProgress(progressData(current, total, success, failure, message))
            postCompletionNotification(success, failure, message)
            Result.success(
                workDataOf(
                    KEY_CURRENT to current,
                    KEY_TOTAL to total,
                    KEY_SUCCESS_COUNT to success,
                    KEY_FAILURE_COUNT to failure,
                    KEY_MESSAGE to message,
                    KEY_PHOTO_IDS to JSONArray(photoIds).toString(),
                    KEY_FAILED_IDS to JSONArray(failedIds).toString(),
                    KEY_SAVED_URI to lastSavedUri?.toString(),
                ),
            )
        } finally {
            payloadFile.parentFile?.deleteRecursively()
        }
    }

    private fun savePhoto(
        photo: PhotoItem,
        index: Int,
        payload: ExportWorkPayload,
    ): SavedExportResult {
        var renderedBitmap: android.graphics.Bitmap? = null
        return try {
            val bitmap = renderer.render(
                context = applicationContext,
                photo = photo,
                template = payload.template,
                settings = payload.settings,
                maxLongSide = saveLongSide(payload.settings),
            )
            renderedBitmap = bitmap
            val name = FileNameUtils.outputName(photo.buildInfo, index, payload.settings)
            val uri = exporter.saveImage(applicationContext, bitmap, name, payload.settings)
            SavedExportResult(
                uri = uri,
                exportedImage = ExportedImage(
                    id = "${System.currentTimeMillis()}-${photo.id}-$index",
                    uri = uri.toString(),
                    fileName = name,
                    createdAt = System.currentTimeMillis(),
                    width = bitmap.width,
                    height = bitmap.height,
                    fileSizeBytes = savedFileSize(uri),
                    templateName = payload.template.name,
                    housing = photo.buildInfo.housing.meaningfulBuildTextOrNull(),
                    switchName = photo.buildInfo.switchName.meaningfulBuildTextOrNull(),
                    keycap = photo.buildInfo.keycap.meaningfulBuildTextOrNull(),
                    nickname = photo.buildInfo.nickname.meaningfulBuildTextOrNull(),
                    paletteColors = photo.analysisResult.paletteColors,
                ),
            )
        } finally {
            renderedBitmap?.recycle()
        }
    }

    private fun savedFileSize(uri: Uri): Long {
        return runCatching {
            applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length
            } ?: 0L
        }.getOrDefault(0L).coerceAtLeast(0L)
    }

    private fun saveLongSide(settings: AppSettings): Int {
        return if (settings.keepOriginalResolution) {
            Int.MAX_VALUE
        } else {
            settings.maxLongSidePx ?: BitmapUtils.SAVE_LONG_SIDE_LIMIT
        }
    }

    private fun progressData(
        current: Int,
        total: Int,
        success: Int,
        failure: Int,
        message: String,
        currentPhotoId: String? = null,
    ) = workDataOf(
        KEY_CURRENT to current,
        KEY_TOTAL to total,
        KEY_SUCCESS_COUNT to success,
        KEY_FAILURE_COUNT to failure,
        KEY_MESSAGE to message,
        KEY_CURRENT_PHOTO_ID to currentPhotoId,
    )

    private fun createForegroundInfo(
        current: Int,
        total: Int,
        success: Int,
        failure: Int,
        message: String,
    ): ForegroundInfo {
        val notification = notificationBuilder()
            .setContentTitle("Keyxif 저장 중")
            .setContentText(message)
            .setProgress(total.coerceAtLeast(1), current.coerceIn(0, total.coerceAtLeast(1)), false)
            .setSubText("성공 $success · 실패 $failure")
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

    private fun postCompletionNotification(
        success: Int,
        failure: Int,
        message: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = notificationBuilder()
            .setContentTitle("Keyxif 저장 완료")
            .setContentText(message)
            .setSubText("성공 $success · 실패 $failure")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private data class SavedExportResult(
        val uri: Uri,
        val exportedImage: ExportedImage,
    )

    companion object {
        const val UNIQUE_WORK_NAME = "keyxif_export_work"
        const val CHANNEL_ID = "keyxif_export"
        const val CHANNEL_NAME = "Keyxif Export"
        const val KEY_PAYLOAD_PATH = "payload_path"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_SUCCESS_COUNT = "success_count"
        const val KEY_FAILURE_COUNT = "failure_count"
        const val KEY_MESSAGE = "message"
        const val KEY_CURRENT_PHOTO_ID = "current_photo_id"
        const val KEY_PHOTO_IDS = "photo_ids"
        const val KEY_FAILED_IDS = "failed_ids"
        const val KEY_SAVED_URI = "saved_uri"
        private const val NOTIFICATION_ID = 2407

        fun request(payloadPath: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(workDataOf(KEY_PAYLOAD_PATH to payloadPath))
                .build()
        }
    }
}
