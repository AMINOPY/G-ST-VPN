package com.aras.client.ui.radar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aras.client.radar.RadarCountries
import com.aras.client.radar.RadarEngine
import com.aras.client.radar.RadarEvent
import com.aras.client.radar.RadarGroup
import com.aras.client.radar.RadarResult
import com.aras.client.radar.RadarSources
import com.aras.client.radar.RadarStatus
import com.aras.client.radar.ui.RadarScanView
import com.aras.client.ui.compose.AppTheme
import com.aras.client.ui.compose.AppTopBar
import com.aras.client.ui.main.MainActivity
import com.aras.client.ui.rdr.LocalRdrActive
import com.aras.client.ui.rdr.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class SourceStat(val count: Int, val error: String?, val fromCache: Boolean)

private val Green = Color(0xFF30D158)
private val Amber = Color(0xFFFFB800)
private val Red = Color(0xFFFF453A)
private val Blue = Color(0xFF0A84FF)

/**
 * رادار کانفیگ: جست‌وجو در منابع عمومی (کشوری، Reality/ضد DPI، Hysteria2، سریع، برترین‌ها)،
 * تست هم‌زمانِ DNS ← TCP ← TLS، امتیازدهی و افزودن تکی یا دسته‌ای به برنامه برای تست واقعی.
 * هیچ کانفیگی خودکار نصب یا متصل نمی‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
class RadarConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                RadarScreen(
                    onClose = { finish() },
                    onAdd = { uris ->
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            action = "com.aras.client.action.IMPORT_RADAR_CONFIG"
                            putExtra("radar_config", uris.take(200).joinToString("\n"))
                        })
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun RadarScreen(onClose: () -> Unit, onAdd: (List<String>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf(RadarGroup.entries.filter { it.defaultOn }.toSet()) }
    var countries by remember { mutableStateOf(RadarCountries.list.map { it.first }.toSet()) }
    var depth by remember { mutableIntStateOf(400) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("منابع را انتخاب کن و اسکن را شروع کن.") }
    var checked by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var uniqueFound by remember { mutableIntStateOf(0) }
    var okOnly by remember { mutableStateOf(true) }
    var protocolFilter by remember { mutableStateOf("all") }
    var sortMode by remember { mutableStateOf("score") }
    var showSources by remember { mutableStateOf(false) }
    var radar by remember { mutableStateOf<RadarScanView?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    val results = remember { mutableStateListOf<RadarResult>() }
    val selected = remember { mutableStateListOf<String>() }
    val sourceStats = remember { mutableStateMapOf<String, SourceStat>() }

    fun start() {
        val sources = RadarSources.all.filter { it.group in groups && (it.country == null || it.country in countries) }
        if (sources.isEmpty()) {
            message = "حداقل یک گروه منبع را روشن کن."
            return
        }
        results.clear(); selected.clear(); sourceStats.clear(); radar?.clearBlips()
        checked = 0; total = 0; uniqueFound = 0
        scanning = true
        message = "در حال دریافت " + sources.size + " منبع…"
        job = scope.launch {
            try {
                RadarEngine.scan(context, sources, depth).collect { ev ->
                    when (ev) {
                        is RadarEvent.SourceDone -> sourceStats[ev.source.name] = SourceStat(ev.count, ev.error, ev.fromCache)
                        is RadarEvent.Planned -> {
                            total = ev.total; uniqueFound = ev.uniqueFound
                            message = if (ev.total == 0) "هیچ کانفیگی از منابع دریافت نشد؛ اینترنت یا دسترسی به GitHub را بررسی کن."
                            else "در حال تست " + ev.total + " کانفیگ یکتا…"
                        }
                        is RadarEvent.Result -> {
                            results.add(ev.result)
                            when (ev.result.status) {
                                RadarStatus.OK, RadarStatus.TLS_FAILED -> radar?.addTestedCandidate(ev.result.tcpMs ?: -1L)
                                RadarStatus.FAILED -> radar?.addTestedCandidate(-1L)
                                RadarStatus.UDP_UNVERIFIED -> Unit
                            }
                        }
                        is RadarEvent.Progress -> {
                            checked = ev.checked
                            message = "بررسی " + ev.checked + "/" + ev.total + " · سالم: " + results.count { it.status == RadarStatus.OK }
                        }
                    }
                }
                val ok = results.count { it.status == RadarStatus.OK }
                if (total > 0) message = "پایان: " + results.size + " کانفیگ تست شد · سالم: " + ok +
                    ". پاسخ TCP/TLS جای تست واقعی تونل را نمی‌گیرد؛ بعد از افزودن داخل برنامه تست کن."
            } catch (c: CancellationException) {
                message = "اسکن متوقف شد · " + results.size + " نتیجه"
            } catch (e: Exception) {
                message = "خطا در اسکن: " + (e.message ?: "شبکه")
            } finally {
                scanning = false
                radar?.stopScan()
            }
        }
    }

    fun stop() { job?.cancel() }

    val sizeKey = if (scanning) results.size / 8 else results.size
    val visible = remember(sizeKey, okOnly, protocolFilter, sortMode) {
        val filtered = results.filter { r ->
            (!okOnly || r.status == RadarStatus.OK || r.status == RadarStatus.UDP_UNVERIFIED) &&
                (protocolFilter == "all" || r.endpoint?.protocol == protocolFilter)
        }
        if (sortMode == "ping") {
            filtered.sortedWith(compareBy<RadarResult> { it.tcpMs == null }.thenBy { it.tcpMs ?: 0L })
        } else {
            filtered.sortedByDescending { it.score }
        }
    }
    fun addable(r: RadarResult) = r.status == RadarStatus.OK || r.status == RadarStatus.UDP_UNVERIFIED

    Scaffold(
        topBar = { AppTopBar(title = "رادار کانفیگ", onBackClick = onClose, isLoading = scanning) },
        bottomBar = {
            val topTen = results.filter { it.status == RadarStatus.OK }.sortedByDescending { it.score }.take(10)
            if (selected.isNotEmpty() || topTen.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, color = Color.Transparent) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (selected.isNotEmpty()) {
                            Button(onClick = { onAdd(selected.toList()) }, modifier = Modifier.weight(1f)) {
                                Text("افزودن " + selected.size + " انتخاب‌شده")
                            }
                        }
                        OutlinedButton(
                            onClick = { onAdd(topTen.map { it.uri }) },
                            enabled = topTen.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("۱۰ تای برتر") }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AndroidView(
                    factory = { ctx -> RadarScanView(ctx).also { radar = it } },
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                    update = { if (scanning) it.startScan() else it.stopScan() },
                )
            }
            item {
                Text("منابع", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadarGroup.entries.forEach { g ->
                        FilterChip(
                            selected = g in groups,
                            enabled = !scanning,
                            onClick = { groups = if (g in groups) groups - g else groups + g },
                            label = { Text(g.label) },
                        )
                    }
                }
                if (RadarGroup.COUNTRY in groups) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadarCountries.list.forEach { (iso, name) ->
                            FilterChip(
                                selected = iso in countries,
                                enabled = !scanning,
                                onClick = { countries = if (iso in countries) countries - iso else countries + iso },
                                label = { Text(RadarCountries.flag(iso) + " " + name) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(150 to "سریع ۱۵۰", 400 to "متعادل ۴۰۰", 800 to "عمیق ۸۰۰").forEach { (n, label) ->
                        FilterChip(selected = depth == n, enabled = !scanning, onClick = { depth = n }, label = { Text(label) })
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !scanning, onClick = { start() }) { Text("شروع اسکن") }
                    OutlinedButton(enabled = scanning, onClick = { stop() }) { Text("توقف") }
                }
                if (scanning && total > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { checked / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                RadarSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("یکتا", uniqueFound.toString(), MaterialTheme.colorScheme.onSurface)
                        Stat("تست‌شده", results.size.toString(), MaterialTheme.colorScheme.onSurface)
                        Stat("سالم", results.count { it.status == RadarStatus.OK }.toString(), Green)
                        Stat("سریع ≤۱۵۰ms", results.count { (it.tcpMs ?: Long.MAX_VALUE) <= 150 }.toString(), Blue)
                    }
                }
            }
            if (sourceStats.isNotEmpty()) {
                item {
                    TextButton(onClick = { showSources = !showSources }) {
                        val okSources = sourceStats.values.count { it.error == null && it.count > 0 }
                        Text((if (showSources) "▾ " else "▸ ") + "وضعیت منابع ($okSources/${sourceStats.size} سالم)")
                    }
                    if (showSources) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            sourceStats.entries.sortedBy { it.key }.forEach { (name, st) ->
                                val text = when {
                                    st.error != null -> "✗ " + st.error
                                    st.fromCache -> "✓ " + st.count + " (کش)"
                                    else -> "✓ " + st.count
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text, style = MaterialTheme.typography.bodySmall, color = if (st.error != null) Red else Green)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = okOnly, onClick = { okOnly = !okOnly }, label = { Text("فقط سالم") })
                    FilterChip(selected = sortMode == "score", onClick = { sortMode = "score" }, label = { Text("امتیاز") })
                    FilterChip(selected = sortMode == "ping", onClick = { sortMode = "ping" }, label = { Text("پینگ") })
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "همه", "vless" to "VLESS", "vmess" to "VMess", "trojan" to "Trojan", "ss" to "SS", "hysteria2" to "Hysteria2", "tuic" to "TUIC").forEach { (v, label) ->
                        FilterChip(selected = protocolFilter == v, onClick = { protocolFilter = v }, label = { Text(label) })
                    }
                }
            }
            items(visible, key = { it.uri }) { r ->
                ResultCard(
                    r = r,
                    selected = r.uri in selected,
                    onToggle = { if (r.uri in selected) selected.remove(r.uri) else selected.add(r.uri) },
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("config", r.uri))
                        Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
                    },
                    onAdd = { onAdd(listOf(r.uri)) },
                    addable = addable(r),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
    }
}

/** در تم RADAR Glass شیشه‌ای، در بقیه‌ی تم‌ها Card معمولی. */
@Composable
private fun RadarSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalRdrActive.current) {
        Box(modifier.glass(RoundedCornerShape(18.dp), shadow = 0.dp)) { content() }
    } else {
        Card(modifier) { content() }
    }
}

