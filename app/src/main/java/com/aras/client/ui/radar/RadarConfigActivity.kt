package com.aras.client.ui.radar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.aras.client.ui.radar.RadarRdyResultStore
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
                var message by remember { mutableStateOf("") }
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
                }
                Scaffold(topBar = { TopAppBar(title = { Text("رادار کانفیگ") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AndroidView(
                            factory = { ctx -> RadarScanView(ctx).also { radar = it; it.startScan() } },
                            modifier = Modifier.fillMaxWidth().height(230.dp),
                            update = { if (scanning) it.startScan() else it.stopScan() }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !scanning, onClick = { results.clear(); radar?.clearBlips(); scanning = true; message = "در حال اسکن…" }) { Text("شروع اسکن") }
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
                                    Text("TCP در دسترس", style = MaterialTheme.typography.labelSmall)
                                    Text(results.count { it.latency != null }.toString(), style = MaterialTheme.typography.titleLarge, color = Color(0xFF00A86B))
                                }
                                Column {
                                    Text("RDY واقعی تأییدشده", style = MaterialTheme.typography.labelSmall)
                                    Text(results.count { RadarRdyResultStore.read(it.uri)?.startsWith("RESULT:") == true }.toString(), style = MaterialTheme.typography.titleLarge)
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
                                label = { Text("دسترسی اولیه (" + results.count { it.latency != null } + ")") }
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
                        // The screen already owns the vertical scroll container. Use a regular
                        // Column here rather than nesting a LazyColumn inside verticalScroll;
                        // the nested lazy list received unbounded height and hid lower configs.
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            visibleResults.forEach { item ->
                                val coreRdy = RadarRdyResultStore.read(item.uri)
                                val coreRdyLabel = when {
                                    coreRdy == "PENDING" -> "RDY هسته: تست واقعی در حال اجراست"
                                    coreRdy?.startsWith("RESULT:") == true -> {
                                        val delay = coreRdy.removePrefix("RESULT:").substringBefore(":").toLongOrNull()
                                        if (delay != null) "RDY هسته: " + delay + " ms" else "RDY هسته: نتیجه ثبت شد"
                                    }
                                    coreRdy == "FINISHED:NO_VALID_RESULT" -> "RDY هسته: نتیجهٔ معتبر دریافت نشد"
                                    else -> "RDY هسته: هنوز اندازه‌گیری نشده"
                                }
                                Card(
                                    Modifier.fillMaxWidth().clickable {
                                        startActivity(
                                            Intent(this@RadarConfigActivity, RadarConfigDetailActivity::class.java)
                                                .putExtra(RadarConfigDetailActivity.EXTRA_CONFIG, item.uri)
                                        )
                                    }
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(item.uri.take(120), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (item.latency == null) {
                                                (item.dnsLatency?.let { "DNS " + it + " ms · " } ?: "") + item.note
                                            } else {
                                                "TCP: " + (item.latency?.toString() ?: "—") + " ms · DNS: " + (item.dnsLatency?.toString() ?: "—") + " ms · " + coreRdyLabel
                                            },
                                            color = if (item.latency == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (item.latency != null) {
                                            Button(onClick = {
                                                startActivity(Intent(this@RadarConfigActivity, MainActivity::class.java).apply {
                                                    action = "com.aras.client.action.IMPORT_RADAR_CONFIG"
                                                    putExtra("radar_config", item.uri)
                                                    putExtra("test_real_delay_after_import", true)
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
                        val bounded = uris.take(MAX_SCAN_RESULTS)
                        if (bounded.isEmpty()) {
                            message = "منابع پاسخ ندادند یا کانفیگ قابل‌خواندن پیدا نشد؛ اتصال اینترنت و دسترسی به GitHub را بررسی کن."
                        } else if (uris.size > bounded.size) {
                            message = "بررسی حداکثر ۵۰۰ کانفیگ…"
                        }
                        var checked = 0
                        for (batch in bounded.chunked(8)) {
                            if (!scanning) break
                            val batchResults = coroutineScope {
                                batch.map { uri -> async(Dispatchers.IO) { testTcp(uri) } }.map { it.await() }
                            }
                            for (result in batchResults) {
                                if (result.latency != null) reachable++
                                // TCP/DNS are preflight diagnostics only. Never render them as RDY blips;
                                // a real radar result must come from a VPN-core measurement.
                                results.add(result)
                                val coreResult = RadarRdyResultStore.read(result.uri)
                                    ?.takeIf { it.startsWith("RESULT:") }
                                    ?.removePrefix("RESULT:")?.substringBefore(":")?.toLongOrNull()
                                radar?.addScanResult(result.latency, coreResult)
                                checked++
                            }
                            message = checked.toString() + "/" + bounded.size
                        }
                        if (scanning) {
                            message = if (checked == 0) {
                                "اسکن پایان یافت، اما نتیجه‌ای برای آزمایش پیدا نشد."
                            } else {
                                "پایان اسکن · " + checked + " کانفیگ · " + reachable + " پاسخ TCP"
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
        // Prefer maintained public feeds that publish ready-to-import URI lines.
        // All sources are best-effort: one unavailable repository must not stop others.
        val sources = listOf(
            "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs_base64.txt",
            "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/top100.txt",
            "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/all.txt",
            "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/best.txt",
            "https://raw.githubusercontent.com/Alirewa/V2ray-Configs/main/config.txt",
            "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/main/subscriptions/v2ray/all_sub.txt",
            "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt",
            "https://raw.githubusercontent.com/ALIILAPRO/v2rayNG-Config/main/sub.txt",
            "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/all_configs.txt",
            "https://raw.githubusercontent.com/mheidari98/.proxy/main/all",
            "https://raw.githubusercontent.com/itsyebekhe/PSG/main/config.txt",
            "https://raw.githubusercontent.com/Surfboardv2ray/TGParse/main/configtg.txt",
            "https://raw.githubusercontent.com/4n0nymous/multi-proxy-config-fetcher/main/configs/proxy_configs.txt",
            "https://raw.githubusercontent.com/Rayan-Config/C-Sub/main/configs/proxy.txt",
            "https://raw.githubusercontent.com/shaoyouvip/free/main/base64.txt",
            "https://raw.githubusercontent.com/V2RayRoot/V2RayConfig/main/Config/vless.txt",
            "https://raw.githubusercontent.com/V2RayRoot/V2RayConfig/main/Config/shadowsocks.txt",
            "https://raw.githubusercontent.com/fengfp/v2ray-configs-free/main/All_Configs_Sub.txt",
            "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/All_Configs_Sub.txt",
            "https://raw.githubusercontent.com/ircfspace/tconfig/main/config.txt",
            "https://raw.githubusercontent.com/Argh94/V2RayAutoConfig/main/config.txt"
        )
        val found = linkedSetOf<String>()
        for (source in sources) {
            if (found.size >= MAX_SCAN_RESULTS) break
            try {
                val raw = get(source, 7000)
                extract(raw).forEach { candidate ->
                    if (found.size < MAX_SCAN_RESULTS) found.add(candidate)
                }
            } catch (_: Exception) {
                // Keep scanning the remaining independent sources.
            }
        }
        return found.take(MAX_SCAN_RESULTS)
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
        c.setRequestProperty("User-Agent", "G-ST-VPN-Radar/1.1")
        return try { c.inputStream.bufferedReader().use { it.readText().take(2_000_000) } } finally { c.disconnect() }
    }
    companion object { private const val MAX_SCAN_RESULTS = 500 }
}
