package com.aras.client.ui.radar

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aras.client.ui.compose.AppTheme
import com.aras.client.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

private data class CandidateDiagnostics(
    val host: String? = null, val port: Int? = null,
    val dnsMs: Long? = null, val tcpMs: Long? = null,
    val tlsMs: Long? = null, val tlsNote: String = "هنوز آزمایش نشده",
    val error: String? = null
)

class RadarConfigDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
        setContent {
            AppTheme {
                var diagnostics by remember { mutableStateOf<CandidateDiagnostics?>(null) }
                var running by remember { mutableStateOf(true) }
                LaunchedEffect(uri) {
                    diagnostics = withContext(Dispatchers.IO) { inspectCandidate(uri) }
                    running = false
                }
                Scaffold(topBar = { TopAppBar(title = { Text("جزئیات کانفیگ") }) }) { insets ->
                    Column(
                        Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("لینک کانفیگ", style = MaterialTheme.typography.titleMedium)
                        Card(Modifier.fillMaxWidth()) { Text(uri, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                        Text("آزمایش‌های شبکه", style = MaterialTheme.typography.titleMedium)
                        if (running) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("در حال اندازه‌گیری DNS، TCP و TLS…")
                        }
                        diagnostics?.let { d ->
                            MetricRow("میزبان", d.host ?: "قابل تشخیص نیست")
                            MetricRow("پورت", d.port?.toString() ?: "—")
                            MetricRow("DNS واقعی", d.dnsMs?.let { it.toString() + " ms" } ?: "ناموفق")
                            MetricRow("TCP واقعی", d.tcpMs?.let { it.toString() + " ms" } ?: "ناموفق")
                            MetricRow("TLS / SNI", d.tlsMs?.let { it.toString() + " ms · " + d.tlsNote } ?: d.tlsNote)
                            d.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("RDY · Real Delay", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "تأخیر واقعی تونل فقط پس از اجرای کانفیگ با هسته VPN قابل اندازه‌گیری است. زمان TCP یا TLS به‌تنهایی RDY یا پینگ تونل نیست؛ اینجا عدد ساختگی نمایش داده نمی‌شود.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "آزمایش TLS/SNI یک نشانهٔ کمکی برای سازگاری مسیر است، نه تشخیص قطعی DPI.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Button(
                            enabled = uri.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                startActivity(Intent(this@RadarConfigDetailActivity, MainActivity::class.java).apply {
                                    action = "com.aras.client.action.IMPORT_RADAR_CONFIG"
                                    putExtra("radar_config", uri)
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                })
                                finish()
                            }
                        ) { Text("اتصال / افزودن به سرورها") }
                        OutlinedButton(Modifier.fillMaxWidth(), onClick = { finish() }) { Text("بازگشت به رادار کانفیگ") }
                    }
                }
            }
        }
    }

    private fun inspectCandidate(uri: String): CandidateDiagnostics {
        val endpoint = parseEndpoint(uri) ?: return CandidateDiagnostics(error = "از این لینک میزبان و پورت قابل استخراج نیست.")
        val host = endpoint.first
        val port = endpoint.second
        val dnsStart = System.nanoTime()
        val addresses = try { InetAddress.getAllByName(host) } catch (_: Exception) {
            return CandidateDiagnostics(host, port, error = "DNS ناموفق بود؛ TCP و TLS قابل اندازه‌گیری نشدند.")
        }
        val dnsMs = ((System.nanoTime() - dnsStart) / 1_000_000L).coerceAtLeast(0L)
        var tcpMs: Long? = null
        var tcpAddress: InetAddress? = null
        for (address in addresses) {
            val started = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(address, port), 2500) }
                tcpMs = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L)
                tcpAddress = address
                break
            } catch (_: Exception) { }
        }
        if (tcpMs == null) return CandidateDiagnostics(host, port, dnsMs, error = "DNS موفق بود اما اتصال TCP برقرار نشد.")
        val sni = parseSni(uri) ?: host
        val tlsStart = System.nanoTime()
        var tlsMs: Long? = null
        val tlsNote = try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            factory.createSocket().use { raw ->
                raw.soTimeout = 2500
                raw.connect(InetSocketAddress(tcpAddress, port), 2500)
                val ssl = raw as SSLSocket
                ssl.sslParameters = ssl.sslParameters.apply {
                    serverNames = listOf(javax.net.ssl.SNIHostName(sni))
                }
                ssl.startHandshake()
                tlsMs = ((System.nanoTime() - tlsStart) / 1_000_000L).coerceAtLeast(0L)
                "TLS handshake موفق"
            }
        } catch (_: Exception) { "TLS handshake ناموفق؛ ممکن است پورت غیر-TLS یا SNI متفاوت باشد" }
        return CandidateDiagnostics(host, port, dnsMs, tcpMs, tlsMs, tlsNote)
    }

    private fun parseEndpoint(uri: String): Pair<String, Int>? = runCatching {
        if (uri.startsWith("vmess://", true)) {
            val raw = Base64.decode(uri.substringAfter("://").substringBefore('#'), Base64.DEFAULT).toString(Charsets.UTF_8)
            val host = Regex("\"add\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: return null
            val port = Regex("\"port\"\\s*:\\s*\"?(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return host to port
        }
        val authority = uri.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        val host = authority.substringBeforeLast(':').trim('[', ']')
        val port = authority.substringAfterLast(':').toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) null else host to port
    }.getOrNull()

    private fun parseSni(uri: String): String? = runCatching {
        if (uri.startsWith("vmess://", true)) {
            val raw = Base64.decode(uri.substringAfter("://").substringBefore('#'), Base64.DEFAULT).toString(Charsets.UTF_8)
            Regex("\"(?:sni|host)\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)
        } else {
            uri.substringAfter('?', "").substringBefore('#').split('&').mapNotNull { part ->
                val pair = part.split('=', limit = 2)
                if (pair.size == 2) URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8") else null
            }.firstOrNull { it.first.equals("sni", true) || it.first.equals("serverName", true) }?.second
        }
    }.getOrNull()

    companion object { const val EXTRA_CONFIG = "radar_config_uri" }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
