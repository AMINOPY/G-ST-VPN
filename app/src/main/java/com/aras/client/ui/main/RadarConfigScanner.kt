package com.aras.client.ui.main

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aras.client.AppConfig
import com.aras.client.dto.RealPingEvent
import com.aras.client.handler.AngConfigManager
import com.aras.client.handler.MmkvManager
import com.aras.client.service.RealPingWorkerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

private data class RadarSource(val owner: String, val repo: String) { val name get() = "$owner/$repo" }
private data class RadarCandidate(val source: String, val path: String, val url: String)
private data class RadarResult(val guid: String, val name: String, val delayMs: Long)

private val radarSources = listOf(
    RadarSource("mahxray", "Free-SUB-Link"), RadarSource("0xRadikal", "Free-v2ray-Configs"),
    RadarSource("itsyebekhe", "PSG"), RadarSource("Delta-Kronecker", "V2ray-Config"),
    RadarSource("mahsanet", "MahsaFreeConfig"), RadarSource("iampedii", "whitedns-sub"),
    RadarSource("4n0nymou3", "multi-proxy-config-fetcher"), RadarSource("roosterkid", "openproxylist"),
    RadarSource("arshiacomplus", "v2rayExtractor"), RadarSource("ShadowException", "VPN"),
    RadarSource("zieng2", "wl"), RadarSource("v2FreeHub", "v2hub-configs"),
    RadarSource("prominbro", "sub"), RadarSource("Mahdi0024", "ProxyCollector"),
    RadarSource("luxxuria", "harvester"), RadarSource("barry-far", "v2ray-config"),
    RadarSource("Epodonios", "v2ray-configs"), RadarSource("ebrasha", "free-v2ray-public-list"),
    RadarSource("MatinGhanbari", "v2ray-configs"), RadarSource("SoliSpirit", "v2ray-configs"),
    RadarSource("ripaojiedian", "freenode"), RadarSource("Pawdroid", "Free-servers"),
    RadarSource("Leon406", "SubCrawler"), RadarSource("ermaozi", "get_subscribe"),
    RadarSource("peasoft", "NoMoreWalls"), RadarSource("patterniha", "free-configs"),
    RadarSource("ircfspace", "tconfig")
)

