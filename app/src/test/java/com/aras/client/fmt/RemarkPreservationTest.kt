package com.aras.client.fmt

import com.aras.client.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The remark is the provider's name. Nothing on the import path may rewrite a
 * non-empty remark — not flag emojis, not whitespace normalization, not a
 * default name. A default name is allowed only when the remark is empty,
 * because a profile can't be identified without one.
 */
class RemarkPreservationTest {

    private val link = "awg://cHfvEYAOf%2BZFEizLKhyJEDGhY9egzPxwIwlcb2ThTEo%3D@8.6.112.148:891" +
        "?publickey=bmXOC%2BF1FxEMF9dyiK2H5%2F1SUtzH0JuVo51h2wPfgyo%3D" +
        "&address=10.2.0.2%2F32&junkPacketCount=5#🇩🇪%20Frankfurt%2001"

    @Test
    fun `an awg link remark keeps its flag and wording`() {
        val config = AmneziawgFmt.parse(link)!!

        assertEquals("🇩🇪 Frankfurt 01", config.remarks)
    }

    @Test
    fun `an awg share-link round trip preserves the remark byte for byte`() {
        val original = AmneziawgFmt.parse(link)!!
        val shared = EConfigType.AMNEZIAWG.protocolScheme + AmneziawgFmt.toUri(original)
        val reparsed = AmneziawgFmt.parse(shared)!!

        assertEquals(original.remarks, reparsed.remarks)
    }

    @Test
    fun `a vless link remark with a flag survives parsing`() {
        val vless = "vless://5e1f4e06-dd77-4a53-8f22-13fd4d5c3c3d@example.com:443" +
            "?security=tls&type=ws#🇹🇷%20Istanbul%20Pro"

        val config = VlessFmt.parse(vless)!!

        assertEquals("🇹🇷 Istanbul Pro", config.remarks)
    }

    @Test
    fun `only an empty remark gets a default name`() {
        val empty = "vless://5e1f4e06-dd77-4a53-8f22-13fd4d5c3c3d@example.com:443" +
            "?security=tls&type=ws#"

        val config = VlessFmt.parse(empty)!!

        assertEquals("none", config.remarks)
    }
}
