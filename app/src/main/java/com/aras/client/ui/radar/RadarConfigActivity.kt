package com.aras.client.ui.radar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aras.client.radar.ui.RadarScanView
import android.content.Intent
import com.aras.client.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Base64

private data class RadarCandidate(val uri: String, val latency: Long?, val note: String)

/** Public-source discovery. TCP reachability is not treated as proof of a working proxy tunnel. */
@OptIn(ExperimentalMaterial3Api::class)
class RadarConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var scanning by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("برای شروع، اسکن را اجرا کن.") }
                val results = remember { mutableStateListOf<RadarCandidate>() }
                var radar by remember { mutableStateOf<RadarScanView?>(null) }
                Scaffold(topBar = { TopAppBar(title = { Text("رادار کانفیگ") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("جست‌وجو در منابع عمومی و پیش‌آزمایش دسترسی شبکه", style = MaterialTheme.typography.titleMedium)
                        Text("پاسخ TCP به‌تنهایی سلامت تونل را ثابت نمی‌کند. هیچ کانفیگی خودکار نصب یا متصل نمی‌شود.", style = MaterialTheme.typography.bodySmall)
                        AndroidView(Modifier.fillMaxWidth().height(230.dp), factory = { ctx -> RadarScanView(ctx).also { radar = it; it.startScan() } },
                            update = { if (scanning) it.startScan() else it.stopScan() })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !scanning, onClick = { results.clear(); radar?.clearBlips(); scanning = true; message = "در حال دریافت و بررسی منابع…" }) { Text("شروع اسکن") }
                            OutlinedButton(enabled = scanning, onClick = { scanning = false }) { Text("توقف") }
                        }
                        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(results, key = { it.uri }) { item ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(item.uri.take(120), style = MaterialTheme.typography.bodyMedium)
                                        Text(if (item.latency == null) item.note else "TCP " + item.latency + " ms · " + item.note,
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
                        val uris = withContext(Dispatchers.IO) { discover() }
                        var reachable = 0
                        for ((index, uri) in uris.take(120).withIndex()) {
                            if (!scanning) break
                            val result = withContext(Dispatchers.IO) { testTcp(uri) }
                            if (result.latency != null) { reachable++; radar?.addTestedCandidate(result.latency) }
                            results.add(result)
                            message = "بررسی " + (index + 1) + "/" + uris.size + " · پاسخ TCP: " + reachable
                        }
                        if (scanning) message += " · پیش‌آزمایش تمام شد"
                    } catch (e: Exception) { message = "خطا در اسکن: " + (e.message ?: "شبکه") }
                    scanning = false
                    radar?.stopScan()
                }
            }
        }
    }

    private fun discover(): List<String> {
        val repos = listOf("barry-far/V2ray-Config", "0xRadikal/Free-v2ray-Configs", "ircfspace/tconfig", "Argh94/V2RayAutoConfig")
        val found = linkedSetOf<String>()
        for (repo in repos) try {
            val metadata = get("https://api.github.com/repos/" + repo, 10000)
            val branch = Regex("\\"default_branch\\"\\s*:\\s*\\"([^\\"]+)\\"").find(metadata)?.groupValues?.get(1) ?: "main"
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
        val decoded = runCatching { Base64.getMimeDecoder().decode(compact.replace('-', '+').replace('_', '/')).toString(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        return pattern.findAll(decoded).map { it.value.trimEnd(',', ';', ')') }.toList()
    }

    private fun testTcp(uri: String): RadarCandidate {
        val endpoint = parseEndpoint(uri) ?: return RadarCandidate(uri, null, "آدرس قابل تشخیص نیست")
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(endpoint.first, endpoint.second), 2500) }
            RadarCandidate(uri, (System.nanoTime() - start) / 1_000_000L, "پورت پاسخ داد؛ تونل تأیید نشده")
        } catch (_: Exception) { RadarCandidate(uri, null, "پورت پاسخ نداد") }
    }

    private fun parseEndpoint(uri: String): Pair<String, Int>? = runCatching {
        if (uri.startsWith("vmess://", true)) {
            val raw = Base64.getDecoder().decode(uri.substringAfter("://").substringBefore('#')).toString(Charsets.UTF_8)
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
