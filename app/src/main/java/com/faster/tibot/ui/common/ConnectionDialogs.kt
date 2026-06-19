package com.faster.tibot.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.faster.tibot.data.BotConnectionStore
import com.faster.tibot.data.ConnectionStatus
import kotlinx.coroutines.delay

// 离线/崩溃/超时 Dialog — 阻塞操作
@Composable
fun OfflineDialog(onExit: () -> Unit, onReconnect: () -> Unit) {
    val state by BotConnectionStore.state.collectAsState()
    val show = state.status == ConnectionStatus.OFFLINE ||
               state.status == ConnectionStatus.CRASHED ||
               state.status == ConnectionStatus.TIMEOUT

    var autoRetryCountdown by remember { mutableStateOf(15) }

    LaunchedEffect(state.status) {
        if (show) {
            autoRetryCountdown = 15
            while (autoRetryCountdown > 0) {
                delay(1000)
                autoRetryCountdown--
            }
            onReconnect() // 自动重连
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { /* 不可取消 */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (state.status) {
                            ConnectionStatus.CRASHED -> "容器已崩溃"
                            ConnectionStatus.TIMEOUT -> "启动超时"
                            else -> "Bot 离线"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            text = {
                Column {
                    Text(state.reason, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "自动重连倒计时: ${autoRetryCountdown}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onReconnect) {
                    Text("重连", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = onExit) {
                    Text("退出 App")
                }
            },
        )
    }
}
