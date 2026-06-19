package com.faster.tibot.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faster.tibot.data.rootfs.DownloadProgress
import com.faster.tibot.data.rootfs.DownloadState
import com.faster.tibot.data.rootfs.MirrorSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadStep(
    progress: DownloadProgress,
    mirrors: List<MirrorSource>,
    selectedMirrorId: String,
    triedMirrorIds: List<String>,
    onMirrorSelect: (String) -> Unit,
    onStartDownload: () -> Unit,
    onRetry: () -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val selectedMirror = mirrors.find { it.id == selectedMirrorId }
    val isError = progress.state == DownloadState.ERROR
    val isDone = progress.state == DownloadState.DONE
    val isActive = progress.state == DownloadState.DOWNLOADING
    val isExtracting = progress.state == DownloadState.EXTRACTING
    val hasLogs = progress.logs.isNotEmpty()
    val logListState = rememberLazyListState()

    // Auto-scroll log to bottom
    LaunchedEffect(progress.logs.size) {
        if (progress.logs.isNotEmpty()) {
            logListState.animateScrollToItem(progress.logs.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        // ---- Status Header ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isError -> Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                isDone -> Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                isActive || isExtracting -> CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                else -> Icon(Icons.Filled.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = when {
                    isError -> "下载失败"
                    isExtracting -> "正在部署环境…"
                    isDone -> "部署完成"
                    isActive -> "正在下载 Ubuntu 环境"
                    else -> "准备下载 Ubuntu 环境"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- Progress bar ----
        if (isActive || isExtracting) {
            LinearProgressIndicator(
                progress = { (progress.percent.toFloat() / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${progress.percent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress.totalBytes > 0 && !isExtracting) {
                    val downloadedMB = progress.downloadedBytes / 1024 / 1024
                    val totalMB = progress.totalBytes / 1024 / 1024
                    Text(
                        "${downloadedMB}MB / ${totalMB}MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isActive && progress.speedBytesPerSec > 0) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val speedMB = progress.speedBytesPerSec / 1024.0 / 1024.0
                    Text(
                        "${"%.1f".format(speedMB)} MB/s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (progress.totalBytes > progress.downloadedBytes) {
                        val remainingSec = (progress.totalBytes - progress.downloadedBytes) / progress.speedBytesPerSec.coerceAtLeast(1)
                        Text(
                            "剩余 ~${remainingSec}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- Scrollable Log Column ----
        if (hasLogs) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                LazyColumn(
                    state = logListState,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                ) {
                    items(progress.logs) { line ->
                        val logColor = when {
                            line.contains("失败") || line.contains("Error") || line.contains("超时") || line.contains("停滞") ->
                                MaterialTheme.colorScheme.error
                            line.contains("完成") || line.contains("成功") ->
                                MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            ),
                            color = logColor,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        } else {
            // Fill space when no logs yet
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        // ---- Bottom Controls ----

        // Mirror selector (only when idle)
        if (!isActive && !isExtracting && !isDone) {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedMirror?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("镜像源") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    mirrors.forEach { mirror ->
                        val tried = mirror.id in triedMirrorIds
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${if (tried) "⚠ " else ""}${mirror.name}",
                                    color = if (tried) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                onMirrorSelect(mirror.id)
                                dropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }

        // Action buttons
        if (isError) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = progress.error ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            if (triedMirrorIds.isNotEmpty()) {
                Text(
                    "已尝试: ${triedMirrorIds.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("重试")
            }
        } else if (!isActive && !isExtracting && !isDone) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStartDownload,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("开始下载", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
