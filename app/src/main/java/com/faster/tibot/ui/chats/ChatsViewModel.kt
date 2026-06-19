package com.faster.tibot.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class ChatsViewModel : ViewModel() {
    private val _chats = MutableStateFlow(listOf<ChatSummary>())
    val chats = _chats.asStateFlow()

    private val _messages = MutableStateFlow(listOf<ChatMessage>())
    val messages = _messages.asStateFlow()

    private val _activeChatId = MutableStateFlow<Long?>(null)
    val activeChatId = _activeChatId.asStateFlow()

    init {
        // Sample data for development / demo
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

    fun selectChat(chatId: Long) {
        _activeChatId.value = chatId
        // TODO: subscribe to MQTT tibot/msg/in/{chatId}
        // TODO: request history via MQTT tibot/chat/history/{chatId}

        // Load sample messages for the selected chat
        _messages.value = sampleMessagesForChat(chatId)
    }

    fun sendMessage(chatId: Long, text: String) {
        // TODO: publish to MQTT tibot/msg/out/{chatId}

        // Optimistically add the message locally
        val newMsg = ChatMessage(
            id = "local_${System.currentTimeMillis()}",
            chatId = chatId,
            text = text,
            isOutgoing = true,
            senderName = "我",
            time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date()),
        )
        _messages.value = _messages.value + newMsg
    }

    fun sendFile(chatId: Long, filePath: String, caption: String = "") {
        // TODO: publish to MQTT tibot/msg/file/{chatId}
    }

    fun refreshChats() {
        // TODO: request via MQTT tibot/chat/list
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
