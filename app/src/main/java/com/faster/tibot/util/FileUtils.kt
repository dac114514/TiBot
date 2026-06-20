package com.faster.tibot.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    private const val SENT_SUBDIR = "sent"
    private const val BUFFER_SIZE = 8 * 1024

    suspend fun copyToCache(
        context: Context,
        uri: Uri,
        subDir: String = SENT_SUBDIR,
    ): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = queryDisplayName(context, uri)
            ?: "file_${System.currentTimeMillis()}.${extFromMime(resolver.getType(uri))}"
        val safeName = sanitizeFilename(fileName)
        val dir = File(context.cacheDir, subDir).apply { mkdirs() }
        val dest = File(dir, "${System.currentTimeMillis()}_$safeName")
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        } ?: error("Cannot open input stream for $uri")
        dest.absolutePath
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) { null }
    }

    fun extFromMime(mime: String?): String {
        if (mime.isNullOrBlank()) return "bin"
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
    }

    fun sanitizeFilename(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\u0000]"), "_")
            .take(120)
            .ifBlank { "file" }

    fun clearOldSentCache(context: Context, maxAgeMs: Long = 24L * 60 * 60 * 1000) {
        val dir = File(context.cacheDir, SENT_SUBDIR)
        if (!dir.isDirectory) return
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { f ->
            if (now - f.lastModified() > maxAgeMs) f.delete()
        }
    }
}
