package com.aras.client.ui.compose

import android.graphics.Paint
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Value stored in MMKV for the Persepolis theme. */
const val PERSEPOLIS_THEME_MODE = "4"

/**
 * Set to false if the device does not show Old Persian glyphs
 * (they would appear as empty boxes).
 */
const val PERSEPOLIS_SHOW_CUNEIFORM = true

private val PersepolisBasalt = Color(0xFF16110D)
private val PersepolisClay = Color(0xFF241A13)
private val PersepolisGold = Color(0xFFD4AF37)
private val PersepolisGoldLight = Color(0xFFF2D27A)
private val PersepolisLapisLight = Color(0xFF6FA3E0)
private val PersepolisBrickLight = Color(0xFFE0735A)
private val PersepolisIvory = Color(0xFFEADBC0)

/** Colour scheme used when the Persepolis theme is selected. */
val PersepolisColorScheme: ColorScheme = darkColorScheme(
    primary = PersepolisGold,
    onPrimary = PersepolisBasalt,
    primaryContainer = Color(0xFF4A3A12),
    onPrimaryContainer = PersepolisGold,
    secondary = PersepolisLapisLight,
    onSecondary = PersepolisBasalt,
    secondaryContainer = Color(0xFF13305A),
    onSecondaryContainer = Color(0xFFBBD4F2),
    tertiary = PersepolisGoldLight,
    onTertiary = PersepolisBasalt,
    tertiaryContainer = Color(0xFF3A2C0E),
    onTertiaryContainer = PersepolisGoldLight,
    error = PersepolisBrickLight,
    errorContainer = Color(0xFF4A1A10),
    onError = PersepolisBasalt,
    onErrorContainer = Color(0xFFFFC9BA),
    background = PersepolisBasalt,
    onBackground = PersepolisIvory,
    surface = PersepolisClay,
    onSurface = PersepolisIvory,
    surfaceVariant = Color(0xFF2E2218),
    onSurfaceVariant = Color(0xFFC8B596),
    outline = Color(0xFF8A7048),
    outlineVariant = Color(0xFF3F3122),
    inverseSurface = PersepolisIvory,
    inverseOnSurface = PersepolisBasalt,
    inversePrimary = Color(0xFF8A6A1E),
    scrim = Color(0xFF000000),
    surfaceTint = PersepolisGold,
    surfaceContainerLowest = Color(0xFF110C09),
    surfaceContainerLow = Color(0xFF1B140E),
    surfaceContainer = Color(0xFF211A12),
    surfaceContainerHigh = Color(0xFF2A2117),
    surfaceContainerHighest = Color(0xFF34291D),
)

/**
 * Chipped stone-tablet outline: the top-right and bottom-left corners are
 * cut at 45 degrees, the other two stay square. Use with clip() and border()
 * on cards that should look like carved tablets.
 */
class PersepolisStoneShape(private val chipDp: Float = 12f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val chip = with(density) { chipDp.dp.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - chip, 0f)
            lineTo(size.width, chip)
            lineTo(size.width, size.height)
            lineTo(chip, size.height)
            lineTo(0f, size.height - chip)
            close()
        }
        return Outline.Generic(path)
    }
}

private data class PersepolisDust(
    val x: Float,
    val y: Float,
    val speed: Int,
    val radiusDp: Float,
    val phase: Float
)

private fun makeDust(): List<PersepolisDust> {
    val rnd = Random(11)
    return List(36) {
        PersepolisDust(
            x = rnd.nextFloat(),
            y = rnd.nextFloat(),
            speed = 1 + rnd.nextInt(2),
            radiusDp = 1f + rnd.nextFloat() * 1.8f,
            phase = rnd.nextFloat() * 6.283f
        )
    }
}

// Old Persian letters (U+103A0 .. U+103A3), written as surrogate pairs.
private const val CUNEIFORM_WATERMARK = "\uD800\uDFA0\uD800\uDFA1\uD800\uDFA2\uD800\uDFA3"

/**
 * Full-screen Persepolis HUD layer drawn above the app content when this
 * theme is selected. It never consumes touches.
 */
