package com.faster.tibot.ui.chats

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.message.ChatSummary
import com.faster.tibot.data.message.MessageStore
import com.faster.tibot.data.telegram.TelegramBotClient
import com.faster.tibot.data.telegram.TelegramMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class ChatMessage(
    val id: String,
    val chatId: Long,
    val text: String = "",
    val isOutgoing: Boolean = false,
    val isAutoReply: Boolean = false,
    val isBlocked: Boolean = false,
    val senderName: String = "",
    val time: String = "",
    val date: Long = 0,
    val hasFile: Boolean = false,
    val status: String = "sent",
    val localFilePath: String = "",
    val fileSize: Long = 0L,
    val mediaType: String = "text",
    val fileName: String = "",
    val mimeType: String = "",
    val chatType: String = "private",
    val replyToMessageId: Long = 0L,
)

class ChatsViewModel(application: Application) : AndroidViewModel(application) {
    private val messageStore = MessageStore(application)
    private val settingsRepo = SettingsRepository(application)

    private val _botClient = MutableStateFlow<TelegramBotClient?>(null)

    init {
        viewModelScope.launch {
            val token = settingsRepo.botToken.first()
            if (token.isNotBlank()) {
                _botClient.value = TelegramBotClient(token)
            }
        }
    }

    val chats: StateFlow<List<ChatSummary>> = messageStore.getAllChatsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableStateFlow(listOf<ChatMessage>())
    val messages = _messages.asStateFlow()

    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId = _activeChatId.asStateFlow()

    private val _replyToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyToMessage: StateFlow<ChatMessage?> = _replyToMessage.asStateFlow()

    private var messagesJob: Job? = null

