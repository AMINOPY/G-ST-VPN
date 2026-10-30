package com.aras.client.ui.rdrn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.ui.main.MainDestination

/** The six main tabs, in bottom-bar order. */
enum class RnTab(val label: String, val icon: ImageVector) {
    CONNECT("Connect", Icons.Rounded.PowerSettingsNew),
    RADAR("Radar", Icons.Rounded.Radar),
    CONFIGS("Configs", Icons.Rounded.Description),
    COUNTRY("Country", Icons.Rounded.Public),
    SPEED("Speed", Icons.Rounded.Speed),
    MENU("Menu", Icons.Rounded.Menu),
}

/**
 * Tab 6 and the hamburger target. Same sections as the bottom bar, plus Settings / Help / About,
 * plus a collapsible "More tools" group that keeps every other screen of the app reachable
 * (subscriptions, routing, per-app proxy, backup …). Nothing is removed from the app.
 */
@Composable
fun RnMenuScreen(
    onTab: (RnTab) -> Unit,
    onDestination: (MainDestination) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val extra = listOf(
        MainDestination.Subscriptions, MainDestination.Routing, MainDestination.PerAppProxy, MainDestination.UserAssets,
        MainDestination.Hybrid, MainDestination.AI, MainDestination.Export, MainDestination.BackupRestore,
        MainDestination.Logcat, MainDestination.CheckUpdate,
    )
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp)) {
        RnHeader("Menu", onMenu, Modifier.rnRise(0))
        Row(
            Modifier.fillMaxWidth().rnRise(1).rnCard().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).border(2.dp, RnColors.Gold, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Radar, null, tint = RnColors.Gold, modifier = Modifier.size(26.dp))
            }
            Column {
                Text("RADAR VPN", color = RnColors.Gold, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                Text("Fast • Secure • Freedom", color = Color(0xFFA9BDB0), fontSize = 11.5.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        val tabs = listOf(
            Triple(RnTab.CONNECT, Icons.Rounded.Bolt, "Quick connect to the best server"),
            Triple(RnTab.RADAR, Icons.Rounded.Radar, "Scan and find the best configs"),
            Triple(RnTab.CONFIGS, Icons.Rounded.Description, "Manage and search configs"),
            Triple(RnTab.COUNTRY, Icons.Rounded.Public, "Filter by country and IP"),
            Triple(RnTab.SPEED, Icons.Rounded.Speed, "Speed test, network and SIM info"),
        )
        tabs.forEachIndexed { i, (tab, icon, sub) ->
            MenuRow(icon, tab.label, sub, RnColors.Gold, { onTab(tab) }, Modifier.rnRise(2 + i))
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(4.dp))
        MenuRow(Icons.Rounded.Settings, "Settings", "App preferences", RnColors.Green, { onDestination(MainDestination.Settings) }, Modifier.rnRise(8))
        Spacer(Modifier.height(8.dp))
        MenuRow(Icons.AutoMirrored.Rounded.HelpOutline, "Help & Support", "Get help or contact us", RnColors.Green, { onDestination(MainDestination.Promotion) }, Modifier.rnRise(9))
        Spacer(Modifier.height(8.dp))
        MenuRow(Icons.Rounded.Info, "About", "RADAR VPN", RnColors.Green, { onDestination(MainDestination.About) }, Modifier.rnRise(10))
        Spacer(Modifier.height(8.dp))
        MenuRow(
            if (moreOpen) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
            "More tools", "Subscriptions, routing, per-app proxy, backup…", RnColors.Green, { moreOpen = !moreOpen }, Modifier.rnRise(11),
        )
        AnimatedVisibility(moreOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                extra.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().rnCard(tint = RnColors.Green, borderAlpha = 0.25f).clickable { onDestination(d) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(d.iconRes), null, tint = RnColors.Green, modifier = Modifier.size(20.dp))
                        Text(stringResource(d.labelRes), color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFFCFD8D2), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, sub: String, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().rnCard(tint = tint, borderAlpha = 0.35f).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.06f)).border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Color(0xFFA9BDB0), fontSize = 11.5.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFFCFD8D2), modifier = Modifier.size(20.dp))
    }
}
