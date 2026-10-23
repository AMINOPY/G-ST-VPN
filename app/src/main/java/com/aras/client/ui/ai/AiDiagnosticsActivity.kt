package com.aras.client.ui.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.telephony.TelephonyManager
import android.net.TrafficStats
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aras.client.R
import com.aras.client.ui.base.BaseComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.Socket
import java.net.InetSocketAddress
import java.util.Locale

private data class NetworkSnapshot(
    val connection: String,
    val internet: String,
    val validated: Boolean,
    val vpn: Boolean,
    val metered: Boolean,
    val dns: String,
    val carrier: String,
    val radio: String,
    val signal: String,
    val mobileDataUsage: String,
    val signalLevel: Int?,
    val advice: String,
)

private data class SpeedTestOutcome(
    val display: String,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
)

private data class StabilityTestOutcome(
    val display: String,
    val medianMs: Long?,
    val jitterMs: Long?,
    val failurePercent: Int,
)

private data class IpProbeOutcome(val display: String, val reachable: Int, val tested: Int)

class AiDiagnosticsActivity : BaseComponentActivity() {
    private var snapshot by mutableStateOf<NetworkSnapshot?>(null)
    private var scanMessage by mutableStateOf<Int?>(R.string.ai_scan_hint)
    private var latencyResult by mutableStateOf<String?>(null)
    private var dnsLookupResult by mutableStateOf<String?>(null)
    private var speedTestResult by mutableStateOf<String?>(null)
    private var speedTestRunning by mutableStateOf(false)
    private var stabilityTestRunning by mutableStateOf(false)
    private var stabilityResult by mutableStateOf<String?>(null)
    private var ipScanRunning by mutableStateOf(false)
    private var ipScanResult by mutableStateOf<String?>(null)
    private var stabilityReport: StabilityTestOutcome? = null
    private var smartAnalysis by mutableStateOf<String?>(null)
    private var measuredLatencyMs: Long? = null
    private var measuredDnsMs: Long? = null
    private var measuredDownloadMbps: Double? = null
    private var measuredUploadMbps: Double? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        scanNetwork()
    }

    @Composable
    override fun ScreenContent() {
        AiDiagnosticsScreen(
            snapshot = snapshot,
            scanMessage = scanMessage,
            latencyResult = latencyResult,
            dnsLookupResult = dnsLookupResult,
            speedTestResult = speedTestResult,
            speedTestRunning = speedTestRunning,
            stabilityResult = stabilityResult,
            stabilityTestRunning = stabilityTestRunning,
            ipScanResult = ipScanResult,
            ipScanRunning = ipScanRunning,
            smartAnalysis = smartAnalysis,
            onSpeedTest = { startSpeedTest() },
            onStabilityTest = { startStabilityTest() },
            onIpScan = { startIpScan() },
            onShareReport = { shareReport() },
            onBack = { finish() },
            onScan = { scanNetwork() },
            onSimPermission = {
                requestPhonePermission.launch(Manifest.permission.READ_PHONE_STATE)
            },
        )
    }

    private val requestPhonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scanNetwork()
            else scanMessage = R.string.ai_sim_permission_denied
        }

    @Suppress("DEPRECATION")
    private fun scanNetwork() {
        measuredLatencyMs = null
        measuredDnsMs = null
        measuredDownloadMbps = null
        measuredUploadMbps = null
        speedTestResult = null
        try {
            val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivity.activeNetwork
            } else null
            val caps = if (network != null) connectivity.getNetworkCapabilities(network) else null
            val link = if (network != null) connectivity.getLinkProperties(network) else null

            val connection = when {
                caps == null -> getString(R.string.ai_network_none)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> getString(R.string.ai_network_vpn)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> getString(R.string.ai_network_wifi)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> getString(R.string.ai_network_mobile)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> getString(R.string.ai_network_ethernet)
                else -> getString(R.string.ai_network_other)
            }
            val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val internet = if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                getString(R.string.ai_internet_capable)
            } else getString(R.string.ai_internet_no_capability)
            val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            val metered = connectivity.isActiveNetworkMetered
            val dns = link?.dnsServers?.take(4)?.joinToString { it.hostAddress ?: "" }
                ?.takeIf { it.isNotBlank() } ?: getString(R.string.ai_value_unknown)

            val hasPhonePermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            val telephony = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val carrier = if (hasPhonePermission) {
                telephony.networkOperatorName.takeIf { it.isNotBlank() }
                    ?: getString(R.string.ai_value_unknown)
            } else getString(R.string.ai_sim_permission_needed)
            val radio = if (hasPhonePermission) {
                radioLabel(telephony.dataNetworkType)
            } else getString(R.string.ai_sim_permission_needed)
            val signal = if (hasPhonePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { telephony.signalStrength?.level }
                    .getOrNull()?.takeIf { it in 0..4 }
                    ?.let { getString(R.string.ai_signal_level, it) }
                    ?: getString(R.string.ai_value_unknown)
            } else getString(R.string.ai_sim_permission_needed)
            val rxBytes = TrafficStats.getMobileRxBytes()
            val txBytes = TrafficStats.getMobileTxBytes()
            val mobileDataUsage = if (rxBytes >= 0L && txBytes >= 0L) {
                getString(R.string.ai_mobile_usage_value, formatBytes(rxBytes + txBytes))
            } else getString(R.string.ai_value_unknown)

            val advice = when {
                caps == null -> getString(R.string.ai_advice_no_network)
                !validated -> getString(R.string.ai_advice_no_internet)
                vpn -> getString(R.string.ai_advice_vpn)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    getString(R.string.ai_advice_mobile)
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    getString(R.string.ai_advice_wifi)
                else -> getString(R.string.ai_advice_connected)
            }
            val signalLevel = if (hasPhonePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { telephony.signalStrength?.level }.getOrNull()?.takeIf { it in 0..4 }
            } else null
            snapshot = NetworkSnapshot(
                connection, internet, validated, vpn, metered, dns, carrier, radio,
                signal, mobileDataUsage, signalLevel, advice
            )
            smartAnalysis = advice
            refreshSmartAnalysis()
            scanMessage = R.string.ai_scan_complete
            startLatencyCheck()
        } catch (_: Exception) {
            scanMessage = R.string.ai_scan_failed
        }
    }

    /**
     * Measures HTTPS round-trip latency against a lightweight 204 endpoint.
     * This is an application-level latency estimate, not ICMP ping and not a speed test.
     */
    private fun startLatencyCheck() {
        latencyResult = getString(R.string.ai_latency_running)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val dnsStarted = System.nanoTime()
                val dnsElapsed = runCatching {
                    InetAddress.getAllByName("connectivitycheck.gstatic.com")
                    (System.nanoTime() - dnsStarted) / 1_000_000
                }.getOrNull()
                val samples = mutableListOf<Long>()
                repeat(3) {
                    var connection: HttpURLConnection? = null
                    try {
                        val started = System.nanoTime()
                        connection = (URL("https://connectivitycheck.gstatic.com/generate_204")
                            .openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 3500
                            readTimeout = 3500
                            instanceFollowRedirects = false
                            useCaches = false
                        }
                        connection.connect()
                        connection.inputStream?.close()
                        val elapsed = (System.nanoTime() - started) / 1_000_000
                        if (connection.responseCode in 200..299) samples += elapsed
                    } catch (_: Exception) {
                        // Keep scanning the local network even if the probe endpoint is blocked.
                    } finally {
                        connection?.disconnect()
                    }
                }
                val httpMedian = if (samples.isEmpty()) null else samples.sorted()[samples.size / 2]
                dnsElapsed to httpMedian
            }
            measuredDnsMs = result.first
            measuredLatencyMs = result.second
            dnsLookupResult = result.first?.let { getString(R.string.ai_dns_latency_result, it) }
                ?: getString(R.string.ai_latency_failed)
            latencyResult = result.second?.let { getString(R.string.ai_latency_result, it) }
                ?: getString(R.string.ai_latency_failed)
            refreshSmartAnalysis()
        }
    }

    /**
     * Runs a user-triggered, bounded transfer test against Cloudflare's public speed-test
     * endpoints. Downloads up to 1 MB and uploads 256 KB; results are approximate and
     * depend on routing, server load, VPN state, and device conditions.
     */
    private fun startSpeedTest() {
        if (speedTestRunning) return
        speedTestRunning = true
        speedTestResult = getString(R.string.ai_speed_running)
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val downloadBytes = 1_000_000
                    val downloadConnection = (URL("https://speed.cloudflare.com/__down?bytes=$downloadBytes")
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout = 15_000
                        useCaches = false
                    }
                    val downloaded: Long
                    val downloadMillis: Long
                    try {
                        val started = System.nanoTime()
                        downloaded = downloadConnection.inputStream.use { input ->
                            val buffer = ByteArray(16 * 1024)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                            }
                            total
                        }
                        downloadMillis = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
                        if (downloadConnection.responseCode !in 200..299 || downloaded <= 0L) {
                            error("Download test returned no data")
                        }
                    } finally {
                        downloadConnection.disconnect()
                    }

                    val uploadPayload = ByteArray(256 * 1024)
                    java.security.SecureRandom().nextBytes(uploadPayload)
                    val uploadConnection = (URL("https://speed.cloudflare.com/__up")
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 10_000
                        readTimeout = 15_000
                        useCaches = false
                        setRequestProperty("Content-Type", "application/octet-stream")
                        setFixedLengthStreamingMode(uploadPayload.size)
                    }
                    val uploadMillis: Long
                    try {
                        val started = System.nanoTime()
                        uploadConnection.outputStream.use { it.write(uploadPayload) }
                        val code = uploadConnection.responseCode
                        if (code !in 200..299) error("Upload test returned HTTP $code")
                        runCatching { uploadConnection.inputStream.use { it.readBytes() } }
                        uploadMillis = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1)
                    } finally {
                        uploadConnection.disconnect()
                    }

                    val downMbps = downloaded * 8.0 / downloadMillis / 1000.0
                    val upMbps = uploadPayload.size * 8.0 / uploadMillis / 1000.0
                    SpeedTestOutcome(
                        display = getString(
                            R.string.ai_speed_result,
                            String.format(Locale.US, "%.1f", downMbps),
                            String.format(Locale.US, "%.1f", upMbps)
                        ),
                        downloadMbps = downMbps,
                        uploadMbps = upMbps,
                    )
                }.getOrElse {
                    SpeedTestOutcome(getString(R.string.ai_speed_failed))
                }
            }
            speedTestResult = outcome.display
            measuredDownloadMbps = outcome.downloadMbps
            measuredUploadMbps = outcome.uploadMbps
            speedTestRunning = false
            refreshSmartAnalysis()
        }
    }

    /** Builds an explainable diagnosis from measurements actually observed on this device. */
    private fun refreshSmartAnalysis() {
        val current = snapshot ?: return
        val findings = mutableListOf<String>()

        if (!current.validated) {
            findings += getString(R.string.ai_diagnosis_internet_unvalidated)
        }
        if (measuredDnsMs != null && measuredDnsMs!! >= 500L) {
            findings += getString(R.string.ai_diagnosis_dns_slow, measuredDnsMs!!)
        }
        if (measuredLatencyMs != null) {
            when {
                measuredLatencyMs!! >= 1000L -> findings += getString(
                    R.string.ai_diagnosis_latency_high, measuredLatencyMs!!
                )
                measuredLatencyMs!! >= 300L -> findings += getString(
                    R.string.ai_diagnosis_latency_medium, measuredLatencyMs!!
                )
            }
        }
        if (current.signalLevel != null && current.signalLevel <= 1) {
            findings += getString(R.string.ai_diagnosis_signal_weak)
        }
        if (measuredDownloadMbps != null && measuredDownloadMbps!! < 5.0) {
            findings += getString(R.string.ai_diagnosis_download_low, measuredDownloadMbps!!)
        }
        if (measuredUploadMbps != null && measuredUploadMbps!! < 1.0) {
            findings += getString(R.string.ai_diagnosis_upload_low, measuredUploadMbps!!)
        }
        stabilityReport?.let { result ->
            if (result.failurePercent >= 34) findings += "برخی probeهای HTTPS ناموفق بودند (" + result.failurePercent + "٪)؛ endpoint مسدود یا ناپایدار هم می‌تواند علت باشد."
            if (result.jitterMs != null && result.jitterMs >= 120L) findings += "نوسان تأخیر HTTPS بالاست (" + result.jitterMs + " ms)."
        }
        if (current.vpn) findings += getString(R.string.ai_diagnosis_vpn_note)

        smartAnalysis = when {
            findings.isNotEmpty() -> findings.joinToString("\n\n")
            measuredLatencyMs != null || measuredDnsMs != null || measuredDownloadMbps != null ->
                getString(R.string.ai_diagnosis_healthy)
            else -> current.advice
        }
    }

    private fun startIpScan() {
        if (ipScanRunning) return
        ipScanRunning = true
        ipScanResult = "در حال بررسی IPهای منتخب سرویس‌های عمومی از طریق TCP/443…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                // Bounded public-IP sweep: test well-known anycast services on TCP/443.
                // This is reachability/handshake timing, not ICMP ping or VPN-core RDY.
                val targets = listOf(
                    "1.1.1.1" to "Cloudflare DNS",
                    "1.0.0.1" to "Cloudflare DNS backup",
                    "8.8.8.8" to "Google DNS",
                    "8.8.4.4" to "Google DNS backup",
                    "9.9.9.9" to "Quad9 DNS",
                    "149.112.112.112" to "Quad9 DNS backup",
                    "208.67.222.222" to "OpenDNS",
                    "208.67.220.220" to "OpenDNS backup",
                    "76.76.2.0" to "Control D",
                    "76.76.10.0" to "Control D backup",
                    "94.140.14.14" to "AdGuard DNS",
                    "94.140.15.15" to "AdGuard DNS backup",
                    "8.26.56.26" to "Comodo Secure DNS",
                    "8.20.247.20" to "Comodo Secure DNS backup",
                    "185.228.168.9" to "CleanBrowsing",
                    "185.228.169.9" to "CleanBrowsing backup",
                    "45.90.28.0" to "NextDNS",
                    "45.90.30.0" to "NextDNS backup"
                )
                val lines = mutableListOf<String>()
                var reachable = 0
                targets.forEach { (ip, label) ->
                    var socket: Socket? = null
                    try {
                        socket = Socket()
                        val started = System.nanoTime()
                        socket.connect(InetSocketAddress(ip, 443), 1800)
                        val elapsed = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L)
                        reachable++
                        lines += "✓ " + ip + " · " + label + " · TCP/443: " + elapsed + " ms"
                    } catch (_: Exception) {
                        lines += "✕ " + ip + " · " + label + " · TCP/443 ناموفق"
                    } finally {
                        runCatching { socket?.close() }
                    }
                }
                IpProbeOutcome(
                    "دسترسی IP: " + reachable + "/" + targets.size + "\n" + lines.joinToString("\n") +
                        "\nاین اسکن فقط دسترسی TCP/443 به IPهای عمومی منتخب را می‌سنجد؛ پینگ ICMP، کیفیت VPN یا RDY هسته نیست.",
                    reachable, targets.size
                )
            }
            ipScanResult = result.display
            ipScanRunning = false
            refreshSmartAnalysis()
        }
    }

    private fun startStabilityTest() {
        if (stabilityTestRunning) return
        stabilityTestRunning = true
        stabilityResult = "در حال اجرای ۶ بررسی HTTPS برای اندازه‌گیری میانه، نوسان و نرخ شکست…"
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val endpoints = listOf(
                    "https://www.google.com/generate_204",
                    "https://connectivitycheck.gstatic.com/generate_204",
                    "https://www.cloudflare.com/cdn-cgi/trace"
                )
                val samples = mutableListOf<Long>()
                var failures = 0
                repeat(6) { index ->
                    var connection: HttpURLConnection? = null
                    try {
                        val probe = (URL(endpoints[index % endpoints.size]).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 3500
                            readTimeout = 3500
                            instanceFollowRedirects = true
                            useCaches = false
                            setRequestProperty("Cache-Control", "no-cache")
                            setRequestProperty("User-Agent", "RADAR-VPN-Network-Diagnostics/1.0")
                        }
                        connection = probe
                        val started = System.nanoTime()
                        val code = probe.responseCode
                        if (code in 200..399) {
                            runCatching { probe.inputStream.use { it.read() } }
                            samples += ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L)
                        } else failures++
                    } catch (_: Exception) {
                        failures++
                    } finally {
                        connection?.disconnect()
                    }
                }
                val sorted = samples.sorted()
                val median = if (sorted.isEmpty()) null else sorted[sorted.size / 2]
                val jitter = if (samples.size < 2) null else
                    samples.zipWithNext { a, b -> kotlin.math.abs(b - a) }.sorted().let { it[it.size / 2] }
                val failurePercent = failures * 100 / 6
                val quality = if (median == null) 0 else {
                    (100 - failurePercent - (median / 20).toInt().coerceAtMost(35) -
                        ((jitter ?: 0L) / 10).toInt().coerceAtMost(25)).coerceIn(0, 100)
                }
                val display = buildString {
                    append("پایداری مسیر: ").append(if (samples.isEmpty()) "قابل اندازه‌گیری نبود" else quality.toString() + "/100 (تخمینی)")
                    append("\nمیانه HTTPS: ").append(median?.let { it.toString() + " ms" } ?: "ناموفق")
                    append("\nنوسان بین نمونه‌ها: ").append(jitter?.let { it.toString() + " ms" } ?: "داده کافی نیست")
                    append("\nدرخواست‌های ناموفق: ").append(failures).append("/6 (").append(failurePercent).append("%)")
                    append("\nاین نرخ، شکست probeهای HTTPS است؛ packet loss واقعی ICMP نیست و مسدود بودن یک endpoint می‌تواند نتیجه را بدتر کند.")
                }
                StabilityTestOutcome(display, median, jitter, failurePercent)
            }
            stabilityReport = outcome
            stabilityResult = outcome.display
            stabilityTestRunning = false
            refreshSmartAnalysis()
        }
    }

    private fun shareReport() {
        val report = snapshot?.let {
            listOf(
                getString(R.string.ai_report_title),
                "${getString(R.string.ai_connection_type)}: ${it.connection}",
                "${getString(R.string.ai_internet_status)}: ${it.internet}",
                "${getString(R.string.ai_internet_validation)}: ${getString(if (it.validated) R.string.ai_status_validated else R.string.ai_status_not_validated)}",
                "${getString(R.string.ai_vpn_status)}: ${getString(if (it.vpn) R.string.ai_status_active else R.string.ai_status_inactive)}",
                "${getString(R.string.ai_metered)}: ${getString(if (it.metered) R.string.ai_metered_yes else R.string.ai_metered_no)}",
                "${getString(R.string.ai_dns_servers)}: ${it.dns}",
                "${getString(R.string.ai_carrier)}: ${it.carrier}",
                "${getString(R.string.ai_mobile_radio)}: ${it.radio}",
                "${getString(R.string.ai_signal_strength)}: ${it.signal}",
                "${getString(R.string.ai_mobile_data_usage)}: ${it.mobileDataUsage}",
                "${getString(R.string.ai_latency_label)}: ${latencyResult ?: getString(R.string.ai_latency_not_run)}",
                "${getString(R.string.ai_dns_lookup_latency)}: ${dnsLookupResult ?: getString(R.string.ai_latency_not_run)}",
                "${getString(R.string.ai_speed_title)}: ${speedTestResult ?: getString(R.string.ai_latency_not_run)}",
                "Stability probe: ${stabilityResult ?: "not run"}",
                "${getString(R.string.ai_analysis_title)}: ${smartAnalysis ?: it.advice}",
            ).joinToString("\n")
        } ?: getString(R.string.ai_analysis_not_ready)
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
            }, getString(R.string.ai_share_report)))
        }.onFailure { scanMessage = R.string.ai_open_link_failed }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = -1
        do {
            value /= 1024.0
            index++
        } while (value >= 1024.0 && index < units.lastIndex)
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }

    private fun radioLabel(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN -> getString(R.string.ai_radio_2g)
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> getString(R.string.ai_radio_3g)
        TelephonyManager.NETWORK_TYPE_LTE -> getString(R.string.ai_radio_4g)
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            type == TelephonyManager.NETWORK_TYPE_NR
        ) getString(R.string.ai_radio_5g) else getString(R.string.ai_radio_unknown)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiDiagnosticsScreen(
    snapshot: NetworkSnapshot?,
    scanMessage: Int?,
    latencyResult: String?,
    dnsLookupResult: String?,
    speedTestResult: String?,
    speedTestRunning: Boolean,
    stabilityResult: String?,
    stabilityTestRunning: Boolean,
    ipScanResult: String?,
    ipScanRunning: Boolean,
    smartAnalysis: String?,
    onSpeedTest: () -> Unit,
    onStabilityTest: () -> Unit,
    onIpScan: () -> Unit,
    onShareReport: () -> Unit,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSimPermission: () -> Unit,
) {
    val context = LocalContext.current
    val hasPhonePermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ai_hero_title), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.ai_hero_subtitle), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.ai_local_model_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ai_scan_now))
                    }
                    scanMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                }
            }

            Text(stringResource(R.string.ai_report_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportRow(stringResource(R.string.ai_connection_type), snapshot?.connection ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_internet_status), snapshot?.internet ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_internet_validation), snapshot?.let {
                        if (it.validated) stringResource(R.string.ai_status_validated)
                        else stringResource(R.string.ai_status_not_validated)
                    } ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_vpn_status), snapshot?.let {
                        if (it.vpn) stringResource(R.string.ai_status_active) else stringResource(R.string.ai_status_inactive)
                    } ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_metered), snapshot?.let {
                        if (it.metered) stringResource(R.string.ai_metered_yes) else stringResource(R.string.ai_metered_no)
                    } ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_dns_servers), snapshot?.dns ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_carrier), snapshot?.carrier ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_mobile_radio), snapshot?.radio ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_signal_strength), snapshot?.signal ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_mobile_data_usage), snapshot?.mobileDataUsage ?: stringResource(R.string.ai_value_not_scanned))
                    ReportRow(stringResource(R.string.ai_latency_label), latencyResult ?: stringResource(R.string.ai_latency_not_run))
                    ReportRow(stringResource(R.string.ai_dns_lookup_latency), dnsLookupResult ?: stringResource(R.string.ai_latency_not_run))
                    if (!hasPhonePermission) {
                        OutlinedButton(onClick = onSimPermission, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ai_allow_sim_info))
                        }
                    }
                }
            }

            OutlinedButton(onClick = onShareReport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_share_report))
            }

            Text(stringResource(R.string.ai_analysis_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Text(
                    smartAnalysis ?: snapshot?.advice ?: stringResource(R.string.ai_analysis_not_ready),
                    modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge
                )
            }

            Text("اسکن دسترسی IP", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text("بررسی کنترل‌شدهٔ IPهای منتخب سرویس‌های عمومی روی TCP/443؛ این پینگ ICMP، تست ضد DPI یا RDY هستهٔ VPN نیست. اسکن رنج‌های تصادفی و بسیار بزرگ اجرا نمی‌شود.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = onIpScan, enabled = !ipScanRunning, modifier = Modifier.fillMaxWidth()) {
                Text(if (ipScanRunning) "در حال اسکن IPها…" else "شروع اسکن IP")
            }
            ipScanResult?.let {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("اسکن هوشمند پایداری", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text("۶ درخواست HTTPS به چند مقصد عمومی؛ محاسبه میانه تأخیر، نوسان نمونه‌ها و نرخ شکست probe. این آزمون ping/packet-loss سطح ICMP نیست و هر مقصد ممکن است جداگانه مسدود باشد.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = onStabilityTest, enabled = !stabilityTestRunning, modifier = Modifier.fillMaxWidth()) {
                Text(if (stabilityTestRunning) "در حال بررسی پایداری…" else "اجرای اسکن پایداری")
            }
            stabilityResult?.let {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text(stringResource(R.string.ai_speed_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ai_speed_note), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onSpeedTest, enabled = !speedTestRunning, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (speedTestRunning) R.string.ai_speed_running else R.string.ai_speed_run))
            }
            speedTestResult?.let {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(0.9f), style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(1.1f), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}
