package com.keyxif.app.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapUtilsInstrumentedTest {
    @Test
    fun modernDecoderRejectsTruncatedImagesInsteadOfRenderingCorruptPixels() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val complete = File(context.cacheDir, "keyxif-complete-test.jpg")
        val truncated = File(context.cacheDir, "keyxif-truncated-test.jpg")
        val bitmap = Bitmap.createBitmap(256, 192, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(38, 96, 142))
        }
        try {
            complete.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            val bytes = complete.readBytes()
            truncated.writeBytes(bytes.copyOf((bytes.size * 0.55f).toInt()))
            assertThrows(SourceImageDecodeException::class.java) {
                BitmapUtils.decodeOrientedBitmap(context, Uri.fromFile(truncated), 1024)
            }
        } finally {
            bitmap.recycle()
            complete.delete()
            truncated.delete()
        }
    }

    @Test
    fun modernDecoderReturnsStableArgbBitmap() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "keyxif-decode-test.png")
        val source = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(12, 34, 56))
        }
        try {
            file.outputStream().use { output -> source.compress(Bitmap.CompressFormat.PNG, 100, output) }
            val decoded = BitmapUtils.decodeOrientedBitmap(context, Uri.fromFile(file), 1024)
            try {
                assertEquals(Bitmap.Config.ARGB_8888, decoded.config)
                assertEquals(120, decoded.width)
                assertEquals(80, decoded.height)
            } finally {
                decoded.recycle()
            }
        } finally {
            source.recycle()
            file.delete()
        }
    }
}
