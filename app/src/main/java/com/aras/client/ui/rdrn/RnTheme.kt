package com.aras.client.ui.rdrn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.shape.RoundedCornerShape

/** RADAR Neon palette — the same tokens as the HTML simulator. */
object RnColors {
    val Bg = Color(0xFF040B07)
    val BgTop = Color(0xFF0D2A1B)
    val Green = Color(0xFF22E07A)
    val GreenSoft = Color(0xFF9BFFC4)
    val GreenDeep = Color(0xFF0B6B38)
    val Gold = Color(0xFFE8B923)
    val Red = Color(0xFFFF3B30)
    val Amber = Color(0xFFFF9F1A)
    val Text = Color(0xFFE9F3EC)
    val Muted = Color(0xFF9DB3A5)
    val CardA = Color(0x80184630)
    val CardB = Color(0xEB07140D)
}

/** Colour scheme for the rest of the app (settings, dialogs, the full config list) while "rdrn" is active. */
val RadarNeonColorScheme: ColorScheme = darkColorScheme(
    primary = RnColors.Green,
    onPrimary = Color(0xFF03150A),
    primaryContainer = Color(0xFF0B3D22),
    onPrimaryContainer = RnColors.GreenSoft,
    secondary = RnColors.Gold,
    onSecondary = Color(0xFF1A1400),
    secondaryContainer = Color(0xFF3D2F00),
    onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = RnColors.Amber,
    onTertiary = Color(0xFF1A0E00),
    error = RnColors.Red,
    onError = Color(0xFF1A0402),
    errorContainer = Color(0xFF3A0B08),
    onErrorContainer = Color(0xFFFFB4AB),
    background = RnColors.Bg,
    onBackground = RnColors.Text,
    surface = Color(0xFF06150C),
    onSurface = RnColors.Text,
    surfaceVariant = Color(0xFF0A2216),
    onSurfaceVariant = Color(0xFF8FC9A8),
    outline = Color(0xFF1E6B43),
    outlineVariant = Color(0xFF13402A),
    surfaceTint = RnColors.Green,
    surfaceContainerLowest = Color(0xFF010603),
    surfaceContainerLow = Color(0xFF041109),
    surfaceContainer = Color(0xFF07190E),
    surfaceContainerHigh = Color(0xFF0A2116),
    surfaceContainerHighest = Color(0xFF0E2B1C),
)

/** Glass-green card with a thin tinted border (the card used on every Neon screen). */
fun Modifier.rnCard(
    tint: Color = RnColors.Gold,
    shape: Shape = RoundedCornerShape(14.dp),
    borderAlpha: Float = 0.35f,
    top: Color = RnColors.CardA,
    bottom: Color = RnColors.CardB,
): Modifier = this
    .background(Brush.linearGradient(listOf(top, bottom)), shape)
    .border(1.dp, tint.copy(alpha = borderAlpha), shape)
    .clip(shape)

/**
 * Entrance animation: the element rises, un-blurs (scale) and fades in, staggered by [index].
 * Re-runs every time the element enters composition, i.e. on every tab change.
 */
fun Modifier.rnRise(index: Int = 0): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(14) * 45).toLong())
        p.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 22.dp.toPx()
        val s = 0.96f + 0.04f * p.value
        scaleX = s
        scaleY = s
    }
}

/** Full-screen backdrop: deep green gradient, twinkling gold dot-map, red glow bottom-right. */
@Composable
fun RnBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "rn-bg")
    val twinkle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "twinkle",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RnColors.BgTop, RnColors.Bg, Color(0xFF030806))))
            .drawWithCache {
                // Dots are rendered once into a bitmap; only its alpha animates (cheap per frame).
                val w = size.width.toInt().coerceAtLeast(1)
                val h = (size.height * 0.62f).toInt().coerceAtLeast(1)
                val bmp = ImageBitmap(w, h)
                val scope = CanvasDrawScope()
                scope.draw(this, layoutDirection, Canvas(bmp), androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat())) {
                    val step = 10.dp.toPx()
                    val cx = w / 2f
                    val cy = h * 0.46f
                    val rx = w * 0.56f
                    val ry = h * 0.40f
                    var y = cy - ry
                    while (y < cy + ry) {
                        var x = cx - rx
                        while (x < cx + rx) {
                            val d = ((x - cx) / rx).pow(2) + ((y - cy) / ry).pow(2)
                            if (d < 1f) {
                                val keep = (sin(x * 0.045f) * cos(y * 0.06f) + 1f) / 2f
                                if (keep > 0.32f) {
                                    drawCircle(
                                        RnColors.Gold.copy(alpha = ((1f - d) * 0.7f).coerceIn(0f, 1f)),
                                        1.1.dp.toPx(),
                                        Offset(x, y),
                                    )
                                }
                            }
                            x += step
                        }
                        y += step
                    }
                }
                onDrawBehind {
                    drawImage(bmp, Offset(0f, size.height * 0.04f), alpha = 0.45f + 0.4f * twinkle.value)
                    drawRect(
                        Brush.radialGradient(
                            listOf(RnColors.Red.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width, size.height * 0.55f),
                            radius = size.width * 0.8f,
                        ),
                    )
                }
            },
        content = content,
    )
}

/**
 * Shock-wave ring plus a spray of sparks. Plays once every time [trigger] changes to a value > 0.
 * Draw it over the element that should "explode" (the connect button, the speed gauge).
 */
@Composable
fun RnBurst(trigger: Int, color: Color, modifier: Modifier = Modifier, ringRadiusFraction: Float = 0.5f) {
    val p = remember { Animatable(1f) }
    val sparks = remember { List(26) { Triple(Random.nextFloat() * (2f * PI.toFloat()), 0.7f + Random.nextFloat() * 0.8f, it % 4 == 0) } }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            p.snapTo(0f)
            p.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier.drawBehind {
            val t = p.value
            if (t >= 1f) return@drawBehind
            val c = center
            val base = size.minDimension * ringRadiusFraction
            drawCircle(
                color.copy(alpha = (1f - t) * 0.95f),
                radius = base * (0.8f + 1.5f * t),
                center = c,
                style = Stroke(3.dp.toPx() * (1f - t * 0.6f)),
            )
            sparks.forEach { (a, d, gold) ->
                val dist = base * 1.1f * d * (0.15f + t)
                drawCircle(
                    (if (gold) RnColors.Gold else color).copy(alpha = (1f - t).coerceIn(0f, 1f)),
                    radius = 2.6.dp.toPx() * (1f - t * 0.7f),
                    center = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist),
                )
            }
        },
    )
}
