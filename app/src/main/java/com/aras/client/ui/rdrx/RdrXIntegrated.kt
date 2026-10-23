package com.aras.client.ui.rdrx

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

enum class RdrXStatus { OFFLINE, CONNECTING, VERIFYING, CONNECTED, ERROR }
data class RdrXNode(val id: String, val name: String, val country: String, val protocol: String, val pingMs: Int?)
data class RdrXMenuItem(val id: String, val label: String)
enum class RdrXSort(val label: String) { PING("پینگ"), NAME("کشور") }

data class RdrXPalette(
    val background: Color, val surface: Color, val glass: Color, val border: Color,
    val text: Color, val textDim: Color, val accent: Color, val connected: Color,
    val error: Color, val offline: Color, val warning: Color
)

private val RdrXDark = RdrXPalette(
    Color(0xFF0A0E1A), Color(0xFF111827), Color(0x14FFFFFF), Color(0x332F4865),
    Color(0xFFEAF3FF), Color(0xFF8D9BB2), Color(0xFF3DA9FF), Color(0xFF39FF88),
    Color(0xFFFF4D5E), Color(0xFF5A6478), Color(0xFFFFD60A)
)
private val RdrXLight = RdrXPalette(
    Color(0xFFF2F6FB), Color(0xFFFFFFFF), Color(0xCCFFFFFF), Color(0x332D4968),
    Color(0xFF0A0E1A), Color(0xFF5A6478), Color(0xFF1B7FD1), Color(0xFF12A956),
    Color(0xFFD93A4A), Color(0xFF7B8799), Color(0xFFB88600)
)
private val LocalRdrXPalette = compositionLocalOf { RdrXDark }

