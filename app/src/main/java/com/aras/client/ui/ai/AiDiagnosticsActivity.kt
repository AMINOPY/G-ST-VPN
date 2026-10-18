package com.aras.client.ui.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.Uri
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
    val advice: String,
)

class AiDiagnosticsActivity : BaseComponentActivity() {
    private var snapshot by mutableStateOf<NetworkSnapshot?>(null)
    private var scanMessage by mutableStateOf<Int?>(R.string.ai_scan_hint)
    private var latencyResult by mutableStateOf<String?>(null)
    private var dnsLookupResult by mutableStateOf<String?>(null)
    private var speedTestResult by mutableStateOf<String?>(null)
    private var speedTestRunning by mutableStateOf(false)

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
            onSpeedTest = { startSpeedTest() },
            onShareReport = { shareReport() },
            onBack = { finish() },
            onScan = { scanNetwork() },
            onSimPermission = {
                requestPhonePermission.launch(Manifest.permission.READ_PHONE_STATE)
            },
            onLibreSpeed = { openExternal("https://librespeed.org") },
            onOpenSpeedTest = { openExternal("https://openspeedtest.com") },
            onNetworkChecker = { openExternal("https://github.com/mirarr-app/network-checker") },
        )
    }

    private val requestPhonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scanNetwork()
            else scanMessage = R.string.ai_sim_permission_denied
        }

    private fun openExternal(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { scanMessage = R.string.ai_open_link_failed }
    }

    @Suppress("DEPRECATION")
    private fun scanNetwork() {
        startLatencyCheck()
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
            val signal = if (hasPhonePermission) {
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
            snapshot = NetworkSnapshot(
                connection, internet, validated, vpn, metered, dns, carrier, radio, signal, mobileDataUsage, advice
            )
            scanMessage = R.string.ai_scan_complete
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
            dnsLookupResult = result.first?.let { getString(R.string.ai_dns_latency_result, it) }
                ?: getString(R.string.ai_latency_failed)
            latencyResult = result.second?.let { getString(R.string.ai_latency_result, it) }
                ?: getString(R.string.ai_latency_failed)
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
            val result = withContext(Dispatchers.IO) {
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
                    getString(
                        R.string.ai_speed_result,
                        String.format(Locale.US, "%.1f", downMbps),
                        String.format(Locale.US, "%.1f", upMbps)
                    )
                }.getOrElse {
                    getString(R.string.ai_speed_failed)
                }
            }
            speedTestResult = result
            speedTestRunning = false
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
                "${getString(R.string.ai_analysis_title)}: ${it.advice}",
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
    onSpeedTest: () -> Unit,
    onShareReport: () -> Unit,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSimPermission: () -> Unit,
    onLibreSpeed: () -> Unit,
    onOpenSpeedTest: () -> Unit,
    onNetworkChecker: () -> Unit,
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

            OutlinedButton(onClick = onShareReport, modifier = Modifier.fillMaxWidth()) {\n                Text(stringResource(R.string.ai_share_report))\n            }\n\n            Text(stringResource(R.string.ai_analysis_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Text(
                    snapshot?.advice?.let { it } ?: stringResource(R.string.ai_analysis_not_ready),
                    modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge
                )
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
            Button(onClick = onLibreSpeed, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_speed_librespeed))
            }
            OutlinedButton(onClick = onOpenSpeedTest, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_speed_openspeedtest))
            }
            OutlinedButton(onClick = onNetworkChecker, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_network_checker))
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
