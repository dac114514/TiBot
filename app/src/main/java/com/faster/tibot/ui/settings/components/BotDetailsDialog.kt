package com.faster.tibot.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.faster.tibot.data.telegram.BotState

@Composable
fun BotDetailsDialog(
    botInfo: BotState.BotInfo,
    uptimeSeconds: Long,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Bot 详情", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                DetailRow("Bot ID", botInfo.botId.toString())
                DetailRow("Bot 名称", botInfo.firstName.ifBlank { "未知" })
                DetailRow(
                    "Username",
                    if (botInfo.username.isNotBlank()) "@${botInfo.username}" else "未设置",
                )
                DetailRow(
                    "状态",
                    when {
                        botInfo.errorReason != null -> "❌ ${botInfo.errorReason}"
                        botInfo.isOnline -> "🟢 在线"
                        else -> "🟡 连接中…"
                    },
                )
                DetailRow("运行时间", formatUptimeDetailed(uptimeSeconds))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatUptimeDetailed(seconds: Long): String {
    if (seconds <= 0) return "未启动"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "${h}h ${m}m ${s}s"
}
