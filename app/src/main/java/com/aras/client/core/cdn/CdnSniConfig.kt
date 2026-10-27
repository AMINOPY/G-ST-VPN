package com.aras.client.core.cdn

import com.aras.client.dto.entities.ProfileItem
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** What a CDN/SNI profile asks for, after its two text fields are read. */
data class CdnSniSettings(
    /** The single CDN edge IP to dial. */
    val edgeIp: String,
    /** The TLS server name sent by Xray to that edge. */
    val sni: String,
)

/**
 * CDN/SNI as a standalone profile: only a CDN edge IP and the TLS server name.
 *
 * It follows the MITM domain-fronting setup for Xray. The browser's TLS to a target
 * site is ended locally with a CA that exists only on this device, the plain HTTP
 * request (with its real Host header) is read, and a new TLS connection is made to
 * the configured CDN edge under the configured server name.
 */
object CdnSniConfig {

    const val MODE_AUTO = "auto"
    const val MODE_MANUAL = "manual"

    const val INBOUND_TAG = "cdn-sni-decrypt"
    const val REDIRECT_TAG = "cdn-sni-redirect"
    const val REPACK_TAG = "cdn-sni-repack"
    const val EDGE_HOST = "cdn-sni.redirect"
    const val DECRYPT_PORT = 11777

    private val hostName = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /** A host name, not a dotted number (which would otherwise match the name pattern). */
    private fun isName(token: String) = hostName.matches(token) && token.any { it.isLetter() }

