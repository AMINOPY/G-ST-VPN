package com.aras.client.radar

/** گروه‌های منبع در «رادار کانفیگ». */
enum class RadarGroup(val label: String, val defaultOn: Boolean) {
    COUNTRY("کشوری", true),
    REALITY("Reality و ضد DPI", true),
    HYSTERIA("Hysteria2", false),
    FAST("سریع و تأییدشده", true),
    TOP("برترین‌ها", true),
    AUTO("کشف خودکار GitHub", false),
}

data class RadarSource(
    val name: String,
    val url: String,
    val group: RadarGroup,
    /** کد دو حرفی کشور برای منابع کشوری */
    val country: String? = null,
)

object RadarCountries {
    val list: List<Pair<String, String>> = listOf(
        "US" to "آمریکا",
        "DE" to "آلمان",
        "TR" to "ترکیه",
        "KR" to "کره جنوبی",
        "RU" to "روسیه",
    )

    fun flag(iso: String?): String {
        val code = iso?.uppercase().orEmpty()
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return "🌐"
        val base = 0x1F1E6
        return String(Character.toChars(base + (code[0] - 'A'))) + String(Character.toChars(base + (code[1] - 'A')))
    }
}

/** منابع عمومی؛ هر منبع مستقل از بقیه دریافت می‌شود و خرابی یکی روی بقیه اثر ندارد. */
object RadarSources {
    const val AUTO_URL = "auto:github"

    private const val RAW = "https://raw.githubusercontent.com"

    // نام فایل کشورها در دو مخزن اول (Türkiye با %-encoding)
    private val longNames = mapOf(
        "US" to "United_States",
        "DE" to "Germany",
        "TR" to "T%C3%BCrkiye",
        "KR" to "South_Korea",
        "RU" to "Russia",
    )

    val all: List<RadarSource> = buildList {
        for ((iso, _) in RadarCountries.list) {
            val long = longNames.getValue(iso)
            val low = iso.lowercase()
            add(RadarSource("miladtahanian · $iso", "$RAW/miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/$long.txt", RadarGroup.COUNTRY, iso))
            add(RadarSource("SoliSpirit · $iso", "$RAW/SoliSpirit/v2ray-configs/main/Countries/$long.txt", RadarGroup.COUNTRY, iso))
            add(RadarSource("Delta-Kronecker · $iso", "$RAW/Delta-Kronecker/V2ray-Config/main/config/countries/$low.txt", RadarGroup.COUNTRY, iso))
            add(RadarSource("Danialsamadi · $iso", "$RAW/Danialsamadi/v2go/main/Splitted-By-Country/$iso.txt", RadarGroup.COUNTRY, iso))
        }
        add(RadarSource("morpheusadam · Reality", "$RAW/morpheusadam/v2ray-config/main/subs/bundles/reality.txt", RadarGroup.REALITY))
        add(RadarSource("morpheusadam · Best", "$RAW/morpheusadam/v2ray-config/main/subs/bundles/best.txt", RadarGroup.REALITY))
        add(RadarSource("morpheusadam · Iran", "$RAW/morpheusadam/v2ray-config/main/subs/bundles/iran.txt", RadarGroup.REALITY))
        add(RadarSource("MatinGhanbari · Hysteria2", "$RAW/MatinGhanbari/v2ray-configs/main/subscriptions/filtered/subs/hysteria2.txt", RadarGroup.HYSTERIA))
        add(RadarSource("0xRadikal · Fast", "$RAW/0xRadikal/Free-v2ray-Configs/main/fast/configs.txt", RadarGroup.FAST))
        add(RadarSource("0xRadikal · Verified", "$RAW/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt", RadarGroup.FAST))
        add(RadarSource("PulseConfigs · Top5", "$RAW/drmikecrypto/PulseConfigs/main/top5.txt", RadarGroup.TOP))
        add(RadarSource("PulseConfigs · Top5 Speed", "$RAW/drmikecrypto/PulseConfigs/main/top5_speed.txt", RadarGroup.TOP))
        add(RadarSource("FastNodes · Top100", "$RAW/rtwo2/FastNodes/main/sub/Best-Results/top100.txt", RadarGroup.TOP))
        add(RadarSource("FastNodes · Top500", "$RAW/rtwo2/FastNodes/main/sub/Best-Results/top500.txt", RadarGroup.TOP))
        add(RadarSource("GitHub · کشف خودکار", AUTO_URL, RadarGroup.AUTO))
    }
}
