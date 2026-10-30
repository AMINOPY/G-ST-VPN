package com.aras.client.ui.rdrn

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aras.client.ui.main.MainAction
import com.aras.client.ui.main.MainDestination
import com.aras.client.ui.main.MainUiState
import com.aras.client.ui.rdr.RadarState
import com.aras.client.ui.rdr.RdrHomeState
import com.aras.client.ui.rdr.ServerUi
import com.aras.client.ui.rdr.TrafficUi
import com.aras.client.util.ConnectionStatsManager
import kotlinx.coroutines.delay

/**
 * Host for the "RADAR VPN" (rdrn) look: six tabs (Connect, Radar, Configs, Country, Speed, Menu) with an
 * animated bottom bar. The hamburger button in every header opens the Menu tab.
 *
 * Presentation only. Every action goes through the same [MainAction]s the other looks use, so connect,
 * testing, selection and import behave exactly as before. The full config manager (the app's normal list)
 * stays reachable through [onOpenList].
 *
 * Radar colour logic (same as the Glass look): off = idle, on + testing = connecting,
 * on + real ping through the tunnel = connected, on + failed test = danger.
 */
@Composable
fun RnMainHost(
    uiState: MainUiState,
    hasPing: Boolean,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = RnTab.entries[tabIndex]
    BackHandler(enabled = tabIndex != 0) { tabIndex = 0 }
    val go: (RnTab) -> Unit = { t -> tabIndex = t.ordinal }

    val isRunning = uiState.isRunning
    val selectedGuid = uiState.selectedGuid
    val hasPingNow by rememberUpdatedState(hasPing)
    val onActionNow by rememberUpdatedState(onAction)

    // Verify that traffic really passes the tunnel before showing green.
    var sawTesting by remember(isRunning, selectedGuid) { mutableStateOf(false) }
    var verifyFinished by remember(isRunning, selectedGuid) { mutableStateOf(false) }
    LaunchedEffect(uiState.isTesting, isRunning, selectedGuid) {
        if (uiState.isTesting) sawTesting = true else if (sawTesting) verifyFinished = true
    }
    LaunchedEffect(isRunning, selectedGuid) {
        if (!isRunning || selectedGuid == null) return@LaunchedEffect
        delay(1200)
        if (!hasPingNow) onActionNow(MainAction.TestCurrentServer)
        while (true) {
            delay(15_000)
            if (!hasPingNow) onActionNow(MainAction.TestCurrentServer)
        }
    }

    var snap by remember { mutableStateOf(ConnectionStatsManager.snapshot()) }
    LaunchedEffect(isRunning) {
        snap = ConnectionStatsManager.snapshot()
        while (isRunning) {
            delay(1000)
            snap = ConnectionStatsManager.snapshot()
        }
    }

    // Saved configs for Radar / Configs / Country. Reloaded whenever selection or test results can change.
    var servers by remember { mutableStateOf<List<RnServer>>(emptyList()) }
    LaunchedEffect(selectedGuid, uiState.isTesting, uiState.status, isRunning, hasPing, uiState.selectedGroupId, uiState.groups) {
        servers = RnData.loadServers()
    }

    val radar = when {
        !isRunning -> if (uiState.requestServiceStart) RadarState.CONNECTING else RadarState.IDLE
        hasPing -> RadarState.CONNECTED
        verifyFinished && !uiState.isTesting -> RadarState.DANGER
        else -> RadarState.CONNECTING
    }
    val selected = remember(servers, selectedGuid, uiState.isTesting) {
        servers.firstOrNull { it.id == selectedGuid }?.let {
            ServerUi(id = it.id, name = it.name, host = it.host, port = it.port, protocol = it.protocol, flag = it.flag, pingMs = it.pingMs, testing = uiState.isTesting)
        }
    }
    val home = RdrHomeState(
        radar = radar,
        selected = selected,
        traffic = TrafficUi(
            upBytesPerSec = if (isRunning) snap.sessionUp else 0,
            downBytesPerSec = if (isRunning) snap.sessionDown else 0,
            elapsedSec = if (isRunning) snap.sessionMillis / 1000 else 0,
        ),
    )
    val speedState = remember { RnSpeedState() }
    val openMenu = { go(RnTab.MENU) }
    val select: (String) -> Unit = { onAction(MainAction.SelectServer(it)) }

    RnBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(340)) { it / 6 * dir } + fadeIn(tween(340))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it / 6 * dir } + fadeOut(tween(160)))
                    },
                    label = "rn-tab",
                ) { t ->
                    when (t) {
                        RnTab.CONNECT -> RnHomeScreen(
                            state = home,
                            onToggle = { onAction(MainAction.ToggleService) },
                            onMenu = openMenu,
                            onSmartConnect = { onAction(MainAction.SmartConnect) },
                        )
                        RnTab.RADAR -> RnRadarScreen(servers, selectedGuid, select, openMenu)
                        RnTab.CONFIGS -> RnConfigsScreen(
                            servers = servers,
                            selectedId = selectedGuid,
                            onSelect = select,
                            onTest = { onAction(MainAction.SingleTestServer(it)) },
                            onAction = onAction,
                            onOpenManager = onOpenList,
                            onMenu = openMenu,
                        )
                        RnTab.COUNTRY -> RnCountryScreen(servers, selectedGuid, select, openMenu)
                        RnTab.SPEED -> RnSpeedScreen(speedState, isRunning, openMenu)
                        RnTab.MENU -> RnMenuScreen(
                            onTab = go,
                            onDestination = onNavigate,
                            onMenu = { go(RnTab.CONNECT) },
                        )
                    }
                }
            }
            RnBottomBar(tab, go)
        }
    }
}

/** Bottom bar with a gold pill that slides (with a little overshoot) to the selected tab. */
@Composable
private fun RnBottomBar(selected: RnTab, onSelect: (RnTab) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)
            .background(Color(0xEB050E09), shape)
            .border(1.dp, RnColors.Gold.copy(alpha = 0.5f), shape)
            .clip(shape).padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        val cell = maxWidth / RnTab.entries.size
        val x = animateFloatAsState(
            selected.ordinal.toFloat(),
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "pill",
        ).value
        Box(
            Modifier.offset(x = cell * x).width(cell).height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(RnColors.Gold.copy(alpha = 0.22f), RnColors.Gold.copy(alpha = 0.05f))))
                .border(1.dp, RnColors.Gold.copy(alpha = 0.8f), RoundedCornerShape(14.dp)),
        )
        Row(Modifier.fillMaxWidth()) {
            RnTab.entries.forEach { t ->
                val on = t == selected
                val pop = animateFloatAsState(if (on) 1.18f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "pop").value
                Column(
                    Modifier.width(cell).height(52.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, t.label, tint = if (on) RnColors.Gold else Color(0xFFD9E2DC), modifier = Modifier.size(22.dp).scale(pop))
                    Text(t.label, color = if (on) RnColors.Gold else Color(0xFFD9E2DC), fontSize = 10.5.sp, maxLines = 1)
                }
            }
        }
    }
}
