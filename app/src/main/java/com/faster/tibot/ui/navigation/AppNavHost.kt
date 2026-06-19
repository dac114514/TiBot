package com.faster.tibot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.faster.tibot.ui.display.DisplayScreen
import com.faster.tibot.ui.settings.SettingsScreen
import com.faster.tibot.ui.widget.WidgetScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.WIDGET,
        modifier = modifier,
    ) {
        composable(Routes.WIDGET) { WidgetScreen() }
        composable(Routes.DISPLAY) { DisplayScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
