package com.aras.client.service

import android.content.Context
import com.aras.client.AppConfig
import com.aras.client.core.CoreConfigContextBuilder
import com.aras.client.core.CoreConfigManager
import com.aras.client.core.CoreNativeManager
import com.aras.client.core.HelperProcessSupport
import com.aras.client.dto.RealPingEvent
import com.aras.client.enums.EConfigType
import com.aras.client.extension.isComplexType
import com.aras.client.extension.isNotNullEmpty
import com.aras.client.handler.MmkvManager
import com.aras.client.handler.SettingsManager
import com.aras.client.handler.SpeedtestManager
import com.aras.client.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal object RealPingExecutionLimiter {
    private val customConfigMutex = Mutex()

    suspend fun <T> run(configType: EConfigType, block: () -> T): T {
        // Custom profiles bypass speed-test trimming and start complete Xray configs.
        // Parallel teardown can abort the native probe process, so serialize their
        // JNI measurements globally across batches.
        return if (configType == EConfigType.CUSTOM) {
            customConfigMutex.withLock { block() }
        } else {
            block()
        }
    }
}

/**
 * Worker that runs a batch of real-ping tests independently.
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled separately.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val concurrency = SettingsManager.getRealPingConcurrency()
    private val dispatcher = Executors.newFixedThreadPool(if (onlyTcp) concurrency * 2 else concurrency).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    private val runningCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    fun start() {
        val jobs = guids.map { guid ->
            totalCount.incrementAndGet()
            scope.launch {
                runningCount.incrementAndGet()
                try {
                    val result = if (onlyTcp) startTcping(guid) else startRealPing(guid)
                    if (scope.isActive) {
                        onEvent(RealPingEvent.Result(guid, result))
                    }
                } catch (_: Throwable) {
                    // ignore
                } finally {
                    val count = totalCount.decrementAndGet()
                    val left = runningCount.decrementAndGet()
                    if (scope.isActive) {
                        onEvent(RealPingEvent.Progress("$left / $count"))
                    }
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                if (isActive) {
                    onEvent(RealPingEvent.Finish("0"))
                }
            } catch (_: CancellationException) {
                // If cancelled, don't send finish event to avoid confusion
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private suspend fun startRealPing(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        val chain = CoreConfigContextBuilder.resolveChainProfiles(context, guid)
        // Only a profile that is its own chain is worth probing straight to. A profile
        // that hops through an entry or exit proxy may be reachable *only* through it,
        // and the direct probe rejected the whole subscription before the chain was
        // ever dialled — which is what made an entry proxy mark every server -1.
        if (chain.size <= 1
            && !config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.configType != EConfigType.AMNEZIAWG
            && config.configType != EConfigType.ANYTLS
            && config.configType != EConfigType.MASQUE
            && config.configType != EConfigType.AETHER
            && config.configType != EConfigType.MIERU
            && config.alpn?.startsWith("h3") != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val url = config.server.orEmpty()
            val port = config.serverPort.orEmpty().toInt()
            val tcpTime = SpeedtestManager.socketConnectTime(url, port, 1000)
            if (tcpTime <= -1L) {
                return retFailure
            }
        }

        val configResult = CoreConfigManager.getXrayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return retFailure
        }
        return RealPingExecutionLimiter.run(config.configType) {
            // Any Aether or Mieru hop in the resolved chain lowers to a SOCKS hop on
            // loopback, so its daemon has to be listening or every measurement through
            // it is -1. The refcounts here mean a batch of profiles sharing one entry
            // proxy starts that daemon once, not once per profile.
            val helpers = HelperProcessSupport.acquire(context, chain)
            if (!helpers.ok) {
                LogUtil.w(AppConfig.TAG, "RealPing: ${helpers.error}")
                HelperProcessSupport.release(helpers)
                return@run retFailure
            }
            try {
                CoreNativeManager.measureOutboundDelay(
                    configResult.content,
                    SettingsManager.getDelayTestUrl(),
                )
            } finally {
                HelperProcessSupport.release(helpers)
            }
        }
    }

    private fun startTcping(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.configType != EConfigType.AMNEZIAWG
            && config.configType != EConfigType.ANYTLS
            && config.configType != EConfigType.MASQUE
            && config.configType != EConfigType.AETHER
            && config.configType != EConfigType.MIERU
            && config.alpn?.split(',')?.all { it.trim().startsWith("h3") } != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val url = config.server.orEmpty()
            val port = config.serverPort.orEmpty().toInt()
            val tcpTime = SpeedtestManager.socketConnectTime(url, port, 1000)

            return tcpTime
        }

        return retFailure
    }
}