    /** Entries may be separated by Enter, comma (English or Persian), semicolon, space or tab. */
    fun tokens(text: String?): List<String> =
        text.orEmpty().split('\n', '\r', ',', '،', ';', ' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }

    /** IP and SNI are the only connection values; old editor mode is ignored. */
    fun effectiveIp(profile: ProfileItem): String {
        val candidates = tokens(profile.cdnSniTargets) + tokens(profile.cdnSniFront)
        return candidates.firstOrNull { CdnIp.isIpv4(it) }.orEmpty()
    }

    fun effectiveSni(profile: ProfileItem): String {
        val candidates = tokens(profile.cdnSniFront) + tokens(profile.cdnSniTargets)
        return candidates.firstOrNull { isName(it.lowercase()) }?.lowercase().orEmpty()
    }

    fun parseIp(text: String?): String? =
        tokens(text).singleOrNull()?.takeIf { CdnIp.isIpv4(it) }

    fun parseSni(text: String?): String? =
        tokens(text).singleOrNull()?.lowercase()?.takeIf { isName(it) }

    /** Null when either field is not exactly one valid value. */
    fun settingsOf(profile: ProfileItem): CdnSniSettings? {
        val ip = parseIp(profile.cdnSniTargets) ?: return null
        val sni = parseSni(profile.cdnSniFront) ?: return null
        return CdnSniSettings(edgeIp = ip, sni = sni)
    }

    /**
     * Adds the local TLS-ending inbound, the two outbounds and the routing rules to a
     * finished Xray config. This mode intercepts TCP/443 traffic while this profile is active.
     *
     * @param caCertPem the device-only CA certificate, PEM
     * @param caKeyPem its private key, PEM
     */
    fun inject(root: JsonObject, settings: CdnSniSettings, caCertPem: String, caKeyPem: String) {
        val inbounds = array(root, "inbounds")
        val outbounds = array(root, "outbounds")
        val routing = obj(root, "routing")
        val rules = array(routing, "rules")

        // Enable TLS/HTTP sniffing on existing inbounds so the local bridge can inspect
        // the original request and preserve its HTTP Host when repacking the TLS stream.
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

        val dns = obj(root, "dns")
        val hosts = obj(dns, "hosts")
        hosts.addProperty(EDGE_HOST, settings.edgeIp)

        // Rules go first: decrypted local traffic is re-packed; TCP/443 from the device
        // enters the local TLS bridge before ordinary routing rules are considered.
        val front = JsonArray()
        front.add(JsonObject().apply {
            add("inboundTag", JsonArray().apply { add(INBOUND_TAG) })
            addProperty("outboundTag", REPACK_TAG)
        })
        front.add(JsonObject().apply {
            // This profile is a standalone connection mode: route TLS traffic through
            // the local Xray TLS bridge, without requiring a third "target sites" field.
            addProperty("network", "tcp")
            addProperty("port", "443")
            addProperty("outboundTag", REDIRECT_TAG)
        })
        val merged = JsonArray()
        front.forEach { merged.add(it) }
        rules.forEach { merged.add(it) }
        routing.add("rules", merged)
    }

    /**
     * Structural sanity check after injection. This catches incomplete or conflicting
     * generated JSON before it is handed to the native core; it does not claim that the
     * remote CDN accepts the configured SNI or that a live VPN connection works.
     *
     * @return a diagnostic when a required generated component is missing or inconsistent.
     */
    fun validateInjectedConfig(root: JsonObject, settings: CdnSniSettings): String? {
        val inbounds = root.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return "Missing Xray inbounds"
        val outbounds = root.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return "Missing Xray outbounds"
        val rules = root.getAsJsonObject("routing")?.get("rules")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return "Missing Xray routing rules"

        fun countTag(items: JsonArray, tag: String) = items.count {
            it.isJsonObject && it.asJsonObject.get("tag")?.let { value ->
                value.isJsonPrimitive && value.asString == tag
            } == true
        }
        if (countTag(inbounds, INBOUND_TAG) != 1) return "CDN/SNI local TLS inbound is missing or duplicated"
        if (countTag(outbounds, REDIRECT_TAG) != 1 || countTag(outbounds, REPACK_TAG) != 1) {
            return "CDN/SNI outbound is missing or duplicated"
        }

        val repack = outbounds.firstOrNull {
            it.isJsonObject && it.asJsonObject.get("tag")?.asString == REPACK_TAG
        }?.asJsonObject ?: return "CDN/SNI outbound is missing"
        val tls = repack.getAsJsonObject("streamSettings")?.getAsJsonObject("tlsSettings")
            ?: return "CDN/SNI TLS settings are missing"
        if (tls.get("serverName")?.asString != settings.sni) return "CDN/SNI hostname does not match the profile"
        val hosts = root.getAsJsonObject("dns")?.getAsJsonObject("hosts")
            ?: return "CDN/SNI DNS mapping is missing"
        if (hosts.get(EDGE_HOST)?.asString != settings.edgeIp) return "CDN/SNI edge IP mapping does not match the profile"

        val inboundRule = rules.count {
            it.isJsonObject &&
                it.asJsonObject.get("outboundTag")?.asString == REPACK_TAG &&
                it.asJsonObject.getAsJsonArray("inboundTag")?.any { tag -> tag.asString == INBOUND_TAG } == true
        }
        val tlsRule = rules.count {
            it.isJsonObject &&
                it.asJsonObject.get("outboundTag")?.asString == REDIRECT_TAG &&
                it.asJsonObject.get("network")?.asString == "tcp" &&
                it.asJsonObject.get("port")?.asString == "443"
        }
        if (inboundRule != 1 || tlsRule != 1) return "CDN/SNI routing rules are missing or duplicated"
        return null
    }

    private fun decryptInbound(certPem: String, keyPem: String) = JsonObject().apply {
        addProperty("tag", INBOUND_TAG)
        addProperty("listen", "127.0.0.1")
        addProperty("port", DECRYPT_PORT)
        addProperty("protocol", "dokodemo-door")
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
        add("settings", JsonObject().apply { addProperty("redirect", "$EDGE_HOST:443") })
        add("streamSettings", JsonObject().apply {
            addProperty("security", "tls")
            add("tlsSettings", JsonObject().apply {
                addProperty("serverName", settings.sni)
                // The origin certificate is selected by the request's Host at the edge;
                // Xray's local TLS bridge has already terminated the device-side TLS.
                addProperty("verifyPeerCertByName", "fromMitM,${settings.sni}")
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
        // Accept only canonical ASCII dotted-decimal IPv4. Char.isDigit() also accepts
        // non-ASCII numerals, which are not valid in an Xray IP literal.
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() &&
                part.length <= 3 &&
                part.all { it in '0'..'9' } &&
                (part.length == 1 || part[0] != '0') &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
