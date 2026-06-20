package com.faster.tibot.ui.chats

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.faster.tibot.ui.chats.components.BlockedMessage
import com.faster.tibot.ui.chats.components.ChatInputBar
import com.faster.tibot.ui.chats.components.ChatTopBar
import com.faster.tibot.ui.chats.components.MessageBubble
import com.faster.tibot.ui.chats.components.SystemMessage
import com.faster.tibot.ui.theme.LocalTgBubbleColors

@Composable
fun ChatDetailScreen(
    chatId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vm: ChatsViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
    val messages by vm.messages.collectAsState()
    val chats by vm.chats.collectAsState()
    val chat = remember(chats, chatId) { chats.find { it.chatId == chatId } }
    val bubbles = LocalTgBubbleColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bubbles.chatBackground),
    ) {
        ChatTopBar(
            chatTitle = chat?.chatTitle ?: "聊天",
            chatAvatarLetter = chat?.avatarLetter?.toString() ?: "?",
            chatId = chatId,
            isOnline = true,
            onBack = onBack,
            onMenuClick = { /* TODO P2 */ },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false,
        ) {
            if (messages.isEmpty()) {
                item { EmptyChat() }
            } else {
                item { Spacer(Modifier.height(8.dp)) }
                itemsIndexed(messages, key = { _, m -> m.id }) { idx, message ->
                    val prev = messages.getOrNull(idx - 1)
                    when {
                        message.isBlocked ->
                            BlockedMessage(fromName = message.senderName.ifBlank { "unknown" })
                        message.mediaType == "service" ->
                            SystemMessage(text = message.text.ifBlank { "系统消息" })
                        else ->
                            MessageBubble(
                                message = message,
                                previousMessage = prev,
                                onRetry = { vm.retrySendFile(chatId, message) },
                            )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        ChatInputBar(
            chatId = chatId,
            onSendText = { text -> vm.sendMessage(chatId, text) },
            onSendFile = { path, caption -> vm.sendFile(chatId, path, caption) },
        )
    }
}

@Composable
private fun EmptyChat() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "暂无消息",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "发送第一条消息开始对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
