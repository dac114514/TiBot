package com.faster.tibot.ui.chats.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.faster.tibot.ui.chats.ChatMessage
import com.faster.tibot.ui.theme.LocalTgBubbleColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    previousMessage: ChatMessage? = null,
    onRetry: () -> Unit = {},
    onLongPress: (ChatMessage) -> Unit = {},
    onMenuAction: (ChatMessage, String) -> Unit = { _, _ -> },
) {
    val isMine = message.isOutgoing
    val arrangement = if (isMine) Arrangement.End else Arrangement.Start
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    showMenu = true
                    onLongPress(message)
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp),
        ) {
            if (shouldShowDateSeparator(message, previousMessage)) {
                DateSeparator(epochSeconds = message.date)
                Spacer(Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = arrangement,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 280.dp),
                ) {
                    if (message.isAutoReply) {
                        AutoReplyBadge()
                    }

                    if (!message.isOutgoing &&
                        (message.chatType == "group" || message.chatType == "supergroup") &&
                        message.senderName.isNotEmpty()) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                        )
                    }

                    BubbleShell(message = message) {
                        MessageContent(message = message)
                    }

                    BubbleStatus(message = message, onRetry = onRetry)
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("复制") },
                onClick = {
                    showMenu = false
                    onMenuAction(message, "copy")
                },
            )
            if (!message.isOutgoing) {
                DropdownMenuItem(
                    text = { Text("回复") },
                    onClick = {
                        showMenu = false
                        onMenuAction(message, "reply")
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("转发") },
                onClick = {
                    showMenu = false
                    onMenuAction(message, "forward")
                },
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    showMenu = false
                    onMenuAction(message, "delete")
                },
            )
        }
    }
}

@Composable
private fun AutoReplyBadge() {
    val bubbles = LocalTgBubbleColors.current
    Row(
        modifier = Modifier
            .padding(start = 4.dp, bottom = 2.dp)
            .background(
                bubbles.outgoingBubble.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "🤖 自动回复",
            style = MaterialTheme.typography.labelSmall,
            color = bubbles.outgoingText,
        )
    }
}

@Composable
private fun BubbleShell(
    message: ChatMessage,
    content: @Composable () -> Unit,
) {
    val bubbles = LocalTgBubbleColors.current
    val isMine = message.isOutgoing
    val shape = RoundedCornerShape(
        topStart = if (isMine) 12.dp else 4.dp,
        topEnd = if (isMine) 4.dp else 12.dp,
        bottomStart = 12.dp,
        bottomEnd = 12.dp,
    )
    val bg = if (isMine) bubbles.outgoingBubble else bubbles.incomingBubble
    val border = if (!isMine) bubbles.incomingBorder else Color.Transparent

    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (border != Color.Transparent) {
                    Modifier.border(0.5.dp, border, shape)
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        content()
    }
}

@Composable
private fun BubbleStatus(message: ChatMessage, onRetry: () -> Unit) {
    if (!message.isOutgoing) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, top = 2.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        when (message.status) {
            "sending" -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.dp,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "发送中",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            "sent" -> Text(
                text = "✓",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            "read" -> Text(
                text = "✓✓",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            "failed" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⚠ 发送失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.height(24.dp),
                ) {
                    Text(
                        text = "重试",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun shouldShowDateSeparator(
    current: ChatMessage,
    previous: ChatMessage?,
): Boolean {
    if (previous == null) return true
    if (current.date <= 0L || previous.date <= 0L) return false
    val calCur = java.util.Calendar.getInstance().apply { timeInMillis = current.date * 1000 }
    val calPrev = java.util.Calendar.getInstance().apply { timeInMillis = previous.date * 1000 }
    return calCur.get(java.util.Calendar.DAY_OF_YEAR) != calPrev.get(java.util.Calendar.DAY_OF_YEAR) ||
        calCur.get(java.util.Calendar.YEAR) != calPrev.get(java.util.Calendar.YEAR)
}
