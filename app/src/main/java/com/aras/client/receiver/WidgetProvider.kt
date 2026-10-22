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

/**
 * ELITE HYBRID home-screen widget.
 *
 * The widget's palette is deliberately static and independent from the app's UI theme.
 * A green status is shown only after Android exposes a VPN transport and a request
 * routed over that VPN network succeeds. Service-running alone is never treated as
 * proof of a working tunnel.
 */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refreshAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AppConfig.BROADCAST_ACTION_WIDGET_CLICK -> {
                if (intent.getBooleanExtra(EXTRA_NEXT_CONFIG, false)) {
                    selectNextConfiguration(context)
                } else if (CoreServiceManager.isRunning()) {
                    LauncherManager.stopService(context)
                } else {
                    LauncherManager.startServiceFromToggle(context)
                }
                refreshAll(context)
            }
            AppConfig.BROADCAST_ACTION_ACTIVITY -> {
                // Re-read the live tunnel after every service state transition.
                refreshAll(context)
            }
        }
    }

    private fun selectNextConfiguration(context: Context) {
        val configs = MmkvManager.decodeAllServerList()
            .distinct()
            .filter { guid -> MmkvManager.decodeServerConfig(guid) != null }
        if (configs.isEmpty()) return

        val current = MmkvManager.getSelectServer()
        val currentIndex = configs.indexOf(current)
        val nextGuid = configs[(currentIndex + 1).mod(configs.size)]
        MmkvManager.setSelectServer(nextGuid)

        // Ask the existing service to reload the selected profile; do not simulate a
        // connection or force-stop the VPN. When disconnected, the next Connect uses it.
        if (CoreServiceManager.isRunning()) {
            LauncherManager.restartService(context)
        }
    }

    private fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
        if (ids.isEmpty()) return

        val running = CoreServiceManager.isRunning()
        val selectedGuid = MmkvManager.getSelectServer()
        val selected = selectedGuid?.let(MmkvManager::decodeServerConfig)
        val ping = selectedGuid?.let { MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis } ?: -1L

        // Render a conservative initial state immediately; the network verification below
        // updates it only if the Android VPN transport and VPN-routed request are confirmed.
        ids.forEach { id ->
            manager.updateAppWidget(id, buildViews(context, running, false, null, ping, selected?.remarks))
        }

        if (!running) return
        Thread({
            val result = verifyVpnEgress(context)
            val currentPing = MmkvManager.getSelectServer()
                ?.let { MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis } ?: -1L
            val currentName = MmkvManager.getSelectServer()
                ?.let(MmkvManager::decodeServerConfig)?.remarks
            val latestRunning = CoreServiceManager.isRunning()
            val latestIds = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
            latestIds.forEach { id ->
                AppWidgetManager.getInstance(context).updateAppWidget(
                    id,
                    buildViews(
                        context,
                        latestRunning,
                        result.verified && latestRunning,
                        result.ip,
                        currentPing,
                        currentName
                    )
                )
            }
        }, "radar-vpn-widget-check").start()
    }

    private fun buildViews(
        context: Context,
        serviceRunning: Boolean,
        tunnelVerified: Boolean,
        publicIp: String?,
        pingMs: Long,
        configName: String?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_switch)
        val connected = serviceRunning && tunnelVerified
        val danger = serviceRunning && !tunnelVerified

        views.setTextViewText(
            R.id.widget_status,
            when {
                connected -> "TUNNEL VERIFIED"
                danger -> "DANGER  /  NO SIGNAL"
                else -> "TUNNEL OFFLINE"
            }
        )
        views.setTextColor(
            R.id.widget_status,
            when {
                connected -> COLOR_GREEN
                danger -> COLOR_RED
                else -> COLOR_GREY
            }
        )
        views.setTextViewText(
            R.id.widget_rtt,
            if (pingMs > 0L) "RTT  ${pingMs} ms" else "RTT  —"
        )
        views.setTextViewText(
            R.id.widget_ip,
            when {
                connected && !publicIp.isNullOrBlank() -> "VPN egress IP: $publicIp"
                danger -> "VPN network present, traffic not verified"
                else -> "Tunnel egress IP: unavailable"
            }
        )
        views.setTextViewText(
            R.id.widget_detail,
            configName?.takeIf { it.isNotBlank() }?.let { "CONFIG  /  $it" }
                ?: "Select a configuration in RADAR VPN"
        )
        views.setTextViewText(R.id.widget_action_label, if (serviceRunning) "DISCONNECT" else "CONNECT")
        views.setInt(
            R.id.image_switch,
            "setImageResource",
            if (serviceRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
        )

        val toggleIntent = Intent(context, WidgetProvider::class.java).apply {
            action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
        }
        val togglePending = PendingIntent.getBroadcast(
            context, REQUEST_TOGGLE, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_action, togglePending)
        views.setOnClickPendingIntent(R.id.layout_switch, togglePending)

        val nextIntent = Intent(context, WidgetProvider::class.java).apply {
            action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
            putExtra(EXTRA_NEXT_CONFIG, true)
        }
        val nextPending = PendingIntent.getBroadcast(
            context, REQUEST_NEXT_CONFIG, nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_config_next, nextPending)
        return views
    }

    private data class EgressCheck(val verified: Boolean, val ip: String? = null)

    private fun verifyVpnEgress(context: Context): EgressCheck {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return EgressCheck(false)
        val networks: List<Network> = connectivity.allNetworks.filter { network ->
            connectivity.getNetworkCapabilities(network)?.let { caps ->
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true
        }
        if (networks.isEmpty()) return EgressCheck(false)

        val endpoints = listOf("https://api.ipify.org", "https://icanhazip.com/")
        for (network in networks) {
            for (endpoint in endpoints) {
                val connection = try {
                    network.openConnection(URL(endpoint)) as? HttpURLConnection
                } catch (_: Exception) {
                    null
                } ?: continue
                try {
                    connection.connectTimeout = 2500
                    connection.readTimeout = 2500
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "text/plain")
                    if (connection.responseCode !in 200..299) continue
                    val ip = connection.inputStream.bufferedReader().use { it.readText() }
                        .trim().take(64)
                    if (ip.isNotBlank() && ip.matches(Regex("^[0-9a-fA-F:.]+$")) &&
                        (ip.contains('.') || ip.contains(':'))
                    ) return EgressCheck(true, ip)
                } catch (_: Exception) {
                    // Try the next endpoint and VPN network.
                } finally {
                    connection.disconnect()
                }
            }
        }
        return EgressCheck(false)
    }

    companion object {
        private const val EXTRA_NEXT_CONFIG = "widget_next_config"
        private const val REQUEST_TOGGLE = 7101
        private const val REQUEST_NEXT_CONFIG = 7102
        private const val COLOR_GREEN = 0xFF8DE6A9.toInt()
        private const val COLOR_RED = 0xFFFF5964.toInt()
        private const val COLOR_GREY = 0xFF9AA4AF.toInt()
    }
}
