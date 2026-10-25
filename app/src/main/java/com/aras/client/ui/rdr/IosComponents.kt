package com.aras.client.ui.rdr

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.R

/**
 * کامپوننت‌های iOS-مانند (گروه‌های inset، ردیف‌ها، سوییچ iOS، سگمنت) برای تمام صفحه‌های تنظیمات.
 * هر صفحه‌ی تنظیمات دیگر را هم می‌توانید با همین‌ها بسازید تا کل برنامه شبیه iOS شود.
 */
@Composable
fun IosScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val c = LocalRdrColors.current
    Column(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(RdrDims.topBarHeight)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (onBack != null) {
                GlassIconButton(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    stringResource(R.string.rdr_back),
                    onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            Text(
                title,
                color = c.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            content = content,
        )
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
fun IosSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalRdrColors.current
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                header,
                color = c.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(24.dp), shadow = 10.dp),
        ) { content() }
        if (footer != null) {
            Text(
                footer,
                color = c.textSecondary,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

@Composable
fun IosRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconBg: Color = Color(0xFF0A84FF),
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalRdrColors.current
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(iconBg, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 16.sp)
                if (subtitle != null) {
                    Text(subtitle, color = c.textSecondary, fontSize = 12.5.sp)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (showChevron) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    null,
                    tint = c.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .padding(start = if (icon != null) 60.dp else 16.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(c.separator),
            )
        }
    }
}

@Composable
fun IosToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconBg: Color = Color(0xFF0A84FF),
    showDivider: Boolean = true,
) {
    IosRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconBg = iconBg,
        showDivider = showDivider,
        modifier = modifier,
        trailing = { IosSwitch(checked, onChange) },
        onClick = { onChange(!checked) },
    )
}

/** سوییچ ۵۱×۳۱ مثل iOS با فنر روی دستگیره */
@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRdrColors.current
    val track by animateColorAsState(if (checked) c.green else c.gray.copy(alpha = 0.35f), label = "swTrack")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(0.6f, 600f), label = "swThumb")
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .size(width = 51.dp, height = 31.dp)
                .background(track, CircleShape)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        ) {
            Box(
                Modifier
                    .absoluteOffset(x = x, y = 2.dp)
                    .size(27.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

/** کنترل سگمنتی iOS با دستگیره‌ی لغزنده */
@Composable
fun IosSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRdrColors.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .glass(RoundedCornerShape(14.dp), shadow = 0.dp)
            .padding(3.dp),
    ) {
        val seg = maxWidth / options.size
        val offsetX by animateDpAsState(seg * selected, spring(0.75f, 500f), label = "segThumb")
        Box(
            Modifier
                .offset(x = offsetX)
                .width(seg)
                .fillMaxHeight()
                .shadow(3.dp, RoundedCornerShape(11.dp))
                .background(
                    if (c.isDark) Color.White.copy(alpha = 0.20f) else Color.White,
                    RoundedCornerShape(11.dp),
                ),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (i == selected) c.text else c.textSecondary,
                        fontSize = 13.5.sp,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
