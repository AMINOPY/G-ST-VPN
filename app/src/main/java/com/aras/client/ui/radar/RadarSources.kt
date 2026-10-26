package com.aras.client.ui.radar

import androidx.annotation.StringRes
import com.aras.client.R

/** گروه‌های منبع؛ کاربر هر گروه را جداگانه روشن/خاموش می‌کند. */
internal enum class RadarGroup(val key: String, @StringRes val label: Int) {
    COUNTRY("country", R.string.radar_group_country),
    ANTI_DPI("antidpi", R.string.radar_group_antidpi),
    HYSTERIA("hysteria", R.string.radar_group_hysteria),
    FAST("fast", R.string.radar_group_fast),
    TOP("top", R.string.radar_group_top),
    CUSTOM("custom", R.string.radar_group_custom),
}

/**
 * یک خوراک (subscription) عمومی. [countryCode] اگر معلوم باشد، کشور بدون GeoIP هم مشخص است.
 * [name] نام ریپو/لیست است که روی هر کارت نشان داده می‌شود.
 */
internal data class RadarSource(
    val id: String,
    val group: RadarGroup,
    val name: String,
    val url: String,
    val countryCode: String? = null,
)

internal object RadarSources {
    private const val GH = "https://raw.githubusercontent.com/"

    /** کشورهای دارای فید اختصاصی: (کد ISO، نام فایل در miladtahanian/SoliSpirit) */
    private val countries = listOf(
        "US" to "United_States",
        "DE" to "Germany",
        "TR" to "Türkiye",
        "KR" to "South_Korea",
        "RU" to "Russia",
    )

    val builtIn: List<RadarSource> = buildList {
        for ((iso, file) in countries) {
            add(
                RadarSource(
                    "milad-$iso", RadarGroup.COUNTRY, "V2RayScrapeByCountry · $iso",
                    GH + "miladtahanian/V2RayScrapeByCountry/refs/heads/main/Countries/$file.txt", iso,
                ),
            )
            add(
                RadarSource(
                    "soli-$iso", RadarGroup.COUNTRY, "SoliSpirit · $iso",
                    GH + "SoliSpirit/v2ray-configs/main/Countries/$file.txt", iso,
                ),
            )
            add(
                RadarSource(
                    "delta-$iso", RadarGroup.COUNTRY, "Delta-Kronecker · $iso",
                    GH + "Delta-Kronecker/V2ray-Config/main/config/countries/${iso.lowercase()}.txt", iso,
                ),
            )
            add(
                RadarSource(
                    "v2go-$iso", RadarGroup.COUNTRY, "v2go · $iso",
                    GH + "Danialsamadi/v2go/main/Splitted-By-Country/$iso.txt", iso,
                ),
            )
        }
        add(RadarSource("morph-reality", RadarGroup.ANTI_DPI, "morpheusadam · Reality", GH + "morpheusadam/v2ray-config/main/subs/bundles/reality.txt"))
        add(RadarSource("morph-iran", RadarGroup.ANTI_DPI, "morpheusadam · Iran", GH + "morpheusadam/v2ray-config/main/subs/bundles/iran.txt"))
        add(RadarSource("morph-best", RadarGroup.ANTI_DPI, "morpheusadam · Best", GH + "morpheusadam/v2ray-config/main/subs/bundles/best.txt"))
        add(RadarSource("matin-hy2", RadarGroup.HYSTERIA, "MatinGhanbari · Hysteria2", GH + "MatinGhanbari/v2ray-configs/main/subscriptions/filtered/subs/hysteria2.txt"))
        add(RadarSource("radikal-fast", RadarGroup.FAST, "0xRadikal · Fast", GH + "0xRadikal/Free-v2ray-Configs/main/fast/configs.txt"))
        add(RadarSource("radikal-verified", RadarGroup.FAST, "0xRadikal · Verified", GH + "0xRadikal/Free-v2ray-Configs/main/verified/configs.txt"))
        add(RadarSource("pulse-top5", RadarGroup.TOP, "PulseConfigs · Top5", GH + "drmikecrypto/PulseConfigs/main/top5.txt"))
        add(RadarSource("pulse-top5-speed", RadarGroup.TOP, "PulseConfigs · Top5 Speed", GH + "drmikecrypto/PulseConfigs/main/top5_speed.txt"))
        add(RadarSource("fastnodes-100", RadarGroup.TOP, "FastNodes · Top100", GH + "rtwo2/FastNodes/main/sub/Best-Results/top100.txt"))
        add(RadarSource("fastnodes-500", RadarGroup.TOP, "FastNodes · Top500", GH + "rtwo2/FastNodes/main/sub/Best-Results/top500.txt"))
    }

    fun forGroups(groups: Set<RadarGroup>, custom: List<String>): List<RadarSource> {
        val base = builtIn.filter { it.group in groups }
        if (RadarGroup.CUSTOM !in groups) return base
        return base + custom.mapIndexed { i, url ->
            RadarSource("custom-$i", RadarGroup.CUSTOM, hostOf(url), url)
        }
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull().orEmpty().ifBlank { url.take(32) }
}
