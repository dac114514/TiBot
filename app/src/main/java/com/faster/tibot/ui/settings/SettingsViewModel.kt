package com.faster.tibot.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.local.ThemeMode
import com.faster.tibot.data.telegram.BotState
import com.faster.tibot.service.BotForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SettingsRepository(application)

    val themeMode: StateFlow<ThemeMode> = repo.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val botToken: StateFlow<String> = repo.botToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val accessMode: StateFlow<String> = repo.accessMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "all")

    val adminIds: StateFlow<List<Long>> = repo.adminIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backgroundRunning: StateFlow<Boolean> = repo.backgroundRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notificationsEnabled: StateFlow<Boolean> = repo.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _botInfo = MutableStateFlow(BotState.info.value)
    val botInfo: StateFlow<BotState.BotInfo> = _botInfo.asStateFlow()

    private val _uptimeSeconds = MutableStateFlow(0L)
    val uptimeSeconds: StateFlow<Long> = _uptimeSeconds.asStateFlow()

    init {
        viewModelScope.launch {
            BotState.info.collect { _botInfo.value = it }
        }
        viewModelScope.launch {
            while (isActive) {
                val start = BotState.startTimeEpoch
                _uptimeSeconds.value = if (start > 0L) {
                    (System.currentTimeMillis() - start) / 1000
                } else 0L
                delay(1000)
            }
        }
    }

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

    fun addAdmin(id: Long) {
        viewModelScope.launch { repo.addAdmin(id) }
    }

    fun removeAdmin(id: Long) {
        viewModelScope.launch { repo.removeAdmin(id) }
    }
}