@Composable
private fun ResultCard(
    r: RadarResult,
    selected: Boolean,
    addable: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onAdd: () -> Unit,
) {
    val ep = r.endpoint
    val scoreColor = when {
        r.status == RadarStatus.UDP_UNVERIFIED -> Blue
        r.score >= 75 -> Green
        r.score >= 50 -> Amber
        else -> Red
    }
    RadarSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (addable) {
                    Checkbox(checked = selected, onCheckedChange = { onToggle() })
                    Spacer(Modifier.width(2.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        RadarCountries.flag(r.source.country) + "  " + (ep?.host ?: r.uri.take(40)) + (ep?.let { ":" + it.port } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(ep?.protocol?.uppercase(), ep?.security?.takeIf { it != "none" }, ep?.transport?.takeIf { it != "tcp" && it != "udp" }, r.source.name)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(scoreColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (r.status == RadarStatus.UDP_UNVERIFIED) "UDP" else r.score.toString(),
                        color = scoreColor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            val metrics = buildString {
                r.dnsMs?.let { append("DNS ").append(it).append(" ms") }
                r.tcpMs?.let { append(" · TCP ").append(it).append(" ms") }
                r.tlsMs?.let { append(" · TLS ").append(it).append(" ms") }
            }.trim().trimStart('·').trim()
            if (metrics.isNotEmpty()) Text(metrics, style = MaterialTheme.typography.bodySmall, color = scoreColor)
            Text(r.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCopy) { Text("کپی") }
                if (addable) TextButton(onClick = onAdd) { Text("افزودن برای تست واقعی") }
            }
        }
    }
}
