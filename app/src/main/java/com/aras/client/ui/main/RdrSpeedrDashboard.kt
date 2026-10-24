package com.aras.client.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.util.ConnectionStatsManager
import com.aras.client.ui.compose.formatBytes
import com.aras.client.ui.compose.formatDuration
import kotlinx.coroutines.delay

/** RDR SPEEDR: distinct liquid-glass-inspired dashboard; live tunnel verification stays in RadarDashboard. */
@Composable
fun RdrSpeedrDashboard(
    isRunning: Boolean,
    hasPing: Boolean,
    selectedGuid: String?,
    isDarkTheme: Boolean,
    serverTitle: String,
    onToggle: () -> Unit,
    onOpenConfigs: () -> Unit,
    onOpenSettings: () -> Unit = onOpenConfigs,
) {
    val accent = Color(0xFF41F6D0)
    val blue = Color(0xFF71B8FF)
    val connected = isRunning && hasPing
    val pulse by animateFloatAsState(
        targetValue = if (connected) 1f else 0.86f,
        animationSpec = tween(900),
        label = "speedr-pulse"
    )
    var menuExpanded by remember { mutableStateOf(false) }
    var sessionSnapshot by remember { mutableStateOf(ConnectionStatsManager.snapshot()) }
    var downRate by remember { mutableLongStateOf(0L) }
    var upRate by remember { mutableLongStateOf(0L) }

    // Counters are cumulative, so derive throughput from deltas.
    LaunchedEffect(isRunning) {
        var previous = ConnectionStatsManager.snapshot()
        while (true) {
            delay(2000)
            val current = ConnectionStatsManager.snapshot()
            if (isRunning) {
                downRate = (current.sessionDown - previous.sessionDown).coerceAtLeast(0L) / 2L
                upRate = (current.sessionUp - previous.sessionUp).coerceAtLeast(0L) / 2L
            } else {
                downRate = 0L
                upRate = 0L
            }
            sessionSnapshot = current
            previous = current
        }
    }
    val status = when {
        !isRunning -> "STANDBY"
        connected -> "LINK VERIFIED"
        else -> "VERIFYING LINK"
    }
    val statusColor = when {
        !isRunning -> Color(0xFFFF6678)
        connected -> accent
        else -> Color(0xFFFFC56E)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "RDR SPEEDR",
                    color = accent,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.4.sp
                )
                Text(
                    "PRECISION TUNNEL INTELLIGENCE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    letterSpacing = 1.6.sp
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                modifier = Modifier.size(46.dp).clickable { menuExpanded = !menuExpanded }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (menuExpanded) "×" else "☰", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Light)
                }
            }
        }

        AnimatedVisibility(
            visible = menuExpanded,
            enter = fadeIn(tween(180)) + slideInVertically { -it / 3 },
            exit = fadeOut(tween(140))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedrMenuAction("⌂", "HOME", accent) { menuExpanded = false }
                SpeedrMenuAction("⌁", "MENU", blue) { menuExpanded = false; onOpenConfigs() }
                SpeedrMenuAction("⏻", "TUNNEL", statusColor) { menuExpanded = false; onToggle() }
                SpeedrMenuAction("⚙", "SETTINGS", blue) { menuExpanded = false; onOpenSettings() }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.48f), blue.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f)))),
            tonalElevation = 5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("NETWORK RADAR", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                        Text("LIVE TUNNEL DIAGNOSTICS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 0.8.sp)
                    }
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.48f))
                    ) {
                        Text(status, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                RadarDashboard(
                    isRunning = isRunning,
                    hasPing = hasPing,
                    selectedGuid = selectedGuid,
                    isDarkTheme = isDarkTheme,
                    serverTitle = serverTitle,
                    modifier = Modifier.alpha(pulse),
                    onRadarClick = onToggle
                )
                Text(
                    if (!isRunning) "TAP RADAR TO CONNECT" else "TAP RADAR TO DISCONNECT",
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.15.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, blue.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenConfigs)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = accent.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⌁", color = accent, fontSize = 27.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("ACTIVE CONFIGURATION", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Text(
                        if (selectedGuid == null) "Choose a configuration" else serverTitle.ifBlank { "Selected configuration" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("Tap to browse saved profiles", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                Text("›", color = blue, fontSize = 29.sp, fontWeight = FontWeight.Light)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpeedrMetricCard(
                modifier = Modifier.weight(1f),
                symbol = "↓",
                title = "DOWNLOAD",
                value = if (connected) "${formatBytes(downRate)}/s" else "—",
                tint = accent
            )
            SpeedrMetricCard(
                modifier = Modifier.weight(1f),
                symbol = "↑",
                title = "UPLOAD",
                value = if (connected) "${formatBytes(upRate)}/s" else "—",
                tint = blue
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpeedrMetricCard(
                modifier = Modifier.weight(1f),
                symbol = "⏱",
                title = "SESSION",
                value = if (isRunning) formatDuration(sessionSnapshot.sessionMillis) else "00:00",
                tint = blue
            )
            SpeedrMetricCard(
                modifier = Modifier.weight(1f),
                symbol = "◉",
                title = "LATENCY",
                value = if (hasPing) "PING OK" else "NOT VERIFIED",
                tint = if (hasPing) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            "RDR // SECURE CONNECTION CONTROL",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 9.sp,
            letterSpacing = 1.25.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun SpeedrMenuAction(symbol: String, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.38f)),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(symbol, color = tint, fontSize = 21.sp)
            }
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, letterSpacing = 0.7.sp)
    }
}

@Composable
private fun SpeedrMetricCard(
    modifier: Modifier = Modifier,
    symbol: String,
    title: String,
    value: String,
    tint: Color
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.84f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.20f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(symbol, color = tint, fontSize = 16.sp)
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Text(value, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
