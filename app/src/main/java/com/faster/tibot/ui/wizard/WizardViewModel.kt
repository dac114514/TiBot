package com.faster.tibot.ui.wizard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WizardState(
    val currentStep: Int = 0,  // 0=welcome, 1=token, 2=admin, 3=deploying
    val botToken: String = "",
    val tokenValid: Boolean = false,
    val adminId: String = "",
    val adminIdValid: Boolean = false,
    val deployProgress: List<DeployStep> = emptyList(),
)

data class DeployStep(val label: String, val status: DeployStatus)
enum class DeployStatus { PENDING, IN_PROGRESS, DONE, ERROR }

class WizardViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val _state = MutableStateFlow(WizardState())
    val state = _state.asStateFlow()

    fun setToken(token: String) {
        val valid = token.length > 20 && token.contains(":")
        _state.value = _state.value.copy(botToken = token, tokenValid = valid)
    }

    fun setAdminId(id: String) {
        val valid = id.trim().toLongOrNull() != null
        _state.value = _state.value.copy(adminId = id.trim(), adminIdValid = valid)
    }

    fun nextStep() {
        val step = _state.value.currentStep
        if (step == 2) {
            // Save and start deployment
            viewModelScope.launch {
                settingsRepo.saveConfig(
                    token = _state.value.botToken,
                    adminId = _state.value.adminId.toLong(),
                )
                _state.value = _state.value.copy(currentStep = 3, deployProgress = listOf(
                    DeployStep("解压 Ubuntu rootfs", DeployStatus.IN_PROGRESS),
                    DeployStep("安装 Python 依赖", DeployStatus.PENDING),
                    DeployStep("启动 Mosquitto", DeployStatus.PENDING),
                    DeployStep("启动 Bot 桥接层", DeployStatus.PENDING),
                ))
                // Simulate deployment steps
                deployProgress()
            }
        } else {
            _state.value = _state.value.copy(currentStep = step + 1)
        }
    }

    fun prevStep() {
        val step = _state.value.currentStep
        if (step > 0) _state.value = _state.value.copy(currentStep = step - 1)
    }

    private suspend fun deployProgress() {
        val steps = _state.value.deployProgress.toMutableList()
        steps[0] = steps[0].copy(status = DeployStatus.DONE)
        _state.value = _state.value.copy(deployProgress = steps)
        kotlinx.coroutines.delay(500)
        steps[1] = steps[1].copy(status = DeployStatus.DONE)
        _state.value = _state.value.copy(deployProgress = steps)
        kotlinx.coroutines.delay(800)
        steps[2] = steps[2].copy(status = DeployStatus.DONE)
        _state.value = _state.value.copy(deployProgress = steps)
        kotlinx.coroutines.delay(500)
        steps[3] = steps[3].copy(status = DeployStatus.DONE)
        _state.value = _state.value.copy(deployProgress = steps)
    }
}
