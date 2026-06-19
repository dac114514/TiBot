package com.faster.tibot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.faster.tibot.data.BotConnectionStore
import com.faster.tibot.data.ConnectionStatus
import com.faster.tibot.data.proot.ProotManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val TelegramBlue = Color(0xFF2AABEE)
private val TerminalBlack = Color(0xFF0D0D0D)
private val TerminalGreen = Color(0xFF00FF00)
private val TerminalRed = Color(0xFFFF4444)
private val TerminalGray = Color(0xFFCCCCCC)

@Composable
fun StartupTerminalDialog(
    prootManager: ProotManager,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val status by BotConnectionStore.state.collectAsState()
    var lines by remember { mutableStateOf(listOf("$ starting container...")) }
    val listState = rememberLazyListState()
    var lastOutputLen by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val output = prootManager.getLastOutputLines(30)
            if (output.length > lastOutputLen) {
                lines = output.lines()
                lastOutputLen = output.length
                listState.animateScrollToItem(lines.size - 1)
            }
            delay(300)
        }
    }

    LaunchedEffect(status) {
        when (status.status) {
            ConnectionStatus.ONLINE -> {
                delay(800)
                onSuccess()
            }
            ConnectionStatus.CRASHED, ConnectionStatus.TIMEOUT -> {
                lines = lines + listOf(
                    "",
                    "x ${status.reason}",
                )
            }
            else -> {}
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Starting Container",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when (status.status) {
                            ConnectionStatus.ONLINE -> "ONLINE"
                            ConnectionStatus.CRASHED, ConnectionStatus.TIMEOUT -> "FAILED"
                            else -> "LOADING..."
                        },
                        color = when (status.status) {
                            ConnectionStatus.ONLINE -> TelegramBlue
                            ConnectionStatus.CRASHED, ConnectionStatus.TIMEOUT -> TerminalRed
                            else -> TelegramBlue
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                // Terminal
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = TerminalBlack,
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                    ) {
                        items(lines) { line ->
                            Text(
                                text = line.ifEmpty { " " },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 17.sp,
                                ),
                                color = when {
                                    line.startsWith("x") || line.contains("FAILED") || line.contains("error") -> TerminalRed
                                    line.startsWith("v") || line.contains("ONLINE") -> TerminalGreen
                                    else -> TerminalGray
                                },
                                fontWeight = if (line.startsWith("v") || line.startsWith("x")) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Buttons
                val isTerminal = status.status in listOf(ConnectionStatus.CRASHED, ConnectionStatus.TIMEOUT, ConnectionStatus.OFFLINE)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isTerminal) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                        ) { Text("Back") }
                    }
                    Button(
                        onClick = { if (isTerminal) onBack() },
                        enabled = !isTerminal,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TelegramBlue,
                            disabledContainerColor = Color(0xFF8E8E93),
                        ),
                    ) {
                        if (isTerminal) Text("Retry") else Text("Please wait...")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
