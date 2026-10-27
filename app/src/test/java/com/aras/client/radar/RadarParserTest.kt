package com.aras.client.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarParserTest {

    private fun b64(s: String) = java.util.Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun extractsPlainAndSkipsSsInsideVless() {
        val text = "vless://uuid@a.example.com:443?security=reality&sni=x.com#n\nss://YWVzOnB3@1.2.3.4:8388#s"
        val uris = RadarParser.extractUris(text)
        assertEquals(2, uris.size)
        assertTrue(uris[0].startsWith("vless://"))
        assertTrue(uris[1].startsWith("ss://"))
    }

    @Test
    fun extractsFromBase64Subscription() {
        val inner = "trojan://pw@b.example.com:443?sni=b.example.com#t\nvless://id@c.example.com:8443#v"
        val uris = RadarParser.extractUris(b64(inner))
        assertEquals(2, uris.size)
    }

    @Test
    fun decodeBase64HandlesUrlSafeAndNoPadding() {
        val raw = "hello??>>world"
        val enc = b64(raw).replace('+', '-').replace('/', '_').trimEnd('=')
        assertEquals(raw, RadarParser.decodeBase64(enc)!!.toString(Charsets.UTF_8))
    }

    @Test
    fun describesVlessReality() {
        val ep = RadarParser.describe("vless://id@h.example.com:443?security=reality&type=tcp&sni=www.microsoft.com#x")!!
        assertEquals("vless", ep.protocol)
        assertEquals("h.example.com", ep.host)
        assertEquals(443, ep.port)
        assertEquals("reality", ep.security)
        assertEquals("www.microsoft.com", ep.sni)
        assertFalse(ep.udp)
    }

    @Test
    fun describesVmessJson() {
        val json = "{\"add\":\"v.example.com\",\"port\":\"8443\",\"id\":\"abc\",\"tls\":\"tls\",\"net\":\"ws\",\"host\":\"cdn.example.com\"}"
        val ep = RadarParser.describe("vmess://" + b64(json))!!
        assertEquals("v.example.com", ep.host)
        assertEquals(8443, ep.port)
        assertEquals("tls", ep.security)
        assertEquals("ws", ep.transport)
        assertEquals("cdn.example.com", ep.sni)
    }

    @Test
    fun describesShadowsocksVariants() {
        val sip002 = RadarParser.describe("ss://" + b64("aes-256-gcm:pw") + "@1.2.3.4:8388#n")!!
        assertEquals("1.2.3.4", sip002.host)
        assertEquals(8388, sip002.port)
        val legacy = RadarParser.describe("ss://" + b64("aes-256-gcm:pw@5.6.7.8:443") + "#n")!!
        assertEquals("5.6.7.8", legacy.host)
        assertEquals(443, legacy.port)
    }

    @Test
    fun hysteria2IsUdpAndHandlesMultiPort() {
        val ep = RadarParser.describe("hy2://pw@h.example.com:443,5000-6000?sni=h.example.com#x")!!
        assertEquals("hysteria2", ep.protocol)
        assertEquals(443, ep.port)
        assertTrue(ep.udp)
    }

    @Test
    fun ipv6AndInvalidInputs() {
        val ep = RadarParser.describe("trojan://pw@[2001:db8::1]:443#x")!!
        assertEquals("2001:db8::1", ep.host)
        assertNull(RadarParser.describe("vless://id@:443#x"))
        assertNull(RadarParser.describe("vless://id@host:99999#x"))
    }

    @Test
    fun duplicateKeyIgnoresRemarkAndHostCase() {
        val a = RadarParser.describe("vless://id@Host.Example.com:443?security=tls#one")!!
        val b = RadarParser.describe("vless://id@host.example.com:443?security=tls#two")!!
        assertEquals(a.key, b.key)
    }

    @Test
    fun localHostsAreRejected() {
        assertTrue(RadarParser.isLocalHost("127.0.0.1"))
        assertTrue(RadarParser.isLocalHost("192.168.1.5"))
        assertTrue(RadarParser.isLocalHost("172.20.0.1"))
        assertFalse(RadarParser.isLocalHost("172.40.0.1"))
        assertFalse(RadarParser.isLocalHost("8.8.8.8"))
    }

    @Test
    fun scoreRewardsRealityAndPenalisesFailedTls() {
        val reality = RadarParser.describe("vless://id@h.com:443?security=reality&sni=x.com")!!
        val plain = RadarParser.describe("vless://id@h.com:80")!!
        val good = RadarParser.score(reality, 60, true, RadarGroup.TOP)
        val bad = RadarParser.score(reality, 60, false, RadarGroup.TOP)
        assertTrue(good > bad)
        assertTrue(good > RadarParser.score(plain, 60, null, RadarGroup.COUNTRY))
        assertEquals(0, RadarParser.score(plain, null, null, RadarGroup.COUNTRY))
        assertNotNull(good)
    }
}
