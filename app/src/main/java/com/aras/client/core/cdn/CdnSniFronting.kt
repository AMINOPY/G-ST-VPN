package com.aras.client.core.cdn

import com.aras.client.dto.entities.ProfileItem
import com.aras.client.enums.EConfigType
import com.aras.client.enums.NetworkType
import kotlin.random.Random

/** One edge address paired with the server name to present to it. */
data class CdnCandidate(val ip: String, val sni: String)

/** A candidate that completed the probe, with how long the handshake took. */
data class CdnScanHit(val candidate: CdnCandidate, val latencyMs: Long)

/**
 * CDN fronting for an existing profile.
 *
 * The profile is dialled at an edge address of the CDN (`server`), the TLS
 * handshake presents a different, allowed server name (`sni`), and the profile's
 * own host name keeps travelling inside the encrypted stream as the Host header
 * (WebSocket / HTTPUpgrade / XHTTP `host`, gRPC `authority`). The CDN reads that
 * Host header after the handshake and routes to the real origin.
 *
 * Nothing here touches the core: it only rewrites which address and server name
 * the existing outbound builder is given.
 */
object CdnSniFronting {

    private val frontableConfigTypes = setOf(EConfigType.VLESS, EConfigType.VMESS, EConfigType.TROJAN)

    /**
     * Transports that carry the real host name above TLS. Plain TCP, KCP and the
     * others have no Host header for the CDN to route on, so fronting them would
     * only make the profile unreachable.
     */
    private val frontableNetworks = setOf(
        NetworkType.WS.type,
        NetworkType.HTTP_UPGRADE.type,
        NetworkType.XHTTP.type,
        NetworkType.H2.type,
        NetworkType.HTTP.type,
        NetworkType.GRPC.type,
    )

    fun isEligible(profile: ProfileItem): Boolean =
        profile.configType in frontableConfigTypes &&
            profile.security == "tls" &&
            profile.network in frontableNetworks

    /**
     * The host name the CDN must still be told about: the Host header the profile
     * already carries, else its server name, else its server address.
     */
    fun originHost(profile: ProfileItem): String? {
        val candidates = if (profile.network == NetworkType.GRPC.type) {
            listOf(profile.authority, profile.sni, profile.server)
        } else {
            listOf(profile.host, profile.sni, profile.server)
        }
        return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    /**
     * A copy of [base] that dials [candidate]. The original is left untouched.
     * The copy is a new profile: the caller saves it under a new key.
     */
    fun apply(base: ProfileItem, candidate: CdnCandidate): ProfileItem {
        require(isEligible(base)) { "profile cannot be fronted" }
        val origin = originHost(base)
        return base.copy(
            addedTime = System.currentTimeMillis(),
            remarks = "${base.remarks.ifBlank { base.server.orEmpty() }} · CDN/SNI ${candidate.sni}",
        ).also {
            it.server = candidate.ip
            it.sni = candidate.sni
            // Keep the real destination in the encrypted Host header.
            if (base.network == NetworkType.GRPC.type) {
                it.authority = origin
            } else {
                it.host = origin
            }
            // The edge certificate is for the front name, which the handshake
            // presents, so normal verification stays on.
            it.insecure = false
            it.verifyPeerCertByName = null
        }
    }
}

/** Edge addresses and server names to try, parsed from what the user typed. */
object CdnCandidatePool {

    /** Published Cloudflare IPv4 ranges. A starting point; the user can replace them. */
    val DEFAULT_RANGES = listOf(
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "162.159.128.0/17",
        "188.114.96.0/20",
        "141.101.64.0/18",
    )

    val DEFAULT_SNIS = listOf(
        "www.cloudflare.com",
        "cdnjs.cloudflare.com",
        "speed.cloudflare.com",
    )

    const val MAX_PAIRS = 120

    /**
     * Turns lines of `1.2.3.4` and `1.2.3.0/24` into at most [limit] distinct IPv4
     * addresses. A range contributes random members rather than its first ones, so a
     * big range is sampled instead of walked.
     */
    fun parseIps(text: String, limit: Int = 40, random: Random = Random.Default): List<String> {
        val fixed = LinkedHashSet<String>()
        val ranges = mutableListOf<Pair<Long, Int>>() // base, host bits
        for (token in tokens(text)) {
            val slash = token.indexOf('/')
            if (slash < 0) {
                ipv4ToLong(token)?.let { fixed.add(token) }
                continue
            }
            val base = ipv4ToLong(token.substring(0, slash)) ?: continue
            val prefix = token.substring(slash + 1).toIntOrNull() ?: continue
            if (prefix !in 8..32) continue
            val hostBits = 32 - prefix
            val network = base and (0xFFFFFFFFL shl hostBits) and 0xFFFFFFFFL
            ranges.add(network to hostBits)
        }
        val out = LinkedHashSet<String>(fixed.take(limit))
        if (ranges.isNotEmpty()) {
            var guard = 0
            var index = 0
            while (out.size < limit && guard++ < limit * 8) {
                val (network, hostBits) = ranges[index++ % ranges.size]
                val size = 1L shl hostBits
                val offset = if (size <= 2) 0L else 1 + random.nextLong(size - 2)
                out.add(longToIpv4(network + offset))
            }
        }
        return out.toList()
    }

    fun parseSnis(text: String): List<String> =
        tokens(text).map { it.lowercase() }.filter { isHostName(it) }.distinct()

    /** Every IP with every server name, trimmed to [MAX_PAIRS] while mixing names evenly. */
    fun pairs(ips: List<String>, snis: List<String>, max: Int = MAX_PAIRS): List<CdnCandidate> {
        if (ips.isEmpty() || snis.isEmpty()) return emptyList()
        val out = ArrayList<CdnCandidate>()
        for ((i, ip) in ips.withIndex()) {
            for (j in snis.indices) {
                // Rotate the name per address so a short list still covers every name.
                out.add(CdnCandidate(ip, snis[(i + j) % snis.size]))
            }
        }
        return out.take(max)
    }

    private fun tokens(text: String): List<String> =
        text.split('\n', ',', '،', ';', ' ', '\t', '\r').map { it.trim() }.filter { it.isNotEmpty() }

    private fun isHostName(value: String): Boolean =
        value.length <= 253 && Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$").matches(value)

    internal fun ipv4ToLong(ip: String): Long? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val n = part.toIntOrNull() ?: return null
            if (n !in 0..255 || part.length > 3) return null
            value = (value shl 8) or n.toLong()
        }
        return value
    }

    internal fun longToIpv4(value: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { ((value shr it) and 0xFF).toString() }
}
