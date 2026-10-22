package com.aras.client.ui.main

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val FieldSignal = Color(0xFF9BCB72)
private val FieldPaper = Color(0xFFD8D0B8)
private val FieldMuted = Color(0xFF879486)
private val FieldRed = Color(0xFFE05249)

/** Theme-only home briefing. Connection state is supplied by the real VPN UI state. */
@Composable
fun SurvivalFieldPanel(
    isConnected: Boolean,
    serverTitle: String,
) {
    val statusColor = if (isConnected) FieldSignal else FieldRed
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1A281B), Color(0xFF0C120D), Color(0xFF111711))))
            .border(1.dp, FieldSignal.copy(alpha = 0.36f), RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AFTER THE FALL", color = FieldSignal, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black)
                Text("FIELD OPERATIONS  /  SECURE ACCESS", color = FieldMuted,
                    style = MaterialTheme.typography.labelSmall)
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(3.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .border(1.dp, statusColor.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isConnected) "LINK ACTIVE" else "NO TUNNEL",
                    color = statusColor, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(74.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val grid = Color(0xFF56704D).copy(alpha = 0.24f)
                val stepX = size.width / 12f
                val stepY = size.height / 5f
                for (i in 0..12) drawLine(grid, androidx.compose.ui.geometry.Offset(i * stepX, 0f),
                    androidx.compose.ui.geometry.Offset(i * stepX, size.height), 0.7f)
                for (i in 0..5) drawLine(grid, androidx.compose.ui.geometry.Offset(0f, i * stepY),
                    androidx.compose.ui.geometry.Offset(size.width, i * stepY), 0.7f)
                val route = Path().apply {
                    moveTo(size.width * 0.03f, size.height * 0.72f)
                    cubicTo(size.width * 0.18f, size.height * 0.8f, size.width * 0.2f, size.height * 0.18f, size.width * 0.37f, size.height * 0.45f)
                    cubicTo(size.width * 0.49f, size.height * 0.67f, size.width * 0.57f, size.height * 0.16f, size.width * 0.72f, size.height * 0.44f)
                    cubicTo(size.width * 0.84f, size.height * 0.68f, size.width * 0.9f, size.height * 0.28f, size.width * 0.98f, size.height * 0.4f)
                }
                drawPath(route, statusColor.copy(alpha = 0.78f), style = Stroke(width = 2.dp.toPx()))
                listOf(0.37f to 0.45f, 0.72f to 0.44f, 0.98f to 0.4f).forEach { (x, y) ->
                    drawCircle(statusColor.copy(alpha = 0.18f), 7.dp.toPx(),
                        androidx.compose.ui.geometry.Offset(size.width * x, size.height * y))
                    drawCircle(statusColor, 2.5.dp.toPx(),
                        androidx.compose.ui.geometry.Offset(size.width * x, size.height * y))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ACTIVE ROUTE", color = FieldMuted, style = MaterialTheme.typography.labelSmall)
            Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(statusColor))
            Text(
                text = serverTitle.ifBlank { if (isConnected) "Tunnel connected" else "Select a field route" },
                color = FieldPaper, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(if (isConnected) "ENCRYPTED" else "STANDBY",
                color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
