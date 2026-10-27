package com.aras.client.core.cdn

import com.aras.client.dto.entities.ProfileItem
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** What a CDN/SNI profile asks for, after its two text fields are read. */
data class CdnSniSettings(
    /** Xray routing domain rules for the sites to unblock, e.g. `domain:github.com`. */
    val targets: List<String>,
    /** Allowed server names; the first is sent in the handshake. */
    val snis: List<String>,
    /** Optional CDN edge addresses to dial; empty means resolve the site's own name. */
    val edgeIps: List<String>,
)

/**
 * CDN/SNI as a standalone profile: no server, only the sites to unblock and the
 * server name (and optionally edge IPs) to present to the CDN.
 *
 * It follows the MITM domain-fronting setup for Xray. The browser's TLS to a target
 * site is ended locally with a CA that exists only on this device, the plain HTTP
 * request (with its real Host header) is read, and a new TLS connection is made to a
 * CDN edge under the allowed server name. The CDN routes on the Host header; the
 * network only sees the allowed name.
 */
object CdnSniConfig {

    const val MODE_AUTO = "auto"
    const val MODE_MANUAL = "manual"

    const val INBOUND_TAG = "cdn-sni-decrypt"
    const val REDIRECT_TAG = "cdn-sni-redirect"
    const val REPACK_TAG = "cdn-sni-repack"
    const val EDGE_HOST = "cdn-sni.redirect"
    const val DECRYPT_PORT = 11777

    /** What "auto" fills in: Google's own services fronted under www.google.com. */
    const val AUTO_TARGETS = "geosite:google"
    const val AUTO_FRONT = "www.google.com"

    private val ruleKinds = listOf("geosite:", "domain:", "full:", "regexp:", "keyword:")
    private val hostName = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /** A host name, not a dotted number (which would otherwise match the name pattern). */
    private fun isName(token: String) = hostName.matches(token) && token.any { it.isLetter() }

