package com.aras.client.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.aras.client.AppConfig
import com.aras.client.dto.entities.ProfileItem
import com.aras.client.enums.AetherIpVersion
import com.aras.client.enums.AetherProtocol
import com.aras.client.enums.AetherPsiphon
import com.aras.client.enums.AetherPsiphonMode
import com.aras.client.enums.AetherScanMode
import com.aras.client.enums.EConfigType
import com.aras.client.handler.AngConfigManager
import com.aras.client.handler.MmkvManager
import com.aras.client.handler.SettingsManager
import java.util.UUID

/**
 * Owns the short-lived profiles used by Hybrid mode.
 *
 * The current core supports Aether/Psiphon only as the final (entry-proxy) hop in a
 * chain. The resulting route is V2Ray/Xray -> local Psiphon SOCKS -> internet.
 * Reverse order is deliberately not advertised because the local Aether SOCKS listener
 * cannot itself be dialed through another outbound.
 */
object HybridConnectManager {
    data class Session(
        val chainGuid: String,
        val temporaryGuids: List<String>,
        val previousGuid: String?,
    )

    @Volatile
    private var activeSession: Session? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun prepare(context: Context, rawLink: String): String? {
        if (activeSession != null) {
            return context.getString(com.aras.client.R.string.hybrid_already_prepared)
        }

        val parsed = AngConfigManager.parseAnyLink(rawLink.trim())
            ?: return context.getString(com.aras.client.R.string.hybrid_invalid_config)
        if (parsed.configType !in setOf(
                EConfigType.VLESS,
                EConfigType.VMESS,
                EConfigType.TROJAN,
                EConfigType.SHADOWSOCKS,
                EConfigType.SOCKS,
            )
        ) {
            return context.getString(com.aras.client.R.string.hybrid_unsupported_config)
        }

        val previousGuid = MmkvManager.getSelectServer()
        val token = UUID.randomUUID().toString().take(8)
        val v2rayName = "Hybrid-$token-V2Ray"
        val psiphonName = "Hybrid-$token-Psiphon"
        val chainName = "Hybrid-$token"

        val created = mutableListOf<String>()
        try {
            parsed.remarks = v2rayName
            parsed.subscriptionId = ""
            parsed.description = "Temporary profile for Hybrid mode"
            val v2rayGuid = MmkvManager.encodeServerConfig("", parsed)
            created += v2rayGuid

            // Psiphon-only mode needs no Aether/WARP peer. A loopback sentinel is kept
            // in the profile because the generic chain resolver requires each hop to
            // have a syntactically valid server address; Aether's builder intentionally
            // ignores peer fields when psiphon=only.
            val psiphon = ProfileItem.create(EConfigType.AETHER).apply {
                remarks = psiphonName
                subscriptionId = ""
                server = "127.0.0.1"
                serverPort = "1"
                aetherProtocol = AetherProtocol.WIREGUARD.type
                aetherScanMode = AetherScanMode.BALANCED.type
                aetherIpVersion = AetherIpVersion.V4.type
                aetherPsiphon = AetherPsiphon.ONLY.type
                aetherPsiphonMode = AetherPsiphonMode.AUTO.type
                aetherListenPort = AetherCoreManager.socksPort.toString()
                description = "Temporary Psiphon-only entry hop for Hybrid mode"
            }
            val psiphonGuid = MmkvManager.encodeServerConfig("", psiphon)
            created += psiphonGuid

            // Chain resolution reverses the saved member list. Saving Psiphon first
            // makes V2Ray dial through Psiphon, while Psiphon remains the final hop.
            val chain = ProfileItem.create(EConfigType.PROXYCHAIN).apply {
                remarks = chainName
                subscriptionId = ""
                proxyChainProfiles = "$psiphonName,$v2rayName"
                description = "V2Ray/Xray through Psiphon (temporary Hybrid connection)"
            }
            val chainGuid = MmkvManager.encodeServerConfig("", chain)
            created += chainGuid

            activeSession = Session(chainGuid, created.toList(), previousGuid)
            MmkvManager.setSelectServer(chainGuid)
            return null
        } catch (e: Exception) {
            created.asReversed().forEach { runCatching { MmkvManager.removeServer(it) } }
            if (previousGuid != null && MmkvManager.decodeServerConfig(previousGuid) != null) {
                MmkvManager.setSelectServer(previousGuid)
            }
            return context.getString(com.aras.client.R.string.hybrid_prepare_failed, e.message ?: e.javaClass.simpleName)
        }
    }

    fun start(context: Context): Boolean {
        val session = activeSession ?: return false
        return LauncherManager.startService(context, session.chainGuid)
    }

    fun hasSession(): Boolean = activeSession != null

    /**
     * Stop first, then restore the user's previous selection and remove every temporary
     * profile. The small delay lets the service process its stop message before cleanup.
     */
    fun stop(context: Context, onComplete: (() -> Unit)? = null) {
        val session = activeSession
        if (session == null) {
            onComplete?.invoke()
            return
        }

        LauncherManager.stopService(context)
        mainHandler.postDelayed({
            synchronized(HybridConnectManager) {
                val current = activeSession
                if (current?.chainGuid == session.chainGuid) {
                    val previous = session.previousGuid
                    if (previous != null && MmkvManager.decodeServerConfig(previous) != null) {
                        MmkvManager.setSelectServer(previous)
                    }
                    session.temporaryGuids.asReversed().forEach { guid ->
                        runCatching { MmkvManager.removeServer(guid) }
                    }
                    // If no previous profile existed, removeServer clears the temporary
                    // selection. Never leave a dangling selected GUID.
                    activeSession = null
                }
            }
            onComplete?.invoke()
        }, 1800L)
    }
}
