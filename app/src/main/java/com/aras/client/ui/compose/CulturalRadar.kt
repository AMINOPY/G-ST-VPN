package com.aras.client.ui.compose

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private val RadarGold = Color(0xFFC6A45D)
private val RadarTurquoise = Color(0xFF4FB3B0)
private val RadarGood = Color(0xFF3FBF7F)
private val RadarBad = Color(0xFFFF8A76)

/**
 * Lightweight cultural-theme radar. It is presentation-only: it never starts,
 * stops, or changes a VPN connection. Animation work exists only while connected.
 */
@Composable
fun CulturalRadar(
    hasConfigs: Boolean,
    isRunning: Boolean,
    onImportClipboard: () -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sweep = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val reducedMotion by ThemeManager.reducedMotionEnabled.collectAsState()
    val patternIntensity by ThemeManager.patternIntensity.collectAsState()
    val persianDigits by ThemeManager.persianDigitsEnabled.collectAsState()
    var connectedAt by rememberSaveable { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(isRunning, reducedMotion) {
        if (!isRunning) {
            connectedAt = 0L
            elapsedSeconds = 0L
            sweep.snapTo(0f)
            pulse.snapTo(1f)
            return@LaunchedEffect
        }
        if (connectedAt == 0L) connectedAt = SystemClock.elapsedRealtime()
        if (reducedMotion) {
            sweep.snapTo(0f)
            pulse.snapTo(1f)
        }
        coroutineScope {
            if (!reducedMotion) {
                launch {
                    while (isActive) {
                        sweep.animateTo(360f, tween(3000, easing = LinearEasing))
                        sweep.snapTo(0f)
                    }
                }
                launch {
                    while (isActive) {
                        pulse.animateTo(1.12f, tween(1100))
                        pulse.animateTo(0.94f, tween(1100))
                    }
                }
            }
            launch {
                while (isActive) {
                    elapsedSeconds = ((SystemClock.elapsedRealtime() - connectedAt) / 1000L).coerceAtLeast(0L)
                    delay(1000L)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                1.dp,
                RadarGold.copy(alpha = 0.08f + patternIntensity / 100f * 0.17f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (!hasConfigs) 174.dp else 144.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val radius = size.minDimension * 0.46f
                for (ring in 1..4) {
                    drawCircle(
                        color = RadarGold.copy(alpha = (if (hasConfigs) 0.02f else 0.03f) + patternIntensity / 100f * (if (hasConfigs) 0.12f else 0.14f)),
                        radius = radius * ring / 4f,
                        center = c,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                if (isRunning) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0f to RadarTurquoise.copy(alpha = 0f),
                                0.72f to RadarTurquoise.copy(alpha = 0.02f),
                                0.91f to RadarTurquoise.copy(alpha = 0.16f),
                                1f to RadarTurquoise.copy(alpha = 0.68f)
                            )
                        ),
                        startAngle = sweep.value,
                        sweepAngle = 68f,
                        useCenter = true,
                        topLeft = Offset(c.x - radius, c.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
                    )
                }

                // Fixed server markers remain visible when disconnected. The diamond
                // shape marks weak/offline nodes so status is not communicated by color alone.
                val nodes = listOf(
                    Offset(0.22f, 0.40f), Offset(0.77f, 0.34f),
                    Offset(0.30f, 0.72f), Offset(0.73f, 0.69f),
                    Offset(0.49f, 0.19f), Offset(0.56f, 0.82f)
                )
                if (hasConfigs) nodes.forEachIndexed { index, p ->
                    val point = Offset(size.width * p.x, size.height * p.y)
                    val good = index % 3 != 0
                    val activeColor = if (good) RadarGood else RadarBad
                    if (isRunning) {
                        drawCircle(
                            color = activeColor.copy(alpha = 0.18f),
                            radius = if (good) 7.dp.toPx() else 8.dp.toPx(),
                            center = point
                        )
                        if (good) {
                            drawCircle(activeColor, radius = 3.dp.toPx(), center = point)
                        } else {
                            val r = 4.dp.toPx()
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(point.x, point.y - r)
                                lineTo(point.x + r, point.y)
                                lineTo(point.x, point.y + r)
                                lineTo(point.x - r, point.y)
                                close()
                            }
                            drawPath(path, activeColor)
                        }
                    } else {
                        drawCircle(
                            color = RadarGold.copy(alpha = 0.28f),
                            radius = 3.dp.toPx(),
                            center = point,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
                if (isRunning) {
                    drawCircle(
                        color = RadarTurquoise.copy(alpha = 0.14f),
                        radius = 15.dp.toPx() * pulse.value,
                        center = c
                    )
                    drawCircle(RadarTurquoise, radius = 5.dp.toPx(), center = c)
                }
            }

            if (!hasConfigs) {
                Text(
                    text = "✧",
                    color = RadarGold.copy(alpha = 0.85f),
                    fontSize = 30.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (!isRunning) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.radar_no_signal),
                        color = RadarBad,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.radar_no_signal_fa),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (!hasConfigs) {
            Text(
                text = stringResource(R.string.radar_add_config),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.radar_add_config_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onImportClipboard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.radar_clipboard), maxLines = 1)
                }
                Button(
                    onClick = onScanQr,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.radar_scan_qr), maxLines = 1)
                }
            }
        } else if (isRunning) {
            Text(
                text = formatRadarDuration(elapsedSeconds, persianDigits),
                color = RadarTurquoise,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.scale(pulse.value)
            )
        }
    }
}

private fun formatRadarDuration(seconds: Long, persianDigits: Boolean): String {
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val secs = seconds % 60L
    val formatted = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, secs)
    if (!persianDigits) return formatted
    return formatted.map { ch ->
        if (ch in '0'..'9') ('۰'.code + (ch.code - '0'.code)).toChar() else ch
    }.joinToString("")
}
