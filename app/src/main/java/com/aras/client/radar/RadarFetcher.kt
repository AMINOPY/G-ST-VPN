package com.aras.client.radar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** دریافت منابع عمومی؛ اگر raw.githubusercontent در دسترس نبود، آینه‌ی jsDelivr امتحان می‌شود. */
object RadarFetcher {
    data class Fetched(val uris: List<String>, val error: String?)

    private val RAW = Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/(?:refs/heads/)?([^/]+)/(.+)$")

    private fun mirror(url: String): String? {
        val m = RAW.find(url) ?: return null
        val (user, repo, branch, path) = m.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$branch/$path"
    }

    suspend fun fetch(src: RadarSource): Fetched = withContext(Dispatchers.IO) {
        if (src.repo.isNotBlank()) return@withContext discover(src.repo)
        val urls = listOfNotNull(src.url, mirror(src.url))
        var error: String? = null
        for (u in urls) {
            try {
                val uris = RadarParser.extract(get(u, 9000))
                if (uris.isNotEmpty()) return@withContext Fetched(uris, null)
                error = "فایل خالی است یا کانفیگی ندارد"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message?.take(70) ?: "خطای شبکه"
            }
        }
        Fetched(emptyList(), error)
    }

    /** کشف خودکار فایل‌های کانفیگ در یک ریپوی گیت‌هاب (حداکثر ۱۰ فایل). */
    private fun discover(repo: String): Fetched {
        val found = LinkedHashSet<String>()
        var error: String? = null
        try {
            val meta = get("https://api.github.com/repos/$repo", 10000)
            val branch = Regex("\"default_branch\"\\s*:\\s*\"([^\"]+)\"").find(meta)?.groupValues?.get(1) ?: "main"
            val tree = get("https://api.github.com/repos/$repo/git/trees/$branch?recursive=1", 10000)
            val paths = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"").findAll(tree).map { it.groupValues[1] }
                .filter { p ->
                    val q = p.lowercase()
                    (q.endsWith(".txt") || q.endsWith(".yaml") || q.endsWith(".yml") || q.endsWith(".json")) &&
                        (q.contains("config") || q.contains("sub") || q.contains("all") || q.contains("country"))
                }
                .take(10)
                .toList()
            for (path in paths) {
                val encoded = path.split("/").joinToString("/") {
                    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
                val raw = runCatching { get("https://raw.githubusercontent.com/$repo/$branch/$encoded", 8000) }
                    .getOrNull() ?: continue
                found.addAll(RadarParser.extract(raw))
                if (found.size >= 2000) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message?.take(70) ?: "خطای شبکه"
        }
        return Fetched(found.toList(), if (found.isEmpty()) (error ?: "چیزی پیدا نشد") else null)
    }

    private fun get(url: String, timeoutMs: Int): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "RADAR-VPN-Radar/2.0")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { r ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                    if (sb.length > 3_000_000) break
                }
                sb.toString()
            }
        } finally {
            c.disconnect()
        }
    }
}
