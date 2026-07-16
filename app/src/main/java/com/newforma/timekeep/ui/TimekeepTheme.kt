package com.newforma.timekeep.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TimekeepColorScheme = lightColorScheme(
    primary = Color(0xFF155E63),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB65D32),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F4EE),
    onBackground = Color(0xFF112527),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF112527),
    surfaceVariant = Color(0xFFE2ECEA),
    onSurfaceVariant = Color(0xFF365357),
    outline = Color(0xFF6D8488),
)

@Composable
fun TimekeepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TimekeepColorScheme,
        typography = Typography(),
        content = content,
    )
}
