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
import android.os.SystemClock
import android.widget.RemoteViews
import com.aras.client.AppConfig
import com.aras.client.R
import com.aras.client.core.CoreServiceManager
import com.aras.client.core.LauncherManager
import com.aras.client.ui.main.MainActivity
import java.net.HttpURLConnection
import java.net.URL

/**
 * Large ELITE HYBRID home-screen widget.
 *
 * The service-running flag alone is not treated as a successful VPN connection:
 * the widget checks for Android's actual VPN transport and performs a small HTTP
 * request explicitly on that VPN Network before displaying a verified green state.
 */
class WidgetProvider : AppWidgetProvider() {

    private data class TunnelProbe(
        val tunnelFound: Boolean,
        val verified: Boolean,
        val exitIp: String? = null,
        val rttMs: Long? = null,
        val detail: String
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refreshWidgets(context, appWidgetIds, goAsync())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AppConfig.BROADCAST_ACTION_WIDGET_CLICK -> {
                // Keep the app's existing, permission-aware start/stop path.
                if (CoreServiceManager.isRunning()) {
                    LauncherManager.stopService(context)
                } else {
                    LauncherManager.startServiceFromToggle(context)
                }
                refreshWidgets(
                    context,
                    widgetIds(context),
                    goAsync()
                )
            }

            AppConfig.BROADCAST_ACTION_ACTIVITY -> {
                refreshWidgets(
                    context,
                    widgetIds(context),
                    goAsync()
                )
            }
        }
    }

    private fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))

    private fun refreshWidgets(
        context: Context,
        ids: IntArray,
        pendingResult: PendingResult
    ) {
        if (ids.isEmpty()) {
            pendingResult.finish()
            return
        }

        val running = CoreServiceManager.isRunning()
        render(context, ids, running, probe = null)

        Thread({
            try {
                val probe = if (running) probeVpnNetwork(context) else null
                // Re-read service state: it may have stopped while the probe was running.
                render(context, ids, CoreServiceManager.isRunning(), probe)
            } finally {
                pendingResult.finish()
            }
        }, "RadarVpnWidgetProbe").start()
    }

    private fun render(
        context: Context,
        ids: IntArray,
        isRunning: Boolean,
        probe: TunnelProbe?
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.widget_switch)

        val actionIntent = Intent(context, WidgetProvider::class.java).apply {
            action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
        }
        val actionPendingIntent = PendingIntent.getBroadcast(
            context,
            R.id.widget_action,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_action, actionPendingIntent)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            R.id.layout_switch,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.layout_switch, openAppPendingIntent)
        // The child action has its own PendingIntent and remains the explicit toggle.

        val status: String
        val detail: String
        val exitIp: String
        val rtt: String
        val statusColor: Int
        if (!isRunning) {
            status = "NO SIGNAL"
            detail = "VPN service is disconnected"
            exitIp = "Tunnel egress IP: —"
            rtt = "RTT  —"
            statusColor = 0xFFFF525C.toInt()
        } else if (probe == null) {
            status = "CHECKING TUNNEL"
            detail = "Checking Android VPN network and tunnel egress"
            exitIp = "Tunnel egress IP: checking…"
            rtt = "RTT  —"
            statusColor = 0xFFFFD36A.toInt()
        } else if (probe.tunnelFound && probe.verified) {
            status = "TUNNEL VERIFIED"
            detail = "Traffic request completed over the VPN network"
            exitIp = "Tunnel egress IP: ${probe.exitIp ?: "verified"}"
            rtt = "RTT  ${probe.rttMs ?: 0} ms"
            statusColor = 0xFF63F28D.toInt()
        } else if (probe.tunnelFound) {
            status = "DANGER · NO EGRESS"
            detail = probe.detail
            exitIp = "Tunnel egress IP: not verified"
            rtt = "RTT  —"
            statusColor = 0xFFFF2938.toInt()
        } else {
            status = "DANGER · TUNNEL LOST"
            detail = "Service is running, but Android reports no VPN transport"
            exitIp = "Tunnel egress IP: unavailable"
            rtt = "RTT  —"
            statusColor = 0xFFFF2938.toInt()
        }

        views.setTextViewText(R.id.widget_status, status)
        views.setTextViewText(R.id.widget_detail, detail)
        views.setTextViewText(R.id.widget_ip, exitIp)
        views.setTextViewText(R.id.widget_rtt, rtt)
        views.setTextColor(R.id.widget_status, statusColor)
        views.setInt(R.id.widget_status_dot, "setBackgroundColor", statusColor)
        views.setInt(R.id.widget_radar_center_dot, "setBackgroundColor", statusColor)
        views.setImageViewResource(
            R.id.image_switch,
            if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
        )
        views.setTextViewText(R.id.widget_action_label, if (isRunning) "DISCONNECT" else "CONNECT")

        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun probeVpnNetwork(context: Context): TunnelProbe {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return TunnelProbe(false, false, detail = "Android connectivity service unavailable")

        val candidates = buildList {
            val active = connectivity.activeNetwork
            if (active != null && isVpnNetwork(connectivity, active)) add(active)
            connectivity.allNetworks.forEach { network ->
                if (network !in this && isVpnNetwork(connectivity, network)) add(network)
            }
        }

        if (candidates.isEmpty()) {
            return TunnelProbe(
                tunnelFound = false,
                verified = false,
                detail = "No Android VPN transport is active"
            )
        }

        // Each request is opened on a VPN Network object, never on the default network.
        val endpoints = listOf("https://api.ipify.org", "https://icanhazip.com/")
        for (network in candidates) {
            for (endpoint in endpoints) {
                val connection = try {
                    network.openConnection(URL(endpoint)) as? HttpURLConnection
                } catch (_: Exception) {
                    null
                } ?: continue

                val startedAt = SystemClock.elapsedRealtime()
                try {
                    connection.connectTimeout = 2500
                    connection.readTimeout = 2500
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "text/plain")
                    if (connection.responseCode !in 200..299) continue

                    val ip = connection.inputStream.bufferedReader().use { it.readText() }
                        .trim()
                        .take(64)
                    val looksLikeIp = ip.isNotBlank() &&
                        ip.matches(Regex("^[0-9a-fA-F:.]+$")) &&
                        (ip.contains('.') || ip.contains(':'))
                    if (looksLikeIp) {
                        return TunnelProbe(
                            tunnelFound = true,
                            verified = true,
                            exitIp = ip,
                            rttMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L),
                            detail = "Traffic request completed over the VPN network"
                        )
                    }
                } catch (_: Exception) {
                    // Try another endpoint on the same VPN network before reporting danger.
                } finally {
                    connection.disconnect()
                }
            }
        }

        return TunnelProbe(
            tunnelFound = true,
            verified = false,
            detail = "VPN transport exists, but tunnel egress could not be verified"
        )
    }

    private fun isVpnNetwork(connectivity: ConnectivityManager, network: Network): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
