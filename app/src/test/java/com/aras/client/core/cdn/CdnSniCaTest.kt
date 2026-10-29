package com.aras.client.core.cdn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class CdnSniCaTest {

    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun cert(): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(
            ByteArrayInputStream(CdnSniCa.buildCertificate(pair))
        ) as X509Certificate

    @Test
    fun certificateParsesAsAVersion3SelfSignedCa() {
        val c = cert()
        assertEquals(3, c.version)
        assertEquals(c.subjectX500Principal, c.issuerX500Principal)
        assertTrue("CN=ArasClient CDN-SNI Local CA" in c.subjectX500Principal.name)
        assertEquals("SHA256withRSA", c.sigAlgName)
    }

    @Test
    fun certificateIsACaAllowedToSignCertificates() {
        val c = cert()
        assertTrue(c.basicConstraints >= 0) // CA:TRUE
        val usage = c.keyUsage!!
        assertTrue(usage[5]) // keyCertSign
    }

    @Test
    fun signatureVerifiesAgainstItsOwnKeyAndIsValidNow() {
        val c = cert()
        c.verify(pair.public)
        c.checkValidity()
        assertEquals(pair.public, c.publicKey)
    }

    @Test
    fun oidEncodingMatchesKnownValues() {
        // 2.5.4.3 = 55 04 03 ; 1.2.840.113549.1.1.11 = 2A 86 48 86 F7 0D 01 01 0B
        assertEquals(listOf(0x06, 0x03, 0x55, 0x04, 0x03), CdnSniCa.Der.oid("2.5.4.3").map { it.toInt() and 0xFF })
        assertEquals(
            listOf(0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x0B),
            CdnSniCa.Der.oid("1.2.840.113549.1.1.11").map { it.toInt() and 0xFF }
        )
    }
}
