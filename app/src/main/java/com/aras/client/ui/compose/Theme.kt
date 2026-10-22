package com.aras.client.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import com.aras.client.R
import androidx.compose.material3.Typography
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aras.client.AppConfig
import com.aras.client.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val LightColor = lightColorScheme(
    primary = Color(0xFF0284C7), // Aras Blue (darker sky)
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFFD3E9FB), // Pale Blue
    onPrimaryContainer = Color(0xFF04253D), // Deep Navy
    secondary = Color(0xFF0EA5E9), // Sky Blue
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFD8EFFC), // Pale Sky
    onSecondaryContainer = Color(0xFF082B3A), // Deep Sky
    tertiary = Color(0xFF5B8DEF), // Chip Blue (protocol badges)
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFDCE6FB), // Pale Blue
    onTertiaryContainer = Color(0xFF101C33), // Deep Navy
    error = Color(0xFFBA1A1A), // Red
    errorContainer = Color(0xFFFFDAD6), // Light Red
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF410002), // Dark Red
    background = Color(0xFFFAF6F0), // Warm Cream
    onBackground = Color(0xFF222226), // Charcoal
    surface = Color(0xFFFFFDFA), // Warm White
    onSurface = Color(0xFF222226), // Charcoal
    surfaceVariant = Color(0xFFEFE6DA), // Warm Sand
    onSurfaceVariant = Color(0xFF52443A), // Warm Gray Brown
    outline = Color(0xFF857468), // Warm Gray
    outlineVariant = Color(0xFFE2D7C9), // Light Sand
    inverseSurface = Color(0xFF37302B), // Warm Dark
    inverseOnSurface = Color(0xFFFAF6F0), // Cream
    inversePrimary = Color(0xFF7FC8F2), // Light Blue
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF0284C7), // Aras Blue
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFF7F1E8), // Light Cream
    surfaceContainer = Color(0xFFF1EAE0), // Cream Sand
    surfaceContainerHigh = Color(0xFFEBE3D8), // Sand
    surfaceContainerHighest = Color(0xFFE5DCD0), // Deep Sand
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF54FFC0), // Aurora Mint
    onPrimary = Color(0xFF06140E), // Obsidian
    primaryContainer = Color(0xFF174A37), // Aurora Forest
    onPrimaryContainer = Color(0xFFBFFFE0), // Aurora Ice
    secondary = Color(0xFFB8FF70), // Aurora Lime
    onSecondary = Color(0xFF17220B), // Lime Ink
    secondaryContainer = Color(0xFF344C23), // Lime Forest
    onSecondaryContainer = Color(0xFFE4FFC8), // Lime Ice
    tertiary = Color(0xFF85E9D0), // Aurora Teal
    onTertiary = Color(0xFF08221B), // Teal Ink
    tertiaryContainer = Color(0xFF16443B), // Teal Forest
    onTertiaryContainer = Color(0xFFC8FFF1), // Teal Ice
    error = Color(0xFFFF5369), // Signal Red
    errorContainer = Color(0xFF5A1523), // Danger Surface
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFFFFDCE1), // Danger Ice
    background = Color(0xFF070A0B), // Obsidian
    onBackground = Color(0xFFF1F8F4), // Aurora White
    surface = Color(0xFF0C1212), // Deep Surface
    onSurface = Color(0xFFF1F8F4), // Aurora White
    surfaceVariant = Color(0xFF172320), // Glass Surface
    onSurfaceVariant = Color(0xFF91A39A), // Muted Sage
    outline = Color(0xFF47645A), // Glass Outline
    outlineVariant = Color(0xFF293B36), // Subtle Outline
    inverseSurface = Color(0xFFE4F4EB), // Light Surface
    inverseOnSurface = Color(0xFF101715), // Dark Ink
    inversePrimary = Color(0xFF087C58), // Aras Blue
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF54FFC0), // Aurora Mint
    surfaceContainerLowest = Color(0xFF050707), // Near Black
    surfaceContainerLow = Color(0xFF0A100F), // Low Glass
    surfaceContainer = Color(0xFF101817), // Glass
    surfaceContainerHigh = Color(0xFF16211E), // Elevated Glass
    surfaceContainerHighest = Color(0xFF1D2C26), // High Glass
)

// Semantic Colors
val colorPing = Color(0xFF55FFC0) // Aurora Mint
val colorPingRed = Color(0xFFFF5369) // Signal Red
val colorConfigType = Color(0xFF85E9D0) // Aurora Teal
val colorFabActive = Color(0xFF54FFC0) // Aurora Mint
val colorFabInactiveLight = Color(0xFFB6C2BB) // Cool Gray
val colorFabInactiveDark = Color(0xFF35443D) // Dark Sage
val dividerColorLight = Color(0xFFE2D7C9) // Light Sand
val dividerColorDark = Color(0xFF293B36) // Aurora Divider
val colorSelectedGlow = Color(0xFF54FFC0) // Aurora selected-card glow

// Toast Colors 70%
val toastNormalBgLight = Color(0xB3353A3E) // Dark Gray
val toastNormalBgDark = Color(0xB34A4F54) // Darker Gray
val toastSuccessBg = Color(0xB3388E3C) // Green
val toastErrorBg = Color(0xB3D50000) // Red
val toastInfoBg = Color(0xB33F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

private val AppFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val defaultTypography = Typography()

val AppTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = AppFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = AppFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = AppFontFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = AppFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = AppFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = AppFontFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = AppFontFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = AppFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = AppFontFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = AppFontFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = AppFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = AppFontFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = AppFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = AppFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = AppFontFamily),
)

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
