package com.aras.client.radar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

enum class RadarStatus { OK, SLOW, UDP, FAILED }

/**
 * نتیجه‌ی پیش‌آزمایش یک کانفیگ.
 * توجه: این فقط «دسترس‌پذیری شبکه» را می‌سنجد، نه سلامت کامل تونل؛ تست واقعی باید داخل برنامه انجام شود.
 */
data class RadarResult(
    val cfg: ParsedConfig,
    val status: RadarStatus,
    val dnsMs: Long?,
    val tcpMs: Long?,
    val jitterMs: Long?,
    val tlsMs: Long?,
    /** ۰ تا ۱۰۰؛ ترکیبی از تأخیر، نوسان و پاسخ TLS */
    val score: Int,
    val note: String,
    val country: String?,
    val sourceTitle: String,
)

object RadarProbe {
    private val dnsCache = ConcurrentHashMap<String, List<InetAddress>>()
    private val dnsPool = Executors.newCachedThreadPool { r ->
        Thread(r, "radar-dns").apply { isDaemon = true }
    }

    private suspend fun resolve(host: String): List<InetAddress>? {
        dnsCache[host]?.let { return it }
        val deferred = CompletableDeferred<List<InetAddress>?>()
        dnsPool.execute {
            deferred.complete(runCatching { InetAddress.getAllByName(host).toList() }.getOrNull())
        }
        val found = withTimeoutOrNull(3500) { deferred.await() }
        if (!found.isNullOrEmpty()) dnsCache[host] = found
        return found?.takeIf { it.isNotEmpty() }
    }

    private fun tcpOnce(addr: InetAddress, port: Int, timeoutMs: Int): Long? {
        val socket = Socket()
        return try {
            val t0 = System.nanoTime()
            socket.connect(InetSocketAddress(addr, port), timeoutMs)
            (System.nanoTime() - t0) / 1_000_000L
        } catch (_: Exception) {
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * دست‌دادن TLS (بدون ارسال داده). خطای گواهی یعنی طرف مقابل واقعاً TLS صحبت کرده؛
     * پس آن را «پاسخ TLS» حساب می‌کنیم (بیشتر سرورهای پروکسی گواهی خودامضا دارند).
     */
    private fun tlsOnce(addr: InetAddress, port: Int, sni: String, timeoutMs: Int): Long? {
        val raw = Socket()
        return try {
            val t0 = System.nanoTime()
            raw.connect(InetSocketAddress(addr, port), timeoutMs)
            raw.soTimeout = timeoutMs
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(raw, sni, port, true) as SSLSocket
            if (sni.any { it.isLetter() }) {
                val params = ssl.sslParameters
                params.serverNames = listOf(SNIHostName(sni))
                ssl.sslParameters = params
            }
            try {
                ssl.startHandshake()
            } catch (e: SSLHandshakeException) {
                if (e.cause is java.security.cert.CertificateException) {
                    return (System.nanoTime() - t0) / 1_000_000L
                }
                return null
            }
            (System.nanoTime() - t0) / 1_000_000L
        } catch (_: Exception) {
            null
        } finally {
            runCatching { raw.close() }
        }
    }

    suspend fun probe(
        cfg: ParsedConfig,
        deep: Boolean,
        sourceTitle: String,
        countryFallback: String?,
    ): RadarResult = withContext(Dispatchers.IO) {
        val country = cfg.flagIso ?: countryFallback
        fun fail(note: String, dns: Long? = null) = RadarResult(
            cfg, RadarStatus.FAILED, dns, null, null, null, 0, note, country, sourceTitle,
        )

        val dnsStart = System.nanoTime()
        val addresses = resolve(cfg.host) ?: return@withContext fail("DNS ناموفق یا میزبان در دسترس نیست")
        val dnsMs = (System.nanoTime() - dnsStart) / 1_000_000L
        coroutineContext.ensureActive()

        if (cfg.protocol == "hysteria2" || cfg.protocol == "tuic") {
            return@withContext RadarResult(
                cfg, RadarStatus.UDP, dnsMs, null, null, null, 45,
                "پروتکل UDP/QUIC؛ تست TCP معنا ندارد. DNS جواب داد، در برنامه تست واقعی کن",
                country, sourceTitle,
            )
        }

        var target: InetAddress? = null
        val samples = ArrayList<Long>(2)
        for (addr in addresses.take(3)) {
            val first = tcpOnce(addr, cfg.port, 2500) ?: continue
            samples += first
            tcpOnce(addr, cfg.port, 2500)?.let { samples += it }
            target = addr
            break
        }
        if (target == null) {
            return@withContext fail("DNS جواب داد اما پورت TCP باز نیست یا مهلت تمام شد", dnsMs)
        }
        coroutineContext.ensureActive()
        val tcpMs = samples.min()
        val jitter = if (samples.size >= 2) abs(samples[0] - samples[1]) else 0L

        var tlsMs: Long? = null
        val wantsTls = deep && (cfg.security == "tls" || cfg.security == "reality")
        if (wantsTls) tlsMs = tlsOnce(target, cfg.port, cfg.sni, 3000)

        var score = 100 - (tcpMs.coerceIn(0, 1000) / 10).toInt()
        score -= (jitter / 10).coerceAtMost(20).toInt()
        if (tlsMs != null) score += 8
        if (wantsTls && tlsMs == null) score -= 15
        if (cfg.security == "reality") score += 4
        score = score.coerceIn(1, 100)

        val status = if (tcpMs <= 300) RadarStatus.OK else RadarStatus.SLOW
        val note = when {
            wantsTls && tlsMs != null -> "TCP و TLS پاسخ دادند؛ سلامت تونل هنوز تأیید نشده"
            wantsTls -> "TCP باز است اما TLS جواب نداد"
            else -> "پورت پاسخ داد؛ سلامت تونل هنوز تأیید نشده"
        }
        RadarResult(cfg, status, dnsMs, tcpMs, jitter, tlsMs, score, note, country, sourceTitle)
    }
}
