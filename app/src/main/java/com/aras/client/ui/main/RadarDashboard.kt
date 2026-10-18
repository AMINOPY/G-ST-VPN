package com.aras.client.ui.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private enum class RadarVerification { DISCONNECTED, CHECKING, VERIFIED, UNVERIFIED }

private data class RadarIpResult(val verified: Boolean, val ip: String? = null)

@Composable
fun RadarDashboard(
    isRunning: Boolean,
    isDarkTheme: Boolean,
    serverTitle: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var verification by remember { mutableStateOf(RadarVerification.DISCONNECTED) }
    var publicIp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isRunning) {
        publicIp = null
        if (!isRunning) {
            verification = RadarVerification.DISCONNECTED
            return@LaunchedEffect
        }

        verification = RadarVerification.CHECKING
        val result = withContext(Dispatchers.IO) { verifyVpnEgress(context) }
        // This effect is keyed to the service state, so a disconnect cancels an older check.
        if (isRunning) {
            publicIp = result.ip
            verification = if (result.verified) RadarVerification.VERIFIED
            else RadarVerification.UNVERIFIED
        }
    }

    val panel = if (isDarkTheme) Color(0xFF111821) else Color(0xFFFFFFFF)
    val outline = if (isDarkTheme) Color(0xFF273442) else Color(0xFFD2D9E1)
    val primaryText = if (isDarkTheme) Color(0xFFEAF1F7) else Color(0xFF17212E)
    val secondaryText = if (isDarkTheme) Color(0xFF8997A7) else Color(0xFF5D6978)
    val radarBase = if (isDarkTheme) Color(0xFF101923) else Color(0xFFE5EAF0)
    val radarGrid = if (isDarkTheme) Color(0xFF273442) else Color(0xFFB9C4D0)

    val statusColor = when (verification) {
        RadarVerification.DISCONNECTED -> Color(0xFFFF525C)
        RadarVerification.CHECKING, RadarVerification.UNVERIFIED -> Color(0xFFC18A32)
        RadarVerification.VERIFIED -> Color(0xFF7FE3AC)
    }
    val statusLabel = when (verification) {
        RadarVerification.DISCONNECTED -> "NO SIGNAL"
        RadarVerification.CHECKING -> "VERIFYING TUNNEL"
        RadarVerification.UNVERIFIED -> "TUNNEL ACTIVE · IP UNVERIFIED"
        RadarVerification.VERIFIED -> "SIGNAL ACTIVE"
    }
    val statusDetail = when (verification) {
        RadarVerification.DISCONNECTED -> "VPN tunnel inactive"
        RadarVerification.CHECKING -> "Checking the active VPN network"
        RadarVerification.UNVERIFIED -> "Tunnel is running, but egress IP could not be verified"
        RadarVerification.VERIFIED -> "SECURE TUNNEL ESTABLISHED"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = panel,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RADAR // NETWORK STATUS",
                        color = primaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "LIVE TUNNEL MONITOR",
                        color = secondaryText,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = when (verification) {
                        RadarVerification.VERIFIED -> "ONLINE"
                        RadarVerification.DISCONNECTED -> "OFFLINE"
                        else -> "CHECKING"
                    },
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(228.dp)
                    .clip(CircleShape)
                    .background(radarBase),
                contentAlignment = Alignment.Center
            ) {
                RadarGraphic(
                    active = verification == RadarVerification.VERIFIED,
                    base = radarBase,
                    grid = radarGrid,
                    signal = statusColor,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.size(228.dp)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 18.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = if (verification == RadarVerification.UNVERIFIED) 10.sp else 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = statusDetail,
                        color = secondaryText,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDarkTheme) Color(0xFF171F29) else Color(0xFFF1F4F7))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PUBLIC EGRESS IP", color = secondaryText, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(
                        text = publicIp ?: if (verification == RadarVerification.CHECKING) "Checking…" else "—",
                        color = primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                    Text("SELECTED NODE", color = secondaryText, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(
                        text = if (isRunning) serverTitle.take(22).ifBlank { "Connected profile" } else "No active node",
                        color = primaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = "IP check uses the active VPN network when Android exposes it.",
                color = secondaryText,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 7.dp)
            )
        }
    }
}

