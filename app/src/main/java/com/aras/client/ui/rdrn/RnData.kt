package com.aras.client.ui.rdrn

import androidx.compose.runtime.Immutable
import com.aras.client.handler.MmkvManager
import com.aras.client.util.CountryResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class RnServer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    /** Display label: VMess, VLESS, Shadowsocks … */
    val protocol: String,
    val flag: String,
    /** ISO-3166 alpha-2, "" when the country is unknown. */
    val iso: String,
    /** null = never tested, -1 = failed/timeout. */
    val pingMs: Int?,
)

/** Read-only snapshot of every saved config. Presentation only: nothing here writes to storage. */
object RnData {
    suspend fun loadServers(): List<RnServer> = withContext(Dispatchers.Default) {
        MmkvManager.decodeAllServerList().mapNotNull { guid ->
            val p = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            val flag = CountryResolver
                .resolveCardCountry(p, aff?.countryCode.orEmpty(), aff?.countryCode)
                .ifBlank { "🌐" }
            val delay = aff?.testDelayMillis ?: 0L
            RnServer(
                id = guid,
                name = p.remarks.ifBlank { p.server.orEmpty() },
                host = p.server.orEmpty(),
                port = p.serverPort?.toIntOrNull() ?: 0,
                protocol = RnUtil.protocolLabel(p.configType.name),
                flag = flag,
                iso = RnUtil.isoFromFlag(flag),
                pingMs = when {
                    delay > 0 -> delay.toInt()
                    delay < 0 -> -1
                    else -> null
                },
            )
        }
    }
}
