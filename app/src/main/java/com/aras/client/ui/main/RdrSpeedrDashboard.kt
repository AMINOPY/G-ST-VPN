package com.aras.client.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** RDR SPEEDR home module. Tunnel health is delegated to RadarDashboard's live VPN/IP verifier. */
@Composable
fun RdrSpeedrDashboard(
    isRunning: Boolean,
    hasPing: Boolean,
    selectedGuid: String?,
    isDarkTheme: Boolean,
    serverTitle: String,
    onToggle: () -> Unit,
    onOpenConfigs: () -> Unit,
) {
    val accent = Color(0xFF41F6D0)
    val connectedPulse by animateFloatAsState(if (isRunning && hasPing) 1f else 0.72f, tween(650), label = "speedr-pulse")
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { -it / 5 }) {
            Column {
                Text(
                    "RDR SPEEDR",
                    color = accent,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "PRECISION TUNNEL INTELLIGENCE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadarDashboard(
                    isRunning = isRunning,
                    hasPing = hasPing,
                    selectedGuid = selectedGuid,
                    isDarkTheme = isDarkTheme,
                    serverTitle = serverTitle,
                    modifier = Modifier.alpha(connectedPulse),
                    onRadarClick = onToggle
                )
                Text(
                    if (!isRunning) "TAP RADAR TO CONNECT" else "TAP RADAR TO DISCONNECT",
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF71B8FF).copy(alpha = 0.18f)),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenConfigs)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ACTIVE CONFIGURATION", color = accent, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(
                        if (isRunning) serverTitle.ifBlank { "Selected server" } else "No active tunnel",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text("TAP TO BROWSE CONFIGURATIONS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 0.7.sp)
                }
                Text("›", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}
