package com.aras.client.ui.nova

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private val Void = Color(0xFF05090F)
private val Panel = Color(0xFF0B1520)
private val PanelRaised = Color(0xFF122432)
private val Cyan = Color(0xFF48E5F2)
private val Mint = Color(0xFF00E6A0)
private val Amber = Color(0xFFFFC857)
private val Danger = Color(0xFFFF496C)
private val Muted = Color(0xFF8198A8)
private val Ink = Color(0xFFEAF8FF)
private val Line = Color(0xFF23404E)

enum class NovaStatus { OFFLINE, CONNECTING, VERIFYING, CONNECTED, ERROR }

@Composable
fun NovaDashboard(
    status: NovaStatus,
    isRunning: Boolean,
    hasPing: Boolean,
    serverTitle: String,
    pingMs: Long?,
    onConnect: () -> Unit,
    onTest: () -> Unit,
    onMenu: () -> Unit,
    onImport: () -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (status) {
        NovaStatus.CONNECTED -> Mint
        NovaStatus.CONNECTING, NovaStatus.VERIFYING -> Amber
        NovaStatus.ERROR -> Danger
        NovaStatus.OFFLINE -> Muted
    }
    val statusText = when (status) {
        NovaStatus.OFFLINE -> "NO SIGNAL"
        NovaStatus.CONNECTING -> "ESTABLISHING LINK"
        NovaStatus.VERIFYING -> "VERIFYING TUNNEL"
        NovaStatus.CONNECTED -> "TUNNEL ONLINE"
        NovaStatus.ERROR -> "LINK FAILURE"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1723), Void, Color(0xFF030609))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("RADAR VPN  /  NOVA", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("ORBITAL CONTROL", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("SECURE NETWORK INTERFACE", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
            OutlinedButton(
                onClick = onMenu,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
            ) {
                Text("☰", fontSize = 20.sp)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.96f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LIVE TELEMETRY", color = Muted, fontSize = 10.sp, letterSpacing = 1.6.sp)
                        Text(statusText, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.12f))
                            .border(1.dp, accent.copy(alpha = 0.65f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (isRunning) "ACTIVE" else "STANDBY", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OrbitalRadar(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    accent = accent,
                    active = status == NovaStatus.CONNECTED || status == NovaStatus.VERIFYING || status == NovaStatus.CONNECTING
                )
                Text(
                    text = when {
                        status == NovaStatus.CONNECTED -> "ENCRYPTED ROUTE VERIFIED"
                        status == NovaStatus.ERROR -> "CHECK PROFILE OR NETWORK"
                        status == NovaStatus.OFFLINE -> "AWAITING OPERATOR COMMAND"
                        else -> "SCANNING FOR A STABLE ROUTE"
                    },
                    color = if (status == NovaStatus.CONNECTED) Mint else Muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Danger.copy(alpha = 0.15f) else Mint,
                        contentColor = if (isRunning) Danger else Void
                    )
                ) {
                    Text(if (isRunning) "DISCONNECT TUNNEL" else "INITIATE CONNECTION", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
                ) {
                    Text("RUN LINK DIAGNOSTICS", fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryTile("LATENCY", pingMs?.takeIf { it > 0L }?.let { "${it} ms" } ?: "—", if (hasPing) Mint else Muted, Modifier.weight(1f))
            TelemetryTile("TUNNEL", if (isRunning && hasPing) "VERIFIED" else "UNVERIFIED", if (isRunning && hasPing) Mint else Amber, Modifier.weight(1f))
            TelemetryTile("PROFILE", if (serverTitle.isBlank()) "NONE" else "SELECTED", Cyan, Modifier.weight(1f))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.9f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ACTIVE ROUTE", color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp)
                Text(
                    text = serverTitle.ifBlank { "No server selected" },
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (isRunning && hasPing) Mint else Muted))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRunning && hasPing) "Traffic check passed" else "Waiting for verified traffic", color = Muted, fontSize = 11.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAction("＋  IMPORT CONFIG", onImport, Modifier.weight(1f))
            QuickAction("▦  SCAN QR", onScanQr, Modifier.weight(1f))
        }
        Text("NOVA INTERFACE  •  VPN ENGINE UNCHANGED", color = Color(0xFF476171), fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
    }
}

@Composable
private fun OrbitalRadar(modifier: Modifier, accent: Color, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "nova-radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "radar-sweep"
    )
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.43f
        val ringColor = Color(0xFF1E5662)
        for (i in 1..4) {
            drawCircle(ringColor.copy(alpha = 0.78f - i * 0.12f), radius = radius * i / 4f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        }
        drawLine(ringColor.copy(alpha = 0.75f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(ringColor.copy(alpha = 0.75f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
        val angle = Math.toRadians(sweep.toDouble())
        val end = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
        drawLine(
            brush = Brush.linearGradient(listOf(accent.copy(alpha = 0.02f), accent.copy(alpha = 0.9f)), start = center, end = end),
            start = center,
            end = end,
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        val blips = listOf(0.26f to -0.38f, -0.48f to 0.12f, 0.55f to 0.34f, -0.12f to -0.68f)
        blips.forEachIndexed { index, point ->
            val p = Offset(center.x + radius * point.first, center.y + radius * point.second)
            drawCircle(if (active) accent.copy(alpha = 0.28f) else Muted.copy(alpha = 0.2f), radius = (5 + index % 2 * 2).dp.toPx(), center = p)
            drawCircle(if (active) accent else Muted, radius = 2.2.dp.toPx(), center = p)
        }
        drawCircle(accent.copy(alpha = 0.18f), radius = 13.dp.toPx(), center = center)
        drawCircle(accent, radius = 4.dp.toPx(), center = center)
    }
}

@Composable
private fun TelemetryTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = PanelRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp, maxLines = 1)
            Text(value, color = accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
