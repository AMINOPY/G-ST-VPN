package com.aras.client.service

import com.aras.client.enums.EConfigType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which component is allowed to read the TUN.
 *
 * There is exactly one TUN descriptor and two things that want it: hev-socks5-
 * tunnel (`TProxyStartService(config, vpnInterface.fd)`) and the standalone
 * AmneziaWG tunnel (`pfd.detachFd()` in CoreServiceManager). Handing it to both
 * gives the TUN two readers — tun2socks drains every packet into the local SOCKS
 * proxy, which in AmneziaWG mode no core is serving, so the tunnel is up and
 * carries nothing. That is why AmneziaWG appeared to start and then do nothing.
 */
class TunOwnershipTest {

    @Test
    fun `amneziawg does not start tun2socks`() {
        assertFalse(shouldRunTun2Socks(EConfigType.AMNEZIAWG))
    }

    @Test
    fun `plain wireguard still uses tun2socks`() {
        // WIREGUARD goes through the Xray wireguard outbound, unlike AMNEZIAWG.
        assertTrue(shouldRunTun2Socks(EConfigType.WIREGUARD))
    }

    @Test
    fun `every core-dialed protocol still uses tun2socks`() {
        val coreDialed = listOf(
            EConfigType.VMESS, EConfigType.VLESS, EConfigType.TROJAN,
            EConfigType.SHADOWSOCKS, EConfigType.SOCKS, EConfigType.HTTP,
            EConfigType.HYSTERIA, EConfigType.HYSTERIA2,
            EConfigType.ANYTLS, EConfigType.MASQUE,
            EConfigType.AETHER, EConfigType.MIERU,
            EConfigType.CUSTOM,
        )

        coreDialed.forEach { assertTrue("tun2socks must run for $it", shouldRunTun2Socks(it)) }
    }

    @Test
    fun `an unresolved profile falls back to tun2socks`() {
        // A null type means we could not decode the selection; the safe default is
        // the path every other profile takes.
        assertTrue(shouldRunTun2Socks(null))
    }
}