    fun selectChat(chatId: Long) {
        _activeChatId.value = chatId
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            messageStore.getMessagesFlow(chatId).collect { msgs ->
                _messages.value = msgs.map { msg -> msg.toUi() }
            }
        }
    }

    fun sendMessage(chatId: Long, text: String) {
        val localId = "local_text_${System.currentTimeMillis()}_${Random.nextInt()}"
        val pendingMsg = ChatMessage(
            id = localId,
            chatId = chatId,
            text = text,
            isOutgoing = true,
            senderName = "我",
            time = currentTime(),
            status = "sending",
            mediaType = "text",
        )
        _messages.value = _messages.value + pendingMsg

        val client = _botClient.value
        if (client == null) {
            Log.w(TAG, "client not ready, marking message as failed")
            _messages.value = _messages.value.map { m ->
                if (m.id == localId) m.copy(status = "failed") else m
            }
            return
        }

        viewModelScope.launch {
            val result = client.sendMessage(chatId, text)
            result.fold(
                onSuccess = { serverId ->
                    val chatTitle = chats.value.find { it.chatId == chatId }?.chatTitle ?: ""
                    val sentMsg = TelegramMessage(
                        messageId = serverId ?: -Random.nextLong(1, Long.MAX_VALUE),
                        chatId = chatId,
                        chatTitle = chatTitle,
                        text = text,
                        fromName = "我",
                        date = System.currentTimeMillis() / 1000,
                        isOutgoing = true,
                        mediaType = "text",
                    )
                    messageStore.saveMessage(sentMsg)
                    _messages.value = _messages.value.map { m ->
                        if (m.id == localId) m.copy(id = "msg_${sentMsg.messageId}", status = "sent") else m
                    }
                },
                onFailure = { _ ->
                    _messages.value = _messages.value.map { m ->
                        if (m.id == localId) m.copy(status = "failed") else m
                    }
                },
            )
        }
    }

    fun sendFile(chatId: Long, filePath: String, caption: String = "", replaceLocalId: String? = null) {
        val localId = "local_file_${System.currentTimeMillis()}_${Random.nextInt()}"
        val fileName = filePath.substringAfterLast('/').ifBlank { "file" }
        val fileSize = runCatching { File(filePath).length() }.getOrDefault(0L)

        val displayText = if (caption.isNotBlank()) "[文件: $fileName] $caption" else "[文件: $fileName]"

        val client = _botClient.value
        if (client == null) {
            Log.w(TAG, "client not ready, marking file message as failed")
            val failed = ChatMessage(
                id = localId,
                chatId = chatId,
                text = displayText,
                isOutgoing = true,
                senderName = "我",
                time = currentTime(),
                hasFile = true,
                status = "failed",
                fileName = fileName,
                mediaType = "document",
                fileSize = fileSize,
                localFilePath = filePath,
            )
            if (replaceLocalId != null) {
                _messages.value = _messages.value.filter { it.id != replaceLocalId }
            }
            _messages.value = _messages.value + failed
            return
        }

        val pendingMsg = ChatMessage(
            id = localId,
            chatId = chatId,
            text = displayText,
            isOutgoing = true,
            senderName = "我",
            time = currentTime(),
            hasFile = true,
            status = "sending",
            fileName = fileName,
            mediaType = "document",
            fileSize = fileSize,
            localFilePath = filePath,
        )
        if (replaceLocalId != null) {
            _messages.value = _messages.value.filter { it.id != replaceLocalId }
        }
        _messages.value = _messages.value + pendingMsg

        viewModelScope.launch {
            val result = client.sendDocument(chatId, filePath, caption)
            result.fold(
                onSuccess = { serverId ->
                    val chatTitle = chats.value.find { it.chatId == chatId }?.chatTitle ?: ""
                    val sentMsg = TelegramMessage(
                        messageId = serverId ?: -Random.nextLong(1, Long.MAX_VALUE),
                        chatId = chatId,
                        chatTitle = chatTitle,
                        text = caption.ifBlank { fileName },
                        fromName = "我",
                        date = System.currentTimeMillis() / 1000,
                        fileName = fileName,
                        isOutgoing = true,
                        fileId = "",
                        fileSize = fileSize,
                        mimeType = "",
                        mediaType = "document",
                        localFilePath = filePath,
                    )
                    messageStore.saveMessage(sentMsg)
                    _messages.value = _messages.value.map { m ->
                        if (m.id == localId) m.copy(id = "msg_${sentMsg.messageId}", status = "sent") else m
                    }
                },
                onFailure = { _ ->
                    _messages.value = _messages.value.map { m ->
                        if (m.id == localId) m.copy(status = "failed") else m
                    }
                },
            )
        }
    }

    fun retrySendFile(chatId: Long, message: ChatMessage) {
        if (message.localFilePath.isBlank() || !File(message.localFilePath).exists()) {
            _messages.value = _messages.value.map {
                if (it.id == message.id) it.copy(text = "[文件已清理，请重新选择]") else it
            }
            return
        }
        val caption = message.text.removePrefix("[文件: ${message.fileName}] ")
            .takeIf { it != message.text } ?: ""
        sendFile(chatId, message.localFilePath, caption, replaceLocalId = message.id)
    }

    fun setReplyTo(message: ChatMessage?) {
        _replyToMessage.value = message
    }

    fun sendReply(replyToMessageId: Long, text: String) {
        val chatId = _activeChatId.value ?: return
        val client = _botClient.value ?: return

        if (text.isBlank()) return

        val tempId = "local_reply_${System.currentTimeMillis()}_${Random.nextInt()}"
        val pendingMsg = ChatMessage(
            id = tempId,
            chatId = chatId,
            text = text,
            isOutgoing = true,
            senderName = "我",
            time = currentTime(),
            status = "sending",
            mediaType = "text",
            replyToMessageId = replyToMessageId,
        )
        _messages.value = _messages.value + pendingMsg

        viewModelScope.launch {
            val result = client.sendMessage(chatId, text, replyToMessageId)
            result.fold(
                onSuccess = { serverId ->
                    val chatTitle = chats.value.find { it.chatId == chatId }?.chatTitle ?: ""
                    val sentMsg = TelegramMessage(
                        messageId = serverId ?: -Random.nextLong(1, Long.MAX_VALUE),
                        chatId = chatId,
                        chatTitle = chatTitle,
                        text = text,
                        fromName = "我",
                        date = System.currentTimeMillis() / 1000,
                        isOutgoing = true,
                        mediaType = "text",
                        replyToMessageId = replyToMessageId,
                    )
                    messageStore.saveMessage(sentMsg)
                    _messages.value = _messages.value.map { m ->
                        if (m.id == tempId) m.copy(id = "msg_${sentMsg.messageId}", status = "sent") else m
                    }
                    _replyToMessage.value = null
                },
                onFailure = { _ ->
                    _messages.value = _messages.value.map { m ->
                        if (m.id == tempId) m.copy(status = "failed") else m
                    }
                },
            )
        }
    }

    fun refreshChats() {
        // No-op: chats are now reactive via stateIn(getAllChatsFlow).
    }

    private fun currentTime(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    companion object {
        private const val TAG = "ChatsViewModel"
    }
}

private fun TelegramMessage.toUi(): ChatMessage = ChatMessage(
    id = "msg_$messageId",
    chatId = chatId,
    text = text,
    isOutgoing = isOutgoing,
    isAutoReply = isAutoReply,
    isBlocked = isBlocked,
    senderName = fromName,
    time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(date * 1000)),
    date = date,
    hasFile = fileName.isNotBlank() || mediaType != "text",
    status = "sent",
    localFilePath = localFilePath,
    fileSize = fileSize,
    mediaType = mediaType,
    fileName = fileName,
    mimeType = mimeType,
    chatType = chatType,
    replyToMessageId = replyToMessageId,
)
