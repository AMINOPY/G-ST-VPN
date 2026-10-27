package com.aras.client.ui.server

import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
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
import com.aras.client.core.cdn.CdnSniCa
import com.aras.client.core.cdn.CdnSniConfig
import com.aras.client.enums.EConfigType
import com.aras.client.extension.toastError
import com.aras.client.extension.toastInfo
import com.aras.client.extension.toastSuccess
import com.aras.client.handler.MmkvManager
import com.aras.client.ui.compose.FormTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Standalone CDN/SNI profile editor. Manual IP/SNI and the two independent auto scanners
 * are kept together; a successful auto scan saves the selected pair and returns to profiles.
 */
class ServerCdnSniActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.CDN_SNI

    private val saveCertificate =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-x509-ca-cert")) { uri ->
            uri ?: return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(CdnSniCa.certificatePem(this).toByteArray()) }
                toastSuccess(R.string.cdn_sni_cert_saved)
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (e: Exception) {
                toastError(R.string.cdn_sni_cert_failed)
            }
        }

    @Composable
    override fun ScreenContent() {
        var edgeIp by rememberSaveable { mutableStateOf(CdnSniConfig.effectiveIp(initialConfig)) }
        var sni by rememberSaveable { mutableStateOf(CdnSniConfig.effectiveSni(initialConfig)) }
        var scanning by rememberSaveable { mutableStateOf(false) }

        val badInput = stringResource(R.string.cdn_sni_invalid)

        fun startAuto(mode: CdnSniAutoScanner.Mode) {
            if (scanning) return
            scanning = true
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    CdnSniAutoScanner.scan(applicationContext, mode)
                }
                scanning = false
                if (result == null) {
                    toastError(R.string.cdn_sni_auto_not_found)
                    return@launch
                }
                edgeIp = result.ip
                sni = result.sni
                val draft = initialConfig.copy().also {
                    it.cdnSniMode = mode.key
                    it.cdnSniTargets = result.ip
                    it.cdnSniFront = result.sni
                }
                val label = if (mode == CdnSniAutoScanner.Mode.IR) "AUTO IR" else "AUTO US"
                save(draft, "$label · ${result.sni}")
            }
        }

        ServerEditorScaffold(
            title = stringResource(R.string.menu_item_cdn_sni),
            onSaveClick = {
                val draft = initialConfig.copy().also {
                    it.cdnSniMode = CdnSniConfig.MODE_MANUAL
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
                    onClick = { startAuto(CdnSniAutoScanner.Mode.IR) },
                    enabled = !scanning,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.cdn_sni_auto_ir)) }
                Spacer(Modifier.width(8.dp))
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
            FormTextField(
                label = stringResource(R.string.cdn_sni_targets),
                value = edgeIp,
                onValueChange = { edgeIp = it },
                maxLines = 1,
            )
            FormTextField(
                label = stringResource(R.string.cdn_sni_front),
                value = sni,
                onValueChange = { sni = it },
                maxLines = 1,
            )
            Text(
                stringResource(R.string.cdn_sni_auto_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                stringResource(R.string.cdn_sni_cert_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            OutlinedButton(
                onClick = {
                    toastInfo(R.string.cdn_sni_cert_steps)
                    saveCertificate.launch("ArasClient-CDN-SNI-CA.crt")
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) { Text(stringResource(R.string.cdn_sni_cert_export)) }
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
