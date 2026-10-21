package com.aras.client.ui.radar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** UI states must be derived from VPN service facts, never from a button press. */
enum class RadarState(val isActive: Boolean) {
    DISCONNECTED(false),
    CONNECTING(false),
    CONNECTED_VERIFIED(true),
    TUNNEL_IP_UNVERIFIED(true),
    ERROR(false),
}

enum class TunnelPhase { STOPPED, STARTING, RUNNING, ERROR }

sealed interface IpCheck {
    data object NotRun : IpCheck
    data object Pending : IpCheck
    data class Verified(val egressIp: String) : IpCheck
    data object Failed : IpCheck
}

/**
 * Snapshot model for service integrations and tests.
 * A producer should only set Verified after a request routed through the VPN succeeds.
 * sessionId is used by the producer to reject results from old connection attempts.
 */
data class TunnelSnapshot(
    val sessionId: Long,
    val phase: TunnelPhase,
    val tunInterfaceUp: Boolean,
    val trafficBytesLastSample: Long,
    val ipCheck: IpCheck,
)

fun resolveRadarState(snapshot: TunnelSnapshot): RadarState = when (snapshot.phase) {
    TunnelPhase.ERROR -> RadarState.ERROR
    TunnelPhase.STARTING -> RadarState.CONNECTING
    TunnelPhase.STOPPED -> RadarState.DISCONNECTED
    TunnelPhase.RUNNING -> when {
        !snapshot.tunInterfaceUp -> RadarState.ERROR
        snapshot.ipCheck is IpCheck.Verified -> RadarState.CONNECTED_VERIFIED
        else -> RadarState.TUNNEL_IP_UNVERIFIED
    }
}

data class RadarNode(
    val bearingDeg: Float,
    val distance01: Float,
    val pingMs: Int?,
)

data class RadarPalette(
    val background: Color,
    val surface: Color,
    val radarBase: Color,
    val gridLine: Color,
    val inactiveRing: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val active: Color,
    val warning: Color,
    val connecting: Color,
    val accent: Color,
) {
    companion object {
        val Night = RadarPalette(
            background = Color(0xFF090D12),
            surface = Color(0xFF111821),
            radarBase = Color(0xFF101923),
            gridLine = Color(0xFF273442),
            inactiveRing = Color(0xFF3A4656),
            primaryText = Color(0xFFEAF1F7),
            secondaryText = Color(0xFF8997A7),
            active = Color(0xFF7FE3AC),
            warning = Color(0xFFFF525C),
            connecting = Color(0xFF00D9F5),
            accent = Color(0xFF00D9F5),
        )
        val Day = RadarPalette(
            background = Color(0xFFF1F4F7),
            surface = Color(0xFFFFFFFF),
            radarBase = Color(0xFFE5EAF0),
            gridLine = Color(0xFF8793A3),
            inactiveRing = Color(0xFF8793A3),
            primaryText = Color(0xFF17212E),
            secondaryText = Color(0xFF5D6978),
            active = Color(0xFF329B70),
            warning = Color(0xFFD94B4B),
            connecting = Color(0xFFC18A32),
            accent = Color(0xFF329B70),
        )
    }
}

private const val SWEEP_PERIOD_MS = 3200f

