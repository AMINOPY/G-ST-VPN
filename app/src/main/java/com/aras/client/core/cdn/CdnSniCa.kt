package com.aras.client.core.cdn

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The certificate authority that lets CDN/SNI end the browser's TLS locally.
 *
 * It is made on this device the first time it is needed and its private key never
 * leaves the app's private storage: only the certificate is ever exported, so the
 * user can trust it in the system settings. Nothing is shared between devices.
 */
object CdnSniCa {

    private const val CERT_FILE = "cdn_sni_ca.crt"
    private const val KEY_FILE = "cdn_sni_ca.key"
    private const val SUBJECT = "ArasClient CDN-SNI Local CA"

    data class Pem(val certificate: String, val key: String)

    /** The CA, made now if this device has none yet. */
    @Synchronized
    fun getOrCreate(context: Context): Pem {
        val certFile = File(context.filesDir, CERT_FILE)
        val keyFile = File(context.filesDir, KEY_FILE)
        if (certFile.isFile && keyFile.isFile) {
            val cert = certFile.readText()
            val key = keyFile.readText()
            if (cert.isNotBlank() && key.isNotBlank()) return Pem(cert, key)
        }
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = Pem(
            certificate = toPem("CERTIFICATE", buildCertificate(pair)),
            key = toPem("PRIVATE KEY", pair.private.encoded),
        )
        // Write the key first: a certificate without its key would be useless.
        keyFile.writeText(pem.key)
        certFile.writeText(pem.certificate)
        return pem
    }

    fun hasCa(context: Context): Boolean =
        File(context.filesDir, CERT_FILE).isFile && File(context.filesDir, KEY_FILE).isFile

    fun certificatePem(context: Context): String = getOrCreate(context).certificate

    fun toPem(label: String, der: ByteArray): String {
        val body = Base64.encodeToString(der, Base64.NO_WRAP).chunked(64).joinToString("\n")
        return "-----BEGIN $label-----\n$body\n-----END $label-----\n"
    }

    /** A self-signed X.509 v3 CA certificate for [pair], valid for ten years. DER. */
    internal fun buildCertificate(pair: KeyPair, now: Date = Date()): ByteArray {
        val sigAlg = Der.seq(Der.oid("1.2.840.113549.1.1.11"), Der.nul())
        val name = Der.seq(Der.set(Der.seq(Der.oid("2.5.4.3"), Der.utf8(SUBJECT))))
        val serial = ByteArray(16).also { SecureRandom().nextBytes(it) }.also { it[0] = (it[0].toInt() and 0x7F).toByte() }
        val notAfter = Date(now.time + 10L * 365 * 24 * 3600 * 1000)

        val basicConstraints = Der.seq(
            Der.oid("2.5.29.19"), Der.bool(true), Der.octets(Der.seq(Der.bool(true)))
        )
        // keyCertSign and cRLSign
        val keyUsage = Der.seq(
            Der.oid("2.5.29.15"), Der.bool(true), Der.octets(Der.bitString(byteArrayOf(0x06), unused = 1))
        )
        val tbs = Der.seq(
            Der.explicit(0, Der.integer(BigInteger.valueOf(2))), // v3
            Der.integer(BigInteger(1, serial)),
            sigAlg,
            name,
            Der.seq(Der.utcTime(now), Der.utcTime(notAfter)),
            name,
            pair.public.encoded, // SubjectPublicKeyInfo
            Der.explicit(3, Der.seq(basicConstraints, keyUsage)),
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(pair.private)
            update(tbs)
            sign()
        }
        return Der.seq(tbs, sigAlg, Der.bitString(signature, unused = 0))
    }

    /** The few DER encodings an X.509 certificate needs. */
    internal object Der {
        private fun length(n: Int): ByteArray = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        }

        fun tlv(tag: Int, content: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + length(content.size) + content
        private fun cat(parts: Array<out ByteArray>): ByteArray = parts.fold(ByteArray(0)) { a, b -> a + b }

        fun seq(vararg parts: ByteArray) = tlv(0x30, cat(parts))
        fun set(vararg parts: ByteArray) = tlv(0x31, cat(parts))
        fun explicit(n: Int, inner: ByteArray) = tlv(0xA0 or n, inner)
        fun nul() = byteArrayOf(0x05, 0x00)
        fun bool(v: Boolean) = tlv(0x01, byteArrayOf(if (v) 0xFF.toByte() else 0x00))
        fun integer(v: BigInteger) = tlv(0x02, v.toByteArray())
        fun octets(b: ByteArray) = tlv(0x04, b)
        fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
        fun bitString(b: ByteArray, unused: Int) = tlv(0x03, byteArrayOf(unused.toByte()) + b)

        fun utcTime(d: Date): ByteArray {
            val f = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return tlv(0x17, f.format(d).toByteArray(Charsets.US_ASCII))
        }

        fun oid(dotted: String): ByteArray {
            val p = dotted.split('.').map { it.toLong() }
            val out = java.io.ByteArrayOutputStream()
            out.write((p[0] * 40 + p[1]).toInt())
            for (v in p.drop(2)) {
                val chunks = ArrayList<Int>()
                var x = v
                chunks.add((x and 0x7F).toInt())
                x = x shr 7
                while (x > 0) { chunks.add(((x and 0x7F) or 0x80).toInt()); x = x shr 7 }
                chunks.reversed().forEach { out.write(it) }
            }
            return tlv(0x06, out.toByteArray())
        }
    }
}
