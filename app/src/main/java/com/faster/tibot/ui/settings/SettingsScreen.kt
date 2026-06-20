package com.faster.tibot.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.faster.tibot.BuildConfig
import com.faster.tibot.data.local.ThemeMode
import com.faster.tibot.ui.settings.components.AdminManager
import com.faster.tibot.ui.settings.components.BotDetailsDialog
import com.faster.tibot.ui.settings.components.BotInfoCard
import com.faster.tibot.ui.settings.components.SettingsRow
import com.faster.tibot.ui.settings.components.SettingsSection
import com.faster.tibot.ui.settings.components.ToggleRow

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val themeMode by vm.themeMode.collectAsState()
    val accessMode by vm.accessMode.collectAsState()
    val adminIds by vm.adminIds.collectAsState()
    val botToken by vm.botToken.collectAsState()
    val backgroundRunning by vm.backgroundRunning.collectAsState()
    val notificationsEnabled by vm.notificationsEnabled.collectAsState()
    val botInfo by vm.botInfo.collectAsState()
    val uptimeSeconds by vm.uptimeSeconds.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccessModeDialog by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }
    var showBotDetails by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        ThemeModeDialog(
            current = themeMode,
            onSelect = { mode ->
                vm.setThemeMode(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showAccessModeDialog) {
        AccessModeDialog(
            current = accessMode,
            onSelect = { mode ->
                vm.setAccessMode(mode)
                showAccessModeDialog = false
            },
            onDismiss = { showAccessModeDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))

        BotInfoCard(
            botInfo = botInfo,
            uptimeSeconds = uptimeSeconds,
            onDetailsClick = { showBotDetails = true },
        )

        if (showBotDetails) {
            BotDetailsDialog(
                botInfo = botInfo,
                uptimeSeconds = uptimeSeconds,
                onDismiss = { showBotDetails = false },
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(icon = Icons.Filled.Settings, title = "通用") {
            ToggleRow(
                title = "后台运行",
                subtitle = "应用退出后保持 Bot 运行",
                checked = backgroundRunning,
                onCheckedChange = { vm.setBackgroundRunning(it) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ToggleRow(
                title = "深色主题",
                subtitle = when (themeMode) {
                    ThemeMode.SYSTEM -> "跟随系统"
                    ThemeMode.LIGHT -> "浅色"
                    ThemeMode.DARK -> "深色"
                },
                checked = themeMode == ThemeMode.DARK,
                onCheckedChange = { dark ->
                    vm.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                },
                onRowClick = { showThemeDialog = true },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ToggleRow(
                title = "通知",
                subtitle = "接收重要状态提醒",
                checked = notificationsEnabled,
                onCheckedChange = { vm.setNotificationsEnabled(it) },
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(icon = Icons.Filled.Security, title = "管理员") {
            SettingsRow(
                icon = Icons.Filled.Public,
                title = "访问模式",
                subtitle = if (accessMode == "admin") "仅管理员" else "所有人",
                onClick = { showAccessModeDialog = true },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            AdminManager(
                adminIds = adminIds,
                onAdd = { vm.addAdmin(it) },
                onRemove = { vm.removeAdmin(it) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SettingsRow(
                icon = Icons.Filled.Key,
                title = "Bot Token",
                subtitle = if (tokenVisible) {
                    val botId = botToken.substringBefore(":")
                    if (botId.isNotEmpty()) "$botId:••••••••" else "未配置"
                } else "••••••••••••••••••",
                onClick = { tokenVisible = !tokenVisible },
                trailing = {
                    Icon(
                        imageVector = if (tokenVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (tokenVisible) "隐藏" else "显示",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(icon = Icons.Filled.Info, title = "关于") {
            SettingsRow(
                icon = Icons.Filled.PhoneAndroid,
                title = "应用版本",
                subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onClick = { },
                showChevron = false,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "开发者",
                subtitle = "dac114514",
                onClick = { },
                showChevron = false,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        ThemeMode.SYSTEM to "跟随系统",
        ThemeMode.LIGHT to "浅色",
        ThemeMode.DARK to "深色",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择主题模式", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (mode == current) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (mode != options.last().first) {
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun AccessModeDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf("all" to "所有人", "admin" to "仅管理员")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("访问模式", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (mode == current) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (mode != options.last().first) {
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
