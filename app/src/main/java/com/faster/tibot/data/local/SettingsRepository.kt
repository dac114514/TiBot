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
        val BOT_FIRST_NAME = stringPreferencesKey("bot_first_name")
        val BOT_USERNAME = stringPreferencesKey("bot_username")
        val UPDATE_OFFSET = longPreferencesKey("update_offset")
        val ACCESS_MODE = stringPreferencesKey("access_mode")
        val BACKGROUND_RUNNING = stringPreferencesKey("background_running")
        val NOTIFICATIONS_ENABLED = stringPreferencesKey("notifications_enabled")
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

    val botFirstName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.BOT_FIRST_NAME] ?: ""
    }

    val botUsername: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.BOT_USERNAME] ?: ""
    }

    val adminId: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.ADMIN_ID] ?: 0L
    }

    val accessMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACCESS_MODE] ?: "all"
    }

    val backgroundRunning: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.BACKGROUND_RUNNING] != "false"
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATIONS_ENABLED] != "false"
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

    suspend fun saveTokenOnly(token: String, adminId: Long) {
        context.dataStore.edit {
            it[Keys.BOT_TOKEN] = token
            it[Keys.ADMIN_ID] = adminId
        }
    }

    suspend fun markConfigured() {
        context.dataStore.edit { it[Keys.IS_CONFIGURED] = "true" }
    }

    suspend fun isConfiguredSync(): Boolean = isConfigured.first()

    suspend fun saveBotInfo(firstName: String, username: String) {
        context.dataStore.edit {
            it[Keys.BOT_FIRST_NAME] = firstName
            it[Keys.BOT_USERNAME] = username
        }
    }

    suspend fun saveUpdateOffset(offset: Long) {
        context.dataStore.edit { it[Keys.UPDATE_OFFSET] = offset }
    }

    suspend fun getUpdateOffset(): Long {
        return context.dataStore.data.first()[Keys.UPDATE_OFFSET] ?: 0L
    }

    suspend fun setAccessMode(mode: String) {
        require(mode == "all" || mode == "admin") { "invalid access mode: $mode" }
        context.dataStore.edit { it[Keys.ACCESS_MODE] = mode }
    }

    suspend fun setBackgroundRunning(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_RUNNING] = enabled.toString() }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled.toString() }
    }
}
