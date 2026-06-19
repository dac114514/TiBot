package com.faster.tibot.data.rootfs

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    val logs: List<String> = emptyList(),
)

class RootfsDownloadManager(private val context: Context) {

    // Real Ubuntu base rootfs for ARM64 (24.04 Noble)
    // Official + verified Chinese mirrors
    fun buildMirrors(): List<MirrorSource> = listOf(
        MirrorSource("ustc",       "中科大镜像",   "https://mirrors.ustc.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz"),
        MirrorSource("tuna",       "清华 TUNA",    "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz"),
        MirrorSource("aliyun",     "阿里云镜像",    "https://mirrors.aliyun.com/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz"),
        MirrorSource("net163",     "网易 163",     "https://mirrors.163.com/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz"),
        MirrorSource("official",   "Ubuntu 官方",   "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz"),
    )

    suspend fun speedTest(mirrors: List<MirrorSource>): List<SpeedResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            mirrors.map { mirror ->
                async {
                    val start = System.currentTimeMillis()
                    try {
                        val url = java.net.URL(mirror.url)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "HEAD"
                        conn.connectTimeout = 5_000
                        conn.readTimeout = 5_000
                        conn.connect()
                        val latency = System.currentTimeMillis() - start
                        conn.disconnect()
                        SpeedResult(mirror.id, latency, null)
                    } catch (e: Exception) {
                        SpeedResult(mirror.id, -1, e.message ?: "unknown")
                    }
                }
            }.awaitAll()
        }
    }

    // Stall timeout: fail if no bytes received for this duration
    private val STALL_TIMEOUT_MS = 30_000L
    // Hard timeout: absolute max time for download (10 minutes)
    private val HARD_TIMEOUT_MS = 600_000L
    // Polling interval for DownloadManager progress
    private val POLL_INTERVAL_MS = 500L

    suspend fun download(
        mirror: MirrorSource,
        destFile: File,
    ): Flow<DownloadProgress> = flow {
        val logs = mutableListOf<String>()
        fun log(msg: String) {
            logs.add("[${mirror.name}] $msg")
        }
        log("开始下载 ${mirror.url}")

        emit(DownloadProgress(state = DownloadState.DOWNLOADING, logs = logs.toList()))

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = try {
            DownloadManager.Request(Uri.parse(mirror.url))
                .setDestinationInExternalFilesDir(context, null, "rootfs.tar.gz")
                .setTitle("TiBot Ubuntu 环境")
                .setDescription("来自 ${mirror.name}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
        } catch (e: Exception) {
            log("URL 解析失败: ${e.message}")
            emit(DownloadProgress(
                state = DownloadState.ERROR,
                error = "URL 无效: ${mirror.name}",
                logs = logs.toList(),
            ))
            return@flow
        }

        val downloadId = dm.enqueue(request)
        log("下载任务已加入队列 (id=$downloadId)")

        var lastBytes = 0L
        var lastTime = System.currentTimeMillis()
        var lastProgressTime = System.currentTimeMillis()

        val success = withTimeoutOrNull(HARD_TIMEOUT_MS) {
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
                                    log("下载完成 (${lastBytes / 1024 / 1024}MB)")
                                    emit(
                                        DownloadProgress(
                                            percent = 100,
                                            downloadedBytes = lastBytes,
                                            totalBytes = lastBytes,
                                            state = DownloadState.DONE,
                                            logs = logs.toList(),
                                        )
                                    )
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    done = true
                                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                    val reasonText = when (reason) {
                                        DownloadManager.ERROR_CANNOT_RESUME -> "无法续传"
                                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "存储设备未找到"
                                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
                                        DownloadManager.ERROR_FILE_ERROR -> "文件错误"
                                        DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP 数据错误"
                                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
                                        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向过多"
                                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP 状态码异常"
                                        DownloadManager.ERROR_UNKNOWN -> "未知错误"
                                        else -> "错误码 $reason"
                                    }
                                    log("下载失败: $reasonText")
                                    emit(
                                        DownloadProgress(
                                            state = DownloadState.ERROR,
                                            error = "${mirror.name}: $reasonText",
                                            logs = logs.toList(),
                                        )
                                    )
                                }
                                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                    val now = System.currentTimeMillis()
                                    val timeDelta = (now - lastTime).coerceAtLeast(1)
                                    val bytesDelta = downloaded - lastBytes
                                    val speed = bytesDelta * 1000 / timeDelta

                                    // Reset progress time when bytes received
                                    if (bytesDelta > 0) {
                                        lastProgressTime = now
                                    }

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
                                            logs = logs.toList(),
                                        )
                                    )

                                    // Fail if stalled: no bytes received for STALL_TIMEOUT_MS
                                    if (now - lastProgressTime > STALL_TIMEOUT_MS) {
                                        done = true
                                        log("下载停滞: 30s 无数据")
                                        dm.remove(downloadId)
                                        emit(
                                            DownloadProgress(
                                                state = DownloadState.ERROR,
                                                error = "${mirror.name}: 下载停滞 (30s 无数据)",
                                                logs = logs.toList(),
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!done) delay(POLL_INTERVAL_MS)
                }
            }
        }

        if (success == null) {
            // Timeout
            dm.remove(downloadId)
            val actualFile = File(context.getExternalFilesDir(null), "rootfs.tar.gz")
            actualFile.delete()
            log("下载超时 (${HARD_TIMEOUT_MS / 1000}s)")
            emit(
                DownloadProgress(
                    state = DownloadState.ERROR,
                    error = "${mirror.name}: 下载超时 (${HARD_TIMEOUT_MS / 1000}s)",
                    logs = logs.toList(),
                )
            )
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

    suspend fun extractTar(
        tarFile: File,
        destDir: File,
    ): Flow<DownloadProgress> = flow {
        val logs = mutableListOf<String>()
        fun log(msg: String) { logs.add(msg) }

        log("开始解压 ${tarFile.name}")
        emit(DownloadProgress(percent = 0, state = DownloadState.EXTRACTING, logs = logs.toList()))

        withContext(Dispatchers.IO) {
            destDir.mkdirs()
            val tarStream = decompressStream(tarFile)
            if (tarStream == null) {
                log("解压失败: 无法识别文件格式")
                emit(DownloadProgress(state = DownloadState.ERROR, error = "无法识别文件格式 (需要 .tar.gz 或 .tar.xz)", logs = logs.toList()))
                return@withContext
            }

            try {
                val total = tarFile.length()
                // Two-pass extraction: count entries then extract
                val entries = mutableListOf<org.apache.commons.compress.archivers.tar.TarArchiveEntry>()
                decompressStream(tarFile)?.use { countingIn ->
                    org.apache.commons.compress.archivers.tar.TarArchiveInputStream(countingIn).use { tarIn ->
                        var entry = tarIn.nextTarEntry
                        while (entry != null) {
                            entries.add(entry)
                            entry = tarIn.nextTarEntry
                        }
                    }
                }
                log("共 ${entries.size} 个文件待解压")

                tarStream.use { compressedIn ->
                    org.apache.commons.compress.archivers.tar.TarArchiveInputStream(compressedIn).use { tarIn ->
                        var entry = tarIn.nextTarEntry
                        var extracted = 0
                        val totalNonDir = entries.count { !it.isDirectory }.coerceAtLeast(1)

                        while (entry != null) {
                            val destPath = File(destDir, entry.name)
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
                                FileOutputStream(destPath).use { fos -> tarIn.copyTo(fos) }
                                if ((entry.mode and 0x40) != 0) destPath.setExecutable(true)
                                extracted++
                                if (extracted % 50 == 0) {
                                    val pct = (extracted * 100 / totalNonDir).coerceAtMost(99)
                                    emit(DownloadProgress(
                                        percent = pct,
                                        totalBytes = total,
                                        downloadedBytes = extracted.toLong(),
                                        state = DownloadState.EXTRACTING,
                                        logs = logs.toList(),
                                    ))
                                }
                            }
                            entry = tarIn.nextTarEntry
                        }
                        log("解压完成: $extracted 个文件")
                    }
                }
            } catch (e: Exception) {
                log("解压失败: ${e.message}")
                emit(DownloadProgress(
                    state = DownloadState.ERROR,
                    error = "解压失败: ${e.message}",
                    logs = logs.toList(),
                ))
                return@withContext
            }
        }

        emit(DownloadProgress(percent = 100, state = DownloadState.DONE, logs = logs.toList()))
    }

    private fun decompressStream(file: File): java.io.InputStream? {
        return try {
            val name = file.name.lowercase()
            when {
                name.endsWith(".tar.gz") || name.endsWith(".tgz") ->
                    org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(
                        BufferedInputStream(FileInputStream(file))
                    )
                name.endsWith(".tar.xz") || name.endsWith(".txz") ->
                    org.apache.commons.compress.compressors.xz.XZCompressorInputStream(
                        BufferedInputStream(FileInputStream(file))
                    )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
