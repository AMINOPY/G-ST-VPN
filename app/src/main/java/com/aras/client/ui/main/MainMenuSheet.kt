package com.aras.client.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aras.client.AppConfig
import com.aras.client.R
import com.aras.client.extension.ltrIsolated
import com.aras.client.util.Utils

enum class MainDestination(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    Hybrid(R.drawable.ic_hybrid_24dp, R.string.menu_sub_hybrid),
    AI(R.drawable.ic_ai_24dp, R.string.menu_sub_ai),
    Subscriptions(R.drawable.ic_subscriptions_24dp, R.string.title_sub_setting),
    PerAppProxy(R.drawable.ic_per_apps_24dp, R.string.per_app_proxy_settings),
    Routing(R.drawable.ic_routing_24dp, R.string.routing_settings_title),
    UserAssets(R.drawable.ic_file_24dp, R.string.title_user_asset_setting),
    Export(R.drawable.ic_share_24dp, R.string.title_export),
    Settings(R.drawable.ic_settings_24dp, R.string.title_settings),
    Promotion(R.drawable.ic_promotion_24dp, R.string.title_pref_promotion),
    Logcat(R.drawable.ic_logcat_24dp, R.string.title_logcat),
    CheckUpdate(R.drawable.ic_check_update_24dp, R.string.update_check_for_update),
    BackupRestore(R.drawable.ic_restore_24dp, R.string.title_configuration_backup_restore),
    About(R.drawable.ic_about_24dp, R.string.title_about)
}

private data class MenuTileStyle(
    val destination: MainDestination,
    val accent: Color,
    @StringRes val subtitleRes: Int,
)

private data class MenuSection(
    @StringRes val titleRes: Int,
    val items: List<MenuTileStyle>,
    /** The MANAGE row is the primary section — slightly larger tiles. */
    val prominent: Boolean = false,
)

@Composable
private fun menuSections(): List<MenuSection> {
    val sky = MaterialTheme.colorScheme.secondary
    val teal = MaterialTheme.colorScheme.primary
    val amber = MaterialTheme.colorScheme.tertiary
    val rose = Color(0xFFE11D48)
    val violet = Color(0xFF7C3AED)
    val blue = Color(0xFF2563EB)
    val green = Color(0xFF059669)
    val slate = MaterialTheme.colorScheme.onSurfaceVariant

    fun item(destination: MainDestination, accent: Color, subtitleRes: Int) =
        MenuTileStyle(destination, accent, subtitleRes)

    return listOf(
        MenuSection(
            R.string.menu_section_manage,
            listOf(
                item(MainDestination.Subscriptions, sky, R.string.menu_sub_subscriptions),
                item(MainDestination.PerAppProxy, violet, R.string.menu_sub_per_app),
                item(MainDestination.Routing, teal, R.string.menu_sub_routing),
            ),
            prominent = true
        ),
        MenuSection(
            R.string.menu_section_files_tools,
            listOf(
                item(MainDestination.UserAssets, amber, R.string.menu_sub_assets),
                item(MainDestination.Export, blue, R.string.menu_sub_export),
                item(MainDestination.Settings, slate, R.string.menu_sub_settings),
            )
        ),
        MenuSection(
            R.string.menu_section_system,
            listOf(
                item(MainDestination.Logcat, green, R.string.menu_sub_logcat),
                item(MainDestination.CheckUpdate, blue, R.string.menu_sub_check_update),
                item(MainDestination.BackupRestore, rose, R.string.menu_sub_backup),
            )
        ),
    )
}

