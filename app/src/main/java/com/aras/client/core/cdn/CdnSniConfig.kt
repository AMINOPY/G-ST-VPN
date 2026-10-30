package com.aras.client.core.cdn

import com.aras.client.dto.entities.ProfileItem
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** The two values used by Psiphon's CDN-fronted transport. */
data class CdnSniSettings(
    val edgeIp: String,
    val sni: String,
)

/**
 * Xray's CDN/SNI profile is carried by a real Psiphon session.
 *
 * The edge IP and SNI are passed to Psiphon when its transport is started. Xray
 * then sends TCP through the Psiphon SOCKS listener. There is deliberately no
 * local TLS interception, generated CA, or direct-to-edge fallback in this path.
 */
object CdnSniConfig {
    const val MODE_AUTO = "auto"
    const val MODE_MANUAL = "manual"

    const val PROXY_TAG = "proxy"
    const val UDP_BLOCK_TAG = "cdn-sni-udp-block"

    private val hostName = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    private fun isName(token: String) = hostName.matches(token) && token.any { it.isLetter() }

    /** Entries may be separated by Enter, comma (English or Persian), semicolon, space or tab. */
    fun tokens(text: String?): List<String> =
        text.orEmpty().split('\n', '\r', ',', '،', ';', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

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

    /** Remove the sample proxy outbound and its rules before installing the Psiphon SOCKS hop. */
    fun removeTemplateProxyOutbound(root: JsonObject) {
        val outbounds = root.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray
        outbounds?.toList()?.filter { element ->
            element.isJsonObject && element.asJsonObject.get("tag")?.let { tag ->
                tag.isJsonPrimitive && tag.asString == PROXY_TAG
            } == true
        }?.forEach { outbounds.remove(it) }

        val rules = root.getAsJsonObject("routing")?.get("rules")
            ?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        rules.toList().filter { element ->
            element.isJsonObject && element.asJsonObject.get("outboundTag")?.let { tag ->
                tag.isJsonPrimitive && tag.asString == PROXY_TAG
            } == true
        }.forEach { rules.remove(it) }
    }

    /**
     * Route Xray TCP through the already-started Psiphon SOCKS listener.
     * UDP is explicitly blocked because the Psiphon listener is a TCP SOCKS
     * transport; it must never leak through Xray's direct outbound.
     */
    fun inject(root: JsonObject, socksPort: Int) {
        require(socksPort in 1..65535) { "Invalid Psiphon SOCKS port" }
        val outbounds = array(root, "outbounds")
        val routing = obj(root, "routing")
        val rules = array(routing, "rules")

        // This standalone profile has no alternate direct transport. Remove the
        // template's direct bypasses so TCP cannot escape around the Psiphon carrier.
        rules.toList().filter { element ->
            element.isJsonObject && element.asJsonObject.get("outboundTag")?.asString == "direct"
        }.forEach { rules.remove(it) }

        outbounds.add(psiphonSocksOutbound(socksPort))
        if (outbounds.none { it.isJsonObject && it.asJsonObject.get("tag")?.asString == UDP_BLOCK_TAG }) {
            outbounds.add(JsonObject().apply {
                addProperty("tag", UDP_BLOCK_TAG)
                addProperty("protocol", "blackhole")
                add("settings", JsonObject())
            })
        }

        // Append after the template's explicit/private-domain rules, preserving those
        // exceptions while making the default TCP path fail closed through Psiphon.
        rules.add(JsonObject().apply {
            addProperty("network", "tcp")
            addProperty("outboundTag", PROXY_TAG)
        })
        rules.add(JsonObject().apply {
            addProperty("network", "udp")
            addProperty("outboundTag", UDP_BLOCK_TAG)
        })
    }

    /** Structural validation; it does not claim that Psiphon reached a remote server. */
    fun validateInjectedConfig(root: JsonObject, socksPort: Int): String? {
        val outbounds = root.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return "Missing Xray outbounds"
        val rules = root.getAsJsonObject("routing")?.get("rules")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return "Missing Xray routing rules"

        val proxy = outbounds.firstOrNull {
            it.isJsonObject && it.asJsonObject.get("tag")?.asString == PROXY_TAG
        }?.asJsonObject ?: return "Psiphon SOCKS outbound is missing"
        if (proxy.get("protocol")?.asString != "socks") return "CDN/SNI proxy is not a SOCKS outbound"
        val server = proxy.getAsJsonObject("settings")?.getAsJsonArray("servers")
            ?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return "Psiphon SOCKS listener is missing"
        if (server.get("address")?.asString != "127.0.0.1" ||
            server.get("port")?.asInt != socksPort
        ) return "Psiphon SOCKS listener does not match the configured port"

        val tcpRule = rules.count {
            it.isJsonObject &&
                it.asJsonObject.get("network")?.asString == "tcp" &&
                it.asJsonObject.get("outboundTag")?.asString == PROXY_TAG
        }
        val udpRule = rules.count {
            it.isJsonObject &&
                it.asJsonObject.get("network")?.asString == "udp" &&
                it.asJsonObject.get("outboundTag")?.asString == UDP_BLOCK_TAG
        }
        if (tcpRule != 1) return "TCP traffic is not exclusively routed through Psiphon"
        if (udpRule != 1) return "UDP fail-closed rule is missing"
        return null
    }

    private fun psiphonSocksOutbound(port: Int) = JsonObject().apply {
        addProperty("tag", PROXY_TAG)
        addProperty("protocol", "socks")
        add("settings", JsonObject().apply {
            add("servers", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("address", "127.0.0.1")
                    addProperty("port", port)
                })
            })
        })
    }

    private fun array(parent: JsonObject, name: String): JsonArray =
        parent.get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray().also { parent.add(name, it) }

    private fun obj(parent: JsonObject, name: String): JsonObject =
        parent.get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { parent.add(name, it) }
}

object CdnIp {
    fun isIpv4(value: String): Boolean {
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