@Composable
fun RadarConfigDialog(context: Context, onDismiss: () -> Unit, onSelectBest: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val results = remember { mutableStateListOf<RadarResult>() }
    var running by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("آمادهٔ جست‌وجو در منابع عمومی") }
    var discovered by remember { mutableIntStateOf(0) }
    var imported by remember { mutableIntStateOf(0) }
    var tested by remember { mutableIntStateOf(0) }
    var bestGuid by remember { mutableStateOf<String?>(null) }
    var worker by remember { mutableStateOf<RealPingWorkerService?>(null) }

    DisposableEffect(Unit) { onDispose { worker?.cancel() } }

    fun startScan() {
        if (running || testing) return
        running = true
        results.clear()
        discovered = 0
        imported = 0
        tested = 0
        bestGuid = null
        status = "در حال بررسی منابع عمومی کانفیگ…"
        scope.launch {
            try {
                val importedGuids = scanAndImport { message, found ->
                    status = message
                    discovered = found
                }
                imported = importedGuids.size
                if (importedGuids.isEmpty()) {
                    status = "کانفیگ قابل‌واردکردنی پیدا نشد؛ منابع ممکن است موقتاً در دسترس نباشند."
                    running = false
                    return@launch
                }
                status = "پیدا شد: $imported کانفیگ؛ تست واقعی تونل و پینگ آغاز می‌شود…"
                testing = true
                running = false
                val service = RealPingWorkerService(context, importedGuids) { event ->
                    Handler(Looper.getMainLooper()).post {
                        when (event) {
                            is RealPingEvent.Progress -> status = "در حال تست کانفیگ‌ها…"
                            is RealPingEvent.Result -> {
                                val name = MmkvManager.decodeServerConfig(event.guid)?.remarks
                                    ?.takeIf { it.isNotBlank() } ?: "کانفیگ"
                                results.removeAll { it.guid == event.guid }
                                results.add(RadarResult(event.guid, name, event.delayMillis))
                                tested = results.size
                            }
                            is RealPingEvent.Finish -> {
                                testing = false
                                val best = results.filter { it.delayMs > 0L }.minByOrNull { it.delayMs }
                                bestGuid = best?.guid
                                status = if (best != null) {
                                    "تست تمام شد؛ بهترین پینگ ثبت‌شده ${best.delayMs} ms است."
                                } else "هیچ‌کدام از کانفیگ‌ها تست موفق نداشتند."
                            }
                        }
                    }
                }
                worker = service
                service.start()
            } catch (e: Exception) {
                running = false
                testing = false
                status = "جست‌وجو کامل نشد: ${e.message ?: "خطای شبکه"}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رادار کانفیگ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "منابع عمومی بررسی می‌شوند؛ کانفیگ‌های قابل‌خواندن وارد برنامه شده و با تست واقعی تأخیر تونل ارزیابی می‌شوند.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                        Text("منابع: ${radarSources.size} • کانفیگ واردشده: $imported • تست‌شده: $tested",
                            style = MaterialTheme.typography.labelMedium)
                        if (running || testing) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator()
                                Text(if (running) "جست‌وجو و بررسی منابع…" else "تست پینگ و سلامت تونل…")
                            }
                        }
                    }
                }
                if (results.isNotEmpty()) {
                    Text("نتایج تست", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(modifier = Modifier.heightIn(max = 190.dp)) {
                        items(results.sortedWith(compareBy<RadarResult> { if (it.delayMs > 0L) 0 else 1 }.thenBy { it.delayMs })) { result ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(result.name, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(
                                    if (result.delayMs > 0L) "${result.delayMs} ms" else "ناموفق",
                                    color = if (result.delayMs > 0L) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                Text(
                    "انتخاب بهترین مورد بر اساس پینگ موفق انجام می‌شود؛ پینگ به‌تنهایی معیار قطعی سرعت دانلود یا پایداری بلندمدت نیست.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            if (bestGuid != null && !testing && !running) {
                Button(onClick = { bestGuid?.let(onSelectBest) }) { Text("انتخاب بهترین کانفیگ") }
            } else {
                Button(onClick = ::startScan, enabled = !running && !testing) {
                    Text(if (results.isEmpty()) "شروع اسکن" else "اسکن دوباره")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

private suspend fun scanAndImport(onProgress: (String, Int) -> Unit): List<String> = withContext(Dispatchers.IO) {
    val treeGate = Semaphore(5)
    val candidates = mutableListOf<RadarCandidate>()
    val progress = AtomicInteger(0)
    radarSources.chunked(5).forEach { batch ->
        candidates += coroutineScope {
            batch.map { source ->
                async {
                    treeGate.withPermit {
                        val index = progress.incrementAndGet()
                        onProgress("در حال بررسی منبع $index از ${radarSources.size}: ${source.repo}", index)
                        findCandidateFiles(source)
                    }
                }
            }.flatMap { it.await() }
        }
    }
    candidates += RadarCandidate("openproxylist.com", "v2ray", "https://openproxylist.com/v2ray")

    val contentGate = Semaphore(6)
    val fetched = candidates.distinctBy { it.url }.take(45).chunked(6).flatMap { batch ->
        coroutineScope {
            batch.map { candidate ->
                async {
                    contentGate.withPermit {
                        candidate to runCatching { httpGet(candidate.url, 1_000_000) }.getOrNull()
                    }
                }
            }.mapNotNull { deferred ->
                val (candidate, body) = deferred.await()
                if (body != null && looksLikeConfigPayload(body)) candidate to body else null
            }
        }
    }

    val newGuids = linkedSetOf<String>()
    var lastKnown = MmkvManager.decodeServerList(AppConfig.DEFAULT_SUBSCRIPTION_ID).toSet()
    fetched.forEachIndexed { index, pair ->
        onProgress("در حال وارد کردن موارد معتبر: ${index + 1} از ${fetched.size}", index + 1)
        runCatching {
            val result = AngConfigManager.importBatchConfig(pair.second, AppConfig.DEFAULT_SUBSCRIPTION_ID, true)
            if (result.configCount > 0) {
                val current = MmkvManager.decodeServerList(AppConfig.DEFAULT_SUBSCRIPTION_ID).toSet()
                newGuids += current - lastKnown
                lastKnown = current
            }
        }
    }
    newGuids.toList()
}

private fun findCandidateFiles(source: RadarSource): List<RadarCandidate> = try {
    val api = "https://api.github.com/repos/${source.owner}/${source.repo}/git/trees/HEAD?recursive=1"
    val json = JSONObject(httpGet(api, 2_000_000))
    val tree = json.optJSONArray("tree") ?: return emptyList()
    val candidates = mutableListOf<Pair<Int, String>>()
    for (i in 0 until tree.length()) {
        val item = tree.optJSONObject(i) ?: continue
        if (item.optString("type") != "blob") continue
        val path = item.optString("path")
        val lower = path.lowercase()
        val extOk = listOf(".txt", ".yaml", ".yml", ".json", ".conf", ".list", ".sub", ".base64").any(lower::endsWith)
        if (!extOk || lower.contains("readme") || lower.contains("license") ||
            lower.contains("workflow") || lower.contains("test/") || lower.contains("example")) continue
        val score = when {
            "all" in lower && ("config" in lower || "node" in lower || "sub" in lower) -> 6
            "sub" in lower || "subscribe" in lower -> 5
            "config" in lower || "v2ray" in lower || "proxy" in lower -> 4
            "node" in lower || "free" in lower || "base64" in lower -> 3
            else -> 0
        }
        if (score > 0) candidates += score to path
    }
    candidates.sortedWith(compareByDescending<Pair<Int, String>> { it.first }.thenBy { it.second.length })
        .take(2)
        .map { (_, path) ->
            val encodedPath = path.split("/").joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
            RadarCandidate(source.name, path,
                "https://raw.githubusercontent.com/${source.owner}/${source.repo}/HEAD/$encodedPath")
        }
} catch (_: Exception) { emptyList() }

private fun looksLikeConfigPayload(body: String): Boolean {
    val lower = body.lowercase()
    if (listOf("vmess://", "vless://", "trojan://", "ss://", "socks://", "hysteria2://", "hy2://", "wireguard://", "tuic://", "anytls://")
            .any(lower::contains)) return true
    if (lower.contains("proxies:") && (lower.contains("server:") || lower.contains("type:"))) return true
    val compact = body.trim().replace("\\s".toRegex(), "")
    return compact.length > 120 && compact.matches(Regex("[A-Za-z0-9+/=_-]+"))
}

private fun httpGet(url: String, maxChars: Int): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000
        connection.setRequestProperty("User-Agent", "G-ST-VPN-RadarConfig/1.0")
        connection.setRequestProperty("Accept", "application/vnd.github+json, text/plain, */*")
        connection.instanceFollowRedirects = true
        if (connection.responseCode !in 200..299) return ""
        val stream = connection.inputStream.buffered()
        val output = StringBuilder()
        val buffer = ByteArray(8192)
        var total = 0
        while (total < maxChars) {
            val read = stream.read(buffer, 0, minOf(buffer.size, maxChars - total))
            if (read <= 0) break
            output.append(String(buffer, 0, read, Charsets.UTF_8))
            total += read
        }
        stream.close()
        return output.toString()
    } finally { connection.disconnect() }
}
