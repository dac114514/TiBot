package com.faster.tibot.data.telegram

import android.content.Context
import android.util.Log
import com.faster.tibot.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "FileDownloader"
private const val MAX_FILE_SIZE = 20L * 1024 * 1024
private const val MAX_RETRY = 3
private const val CONCURRENT_LIMIT = 3

class FileDownloader(
    private val botClient: TelegramBotClient,
    private val context: Context,
) {
    private val inflight = ConcurrentHashMap<String, Deferred<String?>>()
    private val semaphore = Semaphore(CONCURRENT_LIMIT)

    fun ensureDownloaded(
        msg: TelegramMessage,
        scope: CoroutineScope,
    ): Deferred<String?> {
        if (msg.fileId.isBlank()) return scope.async { null }
        return inflight.getOrPut(msg.fileId) {
            scope.async {
                try {
                    semaphore.withPermit {
                        downloadWithRetry(msg)
                    }
                } finally {
                    inflight.remove(msg.fileId)
                }
            }
        }
    }

    private suspend fun downloadWithRetry(msg: TelegramMessage): String? {
        val destDir = File(context.filesDir, "media/${msg.chatId}").apply { mkdirs() }
        val safeName = FileUtils.sanitizeFilename(
            msg.fileName.ifBlank {
                "${msg.fileId}.${FileUtils.extFromMime(msg.mimeType)}"
            }
        )
        val finalFile = File(destDir, "${msg.messageId}_$safeName")
        if (finalFile.exists() && finalFile.length() > 0) {
            return finalFile.absolutePath
        }
        val tmpFile = File(destDir, "${msg.messageId}_$safeName.part")

        var attempt = 0
        while (attempt < MAX_RETRY) {
            attempt++
            try {
                val file = botClient.getFile(msg.fileId)
                if (file == null) {
                    Log.w(TAG, "getFile returned null for ${msg.fileId}")
                    continue
                }
                if (file.fileSize > MAX_FILE_SIZE) {
                    Log.w(TAG, "file too large: ${file.fileSize} > $MAX_FILE_SIZE")
                    return "too_large"
                }
                val ok = botClient.downloadFile(file.filePath, tmpFile)
                if (ok && tmpFile.length() > 0) {
                    if (finalFile.exists()) finalFile.delete()
                    if (tmpFile.renameTo(finalFile)) {
                        return finalFile.absolutePath
                    } else {
                        Log.e(TAG, "rename failed: ${tmpFile.path} -> ${finalFile.path}")
                    }
                } else {
                    Log.w(TAG, "download failed, attempt=$attempt")
                    tmpFile.delete()
                    if (attempt < MAX_RETRY) delay(1000L * (1L shl (attempt - 1)))
                }
            } catch (e: Exception) {
                Log.w(TAG, "attempt $attempt error: ${e.message}")
                tmpFile.delete()
                if (attempt < MAX_RETRY) delay(1000L * (1L shl (attempt - 1)))
            }
        }
        return null
    }
}
