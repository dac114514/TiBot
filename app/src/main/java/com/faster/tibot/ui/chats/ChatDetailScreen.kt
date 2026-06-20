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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageRequest
import coil.compose.AsyncImage
import com.faster.tibot.util.FileUtils
import kotlinx.coroutines.launch
import java.io.File

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
        ChatDetailTopBar(
            chatTitle = chat?.title ?: "聊天",
            chatAvatarLetter = chat?.avatarLetter ?: '?',
            onBack = onBack,
        )

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
                    MessageBubble(
                        message = message,
                        onRetry = { vm.retrySendFile(chatId, message) },
                    )
                    Spacer(Modifier.height(2.dp))
                }
                item {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        ChatInputBar(
            chatId = chatId,
            onSend = { text ->
                vm.sendMessage(chatId, text)
            },
            onSendFile = { path, caption ->
                vm.sendFile(chatId, path, caption)
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
private fun MessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit = {},
) {
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
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Box(
                modifier = Modifier
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
                    when (message.mediaType) {
                        "photo" -> PhotoContent(message)
                        "video", "video_note", "animation" -> VideoCard(message)
                        "voice" -> VoiceCard(message)
                        "audio" -> AudioCard(message)
                        "document", "sticker" -> DocumentCard(message)
                        else -> TextContent(message, textColor)
                    }
                    if (message.time.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = message.time,
                            style = MaterialTheme.typography.labelSmall,
                            color = timeColor,
                        )
                    }
                }
            }
            if (message.isOutgoing && message.status == "failed") {
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "发送失败",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (message.localFilePath.isNotBlank() && File(message.localFilePath).exists()) {
                        TextButton(onClick = onRetry, modifier = Modifier.height(28.dp)) {
                            Text("重试", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (message.localFilePath.isNotBlank()) {
                        Text(
                            text = "· 文件已清理",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (message.isOutgoing && message.status == "sending") {
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "发送中...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextContent(message: ChatMessage, textColor: Color) {
    Text(
        text = message.text,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
    )
}

@Composable
private fun PhotoContent(message: ChatMessage) {
    val context = LocalContext.current
    val path = message.localFilePath
    val exists = path.isNotBlank() && File(path).exists()
    if (exists) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(path))
                .crossfade(true)
                .build(),
            contentDescription = "图片",
            modifier = Modifier
                .widthIn(max = 260.dp)
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
    if (message.text.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun DocumentCard(message: ChatMessage) {
    Row(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.fileName.ifBlank { "文件" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatFileSize(message.fileSize) + " · " + message.mimeType.ifBlank { "未知类型" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (message.text.isNotBlank() && message.text != message.fileName) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VideoCard(message: ChatMessage) {
    val exists = message.localFilePath.isNotBlank() && File(message.localFilePath).exists()
    Box(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (exists) {
            Icon(
                imageVector = Icons.Filled.PlayCircleFilled,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        } else {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = message.fileName.ifBlank { "视频" },
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
    if (message.text.isNotBlank() && message.text != message.fileName) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun VoiceCard(message: ChatMessage) {
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "语音消息 · ${formatFileSize(message.fileSize)}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AudioCard(message: ChatMessage) {
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.fileName.ifBlank { "音频" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatFileSize(message.fileSize),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "未知大小"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> "%.1f MB".format(mb)
        kb >= 1 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}

@Composable
private fun ChatInputBar(
    chatId: Long,
    onSend: (String) -> Unit,
    onSendFile: (String, String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                runCatching {
                    val path = FileUtils.copyToCache(context, it)
                    onSendFile(path, "")
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = "附件",
                tint = MaterialTheme.colorScheme.secondary,
            )
        }

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
