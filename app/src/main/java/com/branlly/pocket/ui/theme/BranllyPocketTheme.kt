package com.branlly.pocket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF3658D4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE1E7FF),
        onPrimaryContainer = Color(0xFF102A78),
        secondary = Color(0xFF4E5F80),
        secondaryContainer = Color(0xFFE2E8F8),
        onSecondaryContainer = Color(0xFF172A49),
        tertiary = Color(0xFF006B66),
        background = Color(0xFFF8F9FF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE7E9F1),
        onSurface = Color(0xFF1A1B21),
        onSurfaceVariant = Color(0xFF454750),
        outline = Color(0xFF757780),
        error = Color(0xFFBA1A1A),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFB9C5FF),
        onPrimary = Color(0xFF052060),
        primaryContainer = Color(0xFF24449A),
        onPrimaryContainer = Color(0xFFDEE4FF),
        secondary = Color(0xFFBBC7E6),
        secondaryContainer = Color(0xFF35415D),
        onSecondaryContainer = Color(0xFFDCE5FF),
        tertiary = Color(0xFF61DBD1),
        background = Color(0xFF101116),
        surface = Color(0xFF18191F),
        surfaceVariant = Color(0xFF25262E),
        onSurface = Color(0xFFE2E2E9),
        onSurfaceVariant = Color(0xFFC5C6D0),
        outline = Color(0xFF8F909A),
        error = Color(0xFFFFB4AB),
    )

@Composable
fun BranllyPocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
