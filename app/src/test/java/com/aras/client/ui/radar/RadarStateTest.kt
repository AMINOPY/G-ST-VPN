package com.aras.client.ui.radar

import org.junit.Assert.assertEquals
import org.junit.Test

class RadarStateTest {

    private fun snap(
        phase: TunnelPhase,
        tunUp: Boolean = true,
        traffic: Long = 0L,
        ip: IpCheck = IpCheck.NotRun,
    ) = TunnelSnapshot(
        sessionId = 1L,
        phase = phase,
        tunInterfaceUp = tunUp,
        trafficBytesLastSample = traffic,
        ipCheck = ip,
    )

    @Test
    fun stopped_is_disconnected() {
        assertEquals(RadarState.DISCONNECTED, resolveRadarState(snap(TunnelPhase.STOPPED)))
    }

    @Test
    fun starting_is_connecting_even_if_old_ip_check_was_verified() {
        val s = snap(TunnelPhase.STARTING, ip = IpCheck.Verified("1.2.3.4"), traffic = 10L)
        assertEquals(RadarState.CONNECTING, resolveRadarState(s))
    }

    @Test
    fun running_with_verified_ip_and_traffic_is_connected() {
        val s = snap(TunnelPhase.RUNNING, traffic = 2048L, ip = IpCheck.Verified("1.2.3.4"))
        assertEquals(RadarState.CONNECTED_VERIFIED, resolveRadarState(s))
    }

    @Test
    fun running_with_verified_ip_and_no_recent_traffic_is_still_connected() {
        // An idle but healthy VPN tunnel may legitimately have no recent traffic.
        val s = snap(TunnelPhase.RUNNING, traffic = 0L, ip = IpCheck.Verified("1.2.3.4"))
        assertEquals(RadarState.CONNECTED_VERIFIED, resolveRadarState(s))
    }

    @Test
    fun running_with_failed_ip_check_is_unverified_not_connected() {
        val s = snap(TunnelPhase.RUNNING, traffic = 2048L, ip = IpCheck.Failed)
        assertEquals(RadarState.TUNNEL_IP_UNVERIFIED, resolveRadarState(s))
    }

    @Test
    fun running_with_pending_ip_check_is_unverified() {
        val s = snap(TunnelPhase.RUNNING, traffic = 2048L, ip = IpCheck.Pending)
        assertEquals(RadarState.TUNNEL_IP_UNVERIFIED, resolveRadarState(s))
    }

    @Test
    fun running_with_ip_check_not_run_is_unverified() {
        val s = snap(TunnelPhase.RUNNING, ip = IpCheck.NotRun)
        assertEquals(RadarState.TUNNEL_IP_UNVERIFIED, resolveRadarState(s))
    }

    @Test
    fun running_but_tun_interface_gone_is_error() {
        val s = snap(TunnelPhase.RUNNING, tunUp = false, traffic = 2048L, ip = IpCheck.Verified("1.2.3.4"))
        assertEquals(RadarState.ERROR, resolveRadarState(s))
    }

    @Test
    fun service_error_is_error_even_if_ip_was_verified() {
        val s = snap(TunnelPhase.ERROR, traffic = 2048L, ip = IpCheck.Verified("1.2.3.4"))
        assertEquals(RadarState.ERROR, resolveRadarState(s))
    }

    @Test
    fun only_tunnel_states_are_active_radar() {
        assertEquals(false, RadarState.DISCONNECTED.isActive)
        assertEquals(false, RadarState.CONNECTING.isActive)
        assertEquals(true, RadarState.CONNECTED_VERIFIED.isActive)
        assertEquals(true, RadarState.TUNNEL_IP_UNVERIFIED.isActive)
        assertEquals(false, RadarState.ERROR.isActive)
    }
}