package com.aras.client.ui.rdr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aras.client.handler.MmkvManager
import com.aras.client.ui.compose.ThemeManager
import com.aras.client.ui.main.MainAction
import com.aras.client.ui.main.MainDestination
import com.aras.client.ui.main.MainMoreMenuAction
import com.aras.client.ui.main.MainUiState
import com.aras.client.util.ConnectionStatsManager
import com.aras.client.util.CountryResolver
import kotlinx.coroutines.delay

/**
 * آداپتور تم «RADAR Glass» (کلید ذخیره‌شده‌ی "rdrx" در ThemeManager.uiTheme) به منطق فعلی برنامه.
 *
 * فقط نمایش است: همه‌ی عملیات از طریق همان MainAction ها انجام می‌شود و هیچ قابلیتی عوض نمی‌شود.
 * صفحه‌ی «لیست کانفیگ‌ها» همان لیست فعلی برنامه است (MainScreen) و با [onOpenList] باز می‌شود.
 *
 * منطق رنگ رادار:
 *  - سرویس خاموش            → IDLE      (طوسی، Not connect)
 *  - سرویس روشن، تست جاری   → CONNECTING(کهربایی)
 *  - سرویس روشن و پینگِ واقعی از تونل مثبت → CONNECTED (سبز + نقطه، Connect)
 *  - سرویس روشن ولی تست از تونل شکست خورد → DANGER (قرمز، Danger)
 * بعد از اتصال، یک تست «پینگ کانفیگ فعلی» خودکار اجرا می‌شود (و اگر شکست خورد هر ۱۵ ثانیه دوباره)،
 * چون رنگ سبز باید فقط وقتی باشد که اینترنت واقعاً از تونل رد می‌شود.
 */
@Composable
fun RdrMainHost(
    uiState: MainUiState,
    hasPing: Boolean,
    isDark: Boolean,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenList: () -> Unit,
    onOpenSearch: () -> Unit,
    onMoreAction: (MainMoreMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeState = rememberRdrThemeState()
    val isRunning = uiState.isRunning
    val selectedGuid = uiState.selectedGuid
    var menuOpen by remember { mutableStateOf(false) }

    val hasPingNow by rememberUpdatedState(hasPing)
    val onActionNow by rememberUpdatedState(onAction)

    // ── تأیید واقعی تونل ──
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

    // ── ترافیک ──
    var snap by remember { mutableStateOf(ConnectionStatsManager.snapshot()) }
    LaunchedEffect(isRunning) {
        snap = ConnectionStatsManager.snapshot()
        while (isRunning) {
            delay(1000)
            snap = ConnectionStatsManager.snapshot()
        }
    }

    val radar = when {
        !isRunning -> if (uiState.requestServiceStart) RadarState.CONNECTING else RadarState.IDLE
        hasPing -> RadarState.CONNECTED
        verifyFinished && !uiState.isTesting -> RadarState.DANGER
        else -> RadarState.CONNECTING
    }

    val server = remember(selectedGuid, hasPing, uiState.status, uiState.isTesting) {
        selectedGuid?.let { guid ->
            val p = MmkvManager.decodeServerConfig(guid) ?: return@let null
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            val flag = CountryResolver
                .resolveCardCountry(p, aff?.countryCode.orEmpty(), aff?.countryCode)
                .ifBlank { "🌐" }
            val delay = aff?.testDelayMillis ?: 0L
            ServerUi(
                id = guid,
                name = p.remarks.ifBlank { p.server.orEmpty() },
                host = p.server.orEmpty(),
                port = p.serverPort?.toIntOrNull() ?: 0,
                protocol = p.configType.name,
                flag = flag,
                pingMs = when {
                    delay > 0 -> delay.toInt()
                    delay < 0 -> -1
                    else -> null
                },
                testing = uiState.isTesting,
            )
        }
    }

    val home = RdrHomeState(
        radar = radar,
        selected = server,
        traffic = TrafficUi(
            upBytesPerSec = if (isRunning) snap.sessionUp else 0,
            downBytesPerSec = if (isRunning) snap.sessionDown else 0,
            elapsedSec = if (isRunning) snap.sessionMillis / 1000 else 0,
        ),
    )
    val actions = RdrHomeActions(
        onRadarClick = { onAction(MainAction.ToggleService) },
        onSmartConnect = { onAction(MainAction.SmartConnect) },
    )

    RdrTheme(themeState, darkOverride = isDark) {
        RdrBackground(modifier) {
            RdrHomeScreen(
                state = home,
                actions = actions,
                onOpenConfigs = onOpenList,
                onMenu = { menuOpen = true },
                toolbar = {
                    RdrToolbarCapsule(
                        onSearch = onOpenSearch,
                        onImportAction = onAction,
                        onMoreAction = onMoreAction,
                        onSync = { onAction(MainAction.UpdateSubscriptions) },
                    )
                },
            )
            ArcMenu(
                open = menuOpen,
                onDismiss = { menuOpen = false },
                onSelect = { dest ->
                    menuOpen = false
                    onNavigate(dest.toMainDestination())
                },
                isDark = isDark,
                onToggleTheme = { ThemeManager.toggleRdrxAppearance(isDark) },
                fromRight = true,
            )
        }
    }
}

private fun RdrDest.toMainDestination(): MainDestination = when (this) {
    RdrDest.SUBSCRIPTIONS -> MainDestination.Subscriptions
    RdrDest.PER_APP -> MainDestination.PerAppProxy
    RdrDest.ROUTING -> MainDestination.Routing
    RdrDest.GEO_FILES -> MainDestination.UserAssets
    RdrDest.EXPORT -> MainDestination.Export
    RdrDest.SETTINGS -> MainDestination.Settings
    RdrDest.SMART_SCAN -> MainDestination.AI
    RdrDest.LOGCAT -> MainDestination.Logcat
    RdrDest.UPDATE -> MainDestination.CheckUpdate
    RdrDest.BACKUP -> MainDestination.BackupRestore
    RdrDest.HYBRID -> MainDestination.Hybrid
    RdrDest.PROMOTION -> MainDestination.Promotion
    RdrDest.ABOUT -> MainDestination.About
}
