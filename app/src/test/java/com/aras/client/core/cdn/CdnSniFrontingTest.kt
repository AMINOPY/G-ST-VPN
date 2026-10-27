package com.aras.client.core.cdn

import com.aras.client.dto.entities.ProfileItem
import com.aras.client.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CdnSniFrontingTest {

    private fun wsProfile() = ProfileItem.create(EConfigType.VLESS).apply {
        remarks = "my-ws"
        server = "origin.example.com"
        serverPort = "443"
        network = "ws"
        security = "tls"
        host = "origin.example.com"
        sni = "origin.example.com"
        insecure = true
    }

    @Test
    fun eligibleOnlyForTlsOverHostCarryingTransport() {
        assertTrue(CdnSniFronting.isEligible(wsProfile()))
        assertFalse(CdnSniFronting.isEligible(wsProfile().apply { network = "tcp" }))
        assertFalse(CdnSniFronting.isEligible(wsProfile().apply { security = "reality" }))
        assertFalse(CdnSniFronting.isEligible(wsProfile().apply { security = "" }))
        assertFalse(CdnSniFronting.isEligible(ProfileItem.create(EConfigType.SHADOWSOCKS).apply {
            network = "ws"; security = "tls"
        }))
    }

    @Test
    fun applyDialsEdgeWithFrontNameAndKeepsOriginHost() {
        val base = wsProfile()
        val out = CdnSniFronting.apply(base, CdnCandidate("104.16.1.2", "www.cloudflare.com"))
        assertEquals("104.16.1.2", out.server)
        assertEquals("www.cloudflare.com", out.sni)
        assertEquals("origin.example.com", out.host)
        assertEquals(false, out.insecure)
    }

    @Test
    fun applyLeavesOriginalUntouched() {
        val base = wsProfile()
        CdnSniFronting.apply(base, CdnCandidate("104.16.1.2", "www.cloudflare.com"))
        assertEquals("origin.example.com", base.server)
        assertEquals("origin.example.com", base.sni)
        assertEquals(true, base.insecure)
    }

    @Test
    fun originFallsBackToSniThenServerWhenHostIsBlank() {
        assertEquals("a.example", CdnSniFronting.originHost(wsProfile().apply { host = ""; sni = "a.example" }))
        assertEquals("s.example", CdnSniFronting.originHost(wsProfile().apply { host = null; sni = null; server = "s.example" }))
        val out = CdnSniFronting.apply(wsProfile().apply { host = ""; sni = "a.example" }, CdnCandidate("1.1.1.1", "front.example"))
        assertEquals("a.example", out.host)
    }

    @Test
    fun grpcKeepsOriginInAuthority() {
        val base = wsProfile().apply { network = "grpc"; host = null; authority = "g.example.com" }
        val out = CdnSniFronting.apply(base, CdnCandidate("1.1.1.1", "front.example"))
        assertEquals("g.example.com", out.authority)
        assertEquals("front.example", out.sni)
    }

    @Test(expected = IllegalArgumentException::class)
    fun applyRejectsIneligibleProfile() {
        CdnSniFronting.apply(wsProfile().apply { network = "tcp" }, CdnCandidate("1.1.1.1", "x.example"))
    }

    @Test
    fun parseIpsKeepsLiteralsAndSamplesRangesInsideThem() {
        val ips = CdnCandidatePool.parseIps("9.9.9.9\n104.16.0.0/13, bogus, 300.1.1.1", limit = 10, random = Random(1))
        assertEquals(10, ips.size)
        assertTrue("9.9.9.9" in ips)
        assertFalse("300.1.1.1" in ips)
        val low = CdnCandidatePool.ipv4ToLong("104.16.0.0")!!
        val high = CdnCandidatePool.ipv4ToLong("104.23.255.255")!!
        ips.filter { it != "9.9.9.9" }.forEach {
            val v = CdnCandidatePool.ipv4ToLong(it)!!
            assertTrue("$it outside range", v in low..high)
        }
        assertEquals(ips.size, ips.toSet().size)
    }

    @Test
    fun parseIpsRejectsTooWideOrMalformedPrefixes() {
        assertTrue(CdnCandidatePool.parseIps("10.0.0.0/4\n1.2.3.4/abc\n1.2.3.4/33").isEmpty())
    }

    @Test
    fun parseSnisNormalisesAndDropsInvalid() {
        assertEquals(
            listOf("www.cloudflare.com", "a.example"),
            CdnCandidatePool.parseSnis("WWW.Cloudflare.com\nwww.cloudflare.com, not a host,-bad.com ;a.example")
        )
    }

    @Test
    fun pairsRotateNamesAndRespectTheCap() {
        val pairs = CdnCandidatePool.pairs(listOf("1.1.1.1", "2.2.2.2"), listOf("a.x", "b.x"), max = 3)
        assertEquals(3, pairs.size)
        assertEquals(CdnCandidate("1.1.1.1", "a.x"), pairs[0])
        assertEquals(CdnCandidate("2.2.2.2", "b.x"), pairs[2])
        assertTrue(CdnCandidatePool.pairs(emptyList(), listOf("a.x")).isEmpty())
    }

    @Test
    fun statusLineDetection() {
        assertTrue(CdnSniScanner.isHttpStatusLine("HTTP/1.1 403 Forbidden"))
        assertTrue(CdnSniScanner.isHttpStatusLine("HTTP/2 200"))
        assertFalse(CdnSniScanner.isHttpStatusLine("garbage"))
        assertNotNull(CdnCandidatePool.DEFAULT_RANGES.firstOrNull())
    }
}
