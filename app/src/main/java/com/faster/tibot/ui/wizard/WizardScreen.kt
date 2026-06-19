package com.faster.tibot.ui.wizard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.faster.tibot.ui.common.StartupTerminalDialog

@Composable
fun WizardScreen(
    onComplete: () -> Unit = {},
    vm: WizardViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var showStartupDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Progress dots (hidden during deploy simulation)
        if (state.currentStep < 3) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) {
                (0..3).forEach { i ->
                    val color by animateColorAsState(
                        targetValue = when {
                            i < state.currentStep -> MaterialTheme.colorScheme.primary
                            i == state.currentStep -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        label = "dotColor",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(
                                width = if (i == state.currentStep) 24.dp else 8.dp,
                                height = 8.dp,
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                    )
                }
            }
        }

        // Step content
        Box(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
            when (state.currentStep) {
                0 -> WelcomeStep(onNext = { vm.nextStep() })
                1 -> TokenStep(
                    token = state.botToken,
                    tokenValid = state.tokenValid,
                    onTokenChange = { vm.setToken(it) },
                    onNext = { vm.nextStep() },
                    onBack = { vm.prevStep() },
                )
                2 -> AdminStep(
                    adminId = state.adminId,
                    adminIdValid = state.adminIdValid,
                    onAdminChange = { vm.setAdminId(it) },
                    onNext = { vm.nextStep() },
                    onBack = { vm.prevStep() },
                )
                3 -> DownloadStep(
                    phase = state.phase,
                    subtitle = state.phaseSubtitle,
                    progressPercent = state.progressPercent,
                    downloadedBytes = state.downloadedBytes,
                    totalBytes = state.totalBytes,
                    speedBytesPerSec = state.speedBytesPerSec,
                    logs = state.logs,
                    mirrors = vm.mirrors,
                    selectedMirrorId = state.selectedMirrorId,
                    onMirrorSelect = { vm.setMirror(it) },
                    onStartDownload = { vm.startDownload() },
                    onRetry = { vm.startDownload() },
                    onLaunch = {
                        vm.onLaunchGateway()
                        showStartupDialog = true
                    },
                )
            }
        }
    }

    if (showStartupDialog) {
        StartupTerminalDialog(
            prootManager = vm.getProotManager(),
            onSuccess = {
                showStartupDialog = false
                onComplete()
            },
            onBack = {
                showStartupDialog = false
            },
        )
    }
}

// ── Welcome Step ──────────────────────────────────────────────

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Bot icon in a rounded surface
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🤖", fontSize = 48.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "TiBot",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Telegram Bot 管理工具",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(36.dp))

        // Feature list
        val features = listOf(
            "接收和管理 Telegram 私聊/群聊消息" to "聊天",
            "关键词匹配 + 自动回复规则" to "规则",
            "内置 Linux 终端 (PRoot)" to "终端",
            "自定义 Python 回复策略" to "脚本",
        )
        features.forEach { (text, _) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("开始配置", style = MaterialTheme.typography.titleMedium)
        }

        // Bottom spacer so content isn't stuck to the edge on scroll
        Spacer(Modifier.height(24.dp))
    }
}

// ── Token Step ─────────────────────────────────────────────────

@Composable
private fun TokenStep(
    token: String,
    tokenValid: Boolean,
    onTokenChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "设置 Bot Token",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "请输入从 @BotFather 获取的 Bot Token",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Bot Token") },
                placeholder = { Text("1234567890:ABCdefGHIjklMNOpqrsTUVwxyz") },
                singleLine = true,
                isError = token.isNotEmpty() && !tokenValid,
                supportingText = if (token.isNotEmpty() && !tokenValid) {
                    { Text("Token 格式不正确，应包含 \":\"且长度大于20") }
                } else if (tokenValid) {
                    { Text("Token 格式有效", color = MaterialTheme.colorScheme.primary) }
                } else null,
                trailingIcon = {
                    when {
                        tokenValid -> Icon(Icons.Filled.CheckCircle, "有效", tint = MaterialTheme.colorScheme.primary)
                        token.isNotEmpty() -> Icon(Icons.Filled.Error, "无效", tint = MaterialTheme.colorScheme.error)
                    }
                },
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(24.dp))

            // @BotFather instructions card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Filled.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "@BotFather 获取指引",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "1. 在 Telegram 搜索 @BotFather\n" +
                                "2. 发送 /newbot 创建 Bot\n" +
                                "3. 复制获取到的 API Token",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onNext,
                enabled = tokenValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("下一步", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Admin Step ─────────────────────────────────────────────────

@Composable
private fun AdminStep(
    adminId: String,
    adminIdValid: Boolean,
    onAdminChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "设置管理员",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "请输入你的 Telegram User ID，用于接收 Bot 通知",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = adminId,
                onValueChange = onAdminChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("User ID") },
                placeholder = { Text("例如：123456789") },
                singleLine = true,
                isError = adminId.isNotEmpty() && !adminIdValid,
                supportingText = if (adminId.isNotEmpty() && !adminIdValid) {
                    { Text("请输入有效的数字 User ID") }
                } else if (adminIdValid) {
                    { Text("User ID 格式有效", color = MaterialTheme.colorScheme.primary) }
                } else null,
                trailingIcon = {
                    when {
                        adminIdValid -> Icon(Icons.Filled.CheckCircle, "有效", tint = MaterialTheme.colorScheme.primary)
                        adminId.isNotEmpty() -> Icon(Icons.Filled.Error, "无效", tint = MaterialTheme.colorScheme.error)
                    }
                },
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(24.dp))

            // @userinfobot instructions card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Filled.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "@userinfobot 获取指引",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "1. 在 Telegram 搜索 @userinfobot\n" +
                                "2. 发送任意消息给该 Bot\n" +
                                "3. 复制返回的 Id 字段内容",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onNext,
                enabled = adminIdValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("开始部署", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

