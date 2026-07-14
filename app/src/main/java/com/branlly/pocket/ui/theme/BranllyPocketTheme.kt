package com.branlly.pocket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3559E0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFF59617A),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    primaryContainer = Color(0xFF173FBE),
    background = Color(0xFF111318),
    surface = Color(0xFF191B20),
)

@Composable
fun BranllyPocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
