package com.aras.client.ui.radar

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class RadarPhase { IDLE, FETCHING, TESTING, DONE, STOPPED, NO_SOURCES, NONE_SELECTED, ERROR }

/**
 * وضعیت صفحه‌ی رادار کانفیگ. با چرخش صفحه از بین نمی‌رود و انتخاب‌های کاربر در
 * SharedPreferences ذخیره می‌شوند. نتیجه‌ها هر ~۲۰۰ms یک‌جا به UI می‌رسند تا لیست روان بماند.
 */
internal class RadarViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("radar_cfg", Context.MODE_PRIVATE)
    private val engine = RadarEngine()
    private var job: Job? = null

    var phase by mutableStateOf(RadarPhase.IDLE); private set
    var errorText by mutableStateOf(""); private set
    var planned by mutableIntStateOf(0); private set
    var rawFound by mutableIntStateOf(0); private set
    var sourcesTotal by mutableIntStateOf(0); private set
    val results = mutableStateListOf<RadarResult>()
    val sourceStates = mutableStateMapOf<String, Pair<SourceState, Int>>()

    val scanning: Boolean get() = phase == RadarPhase.FETCHING || phase == RadarPhase.TESTING

    // ── تنظیمات ماندگار ──
    var groups by mutableStateOf(loadGroups()); private set
    var limit by mutableIntStateOf(prefs.getInt(KEY_LIMIT, 400)); private set
    var geo by mutableStateOf(prefs.getBoolean(KEY_GEO, true)); private set
    val customLinks = mutableStateListOf<String>().also {
        it.addAll(prefs.getString(KEY_CUSTOM, "").orEmpty().split('\n').filter { l -> l.isNotBlank() })
    }

    fun toggleGroup(g: RadarGroup) {
        groups = if (g in groups) groups - g else groups + g
        prefs.edit().putStringSet(KEY_GROUPS, groups.map { it.key }.toSet()).apply()
    }

    fun setLimit(v: Int) {
        limit = v
        prefs.edit().putInt(KEY_LIMIT, v).apply()
    }

    fun setGeo(v: Boolean) {
        geo = v
        prefs.edit().putBoolean(KEY_GEO, v).apply()
    }

    fun addCustom(link: String): Boolean {
        val url = link.trim()
        if (!(url.startsWith("https://") || url.startsWith("http://")) || url in customLinks) return false
        customLinks += url
        saveCustom()
        if (RadarGroup.CUSTOM !in groups) toggleGroup(RadarGroup.CUSTOM)
        return true
    }

    fun removeCustom(link: String) {
        customLinks -= link
        saveCustom()
    }

    private fun saveCustom() {
        prefs.edit().putString(KEY_CUSTOM, customLinks.joinToString("\n")).apply()
    }

    private fun loadGroups(): Set<RadarGroup> {
        val saved = prefs.getStringSet(KEY_GROUPS, null)
            ?: return setOf(RadarGroup.COUNTRY, RadarGroup.ANTI_DPI, RadarGroup.FAST, RadarGroup.TOP)
        return RadarGroup.entries.filter { it.key in saved }.toSet()
    }

    // ── اسکن ──
    fun start() {
        if (scanning) return
        val sources = RadarSources.forGroups(groups, customLinks)
        results.clear()
        sourceStates.clear()
        planned = 0
        rawFound = 0
        sourcesTotal = sources.size
        if (sources.isEmpty()) {
            phase = RadarPhase.NONE_SELECTED
            return
        }
        phase = RadarPhase.FETCHING
        job = viewModelScope.launch {
            val pending = ArrayList<RadarResult>()
            var lastFlush = System.currentTimeMillis()
            fun flush() {
                if (pending.isEmpty()) return
                results.addAll(pending)
                pending.clear()
                lastFlush = System.currentTimeMillis()
            }
            try {
                engine.scan(sources, limit, geo).collect { ev ->
                    when (ev) {
                        is RadarEvent.Source -> sourceStates[ev.id] = ev.state to ev.count
                        is RadarEvent.Planned -> {
                            planned = ev.total
                            rawFound = ev.raw
                            phase = if (ev.total == 0) RadarPhase.NO_SOURCES else RadarPhase.TESTING
                        }
                        is RadarEvent.Result -> {
                            pending += ev.value
                            if (System.currentTimeMillis() - lastFlush >= FLUSH_MS) flush()
                        }
                    }
                }
                flush()
                if (phase == RadarPhase.TESTING) phase = RadarPhase.DONE
            } catch (e: CancellationException) {
                flush()
                phase = RadarPhase.STOPPED
                throw e
            } catch (e: Exception) {
                flush()
                errorText = e.message ?: "network"
                phase = RadarPhase.ERROR
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    override fun onCleared() {
        job?.cancel()
    }

    private companion object {
        const val KEY_GROUPS = "groups"
        const val KEY_LIMIT = "limit"
        const val KEY_GEO = "geo"
        const val KEY_CUSTOM = "custom"
        const val FLUSH_MS = 200L
    }
}
