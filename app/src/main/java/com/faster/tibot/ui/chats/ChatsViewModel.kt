package com.faster.tibot.ui.chats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.mqtt.MqttManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatSummary(
    val chatId: Long,
    val title: String,
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val avatarLetter: Char = '?',
)

data class ChatMessage(
    val id: String,
    val chatId: Long,
    val text: String = "",
    val isOutgoing: Boolean = false,
    val senderName: String = "",
    val time: String = "",
    val hasFile: Boolean = false,
)

class ChatsViewModel(application: Application) : AndroidViewModel(application) {
    private val mqtt = MqttManager.getInstance(application)

    private val _chats = MutableStateFlow(listOf<ChatSummary>())
    val chats = _chats.asStateFlow()

    private val _messages = MutableStateFlow(listOf<ChatMessage>())
    val messages = _messages.asStateFlow()

    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId = _activeChatId.asStateFlow()

    private val subscribedTopics = mutableListOf<String>()

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    init {
        // MQTT connection is managed by TiBotForegroundService

        // Listen for incoming MQTT messages
        viewModelScope.launch {
            mqtt.messages.collect { event ->
                handleIncomingMessage(event.topic, event.payload)
            }
        }

        // Load sample data for development until backend sends real data
        _chats.value = listOf(
            ChatSummary(
                chatId = 1001L,
                title = "张三",
                lastMessage = "好的，明天见！",
                lastMessageTime = "14:32",
                unreadCount = 2,
                avatarLetter = '张',
            ),
            ChatSummary(
                chatId = 1002L,
                title = "技术交流群",
                lastMessage = "李四: 有人用过 Compose 吗？",
                lastMessageTime = "13:15",
                unreadCount = 0,
                avatarLetter = '技',
            ),
            ChatSummary(
                chatId = 1003L,
                title = "王小明",
                lastMessage = "文件已发送",
                lastMessageTime = "昨天",
                unreadCount = 0,
                avatarLetter = '王',
            ),
            ChatSummary(
                chatId = 1004L,
                title = "项目讨论组",
                lastMessage = "赵六: PR 已经合并了",
                lastMessageTime = "昨天",
                unreadCount = 5,
                avatarLetter = '项',
            ),
            ChatSummary(
                chatId = 1005L,
                title = "家人群",
                lastMessage = "妈妈: 晚上回来吃饭吗？",
                lastMessageTime = "周一",
                unreadCount = 1,
                avatarLetter = '家',
            ),
        )
    }

