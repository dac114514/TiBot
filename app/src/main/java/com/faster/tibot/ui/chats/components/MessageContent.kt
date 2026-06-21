package com.faster.tibot.ui.chats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.faster.tibot.ui.chats.ChatMessage
import com.faster.tibot.ui.theme.LocalTgBubbleColors
import java.io.File

@Composable
fun BoxScope.MessageContent(message: ChatMessage) {
    when (message.mediaType) {
        "photo" -> PhotoContent(message)
        "video", "video_note", "animation" -> VideoContent(message)
        "voice" -> VoiceContent(message)
        "audio" -> AudioContent(message)
        "document", "sticker" -> DocumentContent(message)
        else -> TextContent(message)
    }
}

@Composable
private fun BoxScope.TextContent(message: ChatMessage) {
    val bubbles = LocalTgBubbleColors.current
    val textColor = if (message.isOutgoing) bubbles.outgoingText else bubbles.incomingText
    val timeColor = if (message.isOutgoing) bubbles.outgoingTime else bubbles.incomingTime

    if (message.text.isNotBlank()) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
    if (message.time.isNotEmpty()) {
        // R0-E: 用 align(BottomEnd) 替代 fillMaxWidth,避免把外层 BubbleShell 撑到 280dp。
        // BubbleShell 是 wrap content 的 Box;内部子项 fillMaxWidth 会让它报告父级
        // maxWidth 作为自己的宽度,导致短消息气泡也被撑宽。BottomEnd 让 TimeText
        // 显示在气泡右下角(类似 Telegram/微信),长消息时 TimeText 覆盖 Text 右下角
        // (与原 Box(fillMaxWidth, contentAlignment=CenterEnd) 行为接近,只是位置从
        // "右上偏中"改为"右下角")。短消息时 TimeText 与 Text 底部 10dp 视觉重叠,
        // 但比撑宽 280dp 更合理。
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            Text(
                text = message.time,
                style = MaterialTheme.typography.labelSmall,
                color = timeColor,
                modifier = Modifier.padding(top = 2.dp, start = 8.dp),
            )
        }
    }
}

@Composable
private fun PhotoContent(message: ChatMessage) {
    val bubbles = LocalTgBubbleColors.current
    val context = LocalContext.current
    val path = message.localFilePath
    val exists = path.isNotBlank() && File(path).exists()
    val captionColor = if (message.isOutgoing) bubbles.outgoingText else bubbles.incomingText

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
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                ),
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
            color = captionColor,
        )
    }
}

@Composable
private fun VideoContent(message: ChatMessage) {
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
        )
    }
}

@Composable
private fun VoiceContent(message: ChatMessage) {
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
private fun AudioContent(message: ChatMessage) {
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

@Composable
private fun DocumentContent(message: ChatMessage) {
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
                text = formatFileSize(message.fileSize) +
                    " · " +
                    message.mimeType.ifBlank { "未知类型" },
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
