package com.aras.client.core.cdn

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Finds a reachable IP/SNI pair using real TCP + TLS handshakes.
 *
 * IR and US have isolated candidate sets, caches and success history. The region names
 * describe candidate preference only: a globally anycast CDN IP is not guaranteed to
 * physically reside in that country.
 */
object CdnSniAutoScanner {
    enum class Mode(val key: String) { IR("auto_ir"), US("auto_us") }

    data class Result(val ip: String, val sni: String, val latencyMs: Long, val fromCache: Boolean = false)

    private data class Candidate(val sni: String)

    private const val PREFS = "cdn_sni_auto_scan_v1"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val CONNECT_TIMEOUT_MS = 2200
    private const val HANDSHAKE_TIMEOUT_MS = 2500
    // One wall-clock budget for the whole scan, not one fresh timeout per candidate.
    private const val SCAN_TIMEOUT_MS = 20_000L
    private const val TAG = "CdnSniAutoScanner"

    private val irCandidates = listOf(
        Candidate("digikala.com"),
        Candidate("www.digikala.com"),
        Candidate("aparat.com"),
        Candidate("www.aparat.com"),
        Candidate("snapp.ir"),
        Candidate("www.snapp.ir"),
        Candidate("varzesh3.com"),
        Candidate("www.varzesh3.com"),
        Candidate("telewebion.com"),
        Candidate("www.telewebion.com"),
        Candidate("bmi.ir"),
        Candidate("www.bmi.ir"),
        Candidate("bankmellat.ir"),
        Candidate("www.bankmellat.ir"),
        Candidate("bsi.ir"),
        Candidate("www.bsi.ir"),
        Candidate("shaparak.ir"),
        Candidate("www.shaparak.ir"),
        Candidate("irancell.ir"),
        Candidate("www.irancell.ir"),
        Candidate("mci.ir"),
        Candidate("www.mci.ir"),
        Candidate("hamrahaval.ir"),
        Candidate("www.hamrahaval.ir"),
        Candidate("divar.ir"),
        Candidate("www.divar.ir"),
        Candidate("torob.com"),
        Candidate("www.torob.com"),
        Candidate("zoomit.ir"),
        Candidate("www.zoomit.ir"),
        Candidate("isna.ir"),
        Candidate("www.isna.ir"),
        Candidate("irib.ir"),
        Candidate("www.irib.ir"),
        Candidate("google.com"),
        Candidate("www.google.com"),
    )

    private val usCandidates = listOf(
        Candidate("www.google.com"),
        Candidate("www.microsoft.com"),
        Candidate("www.cloudflare.com"),
        Candidate("www.amazon.com"),
        Candidate("www.apple.com"),
        Candidate("www.wikipedia.org"),
        Candidate("www.github.com"),
        Candidate("www.python.org"),
        Candidate("www.mozilla.org"),
        Candidate("www.nytimes.com"),
        Candidate("www.bbc.com"),
        Candidate("www.cisco.com"),
        Candidate("www.adobe.com"),
        Candidate("www.salesforce.com"),
        Candidate("www.fastly.com"),
        Candidate("www.akamai.com"),
        Candidate("www.amazonaws.com"),
    )

    /**
     * Uses the Android resolver for each candidate, then verifies the resolved address
     * with a certificate-validated TLS handshake using that hostname as SNI.
     */
    fun scan(context: Context, mode: Mode): Result? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        readCache(prefs, mode)?.let { cached ->
            // Validate cache entries before reusing them; networks can change quickly.
            probe(cached.ip, cached.sni)?.let { return it.copy(fromCache = true) }
        }