    /** Entries may be separated by Enter, comma (English or Persian), semicolon, space or tab. */
    fun tokens(text: String?): List<String> =
        text.orEmpty().split('\n', '\r', ',', '،', ';', ' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }

    /** Values a profile actually uses: the presets in auto mode, the typed fields otherwise. */
    fun effectiveTargets(profile: ProfileItem): String =
        if (profile.cdnSniMode == MODE_MANUAL) profile.cdnSniTargets.orEmpty() else AUTO_TARGETS

    fun effectiveFront(profile: ProfileItem): String =
        if (profile.cdnSniMode == MODE_MANUAL) profile.cdnSniFront.orEmpty() else AUTO_FRONT

    fun parseTargets(text: String?): List<String> =
        tokens(text).mapNotNull { raw ->
            val token = raw.lowercase()
            when {
                ruleKinds.any { token.startsWith(it) && token.length > it.length } -> token
                isName(token) -> "domain:$token"
                else -> null
            }
        }.distinct()

    fun parseFront(text: String?): Pair<List<String>, List<String>> {
        val snis = ArrayList<String>()
        val ips = ArrayList<String>()
        for (raw in tokens(text)) {
            val token = raw.lowercase()
            when {
                CdnIp.isIpv4(token) -> ips.add(token)
                isName(token) -> snis.add(token)
            }
        }
        return snis.distinct() to ips.distinct()
    }

    /** Null when the profile is missing something it needs; the message says what. */
    fun settingsOf(profile: ProfileItem): CdnSniSettings? {
        val targets = parseTargets(effectiveTargets(profile))
        val (snis, ips) = parseFront(effectiveFront(profile))
        if (targets.isEmpty() || snis.isEmpty()) return null
        return CdnSniSettings(targets, snis, ips)
    }

    /**
     * Adds the local TLS-ending inbound, the two outbounds and the routing rules to a
     * finished Xray config, and makes sure traffic carries the site name so the rules
     * can match it.
     *
     * @param caCertPem the device-only CA certificate, PEM
     * @param caKeyPem its private key, PEM
     */
    fun inject(root: JsonObject, settings: CdnSniSettings, caCertPem: String, caKeyPem: String) {
        val inbounds = array(root, "inbounds")
        val outbounds = array(root, "outbounds")
        val routing = obj(root, "routing")
        val rules = array(routing, "rules")

        // The name in the TLS handshake must come through to routing, and the
        // connection must then be made to the name rather than to an address the
        // application resolved itself.
        for (element in inbounds) {
            val inbound = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            if (inbound.get("tag")?.asString == INBOUND_TAG) continue
            val sniffing = inbound.get("sniffing")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: JsonObject().also { inbound.add("sniffing", it) }
            sniffing.addProperty("enabled", true)
            sniffing.addProperty("routeOnly", false)
            val dest = sniffing.get("destOverride")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: JsonArray().also { sniffing.add("destOverride", it) }
            for (kind in listOf("http", "tls")) {
                if (dest.none { it.isJsonPrimitive && it.asString == kind }) dest.add(kind)
            }
        }

        inbounds.add(decryptInbound(caCertPem, caKeyPem))

        outbounds.add(redirectOutbound())
        outbounds.add(repackOutbound(settings))

        if (settings.edgeIps.isNotEmpty()) {
            val dns = obj(root, "dns")
            val hosts = obj(dns, "hosts")
            hosts.add(EDGE_HOST, JsonArray().apply { settings.edgeIps.forEach { add(it) } })
        }

        // Rules go first so they win over the app's own routing: whatever the local
        // inbound decrypted goes out re-packed, and the target sites are sent into it.
        val front = JsonArray()
        front.add(JsonObject().apply {
            add("inboundTag", JsonArray().apply { add(INBOUND_TAG) })
            addProperty("outboundTag", REPACK_TAG)
        })
        front.add(JsonObject().apply {
            addProperty("network", "tcp")
            addProperty("port", "443")
            add("domain", JsonArray().apply { settings.targets.forEach { add(it) } })
            addProperty("outboundTag", REDIRECT_TAG)
        })
        val merged = JsonArray()
        front.forEach { merged.add(it) }
        rules.forEach { merged.add(it) }
        routing.add("rules", merged)
    }

    private fun decryptInbound(certPem: String, keyPem: String) = JsonObject().apply {
        addProperty("tag", INBOUND_TAG)
        addProperty("listen", "127.0.0.1")
        addProperty("port", DECRYPT_PORT)
        addProperty("protocol", "tunnel")
        add("settings", JsonObject().apply {
            addProperty("network", "tcp")
            addProperty("port", 443)
            addProperty("followRedirect", true)
        })
        add("streamSettings", JsonObject().apply {
            addProperty("security", "tls")
            add("tlsSettings", JsonObject().apply {
                add("alpn", JsonArray().apply { add("h2"); add("http/1.1") })
                add("certificates", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("usage", "issue")
                        add("certificate", lines(certPem))
                        add("key", lines(keyPem))
                    })
                })
            })
        })
    }

    private fun redirectOutbound() = JsonObject().apply {
        addProperty("tag", REDIRECT_TAG)
        addProperty("protocol", "freedom")
        add("settings", JsonObject().apply { addProperty("redirect", "127.0.0.1:$DECRYPT_PORT") })
    }

    private fun repackOutbound(settings: CdnSniSettings) = JsonObject().apply {
        addProperty("tag", REPACK_TAG)
        addProperty("protocol", "freedom")
        if (settings.edgeIps.isNotEmpty()) {
            add("settings", JsonObject().apply { addProperty("redirect", "$EDGE_HOST:443") })
        }
        add("streamSettings", JsonObject().apply {
            addProperty("security", "tls")
            add("tlsSettings", JsonObject().apply {
                addProperty("serverName", settings.snis.first())
                // "fromMitM" accepts a certificate valid for the site the browser asked
                // for, which is what an edge serving that site presents; the allowed
                // names are accepted too.
                addProperty("verifyPeerCertByName", (listOf("fromMitM") + settings.snis).joinToString(","))
                add("alpn", JsonArray().apply { add("fromMitM") })
                addProperty("fingerprint", "chrome")
            })
            add("sockopt", JsonObject().apply { addProperty("domainStrategy", "ForceIP") })
        })
    }

    private fun lines(pem: String) =
        JsonArray().apply { pem.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) } }

    private fun array(parent: JsonObject, name: String): JsonArray =
        parent.get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray().also { parent.add(name, it) }

    private fun obj(parent: JsonObject, name: String): JsonObject =
        parent.get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { parent.add(name, it) }
}

object CdnIp {
    fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { p -> p.length in 1..3 && p.all(Char::isDigit) && p.toInt() in 0..255 }
    }
}
