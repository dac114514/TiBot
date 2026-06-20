package com.faster.tibot.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faster.tibot.data.telegram.BotState
import com.faster.tibot.ui.chats.components.TgAvatar

@Composable
fun BotInfoCard(
    botInfo: BotState.BotInfo,
    uptimeSeconds: Long,
    onDetailsClick: () -> Unit = {},
) {
    val displayName = botInfo.firstName.ifBlank { "TiBot" }
    val username = if (botInfo.username.isNotBlank()) "@${botInfo.username}" else ""
    val onlineText = when {
        botInfo.errorReason != null -> "\u26A0 ${botInfo.errorReason}"
        botInfo.isOnline -> "\uD83D\uDFE2 在线"
        else -> "\uD83D\uDFE1 连接中…"
    }
    val uptimeText = formatUptime(uptimeSeconds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TgAvatar(
            chatId = botInfo.botId.takeIf { it != 0L } ?: 0L,
            label = botInfo.firstName.take(1).ifBlank { "T" },
            size = 72.dp,
            fontSize = 36.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (username.isNotBlank() && username != "@${displayName}") {
            Spacer(Modifier.height(2.dp))
            Text(
                text = username,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = onlineText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uptimeText.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "· 运行 $uptimeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onDetailsClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("详情", modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

private fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
