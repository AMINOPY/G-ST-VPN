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

    private fun profile(mode: String?, targets: String? = null, front: String? = null) =
        ProfileItem.create(EConfigType.CDN_SNI).apply {
            cdnSniMode = mode; cdnSniTargets = targets; cdnSniFront = front
        }

    @Test
    fun tokensSplitOnEnterCommaPersianCommaSpaceAndSemicolon() {
        assertEquals(
            listOf("a.com", "b.com", "c.com", "d.com", "e.com"),
            CdnSniConfig.tokens("a.com\nb.com, c.com،d.com ;e.com")
        )
    }

    @Test
    fun targetsAreNormalisedAndInvalidOnesDropped() {
        assertEquals(
            listOf("domain:github.com", "geosite:google", "full:x.example", "domain:y.example"),
            CdnSniConfig.parseTargets("GitHub.com, geosite:google\nfull:x.example y.example bad_name geosite:")
        )
    }

    @Test
    fun frontSplitsNamesFromEdgeIps() {
        val (snis, ips) = CdnSniConfig.parseFront("www.google.com 104.16.1.2,\n104.16.1.2 999.1.1.1 WWW.Google.com")
        assertEquals(listOf("www.google.com"), snis)
        assertEquals(listOf("104.16.1.2"), ips)
    }

    @Test
    fun autoModeIgnoresStoredTextAndUsesThePreset() {
        val s = CdnSniConfig.settingsOf(profile(CdnSniConfig.MODE_AUTO, "ignored.com", "ignored.com"))!!
        assertEquals(listOf("geosite:google"), s.targets)
        assertEquals(listOf("www.google.com"), s.snis)
        assertTrue(s.edgeIps.isEmpty())
    }

    @Test
    fun manualModeNeedsBothFields() {
        assertNull(CdnSniConfig.settingsOf(profile(CdnSniConfig.MODE_MANUAL, "github.com", "")))
        assertNull(CdnSniConfig.settingsOf(profile(CdnSniConfig.MODE_MANUAL, "", "www.python.org")))
        assertNull(CdnSniConfig.settingsOf(profile(CdnSniConfig.MODE_MANUAL, "github.com", "1.2.3.4")))
        assertNotNull(CdnSniConfig.settingsOf(profile(CdnSniConfig.MODE_MANUAL, "github.com", "www.python.org")))
    }

    private fun baseConfig(): JsonObject = JsonParser.parseString(
        """{"inbounds":[{"tag":"tun","protocol":"tun","sniffing":{"enabled":false,"destOverride":["http"],"routeOnly":true}}],
            "outbounds":[{"tag":"proxy","protocol":"freedom"}],
            "routing":{"rules":[{"outboundTag":"proxy","network":"tcp,udp"}]}}"""
    ).asJsonObject

    private fun tags(a: JsonArray) = a.map { it.asJsonObject.get("tag")?.asString }

    @Test
    fun injectAddsInboundOutboundsAndPutsItsRulesFirst() {
        val root = baseConfig()
        val settings = CdnSniSettings(listOf("domain:github.com"), listOf("www.python.org"), listOf("151.101.1.1"))
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
        assertEquals("proxy", rules[2].asJsonObject.get("outboundTag").asString)
        assertEquals("domain:github.com", rules[1].asJsonObject.getAsJsonArray("domain")[0].asString)
    }

    @Test
    fun injectKeepsTheCertificateAsPemLinesAndTheKeyOutOfRouting() {
        val root = baseConfig()
        CdnSniConfig.inject(
            root, CdnSniSettings(listOf("domain:a.com"), listOf("b.com"), emptyList()),
            "-----BEGIN CERTIFICATE-----\nAAA\n-----END CERTIFICATE-----\n",
            "-----BEGIN PRIVATE KEY-----\nBBB\n-----END PRIVATE KEY-----\n"
        )
        val inbound = root.getAsJsonArray("inbounds").map { it.asJsonObject }.first { it.get("tag")?.asString == CdnSniConfig.INBOUND_TAG }
        val cert = inbound.getAsJsonObject("streamSettings").getAsJsonObject("tlsSettings")
            .getAsJsonArray("certificates")[0].asJsonObject
        assertEquals("issue", cert.get("usage").asString)
        assertEquals(3, cert.getAsJsonArray("certificate").size())
        assertEquals("-----BEGIN PRIVATE KEY-----", cert.getAsJsonArray("key")[0].asString)
        assertEquals("tunnel", inbound.get("protocol").asString)
        assertTrue(inbound.getAsJsonObject("settings").get("followRedirect").asBoolean)
    }

    @Test
    fun injectMakesExistingInboundsSniffTheNameAndDialByIt() {
        val root = baseConfig()
        CdnSniConfig.inject(root, CdnSniSettings(listOf("domain:a.com"), listOf("b.com"), emptyList()), "c", "k")
        val tun = root.getAsJsonArray("inbounds")[0].asJsonObject.getAsJsonObject("sniffing")
        assertTrue(tun.get("enabled").asBoolean)
        assertFalse(tun.get("routeOnly").asBoolean)
        val dest = tun.getAsJsonArray("destOverride").map { it.asString }
        assertTrue("tls" in dest && "http" in dest)
        assertEquals(1, dest.count { it == "http" })
    }

    @Test
    fun repackUsesTheFirstNameAndAcceptsTheSitesOwnCertificate() {
        val root = baseConfig()
        CdnSniConfig.inject(root, CdnSniSettings(listOf("domain:a.com"), listOf("front.example", "other.example"), listOf("1.2.3.4", "5.6.7.8")), "c", "k")
        val repack = root.getAsJsonArray("outbounds").map { it.asJsonObject }.first { it.get("tag").asString == CdnSniConfig.REPACK_TAG }
        val tls = repack.getAsJsonObject("streamSettings").getAsJsonObject("tlsSettings")
        assertEquals("front.example", tls.get("serverName").asString)
        assertEquals("fromMitM,front.example,other.example", tls.get("verifyPeerCertByName").asString)
        assertEquals("${CdnSniConfig.EDGE_HOST}:443", repack.getAsJsonObject("settings").get("redirect").asString)
        val hosts = root.getAsJsonObject("dns").getAsJsonObject("hosts").getAsJsonArray(CdnSniConfig.EDGE_HOST)
        assertEquals(listOf("1.2.3.4", "5.6.7.8"), hosts.map { it.asString })
    }

    @Test
    fun withoutEdgeIpsTheRepackOutboundResolvesTheSiteItself() {
        val root = baseConfig()
        CdnSniConfig.inject(root, CdnSniSettings(listOf("domain:a.com"), listOf("b.com"), emptyList()), "c", "k")
        val repack = root.getAsJsonArray("outbounds").map { it.asJsonObject }.first { it.get("tag").asString == CdnSniConfig.REPACK_TAG }
        assertFalse(repack.has("settings"))
        assertFalse(root.has("dns"))
    }
}
