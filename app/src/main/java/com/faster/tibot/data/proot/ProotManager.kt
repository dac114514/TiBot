package com.faster.tibot.data.proot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File

class ProotManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ProotManager"

        @Volatile
        private var INSTANCE: ProotManager? = null

        fun getInstance(context: Context): ProotManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProotManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val filesDir get() = context.filesDir
    private var process: Process? = null
    private var restartCount = 0
    val maxRestarts = 3

    /** Last N lines of proot stdout, accessible for diagnostics */
    private val outputBuffer = ArrayDeque<String>(50)
    fun getLastOutput(): String = outputBuffer.joinToString("\n")
    fun getLastOutputLines(count: Int = 10): String =
        outputBuffer.takeLast(count).joinToString("\n")

    var lastError: String = ""
        private set

    fun isRootfsDeployed(): Boolean {
        // Check both usrmerge (bin->usr/bin) and traditional paths
        val shUsr = File(filesDir, "rootfs/usr/bin/sh")
        val shBin = File(filesDir, "rootfs/bin/sh")
        val ok = (shUsr.exists() && shUsr.canExecute()) || (shBin.exists() && shBin.canExecute())
        Log.d(TAG, "isRootfsDeployed: usr/bin/sh=${shUsr.exists()}, bin/sh=${shBin.exists()} -> $ok")
        return ok
    }

    fun isBootstrapDone(): Boolean =
        File(filesDir, "rootfs/home/tibot/.tibot_bootstrap_done").exists()

    fun isRunning(): Boolean = process?.isAlive == true

    fun getProotBinary(): File {
        val a = File(filesDir, "rootfs/usr/bin/proot")
        val b = File(filesDir, "rootfs/bin/proot")
        Log.d(TAG, "getProotBinary: usr/bin/proot=${a.exists()}, bin/proot=${b.exists()}")
        return if (a.exists()) a else b
    }

    suspend fun startProot(): Process? = withContext(Dispatchers.IO) {
        if (!isRootfsDeployed()) {
            lastError = "rootfs not deployed (sh not found or not executable)"
            Log.e(TAG, "startProot FAILED: $lastError")
            return@withContext null
        }
        val prootBinary = getProotBinary()
        if (!prootBinary.exists()) {
            lastError = "proot binary not found: ${prootBinary.absolutePath}"
            Log.e(TAG, "startProot FAILED: $lastError")
            return@withContext null
        }
        // Android 10+ noexec filesystem: setExecutable may fail but linker64 handles it
        prootBinary.setExecutable(true)
        Log.d(TAG, "proot binary: exists=${prootBinary.exists()}, canExec=${prootBinary.canExecute()}, size=${prootBinary.length()}")

        val rootfsDir = File(filesDir, "rootfs")
        // Android 10+ enforces W^X on app data — use linker64 to load the binary
        val linker = "/system/bin/linker64"
        val cmd = mutableListOf(
            linker, prootBinary.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-w", "/home/tibot",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
        )
        if (File("/etc/resolv.conf").exists()) {
            cmd.add("-b")
            cmd.add("/etc/resolv.conf")
        }
        cmd.addAll(listOf(
            "/usr/bin/bash", "-c", "cd /home/tibot && bash start.sh"
        ))
        Log.i(TAG, "startProot: ${cmd.joinToString(" ")}")

        val pb = ProcessBuilder(cmd)
        pb.directory(rootfsDir)
        // Create temp directory for proot (overrides hardcoded Termux path)
        val prootTmpDir = File(filesDir, "tmp")
        prootTmpDir.mkdirs()
        // Proot loader binaries — required for executing guest ELF programs
        val loaderDir = File(filesDir, "rootfs/usr/libexec/proot")
        pb.environment()["PROOT_LOADER"] = File(loaderDir, "loader").absolutePath
        pb.environment()["PROOT_LOADER_32"] = File(loaderDir, "loader32").absolutePath

        pb.environment()["PROOT_TMP_DIR"] = prootTmpDir.absolutePath
        pb.environment()["PROOT_NO_SECCOMP"] = "1"
        pb.environment()["PROOT_F2FS_WORKAROUND"] = "1"
        pb.environment()["TMPDIR"] = prootTmpDir.absolutePath
        pb.environment()["HOME"] = "/home/tibot"
        pb.environment()["TERM"] = "xterm-256color"
        pb.environment()["LD_LIBRARY_PATH"] = File(filesDir, "rootfs/usr/lib").absolutePath
        pb.redirectErrorStream(true)
        try {
            val p = pb.start()
            process = p
            restartCount = 0
            Log.i(TAG, "startProot OK: process started")

            // Log stdout/stderr in background + accumulate for diagnostics
            Thread({
                try {
                    p.inputStream.bufferedReader().use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            Log.d(TAG, "[proot] $line")
                            outputBuffer.addLast(line)
                            if (outputBuffer.size > 50) outputBuffer.removeFirst()
                            line = reader.readLine()
                        }
                    }
                } catch (_: Exception) {}
            }, "proot-stdout").start()

            p
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "startProot FAILED: $lastError", e)
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
