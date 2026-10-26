package com.aras.client.ui.radar

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

internal enum class SourceState { LOADING, OK, EMPTY, FAILED }

internal enum class ProbeStatus {
    /** پورت TCP پاسخ داد (سلامت تونل تأیید نشده) */
    REACHABLE,
    /** پورت پاسخ نداد */
    UNREACHABLE,
    /** پروتکل UDP است؛ تست TCP معیار نیست، فقط DNS سنجیده شد */
    UDP_UNVERIFIED,
    DNS_FAILED,
    /** آدرس نامعتبر یا خصوصی/لوپ‌بک */
    BAD_ENDPOINT,
}

internal data class RadarResult(
    val uri: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val remark: String,
    val sourceName: String,
    val sourceGroup: RadarGroup,
    val country: String?,
    val ip: String?,
    val tcpMs: Long?,
    val dnsMs: Long?,
    val samples: Int,
    val status: ProbeStatus,
)

internal sealed interface RadarEvent {
    data class Source(val id: String, val state: SourceState, val count: Int) : RadarEvent
    /** تعداد کانفیگ یکتایی که آزمایش می‌شود (بعد از حذف تکراری و اعمال سقف) */
    data class Planned(val total: Int, val raw: Int) : RadarEvent
    data class Result(val value: RadarResult) : RadarEvent
}

/**
 * موتور جست‌وجو: خوراک‌ها را همزمان می‌گیرد، تکراری‌ها را حذف می‌کند، منابع را عادلانه (round-robin)
 * مخلوط می‌کند تا سقف تعداد فقط از یک منبع پر نشود، و بعد با تعداد زیادی probe همزمان
 * پاسخ TCP/DNS را می‌سنجد. نتیجه‌ها به‌محض آماده شدن (stream) می‌رسند.
 *
 * هیچ کانفیگی نصب یا وصل نمی‌شود؛ پاسخ TCP به‌تنهایی سلامت تونل را ثابت نمی‌کند.
 */
internal class RadarEngine {

    private val geoCache = ConcurrentHashMap<String, String>()
    private val geoTried = ConcurrentHashMap.newKeySet<String>()

    private class Cand(val uri: String, val source: RadarSource, val key: String)

    fun scan(
        sources: List<RadarSource>,
        limit: Int,
        geoLookup: Boolean,
    ): Flow<RadarEvent> = channelFlow {
        val fetchGate = Semaphore(FETCH_PARALLEL)
        val perSource = coroutineScope {
            sources.map { src ->
                async(Dispatchers.IO) {
                    fetchGate.withPermit {
                        send(RadarEvent.Source(src.id, SourceState.LOADING, 0))
                        val uris = runCatching { extract(fetchText(src.url)) }
                        val list = uris.getOrDefault(emptyList())
                        val state = when {
                            uris.isFailure -> SourceState.FAILED
                            list.isEmpty() -> SourceState.EMPTY
                            else -> SourceState.OK
                        }
                        send(RadarEvent.Source(src.id, state, list.size))
                        src to list
                    }
                }
            }.awaitAll()
        }

        // مخلوط عادلانه + حذف تکراری + سقف
        val seen = HashSet<String>()
        val queues = perSource.map { (src, list) -> src to ArrayDeque(list) }
        val planned = ArrayList<Cand>()
        var raw = 0
        queues.forEach { raw += it.second.size }
        outer@ while (queues.any { it.second.isNotEmpty() }) {
            for ((src, q) in queues) {
                var taken = false
                while (q.isNotEmpty() && !taken) {
                    val uri = q.removeFirst()
                    val key = dedupeKey(uri) ?: continue
                    if (seen.add(key)) {
                        planned += Cand(uri, src, key)
                        taken = true
                    }
                }
                if (planned.size >= limit) break@outer
            }
        }
        send(RadarEvent.Planned(planned.size, raw))

        val gate = Semaphore(PROBE_PARALLEL)
        coroutineScope {
            for (cand in planned) {
                launch(Dispatchers.IO) {
                    gate.withPermit {
                        currentCoroutineContext().ensureActive()
                        send(RadarEvent.Result(probe(cand, geoLookup)))
                    }
                }
            }
        }
    }

    // ───────────────────────── دریافت و استخراج ─────────────────────────

