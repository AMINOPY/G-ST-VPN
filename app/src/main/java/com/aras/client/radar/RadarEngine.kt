package com.aras.client.radar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.CertificateException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class RadarStatus { OK, TLS_FAILED, UDP_UNVERIFIED, FAILED }

data class RadarResult(
    val uri: String,
    val endpoint: RadarEndpoint?,
    val source: RadarSource,
    val status: RadarStatus,
    val dnsMs: Long?,
    val tcpMs: Long?,
    val tlsMs: Long?,
    val note: String,
    val score: Int,
)

sealed interface RadarEvent {
    data class SourceDone(val source: RadarSource, val count: Int, val error: String?, val fromCache: Boolean) : RadarEvent
    data class Planned(val total: Int, val uniqueFound: Int) : RadarEvent
    data class Result(val result: RadarResult) : RadarEvent
    data class Progress(val checked: Int, val total: Int) : RadarEvent
}

/**
 * موتور رادار کانفیگ:
 *  ۱) دریافت موازیِ منابع با کش ۲۰ دقیقه‌ای (اگر شبکه نبود، کش قدیمی)
 *  ۲) حذف تکراری‌ها و تقسیم منصفانه‌ی سهم هر منبع (round-robin)
 *  ۳) تست هم‌زمان: DNS ← TCP ← دست‌دادن TLS با SNI (برای TLS/Reality)؛ UDP/QUIC فقط DNS
 *  ۴) امتیازدهی ۰ تا ۱۰۰
 * هیچ کانفیگی خودکار نصب یا متصل نمی‌شود؛ تست TCP/TLS جای تست واقعی تونل را نمی‌گیرد.
 */
object RadarEngine {
    private const val CACHE_TTL_MS = 20 * 60 * 1000L
    private const val PER_SOURCE_CAP = 400
    private const val TCP_TIMEOUT_MS = 2000
    private const val TLS_TIMEOUT_MS = 3000
    private const val PARALLEL_FETCH = 6
    private const val PARALLEL_PROBE = 48

    private val sslFactory: SSLSocketFactory by lazy { SSLSocketFactory.getDefault() as SSLSocketFactory }

