package com.faster.tibot.ui.wizard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WizardState(
    val currentStep: Int = 0,
    val botToken: String = "",
    val tokenValid: Boolean = false,
    val adminId: String = "",
    val adminIdValid: Boolean = false,
)

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

    fun setMirror(mirrorId: String) {
        // No-op: mirrors removed in v2 direct-HTTP architecture
    }

    fun nextStep() {
        val step = _state.value.currentStep
        if (step == 2) {
            viewModelScope.launch {
                settingsRepo.saveTokenOnly(
                    token = _state.value.botToken,
                    adminId = _state.value.adminId.toLong(),
                )
                settingsRepo.markConfigured()
                _state.value = _state.value.copy(currentStep = 3)
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
