package com.aras.client.radar

import java.net.URLDecoder

/** مشخصات یک کانفیگ برای تست؛ بدون وابستگی به اندروید تا قابل تست واحد باشد. */
data class RadarEndpoint(
    val protocol: String,
    val host: String,
    val port: Int,
    /** none | tls | reality | quic */
    val security: String,
    val transport: String,
    val sni: String?,
    /** پروتکل‌های مبتنی بر UDP/QUIC را نمی‌شود با TCP سنجید */
    val udp: Boolean,
    /** کلید یکتا برای حذف تکراری‌ها */
    val key: String,
)

object RadarParser {

    private val uriPattern = Regex(
        "(?<![A-Za-z0-9])(?:vmess|vless|trojan|ss|hy2|hysteria2|hysteria|tuic)://[^\\s\"'<>]+",
        RegexOption.IGNORE_CASE,
    )

    /** دیکد Base64 تحمل‌پذیر (URL-safe، بدون padding، فاصله و خط جدید). */
    fun decodeBase64(input: String): ByteArray? {
        val clean = input.filter { !it.isWhitespace() && it != '=' }
            .replace('-', '+').replace('_', '/')
        if (clean.isEmpty()) return null
        val table = IntArray(128) { -1 }
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { i, ch -> table[ch.code] = i }
        val out = java.io.ByteArrayOutputStream(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (ch in clean) {
            val v = if (ch.code < 128) table[ch.code] else -1
            if (v < 0) return null
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    private fun decodeText(input: String): String? = decodeBase64(input)?.toString(Charsets.UTF_8)

    /** همه‌ی URI های کانفیگ داخل یک متن (ساده یا Base64 اشتراک). */
    fun extractUris(text: String): List<String> {
        fun scan(s: String): List<String> =
            uriPattern.findAll(s).map { it.value.trimEnd(',', ';', ')', '"', '\'') }.toList()

        val direct = scan(text)
        if (direct.isNotEmpty()) return direct
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.length !in 24..4_000_000) return emptyList()
        val decoded = decodeText(compact) ?: return emptyList()
        return scan(decoded)
    }

    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h == "::1" || h == "0.0.0.0") return true
        val m = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").find(h) ?: return false
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        return a == 127 || a == 10 || a == 0 || (a == 192 && b == 168) || (a == 169 && b == 254) ||
            (a == 172 && b in 16..31)
    }

    private fun jsonField(json: String, name: String): String? =
        Regex("\"" + name + "\"\\s*:\\s*\"?([^\",}]*)").find(json)?.groupValues?.get(1)?.trim()

    private fun queryMap(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else {
                val k = it.substring(0, i).lowercase()
                val v = runCatching { URLDecoder.decode(it.substring(i + 1), "UTF-8") }.getOrDefault(it.substring(i + 1))
                k to v
            }
        }.toMap()
    }

    fun describe(uri: String): RadarEndpoint? = runCatching {
        val scheme = uri.substringBefore("://").lowercase()
        val protocol = if (scheme == "hy2") "hysteria2" else scheme
        val rest = uri.substringAfter("://")

        if (scheme == "vmess") {
            val raw = decodeText(rest.substringBefore('#')) ?: return null
            val host = jsonField(raw, "add")?.takeIf { it.isNotBlank() } ?: return null
            val port = jsonField(raw, "port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val tls = jsonField(raw, "tls")?.lowercase() == "tls"
            val net = jsonField(raw, "net")?.lowercase()?.ifBlank { null } ?: "tcp"
            val sni = jsonField(raw, "sni")?.takeIf { it.isNotBlank() } ?: jsonField(raw, "host")?.takeIf { it.isNotBlank() }
            val id = jsonField(raw, "id").orEmpty()
            return RadarEndpoint("vmess", host, port, if (tls) "tls" else "none", net, sni, false, "vmess|$id|${host.lowercase()}|$port")
        }

        var body = rest.substringBefore('#')
        if (scheme == "ss" && !body.contains('@')) {
            body = decodeText(body.substringBefore('?')) ?: return null
        }
        val query = queryMap(body.substringAfter('?', ""))
        val hostPort = body.substringBefore('?').substringBefore('/')
        val userInfo = if (hostPort.contains('@')) hostPort.substringBeforeLast('@') else ""
        val hp = hostPort.substringAfterLast('@')
        val host: String
        val portText: String
        if (hp.startsWith("[")) {
            host = hp.substringAfter('[').substringBefore(']')
            portText = hp.substringAfter("]:", "")
        } else {
            host = hp.substringBeforeLast(':', "")
            portText = hp.substringAfterLast(':', "")
        }
        val port = portText.takeWhile { it.isDigit() }.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        if (host.isBlank()) return null

        val udp = protocol == "hysteria2" || protocol == "hysteria" || protocol == "tuic"
        val security = when {
            udp -> "quic"
            else -> (query["security"] ?: if (protocol == "trojan") "tls" else "none").lowercase()
        }
        val transport = if (udp) "udp" else (query["type"] ?: "tcp").lowercase()
        val sni = query["sni"] ?: query["peer"] ?: query["host"]
        RadarEndpoint(protocol, host, port, security, transport, sni?.takeIf { it.isNotBlank() }, udp,
            "$protocol|$userInfo|${host.lowercase()}|$port")
    }.getOrNull()

    /**
     * امتیاز ۰ تا ۱۰۰: تأخیر TCP + تأیید TLS + نوع پروتکل (Reality و TLS بهتر از متن‌ساده).
     * UDP/QUIC قابل سنجش با TCP نیست و امتیاز خنثی می‌گیرد.
     */
    fun score(ep: RadarEndpoint, tcpMs: Long?, tlsOk: Boolean?, group: RadarGroup): Int {
        if (ep.udp) return 35
        if (tcpMs == null) return 0
        var s = when {
            tcpMs <= 80 -> 60
            tcpMs <= 150 -> 52
            tcpMs <= 300 -> 40
            tcpMs <= 600 -> 25
            else -> 10
        }
        when (tlsOk) {
            true -> s += 20
            false -> s -= 25
            null -> Unit
        }
        s += when {
            ep.security == "reality" -> 12
            ep.security == "tls" -> 6
            ep.protocol == "vmess" && ep.security == "none" -> -10
            else -> 0
        }
        if (group == RadarGroup.TOP || group == RadarGroup.FAST) s += 5
        return s.coerceIn(0, 100)
    }
}
