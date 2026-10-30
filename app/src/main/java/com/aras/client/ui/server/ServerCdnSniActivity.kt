package com.aras.client.ui.server

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.aras.client.R
import com.aras.client.core.cdn.CdnSniAutoScanner
import com.aras.client.core.cdn.CdnSniConfig
import com.aras.client.enums.EConfigType
import com.aras.client.extension.toastError
import com.aras.client.extension.toastSuccess
import com.aras.client.handler.MmkvManager
import com.aras.client.ui.compose.FormTextField
import com.aras.client.ui.radar.RadarConfigActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Standalone CDN/SNI profile editor. Manual IP/SNI and the two independent auto scanners
 * are kept together; a successful auto scan saves the selected pair and returns to profiles.
 */
class ServerCdnSniActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.CDN_SNI

    @Composable
    override fun ScreenContent() {
        var edgeIp by rememberSaveable { mutableStateOf(initialConfig.cdnSniTargets.orEmpty().ifBlank { CdnSniConfig.effectiveIp(initialConfig) }) }
        var sni by rememberSaveable { mutableStateOf(initialConfig.cdnSniFront.orEmpty().ifBlank { CdnSniConfig.effectiveSni(initialConfig) }) }
        var selectedMode by rememberSaveable {
            mutableStateOf(initialConfig.cdnSniMode?.takeIf { it == "auto_ir" || it == "auto_us" } ?: CdnSniConfig.MODE_MANUAL)
        }
        var scanning by androidx.compose.runtime.remember { mutableStateOf(false) }
        var testingPing by androidx.compose.runtime.remember { mutableStateOf(false) }
        var pingResult by rememberSaveable { mutableStateOf<String?>(null) }

        val badInput = stringResource(R.string.cdn_sni_invalid)

        fun startAuto(mode: CdnSniAutoScanner.Mode) {
            if (scanning) return
            scanning = true
            lifecycleScope.launch {
                val result = try {
                    withContext(Dispatchers.IO) {
                        CdnSniAutoScanner.scan(applicationContext, mode)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } finally {
                    scanning = false
                }
                if (result == null) {
                    toastError(R.string.cdn_sni_auto_not_found)
                    return@launch
                }
                edgeIp = result.ip
                sni = result.sni
                selectedMode = mode.key
                val draft = initialConfig.copy().also {
                    it.cdnSniMode = mode.key
                    it.cdnSniTargets = result.ip
                    it.cdnSniFront = result.sni
                }
                val generatedName = "CDN/SNI · ${result.sni}"
                // AUTO IR/US keep the original one-tap flow: rank the live TLS candidates,
                // save the winning pair, and return directly to the server list.
                save(draft, initialConfig.remarks.takeIf { it.isNotBlank() } ?: generatedName)
            }
        }

        ServerEditorScaffold(
            title = stringResource(R.string.menu_item_cdn_sni),
            onSaveClick = {
                val draft = initialConfig.copy().also {
                    it.cdnSniMode = selectedMode
                    it.cdnSniTargets = edgeIp.trim()
                    it.cdnSniFront = sni.trim()
                }
                if (CdnSniConfig.settingsOf(draft) == null) {
                    toastError(badInput)
                } else {
                    val generatedName = "CDN/SNI · ${sni.trim()}"
                    save(draft, initialConfig.remarks.takeIf { it.isNotBlank() } ?: generatedName)
                }
            }
        ) {
            Text(
                stringResource(R.string.cdn_sni_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                OutlinedButton(
                    onClick = { selectedMode = CdnSniConfig.MODE_MANUAL },
                    enabled = !scanning,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.cdn_sni_manual)) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    onClick = { startAuto(CdnSniAutoScanner.Mode.IR) },
                    enabled = !scanning,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.cdn_sni_auto_ir)) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    onClick = { startAuto(CdnSniAutoScanner.Mode.US) },
                    enabled = !scanning,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.cdn_sni_auto_us)) }
            }
            if (scanning) {
                Text(
                    stringResource(R.string.cdn_sni_auto_scanning),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        startActivity(Intent(this@ServerCdnSniActivity, RadarConfigActivity::class.java).apply {
                            putExtra("cdn_edge_ip", CdnSniConfig.firstIp(edgeIp).orEmpty())
                            putExtra("cdn_sni_host", CdnSniConfig.firstSni(sni).orEmpty())
                        })
                    },
                    enabled = !scanning,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("یافتن کانفیگ سازگار و اعمال CDN/SNI") }
            }
            FormTextField(
                label = stringResource(R.string.cdn_sni_targets),
                value = edgeIp,
                onValueChange = { edgeIp = it; selectedMode = CdnSniConfig.MODE_MANUAL },
                maxLines = 3,
            )
            FormTextField(
                label = stringResource(R.string.cdn_sni_front),
                value = sni,
                onValueChange = { sni = it; selectedMode = CdnSniConfig.MODE_MANUAL },
                maxLines = 3,
            )
            OutlinedButton(
                onClick = {
                    if (testingPing) return@OutlinedButton
                    testingPing = true
                    pingResult = null
                    lifecycleScope.launch {
                        val result = try {
                            withContext(Dispatchers.IO) {
                                CdnSniAutoScanner.test(edgeIp.trim(), sni.trim())
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        } finally {
                            testingPing = false
                        }
                        pingResult = result?.let { "پاسخ TLS واقعی: ${it.latencyMs} میلی‌ثانیه (TCP + TLS؛ نه تأیید تونل VPN)" }
                            ?: "اتصال TCP/TLS ناموفق بود؛ IP و SNI را بررسی کن."
                    }
                },
                enabled = !scanning && !testingPing,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) { Text(if (testingPing) "در حال سنجش واقعی…" else "سنجش واقعی IP/SNI") }
            pingResult?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Text(
                stringResource(R.string.cdn_sni_auto_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                stringResource(R.string.cdn_sni_psiphon_transport_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    /** No address or port to validate, so the shared basic checks are bypassed. */
    private fun save(config: com.aras.client.dto.entities.ProfileItem, remarks: String) {
        config.remarks = remarks
        config.server = null
        config.serverPort = null
        config.description = ""
        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        val guid = MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        ProfileEditorResult.run { finishSaved(guid, isRunning) }
    }
}
