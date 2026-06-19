package com.faster.tibot.data.autoreply

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.faster.tibot.data.telegram.TelegramBotClient
import com.faster.tibot.data.telegram.TelegramMessage
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.autoReplyDataStore by preferencesDataStore(name = "tibot_autoreply")

data class AutoReplyRule(
    val ruleId: String,
    val keyword: String,
    val reply: String,
    val matchType: String, // exact, contains, regex, command
    val enabled: Boolean,
)

class AutoReplyEngine private constructor(
    private val botClient: TelegramBotClient,
    private val context: Context,
) {
    companion object {
        @Volatile
        private var INSTANCE: AutoReplyEngine? = null

        fun getInstance(context: Context, botClient: TelegramBotClient): AutoReplyEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutoReplyEngine(botClient, context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private object Keys {
        val RULES = stringPreferencesKey("autoreply_rules")
    }

    /**
     * Match an incoming message against stored rules and auto-reply if any match.
     * Returns true if a reply was sent.
     */
    suspend fun processMessage(msg: TelegramMessage): Boolean {
        val rules = loadRules()
        for (rule in rules) {
            if (!rule.enabled) continue
            val matched = when (rule.matchType) {
                "exact" -> msg.text.equals(rule.keyword, ignoreCase = true)
                "contains" -> msg.text.contains(rule.keyword, ignoreCase = true)
                "regex" -> try {
                    Regex(rule.keyword).containsMatchIn(msg.text)
                } catch (_: Exception) {
                    false
                }
                "command" -> msg.text.trimStart().startsWith(
                    "/${rule.keyword}", ignoreCase = true
                )
                else -> msg.text.contains(rule.keyword, ignoreCase = true)
            }
            if (matched) {
                botClient.sendMessage(msg.chatId, rule.reply)
                return true
            }
        }
        return false
    }

    /** Read all stored rules from DataStore. */
    suspend fun loadRules(): List<AutoReplyRule> {
        val json = context.autoReplyDataStore.data.first()[Keys.RULES] ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            AutoReplyRule(
                ruleId = obj.optString("ruleId", ""),
                keyword = obj.optString("keyword", ""),
                reply = obj.optString("reply", ""),
                matchType = obj.optString("matchType", "contains"),
                enabled = obj.optBoolean("enabled", true),
            )
        }
    }

    /** Persist rules to DataStore. */
    suspend fun saveRules(rules: List<AutoReplyRule>) {
        val arr = JSONArray()
        for (rule in rules) {
            arr.put(
                JSONObject().apply {
                    put("ruleId", rule.ruleId)
                    put("keyword", rule.keyword)
                    put("reply", rule.reply)
                    put("matchType", rule.matchType)
                    put("enabled", rule.enabled)
                }
            )
        }
        context.autoReplyDataStore.edit { prefs ->
            prefs[Keys.RULES] = arr.toString()
        }
    }
}
