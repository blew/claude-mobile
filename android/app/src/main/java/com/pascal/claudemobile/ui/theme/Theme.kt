package com.pascal.claudemobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF7C8CF8),
    onPrimary = Color.White,
    background = Color(0xFF111118),
    surface = Color(0xFF1A1A26),
    onSurface = Color(0xFFE8E8F0),
    surfaceVariant = Color(0xFF252535),
    onSurfaceVariant = Color(0xFFCCCCDD),
    outline = Color(0xFF55556A),
)

private val Light = lightColorScheme(
    primary = Color(0xFF4A5AF0),
    onPrimary = Color.White,
    background = Color(0xFFF8F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEEEF8),
    onSurfaceVariant = Color(0xFF333344),
)

@Composable
fun ClaudeMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        content = content,
    )
}
