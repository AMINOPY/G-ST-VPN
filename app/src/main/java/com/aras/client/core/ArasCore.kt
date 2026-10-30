package com.aras.client.core

// Single access point to the bundled native core bindings.
typealias CoreController = libv2ray.CoreController
typealias CoreCallbackHandler = libv2ray.CoreCallbackHandler
typealias ProcessFinder = libv2ray.ProcessFinder

object ArasCore {
    fun initCoreEnv(assetPath: String, deviceId: String) =
        libv2ray.Libv2ray.initCoreEnv(assetPath, deviceId)

    fun reconcileBrowserDialer(dialerAddr: String?) =
        libv2ray.Libv2ray.reconcileBrowserDialer(dialerAddr)

    fun checkVersionX() = libv2ray.Libv2ray.checkVersionX()

    fun measureOutboundDelay(config: String?, testUrl: String?) =
        libv2ray.Libv2ray.measureOutboundDelay(config, testUrl)

    fun measureOutboundDelayWithInfo(
        config: String?,
        testUrl: String?,
        ipApiUrl: String?
    ): libv2ray.OutboundDelayResult =
        libv2ray.Libv2ray.measureOutboundDelayWithInfo(config, testUrl, ipApiUrl)

    fun newCoreController(handler: CoreCallbackHandler?) =
        libv2ray.Libv2ray.newCoreController(handler)

    fun fetchQuicCertSha256(request: String) =
        libv2ray.Libv2ray.fetchQuicCertSha256(request)

    fun fetchTlsCertSha256(request: String) =
        libv2ray.Libv2ray.fetchTlsCertSha256(request)

    /**
     * Keep AmneziaWG integration source-compatible with core AAR versions that
     * expose the optional AWG bridge. Resolve it at runtime so an AAR without
     * those optional methods does not prevent the rest of the app from building.
     */
    fun awgTurnOn(fd: Int, uapiConfig: String, mtu: Int) {
        invokeOptionalAwg("awgTurnOn",
            arrayOf(Int::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType),
            arrayOf(fd, uapiConfig, mtu)
        )
    }

    fun awgTurnOff() {
        invokeOptionalAwg("awgTurnOff", emptyArray(), emptyArray())
    }

    fun awgIsRunning(): Boolean {
        return invokeOptionalAwg("awgIsRunning", emptyArray(), emptyArray()) as? Boolean
            ?: false
    }

    private fun invokeOptionalAwg(
        methodName: String,
        parameterTypes: Array<Class<*>?>,
        arguments: Array<Any>
    ): Any? {
        val method = try {
            libv2ray.Libv2ray::class.java.getMethod(methodName, *parameterTypes)
        } catch (e: NoSuchMethodException) {
            throw UnsupportedOperationException(
                "AmneziaWG is not supported by the bundled native core (missing $methodName)",
                e
            )
        }
        return try {
            method.invoke(null, *arguments)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw (e.targetException ?: e)
        }
    }
}