@Composable
fun RdrXTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) RdrXDark else RdrXLight
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, background = p.background, surface = p.surface,
        onBackground = p.text, onSurface = p.text
    ) else lightColorScheme(
        primary = p.accent, background = p.background, surface = p.surface,
        onBackground = p.text, onSurface = p.text
    )
    CompositionLocalProvider(LocalRdrXPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun RdrXHomeScreen(
    status: RdrXStatus,
    statusLabel: String,
    detailLabel: String,
    activeNode: RdrXNode?,
    nodes: List<RdrXNode>,
    menuItems: List<RdrXMenuItem>,
    onRadarTap: () -> Unit,
    onMenuSelect: (String) -> Unit,
    onConfigSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalRdrXPalette.current
    var showConfigs by remember { mutableStateOf(false) }
    val statusColor = when (status) {
        RdrXStatus.OFFLINE -> p.offline
        RdrXStatus.CONNECTING, RdrXStatus.VERIFYING -> p.accent
        RdrXStatus.CONNECTED -> p.connected
        RdrXStatus.ERROR -> p.error
    }

    Column(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(p.surface, p.background, p.background))
        ).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("G-ST-VPN", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text("RADAR X  •  ORBITAL GLASS", color = p.accent, fontSize = 9.sp, letterSpacing = 1.2.sp)
            }
            RdrXArcMenu(items = menuItems, onSelect = onMenuSelect)
        }
        Text(
            "TACTICAL NETWORK RADAR", color = p.textDim, fontSize = 10.sp,
            letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            RdrXRadar(
                status = status, nodes = nodes, activeId = activeNode?.id,
                onTap = onRadarTap, contentDescription = statusLabel,
                modifier = Modifier.size(288.dp)
            )
        }
        Text(statusLabel, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(detailLabel, color = p.textDim, fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { showConfigs = true },
            color = p.surface.copy(alpha = 0.96f), shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, p.border)
        ) {
            Column(Modifier.padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ACTIVE CONFIGURATION", color = p.textDim, fontSize = 9.sp, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(activeNode?.name ?: "هیچ کانفیگی انتخاب نشده", color = p.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(activeNode?.let { "${it.country} · ${it.protocol}" } ?: "برای انتخاب ضربه بزنید",
                            color = p.textDim, fontSize = 10.sp, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PING", color = p.textDim, fontSize = 9.sp)
                        Text(activeNode?.pingMs?.let { "${it} ms" } ?: "—", color = statusColor,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            RdrXMetric("DOWNLOAD ↓", "—", Modifier.weight(1f))
            RdrXMetric("UPLOAD ↑", "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            RdrXAction("CONFIGS", "مدیریت کانفیگ‌ها", Modifier.weight(1f)) { showConfigs = true }
            RdrXAction("ANALYTICS", "تست سرورها", Modifier.weight(1f)) { onMenuSelect("stats") }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showConfigs) {
        RdrXConfigSheet(nodes, activeNode?.id, onConfigSelect, { showConfigs = false })
    }
}

@Composable
private fun RdrXRadar(
    status: RdrXStatus, nodes: List<RdrXNode>, activeId: String?,
    onTap: () -> Unit, contentDescription: String, modifier: Modifier
) {
    val p = LocalRdrXPalette.current
    val color = when (status) {
        RdrXStatus.OFFLINE -> p.offline
        RdrXStatus.CONNECTING, RdrXStatus.VERIFYING -> p.accent
        RdrXStatus.CONNECTED -> p.connected
        RdrXStatus.ERROR -> p.error
    }
    val scanning = status == RdrXStatus.CONNECTING || status == RdrXStatus.VERIFYING || status == RdrXStatus.CONNECTED
    val transition = rememberInfiniteTransition(label = "rdrx")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(
        tween(if (status == RdrXStatus.CONNECTING) 1100 else 3000, easing = LinearEasing)
    ), label = "sweep")
    val pulse by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    Box(
        modifier = modifier.shadow(18.dp, CircleShape, spotColor = color.copy(alpha = .25f))
            .clip(CircleShape).background(Brush.radialGradient(listOf(color.copy(alpha = .12f), p.background)))
            .border(1.dp, color.copy(alpha = .45f), CircleShape).clickable(onClick = onTap)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val c = center
            val r = size.minDimension * .46f
            for (i in 1..4) drawCircle(color.copy(alpha = if (status == RdrXStatus.OFFLINE) .18f else .35f),
                r * i / 4f, c, style = Stroke(1.dp.toPx()))
            drawLine(color.copy(alpha = .2f), Offset(c.x-r,c.y), Offset(c.x+r,c.y), 1.dp.toPx())
            drawLine(color.copy(alpha = .2f), Offset(c.x,c.y-r), Offset(c.x,c.y+r), 1.dp.toPx())
            if (scanning) rotate(angle, pivot = c) {
                drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, color.copy(alpha = .45f)), center = c), r, c)
            }
            nodes.take(40).forEachIndexed { i, node ->
                val ping = node.pingMs ?: return@forEachIndexed
                val a = Math.toRadians((i * 137.508).toDouble())
                val d = r * (ping / 350f).coerceIn(.12f, .92f)
                val point = Offset(c.x + cos(a).toFloat()*d, c.y + sin(a).toFloat()*d)
                val blip = when { ping <= 150 -> p.connected; ping <= 300 -> p.warning; else -> p.error }
                if (node.id == activeId) drawCircle(blip.copy(alpha = .2f*pulse), 12.dp.toPx(), point)
                drawCircle(blip, if (node.id == activeId) 4.5.dp.toPx() else 3.dp.toPx(), point)
            }
            drawCircle(color.copy(alpha = .12f*pulse), r*.23f, c)
            drawCircle(color, r*.23f, c, style = Stroke(1.5.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(when (status) {
                RdrXStatus.OFFLINE -> "NO"; RdrXStatus.CONNECTING -> "LINK"
                RdrXStatus.VERIFYING -> "TEST"; RdrXStatus.CONNECTED -> "LIVE"; RdrXStatus.ERROR -> "FAIL"
            }, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(when (status) {
                RdrXStatus.OFFLINE -> "SIGNAL"; RdrXStatus.CONNECTING -> "CONNECTING"
                RdrXStatus.VERIFYING -> "VERIFYING"; RdrXStatus.CONNECTED -> "TUNNEL"; RdrXStatus.ERROR -> "ROUTE"
            }, color = color, fontSize = 8.sp, letterSpacing = 1.5.sp)
        }
        Text("N", color = p.textDim, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        Text("S", color = p.textDim, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        Text("W", color = p.textDim, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
        Text("E", color = p.textDim, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
    }
}

@Composable
private fun RdrXArcMenu(items: List<RdrXMenuItem>, onSelect: (String) -> Unit) {
    val p = LocalRdrXPalette.current
    var open by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = .72f), label = "arc")
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        items.take(5).forEachIndexed { i, item ->
            val t = if (items.size <= 1) 0.5 else i.toDouble() / (items.size - 1)
            val a = Math.toRadians(180.0 + 180.0*t)
            Box(Modifier.offset((cos(a)*100*progress).dp, (sin(a)*100*progress).dp)
                .size(40.dp).graphicsLayer { alpha = progress; scaleX = progress; scaleY = progress }
                .clip(CircleShape).background(p.surface).border(1.dp,p.border,CircleShape)
                .clickable { open = false; onSelect(item.id) }, contentAlignment = Alignment.Center) {
                Text(item.label, color = p.text, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(p.glass).border(1.dp,p.border,CircleShape)
            .clickable { open = !open }, contentAlignment = Alignment.Center) {
            Text(if (open) "×" else "☰", color = p.accent, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RdrXConfigSheet(nodes: List<RdrXNode>, activeId: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalRdrXPalette.current
    var sort by remember { mutableStateOf(RdrXSort.PING) }
    val sorted = remember(nodes, sort) {
        when (sort) {
            RdrXSort.PING -> nodes.sortedWith(compareBy<RdrXNode>({ it.pingMs == null }, { it.pingMs ?: Int.MAX_VALUE }))
            RdrXSort.NAME -> nodes.sortedBy { it.country }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Text("کانفیگ‌ها", color = p.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RdrXSort.entries.forEach { option -> FilterChip(selected = sort == option, onClick = { sort = option }, label = { Text(option.label) }) }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(sorted, key = { it.id }) { node ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(node.id) }.padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (node.id == activeId) p.connected else p.accent))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(node.name, color = p.text, maxLines = 1, fontWeight = if (node.id == activeId) FontWeight.Bold else FontWeight.Normal)
                        Text("${node.country} · ${node.protocol}", color = p.textDim, fontSize = 10.sp, maxLines = 1)
                    }
                    Text(node.pingMs?.let { "${it} ms" } ?: "—", color = p.accent, fontFamily = FontFamily.Monospace)
                }
                HorizontalDivider(color = p.border, thickness = .5.dp)
            }
        }
    }
}

@Composable
private fun RdrXMetric(title: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalRdrXPalette.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(p.glass).border(1.dp,p.border,RoundedCornerShape(14.dp)).padding(11.dp)) {
        Text(title, color = p.textDim, fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RdrXAction(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalRdrXPalette.current
    Surface(modifier = modifier.height(68.dp).clickable(onClick = onClick), color = p.surface,
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp,p.border)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = p.textDim, fontSize = 10.sp)
        }
    }
}
