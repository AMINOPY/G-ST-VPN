package com.aras.client.ui.compose

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Value stored in MMKV for the radar theme. "0" system, "1" light, "2" dark. */
const val RADAR_THEME_MODE = "3"

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

private data class RadarBlip(val angleDeg: Float, val distance: Float, val color: Color)

private fun makeBlips(): List<RadarBlip> {
    val rnd = Random(7)
    return List(14) {
        RadarBlip(
            angleDeg = rnd.nextFloat() * 360f,
            distance = 0.2f + rnd.nextFloat() * 0.75f,
            color = if (rnd.nextInt(4) == 0) RadarAmber else RadarGreen
        )
    }
}

/**
 * Full-screen HUD layer drawn above the app content when the radar theme is on.
 * It never consumes touches, so the UI underneath stays fully usable.
 */
@Composable
fun RadarOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val blips = remember { makeBlips() }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.5f)
        val radius = min(size.width, size.height) * 0.42f

        drawRadarGrid(center, radius)
        drawRadarBlips(center, radius, sweep, blips)
        drawRadarSweep(center, radius, sweep)
        drawHudFrame()
        drawHudLabels(sweep, blips.size, labelPaint)
    }
}

private fun DrawScope.drawRadarGrid(center: Offset, radius: Float) {
    val line = 1.dp.toPx()
    for (i in 1..5) {
        drawCircle(
            color = RadarGreen.copy(alpha = 0.12f),
            radius = radius * i / 5f,
            center = center,
            style = Stroke(width = line)
        )
    }
    drawLine(
        RadarGreen.copy(alpha = 0.14f),
        Offset(center.x - radius, center.y),
        Offset(center.x + radius, center.y),
        strokeWidth = line
    )
    drawLine(
        RadarGreen.copy(alpha = 0.14f),
        Offset(center.x, center.y - radius),
        Offset(center.x, center.y + radius),
        strokeWidth = line
    )
    // Azimuth ticks: every 5 degrees, longer every 30.
    for (deg in 0 until 360 step 5) {
        val rad = Math.toRadians(deg.toDouble())
        val ux = cos(rad).toFloat()
        val uy = sin(rad).toFloat()
        val major = deg % 30 == 0
        val len = if (major) 12.dp.toPx() else 5.dp.toPx()
        drawLine(
            color = RadarGreen.copy(alpha = if (major) 0.6f else 0.3f),
            start = Offset(center.x + ux * (radius - len), center.y + uy * (radius - len)),
            end = Offset(center.x + ux * radius, center.y + uy * radius),
            strokeWidth = line
        )
    }
}

private fun DrawScope.drawRadarBlips(
    center: Offset,
    radius: Float,
    sweep: Float,
    blips: List<RadarBlip>
) {
    blips.forEach { blip ->
        // Degrees since the sweep last passed this contact: small = freshly lit.
        val age = ((sweep - blip.angleDeg) % 360f + 360f) % 360f
        val glow = (1f - age / 140f).coerceIn(0f, 1f)
        if (glow <= 0f) return@forEach
        val rad = Math.toRadians(blip.angleDeg.toDouble())
        val p = Offset(
            center.x + cos(rad).toFloat() * radius * blip.distance,
            center.y + sin(rad).toFloat() * radius * blip.distance
        )
        drawCircle(blip.color.copy(alpha = 0.25f * glow), radius = 10.dp.toPx(), center = p)
        drawCircle(blip.color.copy(alpha = glow), radius = 2.5.dp.toPx(), center = p)
    }
}

private fun DrawScope.drawRadarSweep(center: Offset, radius: Float, sweep: Float) {
    rotate(degrees = sweep, pivot = center) {
        // Fading trail behind the sweep line.
        for (i in 0 until 24) {
            drawArc(
                color = RadarGreen.copy(alpha = 0.22f * (1f - i / 24f)),
                startAngle = -(i + 1) * 1.5f,
                sweepAngle = 1.5f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
        }
        drawLine(
            RadarGreen.copy(alpha = 0.85f),
            center,
            Offset(center.x + radius, center.y),
            strokeWidth = 2.dp.toPx()
        )
    }
}

private fun DrawScope.drawHudFrame() {
    val inset = 10.dp.toPx()
    val arm = 28.dp.toPx()
    val stroke = 1.5.dp.toPx()
    val color = RadarGreen.copy(alpha = 0.35f)

    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(color, Offset(x, y), Offset(x + dx * arm, y), strokeWidth = stroke)
        drawLine(color, Offset(x, y), Offset(x, y + dy * arm), strokeWidth = stroke)
    }
    corner(inset, inset, 1f, 1f)
    corner(size.width - inset, inset, -1f, 1f)
    corner(inset, size.height - inset, 1f, -1f)
    corner(size.width - inset, size.height - inset, -1f, -1f)

    // Scanlines.
    val step = 4.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(RadarGreen.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawHudLabels(sweep: Float, contacts: Int, paint: Paint) {
    paint.textSize = 11.sp.toPx()
    paint.color = android.graphics.Color.argb(200, 57, 255, 136)
    val margin = 22.dp.toPx()
    val azimuth = sweep.toInt().toString().padStart(3, '0')
    drawContext.canvas.nativeCanvas.apply {
        drawText("RADAR // SECTOR 07", margin, margin, paint)
        drawText("AZ $azimuth°", size.width - margin - paint.measureText("AZ $azimuth°"), margin, paint)
        drawText("RNG 25 NM", margin, size.height - margin, paint)
        val contactsText = "CONTACTS $contacts"
        drawText(
            contactsText,
            size.width - margin - paint.measureText(contactsText),
            size.height - margin,
            paint
        )
    }
}
