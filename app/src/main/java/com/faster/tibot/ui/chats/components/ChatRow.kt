package com.faster.tibot.ui.chats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faster.tibot.data.message.ChatSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChatRow(
    chat: ChatSummary,
    onClick: () -> Unit,
) {
    val previewText = formatPreviewText(chat)
    val timeText = formatChatTime(chat.lastTime)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TgAvatar(
            chatId = chat.chatId,
            label = chat.avatarLetter.toString(),
            size = 54.dp,
            fontSize = 22.sp,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.chatTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            if (timeText.isNotEmpty()) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chat.messageCount > 1) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (chat.messageCount > 99) "99+" else chat.messageCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun formatPreviewText(chat: ChatSummary): String {
    val prefix = when {
        chat.lastIsAutoReply -> "🤖 "
        chat.lastOutgoing -> "你: "
        chat.lastSender.isNotBlank() -> "${chat.lastSender}: "
        else -> ""
    }
    return prefix + chat.lastMessage
}

fun formatChatTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val date = Date(epochSeconds * 1000)
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1 -> "昨天"
        now.get(Calendar.WEEK_OF_YEAR) == msgCal.get(Calendar.WEEK_OF_YEAR) &&
            now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) ->
            SimpleDateFormat("EEE", Locale.CHINESE).format(date)
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(date)
    }
}
