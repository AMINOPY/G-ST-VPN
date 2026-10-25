package com.aras.client.ui.rdr

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.aras.client.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Immutable
data class ArcItem(
    val dest: RdrDest,
    val icon: ImageVector,
    @StringRes val label: Int,
    val tint: Color,
)

object RdrMenuDefaults {
    /** ۴ مورد اول در حلقه‌ی داخلی، بقیه (۹ مورد) در حلقه‌ی بیرونی */
    val items: List<ArcItem> = listOf(
        ArcItem(RdrDest.SUBSCRIPTIONS, Icons.Rounded.RssFeed, R.string.rdr_menu_subscriptions, Color(0xFF0A84FF)),
        ArcItem(RdrDest.PER_APP, Icons.Rounded.Apps, R.string.rdr_menu_per_app, Color(0xFFBF5AF2)),
        ArcItem(RdrDest.ROUTING, Icons.Rounded.CallSplit, R.string.rdr_menu_routing, Color(0xFF30B0C7)),
        ArcItem(RdrDest.SETTINGS, Icons.Rounded.Settings, R.string.rdr_menu_settings, Color(0xFF8E8E93)),
        ArcItem(RdrDest.GEO_FILES, Icons.Rounded.Public, R.string.rdr_menu_geo, Color(0xFF34C759)),
        ArcItem(RdrDest.EXPORT, Icons.Rounded.IosShare, R.string.rdr_menu_export, Color(0xFF5E5CE6)),
        ArcItem(RdrDest.SMART_SCAN, Icons.Rounded.AutoAwesome, R.string.rdr_menu_smart_scan, Color(0xFFFF9F0A)),
        ArcItem(RdrDest.LOGCAT, Icons.Rounded.Terminal, R.string.rdr_menu_logcat, Color(0xFF64D2FF)),
        ArcItem(RdrDest.UPDATE, Icons.Rounded.SystemUpdate, R.string.rdr_menu_update, Color(0xFFFF375F)),
        ArcItem(RdrDest.BACKUP, Icons.Rounded.SettingsBackupRestore, R.string.rdr_menu_backup, Color(0xFFFF453A)),
        ArcItem(RdrDest.HYBRID, Icons.Rounded.Hub, R.string.rdr_menu_hybrid, Color(0xFF0A84FF)),
        ArcItem(RdrDest.PROMOTION, Icons.Rounded.Campaign, R.string.rdr_menu_promotion, Color(0xFFFFD60A)),
        ArcItem(RdrDest.ABOUT, Icons.Rounded.Info, R.string.rdr_menu_about, Color(0xFF8E8E93)),
    )
}

/**
 * منوی نیم‌دایره‌ای شبیه Apple Watch:
 * با لمس همبرگری، یک نیم‌دیسک شیشه‌ای از لبه‌ی صفحه باز می‌شود و آیکون‌ها (فقط آیکون)
 * از محل دکمه به صورت فنری روی دو کمان پخش می‌شوند. لمس هر آیکون، همان بخش را باز می‌کند.
 * هنگام نگه داشتن انگشت روی آیکون، نام آن وسط نیم‌دایره دیده می‌شود.
 */
