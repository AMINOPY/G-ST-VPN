package com.aras.client.ui.rdr

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.R
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * فونت رادار. پیش‌فرض Monospace سیستم است.
 * برای ظاهر واقعی‌تر یک فونت OFL مثل «Share Tech Mono» یا «Orbitron» را در res/font بگذارید و بنویسید:
 *   val RadarFont = FontFamily(Font(R.font.share_tech_mono))
 */
val RadarFont: FontFamily = FontFamily.Monospace

/**
 * دکمه‌ی اتصال = رادار واقعی.
 * - IDLE: طوسی، ثابت، متن «Not connect»
 * - CONNECTING: کهربایی، جاروب سریع
 * - CONNECTED: سبز، جاروب نرم، نقطه‌ی سبز که با رد شدن جاروب روشن می‌شود و محو می‌شود، متن «Connect»
 * - DANGER: قرمز، جاروب تند + حلقه‌ی چشمک‌زن، متن «Danger»
 */
@Composable
fun RadarButton(
    state: RadarState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    subLabel: String? = null,
) {
    val colors = LocalRdrColors.current
    val fx = LocalRdrFx.current
    val hf = LocalHapticFeedback.current

    val target = when (state) {
        RadarState.IDLE -> colors.gray
        RadarState.CONNECTING -> colors.amber
        RadarState.CONNECTED -> colors.green
        RadarState.DANGER -> colors.red
    }
    val tone by animateColorAsState(target, tween(550), label = "tone")

    val speed by animateFloatAsState(
        when (state) {
            RadarState.IDLE -> 0f
            RadarState.CONNECTING -> 260f
            RadarState.CONNECTED -> 110f
            RadarState.DANGER -> 190f
        },
        tween(700),
        label = "speed",
    )
    val speedNow by rememberUpdatedState(speed)
    var sweep by remember { mutableFloatStateOf(0f) }

    // انتگرال‌گیری زاویه: سرعت جاروب بدون پرش بین حالت‌ها عوض می‌شود
    LaunchedEffect(fx.radarSweep) {
        if (!fx.radarSweep) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    sweep = (sweep + speedNow * dt) % 360f
                }
                last = now
            }
        }
    }

    val inf = rememberInfiniteTransition(label = "radar")
    val ping by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)),
        label = "ping",
    )
    val blink by inf.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(620, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "blink",
    )

    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.955f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "radarPress",
    )

    val label = when (state) {
        RadarState.IDLE -> stringResource(R.string.rdr_radar_idle)
        RadarState.CONNECTING -> stringResource(R.string.rdr_radar_connecting)
        RadarState.CONNECTED -> stringResource(R.string.rdr_radar_connect)
        RadarState.DANGER -> stringResource(R.string.rdr_radar_danger)
    }

    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f - 10.dp.toPx()
            val hair = 1.dp.toPx()

            // درخشش محیطی
            drawCircle(
                Brush.radialGradient(
                    listOf(tone.copy(alpha = if (state == RadarState.IDLE) 0.10f else 0.30f), Color.Transparent),
                    center = c,
                    radius = r * 1.28f,
                ),
                radius = r * 1.28f,
                center = c,
            )

            // حلقه‌ی پینگ بیرون‌زنده
            if (state == RadarState.CONNECTED || state == RadarState.CONNECTING) {
                drawCircle(
                    tone.copy(alpha = (1f - ping) * 0.38f),
                    radius = r * (0.60f + 0.40f * ping),
                    center = c,
                    style = Stroke(2.dp.toPx()),
                )
            }

            // صفحه‌ی رادار (همیشه تیره، مثل نمایشگر فسفری)
            val faceInner = lerp(Color(0xFF07090C), tone, 0.14f)
            val faceOuter = lerp(Color(0xFF020304), tone, 0.04f)
            drawCircle(
                Brush.radialGradient(listOf(faceInner, faceOuter), center = c, radius = r),
                radius = r,
                center = c,
            )

            // حلقه‌های هم‌مرکز
            for (i in 1..3) {
                drawCircle(tone.copy(alpha = 0.30f), radius = r * i / 3f, center = c, style = Stroke(hair))
            }
            // محورها
            val axis = tone.copy(alpha = 0.24f)
            drawLine(axis, Offset(c.x - r, c.y), Offset(c.x + r, c.y), hair)
            drawLine(axis, Offset(c.x, c.y - r), Offset(c.x, c.y + r), hair)
            val d = r * 0.7071f
            val diag = tone.copy(alpha = 0.10f)
            drawLine(diag, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), hair)
            drawLine(diag, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), hair)

            // درجه‌بندی لبه
            for (deg in 0 until 360 step 6) {
                val rad = Math.toRadians(deg.toDouble())
                val major = deg % 30 == 0
                val len = if (major) 9.dp.toPx() else 4.5.dp.toPx()
                val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
                drawLine(
                    tone.copy(alpha = if (major) 0.60f else 0.28f),
                    c + dir * (r - len),
                    c + dir * r,
                    hair,
                )
            }

            // جاروب
            if (state != RadarState.IDLE) {
                rotate(sweep, pivot = c) {
                    drawCircle(
                        Brush.sweepGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.72f to Color.Transparent,
                                1f to tone.copy(alpha = 0.58f),
                            ),
                            center = c,
                        ),
                        radius = r * 0.985f,
                        center = c,
                    )
                }
                val a = Math.toRadians(sweep.toDouble())
                drawLine(
                    tone.copy(alpha = 0.92f),
                    c,
                    c + Offset(cos(a).toFloat(), sin(a).toFloat()) * (r * 0.985f),
                    1.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // نقطه‌ی سبز کوچک: وقتی جاروب از رویش رد می‌شود روشن می‌شود و آرام محو می‌شود
            if (state == RadarState.CONNECTED) {
                val blipDeg = -52f
                val br = r * 0.58f
                val rad = Math.toRadians(blipDeg.toDouble())
                val pos = c + Offset(cos(rad).toFloat(), sin(rad).toFloat()) * br
                val delta = (((sweep - blipDeg) % 360f) + 360f) % 360f
                val energy = (1f - delta / 320f).coerceIn(0f, 1f).pow(1.6f)
                val alpha = 0.38f + 0.62f * energy
                val glowR = 15.dp.toPx() * (0.6f + 0.4f * energy)
                drawCircle(
                    Brush.radialGradient(listOf(tone.copy(alpha = alpha * 0.55f), Color.Transparent), pos, glowR),
                    radius = glowR,
                    center = pos,
                )
                drawCircle(tone.copy(alpha = alpha), radius = 3.6.dp.toPx(), center = pos)
            }

            // قرمز: حلقه‌ی هشدار چشمک‌زن
            if (state == RadarState.DANGER) {
                drawCircle(
                    tone.copy(alpha = blink * 0.75f),
                    radius = r * 0.985f,
                    center = c,
                    style = Stroke(3.dp.toPx()),
                )
            }

            // انعکاس شیشه‌ای روی صفحه
            drawCircle(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.17f),
                    0.45f to Color.Transparent,
                    start = Offset(c.x - r * 0.7f, c.y - r),
                    end = c,
                ),
                radius = r,
                center = c,
            )

            // قاب شیشه‌ای
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.60f),
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.85f),
                    ),
                    center = c,
                ),
                radius = r,
                center = c,
                style = Stroke(2.5.dp.toPx()),
            )
            drawCircle(tone.copy(alpha = 0.55f), radius = r + 3.dp.toPx(), center = c, style = Stroke(hair))
        }

        // متن وسط رادار
        Column(
            Modifier.padding(horizontal = size * 0.2f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Crossfade(targetState = label, animationSpec = tween(350), label = "radarLabel") { text ->
                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (state == RadarState.DANGER) blink else 1f
                    },
                    style = TextStyle(
                        color = tone,
                        fontFamily = RadarFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.074f).sp,
                        letterSpacing = 2.sp,
                        shadow = Shadow(tone.copy(alpha = 0.9f), blurRadius = 16f),
                    ),
                )
            }
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = tone.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }
        }

        // ناحیه‌ی لمس دایره‌ای
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .clickable(interactionSource = src, indication = null, role = Role.Button) {
                    if (fx.haptics) hf.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
        )
    }
}
