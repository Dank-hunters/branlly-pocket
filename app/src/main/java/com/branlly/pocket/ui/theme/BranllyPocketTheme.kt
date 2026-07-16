package com.branlly.pocket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF315FDE),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE6FF),
        onPrimaryContainer = Color(0xFF10245E),
        secondary = Color(0xFF4E5F80),
        secondaryContainer = Color(0xFFE2E8F8),
        onSecondaryContainer = Color(0xFF172A49),
        tertiary = Color(0xFF006B66),
        background = Color(0xFFF5F7FC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE9ECF4),
        onSurface = Color(0xFF1A1B21),
        onSurfaceVariant = Color(0xFF454750),
        outline = Color(0xFF757780),
        error = Color(0xFFBA1A1A),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8EAEFF),
        onPrimary = Color(0xFF061842),
        primaryContainer = Color(0xFF162C61),
        onPrimaryContainer = Color(0xFFDCE6FF),
        secondary = Color(0xFFBBC7E6),
        secondaryContainer = Color(0xFF35415D),
        onSecondaryContainer = Color(0xFFDCE5FF),
        tertiary = Color(0xFF61DBD1),
        background = Color(0xFF090C13),
        surface = Color(0xFF111722),
        surfaceVariant = Color(0xFF1A2230),
        onSurface = Color(0xFFE9EDF7),
        onSurfaceVariant = Color(0xFFB7C0D1),
        outline = Color(0xFF536177),
        error = Color(0xFFFFB4AB),
    )

@Composable
fun BranllyPocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
