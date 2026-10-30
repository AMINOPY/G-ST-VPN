package com.aras.client.core

import android.content.Context
import com.aras.client.AppConfig
import com.aras.client.core.aether.AetherCore
import com.aras.client.core.aether.AetherCoreManager
import com.aras.client.core.mieru.MieruProcessManager
import com.aras.client.dto.entities.ProfileItem
import com.aras.client.enums.EConfigType
import com.aras.client.enums.AetherPsiphon
import com.aras.client.enums.AetherPsiphonMode
import com.aras.client.enums.AetherTor
import com.aras.client.fmt.AetherFmt
import com.aras.client.util.LogUtil
import com.aras.client.core.cdn.CdnSniConfig

/**
 * Starts the helper processes a config's *resolved chain* depends on.
 *
 * Aether and Mieru are not protocols the core speaks. Each lowers to a SOCKS
 * outbound on loopback and an external process serves that port, so a profile
 * that hops through one of them connects to a dead port unless the daemon is
 * already listening: the latency test returns -1 and the profile never comes up.
 *
 * Both managers hold a single global slot, so they were only ever started when
 * the *selected* profile was itself an Aether or Mieru profile. An entry proxy
 * (the subscription's `prevProfile`) is the outermost hop of every profile in
 * that subscription, and its own type is whatever the provider named — so its
 * daemon was never started and the whole subscription failed at once.
 */
object HelperProcessSupport {

    private val lock = Any()
    private var aetherKey: String? = null
    private var aetherRefs = 0
    private var mieruKey: String? = null
    private var mieruRefs = 0

    /**
     * The hops of [chain] that an external process has to serve, in chain order.
     * Empty when the core speaks the whole chain itself.
     */
    fun helperProfilesOf(chain: List<ProfileItem>): List<ProfileItem> =
        chain.filter { it.needsHelperProcess }

    /**
     * Add the Psiphon-only carrier required by a standalone CDN/SNI Xray profile.
     * Aether exposes the Psiphon session as loopback SOCKS; Xray remains the only
     * process that owns the device VPN/TUN.
     */
    fun profilesForXraySession(chain: List<ProfileItem>): List<ProfileItem> {
        val cdnProfile = chain.firstOrNull { it.configType == EConfigType.CDN_SNI } ?: return chain
        val settings = CdnSniConfig.settingsOf(cdnProfile) ?: return chain
        val carrier = ProfileItem.create(EConfigType.AETHER).apply {
            remarks = "CDN/SNI Psiphon carrier"
            aetherListenPort = AppConfig.PORT_AETHER_SOCKS.toString()
            aetherPsiphon = AetherPsiphon.ONLY.type
            aetherPsiphonMode = AetherPsiphonMode.CDN.type
            aetherPsiphonCdnIps = settings.edgeIp
            aetherPsiphonCdnSni = settings.sni
            // AUTO US selects an international edge list and asks Psiphon for a US exit.
            // AUTO IR and Manual keep Psiphon's automatic exit selection.
            aetherPsiphonRegion = cdnProfile.cdnSniMode
                ?.takeIf { it.equals("auto_us", ignoreCase = true) }
                ?.let { "US" }
            aetherPsiphonBundledList = true
        }
        return chain + carrier
    }

    /**
     * Brings up whatever [chain] needs.
     *
     * Returns the profiles this call actually started — pass exactly that back to
     * [release]. A daemon already running for the same profile is reused rather
     * than restarted, and a profile whose daemon is taken by a *different*
     * profile is refused rather than measured through the wrong tunnel.
     *
     * An error message is returned instead of a throw, because every caller
     * reports this to the user in its own words.
     */
    fun acquire(context: Context, chain: List<ProfileItem>): Acquired {
        val helpers = helperProfilesOf(chain)
        if (helpers.isEmpty()) return Acquired(emptyList(), null)

        // Serialized: both managers hold one global slot, so two chains claiming
        // them concurrently would silently take each other's daemon.
        val acquired = mutableListOf<ProfileItem>()
        synchronized(lock) {
            for (helper in helpers) {
                when (helper.configType) {
                    EConfigType.AETHER -> {
                        val key = helper.helperKey()
                        when {
                            aetherRefs == 0 -> {
                                val error = startAether(context, helper)
                                if (error != null) {
                                    release(Acquired(acquired.toList(), null))
                                    return Acquired(emptyList(), error)
                                }
                                aetherKey = key
                            }

                            aetherKey != key -> {
                                release(Acquired(acquired.toList(), null))
                                return Acquired(
                                    emptyList(),
                                    "The aether core is already running for a different profile " +
                                        "(${helper.remarks}); it can only serve one at a time"
                                )
                            }
                        }
                        aetherRefs++
                        acquired += helper
                    }

                    EConfigType.MIERU -> {
                        val key = helper.helperKey()
                        when {
                            mieruRefs == 0 -> {
                                val error = startMieru(context, helper)
                                if (error != null) {
                                    release(Acquired(acquired.toList(), null))
                                    return Acquired(emptyList(), error)
                                }
                                mieruKey = key
                            }

                            mieruKey != key -> {
                                release(Acquired(acquired.toList(), null))
                                return Acquired(
                                    emptyList(),
                                    "The mieru client is already running for a different profile " +
                                        "(${helper.remarks}); it can only serve one at a time"
                                )
                            }
                        }
                        mieruRefs++
                        acquired += helper
                    }

                    else -> Unit
                }
            }
        }
        return Acquired(acquired, null)
    }

