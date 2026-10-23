package com.aras.client.ui.rdrx

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class RdrXPalette(
    val background: Color,
    val surface: Color,
    val glass: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val connected: Color,
    val error: Color,
    val offline: Color,
)

val RdrXDark = RdrXPalette(
    background = Color(0xFF0A0E1A),
    surface = Color(0xFF111A2B),
    glass = Color(0x14FFFFFF),
    border = Color(0x332F4865),
    text = Color(0xFFEAF3FF),
    textDim = Color(0xFF8D9BB2),
    accent = Color(0xFF3DA9FF),
    connected = Color(0xFF39FF88),
    error = Color(0xFFFF4D5E),
    offline = Color(0xFF5A6478),
)

val RdrXLight = RdrXPalette(
    background = Color(0xFFF2F6FB),
    surface = Color(0xFFFFFFFF),
    glass = Color(0xCCFFFFFF),
    border = Color(0x332D4968),
    text = Color(0xFF0A0E1A),
    textDim = Color(0xFF5A6478),
    accent = Color(0xFF1B7FD1),
    connected = Color(0xFF12A956),
    error = Color(0xFFD93A4A),
    offline = Color(0xFF7B8799),
)

val LocalRdrXPalette = staticCompositionLocalOf { RdrXDark }

@Composable
fun RdrXTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) RdrXDark else RdrXLight
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.text,
            onSurface = palette.text,
            error = palette.error,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.text,
            onSurface = palette.text,
            error = palette.error,
        )
    }
    CompositionLocalProvider(LocalRdrXPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
