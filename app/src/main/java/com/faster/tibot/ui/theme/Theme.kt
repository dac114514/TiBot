package com.faster.tibot.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TgDarkColorScheme = darkColorScheme(
    primary = TgDarkAccentBlue,
    onPrimary = TgDarkPrimaryText,
    secondary = TgDarkSecondaryText,
    onSecondary = TgDarkPrimaryText,
    tertiary = Color(0xFF6EC9CB),
    onTertiary = TgDarkPrimaryText,
    surface = TgDarkHeader,
    onSurface = TgDarkPrimaryText,
    surfaceVariant = TgDarkIncomingBubble,
    onSurfaceVariant = TgDarkSecondaryText,
    background = TgDarkChatBg,
    onBackground = TgDarkPrimaryText,
    outline = TgDarkIncomingBorder,
    outlineVariant = TgDarkDivider,
    error = TgDarkDanger,
    onError = Color.White,
)

private val TgLightColorScheme = lightColorScheme(
    primary = TgLightAccentBlue,
    onPrimary = Color.White,
    secondary = TgLightSecondaryText,
    onSecondary = Color.White,
    tertiary = Color(0xFF6EC9CB),
    onTertiary = Color.White,
    surface = TgLightHeader,
    onSurface = TgLightPrimaryText,
    surfaceVariant = TgLightIncomingBubble,
    onSurfaceVariant = TgLightSecondaryText,
    background = TgLightChatBg,
    onBackground = TgLightPrimaryText,
    outline = Color(0xFFe0e0e0),
    outlineVariant = TgLightDivider,
    error = TgLightDanger,
    onError = Color.White,
)

@Composable
fun TiBotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) TgDarkColorScheme else TgLightColorScheme
    val bubbleColors = if (darkTheme) TgDarkBubbleColors else TgLightBubbleColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalTgBubbleColors provides bubbleColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TgTypography,
            shapes = TgShapes,
            content = content,
        )
    }
}
