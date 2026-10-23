package com.aras.client.ui.calculator

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CurrencyRate(val rate: Double, val date: String, val cached: Boolean)

internal object CurrencyRatesRepository {
    private const val PREFS = "radar_currency_rates"

    suspend fun rate(context: Context, base: String, quote: String): CurrencyRate =
        withContext(Dispatchers.IO) {
            val from = base.trim().uppercase()
            val to = quote.trim().uppercase()
            require(from.matches(Regex("[A-Z]{3}")) && to.matches(Regex("[A-Z]{3}"))) {
                "Use three-letter ISO currency codes"
            }
            if (from == to) return@withContext CurrencyRate(1.0, LocalDate.now().toString(), false)

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = from + "_" + to
            try {
                val connection = URL("https://api.frankfurter.dev/v2/rate/" +
                    from.lowercase() + "/" + to.lowercase()).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 7000
                connection.readTimeout = 7000
                connection.setRequestProperty("Accept", "application/json")
                try {
                    require(connection.responseCode in 200..299) { "Rate source returned HTTP " + connection.responseCode }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val value = json.getDouble("rate")
                    val date = json.optString("date", LocalDate.now().toString())
                    require(value.isFinite() && value > 0.0) { "Invalid exchange rate" }
                    prefs.edit().putString(key + "_rate", value.toString()).putString(key + "_date", date).apply()
                    CurrencyRate(value, date, false)
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                val cachedRate = prefs.getString(key + "_rate", null)?.toDoubleOrNull()
                    ?: throw IllegalStateException("No network and no cached rate for " + from + "/" + to, error)
                val cachedDate = prefs.getString(key + "_date", "unknown date") ?: "unknown date"
                CurrencyRate(cachedRate, cachedDate, true)
            }
        }
}
