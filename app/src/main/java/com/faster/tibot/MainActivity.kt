package com.faster.tibot

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.faster.tibot.data.local.SettingsRepository
import com.faster.tibot.data.local.ThemeMode
import com.faster.tibot.service.BotForegroundService
import com.faster.tibot.ui.navigation.AppNavHost
import com.faster.tibot.ui.navigation.Routes
import com.faster.tibot.ui.theme.TiBotTheme
import com.faster.tibot.util.PermissionUtils
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsRepo = SettingsRepository(applicationContext)
        setContent {
            val themeMode by settingsRepo.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            TiBotTheme(darkTheme = darkTheme) { AppRoot(settingsRepo) }
        }
    }
}

@Composable
private fun AppRoot(settingsRepo: SettingsRepository) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val context = LocalContext.current
    val isConfigured by settingsRepo.isConfigured.collectAsState(initial = false)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op; service still runs, just no system notification */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionUtils.hasPostNotificationsPermission(context)
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(isConfigured) {
        if (isConfigured) {
            val token = settingsRepo.botToken.first()
            if (token.isNotBlank()) {
                BotForegroundService.start(context, token)
            }
            if (!PermissionUtils.hasPostNotificationsPermission(context)) {
                android.widget.Toast.makeText(
                    context,
                    "未授予通知权限，Bot 后台状态将不显示通知",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        } else {
            context.stopService(Intent(context, BotForegroundService::class.java))
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
