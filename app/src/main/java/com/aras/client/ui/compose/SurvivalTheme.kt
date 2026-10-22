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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

const val SURVIVAL_THEME_MODE = "5"

// "AFTER THE FALL": wet concrete, oxidized metal, emergency phosphor and faded field paper.
internal val SurvivalBlack = Color(0xFF090E0B)
internal val SurvivalOlive = Color(0xFF354531)
internal val SurvivalSignal = Color(0xFF9BCB72)
internal val SurvivalAmber = Color(0xFFC79C58)
internal val SurvivalDanger = Color(0xFFE05249)
internal val SurvivalPaper = Color(0xFFD8D0B8)
internal val SurvivalAsh = Color(0xFF9DA394)
private val Rust = Color(0xFF70452F)
private val Moss = Color(0xFF23382A)
private val Metal = Color(0xFF1B241D)

val SurvivalColorScheme: ColorScheme = darkColorScheme(
    primary = SurvivalSignal, onPrimary = Color(0xFF142014),
    primaryContainer = Color(0xFF344A31), onPrimaryContainer = Color(0xFFE0E9D2),
    secondary = SurvivalAmber, onSecondary = Color(0xFF251A0B),
    secondaryContainer = Color(0xFF3C2E1D), onSecondaryContainer = Color(0xFFEED9B7),
    tertiary = Color(0xFFB8C7A2), onTertiary = Color(0xFF182016),
    tertiaryContainer = Color(0xFF303B2B), onTertiaryContainer = SurvivalPaper,
    error = SurvivalDanger, errorContainer = Color(0xFF4A1D18),
    onError = Color(0xFF2B0907), onErrorContainer = Color(0xFFFFDAD4),
    background = SurvivalBlack, onBackground = SurvivalPaper,
    surface = Color(0xFF141B15), onSurface = SurvivalPaper,
    surfaceVariant = Color(0xFF252E25), onSurfaceVariant = Color(0xFFBEC4B4),
    outline = Color(0xFF68745F), outlineVariant = Color(0xFF303B30),
    inverseSurface = SurvivalPaper, inverseOnSurface = SurvivalBlack,
    inversePrimary = SurvivalOlive, scrim = Color.Black, surfaceTint = SurvivalSignal,
    surfaceContainerLowest = Color(0xFF070B08),
    surfaceContainerLow = Color(0xFF101711),
    surfaceContainer = Color(0xFF151D16),
    surfaceContainerHigh = Color(0xFF202920),
    surfaceContainerHighest = Color(0xFF2B352A)
)

private data class AshParticle(val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float, val alpha: Float)
private val ashParticles = Random(91).let { random ->
    List(100) {
        AshParticle(random.nextFloat(), random.nextFloat(), 0.07f + random.nextFloat() * 0.24f,
            0.6f + random.nextFloat() * 2.1f, random.nextFloat() * 6.28f, 0.08f + random.nextFloat() * 0.2f)
    }
}
private val filmGrain = Random(18).let { random -> List(340) { Offset(random.nextFloat(), random.nextFloat()) } }

/**
 * Atmospheric layer for the selected theme. It is deliberately non-interactive:
 * all touch handling remains with the actual VPN UI beneath it.
 */
@Composable
fun SurvivalOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "after-the-fall")
    val progress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(56000, easing = LinearEasing), RepeatMode.Restart),
        label = "ash-drift"
    )
    val grainShift by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "film-grain"
    )
    val glow by transition.animateFloat(
        0.25f, 0.65f,
        infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Reverse),
        label = "emergency-glow"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // A very soft green haze, darkened edges, and a rust-toned low horizon.
        drawRect(Brush.verticalGradient(
            0f to Color(0x182D5932), 0.42f to Color.Transparent,
            0.78f to Rust.copy(alpha = 0.045f), 1f to Color(0x33000000)
        ))
        drawRect(Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0xC9080D09)),
            center = Offset(size.width * 0.5f, size.height * 0.43f),
            radius = maxOf(size.width, size.height) * 0.77f
        ))

        // Fine film grain; deliberately subtle to preserve text legibility.
        val dot = 1.dp.toPx()
        filmGrain.forEach { p ->
            drawCircle(Color.White.copy(alpha = 0.025f), dot,
                Offset(((p.x + grainShift * 0.018f) % 1f) * size.width, p.y * size.height))
        }

        // Slow drifting ash with slight sideways movement.
        ashParticles.forEach { p ->
            val y = ((p.y + progress * p.speed) % 1f) * size.height
            val sway = sin(progress * 2f * PI.toFloat() + p.phase) * (5f + p.size * 2f)
            drawCircle(SurvivalAsh.copy(alpha = p.alpha), p.size.dp.toPx(),
                Offset(p.x * size.width + sway, y))
        }

        // Restrained emergency glow near the upper edge.
        drawRect(Brush.verticalGradient(
            0f to SurvivalSignal.copy(alpha = glow * 0.055f),
            0.2f to Color.Transparent,
            0.7f to Color.Transparent
        ))

        // Corner marks like an old field monitor, not a full-screen blocking frame.
        val inset = 13.dp.toPx()
        val arm = 17.dp.toPx()
        val stroke = 1.dp.toPx()
        val hud = SurvivalSignal.copy(alpha = 0.15f)
        val w = size.width
        val h = size.height
        drawLine(hud, Offset(inset, inset), Offset(inset + arm, inset), stroke)
        drawLine(hud, Offset(inset, inset), Offset(inset, inset + arm), stroke)
        drawLine(hud, Offset(w - inset, inset), Offset(w - inset - arm, inset), stroke)
        drawLine(hud, Offset(w - inset, inset), Offset(w - inset, inset + arm), stroke)
        drawLine(hud, Offset(inset, h - inset), Offset(inset + arm, h - inset), stroke)
        drawLine(hud, Offset(inset, h - inset), Offset(inset, h - inset - arm), stroke)
        drawLine(hud, Offset(w - inset, h - inset), Offset(w - inset - arm, h - inset), stroke)
        drawLine(hud, Offset(w - inset, h - inset), Offset(w - inset, h - inset - arm), stroke)

        // Faint topographic contour fragments add depth without covering controls.
        val contour = Moss.copy(alpha = 0.20f)
        for (index in 0..2) {
            val path = Path()
            val baseY = size.height * (0.18f + index * 0.035f)
            path.moveTo(size.width * 0.72f, baseY)
            path.cubicTo(size.width * 0.80f, baseY - 16.dp.toPx(), size.width * 0.91f, baseY + 20.dp.toPx(), size.width, baseY + 2.dp.toPx())
            drawPath(path, contour, style = Stroke(width = 0.7.dp.toPx()))
        }
    }
}
