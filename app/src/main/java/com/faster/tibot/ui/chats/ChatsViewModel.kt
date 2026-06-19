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
    private val mqtt = MqttManager.getInstance()

    private val _chats = MutableStateFlow(listOf<ChatSummary>())
    val chats = _chats.asStateFlow()

    private val _messages = MutableStateFlow(listOf<ChatMessage>())
    val messages = _messages.asStateFlow()

    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId = _activeChatId.asStateFlow()

    private val subscribedTopics = mutableListOf<String>()

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    init {
        viewModelScope.launch {
            mqtt.messages.collect { event ->
                handleIncomingMessage(event.topic, event.payload)
            }
        }
    }

    private fun handleIncomingMessage(topic: String, payload: String) {
        try {
            val json = JSONObject(payload)
            val parts = topic.split("/")
            when {
                // tibot/msg/in/{chatId}
                parts.size == 4 && parts[0] == "tibot" && parts[1] == "msg" && parts[2] == "in" -> {
                    val chatId = parts[3].toLongOrNull() ?: return
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
                    updateChatSummary(chatId, json.optString("sender_name", ""), msg.text, msg.time)
                }
                // tibot/chat/list
                parts.size == 3 && parts[0] == "tibot" && parts[1] == "chat" && parts[2] == "list" -> {
                    val chatsArray = json.optJSONArray("chats") ?: return
                    val chatList = mutableListOf<ChatSummary>()
                    for (i in 0 until chatsArray.length()) {
                        val chatObj = chatsArray.getJSONObject(i)
                        chatList.add(ChatSummary(
                            chatId = chatObj.getLong("chat_id"),
                            title = chatObj.optString("title", "Chat ${chatObj.getLong("chat_id")}"),
                            lastMessage = chatObj.optString("last_message", ""),
                            lastMessageTime = chatObj.optString("last_message_time", ""),
                            unreadCount = chatObj.optInt("unread_count", 0),
                            avatarLetter = chatObj.optString("title", "?").firstOrNull() ?: '?',
                        ))
                    }
                    _chats.value = chatList
                }
                // tibot/chat/history/{chatId}
                parts.size == 4 && parts[0] == "tibot" && parts[1] == "chat" && parts[2] == "history" -> {
                    val chatId = parts[3].toLongOrNull() ?: return
                    val msgsArray = json.optJSONArray("messages") ?: return
                    val msgs = mutableListOf<ChatMessage>()
                    for (i in 0 until msgsArray.length()) {
                        val msgObj = msgsArray.getJSONObject(i)
                        msgs.add(ChatMessage(
                            id = msgObj.optString("message_id", "hist_$i"),
                            chatId = chatId,
                            text = msgObj.optString("text", ""),
                            isOutgoing = false,
                            senderName = msgObj.optString("sender_name", ""),
                            time = msgObj.optString("date", ""),
                        ))
                    }
                    if (_activeChatId.value == chatId) {
                        _messages.value = msgs
                    }
                }
                // tibot/msg/file/{chatId}
                parts.size == 4 && parts[0] == "tibot" && parts[1] == "msg" && parts[2] == "file" -> {
                    val chatId = parts[3].toLongOrNull() ?: return
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
        } catch (_: Exception) {}
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

        // Clear old messages, wait for MQTT history response
        _messages.value = emptyList()

        // Refresh chat list
        refreshChats()
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

}
