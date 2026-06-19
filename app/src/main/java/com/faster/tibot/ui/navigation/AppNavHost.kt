package com.faster.tibot.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.faster.tibot.data.proot.ProotManager
import com.faster.tibot.service.TiBotForegroundService
import com.faster.tibot.ui.autoreply.AutoReplyScreen
import com.faster.tibot.ui.chats.ChatDetailScreen
import com.faster.tibot.ui.chats.ChatListScreen
import com.faster.tibot.ui.settings.SettingsScreen
import com.faster.tibot.ui.terminal.TerminalScreen
import com.faster.tibot.ui.wizard.WizardScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    isConfigured: Boolean = false,
    botOnline: Boolean = false,
) {
    val context = LocalContext.current
    val rootfsDeployed = remember(isConfigured) { ProotManager(context).isRootfsDeployed() }

    val startDest = when {
        !isConfigured -> Routes.WIZARD
        isConfigured && !rootfsDeployed -> Routes.WIZARD  // token saved but rootfs not installed
        !botOnline -> Routes.WIZARD
        else -> Routes.CHATS
    }

    NavHost(
        navController = navController,
        startDestination = startDest,
        modifier = modifier,
    ) {
        composable(Routes.WIZARD) {
            val context = LocalContext.current
            WizardScreen(onComplete = {
                // 启动前台服务
                context.startService(Intent(context, TiBotForegroundService::class.java))
                navController.navigate(Routes.CHATS) {
                    popUpTo(Routes.WIZARD) { inclusive = true }
                }
            })
        }
        composable(Routes.CHATS) { ChatListScreen(onChatClick = { chatId -> navController.navigate(Routes.chatDetail(chatId)) }) }
        composable(Routes.CHAT_DETAIL, arguments = listOf(navArgument("chatId") { type = NavType.LongType })) { ChatDetailScreen(chatId = it.arguments?.getLong("chatId") ?: 0L, onBack = { navController.popBackStack() }) }
        composable(Routes.AUTO_REPLY) { AutoReplyScreen() }
        composable(Routes.TERMINAL) { TerminalScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