    private fun fetchText(rawUrl: String): String {
        val url = encodeNonAscii(rawUrl)
        var last: Exception? = null
        repeat(2) {
            try {
                return get(url, FEED_TIMEOUT_MS, MAX_FEED_CHARS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: java.io.IOException("fetch failed")
    }

    private fun encodeNonAscii(url: String): String = buildString {
        for (ch in url) if (ch.code > 127) append(URLEncoder.encode(ch.toString(), "UTF-8")) else append(ch)
    }

    private fun get(url: String, timeout: Int, maxChars: Int): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "RADAR-VPN-Radar/2.0")
        c.setRequestProperty("Accept-Encoding", "identity")
        return try {
            val code = c.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            c.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val out = StringBuilder()
                val buf = CharArray(16 * 1024)
                while (out.length < maxChars) {
                    val n = reader.read(buf, 0, minOf(buf.size, maxChars - out.length))
                    if (n < 0) break
                    out.append(buf, 0, n)
                }
                out.toString()
            }
        } finally {
            c.disconnect()
        }
    }

    private val uriPattern =
        Regex("""(?:vmess|vless|trojan|ss|hy2|hysteria2|hysteria|tuic)://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    /** لینک‌های مستقیم را برمی‌دارد؛ اگر خوراک base64 (حتی تودرتو) بود، رمزگشایی می‌کند. */
    fun extract(text: String): List<String> {
        var current = text
        repeat(3) {
            val found = uriPattern.findAll(current)
                .map { it.value.trimEnd(',', ';', ')', ']', '}') }
                .distinct().toList()
            if (found.isNotEmpty()) return found
            val compact = current.filterNot { it.isWhitespace() }
            if (compact.length !in 24..4_000_000) return emptyList()
            val bytes = runCatching { Base64.decode(compact, Base64.DEFAULT) }
                .recoverCatching { Base64.decode(compact, Base64.URL_SAFE or Base64.NO_WRAP) }
                .getOrNull() ?: return emptyList()
            val decoded = bytes.toString(Charsets.UTF_8)
            if (decoded == current) return emptyList()
            current = decoded
        }
        return uriPattern.findAll(current).map { it.value.trimEnd(',', ';', ')', ']', '}') }.distinct().toList()
    }

    // ───────────────────────── تجزیه‌ی کانفیگ ─────────────────────────

    private data class Parsed(val scheme: String, val host: String, val port: Int, val secret: String, val remark: String)

    private fun parse(uri: String): Parsed? = runCatching {
        val scheme = uri.substringBefore("://").lowercase()
        val body = uri.substringAfter("://")
        val fragment = body.substringAfter('#', "")
        val remarkFromFragment = runCatching { URLDecoder.decode(fragment, "UTF-8") }.getOrDefault(fragment)

        if (scheme == "vmess") {
            val enc = body.substringBefore('#')
            val raw = decodeB64(enc) ?: return null
            val host = jsonString(raw, "add") ?: return null
            val port = jsonString(raw, "port")?.filter { it.isDigit() }?.toIntOrNull() ?: return null
            val id = jsonString(raw, "id").orEmpty()
            val ps = jsonString(raw, "ps").orEmpty()
            return Parsed(scheme, host, port, id, ps.ifBlank { remarkFromFragment })
        }

        var main = body.substringBefore('#').substringBefore('?')
        if (scheme == "ss" && !main.contains('@')) {
            // ss://BASE64(method:password@host:port)
            val decoded = decodeB64(main.trimEnd('/')) ?: return null
            main = decoded
        }
        val userInfo = main.substringBeforeLast('@', "")
        val authority = main.substringAfterLast('@').substringBefore('/')
        val host = authority.substringBeforeLast(':').trim('[', ']')
        val portText = authority.substringAfterLast(':', "")
        val port = Regex("""\d+""").find(portText)?.value?.toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) return null
        Parsed(scheme, host, port, userInfo, remarkFromFragment)
    }.getOrNull()

    private fun decodeB64(s: String): String? = runCatching {
        Base64.decode(s, Base64.DEFAULT).toString(Charsets.UTF_8)
    }.recoverCatching {
        Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun jsonString(json: String, key: String): String? =
        Regex("\"" + key + "\"\\s*:\\s*\"?([^\",}]+)").find(json)?.groupValues?.get(1)?.trim()

    private fun dedupeKey(uri: String): String? {
        val p = parse(uri) ?: return null
        return p.scheme + "|" + p.host.lowercase() + "|" + p.port + "|" + p.secret
    }

    // ───────────────────────── سنجش ─────────────────────────

    private fun probe(cand: Cand, geoLookup: Boolean): RadarResult {
        val p = parse(cand.uri)
        val src = cand.source
        fun result(
            status: ProbeStatus,
            host: String = "",
            port: Int = 0,
            ip: String? = null,
            tcp: Long? = null,
            dns: Long? = null,
            samples: Int = 0,
            country: String? = src.countryCode,
        ) = RadarResult(
            uri = cand.uri,
            protocol = normalizeProtocol(cand.uri.substringBefore("://")),
            host = host, port = port,
            remark = p?.remark.orEmpty(),
            sourceName = src.name, sourceGroup = src.group,
            country = country, ip = ip, tcpMs = tcp, dnsMs = dns, samples = samples, status = status,
        )
        if (p == null) return result(ProbeStatus.BAD_ENDPOINT)

        val dnsStart = System.nanoTime()
        val addresses = try {
            InetAddress.getAllByName(p.host).sortedBy { it is java.net.Inet6Address }
        } catch (_: Exception) {
            return result(ProbeStatus.DNS_FAILED, p.host, p.port)
        }
        val dnsMs = ((System.nanoTime() - dnsStart) / 1_000_000L).coerceAtLeast(0L)
        val first = addresses.firstOrNull() ?: return result(ProbeStatus.DNS_FAILED, p.host, p.port)
        if (isLocal(first)) return result(ProbeStatus.BAD_ENDPOINT, p.host, p.port)
        val ipText = first.hostAddress?.substringBefore('%')

        val udpFirst = normalizeProtocol(p.scheme) in UDP_FIRST
        if (udpFirst) {
            val country = src.countryCode ?: if (geoLookup) lookupCountry(ipText) else null
            return result(ProbeStatus.UDP_UNVERIFIED, p.host, p.port, ipText, dns = dnsMs, country = country)
        }

        val samples = ArrayList<Long>(TCP_SAMPLES)
        var winner: InetAddress? = null
        var timedOut = false
        for (addr in addresses.take(2)) {
            repeat(TCP_SAMPLES) {
                val t0 = System.nanoTime()
                try {
                    Socket().use { s ->
                        s.tcpNoDelay = true
                        s.connect(InetSocketAddress(addr, p.port), TCP_TIMEOUT_MS)
                    }
                    samples += ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(0L)
                    winner = addr
                } catch (e: SocketTimeoutException) {
                    timedOut = true
                } catch (_: Exception) {
                    // رد شد یا بسته بود
                }
            }
            if (samples.isNotEmpty()) break
            if (timedOut) break // اگر مهلت تمام شد، آدرس دوم را هم امتحان نکن تا اسکن کند نشود
        }
        val ip = (winner ?: first).hostAddress?.substringBefore('%')
        if (samples.isEmpty()) return result(ProbeStatus.UNREACHABLE, p.host, p.port, ip, dns = dnsMs)

        val country = src.countryCode ?: if (geoLookup) lookupCountry(ip) else null
        return result(
            ProbeStatus.REACHABLE, p.host, p.port, ip,
            tcp = median(samples), dns = dnsMs, samples = samples.size, country = country,
        )
    }

    private fun median(values: List<Long>): Long {
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else s[m - 1] + (s[m] - s[m - 1]) / 2
    }

    private fun isLocal(a: InetAddress) =
        a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress

    private fun normalizeProtocol(s: String): String = when (val x = s.lowercase()) {
        "hy2" -> "hysteria2"
        else -> x
    }

    /** فقط خودِ IP سرور (نه کانفیگ و نه رمز) به سرویس GeoIP فرستاده می‌شود؛ تعداد درخواست‌ها محدود است. */
    private fun lookupCountry(ip: String?): String? {
        val addr = ip?.takeIf { it.isNotBlank() } ?: return null
        geoCache[addr]?.let { return it }
        if (geoTried.size >= MAX_GEO || !geoTried.add(addr)) return geoCache[addr]
        val enc = URLEncoder.encode(addr, "UTF-8")
        val code = runCatching {
            val json = get("https://ipwho.is/$enc", GEO_TIMEOUT_MS, 20_000)
            if (Regex("\"success\"\\s*:\\s*false").containsMatchIn(json)) null
            else Regex("\"country_code\"\\s*:\\s*\"([A-Za-z]{2})\"").find(json)?.groupValues?.get(1)
        }.getOrNull() ?: runCatching {
            val json = get("https://api.ipapi.is/?q=$enc", GEO_TIMEOUT_MS, 40_000)
            Regex("\"country_code\"\\s*:\\s*\"([A-Za-z]{2})\"").find(json)?.groupValues?.get(1)
        }.getOrNull()
        val iso = code?.uppercase()
        if (iso != null) geoCache.putIfAbsent(addr, iso)
        return iso
    }

    companion object {
        private val UDP_FIRST = setOf("hysteria2", "hysteria", "tuic")
        private const val FETCH_PARALLEL = 6
        private const val PROBE_PARALLEL = 40
        private const val FEED_TIMEOUT_MS = 8_000
        private const val MAX_FEED_CHARS = 4_000_000
        private const val TCP_SAMPLES = 3
        private const val TCP_TIMEOUT_MS = 1_500
        private const val GEO_TIMEOUT_MS = 3_000
        private const val MAX_GEO = 600
    }
}
