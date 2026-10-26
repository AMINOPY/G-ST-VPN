package com.aras.client.radar

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder

/** نتیجه‌ی تجزیه‌ی یک لینک کانفیگ. */
data class ParsedConfig(
    val uri: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val name: String,
    /** none | tls | reality | quic */
    val security: String,
    val transport: String,
    val sni: String,
    /** کلید یکتاسازی: پروتکل + میزبان + پورت + شناسه */
    val key: String,
    /** کشور حدس‌زده‌شده از پرچمِ داخل نام کانفیگ */
    val flagIso: String?,
)

object RadarParser {
    private val URI_PATTERN = Regex(
        "(?:vmess|vless|trojan|ss|hy2|hysteria2|tuic|socks5?)://[^\\s\"'<>]+",
        RegexOption.IGNORE_CASE,
    )

    /** همه‌ی لینک‌ها را از متن ساده یا ساب base64 بیرون می‌کشد. */
    fun extract(text: String): List<String> {
        val direct = URI_PATTERN.findAll(text).map { clean(it.value) }.toList()
        if (direct.isNotEmpty()) return direct
        val decoded = base64(text) ?: return emptyList()
        return URI_PATTERN.findAll(decoded).map { clean(it.value) }.toList()
    }

    private fun clean(s: String) = s.trimEnd(',', ';', ')', ']', '}')

    fun parse(uri: String): ParsedConfig? = runCatching { parseInternal(uri.trim()) }.getOrNull()

    private fun parseInternal(uri: String): ParsedConfig? {
        val scheme = uri.substringBefore("://").lowercase()
        val protocol = when (scheme) {
            "hy2" -> "hysteria2"
            "socks5" -> "socks"
            else -> scheme
        }
        val rest = uri.substringAfter("://")
        val name = decode(rest.substringAfter('#', ""))
        val body = rest.substringBefore('#')
        if (protocol == "vmess") return parseVmess(uri, body, name)

        val query = parseQuery(body.substringAfter('?', ""))
        val authority = body.substringBefore('?').substringBefore('/')
        var userInfo = ""
        var hostPort = authority
        if (authority.contains('@')) {
            userInfo = authority.substringBeforeLast('@')
            hostPort = authority.substringAfterLast('@')
        } else if (protocol == "ss") {
            // قالب قدیمی: base64(method:pass@host:port)
            val dec = base64(authority) ?: return null
            if (!dec.contains('@')) return null
            userInfo = dec.substringBeforeLast('@')
            hostPort = dec.substringAfterLast('@')
        }
        val host = hostPort.substringBeforeLast(':').trim('[', ']')
        val port = Regex("^\\d{1,5}").find(hostPort.substringAfterLast(':'))?.value?.toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null

        val security = when {
            protocol == "hysteria2" || protocol == "tuic" -> "quic"
            protocol == "trojan" && query["security"].isNullOrBlank() -> "tls"
            else -> (query["security"] ?: "none").lowercase()
        }
        return ParsedConfig(
            uri = uri,
            protocol = protocol,
            host = host,
            port = port,
            name = name,
            security = security,
            transport = (query["type"] ?: "tcp").lowercase(),
            sni = query["sni"] ?: query["peer"] ?: host,
            key = "$protocol|${host.lowercase()}|$port|$userInfo",
            flagIso = flagIso(name),
        )
    }

    private fun parseVmess(uri: String, body: String, name: String): ParsedConfig? {
        val json = JSONObject(base64(body) ?: return null)
        val host = json.optString("add").trim()
        val port = json.opt("port")?.toString()?.toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        val ps = json.optString("ps").ifBlank { name }
        val tls = json.optString("tls").lowercase()
        val net = json.optString("net").ifBlank { "tcp" }.lowercase()
        val sni = json.optString("sni").ifBlank { json.optString("host") }.ifBlank { host }
        return ParsedConfig(
            uri = uri,
            protocol = "vmess",
            host = host,
            port = port,
            name = ps,
            security = if (tls == "tls") "tls" else "none",
            transport = net,
            sni = sni,
            key = "vmess|${host.lowercase()}|$port|${json.optString("id")}",
            flagIso = flagIso(ps),
        )
    }

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isBlank()) return emptyMap()
        val map = HashMap<String, String>()
        for (pair in q.split('&')) {
            val k = pair.substringBefore('=', "")
            if (k.isBlank()) continue
            map[k.lowercase()] = decode(pair.substringAfter('=', ""))
        }
        return map
    }

    private fun decode(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun base64(s: String): String? {
        val compact = s.filterNot { it.isWhitespace() }
        if (compact.length < 8 || compact.length > 4_000_000) return null
        return runCatching { String(Base64.decode(compact, Base64.DEFAULT), Charsets.UTF_8) }
            .recoverCatching { String(Base64.decode(compact, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8) }
            .getOrNull()
    }

    /** پرچم ایموجی (دو حرف منطقه‌ای) را به کد ISO تبدیل می‌کند. */
    fun flagIso(name: String): String? {
        var i = 0
        while (i < name.length) {
            val cp = name.codePointAt(i)
            val n = Character.charCount(cp)
            if (cp in 0x1F1E6..0x1F1FF && i + n < name.length) {
                val cp2 = name.codePointAt(i + n)
                if (cp2 in 0x1F1E6..0x1F1FF) {
                    return buildString {
                        append('A' + (cp - 0x1F1E6))
                        append('A' + (cp2 - 0x1F1E6))
                    }
                }
            }
            i += n
        }
        return null
    }

    /** کد دو حرفی کشور → ایموجی پرچم. */
    fun flagEmoji(iso: String?): String {
        if (iso == null || iso.length != 2) return "🌐"
        val a = iso.uppercase()
        if (a[0] !in 'A'..'Z' || a[1] !in 'A'..'Z') return "🌐"
        return String(Character.toChars(0x1F1E6 + (a[0] - 'A'))) +
            String(Character.toChars(0x1F1E6 + (a[1] - 'A')))
    }
}
