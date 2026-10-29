package com.aras.client.ui.rdrn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.ui.rdr.RadarState
import com.aras.client.ui.rdr.RdrHomeState
import com.aras.client.ui.rdr.formatElapsed
import com.aras.client.ui.rdr.formatSpeed
import com.aras.client.ui.rdr.maskHost

/** Tab 1 — the radar connect button, status card and live speed. */
@Composable
fun RnHomeScreen(
    state: RdrHomeState,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
    onSmartConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp).rnRise(0), verticalAlignment = Alignment.CenterVertically) {
            RnIconButton(Icons.Rounded.Menu, "Menu", onMenu)
            RnBrand(Modifier.weight(1f))
            RnIconButton(Icons.Rounded.Shield, "Smart connect", onSmartConnect, tint = RnColors.Gold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.rnRise(1), contentAlignment = Alignment.Center) {
            RnConnectButton(
                state = state.radar,
                subtitle = when (state.radar) {
                    RadarState.IDLE -> "Tap to connect"
                    RadarState.CONNECTING -> "Please wait"
                    RadarState.CONNECTED -> formatElapsed(state.traffic.elapsedSec)
                    RadarState.DANGER -> "No internet in tunnel"
                },
                onClick = onToggle,
            )
        }
        Spacer(Modifier.height(2.dp))
        AnimatedContent(
            targetState = state.radar,
            transitionSpec = { (fadeIn(tween(380)) + scaleIn(tween(380), initialScale = 0.93f)) togetherWith fadeOut(tween(160)) },
            label = "rn-status",
            modifier = Modifier.fillMaxWidth().rnRise(2),
        ) { radar -> RnStatusCard(radar, state) }
        Spacer(Modifier.height(12.dp))
        RnSpeedStrip(state, Modifier.rnRise(3))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RnStatusCard(radar: RadarState, state: RdrHomeState) {
    val (tint, icon, title, sub) = when (radar) {
        RadarState.IDLE -> StatusLook(RnColors.Red, Icons.Rounded.GppBad, "No Connection", "Your internet is not protected")
        RadarState.CONNECTING -> StatusLook(RnColors.Amber, Icons.Rounded.Radar, "Connecting…", "Locating the best node near you")
        RadarState.CONNECTED -> {
            val s = state.selected
            StatusLook(RnColors.Green, Icons.Rounded.GppGood, "Protected", if (s != null) "Connected · ${s.name} · ${maskHost(s.host)}" else "Connected")
        }
        RadarState.DANGER -> StatusLook(RnColors.Red, Icons.Rounded.Warning, "Tunnel problem", "VPN is on but traffic is not passing")
    }
    val bgTop = when (radar) {
        RadarState.IDLE, RadarState.DANGER -> Color(0x8C5A0E0C)
        RadarState.CONNECTING -> Color(0x80593C0A)
        RadarState.CONNECTED -> RnColors.CardA
    }
    Row(
        Modifier.fillMaxWidth().rnCard(tint = tint, borderAlpha = 0.65f, top = bgTop).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)).border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Color(0xFFA9BDB0), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class StatusLook(val tint: Color, val icon: ImageVector, val title: String, val sub: String)

@Composable
private fun RnSpeedStrip(state: RdrHomeState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().rnCard().padding(vertical = 8.dp, horizontal = 6.dp)) {
        SpeedCell("Download", formatSpeed(state.traffic.downBytesPerSec), Icons.Rounded.ArrowDownward, Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(38.dp).background(RnColors.Gold.copy(alpha = 0.25f)).align(Alignment.CenterVertically))
        SpeedCell("Upload", formatSpeed(state.traffic.upBytesPerSec), Icons.Rounded.ArrowUpward, Modifier.weight(1f))
    }
}

@Composable
private fun SpeedCell(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Row(modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).border(1.dp, RnColors.Green.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = RnColors.Green, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(label, color = Color(0xFFA9BDB0), fontSize = 11.5.sp)
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