@Composable
fun ArcMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    onSelect: (RdrDest) -> Unit,
    modifier: Modifier = Modifier,
    fromRight: Boolean = true,
    items: List<ArcItem> = RdrMenuDefaults.items,
) {
    BackHandler(enabled = open) { onDismiss() }

    val master = remember { Animatable(0f) }
    LaunchedEffect(open) {
        master.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = 260f))
    }
    if (!open && master.value < 0.002f) return

    val c = LocalRdrColors.current
    val density = LocalDensity.current
    var pressedLabel by remember { mutableStateOf<Int?>(null) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val bigR = 236.dp
        val cx = if (fromRight) w else 0.dp
        val cy = maxOf(h * 0.40f, 300.dp)
        val dirX = if (fromRight) -1f else 1f

        // محل دکمه‌ی همبرگری (مبدأ پرواز آیکون‌ها)
        val btnX = if (fromRight) w - RdrDims.topBarPadding - RdrDims.menuButton else RdrDims.topBarPadding
        val btnY = statusTop + (RdrDims.topBarHeight - RdrDims.menuButton) / 2
        val originX = btnX + RdrDims.menuButton / 2
        val originY = btnY + RdrDims.menuButton / 2

        // پرده‌ی تیره
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Color.Black.copy(alpha = (if (c.isDark) 0.46f else 0.24f) * master.value.coerceIn(0f, 1f)))
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        )

        // نیم‌دیسک شیشه‌ای
        Box(
            Modifier
                .absoluteOffset(cx - bigR, cy - bigR)
                .size(bigR * 2)
                .graphicsLayer {
                    val s = master.value.coerceAtLeast(0f)
                    scaleX = s
                    scaleY = s
                    alpha = master.value.coerceIn(0f, 1f)
                }
                .glass(CircleShape, strong = true, shadow = 28.dp)
                .drawBehind {
                    val ctr = Offset(size.width / 2f, size.height / 2f)
                    listOf(104.dp, 176.dp).forEach { r ->
                        drawCircle(c.separator, r.toPx(), ctr, style = Stroke(1.dp.toPx()))
                    }
                },
        )

        // نام آیتمِ در حال لمس (وسط نیم‌دایره)
        Box(
            Modifier
                .absoluteOffset(cx + 42.dp * dirX - 33.dp, cy - 20.dp)
                .width(66.dp)
                .size(width = 66.dp, height = 40.dp)
                .graphicsLayer { alpha = master.value.coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = pressedLabel ?: R.string.rdr_menu_title,
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                label = "arcLabel",
            ) { res ->
                Text(
                    stringResource(res),
                    color = if (res == R.string.rdr_menu_title) c.textSecondary else c.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }

        // آیکون‌ها روی دو کمان
        val inner = items.take(4)
        val outer = items.drop(4)
        fun place(list: List<ArcItem>, radius: Dp, spreadDeg: Float, start: Int): List<Triple<ArcItem, Int, Pair<Dp, Dp>>> =
            list.mapIndexed { i, item ->
                val deg = if (list.size == 1) 0f else -spreadDeg + 2f * spreadDeg * i / (list.size - 1)
                val rad = Math.toRadians(deg.toDouble())
                val x = cx + radius * (dirX * cos(rad).toFloat())
                val y = cy + radius * sin(rad).toFloat()
                Triple(item, start + i, x to y)
            }

        val placed = place(inner, 104.dp, 62f, 0) + place(outer, 176.dp, 76f, inner.size)
        placed.forEach { (item, index, pos) ->
            val size = if (index < inner.size) 54.dp else 50.dp
            ArcIconButton(
                item = item,
                index = index,
                size = size,
                targetX = pos.first,
                targetY = pos.second,
                originX = originX,
                originY = originY,
                density = density,
                master = { master.value },
                onPressed = { pressedLabel = if (it) item.label else null },
                onClick = { onSelect(item.dest) },
            )
        }

        // دکمه‌ی بستن دقیقاً روی همبرگری
        GlassIconButton(
            Icons.Rounded.Close,
            stringResource(R.string.rdr_back),
            onDismiss,
            modifier = Modifier
                .absoluteOffset(btnX, btnY)
                .graphicsLayer { alpha = master.value.coerceIn(0f, 1f) },
            size = RdrDims.menuButton,
            strong = true,
        )
    }
}

@Composable
private fun ArcIconButton(
    item: ArcItem,
    index: Int,
    size: Dp,
    targetX: Dp,
    targetY: Dp,
    originX: Dp,
    originY: Dp,
    density: androidx.compose.ui.unit.Density,
    master: () -> Float,
    onPressed: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    // پیشرفت هر آیکون مستقیماً از انیمیشن اصلی مشتق می‌شود (بدون Animatable جدا)،
    // پس نمایش آیکون‌ها به هیچ تأخیر یا افکت جانبی وابسته نیست.
    fun progress(): Float = ((master() - index * 0.03f) / 0.64f).coerceIn(0f, 1f)

    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    LaunchedEffect(pressed) { onPressed(pressed) }
    val hf = LocalHapticFeedback.current
    val fx = LocalRdrFx.current
    val label = stringResource(item.label)

    val ox = with(density) { originX.toPx() }
    val oy = with(density) { originY.toPx() }
    val tx = with(density) { targetX.toPx() }
    val ty = with(density) { targetY.toPx() }
    val half = with(density) { (size / 2).toPx() }

    Box(
        Modifier
            .absoluteOffset {
                val p = progress()
                IntOffset(
                    (lerp(ox, tx, p) - half).roundToInt(),
                    (lerp(oy, ty, p) - half).roundToInt(),
                )
            }
            .size(size)
            .graphicsLayer {
                val p = progress()
                val s = p * (if (pressed) 1.12f else 1f)
                scaleX = s
                scaleY = s
                alpha = p
            }
            // استیکر رنگیِ شیشه‌ای: گرادیان رنگ + هایلایت بالا + لبه‌ی روشن؛ آیکون سفید و همیشه خوانا
            .shadow(10.dp, CircleShape, clip = false, ambientColor = item.tint, spotColor = item.tint)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(item.tint.copy(alpha = 0.78f).compositeOver(Color.White), item.tint),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.38f),
                        0.5f to Color.Transparent,
                    ),
                )
            }
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClickLabel = label) {
                if (fx.haptics) hf.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(item.icon, label, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}
