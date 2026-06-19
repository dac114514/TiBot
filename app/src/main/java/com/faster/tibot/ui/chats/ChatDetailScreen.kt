package com.faster.tibot.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatDetailScreen(
    chatId: Long,
    onBack: () -> Unit,
) {
    val vm: ChatsViewModel = viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val messages by vm.messages.collectAsState()
    val chats by vm.chats.collectAsState()
    val chat = remember(chats, chatId) {
        chats.find { it.chatId == chatId }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top bar
        ChatDetailTopBar(
            chatTitle = chat?.title ?: "聊天",
            chatAvatarLetter = chat?.avatarLetter ?: '?',
            onBack = onBack,
        )

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false,
        ) {
            if (messages.isEmpty()) {
                item {
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
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "发送第一条消息开始对话",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            } else {
                item {
                    Spacer(Modifier.height(8.dp))
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                    Spacer(Modifier.height(2.dp))
                }
                item {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Bottom input bar
        ChatInputBar(
            chatId = chatId,
            onSend = { text ->
                vm.sendMessage(chatId, text)
            },
        )
    }
}

@Composable
private fun ChatDetailTopBar(
    chatTitle: String,
    chatAvatarLetter: Char,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Avatar circle
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(avatarColor(chatAvatarLetter)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = chatAvatarLetter.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chatTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = "在线",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val bubbleShape = if (message.isOutgoing) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 4.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp,
        )
    }

    val bubbleColor = if (message.isOutgoing) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (message.isOutgoing) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val timeColor = if (message.isOutgoing) {
        Color.White.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .then(
                    if (!message.isOutgoing) {
                        Modifier.border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outline,
                            bubbleShape,
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column {
                if (message.senderName.isNotEmpty() && !message.isOutgoing) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (message.time.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = message.time,
                            style = MaterialTheme.typography.labelSmall,
                            color = timeColor,
                            modifier = Modifier.align(Alignment.Bottom),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    chatId: Long,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Attachment button
        IconButton(
            onClick = { /* TODO: file picker */ },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = "附件",
                tint = MaterialTheme.colorScheme.secondary,
            )
        }

        // Text input
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "消息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            // Using a clickable Box with Text that updates; in practice we'd use BasicTextField
            // but for simplicity we'll use the text area as an interactive element
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }

        // Send button (only colored when there's text)
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text.trim())
                    text = ""
                }
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = if (text.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                },
            )
        }
    }
}

private fun avatarColor(letter: Char): Color {
    val colors = listOf(
        Color(0xFFE17076), // red
        Color(0xFF7BC862), // green
        Color(0xFFE5CA59), // yellow
        Color(0xFF65AADD), // blue
        Color(0xFFA695E7), // purple
        Color(0xFFEE7AAE), // pink
        Color(0xFF6EC9CB), // teal
        Color(0xFFFFA559), // orange
    )
    return colors[letter.code.and(0x7FFFFFFF) % colors.size]
}