@Composable
private fun RadarGraphic(
    active: Boolean,
    base: Color,
    grid: Color,
    signal: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val sweep = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            while (true) {
                sweep.animateTo(
                    targetValue = 360f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 2400,
                        easing = androidx.compose.animation.core.LinearEasing
                    )
                )
                sweep.snapTo(0f)
            }
        } else {
            sweep.snapTo(0f)
        }
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.46f
        val ringColor = if (active) Color(0xFF7FE3AC) else grid
        drawCircle(color = base, radius = radius, center = center)
        for (fraction in listOf(0.28f, 0.52f, 0.76f, 1f)) {
            drawCircle(
                color = ringColor.copy(alpha = if (active) 0.62f else 0.8f),
                radius = radius * fraction,
                center = center,
                style = Stroke(width = if (fraction == 1f) 1.8.dp.toPx() else 1.dp.toPx())
            )
        }
        for (degree in 0 until 360 step 30) {
            val angle = Math.toRadians(degree.toDouble())
            val inner = radius * if (degree % 90 == 0) 0.83f else 0.91f
            val outer = radius * 0.98f
            drawLine(
                color = ringColor.copy(alpha = if (degree % 90 == 0) 0.85f else 0.48f),
                start = Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner),
                end = Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer),
                strokeWidth = if (degree % 90 == 0) 1.5.dp.toPx() else 1.dp.toPx()
            )
        }
        drawLine(color = grid.copy(alpha = 0.55f), start = Offset(center.x - radius, center.y), end = Offset(center.x + radius, center.y), strokeWidth = 0.7.dp.toPx())
        drawLine(color = grid.copy(alpha = 0.55f), start = Offset(center.x, center.y - radius), end = Offset(center.x, center.y + radius), strokeWidth = 0.7.dp.toPx())

        if (active) {
            rotate(sweep.value, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, signal.copy(alpha = 0.05f), signal.copy(alpha = 0.48f)),
                        center = center
                    ),
                    startAngle = -52f,
                    sweepAngle = 52f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
                drawLine(
                    color = signal.copy(alpha = 0.9f),
                    start = center,
                    end = Offset(center.x, center.y - radius),
                    strokeWidth = 1.6.dp.toPx()
                )
            }
            val nodes = listOf(
                Offset(center.x + radius * 0.45f, center.y - radius * 0.18f),
                Offset(center.x - radius * 0.38f, center.y + radius * 0.42f),
                Offset(center.x + radius * 0.12f, center.y + radius * 0.65f)
            )
            nodes.forEach { node ->
                drawCircle(color = signal.copy(alpha = 0.22f), radius = 5.dp.toPx(), center = node)
                drawCircle(color = signal, radius = 2.dp.toPx(), center = node)
            }
        } else {
            drawCircle(color = grid, radius = 2.5.dp.toPx(), center = center)
        }
    }
}

private fun verifyVpnEgress(context: Context): RadarIpResult {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return RadarIpResult(false)
    val network = connectivity.activeNetwork ?: return RadarIpResult(false)
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return RadarIpResult(false)
    if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
        return RadarIpResult(false)
    }

    val connection = try {
        network.openConnection(URL("https://api64.ipify.org")) as? HttpURLConnection
            ?: return RadarIpResult(false)
    } catch (_: Exception) {
        return RadarIpResult(false)
    }

    return try {
        connection.connectTimeout = 4500
        connection.readTimeout = 4500
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "text/plain")
        val code = connection.responseCode
        if (code !in 200..299) return RadarIpResult(false)
        val ip = connection.inputStream.bufferedReader().use { it.readText().trim() }.take(64)
        val looksLikeIp = ip.isNotBlank() && ip.matches(Regex("^[0-9a-fA-F:.]+$")) && (ip.contains('.') || ip.contains(':'))
        if (looksLikeIp) RadarIpResult(true, ip) else RadarIpResult(false)
    } catch (_: Exception) {
        RadarIpResult(false)
    } finally {
        connection.disconnect()
    }
}
