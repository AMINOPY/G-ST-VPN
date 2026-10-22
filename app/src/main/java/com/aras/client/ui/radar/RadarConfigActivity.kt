package com.aras.client.ui.radar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aras.client.radar.ui.RadarScanView
import com.aras.client.ui.compose.AppTheme
import android.content.Intent
import com.aras.client.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private data class RadarCandidate(
    val uri: String,
    val latency: Long?,
    val note: String,
    val dnsLatency: Long? = null,
)

/** Public-source discovery. TCP reachability is not treated as proof of a working proxy tunnel. */
@OptIn(ExperimentalMaterial3Api::class)
class RadarConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var scanning by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("برای شروع، اسکن را اجرا کن.") }
                val results = remember { mutableStateListOf<RadarCandidate>() }
                var radar by remember { mutableStateOf<RadarScanView?>(null) }
                var resultFilter by remember { mutableStateOf("all") }
                var protocolFilter by remember { mutableStateOf("all") }
                val visibleResults = results.filter { candidate ->
                    val reachabilityMatches = when (resultFilter) {
                        "reachable" -> candidate.latency != null
                        "failed" -> candidate.latency == null
                        else -> true
                    }
                    val protocolMatches = protocolFilter == "all" ||
                        protocolOf(candidate.uri) == protocolFilter
                    reachabilityMatches && protocolMatches
                }.sortedWith(
                    compareBy<RadarCandidate> { it.latency == null }
                        .thenBy { it.latency ?: Long.MAX_VALUE }
                )
                Scaffold(topBar = { TopAppBar(title = { Text("رادار کانفیگ") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("جست‌وجو در منابع عمومی و پیش‌آزمایش دسترسی شبکه", style = MaterialTheme.typography.titleMedium)
                        Text("پاسخ TCP به‌تنهایی سلامت تونل را ثابت نمی‌کند. هیچ کانفیگی خودکار نصب یا متصل نمی‌شود.", style = MaterialTheme.typography.bodySmall)
                        AndroidView(
                            factory = { ctx -> RadarScanView(ctx).also { radar = it; it.startScan() } },
                            modifier = Modifier.fillMaxWidth().height(230.dp),
                            update = { if (scanning) it.startScan() else it.stopScan() }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !scanning, onClick = { results.clear(); radar?.clearBlips(); scanning = true; message = "در حال دریافت و بررسی منابع…" }) { Text("شروع اسکن") }
                            OutlinedButton(enabled = scanning, onClick = { scanning = false }) { Text("توقف") }
                        }
                        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("پیدا شده", style = MaterialTheme.typography.labelSmall)
                                    Text(results.size.toString(), style = MaterialTheme.typography.titleLarge)
                                }
                                Column {
                                    Text("TCP پاسخ‌گو", style = MaterialTheme.typography.labelSmall)
                                    Text(results.count { it.latency != null }.toString(), style = MaterialTheme.typography.titleLarge, color = Color(0xFF00A86B))
                                }
                                Column {
                                    Text("سریع ≤۱۵۰ms", style = MaterialTheme.typography.labelSmall)
                                    Text(results.count { (it.latency ?: Long.MAX_VALUE) <= 150 }.toString(), style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = resultFilter == "all",
                                onClick = { resultFilter = "all" },
                                label = { Text("همه (" + results.size + ")") }
                            )
                            FilterChip(
                                selected = resultFilter == "reachable",
                                onClick = { resultFilter = "reachable" },
                                label = { Text("پاسخ‌گو (" + results.count { it.latency != null } + ")") }
                            )
                            FilterChip(
                                selected = resultFilter == "failed",
                                onClick = { resultFilter = "failed" },
                                label = { Text("بدون پاسخ (" + results.count { it.latency == null } + ")") }
                            )
                        }
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(selected = protocolFilter == "all", onClick = { protocolFilter = "all" }, label = { Text("همهٔ پروتکل‌ها") })
                            }
                            item {
                                FilterChip(selected = protocolFilter == "vmess", onClick = { protocolFilter = "vmess" }, label = { Text("VMess") })
                            }
                            item {
                                FilterChip(selected = protocolFilter == "vless", onClick = { protocolFilter = "vless" }, label = { Text("VLESS") })
                            }
                            item {
                                FilterChip(selected = protocolFilter == "trojan", onClick = { protocolFilter = "trojan" }, label = { Text("Trojan") })
                            }
                            item {
                                FilterChip(selected = protocolFilter == "ss", onClick = { protocolFilter = "ss" }, label = { Text("Shadowsocks") })
                            }
                            item {
                                FilterChip(selected = protocolFilter == "hysteria2", onClick = { protocolFilter = "hysteria2" }, label = { Text("Hysteria") })
                            }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visibleResults, key = { it.uri }) { item ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(item.uri.take(120), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (item.latency == null) {
                                                (item.dnsLatency?.let { "DNS " + it + " ms · " } ?: "") + item.note
                                            } else {
                                                "DNS " + (item.dnsLatency?.toString() ?: "—") + " ms · TCP " + item.latency + " ms · " + item.note
                                            },
                                            color = when { item.latency == null -> MaterialTheme.colorScheme.error; item.latency <= 150 -> Color(0xFF00A86B); item.latency <= 300 -> Color(0xFFB8860B); else -> Color(0xFFC62828) })
                                        if (item.latency != null) {
                                            Button(onClick = {
                                                startActivity(Intent(this@RadarConfigActivity, MainActivity::class.java).apply {
                                                    action = "com.aras.client.action.IMPORT_RADAR_CONFIG"
                                                    putExtra("radar_config", item.uri)
                                                })
                                                finish()
                                            }) { Text("افزودن برای تست واقعی") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                LaunchedEffect(scanning) {
                    if (!scanning) return@LaunchedEffect
                    try {
                        val uris = withContext(Dispatchers.IO) { discover().distinct() }
                        var reachable = 0
                        val bounded = uris.take(180)
                        if (bounded.isEmpty()) {
                            message = "منابع پاسخ ندادند یا کانفیگ قابل‌خواندن پیدا نشد؛ اتصال اینترنت و دسترسی به GitHub را بررسی کن."
                        } else if (uris.size > bounded.size) {
                            message = "تعداد زیادی نتیجه پیدا شد؛ برای حفظ سرعت، ۱۸۰ مورد اول بررسی می‌شوند."
                        }
                        var checked = 0
                        for (batch in bounded.chunked(8)) {
                            if (!scanning) break
                            val batchResults = coroutineScope {
                                batch.map { uri -> async(Dispatchers.IO) { testTcp(uri) } }.map { it.await() }
                            }
                            for (result in batchResults) {
                                if (result.latency != null) reachable++
                                // Every candidate gets a blip: green/yellow for reachable latency,
                                // red for an unreachable endpoint.
                                radar?.addTestedCandidate(result.latency ?: -1L)
                                results.add(result)
                                checked++
                            }
                            message = "بررسی " + checked + "/" + bounded.size + " · پاسخ TCP: " + reachable
                        }
                        if (scanning) {
                            message = if (checked == 0) {
                                "اسکن پایان یافت، اما نتیجه‌ای برای آزمایش پیدا نشد."
                            } else {
                                "آزمایش تمام شد: " + checked + " کانفیگ · TCP پاسخ‌گو: " + reachable +
                                    " · تکراری‌ها حذف شدند؛ اتصال واقعی هنوز باید داخل برنامه تست شود."
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) { message = "خطا در اسکن: " + (e.message ?: "شبکه") }
                    finally {
                        scanning = false
                        radar?.stopScan()
                    }
                }
            }
        }
    }

    private fun discover(): List<String> {
        val repos = listOf("barry-far/V2ray-Config", "0xRadikal/Free-v2ray-Configs", "ircfspace/tconfig", "Argh94/V2RayAutoConfig")
        val found = linkedSetOf<String>()
        for (repo in repos) try {
            val metadata = get("https://api.github.com/repos/" + repo, 10000)
            val branch = Regex("""default_branch"\s*:\s*"([^"]+)""").find(metadata)?.groupValues?.get(1) ?: "main"
            val tree = get("https://api.github.com/repos/" + repo + "/git/trees/" + branch + "?recursive=1", 10000)
            val paths = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"").findAll(tree).map { it.groupValues[1] }
                .filter { p -> val q = p.lowercase(); (q.endsWith(".txt") || q.endsWith(".yaml") || q.endsWith(".yml") || q.endsWith(".json")) && (q.contains("config") || q.contains("sub") || q.contains("all") || q.contains("country")) }
                .take(10)
            for (path in paths) {
                val raw = get("https://raw.githubusercontent.com/" + repo + "/" + branch + "/" + path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }, 8000)
                extract(raw).forEach { found.add(it) }
                if (found.size >= 250) return found.toList()
            }
        } catch (_: Exception) { /* Other sources may still work. */ }
        return found.toList()
    }

    private fun extract(text: String): List<String> {
        val pattern = Regex("(?:vmess|vless|trojan|ss|hy2|hysteria2|tuic|socks)://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
        val direct = pattern.findAll(text).map { it.value.trimEnd(',', ';', ')') }.toList()
        if (direct.isNotEmpty()) return direct
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.length !in 24..2_000_000) return emptyList()
        val bytes = runCatching { Base64.decode(compact, Base64.DEFAULT) }
            .recoverCatching { Base64.decode(compact, Base64.URL_SAFE or Base64.NO_WRAP) }
            .getOrNull() ?: return emptyList()
        val decoded = bytes.toString(Charsets.UTF_8)
        return pattern.findAll(decoded).map { it.value.trimEnd(',', ';', ')') }.toList()
    }

    private fun testTcp(uri: String): RadarCandidate {
        val endpoint = parseEndpoint(uri)
            ?: return RadarCandidate(uri, null, "آدرس یا پورت قابل تشخیص نیست")
        val dnsStart = System.nanoTime()
        val addresses = try {
            InetAddress.getAllByName(endpoint.first)
        } catch (_: Exception) {
            return RadarCandidate(uri, null, "DNS ناموفق بود یا میزبان در دسترس نیست")
        }
        val dnsMs = (System.nanoTime() - dnsStart) / 1_000_000L
        var lastError: Exception? = null
        for (address in addresses) {
            val tcpStart = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, endpoint.second), 1800)
                }
                val tcpMs = (System.nanoTime() - tcpStart) / 1_000_000L
                return RadarCandidate(
                    uri = uri,
                    latency = tcpMs,
                    note = "DNS و پورت پاسخ دادند؛ سلامت تونل تأیید نشده",
                    dnsLatency = dnsMs,
                )
            } catch (e: Exception) {
                lastError = e
            }
        }
        val detail = if (lastError is java.net.SocketTimeoutException) {
            "مهلت اتصال TCP تمام شد"
        } else {
            "DNS پاسخ داد، اما پورت TCP در دسترس نیست"
        }
        return RadarCandidate(uri, null, detail, dnsMs)
    }

    private fun protocolOf(uri: String): String {
        val scheme = uri.substringBefore("://", "").lowercase()
        return when (scheme) {
            "hy2" -> "hysteria2"
            else -> scheme
        }
    }

    private fun parseEndpoint(uri: String): Pair<String, Int>? = runCatching {
        if (uri.startsWith("vmess://", true)) {
            val encoded = uri.substringAfter("://").substringBefore('#')
            val raw = runCatching { Base64.decode(encoded, Base64.DEFAULT) }
                .recoverCatching { Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP) }
                .getOrThrow().toString(Charsets.UTF_8)
            val host = Regex("\"add\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: return null
            val port = Regex("\"port\"\\s*:\\s*\"?(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return host to port
        }
        val authority = uri.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        val host = authority.substringBeforeLast(':').trim('[', ']')
        val port = authority.substringAfterLast(':').toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) null else host to port
    }.getOrNull()

    private fun get(url: String, timeout: Int): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout; c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "G-ST-VPN-Radar/1.0")
        return try { c.inputStream.bufferedReader().use { it.readText().take(2_000_000) } } finally { c.disconnect() }
    }
}
