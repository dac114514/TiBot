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
import org.json.JSONArray

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tibot_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BOT_TOKEN = stringPreferencesKey("bot_token")
        val ADMIN_ID = longPreferencesKey("admin_id")
        val ADMIN_IDS = stringPreferencesKey("admin_ids")
        val IS_CONFIGURED = stringPreferencesKey("is_configured")
        val BOT_FIRST_NAME = stringPreferencesKey("bot_first_name")
        val BOT_USERNAME = stringPreferencesKey("bot_username")
        val UPDATE_OFFSET = longPreferencesKey("update_offset")
        val ACCESS_MODE = stringPreferencesKey("access_mode")
        val BACKGROUND_RUNNING = stringPreferencesKey("background_running")
        val NOTIFICATIONS_ENABLED = stringPreferencesKey("notifications_enabled")
        /**
         * 静音 chat 列表 (R1-A / B5 引入)。
         * 持久化为 JSON 数组字符串: `[123, 456]`。
         */
        val MUTED_CHATS = stringPreferencesKey("muted_chats")
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

    val adminIds: Flow<List<Long>> = context.dataStore.data.map { prefs ->
        parseAdminIds(prefs[Keys.ADMIN_IDS], prefs[Keys.ADMIN_ID])
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

    /**
     * 静音 chat 集合 (R1-A / B5 引入)。
     * PollingManager 收到消息时检查此 set, 在内则不弹通知。
     */
    val perChatMute: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        parseMutedChats(prefs[Keys.MUTED_CHATS])
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

    /**
     * 翻转 chatId 的静音状态 (R1-A / B5 引入)。
     * 在 muted 集合中 → 移除;不在 → 加入。
     */
    suspend fun toggleMute(chatId: Long) {
        context.dataStore.edit { prefs ->
            val current = parseMutedChats(prefs[Keys.MUTED_CHATS])
            val newSet = if (chatId in current) current - chatId else current + chatId
            prefs[Keys.MUTED_CHATS] = encodeMutedChats(newSet)
        }
    }

    private fun parseMutedChats(json: String?): Set<Long> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val arr = JSONArray(json)
            val out = mutableSetOf<Long>()
            for (i in 0 until arr.length()) {
                runCatching { arr.getLong(i) }.getOrNull()?.let(out::add)
            }
            out
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun encodeMutedChats(ids: Set<Long>): String {
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        return arr.toString()
    }

    suspend fun addAdmin(id: Long) {
        context.dataStore.edit { prefs ->
            val current = parseAdminIds(prefs[Keys.ADMIN_IDS], prefs[Keys.ADMIN_ID])
            if (id !in current) {
                val newList = (current + id).distinct()
                prefs[Keys.ADMIN_IDS] = encodeAdminIds(newList)
            }
        }
    }

    suspend fun removeAdmin(id: Long) {
        context.dataStore.edit { prefs ->
            val current = parseAdminIds(prefs[Keys.ADMIN_IDS], prefs[Keys.ADMIN_ID])
            val newList = current.filter { it != id }
            prefs[Keys.ADMIN_IDS] = encodeAdminIds(newList)
        }
    }

    suspend fun setAdminIds(ids: List<Long>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADMIN_IDS] = encodeAdminIds(ids.distinct())
        }
    }

    private fun parseAdminIds(json: String?, legacySingle: Long?): List<Long> {
        val fromJson = if (json.isNullOrBlank()) emptyList() else try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try { arr.getLong(i) } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
        val fromLegacy = if (legacySingle != null && legacySingle != 0L) listOf(legacySingle) else emptyList()
        return (fromJson + fromLegacy).distinct()
    }

    private fun encodeAdminIds(ids: List<Long>): String {
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        return arr.toString()
    }
}
