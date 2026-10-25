package com.aras.client.ui.rdr

import androidx.compose.runtime.Immutable
import java.util.Locale

/** حالت رادار. فقط همین enum را از لایه‌ی VPN/ViewModel خودتان مپ کنید (به README نگاه کنید). */
enum class RadarState {
    /** متصل نیست → طوسی، «Not connect» */
    IDLE,

    /** در حال اتصال → کهربایی، جاروب سریع */
    CONNECTING,

    /** کانفیگ از تونل رد می‌شود → سبز + نقطه‌ی سبز، «Connect» */
    CONNECTED,

    /** خطا / از تونل رد نمی‌شود → قرمز، «Danger» */
    DANGER,
}

@Immutable
data class ServerUi(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: String,
    val flag: String = "🌐",
    /** null = تست نشده ، -1 = تایم‌اوت/خطا */
    val pingMs: Int? = null,
    val groupId: String = "",
    val testing: Boolean = false,
)

@Immutable
data class GroupUi(val id: String, val title: String, val count: Int)

@Immutable
data class TrafficUi(
    val upBytesPerSec: Long = 0,
    val downBytesPerSec: Long = 0,
    val elapsedSec: Long = 0,
)

enum class ConfigSort { DEFAULT, PING, NAME }

enum class RdrDest {
    SUBSCRIPTIONS, PER_APP, ROUTING, GEO_FILES, EXPORT, SETTINGS,
    SMART_SCAN, LOGCAT, UPDATE, BACKUP, HYBRID, PROMOTION, ABOUT,
}

@Immutable
data class RdrHomeState(
    val radar: RadarState = RadarState.IDLE,
    val selected: ServerUi? = null,
    val traffic: TrafficUi = TrafficUi(),
)

class RdrHomeActions(
    val onRadarClick: () -> Unit = {},
    val onSmartConnect: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onMore: () -> Unit = {},
)

@Immutable
data class ConfigListState(
    val groups: List<GroupUi> = emptyList(),
    val servers: List<ServerUi> = emptyList(),
    val selectedServerId: String? = null,
)

class ConfigListActions(
    val onSelect: (id: String) -> Unit = {},
    val onTest: (ids: List<String>) -> Unit = {},
    val onTestAll: () -> Unit = {},
    val onDelete: (ids: List<String>) -> Unit = {},
    val onDuplicate: (ids: List<String>) -> Unit = {},
    /** آیکون ساعت در نوار ابزار قبلی؛ به همان عملکرد قبلی وصل کنید */
    val onClockAction: (ids: List<String>) -> Unit = {},
    val onEdit: (id: String) -> Unit = {},
    val onShare: (id: String) -> Unit = {},
    val onAddQr: () -> Unit = {},
    val onAddClipboard: () -> Unit = {},
    val onAddFile: () -> Unit = {},
    val onAddSubscription: () -> Unit = {},
    val onAddManual: () -> Unit = {},
)

/** ۱۲۳.۴۵.۶۷.۸۹ → ***.45.67.89 (مثل نمایش فعلی برنامه). برای غیر IPv4 دست نمی‌زند. */
fun maskHost(host: String): String {
    val p = host.split('.')
    return if (p.size == 4 && p.all { it.toIntOrNull() != null }) "***." + p.drop(1).joinToString(".") else host
}

fun formatSpeed(bytesPerSec: Long): String {
    val b = bytesPerSec.coerceAtLeast(0).toDouble()
    val loc = Locale.getDefault()
    return when {
        b >= 1024.0 * 1024.0 -> String.format(loc, "%.1f MB/s", b / (1024.0 * 1024.0))
        b >= 1024.0 -> String.format(loc, "%.1f KB/s", b / 1024.0)
        else -> String.format(loc, "%d B/s", b.toLong())
    }
}

fun formatElapsed(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}
