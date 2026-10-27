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
    fun rejectsMissingOrInvalidIpAndSni() {
        assertNull(CdnSniConfig.settingsOf(profile("", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("999.1.1.1", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("01.2.3.4", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("1.2.3.４", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("1..3.4", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("104.16.1.2 104.16.1.3", "www.google.com")))
        assertNull(CdnSniConfig.settingsOf(profile("104.16.1.2", "not_a_hostname")))
        assertNull(CdnSniConfig.settingsOf(profile("104.16.1.2", "www.google.com other.example")))
        assertNotNull(CdnSniConfig.settingsOf(profile("104.16.1.2", "www.google.com")))
    }

    private fun baseConfig(): JsonObject = JsonParser.parseString(
        """{"inbounds":[{"tag":"tun","protocol":"tun","sniffing":{"enabled":false,"destOverride":["http"],"routeOnly":true}}],
            "outbounds":[{"tag":"proxy","protocol":"freedom"}],
            "routing":{"rules":[{"outboundTag":"proxy","network":"tcp,udp"}]}}"""
    ).asJsonObject

    private fun tags(a: JsonArray) = a.map { it.asJsonObject.get("tag")?.asString }

    @Test
    fun removesInvalidTemplateProxyOutboundAndItsRules() {
        val root = baseConfig()
        CdnSniConfig.removeTemplateProxyOutbound(root)

        assertTrue(root.getAsJsonArray("outbounds").none {
            it.isJsonObject && it.asJsonObject.get("tag")?.asString == "proxy"
        })
        assertTrue(root.getAsJsonObject("routing").getAsJsonArray("rules").none {
            it.isJsonObject && it.asJsonObject.get("outboundTag")?.asString == "proxy"
        })
        assertTrue(root.getAsJsonArray("outbounds").any {
            it.isJsonObject && it.asJsonObject.get("tag")?.asString == "direct"
        })
    }

    @Test
    fun injectAddsBridgeAndRoutesTlsBeforeExistingRules() {
        val root = baseConfig()
        val settings = CdnSniSettings("151.101.1.1", "www.python.org")
        CdnSniConfig.inject(root, settings, "-----BEGIN CERTIFICATE-----\nAAA\n-----END CERTIFICATE-----\n", "-----BEGIN PRIVATE KEY-----\nBBB\n-----END PRIVATE KEY-----\n")

        assertTrue(CdnSniConfig.INBOUND_TAG in tags(root.getAsJsonArray("inbounds")))
        assertEquals(
            listOf("proxy", CdnSniConfig.REDIRECT_TAG, CdnSniConfig.REPACK_TAG),
            tags(root.getAsJsonArray("outbounds"))
        )
        val rules = root.getAsJsonObject("routing").getAsJsonArray("rules")
        assertEquals(3, rules.size())
        assertEquals(CdnSniConfig.REPACK_TAG, rules[0].asJsonObject.get("outboundTag").asString)
        assertEquals(CdnSniConfig.REDIRECT_TAG, rules[1].asJsonObject.get("outboundTag").asString)
        assertEquals("tcp", rules[1].asJsonObject.get("network").asString)
        assertEquals("443", rules[1].asJsonObject.get("port").asString)
        assertFalse(rules[1].asJsonObject.has("domain"))
        assertEquals("proxy", rules[2].asJsonObject.get("outboundTag").asString)
    }

    @Test
    fun injectKeepsCertificateAsPemLinesAndPrivateKeyInInboundOnly() {
        val root = baseConfig()
        CdnSniConfig.inject(
            root, CdnSniSettings("1.2.3.4", "b.com"),
            "-----BEGIN CERTIFICATE-----\nAAA\n-----END CERTIFICATE-----\n",
            "-----BEGIN PRIVATE KEY-----\nBBB\n-----END PRIVATE KEY-----\n"
        )
        val inbound = root.getAsJsonArray("inbounds").map { it.asJsonObject }
            .first { it.get("tag")?.asString == CdnSniConfig.INBOUND_TAG }
        val cert = inbound.getAsJsonObject("streamSettings").getAsJsonObject("tlsSettings")
            .getAsJsonArray("certificates")[0].asJsonObject
        assertEquals("issue", cert.get("usage").asString)
        assertEquals(3, cert.getAsJsonArray("certificate").size())
        assertEquals("-----BEGIN PRIVATE KEY-----", cert.getAsJsonArray("key")[0].asString)
        assertEquals("dokodemo-door", inbound.get("protocol").asString)
        assertTrue(inbound.getAsJsonObject("settings").get("followRedirect").asBoolean)
    }

    @Test
    fun injectMakesExistingInboundsSniffTheName() {
        val root = baseConfig()
        CdnSniConfig.inject(root, CdnSniSettings("1.2.3.4", "b.com"), "c", "k")
        val tun = root.getAsJsonArray("inbounds")[0].asJsonObject.getAsJsonObject("sniffing")
        assertTrue(tun.get("enabled").asBoolean)
        assertFalse(tun.get("routeOnly").asBoolean)
        val dest = tun.getAsJsonArray("destOverride").map { it.asString }
        assertTrue("tls" in dest && "http" in dest)
        assertEquals(1, dest.count { it == "http" })
    }

    @Test
    fun repackUsesConfiguredSniAndEdgeIp() {
        val root = baseConfig()
        CdnSniConfig.inject(root, CdnSniSettings("1.2.3.4", "front.example"), "c", "k")
        val repack = root.getAsJsonArray("outbounds").map { it.asJsonObject }
            .first { it.get("tag").asString == CdnSniConfig.REPACK_TAG }
        val tls = repack.getAsJsonObject("streamSettings").getAsJsonObject("tlsSettings")
        assertEquals("front.example", tls.get("serverName").asString)
        assertEquals("fromMitM,front.example", tls.get("verifyPeerCertByName").asString)
        assertEquals("${CdnSniConfig.EDGE_HOST}:443", repack.getAsJsonObject("settings").get("redirect").asString)
        assertEquals("1.2.3.4", root.getAsJsonObject("dns").getAsJsonObject("hosts").get(CdnSniConfig.EDGE_HOST).asString)
    }
    @Test
    fun validatesGeneratedConfigAndRejectsMismatchedEdgeIp() {
        val root = baseConfig()
        val settings = CdnSniSettings("1.2.3.4", "front.example")
        CdnSniConfig.inject(root, settings, "c", "k")

        assertNull(CdnSniConfig.validateInjectedConfig(root, settings))
        assertEquals(
            "CDN/SNI edge IP mapping does not match the profile",
            CdnSniConfig.validateInjectedConfig(root, settings.copy(edgeIp = "5.6.7.8"))
        )
    }

    @Test
    fun validatesGeneratedConfigAndRejectsMissingRoutingRule() {
        val root = baseConfig()
        val settings = CdnSniSettings("1.2.3.4", "front.example")
        CdnSniConfig.inject(root, settings, "c", "k")
        val rules = root.getAsJsonObject("routing").getAsJsonArray("rules")
        rules.remove(1)

        assertEquals(
            "CDN/SNI routing rules are missing or duplicated",
            CdnSniConfig.validateInjectedConfig(root, settings)
        )
    }

}
