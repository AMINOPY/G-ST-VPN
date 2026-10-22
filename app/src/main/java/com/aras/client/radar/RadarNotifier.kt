package com.aras.client.radar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.aras.client.R
import com.aras.client.ui.main.MainActivity

/**
 * Emits short command alerts. The ongoing foreground notification remains owned by
 * the existing handler.NotificationManager and keeps RAY_NG_CHANNEL_ID + its actions.
 */
class RadarNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(AndroidNotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var connected = false
    private var hasConnectedBefore = false
    private var suppressNextDisconnect = false
    private var pendingLoss: Runnable? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_COMMAND,
                    appContext.getString(R.string.radar_channel_command),
                    AndroidNotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    setShowBadge(false)
                }
            )
        }
    }

    @Synchronized
    fun onTunnelConnected() {
        val hadPendingLoss = pendingLoss != null
        pendingLoss?.let(handler::removeCallbacks)
        pendingLoss = null
        val event = when {
            hadPendingLoss || (hasConnectedBefore && !connected) -> R.string.radar_cmd_restored
            !hasConnectedBefore -> R.string.radar_cmd_established
            else -> null
        }
        connected = true
        hasConnectedBefore = true
        event?.let { showCommand(it, GREEN) }
    }

    @Synchronized
    fun onTunnelDisconnected() {
        if (suppressNextDisconnect) {
            suppressNextDisconnect = false
            connected = false
            return
        }
        if (!connected || pendingLoss != null) return
        val task = Runnable {
            synchronized(this) {
                pendingLoss = null
                if (connected) return@synchronized
                connected = false
                showCommand(R.string.radar_cmd_lost, RED)
            }
        }
        pendingLoss = task
        handler.postDelayed(task, LOSS_GRACE_MS)
        connected = false
    }

    @Synchronized
    fun onManualStop() {
        val hadActiveOrPendingConnection = connected || pendingLoss != null
        pendingLoss?.let(handler::removeCallbacks)
        pendingLoss = null
        connected = false
        suppressNextDisconnect = hadActiveOrPendingConnection
        if (hadActiveOrPendingConnection) {
            showCommand(R.string.radar_cmd_manual_stop, GRAY)
        }
    }

    @Synchronized
    fun release() {
        pendingLoss?.let(handler::removeCallbacks)
        pendingLoss = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun showCommand(messageRes: Int, color: Int) {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_COMMAND)
            .setSmallIcon(R.drawable.ic_radar)
            .setColor(color)
            .setContentTitle(appContext.getString(R.string.radar_title_command))
            .setContentText(appContext.getString(messageRes))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(COMMAND_TIMEOUT_MS)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(COMMAND_NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_COMMAND = "radar_command"
        private const val COMMAND_NOTIFICATION_ID = 2402
        private const val CONTENT_REQUEST_CODE = 2403
        private const val LOSS_GRACE_MS = 3_000L
        private const val COMMAND_TIMEOUT_MS = 5_000L
        private val GREEN = Color.rgb(0, 255, 156)
        private val RED = Color.rgb(255, 59, 92)
        private val GRAY = Color.rgb(107, 122, 143)
    }
}
