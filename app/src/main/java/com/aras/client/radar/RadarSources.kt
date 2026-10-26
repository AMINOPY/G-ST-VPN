package com.aras.client.radar

import java.net.URLEncoder

/** دسته‌بندی منابع در رابط. */
enum class RadarCategory(val title: String) {
    COUNTRY("کشورها"),
    SPEED("سریع و ضد DPI"),
    TOP("برترین‌ها"),
    DISCOVER("کشف خودکار"),
}

/**
 * یک منبع عمومی کانفیگ. یا [url] (فایل متنی/ساب) دارد یا [repo] (کشف خودکار فایل‌ها در ریپوی گیت‌هاب).
 * [ordered] یعنی فایل از قبل بهترین‌ها را اول گذاشته و نباید بُر بخورد.
 */
data class RadarSource(
    val id: String,
    val title: String,
    val url: String = "",
    val repo: String = "",
    val country: String? = null,
    val ordered: Boolean = false,
)

/** گروهی از منابع که با یک چیپ روشن/خاموش می‌شود. */
data class RadarGroup(
    val id: String,
    val title: String,
    val emoji: String,
    val category: RadarCategory,
    val sources: List<RadarSource>,
    val defaultOn: Boolean = false,
)

object RadarSources {
    private const val MILAD =
        "https://raw.githubusercontent.com/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/"
    private const val SOLI = "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/"
    private const val DELTA =
        "https://raw.githubusercontent.com/Delta-Kronecker/V2ray-Config/main/config/countries/"
    private const val DANIAL = "https://raw.githubusercontent.com/Danialsamadi/v2go/main/Splitted-By-Country/"
    private const val MORPH = "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/"

    private fun enc(name: String) = URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    private fun country(id: String, title: String, emoji: String, iso: String, fileName: String): RadarGroup {
        val file = enc(fileName)
        return RadarGroup(
            id = id,
            title = title,
            emoji = emoji,
            category = RadarCategory.COUNTRY,
            sources = listOf(
                RadarSource("$id-milad", "miladtahanian · $title", MILAD + file + ".txt", country = iso),
                RadarSource("$id-soli", "SoliSpirit · $title", SOLI + file + ".txt", country = iso),
                RadarSource("$id-delta", "Delta-Kronecker · $title", DELTA + iso.lowercase() + ".txt", country = iso),
                RadarSource("$id-danial", "Danialsamadi · $title", DANIAL + iso.uppercase() + ".txt", country = iso),
            ),
        )
    }

    val groups: List<RadarGroup> = listOf(
        country("us", "آمریکا", "🇺🇸", "US", "United_States"),
        country("de", "آلمان", "🇩🇪", "DE", "Germany"),
        country("tr", "ترکیه", "🇹🇷", "TR", "Türkiye"),
        country("kr", "کره جنوبی", "🇰🇷", "KR", "South_Korea"),
        country("ru", "روسیه", "🇷🇺", "RU", "Russia"),

        RadarGroup(
            "reality", "Reality", "🛡️", RadarCategory.SPEED,
            listOf(RadarSource("reality", "morpheusadam · Reality", MORPH + "reality.txt")),
            defaultOn = true,
        ),
        RadarGroup(
            "best", "بهترین‌ها", "⭐", RadarCategory.SPEED,
            listOf(RadarSource("best", "morpheusadam · Best", MORPH + "best.txt")),
            defaultOn = true,
        ),
        RadarGroup(
            "iran", "ضد فیلتر ایران", "🇮🇷", RadarCategory.SPEED,
            listOf(RadarSource("iran", "morpheusadam · Iran", MORPH + "iran.txt")),
            defaultOn = true,
        ),
        RadarGroup(
            "hy2", "Hysteria2", "🚀", RadarCategory.SPEED,
            listOf(
                RadarSource(
                    "hy2", "MatinGhanbari · Hysteria2",
                    "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/main/subscriptions/filtered/subs/hysteria2.txt",
                ),
            ),
            defaultOn = true,
        ),
        RadarGroup(
            "fast", "Fast", "⚡", RadarCategory.SPEED,
            listOf(
                RadarSource(
                    "fast", "0xRadikal · Fast",
                    "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/fast/configs.txt",
                ),
            ),
            defaultOn = true,
        ),
        RadarGroup(
            "verified", "Verified", "✅", RadarCategory.SPEED,
            listOf(
                RadarSource(
                    "verified", "0xRadikal · Verified",
                    "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt",
                ),
            ),
            defaultOn = true,
        ),

        RadarGroup(
            "top5", "Top 5", "🏆", RadarCategory.TOP,
            listOf(
                RadarSource(
                    "top5", "PulseConfigs · Top5",
                    "https://raw.githubusercontent.com/drmikecrypto/PulseConfigs/main/top5.txt", ordered = true,
                ),
                RadarSource(
                    "top5speed", "PulseConfigs · Top5 Speed",
                    "https://raw.githubusercontent.com/drmikecrypto/PulseConfigs/main/top5_speed.txt", ordered = true,
                ),
            ),
            defaultOn = true,
        ),
        RadarGroup(
            "top100", "Top 100", "🥇", RadarCategory.TOP,
            listOf(
                RadarSource(
                    "top100", "FastNodes · Top100",
                    "https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/Best-Results/top100.txt", ordered = true,
                ),
            ),
            defaultOn = true,
        ),
        RadarGroup(
            "top500", "Top 500", "🥈", RadarCategory.TOP,
            listOf(
                RadarSource(
                    "top500", "FastNodes · Top500",
                    "https://raw.githubusercontent.com/rtwo2/FastNodes/main/sub/Best-Results/top500.txt", ordered = true,
                ),
            ),
        ),

        RadarGroup(
            "github", "جست‌وجوی ریپوها", "🔎", RadarCategory.DISCOVER,
            listOf(
                RadarSource("gh-barry", "barry-far", repo = "barry-far/V2ray-Config"),
                RadarSource("gh-0xr", "0xRadikal", repo = "0xRadikal/Free-v2ray-Configs"),
                RadarSource("gh-ircf", "ircfspace", repo = "ircfspace/tconfig"),
                RadarSource("gh-argh", "Argh94", repo = "Argh94/V2RayAutoConfig"),
            ),
        ),
    )

    fun defaultGroupIds(): Set<String> = groups.filter { it.defaultOn }.map { it.id }.toSet()
}
