package com.aras.client.ui.rdrn

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.aras.client.handler.MmkvManager
import com.aras.client.handler.SpeedtestManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

enum class RnPhase { IDLE, PING, DOWN, UP, DONE, ERROR }

/** What the user's real (non-VPN) connection looks like right now. */
data class RnNetInfo(
    val connection: String = "Checking…",
    val shortName: String = "",
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val signal: Int = 0,
    val operatorName: String = "—",
    val simLabel: String = "—",
    val extraLabel: String = "Link",
    val extraValue: String = "—",
    val hasPhonePermission: Boolean = true,
    val vpnActive: Boolean = false,
)

object RnNet {
    /** Reads the physical network (the one underneath the VPN) plus the SIM that carries mobile data. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun read(ctx: Context): RnNetInfo {
        val app = ctx.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return RnNetInfo(connection = "Unknown")
        var best: NetworkCapabilities? = null
        var vpn = false
        for (n in cm.allNetworks) {
            val c = cm.getNetworkCapabilities(n) ?: continue
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) { vpn = true; continue }
            if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (best == null || (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && !best.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))) best = c
        }
        val wifi = best?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cell = best?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val eth = best?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val hasPhone = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        // ---- SIM / operator (default data SIM) ----
        var operatorName = "No SIM"
        var simLabel = "—"
        var generation = "Mobile"
        var signal = 0
        var roaming = false
        try {
            val tm0 = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm0 != null) {
                val subId = SubscriptionManager.getDefaultDataSubscriptionId()
                val tm = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) tm0.createForSubscriptionId(subId) else tm0
                val mccMnc = tm.simOperator?.takeIf { it.length >= 5 } ?: tm.networkOperator?.takeIf { it.length >= 5 }
                val name = RnUtil.iranOperator(mccMnc) ?: tm.simOperatorName?.takeIf { it.isNotBlank() } ?: tm.networkOperatorName?.takeIf { it.isNotBlank() }
                if (name != null) operatorName = name
                var slot = -1
                if (Build.VERSION.SDK_INT >= 29 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) slot = SubscriptionManager.getSlotIndex(subId)
                simLabel = buildString {
                    append(if (slot >= 0) "SIM ${slot + 1}" else "SIM")
                    if (mccMnc != null) append(" · MCC-MNC ${mccMnc.take(3)}-${mccMnc.drop(3)}")
                }
                roaming = tm.isNetworkRoaming
                if (hasPhone || Build.VERSION.SDK_INT < 30) {
                    generation = RnUtil.generation(if (Build.VERSION.SDK_INT >= 24) tm.dataNetworkType else tm.networkType)
                    if (Build.VERSION.SDK_INT >= 28) signal = tm.signalStrength?.level ?: 0
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }

        return when {
            wifi -> {
                val info = (app.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
                val freq = info?.frequency ?: 0
                RnNetInfo(
                    connection = "Wi-Fi · ${RnUtil.band(freq)}", shortName = "Wi-Fi", isWifi = true,
                    signal = RnUtil.wifiLevel(info?.rssi ?: -100),
                    operatorName = operatorName, simLabel = "$simLabel · idle",
                    extraLabel = "Link speed", extraValue = (info?.linkSpeed?.takeIf { it > 0 }?.let { "$it Mbps" } ?: "—"),
                    hasPhonePermission = hasPhone, vpnActive = vpn,
                )
            }
            cell -> RnNetInfo(
                connection = "Mobile Data · $generation", shortName = RnUtil.shortGeneration(generation), isCellular = true,
                signal = signal, operatorName = operatorName, simLabel = simLabel,
                extraLabel = "Roaming", extraValue = if (roaming) "Yes" else "No",
                hasPhonePermission = hasPhone, vpnActive = vpn,
            )
            eth -> RnNetInfo(connection = "Ethernet", shortName = "LAN", signal = 4, operatorName = operatorName, simLabel = simLabel, hasPhonePermission = hasPhone, vpnActive = vpn)
            else -> RnNetInfo(connection = "No internet", shortName = "—", operatorName = operatorName, simLabel = simLabel, hasPhonePermission = hasPhone, vpnActive = vpn)
        }
    }

    /** Public IP and ISP of the physical connection ("" when the lookup fails). Pure network call, no side effects. */
    suspend fun lookupIsp(): Pair<String, String> = withContext(Dispatchers.IO) {
        var c: HttpURLConnection? = null
        try {
            c = (URL("https://ipwho.is/").openConnection() as HttpURLConnection).apply { connectTimeout = 4000; readTimeout = 4000 }
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            j.optString("ip") to (j.optJSONObject("connection")?.optString("isp").orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            "" to ""
        } finally {
            c?.disconnect()
        }
    }
}

/** Measures latency, download and upload against Cloudflare's public speed endpoints. */
object RnSpeedTest {
    private const val DOWN_URL = "https://speed.cloudflare.com/__down?bytes=100000000"
    private const val UP_URL = "https://speed.cloudflare.com/__up"
    private const val WARMUP_NS = 1_000_000_000L

    /** Average of the three best TCP connect times to 1.1.1.1:443; -1 when unreachable. */
    suspend fun ping(): Int = withContext(Dispatchers.IO) {
        val ok = (1..6).map { SpeedtestManager.socketConnectTime("1.1.1.1", 443, 1500) }.filter { it > 0 }.sorted()
        if (ok.isEmpty()) -1 else ok.take(3).average().toInt()
    }

