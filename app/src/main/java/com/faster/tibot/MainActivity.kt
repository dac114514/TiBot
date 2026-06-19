package com.faster.tibot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.faster.tibot.data.BotConnectionStore
import com.faster.tibot.data.ConnectionStatus
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.service.TiBotForegroundService
import com.faster.tibot.ui.common.LoadingDialog
import com.faster.tibot.ui.common.OfflineDialog
import com.faster.tibot.ui.navigation.AppNavHost
import com.faster.tibot.ui.navigation.Routes
import com.faster.tibot.ui.theme.TiBotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TiBotTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context.applicationContext) }
    val isConfigured by settingsRepo.isConfigured.collectAsState(initial = false)

    val connectionState by BotConnectionStore.state.collectAsState()
    val botOnline = connectionState.status == ConnectionStatus.ONLINE

    val tabs = listOf(
        TabItem(Routes.CHATS, "消息", Icons.Outlined.Chat, Icons.Filled.Chat),
        TabItem(Routes.AUTO_REPLY, "自动回复", Icons.Outlined.Forum, Icons.Filled.Forum),
        TabItem(Routes.TERMINAL, "终端", Icons.Outlined.Terminal, Icons.Filled.Terminal),
        TabItem(Routes.SETTINGS, "设置", Icons.Outlined.Settings, Icons.Filled.Settings),
    )

    val showBottomBar = botOnline && (currentRoute in listOf(Routes.CHATS, Routes.AUTO_REPLY, Routes.TERMINAL, Routes.SETTINGS))

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = if (selected) tab.filled else tab.outlined, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        // Loading/Offline dialogs (shown globally, not tied to any specific screen)
        LoadingDialog()
        OfflineDialog(
            onExit = { (context as? ComponentActivity)?.finishAffinity() },
            onReconnect = {
                BotConnectionStore.setStatus(ConnectionStatus.CONNECTING)
                // Restart service triggers reconnect
                context.startService(Intent(context, TiBotForegroundService::class.java))
            },
        )

        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            isConfigured = isConfigured,
            botOnline = botOnline,
        )
    }
}

private data class TabItem(val route: String, val label: String, val outlined: ImageVector, val filled: ImageVector)
