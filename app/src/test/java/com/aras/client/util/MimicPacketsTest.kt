package com.aras.client.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AmneziaWG mimic packets (I1..I5) and the specs they carry.
 *
 * A bad spec is not a cosmetic problem: the core's parser runs on the whole
 * device string, so one unparseable I1 fails the tunnel rather than costing only
 * the decoy. The validation here is the last line of defence, and the builders
 * filter on it, so these pin both the accepted grammar and the SIP preset.
 */
class MimicPacketsTest {

    @Test
    fun `a SIP preset is a single valid literal-bytes spec`() {
        val preset = MimicPackets.sipPreset()

        assertNull(MimicPackets.validate(preset))
        assertEquals(1, MimicPackets.specs(preset).size)
    }

    @Test
    fun `the SIP preset decodes to a SIP OPTIONS request`() {
        val hex = MimicPackets.sipPreset()
            .removePrefix("<b ")
            .removeSuffix(">")

        val text = hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
            .toString(Charsets.US_ASCII)

        assertTrue(text, text.startsWith("OPTIONS sip:"))
        assertTrue(text, text.contains(" SIP/2.0\r\n"))
        assertTrue(text, text.contains("Content-Length: 0\r\n"))
        assertTrue(text, text.endsWith("\r\n\r\n"))
    }

    @Test
    fun `every core tag is accepted`() {
        listOf("<b 4f5054>", "<t>", "<r 16>", "<rc 8>", "<rd 4>", "<d>", "<ds>", "<dz 2>")
            .forEach { spec -> assertNull("rejected: $spec", MimicPackets.validate(spec)) }
    }

    @Test
    fun `chained tags in one spec are accepted`() {
        assertNull(MimicPackets.validate("<dz 2><b 4f5054><rc 6>"))
    }

    @Test
    fun `an unknown tag is rejected`() {
        // The core answers "unknown tag <x>" and fails the device string.
        assertEquals("Unknown tag <z>", MimicPackets.validate("<z 4>"))
    }

    @Test
    fun `odd hex is rejected because the core cannot decode it`() {
        assertEquals("<b> has an odd number of hex digits", MimicPackets.validate("<b 4f5>"))
    }

    @Test
    fun `non-hex bytes are rejected`() {
        assertEquals("<b> has non-hex characters", MimicPackets.validate("<b 4f5z>"))
    }

    @Test
    fun `an unclosed tag is rejected`() {
        assertEquals("Missing closing >", MimicPackets.validate("<b 4f5054"))
    }

    @Test
    fun `a non-numeric size is rejected`() {
        assertEquals("<dz> needs a number", MimicPackets.validate("<dz abc>"))
    }

    @Test
    fun `specs drop blanks and keep order`() {
        val value = "<b 4f5054>\n\n   \n<t>\n<dz 2>\n"

        assertEquals(listOf("<b 4f5054>", "<t>", "<dz 2>"), MimicPackets.specs(value))
    }

    @Test
    fun `no more than five specs are kept`() {
        val six = (1..6).joinToString("\n") { "<dz $it>" }

        assertEquals(MimicPackets.MAX_PACKETS, MimicPackets.specs(six).size)
    }

    @Test
    fun `specAt indexes from one`() {
        val value = "<b 4f5054>\n<t>"

        assertEquals("<b 4f5054>", MimicPackets.specAt(value, 0))
        assertEquals("<t>", MimicPackets.specAt(value, 1))
        assertNull(MimicPackets.specAt(value, 2))
        assertNull(MimicPackets.specAt(null, 0))
    }

    @Test
    fun `validateAll reports the offending line by name`() {
        val value = "<b 4f5054>\n<nope>"

        assertEquals(listOf("I2: Unknown tag <nope>"), MimicPackets.validateAll(value))
    }

    @Test
    fun `a blank profile is not an error`() {
        assertTrue(MimicPackets.validateAll(null).isEmpty())
        assertTrue(MimicPackets.validateAll("   ").isEmpty())
        assertTrue(MimicPackets.specs(null).isEmpty())
    }

    @Test
    fun `an empty b argument is rejected`() {
        assertEquals("<b> needs hex bytes", MimicPackets.validate("<b>"))
    }

    @Test
    fun `a 0x prefix is accepted like the core accepts it`() {
        assertNull(MimicPackets.validate("<b 0x4f5054>"))
        assertNotNull(MimicPackets.validate("<b 0x4f5>"))
    }
}