    suspend fun download(seconds: Int = 5, onValue: (Float) -> Unit): Float = transfer(seconds, onValue, upload = false)

    suspend fun upload(seconds: Int = 4, onValue: (Float) -> Unit): Float = transfer(seconds, onValue, upload = true)

    private suspend fun transfer(seconds: Int, onValue: (Float) -> Unit, upload: Boolean): Float = coroutineScope {
        val bytes = AtomicLong(0)
        val conns = CopyOnWriteArrayList<HttpURLConnection>()
        val start = System.nanoTime()
        val deadline = start + seconds * 1_000_000_000L
        val chunk = ByteArray(65536) { (it * 31).toByte() }
        val workers = (1..4).map {
            launch(Dispatchers.IO) {
                try {
                    val c = (URL(if (upload) UP_URL else DOWN_URL).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000; readTimeout = 4000
                        setRequestProperty("User-Agent", "RadarVPN-SpeedTest")
                        if (upload) { doOutput = true; requestMethod = "POST"; setChunkedStreamingMode(65536) }
                    }
                    conns += c
                    if (upload) {
                        c.outputStream.use { os ->
                            while (isActive && System.nanoTime() < deadline) { os.write(chunk); bytes.addAndGet(chunk.size.toLong()) }
                        }
                    } else {
                        c.inputStream.use { ins ->
                            val buf = ByteArray(65536)
                            while (isActive && System.nanoTime() < deadline) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                bytes.addAndGet(n.toLong())
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
        var lastB = 0L
        var lastT = start
        var smooth = 0f
        var warmB = -1L
        var warmT = start
        while (System.nanoTime() < deadline && workers.any { it.isActive }) {
            delay(150)
            val now = System.nanoTime()
            val b = bytes.get()
            val inst = ((b - lastB) * 8f / 1e6f) / ((now - lastT) / 1e9f)
            smooth = if (smooth == 0f) inst else smooth * 0.6f + inst * 0.4f
            onValue(smooth)
            if (warmB < 0 && now - start >= WARMUP_NS) { warmB = b; warmT = now }
            lastB = b
            lastT = now
        }
        val end = System.nanoTime()
        val endB = bytes.get()
        conns.forEach { runCatching { it.disconnect() } }
        workers.forEach { it.cancel() }
        val (b0, t0) = if (warmB >= 0) warmB to warmT else 0L to start
        val secs = ((end - t0) / 1e9f).coerceAtLeast(0.001f)
        ((endB - b0) * 8f / 1e6f) / secs
    }
}

/** Observable state of the Speed screen; lives in the host so results survive tab switches. */
@Stable
class RnSpeedState {
    var phase by mutableStateOf(RnPhase.IDLE)
    var current by mutableFloatStateOf(0f)
    var label by mutableStateOf("Download")
    var unit by mutableStateOf("Mbps")
    var down by mutableFloatStateOf(0f)
    var up by mutableFloatStateOf(0f)
    var ping by mutableIntStateOf(0)
    var doneTick by mutableIntStateOf(0)
    var publicIp by mutableStateOf("")
    var isp by mutableStateOf("")
    var history by mutableStateOf(RnHistory.load())
    private var job: Job? = null

    val running: Boolean get() = phase == RnPhase.PING || phase == RnPhase.DOWN || phase == RnPhase.UP

    fun start(scope: CoroutineScope, networkLabel: String) {
        if (running) return
        job = scope.launch {
            try {
                phase = RnPhase.PING; label = "Ping"; unit = "ms"; current = 0f; down = 0f; up = 0f; ping = 0
                val p = RnSpeedTest.ping()
                ping = p.coerceAtLeast(0); current = ping.toFloat()
                phase = RnPhase.DOWN; label = "Download"; unit = "Mbps"; current = 0f
                down = RnSpeedTest.download { current = it }
                if (down <= 0.05f) { phase = RnPhase.ERROR; return@launch }
                phase = RnPhase.UP; label = "Upload"; current = 0f
                up = RnSpeedTest.upload { current = it }
                label = "Download"; current = down; phase = RnPhase.DONE; doneTick++
                history = RnHistory.add(RnHistoryItem(System.currentTimeMillis(), networkLabel, down, up, ping))
                val (ip, i) = RnNet.lookupIsp()
                if (ip.isNotBlank()) { publicIp = ip; isp = i }
            } catch (e: CancellationException) {
                phase = RnPhase.IDLE
                throw e
            }
        }
    }

    fun cancel() { job?.cancel() }
}

data class RnHistoryItem(val time: Long, val network: String, val down: Float, val up: Float, val ping: Int)

/** Last 20 tests, stored as one small JSON string in the app's own settings storage. */
object RnHistory {
    private const val KEY = "rn_speed_history"

    fun load(): List<RnHistoryItem> = try {
        val arr = JSONArray(MmkvManager.decodeSettingsString(KEY, "[]") ?: "[]")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RnHistoryItem(o.getLong("t"), o.getString("n"), o.getDouble("d").toFloat(), o.getDouble("u").toFloat(), o.getInt("p"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun add(item: RnHistoryItem): List<RnHistoryItem> {
        val list = (listOf(item) + load()).take(20)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.time).put("n", it.network).put("d", it.down.toDouble()).put("u", it.up.toDouble()).put("p", it.ping)) }
        MmkvManager.encodeSettings(KEY, arr.toString())
        return list
    }
}
