package com.faster.tibot.data.message

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
    val lastSender: String = "",
    val lastOutgoing: Boolean = false,
    val lastIsAutoReply: Boolean = false,
    val isAdmin: Boolean = false,
    /**
     * 最近一条消息的 messageId (来自 chat_msgs_<chatId> 的 max)。
     * R1-A 引入, 用于和 lastReadMessageId 一起计算 unreadCount。
     * 默认 0 表示该 chat 还没有消息。
     */
    val lastMessageId: Long = 0L,
    /**
     * 未读消息数 (= max(0, lastMessageId - lastReadMessageId))。
     * 派生字段, 解析时实时计算, 不持久化。
     */
    val unreadCount: Int = 0,
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

        /**
         * 持久化每个 chat 的最后已读 messageId。
         * R1-A 引入, 配套 ChatSummary.lastMessageId 算 unreadCount。
         */
        fun lastRead(chatId: Long) = longPreferencesKey("last_read_$chatId")
    }

    private object MigrationKeys {
        val CHAT_TITLES_DONE = stringPreferencesKey("migration_chat_titles_done")
    }

    suspend fun saveMessage(msg: TelegramMessage, isCurrentUserAdmin: Boolean = false) {
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
            updateChatSummary(prefs, msg, arr.length(), isCurrentUserAdmin)
        }
    }

    private fun updateChatSummary(prefs: MutablePreferences, msg: TelegramMessage, messageCount: Int, isCurrentUserAdmin: Boolean = false) {
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
                updatedList.put(buildChatSummaryJson(msg, messageCount, isCurrentUserAdmin))
                found = true
            } else {
                updatedList.put(item)
            }
        }
        if (!found) {
            updatedList.put(buildChatSummaryJson(msg, messageCount, isCurrentUserAdmin))
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

    private fun buildChatSummaryJson(msg: TelegramMessage, messageCount: Int, isCurrentUserAdmin: Boolean = false): JSONObject {
        val title = msg.chatTitle
        return JSONObject().apply {
            put("chatId", msg.chatId)
            put("chatTitle", title)
            put("lastMessage", msg.text.take(80))
            put("lastTime", msg.date)
            put("messageCount", messageCount)
            put("avatarLetter", title.firstOrNull()?.uppercaseChar() ?: '?')
            put("lastSender", msg.fromName.take(40))
            put("lastOutgoing", msg.isOutgoing)
            put("lastIsAutoReply", msg.isAutoReply)
            put("isAdmin", isCurrentUserAdmin)
            put("lastMessageId", msg.messageId)
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
            .map { prefs ->
                val lastReadMap = readLastReadMap(prefs)
                parseChatsSafe(prefs[Keys.CHAT_LIST] ?: "[]", lastReadMap)
            }
            .distinctUntilChanged()

    suspend fun getAllChats(): List<ChatSummary> = getAllChatsFlow().first()

    /**
     * 从当前 prefs snapshot 中收集所有 `last_read_<chatId>` 键, 返回 chatId -> lastReadMessageId 映射。
     *
     * R1-A 引入: 未读数计算需要"每个 chat 的 lastRead"做对照。
     * 之所以扫描整个 prefs.asMap() 而不是每个 chat 单独读, 是因为 last_read_<chatId>
     * 是独立的 longPreferencesKey, 没有内置的"所有键"枚举 API, 必须扫所有 entry。
     */
    private fun readLastReadMap(prefs: Preferences): Map<Long, Long> {
        val out = mutableMapOf<Long, Long>()
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            if (name.startsWith("last_read_")) {
                val chatId = name.removePrefix("last_read_").toLongOrNull() ?: continue
                val v = (value as? Long) ?: continue
                out[chatId] = v
            }
        }
        return out
    }

    /**
     * 把指定 chat 标记为"已读到 messageId"。单调递增, 即只在新 messageId > 当前 lastRead 时才写入。
     *
     * R1-A 引入: 配套 ChatsViewModel.selectChat / markAllRead 调用。
     */
    suspend fun markRead(chatId: Long, messageId: Long) {
        if (messageId <= 0L) return
        context.messageDataStore.edit { prefs ->
            val key = Keys.lastRead(chatId)
            val current = prefs[key] ?: 0L
            if (messageId > current) {
                prefs[key] = messageId
            }
        }
    }

    /**
     * 当前 chat 的未读消息数 (近似 = lastMessageId - lastReadMessageId, 不小于 0)。
     *
     * 不持久化: 任何时刻都从最新的 lastMessageId (来自 ChatSummary) 和 lastRead 派生。
     */
    suspend fun getUnreadCount(chatId: Long): Int = getUnreadCountFlow(chatId).first()

    fun getUnreadCountFlow(chatId: Long): Flow<Int> =
        context.messageDataStore.data
            .map { prefs ->
                val lastRead = prefs[Keys.lastRead(chatId)] ?: 0L
                val chats = parseChatsSafe(prefs[Keys.CHAT_LIST] ?: "[]", readLastReadMap(prefs))
                val lastMsg = chats.firstOrNull { it.chatId == chatId }?.lastMessageId ?: 0L
                (lastMsg - lastRead).coerceAtLeast(0L).toInt()
            }
            .distinctUntilChanged()

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

    /**
     * 数据迁移: 回填已存 ChatSummary 中空白的 chatTitle / avatarLetter。
     *
     * 背景: P1.5 修复在 TelegramBotClient.parseMessage 中加了 chatTitle 的 takeIf 链,
     * 只对新消息生效。修复前已存的 ChatSummary.chatTitle 仍可能为空,导致用户看到
     * 头像"?"和"聊天"占位。
     *
     * 策略: 遍历 chat_list,对每个 chatTitle 为空的 summary,扫描
     * chat_msgs_{chatId} 中所有消息,挑 chatTitle 非空且 date 最大的那条回填。
     *
     * 幂等: 通过 MigrationKeys.CHAT_TITLES_DONE 标记,每个 DataStore 只跑一次。
     */
    suspend fun migrateChatTitles() {
        var migrated = 0
        var scanned = 0

        context.messageDataStore.edit { prefs ->
            // 幂等检查放在 edit 块内部,防止两个并发调用都通过标志位检查后同时进入迁移逻辑
            if (prefs[MigrationKeys.CHAT_TITLES_DONE] == "true") {
                return@edit
            }

            val chatListStr = prefs[Keys.CHAT_LIST] ?: "[]"
            val chatListArr = try {
                JSONArray(chatListStr)
            } catch (_: Exception) {
                JSONArray()
            }
            val updatedList = JSONArray()
            for (i in 0 until chatListArr.length()) {
                val item = chatListArr.optJSONObject(i) ?: continue
                val chatId = item.optLong("chatId", 0L)
                if (chatId == 0L) {
                    updatedList.put(item)
                    continue
                }
                scanned++
                val currentTitle = item.optString("chatTitle", "")
                if (currentTitle.isNotBlank()) {
                    updatedList.put(item)
                    continue
                }
                // 从消息里挑一条 chatTitle 非空且 date 最大的
                val msgKey = Keys.chatMessages(chatId)
                val raw = prefs[msgKey] ?: "[]"
                val msgsArr = try {
                    JSONArray(raw)
                } catch (_: Exception) {
                    JSONArray()
                }
                var bestTitle: String? = null
                var bestDate = -1L
                for (j in 0 until msgsArr.length()) {
                    val m = msgsArr.optJSONObject(j) ?: continue
                    val title = m.optString("chatTitle", "")
                    if (title.isNotBlank()) {
                        val date = m.optLong("date", 0L)
                        if (date >= bestDate) {
                            bestDate = date
                            bestTitle = title
                        }
                    }
                }
                if (bestTitle != null) {
                    val newItem = JSONObject(item.toString())
                    newItem.put("chatTitle", bestTitle)
                    newItem.put("avatarLetter", bestTitle.firstOrNull()?.uppercaseChar() ?: '?')
                    updatedList.put(newItem)
                    migrated++
                } else {
                    updatedList.put(item)
                }
            }
            if (migrated > 0) {
                prefs[Keys.CHAT_LIST] = updatedList.toString()
            }
            // 无论是否有数据迁移,都标记为已执行 — 避免每次启动都扫描
            prefs[MigrationKeys.CHAT_TITLES_DONE] = "true"
        }

        Log.i(
            "MessageStore",
            "migrateChatTitles: scanned=$scanned migrated=$migrated",
        )
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

    private fun parseChatsSafe(raw: String, lastReadMap: Map<Long, Long> = emptyMap()): List<ChatSummary> {
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
            val lastMessageId = obj.optLong("lastMessageId", 0L)
            val lastRead = lastReadMap[chatId] ?: 0L
            val unreadCount = (lastMessageId - lastRead).coerceAtLeast(0L).toInt()
            list.add(
                ChatSummary(
                    chatId = chatId,
                    chatTitle = title,
                    lastMessage = obj.optString("lastMessage", ""),
                    lastTime = lastTime,
                    lastMessageTime = formatTime(lastTime),
                    messageCount = obj.optInt("messageCount", 0),
                    avatarLetter = obj.optString("avatarLetter", "?")
                        .firstOrNull() ?: title.firstOrNull() ?: '?',
                    lastSender = obj.optString("lastSender", ""),
                    lastOutgoing = obj.optBoolean("lastOutgoing", false),
                    lastIsAutoReply = obj.optBoolean("lastIsAutoReply", false),
                    isAdmin = obj.optBoolean("isAdmin", false),
                    lastMessageId = lastMessageId,
                    unreadCount = unreadCount,
                )
            )
        }
        return list.sortedByDescending { it.lastTime }
    }
}
