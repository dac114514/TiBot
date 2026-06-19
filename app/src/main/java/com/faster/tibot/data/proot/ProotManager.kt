package com.faster.tibot.data.proot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File

class ProotManager(private val context: Context) {

    companion object {
        private const val TAG = "ProotManager"
    }

    private val filesDir get() = context.filesDir
    private var process: Process? = null
    private var restartCount = 0
    val maxRestarts = 3

    fun isRootfsDeployed(): Boolean {
        // Check both usrmerge (bin->usr/bin) and traditional paths
        val shUsr = File(filesDir, "rootfs/usr/bin/sh")
        val shBin = File(filesDir, "rootfs/bin/sh")
        val ok = (shUsr.exists() && shUsr.canExecute()) || (shBin.exists() && shBin.canExecute())
        Log.d(TAG, "isRootfsDeployed: usr/bin/sh=${shUsr.exists()}, bin/sh=${shBin.exists()} -> $ok")
        return ok
    }

    fun isRunning(): Boolean = process?.isAlive == true

    fun getProotBinary(): File {
        val a = File(filesDir, "rootfs/usr/bin/proot")
        val b = File(filesDir, "rootfs/bin/proot")
        Log.d(TAG, "getProotBinary: usr/bin/proot=${a.exists()}, bin/proot=${b.exists()}")
        return if (a.exists()) a else b
    }

    suspend fun startProot(): Process? = withContext(Dispatchers.IO) {
        if (!isRootfsDeployed()) {
            Log.e(TAG, "startProot FAILED: rootfs not deployed")
            return@withContext null
        }
        val prootBinary = getProotBinary()
        if (!prootBinary.exists()) {
            Log.e(TAG, "startProot FAILED: proot binary not found at ${prootBinary.absolutePath}")
            return@withContext null
        }
        if (!prootBinary.canExecute() && !prootBinary.setExecutable(true)) {
            Log.e(TAG, "startProot FAILED: proot binary not executable: ${prootBinary.absolutePath}")
            return@withContext null
        }

        val rootfsDir = File(filesDir, "rootfs")
        val cmd = listOf(
            prootBinary.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-w", "/home/tibot",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/etc/resolv.conf",
            "/bin/bash", "-c", "cd /home/tibot && bash start.sh"
        )
        Log.i(TAG, "startProot: ${cmd.joinToString(" ")}")

        val pb = ProcessBuilder(cmd)
        pb.directory(rootfsDir)
        pb.environment()["HOME"] = "/home/tibot"
        pb.environment()["TERM"] = "xterm-256color"
        pb.redirectErrorStream(true)
        try {
            val p = pb.start()
            process = p
            restartCount = 0
            Log.i(TAG, "startProot OK: process started")

            // Log stdout/stderr in background
            Thread({
                try {
                    p.inputStream.bufferedReader().use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            Log.d(TAG, "[proot] $line")
                            line = reader.readLine()
                        }
                    }
                } catch (_: Exception) {}
            }, "proot-stdout").start()

            p
        } catch (e: Exception) {
            Log.e(TAG, "startProot FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
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
