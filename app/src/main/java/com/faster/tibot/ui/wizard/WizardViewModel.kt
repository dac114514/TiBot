package com.faster.tibot.ui.wizard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.telegram.TelegramBotClient
import com.faster.tibot.service.BotForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WizardState(
    val currentStep: Int = 0,
    val botToken: String = "",
    val tokenValid: Boolean = false,
    val adminId: String = "",
    val adminIdValid: Boolean = false,
    val tokenError: String? = null,
    val isFinishing: Boolean = false,
    val setupCompleted: Boolean = false,
)

class WizardViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val _state = MutableStateFlow(WizardState())
    val state = _state.asStateFlow()

    fun setToken(token: String) {
        val valid = token.length > 20 && token.contains(":")
        _state.value = _state.value.copy(
            botToken = token,
            tokenValid = valid,
            tokenError = null,
        )
    }

    fun setAdminId(id: String) {
        val valid = id.trim().toLongOrNull() != null
        _state.value = _state.value.copy(adminId = id.trim(), adminIdValid = valid)
    }

    fun setMirror(mirrorId: String) {
        // No-op: mirrors removed in v2 direct-HTTP architecture
    }

    fun nextStep() {
        val step = _state.value.currentStep
        if (step == 2) {
            if (_state.value.isFinishing) return
            _state.value = _state.value.copy(isFinishing = true, tokenError = null)
            viewModelScope.launch {
                val token = _state.value.botToken
                val adminId = _state.value.adminId.toLong()
                val client = TelegramBotClient(token)
                val me = client.getMe()
                if (me == null) {
                    _state.value = _state.value.copy(
                        isFinishing = false,
                        tokenError = "Token 无效或网络不可达",
                    )
                    return@launch
                }
                settingsRepo.saveConfig(token, adminId)
                BotForegroundService.start(getApplication(), token)
                _state.value = _state.value.copy(
                    isFinishing = false,
                    setupCompleted = true,
                )
            }
        } else {
            _state.value = _state.value.copy(currentStep = step + 1)
        }
    }

    fun prevStep() {
        val step = _state.value.currentStep
        if (step > 0) _state.value = _state.value.copy(currentStep = step - 1)
    }
}
