package com.aras.client.ui.rdr

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * سطح شیشه‌ای (Liquid Glass شبیه iOS 26):
 * پرکننده‌ی نیمه‌شفاف + هایلایت لبه‌ی بالا + حاشیه‌ی گرادیانی + سایه‌ی نرم.
 * پس‌زمینه‌ی برنامه خودش گرادیان/بلاب نرم است، پس بدون کتابخانه هم حالت «مات‌شیشه‌ای» می‌دهد.
 * اگر بعداً خواستید بلور واقعی (Haze یا Backdrop) اضافه کنید، فقط همین یک تابع را عوض کنید.
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(28.dp),
    strong: Boolean = false,
    tint: Color = Color.Unspecified,
    shadow: Dp = 14.dp,
): Modifier {
    val c = LocalRdrColors.current
    val fill = if (strong) c.glassFillStrong else c.glassFill
    val topFill = fill.copy(alpha = (fill.alpha * 1.25f).coerceAtMost(0.9f))
    var m: Modifier = this
    if (shadow > 0.dp) {
        m = m.shadow(shadow, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
    }
    m = m.clip(shape).background(Brush.verticalGradient(listOf(topFill, fill)))
    if (tint.isSpecified) m = m.background(tint.copy(alpha = 0.16f))
    return m
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(c.strokeHi, c.strokeLo, c.strokeHi.copy(alpha = c.strokeHi.alpha * 0.55f)),
            ),
            shape,
        )
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (c.isDark) 0.07f else 0.30f),
                    0.28f to Color.Transparent,
                ),
            )
        }
}

/** کلیک با انیمیشن فنری + لرزش لمسی (اگر در تنظیمات روشن باشد). */
@Composable
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    haptic: Boolean = false,
    pressedScale: Float = 0.94f,
    onClick: () -> Unit,
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "press",
    )
    val hf = LocalHapticFeedback.current
    val fx = LocalRdrFx.current
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, enabled = enabled) {
            if (haptic && fx.haptics) hf.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
}

/** پس‌زمینه‌ی گرادیانی با بلاب‌های رنگی متحرک؛ همه‌ی سطح‌های شیشه روی این می‌نشینند. */
@Composable
fun RdrBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalRdrColors.current
    val fx = LocalRdrFx.current
    val t: State<Float> = if (fx.animatedBackground) {
        rememberInfiniteTransition(label = "bg").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
            label = "bgT",
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val p = t.value
            val a = if (c.isDark) 0.55f else 0.50f
            fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = a), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
            blob(c.blobA, w * (0.15f + 0.25f * p), h * (0.12f + 0.06f * p), w * 0.75f)
            blob(c.blobB, w * (0.95f - 0.30f * p), h * (0.45f + 0.08f * p), w * 0.70f)
            blob(c.blobC, w * (0.20f + 0.20f * p), h * (0.92f - 0.10f * p), w * 0.80f)
        }
        content()
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Color.Unspecified,
    strong: Boolean = false,
) {
    val c = LocalRdrColors.current
    Box(
        modifier
            .bouncyClick(haptic = true, onClick = onClick)
            .size(size)
            .glass(CircleShape, strong = strong, shadow = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = if (tint.isSpecified) tint else c.text,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** آیکون بدون پس‌زمینه برای داخل کپسول‌ها و نوارهای شیشه‌ای */
@Composable
fun PlainIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 38.dp,
    enabled: Boolean = true,
) {
    val c = LocalRdrColors.current
    val base = if (tint.isSpecified) tint else c.text
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .bouncyClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = base.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun GlassCapsule(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .glass(CircleShape, shadow = 8.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
