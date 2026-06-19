package com.faster.tibot.ui.wizard

import android.app.Application
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
                    _state.value = _state.value.copy(logs = updatedLogs)
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
            val sh = File(rootfsDir, "bin/sh")
            if (sh.exists() && sh.canExecute()) {
                val updatedLogs = _state.value.logs.toMutableList()
                updatedLogs += LogLine("v rootfs verified", LogLevel.SUCCESS)
                _state.value = _state.value.copy(
                    phase = Phase.DONE,
                    phaseSubtitle = "deploy complete",
                    progressPercent = 100,
                    logs = updatedLogs,
                )
            } else if (sh.setExecutable(true)) {
                    val updatedLogs = _state.value.logs.toMutableList()
                    updatedLogs += LogLine("v rootfs verified", LogLevel.SUCCESS)
                    _state.value = _state.value.copy(
                        phase = Phase.DONE,
                        phaseSubtitle = "deploy complete",
                        progressPercent = 100,
                        logs = updatedLogs,
                    )
                } else {
                    val updatedLogs = _state.value.logs.toMutableList()
                    updatedLogs += LogLine("x rootfs verification failed: bin/sh not found", LogLevel.ERROR)
                    _state.value = _state.value.copy(
                        phase = Phase.ERROR,
                        phaseSubtitle = "rootfs verification failed",
                        error = "rootfs verification failed: bin/sh not found",
                        logs = updatedLogs,
                    )
                }
            }
        }
    }

    fun onLaunchGateway() {
        viewModelScope.launch {
            settingsRepo.markConfigured()
            val intent = android.content.Intent(app, com.faster.tibot.service.TiBotForegroundService::class.java)
            app.startForegroundService(intent)
        }
    }
}
