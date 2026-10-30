package com.aras.client.core.cdn

import com.aras.client.dto.entities.ProfileItem
import com.aras.client.enums.EConfigType
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CdnSniConfigTest {
    private fun profile(ip: String?, sni: String?) =
        ProfileItem.create(EConfigType.CDN_SNI).apply {
            cdnSniTargets = ip
            cdnSniFront = sni
        }

    @Test
    fun tokensSplitOnEnterCommaPersianCommaSpaceAndSemicolon() {
        assertEquals(
            listOf("a.com", "b.com", "c.com", "d.com", "e.com"),
            CdnSniConfig.tokens("a.com\nb.com, c.com،d.com ;e.com")
        )
    }

    @Test
    fun parsesOneIpAndSniWithoutImportedConfig() {
        val settings = CdnSniConfig.settingsOf(profile("104.16.1.2", "WWW.Google.com"))!!
        assertEquals("104.16.1.2", settings.edgeIp)
        assertEquals("www.google.com", settings.sni)
    }
    
    @Test
    fun parsesMultipleManualCandidatesAndNormalizesSni() {
        val settings = CdnSniConfig.settingsOf(
            profile("104.16.1.2\n104.16.1.3, 104.16.1.2", "WWW.Google.com،www.cloudflare.com")
        )!!
        assertEquals("104.16.1.2,104.16.1.3", settings.edgeIp)
        assertEquals("www.google.com,www.cloudflare.com", settings.sni)
    }

    @Test
    fun rejectsMissingOrInvalidIpAndSni() {
        assertNull(CdnSniConfig.settingsOf(profile("", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("999.1.1.1", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("01.2.3.4", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("1.2.3.４", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("1..3.4", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("104.16.1.2 invalid", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("104.16.1.2", "not_a_hostname")))
        assertNull(CdnSniConfig.settingsOf(profile("104.16.1.2", "www.google.com invalid_host")))
        assertNotNull(CdnSniConfig.settingsOf(profile("104.16.1.2", "www.google.com")))
    }

    private fun baseConfig(): JsonObject = JsonParser.parseString(
        """{"inbounds":[{"tag":"tun","protocol":"tun"}],
            "outbounds":[{"tag":"proxy","protocol":"vmess"},{"tag":"direct","protocol":"freedom"},{"tag":"block","protocol":"blackhole"}],
            "routing":{"rules":[{"outboundTag":"proxy","network":"tcp,udp"},{"outboundTag":"direct","domain":["domain:private"]}]},
            "dns":{"servers":["1.1.1.1","https+local://dns.google/dns-query","quic+local://dns.adguard.com"],"hosts":{}}}"""
    ).asJsonObject
    private fun tags(a: JsonArray) = a.map { it.asJsonObject.get("tag")?.asString }

    @Test
    fun removesSampleProxyAndItsRulesWithoutRemovingDirectOrBlock() {
        val root = baseConfig()
        CdnSniConfig.removeTemplateProxyOutbound(root)
        assertFalse(tags(root.getAsJsonArray("outbounds")).contains("proxy"))
        assertTrue(tags(root.getAsJsonArray("outbounds")).contains("direct"))
        assertTrue(tags(root.getAsJsonArray("outbounds")).contains("block"))
        assertEquals(1, root.getAsJsonObject("routing").getAsJsonArray("rules").size())
        assertEquals("direct", root.getAsJsonObject("routing").getAsJsonArray("rules")[0].asJsonObject.get("outboundTag").asString)
    }

    @Test
    fun injectRoutesTcpThroughPsiphonSocksAndBlocksUdp() {
        val root = baseConfig()
        CdnSniConfig.removeTemplateProxyOutbound(root)
        CdnSniConfig.inject(root, 10808)

        val proxy = root.getAsJsonArray("outbounds").map { it.asJsonObject }
            .first { it.get("tag").asString == CdnSniConfig.PROXY_TAG }
        assertEquals("socks", proxy.get("protocol").asString)
        val server = proxy.getAsJsonObject("settings").getAsJsonArray("servers")[0].asJsonObject
        assertEquals("127.0.0.1", server.get("address").asString)
        assertEquals(10808, server.get("port").asInt)

        val rules = root.getAsJsonObject("routing").getAsJsonArray("rules")
        assertFalse(rules.any { it.isJsonObject && it.asJsonObject.get("outboundTag")?.asString == "direct" })
        assertEquals(1, rules.count { it.isJsonObject && it.asJsonObject.get("network")?.asString == "tcp" &&
            it.asJsonObject.get("outboundTag")?.asString == CdnSniConfig.PROXY_TAG })
        assertEquals(1, rules.count { it.isJsonObject && it.asJsonObject.get("network")?.asString == "udp" &&
            it.asJsonObject.get("outboundTag")?.asString == CdnSniConfig.UDP_BLOCK_TAG })
        assertTrue(rules.any { it.isJsonObject &&
            it.asJsonObject.get("outboundTag")?.asString == CdnSniConfig.PROXY_TAG &&
            it.asJsonObject.getAsJsonArray("inboundTag")?.any { tag -> tag.asString == "dns-module" } == true })
        assertTrue(rules.any { it.isJsonObject &&
            it.asJsonObject.get("outboundTag")?.asString == "dns-out" &&
            it.asJsonObject.get("port")?.asString == "53" &&
            it.asJsonObject.getAsJsonArray("inboundTag")?.any { tag -> tag.asString == "tun" } == true })
        val dnsServers = root.getAsJsonObject("dns").getAsJsonArray("servers").map { it.asString }
        assertEquals(listOf("tcp://1.1.1.1", "https://dns.google/dns-query"), dnsServers)
        assertEquals("blackhole", root.getAsJsonArray("outbounds").map { it.asJsonObject }
            .first { it.get("tag").asString == CdnSniConfig.UDP_BLOCK_TAG }.get("protocol").asString)
        assertFalse(root.getAsJsonArray("inbounds").any { it.asJsonObject.get("tag")?.asString?.contains("decrypt") == true })
    }

    @Test
    fun validatesPsiphonSocksAndFailClosedUdpRouting() {
        val root = baseConfig()
        CdnSniConfig.removeTemplateProxyOutbound(root)
        CdnSniConfig.inject(root, 10808)
        assertNull(CdnSniConfig.validateInjectedConfig(root, 10808))
        assertEquals("Psiphon SOCKS listener does not match the configured port",
            CdnSniConfig.validateInjectedConfig(root, 10809))
    }

    @Test
    fun validationRejectsMissingUdpBlockRule() {
        val root = baseConfig()
        CdnSniConfig.removeTemplateProxyOutbound(root)
        CdnSniConfig.inject(root, 10808)
        val rules = root.getAsJsonObject("routing").getAsJsonArray("rules")
        rules.remove(rules.size() - 1)
        assertEquals("UDP fail-closed rule is missing", CdnSniConfig.validateInjectedConfig(root, 10808))
    }
}