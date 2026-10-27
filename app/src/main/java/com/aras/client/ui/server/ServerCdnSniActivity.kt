package com.aras.client.ui.server

import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
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
import com.aras.client.R
import com.aras.client.core.cdn.CdnSniCa
import com.aras.client.core.cdn.CdnSniConfig
import com.aras.client.enums.EConfigType
import com.aras.client.extension.toastError
import com.aras.client.extension.toastInfo
import com.aras.client.extension.toastSuccess
import com.aras.client.handler.MmkvManager
import com.aras.client.ui.compose.FormTextField

/**
 * Editor for a CDN/SNI profile: two fields and a mode.
 *
 * Auto fills them from the built-in preset; manual leaves them to the user, who
 * separates entries with Enter, a comma or a space. There is no server.
 */
class ServerCdnSniActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.CDN_SNI

    private val saveCertificate =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-x509-ca-cert")) { uri ->
            uri ?: return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(CdnSniCa.certificatePem(this).toByteArray()) }
                toastSuccess(R.string.cdn_sni_cert_saved)
                // The certificate is trusted from the system settings, not from the app.
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (e: Exception) {
                toastError(R.string.cdn_sni_cert_failed)
            }
        }

    @Composable
    override fun ScreenContent() {
        var remarks by rememberSaveable { mutableStateOf(initialConfig.remarks) }
        var auto by rememberSaveable { mutableStateOf(initialConfig.cdnSniMode != CdnSniConfig.MODE_MANUAL) }
        var manualTargets by rememberSaveable { mutableStateOf(initialConfig.cdnSniTargets.orEmpty()) }
        var manualFront by rememberSaveable { mutableStateOf(initialConfig.cdnSniFront.orEmpty()) }

        val targets = if (auto) CdnSniConfig.AUTO_TARGETS else manualTargets
        val front = if (auto) CdnSniConfig.AUTO_FRONT else manualFront
        val badInput = stringResource(R.string.cdn_sni_invalid)
        val noName = stringResource(R.string.server_lab_remarks)

        ServerEditorScaffold(
            title = stringResource(R.string.menu_item_cdn_sni),
            onSaveClick = {
                if (remarks.isBlank()) {
                    toastError(noName)
                } else {
                    val draft = initialConfig.copy().also {
                        it.cdnSniMode = if (auto) CdnSniConfig.MODE_AUTO else CdnSniConfig.MODE_MANUAL
                        it.cdnSniTargets = targets
                        it.cdnSniFront = front
                    }
                    if (CdnSniConfig.settingsOf(draft) == null) toastError(badInput) else save(draft, remarks.trim())
                }
            }
        ) {
            Text(
                stringResource(R.string.cdn_sni_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            FormTextField(stringResource(R.string.server_lab_remarks), remarks, { remarks = it })
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = auto, onClick = { auto = true }, label = { Text(stringResource(R.string.cdn_sni_mode_auto)) })
                FilterChip(selected = !auto, onClick = { auto = false }, label = { Text(stringResource(R.string.cdn_sni_mode_manual)) })
            }
            FormTextField(
                label = stringResource(R.string.cdn_sni_targets),
                value = targets,
                onValueChange = { manualTargets = it },
                enabled = !auto,
                maxLines = 8,
            )
            FormTextField(
                label = stringResource(R.string.cdn_sni_front),
                value = front,
                onValueChange = { manualFront = it },
                enabled = !auto,
                maxLines = 8,
            )
            Text(
                stringResource(if (auto) R.string.cdn_sni_auto_hint else R.string.cdn_sni_manual_hint),
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
