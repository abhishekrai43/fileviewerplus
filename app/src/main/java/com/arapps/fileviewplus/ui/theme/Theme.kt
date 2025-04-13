package com.arapps.fileviewplus.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    surfaceVariant = Color(0xFFF0F0F0),
    background = Color(0xFFFFFFFF),
    onBackground = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color.Black,
    surfaceVariant = Color(0xFF2C2C2C),
    background = Color(0xFF121212),
    onBackground = Color.White
)

@Composable
fun FileFlowPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
