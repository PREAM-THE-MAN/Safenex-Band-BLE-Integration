package com.safenex.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SafenexColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = SafenexDarkBg,
    primaryContainer = SafenexSurfaceVariant,
    onPrimaryContainer = TextPrimary,
    secondary = SafetyGreen,
    onSecondary = SafenexDarkBg,
    error = EmergencyRed,
    onError = TextPrimary,
    background = SafenexDarkBg,
    onBackground = TextPrimary,
    surface = SafenexSurface,
    onSurface = TextPrimary,
    surfaceVariant = SafenexSurfaceVariant,
    onSurfaceVariant = TextSecondary
)

@Composable
fun SafenexTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SafenexColorScheme,
        typography = SafenexTypography,
        content = content
    )
}
