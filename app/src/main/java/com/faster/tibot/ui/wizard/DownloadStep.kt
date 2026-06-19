package com.faster.tibot.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val isVerifying = progress.state == DownloadState.VERIFYING
    val isExtracting = progress.state == DownloadState.EXTRACTING

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon
        when {
            isError -> Icon(
                Icons.Filled.Error,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp),
            )
            isDone -> Icon(
                Icons.Filled.CloudDownload,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            else -> Icon(
                Icons.Filled.CloudDownload,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = when {
                isError -> "下载失败"
                isVerifying -> "正在校验文件完整性..."
                isExtracting -> "正在部署环境..."
                isDone -> "部署完成"
                isActive -> "正在下载 Ubuntu 环境"
                else -> "准备下载 Ubuntu 环境"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(24.dp))

        // Progress bar
        if (isActive || isExtracting) {
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${progress.percent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress.totalBytes > 0) {
                    val downloadedMB = progress.downloadedBytes / 1024 / 1024
                    val totalMB = progress.totalBytes / 1024 / 1024
                    Text(
                        "${downloadedMB}MB / ${totalMB}MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Speed
        if (isActive && progress.speedBytesPerSec > 0) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val speedMB = progress.speedBytesPerSec / 1024.0 / 1024.0
                Text(
                    "速度: ${"%.1f".format(speedMB)} MB/s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress.speedBytesPerSec > 0 && progress.totalBytes > progress.downloadedBytes) {
                    val remainingBytes = progress.totalBytes - progress.downloadedBytes
                    val remainingSec = remainingBytes / progress.speedBytesPerSec
                    Text(
                        "剩余: ~${remainingSec}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Mirror selector
        if (!isActive && !isExtracting && !isVerifying) {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedMirror?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("镜像源") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    },
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
                                    text = "${if (tried) "⚠ " else ""}${mirror.name}",
                                    color = if (tried) {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
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

        Spacer(Modifier.height(24.dp))

        // Action area
        if (isError) {
            // Error state
            Text(
                text = progress.error ?: "下载失败",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            if (triedMirrorIds.isNotEmpty()) {
                Text(
                    "已尝试: ${triedMirrorIds.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("重试", style = MaterialTheme.typography.titleMedium)
            }
        } else if (!isActive && !isExtracting && !isVerifying && !isDone) {
            // Ready to download
            Button(
                onClick = onStartDownload,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("开始下载", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
