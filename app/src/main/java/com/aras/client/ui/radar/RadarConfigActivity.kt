package com.aras.client.ui.radar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.aras.client.core.cdn.CdnSniConfig
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

private data class RadarSource(val name: String, val url: String, val country: String)

private val radarCountrySources = listOf(
    RadarSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/United_States.txt", "آمریکا"),
    RadarSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Germany.txt", "آلمان"),
    RadarSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Türkiye.txt", "ترکیه"),
    RadarSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/South_Korea.txt", "کره جنوبی"),
    RadarSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Russia.txt", "روسیه"),
    RadarSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/United_States.txt", "آمریکا"),
    RadarSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Germany.txt", "آلمان"),
    RadarSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Türkiye.txt", "ترکیه"),
    RadarSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/South_Korea.txt", "کره جنوبی"),
    RadarSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Russia.txt", "روسیه"),
    RadarSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/us.txt", "آمریکا"),
    RadarSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/de.txt", "آلمان"),
    RadarSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/tr.txt", "ترکیه"),
    RadarSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/kr.txt", "کره جنوبی"),
    RadarSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/ru.txt", "روسیه"),
    RadarSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/US.txt", "آمریکا"),
    RadarSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/DE.txt", "آلمان"),
    RadarSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/TR.txt", "ترکیه"),
    RadarSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/KR.txt", "کره جنوبی"),
    RadarSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/RU.txt", "روسیه"),
    RadarSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/France.txt", "فرانسه"),
    RadarSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Netherlands.txt", "هلند"),
    RadarSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Japan.txt", "ژاپن"),
    RadarSource("V2RayScrapeByCountry", "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Singapore.txt", "سنگاپور"),
    RadarSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/France.txt", "فرانسه"),
    RadarSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Netherlands.txt", "هلند"),
    RadarSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Japan.txt", "ژاپن"),
    RadarSource("SoliSpirit/v2ray-configs", "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Singapore.txt", "سنگاپور"),
    RadarSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/fr.txt", "فرانسه"),
    RadarSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/nl.txt", "هلند"),
    RadarSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/jp.txt", "ژاپن"),
    RadarSource("Delta-Kronecker/V2ray-Config", "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/sg.txt", "سنگاپور"),
    RadarSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/FR.txt", "فرانسه"),
    RadarSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/NL.txt", "هلند"),
    RadarSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/JP.txt", "ژاپن"),
    RadarSource("Danialsamadi/v2go", "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/SG.txt", "سنگاپور")
)

private data class RadarCandidate(
    val uri: String,
    val latency: Long?,
    val note: String,
    val dnsLatency: Long? = null,
    val country: String? = null,
    val source: String? = null,
)

