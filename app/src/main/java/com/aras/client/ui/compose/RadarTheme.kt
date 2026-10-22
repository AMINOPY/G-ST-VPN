package com.aras.client.ui.compose

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

private val RadarGreen = Color(0xFF39FF88)
private val RadarAmber = Color(0xFFFFB000)
private val RadarCyan = Color(0xFF5CF2FF)
private val RadarRed = Color(0xFFFF3B3B)
private val RadarBlack = Color(0xFF020A06)
private val RadarInk = Color(0xFFB8FFD2)

/** Full colour scheme used when the radar theme is selected. */
val RadarColorScheme: ColorScheme = darkColorScheme(
    primary = RadarGreen,
    onPrimary = RadarBlack,
    primaryContainer = Color(0xFF0B3D22),
    onPrimaryContainer = RadarGreen,
    secondary = RadarAmber,
    onSecondary = RadarBlack,
    secondaryContainer = Color(0xFF3D2A00),
    onSecondaryContainer = RadarAmber,
    tertiary = RadarCyan,
    onTertiary = RadarBlack,
    tertiaryContainer = Color(0xFF062A33),
    onTertiaryContainer = RadarCyan,
    error = RadarRed,
    errorContainer = Color(0xFF3A0808),
    onError = RadarBlack,
    onErrorContainer = Color(0xFFFFB4B4),
    background = RadarBlack,
    onBackground = RadarInk,
    surface = Color(0xFF06150C),
    onSurface = RadarInk,
    surfaceVariant = Color(0xFF0A2216),
    onSurfaceVariant = Color(0xFF7FCFA0),
    outline = Color(0xFF1E6B43),
    outlineVariant = Color(0xFF13402A),
    inverseSurface = RadarInk,
    inverseOnSurface = RadarBlack,
    inversePrimary = Color(0xFF0A6B3A),
    scrim = Color(0xFF000000),
    surfaceTint = RadarGreen,
    surfaceContainerLowest = Color(0xFF010603),
    surfaceContainerLow = Color(0xFF041109),
    surfaceContainer = Color(0xFF07190E),
    surfaceContainerHigh = Color(0xFF0A2116),
    surfaceContainerHighest = Color(0xFF0E2B1C),
)
