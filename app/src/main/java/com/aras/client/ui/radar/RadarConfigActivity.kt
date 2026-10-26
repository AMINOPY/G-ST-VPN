package com.aras.client.ui.radar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aras.client.radar.RadarCategory
import com.aras.client.radar.RadarParser
import com.aras.client.radar.RadarResult
import com.aras.client.radar.RadarStatus
import com.aras.client.radar.ui.RadarScanView
import com.aras.client.ui.compose.AppTheme
import com.aras.client.ui.compose.AppTopBar
import com.aras.client.ui.main.MainActivity
import com.aras.client.ui.rdr.LocalRdrActive
import com.aras.client.ui.rdr.glass

private val Green = Color(0xFF34C759)
private val Amber = Color(0xFFFF9F0A)
private val Blue = Color(0xFF0A84FF)
private val Red = Color(0xFFFF453A)

/**
 * رادار کانفیگ: منابع عمومی را می‌گیرد، یکتا می‌کند، با DNS/TCP/TLS می‌سنجد و بهترین‌ها را رتبه می‌دهد.
 * هیچ کانفیگی خودکار نصب یا وصل نمی‌شود؛ کاربر تصمیم می‌گیرد و تست واقعی داخل برنامه انجام می‌شود.
 */
class RadarConfigActivity : ComponentActivity() {
    private val vm: RadarConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                RadarConfigScreen(
                    vm = vm,
                    onBack = { finish() },
                    onImport = { uris -> importConfigs(uris) },
                    onCopy = { uris -> copyConfigs(uris) },
                )
            }
        }
    }

    private fun importConfigs(uris: List<String>) {
        if (uris.isEmpty()) return
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = "com.aras.client.action.IMPORT_RADAR_CONFIG"
                putExtra("radar_config", uris.joinToString("\n"))
            },
        )
        finish()
    }

    private fun copyConfigs(uris: List<String>) {
        if (uris.isEmpty()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("radar-configs", uris.joinToString("\n")))
        Toast.makeText(this, "${uris.size} کانفیگ کپی شد", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun RCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val rdr = LocalRdrActive.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (rdr) Modifier.glass(shape, shadow = 0.dp)
                else Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RadarConfigScreen(
    vm: RadarConfigViewModel,
    onBack: () -> Unit,
    onImport: (List<String>) -> Unit,
    onCopy: (List<String>) -> Unit,
) {
    var radar by remember { mutableStateOf<RadarScanView?>(null) }
    LaunchedEffect(radar) {
        val view = radar ?: return@LaunchedEffect
        vm.blips.collect { ms -> view.addTestedCandidate(ms) }
    }
    LaunchedEffect(vm.clearTick, radar) { radar?.clearBlips() }
    LaunchedEffect(vm.scanning, radar) { if (vm.scanning) radar?.startScan() else radar?.stopScan() }

    val countries = vm.results.mapNotNull { it.country }.distinct().sorted()
    val protocols = vm.results.map { it.cfg.protocol }.distinct().sorted()

    val visible = vm.results.filter { r ->
        val statusOk = when (vm.statusFilter) {
            "good" -> r.status == RadarStatus.OK || r.status == RadarStatus.SLOW
            "fast" -> r.status == RadarStatus.OK
            "udp" -> r.status == RadarStatus.UDP
            "failed" -> r.status == RadarStatus.FAILED
            else -> true
        }
        val protoOk = vm.protocolFilter == "all" || r.cfg.protocol == vm.protocolFilter
        val countryOk = vm.countryFilter == "all" || r.country == vm.countryFilter
        val q = vm.query.trim()
        val queryOk = q.isEmpty() || r.cfg.name.contains(q, true) || r.cfg.host.contains(q, true)
        statusOk && protoOk && countryOk && queryOk
    }.let { list ->
        when (vm.sort) {
            RadarSort.SCORE -> list.sortedByDescending { it.score }
            RadarSort.PING -> list.sortedWith(compareBy<RadarResult> { it.tcpMs == null }.thenBy { it.tcpMs ?: Long.MAX_VALUE })
            RadarSort.NAME -> list.sortedBy { it.cfg.name.lowercase() }
        }
    }
    val good = vm.results.count { it.status == RadarStatus.OK || it.status == RadarStatus.SLOW }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "رادار کانفیگ",
                onBackClick = onBack,
                isLoading = vm.scanning,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── رادار + کنترل ──
            item {
                RCard {
                    AndroidView(
                        factory = { ctx -> RadarScanView(ctx).also { radar = it } },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !vm.scanning, onClick = { vm.startScan() }) { Text("شروع اسکن") }
                        OutlinedButton(enabled = vm.scanning, onClick = { vm.stopScan() }) { Text("توقف") }
                    }
                    if (vm.scanning) {
                        if (vm.planned > 0) {
                            LinearProgressIndicator(
                                progress = { (vm.checked.toFloat() / vm.planned).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${vm.checked} / ${vm.planned}", style = MaterialTheme.typography.labelMedium)
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    Text(vm.message, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ── منابع ──
            item {
                RCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("منابع")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.selectAllGroups(true) }) { Text("همه") }
                        TextButton(onClick = { vm.selectAllGroups(false) }) { Text("هیچ") }
                    }
                    RadarCategory.entries.forEach { cat ->
                        val groupsOfCat = vm.groups.filter { it.category == cat }
                        if (groupsOfCat.isNotEmpty()) {
                            Text(cat.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                groupsOfCat.forEach { g ->
                                    val counts = g.sources.mapNotNull { vm.sourceReports[it.id] }
                                    val total = counts.sumOf { it.toIntOrNull() ?: 0 }
                                    val suffix = when {
                                        counts.isEmpty() -> ""
                                        total > 0 -> " · $total"
                                        else -> " · ✗"
                                    }
                                    FilterChip(
                                        selected = g.id in vm.selectedGroups,
                                        onClick = { vm.toggleGroup(g.id) },
                                        label = { Text(g.emoji + " " + g.title + suffix) },
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = vm.customUrl,
                        onValueChange = { vm.customUrl = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("لینک ساب یا فایل دلخواه (اختیاری)") },
                    )
                }
            }

            // ── گزینه‌ها ──
            item {
                RCard {
                    SectionTitle("گزینه‌های اسکن")
                    Text("سقف تعداد کانفیگ برای آزمایش", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(150, 300, 600, 1000).forEach { n ->
                            FilterChip(selected = vm.limit == n, onClick = { vm.limit = n }, label = { Text(n.toString()) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("بررسی عمیق TLS", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "دست‌دادن TLS برای کانفیگ‌های TLS/Reality؛ دقیق‌تر ولی کمی کندتر",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = vm.deep, onCheckedChange = { vm.deep = it })
                    }
                }
            }

            // ── آمار ──
            if (vm.results.isNotEmpty()) {
                item {
                    RCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("بررسی‌شده", vm.results.size.toString(), MaterialTheme.colorScheme.onSurface)
                            Stat("پاسخ‌گو", good.toString(), Green)
                            Stat("سریع ≤۳۰۰ms", vm.results.count { it.status == RadarStatus.OK }.toString(), Green)
                            Stat(
                                "بهترین پینگ",
                                vm.results.mapNotNull { it.tcpMs }.minOrNull()?.let { "$it ms" } ?: "—",
                                Blue,
                            )
                        }
                    }
                }

                // ── فیلتر و مرتب‌سازی ──
                item {
                    RCard {
                        SectionTitle("فیلتر و مرتب‌سازی")
                        OutlinedTextField(
                            value = vm.query,
                            onValueChange = { vm.query = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("جستجو در نام یا آدرس") },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "all" to "همه (${vm.results.size})",
                                "good" to "پاسخ‌گو ($good)",
                                "fast" to "سریع",
                                "udp" to "UDP",
                                "failed" to "ناموفق",
                            ).forEach { (k, t) ->
                                FilterChip(selected = vm.statusFilter == k, onClick = { vm.statusFilter = k }, label = { Text(t) })
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = vm.protocolFilter == "all", onClick = { vm.protocolFilter = "all" }, label = { Text("همه‌ی پروتکل‌ها") })
                            protocols.forEach { p ->
                                FilterChip(selected = vm.protocolFilter == p, onClick = { vm.protocolFilter = p }, label = { Text(p.uppercase()) })
                            }
                        }
                        if (countries.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = vm.countryFilter == "all", onClick = { vm.countryFilter = "all" }, label = { Text("همه‌ی کشورها") })
                                countries.forEach { c ->
                                    FilterChip(
                                        selected = vm.countryFilter == c,
                                        onClick = { vm.countryFilter = c },
                                        label = { Text(RadarParser.flagEmoji(c) + " " + c) },
                                    )
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                            Text("مرتب‌سازی:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp, end = 4.dp))
                            RadarSort.entries.forEach { s ->
                                FilterChip(selected = vm.sort == s, onClick = { vm.sort = s }, label = { Text(s.title) })
                            }
                        }
                    }
                }

                // ── عملیات گروهی ──
                item {
                    RCard {
                        SectionTitle("افزودن به برنامه")
                        Text(
                            "کانفیگ‌ها فقط به لیست اضافه می‌شوند؛ اتصال خودکار انجام نمی‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(enabled = good > 0, onClick = { onImport(vm.topUris(10)) }) { Text("۱۰ تای برتر") }
                            FilledTonalButton(enabled = good > 0, onClick = { onImport(vm.topUris(50)) }) { Text("۵۰ تای برتر") }
                            OutlinedButton(enabled = good > 0, onClick = { onCopy(vm.topUris(10)) }) { Text("کپی ۱۰ برتر") }
                        }
                    }
                }
            }

            // ── نتیجه‌ها ──
            items(visible, key = { it.cfg.key }) { r ->
                ResultCard(r, onAdd = { onImport(listOf(r.cfg.uri)) }, onCopy = { onCopy(listOf(r.cfg.uri)) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ResultCard(r: RadarResult, onAdd: () -> Unit, onCopy: () -> Unit) {
    val statusColor = when (r.status) {
        RadarStatus.OK -> Green
        RadarStatus.SLOW -> Amber
        RadarStatus.UDP -> Blue
        RadarStatus.FAILED -> Red
    }
    val canAdd = r.status != RadarStatus.FAILED
    RCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(RadarParser.flagEmoji(r.country), fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.cfg.name.ifBlank { r.cfg.host },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    r.cfg.protocol.uppercase() + " · " + r.cfg.security + " · " + r.cfg.transport + " · " + r.cfg.host + ":" + r.cfg.port,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(statusColor.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    if (r.status == RadarStatus.FAILED) "✗" else r.score.toString(),
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        val parts = buildList {
            r.dnsMs?.let { add("DNS $it ms") }
            r.tcpMs?.let { add("TCP $it ms") }
            r.tlsMs?.let { add("TLS $it ms") }
            r.jitterMs?.takeIf { it > 0 }?.let { add("نوسان $it ms") }
        }
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = statusColor)
        }
        Text(r.note + " · " + r.sourceTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (canAdd) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAdd) { Text("افزودن برای تست واقعی") }
                TextButton(onClick = onCopy) { Text("کپی") }
            }
        }
    }
}
