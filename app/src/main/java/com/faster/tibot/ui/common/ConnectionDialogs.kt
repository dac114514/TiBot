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

// 启动 Loading Dialog — 仅已配置用户可见，30s 无响应可返回向导
@Composable
fun LoadingDialog(
    isConfigured: Boolean = false,
    onTimeoutBack: () -> Unit = {},
) {
    val state by BotConnectionStore.state.collectAsState()
    val show = isConfigured && (state.status == ConnectionStatus.CONNECTING || state.status == ConnectionStatus.OFFLINE)

    // 30s 超时后可选择返回向导
    var elapsed by remember(state.status) { mutableStateOf(0) }
    val timeout = elapsed >= 30

    LaunchedEffect(state.status) {
        if (state.status == ConnectionStatus.CONNECTING) {
            elapsed = 0
            while (elapsed < 30) {
                delay(1000)
                elapsed++
            }
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { /* 不可取消 */ },
            title = {
                Text(
                    if (timeout) "启动超时" else "正在启动 Bot",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (!timeout) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(
                        text = when {
                            timeout -> "Bot 未能在 ${timeout}s 内启动，请检查 Token 或网络"
                            state.status == ConnectionStatus.CONNECTING -> "正在启动容器和Bot…"
                            state.status == ConnectionStatus.OFFLINE -> "正在重新连接…"
                            else -> state.reason
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                if (timeout) {
                    TextButton(onClick = onTimeoutBack) {
                        Text("返回设置")
                    }
                }
            },
        )
    }
}

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
