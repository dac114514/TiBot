package com.faster.tibot.data.proot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class ProotManager(private val context: Context) {

    private val filesDir get() = context.filesDir
    private var process: Process? = null
    private var restartCount = 0
    val maxRestarts = 3

    fun isRootfsDeployed(): Boolean {
        return File(filesDir, "rootfs/bin/sh").exists()
    }

    fun isRunning(): Boolean = process?.isAlive == true

    fun getProotBinary(): File = File(filesDir, "rootfs/usr/bin/proot")
        .let { if (it.exists()) it else File(filesDir, "rootfs/bin/proot") }

    suspend fun startProot(): Process? = withContext(Dispatchers.IO) {
        if (!isRootfsDeployed()) return@withContext null
        val prootBinary = getProotBinary()
        if (!prootBinary.exists()) return@withContext null

        val rootfsDir = File(filesDir, "rootfs")
        val pb = ProcessBuilder(
            prootBinary.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-w", "/home/tibot",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "/bin/bash", "-c", "cd /home/tibot && bash start.sh"
        )
        pb.directory(rootfsDir)
        pb.environment()["HOME"] = "/home/tibot"
        pb.environment()["TERM"] = "xterm-256color"
        pb.redirectErrorStream(true)
        try {
            val p = pb.start()
            process = p
            restartCount = 0
            p
        } catch (e: Exception) {
            null
        }
    }

    fun stopProot() {
        try {
            process?.destroy()
        } catch (_: Exception) {}
        process = null
    }

    fun getProcess(): Process? = process

    fun getProcessOutput(): Flow<String> = flow {
        val p = process ?: return@flow
        withContext(Dispatchers.IO) {
            try {
                p.inputStream.bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        emit(line)
                        line = reader.readLine()
                    }
                }
            } catch (_: Exception) {
                // Stream closed
            }
        }
    }

    fun canAutoRestart(): Boolean = restartCount < maxRestarts

    fun incrementRestartCount() { restartCount++ }

    // Clean up all downloaded/deployed files (for reset)
    fun cleanAll() {
        stopProot()
        File(filesDir, "rootfs.tar.gz").delete()
        File(filesDir, "rootfs").deleteRecursively()
    }

    /**
     * No-op retained for backward compatibility with WizardViewModel.
     * Rootfs is now downloaded and extracted by RootfsDownloadManager in Task 4.
     */
    suspend fun deployRootfs() = withContext(Dispatchers.IO) {
        // No-op: rootfs deployment is handled by RootfsDownloadManager
    }
}
