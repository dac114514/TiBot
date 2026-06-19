package com.faster.tibot.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.local.ThemeMode
import com.faster.tibot.data.mqtt.MqttManager
import com.faster.tibot.data.proot.ProotManager
import com.faster.tibot.service.TiBotForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SettingsRepository(application)
    private val prootManager = ProotManager(application)
    private val mqtt = MqttManager.getInstance()

    val themeMode: StateFlow<ThemeMode> = repo.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val botToken: StateFlow<String> = repo.botToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun stopContainer() {
        prootManager.stopProot()
        application.stopService(Intent(application, TiBotForegroundService::class.java))
    }

    fun restartContainer() {
        prootManager.stopProot()
        application.startService(Intent(application, TiBotForegroundService::class.java))
    }

    fun isContainerRunning(): Boolean = prootManager.isRunning()
}
