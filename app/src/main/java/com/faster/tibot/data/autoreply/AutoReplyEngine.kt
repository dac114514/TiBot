package com.faster.tibot.data.autoreply

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.faster.tibot.data.message.MessageStore
import com.faster.tibot.data.telegram.BotState
import com.faster.tibot.data.telegram.TelegramBotClient
import com.faster.tibot.data.telegram.TelegramMessage
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private val Context.autoReplyDataStore by preferencesDataStore(name = "tibot_autoreply")

data class AutoReplyRule(
    val ruleId: String,
    val keyword: String,
    val reply: String,
    val matchType: String,
    val enabled: Boolean,
    val hitCount: Int = 0,
)

class AutoReplyEngine private constructor(
    private val botClient: TelegramBotClient,
    private val context: Context,
    private val messageStore: MessageStore,
) {
    companion object {
        private const val TAG = "AutoReplyEngine"

        @Volatile
        private var INSTANCE: AutoReplyEngine? = null

        fun getInstance(
            context: Context,
            botClient: TelegramBotClient,
            messageStore: MessageStore,
        ): AutoReplyEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutoReplyEngine(
                    botClient,
                    context.applicationContext,
                    messageStore,
                ).also { INSTANCE = it }
            }

        fun getInstance(context: Context, botClient: TelegramBotClient): AutoReplyEngine =
            getInstance(context, botClient, MessageStore(context.applicationContext))

        fun resetInstance(
            context: Context,
            botClient: TelegramBotClient,
            messageStore: MessageStore,
        ): AutoReplyEngine =
            synchronized(this) {
                AutoReplyEngine(
                    botClient,
                    context.applicationContext,
                    messageStore,
                ).also { INSTANCE = it }
            }

        fun resetInstance(context: Context, botClient: TelegramBotClient): AutoReplyEngine =
            resetInstance(context, botClient, MessageStore(context.applicationContext))
    }

    private object Keys {
        val RULES = stringPreferencesKey("autoreply_rules")
    }

    /**
     * Match an incoming message against stored rules and auto-reply if any match.
     * Returns the matched rule's id, or null if no rule matched.
     *
     * On successful send, the outgoing message is persisted to [MessageStore] so that
     * the bot's own reply shows up in chat list / bubbles (this is the H1-H4 bug fix).
     *
     * Note: admin/permission filtering is performed upstream in [PollingManager].
     */
    suspend fun processMessage(msg: TelegramMessage): String? {
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
                val result = botClient.sendMessage(msg.chatId, rule.reply)
                result.fold(
                    onSuccess = { serverId ->
                        saveOutgoing(
                            chatId = msg.chatId,
                            chatTitle = msg.chatTitle,
                            text = rule.reply,
                            serverId = serverId,
                            ruleId = rule.ruleId,
                        )
                        recordHit(rule.ruleId)
                    },
                    onFailure = { e ->
                        Log.w(TAG, "sendMessage failed: ${e.message}")
                    }
                )
                return rule.ruleId
            }
        }
        return null
    }

    private suspend fun saveOutgoing(
        chatId: Long,
        chatTitle: String,
        text: String,
        serverId: Long?,
        ruleId: String,
    ) {
        val msg = TelegramMessage(
            messageId = serverId ?: -Random.nextLong(1, Long.MAX_VALUE),
            chatId = chatId,
            chatTitle = chatTitle,
            text = text,
            fromName = BotState.info.value.firstName.ifBlank { "TiBot" },
            fromId = BotState.info.value.botId,
            date = System.currentTimeMillis() / 1000,
            isOutgoing = true,
            isAutoReply = true,
            mediaType = "text",
            ruleId = ruleId,
        )
        try {
            messageStore.saveMessage(msg)
        } catch (e: Exception) {
            Log.e(TAG, "save outgoing failed: ${e.message}", e)
        }
    }

    suspend fun recordHit(ruleId: String) {
        val rules = loadRules()
        val updated = rules.map {
            if (it.ruleId == ruleId) it.copy(hitCount = it.hitCount + 1) else it
        }
        saveRules(updated)
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
                hitCount = obj.optInt("hitCount", 0),
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
                    put("hitCount", rule.hitCount)
                }
            )
        }
        context.autoReplyDataStore.edit { prefs ->
            prefs[Keys.RULES] = arr.toString()
        }
    }
}
