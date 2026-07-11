package com.keyxif.app.util

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build

object IntentShareUtils {
    fun extractImageUris(
        intent: Intent?,
        contentResolver: ContentResolver,
    ): List<Uri> {
        val shareIntent = intent ?: return emptyList()
        if (shareIntent.action != Intent.ACTION_SEND && shareIntent.action != Intent.ACTION_SEND_MULTIPLE) {
            return emptyList()
        }
        if (shareIntent.type?.startsWith("image/") != true) return emptyList()

        val streams = when (shareIntent.action) {
            Intent.ACTION_SEND -> listOfNotNull(shareIntent.streamUri())
            Intent.ACTION_SEND_MULTIPLE -> shareIntent.streamUris()
            else -> emptyList()
        }
        val clipUris = buildList {
            val clipData = shareIntent.clipData ?: return@buildList
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index).uri?.let(::add)
            }
        }
        return (streams + clipUris)
            .distinctBy(Uri::toString)
            .filter { uri ->
                val resolvedType = runCatching { contentResolver.getType(uri) }.getOrNull()
                resolvedType == null || resolvedType.startsWith("image/")
            }
    }

    private fun Intent.streamUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun Intent.streamUris(): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    }
}
