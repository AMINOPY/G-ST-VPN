package com.aras.client.ui.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.ui.radar.RadarDisplay
import com.aras.client.ui.radar.RadarPalette
import com.aras.client.ui.radar.RadarNode
import com.aras.client.ui.radar.RadarState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private enum class RadarVerification { DISCONNECTED, CHECKING, VERIFIED, UNVERIFIED, TUNNEL_LOST }

private data class RadarIpResult(val tunnelActive: Boolean, val verified: Boolean, val ip: String? = null)

@Composable
fun RadarDashboard(
    isRunning: Boolean,
    hasPing: Boolean,
    selectedGuid: String?,
    isDarkTheme: Boolean,
    serverTitle: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dangerTransition = rememberInfiniteTransition(label = "radar-danger-blink")
    val dangerAlpha by dangerTransition.animateFloat(
        initialValue = 0.38f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), repeatMode = RepeatMode.Reverse),
        label = "danger-pulse"
    )
    var verification by remember { mutableStateOf(RadarVerification.DISCONNECTED) }
    var publicIp by remember { mutableStateOf<String?>(null) }
    // Start compact so the server/configuration list remains visible on phone screens.
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, hasPing, selectedGuid) {
        publicIp = null
        if (!isRunning) {
            verification = RadarVerification.DISCONNECTED
            return@LaunchedEffect
        }

        verification = RadarVerification.CHECKING
        // Re-check periodically so a tunnel drop is reflected without toggling the VPN switch.
        // This effect is cancelled automatically when the service stops or this screen leaves composition.
        while (true) {
            val result = withContext(Dispatchers.IO) { verifyVpnEgress(context) }
            publicIp = result.ip
            // A successful ping for the selected profile is a direct health signal.
            // Do not keep the radar red merely because a public-IP endpoint is blocked.
            val healthy = result.tunnelActive && result.verified && hasPing
            verification = when {
                // A server-profile ping alone must never override a missing Android VPN tunnel.
                !result.tunnelActive -> RadarVerification.TUNNEL_LOST
                healthy -> RadarVerification.VERIFIED
                // No successful ping or no verified egress must never be shown as green.
                else -> RadarVerification.UNVERIFIED
            }
            delay(5_000)
        }
    }

    val panel = when (verification) {
        RadarVerification.VERIFIED -> Color(0xFF00170A)
        RadarVerification.UNVERIFIED, RadarVerification.TUNNEL_LOST -> Color(0xFF210307)
        RadarVerification.DISCONNECTED -> Color(0xFF191C20)
        RadarVerification.CHECKING -> if (isDarkTheme) Color(0xFF111821) else Color(0xFFFFFFFF)
    }
    val outline = when (verification) {
        RadarVerification.VERIFIED -> Color(0xFF07552C)
        RadarVerification.UNVERIFIED, RadarVerification.TUNNEL_LOST -> Color(0xFF74121C)
        RadarVerification.DISCONNECTED -> Color(0xFF414852)
        RadarVerification.CHECKING -> if (isDarkTheme) Color(0xFF273442) else Color(0xFFD2D9E1)
    }
    val primaryText = when (verification) {
        RadarVerification.VERIFIED -> Color(0xFFB8FFD0)
        RadarVerification.UNVERIFIED, RadarVerification.TUNNEL_LOST -> Color(0xFFFFD9DC)
        RadarVerification.DISCONNECTED -> Color(0xFFE2E5E9)
        RadarVerification.CHECKING -> if (isDarkTheme) Color(0xFFEAF1F7) else Color(0xFF17212E)
    }
    val secondaryText = when (verification) {
        RadarVerification.VERIFIED -> Color(0xFF64B982)
        RadarVerification.UNVERIFIED, RadarVerification.TUNNEL_LOST -> Color(0xFFFF737D)
        RadarVerification.DISCONNECTED -> Color(0xFFFF525C)
        RadarVerification.CHECKING -> if (isDarkTheme) Color(0xFF8997A7) else Color(0xFF5D6978)
    }

    val statusColor = when (verification) {
        RadarVerification.DISCONNECTED -> Color(0xFFFF525C)
        RadarVerification.CHECKING -> Color(0xFFC18A32)
        RadarVerification.UNVERIFIED -> Color(0xFFFF2938)
        RadarVerification.TUNNEL_LOST -> Color(0xFFFF2938)
        RadarVerification.VERIFIED -> Color(0xFF20FF79)
    }
    val statusLabel = when (verification) {
        RadarVerification.DISCONNECTED -> "NO SIGNAL"
        RadarVerification.CHECKING -> "VERIFYING TUNNEL"
        RadarVerification.UNVERIFIED -> "DANGER"
        RadarVerification.TUNNEL_LOST -> "DANGER"
        RadarVerification.VERIFIED -> "SIGNAL ACTIVE"
    }
    val statusDetail = when (verification) {
        RadarVerification.DISCONNECTED -> "VPN tunnel inactive"
        RadarVerification.CHECKING -> "Checking VPN tunnel, exit IP and selected-config ping"
        RadarVerification.UNVERIFIED -> if (!hasPing) "Selected configuration has no successful ping" else "Tunnel is active, but traffic could not be verified"
        RadarVerification.TUNNEL_LOST -> "VPN is enabled but the VPN network is not active"
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
                        RadarVerification.UNVERIFIED, RadarVerification.TUNNEL_LOST -> "DANGER"
                        RadarVerification.CHECKING -> "CHECKING"
                    },
                    color = statusColor.copy(
                        alpha = if (verification == RadarVerification.UNVERIFIED ||
                            verification == RadarVerification.TUNNEL_LOST) dangerAlpha else 1f
                    ),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (expanded) "MINIMIZE" else "EXPAND",
                    color = statusColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 5.dp, vertical = 7.dp)
                )
            }

            Spacer(Modifier.height(if (expanded) 8.dp else 2.dp))

            RadarDisplay(
                state = when (verification) {
                    RadarVerification.DISCONNECTED -> RadarState.DISCONNECTED
                    RadarVerification.CHECKING -> RadarState.CONNECTING
                    RadarVerification.VERIFIED -> RadarState.CONNECTED_VERIFIED
                    RadarVerification.UNVERIFIED -> RadarState.ERROR
                    RadarVerification.TUNNEL_LOST -> RadarState.ERROR
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (expanded) 18.dp else 42.dp)
                    .heightIn(max = if (expanded) 280.dp else 145.dp),
                palette = if (isDarkTheme) RadarPalette.Night else RadarPalette.Day,
                title = statusLabel,
                subtitle = statusDetail,
                // One blip represents the currently selected configuration/tunnel health.
                // Never fabricate a cluster of "other configs" from repeated samples.
                nodes = when (verification) {
                    RadarVerification.VERIFIED -> listOf(RadarNode(45f, 0.52f, 75))
                    RadarVerification.UNVERIFIED, RadarVerification.TUNNEL_LOST ->
                        listOf(RadarNode(45f, 0.52f, 500))
                    else -> emptyList()
                },
            )

            if (expanded) {
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(when (verification) {
                        RadarVerification.VERIFIED -> Color(0xFF00230F)
                        RadarVerification.UNVERIFIED, RadarVerification.TUNNEL_LOST -> Color(0xFF33060C)
                        RadarVerification.DISCONNECTED -> Color(0xFF252A30)
                        RadarVerification.CHECKING -> if (isDarkTheme) Color(0xFF171F29) else Color(0xFFF1F4F7)
                    })
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
}

