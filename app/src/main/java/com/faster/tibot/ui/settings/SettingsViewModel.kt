package com.faster.tibot.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.local.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SettingsRepository(application)

    val themeMode: StateFlow<ThemeMode> = repo.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val botToken: StateFlow<String> = repo.botToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun stopContainer() {
        // No-op: container management removed in v2 direct-HTTP architecture
    }

    fun restartContainer() {
        // No-op: container management removed in v2 direct-HTTP architecture
    }

    fun isContainerRunning(): Boolean = false
}
