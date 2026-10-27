package com.aras.client.ui.cdn

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aras.client.AppConfig
import com.aras.client.R
import com.aras.client.core.cdn.CdnCandidatePool
import com.aras.client.core.cdn.CdnScanHit
import com.aras.client.core.cdn.CdnSniFronting
import com.aras.client.core.cdn.CdnSniScanner
import com.aras.client.dto.entities.ProfileItem
import com.aras.client.extension.toastError
import com.aras.client.extension.toastSuccess
import com.aras.client.handler.MmkvManager
import com.aras.client.ui.compose.AppTheme
import com.aras.client.ui.compose.FormDropdownField
import com.aras.client.ui.compose.FormTextField
import com.aras.client.ui.server.ProfileEditorResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * CDN/SNI: pick an existing profile that runs over TLS and an HTTP-style transport,
 * scan edge addresses with allowed server names, and save the pairs that work as new
 * profiles. The original profile is never modified.
 */
@OptIn(ExperimentalMaterial3Api::class)
class CdnSniActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val subscriptionId = intent.getStringExtra("subscriptionId").orEmpty()
        setContent {
            AppTheme {
                CdnSniScreen(subscriptionId)
            }
        }
    }

    private fun loadEligibleProfiles(): List<Pair<String, ProfileItem>> =
        MmkvManager.decodeAllServerList().mapNotNull { guid ->
            MmkvManager.decodeServerConfig(guid)?.takeIf { CdnSniFronting.isEligible(it) }?.let { guid to it }
        }

    private fun save(base: ProfileItem, hit: CdnScanHit, subscriptionId: String): Boolean = try {
        val item = CdnSniFronting.apply(base, hit.candidate)
        item.subscriptionId = subscriptionId.ifBlank { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        val guid = MmkvManager.encodeServerConfig("", item)
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(ProfileEditorResult.EXTRA_ACTION, ProfileEditorResult.ACTION_SAVED)
                putExtra(ProfileEditorResult.EXTRA_GUID, guid)
                putExtra(ProfileEditorResult.EXTRA_RESTART_SERVICE, false)
            }
        )
        true
    } catch (e: Exception) {
        false
    }

    @Composable
    private fun CdnSniScreen(subscriptionId: String) {
        val scope = rememberCoroutineScope()
        val profiles = remember { loadEligibleProfiles() }
        val labels = remember(profiles) {
            profiles.mapIndexed { i, (_, p) ->
                "${i + 1}. ${p.remarks.ifBlank { p.server.orEmpty() }}"
            }
        }
        var selected by remember { mutableIntStateOf(0) }
        var auto by rememberSaveable { mutableStateOf(true) }
        val autoIps = remember { CdnCandidatePool.DEFAULT_RANGES.joinToString("\n") }
        val autoSnis = remember { CdnCandidatePool.DEFAULT_SNIS.joinToString("\n") }
        // Manual text is kept separately so switching modes never loses what was typed.
        var manualIps by rememberSaveable { mutableStateOf("") }
        var manualSnis by rememberSaveable { mutableStateOf("") }
        val ipText = if (auto) autoIps else manualIps
        val sniText = if (auto) autoSnis else manualSnis
        var running by remember { mutableStateOf(false) }
        var done by remember { mutableIntStateOf(0) }
        var total by remember { mutableIntStateOf(0) }
        val hits = remember { mutableStateListOf<CdnScanHit>() }
        var job by remember { mutableStateOf<Job?>(null) }
        var status by remember { mutableStateOf("") }

        val base = profiles.getOrNull(selected)?.second
        val noProfileMsg = stringResource(R.string.cdn_sni_no_profile)
        val nothingMsg = stringResource(R.string.cdn_sni_nothing_to_scan)
        val foundFmt = stringResource(R.string.cdn_sni_found)
        val noneFound = stringResource(R.string.cdn_sni_none_found)
        val savedMsg = stringResource(R.string.cdn_sni_saved)
        val saveFailed = stringResource(R.string.cdn_sni_save_failed)

        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.cdn_sni_title)) }) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.cdn_sni_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (profiles.isEmpty()) {
                    Text(
                        noProfileMsg,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    FormDropdownField(
                        label = stringResource(R.string.cdn_sni_base_profile),
                        value = labels[selected],
                        options = labels,
                        onValueChange = { v -> labels.indexOf(v).takeIf { it >= 0 }?.let { selected = it; hits.clear() } },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = auto,
                        onClick = { auto = true },
                        label = { Text(stringResource(R.string.cdn_sni_mode_auto)) },
                        enabled = !running,
                    )
                    FilterChip(
                        selected = !auto,
                        onClick = { auto = false },
                        label = { Text(stringResource(R.string.cdn_sni_mode_manual)) },
                        enabled = !running,
                    )
                }
                FormTextField(
                    label = stringResource(R.string.cdn_sni_ips),
                    value = ipText,
                    onValueChange = { manualIps = it },
                    enabled = !auto && !running,
                    maxLines = 6,
                )
                FormTextField(
                    label = stringResource(R.string.cdn_sni_snis),
                    value = sniText,
                    onValueChange = { manualSnis = it },
                    enabled = !auto && !running,
                    maxLines = 6,
                )
                Text(
                    stringResource(if (auto) R.string.cdn_sni_auto_hint else R.string.cdn_sni_manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    stringResource(R.string.cdn_sni_vpn_off_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = !running && base != null,
                        onClick = {
                            val ips = CdnCandidatePool.parseIps(ipText)
                            val snis = CdnCandidatePool.parseSnis(sniText)
                            val pairs = CdnCandidatePool.pairs(ips, snis)
                            if (pairs.isEmpty()) {
                                toastError(nothingMsg)
                                return@Button
                            }
                            hits.clear()
                            done = 0
                            total = pairs.size
                            running = true
                            status = ""
                            job = scope.launch {
                                try {
                                    val found = CdnSniScanner.scan(pairs, CdnSniFronting.originHost(base!!)) { _, hit ->
                                        done++
                                        if (hit != null) hits.add(hit)
                                    }
                                    hits.clear()
                                    hits.addAll(found)
                                    status = if (found.isEmpty()) noneFound else foundFmt.format(found.size, pairs.size)
                                } finally {
                                    running = false
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.cdn_sni_scan)) }
                    if (running) {
                        OutlinedButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.cdn_sni_stop)) }
                    }
                }
                if (running) {
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
                if (status.isNotEmpty()) {
                    Text(status, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                }
                hits.sortedBy { it.latencyMs }.forEach { hit ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(hit.candidate.ip, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${hit.candidate.sni} · ${hit.latencyMs} ms",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = {
                                val ok = base != null && save(base, hit, subscriptionId)
                                if (ok) toastSuccess(savedMsg) else toastError(saveFailed)
                            }) { Text(stringResource(R.string.cdn_sni_save)) }
                        }
                    }
                }
            }
        }
    }
}
