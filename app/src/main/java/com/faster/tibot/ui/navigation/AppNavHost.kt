package com.faster.tibot.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
    val startDest = when {
        !isConfigured -> Routes.WIZARD
        !botOnline -> Routes.WIZARD  // 已配置但 Bot 不在线 → 走向导的启动流程
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
