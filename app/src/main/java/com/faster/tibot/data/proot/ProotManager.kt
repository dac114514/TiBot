package com.faster.tibot.data.proot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProotManager(private val context: Context) {

    private val filesDir get() = context.filesDir

    fun isProotInstalled(): Boolean {
        return File(filesDir, "rootfs/bin/sh").exists()
    }

    fun getProotBinary(): File = File(filesDir, "proot/proot-arm64")

    fun getRootFsDir(): File = File(filesDir, "rootfs")

    fun getStartScript(): File = File(filesDir, "rootfs/home/tibot/start.sh")

    suspend fun startProot(): Process? = withContext(Dispatchers.IO) {
        if (!isProotInstalled()) return@withContext null
        val pb = ProcessBuilder(
            getProotBinary().absolutePath,
            "-r", getRootFsDir().absolutePath,
            "-w", "/home/tibot",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "/bin/bash", "-c", "cd /home/tibot && bash start.sh"
        )
        pb.directory(getRootFsDir())
        pb.environment()["HOME"] = "/home/tibot"
        pb.environment()["TERM"] = "xterm-256color"
        pb.redirectErrorStream(true)
        try { pb.start() } catch (e: Exception) { null }
    }

    fun stopProot(process: Process?) {
        process?.destroy()
    }

    suspend fun deployRootfs() = withContext(Dispatchers.IO) {
        // Step 1: Extract proot binary from assets
        // Step 2: Extract Ubuntu rootfs from assets
        // Step 3: chmod +x proot binary
        // Step 4: Run initial pip install
        // For MVP: mark as installed
        File(filesDir, "rootfs/home/tibot").mkdirs()
    }
}
