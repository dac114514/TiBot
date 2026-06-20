package com.faster.tibot.data.message

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.faster.tibot.data.telegram.TelegramMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Context.messageDataStore by preferencesDataStore(name = "tibot_messages")

data class ChatSummary(
    val chatId: Long,
    val chatTitle: String,
    val lastMessage: String,
    val lastTime: Long,
    val lastMessageTime: String,
    val messageCount: Int,
    val avatarLetter: Char,
) {
    val title: String get() = chatTitle
}

class MessageStore(private val context: Context) {

    companion object {
        const val MAX_MESSAGES_PER_CHAT = 500
        const val MAX_CHATS = 1000

        fun formatTime(epochSeconds: Long): String {
            if (epochSeconds == 0L) return ""
            val date = Date(epochSeconds * 1000)
            val cal = Calendar.getInstance()
            val msgCal = Calendar.getInstance().apply { time = date }
            val sdf = if (cal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
            ) {
                SimpleDateFormat("HH:mm", Locale.getDefault())
            } else {
                SimpleDateFormat("MM/dd", Locale.getDefault())
            }
            return sdf.format(date)
        }
    }

    private object Keys {
        val CHAT_LIST = stringPreferencesKey("chat_list")

        fun chatMessages(chatId: Long) = stringPreferencesKey("chat_msgs_$chatId")
    }

    suspend fun saveMessage(msg: TelegramMessage) {
        context.messageDataStore.edit { prefs ->
            val msgKey = Keys.chatMessages(msg.chatId)
            val raw = prefs[msgKey] ?: "[]"
            val arr = try {
                JSONArray(raw)
            } catch (_: Exception) {
                JSONArray()
            }

            val existingIdx = (0 until arr.length()).indexOfFirst {
                runCatching { arr.getJSONObject(it).optLong("messageId", 0L) }
                    .getOrDefault(0L) == msg.messageId
            }
            if (existingIdx >= 0) {
                arr.put(existingIdx, msg.toJson())
            } else {
                arr.put(msg.toJson())
                while (arr.length() > MAX_MESSAGES_PER_CHAT) {
                    arr.remove(0)
                }
            }

            prefs[msgKey] = arr.toString()
            updateChatSummary(prefs, msg, arr.length())
        }
    }

    private fun updateChatSummary(prefs: Preferences, msg: TelegramMessage, messageCount: Int) {
        val chatListStr = prefs[Keys.CHAT_LIST] ?: "[]"
        val chatListArr = try {
            JSONArray(chatListStr)
        } catch (_: Exception) {
            JSONArray()
        }
        val updatedList = JSONArray()
        var found = false
        for (i in 0 until chatListArr.length()) {
            val item = chatListArr.optJSONObject(i) ?: continue
            if (item.optLong("chatId", 0) == msg.chatId) {
                updatedList.put(buildChatSummaryJson(msg, messageCount))
                found = true
            } else {
                updatedList.put(item)
            }
        }
        if (!found) {
            updatedList.put(buildChatSummaryJson(msg, messageCount))
        }
        val finalList = if (updatedList.length() > MAX_CHATS) {
            val sorted = (0 until updatedList.length())
                .mapNotNull { updatedList.optJSONObject(it) }
                .sortedByDescending { it.optLong("lastTime", 0L) }
            JSONArray().apply {
                for (i in 0 until MAX_CHATS) {
                    sorted.getOrNull(i)?.let { put(it) }
                }
            }
        } else {
            updatedList
        }
        prefs[Keys.CHAT_LIST] = finalList.toString()
    }

    private fun buildChatSummaryJson(msg: TelegramMessage, messageCount: Int): JSONObject {
        val title = msg.chatTitle
        return JSONObject().apply {
            put("chatId", msg.chatId)
            put("chatTitle", title)
            put("lastMessage", msg.text)
            put("lastTime", msg.date)
            put("lastMessageTime", formatTime(msg.date))
            put("messageCount", messageCount)
            put("avatarLetter", title.firstOrNull() ?: '?')
        }
    }

    suspend fun getMessages(chatId: Long): List<TelegramMessage> {
        val raw = context.messageDataStore.data.first()[Keys.chatMessages(chatId)] ?: "[]"
        return parseMessagesSafe(raw).sortedBy { it.date }
    }

    fun getMessagesFlow(chatId: Long): Flow<List<TelegramMessage>> =
        context.messageDataStore.data
            .map { prefs -> parseMessagesSafe(prefs[Keys.chatMessages(chatId)] ?: "[]") }
            .map { it.sortedBy { msg -> msg.date } }
            .distinctUntilChanged()

    fun getAllChatsFlow(): Flow<List<ChatSummary>> =
        context.messageDataStore.data
            .map { prefs -> parseChatsSafe(prefs[Keys.CHAT_LIST] ?: "[]") }
            .distinctUntilChanged()

    suspend fun getAllChats(): List<ChatSummary> = getAllChatsFlow().first()

    suspend fun getAllFileNeedingDownload(): List<TelegramMessage> {
        val chats = getAllChats()
        val result = mutableListOf<TelegramMessage>()
        for (cs in chats) {
            val msgs = getMessages(cs.chatId)
            result.addAll(
                msgs.filter {
                    it.fileId.isNotBlank() &&
                        it.localFilePath.isBlank() &&
                        !it.isBlocked &&
                        !it.isOutgoing
                }
            )
        }
        return result
    }

    suspend fun updateLocalFilePath(chatId: Long, messageId: Long, path: String) {
        context.messageDataStore.edit { prefs ->
            val key = Keys.chatMessages(chatId)
            val raw = prefs[key] ?: "[]"
            val arr = try {
                JSONArray(raw)
            } catch (_: Exception) {
                return@edit
            }
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optLong("messageId", 0L) == messageId) {
                    obj.put("localFilePath", path)
                    break
                }
            }
            prefs[key] = arr.toString()
        }
    }

    private fun parseMessagesSafe(raw: String): List<TelegramMessage> {
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val obj = arr.getJSONObject(i)
                TelegramMessage.fromJson(obj)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseChatsSafe(raw: String): List<ChatSummary> {
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        val list = mutableListOf<ChatSummary>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val chatId = obj.optLong("chatId", 0)
            if (chatId == 0L) continue
            val title = obj.optString("chatTitle", "")
            val lastTime = obj.optLong("lastTime", 0)
            list.add(
                ChatSummary(
                    chatId = chatId,
                    chatTitle = title,
                    lastMessage = obj.optString("lastMessage", ""),
                    lastTime = lastTime,
                    lastMessageTime = obj.optString("lastMessageTime", "")
                        .ifBlank { formatTime(lastTime) },
                    messageCount = obj.optInt("messageCount", 0),
                    avatarLetter = obj.optString("avatarLetter", "")
                        .firstOrNull() ?: title.firstOrNull() ?: '?',
                )
            )
        }
        return list.sortedByDescending { it.lastTime }
    }
}