        val candidates = if (mode == Mode.IR) irCandidates else usCandidates
        val ranked = ArrayList<Result>()
        // All DNS/TLS candidates share one deadline. Waiting on each Future in list
        // order gave every queued candidate a new timeout and could freeze AUTO for
        // several minutes on a resolver that stopped responding.
        val executor = java.util.concurrent.Executors.newFixedThreadPool(6) { task ->
            Thread(task, "cdn-sni-probe").apply { isDaemon = true }
        }
        val completion = ExecutorCompletionService<List<Result>>(executor)
        val futures = ArrayList<java.util.concurrent.Future<List<Result>>>(candidates.size)
        try {
            candidates.forEach { candidate ->
                futures += completion.submit<List<Result>> {
                    val candidateResults = ArrayList<Result>()
                    try {
                        // A hostname may have many CDN/Anycast answers; two representative
                        // IPv4 addresses are enough for a bounded first-pass ranking.
                        val addresses = java.net.InetAddress.getAllByName(candidate.sni).take(2)
                        for (address in addresses) {
                            val ip = address.hostAddress?.substringBefore('%') ?: continue
                            if (!CdnIp.isIpv4(ip)) continue
                            probe(ip, candidate.sni)?.let(candidateResults::add)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Candidate lookup failed: ${candidate.sni}", e)
                    }
                    candidateResults
                }
            }
            val completedAll = collectWithinDeadline(
                completion = completion,
                taskCount = futures.size,
                timeoutMs = SCAN_TIMEOUT_MS,
            ) { ranked.addAll(it) }
            if (!completedAll) {
                Log.w(TAG, "AUTO scan reached its ${SCAN_TIMEOUT_MS}ms deadline; ranking completed candidates only")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } finally {
            // DNS resolvers are not always interruptible, but cancel every outstanding
            // task and return control to the UI at the single scan deadline.
            futures.forEach { if (!it.isDone) it.cancel(true) }
            executor.shutdownNow()
        }

        val unique = ranked.distinctBy { "${it.ip}|${it.sni}" }
        if (unique.isEmpty()) return null

        // Learn successful pairs separately per mode, without letting history outweigh latency.
        unique.forEach { recordSuccess(prefs, mode, it) }
        val best = unique.minByOrNull { result ->
            val successes = readScore(prefs, mode, result.ip, result.sni).coerceAtMost(20)
            result.latencyMs - (successes * 3L)
        } ?: return null
        prefs.edit().putString("${mode.key}_cache", JSONObject().apply {
            put("ip", best.ip)
            put("sni", best.sni)
            put("latency", best.latencyMs)
            put("savedAt", System.currentTimeMillis())
            put("mode", mode.key)
        }.toString()).apply()
        return best
    }

    /**
     * Collects completed candidate batches against one absolute wall-clock deadline.
     * Exposed internally so the timeout behavior can be tested without live DNS.
     */
    internal fun <T> collectWithinDeadline(
        completion: CompletionService<T>,
        taskCount: Int,
        timeoutMs: Long,
        onResult: (T) -> Unit,
    ): Boolean {
        if (taskCount <= 0) return true
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(0))
        var completed = 0
        while (completed < taskCount) {
            val remainingNanos = deadline - System.nanoTime()
            if (remainingNanos <= 0L) return false
            val future = completion.poll(remainingNanos, TimeUnit.NANOSECONDS) ?: return false
            completed++
            try {
                onResult(future.get())
            } catch (_: ExecutionException) {
                // One failed DNS/TLS candidate must not discard other successful candidates.
            } catch (_: java.util.concurrent.CancellationException) {
                // A candidate cancelled by the executor is simply absent from the ranking.
            }
        }
        return true
    }

    /** Performs a fresh TCP connect + certificate-validated TLS handshake for the entered pair. */
    fun test(ip: String, sni: String): Result? {
        val ips = CdnSniConfig.parseIps(ip) ?: return null
        val snis = CdnSniConfig.parseSnis(sni) ?: return null
        val pairs = ips.take(4).flatMap { address ->
            snis.take(2).map { host -> address to host }
        }.take(8)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(4) { task ->
            Thread(task, "cdn-sni-manual-test").apply { isDaemon = true }
        }
        return try {
            pairs.map { (address, host) ->
                executor.submit<Result?> { probe(address, host) }
            }.mapNotNull { future ->
                runCatching { future.get(6, TimeUnit.SECONDS) }.getOrNull()
            }.minByOrNull { it.latencyMs }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun probe(ip: String, sni: String): Result? {
        val start = System.nanoTime()
        var raw: Socket? = null
        var tls: SSLSocket? = null
        return try {
            raw = Socket()
            raw.connect(InetSocketAddress(ip, 443), CONNECT_TIMEOUT_MS)
            raw.soTimeout = HANDSHAKE_TIMEOUT_MS
            tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, sni, 443, true) as SSLSocket
            tls.soTimeout = HANDSHAKE_TIMEOUT_MS
            val params = tls.sslParameters
            params.serverNames = listOf(SNIHostName(sni))
            params.endpointIdentificationAlgorithm = "HTTPS"
            tls.sslParameters = params
            tls.startHandshake()
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            Result(ip, sni, elapsed)
        } catch (e: Exception) {
            null
        } finally {
            try { tls?.close() } catch (_: Exception) { }
            try { raw?.close() } catch (_: Exception) { }
        }
    }

    private fun readCache(prefs: android.content.SharedPreferences, mode: Mode): Result? {
        return try {
            val value = prefs.getString("${mode.key}_cache", null) ?: return null
            val obj = JSONObject(value)
            if (System.currentTimeMillis() - obj.optLong("savedAt") > CACHE_TTL_MS) return null
            val ip = obj.optString("ip")
            val sni = obj.optString("sni")
            if (!CdnIp.isIpv4(ip) || CdnSniConfig.parseSni(sni) == null) null
            else Result(ip, sni, obj.optLong("latency"), true)
        } catch (_: Exception) { null }
    }

    private fun scoreKey(mode: Mode, ip: String, sni: String) = "${mode.key}_score_${ip}_${sni}"

    private fun readScore(prefs: android.content.SharedPreferences, mode: Mode, ip: String, sni: String): Int =
        prefs.getInt(scoreKey(mode, ip, sni), 0)

    private fun recordSuccess(prefs: android.content.SharedPreferences, mode: Mode, result: Result) {
        val key = scoreKey(mode, result.ip, result.sni)
        prefs.edit().putInt(key, (prefs.getInt(key, 0) + 1).coerceAtMost(100)).apply()
    }
}
