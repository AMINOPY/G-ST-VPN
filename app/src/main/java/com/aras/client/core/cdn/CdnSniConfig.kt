package com.aras.client.core.cdn

import com.aras.client.AppConfig
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
            if (!element.isJsonObject || element.asJsonObject.get("outboundTag")?.asString != PROXY_TAG) {
                false
            } else {
                // Keep inbound-specific routes (notably Xray's internal DNS -> proxy rule).
                val inboundTags = element.asJsonObject.get("inboundTag")
                inboundTags == null || !inboundTags.isJsonArray || inboundTags.asJsonArray.size() == 0
            }
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

        configureDnsOverPsiphon(root, outbounds, rules)

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


    /**
     * DNS must not escape through UDP or Android's local resolver. Force routed
     * DNS upstreams onto TCP/DoH, and send port-53 traffic from Xray's TUN/SOCKS
     * inbounds into its DNS module. The DNS module's own requests are routed to
     * the Psiphon SOCKS outbound by the inboundTag=dns rule.
     */
    private fun configureDnsOverPsiphon(root: JsonObject, outbounds: JsonArray, rules: JsonArray) {
        val dns = obj(root, "dns")
        val servers = array(dns, "servers")
        val normalized = JsonArray()
        servers.forEach { element ->
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    normalizeDnsAddress(element.asString)?.let { normalized.add(it) }
                }
                element.isJsonObject -> {
                    val server = element.asJsonObject
                    val address = server.get("address")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString
                    if (address == null) {
                        normalized.add(server)
                    } else {
                        normalizeDnsAddress(address)?.let { normalized.add(server.apply { addProperty("address", it) }) }
                    }
                }
                else -> Unit
            }
        }
        val hasRoutedResolver = normalized.any { element ->
            val address = if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                element.asString
            } else if (element.isJsonObject) {
                element.asJsonObject.get("address")?.asString
            } else {
                null
            }
            !address.isNullOrBlank() && address != "fakedns"
        }
        if (!hasRoutedResolver) {
            normalized.add("tcp://1.1.1.1")
            normalized.add("tcp://8.8.8.8")
        }
        dns.add("servers", normalized)

        if (outbounds.none { it.isJsonObject && it.asJsonObject.get("tag")?.asString == "dns-out" }) {
            outbounds.add(JsonObject().apply {
                addProperty("tag", "dns-out")
                addProperty("protocol", "dns")
                add("settings", JsonObject())
            })
        }

        val dnsRuleExists = rules.any { rule ->
            rule.isJsonObject &&
                rule.asJsonObject.get("outboundTag")?.asString == PROXY_TAG &&
                rule.asJsonObject.getAsJsonArray("inboundTag")?.any { it.asString == AppConfig.TAG_DNS } == true
        }
        if (!dnsRuleExists) {
            rules.add(JsonObject().apply {
                add("inboundTag", JsonArray().apply { add(AppConfig.TAG_DNS) })
                addProperty("outboundTag", PROXY_TAG)
            })
        }

        val inbounds = root.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        inbounds.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject?.get("tag")?.asString
                ?.takeIf { it == "tun" || it == "socks" }
        }.distinct().forEach { tag ->
            val exists = rules.any { rule ->
                rule.isJsonObject &&
                    rule.asJsonObject.get("outboundTag")?.asString == "dns-out" &&
                    rule.asJsonObject.get("port")?.asString == "53" &&
                    rule.asJsonObject.getAsJsonArray("inboundTag")?.any { it.asString == tag } == true
            }
            if (!exists) {
                rules.add(JsonObject().apply {
                    add("inboundTag", JsonArray().apply { add(tag) })
                    addProperty("port", "53")
                    addProperty("outboundTag", "dns-out")
                })
            }
        }
    }

    private fun normalizeDnsAddress(address: String): String? {
        val value = address.trim()
        if (value.isEmpty()) return null
        return when {
            value == "fakedns" -> value
            value == "localhost" -> null
            value.startsWith("https+local://") -> value.replaceFirst("https+local://", "https://")
            value.startsWith("tcp+local://") -> value.replaceFirst("tcp+local://", "tcp://")
            value.startsWith("quic://") || value.startsWith("quic+local://") -> null
            value.startsWith("https://") || value.startsWith("tcp://") -> value
            value.contains("://") -> null
            else -> "tcp://$value"
        }
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

        if (rules.any { it.isJsonObject && it.asJsonObject.get("outboundTag")?.asString == "direct" }) {
            return "CDN/SNI direct bypass rule is present"
        }

        val dns = root.getAsJsonObject("dns") ?: return "Xray DNS configuration is missing"
        val dnsServers = dns.getAsJsonArray("servers") ?: return "Psiphon-routed DNS servers are missing"
        if (dnsServers.size() == 0) return "Psiphon-routed DNS servers are empty"
        if (dnsServers.any { element ->
                val address = if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                    element.asString
                } else if (element.isJsonObject) {
                    element.asJsonObject.get("address")?.asString
                } else null
                address?.let { it.startsWith("quic") || it.startsWith("udp://") || it.contains("+local://") } == true
            }
        ) return "A DNS server would bypass the Psiphon TCP transport"

        if (outbounds.none {
                it.isJsonObject && it.asJsonObject.get("tag")?.asString == "dns-out" &&
                    it.asJsonObject.get("protocol")?.asString == "dns"
            }
        ) return "Xray DNS outbound is missing"
        val dnsProxyRule = rules.count {
            it.isJsonObject &&
                it.asJsonObject.get("outboundTag")?.asString == PROXY_TAG &&
                it.asJsonObject.getAsJsonArray("inboundTag")?.any { tag -> tag.asString == AppConfig.TAG_DNS } == true
        }
        if (dnsProxyRule != 1) return "Xray DNS requests are not routed through Psiphon"

        val inbounds = root.getAsJsonArray("inbounds")
        inbounds.mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject?.get("tag")?.asString }
            .filter { it == "tun" || it == "socks" }
            .distinct()
            .forEach { tag ->
                val hijack = rules.any {
                    it.isJsonObject &&
                        it.asJsonObject.get("outboundTag")?.asString == "dns-out" &&
                        it.asJsonObject.get("port")?.asString == "53" &&
                        it.asJsonObject.getAsJsonArray("inboundTag")?.any { item -> item.asString == tag } == true
                }
                if (!hijack) return "DNS requests from $tag are not handled by Xray"
            }

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
