package com.faster.tibot

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
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import com.faster.tibot.data.autoreply.AutoReplyEngine
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.message.MessageStore
import com.faster.tibot.data.telegram.PollingManager
import com.faster.tibot.data.telegram.TelegramBotClient
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

    // Create dependencies and start/stop polling
    val messageStore = remember { MessageStore(context.applicationContext) }
    val pollingManager = remember { mutableStateOf<PollingManager?>(null) }

    LaunchedEffect(isConfigured) {
        if (isConfigured) {
            val token = settingsRepo.botToken.first()
            val botClient = TelegramBotClient(token)
            val engine = AutoReplyEngine(botClient, context.applicationContext)
            val pm = PollingManager(botClient, messageStore, engine, settingsRepo)
            pollingManager.value = pm
            pm.start(this)
        } else {
            pollingManager.value?.stop()
        }
    }

    val tabs = listOf(
        TabItem(Routes.CHATS, "消息", Icons.Outlined.Chat, Icons.Filled.Chat),
        TabItem(Routes.AUTO_REPLY, "自动回复", Icons.Outlined.Forum, Icons.Filled.Forum),
        TabItem(Routes.SETTINGS, "设置", Icons.Outlined.Settings, Icons.Filled.Settings),
    )

    val showBottomBar = currentRoute in listOf(Routes.CHATS, Routes.AUTO_REPLY, Routes.SETTINGS)

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
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            isConfigured = isConfigured,
        )
    }
}

private data class TabItem(val route: String, val label: String, val outlined: ImageVector, val filled: ImageVector)
