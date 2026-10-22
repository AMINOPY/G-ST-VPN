package com.aras.client.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import com.aras.client.ui.compose.ThemeManager

enum class MainDestination(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
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
    onDismiss: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val uiTheme by ThemeManager.uiTheme.collectAsState()

    if (uiTheme == "survival") {
        SurvivalMenuSheet(
            onDismiss = onDismiss,
            onNavigate = onNavigate,
            sheetState = sheetState,
            context = context
        )
        return
    }

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
            Spacer(Modifier.height(14.dp))

            menuSections().forEach { section ->
                SectionHeader(title = stringResource(section.titleRes).uppercase())
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
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurvivalMenuSheet(
    onDismiss: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
    sheetState: androidx.compose.material3.SheetState,
    context: android.content.Context,
) {
    val signal = Color(0xFF9BCB72)
    val paper = Color(0xFFD8D0B8)
    val muted = Color(0xFF8D9986)
    val panel = Color(0xFFD8D0B8)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF090E0B),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50)).background(signal.copy(alpha = 0.65f))
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFE7DFC5), Color(0xFFD8D0B8), Color(0xFFC8BEA1))))
                    .border(1.dp, Color(0xFF80795F), RoundedCornerShape(5.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF354531)).border(1.dp, Color(0xFF6F765F), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_aras_logo), contentDescription = null,
                        tint = signal, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("AFTER THE FALL", color = Color(0xFF354531), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black)
                    Text("FIELD TERMINAL  //  RADAR VPN", color = Color(0xFF343A2E),
                        style = MaterialTheme.typography.labelSmall)
                    Text("FIELD MENU  •  ACCESS POINTS", color = Color(0xFF5F6554), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("FIELD NOTES  /  OPERATIONS", color = paper, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
            HorizontalDivider(color = Color(0xFF6A765C))
            Spacer(Modifier.height(4.dp))
            menuSections().forEach { section ->
                Text(
                    text = stringResource(section.titleRes).uppercase(),
                    color = paper,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 3.dp)
                )
                section.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(panel)
                            .border(1.dp, Color(0xFF8D866C), RoundedCornerShape(5.dp))
                            .clickable {
                                onDismiss()
                                onNavigate(item.destination)
                            }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(4.dp))
                                .background(item.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(painterResource(item.destination.iconRes), contentDescription = null,
                                tint = if (item.destination == MainDestination.Settings) signal else item.accent,
                                modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(item.destination.labelRes), color = Color(0xFF20271E),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(item.subtitleRes), color = Color(0xFF59604F),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text("›", color = signal, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = signal.copy(alpha = 0.22f))
            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.height(8.dp))
            Text("STAY ALERT  •  TRUST THE TUNNEL STATUS", color = muted,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
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
