package com.aras.client.ui.radar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aras.client.ui.compose.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder

private data class TelegramProxyResult(
    val link: String,
    val server: String,
    val port: Int,
    val pingMs: Long,
    val source: String
)

/**
 * Telegram-only proxy discovery. Sources used here publish MTProto links as verified lists;
 * a TCP probe is an additional reachability check, not a substitute for Telegram's MTProto handshake.
 */
class TelegramProxyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val context = LocalContext.current
                val results = remember { mutableStateListOf<TelegramProxyResult>() }
                var scanning by remember { mutableStateOf(false) }
                var scanLimit by remember { mutableIntStateOf(200) }
                var message by remember { mutableStateOf("فهرست‌های عمومی پروکسی MTProto آمادهٔ بررسی هستند.") }
                var scanRequest by remember { mutableIntStateOf(0) }

                Scaffold(topBar = { TopAppBar(title = { Text("پروکسی تلگرام") }) }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "فقط لینک‌های پروکسی تلگرام (MTProto) از منابع اختصاصی دریافت می‌شوند؛ کانفیگ‌های VLESS/VMess وارد این فهرست نمی‌شوند.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("تعداد بررسی", style = MaterialTheme.typography.titleSmall)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(100, 200, 400, 800, 1000, 2000, 4000)) { count ->
                                FilterChip(
                                    selected = scanLimit == count,
                                    enabled = !scanning,
                                    onClick = { scanLimit = count },
                                    label = { Text(count.toString()) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !scanning, onClick = {
                                results.clear()
                                message = "در حال دریافت منابع و بررسی دسترسی پروکسی‌ها…"
                                scanning = true
                                scanRequest++
                            }) { Text("شروع جست‌وجو") }
                            OutlinedButton(enabled = scanning, onClick = { scanning = false }) { Text("توقف") }
                        }
                        if (scanning) CircularProgressIndicator()
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                        Text("پروکسی‌های قابل‌استفادهٔ فعلی: ${results.size}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "نکته: منابع منتخب، فهرست‌های خود را به‌عنوان بررسی‌شده منتشر می‌کنند؛ این برنامه علاوه بر آن DNS/TCP را آزمایش می‌کند. بررسی handshake واقعی تلگرام داخل خود اپ هنوز انجام نمی‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(results, key = { it.link }) { item ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(item.server + ":" + item.port, style = MaterialTheme.typography.titleMedium)
                                        Text("پینگ TCP: ${item.pingMs} ms · منبع: ${item.source}", style = MaterialTheme.typography.bodySmall)
                                        Text(item.link, style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Telegram MTProto proxy", item.link))
                                                Toast.makeText(context, "لینک کپی شد", Toast.LENGTH_SHORT).show()
                                            }) { Text("کپی لینک") }
                                            Button(onClick = {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.link))
                                                runCatching { startActivity(intent) }.onFailure {
                                                    Toast.makeText(context, "برنامه‌ای برای باز کردن لینک پیدا نشد", Toast.LENGTH_SHORT).show()
                                                }
                                            }) { Text("باز کردن در تلگرام") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(scanRequest) {
                    if (scanRequest == 0) return@LaunchedEffect
                    try {
                        val found = withContext(Dispatchers.IO) { discoverTelegramProxies().take(scanLimit) }
                        if (found.isEmpty()) {
                            message = "از منابع در دسترس، لینک MTProto قابل‌خواندن پیدا نشد. بعداً دوباره امتحان کن."
                            scanning = false
                            return@LaunchedEffect
                        }
                        var checked = 0
                        var online = 0
                        for (batch in found.chunked(8)) {
                            if (!scanning) break
                            val checkedBatch = coroutineScope {
                                batch.map { candidate ->
                                    async(Dispatchers.IO) { probe(candidate) }
                                }.awaitAll()
                            }
                            checkedBatch.filterNotNull().forEach {
                                results.add(it)
                                online++
                            }
                            checked += batch.size
                            message = "بررسی‌شده: $checked / ${found.size} · پاسخ‌گوی TCP: $online"
                        }
                        if (scanning) {
                            message = "پایان بررسی: $checked لینک بررسی شد؛ $online مورد از آزمون TCP عبور کرد. این نتیجه به‌تنهایی handshake تلگرام را تضمین نمی‌کند."
                        } else {
                            message = "اسکن متوقف شد؛ $checked لینک بررسی شد و $online مورد پاسخ‌گوی TCP بود."
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        message = "خطا در دریافت/بررسی منابع: ${e.message ?: "شبکه"}"
                    } finally {
                        scanning = false
                    }
                }
            }
        }
    }

    private data class Candidate(val link: String, val source: String, val server: String, val port: Int)

    private fun discoverTelegramProxies(): List<Candidate> {
        val feeds = listOf(
            "https://raw.githubusercontent.com/tgmtproxy/telegram-mtproto-proxy-list/main/proxies.txt" to "tgmtproxy",
            "https://raw.githubusercontent.com/tgmtproxy/telegram-mtproto-proxy-list/main/proxies.json" to "tgmtproxy",
            "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt" to "SoliSpirit",
            "https://raw.githubusercontent.com/Argh94/Proxy-List/main/MTProto.txt" to "Argh94",
            "https://raw.githubusercontent.com/Argh94/telegram-proxy-scraper/main/proxies.txt" to "Argh94 scraper"
        )
        val unique = linkedMapOf<String, Candidate>()
        for ((url, source) in feeds) {
            try {
                val body = get(url, 7000)
                val links = Regex(
                    "(?:tg://proxy\\?[^\\s\\\"'<>]+|https?://(?:t\\.me|telegram\\.me|telegram\\.dog)/proxy\\?[^\\s\\\"'<>]+)",
                    RegexOption.IGNORE_CASE
                ).findAll(body).map { it.value.trimEnd(',', ';', ')', ']', '}') }
                for (link in links) {
                    val candidate = parseTelegramLink(link, source) ?: continue
                    unique.putIfAbsent(candidate.link, candidate)
                }
            } catch (_: Exception) {
                // A missing/renamed optional feed must not stop other feeds.
            }
        }
        return unique.values.toList()
    }

    private fun parseTelegramLink(raw: String, source: String): Candidate? = runCatching {
        val query = raw.substringAfter('?')
        val values = query.split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "").lowercase()
            val value = part.substringAfter('=', "")
            if (key.isBlank()) null else key to URLDecoder.decode(value, "UTF-8")
        }.toMap()
        val server = values["server"]?.trim()?.trim('[', ']') ?: return null
        val port = values["port"]?.toIntOrNull() ?: return null
        val secret = values["secret"]?.trim() ?: return null
        if (server.isBlank() || port !in 1..65535 || secret.length < 16) return null
        val normalized = "tg://proxy?server=" + java.net.URLEncoder.encode(server, "UTF-8") +
            "&port=" + port + "&secret=" + java.net.URLEncoder.encode(secret, "UTF-8")
        Candidate(normalized, source, server, port)
    }.getOrNull()

    private fun probe(candidate: Candidate): TelegramProxyResult? = try {
        val started = System.nanoTime()
        val addresses = InetAddress.getAllByName(candidate.server)
        var connected = false
        for (address in addresses) {
            try {
                Socket().use { it.connect(InetSocketAddress(address, candidate.port), 1800) }
                connected = true
                break
            } catch (_: Exception) {
                // Try the next DNS address.
            }
        }
        if (!connected) null else {
            val ping = (System.nanoTime() - started) / 1_000_000L
            TelegramProxyResult(candidate.link, candidate.server, candidate.port, ping, candidate.source)
        }
    } catch (_: Exception) {
        null
    }

    private fun get(url: String, timeoutMs: Int): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("User-Agent", "G-ST-VPN-TelegramProxyRadar/1.0")
        return try {
            connection.inputStream.bufferedReader().use { it.readText().take(2_000_000) }
        } finally {
            connection.disconnect()
        }
    }
}