    private fun handleIncomingMessage(topic: String, payload: String) {
        try {
            val json = JSONObject(payload)
            when {
                // Incoming chat message from tibot/msg/in/{chatId}
                topic.startsWith("tibot/msg/in/") -> {
                    val chatId = topic.removePrefix("tibot/msg/in/").toLongOrNull() ?: return
                    val msg = ChatMessage(
                        id = json.optString("message_id", "msg_${System.currentTimeMillis()}"),
                        chatId = chatId,
                        text = json.optString("text", ""),
                        isOutgoing = false,
                        senderName = json.optString("sender_name", ""),
                        time = json.optString("timestamp", timeFormatter.format(Date())),
                    )
                    if (_activeChatId.value == chatId) {
                        _messages.value = _messages.value + msg
                    }
                    // Update chat summary
                    updateChatSummary(chatId, json.optString("sender_name", ""), msg.text, msg.time)
                }
                // Chat list response from tibot/chat/list/response
                topic == "tibot/chat/list/response" -> {
                    val chatsArray = json.optJSONArray("chats") ?: return
                    val chatList = mutableListOf<ChatSummary>()
                    for (i in 0 until chatsArray.length()) {
                        val chatObj = chatsArray.getJSONObject(i)
                        chatList.add(
                            ChatSummary(
                                chatId = chatObj.getLong("chat_id"),
                                title = chatObj.optString("title", "Chat ${chatObj.getLong("chat_id")}"),
                                lastMessage = chatObj.optString("last_message", ""),
                                lastMessageTime = chatObj.optString("last_message_time", ""),
                                unreadCount = chatObj.optInt("unread_count", 0),
                                avatarLetter = chatObj.optString("title", "?").firstOrNull() ?: '?',
                            )
                        )
                    }
                    _chats.value = chatList
                }
                // Incoming file notification
                topic.startsWith("tibot/msg/file/") -> {
                    val chatId = topic.removePrefix("tibot/msg/file/").toLongOrNull() ?: return
                    val msg = ChatMessage(
                        id = json.optString("message_id", "file_${System.currentTimeMillis()}"),
                        chatId = chatId,
                        text = json.optString("caption", "[文件] ${json.optString("file_name", "")}"),
                        isOutgoing = false,
                        senderName = json.optString("sender_name", ""),
                        time = json.optString("timestamp", timeFormatter.format(Date())),
                        hasFile = true,
                    )
                    if (_activeChatId.value == chatId) {
                        _messages.value = _messages.value + msg
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore malformed JSON
        }
    }

    private fun updateChatSummary(chatId: Long, senderName: String, text: String, time: String) {
        _chats.value = _chats.value.map { chat ->
            if (chat.chatId == chatId) {
                chat.copy(
                    lastMessage = if (senderName.isNotEmpty()) "$senderName: $text" else text,
                    lastMessageTime = time,
                    unreadCount = chat.unreadCount + 1,
                )
            } else chat
        }
    }

    fun selectChat(chatId: Long) {
        _activeChatId.value = chatId

        // Unsubscribe from previous chat topics
        subscribedTopics.forEach { mqtt.unsubscribe(it) }
        subscribedTopics.clear()

        // Subscribe to incoming messages for this chat
        val msgTopic = "tibot/msg/in/$chatId"
        mqtt.subscribe(msgTopic)
        subscribedTopics.add(msgTopic)

        // Also subscribe to file notifications for this chat
        val fileTopic = "tibot/msg/file/$chatId"
        mqtt.subscribe(fileTopic)
        subscribedTopics.add(fileTopic)

        // Request chat history via MQTT
        val requestJson = JSONObject().apply {
            put("chat_id", chatId)
            put("action", "history")
        }
        mqtt.publish("tibot/chat/history/$chatId", requestJson.toString())

        // Load sample messages for the selected chat (as fallback)
        _messages.value = sampleMessagesForChat(chatId)
    }

    fun sendMessage(chatId: Long, text: String) {
        // Publish message to MQTT
        val envelope = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text)
            put("timestamp", timeFormatter.format(Date()))
        }
        mqtt.publish("tibot/msg/out/$chatId", envelope.toString())

        // Optimistically add the message locally
        val newMsg = ChatMessage(
            id = "local_${System.currentTimeMillis()}",
            chatId = chatId,
            text = text,
            isOutgoing = true,
            senderName = "我",
            time = timeFormatter.format(Date()),
        )
        _messages.value = _messages.value + newMsg
    }

    fun sendFile(chatId: Long, filePath: String, caption: String = "") {
        val fileName = filePath.substringAfterLast("/")
        val envelope = JSONObject().apply {
            put("chat_id", chatId)
            put("file_path", filePath)
            put("file_name", fileName)
            put("caption", caption)
            put("timestamp", timeFormatter.format(Date()))
        }
        mqtt.publish("tibot/msg/file/$chatId", envelope.toString())

        // Optimistically add a local placeholder message
        val displayText = if (caption.isNotBlank()) "[文件: $fileName] $caption" else "[文件: $fileName]"
        val newMsg = ChatMessage(
            id = "file_${System.currentTimeMillis()}",
            chatId = chatId,
            text = displayText,
            isOutgoing = true,
            senderName = "我",
            time = timeFormatter.format(Date()),
            hasFile = true,
        )
        _messages.value = _messages.value + newMsg
    }

    fun refreshChats() {
        val requestJson = JSONObject().apply {
            put("action", "list")
        }
        mqtt.publish("tibot/chat/list", requestJson.toString())
    }

    override fun onCleared() {
        super.onCleared()
        subscribedTopics.forEach { mqtt.unsubscribe(it) }
        subscribedTopics.clear()
    }

    private fun sampleMessagesForChat(chatId: Long): List<ChatMessage> {
        return listOf(
            ChatMessage(
                id = "1",
                chatId = chatId,
                text = "你好！",
                isOutgoing = false,
                senderName = "对方",
                time = "10:30",
            ),
            ChatMessage(
                id = "2",
                chatId = chatId,
                text = "你好呀，最近怎么样？",
                isOutgoing = true,
                senderName = "我",
                time = "10:31",
            ),
            ChatMessage(
                id = "3",
                chatId = chatId,
                text = "还不错，在忙一个新项目",
                isOutgoing = false,
                senderName = "对方",
                time = "10:32",
            ),
            ChatMessage(
                id = "4",
                chatId = chatId,
                text = "哦？什么项目啊，说来听听",
                isOutgoing = true,
                senderName = "我",
                time = "10:33",
            ),
            ChatMessage(
                id = "5",
                chatId = chatId,
                text = "一个基于 Jetpack Compose 的 Telegram 机器人管理 App",
                isOutgoing = false,
                senderName = "对方",
                time = "10:34",
            ),
            ChatMessage(
                id = "6",
                chatId = chatId,
                text = "听起来很有意思！",
                isOutgoing = true,
                senderName = "我",
                time = "10:35",
            ),
            ChatMessage(
                id = "7",
                chatId = chatId,
                text = "是的，界面设计参考了 Telegram 的风格，使用深色主题",
                isOutgoing = false,
                senderName = "对方",
                time = "10:36",
            ),
            ChatMessage(
                id = "8",
                chatId = chatId,
                text = "期待看到成品！",
                isOutgoing = true,
                senderName = "我",
                time = "10:37",
            ),
        )
    }
}
