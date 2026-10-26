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
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aras.client.R
import com.aras.client.radar.ui.RadarScanView
import com.aras.client.ui.compose.AppTheme
import com.aras.client.ui.compose.AppTopBar
import com.aras.client.ui.main.MainActivity
import com.aras.client.ui.rdr.LocalRdrActive
import com.aras.client.ui.rdr.glass
import com.aras.client.util.CountryResolver

/**
 * رادار کانفیگ: جست‌وجو در منابع عمومی + پیش‌آزمایش دسترسی شبکه (DNS و TCP).
 * هیچ کانفیگی خودکار نصب یا وصل نمی‌شود؛ افزودن فقط با لمس کاربر و از مسیر همان واردکردن قبلی برنامه است.
 */
class RadarConfigActivity : ComponentActivity() {
    private val vm: RadarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                RadarScreen(vm = vm, onBack = { finish() }, onAdd = { raw -> importConfigs(raw) })
            }
        }
    }

    private fun importConfigs(raw: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = "com.aras.client.action.IMPORT_RADAR_CONFIG"
                putExtra("radar_config", raw)
            },
        )
        finish()
    }
}

private val Green = Color(0xFF34C759)
private val Amber = Color(0xFFFF9F0A)
private val Red = Color(0xFFFF453A)
private val Violet = Color(0xFFBF5AF2)

private val LIMITS = listOf(100, 200, 400, 800, 1500, 3000)
private val PROTOCOLS = listOf("vmess", "vless", "trojan", "ss", "hysteria2", "tuic")
private const val ADD_TOP = 20

@Composable
private fun RadarSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalRdrActive.current) {
        Box(modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), shadow = 0.dp)) { content() }
    } else {
        androidx.compose.material3.Card(modifier.fillMaxWidth()) { content() }
    }
}

