package com.faster.tibot.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faster.tibot.data.rootfs.MirrorSource

private val TelegramBlue = Color(0xFF2AABEE)
private val TelegramGray = Color(0xFF8E8E93)
private val TerminalBlack = Color(0xFF0D0D0D)
private val TerminalGreen = Color(0xFF00FF00)
private val TerminalRed = Color(0xFFFF4444)
private val TerminalGray = Color(0xFFCCCCCC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadStep(
    phase: Phase,
    subtitle: String,
    progressPercent: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    speedBytesPerSec: Long,
    logs: List<LogLine>,
    mirrors: List<MirrorSource>,
    selectedMirrorId: String,
    onMirrorSelect: (String) -> Unit,
    onStartDownload: () -> Unit,
    onRetry: () -> Unit,
    onLaunch: () -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val selectedMirror = mirrors.find { it.id == selectedMirrorId }
    val logListState = rememberLazyListState()
    val showMirrorSelector = phase != Phase.IDLE && phase != Phase.SPEED_TEST

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Icon(
            imageVector = when (phase) {
                Phase.DOWNLOADING -> Icons.Filled.KeyboardArrowDown
                Phase.EXTRACTING, Phase.DEPLOYING -> Icons.Filled.Archive
                Phase.DONE -> Icons.Filled.CheckCircle
                Phase.ERROR -> Icons.Filled.Error
                else -> Icons.Filled.CloudDownload
            },
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = when (phase) {
                Phase.ERROR -> MaterialTheme.colorScheme.error
                else -> TelegramBlue
            },
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "env deploy",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        if (showMirrorSelector) {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedMirror?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("mirror") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    mirrors.forEach { mirror ->
                        DropdownMenuItem(
                            text = { Text(mirror.name) },
                            onClick = {
                                onMirrorSelect(mirror.id)
                                dropdownExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (phase == Phase.DOWNLOADING || phase == Phase.EXTRACTING || phase == Phase.DEPLOYING) {
            LinearProgressIndicator(
                progress = { (progressPercent.toFloat() / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = TelegramBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("$progressPercent%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (totalBytes > 0 && phase == Phase.DOWNLOADING) {
                    val downloadedMB = downloadedBytes / 1024 / 1024
                    val totalMB = totalBytes / 1024 / 1024
                    Text("${downloadedMB}MB / ${totalMB}MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (phase == Phase.DOWNLOADING && speedBytesPerSec > 0) {
                val speedMB = speedBytesPerSec / 1024.0 / 1024.0
                Text("${"%.1f".format(speedMB)} MB/s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(8.dp),
            color = TerminalBlack,
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "waiting...",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = TerminalGray,
                    )
                }
            } else {
                LazyColumn(
                    state = logListState,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                ) {
                    items(logs) { line ->
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
                            color = when (line.level) {
                                LogLevel.INFO -> TerminalGreen
                                LogLevel.SUCCESS -> TerminalGreen
                                LogLevel.ERROR -> TerminalRed
                                LogLevel.PROGRESS -> TerminalGray
                            },
                            fontWeight = if (line.level == LogLevel.SUCCESS) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val buttonEnabled = phase == Phase.READY || phase == Phase.ERROR || phase == Phase.DONE
        Button(
            onClick = {
                when (phase) {
                    Phase.READY -> onStartDownload()
                    Phase.ERROR -> onRetry()
                    Phase.DONE -> onLaunch()
                    else -> {}
                }
            },
            enabled = buttonEnabled,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TelegramBlue,
                disabledContainerColor = TelegramGray,
                disabledContentColor = Color.White,
            ),
        ) {
            Text(
                text = when (phase) {
                    Phase.IDLE, Phase.SPEED_TEST -> "testing speed..."
                    Phase.READY -> "start download"
                    Phase.DOWNLOADING -> "downloading..."
                    Phase.EXTRACTING, Phase.DEPLOYING -> "deploying..."
                    Phase.DONE -> "launch"
                    Phase.ERROR -> "retry"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
