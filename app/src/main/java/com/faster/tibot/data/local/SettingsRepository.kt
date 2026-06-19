package com.faster.tibot.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tibot_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BOT_TOKEN = stringPreferencesKey("bot_token")
        val ADMIN_ID = longPreferencesKey("admin_id")
        val IS_CONFIGURED = stringPreferencesKey("is_configured")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromStorage(prefs[Keys.THEME_MODE])
    }

    val isConfigured: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.IS_CONFIGURED] == "true"
    }

    val botToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.BOT_TOKEN] ?: ""
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.toStorage() }
    }

    suspend fun saveConfig(token: String, adminId: Long) {
        context.dataStore.edit {
            it[Keys.BOT_TOKEN] = token
            it[Keys.ADMIN_ID] = adminId
            it[Keys.IS_CONFIGURED] = "true"
        }
    }

    suspend fun isConfiguredSync(): Boolean = isConfigured.first()
}
