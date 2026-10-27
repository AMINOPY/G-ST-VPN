package com.aras.client.core.cdn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Tries edge address + server name pairs and reports the ones that work.
 *
 * A pair works when the TCP connection opens, the TLS handshake completes with the
 * chosen server name and a certificate that is valid for that name, and — when an
 * origin host is given — the edge answers an HTTP request carrying that Host header.
 * The last step is what separates an edge that merely terminates TLS from one that
 * will actually route the profile's traffic.
 *
 * Run it with the VPN off: a probe made while the tunnel is up measures the tunnel.
 */
object CdnSniScanner {

    const val PORT = 443
    const val CONNECT_TIMEOUT_MS = 3_000
    const val IO_TIMEOUT_MS = 4_000
    const val CONCURRENCY = 12

    /**
     * @param origin Host header to send; when null only the handshake is checked
     * @param onResult called for every pair, with null when it failed
     */
    suspend fun scan(
        candidates: List<CdnCandidate>,
        origin: String?,
        port: Int = PORT,
        onResult: (CdnCandidate, CdnScanHit?) -> Unit = { _, _ -> },
    ): List<CdnScanHit> = coroutineScope {
        val gate = Semaphore(CONCURRENCY)
        candidates.map { candidate ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val hit = probe(candidate, origin, port)
                    withContext(Dispatchers.Main.immediate) { onResult(candidate, hit) }
                    hit
                }
            }
        }.awaitAll().filterNotNull().sortedBy { it.latencyMs }
    }

    /** Blocking; returns null on any failure. */
    internal fun probe(candidate: CdnCandidate, origin: String?, port: Int): CdnScanHit? {
        val started = System.nanoTime()
        var plain: Socket? = null
        var tls: SSLSocket? = null
        try {
            plain = Socket()
            plain.connect(InetSocketAddress(candidate.ip, port), CONNECT_TIMEOUT_MS)
            plain.soTimeout = IO_TIMEOUT_MS
            tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(plain, candidate.sni, port, true) as SSLSocket
            tls.sslParameters = tls.sslParameters.apply {
                serverNames = listOf(SNIHostName(candidate.sni))
            }
            tls.startHandshake()
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(candidate.sni, tls.session)) return null
            if (!origin.isNullOrBlank() && !answersForHost(tls, origin)) return null
            return CdnScanHit(candidate, (System.nanoTime() - started) / 1_000_000L)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { tls?.close() }
            runCatching { plain?.close() }
        }
    }

    private fun answersForHost(socket: SSLSocket, origin: String): Boolean {
        val request = "GET / HTTP/1.1\r\nHost: $origin\r\nUser-Agent: Mozilla/5.0\r\nAccept: */*\r\nConnection: close\r\n\r\n"
        socket.outputStream.write(request.toByteArray(Charsets.US_ASCII))
        socket.outputStream.flush()
        val line = readStatusLine(socket.inputStream) ?: return false
        return isHttpStatusLine(line)
    }

    private fun readStatusLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 256) {
            val b = input.read()
            if (b < 0) break
            if (b == '\n'.code) break
            sb.append(b.toChar())
        }
        return sb.toString().trim().ifEmpty { null }
    }

    internal fun isHttpStatusLine(line: String): Boolean =
        Regex("^HTTP/\\d(\\.\\d)? \\d{3}").containsMatchIn(line)
}
