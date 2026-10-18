package com.aras.client.ui.radar

import org.junit.Assert.assertEquals
import org.junit.Test

class RadarStateTest {
    @Test
    fun stoppedTunnelShowsNoSignal() {
        assertEquals(
            RadarState.DISCONNECTED,
            resolveRadarState(TunnelSnapshot(1L, TunnelPhase.STOPPED, false, 0L, IpCheck.NotRun))
        )
    }

    @Test
    fun startingTunnelShowsEstablishingLink() {
        assertEquals(
            RadarState.CONNECTING,
            resolveRadarState(TunnelSnapshot(2L, TunnelPhase.STARTING, false, 0L, IpCheck.Pending))
        )
    }

    @Test
    fun runningTunnelWithoutInterfaceIsAnError() {
        assertEquals(
            RadarState.ERROR,
            resolveRadarState(TunnelSnapshot(3L, TunnelPhase.RUNNING, false, 0L, IpCheck.Failed))
        )
    }

    @Test
    fun runningTunnelWithVerifiedEgressIpShowsActiveSignal() {
        assertEquals(
            RadarState.CONNECTED_VERIFIED,
            resolveRadarState(TunnelSnapshot(4L, TunnelPhase.RUNNING, true, 0L, IpCheck.Verified("203.0.113.7")))
        )
    }

    @Test
    fun runningTunnelWithoutVerifiedIpDoesNotClaimVerified() {
        assertEquals(
            RadarState.TUNNEL_IP_UNVERIFIED,
            resolveRadarState(TunnelSnapshot(5L, TunnelPhase.RUNNING, true, 0L, IpCheck.Failed))
        )
    }

    @Test
    fun serviceErrorShowsNoSignal() {
        assertEquals(
            RadarState.ERROR,
            resolveRadarState(TunnelSnapshot(6L, TunnelPhase.ERROR, false, 0L, IpCheck.Failed))
        )
    }
}
