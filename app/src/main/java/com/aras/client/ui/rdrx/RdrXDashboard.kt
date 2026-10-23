package com.aras.client.ui.rdrx

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * RDR X is a presentation layer only. All connection and test actions are
 * delegated to the existing MainAction handlers in MainScreen.
 */
@Composable
fun RdrXDashboard(
    status: RdrXStatus,
    isRunning: Boolean,
    hasPing: Boolean,
    serverTitle: String,
    pingMs: Long?,
    onConnect: () -> Unit,
    onTest: () -> Unit,
    onMenu: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalRdrXPalette.current
    val color = when (status) {
        RdrXStatus.OFFLINE -> p.offline
        RdrXStatus.CONNECTING, RdrXStatus.VERIFYING -> p.accent
        RdrXStatus.CONNECTED -> p.connected
        RdrXStatus.ERROR -> p.error
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
        shape = RoundedCornerShape(26.dp),
        color = p.background,
        border = BorderStroke(1.dp, p.border),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(p.surface, p.background, p.background)
                    )
                )
                .padding(horizontal = 15.dp, vertical = 13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "G-ST-VPN  /  RDR X",
                        color = p.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        "ORBITAL GLASS INTERFACE",
                        color = p.accent,
                        fontSize = 8.sp,
                        letterSpacing = 1.4.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f))
                        .border(1.dp, color.copy(alpha = 0.55f), CircleShape)
                        .clickable(onClick = onMenu)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("☰", color = color, fontSize = 17.sp)
                }
            }

            Spacer(Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RdrXCompactRadar(
                    status = status,
                    modifier = Modifier.size(172.dp).weight(1f, fill = false),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        when (status) {
                            RdrXStatus.OFFLINE -> "NO SIGNAL"
                            RdrXStatus.CONNECTING -> "CONNECTING"
                            RdrXStatus.VERIFYING -> "VERIFYING"
                            RdrXStatus.CONNECTED -> "TUNNEL LIVE"
                            RdrXStatus.ERROR -> "LINK ERROR"
                        },
                        color = color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        serverTitle.ifBlank { "No profile selected" },
                        color = p.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "PING  " + (pingMs?.takeIf { it > 0 }?.let { "$it ms" } ?: "--"),
                        color = if (hasPing) p.connected else p.textDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isRunning && !hasPing) "Tunnel running · verification pending"
                        else if (isRunning) "Profile response received"
                        else "VPN disconnected",
                        color = p.textDim,
                        fontSize = 9.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RdrXAction(
                    title = if (isRunning) "DISCONNECT" else "CONNECT",
                    modifier = Modifier.weight(1f),
                    color = if (isRunning) p.error else p.connected,
                    onClick = onConnect,
                )
                RdrXAction(
                    title = "TEST PING",
                    modifier = Modifier.weight(1f),
                    color = p.accent,
                    onClick = onTest,
                )
                RdrXAction(
                    title = "IMPORT +",
                    modifier = Modifier.weight(1f),
                    color = p.text,
                    onClick = onImport,
                )
            }
        }
    }
}

@Composable
private fun RdrXCompactRadar(
    status: RdrXStatus,
    modifier: Modifier = Modifier,
) {
    val p = LocalRdrXPalette.current
    val color = when (status) {
        RdrXStatus.OFFLINE -> p.offline
        RdrXStatus.CONNECTING, RdrXStatus.VERIFYING -> p.accent
        RdrXStatus.CONNECTED -> p.connected
        RdrXStatus.ERROR -> p.error
    }
    val scanning = status == RdrXStatus.CONNECTING ||
        status == RdrXStatus.VERIFYING || status == RdrXStatus.CONNECTED
    val transition = rememberInfiniteTransition(label = "rdrx-compact-radar")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (status == RdrXStatus.CONNECTING) 1100 else 3000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radar-sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "radar-pulse",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(172.dp)) {
            val c = center
            val r = size.minDimension * 0.46f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.18f * pulse), Color.Transparent),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
            for (i in 1..4) {
                drawCircle(
                    color.copy(alpha = if (status == RdrXStatus.OFFLINE) 0.17f else 0.35f),
                    radius = r * i / 4f,
                    center = c,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawLine(color.copy(alpha = 0.2f), androidx.compose.ui.geometry.Offset(c.x-r,c.y), androidx.compose.ui.geometry.Offset(c.x+r,c.y), 1.dp.toPx())
            drawLine(color.copy(alpha = 0.2f), androidx.compose.ui.geometry.Offset(c.x,c.y-r), androidx.compose.ui.geometry.Offset(c.x,c.y+r), 1.dp.toPx())
            if (scanning) {
                rotate(angle, pivot = c) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.78f to Color.Transparent,
                            1f to color.copy(alpha = 0.48f),
                            center = c,
                        ),
                        radius = r,
                        center = c,
                    )
                }
                val a = Math.toRadians(angle.toDouble())
                val end = androidx.compose.ui.geometry.Offset(
                    c.x + r * cos(a).toFloat(),
                    c.y + r * sin(a).toFloat(),
                )
                drawLine(
                    brush = Brush.linearGradient(
                        listOf(color.copy(alpha = 0.85f), Color.Transparent),
                        start = c,
                        end = end,
                    ),
                    start = c,
                    end = end,
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            drawCircle(color.copy(alpha = 0.13f), radius = r * 0.25f, center = c)
            drawCircle(color, radius = r * 0.25f, center = c, style = Stroke(width = 1.5.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (status) {
                    RdrXStatus.OFFLINE -> "NO"
                    RdrXStatus.CONNECTING -> "LINK"
                    RdrXStatus.VERIFYING -> "TEST"
                    RdrXStatus.CONNECTED -> "LIVE"
                    RdrXStatus.ERROR -> "FAIL"
                },
                color = color,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Text(
                if (status == RdrXStatus.OFFLINE) "SIGNAL" else "RADAR",
                color = color.copy(alpha = 0.85f),
                fontSize = 8.sp,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

@Composable
private fun RdrXAction(
    title: String,
    modifier: Modifier,
    color: Color,
    onClick: () -> Unit,
) {
    val p = LocalRdrXPalette.current
    Surface(
        modifier = modifier.height(38.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                title,
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