/** Public-source discovery. TCP reachability is not treated as proof of a working proxy tunnel. */
@OptIn(ExperimentalMaterial3Api::class)
class RadarConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val requestedEdgeIp = intent.getStringExtra("cdn_edge_ip").orEmpty().trim()
                val requestedSni = intent.getStringExtra("cdn_sni_host").orEmpty().trim()
                val hasAnyCdnSni = requestedEdgeIp.isNotBlank() || requestedSni.isNotBlank()
                val hasCdnSni = CdnSniConfig.parseIp(requestedEdgeIp) != null &&
                    CdnSniConfig.parseSni(requestedSni) != null
                var scanning by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("برای شروع، اسکن را اجرا کن.") }
                val results = remember { mutableStateListOf<RadarCandidate>() }
                var radar by remember { mutableStateOf<RadarScanView?>(null) }
                var resultFilter by remember { mutableStateOf("all") }
                var protocolFilter by remember { mutableStateOf("all") }
                var countryFilter by remember { mutableStateOf("all") }
                val availableCountries = results.mapNotNull { it.country }.distinct().sorted()
                val visibleResults = results.filter { candidate ->
                    val countryMatches = countryFilter == "all" || candidate.country == countryFilter
                    val reachabilityMatches = when (resultFilter) {
                        "reachable" -> candidate.latency != null
                        "failed" -> candidate.latency == null
                        else -> true
                    }
                    val protocolMatches = protocolFilter == "all" ||
                        protocolOf(candidate.uri) == protocolFilter
                    reachabilityMatches && protocolMatches && countryMatches
                }.sortedWith(
                    compareBy<RadarCandidate> { it.latency == null }
                        .thenBy { it.latency ?: Long.MAX_VALUE }
                )
                Scaffold(topBar = { TopAppBar(title = { Text("رادار کانفیگ") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("اتصال خودکار Xray بدون ورود دستی کانفیگ", style = MaterialTheme.typography.titleMedium)
                        Text("رادار کانفیگ‌های سازگار را از منابع عمومی پیدا می‌کند و دسترسی شبکه را پیش‌آزمایش می‌کند. پاسخ TCP فقط مرحلهٔ اول است؛ اتصال موفق باید با اجرای Xray و عبور ترافیک واقعی تأیید شود. IP و SNI به‌تنهایی برای ساخت تونل عمومی Xray کافی نیستند.", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (hasCdnSni) "حالت تطبیق CDN/SNI: $requestedEdgeIp · $requestedSni. فقط لینک‌های TLS سازگار قابل بازنویسی‌اند؛ نتیجهٔ TCP به‌تنهایی تونل را تأیید نمی‌کند."
                            else "حالت فعلی: کشف و بررسی TCP؛ انتخاب و اتصال خودکار هنوز به تأیید واقعی تونل نیاز دارد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
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
                        if (availableCountries.isNotEmpty()) {
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = countryFilter == "all",
                                        onClick = { countryFilter = "all" },
                                        label = { Text("همه کشورها") }
                                    )
                                }
                                items(availableCountries) { country ->
                                    FilterChip(
                                        selected = countryFilter == country,
                                        onClick = { countryFilter = country },
                                        label = { Text(country) }
                                    )
                                }
                            }
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
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        item.country?.let { Text("برچسب کشور منبع: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                                        item.source?.let { Text("منبع: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
                                        Text(item.uri.take(120), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (item.latency == null) {
                                                (item.dnsLatency?.let { "DNS " + it + " ms · " } ?: "") + item.note
                                            } else {
                                                "DNS " + (item.dnsLatency?.toString() ?: "—") + " ms · TCP " + item.latency + " ms · " + item.note
                                            },
                                            color = when { item.latency == null -> MaterialTheme.colorScheme.error; item.latency <= 150 -> Color(0xFF00A86B); item.latency <= 300 -> Color(0xFFB8860B); else -> Color(0xFFC62828) })
                                        if (item.latency != null || hasAnyCdnSni) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = {
                                                    val configToImport = when {
                                                        hasAnyCdnSni && hasCdnSni -> rewriteTlsConfig(item.uri, requestedEdgeIp, requestedSni)
                                                        hasAnyCdnSni -> null
                                                        else -> item.uri
                                                    }
                                                    if (configToImport == null) {
                                                        message = if (hasAnyCdnSni && !hasCdnSni) {
                                                            "IP یا SNI نامعتبر است؛ به صفحهٔ قبل برگرد و هر دو مقدار را بررسی کن."
                                                        } else {
                                                            "این نتیجه کانفیگ VLESS/Trojan با TLS سازگار برای اعمال CDN/SNI ندارد؛ کانفیگ اصلی تغییر نکرد."
                                                        }
                                                    } else {
                                                        startActivity(Intent(this@RadarConfigActivity, MainActivity::class.java).apply {
                                                            action = "com.aras.client.action.IMPORT_RADAR_CONFIG"
                                                            putExtra("radar_config", configToImport)
                                                            putExtra("radar_auto_connect", true)
                                                        })
                                                        finish()
                                                    }
                                                }) { Text(if (hasCdnSni) "اعمال CDN/SNI و افزودن" else "افزودن برای تست واقعی") }
                                                OutlinedButton(onClick = {
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_SUBJECT, "G-ST-VPN · کانفیگ رادار")
                                                        putExtra(Intent.EXTRA_TEXT, item.uri)
                                                    }
                                                    startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری کانفیگ"))
                                                }) { Text("اشتراک‌گذاری") }
                                            }
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
                        val discovered = withContext(Dispatchers.IO) { discover().distinctBy { it.first } }
                        var reachable = 0
                        val bounded = discovered.take(180)
                        if (bounded.isEmpty()) {
                            message = "منابع پاسخ ندادند یا کانفیگ قابل‌خواندن پیدا نشد؛ اتصال اینترنت و دسترسی به GitHub را بررسی کن."
                        } else if (discovered.size > bounded.size) {
                            message = "تعداد زیادی نتیجه پیدا شد؛ برای حفظ سرعت، ۱۸۰ مورد اول بررسی می‌شوند."
                        }
                        var checked = 0
                        for (batch in bounded.chunked(8)) {
                            if (!scanning) break
                            val batchResults = coroutineScope {
                                batch.map { candidate ->
                                    async(Dispatchers.IO) {
                                        val uri = candidate.first
                                        val country = candidate.second
                                        val source = candidate.third
                                        val original = testTcp(uri).copy(country = country, source = source)
                                        if (original.latency != null || !hasCdnSni) {
                                            original
                                        } else {
                                            val recoveredUri = rewriteTlsConfig(uri, requestedEdgeIp, requestedSni)
                                            if (recoveredUri == null) {
                                                original.copy(note = original.note + " · CDN/SNI روی این نوع کانفیگ قابل‌اعمال نیست")
                                            } else {
                                                val retry = testTcp(recoveredUri).copy(country = country, source = source)
                                                if (retry.latency != null) {
                                                    retry.copy(
                                                        note = "CDN/SNI recovery · پاسخ TCP؛ سلامت تونل هنوز تأیید نشده"
                                                    )
                                                } else {
                                                    original.copy(
                                                        note = original.note + " · تلاش CDN/SNI هم پاسخ نداد"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }.map { it.await() }
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

    private fun discover(): List<Triple<String, String?, String?>> {
        // Every configured country feed and every repository is queried. Do not stop
        // after the first popular feed: that used to silently skip the other requested sources.
        val buckets = ArrayList<List<Triple<String, String?, String?>>>()

        val countryPool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val futures = radarCountrySources.map { source ->
                countryPool.submit<List<Triple<String, String?, String?>>> {
                    runCatching {
                        extract(get(source.url, 12000))
                            .distinct()
                            .take(120)
                            .map { uri -> Triple(uri, source.country, source.name) }
                    }.getOrDefault(emptyList())
                }
            }
            futures.forEach { future ->
                runCatching { future.get() }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(buckets::add)
            }
        } finally {
            countryPool.shutdownNow()
        }

        // Keep all nine user-requested repositories, plus the original discovery fallbacks.
        // Each repository contributes its own bounded bucket so one large feed cannot starve
        // the rest. Repository-tree failures are isolated and do not abort the scan.
        val repos = listOf(
            "miladtahanian/V2RayScrapeByCountry",
            "SoliSpirit/v2ray-configs",
            "Delta-Kronecker/V2ray-Config",
            "Danialsamadi/v2go",
            "morpheusadam/v2ray-config",
            "MatinGhanbari/v2ray-configs",
            "0xRadikal/Free-v2ray-Configs",
            "drmikecrypto/PulseConfigs",
            "rtwo2/FastNodes",
            "barry-far/V2ray-Config",
            "ircfspace/tconfig",
            "Argh94/V2RayAutoConfig",
        )
        val repoPool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val futures = repos.map { repo ->
                repoPool.submit<List<Triple<String, String?, String?>>> {
                    runCatching {
                        val metadata = get("https://api.github.com/repos/" + repo, 10000)
                        val branch = Regex("""default_branch"\s*:\s*"([^"]+)""")
                            .find(metadata)?.groupValues?.get(1) ?: "main"
                        val tree = get(
                            "https://api.github.com/repos/" + repo + "/git/trees/" + branch + "?recursive=1",
                            10000,
                        )
                        val paths = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"")
                            .findAll(tree)
                            .map { it.groupValues[1] }
                            .filter { p ->
                                val q = p.lowercase()
                                (q.endsWith(".txt") || q.endsWith(".yaml") || q.endsWith(".yml") || q.endsWith(".json")) &&
                                    (q.contains("config") || q.contains("sub") || q.contains("all") || q.contains("country") || q.contains("best") || q.contains("top"))
                            }
                            .sortedBy { p -> if (p.contains("country", ignoreCase = true)) 0 else 1 }
                            .take(10)

                        val repoFound = linkedMapOf<String, Triple<String, String?, String?>>()
                        for (path in paths) {
                            val encoded = path.split("/").joinToString("/") {
                                java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                            }
                            val raw = get(
                                "https://raw.githubusercontent.com/" + repo + "/" + branch + "/" + encoded,
                                8000,
                            )
                            val country = countryFromPath(path)
                            for (uri in extract(raw)) {
                                repoFound.putIfAbsent(uri, Triple(uri, country, repo))
                                if (repoFound.size >= 120) break
                            }
                            if (repoFound.size >= 120) break
                        }
                        repoFound.values.toList()
                    }.getOrDefault(emptyList())
                }
            }
            futures.forEach { future ->
                runCatching { future.get() }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(buckets::add)
            }
        } finally {
            repoPool.shutdownNow()
        }

        // Fair round-robin merge, deduplicated by the full share URI.
        val unique = linkedMapOf<String, Triple<String, String?, String?>>()
        var round = 0
        while (unique.size < 500) {
            var added = false
            for (bucket in buckets) {
                if (round < bucket.size && unique.size < 500) {
                    val candidate = bucket[round]
                    unique.putIfAbsent(candidate.first, candidate)
                    added = true
                }
            }
            if (!added) break
            round++
        }
        return unique.values.toList()
    }

    private fun countryFromPath(path: String): String? {
        val normalized = path.lowercase().replace('-', '_')
        return when {
            normalized.contains("united_states") || normalized.contains("/us.") || normalized.contains("_us.") -> "آمریکا"
            normalized.contains("germany") || normalized.contains("/de.") || normalized.contains("_de.") -> "آلمان"
            normalized.contains("turkiye") || normalized.contains("türkiye") || normalized.contains("turkey") || normalized.contains("/tr.") -> "ترکیه"
            normalized.contains("south_korea") || normalized.contains("korea") || normalized.contains("/kr.") -> "کره جنوبی"
            normalized.contains("russia") || normalized.contains("/ru.") -> "روسیه"
            normalized.contains("france") || normalized.contains("/fr.") -> "فرانسه"
            normalized.contains("netherlands") || normalized.contains("/nl.") -> "هلند"
            normalized.contains("japan") || normalized.contains("/jp.") -> "ژاپن"
            normalized.contains("singapore") || normalized.contains("/sg.") -> "سنگاپور"
            normalized.contains("iran") || normalized.contains("/ir.") -> "ایران"
            else -> null
        }
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


    /**
     * Apply CDN/SNI only to VLESS/Trojan links that already explicitly use TLS.
     * This preserves credentials and does not pretend arbitrary dead servers can be repaired.
     */
    private fun rewriteTlsConfig(raw: String, edgeIp: String, sni: String): String? {
        if (CdnSniConfig.parseIp(edgeIp) == null || CdnSniConfig.parseSni(sni) == null) return null
        val scheme = raw.substringBefore("://", "").lowercase()
        if (scheme != "vless" && scheme != "trojan") return null
        val beforeFragment = raw.substringBefore("#")
        val fragment = raw.substringAfter("#", "").let { if (raw.contains("#")) "#$it" else "" }
        if (!beforeFragment.contains("?")) return null
        val base = beforeFragment.substringBefore("?")
        val query = beforeFragment.substringAfter("?")
        val params = query.split("&").filter { it.isNotBlank() }.map { part ->
            val key = java.net.URLDecoder.decode(part.substringBefore("="), "UTF-8")
            val value = java.net.URLDecoder.decode(part.substringAfter("=", ""), "UTF-8")
            key to value
        }.toMutableList()
        val security = params.lastOrNull { it.first.equals("security", true) }?.second?.lowercase()
        if (security != "tls") return null
        if (params.any { it.first.equals("type", true) && it.second.equals("reality", true) }) return null
        fun setParam(key: String, value: String) {
            params.removeAll { it.first.equals(key, true) }
            params.add(key to value)
        }
        setParam("sni", sni)
        val network = params.lastOrNull { it.first.equals("type", true) }?.second?.lowercase()
        if (network == "ws") setParam("host", sni)
        val authorityPattern = Regex("^((?:vless|trojan)://(?:[^@/?#]+@)?)([^:/?#]+)(:\\d+)?", RegexOption.IGNORE_CASE)
        if (!authorityPattern.containsMatchIn(base)) return null
        val authorityMatch = authorityPattern.find(base) ?: return null
        val rewrittenBase = buildString {
            append(base.substring(0, authorityMatch.range.first))
            append(authorityMatch.groupValues[1])
            append(edgeIp)
            append(authorityMatch.groupValues[3])
            append(base.substring(authorityMatch.range.last + 1))
        }
        val rewrittenQuery = params.joinToString("&") {
            java.net.URLEncoder.encode(it.first, "UTF-8") + "=" +
                java.net.URLEncoder.encode(it.second, "UTF-8")
        }
        return "$rewrittenBase?$rewrittenQuery$fragment"
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
