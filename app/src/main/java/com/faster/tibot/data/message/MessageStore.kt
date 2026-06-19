package com.faster.tibot.data.message

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.faster.tibot.data.telegram.TelegramMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.messageDataStore by preferencesDataStore(name = "tibot_messages")

data class ChatSummary(
    val chatId: Long,
    val chatTitle: String,
    val lastMessage: String,
    val lastTime: Long,
    val messageCount: Int,
)

class MessageStore(private val context: Context) {

    private object Keys {
        val CHAT_LIST = stringPreferencesKey("chat_list")

        fun chatMessages(chatId: Long) = stringPreferencesKey("chat_msgs_$chatId")
    }

    suspend fun saveMessage(msg: TelegramMessage) {
        context.messageDataStore.edit { prefs ->
            // Append to chat messages
            val msgKey = Keys.chatMessages(msg.chatId)
            val existing = prefs[msgKey] ?: "[]"
            val arr = JSONArray(existing)
            arr.put(
                JSONObject().apply {
                    put("messageId", msg.messageId)
                    put("chatId", msg.chatId)
                    put("chatTitle", msg.chatTitle)
                    put("text", msg.text)
                    put("fromName", msg.fromName)
                    put("date", msg.date)
                }
            )
            prefs[msgKey] = arr.toString()

            // Update chat list (deduplicate + insert/update)
            val chatListStr = prefs[Keys.CHAT_LIST] ?: "[]"
            val chatListArr = JSONArray(chatListStr)
            val updatedList = JSONArray()
            var found = false
            for (i in 0 until chatListArr.length()) {
                val item = chatListArr.getJSONObject(i)
                if (item.optLong("chatId", 0) == msg.chatId) {
                    updatedList.put(
                        JSONObject().apply {
                            put("chatId", msg.chatId)
                            put("chatTitle", msg.chatTitle)
                            put("lastMessage", msg.text)
                            put("lastTime", msg.date)
                            put("messageCount", arr.length())
                        }
                    )
                    found = true
                } else {
                    updatedList.put(item)
                }
            }
            if (!found) {
                updatedList.put(
                    JSONObject().apply {
                        put("chatId", msg.chatId)
                        put("chatTitle", msg.chatTitle)
                        put("lastMessage", msg.text)
                        put("lastTime", msg.date)
                        put("messageCount", 1)
                    }
                )
            }
            prefs[Keys.CHAT_LIST] = updatedList.toString()
        }
    }

    suspend fun getMessages(chatId: Long): List<TelegramMessage> {
        val json = context.messageDataStore.data.first()[Keys.chatMessages(chatId)] ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            TelegramMessage(
                messageId = obj.optLong("messageId", 0),
                chatId = obj.optLong("chatId", 0),
                chatTitle = obj.optString("chatTitle", ""),
                text = obj.optString("text", ""),
                fromName = obj.optString("fromName", ""),
                date = obj.optLong("date", 0),
            )
        }
    }

    fun getAllChatsFlow() = context.messageDataStore.data.map { prefs: Preferences ->
        val json = prefs[Keys.CHAT_LIST] ?: "[]"
        val arr = JSONArray(json)
        val chats = mutableListOf<ChatSummary>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            chats.add(
                ChatSummary(
                    chatId = obj.optLong("chatId", 0),
                    chatTitle = obj.optString("chatTitle", ""),
                    lastMessage = obj.optString("lastMessage", ""),
                    lastTime = obj.optLong("lastTime", 0),
                    messageCount = obj.optInt("messageCount", 0),
                )
            )
        }
        chats.sortByDescending { it.lastTime }
        chats
    }

    suspend fun getAllChats(): List<ChatSummary> = getAllChatsFlow().first()
}
