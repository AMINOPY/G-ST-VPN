package com.aras.client.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

const val SURVIVAL_THEME_MODE = "5"
internal val SurvivalBlack = Color(0xFF0A100C)
internal val SurvivalOlive = Color(0xFF344A32)
internal val SurvivalSignal = Color(0xFF91D66B)
internal val SurvivalAmber = Color(0xFFD6B16A)
internal val SurvivalDanger = Color(0xFFE14F46)
internal val SurvivalPaper = Color(0xFFD8D0B8)
internal val SurvivalAsh = Color(0xFF9CA28E)

val SurvivalColorScheme: ColorScheme = darkColorScheme(
    primary = SurvivalSignal, onPrimary = SurvivalBlack,
    primaryContainer = SurvivalOlive, onPrimaryContainer = SurvivalPaper,
    secondary = SurvivalAmber, onSecondary = SurvivalBlack,
    secondaryContainer = Color(0xFF40351F), onSecondaryContainer = SurvivalPaper,
    tertiary = SurvivalPaper, onTertiary = SurvivalBlack,
    error = SurvivalDanger, errorContainer = Color(0xFF451A17),
    onError = Color.White, onErrorContainer = Color(0xFFFFDAD4),
    background = SurvivalBlack, onBackground = SurvivalPaper,
    surface = Color(0xFF151D16), onSurface = SurvivalPaper,
    surfaceVariant = Color(0xFF252E24), onSurfaceVariant = SurvivalAsh,
    outline = Color(0xFF65715C), outlineVariant = Color(0xFF303A2D),
    inverseSurface = SurvivalPaper, inverseOnSurface = SurvivalBlack,
    inversePrimary = SurvivalOlive, scrim = Color.Black, surfaceTint = SurvivalSignal,
    surfaceContainerLowest = Color(0xFF080D09), surfaceContainerLow = Color(0xFF101711),
    surfaceContainer = Color(0xFF151D16), surfaceContainerHigh = Color(0xFF20291F),
    surfaceContainerHighest = Color(0xFF2B3529)
)
private data class AshParticle(val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float)
private val ashParticles = Random(91).let { random -> List(85) { AshParticle(random.nextFloat(), random.nextFloat(), 0.08f + random.nextFloat() * 0.2f, 0.7f + random.nextFloat() * 1.8f, random.nextFloat() * 6.28f) } }
private val grain = Random(18).let { random -> List(260) { Offset(random.nextFloat(), random.nextFloat()) } }

/** Full-screen cinematic atmosphere. Canvas has no pointer input and never blocks controls. */
@Composable
fun SurvivalOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "after-the-fall")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(52000, easing = LinearEasing), RepeatMode.Restart), label = "ash-time")
    val grainShift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Restart), label = "film-grain")
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x99030905)), center = center, radius = maxOf(size.width, size.height) * 0.76f))
        drawRect(Brush.verticalGradient(listOf(Color(0x182C6937), Color.Transparent, Color(0x33000000))))
        grain.forEach { p -> drawCircle(Color.White.copy(alpha = 0.035f), 1.dp.toPx(), Offset(((p.x + grainShift * 0.025f) % 1f) * size.width, p.y * size.height)) }
        ashParticles.forEach { p ->
            val y = ((p.y + progress * p.speed) % 1f) * size.height
            val sway = sin(progress * 2f * PI.toFloat() + p.phase) * 8f
            drawCircle(SurvivalAsh.copy(alpha = 0.12f + p.size * 0.035f), p.size.dp.toPx(), Offset(p.x * size.width + sway, y))
        }
        val c = 18.dp.toPx(); val i = 14.dp.toPx(); val s = 1.dp.toPx(); val h = SurvivalSignal.copy(alpha = 0.16f)
        drawLine(h, Offset(i,i), Offset(i+c,i), s); drawLine(h, Offset(i,i), Offset(i,i+c), s)
        drawLine(h, Offset(size.width-i,i), Offset(size.width-i-c,i), s); drawLine(h, Offset(size.width-i,i), Offset(size.width-i,i+c), s)
        drawLine(h, Offset(i,size.height-i), Offset(i+c,size.height-i), s); drawLine(h, Offset(i,size.height-i), Offset(i,size.height-i-c), s)
        drawLine(h, Offset(size.width-i,size.height-i), Offset(size.width-i-c,size.height-i), s); drawLine(h, Offset(size.width-i,size.height-i), Offset(size.width-i,size.height-i-c), s)
    }
}
