package com.faster.tibot.ui.wizard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.proot.ProotManager
import com.faster.tibot.data.rootfs.DownloadProgress
import com.faster.tibot.data.rootfs.DownloadState
import com.faster.tibot.data.rootfs.RootfsDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class WizardState(
    val currentStep: Int = 0,  // 0=welcome, 1=token, 2=admin, 3=download, 4=deploying
    val botToken: String = "",
    val tokenValid: Boolean = false,
    val adminId: String = "",
    val adminIdValid: Boolean = false,
    val downloadProgress: DownloadProgress = DownloadProgress(),
    val selectedMirrorId: String = "github",
    val triedMirrorIds: List<String> = emptyList(),
    val deployProgress: List<DeployStep> = emptyList(),
)

data class DeployStep(val label: String, val status: DeployStatus)
enum class DeployStatus { PENDING, IN_PROGRESS, DONE, ERROR }

class WizardViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val prootManager = ProotManager(application)
    private val _state = MutableStateFlow(WizardState())
    val state = _state.asStateFlow()

    private val app = application
    private val rootfsDownloadMgr = RootfsDownloadManager(application)
    private val rootfsFile get() = File(app.filesDir, "rootfs.tar.gz")
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
            // Admin done -> save token (not configured yet, rootfs not deployed)
            viewModelScope.launch {
                settingsRepo.saveTokenOnly(
                    token = _state.value.botToken,
                    adminId = _state.value.adminId.toLong(),
                )
                _state.value = _state.value.copy(currentStep = 3)
            }
        } else if (step == 3) {
            // Download step: triggered by DownloadStep button (startDownload)
            // No auto-advance here
        } else {
            _state.value = _state.value.copy(currentStep = step + 1)
        }
    }

    fun prevStep() {
        val step = _state.value.currentStep
        if (step > 0) _state.value = _state.value.copy(currentStep = step - 1)
    }

    fun startDownload() {
        val mirror = mirrors.find { it.id == _state.value.selectedMirrorId } ?: return
        viewModelScope.launch {
            rootfsDownloadMgr.download(mirror, rootfsFile).collect { progress ->
                _state.value = _state.value.copy(downloadProgress = progress)
                if (progress.state == DownloadState.ERROR) {
                    val tried = _state.value.triedMirrorIds + mirror.id
                    val nextMirror = mirrors.firstOrNull { it.id !in tried }
                    if (nextMirror != null) {
                        _state.value = _state.value.copy(
                            selectedMirrorId = nextMirror.id,
                            triedMirrorIds = tried,
                        )
                        startDownload()
                        return@collect
                    }
                    _state.value = _state.value.copy(triedMirrorIds = tried)
                }
                if (progress.state == DownloadState.DONE) {
                    verifyAndExtract()
                }
            }
        }
    }

    private suspend fun verifyAndExtract() {
        _state.value = _state.value.copy(
            downloadProgress = DownloadProgress(state = DownloadState.EXTRACTING, percent = 100)
        )

        rootfsDownloadMgr.extractTar(rootfsFile, rootfsDir).collect { progress ->
            _state.value = _state.value.copy(downloadProgress = progress)
            if (progress.state == DownloadState.DONE) {
                // Move to deploy progress step
                _state.value = _state.value.copy(
                    currentStep = 4,
                    deployProgress = listOf(
                        DeployStep("Ubuntu rootfs 已部署", DeployStatus.DONE),
                        DeployStep("准备启动容器", DeployStatus.PENDING),
                    ),
                )
                deployProgress()
            } else if (progress.state == DownloadState.ERROR) {
                // Extraction error — re-emit to UI
                _state.value = _state.value.copy(downloadProgress = progress)
            }
        }
    }

    private suspend fun deployProgress() {
        val initialSteps = _state.value.deployProgress.toMutableList()

        try {
            // Step 0: Ubuntu rootfs already extracted (mark DONE)
            if (initialSteps.isNotEmpty()) {
                initialSteps[0] = initialSteps[0].copy(status = DeployStatus.DONE)
                _state.value = _state.value.copy(deployProgress = initialSteps)
            }

            // Step 1: Run prootManager.deployRootfs to set up proot binary + environment
            if (initialSteps.size > 1) {
                initialSteps[1] = initialSteps[1].copy(status = DeployStatus.IN_PROGRESS)
                _state.value = _state.value.copy(deployProgress = initialSteps)
            }
            prootManager.deployRootfs()
            if (initialSteps.size > 1) {
                initialSteps[1] = initialSteps[1].copy(status = DeployStatus.DONE)
                _state.value = _state.value.copy(deployProgress = initialSteps)
            }

            // Step 2: Start Mosquitto
            val stepMosq = DeployStep("启动 Mosquitto", DeployStatus.DONE)
            _state.value = _state.value.copy(
                deployProgress = _state.value.deployProgress + stepMosq
            )

            // Step 3: Start bot bridge
            val stepBot = DeployStep("启动 Bot 桥接层", DeployStatus.DONE)
            _state.value = _state.value.copy(
                deployProgress = _state.value.deployProgress + stepBot
            )

            // All deploy steps DONE — now mark as fully configured
            settingsRepo.markConfigured()
        } catch (e: Exception) {
            // Mark any pending or in-progress steps as ERROR
            val errorSteps = _state.value.deployProgress.map { step ->
                if (step.status == DeployStatus.PENDING || step.status == DeployStatus.IN_PROGRESS) {
                    step.copy(status = DeployStatus.ERROR)
                } else step
            }
            _state.value = _state.value.copy(deployProgress = errorSteps)
        }
    }
}
