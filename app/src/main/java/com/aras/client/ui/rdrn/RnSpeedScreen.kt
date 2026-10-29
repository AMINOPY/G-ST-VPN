package com.aras.client.ui.rdrn

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.util.ConnectionStatsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val SCALE_STEPS = floatArrayOf(10f, 25f, 50f, 100f, 150f, 300f, 500f, 1000f)

/**
 * Tab 5 — speed test plus the user's real network: connection type (Wi-Fi / 4G / 5G),
 * SIM operator (e.g. Hamrah-e Avval, Irancell, Rightel), signal, ISP and public IP.
 * Read-only: it only measures and displays, it never touches the VPN or its settings.
 */
@Composable
fun RnSpeedScreen(
    state: RnSpeedState,
    vpnRunning: Boolean,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("Real-time") }
    var net by remember { mutableStateOf(RnNetInfo()) }

    // Re-read the physical network every few seconds (it changes when the user toggles Wi-Fi / mobile data).
    LaunchedEffect(vpnRunning) {
        while (true) {
            net = withContext(Dispatchers.Default) { RnNet.read(ctx) }
            delay(3000)
        }
    }
    LaunchedEffect(net.isWifi, net.isCellular) {
        if (state.publicIp.isBlank()) {
            val (ip, isp) = RnNet.lookupIsp()
            if (ip.isNotBlank()) { state.publicIp = ip; state.isp = isp }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { net = withContext(Dispatchers.Default) { RnNet.read(ctx) } }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp)) {
        RnHeader("Speed & Traffic", onMenu, Modifier.rnRise(0))
        Row(Modifier.fillMaxWidth().rnRise(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Real-time", "Statistics").forEach { t ->
                RnChip(t, tab == t, { tab = t }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (tab == "Real-time") {
            RealtimeTab(state, net, vpnRunning, onStart = {
                val label = "${net.shortName.ifBlank { "Net" }} · " +
                    (if (net.isWifi) state.isp.ifBlank { "Wi-Fi" } else net.operatorName)
                state.start(scope, label)
            }, onAskPermission = { permission.launch(Manifest.permission.READ_PHONE_STATE) })
        } else {
            StatsTab(state)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RealtimeTab(
    state: RnSpeedState,
    net: RnNetInfo,
    vpnRunning: Boolean,
    onStart: () -> Unit,
    onAskPermission: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().rnRise(2), contentAlignment = Alignment.Center) {
        RnGauge(state)
    }
    Row(Modifier.fillMaxWidth().rnRise(3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStat("Upload", "%.1f".format(Locale.US, state.up), "Mbps", Icons.Rounded.ArrowUpward, Modifier.weight(1f))
        MiniStat("Ping", state.ping.toString(), "ms", Icons.Rounded.Radar, Modifier.weight(1f))
    }
    Spacer(Modifier.height(4.dp))
    RnWave(state.running, Modifier.fillMaxWidth().height(34.dp).rnRise(4))
    Spacer(Modifier.height(6.dp))

    val label = when {
        state.running -> "TESTING…"
        state.phase == RnPhase.DONE -> "TEST AGAIN"
        else -> "START SPEED TEST"
    }
    Box(
        Modifier.fillMaxWidth().height(46.dp).rnRise(5)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(RnColors.Green.copy(alpha = 0.30f), RnColors.Green.copy(alpha = 0.08f))))
            .border(1.dp, RnColors.Green, RoundedCornerShape(14.dp))
            .clickable(enabled = !state.running, onClick = onStart),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color(0xFFEAFFEF), fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 2.sp) }
    if (state.phase == RnPhase.ERROR) {
        Text("Speed test failed. Check your connection and try again.", color = RnColors.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
    if (vpnRunning) {
        Text("VPN is on: the test measures speed through your tunnel.", color = RnColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }

    Spacer(Modifier.height(12.dp))
    RnSectionTitle("YOUR NETWORK")
    Column(Modifier.fillMaxWidth().rnRise(6).rnCard(borderAlpha = 0.28f)) {
        Row {
            InfoCell("Connection", net.connection, Modifier.weight(1f))
            InfoCell("Signal", signalText(net.signal), Modifier.weight(1f), bars = net.signal)
        }
        InfoCell("SIM operator", net.operatorName, Modifier.fillMaxWidth(), accent = true)
        InfoCell("SIM card", net.simLabel, Modifier.fillMaxWidth())
        Row {
            InfoCell("ISP", state.isp.ifBlank { "—" }, Modifier.weight(1f))
            InfoCell(net.extraLabel, net.extraValue, Modifier.weight(1f))
        }
        Row {
            InfoCell("Public IP", state.publicIp.ifBlank { "—" }, Modifier.weight(1f))
            InfoCell("VPN", if (vpnRunning) "Protected" else "Off", Modifier.weight(1f), valueColor = if (vpnRunning) RnColors.Green else RnColors.Red)
        }
    }
    if (!net.hasPhonePermission) {
        Text(
            "Allow phone access to read the SIM operator →",
            color = RnColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onAskPermission),
        )
    }

    Spacer(Modifier.height(12.dp))
    val snap = remember(vpnRunning) { ConnectionStatsManager.snapshot() }
    Column(Modifier.fillMaxWidth().rnRise(7).rnCard(borderAlpha = 0.28f).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("Traffic", color = Color(0xFFCFD9D2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Total", color = Color(0xFFCFD9D2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        }
        TrafficRow("Download", bytesText(snap.totalDown))
        TrafficRow("Upload", bytesText(snap.totalUp))
    }
}

@Composable
private fun StatsTab(state: RnSpeedState) {
    val items = state.history
    Column(Modifier.fillMaxWidth().rnRise(2).rnCard(borderAlpha = 0.28f).padding(12.dp)) {
        Text("Download of recent tests (Mbps)", color = Color(0xFFA9BDB0), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("No tests yet. Run a speed test and the results appear here.", color = RnColors.Muted, fontSize = 12.sp)
        } else {
            val shown = items.take(8).reversed()
            val peak = max(1f, shown.maxOf { it.down })
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                shown.forEach { h ->
                    val grow = animateFloatAsState(h.down / peak, tween(700, easing = FastOutSlowInEasing), label = "bar").value
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text("%.0f".format(Locale.US, h.down), color = Color(0xFFA9BDB0), fontSize = 9.sp)
                        Box(
                            Modifier.fillMaxWidth().height((70f * grow).coerceAtLeast(3f).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(Brush.verticalGradient(listOf(RnColors.Green, RnColors.GreenDeep))),
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    RnSectionTitle("RECENT TESTS")
    Column(Modifier.fillMaxWidth().rnRise(3).rnCard(borderAlpha = 0.28f).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.padding(vertical = 4.dp)) {
            listOf("Network" to 2.2f, "Down" to 1f, "Up" to 1f, "Ping" to 1f).forEach { (t, w) ->
                Text(t, color = Color(0xFFCFD9D2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(w))
            }
        }
        val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }
        items.take(10).forEach { h ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.weight(2.2f)) {
                    Text(h.network, color = Color.White, fontSize = 12.sp, maxLines = 1)
                    Text(fmt.format(Date(h.time)), color = RnColors.Muted, fontSize = 10.sp)
                }
                Text("%.1f".format(Locale.US, h.down), color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("%.1f".format(Locale.US, h.up), color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("${h.ping}ms", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrafficRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(name, color = Color(0xFFCFD9D2), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MiniStat(label: String, value: String, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Row(modifier.rnCard().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).border(1.dp, RnColors.Green.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = RnColors.Green, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(label, color = Color(0xFFA9BDB0), fontSize = 10.5.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(" $unit", color = Color(0xFFA9BDB0), fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun InfoCell(
    label: String,
    value: String,
    modifier: Modifier,
    accent: Boolean = false,
    bars: Int = -1,
    valueColor: Color = Color.White,
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label.uppercase(Locale.US), color = Color(0xFF8FA699), fontSize = 10.sp, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bars >= 0) RnBars(bars)
            Text(value, color = if (accent) RnColors.Gold else valueColor, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun signalText(level: Int) = when (level) { 4 -> "Excellent"; 3 -> "Good"; 2 -> "Fair"; 1 -> "Weak"; else -> "—" }

private fun bytesText(b: Long): String {
    val v = b.coerceAtLeast(0).toDouble()
    return when {
        v >= 1L shl 30 -> "%.1f GB".format(Locale.US, v / (1L shl 30))
        v >= 1L shl 20 -> "%.1f MB".format(Locale.US, v / (1L shl 20))
        v >= 1L shl 10 -> "%.1f KB".format(Locale.US, v / (1L shl 10))
        else -> "$b B"
    }
}

/** 270° gauge: glowing arc, ticks, needle dot, a spinning ring while a test runs and a burst on completion. */
@Composable
private fun RnGauge(state: RnSpeedState) {
    val isPing = state.unit == "ms"
    val value = state.current
    val peak = if (state.phase == RnPhase.DONE) max(state.down, state.up) else max(value, 0f)
    val scaleMax = if (isPing) (if (value > 100f) 300f else 100f) else (SCALE_STEPS.firstOrNull { it >= peak * 1.1f } ?: 1000f)
    val target = (value / scaleMax).coerceIn(0f, 1f)
    val p = animateFloatAsState(target, tween(160, easing = LinearEasing), label = "gauge").value
    val running = state.running
    val spin = rememberInfiniteTransition(label = "gauge-spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "spin").value
    val breathe = rememberInfiniteTransition(label = "gauge-breathe").animateFloat(0.25f, 0.55f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "breathe").value
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = RnColors.Muted, fontSize = 9.sp)

    Box(Modifier.widthIn(max = 240.dp).fillMaxWidth(0.72f).aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val side = size.minDimension
            val r = side * 0.40f
            val stroke = side * 0.045f
            drawCircle(Color(0xB305140C), radius = side * 0.46f, center = c)
            drawCircle(RnColors.Gold.copy(alpha = 0.35f), radius = side * 0.46f, center = c, style = Stroke(1.dp.toPx()))
            drawCircle(RnColors.Green.copy(alpha = breathe * 0.25f), radius = side * 0.5f, center = c, style = Stroke(side * 0.02f))
            val topLeft = Offset(c.x - r, c.y - r)
            val arcSize = Size(r * 2, r * 2)
            rotate(135f, c) {
                drawArc(Color.White.copy(alpha = 0.08f), 0f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (p > 0.002f) {
                    val brush = Brush.sweepGradient(
                        0f to RnColors.Green, 0.30f to Color(0xFFE8E23A), 0.60f to RnColors.Red, 1f to RnColors.Red, center = c,
                    )
                    drawArc(RnColors.Green.copy(alpha = 0.25f), 0f, 270f * p, false, topLeft, arcSize, style = Stroke(stroke * 1.9f, cap = StrokeCap.Round))
                    drawArc(brush, 0f, 270f * p, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            if (running) {
                rotate(spin, c) {
                    drawArc(
                        Brush.sweepGradient(0f to Color.Transparent, 0.72f to Color.Transparent, 1f to RnColors.Green, center = c),
                        0f, 360f, false, Offset(c.x - side * 0.485f, c.y - side * 0.485f), Size(side * 0.97f, side * 0.97f),
                        style = Stroke(3.dp.toPx()),
                    )
                }
            }
            for (i in 0..27) {
                val a = (135.0 + 270.0 * i / 27.0) * PI / 180.0
                val long = i % 9 == 0
                val r1 = side * 0.455f
                val r2 = side * (if (long) 0.42f else 0.437f)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1), Offset(c.x + cos(a).toFloat() * r2, c.y + sin(a).toFloat() * r2), 1.dp.toPx())
            }
            for (i in 0..3) {
                val q = i / 3f
                val a = (135.0 + 270.0 * q) * PI / 180.0
                val txt = measurer.measure((q * scaleMax).toInt().toString(), labelStyle)
                val rr = side * 0.30f
                drawText(txt, topLeft = Offset(c.x + cos(a).toFloat() * rr - txt.size.width / 2f, c.y + sin(a).toFloat() * rr - txt.size.height / 2f))
            }
            val na = (135.0 + 270.0 * p) * PI / 180.0
            val np = Offset(c.x + cos(na).toFloat() * r, c.y + sin(na).toFloat() * r)
            drawCircle(Color.White.copy(alpha = 0.25f), radius = stroke * 1.4f, center = np)
            drawCircle(Color.White, radius = stroke * 0.55f, center = np)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.label, color = RnColors.Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Text(
                if (isPing) value.toInt().toString() else "%.1f".format(Locale.US, value),
                color = Color(0xFFEAFFEF), fontSize = 44.sp, fontWeight = FontWeight.Bold,
            )
            Text(state.unit, color = Color(0xFFB9D0C1), fontSize = 12.sp)
        }
        RnBurst(state.doneTick, RnColors.GreenSoft, Modifier.fillMaxSize(), ringRadiusFraction = 0.46f)
    }
}

/** Calm green sine wave; runs faster while a test is in progress. */
@Composable
private fun RnWave(fast: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "wave")
    val phase = t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(if (fast) 1300 else 5000, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    Box(
        modifier.drawBehind {
            val path = Path()
            val mid = size.height / 2f
            var x = 0f
            path.moveTo(0f, mid)
            while (x <= size.width) {
                val u = x / size.width * 6f * PI.toFloat() + phase
                path.lineTo(x, mid - size.height * 0.30f * sin(u) - size.height * 0.12f * sin(u * 2.7f + 1f))
                x += 4f
            }
            drawPath(path, RnColors.Green.copy(alpha = 0.25f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, RnColors.Green, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        },
    )
}
