package com.aras.client.ui.rdr

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** ذخیره‌سازی ساده (SharedPreferences) تا به MMKV و بقیه‌ی برنامه دست نخورد. */
class RdrPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("rdr_ui", Context.MODE_PRIVATE)

    fun getString(key: String, def: String): String = sp.getString(key, def) ?: def
    fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()
    fun getBool(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)
    fun putBool(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()
}

@Stable
class RdrThemeState(private val prefs: RdrPrefs) {
    var radarSweep by mutableStateOf(prefs.getBool(K_RADAR, true))
        private set
    var animatedBackground by mutableStateOf(prefs.getBool(K_BG, true))
        private set
    var haptics by mutableStateOf(prefs.getBool(K_HAPTIC, true))
        private set

    fun updateRadarSweep(v: Boolean) { radarSweep = v; prefs.putBool(K_RADAR, v) }
    fun updateAnimatedBackground(v: Boolean) { animatedBackground = v; prefs.putBool(K_BG, v) }
    fun updateHaptics(v: Boolean) { haptics = v; prefs.putBool(K_HAPTIC, v) }

    private companion object {
        const val K_RADAR = "fx_radar"
        const val K_BG = "fx_bg"
        const val K_HAPTIC = "fx_haptic"
    }
}

@Composable
fun rememberRdrThemeState(): RdrThemeState {
    val ctx = LocalContext.current.applicationContext
    return remember { RdrThemeState(RdrPrefs(ctx)) }
}

@Immutable
data class RdrFx(
    val radarSweep: Boolean = true,
    val animatedBackground: Boolean = true,
    val haptics: Boolean = true,
)

@Immutable
class RdrColors(
    val isDark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val blobA: Color,
    val blobB: Color,
    val blobC: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val strokeHi: Color,
    val strokeLo: Color,
    val shadow: Color,
    val text: Color,
    val textSecondary: Color,
    val separator: Color,
    val accent: Color,
    val green: Color,
    val red: Color,
    val amber: Color,
    val gray: Color,
)

val LightColors = RdrColors(
    isDark = false,
    bgTop = Color(0xFFEAF1FF),
    bgBottom = Color(0xFFF8F0FF),
    blobA = Color(0xFF5AC8FA),
    blobB = Color(0xFFFF9FCF),
    blobC = Color(0xFFA5F3C9),
    glassFill = Color(0xFFFFFFFF).copy(alpha = 0.46f),
    glassFillStrong = Color(0xFFFFFFFF).copy(alpha = 0.68f),
    strokeHi = Color(0xFFFFFFFF).copy(alpha = 0.95f),
    strokeLo = Color(0xFFFFFFFF).copy(alpha = 0.28f),
    shadow = Color(0xFF1E2A5A).copy(alpha = 0.22f),
    text = Color(0xFF111318),
    textSecondary = Color(0xFF60646E),
    separator = Color(0xFF000000).copy(alpha = 0.08f),
    accent = Color(0xFF0A84FF),
    green = Color(0xFF28C04E),
    red = Color(0xFFFF3B30),
    amber = Color(0xFFFF9F0A),
    gray = Color(0xFF8E8E93),
)

val DarkColors = RdrColors(
    isDark = true,
    bgTop = Color(0xFF05070D),
    bgBottom = Color(0xFF0B1020),
    blobA = Color(0xFF0A84FF),
    blobB = Color(0xFF8E4DFF),
    blobC = Color(0xFF00C2A8),
    glassFill = Color(0xFFFFFFFF).copy(alpha = 0.09f),
    glassFillStrong = Color(0xFFFFFFFF).copy(alpha = 0.15f),
    strokeHi = Color(0xFFFFFFFF).copy(alpha = 0.38f),
    strokeLo = Color(0xFFFFFFFF).copy(alpha = 0.06f),
    shadow = Color(0xFF000000).copy(alpha = 0.55f),
    text = Color(0xFFF5F6FA),
    textSecondary = Color(0xFFA0A6B5),
    separator = Color(0xFFFFFFFF).copy(alpha = 0.10f),
    accent = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    amber = Color(0xFFFF9F0A),
    gray = Color(0xFF8E8E93),
)

val LocalRdrColors = compositionLocalOf { LightColors }
val LocalRdrFx = compositionLocalOf { RdrFx() }

/** true وقتی تم RADAR Glass فعال است؛ کامپوننت‌های مشترک با آن ظاهرشان را عوض می‌کنند. */
val LocalRdrActive = compositionLocalOf { false }

@Composable
fun RdrTheme(
    state: RdrThemeState,
    /** روشن/تیره را خود برنامه (ThemeManager) تعیین می‌کند؛ null = سیستم */
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val fx = remember(state.radarSweep, state.animatedBackground, state.haptics) {
        RdrFx(state.radarSweep, state.animatedBackground, state.haptics)
    }
    // رنگ‌های برنامه (ThemeManager) حفظ می‌شوند؛ فقط تأکید و متن عوض می‌شود و پس‌زمینه شفاف است
    // تا گرادیان RADAR Glass زیر همه‌ی صفحه‌ها دیده شود.
    val base = MaterialTheme.colorScheme
    val scheme = base.copy(
        primary = colors.accent,
        onPrimary = Color.White,
        secondary = colors.accent,
        onSecondary = Color.White,
        background = Color.Transparent,
        onBackground = colors.text,
        onSurface = colors.text,
        onSurfaceVariant = colors.textSecondary,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalRdrColors provides colors,
            LocalRdrFx provides fx,
            LocalRdrActive provides true,
            LocalContentColor provides colors.text,
            content = content,
        )
    }
}

/** در تم RADAR Glass به‌جای رنگ سطح مادریال، پرکننده‌ی شیشه‌ای می‌دهد. */
@Composable
fun rdrOr(default: Color): Color =
    if (LocalRdrActive.current) LocalRdrColors.current.glassFillStrong else default
