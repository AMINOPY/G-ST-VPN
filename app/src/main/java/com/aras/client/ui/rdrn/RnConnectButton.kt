package com.aras.client.ui.rdrn

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.ui.rdr.RadarState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class RnPalette(
    val ring: List<Color>,
    val core: List<Color>,
    val edge: Color,
    val ripple: Color,
    val ringSpeed: Float,
    val orbitSpeed: Float,
    val rippleSeconds: Float,
)

private fun palette(state: RadarState): RnPalette = when (state) {
    RadarState.IDLE -> RnPalette(
        ring = listOf(RnColors.Red, RnColors.Gold, RnColors.Green, RnColors.Green, RnColors.Gold, RnColors.Red),
        core = listOf(Color(0xFF0F6A3A), Color(0xFF08381F), Color(0xFF031A0E)),
        edge = RnColors.Green, ripple = Color(0xFFFF5A3C), ringSpeed = 20f, orbitSpeed = 51f, rippleSeconds = 3.6f,
    )
    RadarState.CONNECTING -> RnPalette(
        ring = listOf(RnColors.Amber, RnColors.Gold, Color(0xFFFFD27A), RnColors.Amber, RnColors.Gold, RnColors.Amber),
        core = listOf(Color(0xFF6A4A0F), Color(0xFF382608), Color(0xFF1A1203)),
        edge = RnColors.Amber, ripple = RnColors.Amber, ringSpeed = 330f, orbitSpeed = 300f, rippleSeconds = 1.3f,
    )
    RadarState.CONNECTED -> RnPalette(
        ring = listOf(RnColors.Green, Color(0xFF8DFFB8), RnColors.Green, Color(0xFF0F9A50), RnColors.Green),
        core = listOf(Color(0xFF0F6A3A), Color(0xFF08381F), Color(0xFF031A0E)),
        edge = RnColors.Green, ripple = RnColors.Green, ringSpeed = 60f, orbitSpeed = 106f, rippleSeconds = 2.8f,
    )
    RadarState.DANGER -> RnPalette(
        ring = listOf(RnColors.Red, Color(0xFFFF7A6B), RnColors.Red, Color(0xFF8A1410), RnColors.Red),
        core = listOf(Color(0xFF6A1410), Color(0xFF380A08), Color(0xFF1A0403)),
        edge = RnColors.Red, ripple = RnColors.Red, ringSpeed = 40f, orbitSpeed = 70f, rippleSeconds = 1.8f,
    )
}

/**
 * The big radar connect button.
 *
 * Idle: breathing core, slow rotating tricolour ring, orbiting gold dot, red sonar pulses.
 * Connecting: ring and orbit spin fast, amber pulses. Connected: green ring, sweep + blip inside the core,
 * rising particles and a one-off shock-wave burst. Danger: red.
 */
