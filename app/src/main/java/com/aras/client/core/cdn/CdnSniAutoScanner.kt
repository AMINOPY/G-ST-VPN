package com.aras.client.core.cdn

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
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
        // DNS and TLS checks are bounded and parallel. The previous sequential loop could
        // spend several minutes on slow candidate networks and made AUTO feel frozen.
        val executor = java.util.concurrent.Executors.newFixedThreadPool(6) { task ->
            Thread(task, "cdn-sni-probe").apply { isDaemon = true }
        }
        try {
            val futures = candidates.map { candidate ->
                executor.submit<List<Result>> {
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
            futures.forEach { future ->
                try {
                    ranked.addAll(future.get(12, TimeUnit.SECONDS))
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    futures.forEach { it.cancel(true) }
                    return null
                } catch (e: Exception) {
                    future.cancel(true)
                }
            }
        } finally {
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

    /** Performs a fresh TCP connect + certificate-validated TLS handshake for the entered pair. */
    fun test(ip: String, sni: String): Result? {
        val validIp = CdnSniConfig.parseIp(ip) ?: return null
        val validSni = CdnSniConfig.parseSni(sni) ?: return null
        return probe(validIp, validSni)
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
