package com.aras.client.ui.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
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
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private enum class RadarVerification { DISCONNECTED, CHECKING, VERIFIED, UNVERIFIED, TUNNEL_LOST }

private data class RadarIpResult(val tunnelActive: Boolean, val verified: Boolean, val ip: String? = null)

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
            verification = when {
                !result.tunnelActive -> RadarVerification.TUNNEL_LOST
                result.verified -> RadarVerification.VERIFIED
                else -> RadarVerification.UNVERIFIED
            }
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
        RadarVerification.CHECKING, RadarVerification.UNVERIFIED -> Color(0xFFFF2938)
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
        RadarVerification.CHECKING -> "Checking the active VPN network"
        RadarVerification.UNVERIFIED -> "Tunnel is active, but traffic could not be verified"
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
                        else -> "CHECKING"
                    },
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            RadarDisplay(
                state = when (verification) {
                    RadarVerification.DISCONNECTED -> RadarState.DISCONNECTED
                    RadarVerification.CHECKING -> RadarState.CONNECTING
                    RadarVerification.VERIFIED -> RadarState.CONNECTED_VERIFIED
                    RadarVerification.UNVERIFIED -> RadarState.ERROR
                    RadarVerification.TUNNEL_LOST -> RadarState.ERROR
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                palette = if (isDarkTheme) RadarPalette.Night else RadarPalette.Day,
                nodes = if (verification == RadarVerification.VERIFIED) {
                    listOf(
                        RadarNode(
                            bearingDeg = ((publicIp?.hashCode() ?: 90).toUInt().toLong() % 360L).toFloat(),
                            distance01 = 0.58f,
                            pingMs = 80,
                        )
                    )
                } else emptyList(),
            )

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

private fun verifyVpnEgress(context: Context): RadarIpResult {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return RadarIpResult(tunnelActive = false, verified = false)
    val network = connectivity.activeNetwork ?: return RadarIpResult(tunnelActive = false, verified = false)
    val capabilities = connectivity.getNetworkCapabilities(network)
        ?: return RadarIpResult(tunnelActive = false, verified = false)
    if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
        return RadarIpResult(tunnelActive = false, verified = false)
    }

    val connection = try {
        network.openConnection(URL("https://api64.ipify.org")) as? HttpURLConnection
            ?: return RadarIpResult(tunnelActive = true, verified = false)
    } catch (_: Exception) {
        return RadarIpResult(tunnelActive = true, verified = false)
    }

    return try {
        connection.connectTimeout = 4500
        connection.readTimeout = 4500
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "text/plain")
        val code = connection.responseCode
        if (code !in 200..299) return RadarIpResult(tunnelActive = true, verified = false)
        val ip = connection.inputStream.bufferedReader().use { it.readText().trim() }.take(64)
        val looksLikeIp = ip.isNotBlank() && ip.matches(Regex("^[0-9a-fA-F:.]+$")) && (ip.contains('.') || ip.contains(':'))
        if (looksLikeIp) RadarIpResult(tunnelActive = true, verified = true, ip = ip)
        else RadarIpResult(tunnelActive = true, verified = false)
    } catch (_: Exception) {
        RadarIpResult(tunnelActive = true, verified = false)
    } finally {
        connection.disconnect()
    }
}