@Composable
fun RadarDisplay(
    state: RadarState,
    modifier: Modifier = Modifier,
    palette: RadarPalette = if (isSystemInDarkTheme()) RadarPalette.Night else RadarPalette.Day,
    nodes: List<RadarNode> = emptyList(),
    reducedMotion: Boolean = false,
    title: String = defaultTitle(state),
    subtitle: String = defaultSubtitle(state),
) {
    val displayPalette = when (state) {
        RadarState.CONNECTED_VERIFIED -> displayPalette.copy(
            background = Color(0xFF00170A),
            surface = Color(0xFF001D0D),
            radarBase = Color(0xFF001B0B),
            gridLine = Color(0xFF07552C),
            inactiveRing = Color(0xFF16834A),
            primaryText = Color(0xFFB8FFD0),
            secondaryText = Color(0xFF64B982),
            active = Color(0xFF20FF79),
            warning = Color(0xFFFF3948),
            accent = Color(0xFF20FF79),
        )
        RadarState.ERROR -> displayPalette.copy(
            background = Color(0xFF210307),
            surface = Color(0xFF2A050A),
            radarBase = Color(0xFF260309),
            gridLine = Color(0xFF74121C),
            inactiveRing = Color(0xFFB51F2C),
            primaryText = Color(0xFFFFD9DC),
            secondaryText = Color(0xFFFF737D),
            active = Color(0xFFFF2938),
            warning = Color(0xFFFF2938),
            accent = Color(0xFFFF2938),
        )
        RadarState.DISCONNECTED -> displayPalette.copy(
            background = Color(0xFF191C20),
            surface = Color(0xFF20242A),
            radarBase = Color(0xFF20242A),
            gridLine = Color(0xFF414852),
            inactiveRing = Color(0xFF737C87),
            primaryText = Color(0xFFE2E5E9),
            secondaryText = Color(0xFFFF525C),
            active = Color(0xFF737C87),
            warning = Color(0xFFFF3948),
            accent = Color(0xFF737C87),
        )
        else -> palette
    }
    val ringTarget = if (state.isActive) displayPalette.active.copy(alpha = 0.55f) else displayPalette.inactiveRing
    val ringColor by animateColorAsState(ringTarget, tween(600), label = "radarRing")
    val headColor by animateColorAsState(
        when (state) {
            RadarState.CONNECTED_VERIFIED, RadarState.TUNNEL_IP_UNVERIFIED -> displayPalette.active
            RadarState.CONNECTING -> displayPalette.connecting
            else -> displayPalette.inactiveRing
        }, tween(500), label = "radarHead"
    )
    val titleColor by animateColorAsState(
        when (state) {
            RadarState.DISCONNECTED, RadarState.ERROR -> displayPalette.warning
            RadarState.CONNECTING -> displayPalette.connecting
            RadarState.CONNECTED_VERIFIED -> displayPalette.active
            RadarState.TUNNEL_IP_UNVERIFIED -> displayPalette.connecting
        }, tween(500), label = "radarTitle"
    )

    val blinkOn = remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(state) {
        if (state == RadarState.ERROR) {
            while (true) {
                blinkOn.value = !blinkOn.value
                kotlinx.coroutines.delay(420)
            }
        } else {
            blinkOn.value = true
        }
    }

    val sweepAngle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isActive, reducedMotion) {
        if (!state.isActive || reducedMotion) {
            sweepAngle.floatValue = 0f
            return@LaunchedEffect
        }
        var startNanos = -1L
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0L) startNanos = now
                val ms = (now - startNanos) / 1_000_000f
                sweepAngle.floatValue = (ms % SWEEP_PERIOD_MS) / SWEEP_PERIOD_MS * 360f
            }
        }
    }

    Column(
        modifier = modifier.background(displayPalette.background).semantics { contentDescription = "$title. $subtitle" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r = size.minDimension / 2f * 0.92f
                val angle = sweepAngle.floatValue
                val lineW = 1.dp.toPx()
                drawCircle(color = displayPalette.radarBase, radius = r, center = c)
                for (i in 1..4) {
                    drawCircle(
                        color = ringColor.copy(alpha = if (i == 4) 0.9f else 0.5f),
                        radius = r * i / 4f,
                        center = c,
                        style = Stroke(width = lineW),
                    )
                }
                for (deg in 0 until 360 step 5) {
                    val rad = Math.toRadians(deg.toDouble())
                    val len = if (deg % 30 == 0) 12.dp.toPx() else 5.dp.toPx()
                    val dx = cos(rad).toFloat()
                    val dy = sin(rad).toFloat()
                    drawLine(
                        color = displayPalette.gridLine,
                        start = Offset(c.x + dx * r, c.y + dy * r),
                        end = Offset(c.x + dx * (r - len), c.y + dy * (r - len)),
                        strokeWidth = lineW,
                    )
                }
                drawLine(displayPalette.gridLine, Offset(c.x - r, c.y), Offset(c.x + r, c.y), lineW)
                drawLine(displayPalette.gridLine, Offset(c.x, c.y - r), Offset(c.x, c.y + r), lineW)
                drawCircle(color = ringColor, radius = 3.dp.toPx(), center = c)

                if (state.isActive) {
                    if (!reducedMotion) {
                        withTransform({ rotate(angle, c) }) {
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    0f to Color.Transparent,
                                    0.6f to Color.Transparent,
                                    1f to headColor.copy(alpha = 0.35f),
                                    center = c,
                                ),
                                radius = r,
                                center = c,
                            )
                        }
                    }
                    val rad = Math.toRadians(angle.toDouble())
                    drawLine(
                        color = headColor.copy(alpha = 0.85f),
                        start = c,
                        end = Offset(c.x + cos(rad).toFloat() * r, c.y + sin(rad).toFloat() * r),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    nodes.forEach { node ->
                        val nr = Math.toRadians(node.bearingDeg.toDouble())
                        val distance = node.distance01.coerceIn(0f, 1f)
                        val p = Offset(c.x + cos(nr).toFloat() * r * distance, c.y + sin(nr).toFloat() * r * distance)
                        val diff = ((angle - node.bearingDeg) % 360f + 360f) % 360f
                        val glow = if (reducedMotion || diff > 40f) 0f else 1f - diff / 40f
                        drawCircle(
                            color = nodeColor(node.pingMs, palette).copy(alpha = 0.35f + 0.5f * glow),
                            radius = 3.dp.toPx() + 3.dp.toPx() * glow,
                            center = p,
                        )
                    }
                } else {
                    drawCircle(color = displayPalette.inactiveRing, radius = 2.5.dp.toPx(), center = c)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = titleColor.copy(alpha = if (state == RadarState.ERROR && !blinkOn.value) 0.28f else 1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            color = displayPalette.secondaryText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

private fun nodeColor(pingMs: Int?, palette: RadarPalette): Color = when {
    pingMs == null -> palette.inactiveRing
    pingMs < 150 -> palette.active
    pingMs < 300 -> palette.connecting
    else -> palette.warning
}

fun defaultTitle(state: RadarState): String = when (state) {
    RadarState.DISCONNECTED, RadarState.ERROR -> "NO SIGNAL"
    RadarState.CONNECTING -> "ESTABLISHING LINK"
    RadarState.CONNECTED_VERIFIED -> "SIGNAL ACTIVE"
    RadarState.TUNNEL_IP_UNVERIFIED -> "TUNNEL ACTIVE · IP UNVERIFIED"
}

fun defaultSubtitle(state: RadarState): String = when (state) {
    RadarState.DISCONNECTED -> "VPN tunnel inactive"
    RadarState.CONNECTING -> "Negotiating secure tunnel"
    RadarState.CONNECTED_VERIFIED -> "SECURE TUNNEL ESTABLISHED"
    RadarState.TUNNEL_IP_UNVERIFIED -> "Tunnel active, exit IP not confirmed"
    RadarState.ERROR -> "Connection error. Tunnel is not active"
}
