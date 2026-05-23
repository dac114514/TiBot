package com.example.androidstarter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.androidstarter.ui.display.DisplayScreen
import com.example.androidstarter.ui.settings.SettingsScreen
import com.example.androidstarter.ui.widget.WidgetScreen

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