/**
 * Fast menu: an instantly-opening bottom sheet with a colorful tile grid,
 * replacing the old slide-in navigation drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuSheet(
    show: Boolean,
    isRdrX: Boolean = false,
    onDismiss: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_aras_logo),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name).ltrIsolated(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.title_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isRdrX) {
                RdrXOrbitalMenuHeader(
                    onDismiss = onDismiss,
                    onNavigate = onNavigate
                )
            }
            Spacer(Modifier.height(14.dp))

            menuSections().forEach { section ->
                SectionHeader(title = stringResource(section.titleRes).uppercase())
                if (section.titleRes == R.string.menu_section_hybrid) {
                    section.items.forEach { item ->
                        HybridMenuCard(
                            title = stringResource(R.string.hybrid_title),
                            subtitle = stringResource(R.string.hybrid_route_badge),
                            iconRes = item.destination.iconRes,
                            accent = item.accent,
                            onClick = {
                                onDismiss()
                                onNavigate(item.destination)
                            }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        section.items.forEach { item ->
                            MenuTileCard(
                                iconRes = item.destination.iconRes,
                                title = stringResource(item.destination.labelRes),
                                subtitle = stringResource(item.subtitleRes),
                                accent = item.accent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onClick = {
                                    onDismiss()
                                    onNavigate(item.destination)
                                }
                            )
                        }
                    }
                    if (section.titleRes == R.string.menu_section_files_tools) {
                        Spacer(Modifier.height(8.dp))
                        MenuRow(
                            iconRes = MainDestination.AI.iconRes,
                            label = stringResource(MainDestination.AI.labelRes),
                            subtitle = stringResource(R.string.menu_sub_ai),
                            accent = Color(0xFF7C3AED),
                            onClick = {
                                onDismiss()
                                onNavigate(MainDestination.AI)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            // Contact & support
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ContactChip(
                    iconRes = R.drawable.ic_source_code_24dp,
                    label = "GitHub · ArasTey",
                    modifier = Modifier.weight(1f),
                    onClick = { Utils.openUri(context, AppConfig.APP_URL) }
                )
                ContactChip(
                    iconRes = R.drawable.ic_telegram_24dp,
                    label = "@imArasTey",
                    modifier = Modifier.weight(1f),
                    onClick = { Utils.openUri(context, AppConfig.TG_CHANNEL_URL) }
                )
            }
        }
    }
}

@Composable
private fun HybridMenuCard(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.09f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.17f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(29.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineSmall,
            color = accent
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(10.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MenuTileCard(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun MenuRow(
    @DrawableRes iconRes: Int,
    label: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MenuTile(
    tile: MenuTileStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tile.accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(tile.destination.iconRes),
            contentDescription = null,
            tint = tile.accent,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(tile.destination.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ContactChip(
    @DrawableRes iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
private fun RdrXOrbitalMenuHeader(
    onDismiss: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val accent = Color(0xFF39FF88)
    val orbit = Color(0xFF3DA9FF)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(Modifier.fillMaxWidth().height(142.dp)) {
            drawArc(
                color = orbit.copy(alpha = 0.48f),
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(size.width * 0.12f, -size.height * 0.28f),
                size = Size(size.width * 0.76f, size.height * 1.52f),
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawArc(
                color = accent.copy(alpha = 0.15f),
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(size.width * 0.2f, -size.height * 0.08f),
                size = Size(size.width * 0.6f, size.height * 1.24f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        val items = listOf(
            Triple(MainDestination.Logcat, "LOGS", -100f) to 52.dp,
            Triple(MainDestination.Subscriptions, "CONFIGS", -54f) to 23.dp,
            Triple(MainDestination.Settings, "SETTINGS", 0f) to 10.dp,
            Triple(MainDestination.Routing, "ROUTING", 54f) to 23.dp,
            Triple(MainDestination.CheckUpdate, "UPDATE", 100f) to 52.dp,
        )
        items.forEach { (item, vertical) ->
            val (destination, label, horizontal) = item
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = horizontal.dp, y = vertical)
                    .width(64.dp)
                    .clickable {
                        onDismiss()
                        onNavigate(destination)
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF122235))
                        .border(1.dp, orbit.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = label,
                        tint = if (destination == MainDestination.Settings) accent else orbit,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF0B1421))
                .border(1.dp, accent.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_aras_logo),
                contentDescription = "RDR X",
                modifier = Modifier.size(25.dp)
            )
        }
    }
}
