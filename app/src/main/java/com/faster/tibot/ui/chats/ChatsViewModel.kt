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
        // 数据迁移: 回填旧 ChatSummary 中空白的 chatTitle / avatarLetter
        // 幂等 — MessageStore 内部用 DataStore 标记只跑一次
        viewModelScope.launch {
            runCatching { messageStore.migrateChatTitles() }
                .onFailure { Log.e(TAG, "migrateChatTitles failed", it) }
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

    /**
     * 是否还有更老的消息可加载 (R1-B / B2 引入)。
     * 翻页用 — true 时允许 loadOlderMessages, false 时早退避免无效 IO。
     */
    private var hasMore: Boolean = true

    fun selectChat(chatId: Long) {
        _activeChatId.value = chatId
        hasMore = true
        // R1-A / B1: 进入 chat 时把当前最新一条标为已读, 清零未读。
        // 单聊 + 群聊统一处理: 群聊里某条新消息也只标记那个 chatId 的 lastRead。
        viewModelScope.launch {
            runCatching {
                val latest = messageStore.getMessages(chatId)
                    .maxByOrNull { it.messageId }?.messageId ?: 0L
                if (latest > 0L) messageStore.markRead(chatId, latest)
            }.onFailure { Log.e(TAG, "selectChat markRead failed", it) }
        }
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            // R1-B / B2: 分页加载, 先取最新 200 条作为首屏
            val initial = messageStore.getMessages(chatId, limit = 200, offset = 0)
            _messages.value = initial.map { it.toUi() }
            // probe 更老是否还有: offset=initial.size, limit=1
            val probe = messageStore.getMessages(chatId, limit = 1, offset = initial.size)
            hasMore = probe.isNotEmpty()
            Log.i(TAG, "selectChat chatId=$chatId initial=${initial.size} hasMore=$hasMore")

            // 持续收 flow, 之后到达的新消息 append 到末尾
            messageStore.getMessagesFlow(chatId).collect { msgs ->
                val currentIds = _messages.value.map { it.id }.toSet()
                val newOnes = msgs.map { it.toUi() }.filter { it.id !in currentIds }
                if (newOnes.isNotEmpty()) {
                    _messages.value = _messages.value + newOnes
                }
            }
        }
    }

    /**
     * 加载更老的消息 (R1-B / B2), prepend 到 _messages 列表前面。
     * 没有更老的消息时是 no-op (hasMore 会被置 false)。
     */
    fun loadOlderMessages(chatId: Long) {
        if (!hasMore) {
            Log.i(TAG, "loadOlderMessages skipped: no more older messages")
            return
        }
        val currentSize = _messages.value.size
        if (currentSize == 0) {
            Log.w(TAG, "loadOlderMessages skipped: messages empty (selectChat not called?)")
            return
        }
        viewModelScope.launch {
            runCatching {
                val older = messageStore.getMessages(chatId, limit = 200, offset = currentSize)
                if (older.isEmpty()) {
                    hasMore = false
                    Log.i(TAG, "loadOlderMessages: reached end chatId=$chatId")
                } else {
                    _messages.value = older.map { it.toUi() } + _messages.value
                    if (older.size < 200) hasMore = false
                    Log.i(TAG, "loadOlderMessages chatId=$chatId loaded=${older.size} total=${_messages.value.size} hasMore=$hasMore")
                }
            }.onFailure { Log.e(TAG, "loadOlderMessages failed", it) }
        }
    }

    /**
     * 编辑指定消息 (R1-B / B4)。
     * 1) 调 Telegram API editMessageText
     * 2) 成功后调 messageStore.editMessage 持久化 (isEdited = true)
     * flow 自动 emit, _messages 同步更新。
     */
    fun editMessage(chatId: Long, messageId: Long, newText: String) {
        if (newText.isBlank()) {
            Log.w(TAG, "editMessage: blank text, skip")
            return
        }
        val client = _botClient.value
        if (client == null) {
            Log.w(TAG, "editMessage: client not ready, skip")
            return
        }
        viewModelScope.launch {
            runCatching {
                val apiResult = client.editMessageText(chatId, messageId, newText)
                if (apiResult.isFailure) {
                    Log.w(TAG, "editMessageText API failed: ${apiResult.exceptionOrNull()?.message}")
                    return@launch
                }
                messageStore.editMessage(chatId, messageId, newText)
                    .onFailure { Log.w(TAG, "editMessage store update failed: ${it.message}") }
            }.onFailure { Log.e(TAG, "editMessage failed", it) }
        }
    }

    /**
     * 删除指定消息 (R1-B / B4)。
     * 1) 调 Telegram API deleteMessage
     * 2) 成功后调 messageStore.deleteMessage 从本地存储移除
     * flow 自动 emit, _messages 同步更新。
     */
    fun deleteMessage(chatId: Long, messageId: Long) {
        val client = _botClient.value
        if (client == null) {
            Log.w(TAG, "deleteMessage: client not ready, skip")
            return
        }
        viewModelScope.launch {
            runCatching {
                val apiResult = client.deleteMessage(chatId, messageId)
                if (apiResult.isFailure) {
                    Log.w(TAG, "deleteMessage API failed: ${apiResult.exceptionOrNull()?.message}")
                    return@launch
                }
                messageStore.deleteMessage(chatId, messageId)
                    .onFailure { Log.w(TAG, "deleteMessage store update failed: ${it.message}") }
            }.onFailure { Log.e(TAG, "deleteMessage failed", it) }
        }
    }

    /**
     * 把所有 chat 标记为已读 (R1-A / B1 引入)。
     * 由 UI 入口(例如菜单"全部已读")触发。
     */
    fun markAllRead() {
        viewModelScope.launch {
            runCatching {
                val all = messageStore.getAllChats()
                for (chat in all) {
                    val latest = messageStore.getMessages(chat.chatId)
                        .maxByOrNull { it.messageId }?.messageId
                        ?: chat.lastMessageId
                    if (latest > 0L) messageStore.markRead(chat.chatId, latest)
                }
            }.onFailure { Log.e(TAG, "markAllRead failed", it) }
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
