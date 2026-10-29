package com.aras.client.ui.rdrn

import java.util.Locale

/** Pure helpers (no Android dependencies) so they can be unit-tested on the JVM. */
object RnUtil {

    /** 🇩🇪 → "DE". Returns "" for anything that is not exactly two regional-indicator symbols. */
    fun isoFromFlag(flag: String): String {
        val cps = flag.codePoints().toArray()
        if (cps.size != 2) return ""
        val base = 0x1F1E6
        if (cps.any { it < base || it > base + 25 }) return ""
        return String(charArrayOf('A' + (cps[0] - base), 'A' + (cps[1] - base)))
    }

    private val asia = setOf(
        "JP", "CN", "KR", "KP", "IN", "SG", "HK", "TW", "TH", "VN", "MY", "ID", "PH", "PK", "BD", "LK", "NP", "KZ", "UZ",
        "AE", "SA", "QA", "KW", "BH", "OM", "IL", "JO", "LB", "IQ", "IR", "AM", "AZ", "GE", "AF", "MN", "KH", "LA", "MM",
    )
    private val europe = setOf(
        "DE", "FR", "GB", "NL", "BE", "LU", "CH", "AT", "IT", "ES", "PT", "IE", "SE", "NO", "DK", "FI", "IS", "PL", "CZ",
        "SK", "HU", "RO", "BG", "GR", "TR", "UA", "RU", "BY", "LT", "LV", "EE", "RS", "HR", "SI", "BA", "MD", "AL", "MK", "CY", "MT",
    )
    private val america = setOf(
        "US", "CA", "MX", "BR", "AR", "CL", "CO", "PE", "VE", "EC", "UY", "PY", "BO", "CR", "PA", "GT", "DO", "CU", "PR", "JM",
    )

    /** "Asia", "Europe", "America" or "" (Africa/Oceania/unknown only appear under "All"). */
    fun regionOf(iso: String): String = when (iso.uppercase(Locale.ROOT)) {
        in asia -> "Asia"
        in europe -> "Europe"
        in america -> "America"
        else -> ""
    }

    fun countryName(iso: String): String =
        if (iso.length == 2) Locale("", iso).getDisplayCountry(Locale.ENGLISH).ifBlank { iso } else "Other"

    /** MCC+MNC (e.g. "43211") → the operator's name as Iranians know it. Null when unknown. */
    fun iranOperator(mccMnc: String?): String? = when (mccMnc) {
        "43211" -> "Hamrah-e Avval (MCI)"
        "43235" -> "Irancell (MTN)"
        "43220" -> "Rightel"
        "43232" -> "Taliya"
        else -> null
    }

    /** android.telephony.TelephonyManager.NETWORK_TYPE_* → "5G NR", "4G LTE" … */
    fun generation(networkType: Int): String = when (networkType) {
        20 -> "5G NR"
        13 -> "4G LTE"
        19 -> "4G LTE+"
        15, 8, 9, 10, 17 -> "3G"
        3, 5, 6, 12, 14 -> "3G"
        1, 2, 4, 7, 11, 16 -> "2G"
        else -> "Mobile"
    }

    fun shortGeneration(g: String): String = when {
        g.startsWith("5G") -> "5G"
        g.startsWith("4G") -> "4G"
        g.startsWith("3G") -> "3G"
        g.startsWith("2G") -> "2G"
        else -> "LTE"
    }

    /** RSSI in dBm → 0..4 bars. */
    fun wifiLevel(rssi: Int): Int = when {
        rssi >= -55 -> 4
        rssi >= -65 -> 3
        rssi >= -75 -> 2
        rssi > -95 -> 1
        else -> 0
    }

    fun band(frequencyMhz: Int): String = when {
        frequencyMhz >= 5925 -> "6 GHz"
        frequencyMhz >= 4900 -> "5 GHz"
        frequencyMhz > 0 -> "2.4 GHz"
        else -> "—"
    }

    fun mmss(totalSeconds: Int): String = String.format(Locale.US, "%02d:%02d", (totalSeconds / 60) % 100, totalSeconds % 60)

    /** Short protocol label used on chips and rows. */
    fun protocolLabel(configTypeName: String): String = when (configTypeName.uppercase(Locale.ROOT)) {
        "VMESS" -> "VMess"
        "VLESS" -> "VLESS"
        "SHADOWSOCKS" -> "Shadowsocks"
        "TROJAN" -> "Trojan"
        "WIREGUARD" -> "WireGuard"
        "HYSTERIA2" -> "Hysteria2"
        "ANYTLS" -> "AnyTLS"
        "AMNEZIAWG" -> "AmneziaWG"
        else -> configTypeName.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
    }

    /** "Fast" under 250 ms, "Stable" under 700 ms, then "Slow"; "Test" when never measured. */
    fun pingBadge(ms: Int?): String = when {
        ms == null -> "Test"
        ms < 0 -> "Down"
        ms < 250 -> "Fast"
        ms < 700 -> "Stable"
        else -> "Slow"
    }
}