private fun verifyVpnEgress(context: Context): RadarIpResult {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return RadarIpResult(tunnelActive = false, verified = false)

    // Do not rely on activeNetwork alone: on some Android/VPN combinations it can
    // report the underlying Wi-Fi/mobile network. Search all Android VPN networks.
    val activeNetwork = connectivity.activeNetwork
    val vpnNetworks = buildList {
        if (activeNetwork != null) {
            val caps = connectivity.getNetworkCapabilities(activeNetwork)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add(activeNetwork)
        }
        connectivity.allNetworks.forEach { network ->
            val caps = connectivity.getNetworkCapabilities(network)
            if (
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                network !in this
            ) {
                add(network)
            }
        }
    }

    if (vpnNetworks.isEmpty()) {
        return RadarIpResult(tunnelActive = false, verified = false)
    }

    // Try more than one lightweight IP endpoint. Some VPN exits or DNS filters
    // block a particular provider even though the tunnel itself carries traffic.
    val endpoints = listOf(
        "https://api.ipify.org",
        "https://icanhazip.com/"
    )

    for (network in vpnNetworks) {
        for (endpoint in endpoints) {
            val connection = try {
                network.openConnection(URL(endpoint)) as? HttpURLConnection
            } catch (_: Exception) {
                null
            } ?: continue

            try {
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "text/plain")
                connection.instanceFollowRedirects = true

                if (connection.responseCode !in 200..299) continue

                val ip = connection.inputStream.bufferedReader().use { it.readText() }
                    .trim()
                    .take(64)
                val looksLikeIp = ip.isNotBlank() &&
                    ip.matches(Regex("^[0-9a-fA-F:.]+$")) &&
                    (ip.contains('.') || ip.contains(':'))

                if (looksLikeIp) {
                    return RadarIpResult(tunnelActive = true, verified = true, ip = ip)
                }
            } catch (_: Exception) {
                // Try the next endpoint/network before reporting a failed verification.
            } finally {
                connection.disconnect()
            }
        }
    }

    // Android has an active VPN interface, but no external request succeeded
    // through it, so do not falsely mark the radar green.
    return RadarIpResult(tunnelActive = true, verified = false)
}
