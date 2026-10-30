package com.aras.client.ui.rdrn

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Tab 2 — live radar. Blips light up exactly when the sweep passes over them. */
@Composable
fun RnRadarScreen(
    servers: List<RnServer>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }

    val tested = remember(servers) { servers.filter { (it.pingMs ?: -1) > 0 }.sortedBy { it.pingMs } }
    val shown = remember(servers, tested) { (tested.ifEmpty { servers }).take(4) }
    val blips = remember(tested, servers) { (tested.ifEmpty { servers }).take(12) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp)) {
        RnHeader("Radar", onMenu, Modifier.rnRise(0), boxedMenu = true) {
            Column(horizontalAlignment = Alignment.End) {
                Text("Scanning...", color = RnColors.Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Radar, null, tint = RnColors.Green, modifier = Modifier.size(13.dp))
                    Text(RnUtil.mmss(seconds), color = RnColors.Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).rnRise(1), contentAlignment = Alignment.Center) {
            RadarDisc(blips, selectedId)
        }
        Row(Modifier.fillMaxWidth().rnRise(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("Active Nodes", tested.size, Icons.Rounded.Hub, Modifier.weight(1f))
            StatBox("Total Configs", servers.size, Icons.Rounded.Layers, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().rnCard().padding(16.dp).rnRise(3)) {
                Text("No configs yet. Add some in the Configs tab and the radar will start scanning them.", color = RnColors.Muted, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEachIndexed { i, s -> RadarRow(s, s.id == selectedId, { onSelect(s.id) }, Modifier.rnRise(3 + i)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RadarDisc(blips: List<RnServer>, selectedId: String?) {
    val t = rememberInfiniteTransition(label = "radar")
    val sweep = t.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val pulseA = t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "pa")
    val glow = t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")

    Canvas(Modifier.size(250.dp).clip(CircleShape).border(1.dp, RnColors.Green.copy(alpha = 0.6f), CircleShape)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(RnColors.Green.copy(alpha = 0.16f), Color(0xE6051410)), c, r), r, c)
        for (k in 1..4) drawCircle(RnColors.Green.copy(alpha = 0.4f), r * k / 4f, c, style = Stroke(1.dp.toPx()))
        drawLine(RnColors.Green.copy(alpha = 0.3f), Offset(c.x, 0f), Offset(c.x, size.height), 1.dp.toPx())
        drawLine(RnColors.Green.copy(alpha = 0.3f), Offset(0f, c.y), Offset(size.width, c.y), 1.dp.toPx())
        // outgoing pulses
        listOf(0f, 0.5f).forEach { off ->
            val p = (pulseA.value + off) % 1f
            drawCircle(RnColors.Green.copy(alpha = (1f - p) * 0.55f), r * p, c, style = Stroke(1.dp.toPx()))
        }
        // sweep
        rotate(sweep.value, c) {
            drawCircle(
                Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, RnColors.Green.copy(alpha = 0.0f), Color(0xC0FFE27A)), c),
                r, c,
            )
        }
        // blips
        blips.forEach { s ->
            val a = ((s.id.hashCode().toLong() and 0x7fffffff) % 360L).toFloat()
            val ping = (s.pingMs ?: 400).coerceIn(20, 700)
            val rf = (0.88f - ping / 700f * 0.6f).coerceIn(0.22f, 0.9f)
            val rad = a * PI.toFloat() / 180f
            val pos = Offset(c.x + cos(rad) * r * rf, c.y + sin(rad) * r * rf)
            val diff = ((sweep.value - a) % 360f + 360f) % 360f
            val lit = (1f - diff / 360f).pow(2.2f).coerceIn(0.08f, 1f)
            val col = if (s.id == selectedId) RnColors.Gold else RnColors.GreenSoft
            drawCircle(col.copy(alpha = lit * 0.35f), (4f + 9f * lit).dp.toPx(), pos)
            drawCircle(col.copy(alpha = lit), 3.dp.toPx(), pos)
        }
        // centre
        drawCircle(Color(0xFFFFD54A).copy(alpha = 0.25f * glow.value), 14.dp.toPx(), c)
        drawCircle(Color(0xFFFFE27A), 5.dp.toPx(), c)
    }
}

@Composable
private fun StatBox(label: String, value: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(value) { go = true }
    val shown by animateIntAsState(if (go) value else 0, tween(1100), label = "count")
    Row(modifier.rnCard(tint = RnColors.Gold).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).border(1.dp, RnColors.Green.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = RnColors.Green, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(label, color = Color(0xFFA9BDB0), fontSize = 11.sp)
            Text("$shown", color = RnColors.Green, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RadarRow(s: RnServer, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val ping = s.pingMs
    Row(
        modifier.fillMaxWidth()
            .rnCard(tint = if (selected) RnColors.Gold else RnColors.Gold, borderAlpha = if (selected) 1f else 0.35f)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RnFlag(s.flag)
        Column(Modifier.weight(1f)) {
            Text(s.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${s.protocol} • ${s.port}", color = Color(0xFFA9BDB0), fontSize = 11.5.sp)
        }
        Text(
            when { ping == null -> "—"; ping < 0 -> "timeout"; else -> "$ping ms" },
            color = rnPingColor(ping), fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
        RnBars(rnPingLevel(ping), rnPingColor(ping))
        Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFFCFD8D2), modifier = Modifier.size(18.dp))
    }
}
