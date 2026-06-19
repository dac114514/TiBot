package com.faster.tibot.data.rootfs

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

enum class DownloadState { DOWNLOADING, VERIFYING, EXTRACTING, DONE, ERROR }

data class DownloadProgress(
    val percent: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val state: DownloadState = DownloadState.DOWNLOADING,
    val error: String? = null,
)

class RootfsDownloadManager(private val context: Context) {

    fun buildDefaultMirrors(baseUrl: String): List<MirrorSource> = listOf(
        MirrorSource("github", "GitHub Releases", "$baseUrl/ubuntu-rootfs.tar.xz"),
        MirrorSource("ghproxy", "GHProxy 加速", "https://ghproxy.com/$baseUrl/ubuntu-rootfs.tar.xz"),
        MirrorSource("moeyy", "Moeyy 加速源", "https://moeyy.cn/gh-proxy/$baseUrl/ubuntu-rootfs.tar.xz"),
        MirrorSource("kkgithub", "kkgithub 加速", "https://kkgithub.com/$baseUrl/ubuntu-rootfs.tar.xz"),
    )

    suspend fun download(
        mirror: MirrorSource,
        destFile: File,
    ): Flow<DownloadProgress> = flow {
        emit(DownloadProgress(state = DownloadState.DOWNLOADING))

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(mirror.url))
            .setDestinationUri(Uri.fromFile(destFile))
            .setTitle("TiBot Ubuntu 环境")
            .setDescription("正在下载 ${mirror.name}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = dm.enqueue(request)
        var lastBytes = 0L
        var lastTime = System.currentTimeMillis()

        withContext(Dispatchers.IO) {
            var done = false
            while (!done) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                dm.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                done = true
                                emit(
                                    DownloadProgress(
                                        percent = 100,
                                        downloadedBytes = lastBytes,
                                        totalBytes = lastBytes,
                                        state = DownloadState.DONE,
                                    )
                                )
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(
                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                                )
                                done = true
                                emit(
                                    DownloadProgress(
                                        state = DownloadState.ERROR,
                                        error = "下载失败 (code=$reason)，镜像: ${mirror.name}",
                                    )
                                )
                            }
                            DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                                val total = cursor.getLong(
                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                )
                                val downloaded = cursor.getLong(
                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                )
                                val now = System.currentTimeMillis()
                                val timeDelta = (now - lastTime).coerceAtLeast(1)
                                val bytesDelta = downloaded - lastBytes
                                val speed = bytesDelta * 1000 / timeDelta
                                lastBytes = downloaded
                                lastTime = now
                                val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                                emit(
                                    DownloadProgress(
                                        percent = pct,
                                        downloadedBytes = downloaded,
                                        totalBytes = total,
                                        speedBytesPerSec = speed.coerceAtLeast(0),
                                        state = DownloadState.DOWNLOADING,
                                    )
                                )
                            }
                        }
                    }
                }
                if (!done) delay(500)
            }
        }
    }

    suspend fun verifySha256(file: File, expectedSha256: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                actual.equals(expectedSha256, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }

    suspend fun extractTarXz(
        tarXzFile: File,
        destDir: File,
    ): Flow<DownloadProgress> = flow {
        emit(DownloadProgress(percent = 0, state = DownloadState.EXTRACTING))

        withContext(Dispatchers.IO) {
            destDir.mkdirs()

            // First pass: count entries for progress tracking
            var totalEntries = 0
            try {
                org.apache.commons.compress.compressors.xz.XZCompressorInputStream(
                    BufferedInputStream(FileInputStream(tarXzFile))
                ).use { xzIn ->
                    org.apache.commons.compress.archivers.tar.TarArchiveInputStream(xzIn).use { tarIn ->
                        var entry = tarIn.nextTarEntry
                        while (entry != null) {
                            if (!entry.isDirectory) totalEntries++
                            entry = tarIn.nextTarEntry
                        }
                    }
                }
            } catch (_: Exception) {
                totalEntries = 0
            }

            // Second pass: extract with progress
            var extracted = 0
            org.apache.commons.compress.compressors.xz.XZCompressorInputStream(
                BufferedInputStream(FileInputStream(tarXzFile))
            ).use { xzIn ->
                org.apache.commons.compress.archivers.tar.TarArchiveInputStream(xzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        val destPath = File(destDir, entry.name)
                        // Guard against path traversal
                        if (!destPath.canonicalPath.startsWith(destDir.canonicalPath + File.separator) &&
                            destPath.canonicalPath != destDir.canonicalPath
                        ) {
                            entry = tarIn.nextTarEntry
                            continue
                        }
                        if (entry.isDirectory) {
                            destPath.mkdirs()
                        } else {
                            destPath.parentFile?.mkdirs()
                            FileOutputStream(destPath).use { fos ->
                                tarIn.copyTo(fos)
                            }
                            // Preserve owner executable permission from tar entry mode
                            if ((entry.mode and 0x40) != 0) {
                                destPath.setExecutable(true)
                            }
                            extracted++
                        }
                        if (totalEntries > 0) {
                            val pct = (extracted * 100 / totalEntries).coerceAtMost(99)
                            emit(DownloadProgress(percent = pct, state = DownloadState.EXTRACTING))
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }

        emit(DownloadProgress(percent = 100, state = DownloadState.DONE))
    }
}
