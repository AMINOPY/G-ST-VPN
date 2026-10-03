package com.aras.client.util

/**
 * AmneziaWG "mimic packets" — the I1..I5 parameters.
 *
 * Before every WireGuard handshake initiation the device sends up to five
 * fixed-shape decoy packets, so the first bytes a censor sees belong to some
 * other protocol entirely. That is what makes "mimic SIP" possible: I1 carries
 * a SIP request, and a middlebox that expects SIP sees SIP.
 *
 * A value is amneziawg's obfuscation spec: a sequence of `<tag arg>` pieces.
 * The core parses it and refuses the whole tunnel on a malformed one, so this
 * validates before anything is written to a profile.
 */
object MimicPackets {

    /** The core accepts five; a sixth line would be silently dropped. */
    const val MAX_PACKETS = 5

    /** Every tag the core's obfuscation builder understands. */
    private val KNOWN_TAGS = setOf("b", "t", "r", "rc", "rd", "d", "ds", "dz")

    /** A SIP OPTIONS request, hex-encoded, as one fixed-shape decoy. */
    private const val SIP_OPTIONS_HEX =
        "4f5054494f4e53207369703a63617272696572407369702e6578616d706c652e636f6d205349502f322e300d0a" +
            "5669613a205349502f322e302f544350203139322e302e322e313a353036303b6272616e63683d7a39684734624b" +
            "2d3532343238372d312d2d2d336639613163376435653262386630340d0a4d61782d466f7277617264733a2037300d" +
            "0a546f3a203c7369703a63617272696572407369702e6578616d706c652e636f6d3e0d0a46726f6d3a20225043532" +
            "220203c7369703a3139322e302e322e313e3b7461673d313932383330313737340d0a43616c6c2d49443a2033663" +
            "961316337643565326238663034403139322e302e322e310d0a435365713a2031204f5054494f4e530d0a436f6e74" +
            "6163743a203c7369703a3139322e302e322e313a353036303e0d0a4163636570743a206170706c69636174696f6e" +
            "2f7364700d0a416c6c6f773a20494e564954452c2041434b2c2043414e43454c2c204f5054494f4e532c20425945" +
            "2c2052454645522c20494e464f2c205550444154450d0a537570706f727465643a2074696d65722c207265706c61" +
            "6365730d0a557365722d4167656e743a2041726173436c69656e740d0a436f6e74656e742d4c656e6774683a20" +
            "300d0a0d0a"

    /** What the editor's "SIP" button fills in: one 464-byte OPTIONS request. */
    fun sipPreset(): String = "<b $SIP_OPTIONS_HEX>"

    /** The stored field split into the up-to-five specs it represents, in order. */
    fun specs(value: String?): List<String> = value
        .orEmpty()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_PACKETS)
        .toList()

    /** The spec for I<n>, or null when the profile does not set one. */
    fun specAt(value: String?, index: Int): String? = specs(value).getOrNull(index)

    /**
     * Why [spec] would be rejected, or null when it is usable.
     *
     * Kept in step with the core's own parser: unknown tags, an unclosed `<`, and
     * a `<b>` that is not an even number of hex digits are the three ways a value
     * can fail there — and there the failure costs the whole tunnel, not the decoy.
     */
    fun validate(spec: String): String? {
        if (spec.isBlank()) return "Empty"
        val open = spec.indexOf('<')
        if (open == -1) return "No <tag> found"

        var remaining = spec
        while (true) {
            val start = remaining.indexOf('<')
            if (start == -1) break
            val end = remaining.indexOf('>', start)
            if (end == -1) return "Missing closing >"
            val parts = remaining.substring(start + 1, end).trim().split(Regex("\\s+"))
            if (parts.isEmpty() || parts[0].isEmpty()) return "Empty tag"
            val tag = parts[0]
            if (tag !in KNOWN_TAGS) return "Unknown tag <$tag>"
            val arg = parts.getOrNull(1).orEmpty()
            when (tag) {
                "b" -> {
                    val hex = arg.removePrefix("0x")
                    if (hex.isEmpty()) return "<b> needs hex bytes"
                    if (hex.length % 2 != 0) return "<b> has an odd number of hex digits"
                    if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                        return "<b> has non-hex characters"
                    }
                }

                "dz" -> if (arg.toIntOrNull() == null) return "<dz> needs a number"
                "r", "rc", "rd" -> if (arg.toIntOrNull() == null) return "<$tag> needs a number"
                // <t>, <d>, <ds> take no argument.
                else -> Unit
            }
            remaining = remaining.substring(end + 1)
        }
        return null
    }

    /** Every problem in [value], one per non-blank line that fails. */
    fun validateAll(value: String?): List<String> = specs(value)
        .mapIndexedNotNull { index, spec -> validate(spec)?.let { "I${index + 1}: $it" } }
}