    fun scan(context: Context, sources: List<RadarSource>, maxTest: Int): Flow<RadarEvent> = channelFlow {
        val appContext = context.applicationContext
        val fetchGate = Semaphore(PARALLEL_FETCH)
        val fetched = sources.map { src ->
            async(Dispatchers.IO) { fetchGate.withPermit { src to fetch(appContext, src) } }
        }

        val perSource = ArrayList<List<Pair<String, RadarSource>>>()
        val seen = HashSet<String>()
        var uniqueFound = 0
        for (job in fetched) {
            val (src, result) = job.await()
            val unique = ArrayList<Pair<String, RadarSource>>()
            for (uri in result.uris) {
                if (unique.size >= PER_SOURCE_CAP) break
                val key = RadarParser.describe(uri)?.key ?: uri
                if (seen.add(key)) unique.add(uri to src)
            }
            uniqueFound += unique.size
            perSource.add(unique)
            send(RadarEvent.SourceDone(src, result.uris.size, result.error, result.fromCache))
        }

        // round-robin: هر منبع سهم برابر دارد، نه اینکه اولین منبع همه‌ی ظرفیت را بگیرد
        val planned = ArrayList<Pair<String, RadarSource>>()
        var round = 0
        while (planned.size < maxTest) {
            var added = false
            for (list in perSource) {
                if (round < list.size && planned.size < maxTest) {
                    planned.add(list[round])
                    added = true
                }
            }
            if (!added) break
            round++
        }
        send(RadarEvent.Planned(planned.size, uniqueFound))

        val dnsCache = ConcurrentHashMap<String, Result<List<InetAddress>>>()
        val gate = Semaphore(PARALLEL_PROBE)
        val done = AtomicInteger(0)
        val jobs = planned.map { (uri, src) ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    ensureActive()
                    val result = probe(uri, src, dnsCache)
                    send(RadarEvent.Result(result))
                    send(RadarEvent.Progress(done.incrementAndGet(), planned.size))
                }
            }
        }
        jobs.joinAll()
    }

    // ───────────── دریافت منابع ─────────────

    private data class Fetched(val uris: List<String>, val error: String?, val fromCache: Boolean)

    private fun fetch(context: Context, src: RadarSource): Fetched {
        if (src.url == RadarSources.AUTO_URL) {
            return try {
                Fetched(discoverAuto(), null, false)
            } catch (e: Exception) {
                Fetched(emptyList(), e.message ?: "خطا", false)
            }
        }
        val cache = File(context.cacheDir, "radar_src_" + src.url.hashCode().toUInt().toString(16) + ".txt")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < CACHE_TTL_MS
        if (fresh) {
            val uris = RadarParser.extractUris(cache.readText())
            if (uris.isNotEmpty()) return Fetched(uris, null, true)
        }
        return try {
            val text = download(src.url)
            val uris = RadarParser.extractUris(text)
            if (uris.isEmpty()) throw IllegalStateException("کانفیگی در فایل نبود")
            runCatching { cache.writeText(text) }
            Fetched(uris, null, false)
        } catch (e: Exception) {
            val stale = if (cache.exists()) runCatching { RadarParser.extractUris(cache.readText()) }.getOrDefault(emptyList()) else emptyList()
            if (stale.isNotEmpty()) Fetched(stale, null, true)
            else Fetched(emptyList(), e.message ?: e.javaClass.simpleName, false)
        }
    }

    private fun download(url: String, timeoutMs: Int = 12000, maxChars: Int = 4_000_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (RADAR-VPN radar)")
        try {
            val code = c.responseCode
            if (code != 200) throw IllegalStateException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText().take(maxChars) }
        } finally {
            c.disconnect()
        }
    }

    /** کشف خودکار مخزن‌های عمومی (روش قبلی رادار)، فقط وقتی کاربر گروه «کشف خودکار» را روشن کند. */
    private fun discoverAuto(): List<String> {
        val repos = listOf("barry-far/V2ray-Config", "0xRadikal/Free-v2ray-Configs", "ircfspace/tconfig", "Argh94/V2RayAutoConfig")
        val found = LinkedHashSet<String>()
        for (repo in repos) try {
            val metadata = download("https://api.github.com/repos/$repo", 10000)
            val branch = Regex("default_branch\"\\s*:\\s*\"([^\"]+)").find(metadata)?.groupValues?.get(1) ?: "main"
            val tree = download("https://api.github.com/repos/$repo/git/trees/$branch?recursive=1", 10000)
            val paths = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"").findAll(tree).map { it.groupValues[1] }
                .filter { p ->
                    val q = p.lowercase()
                    (q.endsWith(".txt") || q.endsWith(".yaml") || q.endsWith(".yml") || q.endsWith(".json")) &&
                        (q.contains("config") || q.contains("sub") || q.contains("all") || q.contains("country"))
                }
                .take(10)
            for (path in paths) {
                val encoded = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                val raw = download("https://raw.githubusercontent.com/$repo/$branch/$encoded", 8000)
                RadarParser.extractUris(raw).forEach { found.add(it) }
                if (found.size >= 400) return found.toList()
            }
        } catch (_: Exception) { /* بقیه‌ی مخزن‌ها ممکن است کار کنند */ }
        return found.toList()
    }

    // ───────────── تست یک کانفیگ ─────────────

    private fun probe(uri: String, src: RadarSource, dnsCache: ConcurrentHashMap<String, Result<List<InetAddress>>>): RadarResult {
        val ep = RadarParser.describe(uri)
            ?: return RadarResult(uri, null, src, RadarStatus.FAILED, null, null, null, "آدرس یا پورت قابل تشخیص نیست", 0)
        if (RadarParser.isLocalHost(ep.host)) {
            return RadarResult(uri, ep, src, RadarStatus.FAILED, null, null, null, "آدرس محلی یا نامعتبر", 0)
        }

        val dnsStart = System.nanoTime()
        val resolved = dnsCache.getOrPut(ep.host.lowercase()) { runCatching { InetAddress.getAllByName(ep.host).toList() } }
        val dnsMs = (System.nanoTime() - dnsStart) / 1_000_000L
        val addresses = resolved.getOrNull().orEmpty()
        if (addresses.isEmpty()) {
            return RadarResult(uri, ep, src, RadarStatus.FAILED, dnsMs, null, null, "DNS ناموفق یا میزبان در دسترس نیست", 0)
        }

        if (ep.udp) {
            return RadarResult(uri, ep, src, RadarStatus.UDP_UNVERIFIED, dnsMs, null, null,
                "پروتکل UDP/QUIC؛ فقط DNS سنجیده شد، تست واقعی لازم است", RadarParser.score(ep, null, null, src.group))
        }

        var lastError: Exception? = null
        for (address in addresses.take(3)) {
            val socket = Socket()
            try {
                val t0 = System.nanoTime()
                socket.connect(InetSocketAddress(address, ep.port), TCP_TIMEOUT_MS)
                val tcpMs = (System.nanoTime() - t0) / 1_000_000L

                val wantsTls = ep.security == "tls" || ep.security == "reality"
                if (!wantsTls) {
                    return RadarResult(uri, ep, src, RadarStatus.OK, dnsMs, tcpMs, null,
                        "پورت TCP باز است؛ سلامت تونل تأیید نشده", RadarParser.score(ep, tcpMs, null, src.group))
                }
                val (tlsOk, tlsMs, why) = tlsHandshake(socket, ep)
                return if (tlsOk) {
                    RadarResult(uri, ep, src, RadarStatus.OK, dnsMs, tcpMs, tlsMs,
                        "TCP و TLS پاسخ دادند؛ سلامت تونل تأیید نشده", RadarParser.score(ep, tcpMs, true, src.group))
                } else {
                    RadarResult(uri, ep, src, RadarStatus.TLS_FAILED, dnsMs, tcpMs, null,
                        "پورت باز است اما TLS ناموفق بود ($why)", RadarParser.score(ep, tcpMs, false, src.group))
                }
            } catch (e: Exception) {
                lastError = e
            } finally {
                runCatching { socket.close() }
            }
        }
        val detail = if (lastError is SocketTimeoutException) "مهلت اتصال TCP تمام شد" else "پورت TCP در دسترس نیست"
        return RadarResult(uri, ep, src, RadarStatus.FAILED, dnsMs, null, null, detail, 0)
    }

    private data class Tls(val ok: Boolean, val ms: Long?, val why: String)

    /**
     * دست‌دادن TLS روی همان سوکت. گواهی نامعتبر/خودامضا یعنی سرور TLS می‌فهمد، پس موفق حساب می‌شود؛
     * قطع اتصال یا تایم‌اوت در حین handshake یعنی ناموفق.
     */
    private fun tlsHandshake(plain: Socket, ep: RadarEndpoint): Tls {
        val serverName = (ep.sni ?: ep.host).trim()
        val t0 = System.nanoTime()
        return try {
            val ssl = sslFactory.createSocket(plain, serverName, ep.port, true) as SSLSocket
            ssl.soTimeout = TLS_TIMEOUT_MS
            runCatching {
                val params = ssl.sslParameters
                params.serverNames = listOf(SNIHostName(serverName))
                ssl.sslParameters = params
            }
            ssl.startHandshake()
            Tls(true, (System.nanoTime() - t0) / 1_000_000L, "")
        } catch (e: Exception) {
            val certIssue = generateSequence<Throwable>(e) { it.cause }.any { it is CertificateException }
            when {
                certIssue -> Tls(true, (System.nanoTime() - t0) / 1_000_000L, "")
                e is SocketTimeoutException -> Tls(false, null, "مهلت")
                e is SSLHandshakeException -> Tls(false, null, "handshake")
                else -> Tls(false, null, e.javaClass.simpleName)
            }
        }
    }
}
