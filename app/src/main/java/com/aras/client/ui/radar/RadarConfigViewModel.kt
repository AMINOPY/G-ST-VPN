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
import com.aras.client.radar.RadarEvent
import com.aras.client.radar.RadarGroup
import com.aras.client.radar.RadarResult
import com.aras.client.radar.RadarScanner
import com.aras.client.radar.RadarSource
import com.aras.client.radar.RadarSources
import com.aras.client.radar.RadarStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

enum class RadarSort(val title: String) { SCORE("امتیاز"), PING("پینگ"), NAME("نام") }

class RadarConfigViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("radar_config", Context.MODE_PRIVATE)

    val groups: List<RadarGroup> = RadarSources.groups
    val selectedGroups = mutableStateListOf<String>().apply {
        addAll(prefs.getStringSet("groups", null) ?: RadarSources.defaultGroupIds())
    }
    var limit by mutableIntStateOf(prefs.getInt("limit", 300))
    var deep by mutableStateOf(prefs.getBoolean("deep", true))
    var customUrl by mutableStateOf(prefs.getString("custom", "") ?: "")

    var scanning by mutableStateOf(false)
        private set
    var message by mutableStateOf("منابع را انتخاب کن و اسکن را شروع کن.")
        private set
    var checked by mutableIntStateOf(0)
        private set
    var planned by mutableIntStateOf(0)
        private set
    var clearTick by mutableIntStateOf(0)
        private set

    val results = mutableStateListOf<RadarResult>()
    /** شناسه‌ی منبع → تعداد کانفیگ یا متن خطا */
    val sourceReports = mutableStateMapOf<String, String>()
    val blips = MutableSharedFlow<Long>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    var statusFilter by mutableStateOf("all")
    var protocolFilter by mutableStateOf("all")
    var countryFilter by mutableStateOf("all")
    var query by mutableStateOf("")
    var sort by mutableStateOf(RadarSort.SCORE)

    private var job: Job? = null

    fun toggleGroup(id: String) {
        if (id in selectedGroups) selectedGroups.remove(id) else selectedGroups.add(id)
    }

    fun selectAllGroups(select: Boolean) {
        selectedGroups.clear()
        if (select) selectedGroups.addAll(groups.map { it.id })
    }

    private fun persist() {
        prefs.edit()
            .putStringSet("groups", selectedGroups.toSet())
            .putInt("limit", limit)
            .putBoolean("deep", deep)
            .putString("custom", customUrl)
            .apply()
    }

    fun startScan() {
        if (scanning) return
        val sources = buildList<RadarSource> {
            groups.filter { it.id in selectedGroups }.forEach { addAll(it.sources) }
            if (customUrl.isNotBlank()) add(RadarSource("custom", "لینک سفارشی", customUrl.trim()))
        }
        if (sources.isEmpty()) {
            message = "حداقل یک منبع انتخاب کن."
            return
        }
        persist()
        results.clear()
        sourceReports.clear()
        checked = 0
        planned = 0
        clearTick++
        statusFilter = "all"
        protocolFilter = "all"
        countryFilter = "all"
        scanning = true
        message = "در حال دریافت ${sources.size} منبع…"
        job = viewModelScope.launch {
            try {
                RadarScanner.scan(sources, limit, deep).collect { ev ->
                    when (ev) {
                        is RadarEvent.SourceDone -> {
                            sourceReports[ev.source.id] = if (ev.error == null) "${ev.count}" else "✗ ${ev.error}"
                        }
                        is RadarEvent.Planned -> {
                            planned = ev.unique
                            message = if (ev.unique == 0) {
                                "هیچ کانفیگ قابل‌خواندنی پیدا نشد؛ اینترنت یا دسترسی به گیت‌هاب را بررسی کن."
                            } else {
                                "${ev.raw} لینک خام · ${ev.unique} کانفیگ یکتا برای آزمایش"
                            }
                        }
                        is RadarEvent.Result -> {
                            results.add(ev.result)
                            checked++
                            val r = ev.result
                            blips.tryEmit(
                                when (r.status) {
                                    RadarStatus.FAILED -> -1L
                                    RadarStatus.UDP -> 250L
                                    else -> r.tcpMs ?: 250L
                                },
                            )
                        }
                    }
                }
                val good = results.count { it.status == RadarStatus.OK || it.status == RadarStatus.SLOW }
                message = if (planned == 0) message else
                    "پایان: $checked بررسی‌شده · $good پاسخ‌گو. اتصال واقعی هنوز باید داخل برنامه تست شود."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "خطا در اسکن: " + (e.message ?: "شبکه")
            } finally {
                scanning = false
            }
        }
    }

    fun stopScan() {
        job?.cancel()
        scanning = false
        message = "متوقف شد؛ $checked کانفیگ بررسی شد."
    }

    /** بهترین‌های پاسخ‌گو، بر اساس امتیاز. */
    fun topUris(count: Int): List<String> = results
        .filter { it.status == RadarStatus.OK || it.status == RadarStatus.SLOW }
        .sortedByDescending { it.score }
        .take(count)
        .map { it.cfg.uri }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
