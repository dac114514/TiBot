package com.faster.tibot.ui.wizard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.rootfs.DownloadProgress
import com.faster.tibot.data.rootfs.DownloadState
import com.faster.tibot.data.rootfs.RootfsDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.walkTopDown

enum class Phase {
    IDLE, SPEED_TEST, READY, DOWNLOADING, EXTRACTING, DEPLOYING, DONE, ERROR
}

enum class LogLevel { INFO, SUCCESS, ERROR, PROGRESS }

data class LogLine(val text: String, val level: LogLevel)

data class WizardState(
    val currentStep: Int = 0,
    val botToken: String = "",
    val tokenValid: Boolean = false,
    val adminId: String = "",
    val adminIdValid: Boolean = false,
    val phase: Phase = Phase.IDLE,
    val phaseSubtitle: String = "",
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val logs: List<LogLine> = emptyList(),
    val selectedMirrorId: String = "ustc",
    val error: String? = null,
)

class WizardViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val _state = MutableStateFlow(WizardState())
    val state = _state.asStateFlow()

    private val app = application
    private val rootfsDownloadMgr = RootfsDownloadManager(application)
    private val rootfsFile get() = File(app.getExternalFilesDir(null), "rootfs.tar.gz")
    private val rootfsDir get() = File(app.filesDir, "rootfs")

    val mirrors = rootfsDownloadMgr.buildMirrors()

    fun setToken(token: String) {
        val valid = token.length > 20 && token.contains(":")
        _state.value = _state.value.copy(botToken = token, tokenValid = valid)
    }

    fun setAdminId(id: String) {
        val valid = id.trim().toLongOrNull() != null
        _state.value = _state.value.copy(adminId = id.trim(), adminIdValid = valid)
    }

    fun setMirror(mirrorId: String) {
        _state.value = _state.value.copy(selectedMirrorId = mirrorId)
    }

    fun nextStep() {
        val step = _state.value.currentStep
        if (step == 2) {
            viewModelScope.launch {
                settingsRepo.saveTokenOnly(
                    token = _state.value.botToken,
                    adminId = _state.value.adminId.toLong(),
                )
                _state.value = _state.value.copy(currentStep = 3)
                startSpeedTest()
            }
        } else {
            _state.value = _state.value.copy(currentStep = step + 1)
        }
    }

    fun prevStep() {
        val step = _state.value.currentStep
        if (step > 0) _state.value = _state.value.copy(currentStep = step - 1)
    }

    private suspend fun startSpeedTest() {
        _state.value = _state.value.copy(phase = Phase.SPEED_TEST, phaseSubtitle = "testing speed...")

        val logs = mutableListOf<LogLine>()
        logs += LogLine("$ ./speedtest", LogLevel.INFO)
        _state.value = _state.value.copy(logs = logs.toList())

        val results = rootfsDownloadMgr.speedTest(mirrors)

        results.forEach { result ->
            val mirror = mirrors.find { it.id == result.mirrorId }
            val name = mirror?.name ?: result.mirrorId
            if (result.error != null) {
                logs += LogLine("x $name: ${result.error}", LogLevel.ERROR)
            } else {
                logs += LogLine("  $name: ${result.latencyMs}ms", LogLevel.INFO)
            }
            _state.value = _state.value.copy(logs = logs.toList())
        }

        val working = results.filter { it.error == null }
        if (working.isEmpty()) {
            logs += LogLine("x all mirrors unreachable", LogLevel.ERROR)
            _state.value = _state.value.copy(
                phase = Phase.ERROR,
                phaseSubtitle = "all mirrors unreachable",
                error = "all mirrors unreachable",
                logs = logs.toList(),
            )
            return
        }

        val fastest = working.first()
        val fastestMirror = mirrors.find { it.id == fastest.mirrorId }!!
        logs += LogLine("v fastest: ${fastestMirror.name} (${fastest.latencyMs}ms)", LogLevel.SUCCESS)

        _state.value = _state.value.copy(
            phase = Phase.READY,
            phaseSubtitle = "fastest: ${fastestMirror.name} (${fastest.latencyMs}ms)",
            selectedMirrorId = fastest.mirrorId,
            logs = logs.toList(),
        )
    }

    fun startDownload() {
        val mirror = mirrors.find { it.id == _state.value.selectedMirrorId } ?: return
        viewModelScope.launch {
            val logs = _state.value.logs.toMutableList()
            logs += LogLine("$ wget ${mirror.url}", LogLevel.INFO)
            logs += LogLine("  downloading...", LogLevel.INFO)
            _state.value = _state.value.copy(phase = Phase.DOWNLOADING, phaseSubtitle = "downloading...", logs = logs)

            rootfsDownloadMgr.download(mirror, rootfsFile).collect { progress ->
                when (progress.state) {
                    DownloadState.DOWNLOADING -> {
                        _state.value = _state.value.copy(
                            phase = Phase.DOWNLOADING,
                            progressPercent = progress.percent,
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes,
                            speedBytesPerSec = progress.speedBytesPerSec,
                        )
                    }
                    DownloadState.DONE -> {
                        val updatedLogs = _state.value.logs.toMutableList()
                        updatedLogs += LogLine("v download complete (${progress.downloadedBytes / 1024 / 1024}MB)", LogLevel.SUCCESS)
                        _state.value = _state.value.copy(
                            progressPercent = 100,
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes,
                            logs = updatedLogs,
                        )
                        extractAndDeploy()
                        return@collect
                    }
                    DownloadState.ERROR -> {
                        val updatedLogs = _state.value.logs.toMutableList()
                        updatedLogs += LogLine("x ${progress.error ?: "download failed"}", LogLevel.ERROR)
                        _state.value = _state.value.copy(
                            phase = Phase.ERROR,
                            phaseSubtitle = progress.error ?: "download failed",
                            error = progress.error,
                            logs = updatedLogs,
                        )
                        return@collect
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun extractAndDeploy() {
        val logs = _state.value.logs.toMutableList()
        logs += LogLine("$ tar -xzf rootfs.tar.gz", LogLevel.INFO)
        logs += LogLine("  extracting...", LogLevel.INFO)
        _state.value = _state.value.copy(phase = Phase.EXTRACTING, phaseSubtitle = "deploying...", logs = logs)

        rootfsDownloadMgr.extractTar(rootfsFile, rootfsDir).collect { progress ->
            when (progress.state) {
                DownloadState.EXTRACTING -> {
                    _state.value = _state.value.copy(
                        phase = Phase.EXTRACTING,
                        progressPercent = progress.percent,
                    )
                }
                DownloadState.DONE -> {
                    val updatedLogs = _state.value.logs.toMutableList()
                    updatedLogs += LogLine("v extraction complete", LogLevel.SUCCESS)
                    _state.value = _state.value.copy(logs = updatedLogs, phaseSubtitle = "copying assets...")
                    rootfsDownloadMgr.copyAssets(rootfsDir)
                    verifyRootfs()
                    return@collect
                }
                DownloadState.ERROR -> {
                    val updatedLogs = _state.value.logs.toMutableList()
                    updatedLogs += LogLine("x ${progress.error ?: "extraction failed"}", LogLevel.ERROR)
                    _state.value = _state.value.copy(
                        phase = Phase.ERROR,
                        phaseSubtitle = progress.error ?: "extraction failed",
                        error = progress.error,
                        logs = updatedLogs,
                    )
                    return@collect
                }
                else -> {}
            }
        }
    }

    private suspend fun verifyRootfs() {
        val logs = _state.value.logs.toMutableList()
        logs += LogLine("$ verify rootfs", LogLevel.INFO)
        _state.value = _state.value.copy(phase = Phase.DEPLOYING, logs = logs)

        withContext(Dispatchers.IO) {
            // Must-exist critical paths (Ubuntu 24.04 usrmerge layout + proot + scripts)
            val checks = listOf(
                "usr/bin/sh" to "shell",
                "usr/bin/bash" to "bash",
                "usr/bin/dpkg" to "dpkg",
                "usr/bin/proot" to "proot",
                "etc/apt/sources.list.d/ubuntu.sources" to "apt sources",
                "etc/os-release" to "os-release",
                "usr/lib" to "lib dir",
                "home/tibot/start.sh" to "start.sh",
                "home/tibot/main.py" to "main.py",
            )

            var allOk = true
            for ((path, label) in checks) {
                val f = File(rootfsDir, path)
                val ok = if (label == "lib dir") f.isDirectory else f.exists()
                if (!ok) {
                    Log.w("WizardVM", "verifyRootfs: missing $path ($label)")
                    logs += LogLine("x missing: $path ($label)", LogLevel.ERROR)
                    allOk = false
                } else {
                    Log.d("WizardVM", "verifyRootfs: ok $path")
                }
            }

            // Verify we have a reasonable number of files (not just a few extracted from a partial archive)
            var fileCount = 0
            rootfsDir.walkTopDown().forEach { fileCount++ }
            if (fileCount < 3000) {
                logs += LogLine("x only $fileCount files found (expected 3000+)", LogLevel.ERROR)
                allOk = false
            }

            if (allOk) {
                val updatedLogs = _state.value.logs.toMutableList()
                updatedLogs += LogLine("v rootfs verified ($fileCount files)", LogLevel.SUCCESS)
                _state.value = _state.value.copy(
                    phase = Phase.DONE,
                    phaseSubtitle = "deploy complete",
                    progressPercent = 100,
                    logs = updatedLogs,
                )
            } else {
                val updatedLogs = _state.value.logs.toMutableList()
                updatedLogs += LogLine("x rootfs verification failed", LogLevel.ERROR)
                _state.value = _state.value.copy(
                    phase = Phase.ERROR,
                    phaseSubtitle = "rootfs verification failed",
                    error = "rootfs verification failed: critical files missing or incomplete",
                    logs = updatedLogs,
                )
            }
        }
    }

    fun onLaunchGateway() {
        viewModelScope.launch {
            // Always refresh assets on launch (handles upgrades from older versions)
            rootfsDownloadMgr.copyAssets(rootfsDir)
            settingsRepo.markConfigured()
            val intent = android.content.Intent(app, com.faster.tibot.service.TiBotForegroundService::class.java)
            app.startForegroundService(intent)
        }
    }
}
