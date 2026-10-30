package com.aras.client.core

import com.aras.client.dto.entities.ProfileItem
import com.aras.client.core.aether.AetherCore
import com.aras.client.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which hops of a chain need an external process before the core can dial them.
 *
 * Aether and Mieru are not protocols the core speaks: each lowers to a SOCKS
 * outbound on loopback and a separate process serves that port. Nothing
 * connects unless that process is up, and when it is not the latency test
 * returns -1 — for every profile in a subscription at once, because an entry
 * proxy is the outermost hop shared by all of them.
 *
 * These pin the selection rule. The start path and the latency-test path both
 * route through it, so a chain that grows a new daemon-backed protocol has to be
 * added here or it silently dials a dead port.
 */
class HelperProcessSupportTest {

    private fun profile(type: EConfigType, remarks: String) =
        ProfileItem(configType = type, remarks = remarks, server = "1.2.3.4", serverPort = "443")

    @Test
    fun `a chain of core-native protocols needs no helper process`() {
        val chain = listOf(
            profile(EConfigType.VLESS, "exit"),
            profile(EConfigType.TROJAN, "middle"),
            profile(EConfigType.VMESS, "entry"),
        )

        assertTrue(HelperProcessSupport.helperProfilesOf(chain).isEmpty())
    }

    @Test
    fun `an aether entry proxy is found in the chain`() {
        val chain = listOf(
            profile(EConfigType.VLESS, "exit"),
            profile(EConfigType.AETHER, "entry"),
        )

        val helpers = HelperProcessSupport.helperProfilesOf(chain)

        assertEquals(listOf("entry"), helpers.map { it.remarks })
    }

    @Test
    fun `a mieru exit proxy is found in the chain`() {
        val chain = listOf(
            profile(EConfigType.MIERU, "exit"),
        )

        assertEquals(listOf("exit"), HelperProcessSupport.helperProfilesOf(chain).map { it.remarks })
    }

    @Test
    fun `every daemon-backed hop is collected and chain order is kept`() {
        // Built innermost first, entry proxy last — the order CoreConfigManager
        // wires dialerProxy in, and the order it must be started in.
        val chain = listOf(
            profile(EConfigType.VLESS, "exit"),
            profile(EConfigType.AETHER, "hop-2"),
            profile(EConfigType.VMESS, "hop-1"),
            profile(EConfigType.MIERU, "entry"),
        )

        val helpers = HelperProcessSupport.helperProfilesOf(chain)

        assertEquals(listOf("hop-2", "entry"), helpers.map { it.remarks })
    }

    @Test
    fun `an unchained aether profile is still its own helper hop`() {
        // A standalone Aether profile resolves to a one-element chain. Dropping
        // it here is what stopped the core from ever being started at all.
        val chain = listOf(profile(EConfigType.AETHER, "warp"))

        assertEquals(listOf("warp"), HelperProcessSupport.helperProfilesOf(chain).map { it.remarks })
    }
    @Test
    fun `standalone CDN SNI adds a Psiphon only carrier for Xray`() {
        val cdn = ProfileItem.create(EConfigType.CDN_SNI).apply {
            remarks = "CDN/SNI"
            cdnSniTargets = "104.16.1.2"
            cdnSniFront = "front.example"
        }

        val sessionProfiles = HelperProcessSupport.profilesForXraySession(listOf(cdn))
        val carrier = sessionProfiles.last()

        assertEquals(EConfigType.CDN_SNI, sessionProfiles.first().configType)
        assertEquals(EConfigType.AETHER, carrier.configType)
        assertEquals("only", carrier.aetherPsiphon)
        assertEquals("cdn", carrier.aetherPsiphonMode)
        assertEquals("104.16.1.2", carrier.aetherPsiphonCdnIps)
        assertEquals("front.example", carrier.aetherPsiphonCdnSni)
        assertEquals(listOf("CDN/SNI Psiphon carrier"), HelperProcessSupport.helperProfilesOf(sessionProfiles).map { it.remarks })
        val arguments = AetherCore.of(carrier).arguments
        assertTrue(arguments.contains("--psiphon-only"))
        assertTrue(arguments.contains("--psiphon-mode"))
        assertTrue(arguments.contains("cdn"))
        assertTrue(arguments.contains("--psiphon-cdn-ips"))
        assertTrue(arguments.contains("104.16.1.2"))
        assertTrue(arguments.contains("--psiphon-cdn-sni"))
        assertTrue(arguments.contains("front.example"))
    }

    @Test
    fun `AUTO US selects a US Psiphon exit while AUTO IR leaves exit automatic`() {
        val autoUs = ProfileItem.create(EConfigType.CDN_SNI).apply {
            cdnSniMode = "auto_us"
            cdnSniTargets = "104.16.1.2"
            cdnSniFront = "front.example"
        }
        val autoIr = ProfileItem.create(EConfigType.CDN_SNI).apply {
            cdnSniMode = "auto_ir"
            cdnSniTargets = "104.16.1.2"
            cdnSniFront = "front.example"
        }

        assertEquals("US", HelperProcessSupport.profilesForXraySession(listOf(autoUs)).last().aetherPsiphonRegion)
        assertEquals(null, HelperProcessSupport.profilesForXraySession(listOf(autoIr)).last().aetherPsiphonRegion)
    }

    @Test
    fun `multiple manual CDN candidates reach the Psiphon carrier`() {
        val cdn = ProfileItem.create(EConfigType.CDN_SNI).apply {
            remarks = "CDN/SNI"
            cdnSniTargets = "104.16.1.2\n104.16.1.3"
            cdnSniFront = "WWW.Google.com،www.cloudflare.com"
        }

        val carrier = HelperProcessSupport.profilesForXraySession(listOf(cdn)).last()
        assertEquals("104.16.1.2,104.16.1.3", carrier.aetherPsiphonCdnIps)
        assertEquals("www.google.com,www.cloudflare.com", carrier.aetherPsiphonCdnSni)
        val arguments = AetherCore.of(carrier).arguments
        assertTrue(arguments.contains("104.16.1.2,104.16.1.3"))
        assertTrue(arguments.contains("www.google.com,www.cloudflare.com"))
    }

    @Test
    fun `invalid CDN SNI does not start a carrier`() {
        val cdn = ProfileItem.create(EConfigType.CDN_SNI).apply {
            cdnSniTargets = "not-an-ip"
            cdnSniFront = "front.example"
        }

        assertEquals(listOf(cdn), HelperProcessSupport.profilesForXraySession(listOf(cdn)))
    }

}