@Composable
fun PersepolisOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "persepolis")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val dust = remember { makeDust() }
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawStoneBands()
        drawDust(dust, time)
        drawGoldShimmer(shimmer)
        drawCornerOrnaments()
        if (PERSEPOLIS_SHOW_CUNEIFORM) drawCuneiformWatermark(paint)
    }
}

private fun DrawScope.drawStoneBands() {
    val band = 34.dp.toPx()
    val step = 7.dp.toPx()
    val thin = 1.dp.toPx()

    // Fluted columns: thin vertical grooves in the top and bottom bands.
    var x = 0f
    while (x <= size.width) {
        drawLine(
            PersepolisGold.copy(alpha = 0.10f),
            Offset(x, 0f),
            Offset(x, band),
            strokeWidth = thin
        )
        drawLine(
            PersepolisGold.copy(alpha = 0.10f),
            Offset(x, size.height - band),
            Offset(x, size.height),
            strokeWidth = thin
        )
        x += step
    }

    // Gold rules.
    drawLine(
        PersepolisGold.copy(alpha = 0.45f),
        Offset(0f, band),
        Offset(size.width, band),
        strokeWidth = 1.5.dp.toPx()
    )
    drawLine(
        PersepolisGold.copy(alpha = 0.45f),
        Offset(0f, size.height - band),
        Offset(size.width, size.height - band),
        strokeWidth = 1.5.dp.toPx()
    )

    // Lotus-style teeth hanging off each rule.
    drawLotusTeeth(baseY = band, direction = -1f)
    drawLotusTeeth(baseY = size.height - band, direction = 1f)
}

private fun DrawScope.drawLotusTeeth(baseY: Float, direction: Float) {
    val step = 14.dp.toPx()
    val tip = 6.dp.toPx() * direction
    val color = PersepolisGold.copy(alpha = 0.35f)
    var x = 0f
    while (x < size.width) {
        val path = Path().apply {
            moveTo(x, baseY)
            lineTo(x + step / 2f, baseY + tip)
            lineTo(x + step, baseY)
            close()
        }
        drawPath(path, color)
        x += step
    }
}

private fun DrawScope.drawDust(dust: List<PersepolisDust>, t: Float) {
    val twoPi = 2f * PI.toFloat()
    dust.forEach { d ->
        val yFrac = ((d.y - t * d.speed) % 1f + 1f) % 1f
        val xFrac = d.x + 0.015f * sin(t * twoPi * d.speed + d.phase)
        drawCircle(
            color = PersepolisGold.copy(alpha = 0.35f),
            radius = d.radiusDp.dp.toPx(),
            center = Offset(xFrac * size.width, yFrac * size.height)
        )
    }
}

private fun DrawScope.drawGoldShimmer(progress: Float) {
    val center = (-0.3f + progress * 1.6f) * size.width
    val half = size.width * 0.18f
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                PersepolisGold.copy(alpha = 0.10f),
                Color.Transparent
            ),
            start = Offset(center - half, 0f),
            end = Offset(center + half, size.height)
        )
    )
}

private fun DrawScope.drawCornerOrnaments() {
    val inset = 46.dp.toPx()
    val arm = 22.dp.toPx()
    val stroke = 1.5.dp.toPx()
    val color = PersepolisGold.copy(alpha = 0.5f)

    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(color, Offset(x, y), Offset(x + dx * arm, y), strokeWidth = stroke)
        drawLine(color, Offset(x, y), Offset(x, y + dy * arm), strokeWidth = stroke)
        drawCircle(color, radius = 2.dp.toPx(), center = Offset(x, y))
    }
    corner(inset, inset, 1f, 1f)
    corner(size.width - inset, inset, -1f, 1f)
    corner(inset, size.height - inset, 1f, -1f)
    corner(size.width - inset, size.height - inset, -1f, -1f)
}

private fun DrawScope.drawCuneiformWatermark(paint: Paint) {
    paint.textSize = 40.dp.toPx()
    paint.color = android.graphics.Color.argb(46, 212, 175, 55)
    drawContext.canvas.nativeCanvas.drawText(
        CUNEIFORM_WATERMARK,
        size.width / 2f,
        size.height - 64.dp.toPx(),
        paint
    )
}
