package com.aras.client.ui.radar

import com.aras.client.handler.MmkvManager
import java.security.MessageDigest

/**
 * Persists only measurements emitted by the VPN core. TCP/DNS preflight values
 * must never be written here.
 */
object RadarRdyResultStore {
    private const val PREFIX = "radar_core_rdy_v1_"

    fun keyFor(config: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(config.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return PREFIX + digest
    }

    fun markPending(key: String) {
        MmkvManager.encodeSettings(key, "PENDING")
    }

    fun recordDelay(key: String, delayMillis: Long) {
        if (delayMillis >= 0L) MmkvManager.encodeSettings(key, "RESULT:$delayMillis")
    }

    fun markFinished(key: String) {
        val current = MmkvManager.decodeSettingsString(key)
        if (current == "PENDING") MmkvManager.encodeSettings(key, "FINISHED:NO_VALID_RESULT")
        else if (current?.startsWith("RESULT:") == true) MmkvManager.encodeSettings(key, current + ":DONE")
    }

    fun read(config: String): String? = MmkvManager.decodeSettingsString(keyFor(config))
}
