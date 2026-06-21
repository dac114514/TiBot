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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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

    // R1-B / B4: 编辑/删除的弹窗状态
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var deletingMessage by remember { mutableStateOf<ChatMessage?>(null) }

    // 消费端 fallback: 如果 ChatSummary.chatTitle 为空(P1.5 修复前的旧数据),
    // 从消息列表里挑最后一条非空 senderName 作为兜底显示,
    // 避免用户看到"?"和"聊天"占位。
    val chatTitle = remember(chat, messages) {
        chat?.chatTitle?.takeIf { it.isNotBlank() }
            ?: messages.lastOrNull { it.senderName.isNotBlank() }?.senderName
            ?: "聊天"
    }
    val chatAvatarLetter = remember(chatTitle) {
        chatTitle.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    // R1-B / B2: 滚到顶时检测, 触发 loadOlderMessages
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            first != null && first.index == 0 && first.offset == 0
        }
    }
    LaunchedEffect(shouldLoadMore, chatId) {
        if (shouldLoadMore) {
            android.util.Log.i("ChatDetailScreen", "loadOlderMessages triggered chatId=$chatId")
            vm.loadOlderMessages(chatId)
        }
    }

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
            .background(bubbles.chatBackground)
            .imePadding(),
    ) {
        ChatTopBar(
            chatTitle = chatTitle,
            chatAvatarLetter = chatAvatarLetter,
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
                                            "edit" -> editingMessage = msg
                                            "delete" -> deletingMessage = msg
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

    // R1-B / B4: 编辑弹窗
    editingMessage?.let { msg ->
        EditMessageDialog(
            initialText = msg.text,
            onDismiss = { editingMessage = null },
            onConfirm = { newText ->
                val messageId = msg.id.removePrefix("msg_").toLongOrNull() ?: 0L
                if (messageId > 0L && newText.isNotBlank()) {
                    vm.editMessage(chatId, messageId, newText)
                }
                editingMessage = null
            },
        )
    }

    // R1-B / B4: 删除确认弹窗
    deletingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { deletingMessage = null },
            title = { Text("删除消息") },
            text = {
                Text(
                    text = msg.text.take(80).ifBlank { "(空消息)" },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val messageId = msg.id.removePrefix("msg_").toLongOrNull() ?: 0L
                    if (messageId > 0L) vm.deleteMessage(chatId, messageId)
                    deletingMessage = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessage = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * R1-B / B4: 编辑消息的对话框 (TextField + 确认/取消)。
 */
@Composable
private fun EditMessageDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑消息") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && text != initialText,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
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
