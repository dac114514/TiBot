package com.faster.tibot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
) {
    NavHost(
        navController = navController,
        startDestination = if (isConfigured) Routes.CHATS else Routes.WIZARD,
        modifier = modifier,
    ) {
        composable(Routes.WIZARD) { WizardScreen(onComplete = { navController.navigate(Routes.CHATS) { popUpTo(Routes.WIZARD) { inclusive = true } } }) }
        composable(Routes.CHATS) { ChatListScreen(onChatClick = { chatId -> navController.navigate(Routes.chatDetail(chatId)) }) }
        composable(Routes.CHAT_DETAIL, arguments = listOf(navArgument("chatId") { type = NavType.LongType })) { ChatDetailScreen(chatId = it.arguments?.getLong("chatId") ?: 0L, onBack = { navController.popBackStack() }) }
        composable(Routes.AUTO_REPLY) { AutoReplyScreen() }
        composable(Routes.TERMINAL) { TerminalScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
