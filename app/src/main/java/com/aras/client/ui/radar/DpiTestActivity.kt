package com.aras.client.ui.radar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aras.client.ui.compose.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

private data class DpiProbeResult(
    val host: String,
    val dnsMs: Long?,
    val httpsMs: Long?,
    val httpCode: Int?,
    val detail: String
)

/** Diagnostic only: DNS and certificate-validated HTTPS checks; failures alone do not prove DPI. */
@OptIn(ExperimentalMaterial3Api::class)
class DpiTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var running by remember { mutableStateOf(false) }
                var summary by remember { mutableStateOf("آزمایش، DNS و HTTPS شبکهٔ فعلی را بررسی می‌کند. برای ارزیابی مسیر VPN، ابتدا به کانفیگ موردنظر وصل شو.") }
                val results = remember { mutableStateListOf<DpiProbeResult>() }
                Scaffold(topBar = { TopAppBar(title = { Text("آزمایش تشخیصی DPI") }) }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "این بررسی، پاسخ DNS و اتصال HTTPS استاندارد با اعتبارسنجی گواهی را مقایسه می‌کند. نتیجه فقط نشانهٔ احتمالی اختلال است و DPI را به‌تنهایی اثبات نمی‌کند.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            enabled = !running,
                            onClick = { results.clear(); running = true; summary = "در حال آزمایش مقصدهای عمومی…" }
                        ) { Text("شروع آزمایش DPI") }
                        if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(summary, style = MaterialTheme.typography.titleSmall)
                        results.forEach { result ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(result.host, style = MaterialTheme.typography.titleMedium)
                                    Text("DNS: " + (result.dnsMs?.let { "$it ms" } ?: "ناموفق/نامشخص"))
                                    Text("HTTPS: " + (result.httpsMs?.let { "$it ms" } ?: "ناموفق") +
                                        " · HTTP: " + (result.httpCode?.toString() ?: "—"))
                                    Text(result.detail, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                LaunchedEffect(running) {
                    if (!running) return@LaunchedEffect
                    try {
                        val probes = listOf(
                            "www.cloudflare.com" to "https://www.cloudflare.com/cdn-cgi/trace",
                            "www.google.com" to "https://www.google.com/generate_204",
                            "www.wikipedia.org" to "https://www.wikipedia.org/"
                        )
                        val checked = withContext(Dispatchers.IO) {
                            probes.map { (host, url) -> async(Dispatchers.IO) { probe(host, url) } }.awaitAll()
                        }
                        results.addAll(checked)
                        val failed = checked.count { it.httpsMs == null }
                        summary = when {
                            failed == 0 -> "هر سه مقصد HTTPS پاسخ دادند؛ در این آزمایش نشانهٔ واضحی از اختلال دیده نشد."
                            failed == checked.size -> "همهٔ مقصدها ناموفق بودند؛ اینترنت، DNS، زمان دستگاه یا محدودیت شبکه را بررسی کن. این نتیجه به‌تنهایی اثبات DPI نیست."
                            else -> "$failed از ${checked.size} مقصد HTTPS ناموفق بود؛ تفاوت پاسخ‌ها می‌تواند نشانهٔ اختلال مسیر یا محدودیت شبکه باشد، اما علت قطعی نیست."
                        }
                    } catch (e: Exception) {
                        summary = "آزمایش کامل نشد: " + (e.message ?: "خطای شبکه")
                    } finally {
                        running = false
                    }
                }
            }
        }
    }

    private fun probe(host: String, url: String): DpiProbeResult {
        var dnsMs: Long? = null
        var httpsMs: Long? = null
        var code: Int? = null
        var detail = "پاسخ معتبر دریافت شد."
        try {
            val dnsStart = System.nanoTime()
            InetAddress.getAllByName(host)
            dnsMs = (System.nanoTime() - dnsStart) / 1_000_000L
        } catch (e: Exception) {
            detail = "حل نام DNS ناموفق بود: " + (e.message ?: e.javaClass.simpleName)
            return DpiProbeResult(host, null, null, null, detail)
        }
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "RadarVPN-Network-Diagnostics")
            }
        } catch (e: Exception) {
            return DpiProbeResult(host, dnsMs, null, null, "ساخت درخواست HTTPS ناموفق بود.")
        }
        try {
            val start = System.nanoTime()
            code = connection.responseCode
            httpsMs = (System.nanoTime() - start) / 1_000_000L
            if (code !in 200..399) detail = "پاسخ HTTP غیرمنتظره است؛ لزوماً به معنی DPI نیست."
        } catch (e: Exception) {
            detail = "اتصال HTTPS/TLS ناموفق بود: " + (e.message ?: e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
        return DpiProbeResult(host, dnsMs, httpsMs, code, detail)
    }
}
