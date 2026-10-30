package com.aras.client.core.aether

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherNativeBinaryTest {
    @Test
    fun `accepts a valid arm64 little endian ELF header`() {
        val file = elfFile(machine = 183, elfClass = 2)
        val result = AetherNativeBinary.inspect(file, listOf("arm64-v8a"))
        assertTrue(result.available)
        assertEquals("arm64-v8a", result.abi)
        file.delete()
    }

    @Test
    fun `rejects a binary for an ABI not supported by the installed app`() {
        val file = elfFile(machine = 62, elfClass = 2)
        val result = AetherNativeBinary.inspect(file, listOf("arm64-v8a"))
        assertFalse(result.available)
        assertEquals("abi_mismatch", result.reason)
        file.delete()
    }

    @Test
    fun `rejects non ELF payloads`() {
        val file = File.createTempFile("aether-invalid", ".so")
        file.writeBytes(ByteArray(64))
        file.setExecutable(true, false)
        val result = AetherNativeBinary.inspect(file, listOf("arm64-v8a"))
        assertFalse(result.available)
        assertEquals("invalid_elf", result.reason)
        file.delete()
    }

    private fun elfFile(machine: Int, elfClass: Int): File {
        val bytes = ByteArray(64)
        bytes[0] = 0x7f
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = elfClass.toByte()
        bytes[5] = 1
        bytes[18] = (machine and 0xff).toByte()
        bytes[19] = ((machine ushr 8) and 0xff).toByte()
        return File.createTempFile("aether-valid", ".so").apply {
            writeBytes(bytes)
            setExecutable(true, false)
        }
    }
}
