package com.aras.client.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.widget.RemoteViews
import com.aras.client.AppConfig
import com.aras.client.R
import com.aras.client.core.CoreServiceManager
import com.aras.client.core.LauncherManager
import com.aras.client.handler.MmkvManager
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * ELITE HYBRID widget. UI colors are intentionally hard-coded here so the widget's
 * appearance does not change when the user changes the in-app UI theme.
 */
class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        renderAll(context, appWidgetManager, appWidgetIds, checking = true)
        verifyExecutor.execute {
            val serviceRunning = runCatching { CoreServiceManager.isRunning() }.getOrDefault(false)
            val egressIp = if (serviceRunning) verifyVpnEgress(context) else null
            renderAll(
                context,
                appWidgetManager,
                appWidgetIds,
                checking = false,
                serviceRunning = serviceRunning,
                verifiedTunnel = !egressIp.isNullOrBlank(),
                egressIp = egressIp
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE, AppConfig.BROADCAST_ACTION_WIDGET_CLICK -> {
                if (runCatching { CoreServiceManager.isRunning() }.getOrDefault(false)) {
                    LauncherManager.stopService(context)
                } else {
                    LauncherManager.startServiceFromToggle(context)
                }
                refresh(context)
            }
            ACTION_PREVIOUS_CONFIG -> selectAdjacentConfig(context, -1)
            ACTION_NEXT_CONFIG -> selectAdjacentConfig(context, 1)
            AppConfig.BROADCAST_ACTION_ACTIVITY -> refresh(context)
        }
    }

    private fun selectAdjacentConfig(context: Context, direction: Int) {
        val configs = runCatching { MmkvManager.decodeAllServerList().distinct() }.getOrDefault(emptyList())
        if (configs.isEmpty()) {
            refresh(context)
            return
        }
        val current = MmkvManager.getSelectServer()
        val index = configs.indexOf(current)
        val nextIndex = if (index < 0) 0 else (index + direction + configs.size) % configs.size
        val nextGuid = configs[nextIndex]
        if (MmkvManager.decodeServerConfig(nextGuid) == null) {
            refresh(context)
            return
        }
        MmkvManager.setSelectServer(nextGuid)
        if (runCatching { CoreServiceManager.isRunning() }.getOrDefault(false)) {
            LauncherManager.restartService(context)
        }
        refresh(context)
    }

    private fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
        onUpdate(context, manager, ids)
    }

    private fun renderAll(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        checking: Boolean,
        serviceRunning: Boolean = false,
        verifiedTunnel: Boolean = false,
        egressIp: String? = null
    ) {
        val selectedGuid = MmkvManager.getSelectServer()
        val profile = selectedGuid?.let(MmkvManager::decodeServerConfig)
        val affiliation = selectedGuid?.let(MmkvManager::decodeServerAffiliationInfo)
        val ping = affiliation?.testDelayMillis ?: -1L
        val configName = profile?.remarks?.takeIf { it.isNotBlank() } ?: profile?.server ?: "No configuration selected"
        val configPingOk = ping > 0L
        val status = when {
            checking -> "CHECKING TUNNEL"
            !serviceRunning -> "OFFLINE · NO TUNNEL"
            !verifiedTunnel -> "DANGER · TUNNEL NOT VERIFIED"
            !configPingOk -> "DANGER · NO CONFIG PING"
            else -> "TUNNEL VERIFIED"
        }
        val statusColor = when {
            checking -> 0xFFFFD36A.toInt()
            serviceRunning && verifiedTunnel && configPingOk -> 0xFF79F2A0.toInt()
            else -> 0xFFFF2938.toInt()
        }
        val detail = when {
            checking -> "Checking the Android VPN network"
            !serviceRunning -> "VPN service is not running"
            !verifiedTunnel -> "VPN service runs, but tunneled traffic could not be verified"
            !configPingOk -> "Tunnel egress works, but the selected profile has no successful ping"
            else -> "Tunnel egress and selected-profile ping are verified"
        }

        ids.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_switch)
            views.setTextViewText(R.id.widget_status, status)
            views.setTextColor(R.id.widget_status, statusColor)
            views.setInt(R.id.widget_status_dot, "setBackgroundColor", statusColor)
            views.setInt(R.id.widget_radar_center_dot, "setBackgroundColor", statusColor)
            views.setTextViewText(R.id.widget_detail, detail)
            views.setTextViewText(R.id.widget_config_name, configName)
            views.setTextViewText(
                R.id.widget_rtt,
                if (ping > 0L) "PING  ${ping} ms" else "PING  —"
            )
            views.setTextViewText(
                R.id.widget_ip,
                when {
                    checking -> "Tunnel egress IP: checking…"
                    verifiedTunnel -> "VPN egress IP: $egressIp"
                    else -> "Tunnel egress IP: unavailable"
                }
            )
            views.setImageViewResource(
                R.id.image_switch,
                if (serviceRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
            )
            views.setTextViewText(R.id.widget_action_label, if (serviceRunning) "DISCONNECT" else "CONNECT")
            views.setOnClickPendingIntent(R.id.widget_action, pending(context, ACTION_TOGGLE, widgetId * 10 + 1))
            views.setOnClickPendingIntent(R.id.layout_switch, pending(context, ACTION_TOGGLE, widgetId * 10 + 1))
            views.setOnClickPendingIntent(R.id.widget_config_prev, pending(context, ACTION_PREVIOUS_CONFIG, widgetId * 10 + 2))
            views.setOnClickPendingIntent(R.id.widget_config_next, pending(context, ACTION_NEXT_CONFIG, widgetId * 10 + 3))
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun pending(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun verifyVpnEgress(context: Context): String? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val networks = mutableListOf<Network>()
        connectivity.allNetworks.forEach { network ->
            val caps = connectivity.getNetworkCapabilities(network)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) networks.add(network)
        }
        for (network in networks) {
            val connection = try {
                network.openConnection(URL("https://api.ipify.org")) as? HttpURLConnection
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                connection.connectTimeout = 1800
                connection.readTimeout = 1800
                connection.requestMethod = "GET"
                if (connection.responseCode in 200..299) {
                    val ip = connection.inputStream.bufferedReader().use { it.readText().trim() }
                    if (ip.isNotBlank() && ip.matches(Regex("^[0-9a-fA-F:.]+$")) &&
                        (ip.contains('.') || ip.contains(':'))
                    ) return ip
                }
            } catch (_: Exception) {
                // A blocked endpoint must not be represented as a verified tunnel.
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    companion object {
        private const val ACTION_TOGGLE = "com.aras.client.widget.TOGGLE"
        private const val ACTION_PREVIOUS_CONFIG = "com.aras.client.widget.PREVIOUS_CONFIG"
        private const val ACTION_NEXT_CONFIG = "com.aras.client.widget.NEXT_CONFIG"
        private val verifyExecutor = Executors.newSingleThreadExecutor()
    }
}
