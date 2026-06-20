package com.faster.tibot.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.local.ThemeMode
import com.faster.tibot.service.BotForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SettingsRepository(application)

    val themeMode: StateFlow<ThemeMode> = repo.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val botToken: StateFlow<String> = repo.botToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val adminId: StateFlow<Long> = repo.adminId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val accessMode: StateFlow<String> = repo.accessMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "all")

    val backgroundRunning: StateFlow<Boolean> = repo.backgroundRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notificationsEnabled: StateFlow<Boolean> = repo.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun setAccessMode(mode: String) {
        viewModelScope.launch { repo.setAccessMode(mode) }
    }

    fun setBackgroundRunning(enabled: Boolean) {
        viewModelScope.launch {
            repo.setBackgroundRunning(enabled)
            if (enabled) {
                val token = repo.botToken.first()
                if (token.isNotBlank()) {
                    BotForegroundService.start(getApplication(), token)
                }
            } else {
                getApplication<Application>().stopService(
                    Intent(getApplication(), BotForegroundService::class.java)
                )
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setNotificationsEnabled(enabled) }
    }

    fun stopContainer() {
        // No-op: container management removed in v2 direct-HTTP architecture
    }

    fun restartContainer() {
        // No-op: container management removed in v2 direct-HTTP architecture
    }

    fun isContainerRunning(): Boolean = false
}
