package com.aras.client.ui.compose

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

private val RadarGreen = Color(0xFF39FF88)
private val RadarAmber = Color(0xFFFFB000)
private val RadarCyan = Color(0xFF3DA9FF)
private val RadarRed = Color(0xFFFF3B3B)
private val RadarBlack = Color(0xFF0A0E1A)
private val RadarInk = Color(0xFFEAF1F7)

/** Full colour scheme used when the radar theme is selected. */
val RadarColorScheme: ColorScheme = darkColorScheme(
    primary = RadarCyan,
    onPrimary = RadarBlack,
    primaryContainer = Color(0xFF163653),
    onPrimaryContainer = Color(0xFFB8DEFF),
    secondary = RadarGreen,
    onSecondary = RadarBlack,
    secondaryContainer = Color(0xFF3D2A00),
    onSecondaryContainer = RadarGreen,
    tertiary = Color(0xFF5CF2FF),
    onTertiary = RadarBlack,
    tertiaryContainer = Color(0xFF062A33),
    onTertiaryContainer = RadarCyan,
    error = RadarRed,
    errorContainer = Color(0xFF3A0808),
    onError = RadarBlack,
    onErrorContainer = Color(0xFFFFB4B4),
    background = RadarBlack,
    onBackground = RadarInk,
    surface = Color(0xFF111827),
    onSurface = RadarInk,
    surfaceVariant = Color(0xFF172235),
    onSurfaceVariant = Color(0xFF9BAAC0),
    outline = Color(0xFF34445B),
    outlineVariant = Color(0xFF26354B),
    inverseSurface = RadarInk,
    inverseOnSurface = RadarBlack,
    inversePrimary = Color(0xFF0A6B3A),
    scrim = Color(0xFF000000),
    surfaceTint = RadarGreen,
    surfaceContainerLowest = Color(0xFF070B14),
    surfaceContainerLow = Color(0xFF0D1422),
    surfaceContainer = Color(0xFF111B2B),
    surfaceContainerHigh = Color(0xFF18243A),
    surfaceContainerHighest = Color(0xFF202E46),
)
