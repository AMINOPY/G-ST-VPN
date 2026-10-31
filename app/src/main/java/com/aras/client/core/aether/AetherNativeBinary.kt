package com.aras.client.core.aether

import java.io.File
import java.io.RandomAccessFile

/**
 * Checks the native Aether payload before attempting ProcessBuilder.start().
 *
 * Android extracts jniLibs into nativeLibraryDir at install time. We therefore validate
 * the installed payload rather than copying .so files into app-private storage (which
 * Android may refuse to execute on modern releases).
 */
internal object AetherNativeBinary {
    data class Check(
        val available: Boolean,
        val reason: String,
        val abi: String? = null,
    )

    fun inspect(file: File, supportedAbis: List<String>): Check {
        if (!file.isFile || file.length() < ELF_HEADER_SIZE) {
            return Check(false, "missing_or_truncated")
        }
        if (!file.canExecute()) return Check(false, "not_executable")

        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val header = ByteArray(ELF_HEADER_SIZE)
                input.readFully(header)
                if (header[0] != 0x7f.toByte() ||
                    header[1] != 'E'.code.toByte() ||
                    header[2] != 'L'.code.toByte() ||
                    header[3] != 'F'.code.toByte()
                ) return Check(false, "invalid_elf")

                val elfClass = header[4].toInt() and 0xff
                val dataEncoding = header[5].toInt() and 0xff
                if (dataEncoding != ELF_LITTLE_ENDIAN) return Check(false, "unsupported_elf_endianness")

                val machine = (header[18].toInt() and 0xff) or
                    ((header[19].toInt() and 0xff) shl 8)
                val abi = ABI_BY_MACHINE[machine] ?: return Check(false, "unknown_elf_machine")
                if (abi !in supportedAbis) return Check(false, "abi_mismatch", abi)

                val expectedClass = if (abi == "arm64-v8a" || abi == "x86_64") ELF_CLASS_64 else ELF_CLASS_32
                if (elfClass != expectedClass) return Check(false, "elf_class_mismatch", abi)

                Check(true, "ok", abi)
            }
        }.getOrElse { Check(false, "unreadable_elf") }
    }

    private const val ELF_HEADER_SIZE = 20
    private const val ELF_LITTLE_ENDIAN = 1
    private const val ELF_CLASS_32 = 1
    private const val ELF_CLASS_64 = 2

    private val ABI_BY_MACHINE = mapOf(
        3 to "x86",
        40 to "armeabi-v7a",
        62 to "x86_64",
        183 to "arm64-v8a",
    )
}
