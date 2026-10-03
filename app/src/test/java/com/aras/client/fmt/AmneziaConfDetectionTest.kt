package com.aras.client.fmt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling an AmneziaWG `.conf` from a plain WireGuard one.
 *
 * Getting this wrong is silent and total: the profile keeps its keys but none of
 * the obfuscation parameters are applied, so it dials as stock WireGuard, the
 * server drops every packet, and the editor shows no AmneziaWG fields — nothing
 * says the config was misread. The previous test was
 * `server.contains("Jc", ignoreCase = false)`, so any file not spelling the key
 * exactly "Jc" was imported as plain WireGuard.
 */
class AmneziaConfDetectionTest {

    private fun conf(interfaceLines: String) = """
        [Interface]
        PrivateKey = 8GYMxCC+QxurPSDGDrTnm2HmDROdYL5ZjYD3mBrnpEQ=
        Address = 172.16.0.2/32
        $interfaceLines

        [Peer]
        PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
        Endpoint = 188.114.99.151:2408
        AllowedIPs = 0.0.0.0/0, ::/0
    """.trimIndent()

    @Test
    fun `the canonical AmneziaVPN export is detected`() {
        assertTrue(AmneziawgFmt.looksLikeAmneziaConf(conf("Jc = 8\nJmin = 49\nJmax = 76")))
    }

    @Test
    fun `a lowercase key is still AmneziaWG`() {
        // This is the case that used to import as plain WireGuard.
        assertTrue(AmneziawgFmt.looksLikeAmneziaConf(conf("jc = 8\njmin = 49")))
    }

    @Test
    fun `an uppercase key is still AmneziaWG`() {
        assertTrue(AmneziawgFmt.looksLikeAmneziaConf(conf("JC = 8\nJMIN = 49")))
    }

    @Test
    fun `keys with no space around the equals are detected`() {
        assertTrue(AmneziawgFmt.looksLikeAmneziaConf(conf("Jc=8\nJmin=49")))
    }

    @Test
    fun `s3 and s4 alone are enough`() {
        assertTrue(AmneziawgFmt.looksLikeAmneziaConf(conf("S3 = 4\nS4 = 8")))
    }

    @Test
    fun `mimic packets alone are enough`() {
        assertTrue(AmneziawgFmt.looksLikeAmneziaConf(conf("I1 = <b 0x494e>\nI2 = <b 0x5350>")))
    }

    @Test
    fun `handshake junk headers alone are enough`() {
        assertTrue(AmneziawgFmt.looksLikeAmneziaConf(conf("H3 = 3\nH4 = 4")))
    }

    @Test
    fun `a plain WireGuard conf is not AmneziaWG`() {
        assertFalse(AmneziawgFmt.looksLikeAmneziaConf(conf("")))
    }

    @Test
    fun `keys that merely mention a WireGuard word are not AmneziaWG`() {
        // Guards against matching a substring: none of these are AWG parameters.
        assertFalse(AmneziawgFmt.looksLikeAmneziaConf(conf("DNS = 1.1.1.1\nMTU = 1420")))
        assertFalse(
            AmneziawgFmt.looksLikeAmneziaConf(
                conf("JcSomething = 1\nSomeJunkHeader = 2")
            )
        )
    }

    @Test
    fun `a key that appears only in a comment does not count`() {
        // Counting comments would turn a plain WireGuard config that merely
        // documents "Jc" into AmneziaWG, and then the import would apply
        // obfuscation the server was never told about. Real parameters have to
        // actually be set.
        assertFalse(AmneziawgFmt.looksLikeAmneziaConf(conf("# Jc = 8 (obfuscation)")))
    }
}
