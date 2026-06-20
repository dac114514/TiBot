package com.faster.tibot.ui.chats

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.faster.tibot.ui.chats.components.BlockedMessage
import com.faster.tibot.ui.chats.components.ChatInputBar
import com.faster.tibot.ui.chats.components.ChatTopBar
import com.faster.tibot.ui.chats.components.MessageBubble
import com.faster.tibot.ui.chats.components.SystemMessage
import com.faster.tibot.ui.theme.LocalTgBubbleColors
import kotlinx.coroutines.launch

@Composable
fun ChatDetailScreen(
    chatId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vm: ChatsViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
    val messages by vm.messages.collectAsState()
    val chats by vm.chats.collectAsState()
    val replyTo by vm.replyToMessage.collectAsState()
    val chat = remember(chats, chatId) { chats.find { it.chatId == chatId } }
    val bubbles = LocalTgBubbleColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showScrollToBottom by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx ->
                showScrollToBottom = (messages.size - idx) > 3
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

        if (replyTo != null) {
            ReplyQuoteBar(
                replyTo = replyTo!!,
                onCancel = { vm.setReplyTo(null) },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
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
                                    onMenuAction = { msg, action ->
                                        when (action) {
                                            "copy" -> {
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                                    as android.content.ClipboardManager
                                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", msg.text))
                                                android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            "reply" -> vm.setReplyTo(msg)
                                            "forward" -> android.widget.Toast.makeText(context, "转发功能待实现", android.widget.Toast.LENGTH_SHORT).show()
                                            "delete" -> android.widget.Toast.makeText(context, "删除功能待实现", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            if (showScrollToBottom) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(44.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "滚动到底部",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        ChatInputBar(
            chatId = chatId,
            onSendText = { text ->
                val current = replyTo
                if (current != null) {
                    val rid = current.id.removePrefix("msg_").toLongOrNull() ?: 0L
                    if (rid > 0L) {
                        vm.sendReply(rid, text)
                    } else {
                        vm.sendMessage(chatId, text)
                        vm.setReplyTo(null)
                    }
                } else {
                    vm.sendMessage(chatId, text)
                }
            },
            onSendFile = { path, caption -> vm.sendFile(chatId, path, caption) },
        )
    }
}

@Composable
private fun ReplyQuoteBar(
    replyTo: ChatMessage,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "回复 ${replyTo.senderName.ifBlank { "消息" }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = replyTo.text.take(80),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "取消回复",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
