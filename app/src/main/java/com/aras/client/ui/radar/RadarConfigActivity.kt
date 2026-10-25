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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private data class RadarCandidate(
    val uri: String,
    val latency: Long?,
    val note: String,
    val dnsLatency: Long? = null,
    val country: String? = null,
    val serverIp: String? = null,
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
                var countryFilter by remember { mutableStateOf("all") }
                var scanLimit by remember { mutableIntStateOf(1000) }
                val availableCountries = results.mapNotNull { it.country }.distinct().sorted()
                val hasUnknownCountry = results.any { it.country.isNullOrBlank() }
                val visibleResults = results.filter { candidate ->
                    val reachabilityMatches = when (resultFilter) {
                        "reachable" -> candidate.latency != null
                        "failed" -> candidate.latency == null
                        else -> true
                    }
                    val protocolMatches = protocolFilter == "all" || protocolOf(candidate.uri) == protocolFilter
                    val countryMatches = when (countryFilter) {
                        "all" -> true
                        "__unknown__" -> candidate.country.isNullOrBlank()
                        else -> candidate.country == countryFilter
                    }
                    reachabilityMatches && protocolMatches && countryMatches
                }
                Scaffold(topBar = { TopAppBar(title = { Text("رادار کانفیگ") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AndroidView(
                            factory = { ctx -> RadarScanView(ctx).also { radar = it; it.startScan() } },
                            modifier = Modifier.fillMaxWidth().height(230.dp),
                            update = { if (scanning) it.startScan() else it.stopScan() }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !scanning, onClick = { results.clear(); radar?.clearBlips(); scanning = true; message = "در حال دریافت و بررسی منابع…" }) { Text("شروع اسکن") }
                            OutlinedButton(enabled = scanning, onClick = { scanning = false }) { Text("توقف") }
                        }
                        OutlinedButton(
                            enabled = !scanning,
                            onClick = { startActivity(Intent(this@RadarConfigActivity, TelegramProxyActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("پروکسی تلگرام · جست‌وجوی MTProto") }
                        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("تعداد:", modifier = Modifier.padding(top = 10.dp))
                            listOf(100, 200, 400, 800, 1000, 2000, 4000).forEach { limit ->
                                FilterChip(selected = scanLimit == limit, onClick = { scanLimit = limit }, enabled = !scanning, label = { Text(limit.toString()) })
                            }
                        }
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
                        Text("کشور سرور بر اساس IP", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "کشور از IP عمومی میزبان تخمین زده می‌شود؛ برای IPهای خصوصی، نامشخص یا سرویس جغرافیایی در دسترس‌نباشد، کشور نمایش داده نمی‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = countryFilter == "all",
                                    onClick = { countryFilter = "all" },
                                    label = { Text("همه کشورها (${results.size})") }
                                )
                            }
                            items(availableCountries.size) { index ->
                                val country = availableCountries[index]
                                val count = results.count { it.country == country }
                                FilterChip(
                                    selected = countryFilter == country,
                                    onClick = { countryFilter = country },
                                    label = { Text("${country} (${count})") }
                                )
                            }
                            if (hasUnknownCountry) {
                                item {
                                    FilterChip(
                                        selected = countryFilter == "__unknown__",
                                        onClick = { countryFilter = "__unknown__" },
                                        label = { Text("نامشخص (${results.count { it.country.isNullOrBlank() }})") }
                                    )
                                }
                            }
                        }
                        // The screen already owns the vertical scroll container. Use a regular
                        // Column here rather than nesting a LazyColumn inside verticalScroll;
                        // the nested lazy list received unbounded height and hid lower configs.
                        if (visibleResults.size > 180) {
                            Text(
                                "۱۸۰ نتیجهٔ برتر از " + visibleResults.size + " مورد نمایش داده می‌شود؛ ترتیب بر اساس کمترین تأخیر TCP است.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            visibleResults.take(180).forEach { item ->
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
                                            "کشور بر اساس IP: " + (item.country ?: "نامشخص") +
                                                " · IP: " + (item.serverIp ?: "نامشخص") +
                                                " · پروتکل: " + protocolOf(item.uri).uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
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
                        message = "در حال جمع‌آوری منابع؛ بعد از آن بهترین نتایج رتبه‌بندی می‌شوند…"
                        val uris = withContext(Dispatchers.IO) { discover().distinct() }
                        var reachable = 0
                        var checked = 0
                        val bounded = uris.take(scanLimit)
                        if (bounded.isEmpty()) {
                            message = "منابع پاسخ ندادند یا کانفیگ قابل‌خواندن پیدا نشد؛ اتصال اینترنت و دسترسی به GitHub را بررسی کن."
                        } else if (uris.size > bounded.size) {
                            message = "نتیجه‌های زیادی پیدا شد؛ تعداد انتخاب‌شده بررسی می‌شود."
                        }
                        for (batch in bounded.chunked(8)) {
                            if (!scanning) break
                            val batchResults = coroutineScope {
                                batch.map { uri -> async(Dispatchers.IO) { testTcp(uri) } }.map { it.await() }
                            }
                            for (result in batchResults) {
                                if (result.latency != null) reachable++
                                // Keep reachable candidates first, but retain failed candidates too so
                                // the "بدون پاسخ" filter works and users can inspect failed sources.
                                results.add(result)
                                val ranked = results
                                    .distinctBy { it.uri }
                                    .sortedWith(compareBy<RadarCandidate> { it.latency == null }
                                        .thenBy { it.latency ?: Long.MAX_VALUE })
                                    .take(180)
                                results.clear()
                                results.addAll(ranked)
                                val savedCoreRdy = RadarRdyResultStore.read(result.uri)
                                val corePing = savedCoreRdy?.removePrefix("RESULT:")?.substringBefore(":")?.toLongOrNull()
                                if (corePing != null) radar?.addCoreRdyCandidate(corePing) else radar?.addDiscoveryCandidate(result.latency != null)
                                checked++
                            }
                            message = "بررسی " + checked + "/" + bounded.size + " · پاسخ TCP: " + reachable + " · نمایش بهترین‌ها: " + results.size
                        }
                        if (scanning) {
                            message = if (checked == 0) {
                                "اسکن پایان یافت، اما نتیجه‌ای برای آزمایش پیدا نشد."
                            } else {
                                "پایان جست‌وجو: " + checked + " کانفیگ بررسی شد؛ " + reachable +
                                    " پاسخ TCP داشتند. بهترین " + results.size + " مورد بر اساس تأخیر مرتب شده‌اند. TCP به‌تنهایی تأیید سلامت تونل نیست."
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
        val priorityRepos = listOf(
            "miladtahanian/V2RayScrapeByCountry",
            "SoliSpirit/v2ray-configs",
            "Delta-Kronecker/V2ray-Config",
            "Danialsamadi/v2go",
            "morpheusadam/v2ray-config",
            "MatinGhanbari/v2ray-configs",
            "0xRadikal/Free-v2ray-Configs",
            "drmikecrypto/PulseConfigs",
            "rtwo2/FastNodes"
        )
        val globalRepos = listOf(
            "barry-far/V2ray-Config", "0xRadikal/Free-v2ray-Configs", "ircfspace/tconfig",
            "Argh94/V2RayAutoConfig", "mahdibland/V2RayAggregator", "peasoft/NoMorewalls",
            "Leon406/SubCrawler", "Pawdroid/Free-servers", "ermaozi/get_subscribe",
            "aiboboxx/v2rayfree", "zxcvos/Free-Node", "Alvin9999/new-pac",
            "soroushmirzaei/telegram-configs-collector", "yaney01/V2ray-Configs",
            "morpheusadam/v2ray-config", "alexantSWE/V2ray-Config", "Delta-Kronecker/V2ray-Config",
            "coldwater-10/V2ray-Config", "sevcator/5ubscrpt10n", "TheCrowCreature/v2rayExtractor",
            "10Dream/sub-mod", "mheidari98/.proxy", "itsyebekhe/PSG", "Surfboardv2ray/TGParse",
            "MatinGhanbari/v2ray-configs", "MhdiTaheri/V2rayCollector", "4n0nymous/multi-proxy-config-fetcher",
            "Rayan-Config/C-Sub", "V2RayRoot/V2RayConfig", "free-nodes/v2rayfree",
            "Mokafela/Config-Finder", "nodesradar/nodesradar-en", "Epodonios/v2ray-configs",
            "TheSpeedX/PROXY-List", "hiddify/hiddify-config", "2dust/v2rayNG",
            "barry-far/V2ray-Config", "vpn-vless-configs-russia/vpn-vless-configs-russia",
            "Farid-Karimi/Config-Collector", "kort0881/vpn-vless-configs-russia"
        )
        val iranianRepos = listOf(
            "ircfspace/tconfig", "itsyebekhe/PSG", "MatinGhanbari/v2ray-configs",
            "MhdiTaheri/V2rayCollector", "Rayan-Config/C-Sub", "soroushmirzaei/telegram-configs-collector",
            "Surfboardv2ray/TGParse", "mheidari98/.proxy", "V2RayRoot/V2RayConfig",
            "4n0nymous/multi-proxy-config-fetcher"
        )
        // Bound sequential GitHub crawling to reduce long stalls and memory pressure on phones.
        val repos = (priorityRepos + iranianRepos + globalRepos).distinct().take(15)
        val found = linkedSetOf<String>()

        // Public country-split feeds; the server IP is still geolocated independently below.
        // Missing or renamed files are skipped so one source never blocks the rest.
        val countryCodes = listOf(
            "IR", "DE", "US", "NL", "FR", "GB", "TR", "AE", "RU", "JP",
            "SG", "CA", "CH", "SE", "FI", "PL", "IT", "ES", "AT", "BE",
            "CZ", "RO", "UA", "IN", "KR", "TW", "HK", "ID", "TH", "BR",
            "AU", "ZA", "IL", "IQ", "AM", "AZ", "GE", "KZ", "NO", "DK"
        )
        val countryFeeds = buildList {
            // User-requested public source feeds; unavailable paths are skipped independently.
            add("https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/United_States.txt")
            add("https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Germany.txt")
            add("https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/T%C3%BCrkiye.txt")
            add("https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/South_Korea.txt")
            add("https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/Russia.txt")
            add("https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/United_States.txt")
            add("https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Germany.txt")
            add("https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/T%C3%BCrkiye.txt")
            add("https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/South_Korea.txt")
            add("https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Russia.txt")
            add("https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/us.txt")
            add("https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/de.txt")
            add("https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/tr.txt")
            add("https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/kr.txt")
            add("https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/ru.txt")
            add("https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/US.txt")
            add("https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/DE.txt")
            add("https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/TR.txt")
            add("https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/KR.txt")
            add("https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/RU.txt")
            add("https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/reality.txt")
            add("https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/best.txt")
            add("https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/iran.txt")
            add("https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/main/subscriptions/filtered/subs/hysteria2.txt")
            add("https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/fast/configs.txt")
            add("https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt")
            add("https://raw.githubusercontent.com/drmikecrypto/PulseConfigs/main/top5.txt")
            add("https://raw.githubusercontent.com/drmikecrypto/PulseConfigs/main/top5_speed.txt")
            add("https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/Best-Results/top100.txt")
            add("https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/Best-Results/top500.txt")
            add("https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/everything.txt")
            add("https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/verified.txt")
            add("https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/curated.txt")
            for (code in countryCodes) {
                add("https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/countries/" + code + ".txt")
                add("https://raw.githubusercontent.com/Au1rxx/free-vpn-subscriptions/main/output/v2ray-base64-" + code + ".txt")
            }
            add("https://raw.githubusercontent.com/Au1rxx/free-vpn-subscriptions/main/output/v2ray-base64.txt")
            add("https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/iran.txt")
            add("https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/all.txt")
            add("https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/best.txt")
        }.distinct()

        // Fetch all configured feeds in small concurrent batches.
        val feedResults = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.coroutineScope {
                countryFeeds.chunked(8).flatMap { batch ->
                    val feedScope = this
                    batch.map { feed ->
                        feedScope.async(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { extract(get(feed, 4500)) }.getOrDefault(emptyList())
                        }
                    }.awaitAll()
                }
            }
        }
        feedResults.forEach { batch -> batch.forEach { found.add(it) } }
        if (found.size >= 4000) return found.toList()

        for (repo in repos.take(6)) try {
            val metadata = get("https://api.github.com/repos/" + repo, 10000)
            val branch = Regex("""default_branch"\s*:\s*"([^"]+)""").find(metadata)?.groupValues?.get(1) ?: "main"
            val tree = get("https://api.github.com/repos/" + repo + "/git/trees/" + branch + "?recursive=1", 10000)
            val paths = Regex(""" "path"\s*:\s*"([^"]+)" """).findAll(tree).map { it.groupValues[1] }
                .filter { p -> val q = p.lowercase(); (q.endsWith(".txt") || q.endsWith(".yaml") || q.endsWith(".yml") || q.endsWith(".json")) && (q.contains("config") || q.contains("sub") || q.contains("all") || q.contains("country")) }
                .take(12)
            for (path in paths) {
                val raw = get("https://raw.githubusercontent.com/" + repo + "/" + branch + "/" + path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }, 8000)
                extract(raw).forEach { found.add(it) }
                if (found.size >= 4000) return found.toList()
            }
        } catch (_: Exception) { /* Other sources may still work. */ }
        return found.toList()
    }

    private fun extract(text: String): List<String> {
        val pattern = Regex("""(?:vmess|vless|trojan|ss|hy2|hysteria2|tuic|socks)://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        var candidate = text
        repeat(3) {
            val found = pattern.findAll(candidate).map { it.value.trimEnd(',', ';', ')', ']', '}') }.distinct().toList()
            if (found.isNotEmpty()) return found
            val compact = candidate.filterNot { it.isWhitespace() }
            if (compact.length !in 24..2_000_000) return emptyList()
            val bytes = runCatching { Base64.decode(compact, Base64.DEFAULT) }
                .recoverCatching { Base64.decode(compact, Base64.URL_SAFE or Base64.NO_WRAP) }
                .getOrNull() ?: return emptyList()
            val decoded = bytes.toString(Charsets.UTF_8)
            if (decoded == candidate) return emptyList()
            candidate = decoded
        }
        return pattern.findAll(candidate).map { it.value.trimEnd(',', ';', ')', ']', '}') }.distinct().toList()
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
                    country = lookupCountry(uri),
                    serverIp = resolveServerIp(uri),
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
        return RadarCandidate(
            uri = uri,
            latency = null,
            note = detail,
            dnsLatency = dnsMs,
            country = lookupCountry(uri),
            serverIp = resolveServerIp(uri),
        )
    }

    private val geoCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val resolvedIpCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun resolveServerIp(uri: String): String? = runCatching {
        val endpoint = parseEndpoint(uri) ?: return null
        val host = endpoint.first
        resolvedIpCache[host]?.let { return it }
        val ip = InetAddress.getAllByName(host).firstOrNull()?.hostAddress?.substringBefore("%")
            ?: return null
        if (resolvedIpCache.size < 500) resolvedIpCache.putIfAbsent(host, ip)
        ip
    }.getOrNull()

    private fun lookupCountry(uri: String): String? = runCatching {
        val address = resolveServerIp(uri) ?: return null
        geoCache[address]?.let { return it }
        if (geoCache.size >= 200) return null
        // Query public IP-only endpoints over HTTPS. Never send the config URI or credentials.
        // A second provider is used only when the first is unavailable or returns no country.
        val encodedIp = java.net.URLEncoder.encode(address, "UTF-8")
        val primary = runCatching { get("https://ipwho.is/" + encodedIp, 3500) }.getOrNull()
        val primaryCountry = primary
            ?.takeUnless { Regex("\"success\"\\s*:\\s*false", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            ?.let { Regex("\"country\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val fallbackCountry = if (primaryCountry.isNullOrBlank()) {
            runCatching {
                val json = get("https://api.ipapi.is/?q=" + encodedIp, 3500)
                Regex("\"country\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(json)?.groupValues?.get(1)
                    ?: Regex("\"country_name\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(json)?.groupValues?.get(1)
            }.getOrNull()
        } else null
        val country = primaryCountry?.takeIf { it.isNotBlank() } ?: fallbackCountry
        if (!country.isNullOrBlank()) geoCache.putIfAbsent(address, country)
        country
    }.getOrNull()

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
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "G-ST-VPN-Radar/1.0")
        c.setRequestProperty("Accept-Encoding", "identity")
        return try {
            val code = c.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            c.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(8192)
                val maxChars = 1_000_000
                while (out.length < maxChars) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, maxChars - out.length))
                    if (count < 0) break
                    out.append(buffer, 0, count)
                }
                out.toString()
            }
        } finally {
            c.disconnect()
        }
    }
}
