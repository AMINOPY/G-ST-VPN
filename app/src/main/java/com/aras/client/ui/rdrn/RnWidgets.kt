package com.aras.client.ui.rdrn

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ping colour: green < 160 ms, gold < 230 ms, amber above; red for timeouts. */
fun rnPingColor(ms: Int?): Color = when {
    ms == null -> RnColors.Muted
    ms < 0 -> RnColors.Red
    ms < 160 -> RnColors.Green
    ms < 230 -> RnColors.Gold
    else -> RnColors.Amber
}

/** Four-bar signal indicator, [level] 0..4. */
@Composable
fun RnBars(level: Int, color: Color = RnColors.Green, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(5, 8, 11, 14).forEachIndexed { i, h ->
            Box(
                Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(1.dp))
                    .background(if (i < level) color else Color(0xFF244634)),
            )
        }
    }
}

/** Ping → 0..4 bars. */
fun rnPingLevel(ms: Int?): Int = when {
    ms == null || ms < 0 -> 0
    ms < 120 -> 4
    ms < 220 -> 3
    ms < 400 -> 2
    else -> 1
}

/** Header: hamburger on the left, title, optional trailing slot. */
@Composable
fun RnHeader(
    title: String,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    boxedMenu: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RnIconButton(Icons.Rounded.Menu, "Menu", onMenu, boxed = boxedMenu)
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun RnIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, boxed: Boolean = false, tint: Color = Color.White) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(36.dp)
            .then(if (boxed) Modifier.border(1.dp, RnColors.Gold.copy(alpha = 0.55f), shape).background(RnColors.Gold.copy(alpha = 0.06f), shape) else Modifier)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Brand title with a travelling gold shimmer. */
@Composable
fun RnBrand(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "brand")
    val x = t.animateFloat(-300f, 700f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "shim")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "RADAR VPN",
            style = TextStyle(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFB8860B), Color(0xFFFFE27A), Color(0xFFFFF6C8), Color(0xFFFFE27A), Color(0xFFB8860B)),
                    start = Offset(x.value, 0f), end = Offset(x.value + 280f, 0f),
                ),
                fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp,
            ),
        )
        Text("FAST · SECURE · FREEDOM", color = Color(0xFFCDD8D0), fontSize = 9.sp, letterSpacing = 3.sp)
    }
}

@Composable
fun RnChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val c = if (selected) RnColors.Green else RnColors.Gold.copy(alpha = 0.45f)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) RnColors.Green.copy(alpha = 0.10f) else Color(0xCC050E09))
            .border(BorderStroke(1.dp, c), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) RnColors.Green else Color(0xFFE8EFE9), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun RnBadge(text: String, good: Boolean) {
    val c = if (good) RnColors.Green else RnColors.Gold
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(c.copy(alpha = 0.08f)).border(1.dp, c, RoundedCornerShape(7.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
    ) { Text(text, color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun RnSearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().height(42.dp).clip(shape).background(Color(0xCC050E09)).border(1.dp, RnColors.Gold.copy(alpha = 0.55f), shape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Color(0xFF8FA699), fontSize = 14.sp)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(RnColors.Green),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Flag emoji in a fixed square so rows line up. */
@Composable
fun RnFlag(flag: String, size: Dp = 30.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) { Text(flag.ifBlank { "🌐" }, fontSize = (size.value * 0.78f).sp) }
}

@Composable
fun RnSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = RnColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, modifier = modifier.padding(start = 2.dp, top = 4.dp))
}

fun circleModifier(size: Dp, border: Color): Modifier = Modifier.size(size).clip(CircleShape).border(1.dp, border, CircleShape)