    /** Stops the daemons [acquire] started, once nothing is using them. */
    fun release(acquired: Acquired) {
        synchronized(lock) {
            acquired.profiles.forEach { helper ->
                when (helper.configType) {
                    EConfigType.AETHER -> {
                        aetherRefs = (aetherRefs - 1).coerceAtLeast(0)
                        if (aetherRefs == 0) {
                            aetherKey = null
                            AetherCoreManager.stop()
                        }
                    }

                    EConfigType.MIERU -> {
                        mieruRefs = (mieruRefs - 1).coerceAtLeast(0)
                        if (mieruRefs == 0) {
                            mieruKey = null
                            MieruProcessManager.stop()
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    /**
     * Drops every claim and stops both daemons. Used when the session ends, so the
     * refcounts cannot outlive the processes they describe.
     */
    fun reset() {
        synchronized(lock) {
            aetherRefs = 0
            aetherKey = null
            mieruRefs = 0
            mieruKey = null
            AetherCoreManager.stop()
            MieruProcessManager.stop()
        }
    }

    private fun startAether(context: Context, profile: ProfileItem): String? {
        if (!AetherCoreManager.isAvailable(context)) {
            return "Aether core is unavailable or incompatible with this ABI: " +
                AetherCoreManager.nativeBinaryReport(context)
        }
        if (AetherPsiphon.fromString(profile.aetherPsiphon) != AetherPsiphon.OFF &&
            !AetherCoreManager.psiphonAvailable(context)
        ) {
            return "Aether Psiphon helper is missing or incompatible: " +
                AetherCoreManager.nativeBinaryReport(context)
        }
        if (AetherTor.fromString(profile.aetherTor) != AetherTor.OFF &&
            !AetherCoreManager.torTransportAvailable(context)
        ) {
            return "Aether Tor transport helper is missing or incompatible: " +
                AetherCoreManager.nativeBinaryReport(context)
        }
        val core = AetherCore.of(profile)
        LogUtil.i(AppConfig.TAG, "HelperProcess: aether core: ${core.command}")
        val started = AetherCoreManager.start(context, core) { line ->
            LogUtil.d(AppConfig.TAG, "aether | $line")
        }
        if (!started) {
            AetherCoreManager.stop()
            return "The aether core did not start; see the log for its own error"
        }
        return null
    }

    private fun startMieru(context: Context, profile: ProfileItem): String? {
        if (!MieruProcessManager.isAvailable(context)) return "This build does not carry the mieru client"
        val port = AetherFmt.listenPortOf(profile.mieruListenPort)
            ?: AppConfig.PORT_MIERU_SOCKS.toInt()
        LogUtil.i(AppConfig.TAG, "HelperProcess: starting the mieru client on $port")
        if (!MieruProcessManager.start(context, profile, port)) {
            MieruProcessManager.stop()
            return "The mieru client did not start; see the log for its own error"
        }
        return null
    }

    private val ProfileItem.needsHelperProcess: Boolean
        get() = configType == EConfigType.AETHER || configType == EConfigType.MIERU

    /** What identifies a running daemon: the profile plus the port it was told to bind. */
    private fun ProfileItem.helperKey(): String {
        val port = if (configType == EConfigType.AETHER) {
            com.aras.client.core.aether.AetherCoreManager.listenPort(this)
        } else {
            AetherFmt.listenPortOf(mieruListenPort) ?: AppConfig.PORT_MIERU_SOCKS.toInt()
        }
        val carrierIdentity = if (configType == EConfigType.AETHER &&
            AetherPsiphon.fromString(aetherPsiphon) == AetherPsiphon.ONLY
        ) {
            "|${aetherPsiphonMode.orEmpty()}|${aetherPsiphonCdnIps.orEmpty()}|${aetherPsiphonCdnSni.orEmpty()}|${aetherPsiphonRegion.orEmpty()}|bundled=${aetherPsiphonBundledList}"
        } else {
            ""
        }
        return "${remarks}|$port$carrierIdentity"
    }

    /** What [acquire] started, and any reason it could not. */
    class Acquired(
        val profiles: List<ProfileItem>,
        val error: String?,
    ) {
        val ok: Boolean get() = error == null
    }
}