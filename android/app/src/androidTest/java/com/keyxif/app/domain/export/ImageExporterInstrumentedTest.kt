package com.keyxif.app.domain.export

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keyxif.app.domain.model.AppSettings
import com.keyxif.app.domain.model.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageExporterInstrumentedTest {
    @Test
    fun savesAndPublishesImageThroughMediaStore() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(32, 96, 160))
        }
        val result = try {
            ImageExporter().saveImage(
                context = context,
                bitmap = bitmap,
                displayName = "Keyxif_instrumented_test.webp",
                settings = AppSettings(
                    outputFormat = OutputFormat.WEBP,
                    saveDirectoryName = "KeyxifTest",
                ),
            )
        } finally {
            bitmap.recycle()
        }

        try {
            assertEquals("Keyxif_instrumented_test.webp", result.displayName)
            assertEquals(OutputFormat.WEBP, result.outputFormat)
            val projection = buildList {
                add(MediaStore.Images.Media.DISPLAY_NAME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Images.Media.IS_PENDING)
                }
            }.toTypedArray()
            context.contentResolver.query(
                result.uri,
                projection,
                null,
                null,
                null,
            )!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(result.displayName, cursor.getString(0))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    assertEquals(0, cursor.getInt(1))
                }
            }
            val byteCount = context.contentResolver.openInputStream(result.uri)?.use { input ->
                input.readBytes().size
            } ?: 0
            assertTrue(byteCount > 0)
        } finally {
            context.contentResolver.delete(result.uri, null, null)
        }
    }

    @Test
    fun repeatedlyPublishesImagesWithoutLeavingPendingItems() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(120, 56, 32))
        }
        try {
            repeat(40) { index ->
                val result = ImageExporter().saveImage(
                    context = context,
                    bitmap = bitmap,
                    displayName = "Keyxif_batch_${index.toString().padStart(2, '0')}.webp",
                    settings = AppSettings(
                        outputFormat = OutputFormat.WEBP,
                        saveDirectoryName = "KeyxifTest",
                    ),
                )
                try {
                    assertTrue(
                        context.contentResolver.openAssetFileDescriptor(result.uri, "r")?.use {
                            it.length > 0L
                        } == true,
                    )
                } finally {
                    context.contentResolver.delete(result.uri, null, null)
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun publishesFourKWebpWithoutChangingFormat() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(4096, 2304, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(28, 72, 96))
        }
        val result = try {
            ImageExporter().saveImage(
                context = context,
                bitmap = bitmap,
                displayName = "Keyxif_4k_test.webp",
                settings = AppSettings(
                    outputFormat = OutputFormat.WEBP,
                    saveDirectoryName = "KeyxifTest",
                ),
            )
        } finally {
            bitmap.recycle()
        }

        try {
            assertEquals(OutputFormat.WEBP, result.outputFormat)
            assertTrue(result.displayName.endsWith(".webp"))
            assertTrue(
                context.contentResolver.openAssetFileDescriptor(result.uri, "r")?.use {
                    it.length > 0L
                } == true,
            )
        } finally {
            context.contentResolver.delete(result.uri, null, null)
        }
    }
}