@Composable
fun RnConnectButton(
    state: RadarState,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 218.dp,
) {
    val pal = palette(state)
    val stateNow by rememberUpdatedState(state)
    var time by remember { mutableFloatStateOf(0f) }
    var ringAngle by remember { mutableFloatStateOf(0f) }
    var orbitAngle by remember { mutableFloatStateOf(0f) }
    var burst by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                val p = palette(stateNow)
                time += dt
                ringAngle = (ringAngle + dt * p.ringSpeed) % 360f
                orbitAngle = (orbitAngle + dt * p.orbitSpeed) % 360f
            }
        }
    }
    LaunchedEffect(state) { if (state == RadarState.CONNECTED) burst++ }

    val particles = remember { List(12) { Triple(Random.nextFloat(), 2.4f + Random.nextFloat() * 2f, Random.nextFloat() * 4f) } }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")

    Box(modifier.size(diameter * 1.4f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter * 1.4f)) {
            val c = center
            val r = diameter.toPx() / 2f
            val breathe = 1f + 0.022f * sin(time * 2f * PI.toFloat() / 3.6f)

            // sonar ripples
            for (k in 0 until 3) {
                val ph = ((time / pal.rippleSeconds) + k / 3f) % 1f
                drawCircle(
                    pal.ripple.copy(alpha = (1f - ph) * 0.65f),
                    radius = r * (0.92f + 0.75f * ph),
                    center = c,
                    style = Stroke(2.dp.toPx()),
                )
            }
            // rising particles (connected only)
            if (state == RadarState.CONNECTED) {
                particles.forEach { (xf, period, delay) ->
                    val ph = ((time + delay) / period) % 1f
                    val a = if (ph < 0.2f) ph / 0.2f else (1f - ph) / 0.8f
                    drawCircle(
                        RnColors.GreenSoft.copy(alpha = a.coerceIn(0f, 1f)),
                        radius = 1.6.dp.toPx(),
                        center = Offset(c.x - r * 1.1f + xf * r * 2.2f, c.y + r * 0.95f - ph * r * 2.3f),
                    )
                }
            }
            // outer rotating tricolour ring (glow + ring)
            rotate(ringAngle, c) {
                drawCircle(
                    brush = Brush.sweepGradient(pal.ring, c),
                    radius = r * 0.985f, center = c,
                    style = Stroke(r * 0.19f),
                    alpha = 0.18f,
                )
                drawCircle(
                    brush = Brush.sweepGradient(pal.ring, c),
                    radius = r * 0.93f, center = c,
                    style = Stroke(r * 0.065f),
                )
            }
            // thin inner glow ring
            drawCircle(pal.edge.copy(alpha = 0.5f), r * 0.80f, c, style = Stroke(1.dp.toPx()))
            drawCircle(pal.edge.copy(alpha = 0.10f), r * 0.80f, c, style = Stroke(10.dp.toPx()))

            // core
            val coreR = r * 0.70f * breathe
            drawCircle(pal.edge.copy(alpha = 0.10f), coreR * 1.18f, c)
            drawCircle(pal.edge.copy(alpha = 0.16f), coreR * 1.08f, c)
            drawCircle(Brush.radialGradient(pal.core, Offset(c.x, c.y - coreR * 0.2f), coreR * 1.15f), coreR, c)
            drawCircle(pal.edge, coreR, c, style = Stroke(3.dp.toPx()))

            // sweep + blip inside the core
            if (state != RadarState.IDLE) {
                val path = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, coreR)) }
                clipPath(path) {
                    rotate(time * (360f / 2.6f), c) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, Color.Transparent, pal.edge.copy(alpha = 0.55f), Color(0xAAD8FFE6)),
                                c,
                            ),
                            radius = coreR, center = c,
                        )
                    }
                }
                val pulse = 0.55f + 0.45f * sin(time * 2f * PI.toFloat() / 1.6f)
                drawCircle(Color(0xFFD8FFE6).copy(alpha = pulse), 3.5.dp.toPx(), Offset(c.x + coreR * 0.38f, c.y - coreR * 0.42f))
                drawCircle(pal.edge.copy(alpha = pulse * 0.4f), 7.dp.toPx(), Offset(c.x + coreR * 0.38f, c.y - coreR * 0.42f))
            }

            // orbiting dot
            val oa = orbitAngle * PI.toFloat() / 180f
            val orR = r * 1.155f
            val op = Offset(c.x + cos(oa - PI.toFloat() / 2f) * orR, c.y + sin(oa - PI.toFloat() / 2f) * orR)
            val orbitColor = if (state == RadarState.CONNECTED) RnColors.GreenSoft else if (state == RadarState.DANGER) RnColors.Red else RnColors.Gold
            drawCircle(orbitColor.copy(alpha = 0.25f), 9.dp.toPx(), op)
            drawCircle(orbitColor, 4.dp.toPx(), op)
        }
        RnBurst(trigger = burst, color = RnColors.Green, modifier = Modifier.size(diameter * 1.4f), ringRadiusFraction = 0.36f)

        Box(
            Modifier
                .size(diameter * 0.72f)
                .scale(pressScale)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = Color(0xFFE9FFF1),
                    modifier = Modifier.size(50.dp).graphicsLayer {
                        val k = if (stateNow == RadarState.CONNECTING) 0.10f * (0.5f + 0.5f * sin(time * 10f)) else 0.04f * (0.5f + 0.5f * sin(time * 2f))
                        scaleX = 1f + k
                        scaleY = 1f + k
                    },
                )
                Text(
                    when (state) {
                        RadarState.IDLE -> "CONNECT"
                        RadarState.CONNECTING -> "CONNECTING"
                        RadarState.CONNECTED -> "CONNECTED"
                        RadarState.DANGER -> "DANGER"
                    },
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                )
                Text(subtitle, color = Color(0xFFB9E6C9), fontSize = 10.5.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
