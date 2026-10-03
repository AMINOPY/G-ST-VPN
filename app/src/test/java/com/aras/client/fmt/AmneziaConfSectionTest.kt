package com.aras.client.fmt

import com.aras.client.core.AwgConfigBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A real AmneziaVPN `.conf` export, shaped exactly the way the app writes one.
 *
 * The obfuscation parameters live under **[Interface]**, because they are
 * device-level: the UAPI parser is in peer context after the first
 * `public_key=` line and rejects them there. The parser used to read only
 * `[Peer]`, so it found nothing, `applyDefaultJunkParams` substituted
 * AmneziaWG's stock values, and the server dropped every packet — the tunnel
 * established, the UI reported success, and nothing passed. Nothing surfaced
 * it because the defaults are valid-looking numbers, and because the project
 * had never been run on a device.
 */
class AmneziaConfSectionTest {

    /** The layout AmneziaVPN produces: junk params under [Interface], peer keys under [Peer]. */
    private val exportedConf = """
        [Interface]
        PrivateKey = 8GYMxCC+QxurPSDGDrTnm2HmDROdYL5ZjYD3mBrnpEQ=
        Address = 172.16.0.2/32, 2606:4700:110:8c08:e42e:f0a6:3aa6:bbce/128
        DNS = 1.1.1.1, 1.0.0.1
        MTU = 1280
        Jc = 8
        Jmin = 49
        Jmax = 76
        S1 = 0
        S2 = 0
        S3 = 0
        S4 = 0
        H1 = 1
        H2 = 2
        H3 = 3
        H4 = 4

        [Peer]
        PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = 188.114.99.151:2408
        PersistentKeepalive = 25
    """.trimIndent()

    private fun parsed() = AmneziawgFmt.parseAmneziaConfFile(exportedConf)

    @Test
    fun `obfuscation params are read from the interface section`() {
        val config = parsed()

        assertEquals("8", config.junkPacketCount)
        assertEquals("49", config.junkPacketMinSize)
        assertEquals("76", config.junkPacketMaxSize)
        assertEquals("0", config.initPacketJunkSize)
        assertEquals("0", config.responsePacketJunkSize)
        assertEquals("1", config.initPacketJunkHeader)
        assertEquals("2", config.responsePacketJunkHeader)
        assertEquals("3", config.cookiePacketJunkHeader)
        assertEquals("4", config.transportPacketJunkHeader)
    }

    @Test
    fun `a zero junk size survives instead of falling back to a default`() {
        // S1 = 0 and S2 = 0 are meaningful: they disable the handshake padding.
        // Treating them as "unset" is what silently re-enabled padding and broke
        // the handshake against a server configured with them off.
        val config = parsed()

        assertEquals("0", config.initPacketJunkSize)
        assertEquals("0", config.responsePacketJunkSize)
    }

    @Test
    fun `the exported values are not replaced by amneziawg defaults`() {
        val uapi = AwgConfigBuilder.buildUapi(parsed())

        assertTrue(uapi, uapi.contains("jc=8\n"))
        assertTrue(uapi, uapi.contains("jmin=49\n"))
        assertTrue(uapi, uapi.contains("jmax=76\n"))
        assertTrue(uapi, uapi.contains("s1=0\n"))
        assertTrue(uapi, uapi.contains("s2=0\n"))
        // The defaults that used to be substituted in.
        assertTrue(uapi, !uapi.contains("jc=4\n"))
        assertTrue(uapi, !uapi.contains("s1=15\n"))
    }

    @Test
    fun `obfuscation params under the peer section are still accepted`() {
        val conf = """
            [Interface]
            PrivateKey = 8GYMxCC+QxurPSDGDrTnm2HmDROdYL5ZjYD3mBrnpEQ=
            Address = 172.16.0.2/32

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            Jc = 3
            Jmin = 10
            Endpoint = 188.114.99.151:2408
        """.trimIndent()

        val config = AmneziawgFmt.parseAmneziaConfFile(conf)

        assertEquals("3", config.junkPacketCount)
        assertEquals("10", config.junkPacketMinSize)
    }

    @Test
    fun `the uapi carries no key this build of amneziawg rejects`() {
        // amneziawg-go's IpcSet answers "invalid UAPI device key: <x>" and fails the
        // whole tunnel for anything outside this set. It deliberately has no `address`
        // key: the device is handed a VpnService TUN the kernel already configured.
        // Sending one produced exactly that error and broke every connect.
        val accepted = setOf(
            "private_key", "listen_port", "fwmark", "replace_peers", "update_only",
            "jc", "jmin", "jmax", "s1", "s2", "s3", "s4",
            "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5",
            "header_protection_key", "random_trailers",
            "content_padding_addition", "disable_cookies",
            "rekey_after_time", "rekey_timeout", "reject_after_time",
            "keepalive_timeout", "max_handshake_attempts",
            "public_key", "preshared_key", "endpoint",
            "persistent_keepalive_interval", "allowed_ip", "remove",
            "replace_allowed_ips",
        )

        val keys = AwgConfigBuilder.buildUapi(parsed())
            .lineSequence()
            .filter { it.contains('=') }
            .map { it.substringBefore('=').trim() }
            .distinct()
            .toList()

        val rejected = keys.filterNot { it in accepted }
        assertTrue("keys this core rejects: $rejected", rejected.isEmpty())
    }

    @Test
    fun `no address line is written`() {
        // The address belongs to the kernel-side VpnService interface, which is
        // established before the core starts. amneziawg-go has no `address` key.
        val uapi = AwgConfigBuilder.buildUapi(parsed())

        assertTrue(uapi, !uapi.contains("address="))
    }

    @Test
    fun `persistent keepalive is parsed and sent`() {
        assertEquals("25", parsed().keepAlive)

        val uapi = AwgConfigBuilder.buildUapi(parsed())

        assertTrue(uapi, uapi.contains("persistent_keepalive_interval=25\n"))
    }

    @Test
    fun `the endpoint and keys are parsed from the peer section`() {
        val config = parsed()

        assertEquals("188.114.99.151", config.server)
        assertEquals("2408", config.serverPort)
        assertEquals("8GYMxCC+QxurPSDGDrTnm2HmDROdYL5ZjYD3mBrnpEQ=", config.secretKey)
        assertEquals("bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=", config.publicKey)
        assertEquals("1280", config.mtu?.toString())
    }

    @Test
    fun `a profile with no keepalive writes none`() {
        val config = parsed().apply { keepAlive = null }

        assertTrue(!AwgConfigBuilder.buildUapi(config).contains("persistent_keepalive_interval"))
    }
}