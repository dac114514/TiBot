package com.faster.tibot.ui.chats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.message.MessageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val messageStore = MessageStore(application)

    private val _chats = MutableStateFlow(listOf<ChatSummary>())
    val chats = _chats.asStateFlow()

    private val _messages = MutableStateFlow(listOf<ChatMessage>())
    val messages = _messages.asStateFlow()

    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId = _activeChatId.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _chats.value = messageStore.getAllChats()
            } catch (_: Exception) {
                // MessageStore not yet wired (Task 2)
            }
        }
    }

    fun selectChat(chatId: Long) {
        _activeChatId.value = chatId
        viewModelScope.launch {
            try {
                val msgs = messageStore.getMessages(chatId)
                _messages.value = msgs.map { msg ->
                    ChatMessage(
                        id = "msg_${msg.messageId}",
                        chatId = msg.chatId,
                        text = msg.text,
                        isOutgoing = false,
                        senderName = msg.fromName,
                        time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(msg.date * 1000)),
                    )
                }
            } catch (_: Exception) {
                // MessageStore not yet wired
            }
        }
    }

    fun sendMessage(chatId: Long, text: String) {
        // TODO: Use TelegramBotClient to send via HTTP API
        // For now, optimistically add to local state
        val timeFormatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val newMsg = ChatMessage(
            id = "local_${System.currentTimeMillis()}",
            chatId = chatId,
            text = text,
            isOutgoing = true,
            senderName = "我",
            time = timeFormatter.format(java.util.Date()),
        )
        _messages.value = _messages.value + newMsg
    }

    fun sendFile(chatId: Long, filePath: String, caption: String = "") {
        // TODO: Use TelegramBotClient to send via HTTP API
        val fileName = filePath.substringAfterLast("/")
        val displayText = if (caption.isNotBlank()) "[文件: $fileName] $caption" else "[文件: $fileName]"
        val timeFormatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val newMsg = ChatMessage(
            id = "file_${System.currentTimeMillis()}",
            chatId = chatId,
            text = displayText,
            isOutgoing = true,
            senderName = "我",
            time = timeFormatter.format(java.util.Date()),
            hasFile = true,
        )
        _messages.value = _messages.value + newMsg
    }

    fun refreshChats() {
        viewModelScope.launch {
            try {
                _chats.value = messageStore.getAllChats()
            } catch (_: Exception) {
                // MessageStore not yet wired
            }
        }
    }
}