private fun rank(r: RadarResult): Int = when (r.status) {
    ProbeStatus.REACHABLE -> 0
    ProbeStatus.UDP_UNVERIFIED -> 1
    else -> 2
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RadarScreen(vm: RadarViewModel, onBack: () -> Unit, onAdd: (String) -> Unit) {
    val context = LocalContext.current
    var radar by remember { mutableStateOf<RadarScanView?>(null) }
    var blipped by remember { mutableIntStateOf(0) }
    var resultFilter by rememberSaveable { mutableStateOf("all") }
    var protocolFilter by rememberSaveable { mutableStateOf("all") }
    var countryFilter by rememberSaveable { mutableStateOf("all") }
    var customInput by rememberSaveable { mutableStateOf("") }
    var showSources by rememberSaveable { mutableStateOf(false) }

    val results = vm.results
    // هر نتیجه‌ی تازه یک نقطه روی رادار می‌شود (سبز/زرد/قرمز بر اساس تأخیر)
    LaunchedEffect(results.size) {
        if (results.size < blipped) blipped = 0
        while (blipped < results.size) {
            val r = results[blipped]
            radar?.addTestedCandidate(if (r.status == ProbeStatus.REACHABLE) (r.tcpMs ?: 0L) else -1L)
            blipped++
        }
    }

    val reachableCount by remember { derivedStateOf { results.count { it.status == ProbeStatus.REACHABLE } } }
    val udpCount by remember { derivedStateOf { results.count { it.status == ProbeStatus.UDP_UNVERIFIED } } }
    val failedCount by remember { derivedStateOf { results.size - reachableCount - udpCount } }
    val fastCount by remember {
        derivedStateOf { results.count { it.status == ProbeStatus.REACHABLE && (it.tcpMs ?: Long.MAX_VALUE) <= 150 } }
    }
    val countries by remember {
        derivedStateOf {
            results.mapNotNull { it.country }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key to it.value }
        }
    }
    val hasUnknown by remember { derivedStateOf { results.any { it.country == null } } }

    val visible by remember {
        derivedStateOf {
            results.filter { r ->
                val statusOk = when (resultFilter) {
                    "reachable" -> r.status == ProbeStatus.REACHABLE
                    "udp" -> r.status == ProbeStatus.UDP_UNVERIFIED
                    "failed" -> rank(r) == 2
                    else -> true
                }
                val protoOk = protocolFilter == "all" || r.protocol == protocolFilter
                val countryOk = when (countryFilter) {
                    "all" -> true
                    "__unknown__" -> r.country == null
                    else -> r.country == countryFilter
                }
                statusOk && protoOk && countryOk
            }.sortedWith(
                compareBy<RadarResult>({ rank(it) }, { it.tcpMs ?: it.dnsMs ?: Long.MAX_VALUE }),
            )
        }
    }

    val message = when (vm.phase) {
        RadarPhase.IDLE -> stringResource(R.string.radar_msg_idle)
        RadarPhase.FETCHING -> stringResource(
            R.string.radar_msg_fetching,
            vm.sourceStates.count { it.value.first != SourceState.LOADING },
            vm.sourcesTotal,
        )
        RadarPhase.TESTING -> stringResource(R.string.radar_msg_testing, results.size, vm.planned, reachableCount)
        RadarPhase.DONE -> stringResource(R.string.radar_msg_done, results.size, reachableCount)
        RadarPhase.STOPPED -> stringResource(R.string.radar_msg_stopped, results.size, reachableCount)
        RadarPhase.NO_SOURCES -> stringResource(R.string.radar_msg_no_sources)
        RadarPhase.NONE_SELECTED -> stringResource(R.string.radar_msg_none_selected)
        RadarPhase.ERROR -> stringResource(R.string.radar_msg_error, vm.errorText)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.radar_title),
                onBackClick = onBack,
                isLoading = vm.scanning,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hero") {
                RadarSurface {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AndroidView(
                            factory = { ctx -> RadarScanView(ctx).also { radar = it } },
                            modifier = Modifier.fillMaxWidth().height(210.dp),
                            update = { if (vm.scanning) it.startScan() else it.stopScan() },
                        )
                        Text(stringResource(R.string.radar_headline), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.radar_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            enabled = !vm.scanning,
                            onClick = { radar?.clearBlips(); blipped = 0; vm.start() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(if (results.isEmpty()) R.string.radar_start else R.string.radar_rescan))
                        }
                        OutlinedButton(
                            enabled = vm.scanning,
                            onClick = { vm.stop() },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.radar_stop)) }
                    }
                    OutlinedButton(
                        enabled = reachableCount > 0,
                        onClick = {
                            val raw = visible.filter { it.status == ProbeStatus.REACHABLE }
                                .take(ADD_TOP).joinToString("\n") { it.uri }
                            if (raw.isBlank()) {
                                Toast.makeText(context, R.string.radar_add_top_none, Toast.LENGTH_SHORT).show()
                            } else onAdd(raw)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.radar_add_top, ADD_TOP)) }
                }
            }

            item(key = "message") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    if (vm.phase == RadarPhase.TESTING && vm.planned > 0) {
                        LinearProgressIndicator(
                            progress = { (results.size.toFloat() / vm.planned).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item(key = "stats") {
                RadarSurface {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Stat(stringResource(R.string.radar_stat_found), results.size.toString(), MaterialTheme.colorScheme.onSurface)
                        Stat(stringResource(R.string.radar_stat_reachable), reachableCount.toString(), Green)
                        Stat(stringResource(R.string.radar_stat_fast), fastCount.toString(), Green)
                    }
                }
            }

            item(key = "sources") {
                RadarSurface {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.radar_sources), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RadarGroup.entries.forEach { g ->
                                FilterChip(
                                    selected = g in vm.groups,
                                    enabled = !vm.scanning,
                                    onClick = { vm.toggleGroup(g) },
                                    label = { Text(stringResource(g.label)) },
                                )
                            }
                        }
                        Text(stringResource(R.string.radar_limit), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LIMITS.forEach { l ->
                                FilterChip(
                                    selected = vm.limit == l,
                                    enabled = !vm.scanning,
                                    onClick = { vm.setLimit(l) },
                                    label = { Text(l.toString()) },
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.radar_geo), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.radar_geo_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = vm.geo, enabled = !vm.scanning, onCheckedChange = { vm.setGeo(it) })
                        }
                    }
                }
            }

            item(key = "custom") {
                RadarSurface {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.radar_custom_title), style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customInput,
                                onValueChange = { customInput = it },
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.radar_custom_hint)) },
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                enabled = customInput.isNotBlank() && !vm.scanning,
                                onClick = { if (vm.addCustom(customInput)) customInput = "" },
                            ) { Text(stringResource(R.string.radar_custom_add)) }
                        }
                        if (vm.customLinks.isEmpty()) {
                            Text(
                                stringResource(R.string.radar_custom_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        vm.customLinks.toList().forEach { link ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    link,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(enabled = !vm.scanning, onClick = { vm.removeCustom(link) }) { Text("✕") }
                            }
                        }
                    }
                }
            }

            if (vm.sourceStates.isNotEmpty()) {
                item(key = "sourceStatus") {
                    RadarSurface {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val responded = vm.sourceStates.count { it.value.first == SourceState.OK }
                            TextButton(onClick = { showSources = !showSources }) {
                                Text(stringResource(R.string.radar_sources_detail, responded, vm.sourcesTotal))
                            }
                            if (showSources) {
                                RadarSources.forGroups(vm.groups, vm.customLinks).forEach { src ->
                                    val st = vm.sourceStates[src.id]
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(
                                            src.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        val (label, color) = when (st?.first) {
                                            SourceState.OK -> stringResource(R.string.radar_source_ok, st?.second ?: 0) to Green
                                            SourceState.EMPTY -> stringResource(R.string.radar_source_empty) to Amber
                                            SourceState.FAILED -> stringResource(R.string.radar_source_failed) to Red
                                            else -> stringResource(R.string.radar_source_loading) to MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (results.isNotEmpty()) {
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(resultFilter == "all", { resultFilter = "all" }, { Text(stringResource(R.string.radar_f_all, results.size)) })
                            FilterChip(resultFilter == "reachable", { resultFilter = "reachable" }, { Text(stringResource(R.string.radar_f_reachable, reachableCount)) })
                            if (udpCount > 0) {
                                FilterChip(resultFilter == "udp", { resultFilter = "udp" }, { Text(stringResource(R.string.radar_f_udp, udpCount)) })
                            }
                            FilterChip(resultFilter == "failed", { resultFilter = "failed" }, { Text(stringResource(R.string.radar_f_failed, failedCount)) })
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(protocolFilter == "all", { protocolFilter = "all" }, { Text(stringResource(R.string.radar_protocol_all)) })
                            PROTOCOLS.forEach { p ->
                                if (results.any { it.protocol == p }) {
                                    FilterChip(protocolFilter == p, { protocolFilter = p }, { Text(p.uppercase()) })
                                }
                            }
                        }
                        if (countries.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(countryFilter == "all", { countryFilter = "all" }, { Text(stringResource(R.string.radar_country_all)) })
                                countries.take(24).forEach { (iso, count) ->
                                    FilterChip(
                                        countryFilter == iso,
                                        { countryFilter = iso },
                                        { Text(CountryResolver.flagForCountry(iso).ifBlank { iso } + " " + iso + " · " + count) },
                                    )
                                }
                                if (hasUnknown) {
                                    FilterChip(
                                        countryFilter == "__unknown__",
                                        { countryFilter = "__unknown__" },
                                        { Text(stringResource(R.string.radar_country_unknown)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(visible, key = { it.uri }) { r ->
                ResultCard(
                    r = r,
                    onAdd = { onAdd(r.uri) },
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("config", r.uri))
                        Toast.makeText(context, R.string.radar_copied, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultCard(r: RadarResult, onAdd: () -> Unit, onCopy: () -> Unit) {
    val ms = r.tcpMs
    val badgeColor = when {
        r.status == ProbeStatus.UDP_UNVERIFIED -> Violet
        ms == null -> Red
        ms <= 150 -> Green
        ms <= 300 -> Amber
        else -> Red
    }
    val badge = when {
        r.status == ProbeStatus.UDP_UNVERIFIED -> "UDP"
        ms == null -> "—"
        else -> "$ms ms"
    }
    val note = when (r.status) {
        ProbeStatus.REACHABLE -> stringResource(R.string.radar_note_tcp, ms ?: 0L, r.dnsMs ?: 0L, r.samples)
        ProbeStatus.UDP_UNVERIFIED -> stringResource(R.string.radar_note_udp, r.dnsMs ?: 0L)
        ProbeStatus.UNREACHABLE -> stringResource(R.string.radar_note_unreach)
        ProbeStatus.DNS_FAILED -> stringResource(R.string.radar_note_dns)
        ProbeStatus.BAD_ENDPOINT -> stringResource(R.string.radar_note_bad)
    }
    val usable = r.status == ProbeStatus.REACHABLE || r.status == ProbeStatus.UDP_UNVERIFIED
    RadarSurface {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(CountryResolver.flagForCountry(r.country).ifBlank { "🌐" }, fontSize = 24.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        r.remark.ifBlank { r.host + ":" + r.port },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        r.protocol.uppercase() + " · " + r.host + ":" + r.port + " · " + r.sourceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    badge,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (usable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onAdd) { Text(stringResource(R.string.radar_add)) }
                    TextButton(onClick = onCopy) { Text(stringResource(R.string.radar_copy)) }
                }
            }
        }
    }
}
