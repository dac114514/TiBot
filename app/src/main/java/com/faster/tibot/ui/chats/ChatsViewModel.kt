package com.faster.tibot.ui.chats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.message.MessageStore
import com.faster.tibot.data.telegram.TelegramBotClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
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
    private val messageStore = MessageStore(application)
    private val settingsRepo = SettingsRepository(application)
    private var botClient: TelegramBotClient? = null

    private val _chats = MutableStateFlow(listOf<ChatSummary>())
    val chats = _chats.asStateFlow()

    private val _messages = MutableStateFlow(listOf<ChatMessage>())
    val messages = _messages.asStateFlow()

    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId = _activeChatId.asStateFlow()

    init {
        viewModelScope.launch {
            val token = settingsRepo.botToken.first()
            botClient = if (token.isNotBlank()) TelegramBotClient(token) else null
        }
        viewModelScope.launch {
            messageStore.getAllChatsFlow().collect { list ->
                _chats.value = list.map { cs ->
                    ChatSummary(
                        chatId = cs.chatId,
                        title = cs.chatTitle,
                        lastMessage = cs.lastMessage,
                        lastMessageTime = formatTime(cs.lastTime),
                        unreadCount = 0,
                        avatarLetter = cs.chatTitle.firstOrNull() ?: '?',
                    )
                }
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
                        time = SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(msg.date * 1000)),
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    fun sendMessage(chatId: Long, text: String) {
        viewModelScope.launch {
            try {
                botClient?.sendMessage(chatId, text)
            } catch (_: Exception) {
            }
            // Optimistic local add regardless
            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
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
    }

    fun sendFile(chatId: Long, filePath: String, caption: String = "") {
        // TODO: Use TelegramBotClient to send via HTTP API
        val fileName = filePath.substringAfterLast("/")
        val displayText = if (caption.isNotBlank()) "[文件: $fileName] $caption" else "[文件: $fileName]"
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
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
        viewModelScope.launch {
            try {
                _chats.value = messageStore.getAllChats().map { cs ->
                    ChatSummary(
                        chatId = cs.chatId,
                        title = cs.chatTitle,
                        lastMessage = cs.lastMessage,
                        lastMessageTime = formatTime(cs.lastTime),
                        unreadCount = 0,
                        avatarLetter = cs.chatTitle.firstOrNull() ?: '?',
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun formatTime(epochSeconds: Long): String {
        if (epochSeconds == 0L) return ""
        val date = Date(epochSeconds * 1000)
        val cal = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = date }
        val sdf = if (cal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
        )
            SimpleDateFormat("HH:mm", Locale.getDefault())
        else
            SimpleDateFormat("MM/dd", Locale.getDefault())
        return sdf.format(date)
    }
}
